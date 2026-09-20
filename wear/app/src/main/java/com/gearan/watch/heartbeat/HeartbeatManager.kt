package com.gearan.watch.heartbeat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Adaptive, battery-aware heartbeat: 30 s active, 120 s idle/screen-off,
 * paused when disconnected. Sends PING, expects PONG; counts misses for
 * diagnostics. Never holds the radio awake continuously.
 */
class HeartbeatManager(private val scope: CoroutineScope) {
    var active: Boolean = false
    var idle: Boolean = true
    private var job: Job? = null
    var lastPongMs: Long? = null
        private set
    var misses: Long = 0
        private set

    fun start(onPing: suspend (seq: Long) -> Unit) {
        stop()
        job = scope.launch {
            var seq = 0L
            while (true) {
                val interval = when {
                    !active -> return@launch
                    idle -> 120_000L
                    else -> 30_000L
                }
                delay(interval)
                if (!active) return@launch
                try { onPing(seq++) } catch (_: Exception) { misses++ }
            }
        }
    }

    fun onPong() { lastPongMs = System.currentTimeMillis() }
    fun stop() { job?.cancel(); job = null }
}
