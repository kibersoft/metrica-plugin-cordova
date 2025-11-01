/*
 * Version for Cordova/PhoneGap
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * https://yandex.com/legal/appmetrica_sdk_agreement/
 */

package com.yandex.metrica.plugin.cordova;

import android.app.Activity;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.appmetrica.analytics.AppMetrica;
import io.appmetrica.analytics.AppMetricaConfig;
import io.appmetrica.analytics.PreloadInfo;
import io.appmetrica.analytics.ecommerce.ECommerceAmount;
import io.appmetrica.analytics.ecommerce.ECommerceCartItem;
import io.appmetrica.analytics.ecommerce.ECommerceEvent;
import io.appmetrica.analytics.ecommerce.ECommerceOrder;
import io.appmetrica.analytics.ecommerce.ECommercePrice;
import io.appmetrica.analytics.ecommerce.ECommerceProduct;
import io.appmetrica.analytics.ecommerce.ECommerceReferrer;
import io.appmetrica.analytics.ecommerce.ECommerceScreen;


public class AppMetricaPlugin extends CordovaPlugin {

    private final Object mLock = new Object();
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private boolean mActivityPaused = true;
    private boolean mAppMetricaActivated = false;

    @Override
    public boolean execute(final String action, final JSONArray args, final CallbackContext cb) {
        getAppMetricaExecutor().execute(() -> {
            try {
                switch (action) {
                    case "activate":          activate(args, cb); break;
                    case "reportEvent":       reportEvent(args, cb); break;
                    case "reportError":       reportError(args, cb); break;
                    case "setLocation":       setLocation(args, cb); break;
                    case "setLocationTracking": setLocationTracking(args, cb); break;
                    case "showScreen":        safeShowScreen(args, cb); break;
                    case "showProductCard":   safeShowProductCard(args, cb); break;
                    case "addToCart":         safeAddToCart(args, cb); break;
                    case "removeFromCart":    safeRemoveFromCart(args, cb); break;
                    case "finishCheckout":    safeFinishCheckout(args, cb); break;
                    case "beginCheckout":     safeBeginCheckout(args, cb); break;
                    default: cb.error("Unknown action: " + action);
                }
            } catch (Throwable t) {
                Log.e("AppMetricaPlugin", "Unhandled error", t);
                safeError(cb, "internal_error", t.getMessage());
            }
        });
        return true;
    }


    @Override
    public void onPause(final boolean multitasking) {
        onPauseSession();
    }

    @Override
    public void onResume(final boolean multitasking) {
        onResumeSession();
    }

    @Override
    public void onNewIntent(final Intent intent) {
        getAppMetricaExecutor().execute(new Runnable() {
            @Override
            public void run() {
                if (mAppMetricaActivated) {
                    AppMetrica.reportAppOpen(getActivity());
                }
            }
        });
    }

    public ECommerceScreen createScreen(final JSONArray args) throws JSONException {
        final JSONObject object = args.getJSONObject(0);
        return new ECommerceScreen().setName(object.getString("screenName")).setSearchQuery(object.getString("searchQuery"));
    }

    public ECommerceProduct createProduct(final JSONArray params) throws JSONException {
        final JSONObject object = params.getJSONObject(0);
        ECommercePrice actualPrice = new ECommercePrice(new ECommerceAmount(Double.parseDouble(object.getString("price")), object.getString("currency")));
        return new ECommerceProduct(object.getString("sku")).setActualPrice(actualPrice).setName(object.getString("name"));
    }

    public ECommerceCartItem createCartItem(final JSONArray args) throws JSONException {
        try {
            final JSONObject object = args.getJSONObject(0);
            ECommerceScreen screen = this.createScreen(args);
            ECommerceProduct product = this.createProduct(args);
            ECommercePrice actualPrice = new ECommercePrice(new ECommerceAmount(Double.parseDouble(object.getString("price")), object.getString("currency")));
            ECommerceReferrer referrer = new ECommerceReferrer().setScreen(screen);
            return new ECommerceCartItem(product, actualPrice, Double.parseDouble(object.getString("quantity"))).setReferrer(referrer);
        } catch (Throwable t) {
            Log.e("ERROR", t.toString(), t);
            return null;
        }
    }


    private Activity getActivity() {
        return cordova.getActivity();
    }

    private ExecutorService getAppMetricaExecutor() {
        return mExecutor;
    }

    private void onPauseSession() {
        synchronized (mLock) {
            mActivityPaused = true;
            if (mAppMetricaActivated) {
                AppMetrica.pauseSession(getActivity());
            }
        }
    }

    private void onResumeSession() {
        synchronized (mLock) {
            mActivityPaused = false;
            if (mAppMetricaActivated) {
                AppMetrica.resumeSession(getActivity());
            }
        }
    }

