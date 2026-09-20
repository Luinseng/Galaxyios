package com.gearan.watch.notifications

/**
 * Notification architecture.
 * Mode 1 (supported): sync of Gearan-owned notifications over GEARAN_NOTIFICATIONS.
 * Mode 2 (experimental): ANCS probe only — the Watch reports what it can see;
 * no iOS bypass is attempted. ANCS availability can only be confirmed on hardware.
 */
object GearanNotificationBridge {
    data class GearanNotification(val id: String, val title: String, val body: String, val ts: Long)

    fun sanitizeForDiagnostics(n: GearanNotification): String = "notif(id=${n.id},ts=${n.ts})"
}

object AncsCapabilityProbe {
    enum class Result { AVAILABLE, UNAVAILABLE, UNKNOWN }
    /** On-Watch placeholder: real ANCS reachability is decided by the iPhone
     *  central after service discovery on hardware. Default: UNAVAILABLE. */
    fun probe(): Result = Result.UNAVAILABLE
}
