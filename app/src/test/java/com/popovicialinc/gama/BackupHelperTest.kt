package com.popovicialinc.gama

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

/**
 * Minimal in-memory SharedPreferences/Editor implementation — just enough
 * surface for BackupHelper (getters, contains, edit with typed puts, sets,
 * clear/remove, commit/apply).
 */
class FakePrefs : SharedPreferences {
    val map = ConcurrentHashMap<String, Any>()

    override fun getAll(): Map<String, *> = map.toMap()
    override fun getString(key: String, defValue: String?): String? = map[key] as? String ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String> =
        (map[key] as? Set<String>)?.toMutableSet() ?: defValues ?: mutableSetOf()
    override fun getInt(key: String, defValue: Int): Int = map[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = map[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = map[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = map.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun edit(): SharedPreferences.Editor = FakeEditor(map)

    private class FakeEditor(private val map: ConcurrentHashMap<String, Any>) : SharedPreferences.Editor {
        private val puts = LinkedHashMap<String, Any>()
        private val removals = LinkedHashSet<String>()
        private var doClear = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) { if (value == null) removals.add(key) else puts[key] = value }
            return this
        }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            if (key != null) { if (values == null) removals.add(key) else puts[key] = HashSet<String>(values) }
            return this
        }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor { if (key != null) puts[key] = value; return this }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor { if (key != null) puts[key] = value; return this }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { if (key != null) puts[key] = value; return this }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { if (key != null) puts[key] = value; return this }
        override fun remove(key: String?): SharedPreferences.Editor { if (key != null) removals.add(key); return this }
        override fun clear(): SharedPreferences.Editor { doClear = true; return this }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (doClear) map.clear()
            removals.forEach { map.remove(it) }
            puts.forEach { (k, v) -> map[k] = v }
        }
    }
}

class BackupHelperTest {

    private fun fullyPopulatedPrefs(): FakePrefs = FakePrefs().apply {
        val e = edit()
        // INT keys (one representative per group + all haptics/matrix ints)
        e.putInt("animation_level", 1)
        e.putInt("notif_interval_idx", 3)
        e.putInt(GamaHaptics.PREF_BOUNCE_RETURN_STRENGTH, 4)
        e.putInt("matrix_fade_length", 2)
        // BOOLEAN keys
        e.putBoolean("particles_enabled", false)
        e.putBoolean("advanced_color_picker", true)
        e.putBoolean(GamaHaptics.PREF_ENABLED, true)
        e.putBoolean("matrix_mode", true) // Boolean despite the name
        e.putBoolean("particle_native_refresh_rate", true)
        // FLOAT / LONG / STRING / STRING_SET
        e.putFloat("time_offset_hours", 2.5f)
        e.putFloat("matrix_bg_alpha", 0.75f)
        e.putLong("notif_last_sent", 1_700_000_000_000L)
        e.putString("user_name", "Alin")
        e.putString(RendererState.PREF_LAST_RENDERER, RendererState.RENDERER_VULKAN)
        e.putString("selected_language", "ro")
        e.putStringSet("excluded_apps", setOf("com.a.b", "com.c.d"))
        e.commit()
    }

    @Test
    fun `export then import round-trips every registered key`() = kotlinx.coroutines.runBlocking {
        val source = FakePrefs()
        val e = source.edit()
        // Seed EVERY registered key with a valid, distinctive value. Iteration
        // order of registeredKeysForTesting() is deterministic (insertion-ordered
        // set), so the index-based expected values match across seed and assert.
        BackupHelper.registeredKeysForTesting().forEachIndexed { i, key ->
            when {
                BackupHelper.isRegisteredStringSetKey(key) ->
                    e.putStringSet(key, setOf("a_$i", "b_$i"))
                BackupHelper.isRegisteredStringKey(key) ->
                    e.putString(
                        key,
                        when (key) {
                            RendererState.PREF_LAST_RENDERER,
                            RendererState.PREF_DESIRED_RENDERER -> RendererState.RENDERER_VULKAN
                            "selected_language" -> "en"
                            else -> "value_$key"
                        }
                    )
                BackupHelper.isRegisteredFloatKey(key) ->
                    e.putFloat(key, 0.5f)
                BackupHelper.isRegisteredLongKey(key) ->
                    e.putLong(key, 1_700_000_000_000L + i)
                BackupHelper.isRegisteredBooleanKey(key) ->
                    e.putBoolean(key, i % 2 == 0)
                else ->
                    e.putInt(key, 1)
            }
        }
        e.commit()

        val json = BackupHelper.export(source)

        val target = FakePrefs()
        val summary = BackupHelper.import(target, json)

        BackupHelper.registeredKeysForTesting().forEachIndexed { i, key ->
            when {
                BackupHelper.isRegisteredStringSetKey(key) ->
                    assertEquals("set $key", setOf("a_$i", "b_$i"), target.getStringSet(key, null))
                BackupHelper.isRegisteredStringKey(key) ->
                    assertEquals(
                        "string $key",
                        when (key) {
                            RendererState.PREF_LAST_RENDERER,
                            RendererState.PREF_DESIRED_RENDERER -> RendererState.RENDERER_VULKAN
                            "selected_language" -> "en"
                            else -> "value_$key"
                        },
                        target.getString(key, null)
                    )
                BackupHelper.isRegisteredFloatKey(key) ->
                    assertEquals("float $key", 0.5f, target.getFloat(key, -1f), 0.0001f)
                BackupHelper.isRegisteredLongKey(key) ->
                    assertEquals("long $key", 1_700_000_000_000L + i, target.getLong(key, -1L))
                BackupHelper.isRegisteredBooleanKey(key) ->
                    assertEquals("bool $key", i % 2 == 0, target.getBoolean(key, !true))
                else ->
                    assertEquals("int $key", 1, target.getInt(key, -1))
            }
        }
        assertTrue(summary.startsWith("Restored"))
        assertFalse(summary.contains("skipped"))
    }

