package org.ovirt.mobile.movirt.ui.vms;

import android.content.Intent;
import android.net.Uri;
import android.os.RemoteException;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Bean;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.FragmentById;
import org.androidannotations.annotations.InstanceState;
import org.androidannotations.annotations.OptionsItem;
import org.androidannotations.annotations.OptionsMenu;
import org.androidannotations.annotations.Receiver;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import org.ovirt.mobile.movirt.Broadcasts;
import org.ovirt.mobile.movirt.R;
import org.ovirt.mobile.movirt.model.Vm;
import org.ovirt.mobile.movirt.model.trigger.Trigger;
import org.ovirt.mobile.movirt.rest.ActionTicket;
import org.ovirt.mobile.movirt.rest.OVirtClient;
import org.ovirt.mobile.movirt.sync.SyncAdapter;
import org.ovirt.mobile.movirt.ui.DiskDetailFragment;
import org.ovirt.mobile.movirt.ui.EventsFragment;
import org.ovirt.mobile.movirt.ui.HasProgressBar;
import org.ovirt.mobile.movirt.ui.ProgressBarResponse;
import org.ovirt.mobile.movirt.ui.TabChangedListener;
import org.ovirt.mobile.movirt.ui.triggers.EditTriggersActivity;
import org.ovirt.mobile.movirt.ui.triggers.EditTriggersActivity_;

@EActivity(R.layout.activity_vm_detail)
@OptionsMenu(R.menu.vm)
public class VmDetailActivity extends ActionBarActivity implements TabChangedListener.HasCurrentlyShown, HasProgressBar {

    private static final String TAG = VmDetailActivity.class.getSimpleName();

    private String vmId = null;

    @InstanceState
    TabChangedListener.CurrentlyShown currentlyShown = TabChangedListener.CurrentlyShown.VM_DETAIL_GENERAL;

    @ViewById
    View generalFragment;

    @ViewById
    View disksFragment;

    @ViewById
    View eventsFragment;

    @FragmentById
    DiskDetailFragment diskDetails;

    @FragmentById
    EventsFragment eventsList;

    @Bean
    OVirtClient client;

    @ViewById
    ProgressBar progress;

    @Bean
    SyncAdapter syncAdapter;

    @AfterViews
    void init() {
        Uri vmUri = getIntent().getData();
        vmId = vmUri.getLastPathSegment();

        diskDetails.setVmId(vmId);
        eventsList.setFilterVmId(vmId);

        initTabs();
        hideProgressBar();
    }

    private void initTabs() {
        generalFragment.setVisibility(currentlyShown == TabChangedListener.CurrentlyShown.VM_DETAIL_GENERAL ? View.VISIBLE : View.GONE);
        eventsFragment.setVisibility(currentlyShown == TabChangedListener.CurrentlyShown.EVENTS ? View.VISIBLE : View.GONE);
        disksFragment.setVisibility(currentlyShown == TabChangedListener.CurrentlyShown.DISKS ? View.VISIBLE : View.GONE);

        TabChangedListener.CurrentlyShown tmpCurrentlyShown = currentlyShown;

        getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        ActionBar.Tab generalTab = getSupportActionBar().newTab()
                .setText("General")
                .setTabListener(new TabChangedListener(generalFragment, TabChangedListener.CurrentlyShown.VM_DETAIL_GENERAL, this));

        ActionBar.Tab eventsTab = getSupportActionBar().newTab()
                .setText("Events")
                .setTabListener(new TabChangedListener(eventsFragment, TabChangedListener.CurrentlyShown.EVENTS, this));

        ActionBar.Tab disksTab = getSupportActionBar().newTab()
                .setText("Disks")
                .setTabListener(new TabChangedListener(disksFragment, TabChangedListener.CurrentlyShown.DISKS, this));

        getSupportActionBar().addTab(generalTab);
        getSupportActionBar().addTab(disksTab);
        getSupportActionBar().addTab(eventsTab);

        if (tmpCurrentlyShown == TabChangedListener.CurrentlyShown.EVENTS) {
            eventsTab.select();
        } else if (tmpCurrentlyShown == TabChangedListener.CurrentlyShown.DISKS) {
            disksTab.select();
        } else {
            generalTab.select();
        }

    }

