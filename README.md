# Gearan

Open-source companion for **Samsung Galaxy Watch4 Classic + iPhone (iOS)**.

> TARGET PRIMARIO: Galaxy Watch4 Classic (Wear OS Powered by Samsung) ↔ iPhone via Bluetooth Low Energy.

Gearan NON sostituisce il provisioning di sistema Samsung/Google (SYSTEM_PAIRING).
Gearan crea una associazione applicativa sicura e persistente (GEARAN_PAIRING) fra
l'app Gearan su iPhone e l'app Gearan sul Watch4 Classic.

- `protocol/` — Gearan Link Protocol 0.1 (framing, chunking, auth, replay protection)
- `wear/` — App nativa Wear OS (Kotlin + Compose for Wear OS) per Galaxy Watch4 Classic
- `ios/` — App iPhone (Swift + SwiftUI + CoreBluetooth + CryptoKit + Keychain)
- `android/` — Tool opzionale di debug/sviluppo (non richiesto per uso finale)
- `shared/` — Reference implementation Python del protocollo (per test locali)
- `tests/` — Test reali (framing, handshake, SAS, replay, reconnect, sync)
- `docs/` — Setup Watch4 Classic, architettura, sicurezza, limitazioni

Stato: vedi `STATUS.md`. Task: vedi `TODO.md`.

## Quick start

```bash
# Test protocollo (richiede solo Python 3.10+)
python -m pytest tests/ -v
# oppure senza pytest:
python tests/test_gearan_link.py
python tests/test_pairing_crypto.py
```

Installazione Watch + iPhone + pairing: vedi `docs/WATCH4_CLASSIC_SETUP.md`.
Protocollo: vedi `protocol/GEARAN_LINK_PROTOCOL.md`.
Sicurezza: vedi `docs/SECURITY.md`.

## DOWNLOAD BUILDS

Ogni push/PR compila automaticamente su GitHub Actions. Per scaricare:

GitHub → Actions → ultima build verde → Artifacts (in fondo alla pagina).

| Artifact | Contenuto | Uso |
|---|---|---|
| `Gearan-Watch4-APK` | `app-debug.apk` | installabile via ADB sul Galaxy Watch4 Classic (`docs/WATCH_INSTALL.md`) |
| `Gearan-iOS-Unsigned` | `Gearan-unsigned.ipa` | **NON installabile** su iPhone stock: va firmata con Apple ID (vedi sotto) |

- APK: `adb connect <watch-ip>:5555` → `adb install app-debug.apk`.
- IPA unsigned: prodotta con `CODE_SIGNING_ALLOWED=NO`, serve firma Apple prima
  dell'installazione (Xcode con tuo team, oppure lane `signed` con secrets
  `APPLE_CERTIFICATE_BASE64`, `APPLE_PROVISIONING_PROFILE_BASE64`,
  `APPLE_CERTIFICATE_PASSWORD`, `APPLE_TEAM_ID` → artifact `Gearan-iOS-Signed`).
- Dettaglio iOS: `docs/IOS_BUILD_AND_INSTALL.md`.
