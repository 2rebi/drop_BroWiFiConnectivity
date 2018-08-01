package com.castlebro.wificonnectivity;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.Nullable;
import android.text.Html;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

import static android.net.wifi.WifiManager.EXTRA_NEW_STATE;

/**
 * ToDo
 * Wifi auto re scan
 */
public class WiFiConnectivity extends Service implements IWiFiConnectivity {
    static Map<String, WeakReference<Context>> ContextServeObject = new HashMap<>();
    static Map<String, WiFiScanListener> ScanListenerServeObject = new HashMap<>();
    static Map<String, WiFiStateListener> StateListenerServeObject = new HashMap<>();
    static Map<String, WiFiConnectListener> ConnectListenerServeObject = new HashMap<>();

    public static final String FAILED_REASON_REQUEST_TIMEOUT = "failed_reason_request_timeout";
    public static final String FAILED_REASON_WRONG_PASSWORD = "failed_reason_wrong_password";

    public static final int REQUEST_PERMISSION_CODE = 102939;

    public static final short WIFI_STATE_UNSPECIFIED = -1;
    public static final short WIFI_STATE_DISABLE = 0;
    public static final short WIFI_STATE_ENABLE = 1;
    public static final short WIFI_STATE_DEFAULT_DIALOG = 2;

    static final short WIFI_UNKNOWN = -1;
    static final short WIFI_DISCONNECTED = 0;
    static final short WIFI_REQUEST_CONNECT = (1 << 1);
    static final short WIFI_CONNECTING = (WIFI_REQUEST_CONNECT << 1);
    static final short WIFI_CONNECTED = (WIFI_CONNECTING << 1);

    static final String REQUESTER_CONTEXT = "request_context";
    static final String WIFI_STATE = "wifi_state";
    static final String REQUEST_PERMISSION = "request_permission";
    static final String START_SCAN = "start_scan";
    static final String CONNECT_WIFI = "connect_wifi";

    private String mRequesterHashCode;
    private short mWifiState = WIFI_STATE_UNSPECIFIED;
    private boolean mIsPermissionRequest = false;
    private boolean mIsStartScan = false;

    private WeakReference<Context> mRequester;
    private WiFiScanListener mScanListener;
    private WiFiStateListener mStateListener;
    private WiFiConnectListener mConnectListener;

    private WifiManager mWifiManager;
    private ConnectivityManager mConnectivityManager;

    private short mWifiConnection = WIFI_UNKNOWN;
    private WiFi mConnectWifi = null;

    static void StartService(Context context, Intent intent,
                             WiFiScanListener scanListener,
                             WiFiStateListener stateListener,
                             WiFiConnectListener connectListener) {
        if (context == null) throw new NullPointerException("context is null");
        String key = String.valueOf(context.hashCode());

        ContextServeObject.put(key, new WeakReference<>(context));
        ScanListenerServeObject.put(key, scanListener);
        StateListenerServeObject.put(key, stateListener);
        ConnectListenerServeObject.put(key, connectListener);

        context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        initalizeConnection();

        mRequesterHashCode = intent.getStringExtra(REQUESTER_CONTEXT);
        mWifiState = intent.getShortExtra(WIFI_STATE, WIFI_STATE_UNSPECIFIED);
        mIsPermissionRequest = intent.getBooleanExtra(REQUEST_PERMISSION, false);
        mIsStartScan = intent.getBooleanExtra(START_SCAN, false);
        WiFi wifi = intent.getParcelableExtra(CONNECT_WIFI);

        mRequester = ContextServeObject.remove(mRequesterHashCode);
        mScanListener = ScanListenerServeObject.remove(mRequesterHashCode);
        mStateListener = StateListenerServeObject.remove(mRequesterHashCode);
        mConnectListener = ConnectListenerServeObject.remove(mRequesterHashCode);

        mWifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        mConnectivityManager = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        if (!isValid()) {
            stopSelf();
            return Service.START_NOT_STICKY;
        }

        IntentFilter intentFilter = new IntentFilter();

        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        intentFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);

        registerReceiver(mReceiver, intentFilter);

        setEnableWifi(mWifiState);
        if (mWifiState != WIFI_STATE_DEFAULT_DIALOG)
            requestPermission();


        if (wifi != null) {
            connect(wifi);
        } else {
            startScan();
        }

