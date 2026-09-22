package com.gearan.watch.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.gearan.watch.util.GearanLog
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.NamedParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    private val random = SecureRandom()
    private val x25519SpkiPrefix = byteArrayOf(
        0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x6e, 0x03, 0x21, 0x00
    )
    const val PAIRING_TIMEOUT_MS = 120_000L

    enum class EcdhGroup { X25519, P256 }
    data class Ephemeral(val group: EcdhGroup, val keyPair: KeyPair)

    fun randomBytes(size: Int) = ByteArray(size).also(random::nextBytes)

    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int = 32): ByteArray {
        require(length in 1..8160) { "invalid HKDF output length" }
        val extract = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(salt, "HmacSHA256")) }
        val prk = extract.doFinal(ikm)
        var previous = ByteArray(0)
        var output = ByteArray(0)
        var counter = 1
        while (output.size < length) {
            previous = Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec(prk, "HmacSHA256"))
                update(previous); update(info); update(counter.toByte()); doFinal()
            }
            output += previous
            counter++
        }
        return output.copyOf(length)
    }

    fun sha256(vararg parts: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").run {
        parts.forEach(::update); digest()
    }

    fun sasFromTranscript(transcript: ByteArray): String {
        val d = sha256(transcript)
        val value = ((d[0].toInt() and 0xff).toLong() shl 24) or
            ((d[1].toInt() and 0xff).toLong() shl 16) or
            ((d[2].toInt() and 0xff).toLong() shl 8) or (d[3].toInt() and 0xff).toLong()
        return "%06d".format(value % 1_000_000L)
    }

    fun sessionKey(sharedSecret: ByteArray, nonceI: ByteArray, nonceW: ByteArray): ByteArray {
        require(nonceI.size == 16 && nonceW.size == 16) { "pairing nonces must be 16 bytes" }
        return hkdfSha256(
            sharedSecret + nonceI + nonceW,
            "Gearan-Link-0.1".toByteArray(),
            "session".toByteArray()
        )
    }

    fun appKeys(sessionKey: ByteArray): Pair<ByteArray, ByteArray> {
        val output = hkdfSha256(sessionKey, "Gearan-AppKeys-0.1".toByteArray(), "keys".toByteArray(), 64)
        return output.copyOfRange(0, 32) to output.copyOfRange(32, 64)
    }

    /** X25519 preferred; P-256 fallback retained for peers advertising support. */
    fun generateEphemeral(): Ephemeral = try {
        val generator = KeyPairGenerator.getInstance("XDH")
        generator.initialize(NamedParameterSpec("X25519"), random)
        Ephemeral(EcdhGroup.X25519, generator.generateKeyPair())
    } catch (error: Exception) {
        GearanLog.crypto("X25519 unavailable (${error.javaClass.simpleName}), using P-256")
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"), random)
        Ephemeral(EcdhGroup.P256, generator.generateKeyPair())
    }

    fun preferredGroup() = try {
        KeyPairGenerator.getInstance("XDH").apply { initialize(NamedParameterSpec("X25519"), random) }
        EcdhGroup.X25519
    } catch (_: Exception) { EcdhGroup.P256 }

    /** 32-byte RFC 7748 X25519 or 65-byte uncompressed X9.63 P-256. */
    fun publicKeyToWire(key: PublicKey, group: EcdhGroup): ByteArray = when (group) {
        EcdhGroup.X25519 -> {
            val encoded = key.encoded
            require(encoded.size == 44 && encoded.copyOfRange(0, 12).contentEquals(x25519SpkiPrefix)) {
                "unexpected X25519 SubjectPublicKeyInfo encoding"
            }
            encoded.copyOfRange(12, 44)
        }
        EcdhGroup.P256 -> {
            val point = (key as? ECPublicKey)?.w
                ?: throw IllegalArgumentException("P-256 provider returned non-EC key")
            byteArrayOf(0x04) + unsigned32(point.affineX) + unsigned32(point.affineY)
        }
    }

    fun publicKeyFromWire(wire: ByteArray, group: EcdhGroup): PublicKey = when (group) {
        EcdhGroup.X25519 -> {
            require(wire.size == 32) { "X25519 public key must be exactly 32 raw bytes" }
            try {
                KeyFactory.getInstance("XDH").generatePublic(X509EncodedKeySpec(x25519SpkiPrefix + wire))
            } catch (error: Exception) {
                throw IllegalStateException("X25519/XDH provider unavailable or rejected raw key", error)
            }
        }
        EcdhGroup.P256 -> {
            require(wire.size == 65 && wire[0] == 0x04.toByte()) {
                "P-256 public key must be 65-byte uncompressed X9.63"
            }
            val params = AlgorithmParameters.getInstance("EC").apply {
                init(ECGenParameterSpec("secp256r1"))
            }.getParameterSpec(java.security.spec.ECParameterSpec::class.java)
            val point = ECPoint(BigInteger(1, wire.copyOfRange(1, 33)), BigInteger(1, wire.copyOfRange(33, 65)))
            KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, params))
        }
    }

    private fun unsigned32(value: BigInteger): ByteArray {
        val signed = value.toByteArray()
        val bytes = if (signed.size == 33 && signed[0] == 0.toByte()) signed.copyOfRange(1, 33) else signed
        require(bytes.size <= 32) { "EC coordinate exceeds 256 bits" }
        return ByteArray(32 - bytes.size) + bytes
    }

    fun ecdhShared(privateKey: PrivateKey, publicKey: PublicKey, group: EcdhGroup): ByteArray {
        val agreement = try {
            KeyAgreement.getInstance(if (group == EcdhGroup.X25519) "XDH" else "ECDH")
        } catch (error: Exception) {
            throw IllegalStateException("${group.name} key agreement provider unavailable", error)
        }
        agreement.init(privateKey); agreement.doPhase(publicKey, true)
        return agreement.generateSecret()
    }

    fun aesGcmEncrypt(key: ByteArray, plaintext: ByteArray): ByteArray {
        val nonce = randomBytes(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.copyOf(16), "AES"), GCMParameterSpec(128, nonce))
        return nonce + cipher.doFinal(plaintext)
    }

    fun aesGcmDecrypt(key: ByteArray, blob: ByteArray): ByteArray {
        require(blob.size >= 28) { "AES-GCM blob is too short" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key.copyOf(16), "AES"), GCMParameterSpec(128, blob.copyOfRange(0, 12)))
        return cipher.doFinal(blob.copyOfRange(12, blob.size))
    }
}

