package com.castlebro.wificonnectivity;

import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;

public interface WiFiConnectListener {
    void onConnected(IWiFiConnectivity IConnectivity, WiFi wifi);
    void onDisconnected(IWiFiConnectivity IConnectivity, WiFi wifi);
    void onConnectionFailed(IWiFiConnectivity IConnectivity , WiFi wifi, String reason);
}
