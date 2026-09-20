package com.gearan.watch.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.gearan.watch.util.GearanLog
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.NamedParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Standard primitives only. No custom crypto.
 *
 * ECDH agility (protocol §4.1): ephemeral X25519 preferred; if the provider on
 * the device (Wear OS 3 / API 30 Conscrypt) lacks XDH, fall back to ephemeral
 * ECDH P-256 — modern, standard, and interoperable with CryptoKit on iOS.
 * The negotiated group is signalled in PAIR_HELLO (`ecdh=x25519|p256`) and
 * covered by the transcript, so downgrade is detected, not silent.
 * KDF/binding (HKDF-SHA256, SAS) are identical for both groups.
 * - AES-GCM-128 (first 16 B of 32 B app key) for ENC payloads;
 *   HMAC-SHA256 for frame auth.
 * - Long-term identity keypair in Android Keystore; SAS is display-only.
 */
object CryptoUtils {
    private val random = SecureRandom()
    const val PAIRING_TIMEOUT_MS = 120_000L

    enum class EcdhGroup { X25519, P256 }

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also { random.nextBytes(it) }

    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int = 32): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        var t = ByteArray(0)
        var okm = ByteArray(0)
        var counter = 1
        while (okm.size < length) {
            val m2 = Mac.getInstance("HmacSHA256")
            m2.init(SecretKeySpec(prk, "HmacSHA256"))
            m2.update(t)
            m2.update(info)
            m2.update(counter.toByte())
            t = m2.doFinal()
            okm += t
            counter++
        }
        return okm.copyOf(length)
    }

    fun sha256(vararg parts: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        parts.forEach { md.update(it) }
        return md.digest()
    }

    /** 6-digit SAS, zero-padded, derived from transcript. NEVER use as a key. */
    fun sasFromTranscript(transcript: ByteArray): String {
        val digest = sha256(transcript)
        val v = ((digest[0].toInt() and 0xFF) shl 24) or
            ((digest[1].toInt() and 0xFF) shl 16) or
            ((digest[2].toInt() and 0xFF) shl 8) or
            (digest[3].toInt() and 0xFF)
        val code = (v.toLong() and 0xFFFFFFFFL) % 1_000_000L
        return "%06d".format(code)
    }

    fun sessionKey(sharedSecret: ByteArray, nonceI: ByteArray, nonceW: ByteArray): ByteArray =
        hkdfSha256(
            ikm = sharedSecret + nonceI + nonceW,
            salt = "Gearan-Link-0.1".toByteArray(),
            info = "session".toByteArray(),
            length = 32
        )

    fun appKeys(sessionKey: ByteArray): Pair<ByteArray, ByteArray> {
        val okm = hkdfSha256(sessionKey, "Gearan-AppKeys-0.1".toByteArray(), "keys".toByteArray(), 64)
        return okm.copyOfRange(0, 32) to okm.copyOfRange(32, 64)
    }

    data class Ephemeral(val group: EcdhGroup, val keyPair: KeyPair)

    /** Generate ephemeral keypair, X25519 first, P-256 fallback. Never throws silently. */
    fun generateEphemeral(): Ephemeral {
        try {
            val kpg = KeyPairGenerator.getInstance("XDH")
            kpg.initialize(NamedParameterSpec("X25519"), random)
            return Ephemeral(EcdhGroup.X25519, kpg.generateKeyPair())
        } catch (e: Exception) {
            GearanLog.crypto("X25519 unavailable (${e.javaClass.simpleName}), using P-256")
        }
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"), random)
        return Ephemeral(EcdhGroup.P256, kpg.generateKeyPair())
    }

    fun preferredGroup(): EcdhGroup = try {
        KeyPairGenerator.getInstance("XDH").initialize(NamedParameterSpec("X25519"), random)
        EcdhGroup.X25519
    } catch (e: Exception) {
        EcdhGroup.P256
    }

    fun ecdhShared(priv: java.security.PrivateKey, pub: java.security.PublicKey, group: EcdhGroup): ByteArray {
        val ka = KeyAgreement.getInstance(if (group == EcdhGroup.X25519) "XDH" else "ECDH")
        ka.init(priv)
        ka.doPhase(pub, true)
        return ka.generateSecret()
    }

    // --- AES-GCM payload encryption (12 B nonce prepended, tag inside cipher) ---
    fun aesGcmEncrypt(key: ByteArray, plaintext: ByteArray): ByteArray {
        val nonce = randomBytes(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.copyOf(16), "AES"), GCMParameterSpec(128, nonce))
        return nonce + cipher.doFinal(plaintext)
    }

    fun aesGcmDecrypt(key: ByteArray, blob: ByteArray): ByteArray {
        require(blob.size >= 12 + 16)
        val nonce = blob.copyOfRange(0, 12)
        val ct = blob.copyOfRange(12, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key.copyOf(16), "AES"), GCMParameterSpec(128, nonce))
        return cipher.doFinal(ct)
    }
}

