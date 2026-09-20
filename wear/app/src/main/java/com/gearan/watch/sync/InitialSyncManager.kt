package com.gearan.watch.sync

import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class DeviceInfoPayload(
    val model: String = "Galaxy Watch4 Classic",
    val batteryPercent: Int = -1,
    val charging: Boolean = false,
    val wearOsVersion: String = "",
    val oneUiVersion: String = "",
    val gearanVersion: String = "0.1.0",
    val capabilities: List<String> = emptyList(),
    val sensors: List<String> = emptyList(),
    val networkRelay: Boolean = true,
    val notifications: Boolean = true,
    val healthSensors: Boolean = true,
)

enum class SyncPhase {
    CONNECTING, SECURING, CHECKING_WATCH, CHECKING_SENSORS, SYNCHRONIZING, COMPLETE
}

/**
 * Initial sync orchestrator (Watch side): emits DeviceInfo + caps + battery
 * in the order the iPhone progress UI expects.
 */
class InitialSyncManager {
    private val _phase = MutableStateFlow(SyncPhase.CONNECTING)
    val phase: StateFlow<SyncPhase> = _phase
    private val json = Json { ignoreUnknownKeys = true }

    fun deviceInfo(batteryPercent: Int, charging: Boolean, caps: List<String>, sensors: List<String>): ByteArray {
        val payload = DeviceInfoPayload(
            batteryPercent = batteryPercent,
            charging = charging,
            wearOsVersion = "Wear OS 3 (API ${Build.VERSION.SDK_INT})",
            oneUiVersion = publiclyReadableOneUi(),
            capabilities = caps,
            sensors = sensors,
        )
        return json.encodeToString(payload).toByteArray()
    }

    fun advanceTo(phase: SyncPhase) { _phase.value = phase }

    /** Only information readable via public APIs; never scrape privileged props. */
    private fun publiclyReadableOneUi(): String = "One UI Watch (public build info only)"
}
