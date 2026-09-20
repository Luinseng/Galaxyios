package com.gearan.watch.ble

import com.gearan.watch.security.CryptoUtils
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Cross-language golden vectors (tests/vectors/golden_vectors.json).
 * Same file asserted by Python (test_vectors.py) and Swift (GoldenVectorTests).
 */
class GoldenVectorTest {
    private fun vectors(): JSONObject {
        // Under `wear/`: tests live at ../../tests/vectors (repo root layout).
        val candidates = listOf(
            File("tests/vectors/golden_vectors.json"),
            File("../tests/vectors/golden_vectors.json"),
            File("../../tests/vectors/golden_vectors.json"),
            File("wear/../tests/vectors/golden_vectors.json")
        )
        val f = candidates.firstOrNull { it.exists() }
            ?: error("golden_vectors.json not found (run from repo root or wear/)")
        return JSONObject(f.readText())
    }

    private fun hex(s: String): ByteArray {
        require(s.length % 2 == 0)
        return ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    private fun b64(s: String): ByteArray = java.util.Base64.getDecoder().decode(s)

    @Test
    fun framePingVector() {
        val v = vectors().getJSONObject("frame_ping")
        val key = hex(v.getString("authKeyHex"))
        val f = LinkFrameCodec.Frame(
            v.getInt("messageType"), v.getInt("flags"), v.getInt("requestId"),
            v.getLong("sequence"), v.getInt("chunkIndex"), v.getInt("totalChunks"),
            b64(v.getString("payloadB64"))
        )
        assertEquals(v.getString("encodedHex"), LinkFrameCodec.encode(f, key).joinToString("") { "%02x".format(it.toInt() and 0xFF) })
    }

    @Test
    fun hkdfSessionVector() {
        val v = vectors().getJSONObject("hkdf_session")
        val okm = CryptoUtils.hkdfSha256(
            hex(v.getString("ikmHex")),
            v.getString("saltAscii").toByteArray(),
            v.getString("infoAscii").toByteArray(),
            v.getInt("length")
        )
        assertEquals(v.getString("okmHex"), okm.joinToString("") { "%02x".format(it.toInt() and 0xFF) })
    }

    @Test
    fun sasVector() {
        val v = vectors().getJSONObject("sas")
        val code = CryptoUtils.sasFromTranscript(hex(v.getString("transcriptHex")))
        assertEquals(v.getString("sas"), code)
    }

    @Test
    fun aesGcmNistCase2Decrypt() {
        // NIST SP 800-38D D.2 case 2: K=0^128, IV=0^96, P=0^128.
        val key = ByteArray(16)
        val iv = ByteArray(12)
        val ct = hex("0388dace60b6a392f328c2b971b2fe78")
        val tag = hex("ab6e47d42cec13bdf53a67b21257bddf")
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            javax.crypto.Cipher.DECRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(key, "AES"),
            javax.crypto.spec.GCMParameterSpec(128, iv)
        )
        cipher.updateAAD(ByteArray(0))
        val pt = cipher.doFinal(ct + tag)
        assertEquals(16, pt.size)
        assertEquals(0, pt.sumOf { it.toInt() })
    }
}
