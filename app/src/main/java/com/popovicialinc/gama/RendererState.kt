package com.popovicialinc.gama

import android.content.SharedPreferences
import android.os.SystemClock

/**
 * Single source of truth for the persisted renderer switch state.
 *
 * Keys that must move together when the user applies a renderer change:
 *  - last_renderer          — which renderer was last applied ("Vulkan"/"OpenGL")
 *  - last_switch_time       — wall-clock ms ("X ago" label)
 *  - last_switch_uptime     — elapsedRealtime ms at switch time
 *  - last_switch_boot_time  — approximate device boot time (wall-clock minus
 *                             uptime) at switch time. Comparing this against the
 *                             current boot time detects reboots even when the
 *                             new session's uptime has grown past the stored
 *                             uptime — which defeats the uptime-only heuristic.
 *
 * Every code path that applies a renderer change MUST call [recordSwitch] —
 * otherwise BootReceiver skips the post-reboot restore (it only re-applies when
 * last_switch_time is set) and the UI/tile show a stale renderer.
 */
object RendererState {

    const val PREF_LAST_RENDERER = "last_renderer"
    /** The user's renderer choice; retained across a reboot while actual state resets. */
    const val PREF_DESIRED_RENDERER = "desired_renderer"
    const val PREF_LAST_SWITCH_TIME = "last_switch_time"
    const val PREF_LAST_SWITCH_UPTIME = "last_switch_uptime"
    const val PREF_LAST_SWITCH_BOOT_TIME = "last_switch_boot_time"

    const val RENDERER_VULKAN = "Vulkan"
    const val RENDERER_OPENGL = "OpenGL"

    /**
     * Boot-time comparison tolerance (ms). currentTimeMillis() and
     * elapsedRealtime() are read in separate calls and the wall clock can be
     * adjusted by NTP between writes; small drift must not look like a reboot.
     * Large deliberate clock changes (>5 min) degrade to the conservative
     * "rebooted" answer, which only affects the offline guess display path —
     * when any backend is available the real prop is read instead.
     */
    const val BOOT_TIME_DRIFT_MS = 5 * 60_000L

    /**
     * Persist a renderer switch.
     *
     * Uses commit() (synchronous), not apply(): switching renderers soft-crashes
     * SystemUI, and on some devices that can also take GAMA's process down before
     * an async write reaches disk. The cost of one synchronous fsync per manual
     * switch is negligible; a lost write means the renderer silently reverts after
     * the next reboot.
     */
    fun recordSwitch(
        prefs: SharedPreferences,
        renderer: String,
        atWallClockMs: Long = System.currentTimeMillis()
    ) {
        prefs.edit()
            .putString(PREF_LAST_RENDERER, renderer)
            .putString(PREF_DESIRED_RENDERER, renderer)
            .putLong(PREF_LAST_SWITCH_TIME, atWallClockMs)
            .putLong(PREF_LAST_SWITCH_UPTIME, SystemClock.elapsedRealtime())
            .putLong(PREF_LAST_SWITCH_BOOT_TIME, currentBootTimeMs())
            .commit()
    }

    /**
     * Called by BootRendererWorker after successfully RE-applying the saved
     * renderer post-reboot. Refreshes the uptime/boot-time stamps so offline
     * reboot detection knows the prop matches the CURRENT session (a plain
     * [recordSwitch] would lie about the wall-clock switch time). The renderer
     * physically changed at boot, so the displayed timestamp is set to the
     * current boot time. commit() makes this visible before the worker exits.
     */
    fun stampRestore(
        prefs: SharedPreferences,
        renderer: String
    ) {
        prefs.edit()
            .putString(PREF_LAST_RENDERER, renderer)
            .putLong(PREF_LAST_SWITCH_TIME, currentBootTimeMs())
            .putLong(PREF_LAST_SWITCH_UPTIME, SystemClock.elapsedRealtime())
            .putLong(PREF_LAST_SWITCH_BOOT_TIME, currentBootTimeMs())
            .commit()
    }

    /**
     * Record the known post-boot fallback when boot restoration could not even
     * start because no privileged backend became available.  The desired
     * renderer is intentionally preserved so a later manual retry can still
     * restore Vulkan.
     */
    fun recordBootRestoreUnavailable(prefs: SharedPreferences) {
        val desired = getDesiredRenderer(prefs)
        if (desired != RENDERER_VULKAN || getRenderer(prefs) != RENDERER_VULKAN) return

        val bootTime = currentBootTimeMs()
        prefs.edit()
            .putString(PREF_DESIRED_RENDERER, desired)
            .putString(PREF_LAST_RENDERER, RENDERER_OPENGL)
            .putLong(PREF_LAST_SWITCH_TIME, bootTime)
            .putLong(PREF_LAST_SWITCH_UPTIME, SystemClock.elapsedRealtime())
            .putLong(PREF_LAST_SWITCH_BOOT_TIME, bootTime)
            .commit()
    }

