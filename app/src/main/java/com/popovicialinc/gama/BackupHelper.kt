package com.popovicialinc.gama

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * BackupHelper
 *
 * Serialises every GAMA preference into a JSON string (for export) and
 * deserialises a JSON string back into SharedPreferences (for import).
 *
 * File naming convention: GAMA_backup_YYYY-MM-DD.json
 * so users can keep multiple dated backups and instantly recognise them.
 *
 * KEY REGISTRY — keep in sync with the writers in GamaUI.savePreferences(),
 * GamaHaptics.PREF_*, RendererState, and GamaLocalization.PREF_KEY:
 *  - Every user-facing setting is included.
 *  - Device/session-specific state is deliberately EXCLUDED because restoring it
 *    on another device would be wrong or harmful:
 *      prefs_version            (migration bookkeeping)
 *      last_switch_uptime       (elapsedRealtime — meaningless on another device)
 *      last_switch_time         ("X ago" label would lie after a restore)
 *      notif_perm_requested     (would suppress the permission prompt on the new device)
 *      button_labels_shown / title_shimmer_played (first-launch cosmetics)
 */
object BackupHelper {

    // ── All keys that are backed up ───────────────────────────────────────────
    private val INT_KEYS = listOf(
        "animation_level", "animation_speed", "particle_speed", "particle_parallax_sensitivity",
        "particle_count", "particle_count_custom", "theme_preference",
        "custom_accent", "custom_gradient_start", "custom_gradient_end",
        "ui_scale", "notif_interval_idx",
        // Haptics strengths
        GamaHaptics.PREF_REGULAR_STRENGTH,
        GamaHaptics.PREF_HOLD_STRENGTH,
        GamaHaptics.PREF_RENDERER_STRENGTH,
        GamaHaptics.PREF_LANGUAGE_STRENGTH,
        GamaHaptics.PREF_BOUNCE_STRENGTH,
        GamaHaptics.PREF_BOUNCE_RETURN_STRENGTH,
        // Matrix tuning
        "matrix_speed", "matrix_density", "matrix_font_size", "matrix_fade_length"
    )
    private val BOOLEAN_KEYS = listOf(
        "gradient_enabled", "particles_enabled", "particle_parallax_enabled",
        "particle_star_mode", "particle_time_mode", "blur_enabled",
        "use_dynamic_color", "verbose_mode", "aggressive_mode",
        "kill_launcher", "kill_keyboard", "show_gpuwatch_button",
        "stagger_enabled", "back_button_avoidance_enabled", "back_button_inversed",
        "shadows_enabled", "advanced_color_picker", "dismiss_on_click_outside",
        "notif_enabled",
        // Particle refresh-rate switches
        "particle_native_refresh_rate", "particle_quarter_refresh_rate",
        // Matrix toggles (matrix_mode is a Boolean pref despite its name — see GamaUI)
        "matrix_mode", "matrix_native_refresh_rate", "matrix_quarter_refresh_rate",
        // Haptics category switches
        GamaHaptics.PREF_ENABLED,
        GamaHaptics.PREF_REGULAR_ENABLED,
        GamaHaptics.PREF_HOLD_ENABLED,
        GamaHaptics.PREF_RENDERER_ENABLED,
        GamaHaptics.PREF_LANGUAGE_ENABLED,
        GamaHaptics.PREF_BOUNCE_ENABLED,
        GamaHaptics.PREF_BOUNCE_RETURN_ENABLED
    )
    private val FLOAT_KEYS = listOf(
        "time_offset_hours", "matrix_bg_alpha",
        "floating_left_x", "floating_left_y", "floating_right_x", "floating_right_y",
        "landscape_floating_left_x", "landscape_floating_left_y",
        "landscape_floating_right_x", "landscape_floating_right_y",
        "settings_button_x", "settings_button_y",
        "landscape_settings_button_x", "landscape_settings_button_y"
    )
    // Runtime notification bookkeeping must not travel between devices.
    private val LONG_KEYS = emptyList<String>()
    private val STRING_KEYS = listOf(
        "user_name", RendererState.PREF_LAST_RENDERER,
        RendererState.PREF_DESIRED_RENDERER, "selected_language"
    )
    private val STRING_SET_KEYS = listOf("excluded_apps")

