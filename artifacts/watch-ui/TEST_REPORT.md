# Gearan Watch4 Classic — hardware check, 2026-09-22

Device: Samsung SM-R870, 450×450, Android API 36. Package: `com.gearan.watch` 0.1.0.

| Check | Result | Evidence |
|---|---|---|
| Round-screen Home and Developer UI | PASS | `before.png`, `final-release-home.png`, `release-developer.png` |
| BLE advertising and GATT server | PASS | Watch log: `advertise started`, `onServiceAdded status=0`; `after-debug-smoke.png` |
| Crypto self-test on Release | PASS | `release-crypto-pass.png`: `PASS (group=P256)` |
| 120 s advertising window, then radio OFF | PASS | Watch log: `pairing advertising window ended`; `final-debug-expired-smoke.png` |
| Retry after 120 s on final Release | PASS | `release-expired-awake.png`, `release-expired-retry-pass.png`; Watch log: `pairing advertising window ended`, then `advertise started` |
| Wear unit tests | PASS | `:app:testDebugUnitTest`: 18 tests, 0 failures |
| Python protocol checks | PASS | 5 pytest tests and 23 direct script checks |
| Release build and APK signature | PASS | `:app:assembleRelease`; `apksigner verify` |
| Final Release installed on Watch | PASS | ADB `install -r`: `Success`; installed `base.apk` SHA-256 equals built APK |
| iPhone scan, DEVICE_INFO, SAS, trust, sync | NOT RUN | iPhone/Xcode session unavailable; see limitation below |

Final test APK: `../../wear/app/build/outputs/apk/release/app-release-watch-final.apk`.
SHA-256: `50E60F2CAAFD264FF1ACDCF0311389A68A583D95E69D5852F4E33F0B87B5D2EF`.
Signed with the local Android **debug key for Watch testing only**. A production Release requires a separate private signing key outside the repository.

## Product limitation

BLE discovery, GATT advertising, UI, and on-device crypto self-test work. Full iPhone pairing is not implemented end to end: `GearanBleHub.onConnected`/`onRx` have no handshake dispatcher assignment, so incoming GATT traffic cannot advance `GearanPairingManager`. On this Watch the crypto provider selects P-256; current iOS session code advertises only X25519. Consequently no SAS/trust/sync result is claimed.
