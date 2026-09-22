# Gearan iOS — build and installation

The existing Xcode project is `ios/Gearan.xcodeproj`, scheme `Gearan`, bundle ID `com.gearan.ios`. Building for iPhone requires macOS and Xcode; the Windows workspace cannot compile an iOS app.

## GitHub Actions

`.github/workflows/build-ios.yml` runs on pushes and manual dispatches. Its `unsigned` job runs `swift test`, builds the Release app for `iphoneos`, and uploads `Gearan-iOS-Unsigned` (`Gearan-unsigned.ipa`). This file is a build artifact and **cannot be installed on an ordinary iPhone without Apple signing**.

The optional `signed` job exports an Ad Hoc IPA as `Gearan-iOS-Signed` when all four repository Actions secrets exist:

- `APPLE_CERTIFICATE_BASE64`: base64 of an Apple Distribution `.p12` certificate and its private key.
- `APPLE_CERTIFICATE_PASSWORD`: password for that `.p12`.
- `APPLE_PROVISIONING_PROFILE_BASE64`: base64 of an Ad Hoc `.mobileprovision` profile for the bundle ID.
- `APPLE_TEAM_ID`: Apple Developer Team ID.

The profile must include the target iPhone's registered device ID. Configure the secrets in GitHub repository Settings → Secrets and variables → Actions. Do not commit certificates, provisioning profiles, passwords or Team IDs to source files. The workflow must complete successfully before an IPA is called built.

## Build and test locally on a Mac

1. Open `ios/Gearan.xcodeproj` in Xcode and select the `Gearan` scheme.
2. Select your Apple development team under Signing & Capabilities. Change the bundle ID if `com.gearan.ios` is unavailable to your team.
3. Run `swift test` in `ios/`, then build or archive the `Gearan` scheme for a physical iPhone.
4. For direct testing, select the connected iPhone and Run. For an installable IPA, export the archive with an Apple signing profile appropriate to that device.

After installation, verify BLE scan, device information, handshake, SAS, trust and sync on a physical iPhone and Watch. The current pairing path is incomplete; see `STATUS.md` before interpreting a successful build as functional pairing.
