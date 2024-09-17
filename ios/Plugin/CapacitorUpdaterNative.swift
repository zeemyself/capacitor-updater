//
//  CapacitorUpdaterNative.swift
//  CapgoCapacitorUpdater
//
//  Created by Swas Kunakorn on 16/7/24.
//

import Foundation
import Capacitor
import Alamofire
import Version

public class CapacitorUpdaterNative {
    public static let shared :CapacitorUpdaterNative = .init()
    public static var isRunning = false
    public var capacitorUpdater = CapacitorUpdater()
//    let capBundle = Bundle.init(identifier: "me.livingmobile.foodstoryowner.BetterCapacitor")
//    let capConfigUrl: URL? = capBundle?.url(
//        forResource: "capacitor.config",
//        withExtension: "json",
//        subdirectory: "build"
//    )
    private lazy var updateUrl = "http://localhost:8080/updates"
    private var currentVersionNative: Version = "0.0.0"
    public let TAG: String = "✨  Capacitor-updater-native:"
    public let CAP_SERVER_PATH: String = "serverBasePath"
    public var versionBuild: String = Bundle.main.versionName ?? ""
    public var appId: String = Bundle.main.infoDictionary?["CFBundleIdentifier"] as? String ?? ""
    private let versionCode: String = Bundle.main.versionCode ?? ""
    private let periodCheckDelay = 60
    private let autoUpdate = true
    private var directUpdate = true
    private var backgroundWork: DispatchWorkItem?
    private var taskRunning = false
    private var backgroundTaskID: UIBackgroundTaskIdentifier = UIBackgroundTaskIdentifier.invalid
    
    private let capacitorUpdaterPlugin = CustomCapacitorUpdaterPlugin()
    
    public init() {}
    
    public func load() {
        if CapacitorUpdaterNative.isRunning {
            print("\(self.TAG) Already running will ignore")
            // Already running ignore
            return
        }
        print("\(self.TAG) Running load")
        CapacitorUpdaterNative.isRunning = true
        let versionName = Bundle.main.versionName
        do {
            currentVersionNative = try Version(versionName ?? "0.0.0")
        } catch {
            print("\(TAG) Cannot parse versionName \(versionName)")
        }
        
        self.capacitorUpdater.versionBuild = Bundle.main.versionName ?? ""
        self.capacitorUpdater.appId = Bundle.main.infoDictionary?["CFBundleIdentifier"] as? String ?? ""
        
        print("\(self.TAG) Current bundle: \(self.capacitorUpdater.getCurrentBundle().toJSON())")
        
        capacitorUpdater.autoReset()
        self.appMovedToForeground()
        self.cleanupObsoleteVersions()
        self.checkForUpdateAfterDelay()
    }
    
    private func appMovedToForeground() {
        let current: BundleInfo = self.capacitorUpdater.getCurrentBundle()
        if backgroundWork != nil && taskRunning {
            backgroundWork!.cancel()
            print("\(TAG) Background Timer Task canceled, Activity resumed before timer completes")
        }
        if self.autoUpdate {
            self.backgroundDownload()
        } else {
            print("\(TAG) Auto update is disabled")
//            self.sendReadyToJs(current: current, msg: "disabled")
        }
//        self.checkAppReady()
    }
    
    
    /**
                Mostyle reset to builtin version when native version upgraded
     */
    private func cleanupObsoleteVersions() {
        var LatestVersionNative: Version = "0.0.0"
        do {
            LatestVersionNative = try Version(UserDefaults.standard.string(forKey: "LatestVersionNative") ?? "0.0.0")
        } catch {
            print("\(TAG) Cannot get version native \(currentVersionNative)")
        }
        if LatestVersionNative != "0.0.0" && self.currentVersionNative.description != LatestVersionNative.description {
            _ = self._reset(toLastSuccessful: false)
            let res = capacitorUpdater.list()
            res.forEach { version in
                print("\(TAG) Deleting obsolete bundle: \(version)")
                let res = capacitorUpdater.delete(id: version.getId())
                if !res {
                    print("\(TAG) Delete failed, id \(version.getId()) doesn't exist")
                }
            }
        }
        UserDefaults.standard.set( self.currentVersionNative.description, forKey: "LatestVersionNative")
        UserDefaults.standard.synchronize()
    }
    
    func _reset(toLastSuccessful: Bool) -> Bool {
        
        let fallback = capacitorUpdater.getFallbackBundle()
        if toLastSuccessful && !fallback.isBuiltin() {
            return capacitorUpdater.set(bundle: fallback)
        }
        print("\(TAG) Resetting to builtin version")
        capacitorUpdater.reset()
        
        return true
    }
    
