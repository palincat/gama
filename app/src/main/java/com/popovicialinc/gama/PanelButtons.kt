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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
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
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.roundToInt

private const val FLOATING_BUTTON_HOLD_MS = 2_900L
private const val FLOATING_BUTTON_INDICATOR_DELAY_MS = 500L



@Composable
fun BackArrowButton(
    onClick: () -> Unit,
    colors: ThemeColors,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val backContentDescription = tr("common.back", "Back")
    val isSmallScreen = LocalConfiguration.current.screenWidthDp.dp < 360.dp
    val buttonSize = if (isSmallScreen) 48.dp else 56.dp
    val iconSize = if (isSmallScreen) 22.dp else 26.dp

    // Static glow alpha — no infinite transition needed; the blur radius
    // already softens the shape and the button is only visible in dialogs.
    val glowAlpha = 0.22f

    var isPressed by remember { mutableStateOf(false) }
    // ── Single Animatable<Float> replaces 2 separate animators ───────────────
    val pressProgress = remember { Animatable(0f) }
    LaunchedEffect(isPressed) {
        pressProgress.animateTo(
            targetValue   = if (isPressed) 1f else 0f,
            animationSpec = spring(
                dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
                stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
            )
        )
    }
    val pp = pressProgress.value
    val pressScale = 1f - pp * (1f - MotionTokens.Scale.subtle)
    val borderWidthDp = (1.5f + pp * 0.5f).dp
    val borderColor = if (pp > 0.5f) colors.primaryAccent
    else colors.primaryAccent.copy(alpha = 0.35f + pp * 0.65f)

    val glowSize = buttonSize * 1.7f

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(glowSize)
    ) {
        // Blurred glow behind button — API 31+ only.
        // On older devices a plain radial gradient gives the same accent-colour
        // hint without touching the GPU blur pipeline.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Box(
                modifier = Modifier
                    .size(glowSize)
                    .blur(radius = 18.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                colors.primaryAccent.copy(alpha = glowAlpha),
                                colors.primaryAccent.copy(alpha = glowAlpha * 0.4f),
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
                                colors.primaryAccent.copy(alpha = glowAlpha * 0.45f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
        }

        // Circular button surface
        Box(
            modifier = Modifier
                .size(buttonSize)
                .graphicsLayer(scaleX = pressScale, scaleY = pressScale)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        listOf(
                            colors.primaryAccent.copy(alpha = 0.18f),
                            colors.primaryAccent.copy(alpha = 0.07f)
                        )
                    )
                )
                .background(colors.primaryAccent.copy(alpha = pp * 0.12f))
                .pressedAccentOutlineGlow(
                    pressProgress = pp,
                    color = colors.primaryAccent,
                    cornerRadius = 999.dp,
                    strokeWidth = borderWidthDp,
                    glowRadius = 9.dp
                )
                .border(borderWidthDp, borderColor, CircleShape)
                .semantics { contentDescription = backContentDescription }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            val hapticStartedAt = GamaHaptics.pressStart(context, view)
                            isPressed = true
                            val released = tryAwaitRelease()
                            isPressed = false
                            GamaHaptics.releaseAfterPress(context, view, hapticStartedAt, released)
                        },
                        onTap = { onClick() }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(iconSize)) {
                val arrowPath = Path().apply {
                    moveTo(size.width * 0.62f, size.height * 0.28f)
                    lineTo(size.width * 0.32f, size.height * 0.50f)
                    lineTo(size.width * 0.62f, size.height * 0.72f)
                }
                drawPath(
                    path = arrowPath,
                    color = colors.primaryAccent.copy(alpha = 0.9f),
                    style = Stroke(
                        width = 2.4.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}

@Composable
internal fun Modifier.repositionFloatingButtonOnLongHold(
    isLeftSide: Boolean,
    holdState: FloatingButtonHoldState?
): Modifier {
    if (holdState == null) return this
    val controller = LocalFloatingButtonPositionController.current
    val view = LocalView.current
    return pointerInput(isLeftSide, controller, view.width, view.height) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // Positioning is intentionally deliberate: a regular tap still works,
            // while a three-second stationary hold enters drag mode.
            holdState.holdStartedAtMs = SystemClock.uptimeMillis()
            holdState.holdProgress = 0f
            val endedBeforeHold = withTimeoutOrNull(FLOATING_BUTTON_HOLD_MS) {
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                        ?: return@withTimeoutOrNull true
                    if (change.changedToUpIgnoreConsumed()) return@withTimeoutOrNull true
                }
            }
            if (endedBeforeHold != null) return@awaitEachGesture

            holdState.repositioning = true
            holdState.suppressTap = true
            var lastPosition = down.position
            var x = if (isLeftSide) controller.leftX else controller.rightX
            var y = if (isLeftSide) controller.leftY else controller.rightY
            val horizontalSpan = (if (controller.fullWidth) view.width.toFloat() else view.width / 2f).coerceAtLeast(1f)
            val height = view.height.toFloat().coerceAtLeast(1f)

            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (change.changedToUpIgnoreConsumed()) break
                val delta = change.position - lastPosition
                lastPosition = change.position
                x = (x + delta.x / horizontalSpan).coerceIn(0.16f, 0.84f)
                y = (y + delta.y / height).coerceIn(0.08f, 0.92f)
                if (isLeftSide) controller.onLeftMove(x, y) else controller.onRightMove(x, y)
            }
                holdState.repositioning = false
            holdState.holdProgress = 0f
        }
    }
}

