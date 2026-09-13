package com.popovicialinc.gama

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

// Data model
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Lightweight wrapper around a parsed translation JSON.
 * All lookups fall back to the English string when a key is missing,
 * so partially-translated community files never crash the app.
 */
class GamaStrings(private val json: JSONObject, private val fallback: JSONObject?) {

    /** Look up a nested key like "settings.title". Returns "" on miss. */
    fun get(section: String, key: String): String {
        return json.optJSONObject(section)?.optString(key)?.takeIf { it.isNotEmpty() }
            ?: fallback?.optJSONObject(section)?.optString(key)
            ?: ""
    }

    /** Convenience operators — `strings["settings.title"]` */
    operator fun get(dotKey: String): String {
        val parts = dotKey.split(".", limit = 2)
        return if (parts.size == 2) get(parts[0], parts[1]) else ""
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CompositionLocal
// ─────────────────────────────────────────────────────────────────────────────

/** Provides [GamaStrings] to the entire composition tree. */
val LocalStrings = compositionLocalOf<GamaStrings> {
    error("LocalStrings not provided — wrap your root composable with GamaLocalizationProvider")
}

// ─────────────────────────────────────────────────────────────────────────────
// Language metadata  (parsed from meta block inside each JSON)
// ─────────────────────────────────────────────────────────────────────────────

data class LanguageEntry(
    val code: String,       // e.g. "en"
    val name: String,       // e.g. "English"
    val nativeName: String, // e.g. "English" / "Română"
    val flag: String,       // emoji flag
    val fileName: String    // asset file name, e.g. "en.json"
)

// ─────────────────────────────────────────────────────────────────────────────
// LocalizationManager  (singleton, loaded once per process)
// ─────────────────────────────────────────────────────────────────────────────

object LocalizationManager {

    private const val PREF_KEY = "selected_language"
    private const val DEFAULT_CODE = "en"
    private const val TRANSLATIONS_DIR = "translations"
    private const val TAG = "GamaLocalization"

    // Assets are immutable for the lifetime of an installed APK, so cache the
    // metadata as well as parsed strings. This avoids reparsing every language
    // file whenever a non-English language is selected.
    private var cachedEnglish: JSONObject? = null
    private var cachedStrings: GamaStrings? = null
    private var cachedCode: String? = null
    private var cachedLanguages: List<LanguageEntry>? = null
    private val cacheLock = Any()

    /** Scan assets/translations/ and return every valid LanguageEntry. */
    suspend fun loadAvailableLanguages(context: Context): List<LanguageEntry> =
        withContext(Dispatchers.IO) {
            cachedLanguages?.let { return@withContext it }
            val assets = context.assets
            val files = try {
                assets.list(TRANSLATIONS_DIR) ?: emptyArray()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Could not list $TRANSLATIONS_DIR: ${e.message}")
                emptyArray()
            }

            android.util.Log.d(TAG, "Found ${files.size} file(s) in assets/$TRANSLATIONS_DIR: ${files.toList()}")

            val entries = mutableListOf<LanguageEntry>()
            for (file in files.sorted()) {
                if (!file.endsWith(".json")) continue
                try {
                    val raw = assets.open("$TRANSLATIONS_DIR/$file").bufferedReader().readText()
                    val obj = JSONObject(raw)
                    val meta = obj.optJSONObject("meta")
                    if (meta == null) {
                        android.util.Log.w(TAG, "$file has no 'meta' block — skipping")
                        continue
                    }
                    val entry = LanguageEntry(
                        code       = meta.optString("code", file.removeSuffix(".json")),
                        name       = meta.optString("name", file),
                        nativeName = meta.optString("nativeName", file),
                        flag       = meta.optString("flag", "🌐"),
                        fileName   = file
                    )
                    android.util.Log.d(TAG, "Loaded language: ${entry.code} / ${entry.nativeName}")
                    entries.add(entry)
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to parse $file: ${e.message}")
                }
            }

            // English always first, rest alphabetical by native name
            entries.sortedWith(compareBy { if (it.code == DEFAULT_CODE) "" else it.nativeName })
                .also { cachedLanguages = it }
        }

    /** Load and return [GamaStrings] for the given language [code]. */
    suspend fun loadStrings(context: Context, code: String): GamaStrings =
        withContext(Dispatchers.IO) {
            // Return cache hit if the code matches what's already loaded
            if (code == cachedCode && cachedStrings != null) {
                return@withContext cachedStrings!!
            }

            // Always load English as fallback (cached after first load)
            val englishJson = cachedEnglish ?: run {
                val raw = try {
                    context.assets.open("$TRANSLATIONS_DIR/$DEFAULT_CODE.json")
                        .bufferedReader().readText()
                } catch (_: Exception) { "{}" }
                JSONObject(raw).also { cachedEnglish = it }
            }

            val targetJson: JSONObject = if (code == DEFAULT_CODE) {
                englishJson
            } else {
                val languages = loadAvailableLanguages(context)
                val entry = languages.firstOrNull { it.code == code }
                if (entry != null) {
                    try {
                        val raw = context.assets.open("$TRANSLATIONS_DIR/${entry.fileName}")
                            .bufferedReader().readText()
                        JSONObject(raw)
                    } catch (_: Exception) { englishJson }
                } else {
                    englishJson
                }
            }

            val strings = GamaStrings(
                json     = targetJson,
                fallback = if (code == DEFAULT_CODE) null else englishJson
            )
            cachedStrings = strings
            cachedCode = code
            strings
        }

    /**
     * Synchronous lookup for framework callbacks that cannot be suspend
     * functions (notifications and QS tile updates). It shares the same parsed
     * cache and only performs asset I/O on a cache miss, avoiding repeated JSON
     * parsing across non-Compose surfaces.
     */
    fun getStringBlocking(
        context: Context,
        code: String,
        section: String,
        key: String,
        fallback: String
    ): String = synchronized(cacheLock) {
        val strings = if (code == cachedCode && cachedStrings != null) {
            cachedStrings!!
        } else {
            val english = cachedEnglish ?: readJson(context, DEFAULT_CODE).also { cachedEnglish = it }
            val target = if (code == DEFAULT_CODE) english else readJson(context, code)
            GamaStrings(target, if (code == DEFAULT_CODE) null else english).also {
                cachedStrings = it
                cachedCode = code
            }
        }
        strings.get(section, key).ifEmpty { fallback }
    }

    private fun readJson(context: Context, code: String): JSONObject {
        return try {
            context.assets.open("$TRANSLATIONS_DIR/$code.json").bufferedReader().use { JSONObject(it.readText()) }
        } catch (_: Exception) {
            JSONObject()
        }
    }

    fun getSavedCode(prefs: SharedPreferences): String =
        prefs.getString(PREF_KEY, DEFAULT_CODE) ?: DEFAULT_CODE

    fun saveCode(prefs: SharedPreferences, code: String) =
        prefs.edit().putString(PREF_KEY, code).apply()

    /** Force-evict string caches so the next loadStrings call re-reads from disk. */
    fun invalidateCache() {
        synchronized(cacheLock) {
            cachedStrings = null
            cachedCode = null
            cachedLanguages = null
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared language state — written by LanguagePanel, read by the provider
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Holds the currently selected language code as observable state.
 * Provided via [LocalLanguageCode] so LanguagePanel can update it directly
 * and the provider reacts immediately — no restart required.
 */
val LocalLanguageCode = compositionLocalOf { mutableStateOf("en") }

// ─────────────────────────────────────────────────────────────────────────────
// Provider composable — wrap GamaUI with this in MainActivity
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Synchronous lookup for non-Compose surfaces (toasts, notifications, tiles,
 * workers) that need the user's selected language without a composition.
 */
fun localizedString(context: Context, section: String, key: String, fallback: String): String {
    val code = context.getSharedPreferences("gama_prefs", Context.MODE_PRIVATE)
        .getString("selected_language", "en") ?: "en"
    return LocalizationManager.getStringBlocking(context, code, section, key, fallback)
}

@Composable
fun GamaLocalizationProvider(
    prefs: SharedPreferences,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    // Single source of truth for the selected code — shared with LanguagePanel
    val languageCodeState = remember {
        mutableStateOf(LocalizationManager.getSavedCode(prefs))
    }
    val selectedCode by languageCodeState

    var strings by remember { mutableStateOf<GamaStrings?>(null) }

    // Reload strings whenever the code changes — runs on IO, updates state on main
    LaunchedEffect(selectedCode) {
        strings = LocalizationManager.loadStrings(context, selectedCode)
    }

    // Do not compose the app with an empty dictionary: every call site would
    // briefly render hard-coded English before the selected language arrived.
    // Keep the activity on its themed background until the first load completes.
    val resolved = strings ?: return@GamaLocalizationProvider

    CompositionLocalProvider(
        LocalStrings      provides resolved,
        LocalLanguageCode provides languageCodeState
    ) {
        content()
    }
}
