package com.popovicialinc.gama

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import rikka.shizuku.Shizuku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    // ── Shizuku listeners ─────────────────────────────────────────────────────

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        if (!Shizuku.isPreV11()) {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(0)
            }
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        // Shizuku service died — nothing to do, binder listener will fire again on reconnect
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                // Restart the app so all Shizuku-dependent UI initialises cleanly
                val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                if (intent != null) {
                    startActivity(intent)
                    finishAffinity()
                    overridePendingTransition(0, 0)
                } else {
                    recreate()
                }
            }
        }

    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        try { installSplashScreen() } catch (_: Exception) {}
        super.onCreate(savedInstanceState)

        // ── Crash logger ──────────────────────────────────────────────────────
        // Capture the default handler BEFORE we replace it so we can chain to
        // it after writing the log — this keeps the normal crash dialog / restart
        // behaviour intact while also persisting the stack trace for our panel.
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        val crashLogFile = java.io.File(filesDir, "crash_log.txt")
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val timestamp = java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss", java.util.Locale.US
                ).format(java.util.Date())
                val entry = buildString {
                    append("── $timestamp ─────────────────────────────\n")
                    append("Thread: ${thread.name}\n")
                    append(throwable.stackTraceToString())
                    append("\n\n")
                }
                // Prepend so newest crash is always at the top; keep file under ~64 KB
                val existing = if (crashLogFile.exists()) crashLogFile.readText() else ""
                val trimmed = if (existing.length > 60_000) existing.take(60_000) else existing
                crashLogFile.writeText(entry + trimmed)
            } catch (_: Exception) {
                // Never let the logger itself prevent the normal crash flow
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Register Shizuku listeners as early as possible
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)

        try {
            enableEdgeToEdge()
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, window.decorView).apply {
                // GAMA draws a full-screen experience; hide both bars so the
                // bottom action controls cannot sit underneath navigation UI.
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } catch (_: Exception) {}

        // Leave refresh-rate selection to Android's adaptive/system governor.
        // The app's animation and particle loops pace themselves from the
        // display they actually receive, so a settings utility does not keep an
        // LTPO panel at its maximum rate while the UI is idle.

        setContent {
            val scope = rememberCoroutineScope()
            val prefs = remember {
                getSharedPreferences("gama_prefs", android.content.Context.MODE_PRIVATE)
            }
            // ── Notification permission ───────────────────────────────────────
            var notifPermTrigger by remember { mutableStateOf(0) }
            val notifPermLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { /* GamaUI re-checks hasPermission on recompose */ }
            LaunchedEffect(notifPermTrigger) {
                if (notifPermTrigger > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            // ── Backup: SAF file create ───────────────────────────────────────
            var pendingBackupContent by remember { mutableStateOf<String?>(null) }
            val createDocLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json")
            ) { uri ->
                val content = pendingBackupContent ?: return@rememberLauncherForActivityResult
                pendingBackupContent = null
                if (uri == null) {
                    Toast.makeText(this@MainActivity, localizedString(this@MainActivity, "backup", "backup_export_cancelled", "Backup export cancelled"), Toast.LENGTH_SHORT).show()
                    return@rememberLauncherForActivityResult
                }
                scope.launch {
                    val saved = withContext(Dispatchers.IO) {
                        runCatching {
                            contentResolver.openOutputStream(uri)?.use { out ->
                                out.write(content.toByteArray(Charsets.UTF_8))
                            } ?: error("Could not open the selected file")
                        }.isSuccess
                    }
                    Toast.makeText(
                        this@MainActivity,
                        if (saved) localizedString(this@MainActivity, "backup", "saved", "Backup saved")
                        else localizedString(this@MainActivity, "backup", "save_failed", "Could not save backup"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            // ── Restore: SAF file open ────────────────────────────────────────
            var pendingRestoreCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }
            val openDocLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                val cb = pendingRestoreCallback ?: return@rememberLauncherForActivityResult
                pendingRestoreCallback = null
                if (uri == null) return@rememberLauncherForActivityResult
                scope.launch {
                    val text = withContext(Dispatchers.IO) {
                        runCatching {
                            contentResolver.openInputStream(uri)?.use { input ->
                                // GAMA backups are tiny. Bound import size so a renamed
                                // giant JSON file cannot consume the UI process's memory.
                                val output = java.io.ByteArrayOutputStream()
                                val buffer = ByteArray(16 * 1024)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    require(output.size() + read <= 1_000_000) { "Backup is too large" }
                                    output.write(buffer, 0, read)
                                }
                                output.toString(Charsets.UTF_8.name())
                            }
                        }.getOrNull()
                    }
                    if (text != null) cb(text)
                    else Toast.makeText(this@MainActivity, localizedString(this@MainActivity, "backup", "read_failed", "Could not read backup"), Toast.LENGTH_SHORT).show()
                }
            }

            // ── Crash log export: SAF file create (plain text) ────────────────
            var pendingCrashLogContent by remember { mutableStateOf<String?>(null) }
            val createCrashLogLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("text/plain")
            ) { uri ->
                val content = pendingCrashLogContent ?: return@rememberLauncherForActivityResult
                pendingCrashLogContent = null
                if (uri == null) {
                    Toast.makeText(this@MainActivity, localizedString(this@MainActivity, "backup", "export_cancelled", "Export cancelled"), Toast.LENGTH_SHORT).show()
                    return@rememberLauncherForActivityResult
                }
                scope.launch {
                    val saved = withContext(Dispatchers.IO) {
                        runCatching {
                            contentResolver.openOutputStream(uri)?.use { out ->
                                out.write(content.toByteArray(Charsets.UTF_8))
                            } ?: error("Could not open the selected file")
                        }.isSuccess
                    }
                    Toast.makeText(
                        this@MainActivity,
                        if (saved) localizedString(this@MainActivity, "backup", "export_saved", "Export saved")
                        else localizedString(this@MainActivity, "backup", "export_save_failed", "Could not save export"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                GamaLocalizationProvider(prefs = prefs) {
                    GamaUI(
                        onRequestNotificationPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifPermTrigger++
                            }
                        },
                        onExportBackup = { jsonContent, fileName ->
                            pendingBackupContent = jsonContent
                            createDocLauncher.launch(fileName)
                        },
                        onImportBackup = { callback ->
                            pendingRestoreCallback = callback
                            openDocLauncher.launch(arrayOf("application/json"))
                        },
                        onExportCrashLog = { content, fileName ->
                            pendingCrashLogContent = content
                            createCrashLogLauncher.launch(fileName)
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
    }
}
