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
import android.os.SystemClock
import android.provider.Settings
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.PorterDuff
import android.graphics.Rect as AndroidRect
import android.view.HapticFeedbackConstants
import android.view.PixelCopy
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
import androidx.compose.foundation.layout.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.platform.LocalViewConfiguration
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
import androidx.compose.ui.text.style.TextOverflow
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

/**
 * A reusable composable that applies standard disabled styling to a card.
 * This ensures consistency across all disabled cards in the app.
 *
 * Standard disabled style:
 * - Scale: 0.90f (keeps the card legible)
 * - A 50% theme-aware wash instead of transparency
 * - Smooth animated transitions
 *
 * @param enabled Whether the card is enabled (true) or disabled (false)
 * @param content The card content to wrap
 */
@Composable
fun DisabledCardWrapper(
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    val animSpeed = LocalAnimationSpeed.current
    val disabledScale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.90f,
        animationSpec = spring(
            dampingRatio = 0.60f,
            stiffness = MotionTokens.SpeedUtil.stiffness(440f, animSpeed)
        ),
        label = "disabled_card_scale"
    )
    Box(
        modifier = Modifier
            .scale(disabledScale)
            .then(if (!enabled) Modifier.pointerInput(enabled) { detectTapGestures { } } else Modifier)
    ) {
        content()
        DisabledCardWash(enabled)
    }
}

@Composable
fun BoxScope.DisabledCardWash(enabled: Boolean, oledMode: Boolean = false) {
    if (!enabled) {
        // Use GAMA's resolved theme, not the device theme. Manual light/dark
        // selection must remain consistent even when Android uses the opposite.
        val appColors = LocalThemeColors.current
        val useDarkWash = oledMode || appColors.background.luminance() < 0.5f
        Box(
            modifier = Modifier
                // This is an overlay, not a layout participant. fillMaxSize()
                // adopts the parent's maximum constraints and can therefore
                // make the parent card as tall as its available viewport.
                .matchParentSize()
                .clip(RoundedCornerShape(28.dp))
                .background((if (useDarkWash) Color.Black else Color.White).copy(alpha = 0.50f))
        )
    }
}

