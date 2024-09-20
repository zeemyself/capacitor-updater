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
        .package(url: "https://github.com/ZipArchive/ZipArchive.git", exact: "2.4.3"),
        .package(url: "https://github.com/Alamofire/Alamofire.git", from: "5.0.0"),
        .package(url: "https://github.com/mrackwitz/Version.git", from: "0.0.0")
    ],
    targets: [
        .target(
            name: "CapgoCapacitorUpdater",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                "ZipArchive",
                "Alamofire",
                "Version"
            ],
            path: "ios/Plugin"
        )
    ]
)