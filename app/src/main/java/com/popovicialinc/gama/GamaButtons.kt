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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick as semanticOnClick
import androidx.compose.ui.semantics.role
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



@Composable
fun FlatButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    enabled: Boolean = true,
    colors: ThemeColors,
    maxLines: Int,
    oledMode: Boolean = false,
    cornerRadius: Dp = 28.dp
) {
    val density = LocalDensity.current
    val animLevel = LocalAnimationLevel.current
    val animSpeed = LocalAnimationSpeed.current
    val context = LocalContext.current
    val view = LocalView.current
    val isSmallScreen = LocalConfiguration.current.screenWidthDp.dp < 360.dp

    val baseHeight = if (isSmallScreen) 54.dp else 58.dp
    val ts = LocalTypeScale.current
    val baseFontSize = ts.buttonLarge
    val shape = RoundedCornerShape(cornerRadius)

    // FlatButton owns its own press state because it uses pointerInput/detectTapGestures
    // rather than a clickable InteractionSource.
    var isPressed by remember { mutableStateOf(false) }

    // Single Animatable<Float> [0=rest, 1=pressed] drives ALL press visuals via
    // graphicsLayer — replaces 5 separate animateFloatAsState/animateDpAsState calls
    // that previously ran simultaneously on every press/release.
    val pressProgress = remember { Animatable(0f) }
    LaunchedEffect(isPressed) {
        pressProgress.animateTo(
            targetValue   = if (isPressed && enabled) 1f else 0f,
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
    val pp = pressProgress.value  // single read; all visuals derived below
    val nudgePx = with(density) { 1.5.dp.toPx() }
    val restBorderW = if (oledMode) 0.75f else 1f
    // Derived press visuals — evaluated in draw phase via graphicsLayer, zero recompose
    val pressScale    = 1f - pp * (1f - MotionTokens.Scale.subtle)
    val textScale     = 1f - pp * 0.07f
    val textTranslateY = pp * nudgePx
    val borderWidthDp  = (restBorderW + pp * restBorderW).dp   // 1dp → 2dp
    val borderAlpha    = 0.5f + pp * 0.5f

    val animatedButtonColor = if (!enabled) colors.textSecondary.copy(alpha = 0.05f)
    else if (accent && !oledMode) colors.primaryAccent
    else colors.cardBackground

    val contentColor = if (accent && !oledMode) {
        if (colors.primaryAccent.luminance() > 0.5f) Color.Black else Color.White
    } else colors.textPrimary
    val animatedTextColor = if (!enabled) colors.textSecondary.copy(alpha = 0.3f) else contentColor

    // On press: full accent border. At rest: accent at consistent alpha.
    val borderColor = when {
        !enabled             -> colors.primaryAccent.copy(alpha = 0.25f)
        isPressed            -> colors.primaryAccent
        accent               -> colors.primaryAccent.copy(alpha = 0.55f)
        else                 -> colors.primaryAccent.copy(alpha = 0.55f)
    }

    val shouldShowBorder = true

    // Outer Box: handles layout sizing (modifier carries weight/fill/etc.) — never scaled
    Box(
        modifier = modifier.heightIn(min = baseHeight),
        contentAlignment = Alignment.Center
    ) {
        // Neon border glow — previously a Modifier.blur(9dp) Box (= full RenderEffect per button).
        // Replaced: a cheap drawBehind radial gradient gives the same "lit edge" feel at zero blur cost.
        // Four buttons on screen × one RenderEffect each = four GPU pipelines removed per frame.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = pressScale, scaleY = pressScale)
                .clip(shape)
                .background(animatedButtonColor)
                .background(colors.primaryAccent.copy(alpha = if (enabled) pp * 0.10f else 0f))
                .then(
                    if (shouldShowBorder) Modifier
                        .pressedAccentOutlineGlow(
                            pressProgress = pp,
                            color = colors.primaryAccent,
                            cornerRadius = cornerRadius,
                            strokeWidth = borderWidthDp,
                            glowRadius = 10.dp
                        )
                        .border(
                            width = borderWidthDp,
                            color = borderColor,
                            shape = shape
                        ) else Modifier
                )
                .then(
                    Modifier.semantics(mergeDescendants = true) {
                        role = Role.Button
                        semanticOnClick(label = text) {
                            if (enabled) onClick()
                            enabled
                        }
                    }
                )
                .then(
                    if (enabled) Modifier.pointerInput(enabled) {
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
                    } else Modifier
                )
        ) {
            Text(
                text = text,
                fontSize = baseFontSize,
                fontWeight = FontWeight.Bold,
                color = animatedTextColor,
                fontFamily = quicksandFontFamily,
                maxLines = maxLines.coerceAtLeast(1),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .graphicsLayer(
                        scaleX = textScale,
                        scaleY = textScale,
                        translationY = textTranslateY
                    )
            )
        }
    }
}

@Composable
private fun MainMenuPressShell(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ThemeColors,
    cardBackground: Color,
    shape: RoundedCornerShape,
    oledMode: Boolean = false,
    selected: Boolean = false,
    hapticStyle: String = "regular",
    semanticsLabel: String? = null,
    contentAlignment: Alignment = Alignment.Center,
    onClick: () -> Unit,
    content: @Composable BoxScope.(Float) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val animLevel = LocalAnimationLevel.current
    val animSpeed = LocalAnimationSpeed.current

    val disabledScale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.88f,
        animationSpec = if (animLevel == 2) snap() else if (animLevel == 1) tween(durationMillis = MotionTokens.SpeedUtil.durationMs(220, animSpeed), easing = MotionTokens.Easing.emphasized) else spring(dampingRatio = 0.60f, stiffness = MotionTokens.SpeedUtil.stiffness(440f, animSpeed)),
        label = "main_menu_shell_disabled_scale"
    )
    val disabledAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.32f,
        animationSpec = tween(durationMillis = MotionTokens.SpeedUtil.durationMs(360, animSpeed), easing = MotionTokens.Easing.emphasized),
        label = "main_menu_shell_disabled_alpha"
    )

    var isPressed by remember { mutableStateOf(false) }
    val pressProgress = remember { Animatable(0f) }
    LaunchedEffect(isPressed, enabled, animLevel) {
        val target = if (isPressed && enabled) 1f else 0f
        when (animLevel) {
            2 -> pressProgress.snapTo(target)
            1 -> pressProgress.animateTo(target, animationSpec = tween(durationMillis = MotionTokens.SpeedUtil.durationMs(135, animSpeed), easing = MotionTokens.Easing.emphasized))
            else -> pressProgress.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
                    stiffness = MotionTokens.SpeedUtil.stiffness(if (isPressed) MotionTokens.Springs.pressDown.stiffness else MotionTokens.Springs.pressUp.stiffness, animSpeed)
                )
            )
        }
    }

    val p = pressProgress.value
    val pressScale = 1f - p * (1f - MotionTokens.Scale.subtle)
    val restBorderWidth = if (oledMode) 0.75f else 1f
    val borderWidth = (restBorderWidth + p * restBorderWidth).dp
    val borderColor = when {
        !enabled -> colors.primaryAccent.copy(alpha = 0.25f)
        selected -> colors.primaryAccent.copy(alpha = 0.82f + p * 0.18f)
        else -> colors.primaryAccent.copy(alpha = 0.55f + p * 0.45f)
    }

    Box(
        modifier = modifier
            .graphicsLayer(
                scaleX = disabledScale * pressScale,
                scaleY = disabledScale * pressScale,
                alpha = disabledAlpha,
                clip = false
            )
            .then(if (semanticsLabel != null) Modifier.semantics(mergeDescendants = true) { contentDescription = semanticsLabel } else Modifier)
            // Do NOT use the blurred outline glow on the main menu shell.
            // On these larger nested cards the blur layer can read as a rectangular
            // box around the rounded button. Keep the main menu clean: crisp outline,
            // clipped press tint inside the real rounded shape.
            .border(width = borderWidth, color = borderColor, shape = shape),
        contentAlignment = contentAlignment
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                // Clipped glow: this keeps the held glow, but forces it to respect
                // the actual rounded button shape instead of drawing a rectangular
                // blur layer around the main menu card.
                .pressedAccentOutlineGlow(
                    pressProgress = p,
                    color = colors.primaryAccent,
                    cornerRadius = 28.dp,
                    strokeWidth = borderWidth,
                    glowRadius = 8.dp,
                    maxAlpha = 0.34f
                )
                .background(cardBackground)
                .background(colors.primaryAccent.copy(alpha = if (enabled) p * 0.09f else 0f))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colors.primaryAccent.copy(alpha = if (enabled) p * 0.055f else 0f),
                            Color.Transparent,
                            colors.primaryAccent.copy(alpha = if (enabled) p * 0.040f else 0f)
                        )
                    )
                )
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            colors.primaryAccent.copy(alpha = if (enabled) p * 0.045f else 0f),
                            Color.Transparent,
                            colors.primaryAccent.copy(alpha = if (enabled) p * 0.035f else 0f)
                        )
                    )
                )
                .then(
                    if (enabled) Modifier.pointerInput(enabled, onClick, hapticStyle) {
                        detectTapGestures(
                            onPress = {
                                val hapticStartedAt = if (hapticStyle == "renderer") {
                                    GamaHaptics.rendererPressStart(context, view)
                                } else {
                                    GamaHaptics.pressStart(context, view)
                                }
                                isPressed = true
                                val released = tryAwaitRelease()
                                isPressed = false
                                GamaHaptics.releaseAfterPress(context, view, hapticStartedAt, released)
                                if (released) onClick()
                            }
                        )
                    } else Modifier
                ),
            contentAlignment = contentAlignment
        ) {
            content(p)
        }
    }
}

