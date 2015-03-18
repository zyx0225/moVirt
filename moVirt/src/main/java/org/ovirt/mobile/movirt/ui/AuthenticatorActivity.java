package org.ovirt.mobile.movirt.ui;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Bean;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.Receiver;
import org.androidannotations.annotations.SystemService;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import org.ovirt.mobile.movirt.Broadcasts;
import org.ovirt.mobile.movirt.R;
import org.ovirt.mobile.movirt.auth.MovirtAuthenticator;
import org.ovirt.mobile.movirt.model.CaCert;
import org.ovirt.mobile.movirt.provider.OVirtContract;
import org.ovirt.mobile.movirt.provider.ProviderFacade;
import org.ovirt.mobile.movirt.rest.NullHostnameVerifier;
import org.ovirt.mobile.movirt.rest.OVirtClient;
import org.ovirt.mobile.movirt.sync.EventsHandler;
import org.ovirt.mobile.movirt.sync.SyncUtils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

@EActivity(R.layout.authenticator_activity)
public class AuthenticatorActivity extends AccountAuthenticatorActivity {

    @Bean
    NullHostnameVerifier verifier;

    @SystemService
    AccountManager accountManager;

    @Bean
    OVirtClient client;

    @ViewById
    EditText txtEndpoint;

    @ViewById
    EditText txtUsername;

    @ViewById
    EditText txtPassword;

    @ViewById
    CheckBox chkAdminPriv;

    @ViewById
    CheckBox chkDisableHttps;

    @ViewById
    CheckBox enforceHttpBasicAuth;

    @ViewById
    ProgressBar authProgress;

    @Bean
    MovirtAuthenticator authenticator;

    @Bean
    SyncUtils syncUtils;

    @Bean
    EventsHandler eventsHandler;

    @Bean
    ProviderFacade providerFacade;

    Certificate ca = null;

    @AfterViews
    void init() {
        txtEndpoint.setText(authenticator.getApiUrl());
        txtUsername.setText(authenticator.getUserName());
        txtPassword.setText(authenticator.getPassword());

        chkAdminPriv.setChecked(authenticator.hasAdminPermissions());
        chkDisableHttps.setChecked(authenticator.disableHttps());
        enforceHttpBasicAuth.setChecked(authenticator.enforceBasicAuth());

    }

    @Click(R.id.btnImportCaCrt)
    void importCaCrt() {
        downloadCa();
    }

    @Background
    void downloadCa() {
        String endpoint = txtEndpoint.getText().toString();
        // todo open a dialog showing the path precofigured properly but changable
        URL url = null;
        try {
            url = new URL(endpoint);
            url = new URL(url.getProtocol() + "://" + url.getAuthority() + "/ca.crt");
        } catch (MalformedURLException e) {
            showToast("The endpoint is not a valid URL");
            return;
        }

        CertificateFactory cf = null;
        try {
            cf = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            showToast("Problem getting the certificate factory: " + e.getMessage());
            return;
        }

        InputStream caInput = null;

        SSLSocketFactory properSocketFactory = null;
        try {
            URLConnection connection = url.openConnection();
            if (connection instanceof HttpsURLConnection) {
                // do not check the crt since we first need to download it from a not yet trusted source
                properSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory();
                TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new java.security.cert.X509Certificate[] {};
                    }

                    public void checkClientTrusted(X509Certificate[] chain,
                                                   String authType) throws CertificateException {
                    }

                    public void checkServerTrusted(X509Certificate[] chain,
                                                   String authType) throws CertificateException {
                    }
                }
                };

                // Install the all-trusting trust manager
                try {
                    SSLContext sc = SSLContext.getInstance("TLS");
                    sc.init(null, trustAllCerts, new java.security.SecureRandom());
                    HttpsURLConnection
                            .setDefaultSSLSocketFactory(sc.getSocketFactory());
                } catch (Exception e) {
                    e.printStackTrace();
                }

