package com.gearan.watch.security

import com.gearan.watch.util.GearanLog
import java.security.PublicKey
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Watch-side handshake. Watch is protocol responder (`nonceW`). */
class GearanSecureSession {
    enum class Role { INITIATOR, RESPONDER }

    val role = Role.RESPONDER
    var ephemeral: CryptoUtils.Ephemeral? = null
        private set
    var peerEphemeralPub: PublicKey? = null
        private set
    var group = CryptoUtils.EcdhGroup.X25519
        private set
    var nonceLocal = CryptoUtils.randomBytes(16)
        private set
    var noncePeer: ByteArray? = null
        private set
    var sessionKey: ByteArray? = null
        private set
    var encKey: ByteArray? = null
        private set
    var macKey: ByteArray? = null
        private set
    var sasCode: String? = null
        private set
    private val transcriptSink = mutableListOf<ByteArray>()

    fun beginHandshake() {
        ephemeral = CryptoUtils.generateEphemeral()
        group = requireNotNull(ephemeral).group
        nonceLocal = CryptoUtils.randomBytes(16)
        peerEphemeralPub = null
        noncePeer = null
        sessionKey = null
        encKey = null
        macKey = null
        sasCode = null
        transcriptSink.clear()
        GearanLog.crypto("handshake begin (group=$group)")
    }

    /** Canonical 32-byte X25519 or 65-byte uncompressed X9.63 P-256. */
    fun localPublicBytes(): ByteArray {
        val local = requireNotNull(ephemeral) { "handshake not started" }
        return CryptoUtils.publicKeyToWire(local.keyPair.public, local.group)
    }

    fun recordTranscript(vararg parts: ByteArray) { transcriptSink.addAll(parts) }
    fun transcript(): ByteArray = transcriptSink.fold(ByteArray(0)) { all, part -> all + part }

    fun onPeerEphemeral(wirePublicKey: ByteArray, peerNonce: ByteArray, peerGroup: CryptoUtils.EcdhGroup) {
        check(peerGroup == group) { "ECDH group mismatch local=$group peer=$peerGroup" }
        require(peerNonce.size == 16) { "pairing nonce must be exactly 16 bytes" }
        peerEphemeralPub = CryptoUtils.publicKeyFromWire(wirePublicKey, peerGroup)
        noncePeer = peerNonce.copyOf()
    }

    fun deriveSession(): String {
        val local = requireNotNull(ephemeral) { "handshake not started" }
        val peer = requireNotNull(peerEphemeralPub) { "peer public key missing" }
        val peerNonce = requireNotNull(noncePeer) { "peer nonce missing" }
        val shared = CryptoUtils.ecdhShared(local.keyPair.private, peer, group)
        val (nonceI, nonceW) = orderedNonces(role, nonceLocal, peerNonce)
        val key = CryptoUtils.sessionKey(shared, nonceI, nonceW)
        sessionKey = key
        sasCode = CryptoUtils.sasFromTranscript(CryptoUtils.sha256(transcript()))
        CryptoUtils.appKeys(key).also { (enc, mac) -> encKey = enc; macKey = mac }
        GearanLog.crypto("session derived (group=$group), SAS ready for display")
        return requireNotNull(sasCode)
    }

    fun commitWord(roleLabel: String): ByteArray {
        val key = requireNotNull(sessionKey) { "session key not derived" }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(("commit" + roleLabel).toByteArray() + CryptoUtils.sha256(transcript()))
    }

    fun reset() {
        ephemeral = null
        peerEphemeralPub = null
        noncePeer = null
        sessionKey = null
        encKey = null
        macKey = null
        sasCode = null
        transcriptSink.clear()
    }

    internal fun orderedNonces(role: Role, local: ByteArray, peer: ByteArray) = when (role) {
        Role.INITIATOR -> local to peer
        Role.RESPONDER -> peer to local
    }
}
