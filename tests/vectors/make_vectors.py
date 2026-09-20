#!/usr/bin/env python3
"""Generate deterministic Gearan Link golden vectors.

Reproducible: fixed keys/nonces/transcripts (NOT random).
Kotlin (GoldenVectorTest) and Swift (GoldenVectorTests) assert the same values,
so the three implementations stay interoperable.
Usage: python tests/vectors/make_vectors.py
"""
import base64
import hashlib
import hmac
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))

MAGIC = b"GR"
VERSION = 0x01


def hkdf_sha256(ikm: bytes, salt: bytes, info: bytes, length: int = 32) -> bytes:
    prk = hmac.new(salt, ikm, hashlib.sha256).digest()
    okm, t, c = b"", b"", 1
    while len(okm) < length:
        t = hmac.new(prk, t + info + bytes([c]), hashlib.sha256).digest()
        okm += t
        c += 1
    return okm[:length]


def encode_frame(mtype, flags, req, seq, cidx, total, payload: bytes, key: bytes) -> bytes:
    import struct
    header = struct.pack(">2sBBHIIHHH", MAGIC, VERSION, mtype, flags, req, seq, cidx, total, len(payload))
    return header + payload + hmac.new(key, header + payload, hashlib.sha256).digest()


def b64(b: bytes) -> str:
    return base64.b64encode(b).decode()


KEY = bytes.fromhex("42" * 32)

vectors = {}

# 1. frame encoding: PING + fragmented SYNC_DATA first chunk
ping_raw = encode_frame(0x03, 0x0001, 42, 7, 0, 1, b'{"seq":7}', KEY)
vectors["frame_ping"] = {
    "messageType": 0x03, "flags": 0x0001, "requestId": 42, "sequence": 7,
    "chunkIndex": 0, "totalChunks": 1, "payloadB64": b64(b'{"seq":7}'),
    "authKeyHex": KEY.hex(), "encodedHex": ping_raw.hex(),
}
big = bytes(range(256)) * 6  # 1536 bytes -> 3 chunks of 512
chunks = [big[i:i + 512] for i in range(0, len(big), 512)]
first_raw = encode_frame(0x0E, 0x0008, 9, 3, 0, 3, chunks[0], KEY)
vectors["frame_frag0"] = {
    "messageType": 0x0E, "flags": 0x0008, "requestId": 9, "sequence": 3,
    "chunkIndex": 0, "totalChunks": 3, "payloadB64": b64(chunks[0]),
    "authKeyHex": KEY.hex(), "encodedHex": first_raw.hex(),
}

# 2. HMAC-SHA256 (frame auth primitive)
h_header = bytes.fromhex(ping_raw.hex()[:40])
h_tag = hmac.new(KEY, bytes.fromhex(ping_raw.hex()[:40]) + b'{"seq":7}', hashlib.sha256).digest()
vectors["hmac_frame_auth"] = {
    "keyHex": KEY.hex(), "headerHex": bytes.fromhex(ping_raw.hex()[:40]).hex(),
    "payloadB64": b64(b'{"seq":7}'), "tagHex": h_tag.hex(),
}

# 3. HKDF-SHA256 session derivation (fixed stand-in shared secret)
shared = hashlib.sha256(b"golden-dh").digest()
nonce_i = bytes.fromhex("01" * 16)
nonce_w = bytes.fromhex("02" * 16)
session = hkdf_sha256(shared + nonce_i + nonce_w, b"Gearan-Link-0.1", b"session", 32)
appkeys = hkdf_sha256(session, b"Gearan-AppKeys-0.1", b"keys", 64)
vectors["hkdf_session"] = {
    "ikmHex": (shared + nonce_i + nonce_w).hex(), "saltAscii": "Gearan-Link-0.1",
    "infoAscii": "session", "length": 32, "okmHex": session.hex(),
}
vectors["hkdf_appkeys"] = {
    "ikmHex": session.hex(), "saltAscii": "Gearan-AppKeys-0.1",
    "infoAscii": "keys", "length": 64,
    "encKeyHex": appkeys[:32].hex(), "macKeyHex": appkeys[32:].hex(),
}

# 4. chunking layout
vectors["chunking"] = {
    "messageBytes": len(big), "maxFramePayload": 512,
    "totalChunks": len(chunks), "chunkSizes": [len(c) for c in chunks],
}

# 5. SAS from fixed transcript
transcript = hashlib.sha256(b"helloI" + nonce_i + b"helloW" + nonce_w).digest()
digest = hashlib.sha256(transcript).digest()
sas = int.from_bytes(digest[:4], "big") % 1_000_000
vectors["sas"] = {
    "transcriptHex": transcript.hex(), "sas": f"{sas:06d}",
}

# 6. AES-GCM: NIST SP 800-38D Case 2 (static reference for Kotlin/Swift;
#    Python stdlib has no AES-GCM, so this file asserts format only).
vectors["aes_gcm_nist_case2"] = {
    "source": "NIST SP 800-38D D.2, Test Case 2 (AES-128-GCM, empty AAD)",
    "keyHex": "00" * 16, "ivHex": "00" * 12,
    "plaintextHex": "00" * 16,
    "ciphertextHex": "0388dace60b6a392f328c2b971b2fe78",
    "tagHex": "ab6e47d42cec13bdf53a67b21257bddf",
    "gearanFormat": "payload = nonce(12B) || ciphertext || tag(16B)",
}

with open(os.path.join(HERE, "golden_vectors.json"), "w") as f:
    json.dump(vectors, f, indent=2)
print(f"wrote golden_vectors.json ({len(vectors)} vector groups)")

# Mirror into the SwiftPM test bundle (Bundle.module resource). The iOS
# Xcode project does not use this copy. Keep both identical: this script is
# the single writer.
mirror = os.path.normpath(os.path.join(
    HERE, "..", "..", "ios", "Tests", "GearanCoreTests", "vectors",
    "golden_vectors.json"))
os.makedirs(os.path.dirname(mirror), exist_ok=True)
with open(mirror, "w") as f:
    json.dump(vectors, f, indent=2)
print(f"mirrored to {mirror}")
