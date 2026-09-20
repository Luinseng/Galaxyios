package com.gearan.watch.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import com.gearan.watch.util.GearanLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * BLE advertiser for pairing mode + auto-reconnect.
 * Advertises Gearan Service UUID + manufacturer blob (GR|ver|shortId|caps).
 * Round-robin between low-latency (pairing) and low-power (reconnect) modes
 * to protect Watch4 Classic battery. State mirrored to [GearanBleHub].
 */
class GearanAdvertiser(private val context: Context) {
    private val _advertising = MutableStateFlow(false)
    val advertising: StateFlow<Boolean> = _advertising
    private var advertiser: BluetoothLeAdvertiser? = null

    private val callback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            GearanLog.ble("advertise started mode=${settingsInEffect.mode} tx=${settingsInEffect.txPowerLevel}")
            GearanBleHub.event("adv: started")
            _advertising.value = true
            GearanBleHub.setAdvertising(true)
        }
        override fun onStartFailure(errorCode: Int) {
            GearanLog.bleWarn("advertise failed code=$errorCode (${advErrorName(errorCode)})")
            GearanBleHub.event("adv: FAILED code=$errorCode")
            _advertising.value = false
            GearanBleHub.setAdvertising(false)
        }
    }

    private fun resolveAdvertiser(): BluetoothLeAdvertiser? {
        val bt = context.getSystemService(BluetoothManager::class.java)
        val adapter = bt?.adapter
        if (adapter == null || !adapter.isEnabled) {
            GearanLog.bleWarn("advertise: adapter unavailable/disabled")
            GearanBleHub.event("adv: adapter off")
            return null
        }
        if (!adapter.isMultipleAdvertisementSupported) {
            GearanLog.bleWarn("advertise: multiple advertisement NOT supported on this build")
            GearanBleHub.event("adv: multi-adv unsupported")
        }
        advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            GearanLog.bleWarn("advertise: bluetoothLeAdvertiser null")
            GearanBleHub.event("adv: advertiser null")
        }
        return advertiser
    }

    @SuppressLint("MissingPermission")
    fun startPairingMode(deviceShortId: ByteArray, capsMask: Int) {
        if (_advertising.value) {
            GearanLog.ble("pairing advertise already running, ignoring duplicate start")
            GearanBleHub.event("adv: already running")
            return
        }
        val adv = resolveAdvertiser() ?: return
        try {
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(true)
                .setTimeout(120_000) // ms; matches 120 s pairing window (max 180000 ms)
                .build()
            adv.startAdvertising(settings, buildData(deviceShortId, capsMask), callback)
            GearanLog.ble("pairing advertise requested uuid=${GearanUuids.SERVICE}")
        } catch (e: SecurityException) {
            GearanLog.bleWarn("pairing advertise denied (permissions?)", e)
            GearanBleHub.event("adv: denied (permissions)")
        }
    }

    @SuppressLint("MissingPermission")
    fun startReconnectMode(deviceShortId: ByteArray, capsMask: Int) {
        if (_advertising.value) {
            GearanLog.ble("reconnect advertise already running, ignoring duplicate start")
            return
        }
        val adv = resolveAdvertiser() ?: return
        try {
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
                .setConnectable(true)
                .setTimeout(0)
                .build()
            adv.startAdvertising(settings, buildData(deviceShortId, capsMask), callback)
            GearanLog.ble("reconnect advertise requested (low power)")
        } catch (e: SecurityException) {
            GearanLog.bleWarn("reconnect advertise denied (permissions?)", e)
            GearanBleHub.event("adv: denied (permissions)")
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        try { advertiser?.stopAdvertising(callback) } catch (e: Exception) {
            GearanLog.bleWarn("advertise stop failed", e)
        }
        _advertising.value = false
        GearanBleHub.setAdvertising(false)
        GearanBleHub.event("adv: stopped")
    }

    private fun buildData(shortId: ByteArray, capsMask: Int): AdvertiseData {
        val mfg = ByteArray(2 + 1 + 8 + 2)
        mfg[0] = 0x47; mfg[1] = 0x52 // "GR"
        mfg[2] = 0x01
        shortId.copyInto(mfg, 3, 0, minOf(8, shortId.size))
        mfg[11] = ((capsMask shr 8) and 0xFF).toByte()
        mfg[12] = (capsMask and 0xFF).toByte()
        return AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(GearanUuids.SERVICE))
            .addManufacturerData(0xFFFF, mfg)
            .build()
    }

    companion object {
        fun advErrorName(code: Int): String = when (code) {
            AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
            AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "DATA_TOO_LARGE"
            AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
            AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
            AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "TOO_MANY_ADVERTISERS"
            else -> "UNKNOWN($code)"
        }
    }
}
