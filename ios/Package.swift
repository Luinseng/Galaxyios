// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "Gearan",
    platforms: [.iOS(.v16), .macOS(.v13)],
    products: [
        .library(name: "GearanCore", targets: ["GearanCore"]),
    ],
    targets: [
        .target(name: "GearanCore", path: "Sources/GearanCore"),
        .testTarget(name: "GearanCoreTests", dependencies: ["GearanCore"], path: "Tests/GearanCoreTests",
                    resources: [.copy("vectors")]),
    ]
)
