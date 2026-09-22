# Gearan status — 2026-09-23

Target: Samsung Galaxy Watch4 Classic + iPhone over Gearan BLE. System provisioning still requires the official Samsung/Google path.

| Area | Current evidence | Remaining work |
|---|---|---|
| Wear OS | Release APK built and installed on SM-R870; 18 unit tests pass. On-device BLE advertising, GATT service, P-256 crypto self-test, 120 s timeout and retry pass. | Complete the connection and frame dispatch into the pairing manager. |
| iOS | Swift package and Xcode project are present. GitHub Actions has an unsigned IPA lane and an optional signed IPA lane. | Run `swift test` and Xcode Release build on macOS; test with a physical iPhone. Signed IPA requires Apple signing assets. |
| Shared protocol | 5 pytest tests and 23 direct Python checks pass. | Verify real Watch-to-iPhone handshake and sync. |

Watch evidence and APK checksum: [`artifacts/watch-ui/TEST_REPORT.md`](artifacts/watch-ui/TEST_REPORT.md).

The apps do **not** yet pair end to end. `GearanBleHub.onConnected` and `onRx` are not routed to the Watch handshake dispatcher. The tested Watch selects P-256 while the current iOS secure session implements X25519 only. No iPhone SAS, trust, or sync success is claimed.
