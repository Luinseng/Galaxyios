package com.gearan.watch.network

import kotlinx.serialization.Serializable

/**
 * Network Relay (Watch side): application proxy over Gearan Link, HTTPS-only.
 * The Watch never opens raw sockets through the iPhone; it sends
 * NETWORK_REQUEST frames and renders NETWORK_RESPONSE/ERROR frames.
 */
object NetworkRelay {
    const val MAX_BODY_BYTES = 1_048_576
    const val DEFAULT_TIMEOUT_MS = 15_000

    @Serializable
    data class RelayRequest(
        val id: String,
        val method: String, // GET|POST|HEAD
        val url: String, // https:// only
        val headers: Map<String, String> = emptyMap(),
        val bodyB64: String? = null,
        val timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    ) {
        fun validate(): String? {
            if (method != "GET" && method != "POST" && method != "HEAD") return "METHOD_NOT_ALLOWED"
            if (!url.startsWith("https://")) return "HTTPS_ONLY"
            if (timeoutMs > 30_000) return "TIMEOUT_TOO_LARGE"
            if ((bodyB64?.length ?: 0) > MAX_BODY_BYTES) return "TOO_LARGE"
            return null
        }
    }

    @Serializable
    data class RelayResponse(val id: String, val status: Int, val headers: Map<String, String>, val bodyB64: String?)

    @Serializable
    data class RelayError(val id: String, val reason: String)

    fun testRequest(id: String) = RelayRequest(id, "GET", "https://example.com")
}