        return Service.START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mReceiver);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public boolean connect(WiFi wifi) {
        if (mWifiConnection >= WIFI_REQUEST_CONNECT) return true;
        if (!isValid()) {
            stopSelf();
            return false;
        }

        WifiInfo info = mWifiManager.getConnectionInfo();
        String infoSSID = (info.getSSID() != null) ? (info.getSSID().replaceAll("\"", "")) : ("");
        String infoBSSID = (info.getBSSID() != null) ? (info.getBSSID().replaceAll("\"", "")) : ("");
        if (infoSSID.equals(wifi.getSSID()) && infoBSSID.equals(wifi.getBSSID())) {
            short state = convertWifiConnection(null, info.getSupplicantState());
            if (state == WIFI_CONNECTED)
            {
                mConnectWifi = wifi;
                mWifiConnection = WIFI_CONNECTED;
                mConnectionCallback.connected();
                return true;
            }
        }

        if (mWifiManager.isWifiEnabled()) {
            int netId = BroWiFiConnectivity.getConfiguredNetworkId(this, wifi);
            if (netId == -1) netId = mWifiManager.addNetwork(wifi.getWifiConfiguration());
            if ((netId != -1) &&
                    mWifiManager.enableNetwork(netId, true) &&
                    mWifiManager.reconnect()) {
                mConnectWifi = wifi;
                mWifiConnection = WIFI_REQUEST_CONNECT;
                return true;
            }
        }
        mConnectWifi = null;
        mWifiConnection = WIFI_UNKNOWN;
        return false;
    }

    @Override
    public void setEnableWifi(int type) {
        if (type == WIFI_STATE_ENABLE) {
            mWifiManager.setWifiEnabled(true);
        } else if (type == WIFI_STATE_DISABLE) {
            mWifiManager.setWifiEnabled(false);
        } else if (type == WIFI_STATE_DEFAULT_DIALOG) showEnableWifiDialog();
        switch (type) {
            case WIFI_STATE_ENABLE:
            case WIFI_STATE_DISABLE:
                requestPermission();
                mWifiManager.setWifiEnabled(type == WIFI_STATE_ENABLE);
                break;
            case WIFI_STATE_DEFAULT_DIALOG:
                break;
        }
    }

    private void showEnableWifiDialog() {
        if (!(mRequester.get() instanceof Activity)) return;
        if (mWifiManager.isWifiEnabled()) {
            requestPermission();
            return;
        }
        String appName = "<b>" + getPackageManager().
                getApplicationLabel(mRequester.get().getApplicationInfo()).toString() + "</b>";
        String message = getString(R.string.wifi_enable_desc, appName);
        //Todo set Theme
        new Handler(getMainLooper()).post(() ->
                new AlertDialog.Builder(mRequester.get()).
                        setMessage(Html.fromHtml(message)).
                        setPositiveButton(R.string.wifi_enable_allow, (dialog, which) ->
                        {
                            setEnableWifi(WIFI_STATE_ENABLE);
                        }).
                        setNegativeButton(R.string.wifi_enable_deny, (dialog, which) ->
                        {
                            setEnableWifi(WIFI_STATE_DISABLE);
                        }).
                        show());
    }

