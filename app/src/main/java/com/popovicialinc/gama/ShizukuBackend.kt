package com.popovicialinc.gama

import android.content.Context
import android.content.pm.PackageManager

/**
 * Identifies installed implementations of the Shizuku client protocol.
 *
 * GAMA deliberately continues to use the upstream Shizuku API. Shevery
 * preserves that API (including the legacy manager authority), so it can use
 * the same Binder command path without becoming a hard dependency.
 */
object ShizukuBackend {
    const val OFFICIAL_PACKAGE = "moe.shizuku.privileged.api"
    const val SHEVERY_PACKAGE = "com.hamondev.shevery"

    enum class Kind(val packageName: String, val displayName: String) {
        OFFICIAL(ShizukuBackend.OFFICIAL_PACKAGE, "Shizuku"),
        SHEVERY(ShizukuBackend.SHEVERY_PACKAGE, "Shevery")
    }

    /**
     * Returns the installed manager GAMA should direct the user to. The
     * official manager wins when both are visible, preserving it as GAMA's
     * default and avoiding any automatic migration to a third-party fork.
     */
    fun installed(context: Context): Kind? =
        Kind.values().firstOrNull { isInstalled(context, it.packageName) }

    fun isInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
