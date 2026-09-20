# Gearan iOS — exact Xcode setup (iPhone companion)

GearanCore (Swift package) runs `swift test` on macOS. The SwiftUI +
CoreBluetooth app (`Sources/GearanApp/`) needs an iOS Xcode project.

## 1. Open the existing project (macOS, Xcode 15+)

Open `ios/Gearan.xcodeproj` (scheme `Gearan`, targets `GearanCore` + `Gearan`).
No need to create anything: sources, Info.plist and the shared scheme are committed.

1. Signing & Capabilities → Team → your Apple ID.
2. Bundle ID `com.gearan.ios` (change it if already taken by someone else).
3. Minimum iOS 16 (already set: `IPHONEOS_DEPLOYMENT_TARGET = 16.0`).
4. Verify `GearanCore` import resolves in the app files (framework target).

## 2. Info.plist (exact keys)

- `NSBluetoothAlwaysUsageDescription` (String):
  "Gearan uses Bluetooth to find and sync your Galaxy Watch4 Classic."
- `UIBackgroundModes` (Array) → item `bluetooth-central`
  (auto-reconnect when locked / backgrounded; works with the restore
  identifier `com.gearan.ios.central` in `BLEScanner`).
- No location keys needed (scan is service-UUID-filtered, `neverForLocation`
  semantics; iOS never exposes MAC to the app).

## 3. Build & run

- `cd ios && swift test` — GearanCore unit + golden-vector tests (macOS).
- In Xcode: select your physical iPhone (CoreBluetooth LE scan is not
  meaningful in Simulator for this use), Product → Run.
- First screen without pairing: Gearan → Add Watch (scan) or BLE Scan Debug
  (state / RSSI / peripheral UUID / service UUIDs).

## 4. What NOT to do

- Do not call any `createBond()` — it does not exist on iOS. If the Watch
  exposes encrypted characteristics, iOS bonds automatically on access.
- Do not show or persist MAC addresses: use `peripheral.identifier` (UUID)
  and, after pairing, `TrustedWatch.gearDeviceId`.
- Pairing timeout 120 s; SAS display-only, never a key.
