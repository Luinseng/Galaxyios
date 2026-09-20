# Gearan STATUS

Data: 2026-09-20 · Target primario: Samsung Galaxy Watch4 Classic · Protocol: Gearan Link 0.1 (+ECDH agility §4.1)

## Build status
| Modulo | Sorgenti | Verifica locale (Windows, senza SDK) | Verifica su hardware |
|---|---|---|---|
| protocol + shared python | completi, emendamento ECDH agility | PASS (pytest 23/23, vedi sotto) | n/a |
| wear (Kotlin/Wear OS) | hardening completato (audit W-01…W-15) | Gradle wrapper presente+valido; build da fare su Android Studio | NOT RUN |
| ios (Swift/SwiftUI) | hardening completato (audit I-01…I-08) | build da fare su Xcode | NOT RUN |
| android (debug tool) | `gearan_doctor.py` invariato | ok (ADB assente qui) | opzionale |
| tests python | 23 test PASS | PASS | n/a |
| tests nativi (Kotlin/Swift) | 4 file (codec, state machine, golden vectors) | da eseguire su Android Studio / Xcode | NOT RUN |

## Test status (locale, eseguiti 2026-09-20)
- `tests/test_gearan_link.py` — 9/9 PASS
- `tests/test_pairing_crypto.py` — 6/6 PASS
- `tests/test_vectors.py` — 8/8 PASS (frame/HMAC/HKDF/SAS/chunking/AES-GCM-formato)
- Kotlin `LinkFrameCodecTest`, `PairingStateMachineTest`, `GoldenVectorTest` — NOT RUN (serve Android SDK)
- Swift `GearanCoreTests`, `GoldenVectorTests` — NOT RUN (serve Xcode/macOS)

## Milestone hardware
MILESTONE 1 (GATT base) … MILESTONE 9 (notifiche): tutti NOT RUN, checklist in
`docs/HARDWARE_TEST_WATCH4_CLASSIC.md`. Niente marcato PASS senza esecuzione reale.

## Daily-driver delta (questo pass, solo repo esistente)
Boot recovery Watch, idempotenza advertiser/GATT, SyncEngine incrementale
doppia piattaforma + test, AutoReconnect iOS reale a 6 stati, Home entrambe
con stati/batteria, Developer Mode iOS, Stock-probe message, doc IPA/APK/pairing.

## Funzionalità operative (dopo build + pairing reale)
Invariate rispetto a v0.1, più: permission gate, BLE smoke test, crypto self-test,
golden vectors, staged errors, logging GEARAN_*, ECDH fallback P-256 documentato.

## Funzionalità sperimentali / limitazioni
Invariate: ANCS EXPERIMENTAL; ECG/BIA/SpO2 SDK-gated; provisioning di sistema
sempre via Android (vedi `docs/WATCH4_CLASSIC_SETUP.md`).
