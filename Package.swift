// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "CapgoCapacitorUpdater",
    platforms: [
        .iOS(.v12)
    ],
    products: [
        .library(
            name: "CapgoCapacitorUpdater",
            targets: ["CapgoCapacitorUpdater"]
        )
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", branch: "main"),
        .package(url: "https://github.com/ZipArchive/ZipArchive.git", from: "2.0.0"),
        .package(url: "https://github.com/Alamofire/Alamofire.git", from: "5.0.0"),
        .package(url: "https://github.com/mrackwitz/Version.git", from: "0.0.0")
    ],
    targets: [
        .target(
            name: "CapgoCapacitorUpdater",
            dependencies: [
                "capacitor-swift-pm",
                "ZipArchive",
                "Alamofire",
                "Version"
            ],
            path: "ios/Plugin"
        )
    ]
)