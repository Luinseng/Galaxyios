"""Security bounds for Gearan frame decoding and fragment reassembly."""

import os
import sys

import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "shared"))

from gearan_link import (  # noqa: E402
    MAX_FRAME_PAYLOAD,
    MAX_MESSAGE_BYTES,
    MAX_TOTAL_CHUNKS,
    MT_SYNC_DATA,
    Frame,
    Reassembler,
    decode_frame,
    encode_frame,
)


KEY = b"k" * 32


def frame(index: int, total: int, payload: bytes, request_id: int = 7) -> Frame:
    return Frame(MT_SYNC_DATA, 0, request_id, index + 1, index, total, payload)


def test_decode_rejects_trailing_bytes() -> None:
    raw = encode_frame(frame(0, 1, b"ok"), KEY)
    with pytest.raises(ValueError, match="trailing"):
        decode_frame(raw + b"junk", KEY)


def test_chunk_count_is_bounded() -> None:
    r = Reassembler()
    with pytest.raises(ValueError, match="metadata"):
        r.feed(frame(0, MAX_TOTAL_CHUNKS + 1, b"x"))
    with pytest.raises(ValueError, match="indices"):
        encode_frame(frame(0, MAX_TOTAL_CHUNKS + 1, b"x"), KEY)


def test_exact_maximum_message_reassembles() -> None:
    r = Reassembler()
    result = None
    chunk = b"x" * MAX_FRAME_PAYLOAD
    for index in range(MAX_TOTAL_CHUNKS):
        result = r.feed(frame(index, MAX_TOTAL_CHUNKS, chunk))
    assert result == b"x" * MAX_MESSAGE_BYTES


def test_duplicate_replacement_does_not_double_count() -> None:
    r = Reassembler()
    assert r.feed(frame(0, 2, b"a" * MAX_FRAME_PAYLOAD)) is None
    assert r.feed(frame(0, 2, b"b")) is None
    assert r.feed(frame(1, 2, b"c")) == b"bc"


def test_mismatch_clears_partial_message() -> None:
    r = Reassembler()
    assert r.feed(frame(0, 2, b"old")) is None
    with pytest.raises(ValueError, match="mismatch"):
        r.feed(frame(1, 3, b"bad"))

    assert r.feed(frame(1, 2, b"new-1")) is None
    assert r.feed(frame(0, 2, b"new-0")) == b"new-0new-1"
