package com.chrismutter.truthorsip;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.appupdate.AppUpdateOptions;
import com.google.android.play.core.install.InstallStateUpdatedListener;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.InstallStatus;
import com.google.android.play.core.install.model.UpdateAvailability;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Update prompt + "What's new" message for Truth or Sip.
 *
 * JS -> native:  window.AndroidUpdate.check()          asks Google Play if a newer version exists
 *                window.AndroidUpdate.startUpdate()    starts a background (flexible) update
 *                window.AndroidUpdate.completeUpdate() restarts the app once the update is downloaded
 *                window.AndroidUpdate.fetchWhatsNew()  downloads the small JSON message from your site
 * native -> JS:  window.onUpdateAvailable(versionCode), window.onUpdateDownloaded(), window.onWhatsNew(jsonString)
 *
 * In-app updates only work for installs that came from Google Play. Sideloaded APKs get no result.
 */
public class UpdateBridge {
    private static final String TAG = "UpdateBridge";

    // Put the real address of your whatsnew.json here (https only). While it still says YOURDOMAIN it is skipped.
    private static final String WHATS_NEW_URL = "https://YOURDOMAIN/whatsnew.json";
    private static final int MAX_BYTES = 8 * 1024;

    private final Activity activity;
    private final WebView web;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final AppUpdateManager manager;
    private AppUpdateInfo cached;
    private boolean listening = false;

    private final InstallStateUpdatedListener listener;

    public UpdateBridge(Activity activity, WebView web) {
        this.activity = activity;
        this.web = web;
        this.manager = AppUpdateManagerFactory.create(activity);
        this.listener = state -> {
            if (state.installStatus() == InstallStatus.DOWNLOADED) {
                js("window.onUpdateDownloaded&&window.onUpdateDownloaded()");
                stopListening();
            }
        };
    }

    private void stopListening() {
        manager.unregisterListener(listener);
        listening = false;
    }

    @JavascriptInterface
    public void check() {
        ui.post(() -> manager.getAppUpdateInfo()
            .addOnSuccessListener(info -> {
                cached = info;
                if (info.installStatus() == InstallStatus.DOWNLOADED) {
                    js("window.onUpdateDownloaded&&window.onUpdateDownloaded()");
                } else if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                        && info.installStatus() != InstallStatus.DOWNLOADING
                        && info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                    js("window.onUpdateAvailable&&window.onUpdateAvailable(" + info.availableVersionCode() + ")");
                }
            })
            .addOnFailureListener(e -> Log.w(TAG, "update check failed: " + e.getMessage())));
    }

    @JavascriptInterface
    public void startUpdate() {
        ui.post(() -> {
            if (cached == null) return;
            if (!listening) { manager.registerListener(listener); listening = true; }
            manager.startUpdateFlow(cached, activity,
                    AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build())
                .addOnFailureListener(e -> Log.w(TAG, "start update failed: " + e.getMessage()));
        });
    }

    @JavascriptInterface
    public void completeUpdate() {
        ui.post(manager::completeUpdate);
    }

    @JavascriptInterface
    public void fetchWhatsNew() {
        if (WHATS_NEW_URL.contains("YOURDOMAIN") || !WHATS_NEW_URL.startsWith("https://")) return;
        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(WHATS_NEW_URL).openConnection();
                c.setConnectTimeout(6000);
                c.setReadTimeout(6000);
                c.setRequestMethod("GET");
                if (c.getResponseCode() != 200) return;
                InputStream in = c.getInputStream();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > MAX_BYTES) return;
                }
                String body = out.toString("UTF-8");
                js("window.onWhatsNew&&window.onWhatsNew(" + JSONObject.quote(body) + ")");
            } catch (Exception e) {
                Log.w(TAG, "whatsnew failed: " + e.getMessage());
            } finally {
                if (c != null) c.disconnect();
            }
        }).start();
    }

    private void js(final String code) {
        web.post(() -> web.evaluateJavascript(code, null));
    }
}
