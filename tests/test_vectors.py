"""Cross-language golden vectors: asserts shared/gearan_link.py reproduces
tests/vectors/golden_vectors.json byte-for-byte (frame/HMAC/HKDF/SAS/chunking).
Kotlin (GoldenVectorTest) and Swift (GoldenVectorTests) assert the same file.
Run: python tests/test_vectors.py
"""
import base64
import json
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "shared"))
from gearan_link import Frame, encode_frame, hkdf_sha256, sas_from_transcript, fragment_message
import hashlib
import hmac as hmac_mod

VECTORS = json.load(open(os.path.join(os.path.dirname(__file__), "vectors", "golden_vectors.json")))
PASS, FAIL = [], []


def check(name, fn):
    try:
        fn()
        PASS.append(name)
        print(f"PASS {name}")
    except Exception as e:  # noqa: BLE001
        FAIL.append((name, e))
        print(f"FAIL {name}: {e!r}")


def v_frame_ping():
    v = VECTORS["frame_ping"]
    key = bytes.fromhex(v["authKeyHex"])
    f = Frame(v["messageType"], v["flags"], v["requestId"], v["sequence"],
              v["chunkIndex"], v["totalChunks"], base64.b64decode(v["payloadB64"]))
    assert encode_frame(f, key).hex() == v["encodedHex"]


def v_frame_frag0():
    v = VECTORS["frame_frag0"]
    key = bytes.fromhex(v["authKeyHex"])
    f = Frame(v["messageType"], v["flags"], v["requestId"], v["sequence"],
              v["chunkIndex"], v["totalChunks"], base64.b64decode(v["payloadB64"]))
    assert encode_frame(f, key).hex() == v["encodedHex"]


def v_hmac():
    v = VECTORS["hmac_frame_auth"]
    tag = hmac_mod.new(bytes.fromhex(v["keyHex"]),
                       bytes.fromhex(v["headerHex"]) + base64.b64decode(v["payloadB64"]),
                       hashlib.sha256).digest()
    assert tag.hex() == v["tagHex"]


def v_hkdf_session():
    v = VECTORS["hkdf_session"]
    okm = hkdf_sha256(bytes.fromhex(v["ikmHex"]), v["saltAscii"].encode(), v["infoAscii"].encode(), v["length"])
    assert okm.hex() == v["okmHex"]


def v_hkdf_appkeys():
    v = VECTORS["hkdf_appkeys"]
    okm = hkdf_sha256(bytes.fromhex(v["ikmHex"]), v["saltAscii"].encode(), v["infoAscii"].encode(), v["length"])
    assert okm[:32].hex() == v["encKeyHex"] and okm[32:].hex() == v["macKeyHex"]


def v_chunking():
    v = VECTORS["chunking"]
    payload = bytes(range(256)) * 6
    assert len(payload) == v["messageBytes"]
    frames = fragment_message(0x0E, payload, request_id=9, sequence=3)
    assert len(frames) == v["totalChunks"]
    assert [len(f.payload) for f in frames] == v["chunkSizes"]


def v_sas():
    v = VECTORS["sas"]
    assert sas_from_transcript(bytes.fromhex(v["transcriptHex"])) == v["sas"]


def v_aes_gcm_format():
    v = VECTORS["aes_gcm_nist_case2"]
    assert len(bytes.fromhex(v["keyHex"])) == 16
    assert len(bytes.fromhex(v["ivHex"])) == 12
    assert len(bytes.fromhex(v["tagHex"])) == 16
    # full NIST vector decrypt verified natively in Kotlin/Swift tests


if __name__ == "__main__":
    for name, fn in sorted({k: val for k, val in globals().items() if k.startswith("v_")}.items()):
        check(name, fn)
    print(f"\n{len(PASS)} passed, {len(FAIL)} failed")
    if FAIL:
        raise SystemExit(1)
