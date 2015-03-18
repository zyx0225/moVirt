package org.ovirt.mobile.movirt.rest;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import org.androidannotations.annotations.AfterInject;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Bean;
import org.androidannotations.annotations.EBean;
import org.androidannotations.annotations.RootContext;
import org.androidannotations.annotations.UiThread;
import org.ovirt.mobile.movirt.provider.ProviderFacade;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.net.HttpURLConnection;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

@EBean
public class OvirtSimpleClientHttpRequestFactory extends SimpleClientHttpRequestFactory {

    @Bean
    NullHostnameVerifier verifier;

    @RootContext
    Context rootContext;

    @Bean
    ProviderFacade providerFacade;

    SSLContext context;

    private static final String TAG = OvirtSimpleClientHttpRequestFactory.class.getSimpleName();
    private SSLSocketFactory properSocketFactory;
    private CertificateHandlingMode certificateHandlingMode;

    @AfterInject
    void initFactory() {
        properSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
    }

    public void setCertificateHandlingMode(CertificateHandlingMode certificateHandlingMode) {
        this.certificateHandlingMode = certificateHandlingMode;

        switch (certificateHandlingMode) {
            case TRUST_ONLY_KNOWN_CERTIFICATES:
                trustOnlyKnownCerts();
                break;
            case TRUST_IMPORTED_CERTIFICATE:
                trustImportedCert();
                break;
            case TRUST_ALL_CERTS:
                trustAllHosts();
                break;
        }
    }

    @Override
    protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
        Log.d(TAG, "Prepare Connection");
        if (connection instanceof HttpsURLConnection) {
            if (certificateHandlingMode == CertificateHandlingMode.TRUST_ALL_CERTS) {
                Log.d(TAG, "trusting all certificates");
                ((HttpsURLConnection) connection).setHostnameVerifier(verifier);
            } else if (certificateHandlingMode == CertificateHandlingMode.TRUST_IMPORTED_CERTIFICATE) {
//                Log.d(TAG, "trusting known certificates");
//                ((HttpsURLConnection) connection).setSSLSocketFactory(context.getSocketFactory());
            }

        }
        super.prepareConnection(connection, httpMethod);
    }

    /**
     * Trust every server - dont check for any certificate
     */
    private void trustAllHosts() {
        // Create a trust manager that does not validate certificate chains
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
    }

    @UiThread
    void showToast(String msg) {
        Toast.makeText(rootContext, msg, Toast.LENGTH_LONG).show();
    }

    @Background
    void trustImportedCert() {
        byte[] caCertBlob = providerFacade.getCaCert();
        if (caCertBlob.length <= 0) {
            incorrectCustomCertificate();
            return;
        }

        ByteArrayInputStream bis = new ByteArrayInputStream(caCertBlob);
        ObjectInput in = null;
        try {
            try {
                in = new ObjectInputStream(bis);
                Object o = in.readObject();
                if (o instanceof Certificate) {
                    installCustomCertificate((Certificate) o);
                } else {
                    incorrectCustomCertificate();
                }
            } catch (IOException e) {
                incorrectCustomCertificate();
            } catch (ClassNotFoundException e) {
                incorrectCustomCertificate();
            }

        } finally {
            try {
                bis.close();
            } catch (IOException ex) {
                // ignore close exception
            }
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException ex) {
                // ignore close exception
            }
        }
    }

    void installCustomCertificate(Certificate o) {
        try {
            String keyStoreType = KeyStore.getDefaultType();
            KeyStore keyStore = KeyStore.getInstance(keyStoreType);
            keyStore.load(null, null);
            keyStore.setCertificateEntry("ca", (Certificate) o);

            // Create a TrustManager that trusts the CAs in our KeyStore
            String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
            tmf.init(keyStore);

            // Create an SSLContext that uses our TrustManager
            context = SSLContext.getInstance("TLS");
            context.init(null, tmf.getTrustManagers(), null);
            HttpsURLConnection
                    .setDefaultSSLSocketFactory(context.getSocketFactory());
        } catch (Exception e) {
            showToast("Error installing certificate - trusting only known certificates" + e.getMessage());
            trustOnlyKnownCerts();
        }
    }

    private void incorrectCustomCertificate() {
        showToast("The CA is not correct - trusting only known certificates");
        trustOnlyKnownCerts();
    }

    /**
     * This method enables certificate checking.
     */
    private void trustOnlyKnownCerts() {
        try {
            HttpsURLConnection.setDefaultSSLSocketFactory(properSocketFactory);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static enum CertificateHandlingMode {
        // means only the certificates from the CAs already installed in the device are trusted
        TRUST_ONLY_KNOWN_CERTIFICATES,
        // means that the certificate manually imported from the specific place is trusted
        TRUST_IMPORTED_CERTIFICATE,
        // means no certificates are going to be checked at all
        TRUST_ALL_CERTS
    }
}