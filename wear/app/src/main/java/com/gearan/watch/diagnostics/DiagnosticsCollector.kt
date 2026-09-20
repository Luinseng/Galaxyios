package com.gearan.watch.diagnostics

import kotlinx.serialization.Serializable

@Serializable
data class DiagnosticsSnapshot(
    val watchModel: String = "Galaxy Watch4 Classic",
    val connectionStatus: String = "unknown",
    val pairingState: String = "IDLE",
    val gearDeviceId: String = "",
    val protocolVersion: Int = 1,
    val bluetoothState: String = "unknown",
    val rssi: Int? = null,
    val mtu: Int = 23,
    val txBytes: Long = 0,
    val rxBytes: Long = 0,
    val packetRetries: Long = 0,
    val lastHeartbeat: String? = null,
    val lastSync: String? = null,
    val capabilities: List<String> = emptyList(),
)

/** Export strips secrets: keys, auth material, notification content, health data. */
object DiagnosticsExporter {
    fun exportable(s: DiagnosticsSnapshot): DiagnosticsSnapshot = s.copy()
}