    public static Location toLocation(final JSONObject locationObj) throws JSONException {
        final Location location = new Location("Custom");

        if (locationObj.has("latitude")) {
            location.setLatitude(locationObj.getDouble("latitude"));
        }
        if (locationObj.has("longitude")) {
            location.setLongitude(locationObj.getDouble("longitude"));
        }
        if (locationObj.has("altitude")) {
            location.setAltitude(locationObj.getDouble("altitude"));
        }
        if (locationObj.has("accuracy")) {
            location.setAccuracy((float) locationObj.getDouble("accuracy"));
        }
        if (locationObj.has("course")) {
            location.setBearing((float) locationObj.getDouble("course"));
        }
        if (locationObj.has("speed")) {
            location.setSpeed((float) locationObj.getDouble("speed"));
        }
        if (locationObj.has("timestamp")) {
            location.setTime(locationObj.getLong("timestamp"));
        }

        return location;
    }

    public static AppMetricaConfig toConfig(final JSONObject configObj) throws JSONException {
        final String apiKey = configObj.getString("apiKey");
        final AppMetricaConfig.Builder builder = AppMetricaConfig.newConfigBuilder(apiKey);

        if (configObj.has("handleFirstActivationAsUpdate")) {
            builder.handleFirstActivationAsUpdate(configObj.getBoolean("handleFirstActivationAsUpdate"));
        }
        if (configObj.has("locationTracking")) {
            builder.withLocationTracking(configObj.getBoolean("locationTracking"));
        }
        if (configObj.has("sessionTimeout")) {
            builder.withSessionTimeout(configObj.getInt("sessionTimeout"));
        }
        if (configObj.has("crashReporting")) {
            builder.withCrashReporting(configObj.getBoolean("crashReporting"));
        }
        if (configObj.has("appVersion")) {
            builder.withAppVersion(configObj.getString("appVersion"));
        }
        if (configObj.optBoolean("logs", false)) {
            builder.withLogs();
        }
        if (configObj.has("location")) {
            final Location location = toLocation(configObj.getJSONObject("location"));
            builder.withLocation(location);
        }
        if (configObj.has("preloadInfo")) {
            final JSONObject preloadInfoObj = configObj.getJSONObject("preloadInfo");
            final PreloadInfo.Builder infoBuilder = PreloadInfo.newBuilder(preloadInfoObj.getString("trackingId"));
            final JSONObject additionalInfoObj = preloadInfoObj.optJSONObject("additionalParams");
            if (additionalInfoObj != null) {
                for (Iterator<String> keyIterator = additionalInfoObj.keys(); keyIterator.hasNext(); ) {
                    final String key = keyIterator.next();
                    final String value = additionalInfoObj.getString(key);
                    infoBuilder.setAdditionalParams(key, value);
                }
            }
            builder.withPreloadInfo(infoBuilder.build());
        }

        return builder.build();
    }

    private void activate(final JSONArray args,
                          final CallbackContext callbackContext) throws JSONException {
        final JSONObject configObj = args.getJSONObject(0);
        final AppMetricaConfig config = toConfig(configObj);

        final Context context = getActivity().getApplicationContext();
        AppMetrica.activate(context, config);

        synchronized (mLock) {
            if (!mAppMetricaActivated) {
                AppMetrica.reportAppOpen(getActivity());
                if (!mActivityPaused) {
                    AppMetrica.resumeSession(getActivity());
                }
            }
            mAppMetricaActivated = true;
        }
    }

    private ECommerceOrder buildOrder(final JSONArray products) {
        try {
            JSONObject root = products.getJSONObject(0);
            String identifier = root.optString("identifier", "");
            if (identifier.isEmpty()) return null;

            JSONArray items = root.optJSONArray("products");
            if (items == null || items.length() == 0) return null;

            ArrayList<ECommerceCartItem> cartItems = new ArrayList<>();
            for (int i = 0; i < items.length(); i++) {
                JSONObject one = items.optJSONObject(i);
                if (one == null) continue;
                JSONArray wrap = new JSONArray().put(one);
                ECommerceCartItem ci = createCartItem(wrap);
                if (ci != null) cartItems.add(ci);
            }
            if (cartItems.isEmpty()) return null;

            return new ECommerceOrder(identifier, cartItems);
        } catch (Throwable t) {
            Log.e("AppMetricaPlugin", "buildOrder", t);
            return null;
        }
    }

    private void reportEvent(final JSONArray args,
                             final CallbackContext callbackContext) throws JSONException {
        final String eventName = args.getString(0);
        String eventParametersJSONString = null;
        try {
            final JSONObject eventParametersObj = args.getJSONObject(1);
            eventParametersJSONString = eventParametersObj.toString();
        } catch (JSONException ignored) {
        }

        if (eventParametersJSONString != null) {
            AppMetrica.reportEvent(eventName, eventParametersJSONString);
        } else {
            AppMetrica.reportEvent(eventName);
        }
    }