@Composable
fun SettingsNavigationCard(
    title: String,
    description: String,
    onClick: () -> Unit,
    isSmallScreen: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    enabled: Boolean = true,
    oledMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val ts = LocalTypeScale.current
    val landscapeGrid = LocalLandscapePanelGrid.current
    val context = LocalContext.current
    val view = LocalView.current
    val animSpeed = LocalAnimationSpeed.current

    val disabledScale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.90f,
        animationSpec = spring(
            dampingRatio = 0.60f,
            stiffness = MotionTokens.SpeedUtil.stiffness(440f, animSpeed)
        ),
        label = "settings_nav_disabled_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = MotionTokens.SpeedUtil.durationMs(360, animSpeed),
            easing = MotionTokens.Easing.emphasized
        ),
        label = "settings_nav_alpha"
    )

    var isPressed by remember { mutableStateOf(false) }
    val pressProgress = remember { Animatable(0f) }
    val animLevel = LocalAnimationLevel.current
    LaunchedEffect(isPressed) {
        if (animLevel == 2) {
            pressProgress.snapTo(if (isPressed && enabled) 1f else 0f)
        } else {
            pressProgress.animateTo(
                targetValue = if (isPressed && enabled) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
                    stiffness = MotionTokens.SpeedUtil.stiffness(
                        if (isPressed) MotionTokens.Springs.pressDown.stiffness else MotionTokens.Springs.pressUp.stiffness,
                        animSpeed
                    )
                )
            )
        }
    }
    val p = pressProgress.value
    val pressScale = 1f - p * 0.070f
    val chevronScaleVal = 1f + p * 0.18f
    val chevronAlphaVal = 0.55f + p * 0.45f
    val borderWidthVal = (if (oledMode) 0.75f else 1f) + p * (if (oledMode) 0.75f else 1f)
    val cardBorderWidth = borderWidthVal.dp

    val animatedCardBorderColor = when {
        !enabled -> colors.primaryAccent.copy(alpha = 0.25f)
        isPressed -> colors.primaryAccent
        else -> colors.primaryAccent.copy(alpha = 0.55f)
    }
    val chevronColor =
        if (isPressed && enabled) colors.primaryAccent else colors.textSecondary.copy(alpha = chevronAlphaVal)
    val titleColor = if (enabled) {
        colors.primaryAccent.copy(alpha = 0.7f)
    } else {
        colors.textSecondary.copy(alpha = 0.72f)
    }
    val descriptionColor = colors.textSecondary.copy(alpha = if (enabled) 1f else 0.72f)
    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier = modifier
            .fillMaxWidth(if (landscapeGrid) 0.48f else 1f)
            .heightIn(min = if (LocalConfiguration.current.screenWidthDp.dp < 360.dp) 88.dp else 96.dp, max = 400.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(
                    scaleX = disabledScale * pressScale,
                    scaleY = disabledScale * pressScale,
                    clip = false
                )
                .pressedAccentOutlineGlow(
                    pressProgress = p,
                    color = colors.primaryAccent,
                    cornerRadius = 28.dp,
                    strokeWidth = cardBorderWidth,
                    glowRadius = 9.dp,
                    maxAlpha = 0.46f
                )
                .clip(shape)
                .background(cardBackground)
                .border(width = cardBorderWidth, color = animatedCardBorderColor, shape = shape)
                .graphicsLayer(alpha = alpha)
                .then(if (!enabled) Modifier.pointerInput(enabled) { detectTapGestures { } } else Modifier)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            val hapticStartedAt = GamaHaptics.pressStart(context, view)
                            isPressed = true
                            val released = tryAwaitRelease()
                            isPressed = false
                            GamaHaptics.releaseAfterPress(context, view, hapticStartedAt, released)
                            if (released) onClick()
                        }
                    )
                }
                .background(colors.primaryAccent.copy(alpha = if (enabled) p * 0.08f else 0f))
                .padding(if (isSmallScreen) 20.dp else 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically  // Changed from Alignment.Top
            ) {
                // Content area that should expand to fit both title and description
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .wrapContentHeight(align = Alignment.Top),  // Added to allow proper expansion
                    verticalArrangement = Arrangement.spacedBy(4.dp)  // Consistent spacing
                ) {
                    Text(
                        text = title,
                        color = titleColor,
                        fontSize = ts.labelLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        fontFamily = quicksandFontFamily,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    AnimatedContent(
                        targetState = description,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(280, easing = MotionTokens.Easing.enter)) +
                                    slideInVertically(
                                        animationSpec = tween(
                                            280,
                                            easing = MotionTokens.Easing.enter
                                        )
                                    ) { it / 4 }) togetherWith
                                    (fadeOut(animationSpec = tween(160, easing = MotionTokens.Easing.exit)) +
                                            slideOutVertically(
                                                animationSpec = tween(
                                                    160,
                                                    easing = MotionTokens.Easing.exit
                                                )
                                            ) { -it / 4 })
                        },
                        label = "description_crossfade"
                    ) { targetDescription ->
                        Text(
                            text = targetDescription,
                            color = descriptionColor,
                            fontSize = ts.bodyMedium,
                            fontFamily = quicksandFontFamily,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(align = Alignment.Top),
                            maxLines = 5,
                            softWrap = true,
                            overflow = TextOverflow.Clip,
                            lineHeight = (ts.bodyMedium.value * 1.3f).sp
                        )
                    }
                }

                // Chevron - now properly vertically centered in the card
                Box(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .align(Alignment.CenterVertically)  // Changed from Alignment.Top
                        .graphicsLayer(
                            scaleX = chevronScaleVal,
                            scaleY = chevronScaleVal,
                            clip = false
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(24.dp)) {
                        val path = Path().apply {
                            moveTo(size.width * 0.4f, size.height * 0.2f)
                            lineTo(size.width * 0.7f, size.height * 0.5f)
                            lineTo(size.width * 0.4f, size.height * 0.8f)
                        }
                        drawPath(
                            path = path,
                            color = chevronColor,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MainContentCards(
    isVisible: Boolean,
    isSmallScreen: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    currentRenderer: String,
    commandOutput: String,
    shizukuStatus: String,
    shizukuRunning: Boolean,
    shizukuPermissionGranted: Boolean,
    onShizukuStatusClick: () -> Unit,
    onVulkanClick: () -> Unit,
    onOpenGLClick: () -> Unit,
    onResourcesClick: () -> Unit,
    onGPUWatchClick: () -> Unit, // NEW CALLBACK
    showGpuWatchButton: Boolean = false,
    oledMode: Boolean = false, // Added
    rendererLoading: Boolean = false,
    lastSwitchTime: Long = 0L
) {
    // In landscape, we want the cards to fill the available space better
    // rather than being constrained too tightly if it's splitting screen with title
    val maxWidth = when {
        isTablet -> 500.dp
        else -> 600.dp // Allow wider on phones to fill 1/2 screen
    }
    val landscape = LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }

    val shizukuReady = shizukuRunning && shizukuPermissionGranted
    val strings = LocalStrings.current

    // Responsive sizing — derived from the available space rather than hardcoded dp values.
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val screenMinDp = minOf(configuration.screenWidthDp, configuration.screenHeightDp)
    // Outer gap between RendererCard and the button container
    val cardSpacing = when {
        isSmallScreen -> 10.dp
        isLandscape -> (screenMinDp * 0.025f).dp.coerceIn(10.dp, 16.dp)
        else -> (screenMinDp * 0.038f).dp.coerceIn(14.dp, 22.dp)
    }
    // Padding inside the button container box
    val containerPadding = when {
        isSmallScreen -> 10.dp
        isLandscape -> (screenMinDp * 0.027f).dp.coerceIn(10.dp, 16.dp)
        else -> (screenMinDp * 0.040f).dp.coerceIn(14.dp, 20.dp)
    }
    // Gap between button rows and between buttons in the same row
    val buttonSpacing = when {
        isSmallScreen -> 8.dp
        isLandscape -> (screenMinDp * 0.022f).dp.coerceIn(8.dp, 14.dp)
        else -> (screenMinDp * 0.030f).dp.coerceIn(10.dp, 16.dp)
    }
    // Container corner radius
    val containerRadius = (screenMinDp * 0.030f).dp.coerceIn(10.dp, 18.dp)

    Column(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = if (landscape) 800.dp else maxWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(cardSpacing)
        ) {
            // Row 2: Renderer Card (Delay 150)
            AnimatedElement(
                visible = isVisible, staggerIndex = 1, cardShadow = true,
                totalItems = 4
            ) {
                RendererCard(
                    currentRenderer = currentRenderer,
                    commandOutput = commandOutput,
                    isSmallScreen = isSmallScreen,
                    colors = colors,
                    cardBackground = cardBackground,
                    shizukuReady = shizukuReady,
                    shizukuRunning = shizukuRunning,
                    shizukuStatus = shizukuStatus,
                    onShizukuErrorClick = onShizukuStatusClick,
                    oledMode = oledMode,
                    rendererLoading = rendererLoading,
                    lastSwitchTime = lastSwitchTime
                )
            }

            // Rows 3 & 4: Vulkan | OpenGL and Resources | GPUWatch — unified glassmorphism container
            AnimatedElement(
                visible = isVisible, staggerIndex = 2, cardShadow = true,
                totalItems = 4, enabled = shizukuReady
            ) {
                // Vulkan/OpenGL row: zoom out and dim when Shizuku is not ready,
                // matching the REMINDERS card style (scale 0.85f, alpha 0.25f).
                val rendererButtonScale by animateFloatAsState(
                    targetValue = if (shizukuReady) 1f else 0.80f,
                    animationSpec = spring(
                        dampingRatio = MotionTokens.Springs.gentle.dampingRatio,
                        stiffness = MotionTokens.Springs.gentle.stiffness
                    ),
                    label = "renderer_button_scale"
                )
                val rendererButtonAlpha by animateFloatAsState(
                    targetValue = if (shizukuReady) 1f else 0.50f,
                    animationSpec = tween(durationMillis = 320, easing = MotionTokens.Easing.velvet),
                    label = "renderer_button_alpha"
                )

                // Container with cleaner, even glow — avoids the faint-corner artifact from blur()+border.
                Box(modifier = Modifier.fillMaxWidth()) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val stroke = 1.dp.toPx()
                        drawRoundRect(
                            color = colors.primaryAccent.copy(alpha = if (oledMode) 0.16f else 0.12f),
                            cornerRadius = CornerRadius(containerRadius.toPx(), containerRadius.toPx())
                        )
                        drawRoundRect(
                            color = colors.primaryAccent.copy(alpha = 0.55f),
                            style = Stroke(width = stroke),
                            cornerRadius = CornerRadius(containerRadius.toPx(), containerRadius.toPx())
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                colors.primaryAccent.copy(alpha = 0.55f),
                                RoundedCornerShape(containerRadius)
                            )
                            .clip(RoundedCornerShape(containerRadius))
                            .background(cardBackground)
                    ) {
                        Box(modifier = Modifier.padding(containerPadding)) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(buttonSpacing)
                            ) {
                                // Row 3: Vulkan | OpenGL
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(buttonSpacing)
                                ) {
                                    IllustratedButton(
                                        text = LocalStrings.current["renderer.vulkan"].ifEmpty { "Vulkan" },
                                        onClick = onVulkanClick,
                                        modifier = Modifier
                                            .weight(1f)
                                            .graphicsLayer(
                                                scaleX = rendererButtonScale,
                                                scaleY = rendererButtonScale,
                                                alpha = rendererButtonAlpha
                                            ),
                                        accent = true,
                                        enabled = true,
                                        colors = colors,
                                        oledMode = oledMode,
                                        iconType = "vulkan"
                                    )
                                    IllustratedButton(
                                        text = LocalStrings.current["renderer.opengl"].ifEmpty { "OpenGL" },
                                        onClick = onOpenGLClick,
                                        modifier = Modifier
                                            .weight(1f)
                                            .graphicsLayer(
                                                scaleX = rendererButtonScale,
                                                scaleY = rendererButtonScale,
                                                alpha = rendererButtonAlpha
                                            ),
                                        accent = false,
                                        enabled = true,
                                        colors = colors,
                                        oledMode = oledMode,
                                        iconType = "opengl"
                                    )
                                }

                                // Row 4: Resources | Open GPUWatch
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(buttonSpacing)
                                ) {
                                    IllustratedButton(
                                        text = strings["renderer.library"].ifEmpty { "Library" },
                                        onClick = onResourcesClick,
                                        modifier = if (showGpuWatchButton) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                                        accent = false,
                                        enabled = true,
                                        colors = colors,
                                        oledMode = oledMode,
                                        iconType = "resources"
                                    )
                                    if (showGpuWatchButton) {
                                        IllustratedButton(
                                            text = LocalStrings.current["renderer.show_gpuwatch"].ifEmpty { "Open GPUWatch" },
                                            onClick = onGPUWatchClick,
                                            modifier = Modifier.weight(1f),
                                            accent = false,
                                            enabled = true,
                                            colors = colors,
                                            oledMode = oledMode,
                                            iconType = "gpuwatch"
                                        )
                                    }
                                }
                            } // close Column
                        } // close padding Box (Layer 3)
                    } // close inner border Box
                } // close outer glow Box
            }
        } // Close nested Column
    } // Close outer Column
}

