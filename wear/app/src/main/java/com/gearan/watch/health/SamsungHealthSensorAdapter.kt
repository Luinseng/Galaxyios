package com.gearan.watch.health

import com.gearan.watch.sync.CapabilityReport
import com.gearan.watch.sync.CapabilityStatus

/**
 * Samsung Health Sensor SDK adapter (capability-gated).
 *
 * The Watch4 Classic exposes sensors through the Samsung Health Sensor SDK
 * (privileged) and standard Android SensorManager. This adapter probes at
 * runtime and NEVER bypasses regional/Samsung Health Monitor/medical
 * restrictions: restricted features report SDK_RESTRICTION.
 *
 * Status per feature: AVAILABLE | UNAVAILABLE | PERMISSION_REQUIRED |
 * SDK_RESTRICTION | EXPERIMENTAL.
 */
class SamsungHealthSensorAdapter {

    companion object {
        /**
         * Feature flag: Samsung Health Sensor SDK wiring lives behind this flag
         * with full dependency isolation. The BLE app compiles and runs with
         * `false` (probe reports SDK_RESTRICTION). See docs/HEALTH_SDK.md to
         * enable with `samsung-health-sensor-api.aar`.
         */
        const val HEALTH_SDK_ENABLED = false
    }

    data class SensorState(
        val heartRate: CapabilityReport,
        val accelerometer: CapabilityReport,
        val ppg: CapabilityReport,
        val spo2: CapabilityReport,
        val bia: CapabilityReport,
        val ecg: CapabilityReport,
    )

    fun probe(hasBodySensorsPermission: Boolean, hasActivityPermission: Boolean): SensorState {
        // Standard sensors via SensorManager are generally available on Watch4.
        // Samsung-privileged streams (continuous PPG/SpO2/BIA/ECG) require the
        // Samsung Health Sensor SDK + data-policy consent; without it we must
        // report SDK_RESTRICTION, never fake data.
        val hr = if (!hasBodySensorsPermission) {
            CapabilityReport("HEART_RATE", CapabilityStatus.PERMISSION_REQUIRED, "BODY_SENSORS not granted")
        } else {
            CapabilityReport("HEART_RATE", CapabilityStatus.AVAILABLE, "SensorManager + Health Services")
        }
        val acc = if (!hasActivityPermission) {
            CapabilityReport("ACCELEROMETER", CapabilityStatus.PERMISSION_REQUIRED, "ACTIVITY_RECOGNITION not granted")
        } else {
            CapabilityReport("ACCELEROMETER", CapabilityStatus.AVAILABLE, "SensorManager")
        }
        return SensorState(
            heartRate = hr,
            accelerometer = acc,
            ppg = CapabilityReport("PPG", CapabilityStatus.SDK_RESTRICTION, "Requires Samsung Health Sensor SDK consent"),
            spo2 = CapabilityReport("SPO2", CapabilityStatus.SDK_RESTRICTION, "Requires Samsung Health Sensor SDK consent"),
            bia = CapabilityReport("BIA", CapabilityStatus.SDK_RESTRICTION, "Requires Samsung Health Sensor SDK + Health Monitor region"),
            ecg = CapabilityReport("ECG", CapabilityStatus.SDK_RESTRICTION, "Medical feature: region + certification gated"),
        )
    }

    fun toCapabilityIds(state: SensorState): List<String> {
        val out = mutableListOf("HEALTH_SENSORS", "DEVICE_INFO", "BATTERY", "DIAGNOSTICS")
        if (state.heartRate.status == CapabilityStatus.AVAILABLE) out.add("HEART_RATE")
        if (state.spo2.status == CapabilityStatus.AVAILABLE) out.add("SPO2")
        if (state.bia.status == CapabilityStatus.AVAILABLE) out.add("BIA")
        out.add("NETWORK_RELAY")
        out.add("GEARAN_NOTIFICATIONS")
        return out
    }
}
