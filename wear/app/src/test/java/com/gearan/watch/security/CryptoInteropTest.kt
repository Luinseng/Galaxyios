package com.gearan.watch.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CryptoInteropTest {
    @Test fun x25519WireKeysAreRawAndRoundTrip() {
        val local = CryptoUtils.generateEphemeral()
        val peer = CryptoUtils.generateEphemeral()
        if (local.group != CryptoUtils.EcdhGroup.X25519 || peer.group != local.group) return
        val localWire = CryptoUtils.publicKeyToWire(local.keyPair.public, local.group)
        val peerWire = CryptoUtils.publicKeyToWire(peer.keyPair.public, peer.group)
        assertEquals(32, localWire.size)
        assertEquals(32, peerWire.size)
        val a = CryptoUtils.ecdhShared(local.keyPair.private, CryptoUtils.publicKeyFromWire(peerWire, peer.group), local.group)
        val b = CryptoUtils.ecdhShared(peer.keyPair.private, CryptoUtils.publicKeyFromWire(localWire, local.group), peer.group)
        assertArrayEquals(a, b)
    }

    @Test fun sessionKdfUsesInitiatorThenWatchNonce() {
        val shared = ByteArray(32) { it.toByte() }
        val nonceI = ByteArray(16) { 1 }
        val nonceW = ByteArray(16) { 2 }
        assertEquals("f6dd43ab8f57853d3aa41adce3fdfa748ee86a14604a5d8b7775ffa421014cc0",
            CryptoUtils.sessionKey(shared, nonceI, nonceW).toHex())
        assertFalse(CryptoUtils.sessionKey(shared, nonceI, nonceW)
            .contentEquals(CryptoUtils.sessionKey(shared, nonceW, nonceI)))
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
