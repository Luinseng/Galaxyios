"""Gearan Link Protocol 0.1 — reference implementation (Python).

Mirrors the framing/auth/chunking/SAS logic implemented natively in
Kotlin (wear/) and Swift (ios/). Used by tests/ on machines without
Android SDK / Xcode.
"""
from __future__ import annotations
import hashlib
import hmac
import os
import struct
import time
from dataclasses import dataclass, field

MAGIC = b"GR"
VERSION = 0x01
HEADER_LEN = 20
MAX_FRAME_PAYLOAD = 512
MAX_MESSAGE_BYTES = 64 * 1024
MAX_TOTAL_CHUNKS = (MAX_MESSAGE_BYTES + MAX_FRAME_PAYLOAD - 1) // MAX_FRAME_PAYLOAD
AUTH_LEN = 32

# flags
FLAG_ACK_REQ = 0x0001
FLAG_ACK = 0x0002
FLAG_ENC = 0x0004
FLAG_FRAG = 0x0008
FLAG_RETRY = 0x0010
FLAG_ERR = 0x8000

# message types
MT_ACK = 0x01
MT_ERROR = 0x02
MT_PING = 0x03
MT_PONG = 0x04
MT_PAIR_HELLO = 0x05
MT_PAIR_EPHEMERAL = 0x06
MT_PAIR_AUTH = 0x07
MT_PAIR_COMMIT = 0x08
MT_PAIR_CONFIRM = 0x09
MT_PAIR_ABORT = 0x0A
MT_CAPS_REQUEST = 0x0B
MT_CAPS_RESPONSE = 0x0C
MT_SYNC_START = 0x0D
MT_SYNC_DATA = 0x0E
MT_SYNC_DONE = 0x0F
MT_BATTERY_UPDATE = 0x10
MT_DEVICE_INFO = 0x11
MT_NETWORK_REQUEST = 0x12
MT_NETWORK_RESPONSE = 0x13
MT_NETWORK_ERROR = 0x14
MT_NETWORK_CANCEL = 0x15

GEARAN_SERVICE_UUID = "6E400001-8A21-4A11-9B5C-F3F2A1E4B001"

HEADER_STRUCT = struct.Struct(">2sBBHIIHHH")  # magic,ver,type,flags,req,seq,chunk,total,len(u16)


@dataclass
class Frame:
    message_type: int
    flags: int
    request_id: int
    sequence: int
    chunk_index: int
    total_chunks: int
    payload: bytes = b""


def compute_auth(header: bytes, payload: bytes, key: bytes) -> bytes:
    return hmac.new(key, header + payload, hashlib.sha256).digest()


def encode_frame(frame: Frame, auth_key: bytes) -> bytes:
    if len(frame.payload) > MAX_FRAME_PAYLOAD:
        raise ValueError("payload exceeds MAX_FRAME_PAYLOAD")
    if (frame.total_chunks < 1 or frame.total_chunks > MAX_TOTAL_CHUNKS
            or frame.chunk_index < 0 or frame.chunk_index >= frame.total_chunks):
        raise ValueError("bad chunk indices")
    header = HEADER_STRUCT.pack(
        MAGIC, VERSION, frame.message_type & 0xFF, frame.flags & 0xFFFF,
        frame.request_id & 0xFFFFFFFF, frame.sequence & 0xFFFFFFFF,
        frame.chunk_index & 0xFFFF, frame.total_chunks & 0xFFFF,
        len(frame.payload),
    )
    auth = compute_auth(header, frame.payload, auth_key)
    return header + frame.payload + auth


def decode_frame(data: bytes, auth_key: bytes) -> Frame:
    if len(data) < HEADER_LEN + AUTH_LEN:
        raise ValueError("frame too short")
    header = data[:HEADER_LEN]
    magic, ver, mtype, flags, req, seq, cidx, total, plen = HEADER_STRUCT.unpack(header)
    if magic != MAGIC:
        raise ValueError("bad magic")
    if ver != VERSION:
        raise ValueError(f"unsupported version {ver}")
    if total == 0 or total > MAX_TOTAL_CHUNKS or cidx >= total:
        raise ValueError("bad chunk indices")
    if plen > MAX_FRAME_PAYLOAD:
        raise ValueError("payload length exceeds max")
    expected_length = HEADER_LEN + plen + AUTH_LEN
    if len(data) < expected_length:
        raise ValueError("truncated frame")
    if len(data) != expected_length:
        raise ValueError("trailing frame data")
    payload = data[HEADER_LEN:HEADER_LEN + plen]
    auth = data[HEADER_LEN + plen:HEADER_LEN + plen + AUTH_LEN]
    expected = compute_auth(header, payload, auth_key)
    if not hmac.compare_digest(auth, expected):
        raise ValueError("authentication failure")
    return Frame(mtype, flags, req, seq, cidx, total, payload)


