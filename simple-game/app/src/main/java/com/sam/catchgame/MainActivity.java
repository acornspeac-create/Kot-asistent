package com.sam.catchgame;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class MainActivity extends Activity implements PurchasesUpdatedListener {
    private WebView webView;
    private BillingClient billingClient;
    private final Map<String, ProductDetails> productDetailsMap = new HashMap<>();

    private static final String[] COIN_PRODUCTS = new String[] {
            "kot_coins_500",
            "kot_coins_1500",
            "kot_coins_5000",
            "kot_coins_12000"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        webView = new WebView(this);
        webView.setBackgroundColor(0xFF070B12);
        WebView.setWebContentsDebuggingEnabled(false);
        webView.setWebViewClient(new WebViewClient() {
            private boolean isAllowed(Uri uri) {
                if (uri == null) return false;
                if ("about".equals(uri.getScheme()) && "blank".equals(uri.getSchemeSpecificPart())) {
                    return true;
                }
                return "file".equals(uri.getScheme())
                        && "/android_asset/index.html".equals(uri.getPath());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !isAllowed(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !isAllowed(Uri.parse(url));
            }
        });

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }
        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.addJavascriptInterface(new BillingBridge(), "AndroidBilling");
        webView.addJavascriptInterface(new SecureStoreBridge(this), "AndroidSecureStore");

        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");

        setupBilling();
    }

    private void setupBilling() {
        billingClient = BillingClient.newBuilder(this)
                .setListener(this)
                .enablePendingPurchases(
                        PendingPurchasesParams.newBuilder()
                                .enableOneTimeProducts()
                                .build()
                )
                .enableAutoServiceReconnection()
                .build();

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    queryProducts();
                    restoreUnconsumedPurchases();
                } else {
                    sendBillingMessage("Google Play Billing: " + billingResult.getDebugMessage());
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                sendBillingMessage("Google Play временно недоступен");
            }
        });
    }

    private void queryProducts() {
        if (billingClient == null || !billingClient.isReady()) {
            sendBillingMessage("Google Play ещё подключается");
            return;
        }

        List<QueryProductDetailsParams.Product> products = new ArrayList<>();
        for (String productId : COIN_PRODUCTS) {
            products.add(
                    QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(productId)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build()
            );
        }

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(products)
                .build();

        billingClient.queryProductDetailsAsync(params, (billingResult, result) -> {
            JSONArray array = new JSONArray();
            productDetailsMap.clear();

            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                for (ProductDetails details : result.getProductDetailsList()) {
                    productDetailsMap.put(details.getProductId(), details);

                    String price = "";
                    ProductDetails.OneTimePurchaseOfferDetails base =
                            details.getOneTimePurchaseOfferDetails();
                    if (base != null) {
                        price = base.getFormattedPrice();
                    } else {
                        List<ProductDetails.OneTimePurchaseOfferDetails> offers =
                                details.getOneTimePurchaseOfferDetailsList();
                        if (offers != null && !offers.isEmpty()) {
                            price = offers.get(0).getFormattedPrice();
                        }
                    }

                    JSONObject obj = new JSONObject();
                    try {
                        obj.put("id", details.getProductId());
                        obj.put("name", details.getName());
                        obj.put("price", price);
                        array.put(obj);
                    } catch (Exception ignored) {}
                }
            }

            final String payload = array.toString();
            runJs("window.onBillingProducts && window.onBillingProducts(" +
                    JSONObject.quote(payload) + ");");
        });
    }

    private void launchPurchase(String productId, String accountId) {
        runOnUiThread(() -> {
            if (billingClient == null || !billingClient.isReady()) {
                sendBillingMessage("Google Play ещё подключается");
                return;
            }

            ProductDetails details = productDetailsMap.get(productId);
            if (details == null) {
                queryProducts();
                sendBillingMessage("Товар ещё не загружен из Google Play");
                return;
            }

            BillingFlowParams.ProductDetailsParams.Builder productBuilder =
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(details);

            List<ProductDetails.OneTimePurchaseOfferDetails> offers =
                    details.getOneTimePurchaseOfferDetailsList();
            if (offers != null && !offers.isEmpty()) {
                String token = offers.get(0).getOfferToken();
                if (token != null && !token.isEmpty()) {
                    productBuilder.setOfferToken(token);
                }
            }

            List<BillingFlowParams.ProductDetailsParams> productParams =
                    new ArrayList<>();
            productParams.add(productBuilder.build());

            BillingFlowParams.Builder flowBuilder = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(productParams);

            if (accountId != null && !accountId.isEmpty() && accountId.length() <= 64) {
                flowBuilder.setObfuscatedAccountId(accountId);
            }

            BillingResult result = billingClient.launchBillingFlow(
                    MainActivity.this,
                    flowBuilder.build()
            );

            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                sendBillingMessage(result.getDebugMessage());
            }
        });
    }

    @Override
    public void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK
                && purchases != null) {
            for (Purchase purchase : purchases) {
                processPurchase(purchase);
            }
        } else if (billingResult.getResponseCode()
                != BillingClient.BillingResponseCode.USER_CANCELED) {
            sendBillingMessage(billingResult.getDebugMessage());
        }
    }

    private void processPurchase(Purchase purchase) {
        if (purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED) {
            if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {
                sendBillingMessage("Платёж ожидает подтверждения");
            }
            return;
        }

        final List<String> productIds = purchase.getProducts();
        if (productIds == null || productIds.isEmpty()) return;

        ConsumeParams params = ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken())
                .build();

        billingClient.consumeAsync(params, (billingResult, purchaseToken) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                for (String productId : productIds) {
                    runJs("window.onBillingPurchase && window.onBillingPurchase(" +
                            JSONObject.quote(productId) + ");");
                }
            } else {
                sendBillingMessage("Не удалось завершить покупку: " +
                        billingResult.getDebugMessage());
            }
        });
    }

    private void restoreUnconsumedPurchases() {
        if (billingClient == null || !billingClient.isReady()) return;

        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build();

        billingClient.queryPurchasesAsync(params, (billingResult, purchases) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                for (Purchase purchase : purchases) {
                    processPurchase(purchase);
                }
            }
        });
    }

    private void runJs(String script) {
        runOnUiThread(() -> {
            if (webView != null) {
                webView.evaluateJavascript(script, null);
            }
        });
    }

    private void sendBillingMessage(String message) {
        runJs("window.onBillingMessage && window.onBillingMessage(" +
                JSONObject.quote(message == null ? "Google Play" : message) + ");");
    }

    public static class SecureStoreBridge {
        private static final String PREFS = "kot_secure_state";
        private static final String STATE_KEY = "encrypted_state";
        private static final String KEY_ALIAS = "kot_catch_aes_gcm_v1";
        private final Context context;
        private final SecureRandom random = new SecureRandom();

        SecureStoreBridge(Context context) {
            this.context = context.getApplicationContext();
        }

        private SecretKey getOrCreateKey() throws Exception {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);

            if (!keyStore.containsAlias(KEY_ALIAS)) {
                KeyGenerator keyGenerator = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES,
                        "AndroidKeyStore"
                );
                keyGenerator.init(
                        new KeyGenParameterSpec.Builder(
                                KEY_ALIAS,
                                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
                        )
                                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                                .setKeySize(256)
                                .build()
                );
                keyGenerator.generateKey();
            }

            KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry)
                    keyStore.getEntry(KEY_ALIAS, null);
            return entry.getSecretKey();
        }

        @JavascriptInterface
        public synchronized boolean save(String json) {
            try {
                if (json == null || json.length() > 1_000_000) return false;

                byte[] iv = new byte[12];
                random.nextBytes(iv);

                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(
                        Cipher.ENCRYPT_MODE,
                        getOrCreateKey(),
                        new GCMParameterSpec(128, iv)
                );

                byte[] encrypted = cipher.doFinal(
                        json.getBytes(StandardCharsets.UTF_8)
                );

                byte[] payload = new byte[iv.length + encrypted.length];
                System.arraycopy(iv, 0, payload, 0, iv.length);
                System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);

                String encoded = Base64.encodeToString(payload, Base64.NO_WRAP);
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .putString(STATE_KEY, encoded)
                        .apply();
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }

        @JavascriptInterface
        public synchronized String load() {
            try {
                SharedPreferences prefs =
                        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                String encoded = prefs.getString(STATE_KEY, "");
                if (encoded == null || encoded.isEmpty()) return "";

                byte[] payload = Base64.decode(encoded, Base64.NO_WRAP);
                if (payload.length <= 12) return "";

                byte[] iv = new byte[12];
                byte[] encrypted = new byte[payload.length - 12];
                System.arraycopy(payload, 0, iv, 0, 12);
                System.arraycopy(payload, 12, encrypted, 0, encrypted.length);

                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(
                        Cipher.DECRYPT_MODE,
                        getOrCreateKey(),
                        new GCMParameterSpec(128, iv)
                );

                byte[] clear = cipher.doFinal(encrypted);
                return new String(clear, StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                return "";
            }
        }

        @JavascriptInterface
        public synchronized boolean clear() {
            try {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .remove(STATE_KEY)
                        .apply();
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    public class BillingBridge {
        @JavascriptInterface
        public void requestProducts() {
            queryProducts();
        }

        @JavascriptInterface
        public void buy(String productId, String accountId) {
            launchPurchase(productId, accountId);
        }

        @JavascriptInterface
        public boolean isReady() {
            return billingClient != null && billingClient.isReady();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    @Override
    protected void onDestroy() {
        if (billingClient != null) {
            billingClient.endConnection();
        }
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidBilling");
            webView.removeJavascriptInterface("AndroidSecureStore");
            webView.destroy();
        }
        super.onDestroy();
    }
}
