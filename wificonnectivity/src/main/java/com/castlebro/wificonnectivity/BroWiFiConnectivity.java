package com.castlebro.wificonnectivity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.List;

public class BroWiFiConnectivity {

    private static WeakReference<WiFiConnectivity> mRunService = null;

    private Context mContext;
    private boolean mIsRequestPermission = false;
    private boolean mIsStartScan = false;
    private short mWifiState = WiFiConnectivity.WIFI_STATE_UNSPECIFIED;

    private WiFiScanListener mScanListener;
    private WiFiStateListener mStateListener;
    private WiFiConnectListener mConnectListener;
    private WiFiDebugListener mDebugListener;

    private BroWiFiConnectivity() {
    }

    static void setRunService(WiFiConnectivity wiFiConnectivity) {
        mRunService = new WeakReference<>(wiFiConnectivity);
    }

    public static void forceStopService() {
        if (mRunService != null && mRunService.get() != null) {
            Log.d(WiFiConnectivity.class.getSimpleName(), "forceStopService");
            WiFiConnectivity connectivity = mRunService.get();
            connectivity.onRelease();
            connectivity.stopSelf();
            mRunService = null;
        }
    }

    public static void openWiFiSetting(Activity activity) {
        activity.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
    }

    public static boolean isAutoNetworkEnabledOpenSetting(Context context) {
        if (context instanceof Activity) {
            if (isAutoNetworkEnabled(context)) {
                openWiFiSetting((Activity) context);
                return true;
            }
        } else {
            throw new UnsupportedOperationException("context not activity");
        }
        return false;
    }