def fragment_message(message_type: int, payload: bytes, request_id: int,
                     sequence: int, flags: int = 0) -> list[Frame]:
    if len(payload) > MAX_MESSAGE_BYTES:
        raise ValueError("message too large")
    if len(payload) == 0:
        return [Frame(message_type, flags, request_id, sequence, 0, 1, b"")]
    chunks = [payload[i:i + MAX_FRAME_PAYLOAD] for i in range(0, len(payload), MAX_FRAME_PAYLOAD)]
    total = len(chunks)
    out = []
    for idx, c in enumerate(chunks):
        f = flags | (FLAG_FRAG if total > 1 else 0)
        out.append(Frame(message_type, f, request_id, sequence, idx, total, c))
    return out


@dataclass
class Reassembler:
    buffers: dict = field(default_factory=dict)  # (req,type) -> {total, chunks{}, ts}
    timeout_s: float = 10.0
    max_concurrent: int = 8

    def feed(self, frame: Frame) -> bytes | None:
        key = (frame.request_id, frame.message_type)
        now = time.monotonic()
        # expire old
        for k in [k for k, v in self.buffers.items() if now - v["ts"] > self.timeout_s]:
            del self.buffers[k]
        if (frame.total_chunks < 1 or frame.total_chunks > MAX_TOTAL_CHUNKS
                or frame.chunk_index < 0 or frame.chunk_index >= frame.total_chunks
                or len(frame.payload) > MAX_FRAME_PAYLOAD):
            self.buffers.pop(key, None)
            raise ValueError("invalid chunk metadata")
        if frame.total_chunks == 1:
            return bytes(frame.payload)
        entry = self.buffers.get(key)
        if entry is None:
            if len(self.buffers) >= self.max_concurrent:
                raise ValueError("too many concurrent fragmented messages")
            entry = {"total": frame.total_chunks, "chunks": {}, "bytes": 0, "ts": now}
            self.buffers[key] = entry
        if entry["total"] != frame.total_chunks:
            del self.buffers[key]
            raise ValueError("totalChunks mismatch")
        payload = bytes(frame.payload)
        previous = entry["chunks"].get(frame.chunk_index)
        new_size = entry["bytes"] - (len(previous) if previous is not None else 0) + len(payload)
        if new_size > MAX_MESSAGE_BYTES:
            del self.buffers[key]
            raise ValueError("reassembled message too large")
        entry["chunks"][frame.chunk_index] = payload
        entry["bytes"] = new_size
        entry["ts"] = now
        if len(entry["chunks"]) == entry["total"]:
            data = b"".join(entry["chunks"][i] for i in range(entry["total"]))
            del self.buffers[key]
            return data
        return None

    def collect_timeout(self) -> bool:
        now = time.monotonic()
        expired = [k for k, v in self.buffers.items() if now - v["ts"] > self.timeout_s]
        for k in expired:
            del self.buffers[k]
        return bool(expired)


class ReplayWindow:
    """Sliding replay window (last 128 sequences)."""

    def __init__(self, size: int = 128):
        self.size = size
        self.max_seq = -1
        self.seen: set[int] = set()

    def accept(self, seq: int) -> bool:
        if seq in self.seen:
            return False
        if seq > self.max_seq:
            self.max_seq = seq
            self.seen.add(seq)
            # evict old
            cutoff = self.max_seq - self.size
            self.seen = {s for s in self.seen if s > cutoff}
            return True
        if seq <= self.max_seq - self.size:
            return False
        self.seen.add(seq)
        return True


def hkdf_sha256(ikm: bytes, salt: bytes, info: bytes, length: int = 32) -> bytes:
    prk = hmac.new(salt, ikm, hashlib.sha256).digest()
    okm = b""
    t = b""
    counter = 1
    while len(okm) < length:
        t = hmac.new(prk, t + info + bytes([counter]), hashlib.sha256).digest()
        okm += t
        counter += 1
    return okm[:length]


def sas_from_transcript(transcript: bytes) -> str:
    digest = hashlib.sha256(transcript).digest()
    code = int.from_bytes(digest[:4], "big") % 1_000_000
    return f"{code:06d}"


def random_nonce(n: int = 16) -> bytes:
    return os.urandom(n)
