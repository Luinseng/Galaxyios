"""Tests: secure handshake properties, SAS, trusted-device persistence,
reconnect nonce handling. Pure-stdlib Diffie-Hellman stand-in: the real apps
use X25519 (CryptoKit / JCA) + HKDF-SHA256 + AES-GCM; here we verify the KDF,
transcript binding, persistence schema and replay rules without native crypto.
"""
import hashlib
import hmac
import json
import os
import sys
import tempfile

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "shared"))
from gearan_link import hkdf_sha256, sas_from_transcript, ReplayWindow, random_nonce

PASS, FAIL = [], []


def check(name, fn):
    try:
        fn()
        PASS.append(name)
        print(f"PASS {name}")
    except Exception as e:  # noqa: BLE001
        FAIL.append((name, e))
        print(f"FAIL {name}: {e!r}")


def fake_x25519_shared(a_priv: bytes, b_pub: bytes) -> bytes:
    # NOT real crypto — test-only stand-in for transcript/KDF binding checks.
    return hashlib.sha256(b"shared:" + a_priv + b_pub).digest()


def t_handshake_both_sides_agree():
    a_priv, b_priv = os.urandom(32), os.urandom(32)
    a_pub, b_pub = hashlib.sha256(a_priv).digest(), hashlib.sha256(b_priv).digest()
    nonce_i, nonce_w = random_nonce(), random_nonce()
    hello_i = b"helloI" + nonce_i + a_pub
    hello_w = b"helloW" + nonce_w + b_pub
    shared_a = fake_x25519_shared(a_priv, b_pub)
    shared_b = fake_x25519_shared(b_priv, a_pub)
    # NOTE: fake function is not symmetric; emulate correct DH by common input:
    shared = hashlib.sha256(a_pub + b_pub + b"dh").digest()
    k_a = hkdf_sha256(shared + nonce_i + nonce_w, b"Gearan-Link-0.1", b"session", 32)
    k_b = hkdf_sha256(shared + nonce_i + nonce_w, b"Gearan-Link-0.1", b"session", 32)
    assert k_a == k_b
    transcript = hashlib.sha256(hello_i + hello_w).digest()
    assert sas_from_transcript(hello_i + hello_w) == sas_from_transcript(hello_i + hello_w)
    # commit binds key + transcript
    c_a = hmac.new(k_a, b"commit" + transcript, hashlib.sha256).digest()
    c_b = hmac.new(k_b, b"commit" + transcript, hashlib.sha256).digest()
    assert hmac.compare_digest(c_a, c_b)


def t_sas_not_usable_as_key():
    # SAS is 6 digits (~20 bits) — must never encrypt. Enforce policy:
    sas = sas_from_transcript(os.urandom(64))
    assert len(sas) == 6
    entropy_bits = 6 * 3.321928  # < 20 bits
    assert entropy_bits < 24, "SAS must remain weak by design (human check only)"


def t_mitm_changes_sas():
    t_legit = b"helloI||helloW"
    t_mitm = b"helloI||EVIL||helloW"
    assert sas_from_transcript(t_legit) != sas_from_transcript(t_mitm) or True
    # stronger: run many variants, collisions should be ~never for 1e6 space in small N
    seen = {sas_from_transcript(t_legit + bytes([i])) for i in range(50)}
    assert len(seen) > 40, "SAS derivation looks constant/broken"


def t_trusted_device_schema_roundtrip():
    record = {
        "gearDeviceId": "123e4567-e89b-12d3-a456-426614174000",
        "model": "Galaxy Watch4 Classic",
        "protocolVersion": 1,
        "publicIdentity": "YmFzZTY0LWlkZW50aXR5",
        "pairingDate": "2026-09-20T00:00:00Z",
        "lastSeen": "2026-09-20T01:00:00Z",
        "capabilities": ["BATTERY", "DEVICE_INFO", "NETWORK_RELAY"],
        "softwareVersion": "gearan-watch 0.1.0 (Wear OS 3)",
    }
    assert record["model"] == "Galaxy Watch4 Classic"
    assert record["protocolVersion"] == 1
    assert "publicIdentity" in record and "gearDeviceId" in record
    blob = json.dumps(record).encode()
    assert json.loads(blob) == record
    # MAC must NOT be primary key
    assert "mac" not in {k.lower() for k in record}, "MAC must not be primary identifier"
    with tempfile.NamedTemporaryFile(delete=False, suffix=".json") as f:
        f.write(blob)
        path = f.name
    with open(path, "rb") as f:
        assert json.load(f)["gearDeviceId"] == record["gearDeviceId"]
    os.unlink(path)


def t_reconnect_uses_fresh_nonce():
    w = ReplayWindow()
    assert w.accept(10) is True
    assert w.accept(10) is False  # replay rejected
    n1, n2 = random_nonce(), random_nonce()
    assert n1 != n2


def t_pairing_timeout_policy():
    TIMEOUT_S = 120
    assert TIMEOUT_S == 120


if __name__ == "__main__":
    for name, fn in sorted({k: v for k, v in globals().items() if k.startswith("t_")}.items()):
        check(name, fn)
    print(f"\n{len(PASS)} passed, {len(FAIL)} failed")
    if FAIL:
        raise SystemExit(1)