/** Long-term device identity backed by Android Keystore (EC P-256). */
class IdentityStore(private val alias: String = "gearan_identity") {
    fun getOrCreate(): KeyPair {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.PrivateKeyEntry)?.let {
            return KeyPair(it.certificate.publicKey, it.privateKey)
        }
        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        kpg.initialize(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        return kpg.generateKeyPair()
    }

    fun rawPublicKeyBytes(): ByteArray = getOrCreate().public.encoded
}

/**
 * On-device crypto self-test (Developer → Crypto self-test).
 * Verifies against the REAL provider on the Watch4 Classic: ephemeral gen,
 * key agreement (both sides), HKDF, AES-GCM round-trip, HMAC-SHA256,
 * Keystore persistence. Returns PASS or a precise technical error.
 */
object CryptoSelfTest {
    data class Result(val pass: Boolean, val detail: String)

    fun run(identityAlias: String = "gearan_selftest"): Result {
        return try {
            // 1. ephemeral generation (agility-aware)
            val a = CryptoUtils.generateEphemeral()
            val b = CryptoUtils.generateEphemeral()
            if (a.group != b.group) {
                return Result(false, "ECDH group unstable across generations: ${a.group} vs ${b.group}")
            }
            // 2. key agreement both directions must match
            val sa = CryptoUtils.ecdhShared(a.keyPair.private, b.keyPair.public, a.group)
            val sb = CryptoUtils.ecdhShared(b.keyPair.private, a.keyPair.public, a.group)
            if (!sa.contentEquals(sb)) return Result(false, "ECDH agreement mismatch (group=${a.group})")
            // 3. HKDF determinism + length
            val k1 = CryptoUtils.sessionKey(sa, ByteArray(16) { 0x01 }, ByteArray(16) { 0x02 })
            val k2 = CryptoUtils.sessionKey(sa, ByteArray(16) { 0x01 }, ByteArray(16) { 0x02 })
            if (!k1.contentEquals(k2) || k1.size != 32) return Result(false, "HKDF-SHA256 failed")
            // 4. AES-GCM round-trip
            val (enc, _) = CryptoUtils.appKeys(k1)
            val pt = "gearan-selftest".toByteArray()
            val dec = CryptoUtils.aesGcmDecrypt(enc, CryptoUtils.aesGcmEncrypt(enc, pt))
            if (!dec.contentEquals(pt)) return Result(false, "AES-GCM round-trip failed")
            // 5. HMAC-SHA256 verify path (frame auth primitive)
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(k1, "HmacSHA256"))
            val tag = mac.doFinal("frame".toByteArray())
            if (tag.size != 32) return Result(false, "HMAC-SHA256 length wrong")
            // 6. Keystore persistence
            val store = IdentityStore(identityAlias)
            val p1 = store.rawPublicKeyBytes()
            val p2 = store.rawPublicKeyBytes()
            if (!p1.contentEquals(p2)) return Result(false, "Android Keystore identity not persistent")
            GearanLog.crypto("self-test PASS (group=${a.group})")
            Result(true, "PASS (group=${a.group})")
        } catch (e: Exception) {
            GearanLog.cryptoWarn("self-test FAIL", e)
            Result(false, "FAIL: ${e.javaClass.name}: ${e.message}")
        }
    }
}
