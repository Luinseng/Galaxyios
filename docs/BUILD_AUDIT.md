# Gearan BUILD AUDIT — hardening pass (2026-09-20)

Scope: portare i sorgenti a "MVP compilabile e testabile su Watch4 Classic + iPhone".
Nessuna nuova feature di prodotto; solo correzioni, wrapper, permessi, logging,
self-test, vettori, restoration e checklist. Ogni voce: stato FIXED / FIXED-BY-DESIGN.

## WEAR / GRADLE

| ID | Problema | Gravità | Fix |
|---|---|---|---|
| W-01 | `app/build.gradle.kts` dichiara `id(...) version ...` nel modulo: Gradle fallisce ("version only in root/settings") | blocco build | plugin serialization spostato in root `apply false`, app usa id senza versione |
| W-02 | Mancano `compileOptions source/target Java 17` + `kotlinOptions.jvmTarget 17` (AGP 8.5 lo richiede) | blocco build | aggiunti |
| W-03 | Nessun Gradle wrapper (`gradlew` assente): `./gradlew assembleDebug` impossibile | blocco build | aggiunti `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties` (jar: generato via `gradle wrapper` o Android Studio) |
| W-04 | `BATTERY_STATS` nel manifest: permission signature-only, non concedibile, lint error | medio | rimossa (batteria letta via `ACTION_BATTERY_CHANGED`, nessuna permission) |
| W-05 | Manifest BT incompleto: manca `BLUETOOTH_SCAN` (API 31+), commento `neverForLocation` fuorviante | medio | manifest riscritto: SCAN/CONNECT/ADVERTISE per 31+, `BLUETOOTH/ADMIN` maxSdk 30, `ACCESS_FINE_LOCATION` maxSdk 30 (serve davvero su API 30 = Watch4) |
| W-06 | `@SuppressLint("MissingPermission")` ovunque, nessun runtime-permission flow → crash/SecurityException su device | alto | nuovo `BlePermissions.kt` (helper API 30 vs 31+) + `PermissionGate` UI che blocca pairing/advertising senza permessi |
| W-07 | `GearanGattServer.kt`: `private fun <T> List<T>.drop` ombreggia stdlib; `toByteArray` duplica stdlib → rischio conflitto/errore compile | alto | rimossi helper custom, usato stdlib (`drop`, conversione esplicita) |
| W-08 | `characteristic.value` + `notifyCharacteristicChanged(dev,c,false)` deprecati con targetSdk 34 | warning→errore con lint strict | `@Suppress("DEPRECATION")` + path compat + commento |
| W-09 | Service senza `onStartCommand`/START_STICKY; callback (`onRx/onConnected`) su istanza non raggiungibile dall'Activity; nessun log | alto | nuovo `GearanBleHub` singleton (StateFlow + callback registry); service `START_STICKY`, log su ogni callback |
| W-10 | Advertiser senza check `isMultipleAdvertisementSupported`, senza log, timeout non documentato | medio | check + `GearanLog` + fallback gestito |
| W-11 | `CryptoUtils` assume provider XDH/X25519 presente su Watch4 API 30 (non garantito su Conscrypt di Wear OS 3) | alto | agility: prova X25519, fallback ECDH P-256 (standard, interop CryptoKit); `CryptoSelfTest` reale; emendamento §4.1 nel protocollo |
| W-12 | `MasterKey`/`EncryptedFile` alpha06 deprecation warnings | basso | `@Suppress("DEPRECATION")` + nota (API stabile per MVP) |
| W-13 | `MainActivity`: nessun permission gate, nessuna schermata Developer (smoke test, crypto self-test), scope pairing legato alla composition | medio | aggiunti menu Developer + gate + scope application |
| W-14 | Nessun logging `GEARAN_*` → `adb logcat | grep GEARAN` inutilizzabile | medio | nuovo `GearanLog.kt` (5 tag) + strumentazione advertiser/server/pairing/crypto/link |
| W-15 | `SamsungHealthSensorAdapter` ok come probe, ma manca feature-flag + doc di isolamento SDK | basso | `HEALTH_SDK_ENABLED=false` flag + `docs/HEALTH_SDK.md` per `samsung-health-sensor-api.aar` |

## iOS