    private void safeShowScreen(final JSONArray params, final CallbackContext cb) {
        try {
            ECommerceScreen screen = createScreen(params);
            AppMetrica.reportECommerce(ECommerceEvent.showScreenEvent(screen));
            safeOk(cb);
        } catch (Throwable t) {
            Log.e("AppMetricaPlugin", "showScreen", t);
            safeError(cb, "show_screen_failed", t.getMessage());
        }
    }

    private void safeShowProductCard(final JSONArray params, final CallbackContext cb) {
        try {
            ECommerceScreen screen = createScreen(params);
            ECommerceProduct product = createProduct(params);
            AppMetrica.reportECommerce(ECommerceEvent.showProductCardEvent(product, screen));
            safeOk(cb);
        } catch (Throwable t) {
            Log.e("AppMetricaPlugin", "showProductCard", t);
            safeError(cb, "show_product_failed", t.getMessage());
        }
    }

    private void safeAddToCart(final JSONArray params, final CallbackContext cb) {
        try {
            ECommerceCartItem item = createCartItem(params);
            if (item == null) {
                safeError(cb, "validation_error", "Invalid cart item");
                return;
            }
            AppMetrica.reportECommerce(ECommerceEvent.addCartItemEvent(item));
            safeOk(cb);
        } catch (Throwable t) {
            Log.e("AppMetricaPlugin", "addToCart", t);
            safeError(cb, "add_to_cart_failed", t.getMessage());
        }
    }

    private void safeRemoveFromCart(final JSONArray params, final CallbackContext cb) {
        try {
            ECommerceCartItem item = createCartItem(params);
            if (item == null) {
                safeError(cb, "validation_error", "Invalid cart item");
                return;
            }
            AppMetrica.reportECommerce(ECommerceEvent.removeCartItemEvent(item));
            safeOk(cb);
        } catch (Throwable t) {
            Log.e("AppMetricaPlugin", "removeFromCart", t);
            safeError(cb, "remove_from_cart_failed", t.getMessage());
        }
    }

    private void safeBeginCheckout(final JSONArray args, final CallbackContext cb) {
        try {
            ECommerceOrder order = buildOrder(args);
            if (order == null) {
                safeError(cb, "validation_error", "Invalid order");
                return;
            }
            AppMetrica.reportECommerce(ECommerceEvent.beginCheckoutEvent(order));
            safeOk(cb, "beginCheckout reported");
        } catch (Throwable t) {
            Log.e("AppMetricaPlugin", "beginCheckout", t);
            safeError(cb, "begin_checkout_failed", t.getMessage());
        }
    }

    private void safeFinishCheckout(final JSONArray args, final CallbackContext cb) {
        try {
            ECommerceOrder order = buildOrder(args);
            if (order == null) {
                safeError(cb, "validation_error", "Invalid order");
                return;
            }
            AppMetrica.reportECommerce(ECommerceEvent.purchaseEvent(order));
            safeOk(cb, "finishCheckout reported");
        } catch (Throwable t) {
            Log.e("AppMetricaPlugin", "finishCheckout", t);
            safeError(cb, "finish_checkout_failed", t.getMessage());
        }
    }

    private void reportError(final JSONArray args,
                             final CallbackContext callbackContext) throws JSONException {
        final String errorName = args.getString(0);
        Throwable errorThrowable = null;
        try {
            final String errorReason = args.getString(1);
            errorThrowable = new Throwable(errorReason);
        } catch (JSONException ignored) {
        }

        AppMetrica.reportError(errorName, errorThrowable);
    }


    private void setLocation(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        final JSONObject locationObj = args.getJSONObject(0);

        final Location location = toLocation(locationObj);
        AppMetrica.setLocation(location);
    }

    private void setLocationTracking(final JSONArray args,
                                     final CallbackContext callbackContext) throws JSONException {
        final boolean enabled = args.getBoolean(0);

        AppMetrica.setLocationTracking(enabled);
    }

    private void safeOk(CallbackContext cb) {
        if (cb == null) return;
        try { cb.success(new JSONObject().put("ok", true)); } catch (Exception ignored) {}
    }

    private void safeOk(CallbackContext cb, String msg) {
        if (cb == null) return;
        try {
            cb.success(new JSONObject().put("ok", true).put("message", msg));
        } catch (Exception ignored) {}
    }

    private void safeError(CallbackContext cb, String code, String message) {
        if (cb == null) return;
        try {
            cb.error(new JSONObject().put("ok", false).put("code", code).put("message", String.valueOf(message)));
        } catch (Exception ignored) {}
    }
}
