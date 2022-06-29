/// <reference types="@capacitor/cli" />
import type { PluginListenerHandle } from '@capacitor/core';

declare module '@capacitor/cli' {
  export interface PluginsConfig {
    /**
     * These configuration values are available:
     */
    CapacitorUpdater?: {
      /**
       * Configure the number of milliseconds the native plugin should wait before considering an update 'failed'.
       *
       * Only available for Android and iOS.
       *
       * @default 10000 // (10 seconds)
       * @example 1000 // (1 second)
       */
      appReadyTimeout?: number;

      /**
       * Configure whether the plugin should use automatically delete failed versions.
       *
       * Only available for Android and iOS.
       *
       * @default true
       * @example false
       */
      autoDeleteFailed?: boolean;

      /**
       * Configure whether the plugin should use automatically delete previous versions after a successful update.
       *
       * Only available for Android and iOS.
       *
       * @default true
       * @example false
       */
      autoDeletePrevious?: boolean;

      /**
       * Configure whether the plugin should use Auto Update via an update server.
       *
       * Only available for Android and iOS.
       *
       * @default false
       * @example false
       */
      autoUpdate?: boolean;

      /**
       * Configure the URL / endpoint to which update checks are sent. 
       *
       * Only available for Android and iOS.
       *
       * @default https://capgo.app/api/auto_update
       * @example https://example.com/api/auto_update
       */
      autoUpdateUrl?: string;

      /**
       * Automatically delete previous downloaded bundles when a newer native version is installed to the device.
       *
       * Only available for Android and iOS.
       *
       * @default true
       * @example false
       */
      resetWhenUpdate?: boolean;

      /**
       * Configure the URL / endpoint to which update statistics are sent. 
       *
       * Only available for Android and iOS.
       *
       * @default https://capgo.app/api/stats
       * @example https://example.com/api/stats
       */
      statsUrl?: string;
    };
  }
}


export interface DownloadEvent {
  /**
   * Current status of download, between 0 and 100.
   *
   * @since  4.0.0
   */
  percent: number;
  version: BundleInfo;
}
export interface MajorAvailableEvent {
  /**
   * Emit when a new major version is available.
   *
   * @since  4.0.0
   */
  version: BundleInfo;
}
export interface UpdateAvailableEvent {
  /**
   * Emit when a new update is available.
   *
   * @since  4.0.0
   */
  version: BundleInfo;
}

export interface UpdateFailedEvent {
  /**
   * Emit when a update failed to install.
   *
   * @since 4.0.0
   */
  version: BundleInfo;
}

export interface BundleInfo {
  id: string;
  version: string;
  downloaded: string;
  status: BundleStatus
}

export type BundleStatus = 'success' | 'error' | 'pending' | 'downloading';

export type DownloadChangeListener = (state: DownloadEvent) => void;
export type MajorAvailableListener = (state: MajorAvailableEvent) => void;
export type UpdateAvailableListener = (state: UpdateAvailableEvent) => void;
export type UpdateFailedListener = (state: UpdateFailedEvent) => void;




export interface CapacitorUpdaterPlugin {

  /**
   * Notify Capacitor Updater that the current bundle is working (a rollback will occur of this method is not called on every app launch)
   *
   * @returns {Promise<void>} an Promise resolved directly
   * @throws An error if the something went wrong
   */
  notifyAppReady(): Promise<BundleInfo>;

  /**
   * Download a new version from the provided URL, it should be a zip file, with files inside or with a unique id inside with all your files
   *
   * @returns {Promise<BundleInfo>} The {@link BundleInfo} for the specified version.
   * @param url The URL of the bundle zip file (e.g: dist.zip) to be downloaded. (This can be any URL. E.g: Amazon S3, a github tag, any other place you've hosted your bundle.)
   * @param version (optional) set the name of this version
   * @example https://example.com/versions/{version}/dist.zip 
   */
  download(options: { url: string, version?: string }): Promise<BundleInfo>;

