// swift-tools-version:5.1
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
        .package(url: "https://github.com/mrackwitz/Version.git", from: "1.0.0")
    ],
    targets: [
        .target(
            name: "CapgoCapacitorUpdater",
            dependencies: [
                "Capacitor",
                "SSZipArchive",
                "Alamofire",
                "Version"
            ],
            path: "ios/Plugin",
            sources: ["."],
            publicHeadersPath: ".",
            cSettings: [
                .headerSearchPath("include")
            ],
            swiftSettings: [
                .define("SWIFT_PACKAGE"),
                .unsafeFlags(["-swift-version", "5.1"])
            ]
        )
    ]
)