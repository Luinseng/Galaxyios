package com.gearan.watch.security

import com.gearan.watch.util.GearanLog
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec

/**
 * Ephemeral handshake state machine (Watch side).
 * Transcript = ordered handshake bytes; SAS shown to user; session/app keys
 * derived only after mutual PAIR_CONFIRM. SAS is never a key.
 * ECDH group (X25519|P-256) negotiated via PAIR_HELLO and transcript-bound.
 */
class GearanSecureSession {
    var ephemeral: CryptoUtils.Ephemeral? = null
        private set
    var peerEphemeralPub: PublicKey? = null
        private set
    var group: CryptoUtils.EcdhGroup = CryptoUtils.EcdhGroup.X25519
        private set
    var nonceLocal: ByteArray = CryptoUtils.randomBytes(16)
        private set
    var noncePeer: ByteArray? = null
        private set
    private val transcriptSink = mutableListOf<ByteArray>()
    var sessionKey: ByteArray? = null
        private set
    var encKey: ByteArray? = null
        private set
    var macKey: ByteArray? = null
        private set
    var sasCode: String? = null
        private set

    fun beginHandshake() {
        ephemeral = CryptoUtils.generateEphemeral()
        group = ephemeral!!.group
        nonceLocal = CryptoUtils.randomBytes(16)
        transcriptSink.clear()
        GearanLog.crypto("handshake begin (group=$group)")
    }

    /** Our ephemeral public bytes for PAIR_EPHEMERAL. */
    fun localPublicBytes(): ByteArray = requireNotNull(ephemeral).keyPair.public.encoded

    fun recordTranscript(vararg parts: ByteArray) {
        transcriptSink.addAll(parts.toList())
    }

    fun transcript(): ByteArray = transcriptSink.fold(ByteArray(0)) { a, b -> a + b }

    fun onPeerEphemeral(rawPub: ByteArray, peerNonce: ByteArray, peerGroup: CryptoUtils.EcdhGroup) {
        if (peerGroup != group) {
            // Transcript-bound: mismatch aborts pairing (downgrade visible, not silent).
            throw IllegalStateException("ECDH group mismatch local=$group peer=$peerGroup")
        }
        val kf = KeyFactory.getInstance(if (peerGroup == CryptoUtils.EcdhGroup.X25519) "XDH" else "EC")
        peerEphemeralPub = kf.generatePublic(X509EncodedKeySpec(rawPub))
        noncePeer = peerNonce
    }

    /** Call after both ephemeral pubs + nonces are known. Returns SAS for display. */
    fun deriveSession(): String {
        val eph = requireNotNull(ephemeral)
        val peer = requireNotNull(peerEphemeralPub)
        val shared = CryptoUtils.ecdhShared(eph.keyPair.private, peer, group)
        val key = CryptoUtils.sessionKey(shared, noncePeer ?: nonceLocal, nonceLocal)
        sessionKey = key
        sasCode = CryptoUtils.sasFromTranscript(CryptoUtils.sha256(transcript()))
        val (enc, mac) = CryptoUtils.appKeys(key)
        encKey = enc
        macKey = mac
        // Log MATCHED-eligible event only; never the code or key material.
        GearanLog.crypto("session derived (group=$group), SAS ready for display")
        return sasCode!!
    }

    fun commitWord(role: String): ByteArray {
        val sk = requireNotNull(sessionKey)
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(sk, "HmacSHA256"))
        return mac.doFinal(("commit" + role).toByteArray() + CryptoUtils.sha256(transcript()))
    }

    fun reset() {
        ephemeral = null
        peerEphemeralPub = null
        sessionKey = null
        encKey = null
        macKey = null
        sasCode = null
        transcriptSink.clear()
    }
}