class IdentityStore(private val alias: String = "gearan_identity") {
    fun getOrCreate(): KeyPair {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(alias, null) as? KeyStore.PrivateKeyEntry)?.let {
            return KeyPair(it.certificate.publicKey, it.privateKey)
        }
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        generator.initialize(KeyGenParameterSpec.Builder(
            alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        ).setDigests(KeyProperties.DIGEST_SHA256).build())
        return generator.generateKeyPair()
    }
    fun rawPublicKeyBytes() = getOrCreate().public.encoded
}

object CryptoSelfTest {
    data class Result(val passed: Boolean, val detail: String)
    fun run(identityAlias: String = "gearan_identity_selftest"): Result {
        return try {
        val a = CryptoUtils.generateEphemeral()
        val b = CryptoUtils.generateEphemeral()
        if (a.group != b.group) return Result(false, "ECDH provider group changed")
        val ap = CryptoUtils.publicKeyFromWire(CryptoUtils.publicKeyToWire(a.keyPair.public, a.group), a.group)
        val bp = CryptoUtils.publicKeyFromWire(CryptoUtils.publicKeyToWire(b.keyPair.public, b.group), b.group)
        val sa = CryptoUtils.ecdhShared(a.keyPair.private, bp, a.group)
        val sb = CryptoUtils.ecdhShared(b.keyPair.private, ap, b.group)
        if (!sa.contentEquals(sb)) return Result(false, "ECDH shared secrets differ")
        val (enc, _) = CryptoUtils.appKeys(CryptoUtils.sessionKey(sa, ByteArray(16) { 1 }, ByteArray(16) { 2 }))
        val plain = "gearan-selftest".toByteArray()
        if (!CryptoUtils.aesGcmDecrypt(enc, CryptoUtils.aesGcmEncrypt(enc, plain)).contentEquals(plain))
            return Result(false, "AES-GCM round-trip failed")
        val store = IdentityStore(identityAlias)
        if (!store.rawPublicKeyBytes().contentEquals(store.rawPublicKeyBytes()))
            return Result(false, "Android Keystore identity not persistent")
        Result(true, "PASS (group=${a.group})")
    } catch (error: Exception) {
        GearanLog.cryptoWarn("self-test FAIL", error)
        Result(false, "FAIL: ${error.javaClass.name}: ${error.message}")
        }
    }
}
