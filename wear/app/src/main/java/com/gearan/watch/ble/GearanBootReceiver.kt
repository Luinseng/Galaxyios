package com.gearan.watch.ble

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.gearan.watch.storage.TrustedDeviceStore
import com.gearan.watch.util.GearanLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Watch reboot recovery (BOOT_COMPLETED).
 *
 * - Does NOT start any visible activity.
 * - Reads the trusted store: only if a trusted iPhone exists does it restore
 *   the foreground GATT service (BLE becomes available → iPhone reconnects).
 * - If never paired, does nothing (no radio, no battery cost).
 * - Trusted state is only read here, never written/corrupted.
 */
class GearanBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return
        GearanLog.ble("boot completed, restoring Gearan state")
        GearanBleHub.event("boot: restoring")
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val store = TrustedDeviceStore(context.applicationContext)
                val trusted = store.loadTrustedPhone()
                if (trusted == null) {
                    GearanLog.ble("boot: never paired, staying idle")
                    GearanBleHub.event("boot: idle (never paired)")
                } else {
                    GearanLog.ble("boot: trusted ${trusted.model} found, restarting GATT service")
                    GearanBleHub.event("boot: restarting service")
                    val svc = Intent(context.applicationContext, GearanPeripheralService::class.java)
                    if (Build.VERSION.SDK_INT >= 26) {
                        context.applicationContext.startForegroundService(svc)
                    } else {
                        context.applicationContext.startService(svc)
                    }
                }
            } catch (e: Exception) {
                GearanLog.bleWarn("boot restore failed", e)
                GearanBleHub.event("boot: restore FAILED")
            } finally {
                pending.finish()
            }
        }
    }
}