    private func checkForUpdateAfterDelay() {
//        if periodCheckDelay == 0 || !self._isAutoUpdateEnabled() {
//            return
//        }
        print("\(self.TAG) checkForUpdateAfterDelay()")
        guard let url = URL(string: self.updateUrl) else {
            print("\(self.TAG) Error no url or wrong format")
            return
        }
        let timer = Timer.scheduledTimer(withTimeInterval: TimeInterval(periodCheckDelay), repeats: true) { _ in
            print("\(self.TAG) schedule timer with delay: \(self.periodCheckDelay)")
            DispatchQueue.global(qos: .background).async {
                let res = self.capacitorUpdater.getLatest(url: url)
                let current = self.capacitorUpdater.getCurrentBundle()

                if res.version != current.getVersionName() {
                    print("\(self.TAG) New version found: \(res.version) from current \(current.getVersionName())")
                    self.backgroundDownload()
                }
                print("\(self.TAG) Version: \(current.getVersionName()) Result: \(res.version)")
            }
        }
        print("\(self.TAG) run loop \(timer.description)")
        RunLoop.current.add(timer, forMode: .default)
    }
    
    private func backgroundDownload() {
        print("\(self.TAG) backgroundDownload()")
        guard let url = URL(string: self.updateUrl) else {
            print("\(self.capacitorUpdater.TAG) Error no url or wrong format")
            return
        }
        DispatchQueue.global(qos: .background).async {
            self.backgroundTaskID = UIApplication.shared.beginBackgroundTask(withName: "Finish Download Tasks") {
                // End the task if time expires.
                self.endBackGroundTask()
            }
            print("\(self.capacitorUpdater.TAG) Check for update via \(self.updateUrl)")
            let res = self.capacitorUpdater.getLatest(url: url)
            let current = self.capacitorUpdater.getCurrentBundle()

            if (res.message) != nil {
                print("\(self.capacitorUpdater.TAG) API message: \(res.message ?? "")")
//                if res.major == true {
//                    self.notifyListeners("majorAvailable", data: ["version": res.version])
//                }
                self.endBackGroundTaskWithNotif(msg: res.message ?? "", latestVersionName: res.version, current: current, error: false)
                return
            }
            let sessionKey = res.sessionKey ?? ""
            guard let downloadUrl = URL(string: res.url) else {
                print("\(self.capacitorUpdater.TAG) Error no url or wrong format")
                self.endBackGroundTaskWithNotif(msg: "Error no url or wrong format", latestVersionName: res.version, current: current)
                return
            }
            let latestVersionName = res.version
            if latestVersionName != "" && current.getVersionName() != latestVersionName {
                do {
                    print("\(self.capacitorUpdater.TAG) New bundle: \(latestVersionName) found. Current is: \(current.getVersionName()).")
                    var nextImpl = self.capacitorUpdater.getBundleInfoByVersionName(version: latestVersionName)
                    if nextImpl == nil || ((nextImpl?.isDeleted()) != nil) {
                        if (nextImpl?.isDeleted()) != nil {
                            print("\(self.capacitorUpdater.TAG) Latest bundle already exists and will be deleted, download will overwrite it.")
                            let res = self.capacitorUpdater.delete(id: nextImpl!.getId(), removeInfo: true)
                            if res {
                                print("\(self.capacitorUpdater.TAG) Delete version deleted: \(nextImpl!.toString())")
                            } else {
                                print("\(self.capacitorUpdater.TAG) Failed to delete failed bundle: \(nextImpl!.toString())")
                            }
                        }
                        nextImpl = try self.capacitorUpdater.download(url: downloadUrl, version: latestVersionName, sessionKey: sessionKey)
                    }
                    guard let next = nextImpl else {
                        print("\(self.capacitorUpdater.TAG) Error downloading file")
                        self.endBackGroundTaskWithNotif(msg: "Error downloading file", latestVersionName: latestVersionName, current: current)
                        return
                    }
                    if next.isErrorStatus() {
                        print("\(self.capacitorUpdater.TAG) Latest version is in error state. Aborting update.")
                        self.endBackGroundTaskWithNotif(msg: "Latest version is in error state. Aborting update.", latestVersionName: latestVersionName, current: current)
                        return
                    }
                    if res.checksum != "" && next.getChecksum() != res.checksum {
                        print("\(self.capacitorUpdater.TAG) Error checksum", next.getChecksum(), res.checksum)
                        self.capacitorUpdater.sendStats(action: "checksum_fail", versionName: next.getVersionName())
                        let id = next.getId()
                        let resDel = self.capacitorUpdater.delete(id: id)
                        if !resDel {
                            print("\(self.capacitorUpdater.TAG) Delete failed, id \(id) doesn't exist")
                        }
                        self.endBackGroundTaskWithNotif(msg: "Error checksum", latestVersionName: latestVersionName, current: current)
                        return
                    }
                    if self.directUpdate {
                        _ = self.capacitorUpdater.set(bundle: next)
                        _ = self._reload()
                        self.directUpdate = false
                        self.endBackGroundTaskWithNotif(msg: "update installed", latestVersionName: latestVersionName, current: current, error: false)
                    } else {
//                        self.notifyListeners("updateAvailable", data: ["bundle": next.toJSON()])
                        _ = self.capacitorUpdater.setNextBundle(next: next.getId())
                        self.endBackGroundTaskWithNotif(msg: "update downloaded, will install next background", latestVersionName: latestVersionName, current: current, error: false)
                    }
                    return
                } catch {
                    print("\(self.capacitorUpdater.TAG) Error downloading file", error.localizedDescription)
                    let current: BundleInfo = self.capacitorUpdater.getCurrentBundle()
                    self.endBackGroundTaskWithNotif(msg: "Error downloading file", latestVersionName: latestVersionName, current: current)
                    return
                }
            } else {
                print("\(self.capacitorUpdater.TAG) No need to update, \(current.getId()) is the latest bundle.")
                self.endBackGroundTaskWithNotif(msg: "No need to update, \(current.getId()) is the latest bundle.", latestVersionName: latestVersionName, current: current, error: false)
                return
            }
        }
    }
    
