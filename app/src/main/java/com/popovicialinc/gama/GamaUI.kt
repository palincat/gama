package com.popovicialinc.gama

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.roundToInt


private fun sanitizeAccentColorForTheme(color: Color, isOledMode: Boolean): Color {
    val luma = color.luminance()
    return when {
        isOledMode && luma < 0.18f -> lerp(color, Color.White, 0.35f)
        !isOledMode && luma > 0.82f -> lerp(color, Color.Black, 0.30f)
        else -> color
    }
}

@Composable
fun GamaUI(
    onRequestNotificationPermission: () -> Unit = {},
    onExportBackup: (jsonContent: String, fileName: String) -> Unit = { _, _ -> },
    onImportBackup: (callback: (String) -> Unit) -> Unit = {},
    onExportCrashLog: (content: String, fileName: String) -> Unit = { _, _ -> }
) {
    val ts = LocalTypeScale.current
    val strings = LocalStrings.current
    val languageCodeState = LocalLanguageCode.current
    val languageCode = languageCodeState.value
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var appInForeground by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START,
                Lifecycle.Event.ON_RESUME -> appInForeground = true

                Lifecycle.Event.ON_STOP -> appInForeground = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val isSmallScreen = screenWidth < 360.dp || screenHeight < 640.dp
    val isLargeScreen = screenWidth > 600.dp
    val isLandscape = screenWidth > screenHeight
    val isTablet = screenWidth >= 600.dp && screenHeight >= 600.dp

    fun performHaptic(type: Int = HapticFeedbackConstants.CONTEXT_CLICK) {
        GamaHaptics.feedback(context, view, type)
    }

    fun performSuccessHaptic() {
        GamaHaptics.success(context, view)
    }

    fun performErrorHaptic() {
        GamaHaptics.error(context, view)
    }

    fun performInteractionHaptic() {
        GamaHaptics.selection(context, view)
    }

    fun performRendererHaptic() {
        GamaHaptics.rendererSelection(context, view)
    }

    val prefs = remember { context.getSharedPreferences("gama_prefs", android.content.Context.MODE_PRIVATE) }

    // Show button labels exactly once — fades after first launch
    var showButtonLabels by remember {
        mutableStateOf(!prefs.getBoolean("button_labels_shown", false))
    }

    // Version-based preferences reset — wrapped in SideEffect so it runs exactly
    // once per composition (not on every recomposition) and never on the hot path.
    // We preserve personal data so a PREFS_VERSION bump doesn't silently nuke the
    // user's name, excluded apps list, or first-launch flags.
    val PREFS_VERSION = 4
    val savedPrefsVersion = prefs.getInt("prefs_version", 0)

    if (savedPrefsVersion < PREFS_VERSION) {
        SideEffect {
            // Migrations must be additive. Clearing the preference file here
            // silently reset themes, particles, haptics, notifications, and
            // renderer settings for every existing user on a schema bump.
            prefs.edit().apply {
                putInt("prefs_version", PREFS_VERSION)
                if (!prefs.contains("animation_speed")) {
                    putInt("animation_speed", 1) // Default for genuinely new keys
                }
            }.apply()
        }
    }

    var shizukuStatus by remember { mutableStateOf(strings["common.checking"].ifEmpty { "Checking..." }) }
    // Shevery exposes the same Shizuku client protocol. This only affects the
    // label shown to the user; command execution stays on the upstream API.
    val shizukuBackendName = remember {
        ShizukuBackend.installed(context)?.displayName ?: "Shizuku"
    }
    var showShizukuHelp by remember { mutableStateOf(false) }
    var shizukuHelpType by remember { mutableStateOf("") }
    var commandOutput by remember { mutableStateOf("") }
    // Default to OpenGL if no preference is saved
    var currentRenderer by remember { mutableStateOf(prefs.getString("last_renderer", "OpenGL") ?: "OpenGL") }
    var lastRendererAction by remember { mutableStateOf(RendererActionHistory.read(prefs)) }
    // True from launch until Shizuku either confirms the renderer or we know Shizuku isn't ready
    var rendererLoading by remember { mutableStateOf(true) }
    var lastSwitchTime by remember { mutableStateOf(prefs.getLong("last_switch_time", 0L)) }
    var showWarningDialog by remember { mutableStateOf(false) }
    var pendingRendererSwitch by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingRendererName by remember { mutableStateOf("") } // Store target renderer name
    var activeRendererTarget by remember { mutableStateOf("") }
    var shizukuRunning by remember { mutableStateOf(false) }
    var shizukuPermissionGranted by remember { mutableStateOf(false) }
    var rootAvailable by remember { mutableStateOf(false) }

    // Status updates drive the progress UI. The timestamp itself is persisted
    // synchronously when the user confirms a switch below: the renderer command
    // can restart SystemUI (and disrupt GAMA) before its final callback arrives.
    val onRendererStatusUpdate: (String) -> Unit = { status ->
        commandOutput = status
    }

    // Dialog States
    var showSuccessDialog by remember { mutableStateOf(false) }
    var successDialogMessage by remember { mutableStateOf("") }
    // True from the moment the user taps Continue until commandOutput arrives,
    // which is the real completion signal from ShizukuHelper. During this window
    // SuccessDialog shows a spinner rather than the final confirmation message.
    var rendererSwitching by remember { mutableStateOf(false) }

    // Renderer changes can happen outside this activity (QS tile, Tasker,
    // boot worker, Quick Settings, or another privileged tool). Refresh on resume so
    // the visible state cannot remain stale for the lifetime of the screen.
    DisposableEffect(lifecycleOwner, rendererSwitching) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !rendererSwitching) {
                scope.launch {
                    // Shizuku can be started while GAMA is backgrounded. Refresh
                    // both flags on every return; they are not guaranteed to be
                    // updated by the Shizuku callbacks after a cold start.
                    val (latestRunning, latestPermission) = if (ShizukuBackend.installed(context) != null) {
                        ShizukuHelper.awaitShizuku()
                    } else {
                        ShizukuHelper.checkBinder() to ShizukuHelper.checkPermission()
                    }
                    shizukuRunning = latestRunning
                    shizukuPermissionGranted = latestPermission
                    val latestRoot = ShizukuHelper.isRootAvailable()
                    rootAvailable = latestRoot
                    lastRendererAction = RendererActionHistory.read(prefs)
                    shizukuStatus = when {
                        latestRoot -> strings["main.root_ready"].ifEmpty { "Running with root access ✅" }
                        latestRunning && latestPermission -> if (shizukuBackendName == "Shizuku") {
                            strings["main.shizuku_ready"].ifEmpty { "Shizuku is running ✅" }
                        } else strings["main.backend_ready"].ifEmpty { "%s is running ✅" }.replace("%s", shizukuBackendName)
                        latestRunning -> strings["main.shizuku_permission_needed"].ifEmpty { "Permission needed ⚠️" }
                        else -> if (shizukuBackendName == "Shizuku") {
                            strings["main.shizuku_not_running"].ifEmpty { "Shizuku isn't running ❌" }
                        } else strings["main.backend_not_running"].ifEmpty { "%s isn't running ❌" }.replace("%s", shizukuBackendName)
                    }
                    val backendReady = latestRoot || (latestRunning && latestPermission)
                    if (backendReady) {
                        val detected = ShizukuHelper.getCurrentRenderer()
                        if (detected == RendererState.RENDERER_VULKAN || detected == RendererState.RENDERER_OPENGL) {
                            currentRenderer = detected
                            if (detected == RendererState.RENDERER_OPENGL) {
                                RendererState.reconcileRebootReset(prefs)
                            }
                            RendererState.recordObservedRenderer(prefs, detected)
                            lastSwitchTime = prefs.getLong(RendererState.PREF_LAST_SWITCH_TIME, lastSwitchTime)
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // showDeveloperMenu removed — DeveloperMenuDialog was never reachable (showDeveloperMenu was
    // never set to true anywhere). DeveloperPanel (showDeveloper) is the live path.
    var showVerbosePanel by remember { mutableStateOf(false) }
    var verboseOutput by remember { mutableStateOf("") }
    var showAggressiveWarning by remember { mutableStateOf(false) }
    var showGPUWatchConfirm by remember { mutableStateOf(false) }

    // User name
    var userName by remember { mutableStateOf(prefs.getString("user_name", "") ?: "") }

    // STARTUP VISIBILITY STATE
    // isVisible controls the staggered entrance of main menu items
    // It should only be true when setup is done
    var isVisible by remember { mutableStateOf(false) }

    var showGitHubDialog by remember { mutableStateOf(false) }
    var showEasterEgg by remember { mutableStateOf(false) }
    var showResourcesPanel by remember { mutableStateOf(false) }
    var showExternalLinkConfirm by remember { mutableStateOf(false) }
    var pendingExternalLink by remember { mutableStateOf("") }
    var pendingExternalLinkLabel by remember { mutableStateOf("") }
    var pendingExternalLinkDescription by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showSettingsSearch by remember { mutableStateOf(false) }
    var showHapticsPanel by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    var showEffects by remember { mutableStateOf(false) }
    var showColorCustomization by remember { mutableStateOf(false) }
    var showGradient by remember { mutableStateOf(false) }
    var showSystem by remember { mutableStateOf(false) }
    var showRendererPanel by remember { mutableStateOf(false) }
    var showIntegrationInfoDialog by remember { mutableStateOf(false) }
    var integrationInfoTitle by remember { mutableStateOf("") }
    var integrationInfoBody by remember { mutableStateOf("") }
    var integrationInfoCopyText by remember { mutableStateOf<String?>(null) }
    var integrationInfoGuideUrl by remember { mutableStateOf<String?>(null) }
    var showDeveloper by remember { mutableStateOf(false) }
    var showParticles by remember { mutableStateOf(false) }
    var showParticlesAppearance by remember { mutableStateOf(false) }
    var showParticlesMotion by remember { mutableStateOf(false) }
    var showParticlesPerformance by remember { mutableStateOf(false) }
    var showMatrixSettings by remember { mutableStateOf(false) }
    var showMatrixAppearance by remember { mutableStateOf(false) }
    var showMatrixMotion by remember { mutableStateOf(false) }
    var showParticlesSettings by remember { mutableStateOf(false) }
    var showBackup by remember { mutableStateOf(false) }
    var showCrashLog by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }

    // ── Notifications panel ───────────────────────────────────────────────────
    var showNotifications by remember { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(prefs.getBoolean("notif_enabled", false)) }
    // 0=2 h, 1=4 h, 2=6 h, 3=12 h, 4=24 h
    var notifIntervalIndex by remember { mutableStateOf(prefs.getInt("notif_interval_idx", 2)) }
    // True once we've asked for the OS permission at least once
    var notifPermissionRequested by remember { mutableStateOf(prefs.getBoolean("notif_perm_requested", false)) }

    // Root backend (su) — first-class alternative to Shizuku. When true, the
    // renderer switches work even if Shizuku isn't running or authorized.
    var showRootAccessDialog by remember { mutableStateOf(false) }

    // Notification permission — re-evaluated once on every ON_RESUME (e.g. returning
    // from the OS permission dialog), not on every recomposition.
    // Previously ShizukuHelper.hasNotificationPermission(context) was called inline in
    // two composition call-sites, meaning every recomposition of GamaUI triggered a
    // JNI checkSelfPermission call. DisposableEffect + LifecycleEventObserver is the
    // idiomatic way to run a side-effect tied to a specific lifecycle event.
    val hasNotifPermission = remember { mutableStateOf(ShizukuHelper.hasNotificationPermission(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasNotifPermission.value = ShizukuHelper.hasNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Back handlers
    BackHandler(enabled = showSuccessDialog) {
        if (!rendererSwitching) {
            performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
            showSuccessDialog = false
        }
    }

    BackHandler(enabled = showRootAccessDialog) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showRootAccessDialog = false
        pendingRendererSwitch = null
        pendingRendererName = ""
    }

    // (DeveloperMenuDialog BackHandler removed — showDeveloperMenu was dead state)

    BackHandler(enabled = showDeveloper) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showDeveloper = false
    }

    BackHandler(enabled = showShizukuHelp) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showShizukuHelp = false
    }

    // ── Appearance sub-panels ─────────────────────────────────────────────────

    BackHandler(enabled = showColorCustomization && !showGradient) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showColorCustomization = false
    }

    BackHandler(enabled = showGradient) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showGradient = false
    }

    BackHandler(enabled = showParticlesAppearance) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showParticlesAppearance = false
        showParticlesSettings = true
    }

    BackHandler(enabled = showParticlesMotion) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showParticlesMotion = false
        showParticlesSettings = true
    }

    BackHandler(enabled = showParticlesPerformance) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showParticlesPerformance = false
        showParticlesSettings = true
    }
    BackHandler(enabled = showMatrixAppearance) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showMatrixAppearance = false
    }
    BackHandler(enabled = showMatrixMotion) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showMatrixMotion = false
    }
    BackHandler(enabled = showMatrixSettings) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showMatrixSettings = false
    }
    BackHandler(enabled = showParticlesSettings) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showParticlesSettings = false
    }

    BackHandler(enabled = showParticles && !showParticlesAppearance && !showParticlesMotion && !showParticlesPerformance && !showMatrixSettings && !showMatrixAppearance && !showMatrixMotion && !showParticlesSettings) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showParticles = false
    }

    BackHandler(enabled = showEffects && !showParticles) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showEffects = false
    }

    BackHandler(enabled = showAppearance) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showAppearance = false
    }

    // ── System sub-panels ─────────────────────────────────────────────────────

    BackHandler(enabled = showNotifications) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showNotifications = false
    }

    BackHandler(enabled = showBackup) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showBackup = false
    }

    BackHandler(enabled = showCrashLog) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showCrashLog = false
    }

    BackHandler(enabled = showLanguage) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showLanguage = false
    }

    BackHandler(enabled = showSystem && !showNotifications && !showBackup && !showCrashLog && !showLanguage) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showSystem = false
    }

    // ── Renderer ──────────────────────────────────────────────────────────────

    BackHandler(enabled = showRendererPanel) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showRendererPanel = false
    }

    // ── Top-level Settings ────────────────────────────────────────────────────

    BackHandler(enabled = showSettingsSearch) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showSettingsSearch = false
    }

    BackHandler(enabled = showHapticsPanel) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showHapticsPanel = false
    }

    BackHandler(enabled = showSettings && !showSettingsSearch && !showHapticsPanel && !showAppearance && !showColorCustomization && !showGradient && !showEffects && !showParticles && !showSystem && !showNotifications && !showBackup && !showCrashLog && !showRendererPanel && !showLanguage && !showHapticsPanel) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showSettings = false
        showSettingsSearch = false
    }

    BackHandler(enabled = showWarningDialog) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showWarningDialog = false
        pendingRendererSwitch = null
    }

    BackHandler(enabled = showGitHubDialog) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showGitHubDialog = false
    }

    BackHandler(enabled = showEasterEgg) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showEasterEgg = false
    }

    BackHandler(enabled = showResourcesPanel) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showWarningDialog = false
        showResourcesPanel = false
    }

    BackHandler(enabled = showExternalLinkConfirm) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showExternalLinkConfirm = false
    }

    BackHandler(enabled = showVerbosePanel) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showVerbosePanel = false
    }

    BackHandler(enabled = showAggressiveWarning) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showAggressiveWarning = false
    }

    BackHandler(enabled = showGPUWatchConfirm) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showGPUWatchConfirm = false
    }

    val systemInDarkTheme = isSystemInDarkTheme()
    // Default animation level to 0 (Normal) — full quality animations
    var animationLevel by remember { mutableStateOf(prefs.getInt("animation_level", 0)) }
    var animationSpeed by remember { mutableStateOf(prefs.getInt("animation_speed", 1)) }
    var gradientEnabled by remember { mutableStateOf(prefs.getBoolean("gradient_enabled", true)) }
    var blurEnabled by remember { mutableStateOf(prefs.getBoolean("blur_enabled", true)) }
    var particlesEnabled by remember { mutableStateOf(prefs.getBoolean("particles_enabled", true)) }
    var particleSpeed by remember {
        mutableStateOf(
            prefs.getInt(
                "particle_speed",
                1
            )
        )
    } // 0=low, 1=medium, 2=high (default: medium)
    var particleParallaxEnabled by remember { mutableStateOf(prefs.getBoolean("particle_parallax_enabled", true)) }
    var particleParallaxSensitivity by remember {
        mutableStateOf(
            prefs.getInt(
                "particle_parallax_sensitivity",
                0
            )
        )
    } // 0=low(0.15), 1=medium(0.3), 2=high(0.5)
    var particleStarMode by remember {
        mutableStateOf(
            prefs.getBoolean(
                "particle_star_mode",
                false
            )
        )
    } // New: star mode toggle
    var particleTimeMode by remember {
        mutableStateOf(
            prefs.getBoolean(
                "particle_time_mode",
                true
            )
        )
    } // Time-based sun & moon system
    var timeOffsetHours by remember {
        mutableStateOf(
            prefs.getFloat(
                "time_offset_hours",
                0f
            )
        )
    } // Developer: time offset
    var particleCount by remember {
        mutableStateOf(
            prefs.getInt(
                "particle_count",
                0
            )
        )
    } // 0=low(75), 1=medium(150), 2=high(300), 3=custom (default: low)
    var particleCountCustom by remember { mutableStateOf(prefs.getInt("particle_count_custom", 150).toString()) }
    var matrixMode by remember { mutableStateOf(prefs.getBoolean("matrix_mode", true)) }
    var matrixSpeed by remember { mutableStateOf(prefs.getInt("matrix_speed", 1)) }          // 0=slow 1=medium 2=fast
    var matrixDensity by remember {
        mutableStateOf(
            prefs.getInt(
                "matrix_density",
                1
            )
        )
    }        // 0=sparse 1=medium 2=dense
    var matrixFontSize by remember {
        mutableStateOf(
            prefs.getInt(
                "matrix_font_size",
                1
            )
        )
    }      // 0=small 1=medium 2=large
    var matrixFadeLength by remember {
        mutableStateOf(
            prefs.getInt(
                "matrix_fade_length",
                1
            )
        )
    }    // 0=short 1=medium 2=full
    var matrixBgAlpha by remember { mutableStateOf(prefs.getFloat("matrix_bg_alpha", 0f)) }    // 0=transparent 1=black
    var themePreference by remember {
        mutableStateOf(
            if (prefs.contains("theme_preference")) prefs.getInt("theme_preference", 0)
            // Migrate installs that only had the historical OLED toggle. An
            // explicit theme selection always wins over legacy state.
            else if (prefs.getBoolean("oled_mode", false)) 1 else 0
        )
    }
    val legacyOledStatePresent = remember { prefs.contains("oled_mode") }
    val legacyOledMigrationNeeded = remember {
        !prefs.contains("theme_preference") && prefs.getBoolean("oled_mode", false)
    }
    LaunchedEffect(legacyOledStatePresent) {
        if (legacyOledStatePresent) {
            prefs.edit().apply {
                if (legacyOledMigrationNeeded) putInt("theme_preference", themePreference)
                remove("oled_mode")
                remove("oled_accent_color")
                remove("use_dynamic_color_oled")
            }.apply()
        }
    }

    // New settings
    // Replaced separate buttonSize with shared uiScale
    var uiScale by remember { mutableStateOf(prefs.getInt("ui_scale", 1)) } // 0=75%, 1=100%, 2=125%
    var verboseMode by remember { mutableStateOf(prefs.getBoolean("verbose_mode", false)) }
    var aggressiveMode by remember { mutableStateOf(prefs.getBoolean("aggressive_mode", false)) }
    var killLauncher by remember { mutableStateOf(prefs.getBoolean("kill_launcher", false)) }
    var killKeyboard by remember { mutableStateOf(prefs.getBoolean("kill_keyboard", false)) }
    var showGpuWatchButton by remember { mutableStateOf(prefs.getBoolean("show_gpuwatch_button", false)) }
    var staggerEnabled by remember { mutableStateOf(prefs.getBoolean("stagger_enabled", true)) }
    var backButtonAvoidanceEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                "back_button_avoidance_enabled",
                true
            )
        )
    }
    var backButtonInversed by remember { mutableStateOf(prefs.getBoolean("back_button_inversed", false)) }
    var floatingLeftX by remember { mutableStateOf(prefs.getFloat("floating_left_x", 0.30f).coerceIn(0.16f, 0.84f)) }
    var floatingLeftY by remember { mutableStateOf(prefs.getFloat("floating_left_y", 0.94f).coerceIn(0.50f, 0.96f)) }
    var floatingRightX by remember { mutableStateOf(prefs.getFloat("floating_right_x", 0.70f).coerceIn(0.16f, 0.84f)) }
    var floatingRightY by remember { mutableStateOf(prefs.getFloat("floating_right_y", 0.94f).coerceIn(0.50f, 0.96f)) }
    var landscapeFloatingLeftX by remember { mutableStateOf(prefs.getFloat("landscape_floating_left_x", 0.12f).coerceIn(0.04f, 0.96f)) }
    var landscapeFloatingLeftY by remember { mutableStateOf(prefs.getFloat("landscape_floating_left_y", 0.88f).coerceIn(0.50f, 0.96f)) }
    var landscapeFloatingRightX by remember { mutableStateOf(prefs.getFloat("landscape_floating_right_x", 0.88f).coerceIn(0.04f, 0.96f)) }
    var landscapeFloatingRightY by remember { mutableStateOf(prefs.getFloat("landscape_floating_right_y", 0.88f).coerceIn(0.50f, 0.96f)) }
    var settingsButtonX by remember { mutableStateOf(prefs.getFloat("settings_button_x", 0.84f).coerceIn(0.16f, 0.84f)) }
    var settingsButtonY by remember { mutableStateOf(prefs.getFloat("settings_button_y", 0.94f).coerceIn(0.50f, 0.96f)) }
    var landscapeSettingsButtonX by remember { mutableStateOf(prefs.getFloat("landscape_settings_button_x", 0.06f).coerceIn(0.04f, 0.96f)) }
    var landscapeSettingsButtonY by remember { mutableStateOf(prefs.getFloat("landscape_settings_button_y", 0.94f).coerceIn(0.50f, 0.96f)) }
    var shadowsEnabled by remember { mutableStateOf(prefs.getBoolean("shadows_enabled", true)) }
    var hapticsEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                GamaHaptics.PREF_ENABLED,
                GamaHaptics.DEFAULT_ENABLED
            )
        )
    }
    var hapticsRegularEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                GamaHaptics.PREF_REGULAR_ENABLED,
                GamaHaptics.DEFAULT_REGULAR_ENABLED
            )
        )
    }
    var hapticsHoldEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                GamaHaptics.PREF_HOLD_ENABLED,
                GamaHaptics.DEFAULT_HOLD_ENABLED
            )
        )
    }
    var hapticsRendererEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                GamaHaptics.PREF_RENDERER_ENABLED,
                GamaHaptics.DEFAULT_RENDERER_ENABLED
            )
        )
    }
    var hapticsLanguageEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                GamaHaptics.PREF_LANGUAGE_ENABLED,
                GamaHaptics.DEFAULT_LANGUAGE_ENABLED
            )
        )
    }
    var hapticsBounceEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                GamaHaptics.PREF_BOUNCE_ENABLED,
                GamaHaptics.DEFAULT_BOUNCE_ENABLED
            )
        )
    }
    var hapticsReturnEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                GamaHaptics.PREF_BOUNCE_RETURN_ENABLED,
                GamaHaptics.DEFAULT_BOUNCE_RETURN_ENABLED
            )
        )
    }
    var hapticsRegularStrength by remember {
        mutableStateOf(
            prefs.getInt(
                GamaHaptics.PREF_REGULAR_STRENGTH,
                GamaHaptics.DEFAULT_REGULAR_STRENGTH
            )
        )
    }
    var hapticsHoldStrength by remember {
        mutableStateOf(
            prefs.getInt(
                GamaHaptics.PREF_HOLD_STRENGTH,
                GamaHaptics.DEFAULT_HOLD_STRENGTH
            )
        )
    }
    var hapticsRendererStrength by remember {
        mutableStateOf(
            prefs.getInt(
                GamaHaptics.PREF_RENDERER_STRENGTH,
                GamaHaptics.DEFAULT_RENDERER_STRENGTH
            )
        )
    }
    var hapticsLanguageStrength by remember {
        mutableStateOf(
            prefs.getInt(
                GamaHaptics.PREF_LANGUAGE_STRENGTH,
                GamaHaptics.DEFAULT_LANGUAGE_STRENGTH
            )
        )
    }
    var hapticsBounceStrength by remember {
        mutableStateOf(
            prefs.getInt(
                GamaHaptics.PREF_BOUNCE_STRENGTH,
                GamaHaptics.DEFAULT_BOUNCE_STRENGTH
            )
        )
    }
    var hapticsBounceReturnStrength by remember {
        mutableStateOf(
            prefs.getInt(
                GamaHaptics.PREF_BOUNCE_RETURN_STRENGTH,
                GamaHaptics.DEFAULT_BOUNCE_RETURN_STRENGTH
            )
        )
    }
    var particleNativeRefreshRate by remember {
        mutableStateOf(
            prefs.getBoolean(
                "particle_native_refresh_rate",
                false
            )
        )
    }
    var particleQuarterRefreshRate by remember {
        mutableStateOf(
            prefs.getBoolean(
                "particle_quarter_refresh_rate",
                false
            )
        )
    }
    // Using SnapshotStateList for instant updates
    val excludedAppsList = remember {
        mutableStateListOf<String>().apply {
            addAll(
                prefs.getStringSet("excluded_apps", setOf()) ?: emptySet()
            )
        }
    }

    // Dynamic colors
    var useDynamicColor by remember { mutableStateOf(prefs.getBoolean("use_dynamic_color", true)) }
    var advancedColorPicker by remember { mutableStateOf(prefs.getBoolean("advanced_color_picker", false)) }
    var customAccentColor by remember {
        mutableStateOf(sanitizeAccentColorForTheme(Color(prefs.getInt("custom_accent", 0xFF4895EF.toInt())), false))
    }
    var customGradientStart by remember {
        mutableStateOf(
            Color(
                prefs.getInt(
                    "custom_gradient_start",
                    0xFF0A2540.toInt()
                )
            )
        )
    }
    var customGradientEnd by remember { mutableStateOf(Color(prefs.getInt("custom_gradient_end", 0xFF000000.toInt()))) }

    // Global back behavior toggle
    var dismissOnClickOutside by remember { mutableStateOf(prefs.getBoolean("dismiss_on_click_outside", true)) }

    // Aggressive mode confirmation
    var aggressiveModeConfirmed by remember { mutableStateOf(false) }


    // remember(timeOffsetHours): Calendar.getInstance() is called once per offset change,
    // not on every recomposition (which previously happened dozens of times per second).
    val timeAwareHourForTheme = remember(timeOffsetHours) {
        val raw = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        ((raw + timeOffsetHours.toInt()).let { h -> ((h % 24) + 24) % 24 })
    }
    val isDarkTheme = when (themePreference) {
        1 -> true
        2 -> false
        else -> systemInDarkTheme // Auto: always follow the OS dark/light mode setting
    }

    // In GAMA, dark mode is OLED mode. There is no separate dark-grey theme anymore.
    // This also makes Auto follow the system: if the phone is in dark mode, GAMA uses pure black.
    // Dark is the single source of truth for the OLED palette. Older installs
    // may still carry oled_mode=true, but it must never override an explicit
    // Light theme selection.
    val effectiveOledMode = isDarkTheme

    LaunchedEffect(effectiveOledMode) {
        val sanitized = sanitizeAccentColorForTheme(customAccentColor, effectiveOledMode)
        if (sanitized.toArgb() != customAccentColor.toArgb()) {
            customAccentColor = sanitized
            prefs.edit().putInt("custom_accent", sanitized.toArgb()).apply()
        }
    }

    // Theme changes should feel slow and premium, but not like the whole app is
    // fighting the GPU. Keep one shared transition window and let different UI
    // channels ease at slightly different durations instead of forcing a hard snap.
    var themeModeSwitchInProgress by remember { mutableStateOf(false) }
    LaunchedEffect(effectiveOledMode) {
        themeModeSwitchInProgress = true
        delay(900)
        themeModeSwitchInProgress = false
    }

    // remember(…): context.getColor() is a JNI call — memoize to avoid per-recomposition overhead.
    val dynamicAccent = remember(useDynamicColor, isDarkTheme, customAccentColor) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useDynamicColor) {
            if (isDarkTheme) {
                Color(context.getColor(android.R.color.system_accent1_400))
            } else {
                Color(context.getColor(android.R.color.system_accent1_600))
            }
        } else {
            customAccentColor
        }
    }

    // Single accent source for the whole app.
    // Important: since GAMA now treats dark mode as OLED mode, the old separate
    // The dark palette intentionally uses the same accent as light mode.
    // This keeps cards, particles, Matrix rain, and the background gradient synced.
    val appAccent = dynamicAccent

    // Absolute visual accent used by background effects.
    // When Dynamic Color is OFF, this is the manual ACCENT COLOR picker value.
    // We pass this directly to Stars/Particles/Matrix instead of routing through
    // animated theme colors, so the background effects cannot stay stuck on blue/green.
    val effectsAccent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useDynamicColor) {
        dynamicAccent
    } else {
        customAccentColor
    }

    val dynamicGradientStart = remember(useDynamicColor, isDarkTheme, effectsAccent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useDynamicColor) {
            if (isDarkTheme) {
                Color(context.getColor(android.R.color.system_accent1_700))
            } else {
                Color(context.getColor(android.R.color.system_accent1_400))
            }
        } else {
            // Dynamic Color OFF: derive the gradient from ACCENT COLOR instead of
            // using stale saved gradient colors that can drift away from the accent.
            if (isDarkTheme) lerp(effectsAccent, Color.Black, 0.68f)
            else lerp(effectsAccent, Color.White, 0.22f)
        }
    }

    val dynamicGradientEnd = remember(useDynamicColor, isDarkTheme, effectsAccent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useDynamicColor) {
            if (isDarkTheme) {
                Color(0xFF000000)
            } else {
                Color(context.getColor(android.R.color.system_neutral1_50))
            }
        } else {
            // Dynamic Color OFF: keep the lower gradient tied to the same accent,
            // just much softer so the UI stays readable.
            if (isDarkTheme) Color.Black
            else lerp(effectsAccent, Color.White, 0.90f)
        }
    }

    // ── Matrix/stars visual colors — driven by ACCENT COLOR / Dynamic Color ──
    // Important: these are animated colors, NOT keys. Changing ACCENT COLOR should
    // smoothly repaint existing Matrix/Stars particles instead of disposing and
    // respawning the overlays.
    val targetEffectsAccent = effectsAccent

    val targetMatrixHeadColor = remember(useDynamicColor, isDarkTheme, effectsAccent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useDynamicColor) {
            Color(context.getColor(android.R.color.system_accent1_100))
        } else {
            lerp(effectsAccent, Color.White, 0.72f)
        }
    }
    val targetMatrixRainColor = remember(useDynamicColor, isDarkTheme, effectsAccent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useDynamicColor) {
            if (isDarkTheme) Color(context.getColor(android.R.color.system_accent1_300))
            else Color(context.getColor(android.R.color.system_accent1_600))
        } else {
            effectsAccent
        }
    }
    val targetMatrixTrailColor = remember(useDynamicColor, isDarkTheme, effectsAccent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useDynamicColor) {
            Color(context.getColor(android.R.color.system_accent1_900))
        } else {
            lerp(effectsAccent, Color.Black, 0.70f)
        }
    }

    val animatedEffectsAccent by animateColorAsState(
        targetValue = targetEffectsAccent,
        animationSpec = if (animationLevel == 2) snap() else tween(
            durationMillis = if (themeModeSwitchInProgress) 820 else 340,
            easing = MotionTokens.Easing.velvet
        ),
        label = "effects_accent_anim"
    )
    val matrixHeadColor by animateColorAsState(
        targetValue = targetMatrixHeadColor,
        animationSpec = if (animationLevel == 2) snap() else tween(
            durationMillis = if (themeModeSwitchInProgress) 820 else 340,
            easing = MotionTokens.Easing.velvet
        ),
        label = "matrix_head_color_anim"
    )
    val matrixRainColor by animateColorAsState(
        targetValue = targetMatrixRainColor,
        animationSpec = if (animationLevel == 2) snap() else tween(
            durationMillis = if (themeModeSwitchInProgress) 820 else 340,
            easing = MotionTokens.Easing.velvet
        ),
        label = "matrix_rain_color_anim"
    )
    val matrixTrailColor by animateColorAsState(
        targetValue = targetMatrixTrailColor,
        animationSpec = if (animationLevel == 2) snap() else tween(
            durationMillis = if (themeModeSwitchInProgress) 820 else 340,
            easing = MotionTokens.Easing.velvet
        ),
        label = "matrix_trail_color_anim"
    )
    // Determine target colors.
    // Dark mode is OLED mode now: no dark-grey card theme, only pure black.
    val baseColors = if (effectiveOledMode) {
        ThemeColors.oledMode(appAccent)
    } else {
        ThemeColors.light(appAccent, dynamicGradientStart, dynamicGradientEnd)
    }

    // Use base colors directly - no color changing for success dialog
    val targetColors = baseColors

    // Synchronized color animations — all channels transition together so the UI
    // updates in perfect sync on every theme/accent/oled/dynamic-color change.
    // The accent color picker produces a natural wave feel just from the way
    // Compose recomposes (accent-derived values like border update one frame later
    // naturally), without needing artificial delays that desync fast toggles.
    val themeColorAnimSpec: AnimationSpec<Color> = when {
        animationLevel == 2 -> snap()
        themeModeSwitchInProgress -> tween(durationMillis = 780, easing = MotionTokens.Easing.velvet)
        animationLevel == 1 -> tween(durationMillis = 180, easing = MotionTokens.Easing.emphasized)
        else -> tween(durationMillis = 420, easing = MotionTokens.Easing.velvet)
    }

    val themeTextAnimSpec: AnimationSpec<Color> = when {
        animationLevel == 2 -> snap()
        themeModeSwitchInProgress -> tween(durationMillis = 560, easing = MotionTokens.Easing.velvet)
        animationLevel == 1 -> tween(durationMillis = 160, easing = MotionTokens.Easing.emphasized)
        else -> tween(durationMillis = 320, easing = MotionTokens.Easing.velvet)
    }

    val themeBackgroundAnimSpec: AnimationSpec<Color> = when {
        animationLevel == 2 -> snap()
        themeModeSwitchInProgress -> tween(durationMillis = 900, easing = MotionTokens.Easing.velvet)
        animationLevel == 1 -> tween(durationMillis = 200, easing = MotionTokens.Easing.emphasized)
        else -> tween(durationMillis = 360, easing = MotionTokens.Easing.emphasized)
    }

    val animatedAccent by animateColorAsState(
        targetValue = targetColors.primaryAccent,
        animationSpec = themeColorAnimSpec,
        label = "accent_anim"
    )
    val animatedBorder by animateColorAsState(
        targetValue = targetColors.border,
        animationSpec = themeColorAnimSpec,
        label = "border_anim"
    )
    val animatedTextPrimary by animateColorAsState(
        targetValue = targetColors.textPrimary,
        animationSpec = themeTextAnimSpec,
        label = "text_primary_anim"
    )
    val animatedTextSecondary by animateColorAsState(
        targetValue = targetColors.textSecondary,
        animationSpec = themeTextAnimSpec,
        label = "text_secondary_anim"
    )
    val animatedCardBackground by animateColorAsState(
        targetValue = targetColors.cardBackground,
        animationSpec = themeColorAnimSpec,
        label = "card_bg_anim"
    )
    val animatedBackgroundColor by animateColorAsState(
        targetValue = targetColors.background,
        animationSpec = themeBackgroundAnimSpec,
        label = "bg_anim"
    )
    val animatedGradientStart by animateColorAsState(
        targetValue = targetColors.gradientStart,
        animationSpec = themeBackgroundAnimSpec,
        label = "grad_start_anim"
    )
    val animatedGradientEnd by animateColorAsState(
        targetValue = targetColors.gradientEnd,
        animationSpec = themeBackgroundAnimSpec,
        label = "grad_end_anim"
    )

    val colors = targetColors.copy(
        background = animatedBackgroundColor,
        cardBackground = animatedCardBackground,
        primaryAccent = animatedAccent,
        gradientStart = animatedGradientStart,
        gradientEnd = animatedGradientEnd,
        textPrimary = animatedTextPrimary,
        textSecondary = animatedTextSecondary,
        border = animatedBorder
    )

    // Keep every card synced to the active theme colors.
    val cardBackground = colors.cardBackground

    fun savePreferences() {
        // Capture the excluded-apps list as an immutable snapshot *before* handing
        // off to the IO thread.  SnapshotStateList is not thread-safe: reading it
        // from a background thread while the UI thread mutates it can silently drop
        // or duplicate entries.  Calling toSet() here, on the main thread, produces
        // a stable copy that is safe to hand across the thread boundary.
        val excludedAppsSnapshot = excludedAppsList.toSet()

        // Snapshot every other Compose state value for the same reason: these objects
        // must only be read from the main thread, so we capture primitives/stable
        // values here before the coroutine crosses the thread boundary.
        val snapAnimLevel = animationLevel
        val snapGradient = gradientEnabled
        val snapParticles = particlesEnabled
        val snapParticleSpeed = particleSpeed
        val snapParallax = particleParallaxEnabled
        val snapParallaxSens = particleParallaxSensitivity
        val snapStarMode = particleStarMode
        val snapTimeMode = particleTimeMode
        val snapTimeOffset = timeOffsetHours
        val snapParticleCount = particleCount
        val snapParticleCustom = particleCountCustom.toIntOrNull() ?: 150
        val snapBlur = blurEnabled
        val snapThemePref = themePreference
        val snapDynColor = useDynamicColor
        val snapAdvColorPicker = advancedColorPicker
        val snapAccent = customAccentColor.toArgb()
        val snapGradStart = customGradientStart.toArgb()
        val snapGradEnd = customGradientEnd.toArgb()
        val snapUiScale = uiScale
        val snapVerbose = verboseMode
        val snapAggressive = aggressiveMode
        val snapKillLauncher = killLauncher
        val snapKillKeyboard = killKeyboard
        val snapShowGpuWatch = showGpuWatchButton
        val snapStagger = staggerEnabled
        val snapBackButtonAvoidance = backButtonAvoidanceEnabled
        val snapBackButtonInversed = backButtonInversed
        val snapFloatingLeftX = floatingLeftX
        val snapFloatingLeftY = floatingLeftY
        val snapFloatingRightX = floatingRightX
        val snapFloatingRightY = floatingRightY
        val snapLandscapeFloatingLeftX = landscapeFloatingLeftX
        val snapLandscapeFloatingLeftY = landscapeFloatingLeftY
        val snapLandscapeFloatingRightX = landscapeFloatingRightX
        val snapLandscapeFloatingRightY = landscapeFloatingRightY
        val snapSettingsButtonX = settingsButtonX
        val snapSettingsButtonY = settingsButtonY
        val snapLandscapeSettingsButtonX = landscapeSettingsButtonX
        val snapLandscapeSettingsButtonY = landscapeSettingsButtonY
        val snapShadows = shadowsEnabled
        val snapHapticsEnabled = hapticsEnabled
        val snapHapticsRegularEnabled = hapticsRegularEnabled
        val snapHapticsHoldEnabled = hapticsHoldEnabled
        val snapHapticsRendererEnabled = hapticsRendererEnabled
        val snapHapticsLanguageEnabled = hapticsLanguageEnabled
        val snapHapticsBounceEnabled = hapticsBounceEnabled
        val snapHapticsReturnEnabled = hapticsReturnEnabled
        val snapHapticsRegular = hapticsRegularStrength
        val snapHapticsHold = hapticsHoldStrength
        val snapHapticsRenderer = hapticsRendererStrength
        val snapHapticsLanguage = hapticsLanguageStrength
        val snapHapticsBounce = hapticsBounceStrength
        val snapHapticsBounceReturn = hapticsBounceReturnStrength
        val snapParticleNativeRefresh = particleNativeRefreshRate
        val snapParticleQuarterRefresh = particleQuarterRefreshRate
        val snapMatrixMode = matrixMode
        val snapMatrixSpeed = matrixSpeed
        val snapMatrixDensity = matrixDensity
        val snapMatrixFontSize = matrixFontSize
        val snapMatrixFadeLength = matrixFadeLength
        val snapMatrixBgAlpha = matrixBgAlpha
        val snapDismissOutside = dismissOnClickOutside
        val snapNotifEnabled = notificationsEnabled
        val snapNotifInterval = notifIntervalIndex
        val snapNotifPermReq = notifPermissionRequested

        scope.launch(Dispatchers.IO) {
            prefs.edit().apply {
                putInt("animation_level", snapAnimLevel)
                putBoolean("gradient_enabled", snapGradient)
                putBoolean("particles_enabled", snapParticles)
                putInt("particle_speed", snapParticleSpeed)
                putBoolean("particle_parallax_enabled", snapParallax)
                putInt("particle_parallax_sensitivity", snapParallaxSens)
                putBoolean("particle_star_mode", snapStarMode)
                putBoolean("particle_time_mode", snapTimeMode)
                putFloat("time_offset_hours", snapTimeOffset)
                putInt("particle_count", snapParticleCount)
                putInt("particle_count_custom", snapParticleCustom)
                putBoolean("blur_enabled", snapBlur)
                putInt("theme_preference", snapThemePref)
                putBoolean("use_dynamic_color", snapDynColor)
                putBoolean("advanced_color_picker", snapAdvColorPicker)
                putInt("custom_accent", snapAccent)
                putInt("custom_gradient_start", snapGradStart)
                putInt("custom_gradient_end", snapGradEnd)
                putInt("ui_scale", snapUiScale)
                putBoolean("verbose_mode", snapVerbose)
                putBoolean("aggressive_mode", snapAggressive)
                putBoolean("kill_launcher", snapKillLauncher)
                putBoolean("kill_keyboard", snapKillKeyboard)
                putBoolean("show_gpuwatch_button", snapShowGpuWatch)
                putBoolean("stagger_enabled", snapStagger)
                putBoolean("back_button_avoidance_enabled", snapBackButtonAvoidance)
                putBoolean("back_button_inversed", snapBackButtonInversed)
                putFloat("floating_left_x", snapFloatingLeftX)
                putFloat("floating_left_y", snapFloatingLeftY)
                putFloat("floating_right_x", snapFloatingRightX)
                putFloat("floating_right_y", snapFloatingRightY)
                putFloat("landscape_floating_left_x", snapLandscapeFloatingLeftX)
                putFloat("landscape_floating_left_y", snapLandscapeFloatingLeftY)
                putFloat("landscape_floating_right_x", snapLandscapeFloatingRightX)
                putFloat("landscape_floating_right_y", snapLandscapeFloatingRightY)
                putFloat("settings_button_x", snapSettingsButtonX)
                putFloat("settings_button_y", snapSettingsButtonY)
                putFloat("landscape_settings_button_x", snapLandscapeSettingsButtonX)
                putFloat("landscape_settings_button_y", snapLandscapeSettingsButtonY)
                putBoolean("shadows_enabled", snapShadows)
                putBoolean(GamaHaptics.PREF_ENABLED, snapHapticsEnabled)
                putBoolean(GamaHaptics.PREF_REGULAR_ENABLED, snapHapticsRegularEnabled)
                putBoolean(GamaHaptics.PREF_HOLD_ENABLED, snapHapticsHoldEnabled)
                putBoolean(GamaHaptics.PREF_RENDERER_ENABLED, snapHapticsRendererEnabled)
                putBoolean(GamaHaptics.PREF_LANGUAGE_ENABLED, snapHapticsLanguageEnabled)
                putBoolean(GamaHaptics.PREF_BOUNCE_ENABLED, snapHapticsBounceEnabled)
                putBoolean(GamaHaptics.PREF_BOUNCE_RETURN_ENABLED, snapHapticsReturnEnabled)
                putInt(GamaHaptics.PREF_REGULAR_STRENGTH, snapHapticsRegular)
                putInt(GamaHaptics.PREF_HOLD_STRENGTH, snapHapticsHold)
                putInt(GamaHaptics.PREF_RENDERER_STRENGTH, snapHapticsRenderer)
                putInt(GamaHaptics.PREF_LANGUAGE_STRENGTH, snapHapticsLanguage)
                putInt(GamaHaptics.PREF_BOUNCE_STRENGTH, snapHapticsBounce)
                putInt(GamaHaptics.PREF_BOUNCE_RETURN_STRENGTH, snapHapticsBounceReturn)
                putBoolean("particle_native_refresh_rate", snapParticleNativeRefresh)
                putBoolean("particle_quarter_refresh_rate", snapParticleQuarterRefresh)
                putBoolean("matrix_native_refresh_rate", false)
                putBoolean("matrix_quarter_refresh_rate", false)
                putBoolean("matrix_mode", snapMatrixMode)
                putInt("matrix_speed", snapMatrixSpeed)
                putInt("matrix_density", snapMatrixDensity)
                putInt("matrix_font_size", snapMatrixFontSize)
                putInt("matrix_fade_length", snapMatrixFadeLength)
                putFloat("matrix_bg_alpha", snapMatrixBgAlpha)
                putStringSet("excluded_apps", excludedAppsSnapshot)
                putBoolean("dismiss_on_click_outside", snapDismissOutside)
                putBoolean("notif_enabled", snapNotifEnabled)
                putInt("notif_interval_idx", snapNotifInterval)
                putBoolean("notif_perm_requested", snapNotifPermReq)
                apply()
            }
        }
    }

    // ── Debounced save — for sliders that fire on every frame ────────────────
    // Toggles call savePreferences() directly (one-shot, cheap).
    // Sliders cancel any pending job and reschedule — only the final value
    // after the finger lifts is actually written to SharedPreferences.
    var pendingSaveJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun savePreferencesDebounced(delayMs: Long = 300L) {
        pendingSaveJob?.cancel()
        pendingSaveJob = scope.launch {
            delay(delayMs)
            savePreferences()
        }
    }

    fun closeAllPanelsForMainAction() {
        showWarningDialog = false
        showShizukuHelp = false
        showResourcesPanel = false
        showExternalLinkConfirm = false
        showGitHubDialog = false
        showIntegrationInfoDialog = false
        showSettings = false
        showSettingsSearch = false
        showHapticsPanel = false
        showAppearance = false
        showEffects = false
        showColorCustomization = false
        showGradient = false
        showSystem = false
        showRendererPanel = false
        showDeveloper = false
        showParticles = false
        showParticlesAppearance = false
        showParticlesMotion = false
        showParticlesPerformance = false
        showParticlesSettings = false
        showMatrixSettings = false
        showMatrixAppearance = false
        showMatrixMotion = false
        showNotifications = false
        showBackup = false
        showCrashLog = false
        showLanguage = false
        showEasterEgg = false
        showVerbosePanel = false
        showAggressiveWarning = false
        showGPUWatchConfirm = false
    }

    fun openMainPanelExclusive(open: () -> Unit) {
        closeAllPanelsForMainAction()
        open()
    }

    val animDuration = when (animationLevel) {
        0 -> 585
        1 -> 450
        else -> 0
    }

    // derivedStateOf ensures downstream recompositions only trigger when the boolean
    // *changes* (false→true or true→false), not on every recomposition that happens
    // to read one of the ~20 constituent state variables.
    val anyPanelOpen by remember {
        derivedStateOf {
            showWarningDialog || showGitHubDialog || showResourcesPanel || showExternalLinkConfirm ||
                    showSettings || showSettingsSearch || showHapticsPanel || showAppearance || showColorCustomization || showGradient ||
                    showSystem || showRendererPanel ||
                    showShizukuHelp || showSuccessDialog || showRootAccessDialog ||
                    showVerbosePanel || showAggressiveWarning || showGPUWatchConfirm ||
                    showDeveloper || showEasterEgg || showNotifications || showBackup || showCrashLog ||
                    showEffects || showParticles || showParticlesAppearance ||
                    showParticlesMotion || showParticlesPerformance || showMatrixSettings || showMatrixAppearance ||
                    showMatrixMotion || showParticlesSettings || showIntegrationInfoDialog
        }
    }

    val anyFullPanelOpen by remember {
        derivedStateOf {
            showWarningDialog || showGitHubDialog || showResourcesPanel ||
                    showSettings || showSettingsSearch || showHapticsPanel || showAppearance || showColorCustomization || showGradient ||
                    showSystem || showRendererPanel ||
                    showShizukuHelp || showSuccessDialog || showRootAccessDialog ||
                    showVerbosePanel || showAggressiveWarning || showGPUWatchConfirm ||
                    showDeveloper || showEasterEgg || showNotifications || showBackup || showCrashLog ||
                    showEffects || showParticles || showParticlesAppearance ||
                    showParticlesMotion || showParticlesPerformance || showMatrixSettings || showMatrixAppearance ||
                    showMatrixMotion || showParticlesSettings
        }
    }

    // Visual open-state lingers briefly after the boolean panel state flips false.
    // This lets the panel contents exit first, then the panel shell fade/scale out,
    // and only then lets the main menu unblur + bottom controls return. Without this,
    // the background starts clearing while the panel is still visibly closing.
    var visualAnyPanelOpen by remember { mutableStateOf(anyPanelOpen) }
    var visualAnyFullPanelOpen by remember { mutableStateOf(anyFullPanelOpen) }

    LaunchedEffect(anyPanelOpen, animationLevel) {
        if (anyPanelOpen) {
            visualAnyPanelOpen = true
        } else {
            if (animationLevel != 2) delay(135L)
            visualAnyPanelOpen = false
        }
    }

    LaunchedEffect(anyFullPanelOpen, animationLevel) {
        if (anyFullPanelOpen) {
            visualAnyFullPanelOpen = true
        } else {
            if (animationLevel != 2) delay(95L)
            visualAnyFullPanelOpen = false
        }
    }

    val currentVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
        } catch (_: Exception) {
            "—"
        }
    }

    // Gradient "Come Alive" Animation on Startup
    val gradientStartupAlpha = remember { Animatable(0f) }

    // Stronger breathing gradient loop.
    // The InfiniteTransition is only created (and only ticks) when the gradient
    // is actually visible and motion is enabled — otherwise the slot returns a
    // static 1f so disabled/reduced-motion modes never allocate or tick it.
    val breathingAlpha by if (gradientEnabled && appInForeground && animationLevel != 2) {
        val infiniteTransition = rememberInfiniteTransition(label = "breathing")
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(6000, easing = MotionTokens.Easing.silk),
                repeatMode = RepeatMode.Reverse
            ),
            label = "breathing_alpha"
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    LaunchedEffect(Unit) {
        // Startup Sequence: Elements appear with staggering
        // Show main UI immediately
        isVisible = true

        // Gradient ramps up slowly (0 -> 1 over 2000ms) — emphasizedDecelerate means
        // it rushes in quickly then gently settles, giving the UI a confident first impression.
        gradientStartupAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1800, easing = MotionTokens.Easing.emphasizedDecelerate)
        )
    }

    LaunchedEffect(Unit) {
        // A previous version left the saved Vulkan value in place when boot
        // restore exhausted its retries. That value is the desired renderer,
        // not the actual post-boot renderer. Normalize that known failure on
        // launch so existing installs stop displaying stale Vulkan.
        val recordedAction = RendererActionHistory.read(prefs)
        if (recordedAction?.source == "Boot restore" &&
            !recordedAction.success &&
            recordedAction.detail == "No privileged backend became ready after boot."
        ) {
            RendererState.recordBootRestoreUnavailable(prefs)
            currentRenderer = RendererState.getRenderer(prefs)
            lastSwitchTime = prefs.getLong(RendererState.PREF_LAST_SWITCH_TIME, lastSwitchTime)
        }

        val (initialRunning, initialPermission) = if (ShizukuBackend.installed(context) != null) {
            ShizukuHelper.awaitShizuku()
        } else {
            ShizukuHelper.checkBinder() to ShizukuHelper.checkPermission()
        }
        shizukuRunning = initialRunning
        shizukuPermissionGranted = initialPermission
        // Do not invoke `su` at startup: root managers can show an approval
        // dialog. The user explicitly requests root from RootAccessDialog.
        rootAvailable = ShizukuHelper.isRootAvailable()

        // ── Request notification permission once on first launch ─────────────
        if (!notifPermissionRequested && !ShizukuHelper.hasNotificationPermission(context)) {
            notifPermissionRequested = true
            prefs.edit().putBoolean("notif_perm_requested", true).apply()
            delay(1500) // Let the UI settle before the OS dialog appears
            onRequestNotificationPermission()
        }

        // Dismiss one-time button labels after 3 seconds
        if (showButtonLabels) {
            delay(3000)
            showButtonLabels = false
            prefs.edit().putBoolean("button_labels_shown", true).apply()
        }

        shizukuStatus = when {
            rootAvailable -> strings["main.root_ready"].ifEmpty { "Running with root access ✅" }
            shizukuRunning && shizukuPermissionGranted -> {
                if (userName.isNotEmpty()) strings["main.shizuku_ready_named"].replace("%s", userName)
                    .ifEmpty { "You're all set, $userName! ✅" } else if (shizukuBackendName == "Shizuku") {
                    strings["main.shizuku_ready"].ifEmpty { "Shizuku is running ✅" }
                } else strings["main.backend_ready"].ifEmpty { "%s is running ✅" }.replace("%s", shizukuBackendName)
            }

            shizukuRunning -> strings["main.shizuku_permission_needed"].ifEmpty { "Permission needed ⚠️" }
            else -> if (shizukuBackendName == "Shizuku") {
                strings["main.shizuku_not_running"].ifEmpty { "Shizuku isn't running ❌" }
            } else strings["main.backend_not_running"].ifEmpty { "%s isn't running ❌" }.replace("%s", shizukuBackendName)
        }

        scope.launch {
            // ── Renderer detection ────────────────────────────────────────────
            // Priority order:
            //   1. If Shizuku is available, ask it directly — most authoritative.
            //   2. Otherwise use reboot detection to make a reliable offline guess.
            //
            // Reboot detection uses last_switch_uptime (elapsedRealtime at switch
            // time) rather than last_switch_time (wall-clock). elapsedRealtime
            // resets to ~0 on every boot, so if the stored uptime is greater than
            // the current elapsedRealtime the device has definitely rebooted and
            // the runtime prop has been cleared. Wall-clock comparison was broken
            // for any switch older than the current uptime (e.g. switched 16 days
            // ago — bootTimeMs is always > lastSwitchMs even with no reboot).
            if (shizukuRunning && shizukuPermissionGranted || rootAvailable) {
                // Shizuku is live — get the ground truth directly from the system.
                val detectedRenderer = ShizukuHelper.getCurrentRenderer()
                when (detectedRenderer) {
                    "Vulkan", "OpenGL" -> {
                        currentRenderer = detectedRenderer
                        if (detectedRenderer == RendererState.RENDERER_OPENGL) {
                            RendererState.reconcileRebootReset(prefs)
                        }
                        RendererState.recordObservedRenderer(prefs, detectedRenderer)
                        lastSwitchTime = prefs.getLong(RendererState.PREF_LAST_SWITCH_TIME, lastSwitchTime)
                    }

                    "Default", "Not Set" -> {
                        // Prop is empty — post-reboot state means OpenGL default
                        currentRenderer = "OpenGL"
                        RendererState.reconcileRebootReset(prefs)
                        RendererState.recordObservedRenderer(prefs, RendererState.RENDERER_OPENGL)
                        lastSwitchTime = prefs.getLong(RendererState.PREF_LAST_SWITCH_TIME, lastSwitchTime)
                    }
                    // "Unknown" / error: Shizuku command failed — keep existing value.
                }
            } else {
                // Shizuku unavailable — use offline reboot detection (RendererState:
                // boot-time stamp, uptime fallback, legacy wall-clock fallback).
                // Only correct to OpenGL (and write prefs) if we are certain a
                // reboot occurred; otherwise trust last_renderer as-is.
                if (RendererState.reconcileRebootReset(prefs)) {
                    currentRenderer = "OpenGL"
                    lastSwitchTime = prefs.getLong(RendererState.PREF_LAST_SWITCH_TIME, lastSwitchTime)
                }
                // No reboot detected → leave currentRenderer unchanged (already
                // initialised from last_renderer pref at the top of GamaUI).
            }
            rendererLoading = false // Done — stop skeleton shimmer regardless of outcome
        }

    }

    LaunchedEffect(notificationsEnabled) {
        OpenGLReminderScheduler.sync(context, notificationsEnabled)
    }

    // When a renderer switch is in progress, commandOutput being set is the real
    // completion signal from ShizukuHelper — the shell command has finished.
    // Flip rendererSwitching off so SuccessDialog transitions from its spinner
    // to the final confirmation message.
    LaunchedEffect(commandOutput) {
        if (rendererSwitching && commandOutput.isNotEmpty()) {
            rendererSwitching = false
            if (commandOutput.startsWith("Error", ignoreCase = true) ||
                commandOutput.contains("FAILED", ignoreCase = true) ||
                commandOutput.contains("permission denied", ignoreCase = true)
            ) {
                successDialogMessage = strings["main.renderer_switch_failed"].ifEmpty {
                    "Renderer switch failed — please try again."
                }
                currentRenderer = prefs.getString("last_renderer", "OpenGL") ?: "OpenGL"
                RendererActionHistory.record(prefs, "GAMA", activeRendererTarget, false, commandOutput)
            } else if (commandOutput.contains("applied, but", ignoreCase = true)) {
                // setprop may have succeeded, but an unreadable read-back is not
                // enough to claim a durable switch or schedule reboot restore.
                successDialogMessage = strings["main.renderer_applied_unverified"].ifEmpty {
                    "Renderer applied, but could not be verified."
                }
                RendererActionHistory.record(prefs, "GAMA", activeRendererTarget, false, commandOutput)
            } else {
                currentRenderer = RendererState.getRenderer(prefs)
                lastSwitchTime = prefs.getLong(RendererState.PREF_LAST_SWITCH_TIME, lastSwitchTime)
                // RendererController already records the authoritative action;
                // the UI only reflects the completed transaction here.
            }
            lastRendererAction = RendererActionHistory.read(prefs)
        }
    }

    // UI Scale Multiplier Calculation (removed 50% for better UX)
    val uiScaleMultiplier = when (uiScale) {
        0 -> 0.75f // 75%
        2 -> 1.25f // 125%
        else -> 1f // 100%
    }

    // UI scale must animate the actual layout, not zoom the whole root layer.
    // Root-layer zoom pushed edge buttons off-screen because they were scaled
    // around the screen center. Animating the density keeps every anchor honest:
    // the < button stays on-screen while text/cards ease from old size to new size.
    val animatedUiScale by animateFloatAsState(
        targetValue = uiScaleMultiplier,
        animationSpec = when (animationLevel) {
            2 -> snap()
            1 -> tween(durationMillis = 260, easing = MotionTokens.Easing.emphasizedDecelerate)
            else -> tween(durationMillis = 460, easing = MotionTokens.Easing.velvet)
        },
        label = "ui_scale_density_anim"
    )

    // Adaptive type scale — computed once here, available everywhere via LocalTypeScale
    val typeScale = rememberAdaptiveType()
    val floatingButtonLivePositions = remember { FloatingButtonLivePositions() }
    LaunchedEffect(isLandscape) {
        floatingButtonLivePositions.clearAll()
    }

    // Provide the scaled density to the entire app
    val currentDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalAnimationLevel provides animationLevel,
        LocalAnimationSpeed provides animationSpeed,
        LocalThemeColors provides colors,
        LocalUIScale provides uiScale,
        LocalDismissOnClickOutside provides dismissOnClickOutside,
        LocalStaggerEnabled provides staggerEnabled,
        LocalBackButtonAvoidanceEnabled provides backButtonAvoidanceEnabled,
        LocalBackButtonInversed provides backButtonInversed,
        LocalFloatingButtonAnchors provides if (isLandscape) FloatingButtonAnchors(
            leftX = landscapeFloatingLeftX, leftY = landscapeFloatingLeftY,
            rightX = landscapeFloatingRightX, rightY = landscapeFloatingRightY,
            fullWidth = true
        ) else FloatingButtonAnchors(
            leftX = floatingLeftX, leftY = floatingLeftY,
            rightX = floatingRightX, rightY = floatingRightY
        ),
        LocalFloatingButtonPositionController provides FloatingButtonPositionController(
            leftX = if (isLandscape) landscapeFloatingLeftX else floatingLeftX,
            leftY = if (isLandscape) landscapeFloatingLeftY else floatingLeftY,
            rightX = if (isLandscape) landscapeFloatingRightX else floatingRightX,
            rightY = if (isLandscape) landscapeFloatingRightY else floatingRightY,
            fullWidth = isLandscape,
            settingsX = if (isLandscape) landscapeSettingsButtonX else settingsButtonX,
            settingsY = if (isLandscape) landscapeSettingsButtonY else settingsButtonY,
            settingsFullWidth = isLandscape,
            livePositions = floatingButtonLivePositions,
            onLeftMove = { x, y ->
                if (isLandscape) {
                    landscapeFloatingLeftX = x.coerceIn(0.04f, 0.96f)
                    landscapeFloatingLeftY = y.coerceIn(0.50f, 0.96f)
                } else {
                    floatingLeftX = x.coerceIn(0.16f, 0.84f)
                    floatingLeftY = y.coerceIn(0.50f, 0.96f)
                }
                savePreferencesDebounced()
            },
            onRightMove = { x, y ->
                if (isLandscape) {
                    landscapeFloatingRightX = x.coerceIn(0.04f, 0.96f)
                    landscapeFloatingRightY = y.coerceIn(0.50f, 0.96f)
                } else {
                    floatingRightX = x.coerceIn(0.16f, 0.84f)
                    floatingRightY = y.coerceIn(0.50f, 0.96f)
                }
                savePreferencesDebounced()
            },
            onSettingsMove = { x, y ->
                if (isLandscape) {
                    landscapeSettingsButtonX = x.coerceIn(0.04f, 0.96f)
                    landscapeSettingsButtonY = y.coerceIn(0.50f, 0.96f)
                } else {
                    settingsButtonX = x.coerceIn(0.16f, 0.84f)
                    settingsButtonY = y.coerceIn(0.50f, 0.96f)
                }
                savePreferencesDebounced()
            }
        ),
        LocalShadowsEnabled provides (shadowsEnabled && !effectiveOledMode),
        LocalTypeScale provides typeScale,
        LocalDensity provides Density(
            density = currentDensity.density * animatedUiScale,
            fontScale = currentDensity.fontScale // fontScale includes user system pref, we multiply density so fonts scale too
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(animatedBackgroundColor)
        ) {
            // Gradient Overlay
            val gradientAlpha by animateFloatAsState(
                targetValue = if (gradientEnabled && !effectiveOledMode) 1f else 0f,
                animationSpec = if (animationLevel == 2) snap<Float>() else tween<Float>(
                    durationMillis = if (themeModeSwitchInProgress) 760 else 260,
                    easing = MotionTokens.Easing.velvet
                ),
                label = "gradient_visibility"
            )

            // Combined alpha: Enabled Toggle * Startup Ramp Up
            val finalGradientAlpha = gradientAlpha * gradientStartupAlpha.value

            if (finalGradientAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(finalGradientAlpha)
                ) {
                    // Use graphicsLayer for breathingAlpha so the animation only triggers
                    // a draw-phase update, not a full recomposition on every frame.
                    // This is the single biggest idle-CPU saving: previously the entire
                    // content tree recomposed every ~16ms just because of this one value.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = breathingAlpha * if (isDarkTheme) 1f else 0.65f
                            }
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        animatedGradientStart,
                                        animatedGradientEnd
                                    ),
                                    startY = 0f,
                                    endY = Float.POSITIVE_INFINITY * 0.5f
                                )
                            )
                    )
                }
            }

            // (Grain overlay removed)

            // ── Blur ────────────────────────────────────────────────────────────────────
            // Single render of mainContent — derivedStateOf ensures recomposition
            // only fires when blur state CHANGES (panel open/close), NOT every frame.
            // Previously: mainContent() rendered TWICE (sharp + blurred copies) with
            // three animated floats that caused per-frame recompositions the entire
            // time any panel was open, AND doubled GPU rendering work throughout.
            // Now: ONE render, Modifier.blur(40.dp) toggled on/off.  The panel’s own
            // BouncyDialog entrance animation (scale + fade) provides all visual
            // smoothness — no animated blur-radius transition is needed.
            val blurShouldApply by remember {
                derivedStateOf {
                    (visualAnyFullPanelOpen && blurEnabled) || showAggressiveWarning
                }
            }


            // Animated parallax sensitivity with smooth transition
            val targetParallaxSensitivity = when (particleParallaxSensitivity) {
                0 -> 0.003125f  // Low (0.5x current: 0.00625 * 0.5)
                1 -> 0.01875f    // Medium (0.75x current: 0.025 * 0.75)
                2 -> 0.05f     // High (0.5x less: 0.10 * 0.5)
                else -> 0.01875f
            }
            val animatedParallaxSensitivity by animateFloatAsState(
                targetValue = targetParallaxSensitivity,
                animationSpec = tween(
                    durationMillis = 300,
                    easing = FastOutSlowInEasing  // Ease-in-out
                ),
                label = "parallax_sensitivity"
            )

            // Get current hour for greeting — respects developer time offset
            val currentHour = remember(timeOffsetHours) {
                val raw = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                ((raw + timeOffsetHours.toInt()).let { h -> ((h % 24) + 24) % 24 })
            }

            // ── Main content ──────────────────────────────────────────────────
            // The UI tree is captured once as a lambda and called in two places:
            // mainContent captures the entire main UI tree as a lambda so it can
            // be rendered in the sharp layer and the blurred layer independently.
            val mainContent: @Composable () -> Unit = {
                // Main UI Content
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 0.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    if (isLandscape) {
                        // Landscape Layout — portrait style, split left/right, perfectly fitted
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val lsAvailH = maxHeight
                            val lsAvailW = maxWidth
                            // All spacing derived from available height so nothing ever overflows
                            val lsItemSp = (lsAvailH * 0.036f).coerceIn(6.dp, 14.dp)
                            val lsVPad = (lsAvailH * 0.07f).coerceIn(10.dp, 28.dp)
                            val lsHPad = (lsAvailW * 0.025f).coerceIn(16.dp, 32.dp)

                            Row(modifier = Modifier.fillMaxSize()) {

                                // ── LEFT HALF ────────────────────────────────────────────────
                                // Greeting · GAMA title+bars · Standing by · Choose your path
                                Column(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(1f)
                                        .padding(horizontal = lsHPad, vertical = lsVPad),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(lsItemSp)
                                    ) {
                                        // Greeting — same compact pools as portrait
                                        val lsGreeting = remember(currentHour, userName, strings) {
                                            val pool: Array<String> = when (currentHour) {
                                                in 0..5 -> if (userName.isNotEmpty()) arrayOf(
                                                    strings["greetings.compact_late_named_1"].replace(
                                                        "%s",
                                                        userName
                                                    ).ifEmpty { "Still up, $userName? 🌙" },
                                                    strings["greetings.compact_late_named_2"].replace("%s", userName)
                                                        .ifEmpty { "Late night session, $userName? 🌙" },
                                                    strings["greetings.compact_late_named_3"].replace("%s", userName)
                                                        .ifEmpty { "The world is quiet, $userName. 🌙" },
                                                    strings["greetings.compact_late_named_4"].replace("%s", userName)
                                                        .ifEmpty { "Somewhere between today and tomorrow, $userName. 🌙" }) else arrayOf(
                                                    strings["greetings.compact_late_1"].ifEmpty { "Still up? 🌙" },
                                                    strings["greetings.compact_late_2"].ifEmpty { "Late night session? 🌙" },
                                                    strings["greetings.compact_late_3"].ifEmpty { "The world is quiet. 🌙" },
                                                    strings["greetings.compact_late_4"].ifEmpty { "Somewhere between today and tomorrow. 🌙" })

                                                in 6..11 -> if (userName.isNotEmpty()) arrayOf(
                                                    strings["greetings.compact_morning_named_1"].replace(
                                                        "%s",
                                                        userName
                                                    ).ifEmpty { "Good morning, $userName! ☀️" },
                                                    strings["greetings.compact_morning_named_2"].replace("%s", userName)
                                                        .ifEmpty { "Morning, $userName! ☀️" },
                                                    strings["greetings.compact_morning_named_3"].replace("%s", userName)
                                                        .ifEmpty { "Rise and shine, $userName! ☀️" },
                                                    strings["greetings.compact_morning_named_4"].replace("%s", userName)
                                                        .ifEmpty { "A fresh start, $userName. ☀️" },
                                                    strings["greetings.compact_morning_named_5"].replace("%s", userName)
                                                        .ifEmpty { "Up early, $userName? ☀️" }) else arrayOf(
                                                    strings["greetings.compact_morning_1"].ifEmpty { "Good morning! ☀️" },
                                                    strings["greetings.compact_morning_2"].ifEmpty { "Morning! ☀️" },
                                                    strings["greetings.compact_morning_3"].ifEmpty { "Rise and shine! ☀️" },
                                                    strings["greetings.compact_morning_4"].ifEmpty { "A fresh start. ☀️" },
                                                    strings["greetings.compact_morning_5"].ifEmpty { "Up early? ☀️" })

                                                in 12..16 -> if (userName.isNotEmpty()) arrayOf(
                                                    strings["greetings.compact_afternoon_named_1"].replace(
                                                        "%s",
                                                        userName
                                                    ).ifEmpty { "Hey, $userName! 👋" },
                                                    strings["greetings.compact_afternoon_named_2"].replace(
                                                        "%s",
                                                        userName
                                                    ).ifEmpty { "Good afternoon, $userName! 👋" },
                                                    strings["greetings.compact_afternoon_named_3"].replace(
                                                        "%s",
                                                        userName
                                                    ).ifEmpty { "Welcome back, $userName. 👋" },
                                                    strings["greetings.compact_afternoon_named_4"].replace(
                                                        "%s",
                                                        userName
                                                    ).ifEmpty { "There you are, $userName! 👋" },
                                                    strings["greetings.compact_afternoon_named_5"].replace(
                                                        "%s",
                                                        userName
                                                    ).ifEmpty { "Good to see you, $userName. 👋" }) else arrayOf(
                                                    strings["greetings.compact_afternoon_1"].ifEmpty { "Hey! 👋" },
                                                    strings["greetings.compact_afternoon_2"].ifEmpty { "Good afternoon! 👋" },
                                                    strings["greetings.compact_afternoon_3"].ifEmpty { "Welcome back. 👋" },
                                                    strings["greetings.compact_afternoon_4"].ifEmpty { "There you are! 👋" },
                                                    strings["greetings.compact_afternoon_5"].ifEmpty { "Good to see you. 👋" })

                                                in 17..22 -> if (userName.isNotEmpty()) arrayOf(
                                                    strings["greetings.compact_evening_named_1"].replace(
                                                        "%s",
                                                        userName
                                                    ).ifEmpty { "Good evening, $userName! 🌙" },
                                                    strings["greetings.compact_evening_named_2"].replace("%s", userName)
                                                        .ifEmpty { "Evening, $userName. 🌙" },
                                                    strings["greetings.compact_evening_named_3"].replace("%s", userName)
                                                        .ifEmpty { "Winding down, $userName? 🌙" },
                                                    strings["greetings.compact_evening_named_4"].replace("%s", userName)
                                                        .ifEmpty { "End of the day, $userName. 🌙" },
                                                    strings["greetings.compact_evening_named_5"].replace("%s", userName)
                                                        .ifEmpty { "Hope it was a good one, $userName. 🌙" }) else arrayOf(
                                                    strings["greetings.compact_evening_1"].ifEmpty { "Good evening! 🌙" },
                                                    strings["greetings.compact_evening_2"].ifEmpty { "Evening. 🌙" },
                                                    strings["greetings.compact_evening_3"].ifEmpty { "Winding down? 🌙" },
                                                    strings["greetings.compact_evening_4"].ifEmpty { "End of the day. 🌙" },
                                                    strings["greetings.compact_evening_5"].ifEmpty { "Hope it was a good one. 🌙" })

                                                else -> if (userName.isNotEmpty()) arrayOf(
                                                    strings["greetings.compact_midnight_named_1"].replace(
                                                        "%s",
                                                        userName
                                                    ).ifEmpty { "Still at it, $userName? 🌙" },
                                                    strings["greetings.compact_midnight_named_2"].replace(
                                                        "%s",
                                                        userName
                                                    ).ifEmpty { "The quiet hours, $userName. 🌙" },
                                                    strings["greetings.compact_midnight_named_3"].replace(
                                                        "%s",
                                                        userName
                                                    ).ifEmpty { "Almost tomorrow, $userName. 🌙" },
                                                    strings["greetings.compact_midnight_named_4"].replace(
                                                        "%s",
                                                        userName
                                                    ).ifEmpty { "Some nights are for thinking, $userName. 🌙" },
                                                    strings["greetings.compact_midnight_named_5"].replace(
                                                        "%s",
                                                        userName
                                                    ).ifEmpty { "The world can wait, $userName. 🌙" }) else arrayOf(
                                                    strings["greetings.compact_midnight_1"].ifEmpty { "Still at it? 🌙" },
                                                    strings["greetings.compact_midnight_2"].ifEmpty { "The quiet hours. 🌙" },
                                                    strings["greetings.compact_midnight_3"].ifEmpty { "Almost tomorrow. 🌙" },
                                                    strings["greetings.compact_midnight_4"].ifEmpty { "Some nights are for thinking. 🌙" },
                                                    strings["greetings.compact_midnight_5"].ifEmpty { "The world can wait. 🌙" })
                                            }
                                            pool.random()
                                        }
                                        AnimatedElement(visible = isVisible, staggerIndex = 0, totalItems = 8) {
                                            Text(
                                                text = lsGreeting.withoutGreetingEmoji(),
                                                fontSize = ts.bodyLarge,
                                                fontFamily = quicksandFontFamily,
                                                color = colors.textSecondary.copy(alpha = 0.8f),
                                                textAlign = TextAlign.Center,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        // GAMA title with decorative bars — identical to portrait
                                        AnimatedElement(visible = isVisible, staggerIndex = 1, totalItems = 8) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(4.dp)
                                                        .background(
                                                            brush = Brush.horizontalGradient(
                                                                colors = listOf(
                                                                    colors.primaryAccent.copy(alpha = 0f),
                                                                    colors.primaryAccent.copy(alpha = 1f)
                                                                )
                                                            )
                                                        )
                                                )
                                                val lsGamaTextSize = (ts.displayLarge.value * 1.22f).sp
                                                val lsGamaGlowPaint = remember(colors.textPrimary, lsGamaTextSize) {
                                                    android.graphics.Paint().apply {
                                                        isAntiAlias = true
                                                        color = android.graphics.Color.TRANSPARENT
                                                        setShadowLayer(200f, 0f, 0f, colors.textPrimary.toArgb())
                                                        textAlign = android.graphics.Paint.Align.CENTER
                                                        isDither = true
                                                    }
                                                }
                                                Text(
                                                    text = "GAMA",
                                                    fontSize = lsGamaTextSize,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = quicksandFontFamily,
                                                    color = colors.textPrimary,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier
                                                        .padding(horizontal = 24.dp)
                                                        .pointerInput(Unit) {
                                                            detectTapGestures(
                                                                onLongPress = {
                                                                    performHaptic(HapticFeedbackConstants.LONG_PRESS)
                                                                    showEasterEgg = true
                                                                }
                                                            )
                                                        }
                                                        .drawWithContent {
                                                            drawIntoCanvas { canvas ->
                                                                lsGamaGlowPaint.textSize = lsGamaTextSize.toPx()
                                                                canvas.nativeCanvas.drawText(
                                                                    "GAMA",
                                                                    size.width / 2,
                                                                    size.height / 2 + (lsGamaTextSize.toPx() * 0.25f),
                                                                    lsGamaGlowPaint
                                                                )
                                                            }
                                                            drawContent()
                                                        }
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(4.dp)
                                                        .background(
                                                            brush = Brush.horizontalGradient(
                                                                colors = listOf(
                                                                    colors.primaryAccent.copy(alpha = 1f),
                                                                    colors.primaryAccent.copy(alpha = 0f)
                                                                )
                                                            )
                                                        )
                                                )
                                            }
                                        }

                                        // Standing by — identical to portrait
                                        AnimatedElement(visible = isVisible, staggerIndex = 2, totalItems = 8) {
                                            Text(
                                                text = strings["main.standing_by"].ifEmpty { "Standing by and awaiting your command" },
                                                fontSize = ts.bodyLarge,
                                                fontFamily = quicksandFontFamily,
                                                color = colors.textSecondary,
                                                textAlign = TextAlign.Center,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        // Choose your path — identical to portrait
                                        AnimatedElement(visible = isVisible, staggerIndex = 3, totalItems = 8) {
                                            Text(
                                                text = strings["main.whats_next"].ifEmpty { "CHOOSE YOUR PATH." },
                                                fontSize = ts.headlineSmall,
                                                fontFamily = quicksandFontFamily,
                                                color = colors.primaryAccent.copy(alpha = 0.88f),
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 2.sp,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }

                                // ── RIGHT HALF ───────────────────────────────────────────────
                                // Unified frosted-glass box — identical to portrait's single card
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(1f)
                                        // Keep the right menu concentric inside its
                                        // half: the old zero start inset made the
                                        // shell touch the center seam while leaving
                                        // a larger outer gutter on the right.
                                        .padding(horizontal = lsHPad, vertical = lsVPad),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AnimatedElement(
                                        visible = isVisible, staggerIndex = 4, cardShadow = false,
                                        totalItems = 8, enabled = shizukuRunning && shizukuPermissionGranted
                                    ) {
                                        val lsShizukuReady = shizukuRunning && shizukuPermissionGranted
                                        val lsShellInset = 16.dp
                                        val lsShellRadius = 44.dp

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(lsShellRadius))
                                                .drawWithContent {
                                                    val edgeDepth = 30.dp.toPx()
                                                    val sideDepth = 24.dp.toPx()

                                                    // Interior edge glow: cheap gradients drawn inside the parent box,
                                                    // so the accent outline looks like it spills light inward.
                                                    drawRect(
                                                        brush = Brush.verticalGradient(
                                                            colors = listOf(
                                                                colors.primaryAccent.copy(alpha = 0.18f),
                                                                colors.primaryAccent.copy(alpha = 0.08f),
                                                                Color.Transparent
                                                            ),
                                                            startY = 0f,
                                                            endY = edgeDepth
                                                        ),
                                                        topLeft = Offset.Zero,
                                                        size = Size(size.width, edgeDepth)
                                                    )
                                                    drawRect(
                                                        brush = Brush.verticalGradient(
                                                            colors = listOf(
                                                                Color.Transparent,
                                                                colors.primaryAccent.copy(alpha = 0.08f),
                                                                colors.primaryAccent.copy(alpha = 0.18f)
                                                            ),
                                                            startY = size.height - edgeDepth,
                                                            endY = size.height
                                                        ),
                                                        topLeft = Offset(0f, size.height - edgeDepth),
                                                        size = Size(size.width, edgeDepth)
                                                    )
                                                    drawRect(
                                                        brush = Brush.horizontalGradient(
                                                            colors = listOf(
                                                                colors.primaryAccent.copy(alpha = 0.15f),
                                                                colors.primaryAccent.copy(alpha = 0.06f),
                                                                Color.Transparent
                                                            ),
                                                            startX = 0f,
                                                            endX = sideDepth
                                                        ),
                                                        topLeft = Offset.Zero,
                                                        size = Size(sideDepth, size.height)
                                                    )
                                                    drawRect(
                                                        brush = Brush.horizontalGradient(
                                                            colors = listOf(
                                                                Color.Transparent,
                                                                colors.primaryAccent.copy(alpha = 0.06f),
                                                                colors.primaryAccent.copy(alpha = 0.15f)
                                                            ),
                                                            startX = size.width - sideDepth,
                                                            endX = size.width
                                                        ),
                                                        topLeft = Offset(size.width - sideDepth, 0f),
                                                        size = Size(sideDepth, size.height)
                                                    )

                                                    drawContent()

                                                    val outlineWidth = 1.dp.toPx()
                                                    val inset = outlineWidth / 2f
                                                    val radius = lsShellRadius.toPx() - inset
                                                    drawRoundRect(
                                                        color = colors.primaryAccent.copy(alpha = 0.44f),
                                                        topLeft = Offset(inset, inset),
                                                        size = Size(
                                                            size.width - outlineWidth,
                                                            size.height - outlineWidth
                                                        ),
                                                        cornerRadius = CornerRadius(radius, radius),
                                                        style = Stroke(width = outlineWidth)
                                                    )
                                                }
                                        ) {
                                            // Transparent card — no blur, no frosted backdrop.
                                            // Sharp content layer
                                            // The renderer card, two renderer choices, and Resources must all
                                            // fit in short landscape windows at Android's largest display zoom.
                                            Column(
                                                // The inner 28dp cards sit in a 44dp shell: the
                                                // shared 16dp difference is the same on every edge.
                                                modifier = Modifier.padding(lsShellInset),
                                                verticalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                RendererCard(
                                                    currentRenderer = currentRenderer,
                                                    commandOutput = commandOutput,
                                                    isSmallScreen = isSmallScreen,
                                                    colors = colors,
                                                    cardBackground = cardBackground,
                                                    shizukuReady = lsShizukuReady || rootAvailable,
                                                    shizukuRunning = shizukuRunning,
                                                    shizukuStatus = shizukuStatus,
                                                    onShizukuErrorClick = {
                                                        if (shizukuStatus.contains("❌") || shizukuStatus.contains("⚠️")) {
                                                            performHaptic()
                                                            shizukuHelpType = when {
                                                                shizukuStatus.contains("❌") -> "not_running"
                                                                shizukuStatus.contains("⚠️") -> "permission"
                                                                else -> ""
                                                            }
                                                            openMainPanelExclusive { showShizukuHelp = true }
                                                        }
                                                    },
                                                    oledMode = effectiveOledMode,
                                                    rendererLoading = rendererLoading,
                                                    lastSwitchTime = lastSwitchTime,
                                                    rootAvailable = rootAvailable,
                                                    lastAction = lastRendererAction,
                                                    backendName = shizukuBackendName
                                                )
                                                // Vulkan | OpenGL
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .animateContentSize(
                                                            animationSpec = spring(
                                                                dampingRatio = MotionTokens.Springs.smooth.dampingRatio,
                                                                stiffness = MotionTokens.Springs.smooth.stiffness
                                                            )
                                                        ),
                                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                                ) {
                                                    BigRendererButton(
                                                        text = strings["renderer.vulkan"].ifEmpty { "Vulkan" },
                                                        onClick = {
                                                            performRendererHaptic()
                                                            pendingRendererName = "Vulkan"
                                                            pendingRendererSwitch = {
                                                                verboseOutput = ""
                                                                // Verbose output is shown after the success dialog is dismissed, so panels never overlap.
                                                                ShizukuHelper.runVulkan(
                                                                    context, scope, aggressiveMode,
                                                                    killLauncher,
                                                                    killKeyboard,
                                                                    excludedAppsList.toSet(),
                                                                    onRendererStatusUpdate,
                                                                    if (verboseMode) { output -> verboseOutput += output } else null
                                                                )
                                                            }
                                                            successDialogMessage =
                                                                strings["main.vulkan_applied"].ifEmpty { "Vulkan has been applied!" }
                                                            if (!lsShizukuReady && !rootAvailable) {
                                                                shizukuHelpType =
                                                                    if (!shizukuRunning) "not_running" else "permission"
                                                                openMainPanelExclusive { showRootAccessDialog = true }
                                                                return@BigRendererButton
                                                            }
                                                            openMainPanelExclusive { showWarningDialog = true }
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        isSelected = currentRenderer == "Vulkan",
                                                        forceHighlight = true,
                                                    // Keep the button actionable when no backend is ready;
                                                    // its click handler opens the backend setup dialog.
                                                    enabled = true,
                                                        colors = colors,
                                                        oledMode = effectiveOledMode,
                                                        iconType = "vulkan"
                                                    )
                                                    BigRendererButton(
                                                        text = strings["renderer.opengl"].ifEmpty { "OpenGL" },
                                                        onClick = {
                                                            performRendererHaptic()
                                                            pendingRendererName = "OpenGL"
                                                            pendingRendererSwitch = {
                                                                verboseOutput = ""
                                                                // Verbose output is shown after the success dialog is dismissed, so panels never overlap.
                                                                ShizukuHelper.runOpenGL(
                                                                    context, scope, aggressiveMode,
                                                                    killLauncher,
                                                                    killKeyboard,
                                                                    excludedAppsList.toSet(),
                                                                    onRendererStatusUpdate,
                                                                    if (verboseMode) { output -> verboseOutput += output } else null
                                                                )
                                                            }
                                                            successDialogMessage =
                                                                strings["main.opengl_applied"].ifEmpty { "OpenGL has been applied!" }
                                                            if (!lsShizukuReady && !rootAvailable) {
                                                                shizukuHelpType =
                                                                    if (!shizukuRunning) "not_running" else "permission"
                                                                openMainPanelExclusive { showRootAccessDialog = true }
                                                                return@BigRendererButton
                                                            }
                                                            openMainPanelExclusive { showWarningDialog = true }
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        isSelected = false,
                                                    enabled = true,
                                                        colors = colors,
                                                        oledMode = effectiveOledMode,
                                                        iconType = "opengl"
                                                    )
                                                }
                                                // Resources | GPUWatch
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .animateContentSize(
                                                            animationSpec = spring(
                                                                dampingRatio = MotionTokens.Springs.smooth.dampingRatio,
                                                                stiffness = MotionTokens.Springs.smooth.stiffness
                                                            )
                                                        ),
                                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                                ) {
                                                    IllustratedButton(
                                                        text = strings["renderer.library"].ifEmpty { "Library" },
                                                        onClick = {
                                                            performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                                                            openMainPanelExclusive { showResourcesPanel = true }
                                                        },
                                                        modifier = if (showGpuWatchButton) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                                                        accent = false,
                                                        enabled = true,
                                                        colors = colors,
                                                        oledMode = effectiveOledMode,
                                                        iconType = "resources"
                                                    )
                                                    AnimatedVisibility(
                                                        modifier = Modifier.weight(1f),
                                                        visible = showGpuWatchButton,
                                                        enter = fadeIn(
                                                            animationSpec = tween(
                                                                220,
                                                                easing = MotionTokens.Easing.enter
                                                            )
                                                        ) +
                                                                expandHorizontally(
                                                                    animationSpec = spring(
                                                                        dampingRatio = MotionTokens.Springs.smooth.dampingRatio,
                                                                        stiffness = MotionTokens.Springs.smooth.stiffness
                                                                    ),
                                                                    expandFrom = Alignment.Start
                                                                ) +
                                                                scaleIn(
                                                                    initialScale = 0.88f,
                                                                    animationSpec = spring(
                                                                        dampingRatio = MotionTokens.Springs.gentle.dampingRatio,
                                                                        stiffness = MotionTokens.Springs.gentle.stiffness
                                                                    )
                                                                ),
                                                        exit = fadeOut(
                                                            animationSpec = tween(
                                                                160,
                                                                easing = MotionTokens.Easing.exit
                                                            )
                                                        ) +
                                                                shrinkHorizontally(
                                                                    animationSpec = tween(
                                                                        180,
                                                                        easing = MotionTokens.Easing.exit
                                                                    ),
                                                                    shrinkTowards = Alignment.Start
                                                                ) +
                                                                scaleOut(
                                                                    targetScale = 0.88f,
                                                                    animationSpec = tween(
                                                                        160,
                                                                        easing = MotionTokens.Easing.exit
                                                                    )
                                                                )
                                                    ) {
                                                        IllustratedButton(
                                                            text = strings["renderer.show_gpuwatch"].ifEmpty { "Open GPUWatch" },
                                                            onClick = {
                                                                performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                                                                openMainPanelExclusive { showGPUWatchConfirm = true }
                                                            },
                                                            modifier = Modifier.fillMaxWidth(),
                                                            accent = false,
                                                            enabled = true,
                                                            colors = colors,
                                                            oledMode = effectiveOledMode,
                                                            iconType = "gpuwatch"
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Portrait Layout - Single column using 100% vertical space
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center // Changed back to Center to bring groups together
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 12.dp else 16.dp) // Single spacing for everything
                            ) {
                                // Greeting
                                // Greeting — selected once per hour/name change, stable during the entrance animation.
                                // pool.random() is called inside remember so it only runs when keys change,
                                // not on every recomposition that happens during the 585 ms fade-in.
                                val greeting = remember(currentHour, userName, strings) {
                                    val pool: Array<String> = when (currentHour) {
                                        in 0..5 -> if (userName.isNotEmpty()) arrayOf(
                                            strings["greetings.compact_late_named_1"].replace(
                                                "%s",
                                                userName
                                            ).ifEmpty { "Still up, $userName? 🌙" },
                                            strings["greetings.compact_late_named_2"].replace("%s", userName)
                                                .ifEmpty { "Late night session, $userName? 🌙" },
                                            strings["greetings.compact_late_named_3"].replace("%s", userName)
                                                .ifEmpty { "The world is quiet, $userName. 🌙" },
                                            strings["greetings.compact_late_named_4"].replace("%s", userName)
                                                .ifEmpty { "Somewhere between today and tomorrow, $userName. 🌙" }) else arrayOf(
                                            strings["greetings.compact_late_1"].ifEmpty { "Still up? 🌙" },
                                            strings["greetings.compact_late_2"].ifEmpty { "Late night session? 🌙" },
                                            strings["greetings.compact_late_3"].ifEmpty { "The world is quiet. 🌙" },
                                            strings["greetings.compact_late_4"].ifEmpty { "Somewhere between today and tomorrow. 🌙" })

                                        in 6..11 -> if (userName.isNotEmpty()) arrayOf(
                                            strings["greetings.compact_morning_named_1"].replace(
                                                "%s",
                                                userName
                                            ).ifEmpty { "Good morning, $userName! ☀️" },
                                            strings["greetings.compact_morning_named_2"].replace("%s", userName)
                                                .ifEmpty { "Morning, $userName! ☀️" },
                                            strings["greetings.compact_morning_named_3"].replace("%s", userName)
                                                .ifEmpty { "Rise and shine, $userName! ☀️" },
                                            strings["greetings.compact_morning_named_4"].replace("%s", userName)
                                                .ifEmpty { "A fresh start, $userName. ☀️" },
                                            strings["greetings.compact_morning_named_5"].replace("%s", userName)
                                                .ifEmpty { "Up early, $userName? ☀️" }) else arrayOf(
                                            strings["greetings.compact_morning_1"].ifEmpty { "Good morning! ☀️" },
                                            strings["greetings.compact_morning_2"].ifEmpty { "Morning! ☀️" },
                                            strings["greetings.compact_morning_3"].ifEmpty { "Rise and shine! ☀️" },
                                            strings["greetings.compact_morning_4"].ifEmpty { "A fresh start. ☀️" },
                                            strings["greetings.compact_morning_5"].ifEmpty { "Up early? ☀️" })

                                        in 12..16 -> if (userName.isNotEmpty()) arrayOf(
                                            strings["greetings.compact_afternoon_named_1"].replace(
                                                "%s",
                                                userName
                                            ).ifEmpty { "Hey, $userName! 👋" },
                                            strings["greetings.compact_afternoon_named_2"].replace("%s", userName)
                                                .ifEmpty { "Good afternoon, $userName! 👋" },
                                            strings["greetings.compact_afternoon_named_3"].replace("%s", userName)
                                                .ifEmpty { "Welcome back, $userName. 👋" },
                                            strings["greetings.compact_afternoon_named_4"].replace("%s", userName)
                                                .ifEmpty { "There you are, $userName! 👋" },
                                            strings["greetings.compact_afternoon_named_5"].replace("%s", userName)
                                                .ifEmpty { "Good to see you, $userName. 👋" }) else arrayOf(
                                            strings["greetings.compact_afternoon_1"].ifEmpty { "Hey! 👋" },
                                            strings["greetings.compact_afternoon_2"].ifEmpty { "Good afternoon! 👋" },
                                            strings["greetings.compact_afternoon_3"].ifEmpty { "Welcome back. 👋" },
                                            strings["greetings.compact_afternoon_4"].ifEmpty { "There you are! 👋" },
                                            strings["greetings.compact_afternoon_5"].ifEmpty { "Good to see you. 👋" })

                                        in 17..22 -> if (userName.isNotEmpty()) arrayOf(
                                            strings["greetings.compact_evening_named_1"].replace(
                                                "%s",
                                                userName
                                            ).ifEmpty { "Good evening, $userName! 🌙" },
                                            strings["greetings.compact_evening_named_2"].replace("%s", userName)
                                                .ifEmpty { "Evening, $userName. 🌙" },
                                            strings["greetings.compact_evening_named_3"].replace("%s", userName)
                                                .ifEmpty { "Winding down, $userName? 🌙" },
                                            strings["greetings.compact_evening_named_4"].replace("%s", userName)
                                                .ifEmpty { "End of the day, $userName. 🌙" },
                                            strings["greetings.compact_evening_named_5"].replace("%s", userName)
                                                .ifEmpty { "Hope it was a good one, $userName. 🌙" }) else arrayOf(
                                            strings["greetings.compact_evening_1"].ifEmpty { "Good evening! 🌙" },
                                            strings["greetings.compact_evening_2"].ifEmpty { "Evening. 🌙" },
                                            strings["greetings.compact_evening_3"].ifEmpty { "Winding down? 🌙" },
                                            strings["greetings.compact_evening_4"].ifEmpty { "End of the day. 🌙" },
                                            strings["greetings.compact_evening_5"].ifEmpty { "Hope it was a good one. 🌙" })

                                        else -> if (userName.isNotEmpty()) arrayOf(
                                            strings["greetings.compact_midnight_named_1"].replace(
                                                "%s",
                                                userName
                                            ).ifEmpty { "Still at it, $userName? 🌙" },
                                            strings["greetings.compact_midnight_named_2"].replace("%s", userName)
                                                .ifEmpty { "The quiet hours, $userName. 🌙" },
                                            strings["greetings.compact_midnight_named_3"].replace("%s", userName)
                                                .ifEmpty { "Almost tomorrow, $userName. 🌙" },
                                            strings["greetings.compact_midnight_named_4"].replace("%s", userName)
                                                .ifEmpty { "Some nights are for thinking, $userName. 🌙" },
                                            strings["greetings.compact_midnight_named_5"].replace("%s", userName)
                                                .ifEmpty { "The world can wait, $userName. 🌙" }) else arrayOf(
                                            strings["greetings.compact_midnight_1"].ifEmpty { "Still at it? 🌙" },
                                            strings["greetings.compact_midnight_2"].ifEmpty { "The quiet hours. 🌙" },
                                            strings["greetings.compact_midnight_3"].ifEmpty { "Almost tomorrow. 🌙" },
                                            strings["greetings.compact_midnight_4"].ifEmpty { "Some nights are for thinking. 🌙" },
                                            strings["greetings.compact_midnight_5"].ifEmpty { "The world can wait. 🌙" })
                                    }
                                    pool.random()
                                }
                                AnimatedElement(
                                    visible = isVisible, staggerIndex = 0,
                                    totalItems = 8
                                ) {
                                    Text(
                                        text = greeting.withoutGreetingEmoji(),
                                        fontSize = ts.bodyLarge,
                                        fontFamily = quicksandFontFamily,
                                        color = colors.textSecondary.copy(alpha = 0.8f),
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // GAMA Title with bars
                                AnimatedElement(
                                    visible = isVisible, staggerIndex = 1,
                                    totalItems = 8
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(4.dp)
                                                .background(
                                                    brush = Brush.horizontalGradient(
                                                        colors = listOf(
                                                            colors.primaryAccent.copy(alpha = 0f),
                                                            colors.primaryAccent.copy(alpha = 1f)
                                                        )
                                                    )
                                                )
                                        )

                                        val gamaTextSize = (ts.displayLarge.value * 1.22f).sp  // Hero GAMA text
                                        val context = LocalContext.current
                                        val quicksandBoldTypeface = remember {
                                            try {
                                                android.graphics.Typeface.createFromAsset(
                                                    context.assets,
                                                    "fonts/quicksand_bold.ttf"
                                                )
                                            } catch (e: Exception) {
                                                android.graphics.Typeface.DEFAULT_BOLD
                                            }
                                        }
                                        // Cache Paint outside drawWithContent — same fix as TitleSection.
                                        // Allocating Paint+setShadowLayer inside the draw lambda runs every
                                        // frame and generates GC pressure on JIT-based older devices.
                                        val gamaGlowPaint = remember(colors.textPrimary, gamaTextSize) {
                                            android.graphics.Paint().apply {
                                                isAntiAlias = true
                                                color = android.graphics.Color.TRANSPARENT
                                                setShadowLayer(200f, 0f, 0f, colors.textPrimary.toArgb())
                                                textAlign = android.graphics.Paint.Align.CENTER
                                                isDither = true
                                            }
                                        }

                                        Text(
                                            text = "GAMA",
                                            fontSize = gamaTextSize,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = quicksandFontFamily,
                                            color = colors.textPrimary,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier
                                                .padding(horizontal = 24.dp)
                                                .pointerInput(Unit) {
                                                    detectTapGestures(
                                                        onLongPress = {
                                                            performHaptic(HapticFeedbackConstants.LONG_PRESS)
                                                            showEasterEgg = true
                                                        }
                                                    )
                                                }
                                                .drawWithContent {
                                                    drawIntoCanvas { canvas ->
                                                        gamaGlowPaint.textSize = gamaTextSize.toPx()
                                                        canvas.nativeCanvas.drawText(
                                                            "GAMA",
                                                            size.width / 2,
                                                            size.height / 2 + (gamaTextSize.toPx() * 0.25f),
                                                            gamaGlowPaint
                                                        )
                                                    }
                                                    drawContent()
                                                }
                                        )

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(4.dp)
                                                .background(
                                                    brush = Brush.horizontalGradient(
                                                        colors = listOf(
                                                            colors.primaryAccent.copy(alpha = 1f),
                                                            colors.primaryAccent.copy(alpha = 0f)
                                                        )
                                                    )
                                                )
                                        )
                                    }
                                }

                                // Standing by text
                                AnimatedElement(
                                    visible = isVisible, staggerIndex = 2,
                                    totalItems = 8
                                ) {
                                    Text(
                                        text = strings["main.standing_by"].ifEmpty { "Standing by and awaiting your command" },
                                        fontSize = ts.bodyLarge,  // Reduced from 18/21
                                        fontFamily = quicksandFontFamily,
                                        color = colors.textSecondary,
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // What's Next
                                AnimatedElement(
                                    visible = isVisible, staggerIndex = 3,
                                    totalItems = 8
                                ) {
                                    Text(
                                        text = strings["main.whats_next"].ifEmpty { "CHOOSE YOUR PATH." },
                                        fontSize = ts.headlineSmall,
                                        fontFamily = quicksandFontFamily,
                                        color = colors.primaryAccent.copy(alpha = 0.88f),
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                // Unified frosted glass box: RendererCard + Vulkan | OpenGL + Resources | GPUWatch
                                AnimatedElement(
                                    visible = isVisible, staggerIndex = 4, cardShadow = false,
                                    totalItems = 8, enabled = (shizukuRunning && shizukuPermissionGranted) || rootAvailable
                                ) {
                                    val shizukuReady = shizukuRunning && shizukuPermissionGranted

                                    // Transparent card — no blur, no frosted backdrop.
                                    // Removing the blurred background layer eliminates the
                                    // per-frame RenderEffect pass for a direct GPU performance win.
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(44.dp))
                                            .drawWithContent {
                                                val edgeDepth = 30.dp.toPx()
                                                val sideDepth = 24.dp.toPx()

                                                // Interior edge glow: cheap gradients drawn inside the parent box,
                                                // so the accent outline looks like it spills light inward.
                                                drawRect(
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(
                                                            colors.primaryAccent.copy(alpha = 0.18f),
                                                            colors.primaryAccent.copy(alpha = 0.08f),
                                                            Color.Transparent
                                                        ),
                                                        startY = 0f,
                                                        endY = edgeDepth
                                                    ),
                                                    topLeft = Offset.Zero,
                                                    size = Size(size.width, edgeDepth)
                                                )
                                                drawRect(
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(
                                                            Color.Transparent,
                                                            colors.primaryAccent.copy(alpha = 0.08f),
                                                            colors.primaryAccent.copy(alpha = 0.18f)
                                                        ),
                                                        startY = size.height - edgeDepth,
                                                        endY = size.height
                                                    ),
                                                    topLeft = Offset(0f, size.height - edgeDepth),
                                                    size = Size(size.width, edgeDepth)
                                                )
                                                drawRect(
                                                    brush = Brush.horizontalGradient(
                                                        colors = listOf(
                                                            colors.primaryAccent.copy(alpha = 0.15f),
                                                            colors.primaryAccent.copy(alpha = 0.06f),
                                                            Color.Transparent
                                                        ),
                                                        startX = 0f,
                                                        endX = sideDepth
                                                    ),
                                                    topLeft = Offset.Zero,
                                                    size = Size(sideDepth, size.height)
                                                )
                                                drawRect(
                                                    brush = Brush.horizontalGradient(
                                                        colors = listOf(
                                                            Color.Transparent,
                                                            colors.primaryAccent.copy(alpha = 0.06f),
                                                            colors.primaryAccent.copy(alpha = 0.15f)
                                                        ),
                                                        startX = size.width - sideDepth,
                                                        endX = size.width
                                                    ),
                                                    topLeft = Offset(size.width - sideDepth, 0f),
                                                    size = Size(sideDepth, size.height)
                                                )

                                                drawContent()

                                                val outlineWidth = 1.dp.toPx()
                                                val inset = outlineWidth / 2f
                                                val radius = 44.dp.toPx() - inset
                                                drawRoundRect(
                                                    color = colors.primaryAccent.copy(alpha = 0.44f),
                                                    topLeft = Offset(inset, inset),
                                                    size = Size(size.width - outlineWidth, size.height - outlineWidth),
                                                    cornerRadius = CornerRadius(radius, radius),
                                                    style = Stroke(width = outlineWidth)
                                                )
                                            }
                                    ) {
                                        // Accent tint + border, fully opaque, on top of transparent bg

                                        // Sharp content on top
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            // RendererCard at the top of the box
                                            RendererCard(
                                                currentRenderer = currentRenderer,
                                                commandOutput = commandOutput,
                                                isSmallScreen = isSmallScreen,
                                                colors = colors,
                                                cardBackground = cardBackground,
                                                shizukuReady = (shizukuRunning && shizukuPermissionGranted) || rootAvailable,
                                                shizukuRunning = shizukuRunning,
                                                shizukuStatus = shizukuStatus,
                                                onShizukuErrorClick = {
                                                    if (shizukuStatus.contains("❌") || shizukuStatus.contains("⚠️")) {
                                                        performHaptic()
                                                        shizukuHelpType = when {
                                                            shizukuStatus.contains("❌") -> "not_running"
                                                            shizukuStatus.contains("⚠️") -> "permission"
                                                            else -> ""
                                                        }
                                                        openMainPanelExclusive { showShizukuHelp = true }
                                                    }
                                                },
                                                oledMode = effectiveOledMode,
                                                rendererLoading = rendererLoading,
                                                lastSwitchTime = lastSwitchTime,
                                                rootAvailable = rootAvailable,
                                                lastAction = lastRendererAction,
                                                backendName = shizukuBackendName
                                            )

                                            // Row 1: Vulkan | OpenGL — big square cards
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .animateContentSize(
                                                        animationSpec = spring(
                                                            dampingRatio = MotionTokens.Springs.smooth.dampingRatio,
                                                            stiffness = MotionTokens.Springs.smooth.stiffness
                                                        )
                                                    ),
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                BigRendererButton(
                                                    text = strings["renderer.vulkan"].ifEmpty { "Vulkan" },
                                                    onClick = {
                                                        performRendererHaptic()
                                                        pendingRendererName = "Vulkan"
                                                        pendingRendererSwitch = {
                                                            verboseOutput = ""
                                                            // Verbose output is shown after the success dialog is dismissed, so panels never overlap.
                                                            ShizukuHelper.runVulkan(
                                                                context, scope, aggressiveMode,
                                                                killLauncher,
                                                                killKeyboard,
                                                            excludedAppsList.toSet(),
                                                                onRendererStatusUpdate,
                                                                if (verboseMode) { output -> verboseOutput += output } else null
                                                            )
                                                        }
                                                        successDialogMessage =
                                                            strings["main.vulkan_applied"].ifEmpty { "Vulkan has been applied!" }
                                                        if (!shizukuReady && !rootAvailable) {
                                                            shizukuHelpType =
                                                                if (!shizukuRunning) "not_running" else "permission"
                                                            openMainPanelExclusive { showRootAccessDialog = true }
                                                            return@BigRendererButton
                                                        }
                                                        openMainPanelExclusive { showWarningDialog = true }
                                                    },
                                                    // In landscape, square buttons grow with width. At large
                                                    // Android display-zoom values that pushes the utility row
                                                    // outside the panel and it gets clipped. BigRendererButton
                                                    // already provides a responsive, bounded height.
                                                    modifier = Modifier.weight(1f),
                                                    isSelected = currentRenderer == "Vulkan",
                                                    forceHighlight = true,
                                                    enabled = true,
                                                    colors = colors,
                                                    oledMode = effectiveOledMode,
                                                    iconType = "vulkan"
                                                )
                                                BigRendererButton(
                                                    text = strings["renderer.opengl"].ifEmpty { "OpenGL" },
                                                    onClick = {
                                                        performRendererHaptic()
                                                        pendingRendererName = "OpenGL"
                                                        pendingRendererSwitch = {
                                                            verboseOutput = ""
                                                            // Verbose output is shown after the success dialog is dismissed, so panels never overlap.
                                                            ShizukuHelper.runOpenGL(
                                                                context, scope, aggressiveMode,
                                                                killLauncher,
                                                                killKeyboard,
                                                            excludedAppsList.toSet(),
                                                                onRendererStatusUpdate,
                                                                if (verboseMode) { output -> verboseOutput += output } else null
                                                            )
                                                        }
                                                        successDialogMessage =
                                                            strings["main.opengl_applied"].ifEmpty { "OpenGL has been applied!" }
                                                        if (!shizukuReady && !rootAvailable) {
                                                            shizukuHelpType =
                                                                if (!shizukuRunning) "not_running" else "permission"
                                                            openMainPanelExclusive { showRootAccessDialog = true }
                                                            return@BigRendererButton
                                                        }
                                                        openMainPanelExclusive { showWarningDialog = true }
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    isSelected = false,
                                                    enabled = true,
                                                    colors = colors,
                                                    oledMode = effectiveOledMode,
                                                    iconType = "opengl"
                                                )
                                            }

                                            // Row 2: Resources | GPUWatch
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .animateContentSize(
                                                        animationSpec = spring(
                                                            dampingRatio = MotionTokens.Springs.smooth.dampingRatio,
                                                            stiffness = MotionTokens.Springs.smooth.stiffness
                                                        )
                                                    ),
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                IllustratedButton(
                                                    text = strings["renderer.library"].ifEmpty { "Library" },
                                                    onClick = {
                                                        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                                                        openMainPanelExclusive { showResourcesPanel = true }
                                                    },
                                                    modifier = if (showGpuWatchButton) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                                                    accent = false,
                                                    enabled = true,
                                                    colors = colors,
                                                    oledMode = effectiveOledMode,
                                                    iconType = "resources"
                                                )
                                                AnimatedVisibility(
                                                    modifier = Modifier.weight(1f),
                                                    visible = showGpuWatchButton,
                                                    enter = fadeIn(
                                                        animationSpec = tween(
                                                            220,
                                                            easing = MotionTokens.Easing.enter
                                                        )
                                                    ) +
                                                            expandHorizontally(
                                                                animationSpec = spring(
                                                                    dampingRatio = MotionTokens.Springs.smooth.dampingRatio,
                                                                    stiffness = MotionTokens.Springs.smooth.stiffness
                                                                ),
                                                                expandFrom = Alignment.Start
                                                            ) +
                                                            scaleIn(
                                                                initialScale = 0.88f,
                                                                animationSpec = spring(
                                                                    dampingRatio = MotionTokens.Springs.gentle.dampingRatio,
                                                                    stiffness = MotionTokens.Springs.gentle.stiffness
                                                                )
                                                            ),
                                                    exit = fadeOut(
                                                        animationSpec = tween(
                                                            160,
                                                            easing = MotionTokens.Easing.exit
                                                        )
                                                    ) +
                                                            shrinkHorizontally(
                                                                animationSpec = tween(
                                                                    180,
                                                                    easing = MotionTokens.Easing.exit
                                                                ),
                                                                shrinkTowards = Alignment.Start
                                                            ) +
                                                            scaleOut(
                                                                targetScale = 0.88f,
                                                                animationSpec = tween(
                                                                    160,
                                                                    easing = MotionTokens.Easing.exit
                                                                )
                                                            )
                                                ) {
                                                    IllustratedButton(
                                                        text = strings["renderer.show_gpuwatch"].ifEmpty { "Open GPUWatch" },
                                                        onClick = {
                                                            performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                                                            openMainPanelExclusive { showGPUWatchConfirm = true }
                                                        },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        accent = false,
                                                        enabled = true,
                                                        colors = colors,
                                                        oledMode = effectiveOledMode,
                                                        iconType = "gpuwatch"
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            }

            // ── Background blur when panels are open ─────────────────────────────
            //
            // EFFICIENT FAKE BLUR — two permanently-composed layers, zero stutter:
            //
            // Both the sharp and blurred copies of mainContent() are ALWAYS in the
            // composition tree — their modifier chains never change structurally, so
            // Compose never re-measures or re-lays-out the subtree when blur toggles.
            //
            // The blurred layer uses Modifier.blur() which, on API 31+, compiles to a
            // RenderEffect Gaussian kernel. Crucially, this kernel is built ONCE on the
            // first frame the layer is composed — it is never rebuilt mid-transition
            // because the blur radius is a compile-time constant (40.dp, never animated).
            //
            // The ONLY per-frame work during open/close is a single animateFloatAsState
            // driving two graphicsLayer alpha scalars — a trivially cheap GPU op.
            // When both panels are closed the blurred layer sits at alpha=0: the GPU
            // skips drawing it entirely (hardware layer optimization), so there is zero
            // rendering cost at rest.
            //
            // Particles are rendered AFTER this block so they always sit above the blur
            // overlay and scrim in z-order — their brightness never changes on toggle.
            //
            // Scrim is theme-aware: dark/OLED = darkening, light mode = brightening.

            // Theme-aware scrim. With blur disabled, use an almost opaque
            // themed surface so panels remain readable instead of showing the
            // home UI through a translucent wash.
            val scrimColor = if (!blurEnabled) {
                colors.background.copy(alpha = 0.98f)
            } else if (effectiveOledMode) {
                Color.Black.copy(alpha = 0.32f)
            } else {
                Color.White.copy(alpha = 0.45f)
            }

            // A scrim alone is not enough when blur is disabled: the GAMA title
            // and renderer controls still read through transparent panel space.
            // Fade the interactive home content itself while an overlay is up.
            //
            // The same fallback is needed when motion is set to Off. In that mode
            // this snaps to its final state, preserving the user's motion setting
            // while still ensuring an open panel never competes with the home UI.
            val shouldFadeMainContent = visualAnyFullPanelOpen &&
                    (!blurEnabled || animationLevel == 2)
            val mainContentAlpha by animateFloatAsState(
                targetValue = if (shouldFadeMainContent) 0f else 1f,
                animationSpec = if (animationLevel == 2) snap() else tween(
                    durationMillis = if (shouldFadeMainContent) 260 else 180,
                    easing = if (shouldFadeMainContent) {
                        MotionTokens.Easing.emphasizedDecelerate
                    } else {
                        MotionTokens.Easing.emphasized
                    }
                ),
                label = "main_content_overlay_alpha"
            )

            // Main-screen background transition.
            // When blur is disabled we only fade in the fallback scrim.
            val fallbackScrimAlpha by animateFloatAsState(
                targetValue = if ((visualAnyFullPanelOpen && !blurEnabled) || showAggressiveWarning) 1f else 0f,
                animationSpec = if (animationLevel != 2)
                    tween(
                        durationMillis = if ((visualAnyFullPanelOpen && !blurEnabled) || showAggressiveWarning) 300 else 220,
                        easing = if ((visualAnyFullPanelOpen && !blurEnabled) || showAggressiveWarning) MotionTokens.Easing.emphasizedDecelerate else MotionTokens.Easing.emphasized
                    )
                else snap(),
                label = "fallback_scrim_alpha"
            )

            // Shared animated blur radius — used by the main menu AND the sun/moon,
            // so the celestial blur transitions in perfect lock-step with the content
            // behind it (same radius, same timing, same easing).
            val mainMenuBlurRadius by animateDpAsState(
                targetValue = if (blurShouldApply && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 20.dp else 0.dp,
                animationSpec = if (animationLevel != 2) {
                    tween(
                        durationMillis = if (blurShouldApply) 400 else 330,
                        easing = if (blurShouldApply) MotionTokens.Easing.emphasizedDecelerate else MotionTokens.Easing.emphasized
                    )
                } else {
                    snap()
                },
                label = "main_menu_blur_radius"
            )

            // ── Particles — rendered BEFORE mainContent so they are BEHIND the UI ──
            // Z-order in a Box = last child is on top. Placing particles here means:
            //   • They draw behind the card/buttons — visually correct ✓
            //   • They cannot intercept touch events that belong to the UI ✓
            //   • When a panel opens the blur+scrim (below) covers particles too,
            //     which is intentional: the whole background dims together ✓
            if (appInForeground) {
                if (matrixMode) {
                    // Do NOT key this by accent color. The Matrix columns must stay alive
                    // while the color eases to the new ACCENT COLOR. Keying this block by
                    // color disposes/recreates the overlay, which makes the rain respawn.
                    MatrixRainOverlay(
                        enabled = particlesEnabled,
                        headColor = targetMatrixHeadColor,
                        rainColor = targetMatrixRainColor,
                        trailColor = targetMatrixTrailColor,
                        backgroundColor = Color.Black,
                        backgroundAlpha = matrixBgAlpha,
                        speedLevel = matrixSpeed,
                        densityLevel = matrixDensity,
                        fontSizeLevel = matrixFontSize,
                        fadeLength = matrixFadeLength
                    )
                }

                // Only compose particle/matrix simulations while the app is visible.
                // When GAMA goes to recents/background, these composables are removed,
                // which cancels their LaunchedEffect loops, sensor listeners, and frame clocks.
                // Coming back reloads them cleanly, which is cheaper than burning battery hidden.
                ParticlesOverlay(
                    enabled = particlesEnabled && !matrixMode,
                    color = effectsAccent,
                    particleSpeed = particleSpeed,
                    parallaxEnabled = particleParallaxEnabled,
                    particleCount = particleCount,
                    particleCountCustom = particleCountCustom.toIntOrNull() ?: 150,
                    parallaxSensitivity = animatedParallaxSensitivity,
                    starMode = false,
                    timeModeEnabled = particleTimeMode,
                    timeOffsetHours = timeOffsetHours,
                    blurRadius = mainMenuBlurRadius,
                    isLandscape = isLandscape,
                    nativeRefreshRate = particleNativeRefreshRate,
                    quarterRefreshRate = particleQuarterRefreshRate
                )
            }

            // ── Main content layer ────────────────────────────────────────────
            // Render the main menu ONCE. When blur is unavailable or motion is
            // disabled, its opacity transitions independently so foreground panel
            // copy stays readable instead of competing with the home controls.
            //
            // The previous sharp/blurred crossfade rendered mainContent() twice and
            // animated alpha every time a panel opened. That made the home screen look
            // like it was re-entering or doing a tiny animation behind panels.
            //
            // The menu should stay physically still. Panels may animate; the menu should not.
            val mainContentModifier =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && mainMenuBlurRadius > 0.1.dp) {
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer(alpha = mainContentAlpha)
                        .blur(radius = mainMenuBlurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                } else {
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer(alpha = mainContentAlpha)
                }

            Box(modifier = mainContentModifier) {
                mainContent()
            }

            // Scrim — dims everything (including particles) behind open panels.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = fallbackScrimAlpha)
                    .background(scrimColor)
            )


            ShizukuHelpDialog(
                visible = showShizukuHelp,
                helpType = shizukuHelpType,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showShizukuHelp = false
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                rootAvailable = rootAvailable
            )

            // Root vs Shizuku choice — shown when NO backend is ready and the user
            // taps a renderer button. "Use Root" probes su and, if it works, runs
            // the pending switch straight away.
            RootAccessDialog(
                visible = showRootAccessDialog,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showRootAccessDialog = false
                    pendingRendererSwitch = null
                    pendingRendererName = ""
                },
                onUseShizuku = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showRootAccessDialog = false
                    openMainPanelExclusive { showShizukuHelp = true }
                },
                onUseRoot = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showRootAccessDialog = false
                    scope.launch {
                        rootAvailable = ShizukuHelper.refreshRootAvailability()
                        if (rootAvailable) {
                            openMainPanelExclusive { showWarningDialog = true }
                        } else {
                            pendingRendererSwitch = null
                            pendingRendererName = ""
                            Toast.makeText(
                                context,
                                strings["main.no_root_access"].ifEmpty { "No root access detected — install Magisk/KernelSU, or use Shizuku" },
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                backendName = shizukuBackendName
            )

            // Dialogs
            WarningDialog(
                visible = showWarningDialog,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showWarningDialog = false
                    pendingRendererSwitch = null
                    pendingRendererName = ""
                },
                onContinue = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showWarningDialog = false

                    // The controller commits state only after it has read the
                    // requested property back successfully. Keep the UI on the
                    // observed renderer until then.
                    activeRendererTarget = pendingRendererName

                    // 2. Reset commandOutput so LaunchedEffect below can detect its arrival,
                    //    then open the dialog in the in-progress (spinner) state.
                    commandOutput = ""
                    rendererSwitching = true
                    showSuccessDialog = true

                    // 3. Execute the actual switch — commandOutput will be set by ShizukuHelper
                    //    when the shell command finishes, which flips rendererSwitching off.
                    pendingRendererSwitch?.invoke()
                    pendingRendererSwitch = null

                    // A backend can disappear between the preflight check and
                    // guardedLaunch(). In that race no status callback may arrive;
                    // never leave the modal spinner blocking the app forever.
                    scope.launch {
                        delay(15_000L)
                        if (rendererSwitching && commandOutput.isEmpty()) {
                            rendererSwitching = false
                            successDialogMessage = strings["main.renderer_switch_timed_out"].ifEmpty {
                                "Renderer switch timed out — please try again."
                            }
                            currentRenderer = prefs.getString("last_renderer", "OpenGL") ?: "OpenGL"
                        }
                    }

                    pendingRendererName = ""
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground
            )

            // NEW: Success Dialog
            SuccessDialog(
                visible = showSuccessDialog,
                message = successDialogMessage,
                userName = userName,
                isSwitching = rendererSwitching,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    val shouldShowVerbose = verboseMode && verboseOutput.isNotBlank()
                    showSuccessDialog = false
                    rendererSwitching = false
                    if (shouldShowVerbose) showVerbosePanel = true
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground
            )

            // DeveloperMenuDialog removed — it was never reachable (showDeveloperMenu was never
            // set to true). All developer tooling lives in DeveloperPanel (showDeveloper).

            GitHubDialog(
                visible = showGitHubDialog,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showGitHubDialog = false
                },
                onVisitGitHub = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/popovicialinc/gama"))
                    context.startActivity(intent)
                    showGitHubDialog = false
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground
            )

            ResourcesPanel(
                visible = showResourcesPanel,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showResourcesPanel = false
                },
                onLinkSelected = { url, label, description ->
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    pendingExternalLink = url
                    pendingExternalLinkLabel = label
                    pendingExternalLinkDescription = description
                    showExternalLinkConfirm = true
                },
                onInfoRequested = { title, body, copyText, guideUrl ->
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    integrationInfoTitle = title
                    integrationInfoBody = body
                    integrationInfoCopyText = copyText
                    integrationInfoGuideUrl = guideUrl
                    showIntegrationInfoDialog = true
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                isBlurred = showExternalLinkConfirm || showIntegrationInfoDialog,
                oledMode = effectiveOledMode
            )

            ExternalLinkConfirmDialog(
                visible = showExternalLinkConfirm,
                label = pendingExternalLinkLabel,
                description = pendingExternalLinkDescription,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showExternalLinkConfirm = false
                },
                onConfirm = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(pendingExternalLink))
                    context.startActivity(intent)
                    showExternalLinkConfirm = false
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground
            )



            SettingsPanel(
                visible = showSettings && !showSettingsSearch && !showHapticsPanel && !showAppearance && !showColorCustomization && !showGradient && !showSystem && !showEffects && !showParticles && !showNotifications && !showBackup && !showCrashLog && !showRendererPanel && !showLanguage && !showHapticsPanel,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showSettings = false
                },
                onSearchClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showSettingsSearch = true
                },
                onAppearanceClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showAppearance = true
                },
                onRendererClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showRendererPanel = true
                },
                onSystemClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showSystem = true
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                performHaptic = { performHaptic() },
                oledMode = effectiveOledMode
            )

            SettingsSearchPanel(
                visible = showSettingsSearch,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showSettingsSearch = false
                },
                onAppearanceClick = { showSettingsSearch = false; showAppearance = true },
                onColorCustomizationClick = { showColorCustomization = true },
                onGradientClick = {
                    showSettingsSearch = false
                    showColorCustomization = true
                    showGradient = true
                },
                onEffectsClick = { showEffects = true },
                onParticlesClick = { showParticles = true },
                onRendererClick = { showSettingsSearch = false; showRendererPanel = true },
                onSystemClick = { showSettingsSearch = false; showSystem = true },
                onNotificationsClick = { showSettingsSearch = false; showSystem = true; showNotifications = true },
                onBackupClick = { showSettingsSearch = false; showSystem = true; showBackup = true },
                onCrashLogClick = { showSettingsSearch = false; showSystem = true; showCrashLog = true },
                onLanguageClick = { showSettingsSearch = false; showSystem = true; showLanguage = true },
                onHapticsClick = { showSettingsSearch = false; showSystem = true; showHapticsPanel = true },
                // Visuals / Appearance
                themePreference = themePreference,
                onThemeChange = { themePreference = it; savePreferences() },
                animationLevel = animationLevel,
                onAnimationLevelChange = { animationLevel = it; savePreferences() },
                uiScale = uiScale,
                onUiScaleChange = { uiScale = it; savePreferences() },
                staggerEnabled = staggerEnabled,
                onStaggerEnabledChange = { staggerEnabled = it; savePreferences() },
                backButtonAvoidanceEnabled = backButtonAvoidanceEnabled,
                onBackButtonAvoidanceEnabledChange = { backButtonAvoidanceEnabled = it; savePreferences() },
                backButtonInversed = backButtonInversed,
                onBackButtonInversedChange = { backButtonInversed = it; savePreferences() },
                shadowsEnabled = shadowsEnabled,
                onShadowsEnabledChange = { shadowsEnabled = it; savePreferences() },
                // Colors
                oledMode = effectiveOledMode,
                darkModeActive = effectiveOledMode,
                onOledModeChange = { enabled ->
                    themePreference = if (enabled) 1 else 2
                    savePreferences()
                },
                useDynamicColor = useDynamicColor,
                onDynamicColorChange = { useDynamicColor = it; savePreferences() },
                advancedColorPicker = advancedColorPicker,
                onAdvancedColorPickerChange = { advancedColorPicker = it; savePreferences() },
                gradientEnabled = gradientEnabled,
                onGradientChange = { gradientEnabled = it; savePreferences() },
                customAccentColor = customAccentColor,
                onAccentColorChange = { color ->
                    customAccentColor = sanitizeAccentColorForTheme(color, effectiveOledMode)
                    savePreferencesDebounced()
                },
                customGradientStart = customGradientStart,
                onGradientStartChange = { color -> customGradientStart = color; savePreferencesDebounced() },
                customGradientEnd = customGradientEnd,
                onGradientEndChange = { color -> customGradientEnd = color; savePreferencesDebounced() },
                // Effects
                blurEnabled = blurEnabled,
                onBlurChange = { blurEnabled = it; savePreferences() },
                // Particles
                particlesEnabled = particlesEnabled,
                onParticlesChange = { particlesEnabled = it; savePreferences() },
                matrixMode = matrixMode,
                onMatrixModeChange = { matrixMode = it; savePreferences() },
                particleStarMode = particleStarMode,
                onParticleStarModeChange = { particleStarMode = it; savePreferences() },
                particleTimeMode = particleTimeMode,
                onParticleTimeModeChange = { particleTimeMode = it; savePreferences() },
                particleParallaxEnabled = particleParallaxEnabled,
                onParticleParallaxEnabledChange = { particleParallaxEnabled = it; savePreferences() },
                particleParallaxSensitivity = particleParallaxSensitivity,
                onParticleParallaxSensitivityChange = { particleParallaxSensitivity = it; savePreferences() },
                particleCount = particleCount,
                onParticleCountChange = { particleCount = it; savePreferences() },
                particleSpeed = particleSpeed,
                onParticleSpeedChange = { particleSpeed = it; savePreferences() },
                nativeRefreshRate = particleNativeRefreshRate,
                onNativeRefreshRateChange = { particleNativeRefreshRate = it; savePreferences() },
                quarterRefreshRate = particleQuarterRefreshRate,
                onQuarterRefreshRateChange = { particleQuarterRefreshRate = it; savePreferences() },
                matrixSpeed = matrixSpeed,
                onMatrixSpeedChange = { matrixSpeed = it; savePreferences() },
                matrixDensity = matrixDensity,
                onMatrixDensityChange = { matrixDensity = it; savePreferences() },
                matrixFontSize = matrixFontSize,
                onMatrixFontSizeChange = { matrixFontSize = it; savePreferences() },
                matrixFadeLength = matrixFadeLength,
                onMatrixFadeLengthChange = { matrixFadeLength = it; savePreferences() },
                // Renderer
                aggressiveMode = aggressiveMode,
                onAggressiveModeChange = { enabled ->
                    // The search/settings route must use the same safety gate as
                    // the renderer panel; otherwise users could enable the
                    // all-packages force-stop mode without seeing its warning.
                    if (enabled && !aggressiveModeConfirmed) {
                        aggressiveMode = true
                        showAggressiveWarning = true
                    } else {
                        aggressiveMode = enabled
                        if (!enabled) aggressiveModeConfirmed = false
                        savePreferences()
                    }
                },
                killLauncher = killLauncher,
                onKillLauncherChange = { killLauncher = it; savePreferences() },
                killKeyboard = killKeyboard,
                onKillKeyboardChange = { killKeyboard = it; savePreferences() },
                showGpuWatchButton = showGpuWatchButton,
                onShowGpuWatchButtonChange = { showGpuWatchButton = it; savePreferences() },
                // System / App
                verboseMode = verboseMode,
                onVerboseModeChange = { verboseMode = it; savePreferences() },
                dismissOnClickOutside = dismissOnClickOutside,
                onDismissOnClickOutsideChange = { dismissOnClickOutside = it; savePreferences() },
                notificationsEnabled = notificationsEnabled,
                onNotificationsEnabledChange = {
                    notificationsEnabled = it
                    savePreferences()
                    OpenGLReminderScheduler.sync(context, it)
                },
                notifIntervalIndex = notifIntervalIndex,
                onNotifIntervalChange = {
                    notifIntervalIndex = it
                    savePreferences()
                    OpenGLReminderScheduler.sync(context, notificationsEnabled)
                },
                // Common
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                colors = colors,
                cardBackground = cardBackground,
                performHaptic = { performHaptic(HapticFeedbackConstants.CLOCK_TICK) }
            )



            HapticsPanel(
                visible = showHapticsPanel,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showHapticsPanel = false
                },
                hapticsEnabled = hapticsEnabled,
                onHapticsEnabledChange = { hapticsEnabled = it; savePreferences() },
                regularEnabled = hapticsRegularEnabled,
                onRegularEnabledChange = { hapticsRegularEnabled = it; savePreferences() },
                holdEnabled = hapticsHoldEnabled,
                onHoldEnabledChange = { hapticsHoldEnabled = it; savePreferences() },
                rendererEnabled = hapticsRendererEnabled,
                onRendererEnabledChange = { hapticsRendererEnabled = it; savePreferences() },
                languageEnabled = hapticsLanguageEnabled,
                onLanguageEnabledChange = { hapticsLanguageEnabled = it; savePreferences() },
                bounceEnabled = hapticsBounceEnabled,
                onBounceEnabledChange = { hapticsBounceEnabled = it; savePreferences() },
                bounceReturnEnabled = hapticsReturnEnabled,
                onBounceReturnEnabledChange = { hapticsReturnEnabled = it; savePreferences() },
                regularStrength = hapticsRegularStrength,
                onRegularStrengthChange = { hapticsRegularStrength = it; savePreferencesDebounced() },
                holdStrength = hapticsHoldStrength,
                onHoldStrengthChange = { hapticsHoldStrength = it; savePreferencesDebounced() },
                rendererStrength = hapticsRendererStrength,
                onRendererStrengthChange = { hapticsRendererStrength = it; savePreferencesDebounced() },
                languageStrength = hapticsLanguageStrength,
                onLanguageStrengthChange = { hapticsLanguageStrength = it; savePreferencesDebounced() },
                bounceStrength = hapticsBounceStrength,
                onBounceStrengthChange = { hapticsBounceStrength = it; savePreferencesDebounced() },
                bounceReturnStrength = hapticsBounceReturnStrength,
                onBounceReturnStrengthChange = { hapticsBounceReturnStrength = it; savePreferencesDebounced() },
                onResetHaptics = {
                    GamaHaptics.resetToDefaults(context)
                    hapticsEnabled = GamaHaptics.DEFAULT_ENABLED
                    hapticsRegularEnabled = GamaHaptics.DEFAULT_REGULAR_ENABLED
                    hapticsHoldEnabled = GamaHaptics.DEFAULT_HOLD_ENABLED
                    hapticsRendererEnabled = GamaHaptics.DEFAULT_RENDERER_ENABLED
                    hapticsLanguageEnabled = GamaHaptics.DEFAULT_LANGUAGE_ENABLED
                    hapticsBounceEnabled = GamaHaptics.DEFAULT_BOUNCE_ENABLED
                    hapticsReturnEnabled = GamaHaptics.DEFAULT_BOUNCE_RETURN_ENABLED
                    hapticsRegularStrength = GamaHaptics.DEFAULT_REGULAR_STRENGTH
                    hapticsHoldStrength = GamaHaptics.DEFAULT_HOLD_STRENGTH
                    hapticsRendererStrength = GamaHaptics.DEFAULT_RENDERER_STRENGTH
                    hapticsLanguageStrength = GamaHaptics.DEFAULT_LANGUAGE_STRENGTH
                    hapticsBounceStrength = GamaHaptics.DEFAULT_BOUNCE_STRENGTH
                    hapticsBounceReturnStrength = GamaHaptics.DEFAULT_BOUNCE_RETURN_STRENGTH
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                oledMode = effectiveOledMode,
                performHaptic = { performHaptic(HapticFeedbackConstants.CONTEXT_CLICK) }
            )

            // Blur wrapper for RendererPanel when Aggressive Warning is shown.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (showAggressiveWarning && animationLevel != 2 &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        )
                            Modifier.blur(20.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                        else Modifier
                    )
            ) {
                RendererPanel(
                    visible = showRendererPanel,
                    onDismiss = {
                        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                        showRendererPanel = false
                    },
                    aggressiveMode = aggressiveMode,
                    onAggressiveModeChange = { enabled ->
                        if (enabled && !aggressiveModeConfirmed) {
                            aggressiveMode = true
                            performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                            showAggressiveWarning = true
                        } else {
                            aggressiveMode = enabled
                            savePreferences()
                            if (!enabled) aggressiveModeConfirmed = false
                        }
                    },
                    killLauncher = killLauncher,
                    onKillLauncherChange = { enabled ->
                        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                        killLauncher = enabled
                        savePreferences()
                    },
                    killKeyboard = killKeyboard,
                    onKillKeyboardChange = { enabled ->
                        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                        killKeyboard = enabled
                        savePreferences()
                    },
                    showGpuWatchButton = showGpuWatchButton,
                    onShowGpuWatchButtonChange = { showGpuWatchButton = it; savePreferences() },
                    onShowAggressiveWarning = {
                        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                        showAggressiveWarning = true
                    },
                    isSmallScreen = isSmallScreen,
                    isLandscape = isLandscape,
                    isTablet = isTablet,
                    colors = colors,
                    cardBackground = cardBackground,
                    oledMode = effectiveOledMode,
                    performHaptic = { performHaptic(HapticFeedbackConstants.CONTEXT_CLICK) }
                )
            } // End of blur wrapper
            // ── Panels live inside the outer Box so they share the blur/clip layer ──
            //    (previously they were placed after the Box's closing brace, which meant
            //    Modifier.blur never covered their backgrounds and z-order was fragile)

            SystemPanel(
                visible = showSystem && !showHapticsPanel && !showNotifications && !showBackup && !showCrashLog && !showLanguage,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showSystem = false
                },
                onHapticsClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showHapticsPanel = true
                },
                verboseMode = verboseMode,
                onVerboseModeChange = { enabled ->
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    verboseMode = enabled
                    savePreferences()
                },
                dismissOnClickOutside = dismissOnClickOutside,
                onDismissOnClickOutsideChange = { enabled ->
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    dismissOnClickOutside = enabled
                    savePreferences()
                },
                backButtonInversed = backButtonInversed,
                onBackButtonInversedChange = { enabled ->
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    backButtonInversed = enabled
                    savePreferences()
                },
                onResetFloatingButtonPositions = {
                    floatingButtonLivePositions.clearAll()
                    floatingLeftX = 0.30f
                    floatingLeftY = 0.94f
                    floatingRightX = 0.70f
                    floatingRightY = 0.94f
                    landscapeFloatingLeftX = 0.12f
                    landscapeFloatingLeftY = 0.88f
                    landscapeFloatingRightX = 0.88f
                    landscapeFloatingRightY = 0.88f
                    settingsButtonX = 0.84f
                    settingsButtonY = 0.94f
                    landscapeSettingsButtonX = 0.06f
                    landscapeSettingsButtonY = 0.94f
                    savePreferences()
                },
                onNotificationsClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showNotifications = true
                },
                onBackupClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showBackup = true
                },
                onCrashLogClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showCrashLog = true
                },
                onLanguageClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showLanguage = true
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                oledMode = effectiveOledMode,
                performHaptic = { performHaptic(HapticFeedbackConstants.CONTEXT_CLICK) }
            )

            // Notifications panel (sub-panel of Functionality)
            NotificationsPanel(
                visible = showNotifications,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showNotifications = false
                },
                notificationsEnabled = notificationsEnabled,
                onNotificationsEnabledChange = { enabled ->
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    notificationsEnabled = enabled
                    savePreferences()
                    OpenGLReminderScheduler.sync(context, enabled)
                },
                notifIntervalIndex = notifIntervalIndex,
                onNotifIntervalChange = { idx ->
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    notifIntervalIndex = idx
                    savePreferences()
                    OpenGLReminderScheduler.sync(context, notificationsEnabled)
                },
                hasPermission = hasNotifPermission.value,
                onRequestPermission = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    onRequestNotificationPermission()
                },
                onTestNotification = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    if (!hasNotifPermission.value) {
                        onRequestNotificationPermission()
                        android.widget.Toast.makeText(
                            context,
                            strings["notifications.permission_first"].ifEmpty { "Grant notification permission first" },
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        val sent = sendOpenGLReminderNotification(context, userName)
                        android.widget.Toast.makeText(
                            context,
                            if (sent) strings["notifications.test_sent"].ifEmpty { "Test notification sent! ✅" }
                            else strings["notifications.send_failed"].ifEmpty { "Failed to send notification" },
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                currentRenderer = currentRenderer,
                userName = userName,
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                oledMode = effectiveOledMode
            )

            // ── Backup & Restore panel ────────────────────────────────────────
            BackupPanel(
                visible = showBackup,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showBackup = false
                },
                onExport = {
                    scope.launch {
                        val json = try {
                            BackupHelper.export(prefs)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(
                                context,
                                strings["backup.export_failed"].ifEmpty { "Export failed: %s" }.replace("%s", e.message ?: ""),
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                            return@launch
                        }
                        onExportBackup(json, BackupHelper.buildFileName())
                    }
                },
                onImport = {
                    onImportBackup { json ->
                        scope.launch {
                            try {
                                val msg = BackupHelper.import(prefs, json)
                                // Reload all prefs-backed state from updated SharedPreferences
                                animationLevel = prefs.getInt("animation_level", 0)
                                animationSpeed = prefs.getInt("animation_speed", 1)
                                gradientEnabled = prefs.getBoolean("gradient_enabled", true)
                                blurEnabled = prefs.getBoolean("blur_enabled", true)
                                particlesEnabled = prefs.getBoolean("particles_enabled", true)
                                particleSpeed = prefs.getInt("particle_speed", 1)
                                particleParallaxEnabled = prefs.getBoolean("particle_parallax_enabled", true)
                                particleParallaxSensitivity = prefs.getInt("particle_parallax_sensitivity", 0)
                                particleStarMode = prefs.getBoolean("particle_star_mode", false)
                                particleTimeMode = prefs.getBoolean("particle_time_mode", true)
                                timeOffsetHours = prefs.getFloat("time_offset_hours", 0f)
                                particleCount = prefs.getInt("particle_count", 0)
                                particleCountCustom = prefs.getInt("particle_count_custom", 150).toString()
                                themePreference = prefs.getInt("theme_preference", 0)
                                uiScale = prefs.getInt("ui_scale", 1)
                                verboseMode = prefs.getBoolean("verbose_mode", false)
                                aggressiveMode = prefs.getBoolean("aggressive_mode", false)
                                killLauncher = prefs.getBoolean("kill_launcher", false)
                                killKeyboard = prefs.getBoolean("kill_keyboard", false)
                                staggerEnabled = prefs.getBoolean("stagger_enabled", true)
                                backButtonAvoidanceEnabled = prefs.getBoolean("back_button_avoidance_enabled", true)
                                particleNativeRefreshRate = prefs.getBoolean("particle_native_refresh_rate", false)
                                particleQuarterRefreshRate = prefs.getBoolean("particle_quarter_refresh_rate", false)
                                matrixMode = prefs.getBoolean("matrix_mode", true)
                                matrixSpeed = prefs.getInt("matrix_speed", 1)
                                matrixDensity = prefs.getInt("matrix_density", 1)
                                matrixFontSize = prefs.getInt("matrix_font_size", 1)
                                matrixFadeLength = prefs.getInt("matrix_fade_length", 1)
                                matrixBgAlpha = prefs.getFloat("matrix_bg_alpha", 0f)
                                useDynamicColor = prefs.getBoolean("use_dynamic_color", true)
                                customAccentColor = Color(prefs.getInt("custom_accent", 0xFF4895EF.toInt()))
                                customGradientStart = Color(prefs.getInt("custom_gradient_start", 0xFF0A2540.toInt()))
                                customGradientEnd = Color(prefs.getInt("custom_gradient_end", 0xFF000000.toInt()))
                                dismissOnClickOutside = prefs.getBoolean("dismiss_on_click_outside", true)
                                notificationsEnabled = prefs.getBoolean("notif_enabled", false)
                                notifIntervalIndex = prefs.getInt("notif_interval_idx", 2)
                                userName = prefs.getString("user_name", "") ?: ""
                                shadowsEnabled = prefs.getBoolean("shadows_enabled", true)
                                backButtonInversed = prefs.getBoolean("back_button_inversed", false)
                                showGpuWatchButton = prefs.getBoolean("show_gpuwatch_button", false)
                                advancedColorPicker = prefs.getBoolean("advanced_color_picker", false)
                                floatingLeftX = prefs.getFloat("floating_left_x", 0.30f).coerceIn(0.16f, 0.84f)
                                floatingLeftY = prefs.getFloat("floating_left_y", 0.94f).coerceIn(0.50f, 0.96f)
                                floatingRightX = prefs.getFloat("floating_right_x", 0.70f).coerceIn(0.16f, 0.84f)
                                floatingRightY = prefs.getFloat("floating_right_y", 0.94f).coerceIn(0.50f, 0.96f)
                                landscapeFloatingLeftX = prefs.getFloat("landscape_floating_left_x", 0.12f).coerceIn(0.04f, 0.96f)
                                landscapeFloatingLeftY = prefs.getFloat("landscape_floating_left_y", 0.88f).coerceIn(0.50f, 0.96f)
                                landscapeFloatingRightX = prefs.getFloat("landscape_floating_right_x", 0.88f).coerceIn(0.04f, 0.96f)
                                landscapeFloatingRightY = prefs.getFloat("landscape_floating_right_y", 0.88f).coerceIn(0.50f, 0.96f)
                                settingsButtonX = prefs.getFloat("settings_button_x", 0.84f).coerceIn(0.16f, 0.84f)
                                settingsButtonY = prefs.getFloat("settings_button_y", 0.94f).coerceIn(0.50f, 0.96f)
                                landscapeSettingsButtonX = prefs.getFloat("landscape_settings_button_x", 0.06f).coerceIn(0.04f, 0.96f)
                                landscapeSettingsButtonY = prefs.getFloat("landscape_settings_button_y", 0.94f).coerceIn(0.50f, 0.96f)
                                languageCodeState.value = LocalizationManager.getSavedCode(prefs)
                                OpenGLReminderScheduler.sync(context, notificationsEnabled)
                                // Haptics — restored by BackupHelper, so re-read here too
                                hapticsEnabled = prefs.getBoolean(GamaHaptics.PREF_ENABLED, GamaHaptics.DEFAULT_ENABLED)
                                hapticsRegularEnabled = prefs.getBoolean(GamaHaptics.PREF_REGULAR_ENABLED, GamaHaptics.DEFAULT_REGULAR_ENABLED)
                                hapticsHoldEnabled = prefs.getBoolean(GamaHaptics.PREF_HOLD_ENABLED, GamaHaptics.DEFAULT_HOLD_ENABLED)
                                hapticsRendererEnabled = prefs.getBoolean(GamaHaptics.PREF_RENDERER_ENABLED, GamaHaptics.DEFAULT_RENDERER_ENABLED)
                                hapticsLanguageEnabled = prefs.getBoolean(GamaHaptics.PREF_LANGUAGE_ENABLED, GamaHaptics.DEFAULT_LANGUAGE_ENABLED)
                                hapticsBounceEnabled = prefs.getBoolean(GamaHaptics.PREF_BOUNCE_ENABLED, GamaHaptics.DEFAULT_BOUNCE_ENABLED)
                                hapticsReturnEnabled = prefs.getBoolean(GamaHaptics.PREF_BOUNCE_RETURN_ENABLED, GamaHaptics.DEFAULT_BOUNCE_RETURN_ENABLED)
                                hapticsRegularStrength = prefs.getInt(GamaHaptics.PREF_REGULAR_STRENGTH, GamaHaptics.DEFAULT_REGULAR_STRENGTH)
                                hapticsHoldStrength = prefs.getInt(GamaHaptics.PREF_HOLD_STRENGTH, GamaHaptics.DEFAULT_HOLD_STRENGTH)
                                hapticsRendererStrength = prefs.getInt(GamaHaptics.PREF_RENDERER_STRENGTH, GamaHaptics.DEFAULT_RENDERER_STRENGTH)
                                hapticsLanguageStrength = prefs.getInt(GamaHaptics.PREF_LANGUAGE_STRENGTH, GamaHaptics.DEFAULT_LANGUAGE_STRENGTH)
                                hapticsBounceStrength = prefs.getInt(GamaHaptics.PREF_BOUNCE_STRENGTH, GamaHaptics.DEFAULT_BOUNCE_STRENGTH)
                                hapticsBounceReturnStrength = prefs.getInt(GamaHaptics.PREF_BOUNCE_RETURN_STRENGTH, GamaHaptics.DEFAULT_BOUNCE_RETURN_STRENGTH)
                                currentRenderer = prefs.getString(RendererState.PREF_LAST_RENDERER, currentRenderer) ?: currentRenderer
                                lastSwitchTime = prefs.getLong(RendererState.PREF_LAST_SWITCH_TIME, 0L)
                                excludedAppsList.clear()
                                excludedAppsList.addAll(prefs.getStringSet("excluded_apps", emptySet()) ?: emptySet())
                                val localizedMsg = if (msg.contains("invalid entries skipped")) {
                                    val numbers = Regex("\\d+").findAll(msg).map { it.value }.toList()
                                    strings["backup.restored_with_skips"]
                                        .ifEmpty { "Restored %d settings (%d invalid entries skipped)." }
                                        .replace("%d", numbers.getOrElse(0) { "" })
                                        .replace("%d", numbers.getOrElse(1) { "" })
                                } else {
                                    strings["backup.restored_count"]
                                        .ifEmpty { "Restored %d settings successfully." }
                                        .replace("%d", Regex("\\d+").find(msg)?.value ?: "")
                                }
                                android.widget.Toast.makeText(context, localizedMsg, android.widget.Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                val reason = when (e.message) {
                                    "This doesn't look like a GAMA backup file." ->
                                        strings["backup.invalid_file"].ifEmpty { "This doesn't look like a GAMA backup file." }
                                    "This backup was created by a newer version of GAMA." ->
                                        strings["backup.newer_version"].ifEmpty { "This backup was created by a newer version of GAMA." }
                                    else -> e.message ?: ""
                                }
                                android.widget.Toast.makeText(
                                    context,
                                    strings["backup.import_failed"].ifEmpty { "Import failed: %s" }.replace("%s", reason),
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                oledMode = effectiveOledMode
            )

            // ── Crash Log panel ───────────────────────────────────────────────
            CrashLogPanel(
                visible = showCrashLog,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showCrashLog = false
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                oledMode = effectiveOledMode,
                onExportCrashLog = onExportCrashLog,
                onExportSupportBundle = {
                    onExportCrashLog(SupportBundle.build(context), SupportBundle.fileName())
                }
            )

            // ── Language panel ─────────────────────────────────────────────
            LanguagePanel(
                visible = showLanguage,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showLanguage = false
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                performHaptic = { performHaptic() },
                oledMode = effectiveOledMode
            )

            // ── Info dialog for Quick Settings (shown from Resources panel) ──
            IntegrationInfoDialog(
                visible = showIntegrationInfoDialog,
                title = integrationInfoTitle,
                body = integrationInfoBody,
                copyText = integrationInfoCopyText,
                guideUrl = integrationInfoGuideUrl,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showIntegrationInfoDialog = false
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground
            )

            DeveloperPanel(
                visible = showDeveloper,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showDeveloper = false
                },
                onTestNotification = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)

                    // Check if permission is granted using the lifecycle-aware state
                    if (!hasNotifPermission.value) {
                        // Request permission
                        onRequestNotificationPermission()
                        android.widget.Toast.makeText(
                            context,
                            strings["notifications.grant_to_test"].ifEmpty { "Please grant notification permission to test notifications" },
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    } else {
                        // Send test notification
                        val success = ShizukuHelper.sendTestNotification(context, userName)
                        if (success) {
                            android.widget.Toast.makeText(
                                context,
                                strings["notifications.test_sent_plain"].ifEmpty { "Test notification sent!" },
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            android.widget.Toast.makeText(
                                context,
                                strings["notifications.send_failed"].ifEmpty { "Failed to send notification" },
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onTestBootNotification = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    sendBootNotification(
                        context = context,
                        userName = userName,
                        onRequestPermission = {
                            onRequestNotificationPermission()
                            android.widget.Toast.makeText(
                                context,
                                strings["notifications.grant_to_test"].ifEmpty { "Please grant notification permission to test notifications" },
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                    android.widget.Toast.makeText(context, strings["notifications.boot_sent"].ifEmpty { "Boot notification sent!" }, android.widget.Toast.LENGTH_SHORT)
                        .show()
                },
                userName = userName,
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                oledMode = effectiveOledMode,
                performHaptic = { performHaptic(HapticFeedbackConstants.CONTEXT_CLICK) },
                timeOffsetHours = timeOffsetHours,
                onTimeOffsetChange = { value ->
                    timeOffsetHours = value
                    savePreferencesDebounced()
                }
            )


            VisualEffectsPanel(
                visible = showAppearance && !showEffects && !showColorCustomization && !showGradient && !showParticles,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showAppearance = false
                },
                themePreference = themePreference,
                onThemeChange = { newTheme ->
                    themePreference = newTheme
                    savePreferences()
                },
                animationLevel = animationLevel,
                onAnimationLevelChange = { newLevel ->
                    animationLevel = newLevel
                    savePreferences()
                },
                uiScale = uiScale,
                onUiScaleChange = { newSize ->
                    uiScale = newSize
                    savePreferences()
                },
                userName = userName,
                onUserNameChange = { newName ->
                    userName = newName
                    prefs.edit().putString("user_name", newName).apply()
                },
                oledMode = effectiveOledMode,
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                staggerEnabled = staggerEnabled,
                onStaggerEnabledChange = { staggerEnabled = it; savePreferences() },
                backButtonAvoidanceEnabled = backButtonAvoidanceEnabled,
                onBackButtonAvoidanceEnabledChange = { backButtonAvoidanceEnabled = it; savePreferences() },
                shadowsEnabled = shadowsEnabled,
                onShadowsEnabledChange = { shadowsEnabled = it; savePreferences() },
                onEffectsClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showEffects = true
                },
                onColorsClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showColorCustomization = true
                },
                onOledModeChange = { enabled ->
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    themePreference = if (enabled) 1 else 2
                    savePreferences()
                },
                performHaptic = { performHaptic(HapticFeedbackConstants.CLOCK_TICK) }
            )

            EffectsPanel(
                visible = showEffects && !showParticles && !showParticlesAppearance && !showParticlesMotion && !showParticlesPerformance,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showEffects = false
                },
                blurEnabled = blurEnabled,
                onBlurChange = { value ->
                    performHaptic(HapticFeedbackConstants.CLOCK_TICK)
                    blurEnabled = value
                    savePreferences()
                },
                shadowsEnabled = shadowsEnabled,
                onShadowsEnabledChange = { value ->
                    performHaptic(HapticFeedbackConstants.CLOCK_TICK)
                    shadowsEnabled = value
                    savePreferences()
                },
                onParticlesClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showParticles = true
                },
                userName = userName,
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                performHaptic = { performHaptic(HapticFeedbackConstants.CLOCK_TICK) },
                oledMode = effectiveOledMode
            )

            // ── Particles hub — visible when no sub-panel is open ─────────────
            ParticlesPanel(
                visible = showParticles && !showParticlesAppearance && !showParticlesMotion
                        && !showParticlesPerformance && !showMatrixSettings && !showMatrixAppearance
                        && !showMatrixMotion && !showParticlesSettings,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showParticles = false
                },
                particlesEnabled = particlesEnabled,
                onParticlesChange = { value ->
                    performHaptic(HapticFeedbackConstants.CLOCK_TICK)
                    particlesEnabled = value
                    savePreferences()
                },
                matrixMode = matrixMode,
                onMatrixModeChange = { value ->
                    matrixMode = value
                    savePreferences()
                },
                onParticlesSettingsClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showParticlesSettings = true
                },
                onMatrixSettingsClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showMatrixSettings = true
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                performHaptic = { performHaptic(HapticFeedbackConstants.CLOCK_TICK) },
                oledMode = effectiveOledMode
            )

            // ── Particles Settings sub-panel ──────────────────────────────────
            ParticlesSettingsPanel(
                visible = showParticlesSettings,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showParticlesSettings = false
                },
                particlesEnabled = particlesEnabled,
                onAppearanceClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showParticlesSettings = false
                    showParticlesAppearance = true
                },
                onMotionClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showParticlesSettings = false
                    showParticlesMotion = true
                },
                onPerformanceClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showParticlesSettings = false
                    showParticlesPerformance = true
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                colors = colors,
                cardBackground = cardBackground,
                performHaptic = { performHaptic(HapticFeedbackConstants.CLOCK_TICK) },
                oledMode = effectiveOledMode
            )

            // ── Particles › Appearance sub-panel ─────────────────────────────
            ParticlesAppearancePanel(
                visible = showParticlesAppearance,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showParticlesAppearance = false
                    showParticlesSettings = true
                },
                particlesEnabled = particlesEnabled,
                particleStarMode = particleStarMode,
                onParticleStarModeChange = { value ->
                    particleStarMode = value
                    savePreferences()
                },
                particleTimeMode = particleTimeMode,
                onParticleTimeModeChange = { value ->
                    particleTimeMode = value
                    if (value && particleStarMode) particleStarMode = false
                    savePreferences()
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                colors = colors,
                cardBackground = cardBackground,
                performHaptic = { performHaptic(HapticFeedbackConstants.CLOCK_TICK) },
                oledMode = effectiveOledMode
            )

            // ── Particles › Motion sub-panel ──────────────────────────────────
            ParticlesMotionPanel(
                visible = showParticlesMotion,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showParticlesMotion = false
                    showParticlesSettings = true
                },
                particlesEnabled = particlesEnabled,
                particleSpeed = particleSpeed,
                onParticleSpeedChange = { value ->
                    particleSpeed = value
                    savePreferencesDebounced()
                },
                particleParallaxEnabled = particleParallaxEnabled,
                onParticleParallaxChange = { value ->
                    particleParallaxEnabled = value
                    savePreferences()
                },
                particleParallaxSensitivity = particleParallaxSensitivity,
                onParticleParallaxSensitivityChange = { value ->
                    particleParallaxSensitivity = value
                    savePreferencesDebounced()
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                colors = colors,
                cardBackground = cardBackground,
                performHaptic = { performHaptic(HapticFeedbackConstants.CLOCK_TICK) },
                oledMode = effectiveOledMode
            )

            // ── Particles › Performance sub-panel ────────────────────────────
            ParticlesPerformancePanel(
                visible = showParticlesPerformance,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showParticlesPerformance = false
                    showParticlesSettings = true
                },
                particlesEnabled = particlesEnabled,
                particleCount = particleCount,
                onParticleCountChange = { value ->
                    particleCount = value
                    savePreferencesDebounced()
                },
                nativeRefreshRate = particleNativeRefreshRate,
                onNativeRefreshRateChange = { value ->
                    particleNativeRefreshRate = value
                    savePreferences()
                },
                quarterRefreshRate = particleQuarterRefreshRate,
                onQuarterRefreshRateChange = { value ->
                    particleQuarterRefreshRate = value
                    savePreferences()
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                colors = colors,
                cardBackground = cardBackground,
                performHaptic = { performHaptic(HapticFeedbackConstants.CLOCK_TICK) },
                oledMode = effectiveOledMode
            )

            // ── Matrix rain settings panel ────────────────────────────────────
            MatrixSettingsPanel(
                visible = showMatrixSettings && !showMatrixAppearance && !showMatrixMotion,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showMatrixSettings = false
                },
                particlesEnabled = particlesEnabled,
                onAppearanceClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showMatrixAppearance = true
                },
                onMotionClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showMatrixMotion = true
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                colors = colors,
                cardBackground = cardBackground,
                performHaptic = { performHaptic(HapticFeedbackConstants.CLOCK_TICK) },
                oledMode = effectiveOledMode
            )

            MatrixAppearancePanel(
                visible = showMatrixAppearance,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showMatrixAppearance = false
                },
                particlesEnabled = particlesEnabled,
                matrixFontSize = matrixFontSize,
                onMatrixFontSizeChange = { matrixFontSize = it; savePreferences() },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                colors = colors,
                cardBackground = cardBackground,
                performHaptic = { performHaptic(HapticFeedbackConstants.CLOCK_TICK) },
                oledMode = effectiveOledMode
            )

            MatrixMotionPanel(
                visible = showMatrixMotion,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showMatrixMotion = false
                },
                particlesEnabled = particlesEnabled,
                matrixSpeed = matrixSpeed,
                onMatrixSpeedChange = { matrixSpeed = it; savePreferences() },
                matrixDensity = matrixDensity,
                onMatrixDensityChange = { matrixDensity = it; savePreferences() },
                matrixFadeLength = matrixFadeLength,
                onMatrixFadeLengthChange = { matrixFadeLength = it; savePreferences() },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                colors = colors,
                cardBackground = cardBackground,
                performHaptic = { performHaptic(HapticFeedbackConstants.CLOCK_TICK) },
                oledMode = effectiveOledMode
            )


            ColorCustomizationPanel(
                visible = showColorCustomization && !showGradient,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showColorCustomization = false
                },
                oledMode = effectiveOledMode,
                onOledModeChange = { enabled ->
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    themePreference = if (enabled) 1 else 2
                    savePreferences()
                },
                useDynamicColor = useDynamicColor,
                onDynamicColorChange = { value ->
                    performHaptic(HapticFeedbackConstants.CLOCK_TICK)
                    useDynamicColor = value
                    savePreferences()
                },
                advancedColorPicker = advancedColorPicker,
                onAdvancedColorPickerChange = { value ->
                    performHaptic(HapticFeedbackConstants.CLOCK_TICK)
                    advancedColorPicker = value
                    savePreferences()
                },
                customAccentColor = customAccentColor,
                onAccentColorChange = { color ->
                    customAccentColor = color
                    savePreferencesDebounced()
                },
                onGradientClick = { showGradient = true },
                isDarkTheme = isDarkTheme,
                performHaptic = { performHaptic(HapticFeedbackConstants.CONTEXT_CLICK) },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground
            )

            GradientPanel(
                visible = showGradient,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showGradient = false
                },
                gradientEnabled = gradientEnabled,
                onGradientChange = { gradientEnabled = it; savePreferences() },
                customGradientStart = customGradientStart,
                onGradientStartChange = { customGradientStart = it; savePreferencesDebounced() },
                customGradientEnd = customGradientEnd,
                onGradientEndChange = { customGradientEnd = it; savePreferencesDebounced() },
                useDynamicColor = useDynamicColor,
                advancedColorPicker = advancedColorPicker,
                oledMode = effectiveOledMode,
                darkModeActive = isDarkTheme,
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                performHaptic = { performHaptic(HapticFeedbackConstants.CLOCK_TICK) }
            )

            VerbosePanel(
                visible = showVerbosePanel,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showVerbosePanel = false
                },
                verboseOutput = verboseOutput,
                isSmallScreen = isSmallScreen,
                colors = colors,
                cardBackground = cardBackground,
                blurEnabled = blurEnabled,
                oledMode = effectiveOledMode
            )

            AggressiveWarningDialog(
                visible = showAggressiveWarning,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showAggressiveWarning = false
                    // Reset aggressive mode if not confirmed
                    if (!aggressiveModeConfirmed) {
                        aggressiveMode = false
                        savePreferences()
                    }
                },
                onConfirm = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showAggressiveWarning = false
                    aggressiveModeConfirmed = true
                },
                isSmallScreen = isSmallScreen,
                colors = colors,
                cardBackground = cardBackground,
                oledMode = effectiveOledMode
            )

            GPUWatchConfirmDialog(
                visible = showGPUWatchConfirm,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showGPUWatchConfirm = false
                },
                onConfirm = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showGPUWatchConfirm = false
                    // Straight to Developer Options — GPUWatch lives there.
                    try {
                        val intent = Intent("com.android.settings.SHOW_REGULATORY_INFO")
                        intent.setClassName(
                            "com.android.settings",
                            "com.android.settings.Settings\$TestingSettingsActivity"
                        )
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                            context.startActivity(intent)
                        } catch (e2: Exception) {
                            // Fallback failed
                        }
                    }
                },
                isSmallScreen = isSmallScreen,
                colors = colors,
                cardBackground = cardBackground,
                oledMode = effectiveOledMode
            )

            // Easter egg — triggered by long-pressing the GAMA title
            EasterEggDialog(
                visible = showEasterEgg,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showEasterEgg = false
                },
                colors = colors,
                cardBackground = cardBackground,
                isSmallScreen = isSmallScreen
            )

            // Bottom UI Elements
            // Ensure these are NOT visible during the setup flow
            val controlsVisible = !visualAnyPanelOpen
            val controlsAlpha by animateFloatAsState(
                targetValue = if (controlsVisible) 1f else 0f,
                animationSpec = if (animationLevel == 2) snap<Float>() else tween<Float>(animDuration),
                label = "controls_alpha"
            )
            val controlsScale by animateFloatAsState(
                targetValue = if (controlsVisible) 1f else 0.82f,
                animationSpec = if (animationLevel == 2) snap<Float>() else spring(
                    dampingRatio = 0.62f,
                    stiffness = 360f
                ),
                label = "controls_scale"
            )

            if (controlsVisible || controlsAlpha > 0f) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = if (isSmallScreen) 20.dp else 30.dp, start = 20.dp, end = 20.dp)
                        .alpha(controlsAlpha)
                ) {
                    val floatingButtonController = LocalFloatingButtonPositionController.current
                    val settingsButtonSize = if (isSmallScreen) 48.dp else 52.dp
                    val settingsGlowSize = settingsButtonSize * 1.8f
                    val settingsAnchorX = maxWidth * if (floatingButtonController.settingsFullWidth) {
                        floatingButtonController.settingsX
                    } else {
                        0.5f + 0.5f * floatingButtonController.settingsX
                    } - settingsGlowSize / 2
                    val settingsAnchorY = maxHeight * floatingButtonController.settingsY - settingsGlowSize / 2
                    // Version number
                    AnimatedElement(
                        visible = controlsVisible, staggerIndex = 4,
                        totalItems = 8,
                        modifier = Modifier
                            .align(
                                if (isLandscape) Alignment.CenterStart else Alignment.BottomCenter
                            )
                            .then(
                                if (isLandscape) {
                                    Modifier
                                        .fillMaxWidth(0.5f)
                                        .wrapContentWidth(Alignment.CenterHorizontally)
                                        .zIndex(2f)
                                } else {
                                    Modifier
                                }
                            )
                            .graphicsLayer(
                                scaleX = controlsScale,
                                scaleY = controlsScale
                            )
                    ) {
                        Text(
                            text = "v$currentVersion",
                            fontSize = ts.labelMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = quicksandFontFamily,
                            color = colors.textSecondary.copy(alpha = 0.4f)
                        )
                    }

                    AnimatedElement(
                        visible = controlsVisible, staggerIndex = 4,
                        totalItems = 8,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = settingsAnchorX, y = settingsAnchorY)
                            .graphicsLayer(
                                scaleX = controlsScale,
                                scaleY = controlsScale
                            )
                    ) {
                        val btnSize = settingsButtonSize
                        val iconSize = if (isSmallScreen) 24.dp else 28.dp


                        // Glow blob uses a fixed alpha — no infinite transition needed.
                        // The blur already diffuses the shape; animating alpha here
                        // caused a recomposition every frame on the idle main screen.
                        val settingsGlowAlpha = 0.25f
                        var settingsPressed by remember { mutableStateOf(false) }
                        val settingsHoldState = remember { FloatingButtonHoldState() }
                        val animationLevel = LocalAnimationLevel.current
                        // ── Single Animatable replaces 3 separate animators ───
                        val settingsPressProgress = remember { Animatable(0f) }
                        LaunchedEffect(settingsPressed, animationLevel) {
                            settingsPressProgress.animateTo(
                                targetValue = if (settingsPressed) 1f else 0f,
                                animationSpec = when (animationLevel) {
                                    2 -> snap()
                                    1 -> tween(durationMillis = 120, easing = MotionTokens.Easing.emphasized)
                                    else -> spring(
                                        dampingRatio = if (settingsPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
                                        stiffness = if (settingsPressed) MotionTokens.Springs.pressDown.stiffness else MotionTokens.Springs.pressUp.stiffness
                                    )
                                }
                            )
                        }
                        val spp = settingsPressProgress.value
                        val settingsAppearScale by animateFloatAsState(
                            targetValue = if (controlsVisible) 1f else 0.82f,
                            animationSpec = when (LocalAnimationLevel.current) {
                                2 -> snap()
                                1 -> tween(240, easing = MotionTokens.Easing.emphasized)
                                else -> spring(
                                    dampingRatio = 0.58f,
                                    stiffness = 360f
                                )
                            },
                            label = "settings_appear_scale"
                        )
                        val settingsPressScale = (1f - spp * (1f - MotionTokens.Scale.subtle)) * settingsAppearScale
                        val settingsBorderAlpha = 0.4f + spp * 0.6f
                        val settingsBorderWidth = (1.5f + spp * 0.5f).dp
                        val glowSize = btnSize * 1.8f

                        Column(
                            modifier = Modifier.graphicsLayer(
                                translationX = settingsHoldState.dragTranslationX,
                                translationY = settingsHoldState.dragTranslationY
                            ),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {

                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(glowSize)) {
                                // Static glow blob — blur radius is fixed so GPU sets RenderEffect once.
                                // On API < 31 (no RenderEffect support) just draw a soft circle with alpha.
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    Box(
                                        modifier = Modifier
                                            .size(glowSize)
                                            .blur(radius = 20.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                                            .background(
                                                brush = Brush.radialGradient(
                                                    colors = listOf(
                                                        colors.primaryAccent.copy(alpha = settingsGlowAlpha),
                                                        colors.primaryAccent.copy(alpha = settingsGlowAlpha * 0.4f),
                                                        Color.Transparent
                                                    )
                                                ),
                                                shape = CircleShape
                                            )
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(glowSize)
                                            .background(
                                                brush = Brush.radialGradient(
                                                    colors = listOf(
                                                        colors.primaryAccent.copy(alpha = settingsGlowAlpha * 0.4f),
                                                        Color.Transparent
                                                    )
                                                ),
                                                shape = CircleShape
                                            )
                                    )
                                }
                                FloatingHoldIndicator(settingsHoldState, colors.primaryAccent, glowSize)
                                Box(
                                    modifier = Modifier
                                        .size(btnSize)
                                        .graphicsLayer(scaleX = settingsPressScale, scaleY = settingsPressScale)
                                        .clip(RoundedCornerShape(28.dp))
                                        .background(
                                            Brush.radialGradient(
                                                listOf(
                                                    colors.primaryAccent.copy(alpha = 0.22f),
                                                    colors.primaryAccent.copy(alpha = 0.08f)
                                                )
                                            )
                                        )
                                        .background(colors.primaryAccent.copy(alpha = spp * 0.12f))
                                        .pressedAccentOutlineGlow(
                                            pressProgress = spp,
                                            color = colors.primaryAccent,
                                            cornerRadius = 28.dp,
                                            strokeWidth = settingsBorderWidth,
                                            glowRadius = 10.dp
                                        )
                                        .border(
                                            settingsBorderWidth,
                                            colors.primaryAccent.copy(alpha = settingsBorderAlpha),
                                            RoundedCornerShape(28.dp)
                                        )
                                        .semantics { contentDescription = strings["text_catalog.open_settings"].ifEmpty { "Open Settings" } }
                                        .floatingButtonGesture(
                                            enabled = controlsVisible,
                                            isLeftSide = false,
                                            isSettingsButton = true,
                                            holdState = settingsHoldState,
                                            onPressedChange = { settingsPressed = it },
                                            onClick = { openMainPanelExclusive { showSettings = true } }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.size(iconSize)) {
                                        val dotRadius = 2.5.dp.toPx()
                                        val spacing = size.height / 4
                                        val centerX = size.width / 2
                                        repeat(3) { i ->
                                            drawCircle(
                                                color = colors.primaryAccent.copy(alpha = 0.9f),
                                                radius = dotRadius,
                                                center = Offset(centerX, spacing + (i * spacing))
                                            )
                                        }
                                    }
                                }
                            }
                            // One-time label
                            androidx.compose.animation.AnimatedVisibility(
                                visible = showButtonLabels,
                                enter = fadeIn(animationSpec = tween(260, easing = MotionTokens.Easing.enter)) +
                                        slideInVertically(
                                            animationSpec = tween(
                                                260,
                                                easing = MotionTokens.Easing.emphasizedDecelerate
                                            )
                                        ) { it / 3 },
                                exit = fadeOut(animationSpec = tween(180, easing = MotionTokens.Easing.exit)) +
                                        slideOutVertically(
                                            animationSpec = tween(
                                                180,
                                                easing = MotionTokens.Easing.exit
                                            )
                                        ) { it / 4 }
                            ) {
                                Text(
                                    text = strings["settings.title"].replace("S", "S").let {
                                        it.ifEmpty { "Settings" }.lowercase().replaceFirstChar { c -> c.uppercase() }
                                    },
                                    fontSize = ts.labelSmall,
                                    fontFamily = quicksandFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primaryAccent.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
