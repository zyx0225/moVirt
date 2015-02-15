package org.ovirt.mobile.movirt.mqtt;

import android.app.Service;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;

import org.androidannotations.annotations.AfterInject;
import org.androidannotations.annotations.Bean;
import org.androidannotations.annotations.EService;
import org.androidannotations.annotations.Receiver;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.ovirt.mobile.movirt.Broadcasts;
import org.ovirt.mobile.movirt.MoVirtApp;

@EService
public class MqttService extends Service implements MqttCallback {

    public static final String TAG = MqttService.class.getSimpleName();

    private static final String CLIENT_ID = Settings.Secure.ANDROID_ID;
    private static final String BROKER = "tcp://10.0.2.2:1883";
    private static final int KEEPALIVE_SECONDS = 15 * 60;

    private static final int QOS_GUARANTEE = 2;
    private static final String DOCTOR_TOPIC = "doctor/status";
    private static final String ALL = "/#";
    private static final String VMS_TOPIC = "vms";
    private static final String EVENTS_TOPIC = "events";

    private static final String STATUS_ALIVE = "ALIVE";
    private static final String STATUS_DEAD = "DEAD";

    private MqttAsyncClient client;
    private volatile boolean connected;

    @Bean
    AlarmPingSender alarmPingSender;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @AfterInject
    void init() {
        connect();
    }

    private synchronized void connect() {
        try {
            this.client = new MqttAsyncClient(BROKER, CLIENT_ID, new MemoryPersistence(), alarmPingSender);
            this.client.setCallback(this);
            this.client.connect(getConnectionOptions(), null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken iMqttToken) {
                    try {
                        connected = true;
                        subscribe();
                        sendBroadcast(new Intent(Broadcasts.MQTT_CONNECTED));
                        Log.i(TAG, "MQTT Connection successful!");
                    } catch (MqttException e) {
                        Log.e(TAG, "Error subscribing to messages!", e);
                    }
                }

                @Override
                public void onFailure(IMqttToken iMqttToken, Throwable e) {
                    Log.e(TAG, "Error connecting to MQTT broker at: " + BROKER, e);
                }
            });
        } catch (MqttException e) {
            Log.e(TAG, "Error connecting to MQTT broker at: " + BROKER, e);
        }
    }

    private void subscribe() throws MqttException {
        client.subscribe(DOCTOR_TOPIC, QOS_GUARANTEE);
        client.subscribe(VMS_TOPIC + ALL, QOS_GUARANTEE);
        client.subscribe(EVENTS_TOPIC + ALL, QOS_GUARANTEE);
    }

    private MqttConnectOptions getConnectionOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setKeepAliveInterval(KEEPALIVE_SECONDS);
        return options;
    }

    @Override
    public void connectionLost(Throwable throwable) {
        Log.i(TAG, "MQTT Connection Lost!");
        connected = false;
        sendBroadcast(new Intent(Broadcasts.MQTT_DISCONNECTED));
        Log.i(TAG, "Trying to reconnect ...");
        connect();
    }

    @Override
    public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
        if (DOCTOR_TOPIC.equals(topic)) {
            updateDoctorStatus(new String(mqttMessage.getPayload()));
        } else if (topic.startsWith(VMS_TOPIC)) {
            sendBroadcast(new Intent(Broadcasts.VMS_UPDATED));
            Log.i(TAG, "VMS UPDATED!");
        } else if (topic.startsWith(EVENTS_TOPIC)) {
            sendBroadcast(new Intent(Broadcasts.EVENTS_UPDATED));
            Log.i(TAG, "EVENTS UPDATED!");
        }
    }

    private void updateDoctorStatus(String status) {
        if (STATUS_ALIVE.equals(status)) {
            // doctor lives!
            Log.i(TAG, "DOCTOR LIVES!");
        } else if (STATUS_DEAD.equals(status)) {
            Log.i(TAG, "DOCTOR IS DEAD!");
        } else {
            Log.w(TAG, "Received unknown doctor status: " + status);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {

    }

    @Receiver(actions = ConnectivityManager.CONNECTIVITY_ACTION)
    void connectivityChanged(@Receiver.Extra(ConnectivityManager.EXTRA_NO_CONNECTIVITY) boolean noConnectivity) {
        // try to reconnect if we are not connected and there is at least come connectivity
        if (!noConnectivity && !connected) {
            connect();
        }
    }

    @Receiver(actions = {Broadcasts.REFRESH_TRIGGERED, Broadcasts.IN_SYNC})
    void refresh() {
        // on explicit refresh try to reconnect
        if (!connected) {
            connect();
        }
    }
}
