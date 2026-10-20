package com.chrismutter.truthorsip;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.webkit.JavascriptInterface;

import com.google.android.play.core.review.ReviewInfo;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;
import com.google.android.gms.tasks.Task;

/** Exposed to the web UI as window.AndroidReview. */
public class ReviewBridge {
    // CHANGE THIS to your real support address before building.
    private static final String CONTACT_EMAIL = "support@YOURDOMAIN.com";
    private static final String PKG = "com.chrismutter.truthorsip";

    private final Activity activity;

    public ReviewBridge(Activity activity) {
        this.activity = activity;
    }

    /** Google's own review card. Play decides whether it actually shows (quota). Never call from a button. */
    @JavascriptInterface
    public void requestReview() {
        activity.runOnUiThread(() -> {
            try {
                final ReviewManager manager = ReviewManagerFactory.create(activity);
                Task<ReviewInfo> request = manager.requestReviewFlow();
                request.addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !activity.isFinishing()) {
                        manager.launchReviewFlow(activity, task.getResult());
                    }
                });
            } catch (Throwable ignored) { }
        });
    }

    /** "Rate us" button: opens the Play Store page (allowed for explicit buttons). */
    @JavascriptInterface
    public void openStorePage() {
        activity.runOnUiThread(() -> {
            try {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + PKG)));
            } catch (Throwable e) {
                try {
                    activity.startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/apps/details?id=" + PKG)));
                } catch (Throwable ignored) { }
            }
        });
    }

    /** "Contact us" button: opens the email app with a pre-filled subject. */
    @JavascriptInterface
    public void contactUs() {
        activity.runOnUiThread(() -> {
            try {
                Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"));
                i.putExtra(Intent.EXTRA_EMAIL, new String[]{CONTACT_EMAIL});
                i.putExtra(Intent.EXTRA_SUBJECT, "Truth or Sip feedback");
                activity.startActivity(i);
            } catch (Throwable ignored) { }
        });
    }
}
