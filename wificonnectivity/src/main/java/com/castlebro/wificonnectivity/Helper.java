package com.castlebro.wificonnectivity;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;

import static com.castlebro.wificonnectivity.WiFiConnectionState.CONNECTED;
import static com.castlebro.wificonnectivity.WiFiConnectionState.CONNECTING;
import static com.castlebro.wificonnectivity.WiFiConnectionState.DISCONNECTED;
import static com.castlebro.wificonnectivity.WiFiConnectionState.NONE;

class Helper {

    static WiFiConnectionState convertWifiConnection(NetworkInfo nInfo, SupplicantState sState) {
        if ((nInfo != null) && (nInfo.getType() == ConnectivityManager.TYPE_WIFI)) {
            NetworkInfo.State nState = nInfo.getState();
            if (NetworkInfo.State.DISCONNECTED == nState) return DISCONNECTED;
            if (NetworkInfo.State.CONNECTING == nState) return CONNECTING;
            if (NetworkInfo.State.CONNECTED == nState) return CONNECTED;
        }
        if (sState != null) {
            if (SupplicantState.DISCONNECTED == sState) return DISCONNECTED;
            if (SupplicantState.ASSOCIATING == sState) return CONNECTING;
            if (SupplicantState.AUTHENTICATING == sState) return CONNECTING;
            if (SupplicantState.COMPLETED == sState) return CONNECTED;
            if (SupplicantState.ASSOCIATED == sState) return CONNECTED;
        }
        return NONE;
    }
}