    private func endBackGroundTask() {
        UIApplication.shared.endBackgroundTask(self.backgroundTaskID)
        self.backgroundTaskID = UIBackgroundTaskIdentifier.invalid
    }
    
    private func endBackGroundTaskWithNotif(msg: String, latestVersionName: String, current: BundleInfo, error: Bool = true) {
        endBackGroundTask()
    }
    
    private func _reload() {
        // not reload
    }
    
    
    public func get() {
        self.capacitorUpdaterPlugin.load()
        
        return
        
        
        let capacitorUpdater = CapacitorUpdater()
        capacitorUpdater.versionBuild = Bundle.main.versionName ?? ""
        capacitorUpdater.appId = Bundle.main.infoDictionary?["CFBundleIdentifier"] as? String ?? ""
        
        let latestInfo = capacitorUpdater.getLatest(url: URL(string: updateUrl)!)
        let latestUrl = latestInfo.url
        let latestVersion = latestInfo.version
        print("\(self.TAG) Finish get latest information \(latestInfo.toDict())")
        print("\(self.TAG) Finish get latest information url \(latestUrl) version \(latestVersion)")
        
        if latestUrl.isEmpty {
            print("\(self.TAG) Already on latest version")
            return
        }
        
        //Download
        var downloadedBundle: BundleInfo?
        do {
            downloadedBundle = try capacitorUpdater.download(url: URL(string: latestUrl)!, version: latestVersion, sessionKey: "")
            print("\(self.TAG) Finish downloaded bundle Bundle: \(downloadedBundle)")
            print("\(self.TAG) Finish downloaded bundle BundleJSON: \(downloadedBundle!.toJSON())")
            print("\(self.TAG) Finish downloaded bundle BundleString: \(downloadedBundle!.toString())")
        } catch {
            print("\(self.TAG) Failed to download from: \(String(describing: latestUrl)) \(error.localizedDescription)")
        }
        
        guard let downloadedBundle = downloadedBundle else {
            print("\(self.TAG) Failed to parse bundle")
            return
        }
        
        let success = capacitorUpdater.set(bundle: downloadedBundle)
        print("\(self.TAG) Setting bundle result: \(success)")
    
    }
}

class CustomCAPBridgeViewController: CAPBridgeViewController {
    let Constant = Bundle.init(identifier: "me.livingmobile.foodstoryowner.BetterCapacitor")!
    override func instanceDescriptor() -> InstanceDescriptor {
        guard let resourceURL = Constant.url(forResource: "build", withExtension: nil) else {
//            LoggerLog(type: .error, "Resource not found")
            return InstanceDescriptor()
        }
        guard let configURL = Constant.url(
            forResource: "capacitor.config",
            withExtension: "json",
            subdirectory: "build"
        ) else {
//            LoggerLog(type: .error, "config not found")
            return InstanceDescriptor()
        }
        guard let cordovaConfig = Constant.url(
            forResource: "config",
            withExtension: "xml",
            subdirectory: "build"
        ) else {
//            LoggerLog(type: .error, "Cordova not found")
            return InstanceDescriptor()
        }
        let descriptor = InstanceDescriptor(
            at: resourceURL,
            configuration: configURL,
            cordovaConfiguration: cordovaConfig
        )

        guard !isNewBinary, !descriptor.cordovaDeployDisabled else { return descriptor }

        guard let persistedPath = UserDefaults.standard.string(forKey: "serverBasePath"),
              persistedPath != "" else { return descriptor }

        guard let libPath = NSSearchPathForDirectoriesInDomains(.libraryDirectory, .userDomainMask, true).first
        else { return descriptor }

        descriptor.appLocation = URL(fileURLWithPath: libPath, isDirectory: true)
            .appendingPathComponent("NoCloud")
            .appendingPathComponent("ionic_built_snapshots")
            .appendingPathComponent(URL(fileURLWithPath: persistedPath, isDirectory: true).lastPathComponent)
        return descriptor
    }
}


class CustomCapacitorUpdaterPlugin: CapacitorUpdaterPlugin {
    override func getConfig() -> PluginConfig {
        let vc = CustomCAPBridgeViewController()
        vc.loadView()
//        vc.bridge?.config = 
        let config = (vc.bridge?.config.getPluginConfig("CapacitorUpdater"))!
        print("✨CONFIG: \(config.getConfigJSON())")
//        vc.
        return config
//        return PluginConfig(config: .init())
    }
}
