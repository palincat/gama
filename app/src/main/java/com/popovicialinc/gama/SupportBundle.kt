package com.popovicialinc.gama

import android.content.Context
import android.os.Build
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Replaces printf-style string placeholders from left to right. */
internal fun formatSupportText(template: String, vararg values: String): String =
    values.fold(template) { result, value -> result.replaceFirst("%s", value) }

/** Creates a privacy-conscious, shareable diagnostic report for support. */
object SupportBundle {
    fun build(context: Context): String {
        val prefs = context.getSharedPreferences("gama_prefs", Context.MODE_PRIVATE)
        val unknownVersion = localizedString(context, "support_bundle", "version_unknown", "unknown")
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: unknownVersion
        } catch (_: Exception) { unknownVersion }
        val action = RendererActionHistory.read(prefs)
        val crashLog = try {
            File(context.filesDir, "crash_log.txt").takeIf { it.exists() }
                ?.readText()?.takeLast(12_000).orEmpty()
        } catch (_: Exception) { "" }
        return buildString {
            appendLine(localizedString(context, "support_bundle", "title", "GAMA support bundle"))
            appendLine(
                formatSupportText(
                    localizedString(context, "support_bundle", "generated", "Generated: %s"),
                    DateFormat.getDateTimeInstance().format(Date())
                )
            )
            appendLine(
                formatSupportText(
                    localizedString(context, "support_bundle", "version", "Version: %s"),
                    version
                )
            )
            appendLine(
                formatSupportText(
                    localizedString(context, "support_bundle", "device", "Device: %s %s"),
                    Build.MANUFACTURER,
                    Build.MODEL
                )
            )
            appendLine(
                formatSupportText(
                    localizedString(context, "support_bundle", "android", "Android: %s (API %d)"),
                    Build.VERSION.RELEASE
                ).replaceFirst("%d", Build.VERSION.SDK_INT.toString())
            )
            appendLine(
                formatSupportText(
                    localizedString(context, "support_bundle", "renderer", "Renderer: %s; desired: %s"),
                    RendererState.getRenderer(prefs),
                    RendererState.getDesiredRenderer(prefs)
                )
            )
            appendLine(
                formatSupportText(
                    localizedString(context, "support_bundle", "backend", "Backend: root=%s, binder=%s, permission=%s"),
                    ShizukuHelper.isRootAvailable().toString(),
                    ShizukuHelper.checkBinder().toString(),
                    ShizukuHelper.checkPermission().toString()
                )
            )
            if (action != null) {
                appendLine(
                    formatSupportText(
                        localizedString(context, "support_bundle", "last_action", "Last action: %s → %s; success=%s; %s"),
                        action.source,
                        action.renderer,
                        action.success.toString(),
                        action.detail
                    )
                )
            }
            if (crashLog.isNotBlank()) {
                appendLine()
                appendLine(localizedString(context, "support_bundle", "recent_crash_log", "Recent GAMA crash log:"))
                appendLine(crashLog)
            }
        }
    }

    fun fileName(): String = "GAMA_support_${java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())}.txt"
}
