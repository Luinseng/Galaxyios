# Gearan Link Protocol 0.1

Custom application protocol between Gearan iPhone app and Gearan Wear OS app
(Galaxy Watch4 Classic) over BLE GATT. HTTPS-relay and sync are multiplexed
on top of these frames. BLE bonding is orthogonal: Gearan keeps its own
cryptographic trusted relationship independently of OS bonding.

## 0. Terminology

- SYSTEM_PAIRING: official Samsung/Google provisioning. NOT replaced by Gearan.
- GEARAN_PAIRING: persistent secure app-level association (this document).

## 1. Transport mapping (BLE)

- Watch acts as `BluetoothGattServer` + `BluetoothLeAdvertiser`.
- iPhone acts as `CBCentralManager` + GATT client.
- Gearan Service UUID: `6E400001-8A21-4A11-9B5C-F3F2A1E4B001` (primary service).
  - RX Characteristic (iPhone → Watch, WRITE / WRITE_NO_RESPONSE):
    `6E400002-8A21-4A11-9B5C-F3F2A1E4B001`
  - TX Characteristic (Watch → iPhone, NOTIFY):
    `6E400003-8A21-4A11-9B5C-F3F2A1E4B001`
  - CTRL Characteristic (handshake + control, READ/WRITE, ENCRYPTED when bonded):
    `6E400004-8A21-4A11-9B5C-F3F2A1E4B001`
- Manufacturer data in advertising: company placeholder `0xFFFF`, payload:
  `magic "GR" (2B) + version (1B) + GearanDeviceID-short (8B) + caps-bitmask (2B)`.
  Complete local name: `Gearan Watch4C` (or `Gearan <DeviceID-short>`).
- iPhone MUST filter scans by Gearan Service UUID. MUST NOT show headsets/speakers.
- Sensitive characteristics SHOULD require `PERMISSION_READ_ENCRYPTED |
  PERMISSION_WRITE_ENCRYPTED` on the Watch side when a bond exists. On iOS there
  is NO `createBond()` API: the OS triggers bonding automatically when accessing
  an encrypted characteristic. Gearan never depends on bonding alone.

## 2. Frame format (all integers big-endian unless noted)

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|      magic "G" (0x47)         |      magic "R" (0x52)         |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|     version (0x01)            |    messageType (0x00-0x7F)    |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|              flags (u16)              |      requestId (u32, part) ...
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
... requestId (u32)             |       sequence (u32, part) ...
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
... sequence (u32)              |   chunkIndex (u16)    | totalCh ...
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
... totalChunks (u16)           |      payloadLength (u32, part)...
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
... payloadLength (u32)         |       payload (0..512 bytes) ...
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|              ... payload ...                  | authData (32B)...
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

- `magic`: MUST be `0x47 0x52`. Else drop frame (count `invalid packet`).
- `version`: `0x01`. Unknown versions MUST be NACKed with `ERROR` message.
- Header length: 20 bytes. `payloadLength` (u16, big-endian) MUST be ≤ 512 (MAX_FRAME_PAYLOAD).
  Messages larger than 512 MUST be fragmented into `totalChunks` frames.
- `authenticationData`: 32 bytes HMAC-SHA256 over
  `header(20B) || payload` keyed by current session key (zero-key before handshake
  completes — such frames MUST be limited to handshake types only).
- Encryption: payloads of types marked ENC (see §3) are AES-128/256-GCM
  ciphertexts (12-byte nonce prepended inside payload, 16-byte tag appended
  inside payload) AFTER session establishment. Null cipher is FORBIDDEN after
  `securingConnection`.

### 2.1 Flags (u16)

| Bit | Name | Meaning |
|---|---|---|
| 0x0001 | ACK_REQ | receiver MUST reply ACK |
| 0x0002 | ACK | this frame is an acknowledgement |
| 0x0004 | ENC | payload is AES-GCM encrypted |
| 0x0008 | FRAG | part of fragmented message (totalChunks > 1) |
| 0x0010 | RETRY | retransmission (same requestId+sequence+chunkIndex) |
| 0x8000 | ERR | error indicator (with ERROR payload) |

### 2.2 Reassembly / ACK / retry / flow control

