package com.castlebro.wificonnectivity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class BroWiFiConnectivity {

    private Context mContext;
    private boolean mIsRequestPermission = false;
    private boolean mIsStartScan = false;
    private short mWifiState = WiFiConnectivity.WIFI_UNSPECIFIED;

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
            String ssid = cwifi.SSID.replaceAll("\"", "");
            if (wifi.getSSID().equals(ssid)) return cwifi.networkId;
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
            String ssid = cwifi.SSID.replaceAll("\"", "");
            if (wifi.getSSID().equals(ssid)) return true;
        }

        return false;
    }

    public static boolean isConnected(Context context, WiFi wifi)
    {
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm == null) throw new UnsupportedOperationException("Wifi management is not supported");

        WifiInfo info = wm.getConnectionInfo();
        String ssid = info.getSSID().replaceAll("\"", "");
        String bssid = info.getBSSID().replaceAll("\"" , "");

        return ssid.equals(wifi.getSSID()) && bssid.equals(wifi.getBSSID());
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
        if (mWifiState == WiFiConnectivity.WIFI_DEFAULT_DIALOG) return this;
        mWifiState = wifiEnable ? WiFiConnectivity.WIFI_ENABLE : WiFiConnectivity.WIFI_DISABLE;
        return this;
    }

    public BroWiFiConnectivity showEnableWifiDialog() {
        mWifiState = WiFiConnectivity.WIFI_DEFAULT_DIALOG;
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
