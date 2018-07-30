package com.castlebro.wificonnectivity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WiFi implements Parcelable {

    private static final int UNSPECIFIED = -1;
    private static final int OPEN = 0;
    private static final int WEB = 1;
    private static final int WPA = 2;

    private WifiConfiguration mWifiConfiguration;
    private int mPasswordAuthType = UNSPECIFIED;
    private String mPassword;
    private WeakReference<IWiFiConnectivity> mRequester;

    public WiFi(IWiFiConnectivity requester, ScanResult scanResult) {
        super();
        mRequester = new WeakReference<>(requester);
        this.mWifiConfiguration = new WifiConfiguration();
        this.mWifiConfiguration.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
        this.mWifiConfiguration.SSID = "\"".concat(scanResult.SSID).concat("\"");
        this.mWifiConfiguration.BSSID = scanResult.BSSID;
        this.mWifiConfiguration.status = WifiConfiguration.Status.DISABLED;
        parseCapabilities(scanResult.capabilities);
    }

    protected WiFi(Parcel in) {
        mRequester = new WeakReference<>(null);
        mWifiConfiguration = in.readParcelable(WifiConfiguration.class.getClassLoader());
        mPasswordAuthType = in.readInt();
        mPassword = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mWifiConfiguration, flags);
        dest.writeInt(mPasswordAuthType);
        dest.writeString(mPassword);
    }

    public static final Creator<WiFi> CREATOR = new Creator<WiFi>() {
        @Override
        public WiFi createFromParcel(Parcel in) {
            return new WiFi(in);
        }

        @Override
        public WiFi[] newArray(int size) {
            return new WiFi[size];
        }
    };

    public static List<WiFi> parseCollection(IWiFiConnectivity requester, Collection<? extends ScanResult> scanResults) {
        List<WiFi> rtn = new ArrayList<>();
        for (ScanResult val : scanResults) {
            rtn.add(new WiFi(requester, val));
        }
        return rtn;
    }

    public void setPassword(String password) {
        mPassword = password;
        updatePasswordFormat();
    }


    public boolean connect() {
        if (mRequester.get() != null)
            return mRequester.get().connect(this);

        return false;
    }

    public String getSSID()
    {
        return mWifiConfiguration.SSID.replaceAll("\"", "");
    }

    public String getBSSID()
    {
        return mWifiConfiguration.BSSID;
    }

    public WifiConfiguration getWifiConfiguration()
    {
        return mWifiConfiguration;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof WiFi) {
            WiFi Obj = (WiFi) obj;
            return mWifiConfiguration.SSID.equals(Obj.mWifiConfiguration.SSID) &&
                    mWifiConfiguration.BSSID.equals(Obj.mWifiConfiguration.BSSID);
        }
        return false;
    }

    private void parseCapabilities(String capabilities) {
        //capabilities.contains()

        if (capabilities.contains("WEB")) {
            mPasswordAuthType = WEB;
        } else if (capabilities.contains("WPA")) {
            mPasswordAuthType = WPA;

        } else {
            mPasswordAuthType = OPEN;
        }

        updatePasswordAuthType();
    }

    private void updatePasswordFormat() {
        if (mPasswordAuthType == WPA) {
            this.mWifiConfiguration.preSharedKey = "\"".concat(mPassword).concat("\"");
        } else if (mPasswordAuthType == WEB) {
            this.mWifiConfiguration.wepKeys[0] = "\"".concat(mPassword).concat("\"");
            this.mWifiConfiguration.wepTxKeyIndex = 0;
        }
    }

    private void updatePasswordAuthType() {
        switch (mPasswordAuthType) {
            case WEB:
                this.mWifiConfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);

                this.mWifiConfiguration.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
                this.mWifiConfiguration.allowedProtocols.set(WifiConfiguration.Protocol.WPA);

                this.mWifiConfiguration.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
                this.mWifiConfiguration.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);

                this.mWifiConfiguration.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);

                this.mWifiConfiguration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
                this.mWifiConfiguration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
                break;
            case WPA:
                this.mWifiConfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);

                this.mWifiConfiguration.allowedProtocols.set(WifiConfiguration.Protocol.RSN);

                this.mWifiConfiguration.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);

                this.mWifiConfiguration.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
                this.mWifiConfiguration.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);

                this.mWifiConfiguration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
                this.mWifiConfiguration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
                break;
            case OPEN:
                this.mWifiConfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);

                this.mWifiConfiguration.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
                this.mWifiConfiguration.allowedProtocols.set(WifiConfiguration.Protocol.WPA);

                this.mWifiConfiguration.allowedAuthAlgorithms.clear();

                this.mWifiConfiguration.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);

                this.mWifiConfiguration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
                this.mWifiConfiguration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
                this.mWifiConfiguration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
                this.mWifiConfiguration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
                break;
        }
    }
}
