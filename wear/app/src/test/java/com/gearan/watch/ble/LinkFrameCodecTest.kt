package com.gearan.watch.ble

import org.junit.Assert.*
import org.junit.Test

class LinkFrameCodecTest {
    private val key = ByteArray(32) { 0x42 }

    @Test
    fun roundTrip() {
        val f = LinkFrameCodec.Frame(LinkFrameCodec.MT_PING, 1, 42, 7, 0, 1, "{\"seq\":7}".toByteArray())
        val back = LinkFrameCodec.decode(LinkFrameCodec.encode(f, key), key)
        assertArrayEquals(f.payload, back.payload)
        assertEquals(42, back.requestId)
    }

    @Test
    fun badMagicRejected() {
        val raw = LinkFrameCodec.encode(
            LinkFrameCodec.Frame(LinkFrameCodec.MT_PING, 0, 1, 1, 0, 1, "x".toByteArray()), key
        )
        raw[0] = 'X'.code.toByte()
        try {
            LinkFrameCodec.decode(raw, key)
            fail("bad magic accepted")
        } catch (e: LinkFrameCodec.CodecException) { assertTrue(e.message!!.contains("magic", true)) }
    }

    @Test
    fun authFailureRejected() {
        val raw = LinkFrameCodec.encode(
            LinkFrameCodec.Frame(LinkFrameCodec.MT_PING, 0, 1, 1, 0, 1, "hello".toByteArray()), key
        )
        try {
            LinkFrameCodec.decode(raw, ByteArray(32) { 0x11 })
            fail("forged frame accepted")
        } catch (e: LinkFrameCodec.CodecException) { assertTrue(e.message!!.contains("authentication", true)) }
    }

    @Test
    fun chunkReassemblyOutOfOrder() {
        val payload = ByteArray(1500) { it.toByte() }
        val frames = LinkFrameCodec.fragment(LinkFrameCodec.MT_SYNC_DATA, payload, 9, 3)
        assertEquals(3, frames.size)
        val r = LinkFrameCodec.Reassembler()
        var out: ByteArray? = null
        listOf(frames[2], frames[0], frames[1]).forEach { out = r.feed(it) ?: out }
        assertArrayEquals(payload, out)
    }

    @Test
    fun replayWindowRejectsDuplicates() {
        val w = LinkFrameCodec.ReplayWindow()
        assertTrue(w.accept(1))
        assertFalse(w.accept(1))
        assertTrue(w.accept(2))
    }
}
