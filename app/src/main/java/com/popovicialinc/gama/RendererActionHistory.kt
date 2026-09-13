package com.popovicialinc.gama

import android.content.Context
import android.content.SharedPreferences

data class RendererActionRecord(
    val source: String,
    val renderer: String,
    val success: Boolean,
    val detail: String,
    val timestamp: Long
)

/** Small, persistent audit trail for renderer commands issued outside the UI. */
object RendererActionHistory {
    private const val SOURCE = "renderer_action_source"
    private const val RENDERER = "renderer_action_renderer"
    private const val SUCCESS = "renderer_action_success"
    private const val DETAIL = "renderer_action_detail"
    private const val TIME = "renderer_action_time"

    fun record(prefs: SharedPreferences, source: String, renderer: String, success: Boolean, detail: String) {
        prefs.edit()
            .putString(SOURCE, source)
            .putString(RENDERER, renderer)
            .putBoolean(SUCCESS, success)
            .putString(DETAIL, detail.take(500))
            .putLong(TIME, System.currentTimeMillis())
            .apply()
    }

    fun read(prefs: SharedPreferences): RendererActionRecord? {
        val timestamp = prefs.getLong(TIME, 0L)
        if (timestamp == 0L) return null
        return RendererActionRecord(
            prefs.getString(SOURCE, "GAMA") ?: "GAMA",
            prefs.getString(RENDERER, "") ?: "",
            prefs.getBoolean(SUCCESS, false),
            prefs.getString(DETAIL, "") ?: "",
            timestamp
        )
    }

    fun read(context: Context): RendererActionRecord? =
        read(context.getSharedPreferences("gama_prefs", Context.MODE_PRIVATE))
}
