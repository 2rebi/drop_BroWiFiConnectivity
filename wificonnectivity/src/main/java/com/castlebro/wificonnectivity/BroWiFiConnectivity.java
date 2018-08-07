package com.castlebro.wificonnectivity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class BroWiFiConnectivity {

    private Context mContext;
    private boolean mIsRequestPermission = false;
    private boolean mIsStartScan = false;
    private short mWifiState = WiFiConnectivity.WIFI_STATE_UNSPECIFIED;

    private WiFiScanListener mScanListener;
    private WiFiStateListener mStateListener;
    private WiFiConnectListener mConnectListener;

    private BroWiFiConnectivity() {

    }

    public static BroWiFiConnectivity contact(Context context) {
        BroWiFiConnectivity wifi = new BroWiFiConnectivity();
        wifi.mContext = context;
        return wifi;
    }

    public static int getConfiguredNetworkId(Context context, WiFi wifi)
    {
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm == null) throw new UnsupportedOperationException("Wifi management is not supported");
        List<WifiConfiguration> configurationList = innerGetConfiguredNetworks(wm);
        if (configurationList == null) return -1;
        for(WifiConfiguration cwifi : configurationList)
        {
            if (isSameConfiguredNetwork(wifi, cwifi)) return cwifi.networkId;
        }

        return -1;
    }

    public static boolean isConfiguredNetwork(Context context, WiFi wifi)
    {
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm == null) throw new UnsupportedOperationException("Wifi management is not supported");
        List<WifiConfiguration> configurationList = innerGetConfiguredNetworks(wm);
        if (configurationList == null) return false;
        for(WifiConfiguration cwifi : configurationList)
        {
            if (isSameConfiguredNetwork(wifi, cwifi)) return true;
        }

        return false;
    }

    public static boolean isConfiguredNetwork(Context context, String ssid, String bssid)
    {
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm == null) throw new UnsupportedOperationException("Wifi management is not supported");
        List<WifiConfiguration> configurationList = innerGetConfiguredNetworks(wm);
        if (configurationList == null) return false;
        for(WifiConfiguration cwifi : configurationList)
        {
            String cssid = cwifi.SSID.replaceAll("\"", "");
            if (ssid.equals(cssid)) return bssid == null || bssid.equals(cwifi.BSSID);
        }

        return false;
    }

    public static boolean isSameConfiguredNetwork(WiFi wifi, WifiConfiguration cwifi)
    {
        String cssid = cwifi.SSID.replaceAll("\"", "");
        String cbssid = WiFi.isMacAddress(cwifi.BSSID) ? cwifi.BSSID.replaceAll("\"", "") : null;
        return wifi.getSSID().equals(cssid) && (cbssid == null || wifi.getBSSID().equals(cbssid));
                //&& (cbssid == null || wifi.getBSSID() == null || wifi.getBSSID().equals(cbssid));
    }

    public static boolean isConnected(Context context, WiFi wifi)
    {
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        ConnectivityManager cm = (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (wm == null) throw new UnsupportedOperationException("WIFI_SERVICE is not supported");
        if (cm == null) throw new UnsupportedOperationException("CONNECTIVITY_SERVICE is not supported");

        WifiInfo info = wm.getConnectionInfo();
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        if ((info == null) || (networkInfo == null) ||
                (networkInfo.getType() != ConnectivityManager.TYPE_WIFI)) return false;

        String ssid = info.getSSID() != null ? info.getSSID().replaceAll("\"", "") : "";

        return ssid.equals(wifi.getSSID()) &&
                ((info.getBSSID() == null) || info.getBSSID().equals(wifi.getBSSID()));
    }

    private static List<WifiConfiguration> innerGetConfiguredNetworks(WifiManager wm)
    {
        List<WifiConfiguration> configurationList = null;
        try
        {
            configurationList = wm.getConfiguredNetworks();
        }
        catch (NullPointerException ignored)
        {
            Log.e(BroWiFiConnectivity.class.getSimpleName(), ignored.getMessage());
            Log.e(BroWiFiConnectivity.class.getSimpleName(), ignored.getLocalizedMessage());
            return null;
        }

        return configurationList;
    }

    public BroWiFiConnectivity setRequestPermission(boolean isRequestPermission) {
        if (!(mContext instanceof Activity)) throw new IllegalStateException("if you use this Method, BroWiFiConnectivity.contact method argument must be Activity");
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

    public BroWiFiConnectivity setScan(boolean isStartScan) {
        mIsStartScan = isStartScan;
        return this;
    }

    public void start() {
        start(null);
    }

    public void start(WiFi connectWifi)
    {
        WiFiConnectivity.StartService(mContext,
                new Intent(mContext, WiFiConnectivity.class).
                        putExtra(WiFiConnectivity.REQUESTER_CONTEXT, String.valueOf(mContext.hashCode())).
                        putExtra(WiFiConnectivity.WIFI_STATE, mWifiState).
                        putExtra(WiFiConnectivity.REQUEST_PERMISSION, mIsRequestPermission).
                        putExtra(WiFiConnectivity.START_SCAN, mIsStartScan).
                        putExtra(WiFiConnectivity.CONNECT_WIFI, connectWifi),
                mScanListener, mStateListener, mConnectListener);
    }
}
