package com.chrismutter.truthorsip;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.Collections;
import java.util.List;

/**
 * Connects the game's JavaScript to Google Play Billing.
 *
 * JS -> native:  window.Android.launchPurchase(productId)
 *                window.Android.restorePurchases()
 * native -> JS:  window.onPurchaseSuccess(productId)   (user just paid)
 *                window.onPurchaseRestored(productId)  (already owned, e.g. after reinstall)
 *                window.onPurchaseFailed(reason)
 */
public class BillingBridge implements PurchasesUpdatedListener {

    private static final String TAG = "TruthOrSipBilling";

    private final Activity activity;
    private final WebView webView;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final BillingClient client;

    public BillingBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        this.client = BillingClient.newBuilder(activity.getApplicationContext())
                .setListener(this)
                .enablePendingPurchases(
                        PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build();
    }

    /* ---------------- called from JavaScript ---------------- */

    @JavascriptInterface
    public void launchPurchase(final String productId) {
        activity.runOnUiThread(() -> withConnection(
                () -> startPurchase(productId),
                () -> notifyFailed("billing_unavailable")));
    }

    @JavascriptInterface
    public void restorePurchases() {
        activity.runOnUiThread(() -> withConnection(this::queryOwned, null));
    }

    /* ---------------- connection ---------------- */

    private void withConnection(final Runnable onReady, @Nullable final Runnable onFail) {
        if (client.isReady()) {
            onReady.run();
            return;
        }
        client.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult result) {
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    onReady.run();
                } else {
                    Log.w(TAG, "Billing setup failed: " + result.getDebugMessage());
                    if (onFail != null) onFail.run();
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                // Reconnects on the next call.
            }
        });
    }

    /* ---------------- buying ---------------- */

    private void startPurchase(final String productId) {
        QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build();
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(product))
                .build();

        client.queryProductDetailsAsync(params, (billingResult, queryResult) -> {
            List<ProductDetails> list = queryResult.getProductDetailsList();
            if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK
                    || list == null || list.isEmpty()) {
                Log.w(TAG, "Product not found: " + productId + " / " + billingResult.getDebugMessage());
                notifyFailed("product_not_found");
                return;
            }
            BillingFlowParams.ProductDetailsParams detailsParams =
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(list.get(0))
                            .build();
            BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(Collections.singletonList(detailsParams))
                    .build();
            BillingResult launch = client.launchBillingFlow(activity, flowParams);
            if (launch.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "launchBillingFlow failed: " + launch.getDebugMessage());
                notifyFailed("launch_failed");
            }
        });
    }

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult result, @Nullable List<Purchase> purchases) {
        int code = result.getResponseCode();
        if (code == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) handlePurchase(purchase, true);
        } else if (code == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            queryOwned();
        } else if (code == BillingClient.BillingResponseCode.USER_CANCELED) {
            notifyFailed("cancelled");
        } else {
            Log.w(TAG, "Purchase error " + code + ": " + result.getDebugMessage());
            notifyFailed("error_" + code);
        }
    }

    /* ---------------- owned items / delivery ---------------- */

    private void queryOwned() {
        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build();
        client.queryPurchasesAsync(params, (result, purchases) -> {
            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) return;
            for (Purchase purchase : purchases) handlePurchase(purchase, false);
        });
    }

    private void handlePurchase(final Purchase purchase, final boolean fromFlow) {
        int state = purchase.getPurchaseState();
        if (state == Purchase.PurchaseState.PENDING) {
            if (fromFlow) notifyFailed("pending");
            return;
        }
        if (state != Purchase.PurchaseState.PURCHASED) return;

        // Unlock first, then acknowledge. Unacknowledged purchases are refunded by Google
        // after 3 days; if the acknowledge fails here it is retried on the next app launch.
        for (String productId : purchase.getProducts()) {
            callJs((fromFlow ? "onPurchaseSuccess" : "onPurchaseRestored"), productId);
        }
        if (!purchase.isAcknowledged()) {
            client.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.getPurchaseToken())
                            .build(),
                    ackResult -> {
                        if (ackResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                            Log.w(TAG, "Acknowledge failed: " + ackResult.getDebugMessage());
                        }
                    });
        }
    }

    /* ---------------- native -> JS ---------------- */

    private void notifyFailed(String reason) {
        callJs("onPurchaseFailed", reason);
    }

    private void callJs(final String fn, final String arg) {
        final String safe = arg.replace("\\", "\\\\").replace("'", "\\'");
        main.post(() -> webView.evaluateJavascript(
                "if(window." + fn + ")window." + fn + "('" + safe + "');", null));
    }
}
