package com.castlebro.wificonnectivity;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Application;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityManagerCompat;
import android.text.Html;
import android.util.Log;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.net.wifi.WifiManager.EXTRA_NEW_STATE;

/**
 * ToDo
 * Wifi auto re scan
 */
public class WiFiConnectivity extends Service implements IWiFiConnectivity, Application.ActivityLifecycleCallbacks {
    private static final String TAG = WiFiConnectivity.class.getSimpleName();
    static Map<String, WeakReference<Context>> ContextServeObject = new HashMap<>();
    static Map<String, WiFiScanListener> ScanListenerServeObject = new HashMap<>();
    static Map<String, WiFiStateListener> StateListenerServeObject = new HashMap<>();
    static Map<String, WiFiConnectListener> ConnectListenerServeObject = new HashMap<>();

    public static final String FAILED_REASON_REQUEST_TIMEOUT = "failed_reason_request_timeout";
    public static final String FAILED_REASON_WRONG_PASSWORD = "failed_reason_lost_or_wrong_password"; //wrong_password";

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
        Log.d(TAG, "Start Service");
        if (context == null) throw new NullPointerException("context is null");

        Log.d(TAG, "Set Arguments");
        Log.d(TAG, "context = " + context.getClass().getSimpleName());
        Log.d(TAG, "scanListener = " + (scanListener == null ? "null" : String.valueOf(scanListener.hashCode())));
        Log.d(TAG, "stateListener = " + (stateListener == null ? "null" : String.valueOf(stateListener.hashCode())));
        Log.d(TAG, "connectListener = " + (connectListener == null ? "null" : String.valueOf(connectListener.hashCode())));

        String key = String.valueOf(context.hashCode());
        ContextServeObject.put(key, new WeakReference<>(context));
        ScanListenerServeObject.put(key, scanListener);
        StateListenerServeObject.put(key, stateListener);
        ConnectListenerServeObject.put(key, connectListener);

        context.startService(intent);
        Log.d(TAG, "context called method \"startService\"");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.d(TAG, "onStartCommand");

        Log.d(TAG, "Application call method \"registerActivityLifecycleCallbacks\"");
        getApplication().registerActivityLifecycleCallbacks(this);

        initializeConnection();


        mRequesterHashCode = intent.getStringExtra(REQUESTER_CONTEXT);
        mWifiState = intent.getShortExtra(WIFI_STATE, WIFI_STATE_UNSPECIFIED);
        mIsPermissionRequest = intent.getBooleanExtra(REQUEST_PERMISSION, false);
        mIsStartScan = intent.getBooleanExtra(START_SCAN, false);
        WiFi wifi = intent.getParcelableExtra(CONNECT_WIFI);
        Log.d(TAG, "Get Arguments");
        Log.d(TAG, "mRequesterHashCode = " + mRequesterHashCode);
        Log.d(TAG, "mWifiState = " + mWifiState);
        Log.d(TAG, "mIsPermissionRequest = " + mIsPermissionRequest);
        Log.d(TAG, "mIsStartScan = " + mIsStartScan);
        Log.d(TAG, "Request Connect WiFi = " + (wifi == null ? "null" : wifi.toString()));

        mRequester = ContextServeObject.remove(mRequesterHashCode);
        mScanListener = ScanListenerServeObject.remove(mRequesterHashCode);
        mStateListener = StateListenerServeObject.remove(mRequesterHashCode);
        mConnectListener = ConnectListenerServeObject.remove(mRequesterHashCode);
        Log.d(TAG, "Get Arguments (Context, Listener)");
        Log.d(TAG, "mRequester = " + (mRequester.get() == null ? "null" : mRequester.get().getClass().getSimpleName()));
        Log.d(TAG, "mScanListener = " + (mScanListener == null ? "null" : String.valueOf(mScanListener.hashCode())));
        Log.d(TAG, "mStateListener = " + (mStateListener == null ? "null" : String.valueOf(mStateListener.hashCode())));
        Log.d(TAG, "mConnectListener = " + (mConnectListener == null ? "null" : String.valueOf(mConnectListener.hashCode())));

        mWifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        mConnectivityManager = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        Log.d(TAG, "getSystemService \"Context.WIFI_SERVICE\", \"Context.CONNECTIVITY_SERVICE\"");
        Log.d(TAG, "WifiManager is " + (mWifiManager == null ? "null, not supported" : "supported"));
        Log.d(TAG, "ConnectivityManager is " + (mConnectivityManager == null ? "null, not supported" : "supported"));

        if (!isValid()) {
            Log.d(TAG, "WiFiConnectivity is invalid, I'm stop myself goodbye");
            stopSelf();

            Log.d(TAG, "return Service.START_NOT_STICKY");
            return Service.START_NOT_STICKY;
        }

        IntentFilter intentFilter = new IntentFilter();

        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        intentFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);

        //Todo
        //Maybe Deprecated on API 28 Level
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);

        registerReceiver(mReceiver, intentFilter);

        setEnableWifi(mWifiState);
        if (mWifiState != WIFI_STATE_DEFAULT_DIALOG)
            requestPermission();

        startScan();
        if (wifi != null)
            connect(wifi);

        Log.d(TAG, "return Service.START_NOT_STICKY");
        return Service.START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mReceiver);
        getApplication().unregisterActivityLifecycleCallbacks(this);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public boolean connect(WiFi wifi) {
        Log.d(TAG, "connect wifi / " + wifi.toString());
        if ((mWifiConnection >= WIFI_REQUEST_CONNECT) && (mConnectWifi != null))
        {
            Log.d(TAG, "Already request connect wifi / " + mConnectWifi.toString());
            Log.d(TAG, "return true");
            return true;
        }
        if (!isValid()) {
            Log.d(TAG, "WiFiConnectivity is invalid, I'm stop myself goodbye");
            stopSelf();

            Log.d(TAG, "return false");
            return false;
        }

        Log.d(TAG, "Check compare connected wifi and request wifi");
        WifiInfo info = mWifiManager.getConnectionInfo();
        String infoSSID = (info.getSSID() != null) ? (info.getSSID().replaceAll("\"", "")) : ("");
        String infoBSSID = (info.getBSSID() != null) ? (info.getBSSID().replaceAll("\"", "")) : ("");

        Log.d(TAG, "Connected wifi SSID / " + infoSSID);
        Log.d(TAG, "Connected wifi BSSID / " + infoBSSID);
        if (infoSSID.equals(wifi.getSSID()) && infoBSSID.equals(wifi.getBSSID())) {
            Log.d(TAG, "Same wifi information");
            short state = convertWifiConnection(mConnectivityManager.getActiveNetworkInfo(), null);
            if (state == WIFI_CONNECTED)
            {
                Log.d(TAG, "Connection state change to WIFI_CONNECTED");
                mConnectWifi = wifi;
                mWifiConnection = WIFI_CONNECTED;
                Log.d(TAG, "connected callback");
                mConnectionCallback.connected();

                Log.d(TAG, "return true");
                return true;
            }
        }

        Log.d(TAG, "Check wifi enabled");
        if (mWifiManager.isWifiEnabled()) {
            Log.d(TAG, "Wifi enabled");

            Log.d(TAG, "Check wifi configuredNetwork");
            int netId = BroWiFiConnectivity.getConfiguredNetworkId(mRequester.get(), wifi);

            if (netId == -1)
            {
                Log.d(TAG, "Wifi not configuredNetwork");
                Log.d(TAG, "addNetwork WifiConfiguration / " + wifi.getWifiConfiguration().toString());
                netId = mWifiManager.addNetwork(wifi.getWifiConfiguration());
            }

            Log.d(TAG, "Wifi net ID / " + netId + " reconnect");
            Log.d(TAG, "Wifi reconnect");
            if ((netId != -1) &&
                    mWifiManager.enableNetwork(netId, true) &&
                    mWifiManager.reconnect()) {

                Log.d(TAG, "Connection state change to WIFI_REQUEST_CONNECT");
                mConnectWifi = wifi;
                mWifiConnection = WIFI_REQUEST_CONNECT;

                Log.d(TAG, "return true");
                return true;
            }
        }

        Log.d(TAG, "Wifi disabled");
        initializeConnection();
        Log.d(TAG, "return false");
        return false;
    }

    @Override
    public void setEnableWifi(int type) {
        Log.d(TAG, "EnableWifi type (UNSPECIFIED / -1, DISABLE / 0, ENABLE / 1, SHOW_DEFAULT_DIALOG / 2) = " + type);
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
        {
            Log.d(TAG, "start Scan");
            mWifiManager.startScan();
        }
    }

    private void initializeConnection() {
        Log.d(TAG, "Initialize Connection Info");
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
        if ((nInfo != null) && (nInfo.getType() == ConnectivityManager.TYPE_WIFI)) {
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
                Log.d(TAG, "WiFiConnectivity is invalid, I'm stop myself goodbye");
                stopSelf();

                return;
            }
            String action = intent.getAction();
            if (action == null) return;

            Log.d(TAG, "BroadcastReceive Action / " + action);
            switch (action) {
                case WifiManager.WIFI_STATE_CHANGED_ACTION:
                    Log.d(TAG, "WIFI_STATE_DISABLING / 0, WIFI_STATE_DISABLED / 1, WIFI_STATE_ENABLING / 2, WIFI_STATE_ENABLED / 3, WIFI_STATE_UNKNOWN / 4");
                    Log.d(TAG, String.valueOf(mWifiManager.getWifiState()));
                    if (mStateListener != null)
                        Log.d(TAG, "onStateChange callback");
                        mStateListener.onStateChange(mWifiManager.getWifiState());
                    break;
                case WifiManager.SCAN_RESULTS_AVAILABLE_ACTION:
                    List<ScanResult> results = mWifiManager.getScanResults();
                    Log.d(TAG, "getScanResults Size / " + String.valueOf(results.size()));
                    if (mScanListener != null) {
                        Log.d(TAG, "ScanResults callback");
                        if (!mScanListener.ScanResults(
                                WiFi.parseCollection(WiFiConnectivity.this, results)))
                            startScan();
                    }
                    break;
                case ConnectivityManager.CONNECTIVITY_ACTION:
                    if (mWifiConnection != WIFI_CONNECTED) break;
                    Log.d(TAG, "Connected Success");
                    if ((mConnectivityManager.getActiveNetworkInfo() != null) &&
                            (mConnectivityManager.getActiveNetworkInfo().getType() == ConnectivityManager.TYPE_WIFI))
                    {
                        Log.d(TAG, "Active NetworkInfo type is TYPE_WIFI");
                        Log.d(TAG, "connected callback");
                        mConnectionCallback.connected();
                    }
                    else
                    {
                        Log.d(TAG, "Active NetworkInfo is null or not TYPE_WIFI");
                    }
                    break;
                case WifiManager.SUPPLICANT_STATE_CHANGED_ACTION:
                    SupplicantState sState = intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);
                    Log.d(TAG, "SupplicantState / " + sState.toString());
                    break;
                case WifiManager.NETWORK_STATE_CHANGED_ACTION:
                    WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
                    NetworkInfo nInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);

                    short state = convertWifiConnection(nInfo, null);

                    Log.d(TAG, "WifiInfo = " + wifiInfo.toString());
                    Log.d(TAG, "NetworkInfo = " + nInfo.toString());
                    if (mWifiConnection == WIFI_UNKNOWN)
                    {
                        Log.d(TAG, "mWifiConnection / WIFI_UNKNOWN");
                        Log.d(TAG, "Not Request connect");
                        break;
                    }
                    if (mWifiConnection != WIFI_CONNECTED)
                    {
                        String bssid = wifiInfo.getBSSID() != null ? wifiInfo.getBSSID().replaceAll("\"", "") : "";
                        if (!mConnectWifi.getBSSID().equals(bssid)) {
                            Log.d(TAG, "setTimeout 5000ms");
                            mConnectionCallback.setTimeout();
                            break;
                        } else {
                            Log.d(TAG, "removeTimeout");
                            mConnectionCallback.removeTimeout();
                        }
                    }

                    switch (mWifiConnection) {
                        case WIFI_DISCONNECTED:
                            if (state == WIFI_DISCONNECTED) {
                                Log.d(TAG, "mWifiConnection / WIFI_DISCONNECTED");
                                Log.d(TAG, "Disconnected callback");
                                mConnectionCallback.disconnected();
                            }
                            break;
                        case WIFI_REQUEST_CONNECT:
                            Log.d(TAG, "OldState / WIFI_REQUEST_CONNECT");
                            if (state == WIFI_CONNECTING) {
                                Log.d(TAG, "NewState / WIFI_CONNECTING");
                                mWifiConnection = WIFI_CONNECTING;
                            } else if (state == WIFI_DISCONNECTED) {
                                Log.d(TAG, "NewState / WIFI_DISCONNECTED");
                                Log.d(TAG, "ConnectFailed callback");
                                mConnectionCallback.wrongPassword();
                            } else {
                                Log.d(TAG, "NewState / WIFI_REQUEST_CONNECT");
                            }
                            break;
                        case WIFI_CONNECTING:
                            Log.d(TAG, "OldState / WIFI_CONNECTING");
                            if (state == WIFI_CONNECTED) {
                                Log.d(TAG, "NewState / WIFI_CONNECTED");
                                mWifiConnection = WIFI_CONNECTED;
                            } else if (state == WIFI_DISCONNECTED) {
                                Log.d(TAG, "NewState / WIFI_DISCONNECTED");
                                Log.d(TAG, "ConnectFailed callback");
                                mConnectionCallback.wrongPassword();
                            }
                            break;
                        case WIFI_CONNECTED:
                            Log.d(TAG, "OldState / WIFI_CONNECTED");
                            if (state == WIFI_DISCONNECTED) {
                                Log.d(TAG, "NewState / WIFI_CONNECTED");
                                mWifiConnection = WIFI_DISCONNECTED;
                                Log.d(TAG, "Disconnected callback");
                                mConnectionCallback.disconnected();
                            }
                            break;
                    }
                    break;
            }


            Log.d(TAG, "WifiManager Detail Information");
            Log.d(TAG, "Wifi State / " + mWifiManager.getWifiState());
            Log.d(TAG, "Wifi ConnectionInfo / " + (mWifiManager.getConnectionInfo() == null ? "null" : mWifiManager.getConnectionInfo().toString()));
            Log.d(TAG, "Wifi DhcpInfo / " + (mWifiManager.getDhcpInfo() == null ? "null" : mWifiManager.getDhcpInfo().toString()));

            Log.d(TAG, "ConnectivityManager Detail Information");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) Log.d(TAG, "DefaultNetworkActive / " + mConnectivityManager.isDefaultNetworkActive());
            else Log.d(TAG, "DefaultNetworkActive / " + "Method call requires API level 21 (LOLLIPOP)");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) Log.d(TAG, "ActiveNetworkMetered / " + mConnectivityManager.isActiveNetworkMetered());
            else Log.d(TAG, "ActiveNetworkMetered / " + "Method call requires API level 16 (JELLY_BEAN)");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            {
                Log.d(TAG, "RESTRICT_BACKGROUND_STATUS$ DISABLED / 1, WHITELISTED / 2, ENABLED / 3");
                Log.d(TAG, "RestrictBackgroundStatus / " + mConnectivityManager.getRestrictBackgroundStatus());
            }
            else Log.d(TAG, "RestrictBackgroundStatus / " + "Method call requires API level 24 (N)");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            {
                Log.d(TAG, "DefaultProxy / " + (mConnectivityManager.getDefaultProxy() == null ? "null" : mConnectivityManager.getDefaultProxy().toString()));
                Log.d(TAG, "BoundNetworkForProcess / " + (mConnectivityManager.getBoundNetworkForProcess() == null ? "null" : mConnectivityManager.getBoundNetworkForProcess().toString()));
                Log.d(TAG, "ActiveNetwork / " + (mConnectivityManager.getActiveNetwork() == null ? "null" : mConnectivityManager.getActiveNetwork().toString()));
            } else {
                Log.d(TAG, "DefaultProxy, BoundNetworkForProcess, ActiveNetwork / " + "Method call requires API level 23 (M)");
            }
            Log.d(TAG, "ActiveNetworkInfo / " + (mConnectivityManager.getActiveNetworkInfo() == null ? "null" : mConnectivityManager.getActiveNetworkInfo().toString()));
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
                    requester.initializeConnection();
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
                requester.initializeConnection();
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

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {

    }

    @Override
    public void onActivityStarted(Activity activity) {

    }

    @Override
    public void onActivityResumed(Activity activity) {

    }

    @Override
    public void onActivityPaused(Activity activity) {

    }

    @Override
    public void onActivityStopped(Activity activity) {

    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        if ((mRequester.get() == null) || (mRequester.get().hashCode() == activity.hashCode())) stopSelf();
    }
}