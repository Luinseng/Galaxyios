package com.gearan.watch.ble

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.IBinder
import com.gearan.watch.util.GearanLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Foreground GATT server: RX (write), TX (notify), CTRL (read/write).
 * Sensitive characteristics use PERMISSION_READ_ENCRYPTED/WRITE_ENCRYPTED so
 * the OS may trigger bonding; Gearan's own trusted relationship stays
 * independent of that bond. All Gearan Link frames are authenticated; ENC
 * payloads are AES-GCM after handshake.
 *
 * State is published to [GearanBleHub] so the UI smoke test can show
 * Advertising / GATT Server / Connections without service binding.
 */
@Suppress("DEPRECATION") // characteristic.value + 3-arg notify: compat path for API 30..34
class GearanPeripheralService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var server: BluetoothGattServer? = null
    private var txChar: BluetoothGattCharacteristic? = null
    private var serviceAdded = false
    private val subscribers = mutableSetOf<BluetoothDevice>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        GearanLog.ble("peripheral service onStartCommand")
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundWithChannel()
        openGattServer()
    }

    override fun onDestroy() {
        try { server?.close() } catch (e: Exception) {
            GearanLog.bleWarn("gatt server close failed", e)
        }
        server = null
        txChar = null
        serviceAdded = false
        subscribers.clear()
        GearanBleHub.setServerRunning(false)
        GearanBleHub.setConnections(0)
        job.cancel()
        super.onDestroy()
    }

    private fun startForegroundWithChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel("gearan", "Gearan", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, "gearan")
            .setContentTitle("Gearan")
            .setContentText("Ready to connect to iPhone")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .build()
        try {
            startForeground(1, n)
        } catch (e: Exception) {
            GearanLog.bleWarn("startForeground failed", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun openGattServer() {
        if (serviceAdded) {
            GearanLog.ble("gatt: service already registered, skipping duplicate")
            GearanBleHub.event("gatt: already registered")
            return
        }
        val bt = getSystemService(BluetoothManager::class.java)
        if (bt == null) {
            GearanLog.bleWarn("BluetoothManager unavailable")
            GearanBleHub.event("gatt: no BluetoothManager")
            return
        }
        if (bt.adapter == null || !bt.adapter.isEnabled) {
            GearanLog.bleWarn("Bluetooth adapter unavailable/disabled")
            GearanBleHub.event("gatt: adapter off")
            return
        }
        server = bt.adapter.bluetoothGattServer(this, callback)
        if (server == null) {
            GearanLog.bleWarn("openGattServer returned null")
            GearanBleHub.event("gatt: open failed")
            return
        }
        val service = BluetoothGattService(GearanUuids.SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val rx = BluetoothGattCharacteristic(
            GearanUuids.RX,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED
        )
        txChar = BluetoothGattCharacteristic(
            GearanUuids.TX,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
        )
        txChar?.addDescriptor(
            BluetoothGattDescriptor(
                GearanUuids.CCCD,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
            )
        )
        val ctrl = BluetoothGattCharacteristic(
            GearanUuids.CTRL,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED or BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED
        )
        service.addCharacteristic(rx)
        service.addCharacteristic(txChar)
        service.addCharacteristic(ctrl)
        val added = server?.addService(service) ?: false
        if (added) serviceAdded = true
        GearanLog.ble("gatt server open, service added=$added uuid=${GearanUuids.SERVICE}")
        GearanBleHub.setServerRunning(added)
        GearanBleHub.event(if (added) "gatt: service registered" else "gatt: addService FAILED")
    }

    @SuppressLint("MissingPermission")
    fun notifyTx(payload: ByteArray) {
        val c = txChar ?: return
        c.value = payload
        subscribers.toList().forEach { server?.notifyCharacteristicChanged(it, c, false) }
    }

    private val callback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            GearanLog.ble("onServiceAdded status=$status uuid=${service.uuid}")
            GearanBleHub.event("gatt: onServiceAdded status=$status")
            GearanBleHub.setServerRunning(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            val addr = anonymize(device)
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                GearanLog.ble("central connected addr=$addr status=$status")
                GearanBleHub.event("gatt: connected $addr")
                GearanBleHub.setConnections(GearanBleHub.connections.value + 1)
                scope.launch { GearanBleHub.onConnected?.invoke(device) }
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                subscribers.remove(device)
                GearanLog.ble("central disconnected addr=$addr status=$status")
                GearanBleHub.event("gatt: disconnected $addr")
                GearanBleHub.setConnections(maxOf(0, GearanBleHub.connections.value - 1))
                scope.launch { GearanBleHub.onDisconnected?.invoke(device) }
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray
        ) {
            if (descriptor.uuid == GearanUuids.CCCD) {
                if (value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                    subscribers.add(device)
                    GearanLog.ble("notify enabled by ${anonymize(device)}")
                    GearanBleHub.event("gatt: notify enabled")
                } else {
                    subscribers.remove(device)
                    GearanLog.ble("notify disabled by ${anonymize(device)}")
                }
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            } else {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray
        ) {
            // Log lengths only — never payload bytes (may carry key material).
            GearanLog.link("rx write uuid=${characteristic.uuid} len=${value.size} from=${anonymize(device)}")
            if (characteristic.uuid == GearanUuids.RX || characteristic.uuid == GearanUuids.CTRL) {
                scope.launch { GearanBleHub.onRx?.invoke(device, value) }
                if (responseNeeded) server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            } else {
                if (responseNeeded) server?.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, 0, null)
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == GearanUuids.CTRL) {
                val info = DEVICE_INFO_STRING.toByteArray()
                val sliced = if (offset >= info.size) ByteArray(0) else info.copyOfRange(offset, info.size)
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, sliced)
                GearanLog.link("tx device-info len=${sliced.size} to=${anonymize(device)}")
            } else {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
            }
        }
    }

    companion object {
        /** MILESTONE 1 payload: readable before any crypto. */
        const val DEVICE_INFO_STRING = "Gearan/0.1.0;Samsung Galaxy Watch4 Classic;proto=0.1"
    }
}

/** Never log raw MACs: last 2 bytes only, for correlation in diagnostics. */
internal fun anonymize(device: BluetoothDevice): String = try {
    val a = device.address ?: "??:??:??:??:??:??"
    "…" + a.takeLast(5)
} catch (e: SecurityException) {
    GearanLog.bleWarn("address hidden (no permission)")
    "(hidden)"
}

/** Helper to chunk an encoded message into ATT-safe notifications (≤ 512 B each). */
object TxChunker {
    const val ATT_CHUNK = 512
    fun chunk(bytes: ByteArray): List<ByteArray> {
        if (bytes.isEmpty()) return listOf(ByteArray(0))
        return bytes.asList().chunked(ATT_CHUNK) { part -> part.toByteArray() }
    }
}
