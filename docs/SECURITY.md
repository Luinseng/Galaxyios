# Gearan security model

Standard primitives only. No invented algorithms.

- **Handshake**: ephemeral X25519 (Watch: JCA `XDH`; iPhone: CryptoKit
  Curve25519), 16-byte nonces each side, transcript = ordered handshake bytes.
- **Session key**: `HKDF-SHA256(shared || nonceI || nonceW, salt="Gearan-Link-0.1")`.
- **App keys**: `HKDF(sessionKey, "Gearan-AppKeys-0.1")` → encKey|macKey.
- **Frames**: HMAC-SHA256(header||payload) 32 B trailer; ENC payloads are
  AES-GCM (12 B nonce + ciphertext + tag inside payload).
- **Identity**: long-term public identity per device (Keychain / Keystore),
  exchanged signed in PAIR_AUTH; session keys stay ephemeral (forward secrecy).
- **SAS code**: 6 digits = `BE32(SHA256(transcript)[0:4]) % 1_000_000`, zero-padded.
  Human anti-MITM check. NEVER used as a key (~20 bits by design).
- **Replay**: 128-entry sliding window per direction; duplicates/old dropped.
- **Timeouts**: pairing 120 s; fragment reassembly 10 s; relay 30 s max; retries 3×.
- **Storage**: iPhone Keychain (`AfterFirstUnlockThisDeviceOnly`); Watch
  EncryptedSharedPreferences/DataStore + Keystore. Session keys never persisted.
- **Bonding**: encrypted GATT characteristics may trigger OS bonding; Gearan
  trust is independent and keyed by GearanDeviceID (UUID), never by MAC.
- **Diagnostics export**: model/connection/RSSI/MTU/counters/caps only.
  Never: private keys, session secrets, notification content, health data.
