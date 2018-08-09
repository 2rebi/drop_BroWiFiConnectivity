package com.castlebro.wificonnectivity;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;

import static com.castlebro.wificonnectivity.WiFiConnectivity.WIFI_STATE_UNSPECIFIED;
import static com.castlebro.wificonnectivity.WiFiConnectivity.WIFI_DISCONNECTED;
import static com.castlebro.wificonnectivity.WiFiConnectivity.WIFI_CONNECTING;
import static com.castlebro.wificonnectivity.WiFiConnectivity.WIFI_CONNECTED;

class Helper {

    static WiFiConnectionState convertWifiConnection(NetworkInfo nInfo, SupplicantState sState) {
        if ((nInfo != null) && (nInfo.getType() == ConnectivityManager.TYPE_WIFI)) {
            NetworkInfo.State nState = nInfo.getState();
            if (NetworkInfo.State.DISCONNECTED == nState) return WiFiConnectionState.WIFI_DISCONNECTED;
            if (NetworkInfo.State.CONNECTING == nState) return WiFiConnectionState.WIFI_CONNECTING;
            if (NetworkInfo.State.CONNECTED == nState) return WiFiConnectionState.WIFI_CONNECTED;
        }
        if (sState != null) {
            if (SupplicantState.DISCONNECTED == sState) return WiFiConnectionState.WIFI_DISCONNECTED;
            if (SupplicantState.ASSOCIATING == sState) return WiFiConnectionState.WIFI_CONNECTING;
            if (SupplicantState.AUTHENTICATING == sState) return WiFiConnectionState.WIFI_CONNECTING;
            if (SupplicantState.COMPLETED == sState) return WiFiConnectionState.WIFI_CONNECTED;
            if (SupplicantState.ASSOCIATED == sState) return WiFiConnectionState.WIFI_CONNECTED;
        }
        return WiFiConnectionState.WIFI_STATE_UNSPECIFIED;
    }
}
