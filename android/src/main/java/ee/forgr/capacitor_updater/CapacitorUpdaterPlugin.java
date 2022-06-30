package ee.forgr.capacitor_updater;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.volley.toolbox.Volley;
import com.getcapacitor.CapConfig;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.plugin.WebView;

import io.github.g00fy2.versioncompare.Version;

import org.json.JSONException;

import java.io.IOException;
import java.util.List;

@CapacitorPlugin(name = "CapacitorUpdater")
public class CapacitorUpdaterPlugin extends Plugin implements Application.ActivityLifecycleCallbacks {
    private static final String autoUpdateUrlDefault = "https://xvwzpoazmxkqosrdewyv.functions.supabase.co/updates";
    private static final String statsUrlDefault = "https://xvwzpoazmxkqosrdewyv.functions.supabase.co/stats";
    private static final String DELAY_UPDATE = "delayUpdate";

    private SharedPreferences.Editor editor;
    private SharedPreferences prefs;
    private CapacitorUpdater implementation;

    private Integer appReadyTimeout = 10000;
    private Boolean autoDeleteFailed = true;
    private Boolean autoDeletePrevious = true;
    private Boolean autoUpdate = false;
    private String autoUpdateUrl = "";
    private Version currentVersionNative;
    private Boolean resetWhenUpdate = true;

    private volatile Thread appReadyCheck;

    @Override
    public void load() {
        super.load();
        this.prefs = this.getContext().getSharedPreferences(WebView.WEBVIEW_PREFS_NAME, Activity.MODE_PRIVATE);
        this.editor = this.prefs.edit();

        try {
            this.implementation = new CapacitorUpdater() {
                @Override
                public void notifyDownload(final String id, final int percent) {
                    CapacitorUpdaterPlugin.this.notifyDownload(id, percent);
                }
            };
            final PackageInfo pInfo = this.getContext().getPackageManager().getPackageInfo(this.getContext().getPackageName(), 0);
            this.implementation.versionBuild = pInfo.versionName;
            this.implementation.versionCode = Integer.toString(pInfo.versionCode);
            this.implementation.requestQueue = Volley.newRequestQueue(this.getContext());
            this.currentVersionNative = new Version(pInfo.versionName);
        } catch (final PackageManager.NameNotFoundException e) {
            Log.e(CapacitorUpdater.TAG, "Error instantiating implementation", e);
            return;
        } catch (final Exception e) {
            Log.e(CapacitorUpdater.TAG, "Error getting current native app version", e);
            return;
        }

        final CapConfig config = CapConfig.loadDefault(this.getActivity());
        this.implementation.appId = config.getString("appId", "");
        this.implementation.statsUrl = this.getConfig().getString("statsUrl", statsUrlDefault);
        this.implementation.documentsDir = this.getContext().getFilesDir();
        this.implementation.prefs = this.getContext().getSharedPreferences(WebView.WEBVIEW_PREFS_NAME, Activity.MODE_PRIVATE);
        this.implementation.editor = this.prefs.edit();
        this.implementation.versionOs = Build.VERSION.RELEASE;
        this.implementation.deviceID = Settings.Secure.getString(this.getContext().getContentResolver(), Settings.Secure.ANDROID_ID);

        this.autoDeleteFailed = this.getConfig().getBoolean("autoDeleteFailed", true);
        this.autoDeletePrevious = this.getConfig().getBoolean("autoDeletePrevious", true);
        this.autoUpdateUrl = this.getConfig().getString("autoUpdateUrl", autoUpdateUrlDefault);
        this.autoUpdate = this.getConfig().getBoolean("autoUpdate", false);
        this.appReadyTimeout = this.getConfig().getInt("appReadyTimeout", 10000);
        this.resetWhenUpdate = this.getConfig().getBoolean("resetWhenUpdate", true);

        if (this.resetWhenUpdate) {
            this.cleanupObsoleteVersions();
        }
        final Application application = (Application) this.getContext().getApplicationContext();
        application.registerActivityLifecycleCallbacks(this);

        this.onActivityStarted(this.getActivity());
    }

