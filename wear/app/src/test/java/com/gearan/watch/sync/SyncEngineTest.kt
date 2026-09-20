package com.gearan.watch.sync

import org.junit.Assert.*
import org.junit.Test

class SyncEngineTest {
    @Test
    fun onlyDirtyTravels() {
        val e = GearanSyncEngine()
        assertNull(e.collect("id"))
        assertTrue(e.markDirty(SyncCategories.BATTERY, "2026-09-20T00:00:00Z"))
        assertFalse(e.markDirty("NOPE", "2026-09-20T00:00:00Z"))
        val p = e.collect("id") { cat -> if (cat == SyncCategories.BATTERY) "{\"percent\":84}" else "{}" }
        assertNotNull(p)
        assertEquals(1, p!!.changes.size)
        assertEquals(SyncCategories.BATTERY, p.changes[0].category)
        assertEquals(1L, p.changes[0].revision)
        assertNull(e.collect("id")) // coalesced: nothing left
    }

    @Test
    fun staleRevisionsRejected() {
        val a = GearanSyncEngine()
        val b = GearanSyncEngine()
        b.markDirty(SyncCategories.BATTERY, "t")
        val p = b.collect("watch")!!
        assertEquals(listOf(SyncCategories.BATTERY), a.apply(p))
        assertTrue(a.apply(p).isEmpty()) // replay of same revision ignored
    }
}