    public static boolean isAutoNetworkEnabled(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return Settings.Global.getInt(context.getContentResolver(), "wifi_watchdog_poor_network_test_enabled", 0) == 1;
        }
        return false;
    }

    public static BroWiFiConnectivity contact(Context context) {
        BroWiFiConnectivity wifi = new BroWiFiConnectivity();
        wifi.mContext = context;
        return wifi;
    }

    public static boolean disconnectCurrentWiFi(Context context) {
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm == null) throw new UnsupportedOperationException("Wifi management is not supported");
        return disconnectCurrentWiFi(wm);
    }

    public static boolean disconnectCurrentWiFi(WifiManager wm) {
        return wm.disconnect();
    }

    public static boolean isConnected(Context context, String ssid) {
        return isConnected(context, ssid, null);
    }

    public static boolean isConnected(Context context, WiFi wifi) {
        return isConnected(context, wifi.getSSID(), wifi.getBSSID());
    }

    public static boolean isConnected(Context context, String ssid, String bssid) {
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        ConnectivityManager cm = (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (wm == null) throw new UnsupportedOperationException("WIFI_SERVICE is not supported");
        if (cm == null)
            throw new UnsupportedOperationException("CONNECTIVITY_SERVICE is not supported");
        return isConnected(wm, cm, ssid, bssid);
    }

    public static boolean isConnected(WifiManager wm, ConnectivityManager cm, String ssid, String bssid) {
        WifiInfo info = wm.getConnectionInfo();
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        if ((info == null) || (networkInfo == null) ||
                (networkInfo.getType() != ConnectivityManager.TYPE_WIFI)) return false;

        return innerIsSameWiFi(ssid, info.getSSID(), bssid, info.getBSSID()) &&
                Helper.convertWifiConnection(networkInfo, info.getSupplicantState()) == WiFiConnectionState.CONNECTED;
    }

    public static int getConfiguredNetworkId(Context context, WiFi wifi) {
        return getConfiguredNetworkId(context, wifi.getSSID(), wifi.getBSSID());
    }

    public static int getConfiguredNetworkId(Context context, String ssid) {
        return getConfiguredNetworkId(context, ssid, null);
    }

    public static int getConfiguredNetworkId(Context context, String ssid, String bssid) {
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm == null) throw new UnsupportedOperationException("Wifi management is not supported");
        return getConfiguredNetworkId(wm, ssid, bssid);
    }

    public static int getConfiguredNetworkId(WifiManager wm, WiFi wifi) {
        return getConfiguredNetworkId(wm, wifi.getSSID(), wifi.getBSSID());
    }

    public static int getConfiguredNetworkId(WifiManager wm, String ssid, String bssid) {
        List<WifiConfiguration> configurationList = GetConfiguredNetworks(wm);
        if (configurationList == null) return -1;
        for (WifiConfiguration cwifi : configurationList) {
            if (innerIsSameWiFi(ssid, cwifi.SSID,
                    bssid, cwifi.BSSID))
                return cwifi.networkId;
        }

        return -1;
    }

    public static boolean isConfiguredNetwork(Context context, WiFi wifi) {
        return isConfiguredNetwork(context, wifi.getSSID(), wifi.getBSSID());
    }

    public static boolean isConfiguredNetwork(Context context, String ssid) {
        return isConfiguredNetwork(context, ssid, null);
    }

    public static boolean isConfiguredNetwork(Context context, String ssid, String bssid) {
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm == null) throw new UnsupportedOperationException("Wifi management is not supported");
        return isConfiguredNetwork(wm, ssid, bssid);
    }

    public static boolean isConfiguredNetwork(WifiManager wm, String ssid, String bssid) {
        List<WifiConfiguration> configurationList = GetConfiguredNetworks(wm);
        if (configurationList == null) return false;
        for (WifiConfiguration cwifi : configurationList) {
            if (innerIsSameWiFi(ssid, cwifi.SSID, bssid, cwifi.BSSID)) return true;
        }

        return false;
    }

    public static boolean isThereScanList(Context context, String ssid, String bssid) {
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm == null) throw new UnsupportedOperationException("Wifi management is not supported");
        return isThereScanList(wm, ssid, bssid);
    }

    public static boolean isThereScanList(Context context, String ssid) {
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm == null) throw new UnsupportedOperationException("Wifi management is not supported");
        return isThereScanList(wm, ssid);
    }

    public static boolean isThereScanList(Context context, WiFi wifi) {
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm == null) throw new UnsupportedOperationException("Wifi management is not supported");
        return isThereScanList(wm, wifi);
    }

    public static boolean isThereScanList(WifiManager wm, WiFi wifi) {
        return isThereScanList(wm, wifi.getSSID(), wifi.getBSSID());
    }

    public static boolean isThereScanList(WifiManager wm, String ssid) {
        return isThereScanList(wm, ssid, null);
    }

    public static boolean isThereScanList(WifiManager wm, String ssid, String bssid) {
        List<ScanResult> scanResults = wm.getScanResults();
        for (ScanResult data : scanResults) {
            if (innerIsSameWiFi(ssid, data.SSID, bssid, data.BSSID)) return true;
        }

        return false;
    }

    public static boolean isSameWiFi(WiFi wifi, ScanResult scanResult) {
        return (scanResult != null) && (wifi != null) &&
                innerIsSameWiFi(wifi.getSSID(), scanResult.SSID,
                        wifi.getBSSID(), scanResult.BSSID);
    }

    public static boolean isSameWiFi(WiFi wifi, NetworkInfo ninfo) {
        return (ninfo != null) && (wifi != null) &&
                (ninfo.getType() == ConnectivityManager.TYPE_WIFI) &&
                innerIsSameWiFi(wifi.getSSID(), ninfo.getExtraInfo(),
                        null, null);
    }

    @SuppressLint("HardwareIds")
    public static boolean isSameWiFi(WiFi wifi, WifiInfo wifiInfo) {
        return (wifi != null) && (wifiInfo != null) &&
                innerIsSameWiFi(wifi.getSSID(), wifiInfo.getSSID(),
                        wifi.getBSSID(), WiFi.isMacAddress(wifiInfo.getBSSID()) ? wifiInfo.getBSSID() : wifiInfo.getMacAddress());
    }

    public static boolean isSameWiFi(WiFi wifi, WifiConfiguration cwifi) {
        return (wifi != null) && (cwifi != null) &&
                innerIsSameWiFi(wifi.getSSID(), cwifi.SSID,
                        wifi.getBSSID(), cwifi.BSSID);
    }

    public static boolean isSameWiFi(WiFi lWifi, WiFi rWifi) {
        return (lWifi != null) && (rWifi != null) &&
                innerIsSameWiFi(lWifi.getSSID(), rWifi.getSSID(),
                        lWifi.getBSSID(), rWifi.getBSSID());
    }

    private static boolean innerIsSameWiFi(String lSsid, String rSsid, String lBssid, String rBssid) {
        if ((lSsid == null) || (rSsid == null)) return false;
        lSsid = lSsid.replaceAll("\"", "");
        rSsid = rSsid.replaceAll("\"", "");
        lBssid = WiFi.isMacAddress(lBssid) ? lBssid : null;
        rBssid = WiFi.isMacAddress(rBssid) ? rBssid : null;
        if ((lBssid == null) || (rBssid == null))
            return lSsid.equals(rSsid);
        return lSsid.equals(rSsid) && lBssid.equals(rBssid);
    }

    public static List<WifiConfiguration> GetConfiguredNetworks(Context context) {
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm == null) throw new UnsupportedOperationException("Wifi management is not supported");
        return GetConfiguredNetworks(wm);
    }

    public static List<WifiConfiguration> GetConfiguredNetworks(WifiManager wm) {
        List<WifiConfiguration> configurationList = null;
        try {
            configurationList = wm.getConfiguredNetworks();
        } catch (NullPointerException ignored) {
            Log.e(BroWiFiConnectivity.class.getSimpleName(), ignored.getMessage());
            Log.e(BroWiFiConnectivity.class.getSimpleName(), ignored.getLocalizedMessage());
            return null;
        }

        return configurationList;
    }

    public BroWiFiConnectivity setRequestPermission(boolean isRequestPermission) {
        if (!(mContext instanceof Activity))
            throw new IllegalStateException("if you use this Method, BroWiFiConnectivity.contact method argument must be Activity");
        mIsRequestPermission = isRequestPermission;
        return this;
    }

    public BroWiFiConnectivity setWifiEnable(boolean wifiEnable) {
        if (mWifiState == WiFiConnectivity.WIFI_STATE_DEFAULT_DIALOG) return this;
        mWifiState = wifiEnable ? WiFiConnectivity.WIFI_STATE_ENABLE : WiFiConnectivity.WIFI_STATE_DISABLE;
        return this;
    }

    public BroWiFiConnectivity showEnableWifiDialog() {
        mWifiState = WiFiConnectivity.WIFI_STATE_DEFAULT_DIALOG;
        return this;
    }

    public BroWiFiConnectivity setScanListener(WiFiScanListener scanListener) {
        mScanListener = scanListener;
        return this;
    }

    public BroWiFiConnectivity setStateListener(WiFiStateListener stateListener) {
        mStateListener = stateListener;
        return this;
    }

    public BroWiFiConnectivity setConnectListener(WiFiConnectListener connectListener) {
        mConnectListener = connectListener;
        return this;
    }

    public BroWiFiConnectivity setDebugListener(WiFiDebugListener debugListener) {
        mDebugListener = debugListener;
        return this;
    }

    public BroWiFiConnectivity setScan(boolean isStartScan) {
        mIsStartScan = isStartScan;
        return this;
    }

    public void start() {
        start(null);
    }

    public void start(WiFi connectWifi) {
        WiFiConnectivity.StartService(mContext,
                new Intent(mContext, WiFiConnectivity.class).
                        putExtra(WiFiConnectivity.REQUESTER_CONTEXT, String.valueOf(mContext.hashCode())).
                        putExtra(WiFiConnectivity.WIFI_STATE, mWifiState).
                        putExtra(WiFiConnectivity.REQUEST_PERMISSION, mIsRequestPermission).
                        putExtra(WiFiConnectivity.START_SCAN, mIsStartScan).
                        putExtra(WiFiConnectivity.CONNECT_WIFI, connectWifi),
                mScanListener, mStateListener, mConnectListener, mDebugListener);
    }
}