  /**
   * Set the next bundle version to be used when the app is reloaded.
   *
   * @returns {Promise<BundleInfo>} The {@link BundleInfo} for the specified version.
   * @param version The version to set as current, next time the app is reloaded. See {@link BundleInfo.version}
   * @throws An error if there are is no index.html file inside the version id.
   */
  next(options: { id: string }): Promise<BundleInfo>;

  /**
   * Set the current bundle version and immediately reloads the app.
   *
   * @param version The version to set as current. See {@link BundleInfo.version}
   * @returns {Promise<Void>} An empty promise.
   * @throws An error if there are is no index.html file inside the version id.
   */
  set(options: { id: string }): Promise<void>;

  /**
   * Delete version in storage
   *
   * @returns {Promise<void>} an empty Promise when the version is deleted
   * @param version The version to delete (note, this is the version, NOT the version name)
   * @throws An error if the something went wrong
   */
  delete(options: { id: string }): Promise<void>;

  /**
   * Get all available versions
   *
   * @returns {Promise<{version: BundleInfo[]}>} an Promise witht the version list
   * @throws An error if the something went wrong
   */
  list(): Promise<{ versions: BundleInfo[] }>;

  /**
   * Set the `builtin` version (the one sent to Apple store / Google play store ) as current version
   *
   * @returns {Promise<void>} an empty Promise
   * @param toLastSuccessful [false] if yes it reset to to the last successfully loaded bundle version instead of `builtin`
   * @throws An error if the something went wrong
   */
  reset(options?: { toLastSuccessful?: boolean }): Promise<void>;

  /**
   * Get the current version, if none are set it returns `builtin`, currentNative is the original version install on the device
   *
   * @returns {Promise<{ current: string, currentNative: string }>} an Promise with the current version name
   * @throws An error if the something went wrong
   */
  current(): Promise<{ bundle: BundleInfo, native: string }>;

  /**
   * Reload the view
   *
   * @returns {Promise<void>} an Promise resolved when the view is reloaded
   * @throws An error if the something went wrong
   */
  reload(): Promise<void>;

  /**
   * Set delay to skip updates in the next time the app goes into the background
   *
   * @returns {Promise<void>} an Promise resolved directly
   * @throws An error if the something went wrong
   */
  setDelay(options: {delay: boolean}): Promise<void>;

  /**
   * Listen for download event in the App, let you know when the download is started, loading and finished
   *
   * @since 2.0.11
   */
  addListener(
    eventName: 'download',
    listenerFunc: DownloadChangeListener,
  ): Promise<PluginListenerHandle> & PluginListenerHandle;

  /**
   * Listen for Major update event in the App, let you know when major update is blocked by setting disableAutoUpdateBreaking
   *
   * @since 2.3.0
   */
  addListener(
    eventName: 'majorAvailable',
    listenerFunc: MajorAvailableListener,
  ): Promise<PluginListenerHandle> & PluginListenerHandle;

  /**
   * Listen for update event in the App, let you know when update is ready to install at next app start
   *
   * @since 2.3.0
   */
  addListener(
    eventName: 'updateAvailable',
    listenerFunc: UpdateAvailableListener,
  ): Promise<PluginListenerHandle> & PluginListenerHandle;

    /**
   * Listen for update event in the App, let you know when update is ready to install at next app start
   *
   * @since 2.3.0
   */
  addListener(
      eventName: 'updateFailed',
      listenerFunc: UpdateFailedListener,
    ): Promise<PluginListenerHandle> & PluginListenerHandle;

  /**
   * Get unique ID used to identify device (sent to auto update server)
   *
   * @returns {Promise<{ id: string }>} an Promise with id for this device
   * @throws An error if the something went wrong
   */
  getId(): Promise<{ id: string }>;

  /**
   * Get the native Capacitor Updater plugin version (sent to auto update server)
   *
   * @returns {Promise<{ id: string }>} an Promise with version for this device
   * @throws An error if the something went wrong
   */
  getPluginVersion(): Promise<{ version: string }>;

  /**
   * Get the state of auto update config. This will return `false` in manual mode.
   *
   * @returns {Promise<{enabled: boolean}>} The status for auto update.
   * @throws An error if the something went wrong
   */
  isAutoUpdateEnabled(): Promise<{ enabled: boolean }>;
}
