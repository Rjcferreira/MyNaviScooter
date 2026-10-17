package com.mynavisccooter.capture

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun snapshot(serial: String, hex: String = "0F14") = InitialBaseline(serial,
        listOf(RegisterCapture(0x16, 0x47, "max_speed_eco_drive", 2, hex)))

    @Test fun separateScootersKeepTheirFirstValuesAfterReload() {
        val name = "baseline_multi_test"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val store = BaselineStore(context, name)
            val first = snapshot("ZT3TEST", "0F14")
            val second = snapshot("F3TEST", "0F19")
            assertTrue(store.saveFirst(first))
            assertTrue(store.saveFirst(second))
            assertTrue(store.saveFirst(snapshot("ZT3TEST", "0F1E")))
            val reopened = BaselineStore(context, name)
            assertEquals(first, reopened.load(first.serial))
            assertEquals(second, reopened.load(second.serial))
            assertNull(reopened.load("OTHER"))
            assertFalse(prefs.all.values.joinToString().contains("ZT3TEST"))
        } finally { prefs.edit().clear().commit() }
    }

    @Test fun legacySnapshotMigratesWithoutOverwritingItsOriginalValues() {
        val name = "baseline_legacy_test"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val store = BaselineStore(context, name)
            val original = snapshot("LEGACY")
            assertTrue(store.saveFirst(original))
            val entry = prefs.all.entries.single()
            prefs.edit().remove(entry.key).putString("payload", entry.value as String).commit()
            assertEquals(original, store.load("LEGACY"))
            assertNull(store.load("OTHER"))
            assertTrue(store.saveFirst(snapshot("SECOND")))
            assertTrue(store.saveFirst(snapshot("LEGACY", "0F1E")))
            assertEquals(original, store.load("LEGACY"))
            assertNotNull(store.load("SECOND"))
            assertTrue(prefs.contains("payload"))
        } finally { prefs.edit().clear().commit() }
    }

    @Test fun invalidOrCorruptDataCannotReplaceAnExistingSnapshot() {
        val name = "baseline_corrupt_test"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val store = BaselineStore(context, name)
            assertFalse(store.saveFirst(snapshot("INVALID", "00")))
            assertFalse(store.saveFirst(snapshot("DUP").let { it.copy(registers = it.registers + it.registers) }))
            assertTrue(store.saveFirst(snapshot("GOOD")))
            val slot = prefs.all.keys.single()
            prefs.edit().putString(slot, "broken").commit()
            assertNull(store.load("GOOD"))
            assertFalse(store.saveFirst(snapshot("GOOD")))
            assertEquals("broken", prefs.getString(slot, null))
        } finally { prefs.edit().clear().commit() }
    }
}
