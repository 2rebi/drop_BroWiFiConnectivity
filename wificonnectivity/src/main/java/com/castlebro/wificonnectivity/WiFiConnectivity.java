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
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.Nullable;
import android.text.Html;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.sql.Time;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.TimerTask;

/**
 * ToDo
 * Wifi auto re scan
 */
public class WiFiConnectivity extends Service implements IWiFiConnectivity {
    static Map<String, WeakReference<Context>> ContextServeObject = new HashMap<>();
    static Map<String, WiFiScanListener> ScanListenerServeObject = new HashMap<>();
    static Map<String, WiFiStateListener> StateListenerServeObject = new HashMap<>();
    static Map<String, WiFiConnectListener> ConnectListenerServeObject = new HashMap<>();

    public static final int REQUEST_PERMISSION_CODE = 102939;

    public static final short WIFI_UNSPECIFIED = -1;
    public static final short WIFI_DISABLE = 0;
    public static final short WIFI_ENABLE = 1;
    public static final short WIFI_DEFAULT_DIALOG = 2;

    static final String REQUESTER_CONTEXT = "request_context";
    static final String WIFI_STATE = "wifi_state";
    static final String REQUEST_PERMISSION = "request_permission";
    static final String START_SCAN = "start_scan";
    static final String CONNECT_WIFI = "connect_wifi";

    private String mRequesterHashCode;
    private short mWifiState = WIFI_UNSPECIFIED;
    private boolean mWasRequestPermisson = false;
    private boolean mIsPermissionRequest = false;
    private boolean mIsStartScan = false;

    private WeakReference<Context> mRequester;
    private WiFiScanListener mScanListener;
    private WiFiStateListener mStateListener;
    private WiFiConnectListener mConnectListener;
    private WifiManager mWifiManager;
    private ConnectivityManager mConnectivityManager;

    private NetworkInfo.DetailedState mOldState = null;
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
        mOldState = null;
        mConnectWifi = null;

        mRequesterHashCode = intent.getStringExtra(REQUESTER_CONTEXT);
        mWifiState = intent.getShortExtra(WIFI_STATE, WIFI_UNSPECIFIED);
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

        setEnableWifi(mWifiState);
        if (mWifiState != WIFI_DEFAULT_DIALOG)
            requestPermission();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(mReceiver, intentFilter);

        if (wifi != null) {
            connect(wifi);
        }
        else{
            startScan();
        }

        return Service.START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        mRequester = null;
        mOldState = null;
        //mScanListener = null;
        //mStateListener = null;
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

        if (!isValid()) {
            stopSelf();
            return false;
        }

        WifiInfo info = mWifiManager.getConnectionInfo();
        String infoSSID = (info.getSSID() != null) ? (info.getSSID().replaceAll("\"", "")) : ("");
        String infoBSSID = (info.getBSSID() != null) ? (info.getBSSID().replaceAll("\"", "")) : ("");
        if (infoSSID.equals(wifi.getSSID()) && infoBSSID.equals(wifi.getBSSID())) {
            return true;
        }
        else
        {
            mWifiManager.disconnect();
        }

        if (mWifiManager.isWifiEnabled()) {
            int netId = BroWiFiConnectivity.getConfiguredNetworkId(this, wifi);
            if (netId == -1) netId = mWifiManager.addNetwork(wifi.getWifiConfiguration());

            if ((netId != -1) &&
                    mWifiManager.enableNetwork(netId, true) &&
                    mWifiManager.reconnect())
            {
                mConnectWifi = wifi;
                mOldState = null;
                return true;
            }
        }
        mConnectWifi = null;
        return false;
    }

    @Override
    public void setEnableWifi(int type) {
        if (type == WIFI_ENABLE)
        {
            mWifiManager.setWifiEnabled(true);
        }
        else if (type == WIFI_DISABLE)
        {
            mWifiManager.setWifiEnabled(false);
        }
        else if (type == WIFI_DEFAULT_DIALOG) showEnableWifiDialog();
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
                            setEnableWifi(WIFI_ENABLE);
                            requestPermission();

                        }).
                        setNegativeButton(R.string.wifi_enable_deny, (dialog, which) ->
                        {
                            setEnableWifi(WIFI_DISABLE);
                            requestPermission();
                        }).
                        show());
    }

    private void requestPermission() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (mIsPermissionRequest && !mWasRequestPermisson &&
                    (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
                Activity activity = (Activity) mRequester.get();
                activity.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_PERMISSION_CODE);
            }
        }
        mWasRequestPermisson = true;
    }

    private void startScan() {
        /***
         * TODO
         * startScan Deprecated On API 28 Level
         */
        if (mIsStartScan)
            mWifiManager.startScan();
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

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!isValid()) {
                stopSelf();
                return;
            }
            String action = intent.getAction();
            if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
                int state = mWifiManager.getWifiState();
                if (mStateListener != null)
                    mStateListener.onStateChange(state);
            }
            if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action) && (mScanListener != null)) {
                if (!mScanListener.
                        ScanResults(WiFi.parseCollection(
                                WiFiConnectivity.this,
                                mWifiManager.getScanResults()))) {
                    startScan();
                }
            }
            if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action) &&
                    (mConnectWifi != null) &&
                    (mConnectListener != null)) {
                NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (ConnectivityManager.TYPE_WIFI == info.getType()) {
                    NetworkInfo.DetailedState state = info.getDetailedState();
                    mConnectListener.onDebug(state);
                    //Log.d("TEST LOG" , state.toString());
                    if (((mOldState == NetworkInfo.DetailedState.AUTHENTICATING) || (mOldState == NetworkInfo.DetailedState.CONNECTING)) &&
                            (state == NetworkInfo.DetailedState.DISCONNECTED))
                    {
                        mConnectListener.onConnectionFailed(WiFiConnectivity.this, mConnectWifi);
                        mConnectWifi = null;
                    }
                    else if ((mOldState == NetworkInfo.DetailedState.CONNECTED) &&
                            (state == NetworkInfo.DetailedState.DISCONNECTED))
                    {
                        mConnectListener.onDisconnected(WiFiConnectivity.this, mConnectWifi);
                        mConnectWifi = null;
                    }
                    else if (state == NetworkInfo.DetailedState.CONNECTED)
                    {
                        String infoSSID = mWifiManager.getConnectionInfo().getSSID().replaceAll("\"", "");
                        if (mConnectWifi.getSSID().equals(infoSSID))
                        {
                            mRealityConnect.checkConnected();
                        }
                        else
                        {
                            mOldState = null;
                            return;
                        }
                    }
                    mOldState = state;
                }
            }
        }
    };


    private RealityConnectHanlder mRealityConnect = new RealityConnectHanlder(this);
    private static class RealityConnectHanlder extends Handler
    {
        private WiFiConnectivity mRequester;
        public RealityConnectHanlder(WiFiConnectivity Requester)
        {
            mRequester = Requester;
        }
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if ((msg.what == 1) && (mRequester.mConnectListener != null))
                mRequester.mConnectListener.onConnected(mRequester, mRequester.mConnectWifi);
        }

        public void checkConnected()
        {
            removeMessages(1);
            sendEmptyMessageDelayed(1, 500);
        }
    }
}