    private void cleanupObsoleteVersions() {
        try {
            final Version previous = new Version(this.prefs.getString("LatestVersionNative", ""));
            try {
                if (!"".equals(previous.getOriginalString()) && this.currentVersionNative.getMajor() > previous.getMajor()) {

                    Log.i(CapacitorUpdater.TAG, "New native major version detected: " + this.currentVersionNative);
                    this.implementation.reset(true);
                    final List<BundleInfo> installed = this.implementation.list();
                    for (final BundleInfo bundle: installed) {
                        try {
                            Log.i(CapacitorUpdater.TAG, "Deleting obsolete bundle: " + bundle.getId());
                            this.implementation.delete(bundle.getId());
                        } catch (final Exception e) {
                            Log.e(CapacitorUpdater.TAG, "Failed to delete: " + bundle.getId(), e);
                        }
                    }
                }
            } catch (final Exception e) {
                Log.e(CapacitorUpdater.TAG, "Could not determine the current version", e);
            }
        } catch(final Exception e) {
            Log.e(CapacitorUpdater.TAG, "Error calculating previous native version", e);
        }
        this.editor.putString("LatestVersionNative", this.currentVersionNative.toString());
        this.editor.commit();
    }

    public void notifyDownload(final String id, final int percent) {
        try {
            final JSObject ret = new JSObject();
            ret.put("percent", percent);
            ret.put("bundle", this.implementation.getBundleInfo(id).toJSON());
            this.notifyListeners("download", ret);
        } catch (final Exception e) {
            Log.e(CapacitorUpdater.TAG, "Could not notify listeners", e);
        }
    }


    @PluginMethod
    public void getId(final PluginCall call) {
        try {
            final JSObject ret = new JSObject();
            ret.put("id", this.implementation.deviceID);
            call.resolve(ret);
        } catch (final Exception e) {
            Log.e(CapacitorUpdater.TAG, "Could not get device id", e);
            call.reject("Could not get device id", e);
        }
    }

    @PluginMethod
    public void getPluginVersion(final PluginCall call) {
        try {
            final JSObject ret = new JSObject();
            ret.put("version", CapacitorUpdater.pluginVersion);
            call.resolve(ret);
        } catch (final Exception e) {
            Log.e(CapacitorUpdater.TAG, "Could not get plugin version", e);
            call.reject("Could not get plugin version", e);
        }
    }

    @PluginMethod
    public void download(final PluginCall call) {
        final String url = call.getString("url");
        final String version = call.getString("version");
        if (url == null || version == null) {
            call.reject("missing url or version");
            return;
        }
        try {
            Log.i(CapacitorUpdater.TAG, "Downloading " + url);
            new Thread(new Runnable(){
                @Override
                public void run() {
                    try {

                        final BundleInfo downloaded = CapacitorUpdaterPlugin.this.implementation.download(url, version);
                        call.resolve(downloaded.toJSON());
                    } catch (final IOException e) {
                        Log.e(CapacitorUpdater.TAG, "download failed", e);
                        call.reject("download failed", e);
                    }
                }
            }).start();
        } catch (final Exception e) {
            Log.e(CapacitorUpdater.TAG, "Failed to download " + url, e);
            call.reject("Failed to download " + url, e);
        }
    }

    private boolean _reload() {
        final String path = this.implementation.getCurrentBundlePath();
        Log.i(CapacitorUpdater.TAG, "Reloading: " + path);
        if(this.implementation.isUsingBuiltin()) {
            this.bridge.setServerAssetPath(path);
        } else {
            this.bridge.setServerBasePath(path);
        }
        this.checkAppReady();
        return true;
    }

    @PluginMethod
    public void reload(final PluginCall call) {
        try {
            if (this._reload()) {
                call.resolve();
            } else {
                call.reject("Reload failed");
            }
        } catch(final Exception e) {
            Log.e(CapacitorUpdater.TAG, "Could not reload", e);
            call.reject("Could not reload", e);
        }
    }

