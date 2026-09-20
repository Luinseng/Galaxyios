# Gearan iOS app — open, build, install (iPhone companion)

The repo now contains a real Xcode project: `ios/Gearan.xcodeproj`
(scheme `Gearan`, targets `GearanCore` framework + `Gearan` app).
GearanCore stays a Swift package too (`swift test` on macOS).

## Open & run (macOS, Xcode 15+)
1. Open `ios/Gearan.xcodeproj`, select scheme `Gearan`.
2. Signing & Capabilities → Team → your Apple ID (Bundle ID `com.gearan.ios`;
   change it if already taken).
3. Select your physical iPhone → Run (CoreBluetooth LE scan is not meaningful
   in Simulator for this use). Trust the developer on the iPhone once.
4. `swift test` (in `ios/`) runs GearanCore unit tests on macOS.

Info.plist (`ios/Gearan/Info.plist`) already contains:
`NSBluetoothAlwaysUsageDescription` + `UIBackgroundModes → bluetooth-central`.

## 2. Build & run
- Select iPhone team (Signing & Capabilities), run on physical iPhone
  (CoreBluetooth LE scan does not work usefully in Simulator for this).
- `swift test` (in `ios/`) runs GearanCore unit tests on macOS.

## 3. What NOT to do
- Do not call any `createBond()` — it does not exist on iOS. If the Watch
  exposes encrypted characteristics, iOS bonds automatically on access.
- Pairing timeout is 120 s; SAS is display-only, never a key.
