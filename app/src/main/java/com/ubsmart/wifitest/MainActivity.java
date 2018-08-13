package com.ubsmart.wifitest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
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
import com.castlebro.wificonnectivity.WiFiDebugListener;

import java.util.HashSet;
import java.util.List;

import static android.net.wifi.WifiManager.EXTRA_NETWORK_INFO;
import static android.net.wifi.WifiManager.EXTRA_NEW_STATE;

public class MainActivity extends AppCompatActivity {

    String buf;
    TextView Name;
    WifiManager mWifiManager;
    List<WiFi> results;
    ConnectivityManager mConnectivityManager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Name = findViewById(R.id.name);
        mWifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        mConnectivityManager = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

//        IntentFilter intentFilter = new IntentFilter();
//        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
//        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
//        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
//        intentFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
//        intentFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
//        registerReceiver(mReceiver, intentFilter);
        BroWiFiConnectivity.contact(this)
                .setWifiEnable(true)
                .setRequestPermission(true)
                .setScanListener((wifis) ->
                {
//                    StringBuilder sb = new StringBuilder();
////                    buf = "";
////                    results = wifis;
                    for (WiFi data : wifis) {
                        if (data.getSSID().contains("ca5")){
                            data.setPassword("22026180");
                            data.connect();
                        }
                    }
//                    Name.setText(sb.toString());
//                    Name.setText(buf);
                    return false;
                })
                .setDebugListener(new WiFiDebugListener() {
                    @Override
                    public void onDebug(Intent intent) {

                    }

                    @Override
                    public void onException(Exception e) {

                    }
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
                    public void onConnectionFailed(IWiFiConnectivity IConnectivity, WiFi wifi, String reason) {
                        Name.setText(Name.getText() + "onFailed callback\n" + reason);
                    }
                })
                .start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //unregisterReceiver(mReceiver);
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Name.setText(Name.getText() + "\n///////////////Broadcast onReceive///////////////\n");
            Name.setText(Name.getText() + intent.getAction() + "///\n");
            switch (intent.getAction()) {
                case WifiManager.NETWORK_STATE_CHANGED_ACTION:
                    NetworkInfo netinfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                    if (netinfo != null)
                        Name.setText(Name.getText() + "NetworkInfo///\n" + netinfo.toString() + "\n");
                    Name.setText(Name.getText() + "BSSID///\n" + intent.getStringExtra(WifiManager.EXTRA_BSSID) + "\n");
                    WifiInfo wifiInfo = intent.getParcelableExtra(WifiManager.EXTRA_WIFI_INFO);
                    if (wifiInfo != null)
                        Name.setText(Name.getText() + "WifiInfo///\n" + wifiInfo.toString() + "\n");
                    else
                        Name.setText(Name.getText() + "WifiInfo///\nnull");
                    break;
                case WifiManager.WIFI_STATE_CHANGED_ACTION:
                    break;
                case WifiManager.SCAN_RESULTS_AVAILABLE_ACTION:
                    break;
                case WifiManager.SUPPLICANT_STATE_CHANGED_ACTION:
                    SupplicantState sstate = intent.getParcelableExtra(EXTRA_NEW_STATE);
                    Name.setText(Name.getText() + "SupplicantState///\n" + sstate.toString() + "\n");
                    break;
                case WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION:
                    Name.setText(Name.getText() + "SupplicantState connection///\n" + String.valueOf(intent.getBooleanExtra(WifiManager.EXTRA_SUPPLICANT_CONNECTED, false)) + "\n");
                    break;
                case WifiManager.NETWORK_IDS_CHANGED_ACTION:
                    break;
            }
            if (!intent.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
            {
                WifiInfo dhcpInfo = mWifiManager.getConnectionInfo();
                if (dhcpInfo != null)
                    Name.setText(Name.getText() + "\n\nWifiInfo///\n" + dhcpInfo.toString() + "\n");
                else
                    Name.setText(Name.getText() + "\n\nWifiInfo///\nnull");

                NetworkInfo networkInfo = mConnectivityManager.getActiveNetworkInfo();
                if (networkInfo != null)
                    Name.setText(Name.getText() + "\n\nActiveNetworkInfo///\n" + mConnectivityManager.getActiveNetworkInfo().toString() + "\n");
                else
                    Name.setText(Name.getText() + "\n\nActiveNetworkInfo///\nnull");

                Name.setText(Name.getText() + "\n///////////////Broadcast onReceive End///////////////\n");
            }
        }
    };

}