    @PluginMethod
    public void next(final PluginCall call) {
        final String id = call.getString("id");

        try {
            Log.i(CapacitorUpdater.TAG, "Setting next active id " + id);
            if (!this.implementation.setNextVersion(id)) {
                call.reject("Set next id failed. Bundle " + id + " does not exist.");
            } else {
                call.resolve(this.implementation.getBundleInfo(id).toJSON());
            }
        } catch (final Exception e) {
            Log.e(CapacitorUpdater.TAG, "Could not set next id " + id, e);
            call.reject("Could not set next id " + id, e);
        }
    }

    @PluginMethod
    public void set(final PluginCall call) {
        final String id = call.getString("id");

        try {
            Log.i(CapacitorUpdater.TAG, "Setting active bundle " + id);
            if (!this.implementation.set(id)) {
                Log.i(CapacitorUpdater.TAG, "No such bundle " + id);
                call.reject("Update failed, id " + id + " does not exist.");
            } else {
                Log.i(CapacitorUpdater.TAG, "Bundle successfully set to" + id);
                this.reload(call);
            }
        } catch(final Exception e) {
            Log.e(CapacitorUpdater.TAG, "Could not set id " + id, e);
            call.reject("Could not set id " + id, e);
        }
    }

    @PluginMethod
    public void delete(final PluginCall call) {
        final String id = call.getString("id");
        Log.i(CapacitorUpdater.TAG, "Deleting id: " + id);
        try {
            final Boolean res = this.implementation.delete(id);
            if (res) {
                call.resolve();
            } else {
                call.reject("Delete failed, id " + id + " does not exist");
            }
        } catch(final Exception e) {
            Log.e(CapacitorUpdater.TAG, "Could not delete id " + id, e);
            call.reject("Could not delete id " + id, e);
        }
    }


    @PluginMethod
    public void list(final PluginCall call) {
        try {
            final List<BundleInfo> res = this.implementation.list();
            final JSObject ret = new JSObject();
            final JSArray values = new JSArray();
            for (final BundleInfo bundle : res) {
                values.put(bundle.toJSON());
            }
            ret.put("bundles", values);
            call.resolve(ret);
        }
        catch(final Exception e) {
            Log.e(CapacitorUpdater.TAG, "Could not list bundles", e);
            call.reject("Could not list bundles", e);
        }
    }

    private boolean _reset(final Boolean toLastSuccessful) {
        final BundleInfo fallback = this.implementation.getFallbackVersion();
        this.implementation.reset();

        if (toLastSuccessful && !fallback.isBuiltin()) {
            Log.i(CapacitorUpdater.TAG, "Resetting to: " + fallback);
            return this.implementation.set(fallback) && this._reload();
        }

        Log.i(CapacitorUpdater.TAG, "Resetting to native.");
        return this._reload();
    }

    @PluginMethod
    public void reset(final PluginCall call) {
        try {
            final Boolean toLastSuccessful = call.getBoolean("toLastSuccessful", false);
            if (this._reset(toLastSuccessful)) {
                call.resolve();
                return;
            }
            call.reject("Reset failed");
        }
        catch(final Exception e) {
            Log.e(CapacitorUpdater.TAG, "Reset failed", e);
            call.reject("Reset failed", e);
        }
    }

    @PluginMethod
    public void current(final PluginCall call) {
        try {
            final JSObject ret = new JSObject();
            final BundleInfo bundle = this.implementation.getCurrentBundle();
            ret.put("bundle", bundle.toJSON());
            ret.put("native", this.currentVersionNative);
            call.resolve(ret);
        }
        catch(final Exception e) {
            Log.e(CapacitorUpdater.TAG, "Could not get current bundle", e);
            call.reject("Could not get current bundle", e);
        }
    }

