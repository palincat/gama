package com.popovicialinc.gama

import android.content.Context
import android.os.Build
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Creates a privacy-conscious, shareable diagnostic report for support. */
object SupportBundle {
    fun build(context: Context): String {
        val prefs = context.getSharedPreferences("gama_prefs", Context.MODE_PRIVATE)
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) { "unknown" }
        val action = RendererActionHistory.read(prefs)
        val crashLog = try {
            File(context.filesDir, "crash_log.txt").takeIf { it.exists() }
                ?.readText()?.takeLast(12_000).orEmpty()
        } catch (_: Exception) { "" }
        return buildString {
            appendLine("GAMA support bundle")
            appendLine("Generated: ${DateFormat.getDateTimeInstance().format(Date())}")
            appendLine("Version: $version")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Renderer: ${RendererState.getRenderer(prefs)}; desired: ${RendererState.getDesiredRenderer(prefs)}")
            appendLine("Backend: root=${ShizukuHelper.isRootAvailable()}, binder=${ShizukuHelper.checkBinder()}, permission=${ShizukuHelper.checkPermission()}")
            if (action != null) {
                appendLine("Last action: ${action.source} → ${action.renderer}; success=${action.success}; ${action.detail}")
            }
            if (crashLog.isNotBlank()) {
                appendLine()
                appendLine("Recent GAMA crash log:")
                appendLine(crashLog)
            }
        }
    }

    fun fileName(): String = "GAMA_support_${java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())}.txt"
}
