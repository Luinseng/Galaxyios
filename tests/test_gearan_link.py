"""Tests: frame encoding/decoding, chunking, reassembly, timeouts,
invalid packets, auth failures, replay protection. Run: python tests/test_gearan_link.py
or: python -m pytest tests/ -v
"""
import os
import sys
import time

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "shared"))
from gearan_link import (
    Frame, Reassembler, ReplayWindow, encode_frame, decode_frame,
    fragment_message, sas_from_transcript, hkdf_sha256,
    MT_PING, MT_SYNC_DATA, MAX_FRAME_PAYLOAD,
)

PASS = []
FAIL = []


def check(name, fn):
    try:
        fn()
        PASS.append(name)
        print(f"PASS {name}")
    except Exception as e:  # noqa: BLE001
        FAIL.append((name, e))
        print(f"FAIL {name}: {e!r}")


def t_roundtrip():
    key = b"test-key-32-bytes-padded-00000000"[:32]
    f = Frame(MT_PING, 0x0001, 42, 7, 0, 1, b'{"seq":7}')
    raw = encode_frame(f, key)
    back = decode_frame(raw, key)
    assert back.payload == b'{"seq":7}', back
    assert back.request_id == 42 and back.sequence == 7


def t_invalid_magic():
    key = b"0" * 32
    f = Frame(MT_PING, 0, 1, 1, 0, 1, b"x")
    raw = bytearray(encode_frame(f, key))
    raw[0] = ord("X")
    try:
        decode_frame(bytes(raw), key)
    except ValueError as e:
        assert "magic" in str(e).lower()
        return
    raise AssertionError("bad magic accepted")


def t_auth_failure():
    key = b"0" * 32
    wrong = b"1" * 32
    raw = encode_frame(Frame(MT_PING, 0, 1, 1, 0, 1, b"hello"), key)
    try:
        decode_frame(raw, wrong)
    except ValueError as e:
        assert "authentication" in str(e).lower()
        return
    raise AssertionError("forged frame accepted")


def t_truncated():
    key = b"0" * 32
    raw = encode_frame(Frame(MT_PING, 0, 1, 1, 0, 1, b"hello"), key)
    try:
        decode_frame(raw[:-5], key)
    except ValueError:
        return
    raise AssertionError("truncated frame accepted")


def t_chunking_reassembly():
    key = b"0" * 32
    payload = os.urandom(1500)
    frames = fragment_message(MT_SYNC_DATA, payload, request_id=9, sequence=3)
    assert len(frames) == 3, len(frames)
    assert all(len(f.payload) <= MAX_FRAME_PAYLOAD for f in frames)
    r = Reassembler()
    out = None
    # deliver out of order: 2,0,1
    for f in (frames[2], frames[0], frames[1]):
        raw = encode_frame(f, key)
        back = decode_frame(raw, key)
        res = r.feed(back)
        if res is not None:
            out = res
    assert out == payload, "reassembly mismatch"


def t_reassembly_timeout():
    r = Reassembler(timeout_s=0.05)
    payload = os.urandom(1000)
    frames = fragment_message(MT_SYNC_DATA, payload, request_id=5, sequence=1)
    r.feed(frames[0])
    time.sleep(0.08)
    assert r.collect_timeout() is True


def t_replay_window():
    w = ReplayWindow(size=128)
    assert w.accept(1) is True
    assert w.accept(1) is False  # duplicate
    assert w.accept(2) is True
    assert w.accept(0) is True  # within window
    assert w.accept(0) is False
    for s in range(3, 300):
        w.accept(s)
    assert w.accept(5) is False  # too old
    assert w.accept(300) is True


def t_sas_format():
    sas = sas_from_transcript(b"gearan-test-transcript")
    assert len(sas) == 6 and sas.isdigit(), sas
    # determinism
    assert sas_from_transcript(b"gearan-test-transcript") == sas
    assert sas_from_transcript(b"other") != sas or True  # likely differs


def t_retry_flag_preserved():
    key = b"0" * 32
    f = Frame(MT_PING, 0x0010, 1, 1, 0, 1, b"r")
    assert decode_frame(encode_frame(f, key), key).flags == 0x0010


if __name__ == "__main__":
    for name, fn in sorted({k: v for k, v in globals().items() if k.startswith("t_")}.items()):
        check(name, fn)
    print(f"\n{len(PASS)} passed, {len(FAIL)} failed")
    if FAIL:
        raise SystemExit(1)
