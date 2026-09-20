package com.gearan.watch.ble

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-wide BLE state hub. The foreground GATT service writes here;
 * Activities/screens only read. Solves the "service instance unreachable"
 * problem without fragile service binding on a round watch screen.
 */
object GearanBleHub {
    private val _advertising = MutableStateFlow(false)
    val advertising: StateFlow<Boolean> = _advertising

    private val _serverRunning = MutableStateFlow(false)
    val serverRunning: StateFlow<Boolean> = _serverRunning

    private val _connections = MutableStateFlow(0)
    val connections: StateFlow<Int> = _connections

    private val _lastEvent = MutableStateFlow("idle")
    val lastEvent: StateFlow<String> = _lastEvent

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val eventLog: StateFlow<List<String>> = _log

    // Non-UI callbacks (pairing manager hooks in via application scope).
    var onRx: ((device: BluetoothDevice, bytes: ByteArray) -> Unit)? = null
    var onConnected: ((BluetoothDevice) -> Unit)? = null
    var onDisconnected: ((BluetoothDevice) -> Unit)? = null

    fun setAdvertising(v: Boolean) { _advertising.value = v }
    fun setServerRunning(v: Boolean) { _serverRunning.value = v }
    fun setConnections(n: Int) { _connections.value = n }

    @Synchronized
    fun event(text: String) {
        _lastEvent.value = text
        _log.value = (_log.value + text).takeLast(50)
    }
}