    @PluginMethod
    public void notifyAppReady(final PluginCall call) {
        try {
            Log.i(CapacitorUpdater.TAG, "Current bundle loaded successfully. ['notifyAppReady()' was called]");
            final BundleInfo bundle = this.implementation.getCurrentBundle();
            this.implementation.commit(bundle);
            call.resolve();
        }
        catch(final Exception e) {
            Log.e(CapacitorUpdater.TAG, "Failed to notify app ready state. [Error calling 'notifyAppReady()']", e);
            call.reject("Failed to commit app ready state.", e);
        }
    }

    @PluginMethod
    public void delayUpdate(final PluginCall call) {
        try {
            Log.i(CapacitorUpdater.TAG, "Delay update.");
            this.editor.putBoolean(DELAY_UPDATE, true);
            this.editor.commit();
            call.resolve();
        }
        catch(final Exception e) {
            Log.e(CapacitorUpdater.TAG, "Failed to delay update", e);
            call.reject("Failed to delay update", e);
        }
    }

    @PluginMethod
    public void cancelDelay(final PluginCall call) {
        try {
            Log.i(CapacitorUpdater.TAG, "Cancel update delay.");
            this.editor.putBoolean(DELAY_UPDATE, false);
            this.editor.commit();
            call.resolve();
        }
        catch(final Exception e) {
            Log.e(CapacitorUpdater.TAG, "Failed to cancel update delay", e);
            call.reject("Failed to cancel update delay", e);
        }
    }

    private Boolean _isAutoUpdateEnabled() {
        return CapacitorUpdaterPlugin.this.autoUpdate && !"".equals(CapacitorUpdaterPlugin.this.autoUpdateUrl);
    }

    @PluginMethod
    public void isAutoUpdateEnabled(final PluginCall call) {
        try {
            final JSObject ret = new JSObject();
            ret.put("enabled", this._isAutoUpdateEnabled());
            call.resolve(ret);
        } catch (final Exception e) {
            Log.e(CapacitorUpdater.TAG, "Could not get autoUpdate status", e);
            call.reject("Could not get autoUpdate status", e);
        }
    }

    private void checkAppReady() {
        try {
            if(this.appReadyCheck != null) {
                this.appReadyCheck.interrupt();
            }
            this.appReadyCheck = new Thread(new DeferredNotifyAppReadyCheck());
            this.appReadyCheck.start();
        } catch (final Exception e) {
            Log.e(CapacitorUpdater.TAG, "Failed to start " + DeferredNotifyAppReadyCheck.class.getName(), e);
        }
    }

    @Override // appMovedToForeground
    public void onActivityStarted(@NonNull final Activity activity) {
        if (CapacitorUpdaterPlugin.this._isAutoUpdateEnabled()) {
            new Thread(new Runnable(){
                @Override
                public void run() {

                    Log.i(CapacitorUpdater.TAG, "Check for update via: " + CapacitorUpdaterPlugin.this.autoUpdateUrl);
                    CapacitorUpdaterPlugin.this.implementation.getLatest(CapacitorUpdaterPlugin.this.autoUpdateUrl, (res) -> {
                        try {
                            if (res.has("message")) {
                                Log.i(CapacitorUpdater.TAG, "message: " + res.get("message"));
                                if (res.has("major") && res.getBoolean("major") && res.has("version")) {
                                    final JSObject majorAvailable = new JSObject();
                                    majorAvailable.put("version", (String) res.get("version"));
                                    CapacitorUpdaterPlugin.this.notifyListeners("majorAvailable", majorAvailable);
                                }
                                return;
                            }
                            final BundleInfo current = CapacitorUpdaterPlugin.this.implementation.getCurrentBundle();
                            final String latestVersionName = (String) res.get("version");

                            if (latestVersionName != null && !"".equals(latestVersionName) && !current.getVersionName().equals(latestVersionName)) {

                                final BundleInfo latest = CapacitorUpdaterPlugin.this.implementation.getBundleInfoByName(latestVersionName);
                                if(latest != null) {
                                    if(latest.isErrorStatus()) {
                                        Log.e(CapacitorUpdater.TAG, "Latest bundle already exists, and is in error state. Aborting update.");
                                        return;
                                    }
                                    if(latest.isDownloaded()){
                                        Log.e(CapacitorUpdater.TAG, "Latest bundle already exists and download is NOT required. Update will occur next time app moves to background.");
                                        CapacitorUpdaterPlugin.this.implementation.setNextVersion(latest.getId());
                                        return;
                                    }
                                }


                                new Thread(new Runnable(){
                                    @Override
                                    public void run() {
                                        try {
                                            Log.i(CapacitorUpdater.TAG, "New bundle: " + latestVersionName + " found. Current is: " + current.getVersionName() + ". Update will occur next time app moves to background.");

                                            final String url = (String) res.get("url");
                                            final BundleInfo next = CapacitorUpdaterPlugin.this.implementation.download(url, latestVersionName);

                                            CapacitorUpdaterPlugin.this.implementation.setNextVersion(next.getId());

                                            final JSObject updateAvailable = new JSObject();
                                            updateAvailable.put("bundle", next.toJSON());
                                            CapacitorUpdaterPlugin.this.notifyListeners("updateAvailable", updateAvailable);
                                        } catch (final Exception e) {
                                            Log.e(CapacitorUpdater.TAG, "error downloading file", e);
                                        }
                                    }
                                }).start();
                            } else {
                                Log.i(CapacitorUpdater.TAG, "No need to update, " + current + " is the latest bundle.");
                            }
                        } catch (final JSONException e) {
                            Log.e(CapacitorUpdater.TAG, "error parsing JSON", e);
                        }
                    });
                }
            }).start();
        }

        this.checkAppReady();
    }

