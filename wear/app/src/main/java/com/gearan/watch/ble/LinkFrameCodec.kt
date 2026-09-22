package com.gearan.watch.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Gearan Link Protocol 0.1 framing.
 * Header (20 B, big-endian): magic "GR" | version u8 | type u8 | flags u16 |
 * requestId u32 | sequence u32 | chunkIndex u16 | totalChunks u16 | payloadLen u16.
 * Trailer: HMAC-SHA256(header || payload), 32 B.
 */
object LinkFrameCodec {
    const val MAGIC_0: Byte = 0x47
    const val MAGIC_1: Byte = 0x52
    const val VERSION: Byte = 0x01
    const val HEADER_LEN = 20
    const val AUTH_LEN = 32
    const val MAX_FRAME_PAYLOAD = 512
    const val MAX_MESSAGE_BYTES = 64 * 1024
    const val MAX_TOTAL_CHUNKS = (MAX_MESSAGE_BYTES + MAX_FRAME_PAYLOAD - 1) / MAX_FRAME_PAYLOAD

    const val FLAG_ACK_REQ: Int = 0x0001
    const val FLAG_ACK: Int = 0x0002
    const val FLAG_ENC: Int = 0x0004
    const val FLAG_FRAG: Int = 0x0008
    const val FLAG_RETRY: Int = 0x0010
    const val FLAG_ERR: Int = 0x8000

    // Message types (subset; full list in protocol doc)
    const val MT_ACK: Int = 0x01
    const val MT_ERROR: Int = 0x02
    const val MT_PING: Int = 0x03
    const val MT_PONG: Int = 0x04
    const val MT_PAIR_HELLO: Int = 0x05
    const val MT_PAIR_EPHEMERAL: Int = 0x06
    const val MT_PAIR_AUTH: Int = 0x07
    const val MT_PAIR_COMMIT: Int = 0x08
    const val MT_PAIR_CONFIRM: Int = 0x09
    const val MT_PAIR_ABORT: Int = 0x0A
    const val MT_CAPS_REQUEST: Int = 0x0B
    const val MT_CAPS_RESPONSE: Int = 0x0C
    const val MT_SYNC_START: Int = 0x0D
    const val MT_SYNC_DATA: Int = 0x0E
    const val MT_SYNC_DONE: Int = 0x0F
    const val MT_BATTERY_UPDATE: Int = 0x10
    const val MT_DEVICE_INFO: Int = 0x11
    const val MT_NETWORK_REQUEST: Int = 0x12
    const val MT_NETWORK_RESPONSE: Int = 0x13
    const val MT_NETWORK_ERROR: Int = 0x14
    const val MT_NETWORK_CANCEL: Int = 0x15
    const val MT_NOTIF_POST: Int = 0x16
    const val MT_HEALTH_SAMPLE: Int = 0x18
    const val MT_DIAGNOSTICS_REQ: Int = 0x19
    const val MT_DIAGNOSTICS_RESP: Int = 0x1A
    const val MT_DISCONNECT: Int = 0x1E

    data class Frame(
        val messageType: Int,
        val flags: Int,
        val requestId: Int,
        val sequence: Long,
        val chunkIndex: Int,
        val totalChunks: Int,
        val payload: ByteArray,
    )

    class CodecException(message: String) : Exception(message)

    fun hmacSha256(key: ByteArray, header: ByteArray, payload: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        mac.update(header)
        mac.update(payload)
        return mac.doFinal()
    }

    fun encode(f: Frame, authKey: ByteArray): ByteArray {
        require(f.payload.size <= MAX_FRAME_PAYLOAD) { "payload too large" }
        require(f.totalChunks in 1..MAX_TOTAL_CHUNKS && f.chunkIndex in 0 until f.totalChunks) {
            "bad chunk indices"
        }
        val header = ByteBuffer.allocate(HEADER_LEN).order(ByteOrder.BIG_ENDIAN)
        header.put(MAGIC_0).put(MAGIC_1).put(VERSION).put(f.messageType.toByte())
        header.putShort(f.flags.toShort())
        header.putInt(f.requestId)
        header.putInt(f.sequence.toInt())
        header.putShort(f.chunkIndex.toShort())
        header.putShort(f.totalChunks.toShort())
        header.putShort(f.payload.size.toShort())
        val hb = header.array()
        val auth = hmacSha256(authKey, hb, f.payload)
        return hb + f.payload + auth
    }