- Key: `(requestId, messageType)`. Chunks indexed `0..totalChunks-1`.
- Receiver buffers chunks up to 64 KB per message, 8 concurrent messages max.
- Reassembly timeout: 10 s. On timeout: send `ERROR(TIMEOUT)` + discard.
- ACK frame: `messageType=ACK (0x01)`, payload = acked `requestId+sequence+chunkIndex`.
- Sender retry: up to 3 retries, backoff 200 ms / 500 ms / 1 s. Count `packet retries`.
- Flow control: receiver may send `FLOW_PAUSE` / `FLOW_RESUME` (messageType below).
- Replay protection: per-direction 64-bit sequence window (sliding window of 128);
  duplicates/old sequences are dropped and counted.

## 3. Message types (u8)

```
0x00 RESERVED
0x01 ACK
0x02 ERROR
0x03 PING
0x04 PONG
0x05 PAIR_HELLO            (plaintext, handshake)
0x06 PAIR_EPHEMERAL        (plaintext, handshake)
0x07 PAIR_AUTH             (plaintext+HMAC, handshake)
0x08 PAIR_COMMIT           (SAS commitment exchange)
0x09 PAIR_CONFIRM          (user confirmed SAS)
0x0A PAIR_ABORT
0x0B CAPS_REQUEST
0x0C CAPS_RESPONSE
0x0D SYNC_START
0x0E SYNC_DATA             (ENC)
0x0F SYNC_DONE
0x10 BATTERY_UPDATE        (ENC)
0x11 DEVICE_INFO           (ENC)
0x12 NETWORK_REQUEST       (ENC)
0x13 NETWORK_RESPONSE      (ENC)
0x14 NETWORK_ERROR
0x15 NETWORK_CANCEL
0x16 NOTIF_POST            (ENC, Gearan-owned only)
0x17 NOTIF_ACTION
0x18 HEALTH_SAMPLE         (ENC)
0x19 DIAGNOSTICS_REQ       (ENC)
0x1A DIAGNOSTICS_RESP      (ENC)
0x1B HEARTBEAT_CFG
0x1C FLOW_PAUSE
0x1D FLOW_RESUME
0x1E DISCONNECT
```

Payloads are JSON (UTF-8) unless noted, with keys in camelCase. Binary health
samples use CBOR-shaped JSON arrays for v0.1 simplicity (documented per capability).

## 4. Pairing & secure handshake (normative)

Primitives ONLY: X25519 (ephemeral), HKDF-SHA256, AES-GCM-128/256,
HMAC-SHA256, CSPRNG nonces (16 B), SAS = 6 decimal digits.

```
iPhone                          Watch
  |--- PAIR_HELLO(iPhoneHello) --->|  {gearDeviceId, model:"iPhone", proto:1,
  |<-- PAIR_HELLO(WatchHello) ----|   caps, nonceI/nonceW 16B, ephPubI/ephPubW 32B}
  |--- PAIR_EPHEMERAL ------------>|  (if split across frames)
  |<-- PAIR_EPHEMERAL -------------|
  |   both compute: shared=X25519(ephPriv, ephPeerPub)
  |   sessionKey=HKDF(shared || nonceI || nonceW, salt="Gearan-Link-0.1", 32B)
  |   transcript=SHA256(all handshake bytes in order)
  |   sas = BE32(transcript[0:4]) % 1_000_000, zero-padded 6 digits
  |--- PAIR_COMMIT(c=HMAC(sessionKey,"commit"I/W)) --->|
  |<-- PAIR_COMMIT ----------------|
  DISPLAY sas on both screens (human anti-MITM check, NOT a key)
  user confirms on BOTH devices within 120 s (else PAIR_ABORT + timeout)
  |--- PAIR_CONFIRM(HMAC(sessionKey,"confirm"+transcript)) --->|
  |<-- PAIR_CONFIRM ----------------|
  derive appKeys = HKDF(sessionKey, "Gearan-AppKeys-0.1"): encKey|macKey
  persist trusted device record (see §5), start initial sync (§6)
```

- SAS MUST be derived cryptographically from the session transcript.
- SAS MUST NEVER be used as an encryption key.
- Pairing timeout: 120 s total. Errors → state `failed` + `PAIR_ABORT`.
- Long-term identity: each side generates persistent Ed25519/X25519 identity
  (Keychain / Android Keystore) and exchanges `publicIdentity` signed over the
  handshake (in PAIR_AUTH). Session keys are ephemeral (forward secrecy).