@Composable
fun IllustratedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    enabled: Boolean = true,
    colors: ThemeColors,
    oledMode: Boolean = false,
    iconType: String   // "vulkan" | "opengl" | "resources" | "gpuwatch"
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val isSmallScreen = configuration.screenWidthDp.dp < 360.dp
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val screenMinDp = minOf(configuration.screenWidthDp, configuration.screenHeightDp)
    val ts = LocalTypeScale.current

    val isUtilityButton = iconType == "resources" || iconType == "gpuwatch"
    val baseHeight = when {
        isUtilityButton && isSmallScreen -> 54.dp
        isUtilityButton && isLandscape   -> (screenMinDp * 0.115f).dp.coerceIn(50.dp, 58.dp)
        isUtilityButton                  -> (screenMinDp * 0.135f).dp.coerceIn(58.dp, 66.dp)
        isSmallScreen                    -> 46.dp
        isLandscape                      -> (screenMinDp * 0.125f).dp.coerceIn(44.dp, 54.dp)
        else                             -> (screenMinDp * 0.145f).dp.coerceIn(54.dp, 68.dp)
    }
    val cornerRadius = (baseHeight.value * 0.48f).dp.coerceIn(22.dp, 32.dp)
    val shape = RoundedCornerShape(cornerRadius)

    val iconDescription = when (iconType) {
        "vulkan"    -> LocalStrings.current["common.icon_vulkan"].ifEmpty { "Vulkan lightning bolt icon" }
        "opengl"    -> LocalStrings.current["common.icon_opengl"].ifEmpty { "OpenGL hexagon icon" }
        "resources" -> LocalStrings.current["common.icon_resources"].ifEmpty { "Resources list icon" }
        "gpuwatch"  -> LocalStrings.current["common.icon_gpuwatch"].ifEmpty { "GPU activity waveform icon" }
        else        -> LocalStrings.current["common.icon_button"].ifEmpty { "Button icon" }
    }
    val semanticsLabel = if (enabled) {
        LocalStrings.current["common.semantics_button"].ifEmpty { "%s, %s button" }
            .replace("%s", iconDescription).replace("%s", text)
    } else {
        LocalStrings.current["common.semantics_button_disabled"].ifEmpty { "%s, %s button, disabled" }
            .replace("%s", iconDescription).replace("%s", text)
    }

    val isRendererIconButton = iconType == "vulkan" || iconType == "opengl"
    val allowWholeButtonGlow = oledMode
    val allowIconGlow = oledMode || isRendererIconButton
    val glowBlurRadius = (screenMinDp * 0.027f).dp.coerceIn(7.dp, 14.dp)

    val baseButtonColor = when {
        !enabled -> colors.cardBackground.copy(alpha = 0.92f)
        accent && !oledMode -> colors.primaryAccent.copy(alpha = 0.16f)
        else -> colors.cardBackground
    }
    val iconColor = when {
        !enabled -> colors.primaryAccent.copy(alpha = 0.55f)
        iconType == "opengl" -> colors.primaryAccent.copy(alpha = 0.85f)
        else -> colors.primaryAccent
    }
    val textColor = if (accent && !oledMode && colors.primaryAccent.luminance() > 0.5f) Color.Black else colors.textPrimary

    MainMenuPressShell(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isUtilityButton) Modifier.height(baseHeight) else Modifier.heightIn(min = baseHeight)),
        enabled = enabled,
        colors = colors,
        cardBackground = baseButtonColor,
        shape = shape,
        oledMode = oledMode,
        selected = accent,
        semanticsLabel = semanticsLabel,
        onClick = onClick
    ) { press ->
        if (enabled && allowWholeButtonGlow && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .blur(glowBlurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .border(1.dp, colors.primaryAccent.copy(alpha = 0.62f), shape)
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(colors.primaryAccent.copy(alpha = if (enabled) press * 0.10f else 0f))
                .background(
                    Brush.radialGradient(
                        0.0f to colors.primaryAccent.copy(alpha = if (enabled && allowWholeButtonGlow) 0.12f else 0.0f),
                        0.58f to colors.primaryAccent.copy(alpha = if (enabled && allowWholeButtonGlow) 0.040f else 0.0f),
                        1.0f to Color.Transparent,
                        radius = with(density) { 150.dp.toPx() },
                        center = Offset.Unspecified
                    )
                )
                .background(
                    Brush.verticalGradient(
                        0.0f to colors.primaryAccent.copy(alpha = if (enabled && allowWholeButtonGlow) 0.030f else 0.0f),
                        0.55f to Color.Transparent,
                        1.0f to colors.primaryAccent.copy(alpha = if (enabled && allowWholeButtonGlow) 0.065f else 0.0f)
                    )
                )
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp)
        ) {
            val iconSizeDp = (baseHeight.value * 0.43f).dp.coerceIn(18.dp, 30.dp)
            val spacerWidth = (baseHeight.value * 0.13f).dp.coerceIn(6.dp, 12.dp)
            Canvas(modifier = Modifier.size(iconSizeDp)) {
                val w = size.width
                val h = size.height
                val r = w * 0.72f
                if (allowIconGlow) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.0f to iconColor.copy(alpha = 0.28f),
                            0.5f to iconColor.copy(alpha = 0.10f),
                            1.0f to Color.Transparent,
                            radius = r * 1.45f
                        ),
                        radius = r * 1.45f
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.0f to iconColor.copy(alpha = 0.55f),
                            0.6f to iconColor.copy(alpha = 0.15f),
                            1.0f to Color.Transparent,
                            radius = r * 0.80f
                        ),
                        radius = r * 0.80f
                    )
                }
                drawIconShapeInline(iconType, w, h, iconColor)
            }
            Spacer(modifier = Modifier.width(spacerWidth))
            Text(
                text = text,
                fontSize = ts.buttonLarge,
                fontWeight = FontWeight.Bold,
                color = textColor,
                fontFamily = quicksandFontFamily,
                maxLines = if (isUtilityButton) 1 else 3,
                overflow = if (isUtilityButton) TextOverflow.Ellipsis else TextOverflow.Clip,
                textAlign = if (isUtilityButton) TextAlign.Center else TextAlign.Start,
                modifier = if (isUtilityButton) Modifier else Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun BigRendererButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    forceHighlight: Boolean = false,
    enabled: Boolean = true,
    colors: ThemeColors,
    oledMode: Boolean = false,
    iconType: String
) {
    val density = LocalDensity.current
    val ts = LocalTypeScale.current
    val configuration = LocalConfiguration.current
    val isSmallScreen = configuration.screenWidthDp.dp < 360.dp
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val screenMinDp = minOf(configuration.screenWidthDp, configuration.screenHeightDp)
    val buttonHeight = when {
        isSmallScreen -> 88.dp
        // Keep the renderer row compact enough to leave a real touch target for
        // Resources below it on short landscape windows / large display zoom.
        isLandscape   -> (screenMinDp * 0.20f).dp.coerceIn(56.dp, 78.dp)
        else          -> (screenMinDp * 0.30f).dp.coerceIn(90.dp, 130.dp)
    }

    val highlighted = isSelected || forceHighlight
    val bgColor = colors.cardBackground
    val iconColor = when {
        !enabled -> colors.primaryAccent.copy(alpha = 0.55f)
        iconType == "opengl" && !highlighted -> colors.primaryAccent.copy(alpha = 0.65f)
        else -> colors.primaryAccent
    }
    val textColor = colors.textPrimary

    MainMenuPressShell(
        modifier = modifier
            .fillMaxWidth()
            .height(buttonHeight),
        enabled = enabled,
        colors = colors,
        cardBackground = bgColor,
        shape = RoundedCornerShape(28.dp),
        oledMode = oledMode,
        selected = highlighted,
        hapticStyle = "renderer",
        onClick = onClick
    ) { press ->
        BoxWithConstraints(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
            val buttonSize = minOf(maxWidth, maxHeight)
            val shape = RoundedCornerShape((buttonSize.value * 0.18f).dp.coerceIn(16.dp, 32.dp))
            val iconSizeDp = (buttonSize.value * 0.34f).dp.coerceIn(36.dp, 72.dp)
            val spacerDp = (buttonSize.value * 0.065f).dp.coerceIn(6.dp, 14.dp)
            val allowWholeButtonGlow = oledMode
            val allowIconGlow = oledMode || iconType == "vulkan" || iconType == "opengl"

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(colors.primaryAccent.copy(alpha = if (enabled) press * 0.10f else 0f))
                    .background(
                        Brush.radialGradient(
                            0.0f to colors.primaryAccent.copy(alpha = if (enabled && allowWholeButtonGlow) 0.13f else 0.0f),
                            0.58f to colors.primaryAccent.copy(alpha = if (enabled && allowWholeButtonGlow) 0.045f else 0.0f),
                            1.0f to Color.Transparent,
                            radius = with(density) { 210.dp.toPx() },
                            center = Offset.Unspecified
                        )
                    )
                    .background(
                        Brush.verticalGradient(
                            0.0f to colors.primaryAccent.copy(alpha = if (enabled && allowWholeButtonGlow) 0.035f else 0.0f),
                            0.55f to Color.Transparent,
                            1.0f to colors.primaryAccent.copy(alpha = if (enabled && allowWholeButtonGlow) 0.075f else 0.0f)
                        )
                    )
            )

            val rendererIcon: @Composable () -> Unit = {
                Canvas(modifier = Modifier.size(iconSizeDp)) {
                    val r = size.minDimension * 0.52f
                    if (allowIconGlow) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                0.0f to iconColor.copy(alpha = 0.30f),
                                0.55f to iconColor.copy(alpha = 0.10f),
                                1.0f to Color.Transparent,
                                radius = r * 1.60f
                            ),
                            radius = r * 1.60f
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                0.0f to iconColor.copy(alpha = 0.60f),
                                0.50f to iconColor.copy(alpha = 0.18f),
                                1.0f to Color.Transparent,
                                radius = r * 0.90f
                            ),
                            radius = r * 0.90f
                        )
                    }
                    drawIconShape(iconType, size, iconColor)
                }
            }
            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = (buttonSize.value * 0.10f).dp.coerceIn(8.dp, 16.dp)),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rendererIcon()
                    Spacer(modifier = Modifier.width(spacerDp))
                    Text(
                        text = text,
                        color = textColor,
                        fontSize = ts.buttonLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = quicksandFontFamily,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = (buttonSize.value * 0.05f).dp.coerceIn(4.dp, 12.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    rendererIcon()
                    Spacer(modifier = Modifier.height(spacerDp))
                Text(
                    text = text,
                    color = textColor,
                    fontSize = ts.buttonLarge,
                    fontWeight = FontWeight.Bold,
                    fontFamily = quicksandFontFamily,
                    maxLines = 3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                }
            }
        }
    }
}

