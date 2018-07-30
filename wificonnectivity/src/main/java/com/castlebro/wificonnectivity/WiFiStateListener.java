package com.castlebro.wificonnectivity;

public interface WiFiStateListener {
    void onStateChange(int state);

    /*
     * ToDo
     * onStateChange 인자 받아서 상태 디테일하게 받기.
     */
}
