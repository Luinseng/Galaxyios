# Gearan TODO

Legenda: [x] fatto · [ ] da fare · [!] bloccato da limiti OS/vendor

## Fasi prodotto v0.1 (completate, sorgenti)
- [x] 1–20. repository, protocollo, app Wear + GATT + advertiser, app iOS + scanner,
  pairing state machine, handshake, SAS, trusted store, reconnect, sync, battery,
  Network Relay, Health adapter (flag), notifiche + ANCS probe, diagnostics, test, docs setup

## Hardening / build / hardware test (questo pass)
- [x] F1 audit completo → `docs/BUILD_AUDIT.md` (26 voci, tutte corrette)
- [x] F2 Gradle wrapper valido (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar` verificato)
- [x] F3 permission Watch4 (API 30 vs 31+, `BlePermissions` + `PermissionGate`)
- [x] F4 GATT smoke test (`Developer → BLE Smoke Test`, `GearanBleHub`)
- [x] F5 logging `GEARAN_*` (Android + iOS, niente segreti)
- [x] F6 crypto agility + `CryptoSelfTest` (X25519 preferito, P-256 fallback, §4.1 protocollo)
- [x] F7 golden vectors (`tests/vectors/` + test Python/Kotlin/Swift)
- [x] F8 iOS audit (restore identifier, `GearanConnectionManager`, `docs/XCODE_SETUP.md`)
- [x] F9 BLE Scan Debug (stato/RSSI/UUID/service UUID, no MAC)
- [x] F10 basic GATT + DEVICE_INFO (`GearanConnectionManager`, MILESTONE 1)
- [x] F11 secure pairing flow con staged log (MILESTONE 2, da testare su hardware)
- [x] F12 persistenza/reconnect (MILESTONE 3, da testare su hardware)
- [x] F13 battery sync (MILESTONE 4, da testare su hardware)
- [x] F14 network relay limiti (MILESTONE 5, da testare su hardware)
- [x] F15 health SDK dietro flag + `docs/HEALTH_SDK.md` (build BLE non bloccata)
- [x] F16 ANCS resta EXPERIMENTAL, non prioritario
- [x] F17 `docs/HARDWARE_TEST_WATCH4_CLASSIC.md` (22 check, stati onesti)
- [x] F18 error screen per-stage (Watch `GearanErrorScreen`, iOS `ConnectionErrorView`)
- [x] F19 stati NOT RUN, nessun PASS inventato

## Verifiche residue (hardware reale, NOT RUN)
- [ ] Wear: `./gradlew :app:assembleDebug` + `:app:testDebugUnitTest` su Android Studio
- [ ] `adb install` + smoke test + crypto self-test su Watch4 Classic
- [ ] iOS: `swift test` + build Xcode + scan/connect/DEVICE_INFO su iPhone fisico
- [ ] MILESTONE 1–9 sul campo (GATT → SAS → trust → reboot → battery → relay → sensori → notifiche)
- [ ] Pair-once/reconnect matrix: app kill, reboot entrambi, BT toggle, out-of-range
- [ ] Health capability reali + ANCS probe (dopo milestone 1–7)
