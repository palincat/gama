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
import androidx.activity.compose.BackHandler
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.roundToInt



@Composable
fun ShizukuHelpDialog(
    visible: Boolean,
    helpType: String,
    onDismiss: () -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    rootAvailable: Boolean = false
) {
    val ts = LocalTypeScale.current
    val strings = LocalStrings.current
    val dialogBorderAlpha = 0.55f  // matches SettingsNavigationCard (APPEARANCE button)
    val dialogBorderWidth = 1.dp
    val dialogShape = RoundedCornerShape(40.dp)
    val context = LocalContext.current
    val dialogContentPadding = when {
        isLandscape -> 16.dp
        isSmallScreen -> 22.dp
        else -> 28.dp
    }
    val dialogItemSpacing = when {
        isLandscape -> 10.dp
        isSmallScreen -> 14.dp
        else -> 18.dp
    }
    // Official Shizuku remains the preferred manager. Shevery is detected as
    // an optional compatible implementation and is never downloaded by GAMA.
    var installedBackend by remember { mutableStateOf(ShizukuBackend.installed(context)) }
    val backendName = installedBackend?.displayName ?: "Shizuku"
    val shizukuInstalled = installedBackend != null

    BouncyDialog(visible = visible, onDismiss = onDismiss) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
    Card(
        modifier = Modifier
            .fillMaxWidth(if (isLandscape && !isTablet) 0.6f else 0.9f)
            .widthIn(max = 500.dp)
            // A scrollable child must have a bounded height. Without this, a
            // landscape dialog measures to its full content height and extends
            // below the screen instead of becoming scrollable.
            .heightIn(max = maxHeight * 0.90f)
            .directionalShadow(cornerRadius = 28.dp)
            .border(
                width = dialogBorderWidth,
                color = colors.primaryAccent.copy(alpha = dialogBorderAlpha),
                shape = dialogShape
            )
            .pointerInput(Unit) {
                detectTapGestures { /* Block taps on card from dismissing dialog */ }
            },
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        shape = dialogShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(dialogContentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dialogItemSpacing)
        ) {
            // ── Title — accent-coloured, matches ExternalLinkConfirmDialog ──
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (helpType == "not_running")
                        strings["dialogs.backend_not_running_title"].ifEmpty { "%s isn't running" }
                            .replace("%s", backendName)
                    else strings["dialogs.backend_permission_title"].ifEmpty { "Permission Needed" },
                    fontSize = if (isLandscape) ts.headlineMedium else ts.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    fontFamily = quicksandFontFamily,
                    color = colors.primaryAccent
                )
            }

            // ── One-line intro — what the user needs to do, in a nutshell ──
            Text(
                text = if (helpType == "not_running")
                    strings["dialogs.backend_intro_not_running"]
                        .ifEmpty { "GAMA needs the %s service running to switch the renderer." }
                        .replace("%s", backendName)
                else
                    strings["dialogs.backend_intro_permission"]
                        .ifEmpty { "GAMA is installed, but hasn't been authorized in %s yet." }
                        .replace("%s", backendName),
                fontSize = ts.bodyMedium,
                lineHeight = (ts.bodyMedium.value * 1.4f).sp,
                color = colors.textSecondary,
                fontFamily = quicksandFontFamily,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )

            if (helpType == "not_running") {
                // ── Primary action: download (missing) or open (installed) ──
                if (BuildConfig.CAN_INSTALL_SHIZUKU && !shizukuInstalled) {
                    val scope = rememberCoroutineScope()
                    var installPhase by remember { mutableStateOf(0) } // 0 idle · 1 consent · 2 downloading · 3 installing · 4 installed · -1 failed
                    var installProgress by remember { mutableStateOf(0f) }
                    var installError by remember { mutableStateOf("") }

                    // First tap asks for explicit consent: the APK comes straight
                    // from GitHub and installs outside F-Droid's review. Required by
                    // F-Droid's inclusion policy (opt-in, clearly explained).
                    val consentText = LocalStrings.current["dialogs.shizuku_download_consent"]
                        .ifEmpty { "This downloads the Shizuku APK from GitHub and installs it directly, bypassing F-Droid's checks. Continue?" }

                    val installLabel = when {
                        installPhase == 1 -> LocalStrings.current["dialogs.shizuku_downloading"]
                            .ifEmpty { "Downloading Shizuku… %s%" }
                            .replace("%s", ((installProgress * 100).toInt()).toString())
                        installPhase == 2 -> LocalStrings.current["dialogs.shizuku_installing"]
                            .ifEmpty { "Installing Shizuku…" }
                        installPhase == -1 -> LocalStrings.current["dialogs.btn_retry"].ifEmpty { "Retry download" }
                        else -> LocalStrings.current["dialogs.btn_download_shizuku"]
                            .ifEmpty { "Download & install Shizuku" }
                    }
                    val downloadFailedText = LocalStrings.current["dialogs.shizuku_download_failed"]
                        .ifEmpty { "Download failed. Check your connection and try again." }

                    // Shared download + install pipeline — called by the consent
                    // Continue button and by Retry after a failure.
                    val startDownload: () -> Unit = {
                        installPhase = 1
                        installProgress = 0f
                        installError = ""
                        scope.launch {
                            val result = ShizukuInstaller.downloadLatestApk(context) { p ->
                                installProgress = p
                            }
                            if (result.apkFile == null) {
                                installPhase = -1
                                installError = result.error.ifBlank { downloadFailedText }
                                return@launch
                            }
                            installPhase = 2
                            when (val installResult = ShizukuInstaller.installApk(context, result.apkFile)) {
                                is InstallResult.Installed -> {
                                    installedBackend = ShizukuBackend.Kind.OFFICIAL
                                    installPhase = 3
                                }
                                is InstallResult.Cancelled -> {
                                    installPhase = 0
                                }
                                is InstallResult.Failed -> {
                                    installPhase = -1
                                    installError = installResult.reason
                                }
                            }
                        }
                    }

                    if (installPhase == 0) {
                        DialogButton(
                            text = installLabel,
                            onClick = { installPhase = 4 },
                            modifier = Modifier.fillMaxWidth(),
                            colors = colors,
                            cardBackground = cardBackground,
                            accent = true,
                            borderAlphaOverride = dialogBorderAlpha
                        )
                    } else if (installPhase == 4) {
                        Text(
                            text = consentText,
                            fontSize = ts.bodySmall,
                            lineHeight = (ts.bodySmall.value * 1.3f).sp,
                            color = colors.textSecondary,
                            fontFamily = quicksandFontFamily,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DialogButton(
                                text = LocalStrings.current["dialogs.btn_cancel"].ifEmpty { "Cancel" },
                                onClick = { installPhase = 0 },
                                modifier = Modifier.weight(1f),
                                colors = colors,
                                cardBackground = cardBackground,
                                accent = false,
                                borderAlphaOverride = dialogBorderAlpha
                            )
                            DialogButton(
                                text = LocalStrings.current["dialogs.btn_continue"].ifEmpty { "Continue" },
                                onClick = startDownload,
                                modifier = Modifier.weight(1f),
                                colors = colors,
                                cardBackground = cardBackground,
                                accent = true,
                                borderAlphaOverride = dialogBorderAlpha
                            )
                        }
                    } else {
                        DialogButton(
                            text = installLabel,
                            onClick = {
                                if (installPhase == 1 || installPhase == 2) return@DialogButton
                                startDownload()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = colors,
                            cardBackground = cardBackground,
                            accent = true,
                            borderAlphaOverride = dialogBorderAlpha
                        )
                    }

                if (installPhase == -1 && installError.isNotEmpty()) {
                        Text(
                            text = installError,
                            fontSize = ts.bodySmall,
                            lineHeight = (ts.bodySmall.value * 1.3f).sp,
                            color = Color(0xFFEF5350),
                            fontFamily = quicksandFontFamily,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // ── Steps — numbered, so the path is obvious ──
                DialogSectionLabel(
                    strings["dialogs.backend_how_to_start"].ifEmpty { "How to start %s" }.replace("%s", backendName),
                    colors = colors
                )
                if (!shizukuInstalled) {
                    DialogStepRow(
                        1,
                        if (BuildConfig.CAN_INSTALL_SHIZUKU) {
                            strings["dialogs.backend_step_install_button"].ifEmpty { "Install %s with the button above" }
                                .replace("%s", backendName)
                        } else {
                            strings["dialogs.backend_step_install_manual"].ifEmpty { "Install %s from its official source, then return to GAMA" }
                                .replace("%s", backendName)
                        },
                        colors = colors
                    )
                    DialogStepRow(
                        2,
                        strings["dialogs.backend_step_open_start"].ifEmpty { "Open %s and tap \"Start\"" }
                            .replace("%s", backendName),
                        colors = colors
                    )
                    DialogStepRow(3, strings["dialogs.backend_step_come_back"].ifEmpty { "Come back to GAMA" }, colors = colors)
                } else {
                    DialogStepRow(
                        1,
                        strings["dialogs.backend_step_tap_start"].ifEmpty { "Tap \"Start\" inside the %s app" }
                            .replace("%s", backendName),
                        colors = colors
                    )
                    DialogStepRow(2, strings["dialogs.backend_step_come_back"].ifEmpty { "Come back to GAMA" }, colors = colors)
                }

                if (shizukuInstalled) {
                    DialogButton(
                        text = strings["dialogs.backend_open"].ifEmpty { "Open %s" }.replace("%s", backendName),
                        onClick = {
                            context.packageManager
                                .getLaunchIntentForPackage(installedBackend!!.packageName)
                                ?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = colors,
                        cardBackground = cardBackground,
                        accent = true,
                        borderAlphaOverride = dialogBorderAlpha
                    )
                }

                // ── Troubleshooting footnote ──
                Text(
                    text = strings["dialogs.backend_troubleshoot"]
                        .ifEmpty { "%s won't start? Follow its wireless debugging instructions, then return to GAMA." }
                        .replace("%s", backendName),
                    fontSize = ts.bodySmall,
                    lineHeight = (ts.bodySmall.value * 1.3f).sp,
                    color = colors.textSecondary,
                    fontFamily = quicksandFontFamily,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // ── "Permission Needed" — same numbered-step treatment ──
                DialogSectionLabel(strings["dialogs.backend_authorize"].ifEmpty { "Authorize GAMA" }, colors = colors)
                DialogStepRow(
                    1,
                    strings["dialogs.backend_step_open_app"].ifEmpty { "Open the %s app" }.replace("%s", backendName),
                    colors = colors
                )
                DialogStepRow(
                    2,
                    strings["dialogs.backend_step_authorized_apps"].ifEmpty { "Tap \"Authorized applications\"" },
                    colors = colors
                )
                DialogStepRow(3, strings["dialogs.backend_step_enable_gama"].ifEmpty { "Enable GAMA" }, colors = colors)
                DialogStepRow(4, strings["dialogs.backend_step_reopen"].ifEmpty { "Reopen GAMA from your recents" }, colors = colors)
                if (shizukuInstalled) {
                    DialogButton(
                        text = strings["dialogs.backend_open"].ifEmpty { "Open %s" }.replace("%s", backendName),
                        onClick = {
                            context.packageManager
                                .getLaunchIntentForPackage(installedBackend!!.packageName)
                                ?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = colors,
                        cardBackground = cardBackground,
                        accent = true,
                        borderAlphaOverride = dialogBorderAlpha
                    )
                }
            }

            // ── Root alternative — only relevant when neither backend is ready ──
            if (!rootAvailable) {
                Text(
                    text = strings["dialogs.backend_root_hint"]
                        .ifEmpty { "Device rooted? GAMA also works with root access (Magisk / KernelSU) — no %s needed." }
                        .replace("%s", backendName),
                    fontSize = ts.bodySmall,
                    lineHeight = (ts.bodySmall.value * 1.3f).sp,
                    color = colors.textSecondary,
                    fontFamily = quicksandFontFamily,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── Dismiss — secondary so the download/open action stays primary ──
            DialogButton(
                text = LocalStrings.current["dialogs.btn_okay"].ifEmpty { "Okay" },
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = colors,
                cardBackground = cardBackground,
                accent = false,
                borderAlphaOverride = dialogBorderAlpha
            )
        }
    }
    } // BoxWithConstraints
    } // BouncyDialog
}

// ── Small section header used inside the Shizuku help dialogs ───────────────
@Composable
private fun DialogSectionLabel(text: String, colors: ThemeColors) {
    val ts = LocalTypeScale.current
    Text(
        text = text,
        fontSize = ts.headlineSmall,
        fontWeight = FontWeight.Bold,
        fontFamily = quicksandFontFamily,
        color = colors.primaryAccent,
        modifier = Modifier.fillMaxWidth()
    )
}

// ── Numbered instruction row: circled step number + short text ──────────────
@Composable
private fun DialogStepRow(step: Int, text: String, colors: ThemeColors) {
    val ts = LocalTypeScale.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(colors.primaryAccent.copy(alpha = 0.14f))
                .border(1.dp, colors.primaryAccent.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step.toString(),
                fontSize = ts.bodySmall,
                fontWeight = FontWeight.Bold,
                fontFamily = quicksandFontFamily,
                color = colors.primaryAccent
            )
        }
        Text(
            text = text,
            fontSize = ts.bodyMedium,
            color = colors.textPrimary.copy(alpha = 0.9f),
            fontFamily = quicksandFontFamily,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun EasterEggDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    colors: ThemeColors,
    cardBackground: Color,
    isSmallScreen: Boolean
) {
    val ts = LocalTypeScale.current
    val strings = LocalStrings.current
    val animLevel = LocalAnimationLevel.current
    val dialogShape = RoundedCornerShape(28.dp)
    BouncyDialog(visible = visible, onDismiss = onDismiss) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(if (isSmallScreen) 0.90f else 0.84f)
                    .widthIn(max = 460.dp)
                    .pointerInput(Unit) { detectTapGestures { } },
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                shape = dialogShape,
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                listOf(
                                    colors.primaryAccent.copy(alpha = 0.6f),
                                    colors.primaryAccent.copy(alpha = 0.15f),
                                    colors.primaryAccent.copy(alpha = 0.05f)
                                )
                            ),
                            shape = dialogShape
                        )
                        .clip(dialogShape)
                ) {
                    // ── Ambient glow blob at the top — only runs while dialog is visible ──
                    val glowPulse = rememberInfiniteTransition(label = "egg_glow")
                    val glowAlpha by glowPulse.animateFloat(
                        initialValue = if (visible) 0.28f else 0f,
                        targetValue  = if (visible) 0.48f else 0f,
                        animationSpec = infiniteRepeatable(
                            tween(2200, easing = MotionTokens.Easing.silk),
                            RepeatMode.Reverse
                        ), label = "egg_glow_a"
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .blur(60.dp, BlurredEdgeTreatment.Unbounded)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            colors.primaryAccent.copy(alpha = glowAlpha),
                                            colors.primaryAccent.copy(alpha = glowAlpha * 0.3f),
                                            Color.Transparent
                                        ),
                                        radius = 400f
                                    )
                                )
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            colors.primaryAccent.copy(alpha = glowAlpha * 0.45f),
                                            Color.Transparent
                                        ),
                                        radius = 400f
                                    )
                                )
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                top = if (isSmallScreen) 40.dp else 52.dp,
                                bottom = if (isSmallScreen) 32.dp else 40.dp,
                                start = if (isSmallScreen) 28.dp else 36.dp,
                                end = if (isSmallScreen) 28.dp else 36.dp
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        Text(
                            text = strings["dialogs.egg_access_granted"].ifEmpty { "ACCESS GRANTED // 01" },
                            fontSize = ts.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = quicksandFontFamily,
                            color = colors.primaryAccent.copy(alpha = 0.78f),
                            letterSpacing = 2.2.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(if (isSmallScreen) 14.dp else 18.dp))

                        // ── GAMA wordmark with glow ───────────────────────────
                        val gamaSize = if (isSmallScreen) (ts.displayLarge.value * 1.4f).sp
                        else (ts.displayLarge.value * 1.7f).sp
                        val context = LocalContext.current
                        // Cache Paint+BlurMaskFilter — both are constant; no need to
                        // reconstruct them on every draw frame inside drawWithContent.
                        val easterGlowPaint = remember(colors.primaryAccent, gamaSize) {
                            android.graphics.Paint().apply {
                                isAntiAlias = true
                                color = android.graphics.Color.TRANSPARENT
                                maskFilter = android.graphics.BlurMaskFilter(
                                    100f, android.graphics.BlurMaskFilter.Blur.NORMAL
                                )
                                setShadowLayer(
                                    100f, 0f, 0f,
                                    colors.primaryAccent.copy(alpha = 0.7f).toArgb()
                                )
                            }
                        }
                        Text(
                            text = "GAMA",
                            fontSize = gamaSize,
                            fontWeight = FontWeight.Bold,
                            fontFamily = quicksandFontFamily,
                            color = colors.textPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.drawWithContent {
                                drawIntoCanvas { canvas ->
                                    easterGlowPaint.textSize = gamaSize.toPx()
                                    canvas.nativeCanvas.drawText(
                                        "GAMA",
                                        size.width / 2f,
                                        size.height / 2f + gamaSize.toPx() * 0.28f,
                                        easterGlowPaint
                                    )
                                }
                                drawContent()
                            }
                        )

                        Spacer(Modifier.height(if (isSmallScreen) 6.dp else 8.dp))

                        // ── Thin accent divider ───────────────────────────────
                        Box(
                            modifier = Modifier
                                .width(48.dp)
                                .height(2.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.Transparent,
                                            colors.primaryAccent.copy(alpha = 0.7f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        Spacer(Modifier.height(if (isSmallScreen) 20.dp else 28.dp))

                        // ── Main copy ─────────────────────────────────────────
                        Text(
                            text = strings["dialogs.egg_control_room"].ifEmpty { "THE UNNECESSARY\nCONTROL ROOM" },
                            fontSize = ts.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = quicksandFontFamily,
                            color = colors.textPrimary.copy(alpha = 0.92f),
                            textAlign = TextAlign.Center,
                            lineHeight = (ts.headlineSmall.value * 1.45f).sp
                        )

                        Spacer(Modifier.height(if (isSmallScreen) 16.dp else 22.dp))

                        Text(
                            text = strings["dialogs.egg_tagline"].ifEmpty { "One long press. One small secret.\nZero extra permissions." },
                            fontSize = ts.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = quicksandFontFamily,
                            color = colors.textSecondary.copy(alpha = 0.65f),
                            textAlign = TextAlign.Center,
                            lineHeight = (ts.bodyMedium.value * 1.6f).sp
                        )

                        Spacer(Modifier.height(if (isSmallScreen) 8.dp else 10.dp))

                        Text(
                            text = strings["dialogs.egg_secret"].ifEmpty { "You were never supposed to find this.\n(We hoped you would.)" },
                            fontSize = ts.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = quicksandFontFamily,
                            color = colors.primaryAccent.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(if (isSmallScreen) 6.dp else 8.dp))

                        Text(
                            text = "@palincat",
                            fontSize = ts.labelMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = quicksandFontFamily,
                            color = colors.textSecondary.copy(alpha = 0.38f),
                            textAlign = TextAlign.Center,
                            letterSpacing = 0.5.sp
                        )

                        Spacer(Modifier.height(if (isSmallScreen) 28.dp else 36.dp))

                        // ── Dismiss button ────────────────────────────────────
                        DialogButton(
                            text = strings["dialogs.egg_return"].ifEmpty { "RETURN TO GAMA" },
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            colors = colors,
                            cardBackground = cardBackground,
                            accent = true
                        )
                    }
                }
            }
        }
    }
}