private fun localizedActionSource(strings: GamaStrings, source: String): String = when (source) {
    "Quick Settings" -> strings["renderer.source_quick_settings"].ifEmpty { source }
    "Boot restore" -> strings["renderer.source_boot_restore"].ifEmpty { source }
    "Tasker" -> strings["renderer.source_tasker"].ifEmpty { source }
    "GAMA" -> strings["renderer.source_gama"].ifEmpty { source }
    else -> source
}

private fun localizedActionDetail(strings: GamaStrings, detail: String): String = when (detail) {
    "Renderer switch completed." -> strings["renderer.detail_switch_completed"].ifEmpty { detail }
    "No privileged backend became ready after boot." -> strings["renderer.detail_no_backend_boot"].ifEmpty { detail }
    "No privileged backend is available." -> strings["renderer.detail_no_backend"].ifEmpty { detail }
    "The renderer property could not be verified." -> strings["renderer.detail_not_verified"].ifEmpty { detail }
    "The renderer command failed unexpectedly." -> strings["renderer.detail_command_failed"].ifEmpty { detail }
    "Unknown renderer request." -> strings["renderer.detail_unknown_request"].ifEmpty { detail }
    else -> detail
}

@Composable
fun RendererCard(
    currentRenderer: String,
    commandOutput: String,
    isSmallScreen: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    shizukuReady: Boolean,
    shizukuRunning: Boolean,
    shizukuStatus: String,
    onShizukuErrorClick: () -> Unit,
    oledMode: Boolean = false,
    rendererLoading: Boolean = false,
    lastSwitchTime: Long = 0L,  // epoch millis; 0 means never recorded
    rootAvailable: Boolean = false,
    lastAction: RendererActionRecord? = null,
    backendName: String = "Shizuku"
) {
    // Root is a first-class backend: the card is "ready" when either Shizuku
    // permission or root access is available.
    val backendReady = shizukuReady || rootAvailable

    val density = LocalDensity.current
    val ts = LocalTypeScale.current
    val animLevel = LocalAnimationLevel.current
    val animSpeed = LocalAnimationSpeed.current
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val screenMinDp = minOf(configuration.screenWidthDp, configuration.screenHeightDp)
    // All spatial values derived from screen size — stays proportional at any resolution/orientation.
    val cardCorner = 28.dp
    val cardPadding = when {
        isSmallScreen -> 16.dp
        isLandscape -> (screenMinDp * 0.030f).dp.coerceIn(10.dp, 16.dp)
        else -> (screenMinDp * 0.055f).dp.coerceIn(20.dp, 28.dp)
    }
    val cardInnerSpacing = if (isLandscape) {
        (screenMinDp * 0.018f).dp.coerceIn(5.dp, 9.dp)
    } else {
        (screenMinDp * 0.024f).dp.coerceIn(9.dp, 15.dp)
    }
    val glowBlurRadius = (screenMinDp * 0.027f).dp.coerceIn(8.dp, 14.dp)
    val allowWholeCardGlow = oledMode

    // ── State color: accent when ready, red/amber when not ──────────────────
    val stateColor = when {
        backendReady -> colors.primaryAccent
        !shizukuRunning -> Color(0xFFFF3B30)
        else -> Color(0xFFE8A020)
    }


    // ── Pulse animations: only run when actually needed ───────────────────────
    // Each InfiniteTransition is created only in the branch where it's used.
    // On the happy path (backendReady = true) the error transitions don't exist —
    // zero ticks, zero slots, zero per-frame CPU on old chipsets.

    val warningBorderAlpha by if (!backendReady && animLevel != 2) {
        val t = rememberInfiniteTransition(label = "renderer_warning")
        t.animateFloat(
            initialValue = 0.30f, targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                tween(1100, easing = MotionTokens.Easing.silk), RepeatMode.Reverse
            ),
            label = "warning_border_alpha"
        )
    } else {
        remember { mutableFloatStateOf(0.30f) }
    }

    val glowAlpha by if (!backendReady && animLevel != 2) {
        val t = rememberInfiniteTransition(label = "renderer_glow")
        t.animateFloat(
            initialValue = 0.22f, targetValue = 0.55f,
            animationSpec = infiniteRepeatable(
                tween(1100, easing = MotionTokens.Easing.silk), RepeatMode.Reverse
            ),
            label = "renderer_glow_alpha"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    // (rendererTextAlpha removed — was declared but never consumed anywhere in RendererCard)

    // ── Press state: current renderer card owns its visible shell press state ─
    var currentRendererPressed by remember { mutableStateOf(false) }
    val rendererCardPress = remember { Animatable(0f) }
    LaunchedEffect(currentRendererPressed) {
        rendererCardPress.animateTo(
            targetValue = if (currentRendererPressed) 1f else 0f,
            animationSpec = when (animLevel) {
                2 -> snap()
                1 -> tween(
                    durationMillis = MotionTokens.SpeedUtil.durationMs(120, animSpeed),
                    easing = MotionTokens.Easing.emphasized
                )

                else -> spring(
                    dampingRatio = if (currentRendererPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
                    stiffness = MotionTokens.SpeedUtil.stiffness(
                        if (currentRendererPressed) MotionTokens.Springs.pressDown.stiffness else MotionTokens.Springs.pressUp.stiffness,
                        animSpeed
                    )
                )
            }
        )
    }
    val rcp = rendererCardPress.value  // single read; all visuals derived below
    // Same physical reaction language as SettingsNavigationCard / VISUALS:
    // the whole card compresses slightly and the outline thickens. Keep the text stable.
    val pressScale = 1f - rcp * 0.045f
    val nameScale = 1f
    val nameTY = 0f

    // ── Colors ───────────────────────────────────────────────────────────────
    val borderColor = if (!backendReady) {
        // Breathing outline in error/warning state
        stateColor.copy(alpha = warningBorderAlpha)
    } else {
        colors.primaryAccent.copy(alpha = 0.55f + rcp * 0.45f)
    }

    // Border width follows the same single press progress as the scale, exactly like SettingsNavigationCard.
    val rendererCardRestBorderWidth = if (oledMode) 0.75f else 1f
    val borderWidth = when {
        !backendReady -> 1.5.dp
        else -> (rendererCardRestBorderWidth + rcp * 0.65f).dp
    }
    val rendererCardPressedTintAlpha = if (backendReady) rcp * 0.08f else 0f

    val isDarkTheme = cardBackground.luminance() < 0.5f
    val subtleWarningBackground = if (!backendReady) {
        if (!shizukuRunning) {
            // Red state
            if (isDarkTheme)
            // Dark: nudge the dark card toward red
                cardBackground.copy(
                    red = (cardBackground.red + 0.12f).coerceAtMost(1f),
                    green = (cardBackground.green + 0.01f).coerceAtMost(1f),
                    blue = (cardBackground.blue + 0.01f).coerceAtMost(1f),
                    alpha = 1f
                )
            else
            // Light: punchy rose-red — contrasty and clearly in error state
                Color(0xFFFFD0CC)
        } else {
            // Amber/yellow state
            if (isDarkTheme)
            // Dark: nudge toward amber
                cardBackground.copy(
                    red = (cardBackground.red + 0.10f).coerceAtMost(1f),
                    green = (cardBackground.green + 0.06f).coerceAtMost(1f),
                    blue = (cardBackground.blue + 0.00f).coerceAtMost(1f),
                    alpha = 1f
                )
            else
            // Light: soft warm amber — gentle warning tint without being too intense
                Color(0xFFFFF5D6)
        }
    } else if (backendReady) {
        cardBackground
    } else {
        cardBackground
    }

    // ── Layout: glow blob behind + card on top ───────────────────────────────
    // The glow is larger than the card so it bleeds softly around all edges.
    // When Shizuku is not ready: colored shadow (red = not running, orange = no permission)
    // When Shizuku is ready: no shadow rendered at all (saves a blur pass)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Colored shadow blob — only rendered when NO backend is ready.
        // API 31+: blurred glow. API < 31: unblurred radial gradient at reduced
        // alpha — same colour signal, zero GPU blur cost on old chipsets.
        if (!backendReady) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .blur(radius = 32.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    stateColor.copy(alpha = glowAlpha),
                                    stateColor.copy(alpha = glowAlpha * 0.4f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    stateColor.copy(alpha = glowAlpha * 0.5f),
                                    stateColor.copy(alpha = glowAlpha * 0.15f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
        }

        // Card — scaled on press, all visuals inside the graphicsLayer so clip is respected
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Neon glow behind the card border
            if (backendReady && allowWholeCardGlow && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val cardGlowAlpha = 0.80f
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .blur(glowBlurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                        .border(
                            1.dp,
                            colors.primaryAccent.copy(alpha = cardGlowAlpha),
                            RoundedCornerShape(cardCorner)
                        )
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(scaleX = pressScale, scaleY = pressScale)
                    .pressedAccentOutlineGlow(
                        pressProgress = rcp,
                        color = colors.primaryAccent,
                        cornerRadius = cardCorner,
                        strokeWidth = borderWidth,
                        glowRadius = 12.dp
                    )
                    .clip(RoundedCornerShape(cardCorner))
                    .background(subtleWarningBackground)
                    .background(colors.primaryAccent.copy(alpha = rendererCardPressedTintAlpha))
                    .background(
                        Brush.radialGradient(
                            0.0f to colors.primaryAccent.copy(alpha = if (backendReady && allowWholeCardGlow) 0.13f else 0.0f),
                            0.58f to colors.primaryAccent.copy(alpha = if (backendReady && allowWholeCardGlow) 0.045f else 0.0f),
                            1.0f to Color.Transparent,
                            radius = with(density) { 210.dp.toPx() },
                            center = Offset.Unspecified
                        )
                    )
                    .background(
                        Brush.verticalGradient(
                            0.0f to colors.primaryAccent.copy(alpha = if (backendReady && allowWholeCardGlow) 0.035f else 0.0f),
                            0.55f to Color.Transparent,
                            1.0f to colors.primaryAccent.copy(alpha = if (backendReady && allowWholeCardGlow) 0.075f else 0.0f)
                        )
                    )
                    .border(
                        width = borderWidth,
                        color = borderColor,
                        shape = RoundedCornerShape(cardCorner)
                    )
                    .pointerInput(backendReady, onShizukuErrorClick) {
                        detectTapGestures(
                            onPress = {
                                val hapticStartedAt = GamaHaptics.pressStart(context, view)
                                currentRendererPressed = true
                                val released = tryAwaitRelease()
                                currentRendererPressed = false
                                GamaHaptics.releaseAfterPress(context, view, hapticStartedAt, released)
                                if (released && !backendReady) onShizukuErrorClick()
                            }
                        )
                    }
                    .semantics {
                        contentDescription = if (backendReady) {
                            strings["renderer.accessibility_current_renderer"]
                                .ifEmpty { "Current renderer: %s" }
                                .replace("%s", currentRenderer)
                        } else {
                            strings["renderer.accessibility_unavailable"]
                                .ifEmpty { "Renderer controls unavailable. Tap for setup help." }
                        }
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(cardPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(cardInnerSpacing)
                ) {
                    Text(
                        text = strings["renderer.current_renderer"].ifEmpty { "CURRENT RENDERER" },
                        color = if (!backendReady) stateColor else colors.primaryAccent.copy(alpha = 0.86f),
                        fontSize = ts.labelLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        fontFamily = quicksandFontFamily
                    )

                    val displayRenderer =
                        if (currentRenderer == "Default" || currentRenderer.isEmpty()) "OpenGL" else currentRenderer

                    lastAction?.let { action ->
                        val result = if (action.success) {
                            strings["renderer.last_action_succeeded"].ifEmpty { "Last action succeeded" }
                        } else {
                            strings["renderer.last_action_failed"].ifEmpty { "Last action failed" }
                        }
                        val actionSource = localizedActionSource(strings, action.source)
                        val actionDetail = localizedActionDetail(strings, action.detail)
                        Text(
                            text = "$result · $actionSource: $actionDetail",
                            color = if (action.success) colors.textSecondary else Color(0xFFFF6B6B),
                            fontSize = ts.labelSmall,
                            fontFamily = quicksandFontFamily,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    // Renderer name — shimmer skeleton while loading, then the real value
                    if (rendererLoading) {
                        // Skeleton shimmer: a pill-shaped placeholder that pulses while
                        // Shizuku detects the actual renderer.  Sized to roughly match
                        // the real renderer name text so layout doesn't shift on reveal.
                        val shimmerTransition = rememberInfiniteTransition(label = "renderer_skeleton")
                        val shimmerX by shimmerTransition.animateFloat(
                            initialValue = -1f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(900, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "renderer_skeleton_x"
                        )
                        Box(
                            modifier = Modifier
                                .width(110.dp)
                                .height(ts.headlineSmall.value.dp * 1.2f)
                                .clip(RoundedCornerShape(8.dp))
                                .drawWithContent {
                                    // Base muted fill
                                    drawRoundRect(
                                        color = colors.textSecondary.copy(alpha = 0.12f),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                                    )
                                    // Moving shimmer band
                                    val bandW = size.width * 0.55f
                                    val cx = shimmerX * (size.width + bandW) * 0.5f + size.width * 0.5f
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                colors.textSecondary.copy(alpha = 0.28f),
                                                Color.Transparent
                                            ),
                                            startX = cx - bandW * 0.5f,
                                            endX = cx + bandW * 0.5f
                                        )
                                    )
                                }
                        )
                    } else {
                        Text(
                            text = displayRenderer,
                            color = colors.textPrimary,
                            fontSize = ts.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = quicksandFontFamily,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.graphicsLayer(
                                scaleX = nameScale,
                                scaleY = nameScale,
                                translationY = nameTY
                            )
                        )
                    }

                    // Last-switched timestamp
                    if (lastSwitchTime > 0L && commandOutput.isEmpty()) {
                        val relativeTime = remember(lastSwitchTime, strings) {
                            val diff = System.currentTimeMillis() - lastSwitchTime
                            val minutes = diff / 60_000
                            val hours = diff / 3_600_000
                            val days = diff / 86_400_000
                            when {
                                minutes < 1 -> strings["renderer.api_changed_now"].ifEmpty { "Graphics API last changed just now" }
                                minutes < 60 -> strings["renderer.api_changed_minutes"].replace(
                                    "%d",
                                    minutes.toString()
                                ).ifEmpty { "Graphics API last changed ${minutes}m ago" }

                                hours < 24 -> strings["renderer.api_changed_hours"].replace("%d", hours.toString())
                                    .ifEmpty { "Graphics API last changed ${hours}h ago" }

                                days == 1L -> strings["renderer.api_changed_yesterday"].ifEmpty { "Graphics API last changed yesterday" }
                                else -> strings["renderer.api_changed_days"].replace("%d", days.toString())
                                    .ifEmpty { "Graphics API last changed ${days}d ago" }
                            }
                        }
                        Text(
                            text = relativeTime,
                            color = colors.textSecondary.copy(alpha = 0.45f),
                            fontSize = ts.labelSmall,
                            fontFamily = quicksandFontFamily,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    if (commandOutput.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(Color.Transparent, colors.border, Color.Transparent)
                                    )
                                )
                        )
                        Text(
                            text = commandOutput,
                            color = colors.textSecondary,
                            fontSize = ts.bodyMedium,
                            fontFamily = quicksandFontFamily,
                            textAlign = TextAlign.Start,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    if (!backendReady) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier
                                .wrapContentWidth()
                                .background(stateColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text("⚠️", fontSize = ts.labelMedium)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (!shizukuRunning) {
                                    strings["renderer.state_not_running"].ifEmpty { "%s not running" }
                                        .replace("%s", backendName)
                                } else {
                                    strings["renderer.state_permission_needed"].ifEmpty { "Permission needed" }
                                },
                                fontSize = ts.labelMedium,
                                color = stateColor,
                                fontFamily = quicksandFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colors: ThemeColors,
    cardBackground: Color,
    isSmallScreen: Boolean,
    oledMode: Boolean = false,
    enabled: Boolean = true,
    accentBorder: Boolean = false,
    content: (@Composable () -> Unit)? = null
) {
    val ts = LocalTypeScale.current
    val landscapeGrid = LocalLandscapePanelGrid.current
    val animSpeed = LocalAnimationSpeed.current
    val scale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.90f,
        animationSpec = spring(
            dampingRatio = 0.58f,
            stiffness = MotionTokens.SpeedUtil.stiffness(430f, animSpeed)
        ),
        label = "toggle_card_scale"
    )

    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = MotionTokens.SpeedUtil.durationMs(360, animSpeed),
            easing = MotionTokens.Easing.emphasized
        ),
        label = "toggle_card_alpha"
    )
    // --- checked-state colour expressions ---
    // Only animate the alpha/opacity of the accent — the color itself comes from the
    // global animated `colors.*`, so no local color animation is needed. Adding a
    // second animateColorAsState on top of an already-animating color causes it to
    // perpetually chase a moving target, producing the "delayed" appearance.

    // Border alpha: checked cards should visibly lift, even when accentBorder is true.
    // Renderer-panel toggles use accentBorder=true, so the unchecked state must be
    // calmer than the checked state instead of using the same outline opacity.
    val targetBorderAlpha = when {
        !enabled -> 0.24f
        checked -> 0.82f
        accentBorder -> 0.55f
        oledMode -> 0.30f
        else -> 0.20f
    }
    val borderAlpha by animateFloatAsState(
        targetValue = targetBorderAlpha,
        animationSpec = tween(
            durationMillis = MotionTokens.SpeedUtil.durationMs(240, animSpeed),
            easing = MotionTokens.Easing.enter
        ),
        label = "toggle_border_alpha"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (checked && enabled) 1.35.dp else 1.dp,
        animationSpec = tween(
            durationMillis = MotionTokens.SpeedUtil.durationMs(240, animSpeed),
            easing = MotionTokens.Easing.enter
        ),
        label = "toggle_border_width"
    )
    val cardBorderColor = colors.primaryAccent.copy(alpha = borderAlpha)

    // Left accent edge alpha — only the opacity transitions (boolean state change)
    val accentEdgeAlpha by animateFloatAsState(
        targetValue = if (checked && enabled) 1f else 0f,
        animationSpec = tween(
            durationMillis = MotionTokens.SpeedUtil.durationMs(240, animSpeed),
            easing = MotionTokens.Easing.enter
        ),
        label = "toggle_accent_edge_alpha"
    )

    val cardBg = cardBackground

    // Title alpha — only the opacity transitions, color follows global
    val titleAlpha by animateFloatAsState(
        targetValue = when {
            checked -> 1.0f
            else -> 0.7f
        },
        animationSpec = tween(
            durationMillis = MotionTokens.SpeedUtil.durationMs(240, animSpeed),
            easing = MotionTokens.Easing.enter
        ),
        label = "toggle_title_alpha"
    )
    val titleColor = if (enabled) {
        colors.primaryAccent.copy(alpha = titleAlpha)
    } else {
        colors.textSecondary.copy(alpha = 0.72f)
    }

    // Uniform card height — cards with a Switch tend to be taller than plain button-only
    // cards (like SettingsNavigationCard) because the Switch widget adds extra height.
    // Wrapping everything in a Box with heightIn(min = …) ensures ToggleCards and
    // NavigationCards share the same minimum height so rows of mixed card types align.
    val cardMinHeight = if (isSmallScreen) 72.dp else 80.dp

    CompositionLocalProvider(LocalCardEnabled provides enabled) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (landscapeGrid) 0.48f else 1f)
                .heightIn(min = cardMinHeight)
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    clip = false
                )
                .border(
                    width = borderWidth,
                    color = cardBorderColor,
                    shape = RoundedCornerShape(28.dp)
                )
                .then(if (!enabled) Modifier.pointerInput(enabled) {
                    // Intercept taps only — vertical scroll events pass through to parent scrollable
                    detectTapGestures { }
                } else Modifier)
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Blurred border overlay removed — RenderEffect per-card hurt scrolling fps badly on Snapdragon 8 Gen 2.
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = cardMinHeight)
                    .graphicsLayer(alpha = alpha),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = cardMinHeight)
                        .clip(RoundedCornerShape(28.dp))
                        .drawBehind {
                            // Minimalist left-edge indicator: a clean 3dp solid line, full card height
                            if (accentEdgeAlpha > 0f) {
                                val lineWidth = 3.dp.toPx()
                                drawRect(
                                    color = colors.primaryAccent.copy(alpha = accentEdgeAlpha),
                                    topLeft = Offset(0f, 0f),
                                    size = Size(lineWidth, size.height)
                                )
                            }
                        }
                ) {
                    if (content != null) {
                        content()
                    } else {
                        // Original layout for toggle cards (with switch)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = cardMinHeight)
                                .padding(
                                    top = if (isSmallScreen) 20.dp else 24.dp,
                                    bottom = if (isSmallScreen) 20.dp else 24.dp,
                                    start = if (isSmallScreen) 20.dp else 24.dp,
                                    end = if (isSmallScreen) 20.dp else 24.dp
                                ),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 16.dp)
                            ) {
                                Text(
                                    text = title,
                                    color = titleColor,
                                    fontSize = ts.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp,
                                    fontFamily = quicksandFontFamily
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = description,
                                    color = colors.textSecondary,
                                    fontSize = ts.bodyMedium,
                                    fontFamily = quicksandFontFamily,
                                    fontWeight = FontWeight.Bold // Force Bold
                                )
                            }

                            // Material Switch does not reliably animate palette swaps by itself,
                            // so theme changes looked like a snap even while the rest of the app
                            // eased slowly. Animate the final colours locally with the same gentle
                            // theme timing; checked-state motion stays handled by Switch itself.
                            val oledTrackAlpha by animateFloatAsState(
                                targetValue = if (oledMode) 1f else 0f,
                                animationSpec = tween(
                                    durationMillis = MotionTokens.SpeedUtil.durationMs(520, animSpeed),
                                    easing = MotionTokens.Easing.velvet
                                ),
                                label = "oled_track_alpha"
                            )
                            val targetCheckedTrackColor = lerp(colors.primaryAccent, Color(0xFF1A1A1A), oledTrackAlpha)
                            val targetUncheckedTrackColor = lerp(
                                colors.primaryAccent.copy(alpha = 0.20f),
                                Color(0xFF1A1A1A),
                                oledTrackAlpha
                            )
                            val targetCheckedThumbColor = lerp(Color.White, colors.primaryAccent, oledTrackAlpha)
                            val targetUncheckedThumbColor =
                                lerp(Color.White, colors.primaryAccent.copy(alpha = 0.6f), oledTrackAlpha)

                            val toggleColorSpec =
                                tween<Color>(
                                    durationMillis = MotionTokens.SpeedUtil.durationMs(760, animSpeed),
                                    easing = MotionTokens.Easing.velvet
                                )
                            val checkedTrackColor by animateColorAsState(
                                targetCheckedTrackColor,
                                toggleColorSpec,
                                label = "switch_checked_track_theme"
                            )
                            val uncheckedTrackColor by animateColorAsState(
                                targetUncheckedTrackColor,
                                toggleColorSpec,
                                label = "switch_unchecked_track_theme"
                            )
                            val checkedThumbColor by animateColorAsState(
                                targetCheckedThumbColor,
                                toggleColorSpec,
                                label = "switch_checked_thumb_theme"
                            )
                            val uncheckedThumbColor by animateColorAsState(
                                targetUncheckedThumbColor,
                                toggleColorSpec,
                                label = "switch_unchecked_thumb_theme"
                            )
                            val disabledCheckedTrackColor by animateColorAsState(
                                colors.primaryAccent.copy(alpha = 0.15f),
                                toggleColorSpec,
                                label = "switch_disabled_checked_track_theme"
                            )
                            val disabledUncheckedTrackColor by animateColorAsState(
                                colors.primaryAccent.copy(alpha = 0.10f),
                                toggleColorSpec,
                                label = "switch_disabled_unchecked_track_theme"
                            )
                            val disabledCheckedThumbColor by animateColorAsState(
                                colors.textSecondary.copy(alpha = 0.4f),
                                toggleColorSpec,
                                label = "switch_disabled_checked_thumb_theme"
                            )

                            val switchColors = SwitchDefaults.colors(
                                checkedThumbColor = checkedThumbColor,
                                checkedTrackColor = checkedTrackColor,
                                uncheckedThumbColor = uncheckedThumbColor,
                                uncheckedTrackColor = uncheckedTrackColor,
                                checkedBorderColor = Color.Transparent,
                                uncheckedBorderColor = Color.Transparent,
                                disabledCheckedThumbColor = disabledCheckedThumbColor,
                                disabledCheckedTrackColor = disabledCheckedTrackColor,
                                disabledUncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                                disabledUncheckedTrackColor = disabledUncheckedTrackColor
                            )

                            if (content != null) {
                                content()
                            } else {
                                Switch(
                                    checked = checked,
                                    onCheckedChange = onCheckedChange,
                                    colors = switchColors,
                                    enabled = enabled
                                )
                            }
                        } // end Row for toggle cards
                    } // end else for toggle cards
                } // end Card
            } // end outer Row (edge + content)
        } // end Box (contains Card)
    } // end CompositionLocalProvider
} // end ToggleCard function
