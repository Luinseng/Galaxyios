package com.gearan.watch.sync

/** Capability IDs negotiated via CAPS_REQUEST/CAPS_RESPONSE. */
object Capabilities {
    const val BATTERY = "BATTERY"
    const val HEART_RATE = "HEART_RATE"
    const val HEALTH_SENSORS = "HEALTH_SENSORS"
    const val BIA = "BIA"
    const val SPO2 = "SPO2"
    const val NETWORK_RELAY = "NETWORK_RELAY"
    const val GEARAN_NOTIFICATIONS = "GEARAN_NOTIFICATIONS"
    const val ANCS_EXPERIMENTAL = "ANCS_EXPERIMENTAL"
    const val DEVICE_INFO = "DEVICE_INFO"
    const val DIAGNOSTICS = "DIAGNOSTICS"

    val ALL = listOf(
        BATTERY, HEART_RATE, HEALTH_SENSORS, BIA, SPO2, NETWORK_RELAY,
        GEARAN_NOTIFICATIONS, ANCS_EXPERIMENTAL, DEVICE_INFO, DIAGNOSTICS
    )
}

enum class CapabilityStatus {
    AVAILABLE, UNAVAILABLE, PERMISSION_REQUIRED, SDK_RESTRICTION, EXPERIMENTAL
}

data class CapabilityReport(val id: String, val status: CapabilityStatus, val detail: String = "")

/** Bitmask positions for the BLE advertising manufacturer blob. */
object CapsBitmask {
    fun encode(ids: Collection<String>): Int {
        var m = 0
        Capabilities.ALL.forEachIndexed { i, id -> if (ids.contains(id)) m = m or (1 shl i) }
        return m and 0xFFFF
    }
    fun decode(mask: Int): List<String> =
        Capabilities.ALL.filterIndexed { i, _ -> (mask and (1 shl i)) != 0 }
}