| ID | Problema | Gravità | Fix |
|---|---|---|---|
| I-01 | `localPrivate = try? Curve25519...PrivateKey()`: init non-throwing → warning Swift | medio | rimosso `try?`, init diretto |
| I-02 | `CBCentralManager(delegate:queue:)` senza restore identifier → dopo lock/restart niente reconnect in background | alto | `init(delegate:queue:options: [CBCentralManagerOptionRestoreIdentifierKey: "com.gearan.ios.central"])` + `centralManager(_:willRestoreState:)` |
| I-03 | Nessun `CBPeripheralDelegate` connection manager (connect/discover/read DEVICE_INFO): MILESTONE 1 impossibile | alto | nuovo `GearanConnectionManager.swift` (connect, discoverServices/Characteristics, read CTRL/DEVICE_INFO, errori per-stage) |
| I-04 | `BLEScanner.startScan()` chiamato anche a radio spenta (`onAppear`); scan perso in silenzio | medio | coda `pendingScan` su `poweredOn`; `@Published centralState` |
| I-05 | Nessuna Scan Debug UI (RSSI/UUID/service UUID) — FASE 9 | medio | `ScanDebugView` (senza MAC, solo peripheral UUID + RSSI + service UUID) |
| I-06 | Nessuna error screen per-stage — FASE 18 | medio | `GearanStageError` + `ConnectionErrorView` (stage, error, [Retry]) |
| I-07 | Nessun logging redatto | basso | `GearanLog` con `os_log` (niente chiavi/dati) |
| I-08 | Istruzioni Xcode incomplete (Info.plist, background modes, attach package) | medio | `docs/XCODE_SETUP.md` passo-passo |

## PROTOCOLLO / TEST

| ID | Problema | Gravità | Fix |
|---|---|---|---|
| P-01 | Nessun vettore deterministico cross-language — FASE 7 | alto | `tests/vectors/*.json` + `test_vectors.py` + `GoldenVectorTest` (Kotlin) + `GoldenVectorTests` (Swift) |
| P-02 | Handshake fissa X25519 senza fallback | medio | emendamento protocollo §4.1: X25519 preferito, P-256 ammesso con stessa KDF/binding |

## DOCS

| ID | Problema | Fix |
|---|---|---|
| D-01 | Mancano audit report e checklist hardware | questo file + `docs/HARDWARE_TEST_WATCH4_CLASSIC.md` (stati PASS/FAIL/NOT RUN/BLOCKED) |

## Verifica ambiente locale (Windows, 2026-09-20)
- Android SDK: assente → build Gradle NOT RUN (da eseguire su Android Studio).
- Xcode: assente → build Swift NOT RUN (da eseguire su macOS/Xcode).
- Python reference + vettori: PASS (eseguiti qui).
- Niente è marcato PASS su hardware: tutto ciò che richiede Watch/iPhone è NOT RUN.

## ADDENDUM — daily-driver pass (esperienza finale: PAIR ONCE → TRUST → RECONNECT)

| ID | Problema | Gravità | Fix |
|---|---|---|---|
| W-16 | Nessun boot recovery: dopo reboot Watch, BLE morto fino ad apertura manuale app | alto | `GearanBootReceiver` + `RECEIVE_BOOT_COMPLETED`: ripristina solo il service se esiste trust, mai activity visibili |
| W-17 | Advertiser riavviabile N volte (doppi start) | medio | guard idempotente + hub event |
| W-18 | `addService` rieseguibile dopo restart anomalo | medio | flag `serviceAdded`, reset in `onDestroy`, clear subscribers |
| W-19 | `AutoReconnectManager` senza `stop()` esplicito (Forget) | basso | aggiunto `stop()`; mai tocca il trusted store |
| W-20 | `TrustedDevice` senza displayName/preferredTransport (spec TrustedGearanDevice) | basso | campi aggiunti con default (Codable-compatibile) |
| W-21 | Sync sempre totale, nessun incrementale | alto | `GearanSyncEngine` (revision/timestamp/dirty, solo dirty viaggia) + `SyncEngineTest` |
| W-22 | Home Watch senza batteria/stato e senza Notifications/Diagnostics | basso | `batteryText/syncText` + voci menu |
| I-09 | `TrustedWatch` senza displayName/preferredTransport | basso | aggiunti con default |
| I-10 | `GearanAutoReconnectManager` era solo policy (nessun CBCentralManager reale) | alto | manager reale: 6 stati, restore ID dedicato, `retrievePeripherals` + scan filtrata, backoff 1–30 s, peripheral UUID persistito per GearanDeviceID |
| I-11 | Nessun sync engine iOS | alto | `GearanSyncEngine` in GearanCore (stesse regole del Watch) + test |
| I-12 | Home iPhone mostrava solo Connected/Reconnecting generico | basso | 6 stati reconnect + `restoreAndReconnect()` su appear |
| I-13 | Settings senza Developer Mode | basso | sezione Developer (Scan Debug, Force reconnect, Protocol info, Export redatto) |
| I-14 | Scan vuoto senza spiegazione (probe) | basso | messaggio "Gearan Watch App not detected…" separato dallo scan |
| D-02 | Mancavano doc installazione richiesti per nome | medio | `docs/IOS_BUILD_AND_INSTALL.md`, `docs/WATCH_INSTALL.md` |
