package com.chrismutter.truthorsip;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getBridge().getWebView().addJavascriptInterface(
                new BillingBridge(this, getBridge().getWebView()), "Android");
        getBridge().getWebView().addJavascriptInterface(
                new AdBridge(this, getBridge().getWebView()), "AndroidAds");
        getBridge().getWebView().addJavascriptInterface(
                new UpdateBridge(this, getBridge().getWebView()), "AndroidUpdate");
    }
}