    /** Read the persisted renderer, defaulting to OpenGL (the Android default). */
    fun getRenderer(prefs: SharedPreferences): String =
        prefs.getString(PREF_LAST_RENDERER, RENDERER_OPENGL) ?: RENDERER_OPENGL

    /** The renderer GAMA should restore after a reboot, if privileged access exists. */
    fun getDesiredRenderer(prefs: SharedPreferences): String =
        prefs.getString(PREF_DESIRED_RENDERER, null) ?: getRenderer(prefs)

    /** Records a renderer observed through Shizuku/root without changing user intent. */
    fun recordObservedRenderer(prefs: SharedPreferences, renderer: String) {
        prefs.edit().putString(PREF_LAST_RENDERER, renderer).apply()
    }

    /**
     * If a reboot definitely cleared an active Vulkan property, record the
     * actual OpenGL fallback while preserving the user's Vulkan restore choice.
     * Returns true only when state changed.
     */
    fun reconcileRebootReset(prefs: SharedPreferences): Boolean {
        if (getRenderer(prefs) != RENDERER_VULKAN || wasRebootedSinceLastSwitch(prefs) != true) {
            return false
        }
        val desired = getDesiredRenderer(prefs)
        val bootTime = currentBootTimeMs()
        prefs.edit()
            .putString(PREF_DESIRED_RENDERER, desired)
            .putString(PREF_LAST_RENDERER, RENDERER_OPENGL)
            .putLong(PREF_LAST_SWITCH_TIME, bootTime)
            .putLong(PREF_LAST_SWITCH_UPTIME, SystemClock.elapsedRealtime())
            .putLong(PREF_LAST_SWITCH_BOOT_TIME, bootTime)
            .commit()
        return true
    }

    /**
     * Best-effort offline reboot detection.
     *
     * @return true  — definitely rebooted since the last recorded switch
     *               (runtime props were cleared → actual renderer is OpenGL),
     *         false — definitely still the same session,
     *         null  — not enough information (no stamps; very old install).
     */
    fun wasRebootedSinceLastSwitch(prefs: SharedPreferences): Boolean? {
        return wasRebootedInternal(
            storedBootTimeMs = prefs.getLong(PREF_LAST_SWITCH_BOOT_TIME, 0L),
            currentBootTimeMs = currentBootTimeMs(),
            storedUptimeMs = prefs.getLong(PREF_LAST_SWITCH_UPTIME, 0L),
            currentUptimeMs = SystemClock.elapsedRealtime(),
            storedSwitchWallClockMs = prefs.getLong(PREF_LAST_SWITCH_TIME, 0L),
            nowWallClockMs = System.currentTimeMillis()
        )
    }

    /**
     * Pure decision core — all inputs are parameters so unit tests can exercise
     * every scenario on the JVM (no android.os dependencies).
     */
    internal fun wasRebootedInternal(
        storedBootTimeMs: Long,
        currentBootTimeMs: Long,
        storedUptimeMs: Long,
        currentUptimeMs: Long,
        storedSwitchWallClockMs: Long,
        nowWallClockMs: Long
    ): Boolean? {
        // ── Current-generation stamp: direct boot-time comparison ─────────────
        if (storedBootTimeMs != 0L) {
            val drift = Math.abs(currentBootTimeMs - storedBootTimeMs)
            return drift > BOOT_TIME_DRIFT_MS
        }
        // ── Legacy stamp 1: uptime-only (installs before boot-time stamping) ──
        // If the current uptime is smaller than the uptime recorded at switch
        // time, the device must have rebooted since.
        if (storedUptimeMs > 0L) {
            return currentUptimeMs < storedUptimeMs
        }
        // ── Legacy stamp 2: wall-clock only (oldest installs) ─────────────────
        // Only trust it when the switch was recent (<12 h) — otherwise normal
        // daily reboots make "boot started after the switch" almost always true.
        if (storedSwitchWallClockMs > 0L) {
            val ageMs = nowWallClockMs - storedSwitchWallClockMs
            return currentBootTimeMs > storedSwitchWallClockMs && ageMs in 0..(12 * 60 * 60 * 1000L)
        }
        return null
    }

    private fun currentBootTimeMs(): Long = System.currentTimeMillis() - SystemClock.elapsedRealtime()
}