    fun decode(data: ByteArray, authKey: ByteArray): Frame {
        if (data.size < HEADER_LEN + AUTH_LEN) throw CodecException("frame too short")
        val header = data.copyOfRange(0, HEADER_LEN)
        val bb = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
        val m0 = bb.get(); val m1 = bb.get()
        if (m0 != MAGIC_0 || m1 != MAGIC_1) throw CodecException("bad magic")
        val ver = bb.get()
        if (ver != VERSION) throw CodecException("unsupported version $ver")
        val type = bb.get().toInt() and 0xFF
        val flags = bb.short.toInt() and 0xFFFF
        val req = bb.int
        val seq = bb.int.toLong() and 0xFFFFFFFFL
        val cidx = bb.short.toInt() and 0xFFFF
        val total = bb.short.toInt() and 0xFFFF
        val plen = bb.short.toInt() and 0xFFFF
        if (total == 0 || total > MAX_TOTAL_CHUNKS || cidx >= total) {
            throw CodecException("bad chunk indices")
        }
        if (plen > MAX_FRAME_PAYLOAD) throw CodecException("payload too large")
        val expectedLength = HEADER_LEN + plen + AUTH_LEN
        if (data.size < expectedLength) throw CodecException("truncated frame")
        if (data.size != expectedLength) throw CodecException("trailing frame data")
        val payload = data.copyOfRange(HEADER_LEN, HEADER_LEN + plen)
        val auth = data.copyOfRange(HEADER_LEN + plen, HEADER_LEN + plen + AUTH_LEN)
        val expected = hmacSha256(authKey, header, payload)
        if (!auth.contentEquals(expected)) throw CodecException("authentication failure")
        return Frame(type, flags, req, seq, cidx, total, payload)
    }

    fun fragment(messageType: Int, payload: ByteArray, requestId: Int, sequence: Long, flags: Int = 0): List<Frame> {
        require(payload.size <= MAX_MESSAGE_BYTES) { "message too large" }
        if (payload.isEmpty()) return listOf(Frame(messageType, flags, requestId, sequence, 0, 1, ByteArray(0)))
        val chunks = payload.toList().chunked(MAX_FRAME_PAYLOAD) { it.toByteArray() }
        return chunks.mapIndexed { idx, c ->
            val f = if (chunks.size > 1) flags or FLAG_FRAG else flags
            Frame(messageType, f, requestId, sequence, idx, chunks.size, c)
        }
    }

    /** Sliding replay window (128). */
    class ReplayWindow(private val size: Int = 128) {
        private var maxSeq: Long = -1
        private val seen = HashSet<Long>()
        @Synchronized
        fun accept(seq: Long): Boolean {
            if (seen.contains(seq)) return false
            if (seq > maxSeq) {
                maxSeq = seq
                seen.add(seq)
                val cutoff = maxSeq - size
                seen.removeIf { it <= cutoff }
                return true
            }
            if (seq <= maxSeq - size) return false
            seen.add(seq)
            return true
        }
    }

    class Reassembler(private val timeoutMs: Long = 10_000, private val maxConcurrent: Int = 8) {
        private data class Entry(
            val total: Int,
            val chunks: MutableMap<Int, ByteArray>,
            var byteCount: Int,
            var ts: Long
        )
        private val buffers = HashMap<Pair<Int, Int>, Entry>()

        @Synchronized
        fun feed(f: Frame): ByteArray? {
            val now = System.currentTimeMillis()
            buffers.entries.removeIf { now - it.value.ts > timeoutMs }
            val key = f.requestId to f.messageType
            if (f.totalChunks !in 1..MAX_TOTAL_CHUNKS ||
                f.chunkIndex !in 0 until f.totalChunks ||
                f.payload.size > MAX_FRAME_PAYLOAD
            ) {
                buffers.remove(key)
                throw CodecException("invalid chunk metadata")
            }
            if (f.totalChunks == 1) return f.payload
            var e = buffers[key]
            if (e == null) {
                require(buffers.size < maxConcurrent) { "too many concurrent messages" }
                e = Entry(f.totalChunks, HashMap(), 0, now)
                buffers[key] = e
            }
            if (e.total != f.totalChunks) {
                buffers.remove(key)
                throw CodecException("totalChunks mismatch")
            }
            val previousSize = e.chunks[f.chunkIndex]?.size ?: 0
            val newByteCount = e.byteCount - previousSize + f.payload.size
            if (newByteCount > MAX_MESSAGE_BYTES) {
                buffers.remove(key)
                throw CodecException("reassembled message too large")
            }
            e.chunks[f.chunkIndex] = f.payload.copyOf()
            e.byteCount = newByteCount
            e.ts = now
            if (e.chunks.size == e.total) {
                val out = (0 until e.total).map { e.chunks[it]!! }.fold(ByteArray(0)) { a, b -> a + b }
                buffers.remove(key)
                return out
            }
            return null
        }

        @Synchronized
        fun collectTimeouts(): Boolean {
            val now = System.currentTimeMillis()
            val expired = buffers.filter { now - it.value.ts > timeoutMs }.keys.toList()
            expired.forEach { buffers.remove(it) }
            return expired.isNotEmpty()
        }
    }
}
