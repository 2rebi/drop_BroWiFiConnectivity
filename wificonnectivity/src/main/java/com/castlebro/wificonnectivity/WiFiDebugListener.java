package com.castlebro.wificonnectivity;

import android.content.Intent;

public interface WiFiDebugListener {
    void onDebug(Intent intent);
    void onException(Exception e);
}
