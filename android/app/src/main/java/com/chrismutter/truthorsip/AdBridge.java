package com.chrismutter.truthorsip;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

/**
 * Rewarded ads for Truth or Sip. JS calls window.AndroidAds.showRewardedAd(catKey);
 * result comes back via window.onAdRewarded(catKey) or window.onAdFailed(reason).
 */
public class AdBridge {
    private static final String TAG = "AdBridge";

    // Flip to false (or leave true until you're ready) -- true = Google's test ad, safe to click.
    private static final boolean USE_TEST_ADS = true;
    private static final String TEST_UNIT = "ca-app-pub-3940256099942544/5224354917";
    private static final String REAL_UNIT = "ca-app-pub-5662091650537711/2193122834";

    private final Activity activity;
    private final WebView web;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private RewardedAd ad;
    private boolean loading = false;
    private boolean showing = false;

    public AdBridge(Activity activity, WebView web) {
        this.activity = activity;
        this.web = web;
        MobileAds.initialize(activity, status -> { });
        ui.post(this::preload);
    }

    private String unit() { return USE_TEST_ADS ? TEST_UNIT : REAL_UNIT; }

    private void preload() {
        if (ad != null || loading) return;
        loading = true;
        RewardedAd.load(activity, unit(), new AdRequest.Builder().build(),
            new RewardedAdLoadCallback() {
                @Override public void onAdLoaded(@NonNull RewardedAd loaded) {
                    ad = loaded; loading = false;
                }
                @Override public void onAdFailedToLoad(@NonNull LoadAdError err) {
                    Log.w(TAG, "load failed: " + err.getMessage());
                    ad = null; loading = false;
                }
            });
    }

    @JavascriptInterface
    public void showRewardedAd(final String catKey) {
        final String key = catKey == null ? "" : catKey.replaceAll("[^A-Za-z0-9_]", "");
        ui.post(() -> {
            if (showing) return;
            if (ad == null) { preload(); js("window.onAdFailed&&window.onAdFailed('no_fill')"); return; }
            final RewardedAd current = ad;
            ad = null;
            showing = true;
            final boolean[] earned = { false };
            current.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override public void onAdDismissedFullScreenContent() {
                    showing = false;
                    if (earned[0]) js("window.onAdRewarded&&window.onAdRewarded('" + key + "')");
                    else js("window.onAdFailed&&window.onAdFailed('dismissed')");
                    preload();
                }
                @Override public void onAdFailedToShowFullScreenContent(@NonNull AdError e) {
                    showing = false;
                    js("window.onAdFailed&&window.onAdFailed('error')");
                    preload();
                }
            });
            current.show(activity, item -> earned[0] = true);
        });
    }

    private void js(final String code) {
        web.post(() -> web.evaluateJavascript(code, null));
    }
}