                ((HttpsURLConnection) connection).setHostnameVerifier(verifier);
            }

            caInput = new BufferedInputStream(url.openStream());
        } catch (IOException e) {
            showToast("Error loading certificate: " + e.getMessage());
            return;
        }

        Certificate ca = null;
        try {
            ca = cf.generateCertificate(caInput);
            // todo show it in dialog and proceed only if agreed
            okImportCa(ca);
            System.out.println("ca=" + ((X509Certificate) ca).getSubjectDN());
        } catch (CertificateException e) {
            showToast("Error CA generation: " + e.getMessage());
            return;
        } finally {
            try {
                caInput.close();
                if (properSocketFactory != null) {
                    HttpsURLConnection.setDefaultSSLSocketFactory(properSocketFactory);
                }
            } catch (IOException e) {
                // really nothing to do about this one...
            }
        }
    }

    void okImportCa(Certificate ca) {
        this.ca = ca;

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutput out = null;
        try {
            try {
                out = new ObjectOutputStream(bos);
                out.writeObject(ca);
                byte[] caAsBlob = bos.toByteArray();
                CaCert caCertEntity = new CaCert();
                // nvm, only support one
                caCertEntity.setId(1);
                caCertEntity.setContent(caAsBlob);
                providerFacade.deleteAll(OVirtContract.CaCert.CONTENT_URI);
                providerFacade.batch().insert(caCertEntity).apply();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException ex) {
                // ignore close exception
            }
            try {
                bos.close();
            } catch (IOException ex) {
                // ignore close exception
            }
        }
    }

    @Click(R.id.btnCreate)
    void addNew() {
        String endpoint = txtEndpoint.getText().toString();
        String username = txtUsername.getText().toString();
        String password = txtPassword.getText().toString();

        Boolean adminPriv = chkAdminPriv.isChecked();
        Boolean disableHttps = chkDisableHttps.isChecked();
        Boolean enforceHttpBasic = enforceHttpBasicAuth.isChecked();

        finishLogin(endpoint, username, password, adminPriv, disableHttps, enforceHttpBasic);
    }

    @Background
    void finishLogin(String apiUrl, String name, String password, Boolean hasAdminPermissions, Boolean disableHttps, Boolean enforceHttpBasic) {
        boolean endpointChanged = false;
        if (!TextUtils.equals(apiUrl, authenticator.getApiUrl()) ||
                !TextUtils.equals(name, authenticator.getUserName())) {
            endpointChanged = true;
        }

        if (accountManager.getAccountsByType(MovirtAuthenticator.ACCOUNT_TYPE).length == 0) {
            accountManager.addAccountExplicitly(MovirtAuthenticator.MOVIRT_ACCOUNT, password, null);
        }

        ContentResolver.setSyncAutomatically(MovirtAuthenticator.MOVIRT_ACCOUNT, OVirtContract.CONTENT_AUTHORITY, true);
        ContentResolver.setIsSyncable(MovirtAuthenticator.MOVIRT_ACCOUNT, OVirtContract.CONTENT_AUTHORITY, 1);

        setUserData(MovirtAuthenticator.MOVIRT_ACCOUNT, apiUrl, name, password, hasAdminPermissions, disableHttps, enforceHttpBasic);

        changeProgressVisibilityTo(View.VISIBLE);
        String token = "";
        boolean success = true;
        try {
            token = client.login(apiUrl, name, password, disableHttps, hasAdminPermissions);
        } catch (Exception e) {
            showToast("Error logging in: " + e.getMessage());
            success = false;
            return;
        } finally {
            changeProgressVisibilityTo(View.GONE);
            if (success) {
                if (TextUtils.isEmpty(token)) {
                    showToast("Error: the returned token is empty");
                    return;
                } else {
                    showToast("Login successful");
                    if (endpointChanged) {
                        // there is a different set of events and since we are counting only the increments, this ones are not needed anymore
                        eventsHandler.deleteEvents();
                    }

                    syncUtils.triggerRefresh();
                }
            } else {
                return;
            }
        }

        accountManager.setAuthToken(MovirtAuthenticator.MOVIRT_ACCOUNT, MovirtAuthenticator.AUTH_TOKEN_TYPE, token);

        final Intent intent = new Intent();
        intent.putExtra(AccountManager.KEY_ACCOUNT_NAME, MovirtAuthenticator.ACCOUNT_NAME);
        intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, MovirtAuthenticator.ACCOUNT_TYPE);
        intent.putExtra(AccountManager.KEY_AUTHTOKEN, token);

        setAccountAuthenticatorResult(intent.getExtras());
        setResult(RESULT_OK, intent);
        finish();
    }

    @UiThread
    void changeProgressVisibilityTo(int visibility) {
        authProgress.setVisibility(visibility);
    }

    @UiThread
    void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private void setUserData(Account account, String apiUrl, String name, String password, Boolean hasAdminPermissions, Boolean disableHttps, Boolean enforceHttpBasic) {
        accountManager.setUserData(account, MovirtAuthenticator.API_URL, apiUrl);
        accountManager.setUserData(account, MovirtAuthenticator.USER_NAME, name);
        accountManager.setUserData(account, MovirtAuthenticator.HAS_ADMIN_PERMISSIONS, Boolean.toString(hasAdminPermissions));
        accountManager.setUserData(account, MovirtAuthenticator.DISABLE_HTTPS, Boolean.toString(disableHttps));
        accountManager.setUserData(account, MovirtAuthenticator.ENFORCE_HTTP_BASIC, Boolean.toString(enforceHttpBasic));
        accountManager.getUserData(account, MovirtAuthenticator.API_URL);
        accountManager.setPassword(account, password);
    }

    @Receiver(actions = {Broadcasts.CONNECTION_FAILURE}, registerAt = Receiver.RegisterAt.OnResumeOnPause)
    void connectionFailure(@Receiver.Extra(Broadcasts.Extras.CONNECTION_FAILURE_REASON) String reason) {
        Toast.makeText(AuthenticatorActivity.this, R.string.rest_req_failed + " " + reason, Toast.LENGTH_LONG).show();
    }
}
