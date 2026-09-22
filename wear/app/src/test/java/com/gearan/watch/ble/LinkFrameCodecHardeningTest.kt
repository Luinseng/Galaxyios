package com.gearan.watch.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class LinkFrameCodecHardeningTest {
    private val key = ByteArray(32) { 0x4B }

    private fun frame(index: Int, total: Int, payload: ByteArray, requestId: Int = 7) =
        LinkFrameCodec.Frame(LinkFrameCodec.MT_SYNC_DATA, 0, requestId, index.toLong(), index, total, payload)

    @Test fun trailingBytesRejected() {
        val raw = LinkFrameCodec.encode(frame(0, 1, "ok".toByteArray()), key) + byteArrayOf(1)
        try {
            LinkFrameCodec.decode(raw, key)
            fail("trailing bytes accepted")
        } catch (e: LinkFrameCodec.CodecException) {
            // expected
        }
    }

    @Test fun duplicateReplacementAndMaximumSize() {
        val r = LinkFrameCodec.Reassembler()
        assertNull(r.feed(frame(0, 2, ByteArray(LinkFrameCodec.MAX_FRAME_PAYLOAD) { 1 })))
        assertNull(r.feed(frame(0, 2, byteArrayOf(2))))
        assertArrayEquals(byteArrayOf(2, 3), r.feed(frame(1, 2, byteArrayOf(3))))

        val max = LinkFrameCodec.Reassembler()
        var result: ByteArray? = null
        repeat(LinkFrameCodec.MAX_TOTAL_CHUNKS) { index ->
            result = max.feed(frame(index, LinkFrameCodec.MAX_TOTAL_CHUNKS,
                ByteArray(LinkFrameCodec.MAX_FRAME_PAYLOAD) { 4 }, requestId = 8))
        }
        assertArrayEquals(ByteArray(LinkFrameCodec.MAX_MESSAGE_BYTES) { 4 }, result)
    }

    @Test fun mismatchClearsBuffer() {
        val r = LinkFrameCodec.Reassembler()
        assertNull(r.feed(frame(0, 2, "old".toByteArray())))
        try {
            r.feed(frame(1, 3, "bad".toByteArray()))
            fail("mismatch accepted")
        } catch (e: LinkFrameCodec.CodecException) {
            // expected
        }
        assertNull(r.feed(frame(1, 2, "new-1".toByteArray())))
        assertArrayEquals("new-0new-1".toByteArray(), r.feed(frame(0, 2, "new-0".toByteArray())))
    }
}