    @Test
    fun `import rejects foreign json`() = kotlinx.coroutines.runBlocking {
        val prefs = FakePrefs()
        try {
            BackupHelper.import(prefs, """{"some":"random file"}""")
            throw AssertionError("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `import accepts version-1 backups`() = kotlinx.coroutines.runBlocking {
        val v1Json = """{"gama_backup_version":1,"user_name":"Old"}"""
        val prefs = FakePrefs()
        val summary = BackupHelper.import(prefs, v1Json)
        assertEquals("Old", prefs.getString("user_name", ""))
        assertTrue(summary.contains("Restored"))
    }

    @Test
    fun `import rejects backups from a newer schema`() = kotlinx.coroutines.runBlocking {
        try {
            BackupHelper.import(FakePrefs(), """{"gama_backup_version":999}""")
            throw AssertionError("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("newer"))
        }
    }

    @Test
    fun `import skips invalid values but restores the rest`() = kotlinx.coroutines.runBlocking {
        val json = """
            {"gama_backup_version":2,
             "animation_level":"not-a-number",
             "ui_scale":2,
             "time_offset_hours":"NaN-ish",
             "user_name":"Ok"}
        """.trimIndent()
        val prefs = FakePrefs()
        val summary = BackupHelper.import(prefs, json)
        assertEquals(-1, prefs.getInt("animation_level", -1))   // skipped
        assertEquals(2, prefs.getInt("ui_scale", -1))           // restored
        assertEquals(0f, prefs.getFloat("time_offset_hours", 0f), 0f) // skipped
        assertEquals("Ok", prefs.getString("user_name", ""))    // restored
        assertTrue(summary.contains("skipped"))
    }

    @Test
    fun `export only includes keys present in prefs`() = kotlinx.coroutines.runBlocking {
        val prefs = FakePrefs()
        prefs.edit().putString("user_name", "Only").commit()
        val json = BackupHelper.export(prefs)
        assertTrue(json.contains("\"user_name\""))
        assertFalse(json.contains("particle_speed"))
        assertFalse(json.contains("excluded_apps"))
    }

    @Test
    fun `device-specific state is never exported`() = kotlinx.coroutines.runBlocking {
        val prefs = fullyPopulatedPrefs()
        prefs.edit()
            .putString("last_renderer", "Vulkan")
            .putLong("last_switch_uptime", 123456789L)
            .putLong("last_switch_time", 987654321L)
            .putBoolean("notif_perm_requested", true)
            .putBoolean("button_labels_shown", true)
            .commit()
        val json = BackupHelper.export(prefs)
        assertFalse(json.contains("last_switch_uptime"))
        assertFalse(json.contains("last_switch_time"))
        assertFalse(json.contains("notif_perm_requested"))
        assertFalse(json.contains("button_labels_shown"))
        assertFalse(json.contains("prefs_version"))
    }

    @Test
    fun `import skips oversized excluded app entries`() = kotlinx.coroutines.runBlocking {
        val huge = "x".repeat(257)
        val summary = BackupHelper.import(
            FakePrefs(),
            """{"gama_backup_version":2,"excluded_apps":["$huge"]}"""
        )
        assertTrue(summary.contains("invalid entries skipped"))
    }
}