    @Override // appMovedToBackground
    public void onActivityStopped(@NonNull final Activity activity) {
        Log.i(CapacitorUpdater.TAG, "Checking for pending update");
        try {
            final Boolean delayUpdate = this.prefs.getBoolean(DELAY_UPDATE, false);
            this.editor.putBoolean(DELAY_UPDATE, false);
            this.editor.commit();

            if (delayUpdate) {
                Log.i(CapacitorUpdater.TAG, "Update delayed to next backgrounding");
                return;
            }

            final BundleInfo fallback = this.implementation.getFallbackVersion();
            final BundleInfo current = this.implementation.getCurrentBundle();
            final BundleInfo next = this.implementation.getNextVersion();

            final Boolean success = current.getStatus() == BundleStatus.SUCCESS;

            Log.d(CapacitorUpdater.TAG, "Fallback bundle is: " + fallback);
            Log.d(CapacitorUpdater.TAG, "Current bundle is: " + current);

            if (next != null && !next.isErrorStatus() && (next.getId() != current.getId())) {
                // There is a next bundle waiting for activation
                Log.d(CapacitorUpdater.TAG, "Next bundle is: " + next.getVersionName());
                if (this.implementation.set(next) && this._reload()) {
                    Log.i(CapacitorUpdater.TAG, "Updated to bundle: " + next.getVersionName());
                    this.implementation.setNextVersion(null);
                } else {
                    Log.e(CapacitorUpdater.TAG, "Update to bundle: " + next.getVersionName() + " Failed!");
                }
            } else if (!success) {
                // There is a no next bundle, and the current bundle has failed

                if(!current.isBuiltin()) {
                    // Don't try to roll back the builtin bundle. Nothing we can do.

                    this.implementation.rollback(current);

                    Log.i(CapacitorUpdater.TAG, "Update failed: 'notifyAppReady()' was never called.");
                    Log.i(CapacitorUpdater.TAG, "Bundle: " + current + ", is in error state.");
                    Log.i(CapacitorUpdater.TAG, "Will fallback to: " + fallback + " on application restart.");
                    Log.i(CapacitorUpdater.TAG, "Did you forget to call 'notifyAppReady()' in your Capacitor App code?");
                    final JSObject ret = new JSObject();
                    ret.put("bundle", current);
                    this.notifyListeners("updateFailed", ret);
                    this.implementation.sendStats("revert", current);
                    if (!fallback.isBuiltin() && !fallback.equals(current)) {
                        final Boolean res = this.implementation.set(fallback);
                        if (res && this._reload()) {
                            Log.i(CapacitorUpdater.TAG, "Revert to bundle: " + fallback.getVersionName());
                        } else {
                            Log.e(CapacitorUpdater.TAG, "Revert to bundle: " + fallback.getVersionName() + " Failed!");
                        }
                    } else {
                        if (this._reset(false)) {
                            Log.i(CapacitorUpdater.TAG, "Reverted to 'builtin' bundle.");
                        }
                    }

                    if (this.autoDeleteFailed) {
                        Log.i(CapacitorUpdater.TAG, "Deleting failing bundle: " + current.getVersionName());
                        try {
                            final Boolean res = this.implementation.delete(current.getId());
                            if (res) {
                                Log.i(CapacitorUpdater.TAG, "Failed bundle deleted: " + current.getVersionName());
                            }
                        } catch (final IOException e) {
                            Log.e(CapacitorUpdater.TAG, "Failed to delete failed bundle: " + current.getVersionName(), e);
                        }
                    }
                } else {
                    // Nothing we can/should do by default if the 'builtin' bundle fails to call 'notifyAppReady()'.
                }

            } else if (!fallback.isBuiltin()) {
                // There is a no next bundle, and the current bundle has succeeded
                this.implementation.commit(current);

                if(this.autoDeletePrevious) {
                    Log.i(CapacitorUpdater.TAG, "Bundle successfully loaded: " + current);
                    try {
                        final Boolean res = this.implementation.delete(fallback.getVersionName());
                        if (res) {
                            Log.i(CapacitorUpdater.TAG, "Deleted previous bundle: " + fallback.getVersionName());
                        }
                    } catch (final IOException e) {
                        Log.e(CapacitorUpdater.TAG, "Failed to delete previous bundle: " + fallback.getVersionName(), e);
                    }
                }
            }
        }
        catch(final Exception e) {
            Log.e(CapacitorUpdater.TAG, "Error during onActivityStopped", e);
        }
    }

