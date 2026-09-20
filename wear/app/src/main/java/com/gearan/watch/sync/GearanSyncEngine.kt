package com.gearan.watch.sync

import com.gearan.watch.util.GearanLog
import kotlinx.serialization.Serializable

/**
 * Incremental sync engine: after initial sync, only dirty categories travel.
 * Both sides track per-category revision + timestamp; the sender emits only
 * categories marked dirty since the last acknowledged revision.
 * Same category set and rules as ios/GearanSyncEngine (wire-compatible JSON).
 */
object SyncCategories {
    const val DEVICE_STATE = "DEVICE_STATE"
    const val BATTERY = "BATTERY"
    const val HEALTH = "HEALTH"
    const val NOTIFICATIONS = "NOTIFICATIONS"
    const val NETWORK = "NETWORK"
    const val SETTINGS = "SETTINGS"
    const val CAPABILITIES = "CAPABILITIES"

    val ALL = listOf(DEVICE_STATE, BATTERY, HEALTH, NOTIFICATIONS, NETWORK, SETTINGS, CAPABILITIES)
}

@Serializable
data class CategoryState(
    val category: String,
    val revision: Long = 0,
    val timestamp: String = "",
)

@Serializable
data class SyncPayload(
    val deviceId: String,
    val changes: List<CategoryChange>,
)

@Serializable
data class CategoryChange(
    val category: String,
    val revision: Long,
    val timestamp: String,
    /** Opaque per-category JSON (e.g. {"percent":84}). Never health/notification content in logs. */
    val dataJson: String = "{}",
)

class GearanSyncEngine {
    private val revisions = SyncCategories.ALL.associateWith { 0L }.toMutableMap()
    private val dirty = LinkedHashSet<String>()
    var lastSync: String? = null
        private set

    /** Mark a category changed; bumps nothing until collected (coalesces bursts). */
    fun markDirty(category: String, nowIso: String): Boolean {
        if (!revisions.containsKey(category)) return false
        dirty.add(category)
        lastPendingTs[category] = nowIso
        return true
    }

    private val lastPendingTs = mutableMapOf<String, String>()

    fun pendingCount(): Int = dirty.size

    /**
     * Collect dirty categories into one payload (each revision = prev + 1).
     * Returns null when nothing changed — no empty syncs on the radio.
     */
    fun collect(deviceId: String, dataFor: (String) -> String = { "{}" }): SyncPayload? {
        if (dirty.isEmpty()) return null
        val changes = dirty.sorted().map { cat ->
            val rev = (revisions[cat] ?: 0L) + 1
            revisions[cat] = rev
            val ts = lastPendingTs[cat] ?: ""
            CategoryChange(cat, rev, ts, dataFor(cat))
        }
        dirty.clear()
        lastPendingTs.clear()
        GearanLog.sync("incremental sync: ${changes.size} categories")
        return SyncPayload(deviceId, changes)
    }

    /** Apply a received payload; returns categories actually newer than local. */
    fun apply(payload: SyncPayload): List<String> {
        val applied = mutableListOf<String>()
        payload.changes.forEach { c ->
            val local = revisions[c.category]
            if (local != null && c.revision > local) {
                revisions[c.category] = c.revision
                applied.add(c.category)
            }
        }
        if (applied.isNotEmpty()) {
            lastSync = applied.joinToString()
            GearanLog.sync("applied ${applied.size} categories")
        }
        return applied
    }

    fun snapshot(): List<CategoryState> =
        revisions.map { (cat, rev) -> CategoryState(cat, rev) }
}
