package com.castlebro.wificonnectivity;

import android.net.wifi.WifiConfiguration;

import java.util.List;

public interface WiFiScanListener {
    boolean ScanResults(List<WiFi> scanResults);
}