    @Receiver(actions = Broadcasts.CONNECTION_FAILURE, registerAt = Receiver.RegisterAt.OnResumeOnPause)
    void connectionFailure(@Receiver.Extra(Broadcasts.Extras.CONNECTION_FAILURE_REASON) String reason) {
        Toast.makeText(VmDetailActivity.this, R.string.rest_req_failed + " " + reason, Toast.LENGTH_LONG).show();
    }

    @OptionsItem(R.id.action_edit_triggers)
    void editTriggers() {
        final Intent intent = new Intent(this, EditTriggersActivity_.class);
        intent.putExtra(EditTriggersActivity.EXTRA_TARGET_ENTITY_ID, vmId);
        intent.putExtra(EditTriggersActivity.EXTRA_TARGET_ENTITY_NAME, vmId);
        intent.putExtra(EditTriggersActivity.EXTRA_SCOPE, Trigger.Scope.ITEM);
        startActivity(intent);
    }

    @OptionsItem(R.id.action_run)
    @Background
    void start() {
        client.startVm(vmId);
        syncVm();
    }

    @OptionsItem(R.id.action_stop)
    @Background
    void stop() {
        client.stopVm(vmId);
        syncVm();
    }

    @OptionsItem(R.id.action_reboot)
    @Background
    void reboot() {
        client.rebootVm(vmId);
        syncVm();
    }

    @Override
    public void setCurrentlyShown(TabChangedListener.CurrentlyShown currentlyShown) {
        this.currentlyShown = currentlyShown;
    }

    @OptionsItem(R.id.action_console)
    @Background
    void openConsole() {
        syncAdapter.syncVm(vmId, new ProgressBarResponse<Vm>(this) {

            @Override
            public void onResponse(final Vm freshVm) throws RemoteException {

                client.getConsoleTicket(vmId, new ProgressBarResponse<ActionTicket>(VmDetailActivity.this) {
                    @Override
                    public void onResponse(ActionTicket ticket) throws RemoteException {
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW)
                                    .setType("application/vnd.vnc")
                                    .setData(Uri.parse(makeConsoleUrl(freshVm, ticket)));
                            startActivity(intent);
                        } catch (IllegalArgumentException e) {
                            makeToast(e.getMessage());
                        } catch (Exception e) {
                            makeToast("Failed to open console client. Check if aSPICE/bVNC is installed.");
                        }
                    }
                });
            }
        });
    }

    private void syncVm() {
        syncAdapter.syncVm(vmId, new ProgressBarResponse<Vm>(this));
    }

    /**
     * Returns URL for running console intent.
     * @throws java.lang.IllegalArgumentException with description
     *   if the URL can't be created from input.
     */
    private String makeConsoleUrl(Vm vm, ActionTicket ticket)
            throws IllegalArgumentException {

        if (vm.getDisplayType() == null) {
            throw new IllegalArgumentException("Vm's display type cannot be null");
        }

        String passwordPart = "";
        if (ticket != null && ticket.ticket != null && ticket.ticket.value != null
                && !ticket.ticket.value.isEmpty()) {
            switch (vm.getDisplayType()) {
                case VNC:
                    passwordPart = "VncPassword";
                    break;
                case SPICE:
                    passwordPart = "SpicePassword";
                    break;
            }
            passwordPart += "=" + ticket.ticket.value;
        }

        return vm.getDisplayType().getProtocol() + "://" + vm.getDisplayAddress() + ":" + vm.getDisplayPort() + "?" + passwordPart;
    }

    @UiThread
    void makeToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    @UiThread
    @Override
    public void showProgressBar() {
        progress.setVisibility(View.VISIBLE);
    }

    @UiThread
    @Override
    public void hideProgressBar() {
        progress.setVisibility(View.GONE);
    }
}