@Composable
fun DialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: ThemeColors,
    cardBackground: Color,
    accent: Boolean = false,
    oledMode: Boolean = false,
    borderAlphaOverride: Float? = null // when set, rest-state border alpha matches the surrounding card outline
) {
    val density = LocalDensity.current
    val ts = LocalTypeScale.current
    val animLevel = LocalAnimationLevel.current
    var isPressed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val view = LocalView.current
    val isSmallScreen = LocalConfiguration.current.screenWidthDp.dp < 360.dp
    val shape = RoundedCornerShape(28.dp)

    // ── Single Animatable<Float> [0=rest, 1=pressed] drives ALL press visuals ─
    // Replaces 4 separate animateFloatAsState/animateDpAsState calls that all
    // fired simultaneously on every press/release — same optimisation as FlatButton.
    val pressProgress = remember { Animatable(0f) }
    LaunchedEffect(isPressed) {
        pressProgress.animateTo(
            targetValue   = if (isPressed) 1f else 0f,
            animationSpec = when (animLevel) {
                2 -> snap()
                1 -> tween(durationMillis = 120, easing = MotionTokens.Easing.emphasized)
                else -> spring(
                    dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
                    stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
                )
            }
        )
    }
    val pp = pressProgress.value
    // Derive all press visuals from pp — evaluated in draw phase via graphicsLayer
    val pressScale       = 1f - pp * (1f - MotionTokens.Scale.subtle)
    val borderWidthDp    = (if (oledMode) 0.75f else 1f) + pp * (if (oledMode) 0.75f else 1f)  // 1dp → 2dp
    val borderAlphaVal   = 0.5f + pp * 0.5f

    val containerColor = if (accent) colors.primaryAccent else cardBackground
    val borderColor = when {
        isPressed -> colors.primaryAccent
        else      -> colors.primaryAccent.copy(alpha = borderAlphaOverride ?: 0.55f)
    }

    // Outer Box: takes the caller's modifier (weight/fill) — never scaled, so layout is stable
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (oledMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .blur(12.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .background(Brush.radialGradient(listOf(
                        colors.primaryAccent.copy(alpha = 0.16f),
                        Color.Transparent
                    )))
            )
        }
        // Inner Box: visually scaled on press, but layout is already committed by outer Box,
        // so background, border, clip and highlight are always perfectly coincident
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(scaleX = pressScale, scaleY = pressScale)
                .clip(shape)
                .background(containerColor)
                .background(colors.primaryAccent.copy(alpha = pp * 0.10f))
                .pressedAccentOutlineGlow(
                    pressProgress = pp,
                    color = colors.primaryAccent,
                    cornerRadius = 28.dp,
                    strokeWidth = borderWidthDp.dp,
                    glowRadius = 10.dp
                )
                .border(width = borderWidthDp.dp, color = borderColor, shape = shape)
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
                }
                .padding(horizontal = 16.dp, vertical = if (isSmallScreen) 12.dp else 14.dp)
        ) {
            Text(
                text,
                color = if (accent) {
                    if (colors.primaryAccent.luminance() > 0.6f) Color.Black else Color.White
                } else {
                    colors.textPrimary
                },
                fontSize = ts.bodyLarge,
                fontWeight = FontWeight.Bold,
                fontFamily = quicksandFontFamily,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
fun SecondaryIconButton(
    label: String,
    onClick: () -> Unit,
    colors: ThemeColors,
    oledMode: Boolean,
    modifier: Modifier = Modifier,
    icon: @Composable (Dp) -> Unit
) {
    val ts       = LocalTypeScale.current
    val animLevel = LocalAnimationLevel.current
    val animSpeed = LocalAnimationSpeed.current
    val density  = LocalDensity.current
    var isPressed by remember { mutableStateOf(false) }

    // ── Single Animatable<Float> replaces 3 separate animators ───────────────
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
    val pressScale    = 1f - pp * (1f - MotionTokens.Scale.subtle)
    val borderAlphaVal = 0.45f + pp * 0.55f
    val borderWidthDp  = (if (oledMode) 0.75f else 1f) + pp * (if (oledMode) 0.75f else 1f)
    val borderColor    = colors.primaryAccent.copy(alpha = borderAlphaVal)

    val bgColor = colors.cardBackground
    val iconSizeDp = 18.dp

    Box(
        modifier = modifier
            .graphicsLayer(scaleX = pressScale, scaleY = pressScale)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .background(colors.primaryAccent.copy(alpha = pp * 0.10f))
            .pressedAccentOutlineGlow(
                pressProgress = pp,
                color = colors.primaryAccent,
                cornerRadius = 14.dp,
                strokeWidth = borderWidthDp.dp,
                glowRadius = 8.dp
            )
            .border(borderWidthDp.dp, borderColor, RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() }
                )
            }
            .padding(vertical = 12.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            icon(iconSizeDp)
            Text(
                text = label,
                fontSize = ts.labelMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = quicksandFontFamily,
                color = colors.textPrimary,
                maxLines = 2
            )
        }
    }
}