//    private boolean isApiLevelOver23()
//    {
//        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M);
//    }
//
//    private boolean isAllowPermission()
//    {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
//        {
//            return (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED);
//        }
//
//        return true;
//    }

    private void requestPermission() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                return;
            if (mIsPermissionRequest) {
                Activity activity = (Activity) mRequester.get();
                activity.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_PERMISSION_CODE);
            }
        }
    }

    private void startScan() {
        /***
         * TODO
         * startScan Deprecated On API 28 Level
         */
        if (mIsStartScan)
            mWifiManager.startScan();
    }

    private void initalizeConnection() {
        mConnectWifi = null;
        mWifiConnection = WIFI_UNKNOWN;
    }

    private boolean isSupported() {
        return (mWifiManager != null) && (mConnectivityManager != null);
    }

    private boolean hasRequester() {
        return mRequester != null && mRequester.get() != null;
    }

    private boolean isValid() {
        return isSupported() && hasRequester();
    }

    private short convertWifiConnection(NetworkInfo nInfo, SupplicantState sState) {
        if (nInfo != null) {
            NetworkInfo.State nState = nInfo.getState();
            if (NetworkInfo.State.DISCONNECTED == nState) return WIFI_DISCONNECTED;
            if (NetworkInfo.State.CONNECTING == nState) return WIFI_CONNECTING;
            if (NetworkInfo.State.CONNECTED == nState) return WIFI_CONNECTED;
        }
        if (sState != null) {
            if (SupplicantState.DISCONNECTED == sState) return WIFI_DISCONNECTED;
            if (SupplicantState.ASSOCIATING == sState) return WIFI_CONNECTING;
            if (SupplicantState.AUTHENTICATING == sState) return WIFI_CONNECTING;
            if (SupplicantState.COMPLETED == sState) return WIFI_CONNECTED;
            if (SupplicantState.ASSOCIATED == sState) return WIFI_CONNECTED;
        }
        return WIFI_STATE_UNSPECIFIED;
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!isValid()) {
                stopSelf();
                return;
            }
            String action = intent.getAction();
            if (action == null) return;
            switch (action) {
                case WifiManager.WIFI_STATE_CHANGED_ACTION:
                    if (mStateListener != null)
                        mStateListener.onStateChange(mWifiManager.getWifiState());
                    break;
                case WifiManager.SCAN_RESULTS_AVAILABLE_ACTION:
                    if (mScanListener != null) {
                        if (!mScanListener.ScanResults(
                                WiFi.parseCollection(WiFiConnectivity.this, mWifiManager.getScanResults())))
                            startScan();
                    }
                    break;
                case WifiManager.SUPPLICANT_STATE_CHANGED_ACTION:
                case WifiManager.NETWORK_STATE_CHANGED_ACTION:
                    if (mWifiConnection == WIFI_UNKNOWN) break;
                    WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
                    SupplicantState sState = intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);
                    NetworkInfo nInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);

                    if (wifiInfo.getSupplicantState() == SupplicantState.COMPLETED) sState = null;
                    short state = convertWifiConnection(nInfo, sState);
                    if (mWifiConnection != WIFI_CONNECTED)
                    {
                        String bssid = wifiInfo.getBSSID() != null ? wifiInfo.getBSSID().replaceAll("\"", "") : "";
                        if (!mConnectWifi.getBSSID().equals(bssid)) {
                            mConnectionCallback.setTimeout();
                            break;
                        } else {
                            mConnectionCallback.removeTimeout();
                        }
                    }

                    switch (mWifiConnection) {
                        case WIFI_DISCONNECTED:
                            if (state == WIFI_DISCONNECTED) {
                                mConnectionCallback.disconnected();
                            }
                            break;
                        case WIFI_REQUEST_CONNECT:
                            if (state == WIFI_CONNECTING) {
                                mWifiConnection = WIFI_CONNECTING;
                            } else if (state == WIFI_DISCONNECTED) {
                                mConnectionCallback.wrongPassword();
                            }
                            break;
                        case WIFI_CONNECTING:
                            if (state == WIFI_CONNECTED) {
                                mWifiConnection = WIFI_CONNECTED;
                                mConnectionCallback.connected();
                            } else if (state == WIFI_DISCONNECTED) {
                                mConnectionCallback.wrongPassword();
                            }
                            break;
                        case WIFI_CONNECTED:
                            if (state == WIFI_CONNECTED) {
                                mConnectionCallback.connected();
                            } else if (state == WIFI_DISCONNECTED) {
                                mWifiConnection = WIFI_DISCONNECTED;
                                mConnectionCallback.disconnected();
                            }
                            break;
                    }
                    break;
            }
        }
    };


    private ConnectionCallbackHanlder mConnectionCallback = new ConnectionCallbackHanlder(this);

    private static class ConnectionCallbackHanlder extends Handler {
        private static final long SEND_MSG_DELAY_DEFAULT = 500;
        private static final long SEND_MSG_DELAY_TIMEOUT = 5000;

        private static final short DISCONNECTED = 0;
        private static final short CONNECTED = 1;
        private static final short TIMEOUT = 2;
        private static final short WRONG_PASSWORD = 3;
        private WeakReference<WiFiConnectivity> mRequester;

        public ConnectionCallbackHanlder(WiFiConnectivity Requester) {
            mRequester = new WeakReference<>(Requester);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            WiFiConnectivity requester = mRequester.get();
            if (requester == null) return;
            String failedReason = null;
            switch (msg.what) {
                case DISCONNECTED:
                    requester.mConnectListener.onDisconnected(requester, requester.mConnectWifi);
                    requester.initalizeConnection();
                    break;
                case CONNECTED:
                    requester.mConnectListener.onConnected(requester, requester.mConnectWifi);
                    break;
                case TIMEOUT:
                    failedReason = FAILED_REASON_REQUEST_TIMEOUT;
                    break;
                case WRONG_PASSWORD:
                    failedReason = FAILED_REASON_WRONG_PASSWORD;
                    break;
            }
            if (failedReason != null) {
                requester.mConnectListener.onConnectionFailed(requester, requester.mConnectWifi, failedReason);
                requester.initalizeConnection();
            }
        }

        public void wrongPassword() {
            removeMessages(WRONG_PASSWORD);
            sendEmptyMessageDelayed(WRONG_PASSWORD, SEND_MSG_DELAY_DEFAULT);
        }

        public void setTimeout() {
            if (!hasMessages(TIMEOUT))
                sendEmptyMessageDelayed(TIMEOUT, SEND_MSG_DELAY_TIMEOUT);
        }

        public void removeTimeout() {
            removeMessages(TIMEOUT);
        }

        public void connected() {
            removeMessages(CONNECTED);
            sendEmptyMessageDelayed(CONNECTED, SEND_MSG_DELAY_DEFAULT);
        }

        public void disconnected() {
            removeMessages(DISCONNECTED);
            sendEmptyMessageDelayed(DISCONNECTED, SEND_MSG_DELAY_DEFAULT);
        }

//        private void clear()
//        {
//            removeMessages(CONNECTED);
//            removeMessages(TIMEOUT);
//            removeMessages(WRONG_PASSWORD);
//        }
    }
}