    // Read-only registry access for the unit-test suite (same module) so the
    // round-trip test can cover every registered key with correct typing.
    internal fun registeredKeysForTesting(): Set<String> =
        (INT_KEYS + BOOLEAN_KEYS + FLOAT_KEYS + LONG_KEYS + STRING_KEYS + STRING_SET_KEYS).toSet()
    internal fun isRegisteredStringSetKey(key: String) = key in STRING_SET_KEYS
    internal fun isRegisteredStringKey(key: String) = key in STRING_KEYS
    internal fun isRegisteredFloatKey(key: String) = key in FLOAT_KEYS
    internal fun isRegisteredLongKey(key: String) = key in LONG_KEYS
    internal fun isRegisteredBooleanKey(key: String) = key in BOOLEAN_KEYS

    /** Generate a timestamped filename like "GAMA_backup_2025-06-14.json" */
    fun buildFileName(): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return "GAMA_backup_$date.json"
    }

    /**
     * Export — read every key from [prefs] and return a pretty-printed JSON string.
     * Runs on [Dispatchers.IO] so SharedPreferences reads never block the main thread.
     * @throws Exception if serialisation fails.
     */
    suspend fun export(prefs: SharedPreferences): String = withContext(Dispatchers.IO) {
        val root = JSONObject()

        // Version stamp — lets future GAMA versions handle schema migrations
        root.put("gama_backup_version", CURRENT_VERSION)
        root.put("exported_at", System.currentTimeMillis())

        INT_KEYS.forEach { key -> if (prefs.contains(key)) root.put(key, prefs.getInt(key, 0)) }
        BOOLEAN_KEYS.forEach { key -> if (prefs.contains(key)) root.put(key, prefs.getBoolean(key, false)) }
        FLOAT_KEYS.forEach { key -> if (prefs.contains(key)) root.put(key, prefs.getFloat(key, 0f).toDouble()) }
        LONG_KEYS.forEach { key -> if (prefs.contains(key)) root.put(key, prefs.getLong(key, 0L)) }
        STRING_KEYS.forEach { key -> if (prefs.contains(key)) root.put(key, prefs.getString(key, "") ?: "") }

        STRING_SET_KEYS.forEach { key ->
            if (!prefs.contains(key)) return@forEach
            val set = prefs.getStringSet(key, emptySet()) ?: emptySet()
            val arr = JSONArray()
            set.forEach { arr.put(it) }
            root.put(key, arr)
        }

        root.toString(2) // pretty-print with 2-space indent
    }

    /**
     * Import — parse [json] and write every recognised key back into [prefs].
     *
     * Accepts older supported backups from v1. Backups from a newer schema are
     * rejected rather than silently partially restored. Unknown fields in a
     * supported version are ignored; one invalid value never aborts the rest.
     *
     * Runs on [Dispatchers.IO] so JSON parsing and SharedPreferences writes
     * never block the main thread.
     * @return a human-readable summary like "Restored 24 settings."
     * @throws Exception if the JSON is malformed or has wrong backup version.
     */
    suspend fun import(prefs: SharedPreferences, json: String): String = withContext(Dispatchers.IO) {
        val root = JSONObject(json)

        // Reject completely foreign files
        val version = root.optInt("gama_backup_version", 0)
        if (!root.has("gama_backup_version") || version < 1) {
            throw IllegalArgumentException("This doesn't look like a GAMA backup file.")
        }
        if (version > CURRENT_VERSION) {
            throw IllegalArgumentException("This backup was created by a newer version of GAMA.")
        }

        var count = 0
        val editor = prefs.edit()
        var skipped = 0

        INT_KEYS.forEach { key ->
            if (root.has(key)) {
                val value = root.optInt(key, Int.MIN_VALUE)
                if (value != Int.MIN_VALUE && isValidInt(key, value)) { editor.putInt(key, value); count++ } else skipped++
            }
        }
        BOOLEAN_KEYS.forEach { key ->
            if (root.has(key)) {
                val raw = root.opt(key)
                // Accept real booleans or exact "true"/"false" strings only.
                // org.json's optBoolean silently maps anything else (e.g. 1,
                // "yes") to false — writing that would corrupt the setting
                // while counting it as restored, so skip instead.
                val coercible = raw is Boolean ||
                    (raw is String && (raw.equals("true", true) || raw.equals("false", true)))
                if (coercible) {
                    editor.putBoolean(key, root.optBoolean(key, false)); count++
                } else skipped++
            }
        }
        FLOAT_KEYS.forEach { key ->
            if (root.has(key)) {
                val value = root.optDouble(key, Double.NaN)
                if (value.isFinite() && isValidFloat(key, value.toFloat())) { editor.putFloat(key, value.toFloat()); count++ } else skipped++
            }
        }
        LONG_KEYS.forEach { key ->
            if (root.has(key)) {
                val value = root.optLong(key, Long.MIN_VALUE)
                if (value != Long.MIN_VALUE) { editor.putLong(key, value); count++ } else skipped++
            }
        }
        STRING_KEYS.forEach { key ->
            if (root.has(key)) {
                val value = root.optString(key, "\u0000")
                if (value != "\u0000" && isValidString(key, value)) { editor.putString(key, value); count++ } else skipped++
            }
        }
        STRING_SET_KEYS.forEach { key ->
            if (root.has(key)) {
                val arrOpt = root.optJSONArray(key)
                if (arrOpt != null) {
                    val set = mutableSetOf<String>()
                    var valid = arrOpt.length() <= MAX_STRING_SET_ITEMS
                    for (i in 0 until arrOpt.length()) {
                        val item = arrOpt.optString(i, null as String?) ?: continue
                        if (item.length > MAX_STRING_SET_ITEM_LENGTH) valid = false else set.add(item)
                    }
                    if (valid) {
                        editor.putStringSet(key, set)
                        count++
                    } else {
                        skipped++
                    }
                } else skipped++
            }
        }

        editor.apply()
        if (skipped > 0) "Restored $count settings ($skipped invalid entries skipped)."
        else "Restored $count settings successfully."
    }

    private const val CURRENT_VERSION = 2
    private const val MAX_STRING_SET_ITEMS = 512
    private const val MAX_STRING_SET_ITEM_LENGTH = 256

    private fun isValidInt(key: String, value: Int): Boolean = when (key) {
        "theme_preference", "ui_scale", "animation_level" -> value in 0..2
        "particle_count" -> value in 0..2_000
        "particle_count_custom" -> value in 1..10_000
        "matrix_speed", "matrix_density", "matrix_font_size", "matrix_fade_length" -> value in 0..1_000
        "notif_interval_idx" -> value in 0..10
        else -> true
    }

    private fun isValidFloat(key: String, value: Float): Boolean = when (key) {
        "time_offset_hours" -> value in -24f..24f
        "matrix_bg_alpha" -> value in 0f..1f
        else -> value in -100_000f..100_000f
    }

    private fun isValidString(key: String, value: String): Boolean = when (key) {
        RendererState.PREF_LAST_RENDERER, RendererState.PREF_DESIRED_RENDERER ->
            value == RendererState.RENDERER_OPENGL || value == RendererState.RENDERER_VULKAN
        "selected_language" -> value.matches(Regex("[a-z]{2,3}(_[A-Z]{2})?"))
        else -> value.length <= 4_096
    }
}