    private class DeferredNotifyAppReadyCheck implements Runnable {
        @Override
        public void run() {
            try {
                Log.i(CapacitorUpdater.TAG, "Wait for " + CapacitorUpdaterPlugin.this.appReadyTimeout + "ms, then check for notifyAppReady");
                Thread.sleep(CapacitorUpdaterPlugin.this.appReadyTimeout);
                // Automatically roll back to fallback version if notifyAppReady has not been called yet
                final BundleInfo current = CapacitorUpdaterPlugin.this.implementation.getCurrentBundle();
                if(current.isBuiltin()) {
                    Log.i(CapacitorUpdater.TAG, "Built-in bundle is active. Nothing to do.");
                    return;
                }

                if(BundleStatus.SUCCESS != current.getStatus()) {
                    Log.e(CapacitorUpdater.TAG, "notifyAppReady was not called, roll back current bundle: " + current.getId());
                    CapacitorUpdaterPlugin.this.implementation.rollback(current);
                    CapacitorUpdaterPlugin.this._reset(true);
                } else {
                    Log.i(CapacitorUpdater.TAG, "notifyAppReady was called. This is fine: " + current.getId());
                }

                CapacitorUpdaterPlugin.this.appReadyCheck = null;
            } catch (final InterruptedException e) {
                Log.e(CapacitorUpdater.TAG, DeferredNotifyAppReadyCheck.class.getName() + " was interrupted.");
            }
        }
    }

    // not use but necessary here to remove warnings
    @Override
    public void onActivityResumed(@NonNull final Activity activity) {
        // TODO: Implement background updating based on `backgroundUpdate` and `backgroundUpdateDelay` capacitor.config.ts settings
    }

    @Override
    public void onActivityPaused(@NonNull final Activity activity) {
        // TODO: Implement background updating based on `backgroundUpdate` and `backgroundUpdateDelay` capacitor.config.ts settings
    }
    @Override
    public void onActivityCreated(@NonNull final Activity activity, @Nullable final Bundle savedInstanceState) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull final Activity activity, @NonNull final Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull final Activity activity) {
    }
}
