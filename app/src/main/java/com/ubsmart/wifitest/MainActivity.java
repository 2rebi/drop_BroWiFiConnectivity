package com.ubsmart.wifitest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import com.castlebro.wificonnectivity.BroWiFiConnectivity;
import com.castlebro.wificonnectivity.IWiFiConnectivity;
import com.castlebro.wificonnectivity.WiFi;
import com.castlebro.wificonnectivity.WiFiConnectListener;

import java.util.HashSet;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    String buf;
    TextView Name;
    WifiManager mWifiManager;
    List<WiFi> results;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Name = findViewById(R.id.name);

        BroWiFiConnectivity.contact(this)
                .setWifiEnable(true)
                .setRequestPermission(true)
                .setScanListener((wifis) ->
                {
//                    buf = "";
//                    results = wifis;
                    for (WiFi data : wifis) {
                        if (data.getSSID().contains("smart-ca5"))
                        {
                            data.setPassword("22026180");
                            boolean test = data.connect();
                            if (test)
                            {
                                Name.setText(Name.getText() + "Request true \n");
                                return true;
                            }
                            else
                            {
                                Name.setText(Name.getText() + "Request false \n");
                            }
                        }
                    }
//                    Name.setText(buf);
                    return false;
                })
                .setScan(true)
                .setConnectListener(new WiFiConnectListener() {
                    @Override
                    public void onConnected(IWiFiConnectivity IConnectivity, WiFi wifi) {
                        Name.setText(Name.getText() + "onConnected callback\n");
                    }

                    @Override
                    public void onDisconnected(IWiFiConnectivity IConnectivity, WiFi wifi) {
                        Name.setText(Name.getText() + "onDisconnected callback\n");
                    }

                    @Override
                    public void onConnectionFailed(IWiFiConnectivity IConnectivity, WiFi wifi) {
                        Name.setText(Name.getText() + "onFailed callback\n");
                    }

                    @Override
                    public void onDebug(NetworkInfo.DetailedState state) {
                        Name.setText(Name.getText() + state.toString() + "\n");
                    }
                })
                .start();
    }

}
