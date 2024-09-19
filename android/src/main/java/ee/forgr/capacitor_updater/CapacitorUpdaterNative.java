package ee.forgr.capacitor_updater;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.android.volley.toolbox.Volley;
import com.getcapacitor.JSObject;
import com.getcapacitor.plugin.WebView;

import org.json.JSONException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.Timer;
import java.util.TimerTask;

import io.github.g00fy2.versioncompare.Version;

public class CapacitorUpdaterNative {
	private final String TAG = "CapacitorUpdaterNative";
	private SharedPreferences prefs;
	private SharedPreferences.Editor editor;
	private final CapacitorUpdater updater = new CapacitorUpdater();
	private String updateUrl = "http://localhost:8080/updates";
	private String packageName;
	private Version currentVersionNative;
	private final Integer periodCheckDelay = 60 * 1000;
	private volatile Thread backgroundDownloadTask;

	private void initializeUpdater(Context context) throws
													PackageManager.NameNotFoundException {
		updater.requestQueue = Volley.newRequestQueue(context);
		prefs = context.getSharedPreferences(WebView.WEBVIEW_PREFS_NAME, Context.MODE_PRIVATE);
		PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(),0);
		editor = prefs.edit();
		updater.editor = this.editor;
		updater.prefs = prefs;
		updater.documentsDir = context.getFilesDir();
		this.packageName = InternalUtils.getPackageName(
				context.getPackageManager(),
				context.getPackageName()
		);
		this.updateUrl = isProduction() ? "http://localhost:8080/updatestest" : "http://localhost:8080/updates";
		updater.appId = InternalUtils.getPackageName(
				context.getPackageManager(),
				context.getPackageName()
		);
		updater.versionBuild = packageInfo.versionName;
		updater.versionCode = Integer.toString(packageInfo.versionCode);
		currentVersionNative = new Version(packageInfo.versionName);
		updater.deviceID = prefs.getString(
				"appUUID",
				UUID.randomUUID().toString()
		);
		updater.directUpdate = true;
	}

	/**
	 * Updates the capacitor webview to the latest version if available.
	 * 
	 * @param context the Android context (android.content.Context)
	 * @throws PackageManager.NameNotFoundException if the package name is not found
	 */
	public void update(Context context) throws PackageManager.NameNotFoundException {
		initializeUpdater(context);

		updater.autoReset();
		cleanupObsoleteVersions();
		checkForUpdateAfterDelay();

	}

	/**
	 * Cleanup obsolete versions (especially after native version change (reset to builtin version))
	 */
	private void cleanupObsoleteVersions() {
		try {
			final Version previous = new Version(
					this.prefs.getString("LatestVersionNative", "")
			);
			try {
				if (
						!"".equals(previous.getOriginalString()) &&
						!Objects.equals(
								this.currentVersionNative.getOriginalString(),
								previous.getOriginalString()
						)
				) {
					Log.i(
							TAG,
							"New native version detected: " + this.currentVersionNative
					);
					this.updater.reset(true);
					final List<BundleInfo> installed = this.updater.list();
					for (final BundleInfo bundle : installed) {
						try {
							Log.i(
									TAG,
									"Deleting obsolete bundle: " + bundle.getId()
							);
							this.updater.delete(bundle.getId());
						} catch (final Exception e) {
							Log.e(
									TAG,
									"Failed to delete: " + bundle.getId(),
									e
							);
						}
					}
				}
			} catch (final Exception e) {
				Log.e(
						TAG,
						"Could not determine the current version",
						e
				);
			}
		} catch (final Exception e) {
			Log.e(
					TAG,
					"Error calculating previous native version",
					e
			);
		}
		this.editor.putString(
				"LatestVersionNative",
				this.currentVersionNative.toString()
		);
		this.editor.commit();
	}

	private void checkForUpdateAfterDelay() {
		if (this.periodCheckDelay == 0) {
			return;
		}
		final Timer timer = new Timer();
		timer.schedule(
				new TimerTask() {
					@Override
					public void run() {
						try {
							updater.getLatest(
									CapacitorUpdaterNative.this.updateUrl,
									res -> {
										if (res.has("error")) {
											Log.e(
													TAG,
													Objects.requireNonNull(res.getString("error"))
											);
										} else if (res.has("version")) {
											String newVersion = res.getString("version");
											String currentVersion = String.valueOf(
													CapacitorUpdaterNative.this.updater.getCurrentBundle()
											);
											if (!Objects.equals(newVersion, currentVersion)) {
												Log.i(
														TAG,
														"New version found: " + newVersion
												);
												CapacitorUpdaterNative.this.backgroundDownload();
											}
										}
									}
							);
						} catch (final Exception e) {
							Log.e(TAG, "Failed to check for update", e);
						}
					}
				},
				this.periodCheckDelay,
				this.periodCheckDelay
		);
	}

	private Thread backgroundDownload() {
		String messageUpdate = this.updater.directUpdate
				? "Update will occur now."
				: "Update will occur next time app moves to background.";
		return startNewThread(() -> {
			Log.i(
					TAG,
					"Check for update via: " + this.updateUrl
			);
			this.updater.getLatest(
					this.updateUrl,
					res -> {
						final BundleInfo current =
								this.updater.getCurrentBundle();
						try {
							if (res.has("message")) {
								Log.i(
										TAG,
										"API message: " + res.get("message")
								);
								if (
										res.has("major") &&
										res.getBoolean("major") &&
										res.has("version")
								) {
									final JSObject majorAvailable = new JSObject();
									majorAvailable.put("version", res.getString("version"));
								}
								this.endBackGroundTaskWithNotif(
										res.getString("message"),
										current.getVersionName(),
										current,
										true
								);
								return;
							}

							if (
									!res.has("url") ||
									!this.isValidURL(res.getString("url"))
							) {
								Log.e(TAG, "Error no url or wrong format");
								this.endBackGroundTaskWithNotif(
										"Error no url or wrong format",
										current.getVersionName(),
										current,
										true
								);
								return;
							}
							final String latestVersionName = res.getString("version");

							if (
									latestVersionName != null &&
									!latestVersionName.isEmpty() &&
									!current.getVersionName().equals(latestVersionName)
							) {
								final BundleInfo latest =
										this.updater.getBundleInfoByName(
												latestVersionName
										);
								if (latest != null) {
									final JSObject ret = new JSObject();
									ret.put("bundle", latest.toJSON());
									if (latest.isErrorStatus()) {
										Log.e(
												TAG,
												"Latest bundle already exists, and is in error state. Aborting update."
										);
										this.endBackGroundTaskWithNotif(
												"Latest bundle already exists, and is in error state. Aborting update.",
												latestVersionName,
												current,
												true
										);
										return;
									}
									if (latest.isDownloaded()) {
										Log.i(
												TAG,
												"Latest bundle already exists and download is NOT required. " +
												messageUpdate
										);
										if (
												this.updater.directUpdate
										) {
											this.updater.set(latest);
											this.endBackGroundTaskWithNotif(
													"Update installed",
													latestVersionName,
													latest,
													false
											);
										} else {
											Log.i(TAG, "Update will be installed next time app moves to background.");
										}
										return;
									}
									if (latest.isDeleted()) {
										Log.i(
												TAG,
												"Latest bundle already exists and will be deleted, download will overwrite it."
										);
										try {
											final Boolean deleted =
													this.updater.delete(
															latest.getId(),
															true
													);
											if (deleted) {
												Log.i(
														TAG,
														"Failed bundle deleted: " + latest.getVersionName()
												);
											}
										} catch (final IOException e) {
											Log.e(
													TAG,
													"Failed to delete failed bundle: " +
													latest.getVersionName(),
													e
											);
										}
									}
								}
								startNewThread(() -> {
									try {
										Log.i(
												TAG,
												"New bundle: " +
												latestVersionName +
												" found. Current is: " +
												current.getVersionName() +
												". " +
												messageUpdate
										);

										final String url = res.getString("url");
										final String sessionKey = res.has("sessionKey")
												? res.getString("sessionKey")
												: "";
										final String checksum = res.has("checksum")
												? res.getString("checksum")
												: "";
										// Download without background
										this.updater.download(
												url,
												latestVersionName,
												sessionKey,
												checksum
										);
									} catch (final Exception e) {
										Log.e(TAG, "error downloading file", e);
										this.endBackGroundTaskWithNotif(
												"Error downloading file",
												latestVersionName,
												this.updater.getCurrentBundle(),
												true
										);
									}
								});
							} else {
								Log.i(
										TAG,
										"No need to update, " +
										current.getId() +
										" is the latest bundle."
								);
								Log.i(
										TAG,
										"latestVersionName != null : " + (latestVersionName != null) +
										"   !latestVersionName.isEmpty(): " + !latestVersionName.isEmpty() +
										"   !current.getVersionName().equals(latestVersionName): " + !current.getVersionName().equals(latestVersionName) +
										"   current.getVersionName():" + current.getVersionName() +
										"    latestVersionName:" + latestVersionName
								);
								this.endBackGroundTaskWithNotif(
										"No need to update",
										latestVersionName,
										current,
										false
								);
							}
						} catch (final JSONException e) {
							Log.e(TAG, "error parsing JSON", e);
							this.endBackGroundTaskWithNotif(
									"Error parsing JSON",
									current.getVersionName(),
									current,
									true
							);
						}
					}
			);
		});
	}
	private void endBackGroundTaskWithNotif(
			String msg,
			String latestVersionName,
			BundleInfo current,
			Boolean error
	) {
		if (error) {
			Log.i(TAG, "endBackGroundTaskWithNotif error" + error);
			this.updater.sendStats("download_fail", current.getVersionName());
			final JSObject ret = new JSObject();
			ret.put("version", latestVersionName);
		}
		final JSObject ret = new JSObject();
		ret.put("bundle", current.toJSON());
		this.backgroundDownloadTask = null;
		Log.i(TAG, "endBackGroundTaskWithNotif " + msg);
	}

	private boolean isProduction() {
		Log.d("CapacitorUpdaterNative", "isProduction() " + this.packageName);
		if (this.packageName == null) {
			Log.d("CapacitorUpdaterNative", "isProduction() packageName null return false" );
			return false;
		}
		Log.d("CapacitorUpdaterNative", "isProduction() return " + "com.wongnai.android.pos".equals(this.packageName));
		return "com.wongnai.android.pos".equals(this.packageName);
	}

	private boolean isValidURL(String urlStr) {
		try {
			new URL(urlStr);
			return true;
		} catch (MalformedURLException e) {
			return false;
		}
	}

	private Thread startNewThread(Runnable task) {
		Thread thread = new Thread(task);
		thread.start();
		return thread;
	}

}
