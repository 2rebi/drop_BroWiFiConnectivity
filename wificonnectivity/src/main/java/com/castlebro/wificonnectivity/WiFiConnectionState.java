package com.castlebro.wificonnectivity;

import android.os.Parcel;
import android.os.Parcelable;

public enum WiFiConnectionState implements Parcelable {
    NONE,
    UNKNOWN(0),
    DISCONNECTED(1),
    REQUEST_CONNECT(2),
    CONNECTING(3),
    CONNECTED(4),
    CONNECTED_DONE(5);

    private int mLevel;

    WiFiConnectionState()
    {
        mLevel = -1;
    }

    WiFiConnectionState(int level)
    {
        mLevel = level;
    }

    public int getLevel()
    {
        return mLevel;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.name());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<WiFiConnectionState> CREATOR = new Creator<WiFiConnectionState>() {
        @Override
        public WiFiConnectionState createFromParcel(Parcel in) {
            return WiFiConnectionState.valueOf(in.readString());
        }

        @Override
        public WiFiConnectionState[] newArray(int size) {
            return new WiFiConnectionState[size];
        }
    };
}
