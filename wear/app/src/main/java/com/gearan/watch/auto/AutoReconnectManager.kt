package com.gearan.watch.auto

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Watch-side auto-reconnect: keeps low-power advertising alive for the trusted
 * iPhone, with exponential backoff (1 s → 30 s max) after disconnects.
 * A plain disconnect NEVER deletes the trusted association.
 */
class AutoReconnectManager(private val scope: CoroutineScope) {
    var enabled: Boolean = true
    private var job: Job? = null
    var attempts: Int = 0
        private set

    fun onDisconnected(startAdvertising: () -> Unit) {
        if (!enabled) return
        job?.cancel()
        job = scope.launch {
            attempts = 0
            var backoff = 1_000L
            while (enabled) {
                delay(backoff)
                attempts++
                try { startAdvertising() } catch (_: Exception) { }
                backoff = minOf(backoff * 2, 30_000L)
                if (attempts > 60) return@launch
            }
        }
    }

    fun onConnected() {
        job?.cancel()
        job = null
        attempts = 0
    }

    /** Explicit stop (e.g. Forget iPhone). Never touches the trusted store. */
    fun stop() {
        enabled = false
        job?.cancel()
        job = null
        attempts = 0
    }
}