@Composable
internal fun Modifier.floatingButtonGesture(
    enabled: Boolean,
    isLeftSide: Boolean,
    isSettingsButton: Boolean = false,
    holdState: FloatingButtonHoldState?,
    onPressedChange: (Boolean) -> Unit,
    onClick: () -> Unit
): Modifier {
    if (!enabled) return this
    val controllerState = rememberUpdatedState(LocalFloatingButtonPositionController.current)
    val latestPressedChange by rememberUpdatedState(onPressedChange)
    val latestClick by rememberUpdatedState(onClick)
    val context = LocalContext.current
    val view = LocalView.current
    return pointerInput(enabled, isLeftSide, holdState) {
        awaitPointerEventScope {
            while (true) {
                val down = awaitPointerEvent().changes.firstOrNull { it.changedToDownIgnoreConsumed() }
                    ?: continue
            val hapticStartedAt = GamaHaptics.pressStart(context, view)
            latestPressedChange(true)
            holdState?.holding = true
            holdState?.holdStartedAtMs = SystemClock.uptimeMillis()
            holdState?.holdProgress = 0f
            val endedBeforeHold = withTimeoutOrNull(FLOATING_BUTTON_HOLD_MS) {
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                        ?: return@withTimeoutOrNull true
                    if (change.changedToUpIgnoreConsumed()) return@withTimeoutOrNull true
                }
            }
            if (endedBeforeHold != null) {
                holdState?.holding = false
                holdState?.holdProgress = 0f
                latestPressedChange(false)
                GamaHaptics.releaseAfterPress(context, view, hapticStartedAt, true)
                latestClick()
                continue
            }

            // The same pointer stream now owns the drag, so releasing after a
            // three-second hold cannot also trigger the button's normal action.
            holdState?.repositioning = true
            holdState?.suppressTap = true
            val controller = controllerState.value
            val originX = when {
                isSettingsButton -> controller.settingsX
                isLeftSide -> controller.leftX
                else -> controller.rightX
            }
            val originY = when {
                isSettingsButton -> controller.settingsY
                isLeftSide -> controller.leftY
                else -> controller.rightY
            }
            var totalDelta = Offset.Zero
            val fullWidth = if (isSettingsButton) controller.settingsFullWidth else controller.fullWidth
            val horizontalSpan = (if (fullWidth) view.width.toFloat() else view.width / 2f).coerceAtLeast(1f)
            val height = view.height.toFloat().coerceAtLeast(1f)
            val minX = if (fullWidth) 0.04f else 0.16f
            val maxX = if (fullWidth) 0.96f else 0.84f
            val minY = 0.50f
            val grid = adaptiveSquareSnapGrid(
                horizontalPixels = horizontalSpan,
                verticalPixels = height * (0.96f - minY)
            )
            var finalX = originX
            var finalY = originY
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (change.changedToUpIgnoreConsumed()) break
                // Graphics translation changes the local pointer coordinates. Add
                // the current visual translation back to obtain a stable physical
                // delta from the original finger-down location.
                totalDelta = change.position - down.position + Offset(
                    holdState?.dragTranslationX ?: 0f,
                    holdState?.dragTranslationY ?: 0f
                )
                val previewX = snapFloatingButtonGrid(
                    (originX + totalDelta.x / horizontalSpan).coerceIn(minX, maxX), minX, maxX, grid.horizontalSteps
                )
                val previewY = snapFloatingButtonGrid(
                    (originY + totalDelta.y / height).coerceIn(minY, 0.96f), minY, 0.96f, grid.verticalSteps
                )
                finalX = previewX
                finalY = previewY
                holdState?.dragTranslationX = (previewX - originX) * horizontalSpan
                holdState?.dragTranslationY = (previewY - originY) * height
                controller.livePositions.set(isLeftSide, isSettingsButton, previewX, previewY)
            }
            // Commit exactly the last previewed grid cell. Recomputing from the
            // transformed node's local pointer coordinates here can reintroduce
            // the old anchor on the pointer-up event.
            when {
                isSettingsButton -> controller.onSettingsMove(finalX, finalY)
                isLeftSide -> controller.onLeftMove(finalX, finalY)
                else -> controller.onRightMove(finalX, finalY)
            }
            holdState?.dragTranslationX = 0f
            holdState?.dragTranslationY = 0f
            holdState?.holding = false
            holdState?.holdProgress = 0f
            latestPressedChange(false)
            GamaHaptics.releaseAfterPress(context, view, hapticStartedAt, false)
            holdState?.repositioning = false
            }
        }
    }
}

