//
//  CapacitorUpdaterNative.swift
//  CapgoCapacitorUpdater
//
//  Created by Swas Kunakorn on 16/7/24.
//

import Foundation
import Capacitor
import Alamofire

public class CapacitorUpdaterNative {
//    let capBundle = Bundle.init(identifier: "me.livingmobile.foodstoryowner.BetterCapacitor")
//    let capConfigUrl: URL? = capBundle?.url(
//        forResource: "capacitor.config",
//        withExtension: "json",
//        subdirectory: "build"
//    )
    private lazy var updateUrl = "http://localhost:1323/updates"
    public let TAG: String = "✨  Capacitor-updater:"
    public let CAP_SERVER_PATH: String = "serverBasePath"
    public var versionBuild: String = ""
    public var customId: String = ""
    public var PLUGIN_VERSION: String = ""
    public var timeout: Double = 20
    public var statsUrl: String = ""
    public var channelUrl: String = ""
    public var defaultChannel: String = ""
    public var appId: String = ""
    public var deviceID = UIDevice.current.identifierForVendor?.uuidString ?? ""
    public var privateKey: String = ""
    private let versionCode: String = Bundle.main.versionCode ?? ""
    private let versionOs = UIDevice.current.systemVersion
    private let isEmulator: () -> Bool = { false }
    private let isProd: () -> Bool = { false }
    
    public init() {}
    
    private func createInfoObject() -> InfoObject {
        return InfoObject(
            platform: "ios",
            device_id: self.deviceID,
            app_id: self.appId,
            custom_id: self.customId,
            version_build: self.versionBuild,
            version_code: self.versionCode,
            version_os: self.versionOs,
            version_name: CapacitorUpdater().getCurrentBundle().getVersionName(),
            plugin_version: self.PLUGIN_VERSION,
            is_emulator: self.isEmulator(),
            is_prod: self.isProd(),
            action: nil,
            channel: nil,
            defaultChannel: self.defaultChannel
        )
    }

//    struct LatestUpdateInfo {
//        var url = ""
//        var checksum = ""
//        var version: String?
//        var major: Bool?
//        var error: String?
//        var message: String?
//        var sessionKey: String?
//        var data: [String: String]?
//    }
    
    public func get() {
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
    
    public func checkLatest() -> AppVersion {
        let parameters: InfoObject = self.createInfoObject()
//        print("\(self.TAG) Auto-update parameters: \(parameters)")
        let request = AF.request(updateUrl, method: .post, parameters: parameters, encoder: JSONParameterEncoder.default, requestModifier: { $0.timeoutInterval = self.timeout })
        var latest = AppVersion()
        request.validate().responseDecodable(of: AppVersionDec.self) { response in
            switch response.result {
            case .success:
                if let url = response.value?.url {
                    latest.url = url
                }
                if let checksum = response.value?.checksum {
                    latest.checksum = checksum
                }
                if let version = response.value?.version {
                    latest.version = version
                }
                if let major = response.value?.major {
                    latest.major = major
                }
                if let error = response.value?.error {
                    latest.error = error
                }
                if let message = response.value?.message {
                    latest.message = message
                }
                if let sessionKey = response.value?.session_key {
                    latest.sessionKey = sessionKey
                }
                if let data = response.value?.data {
                    latest.data = data
                }
            case let .failure(error):
                print("\(self.TAG) Error getting Latest", response.value ?? "", error )
                latest.message = "Error getting Latest \(String(describing: response.value))"
                latest.error = "response_error"
            }
            
        }
        return latest
    }
    
    public func download(url: URL) {
        
    }
}
