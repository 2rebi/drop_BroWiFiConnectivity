package com.castlebro.wificonnectivity;

import android.net.wifi.WifiConfiguration;

public interface IWiFiConnectivity {
    boolean connect(WiFi wifi);
    void setEnableWifi(int type);
}
