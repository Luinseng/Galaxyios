# HARDWARE TEST — Galaxy Watch4 Classic + iPhone (2026-09-20)

Stati ammessi: PASS · FAIL · NOT RUN · BLOCKED. In questo ambiente
(senza Android SDK, Xcode, Watch, iPhone) tutto ciò che richiede hardware
è onestamente NOT RUN. Niente è marcato PASS senza esecuzione reale.

## MILESTONE 1 — basic GATT (prima di qualsiasi crypto)

| # | Check | Stato | Note |
|---|---|---|---|
| 1 | APK build (`cd wear && ./gradlew :app:assembleDebug`) | NOT RUN | richiede Android Studio / SDK |
| 2 | APK installed (`adb connect <ip>:5555`, `adb install …/app-debug.apk`) | NOT RUN | |
| 3 | Gearan launches (Home "Connect to iPhone" / Developer) | NOT RUN | |
| 4 | Bluetooth permission accepted (PermissionGate scompare) | NOT RUN | API 30: location rationale mostrata |
| 5 | Developer → BLE Smoke Test → Start GATT: GATT Server YES | NOT RUN | |
| 6 | Start Adv: Advertising YES | NOT RUN | |
| 7 | iPhone BLE Scan Debug trova Galaxy Watch4 Classic (RSSI + UUID + service UUID) | NOT RUN | richiede Xcode build su iPhone fisico |
| 8 | iPhone connect → service discovery OK | NOT RUN | |
| 9 | DEVICE_INFO leggibile: `Gearan/0.1.0;Samsung Galaxy Watch4 Classic;proto=0.1` | NOT RUN | MILESTONE 1 |

## MILESTONE 2 — secure pairing

| # | Check | Stato | Note |
|---|---|---|---|
| 10 | HELLO / ephemeral exchange (GEARAN_PAIR log) | NOT RUN | |
| 11 | Crypto self-test PASS sul Watch (Developer → Run test) | NOT RUN | gruppo reale X25519 o P-256 |
| 12 | Stesso SAS a 6 cifre su entrambi i display | NOT RUN | |
| 13 | SAS MATCHED, conferma ✓ su entrambi | NOT RUN | |
| 14 | PAIRING_COMPLETE, trusted identity salvata | NOT RUN | MILESTONE 2 |

## MILESTONE 3 — persistenza / reconnect

| # | Check | Stato | Note |
|---|---|---|---|
| 15 | Chiudi/riapri app iPhone → "Galaxy Watch4 Classic / Paired" + reconnect | NOT RUN | |
| 16 | Riavvio Watch → reconnect | NOT RUN | |
| 17 | Bluetooth OFF/ON → reconnect, trust intatta | NOT RUN | |
| 18 | Lontano/vicino → reconnect | NOT RUN | MILESTONE 3 |

## MILESTONE 4 — battery sync

| # | Check | Stato | Note |
|---|---|---|---|
| 19 | iPhone mostra Battery XX% + Last sync | NOT RUN | MILESTONE 4 |

## MILESTONE 5 — network relay

| # | Check | Stato | Note |
|---|---|---|---|
| 20 | Watch GET https://example.com via relay → HTTP 200 sul Watch | NOT RUN | limiti: 30 s, 1 MB, HTTPS-only. MILESTONE 5 |

## Extra (non bloccanti)

| # | Check | Stato | Note |
|---|---|---|---|
| 21 | Health sensor capability check (reali sul Watch4) | NOT RUN | SDK-gated, vedi HEALTH_SDK.md |
| 22 | ANCS probe | NOT RUN | EXPERIMENTAL, dopo milestone 1–5 |

## Log di riferimento
- Watch: `adb logcat | grep GEARAN` (tag: GEARAN_BLE/PAIR/CRYPTO/LINK/SYNC).
- iPhone: Console.app → processo Gearan, categorie GEARAN_* (os_log).