private fun snapFloatingButtonGrid(value: Float, min: Float, max: Float, steps: Int): Float {
    val fraction = ((value - min) / (max - min)).coerceIn(0f, 1f)
    return min + (fraction * steps).roundToInt().coerceIn(0, steps) * (max - min) / steps
}

private data class FloatingSnapGrid(val horizontalSteps: Int, val verticalSteps: Int)

private fun adaptiveSquareSnapGrid(horizontalPixels: Float, verticalPixels: Float): FloatingSnapGrid {
    val ratio = (horizontalPixels / verticalPixels).coerceAtLeast(0.01f)
    return if (ratio >= 1f) {
        val verticalSteps = 4
        FloatingSnapGrid(
            horizontalSteps = (verticalSteps * ratio).roundToInt().coerceIn(4, 20),
            verticalSteps = verticalSteps
        )
    } else {
        val horizontalSteps = 4
        FloatingSnapGrid(
            horizontalSteps = horizontalSteps,
            verticalSteps = (horizontalSteps / ratio).roundToInt().coerceIn(4, 20)
        )
    }
}

@Composable
internal fun FloatingHoldIndicator(
    holdState: FloatingButtonHoldState?,
    color: Color,
    indicatorSize: Dp
) {
    val progress = holdState?.holdProgress ?: 0f
    LaunchedEffect(holdState?.holding, holdState?.holdStartedAtMs) {
        if (holdState?.holding != true) {
            holdState?.holdProgress = 0f
            return@LaunchedEffect
        }
        while (holdState.holding) {
            val elapsed = SystemClock.uptimeMillis() - holdState.holdStartedAtMs
            holdState.holdProgress = if (elapsed <= FLOATING_BUTTON_INDICATOR_DELAY_MS) {
                0f
            } else {
                ((elapsed - FLOATING_BUTTON_INDICATOR_DELAY_MS).toFloat() /
                    (FLOATING_BUTTON_HOLD_MS - FLOATING_BUTTON_INDICATOR_DELAY_MS)).coerceIn(0f, 1f)
            }
            withFrameNanos { }
        }
    }
    if (progress <= 0f) return
    Canvas(modifier = Modifier.size(indicatorSize)) {
        val stroke = 2.5.dp.toPx()
        val radius = minOf(size.width, size.height) * (0.34f + progress * 0.13f)
        drawArc(
            color = color.copy(alpha = 0.42f + progress * 0.5f),
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            topLeft = Offset(this.size.width / 2f - radius, this.size.height / 2f - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun PanelBackButton(
    onClick: () -> Unit,
    colors: ThemeColors,
    oledMode: Boolean = false,
    isSmallScreen: Boolean = false,
    enabled: Boolean = true,
    scrollState: ScrollState? = null,
    floatingHoldState: FloatingButtonHoldState? = null,
    isLeftSide: Boolean = false,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    // scrollState is kept as a parameter for future use (e.g. auto-scroll-to-top on tap),
    // but the back button is always rendered regardless of whether the content scrolls.
    // Hiding it when content fits on screen caused panels to have no visible back button
    // on larger displays or when panel content was short.

    val animLevel = LocalAnimationLevel.current
    val animSpeed = LocalAnimationSpeed.current
    val context = LocalContext.current
    val view = LocalView.current
    val currentOnClick by rememberUpdatedState(onClick)
    val btnSize  = if (isSmallScreen) 48.dp else 52.dp
    val iconSize = if (isSmallScreen) 22.dp else 26.dp
    val backContentDescription = tr("common.back", "Back")

    // Static glow alpha — back button is shown inside already-open panels;
    // running an infinite transition here would tick every frame for the
    // entire duration the panel is visible.  Static value is indistinguishable.
    val glowAlpha = 0.26f

    var isPressed by remember { mutableStateOf(false) }
    // ── Single Animatable<Float> replaces 4 separate animators ───────────────
    val pressProgress = remember { Animatable(0f) }
    LaunchedEffect(isPressed) {
        pressProgress.animateTo(
            targetValue   = if (isPressed) 1f else 0f,
            animationSpec = when (animLevel) {
                2 -> snap()
                1 -> tween(durationMillis = MotionTokens.SpeedUtil.durationMs(120, animSpeed), easing = MotionTokens.Easing.emphasized)
                else -> spring(
                    dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
                    stiffness    = MotionTokens.SpeedUtil.stiffness(if (isPressed) MotionTokens.Springs.pressDown.stiffness else MotionTokens.Springs.pressUp.stiffness, animSpeed)
                )
            }
        )
    }
    val pp = pressProgress.value
    val appearScale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.82f,
        animationSpec = if (animLevel == 2) snap() else if (animLevel == 1) tween(durationMillis = MotionTokens.SpeedUtil.durationMs(240, animSpeed), easing = MotionTokens.Easing.emphasized) else spring(dampingRatio = 0.58f, stiffness = MotionTokens.SpeedUtil.stiffness(360f, animSpeed)),
        label = "panel_back_appear_scale"
    )
    val pressScale    = (1f - pp * (1f - MotionTokens.Scale.subtle)) * appearScale
    val chevronTX     = pp * with(density) { -3.dp.toPx() }
    val borderWidthDp = (1.5f + pp * 0.5f).dp
    val borderAlphaVal = 0.4f + pp * 0.6f

    val glowSize = btnSize * 1.8f

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Glow blob — API 31+ only; plain radial gradient fallback on older devices.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Box(
                modifier = Modifier
                    .size(glowSize)
                    .blur(radius = 20.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                colors.primaryAccent.copy(alpha = glowAlpha),
                                colors.primaryAccent.copy(alpha = glowAlpha * 0.4f),
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
                                colors.primaryAccent.copy(alpha = glowAlpha * 0.45f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
        }
        // Button surface — square with rounded corners, same as Settings button
        FloatingHoldIndicator(floatingHoldState, colors.primaryAccent, glowSize)
        Box(
            modifier = Modifier
                .size(btnSize)
                .graphicsLayer(scaleX = pressScale, scaleY = pressScale)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.radialGradient(
                        listOf(
                            colors.primaryAccent.copy(alpha = 0.22f),
                            colors.primaryAccent.copy(alpha = 0.08f)
                        )
                    )
                )
                .background(colors.primaryAccent.copy(alpha = pp * 0.12f))
                .pressedAccentOutlineGlow(
                    pressProgress = pp,
                    color = colors.primaryAccent,
                    cornerRadius = 28.dp,
                    strokeWidth = borderWidthDp,
                    glowRadius = 10.dp
                )
                .border(
                    borderWidthDp,
                    colors.primaryAccent.copy(alpha = borderAlphaVal),
                    RoundedCornerShape(28.dp)
                )
                .semantics { contentDescription = backContentDescription }
                .floatingButtonGesture(
                    enabled = enabled,
                    isLeftSide = isLeftSide,
                    holdState = floatingHoldState,
                    onPressedChange = { isPressed = it },
                    onClick = currentOnClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer(translationX = chevronTX)
            ) {
                val path = Path().apply {
                    moveTo(size.width * 0.62f, size.height * 0.25f)
                    lineTo(size.width * 0.32f, size.height * 0.50f)
                    lineTo(size.width * 0.62f, size.height * 0.75f)
                }
                drawPath(
                    path = path,
                    color = colors.primaryAccent.copy(alpha = 0.9f),
                    style = Stroke(
                        width = 2.4.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }

}

@Composable
fun PanelSearchButton(
    onClick: () -> Unit,
    colors: ThemeColors,
    oledMode: Boolean = false,
    isSmallScreen: Boolean = false,
    enabled: Boolean = true,
    floatingHoldState: FloatingButtonHoldState? = null,
    isLeftSide: Boolean = false,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val animLevel = LocalAnimationLevel.current
    val animSpeed = LocalAnimationSpeed.current
    val strings = LocalStrings.current
    val context = LocalContext.current
    val view = LocalView.current
    val currentOnClick by rememberUpdatedState(onClick)
    val btnSize  = if (isSmallScreen) 48.dp else 52.dp
    val iconSize = if (isSmallScreen) 23.dp else 27.dp
    val glowAlpha = 0.26f

    var isPressed by remember { mutableStateOf(false) }
    val pressProgress = remember { Animatable(0f) }
    LaunchedEffect(isPressed) {
        pressProgress.animateTo(
            targetValue = if (isPressed) 1f else 0f,
            animationSpec = when (animLevel) {
                2 -> snap()
                1 -> tween(durationMillis = MotionTokens.SpeedUtil.durationMs(120, animSpeed), easing = MotionTokens.Easing.emphasized)
                else -> spring(
                    dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
                    stiffness    = MotionTokens.SpeedUtil.stiffness(if (isPressed) MotionTokens.Springs.pressDown.stiffness else MotionTokens.Springs.pressUp.stiffness, animSpeed)
                )
            }
        )
    }

    val pp = pressProgress.value
    val appearScale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.82f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = MotionTokens.SpeedUtil.stiffness(360f, animSpeed)),
        label = "panel_search_appear_scale"
    )
    val pressScale = (1f - pp * (1f - MotionTokens.Scale.subtle)) * appearScale
    val iconTY = pp * with(density) { 1.5.dp.toPx() }
    val borderWidthDp = (1.5f + pp * 0.5f).dp
    val borderAlphaVal = 0.4f + pp * 0.6f
    val glowSize = btnSize * 1.8f

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Box(
                modifier = Modifier
                    .size(glowSize)
                    .blur(radius = 20.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                colors.primaryAccent.copy(alpha = glowAlpha),
                                colors.primaryAccent.copy(alpha = glowAlpha * 0.4f),
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
                                colors.primaryAccent.copy(alpha = glowAlpha * 0.45f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
        }

        FloatingHoldIndicator(floatingHoldState, colors.primaryAccent, glowSize)
        Box(
            modifier = Modifier
                .size(btnSize)
                .graphicsLayer(scaleX = pressScale, scaleY = pressScale)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.radialGradient(
                        listOf(
                            colors.primaryAccent.copy(alpha = 0.22f),
                            colors.primaryAccent.copy(alpha = 0.08f)
                        )
                    )
                )
                .background(colors.primaryAccent.copy(alpha = pp * 0.12f))
                .pressedAccentOutlineGlow(
                    pressProgress = pp,
                    color = colors.primaryAccent,
                    cornerRadius = 28.dp,
                    strokeWidth = borderWidthDp,
                    glowRadius = 10.dp
                )
                .border(
                    borderWidthDp,
                    colors.primaryAccent.copy(alpha = borderAlphaVal),
                    RoundedCornerShape(28.dp)
                )
                .semantics { contentDescription = strings["search.content_description"].ifEmpty { "Search settings" } }
                .floatingButtonGesture(
                    enabled = enabled,
                    isLeftSide = isLeftSide,
                    holdState = floatingHoldState,
                    onPressedChange = { isPressed = it },
                    onClick = currentOnClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer(translationY = iconTY)
            ) {
                val strokeWidth = 2.3.dp.toPx()
                val radius = size.minDimension * 0.30f
                val center = Offset(size.width * 0.43f, size.height * 0.43f)
                drawCircle(
                    color = colors.primaryAccent.copy(alpha = 0.9f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                drawLine(
                    color = colors.primaryAccent.copy(alpha = 0.9f),
                    start = Offset(center.x + radius * 0.70f, center.y + radius * 0.70f),
                    end = Offset(size.width * 0.78f, size.height * 0.78f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun PanelGlobalButton(
    onClick: () -> Unit,
    colors: ThemeColors,
    oledMode: Boolean = false,
    isSmallScreen: Boolean = false,
    enabled: Boolean = true,
    floatingHoldState: FloatingButtonHoldState? = null,
    isLeftSide: Boolean = false,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val animLevel = LocalAnimationLevel.current
    val animSpeed = LocalAnimationSpeed.current
    val strings = LocalStrings.current
    val context = LocalContext.current
    val view = LocalView.current
    val currentOnClick by rememberUpdatedState(onClick)
    val btnSize  = if (isSmallScreen) 48.dp else 52.dp
    val iconSize = if (isSmallScreen) 24.dp else 28.dp
    val glowAlpha = 0.26f

    var isPressed by remember { mutableStateOf(false) }
    val pressProgress = remember { Animatable(0f) }
    LaunchedEffect(isPressed) {
        pressProgress.animateTo(
            targetValue = if (isPressed) 1f else 0f,
            animationSpec = when (animLevel) {
                2 -> snap()
                1 -> tween(durationMillis = MotionTokens.SpeedUtil.durationMs(120, animSpeed), easing = MotionTokens.Easing.emphasized)
                else -> spring(
                    dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
                    stiffness    = MotionTokens.SpeedUtil.stiffness(if (isPressed) MotionTokens.Springs.pressDown.stiffness else MotionTokens.Springs.pressUp.stiffness, animSpeed)
                )
            }
        )
    }

    val pp = pressProgress.value
    val appearScale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.82f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = MotionTokens.SpeedUtil.stiffness(360f, animSpeed)),
        label = "panel_global_appear_scale"
    )
    val pressScale = (1f - pp * (1f - MotionTokens.Scale.subtle)) * appearScale
    val iconTY = pp * with(density) { 1.5.dp.toPx() }
    val borderWidthDp = (1.5f + pp * 0.5f).dp
    val borderAlphaVal = 0.4f + pp * 0.6f
    val glowSize = btnSize * 1.8f

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Box(
                modifier = Modifier
                    .size(glowSize)
                    .blur(radius = 20.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                colors.primaryAccent.copy(alpha = glowAlpha),
                                colors.primaryAccent.copy(alpha = glowAlpha * 0.4f),
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
                                colors.primaryAccent.copy(alpha = glowAlpha * 0.45f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
        }

        FloatingHoldIndicator(floatingHoldState, colors.primaryAccent, glowSize)
        Box(
            modifier = Modifier
                .size(btnSize)
                .graphicsLayer(scaleX = pressScale, scaleY = pressScale)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.radialGradient(
                        listOf(
                            colors.primaryAccent.copy(alpha = 0.22f),
                            colors.primaryAccent.copy(alpha = 0.08f)
                        )
                    )
                )
                .background(colors.primaryAccent.copy(alpha = pp * 0.12f))
                .pressedAccentOutlineGlow(
                    pressProgress = pp,
                    color = colors.primaryAccent,
                    cornerRadius = 28.dp,
                    strokeWidth = borderWidthDp,
                    glowRadius = 10.dp
                )
                .border(
                    borderWidthDp,
                    colors.primaryAccent.copy(alpha = borderAlphaVal),
                    RoundedCornerShape(28.dp)
                )
                .semantics { contentDescription = strings["search.global_shortcut_content_description"].ifEmpty { "Open global settings list" } }
                .floatingButtonGesture(
                    enabled = enabled,
                    isLeftSide = isLeftSide,
                    holdState = floatingHoldState,
                    onPressedChange = { isPressed = it },
                    onClick = currentOnClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer(translationY = iconTY)
            ) {
                val strokeWidth = 2.1.dp.toPx()
                val globeColor = colors.primaryAccent.copy(alpha = 0.92f)
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension * 0.39f
                val meridianSize = Size(radius * 1.05f, radius * 2.0f)
                val meridianTopLeft = Offset(center.x - meridianSize.width / 2f, center.y - meridianSize.height / 2f)

                drawCircle(
                    color = globeColor,
                    radius = radius,
                    center = center,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                drawLine(
                    color = globeColor,
                    start = Offset(center.x - radius * 0.88f, center.y),
                    end = Offset(center.x + radius * 0.88f, center.y),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawArc(
                    color = globeColor,
                    startAngle = 90f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = meridianTopLeft,
                    size = meridianSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                drawArc(
                    color = globeColor,
                    startAngle = 270f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = meridianTopLeft,
                    size = meridianSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                val latitudeSize = Size(radius * 1.65f, radius * 0.82f)
                val latitudeTopLeft = Offset(center.x - latitudeSize.width / 2f, center.y - latitudeSize.height / 2f)

                drawArc(
                    color = globeColor.copy(alpha = 0.78f),
                    startAngle = 20f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = latitudeTopLeft.copy(y = latitudeTopLeft.y - radius * 0.33f),
                    size = latitudeSize,
                    style = Stroke(width = strokeWidth * 0.75f, cap = StrokeCap.Round)
                )
                drawArc(
                    color = globeColor.copy(alpha = 0.78f),
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = latitudeTopLeft.copy(y = latitudeTopLeft.y + radius * 0.33f),
                    size = latitudeSize,
                    style = Stroke(width = strokeWidth * 0.75f, cap = StrokeCap.Round)
                )
            }
        }
    }
}

