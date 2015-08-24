package org.ovirt.mobile.movirt.ui;

import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.content.Loader;
import android.support.v4.widget.SwipeRefreshLayout;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Bean;
import org.androidannotations.annotations.EFragment;
import org.androidannotations.annotations.InstanceState;
import org.androidannotations.annotations.Receiver;
import org.androidannotations.annotations.ViewById;
import org.ovirt.mobile.movirt.Broadcasts;
import org.ovirt.mobile.movirt.R;
import org.ovirt.mobile.movirt.model.Event;
import org.ovirt.mobile.movirt.provider.ProviderFacade;
import org.ovirt.mobile.movirt.sync.EventsHandler;
import org.ovirt.mobile.movirt.sync.SyncUtils;
import org.ovirt.mobile.movirt.util.CursorAdapterLoader;

import static org.ovirt.mobile.movirt.provider.OVirtContract.BaseEntity.ID;
import static org.ovirt.mobile.movirt.provider.OVirtContract.Event.CLUSTER_ID;
import static org.ovirt.mobile.movirt.provider.OVirtContract.Event.STORAGE_DOMAIN_ID;
import static org.ovirt.mobile.movirt.provider.OVirtContract.Event.VM_ID;
import static org.ovirt.mobile.movirt.provider.OVirtContract.Vm.HOST_ID;

@EFragment(R.layout.fragment_event_list)
public class EventsFragment extends RefreshableLoaderFragment {

    @ViewById
    ListView list;

    @Bean
    ProviderFacade provider;

    @Bean
    EventsHandler eventsHandler;

    @InstanceState
    String filterHostId;

    @InstanceState
    String filterClusterId;

    @InstanceState
    String filterVmId;

    @InstanceState
    String filterStorageDomainId;

    @Bean
    SyncUtils syncUtils;

    @ViewById
    SwipeRefreshLayout swipeEventsContainer;

    private int page = 1;
    private static final int EVENTS_PER_PAGE = 20;
    private static final String TAG = EventsFragment.class.getSimpleName();

    private EndlessScrollListener endlessScrollListener = new EndlessScrollListener() {
        @Override
        public void onLoadMore(int page, int totalItemsCount) {
            loadMoreData(page);
        }
    };

    private CursorAdapterLoader cursorAdapterLoader;

    @AfterViews
    void init() {
        SimpleCursorAdapter eventListAdapter = new EventsCursorAdapter(getActivity());

        list.setAdapter(eventListAdapter);

        cursorAdapterLoader = new CursorAdapterLoader(eventListAdapter) {
            @Override
            public synchronized Loader<Cursor> onCreateLoader(int id, Bundle args) {
                final ProviderFacade.QueryBuilder<Event> query = provider.query(Event.class);
                if (filterHostId != null) query.where(HOST_ID, filterHostId);
                if (filterClusterId != null) query.where(CLUSTER_ID, filterClusterId);
                if (filterVmId != null) query.where(VM_ID, filterVmId);
                if (filterStorageDomainId != null) query.where(STORAGE_DOMAIN_ID, filterStorageDomainId);
                return query.orderByDescending(ID).limit(page * EVENTS_PER_PAGE).asLoader();
            }
        };

        getLoaderManager().initLoader(0, null, cursorAdapterLoader);

        list.setOnScrollListener(endlessScrollListener);
    }

    @Override
    public void restartLoader() {
        getLoaderManager().restartLoader(0, null, cursorAdapterLoader);
    }

    @Override
    public void destroyLoader() {
        getLoaderManager().destroyLoader(0);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (EventsHandler.inSync) {
            showProgressBar();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        hideProgressBar();
    }

    public void setFilterHostId(String filterHostId){
        this.filterHostId = filterHostId;
    }

    public void setFilterStorageDomainId(String filterStorageDomainId){
        this.filterStorageDomainId = filterStorageDomainId;
    }

    public void updateFilterClusterIdTo(String filterClusterId) {
        this.filterClusterId = filterClusterId;
        page = 1;
        list.setSelectionAfterHeaderView();
        endlessScrollListener.resetListener();
        restartLoader();
    }

    public void setFilterVmId(String filterVmId) {
        this.filterVmId = filterVmId;
    }

    public void loadMoreData(int page) {
        this.page = page;
        restartLoader();
    }

    @Receiver(actions = Broadcasts.EVENTS_IN_SYNC, registerAt = Receiver.RegisterAt.OnResumeOnPause)
    void eventsSyncing(@Receiver.Extra((Broadcasts.Extras.SYNCING)) boolean syncing) {
        setRefreshing(syncing);
    }

    @Background
    @Override
    public void onRefresh() {
//        eventsHandler.deleteEvents();
        eventsHandler.updateEvents(true);
    }

    @Override
    protected SwipeRefreshLayout getSwipeRefreshLayout() {
        return swipeEventsContainer;
    }
}
