package com.gearan.watch.storage

import kotlinx.serialization.Serializable

@Serializable
data class TrustedDevice(
    val gearDeviceId: String,
    val model: String = "Galaxy Watch4 Classic",
    val displayName: String = "Galaxy Watch4 Classic",
    val protocolVersion: Int = 1,
    val publicIdentity: String = "",
    val pairingDate: String = "",
    val lastSeen: String = "",
    val capabilities: List<String> = emptyList(),
    val softwareVersion: String = "",
    val preferredTransport: String = "ble-gatt",
)

/** iPhone-side record of the paired Watch (same schema, role swapped). */
@Serializable
data class TrustedWatch(
    val gearDeviceId: String,
    val model: String = "Galaxy Watch4 Classic",
    val displayName: String = "Galaxy Watch4 Classic",
    val protocolVersion: Int = 1,
    val publicIdentity: String = "",
    val pairingDate: String = "",
    val lastSeen: String = "",
    val capabilities: List<String> = emptyList(),
    val softwareVersion: String = "",
    val preferredTransport: String = "ble-gatt",
)
