package com.popovicialinc.gama

import android.app.Notification
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

object ShizukuHelper {

    // ── Core Shizuku checks — use the real API now that source is vendored ────

    fun checkBinder(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Exception) { false }

    fun checkPermission(): Boolean {
        return try {
            if (Shizuku.isPreV11()) return false
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) { false }
    }

    // ── Root (su) backend ─────────────────────────────────────────────────────
    // Root is a first-class alternative to Shizuku: GAMA works with whichever
    // backend is available (root first, Shizuku second, neither = error).

    @Volatile
    private var rootAvailabilityCache: Boolean? = null
    @Volatile
    private var rootAvailabilityCheckedAtMs: Long = 0L
    private const val ROOT_CACHE_TTL_MS = 30_000L
    private const val SYSTEM_UI_RESTART_COOLDOWN_MS = 15_000L

    /**
     * Requests and verifies root through `su`. This must be called only after
     * an explicit user action: Magisk / KernelSU may display an approval prompt.
     *
     * [runRootCommand] waits for the process before consuming its streams. Reading
     * stdout first can otherwise block forever while a root manager is waiting
     * for the user to approve the request.
     */
    suspend fun refreshRootAvailability(): Boolean = withContext(Dispatchers.IO) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (rootAvailabilityCache == true && now - rootAvailabilityCheckedAtMs < ROOT_CACHE_TTL_MS) {
            return@withContext true
        }
        val result = runRootCommand("id").contains("uid=0")
        rootAvailabilityCache = result
        rootAvailabilityCheckedAtMs = android.os.SystemClock.elapsedRealtime()
        result
    }

    fun isRootAvailable(): Boolean = rootAvailabilityCache ?: false

    /** True when any backend (root or Shizuku) can execute commands right now. */
    fun isBackendReady(): Boolean = isRootAvailable() || (checkBinder() && checkPermission())

    /**
     * After a renderer change, keeps the QS renderer toggle tile in sync
     * so its subtitle reflects the current renderer.
     */
    suspend fun refreshRendererViewSync(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                TileService.requestListeningState(
                    context, ComponentName(context, RendererToggleTileService::class.java)
                )
            } catch (_: Exception) {}
        }
    }

    /**
     * Silently installs an APK via root (`pm install -r`). No confirmation dialog
     * appears at all. Returns false if root isn't available or the install failed.
     * The path is single-quoted with [shellQuote] so spaces or shell metacharacters
     * in the path can never break out of the argument.
     */
    suspend fun installApkViaRoot(apkPath: String): Boolean {
        if (!isRootAvailable()) return false
        val result = runRootCommand("pm install -r ${shellQuote(apkPath)}")
        return result == "Success"
    }

    private suspend fun runRootCommand(cmd: String, timeoutSeconds: Long = 3): String = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("su", "-c", cmd).start()
            try {
                val (output, error, finished) = coroutineScope {
                    val outputDeferred = async(Dispatchers.IO) {
                        process.inputStream.bufferedReader().readText()
                    }
                    val errorDeferred = async(Dispatchers.IO) {
                        process.errorStream.bufferedReader().readText()
                    }

                    val didFinish = process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
                    if (!didFinish) {
                        outputDeferred.cancel()
                        errorDeferred.cancel()
                        return@coroutineScope Triple("", "", false)
                    }

                    val out = try { outputDeferred.await() } catch (_: Exception) { "" }
                    val err = try { errorDeferred.await() } catch (_: Exception) { "" }
                    Triple(out, err, true)
                }

                val exitCode = if (finished) {
                    try { process.exitValue() } catch (_: Exception) { -1 }
                } else -1
                when {
                    !finished -> "Error: command timed out"
                    exitCode != 0 -> "Error: ${(error.ifBlank { output }).trim().ifBlank { "exit code $exitCode" }}"
                    output.isNotEmpty() -> output.trim()
                    else -> "Success"
                }
            } finally {
                process.destroy()
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    // ── Shell command execution via Shizuku.newProcess() ─────────────────────
    // Still uses reflection for newProcess since it keeps the Shizuku source
    // dependency minimal — only Shizuku.java needs to be vendored, not the
    // full process wrapper hierarchy.

    /**
     * Timeout-aware wait that works for both native and Shizuku remote processes.
     *
     * ShizukuRemoteProcess's base-class waitFor(timeout) polls isAlive() -> exitValue(),
     * and each poll is a Binder call. While the remote process is still running the
     * Shizuku server throws IllegalStateException("process hasn't exited") — a DIFFERENT
     * type than the IllegalThreadStateException java.lang.Process.isAlive() catches,
     * so it escapes and surfaces as a bogus "Error: process hasn't exited". waitForTimeout()
     * performs the whole wait server-side and returns a clean boolean, so it must be
     * used for remote processes.
     */
    private fun waitForProcessExit(process: Process, timeoutSeconds: Long): Boolean {
        return try {
            if (process is ShizukuRemoteProcess) {
                process.waitForTimeout(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
            } else {
                process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
            }
        } catch (_: Exception) {
            false
        }
    }

    // ── Shell command execution via Shizuku.newProcess() ─────────────────────
    // Still uses reflection for newProcess since it keeps the Shizuku source
    // dependency minimal — only Shizuku.java needs to be vendored, not the
    // full process wrapper hierarchy.
    //
    // runCommand is a suspend function so it can be cancelled by the caller's
    // coroutine scope (e.g. the user dismisses the switch dialog mid-switch).
    // The blocking waitFor runs on Dispatchers.IO — never on the main thread.
    //
    // Timeout is 3 seconds, not 10. `getprop`, `setprop`, `am crash`, and
    // `am force-stop` all complete in well under 1s on any supported device.
    // 10s was just the outer safety net; 3s still covers any legitimate slow
    // case while cutting the worst-case UI freeze from 10s to 3s if Shizuku
    // hangs on an unusual command.

    suspend fun runCommand(cmd: String): String = withContext(Dispatchers.IO) {
        if (isRootAvailable()) {
            val rootResult = runRootCommand(cmd)
            if (!rootResult.startsWith("Error", ignoreCase = true)) {
                return@withContext rootResult
            }

            // Root authorization can be revoked or disappear while the app is
            // alive. Do not let a stale positive cache hide a working
            // Shizuku/Shevery backend; invalidate root and fall through.
            rootAvailabilityCache = false
            rootAvailabilityCheckedAtMs = android.os.SystemClock.elapsedRealtime()
        }
        if (!checkBinder() || !checkPermission()) {
            return@withContext "Error: Shizuku not available and no root access"
        }
        try {
            val cls = Shizuku::class.java
            val method = cls.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val remoteProcess = method.invoke(null, arrayOf("sh", "-c", cmd), null, null)
            val process = remoteProcess as? Process ?: return@withContext "Error: Could not cast to Process"

            try {
                val (output, error, finished) = coroutineScope {
                    val outputDeferred = async(Dispatchers.IO) {
                        process.inputStream.bufferedReader().readText()
                    }
                    val errorDeferred = async(Dispatchers.IO) {
                        process.errorStream.bufferedReader().readText()
                    }

                    val didFinish = waitForProcessExit(process, 3)
                    if (!didFinish) {
                        outputDeferred.cancel()
                        errorDeferred.cancel()
                        return@coroutineScope Triple("", "", false)
                    }

                    val out = try { outputDeferred.await() } catch (_: Exception) { "" }
                    val err = try { errorDeferred.await() } catch (_: Exception) { "" }
                    Triple(out, err, true)
                }

                val exitCode = if (finished) {
                    try { process.exitValue() } catch (_: Exception) { -1 }
                } else -1
                when {
                    !finished -> "Error: command timed out"
                    exitCode != 0 -> "Error: ${(error.ifBlank { output }).trim().ifBlank { "exit code $exitCode" }}"
                    output.isNotEmpty() -> output.trim()
                    else -> "Success"
                }
            } finally {
                process.destroy()
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun requestPermissionFallback(context: Context) {
        val backendName = ShizukuBackend.installed(context)?.displayName ?: "Shizuku"
        try {
            Shizuku.requestPermission(0)
        } catch (_: Exception) {
            Toast.makeText(
                context,
                localizedString(context, "dialogs", "backend_grant_permission", "Open the %s app and grant GAMA permission")
                    .replace("%s", backendName),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ── Renderer detection ────────────────────────────────────────────────────
    //
    // Multi-source, foolproof renderer detection strategy:
    //  1. Check debug.hwui.renderer  — the prop GAMA sets; most reliable signal
    //  2. If empty/unset → system is running OpenGL (Android default when prop absent)
    //  3. If that fails → scan all properties for any hwui renderer mention
    //  4. Only return "Unknown" on a genuine I/O / permission error

    suspend fun getCurrentRenderer(): String = withContext(Dispatchers.IO) {
        if (!isBackendReady()) return@withContext "Unknown"

        // ── Source 1: the prop GAMA directly sets ─────────────────────────────
        val primary = runCommand("getprop debug.hwui.renderer").trim()
        when {
            primary.contains("skiavk", ignoreCase = true) -> return@withContext "Vulkan"
            primary.contains("opengl", ignoreCase = true) -> return@withContext "OpenGL"
            primary.isEmpty() || primary == "Success"     -> return@withContext "OpenGL"
            primary.startsWith("Error")                   -> { /* fall through to secondary */ }
            else                                          -> return@withContext "OpenGL"
        }

        // ── Source 2: scan all properties for any renderer mention ────────────
        // Use grep -E for extended regex so | is proper alternation (not \|
        // which is a literal backslash-pipe in basic grep and matches nothing).
        val allProps = runCommand("getprop | grep -Ei 'hwui|renderer'").trim()
        when {
            allProps.contains("skiavk", ignoreCase = true) -> return@withContext "Vulkan"
            allProps.contains("opengl", ignoreCase = true)  -> return@withContext "OpenGL"
            allProps.startsWith("Error")                    -> { /* fall through */ }
        }

        // ── Source 3: last resort — ask hwui what it's actually using ─────────
        // "dumpsys hwui" is slow so we pipe just the first 20 lines.
        val dumpsys = runCommand("dumpsys hwui 2>/dev/null | head -20").trim()
        when {
            dumpsys.contains("skiavk",  ignoreCase = true) -> return@withContext "Vulkan"
            dumpsys.contains("vulkan",  ignoreCase = true) -> return@withContext "Vulkan"
            dumpsys.contains("opengl",  ignoreCase = true) -> return@withContext "OpenGL"
            dumpsys.contains("skiagl",  ignoreCase = true) -> return@withContext "OpenGL"
            dumpsys.contains("software",ignoreCase = true) -> return@withContext "OpenGL"
        }

        // All three sources failed — return Unknown so the caller can decide what to show
        "Unknown"
    }

    // Offline renderer guessing moved to RendererState (boot-time stamp based,
    // unit-tested). ShizukuHelper only owns backend-backed detection.

    private val safePackageNameRegex = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
    private fun isSafePackageName(pkg: String): Boolean {
        return pkg.isNotBlank() && pkg.length <= 255 && safePackageNameRegex.matches(pkg)
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\\''") + "'"
    }

    private fun packageFromComponent(component: String): String {
        return component.substringBefore('/').trim()
    }

    private fun launcherPackageFromShellOutput(output: String): String {
        return output.lineSequence()
            .flatMap { line -> line.split(Regex("[\\s,]+" )).asSequence() }
            .map { token -> token.trim('[', ']', '(', ')').substringBefore('/') }
            .firstOrNull(::isSafePackageName)
            .orEmpty()
    }

    private fun isXiaomiFamilyDevice(): Boolean {
        val maker = listOf(Build.MANUFACTURER, Build.BRAND, Build.DEVICE, Build.PRODUCT)
            .joinToString(" ")
            .lowercase()
        return maker.contains("xiaomi") || maker.contains("redmi") || maker.contains("poco") || maker.contains("miui") || maker.contains("hyperos")
    }

    private fun knownLauncherPackages(): Set<String> = setOf(
        "com.miui.home",
        "com.sec.android.app.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.android.launcher3",
        "com.android.launcher",
        "com.huawei.android.launcher",
        "com.oppo.launcher",
        "com.coloros.launcher",
        "com.vivo.launcher",
        "com.realme.launcher",
        "com.oneplus.launcher"
    )

    private fun neverForceStopPackages(): Set<String> = setOf(
        "android",
        "system",
        "com.android.systemui",
        "com.android.phone",
        "com.android.providers.settings",
        "com.android.providers.media",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "moe.shizuku.privileged.api",
        "rikka.shizuku",
        "com.popovicialinc.gama"
    )

    // ── Renderer switch: shared implementation ────────────────────────────────
    // Both Vulkan and OpenGL switching are identical except for the prop value
    // and the display label. A single private function eliminates the duplication
    // so future changes (e.g. new app-restart logic) only need to be made once.
    //
    // Returns true when the renderer prop was verified to hold the target value
    // after the switch (or already held it). Callers that persist switch state
    // (tile, Tasker) should only record the new renderer when this returns true,
    // so a failed switch never poisons boot-restore.
    internal suspend fun applyRenderer(
        target: String,
        context: Context,
        aggressiveMode: Boolean,
        killLauncher: Boolean,
        killKeyboard: Boolean,
        excludedApps: Set<String>,
        onStatusUpdate: (String) -> Unit,
        onVerboseOutput: ((String) -> Unit)? = null,
        onVerified: (() -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val (propValue, label) = when (target) {
            RendererState.RENDERER_VULKAN -> "skiavk" to RendererState.RENDERER_VULKAN
            RendererState.RENDERER_OPENGL -> "opengl" to RendererState.RENDERER_OPENGL
            else -> return@withContext false
        }
        withContext(Dispatchers.Main) {
            onStatusUpdate(
                localizedString(context, "renderer", "status_running_commands", "Running %s commands…")
                    .replace("%s", label)
            )
        }

        val originalIme = runCommand("settings get secure default_input_method")
            .lineSequence()
            .firstOrNull()
            ?.trim()
            .orEmpty()
            .takeIf { it.isNotBlank() && it != "null" }
        val originalImePackage = originalIme?.let { packageFromComponent(it) }.orEmpty()
        // Resolve the actual default HOME app. Samsung One UI Home, third-party
        // launchers, and OEM launchers are all handled without guessing package
        // names. The role query is a fallback for ROMs that omit resolve-activity.
        val resolvedLauncher = launcherPackageFromShellOutput(
            runCommand("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
        )
        val activeLauncher = resolvedLauncher.ifBlank {
            launcherPackageFromShellOutput(
                runCommand("cmd role get-role-holders android.app.role.HOME")
            )
        }
        val launcherPackages = knownLauncherPackages() + activeLauncher
        val xiaomiFamilyDevice = isXiaomiFamilyDevice()
        val protectedPackages = neverForceStopPackages().toMutableSet().apply {
            addAll(excludedApps)
            if (!killKeyboard && originalImePackage.isNotBlank()) add(originalImePackage)
            // Aggressive mode must not indiscriminately stop every installed
            // launcher. The opt-in launcher path below restarts only the active HOME app.
            addAll(launcherPackages)
            // com.miui.home is never force-stopped. Xiaomi / HyperOS launchers have
            // repeatedly caused severe launcher loops / soft-bootloop behavior when
            // killed from a third-party Shizuku flow.
            add("com.miui.home")
        }

        fun canForceStopPackage(pkg: String): Boolean {
            if (!isSafePackageName(pkg)) return false
            if (pkg in protectedPackages) return false
            if (pkg.startsWith("com.android.inputmethod") && !killKeyboard) return false
            return true
        }

        suspend fun restoreOriginalImeIfNeeded(reason: String) {
            val ime = originalIme ?: return
            if (killKeyboard) return
            val quoted = shellQuote(ime)
            val current = runCommand("settings get secure default_input_method")
                .lineSequence()
                .firstOrNull()
                ?.trim()
                .orEmpty()
            if (current == ime) return
            onVerboseOutput?.invoke(
                (localizedString(context, "verbose", "restoring_ime", "Restoring original IME after %s: %s")
                    .replaceFirst("%s", reason).replaceFirst("%s", ime)) + "\n"
            )
            // `ime set` is the clean path; the settings write is the fallback for ROMs
            // where ime exits non-zero even though shell can update secure settings.
            runCommand("ime set $quoted >/dev/null 2>&1 || settings put secure default_input_method $quoted").also {
                onVerboseOutput?.invoke(
                    localizedString(context, "verbose", "output", "Output: %s").replace("%s", it) + "\n\n"
                )
            }
        }

        onVerboseOutput?.invoke(
            localizedString(context, "verbose", "running", "Running: %s")
                .replace("%s", "setprop debug.hwui.renderer $propValue") + "\n"
        )
        val setpropResult = runCommand("setprop debug.hwui.renderer $propValue")
        onVerboseOutput?.invoke(
            localizedString(context, "verbose", "output", "Output: %s").replace("%s", setpropResult) + "\n\n"
        )
        if (setpropResult.startsWith("Error", ignoreCase = true) ||
            setpropResult.contains("failed", ignoreCase = true) ||
            setpropResult.contains("permission denied", ignoreCase = true)
        ) {
            // The shell can report a bogus failure (e.g. the Shizuku remote process
            // died mid-wait) while the prop actually applied. Trust the property
            // itself: read it back before giving up.
            val readBack = runCommand("getprop debug.hwui.renderer").trim()
            if (readBack.equals(propValue, ignoreCase = true)) {
                onVerboseOutput?.invoke(
                    localizedString(
                        context, "verbose", "setprop_readback_error",
                        "setprop reported an error, but the prop reads back as '%s' — continuing."
                    ).replace("%s", readBack) + "\n\n"
                )
            } else {
                withContext(Dispatchers.Main) {
                    onStatusUpdate(
                        localizedString(context, "renderer", "status_setprop_failed", "%s setprop FAILED: %s")
                            .replace("%s", label).replace("%s", setpropResult)
                    )
                    Toast.makeText(
                        context,
                        localizedString(context, "renderer", "toast_setprop_failed", "Could not set renderer — %s")
                            .replace("%s", setpropResult),
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@withContext false
            }
        }

        // Verify before restarting anything. The durable state is committed by
        // RendererController through onVerified immediately below, before a
        // launcher/SystemUI restart can terminate this process.
        val verifyDetail = runCommand("getprop debug.hwui.renderer").trim()
        val verified = verifyDetail.equals(propValue, ignoreCase = true) ||
            (verifyDetail == "Success" && propValue == "opengl")
        if (!verified) {
            withContext(Dispatchers.Main) {
                val readBack = verifyDetail.takeUnless { it.startsWith("Error", ignoreCase = true) } ?: "unreadable"
                onStatusUpdate(
                    localizedString(
                        context, "renderer", "status_applied_unreadable",
                        "%s applied, but the renderer prop reads back as '%s'"
                    ).replace("%s", label).replace("%s", readBack)
                )
            }
            return@withContext false
        }
        onVerified?.invoke()

        if (aggressiveMode) {
            // Never mass-stop OEM/system packages. This mode deliberately
            // restarts third-party apps only; protected packages still cover
            // GAMA, Shizuku, IME and the active launcher.
            val packages = getThirdPartyPackageNames()
                .filter { pkg -> canForceStopPackage(pkg) }

            packages.forEach { pkg ->
                onVerboseOutput?.invoke(
                    localizedString(context, "verbose", "stopping", "Stopping: %s").replace("%s", pkg) + "\n"
                )
                runCommand("am force-stop ${shellQuote(pkg)}").also {
                    onVerboseOutput?.invoke(
                        localizedString(context, "verbose", "output", "Output: %s").replace("%s", it) + "\n"
                    )
                }
            }
        }

        // ── Keyboard restart (opt-in) ─────────────────────────────────────────
        // Done outside aggressive/targeted branches so the toggle is the ONLY path
        // that may restart the keyboard. If disabled, we protect and restore the
        // original IME to prevent Samsung/OneUI from falling back to Samsung Keyboard.
        if (killKeyboard && originalImePackage.isNotBlank()) {
            // IMEs only run while a text field is focused, so a bare force-stop can
            // look like nothing happened. Kill the process, let the system restart
            // it, then explicitly re-bind the original IME. The output is surfaced
            // so a refused force-stop is visible instead of silently ignored.
            val cmd = "am force-stop ${shellQuote(originalImePackage)}; sleep 0.5; ime set ${shellQuote(originalIme ?: "")} >/dev/null 2>&1"
            val out = runCommand(cmd)
            onVerboseOutput?.invoke(
                localizedString(context, "verbose", "running", "Running: %s").replace("%s", cmd) + "\n" +
                    localizedString(context, "verbose", "output", "Output: %s").replace("%s", out) + "\n\n"
            )
            if (out.startsWith("Error", ignoreCase = true)) {
                onVerboseOutput?.invoke(
                    localizedString(context, "verbose", "keyboard_restart_failed", "Keyboard restart failed: %s")
                        .replace("%s", out) + "\n"
                )
            }
        } else {
            restoreOriginalImeIfNeeded("renderer switch")
        }

        // ── System & launcher restart (opt-in, Xiaomi launcher-guarded) ──────
        // Restarts the launcher AND SystemUI so the new renderer applies to the
        // system chrome too (status bar, notification shade, recents). SystemUI
        // comes right back on its own, so the restart is safe. Only the launcher
        // is Xiaomi-guarded — com.miui.home is never force-stopped.
        if (killLauncher) {
            if (activeLauncher.isNotBlank() && !xiaomiFamilyDevice && activeLauncher != "com.miui.home") {
                val stopLauncher = "am force-stop ${shellQuote(activeLauncher)}"
                val stopOutput = runCommand(stopLauncher)
                onVerboseOutput?.invoke(
                    localizedString(context, "verbose", "running", "Running: %s").replace("%s", stopLauncher) + "\n" +
                        localizedString(context, "verbose", "output", "Output: %s").replace("%s", stopOutput) + "\n\n"
                )
                if (stopOutput.startsWith("Error", ignoreCase = true)) {
                    onVerboseOutput?.invoke(
                        (localizedString(
                            context, "verbose", "launcher_restart_failed",
                            "Launcher restart failed (%s): %s"
                        ).replaceFirst("%s", activeLauncher).replaceFirst("%s", stopOutput)) + "\n"
                    )
                } else {
                    // Force-stop alone relies on the system deciding when to recreate
                    // HOME. Explicitly launching HOME makes the restart immediate.
                    val launchHome = "am start -a android.intent.action.MAIN -c android.intent.category.HOME"
                    val launchOutput = runCommand(launchHome)
                    onVerboseOutput?.invoke(
                        localizedString(context, "verbose", "running", "Running: %s").replace("%s", launchHome) + "\n" +
                            localizedString(context, "verbose", "output", "Output: %s").replace("%s", launchOutput) + "\n\n"
                    )
                }
            } else if (xiaomiFamilyDevice) {
                onVerboseOutput?.invoke(
                    localizedString(
                        context, "verbose", "launcher_skipped_xiaomi",
                        "Launcher restart skipped on Xiaomi / HyperOS for safety."
                    ) + "\n\n"
                )
            } else {
                onVerboseOutput?.invoke(
                    localizedString(
                        context, "verbose", "launcher_skipped_no_home",
                        "Launcher restart skipped: could not resolve the active HOME app."
                    ) + "\n\n"
                )
            }
            val restartPrefs = context.getSharedPreferences("gama_prefs", Context.MODE_PRIVATE)
            val now = android.os.SystemClock.elapsedRealtime()
            val lastRestart = restartPrefs.getLong("last_systemui_restart_uptime", 0L)
            if (lastRestart == 0L || now - lastRestart >= SYSTEM_UI_RESTART_COOLDOWN_MS) {
                val systemUiCommand = "am force-stop com.android.systemui"
                val systemUiOutput = runCommand(systemUiCommand)
                restartPrefs.edit().putLong("last_systemui_restart_uptime", now).apply()
                onVerboseOutput?.invoke(
                    localizedString(context, "verbose", "running", "Running: %s").replace("%s", systemUiCommand) + "\n" +
                        localizedString(context, "verbose", "output", "Output: %s").replace("%s", systemUiOutput) + "\n\n"
                )
                if (systemUiOutput.startsWith("Error", ignoreCase = true)) {
                    onVerboseOutput?.invoke(
                        localizedString(context, "verbose", "systemui_restart_failed", "System UI restart failed: %s")
                            .replace("%s", systemUiOutput) + "\n"
                    )
                }
            } else {
                onVerboseOutput?.invoke(
                    localizedString(
                        context, "verbose", "systemui_skipped_cooldown",
                        "System UI restart skipped: 15-second cooldown is active."
                    ) + "\n\n"
                )
            }
        }

        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                localizedString(context, "renderer", "toast_switched", "Switched to %s").replace("%s", label),
                Toast.LENGTH_SHORT
            ).show()
        }
        true
    }

    /**
     * Wait for either the official Shizuku service or a compatible manager such
     * as Shevery to finish delivering and attaching its binder. Both managers
     * implement the same client protocol; the only meaningful distinction here
     * is whether the binder and GAMA's authorization are ready.
     */
    suspend fun awaitShizuku(timeoutMs: Long = 10_000L): Pair<Boolean, Boolean> {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs
        var running = false
        var permission = false
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            running = checkBinder()
            permission = running && checkPermission()
            if (permission || running) return running to permission
            delay(250L)
        }
        running = checkBinder()
        permission = running && checkPermission()
        return running to permission
    }

    private suspend fun getThirdPartyPackageNames(): List<String> =
        getPackageNames("pm list packages -3")

    suspend fun runVulkanSuspend(
        context: Context,
        aggressiveMode: Boolean,
        killLauncher: Boolean = false,
        killKeyboard: Boolean = false,
        excludedApps: Set<String>,
        onStatusUpdate: (String) -> Unit,
        onVerboseOutput: ((String) -> Unit)? = null
    ) = RendererController.switch(
        context,
        RendererController.Request(RendererState.RENDERER_VULKAN, "GAMA", aggressiveMode, killLauncher, killKeyboard, excludedApps),
        onStatusUpdate,
        onVerboseOutput
    ).verified

    suspend fun runOpenGLSuspend(
        context: Context,
        aggressiveMode: Boolean,
        killLauncher: Boolean = false,
        killKeyboard: Boolean = false,
        excludedApps: Set<String>,
        onStatusUpdate: (String) -> Unit,
        onVerboseOutput: ((String) -> Unit)? = null
    ) = RendererController.switch(
        context,
        RendererController.Request(RendererState.RENDERER_OPENGL, "GAMA", aggressiveMode, killLauncher, killKeyboard, excludedApps),
        onStatusUpdate,
        onVerboseOutput
    ).verified

    // ── Public fun wrappers ───────────────────────────────────────────────────

    // ── Public fun wrappers ───────────────────────────────────────────────────
    // Single guard eliminates duplicated checkBinder/checkPermission boilerplate.

    private fun guardedLaunch(
        context: Context,
        scope: CoroutineScope,
        block: suspend () -> Unit
    ) {
        if (isRootAvailable()) {
            scope.launch { block() }
            return
        }
        if (!checkBinder()) {
            val backendName = ShizukuBackend.installed(context)?.displayName ?: "Shizuku"
            Toast.makeText(
                context,
                localizedString(context, "common", "backend_not_running_toast", "%s not running!").replace("%s", backendName),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (!checkPermission()) {
            requestPermissionFallback(context)
            return
        }
        scope.launch { block() }
    }

    fun runVulkan(
        context: Context, scope: CoroutineScope, aggressiveMode: Boolean,
        killLauncher: Boolean = false,
        killKeyboard: Boolean = false,
        excludedApps: Set<String>,
        onStatusUpdate: (String) -> Unit, onVerboseOutput: ((String) -> Unit)? = null
    ) = guardedLaunch(context, scope) {
        runVulkanSuspend(context, aggressiveMode, killLauncher, killKeyboard, excludedApps, onStatusUpdate, onVerboseOutput)
    }

    fun runOpenGL(
        context: Context, scope: CoroutineScope, aggressiveMode: Boolean,
        killLauncher: Boolean = false,
        killKeyboard: Boolean = false,
        excludedApps: Set<String>,
        onStatusUpdate: (String) -> Unit, onVerboseOutput: ((String) -> Unit)? = null
    ) = guardedLaunch(context, scope) {
        runOpenGLSuspend(context, aggressiveMode, killLauncher, killKeyboard, excludedApps, onStatusUpdate, onVerboseOutput)
    }

    /**
     * Returns every package name on the device via `pm list packages -a`.
     *
     * This CANNOT use runCommand() because runCommand() calls waitFor() BEFORE
     * reading stdout.  If the process output exceeds the OS pipe buffer (~64 KB),
     * the process blocks trying to write, waitFor() never returns, the 3-second
     * timeout fires, and we discard all output.  On MIUI devices with 500+ packages
     * the output easily reaches 25–50 KB — close enough to the buffer limit that
     * it triggers intermittently depending on ROM and kernel config.
     *
     * Fix: read stdout in a concurrent coroutine so the buffer drains continuously
     * while the process runs.  Process can never block on a full buffer, so it
     * always exits cleanly within the timeout.
     */
    suspend fun getAllPackageNames(): List<String> = getPackageNames("pm list packages -a")

    private suspend fun getPackageNames(command: String): List<String> = withContext(Dispatchers.IO) {
        val shizukuReady = checkBinder() && checkPermission()
        // Package enumeration is never allowed to trigger a background root
        // prompt. Root must already have been explicitly approved in this process.
        if (!shizukuReady && !isRootAvailable()) return@withContext emptyList()
        if (!shizukuReady) {
            // Root path: same concurrent-reader pattern, just spawned via su.
            return@withContext try {
                val process = ProcessBuilder("su", "-c", command).start()
                try {
                    val (outputText, _) = coroutineScope {
                        val outputDeferred = async(Dispatchers.IO) {
                            process.inputStream.bufferedReader().readText()
                        }
                        val errorDeferred = async(Dispatchers.IO) {
                            process.errorStream.bufferedReader().readText()
                        }
                        val finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
                        if (!finished) {
                            outputDeferred.cancel()
                            errorDeferred.cancel()
                            return@coroutineScope Pair("", "")
                        }
                        val out = try { outputDeferred.await() } catch (_: Exception) { "" }
                        errorDeferred.cancel()
                        Pair(out, "")
                    }
                    outputText
                        .lines()
                        .filter { it.startsWith("package:") }
                        .map { it.removePrefix("package:").trim() }
                        .filter { it.isNotEmpty() }
                } finally {
                    process.destroy()
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
        try {
            val cls    = Shizuku::class.java
            val method = cls.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val remoteProcess = method.invoke(
                null, arrayOf("sh", "-c", command), null, null
            )
            val process = remoteProcess as? Process ?: return@withContext emptyList()

            try {
                // Launch concurrent readers BEFORE calling waitFor so the pipe
                // buffer never fills up regardless of how many packages exist.
                // coroutineScope provides the scope that async requires.
                val (outputText, _) = coroutineScope {
                    val outputDeferred = async(Dispatchers.IO) {
                        process.inputStream.bufferedReader().readText()
                    }
                    // stderr also needs draining to prevent a secondary buffer block
                    val errorDeferred = async(Dispatchers.IO) {
                        process.errorStream.bufferedReader().readText()
                    }

                    val finished = waitForProcessExit(process, 30)

                    if (!finished) {
                        outputDeferred.cancel()
                        errorDeferred.cancel()
                        return@coroutineScope Pair("", "")
                    }

                    val out = try { outputDeferred.await() } catch (_: Exception) { "" }
                    errorDeferred.cancel() // discard stderr — we only need package names
                    Pair(out, "")
                }

                outputText
                    .lines()
                    .filter { it.startsWith("package:") }
                    .map { it.removePrefix("package:").trim() }
                    .filter { it.isNotEmpty() }
            } finally {
                process.destroy()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ── Crash log fetching ────────────────────────────────────────────────────
    //
    // fetchCrashLogs uses the same concurrent-reader pattern as getAllPackageNames
    // because "dumpsys dropbox --print" on a full dropbox can easily produce
    // hundreds of KB — far beyond the OS pipe buffer (~64 KB).
    // runCommand() calls waitFor() before reading stdout, so on a full dropbox
    // the subprocess would block writing while waitFor() waits for it to exit:
    // a classic deadlock.  Draining stdout in a concurrent coroutine prevents this.

    suspend fun fetchCrashLogs(): List<CrashEntry> = withContext(Dispatchers.IO) {
        if (isRootAvailable()) {
            val raw = runRootCommand("dumpsys dropbox --print", timeoutSeconds = 30)
            return@withContext if (raw.isBlank() || raw.startsWith("Error:")) emptyList() else parseCrashLogs(raw)
        }
        if (!checkBinder() || !checkPermission()) return@withContext emptyList()

        try {
            val cls    = Shizuku::class.java
            val method = cls.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val remoteProcess = method.invoke(
                null,
                arrayOf("sh", "-c", "dumpsys dropbox --print"),
                null,
                null
            )
            val process = remoteProcess as? Process ?: return@withContext emptyList()

            val raw = try {
                coroutineScope {
                    val outputDeferred = async(Dispatchers.IO) {
                        process.inputStream.bufferedReader().readText()
                    }
                    // Always drain stderr — even if we don't use it — to prevent
                    // a secondary buffer block if the command writes to both streams.
                    val errorDeferred = async(Dispatchers.IO) {
                        process.errorStream.bufferedReader().readText()
                    }

                    val finished = waitForProcessExit(process, 30)

                    if (!finished) {
                        outputDeferred.cancel()
                        errorDeferred.cancel()
                        return@coroutineScope ""
                    }

                    val out = try { outputDeferred.await() } catch (_: Exception) { "" }
                    errorDeferred.cancel()
                    out
                }
            } finally {
                process.destroy()
            }

            if (raw.isEmpty()) return@withContext emptyList()
            parseCrashLogs(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseCrashLogs(raw: String): List<CrashEntry> {
        val entries = mutableListOf<CrashEntry>()
        val sections = raw.split(Regex("(?=Drop box tag:)"))
        for (section in sections) {
            if (section.isBlank()) continue
            val tagLine = section.lines().firstOrNull() ?: continue
            val relevantTags = listOf("system_app_crash","system_server_crash","system_app_anr","system_server_anr","crash","anr")
            if (relevantTags.none { tagLine.contains(it, ignoreCase = true) }) continue
            val tag = Regex("Drop box tag: ([^,]+)").find(tagLine)?.groupValues?.get(1)?.trim() ?: "unknown"
            val timeMillis = Regex("time: (\\d+)").find(tagLine)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val body = section.lines().drop(1).joinToString("\n").trim()
            val isRelevant = body.contains("SystemUI", ignoreCase = true) ||
                    body.contains("hwui", ignoreCase = true) ||
                    body.contains("renderer", ignoreCase = true) ||
                    body.contains("com.android.systemui", ignoreCase = true) ||
                    tagLine.contains("SystemUI", ignoreCase = true)
            if (!isRelevant && entries.size >= 30) continue
            if (!isRelevant && !tagLine.contains("system", ignoreCase = true)) continue
            val shortSummary = body.lines()
                .firstOrNull { it.contains("Exception") || it.contains("Error") || it.contains("at ") }
                ?.trim() ?: body.lines().firstOrNull { it.isNotBlank() }?.trim() ?: ""
            entries.add(CrashEntry(
                tag        = tag,
                timeMillis = timeMillis,
                summary    = shortSummary.take(200),
                fullText   = body.take(4000),
                isSystemUI = body.contains("com.android.systemui", ignoreCase = true) ||
                        tagLine.contains("SystemUI", ignoreCase = true)
            ))
        }
        return entries.sortedByDescending { it.timeMillis }.take(50)
    }

    data class CrashEntry(
        val tag: String,
        val timeMillis: Long,
        val summary: String,
        val fullText: String,
        val isSystemUI: Boolean
    )

    // ── Notification helpers ──────────────────────────────────────────────────

    fun hasNotificationPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        else true

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "gama_test",
                localizedString(context, "notification", "channel_test_name", "GAMA Test Notifications"),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = localizedString(context, "notification", "channel_test_desc", "Test notifications from GAMA")
                enableVibration(true); enableLights(true)
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    fun sendTestNotification(context: Context, userName: String): Boolean {
        return try {
            if (!hasNotificationPermission(context)) return false
            createNotificationChannel(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(
                System.currentTimeMillis().toInt(),
                Notification.Builder(context, "gama_test")
                    // Android notification small icons must be an app-provided monochrome drawable.
                    // Framework icons can render as a blank white square on some ROMs.
                    .setSmallIcon(R.drawable.ic_notification)
                    .setColor(0xFF5A63A8.toInt())
                    .setColorized(false)
                    .setContentTitle(
                        if (userName.isNotEmpty())
                            localizedString(context, "notification", "test_title_named", "Hey %s! 👋").replace("%s", userName)
                        else localizedString(context, "notification", "test_title_unnamed", "Test Notification")
                    )
                    .setContentText(
                        if (userName.isNotEmpty())
                            localizedString(context, "notification", "test_body_named", "Your notification system is working perfectly!")
                        else localizedString(context, "notification", "test_body_unnamed", "Notifications are working correctly!")
                    )
                    .setPriority(Notification.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setVibrate(longArrayOf(0, 250, 250, 250))
                    .build()
            )
            true
        } catch (_: Exception) { false }
    }
}
