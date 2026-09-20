package com.gearan.watch.ble

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Runtime Bluetooth permissions for the Watch4 Classic (API 30) and newer.
 *
 * - API <= 30 (Watch4 Classic, Wear OS 3): BLUETOOTH/BLUETOOTH_ADMIN are install-time;
 *   BLE scanning additionally needs ACCESS_FINE_LOCATION at runtime. This is an
 *   Android-11 platform requirement, not Gearan choice — hence location is asked
 *   ONLY on API <= 30 and explained in the PermissionGate screen.
 * - API >= 31: BLUETOOTH_SCAN (neverForLocation: filtered scan, no location
 *   derivation) + BLUETOOTH_ADVERTISE + BLUETOOTH_CONNECT.
 */
object BlePermissions {
    fun required(): Array<String> = if (Build.VERSION.SDK_INT >= 31) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun missing(context: Context): List<String> =
        required().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

    fun granted(context: Context): Boolean = missing(context).isEmpty()

    /** Human-readable rationale shown in the PermissionGate screen. */
    fun rationale(): String = if (Build.VERSION.SDK_INT >= 31) {
        "Gearan needs Bluetooth scan, advertise and connect permissions " +
            "to find your iPhone and sync. Scanning is filtered to Gearan only."
    } else {
        "On Wear OS 3 (Android 11) Bluetooth scanning requires Location " +
            "permission by the OS. Gearan never uses your location: scanning is " +
            "filtered to the Gearan service UUID only."
    }
}
