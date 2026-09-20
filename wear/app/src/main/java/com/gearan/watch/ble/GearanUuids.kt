package com.gearan.watch.ble

import java.util.UUID

/** Gearan Link 0.1 BLE identifiers. Must match ios/ + protocol doc. */
object GearanUuids {
    val SERVICE: UUID = UUID.fromString("6E400001-8A21-4A11-9B5C-F3F2A1E4B001")
    val RX: UUID = UUID.fromString("6E400002-8A21-4A11-9B5C-F3F2A1E4B001") // iPhone -> Watch
    val TX: UUID = UUID.fromString("6E400003-8A21-4A11-9B5C-F3F2A1E4B001") // Watch -> iPhone (notify)
    val CTRL: UUID = UUID.fromString("6E400004-8A21-4A11-9B5C-F3F2A1E4B001")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val ADVERTISED_NAME = "Gearan Watch4C"
    const val MODEL_STRING = "Galaxy Watch4 Classic"
}