## 4.1 ECDH agility (amendment 2026-09-20, hardening)

Ephemeral group is X25519 preferred with ECDH P-256 fallback (both modern,
standard, CryptoKit-interoperable). `PAIR_HELLO` carries `ecdh:"x25519"|"p256"`;
both sides MUST use the same group and the value is covered by the transcript —
a mismatch aborts pairing (downgrade is visible, never silent). HKDF-SHA256,
SAS and commit-word binding are identical for both groups. No custom crypto.

## 5. Trusted device record

```json
{
  "gearDeviceId": "uuid-v4",
  "model": "Galaxy Watch4 Classic",
  "protocolVersion": 1,
  "publicIdentity": "base64(32B)",
  "pairingDate": "RFC3339",
  "lastSeen": "RFC3339",
  "capabilities": ["BATTERY","DEVICE_INFO", "..."],
  "softwareVersion": "gearan-watch 0.1.0 (Wear OS 3, One UI Watch 4)"
}
```

- NEVER use Bluetooth MAC as primary key. MACs randomize; GearanDeviceID is stable.
- iPhone stores in Keychain; Watch stores in EncryptedSharedPreferences/Keystore.

## 6. Initial sync (after first pairing)

Order: `Connecting → Securing connection → Checking Watch → Checking sensors →
Synchronizing → Complete`. Messages: `SYNC_START → DEVICE_INFO → CAPS_RESPONSE →
BATTERY_UPDATE → HEALTH caps → SYNC_DONE`. Progress reported on both UIs.

Device info fields: model, battery level+charging, Wear OS version, One UI
version (when publicly readable), Gearan version, caps list, sensors list,
Network Relay / Notification / Health capabilities.

## 7. Network Relay (app-proxy, HTTPS-only v0.1)

- NOT general Bluetooth tethering. Only Gearan-originated HTTPS via iPhone.
- `NETWORK_REQUEST`: `{id, method:GET|POST|HEAD, url:https-only, headers, bodyB64?, timeoutMs≤30000, maxBytes≤1MB, chunkSize}`
- Chunked transfer: responses > 512 B use fragmented frames (§2.2).
- `NETWORK_CANCEL` aborts. Timeouts → `NETWORK_ERROR{reason:TIMEOUT|DENIED|TOO_LARGE|AUTH|OFFLINE}`.
- Auth: every relay message is ENC + HMAC; iPhone enforces per-device permission
  toggle ("Internet via iPhone") + user prompt for new hosts (allowlist).
- Watch UI: `Internet via iPhone [Test connection]` → GET `https://example.com`.

## 8. Capabilities

`BATTERY HEART_RATE HEALTH_SENSORS BIA SPO2 NETWORK_RELAY GEARAN_NOTIFICATIONS
ANCS_EXPERIMENTAL DEVICE_INFO DIAGNOSTICS`. Each: `AVAILABLE | UNAVAILABLE |
PERMISSION_REQUIRED | SDK_RESTRICTION | EXPERIMENTAL`. iPhone MUST call
`CAPS_REQUEST` and MUST NOT assume availability.

## 9. Heartbeat (battery-aware)

`PING{seq, batteryMv?}` / `PONG{seq}`. Adaptive: 30 s when connected+active,
120 s when idle/screen-off, paused when disconnected. No continuous radio hold;
uses BLE notifications only. Watch4 Classic battery is explicitly optimized.

## 10. Diagnostics (privacy)

Exposes: model, connection, pairing state, Gearan Device ID, proto version, BT
state, RSSI, MTU, TX/RX bytes, retries, last heartbeat/sync, caps. Export MUST
strip: private keys, auth secrets, notification content, personal health data.

## 11. Test vectors (informative)

See `shared/gearan_link.py` + `tests/`. Magic+version+HMAC behavior covered by
automated tests. SAS example: transcript `SHA256("gearan-test")` → SAS `083412`
(padded). Real SAS is handshake-derived, never hardcoded.

## 12. Versioning

Protocol version 1. Higher minor versions MUST stay backward compatible at
framing level; unknown messageTypes MUST get `ERROR(UNKNOWN_TYPE)`.
