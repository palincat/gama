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
fun GlideOptionSelector(
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit,
    colors: ThemeColors,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    rescaleWhenDisabled: Boolean = false
) {
    val density = LocalDensity.current
    val ts = LocalTypeScale.current
    val animLevel = LocalAnimationLevel.current
    val animSpeed = LocalAnimationSpeed.current
    val viewConfiguration = LocalViewConfiguration.current
    val selectorCornerRadius = 28.dp
    val indicatorInset = 4.dp
    // An inset rounded shape must reduce its radius by the same inset to keep
    // the arcs concentric. Keeping both at 28.dp made the selected pill bulge
    // toward the selector/card edge at the corners.
    val indicatorCornerRadius = selectorCornerRadius - indicatorInset

    val currentOnOptionSelected by rememberUpdatedState(onOptionSelected)
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)

    val scale by animateFloatAsState(
        targetValue = if (enabled || !rescaleWhenDisabled) 1f else 0.80f,
        animationSpec = if (animLevel == 2) snap() else if (animLevel == 1) tween(MotionTokens.SpeedUtil.durationMs(260, animSpeed), easing = MotionTokens.Easing.emphasized) else spring(
            dampingRatio = MotionTokens.Springs.gentle.dampingRatio,
            stiffness = MotionTokens.SpeedUtil.stiffness(MotionTokens.Springs.gentle.stiffness, animSpeed)
        ),
        label = "glide_selector_scale"
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (enabled || !rescaleWhenDisabled) 1f else 0.50f,
        animationSpec = tween(durationMillis = MotionTokens.SpeedUtil.durationMs(340, animSpeed), easing = MotionTokens.Easing.velvet),
        label = "glide_selector_alpha"
    )

    BoxWithConstraints(
        modifier = modifier
            .height(60.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(selectorCornerRadius))
            .background(colors.primaryAccent.copy(alpha = if (enabled) 0.07f else 0.06f))
    ) {
        val maxWidth = maxWidth
        val itemWidth = maxWidth / options.size

        val itemWidthPxForIndicator = with(density) { itemWidth.toPx() }
        val selectedOffsetPx = itemWidthPxForIndicator * selectedIndex.coerceIn(0, options.lastIndex)

        // Anchored indicator: it does not follow the finger anymore.
        // Touching/dragging over another option updates selection, then the accent pill
        // glides to that selected stop with a small spring overshoot.
        val animatedIndicatorPx by animateFloatAsState(
            targetValue = selectedOffsetPx,
            animationSpec = if (animLevel == 2) {
                snap()
            } else {
                if (animLevel == 1) {
                    tween(durationMillis = MotionTokens.SpeedUtil.durationMs(430, animSpeed), easing = MotionTokens.Easing.velvet)
                } else {
                    spring(
                        dampingRatio = 0.78f,
                        stiffness = MotionTokens.SpeedUtil.stiffness(220f, animSpeed)
                    )
                }
            },
            label = "indicator"
        )
        val indicatorOffset = with(density) { animatedIndicatorPx.toDp() }

        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(itemWidth)
                .fillMaxHeight()
                .padding(indicatorInset)
                .clip(RoundedCornerShape(indicatorCornerRadius))
                .background(colors.primaryAccent.copy(alpha = contentAlpha))
        )

        Row(modifier = Modifier.fillMaxSize().graphicsLayer(alpha = contentAlpha)) {
            options.forEachIndexed { index, text ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    val isSelected = index == selectedIndex
                    val selectedTextColor = if (colors.primaryAccent.luminance() > 0.6f) Color.Black else Color.White
                    val targetColor = if (isSelected) selectedTextColor else colors.textSecondary
                    val textColor by animateColorAsState(
                        targetValue = targetColor,
                        animationSpec = if (animLevel == 2) snap() else tween(
                            durationMillis = MotionTokens.SpeedUtil.durationMs(260, animSpeed),
                            easing = MotionTokens.Easing.emphasized
                        ),
                        label = "glide_option_color_$index"
                    )

                    Text(
                        text = text,
                        color = textColor,
                        fontSize = ts.labelLarge,
                        fontWeight = FontWeight.Bold, // Force Bold
                        fontFamily = quicksandFontFamily
                    )
                }
            }
        }

        // ── Unified gesture overlay ───────────────────────────────────────────
        // Previously: two separate pointerInput blocks (one for tap, one for drag)
        // caused gesture conflicts — detectDragGestures consumed onDragStart even
        // for finger movements that were really taps, so taps often silently fired
        // onDragStart and then the drag never moved far enough to trigger onDrag,
        // leaving the selection unchanged.
        //
        // Fix: one awaitPointerEventScope loop that decides tap-vs-drag from the
        // first movement after touch-down, so neither detector can steal from the other.
        if (enabled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(enabled, options.size) {
                        val itemWidthPx = size.width.toFloat() / options.size
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var lastIndex = (down.position.x / itemWidthPx)
                                .toInt().coerceIn(0, options.size - 1)
                            var isDrag = false

                            if (lastIndex != currentSelectedIndex) {
                                currentOnOptionSelected(lastIndex)
                            }

                            do {
                                val event = awaitPointerEvent()
                                val ptr = event.changes.firstOrNull() ?: break
                                if (!ptr.pressed) break // finger lifted

                                val newIndex = (ptr.position.x / itemWidthPx)
                                    .toInt().coerceIn(0, options.size - 1)

                                if (!isDrag) {
                                    val dx = kotlin.math.abs(ptr.position.x - down.position.x)
                                    if (dx > viewConfiguration.touchSlop) isDrag = true
                                }

                                if (newIndex != lastIndex) {
                                    currentOnOptionSelected(newIndex)
                                    lastIndex = newIndex
                                    ptr.consume()
                                }
                            } while (true)
                        }
                    }
            )
        } else {
            // Disabled: swallow taps so they don't leak through to parent
            Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures { }
            })
        }
    }
}

@Composable
fun CompactColorPickerCard(
    title: String,
    description: String,
    currentColor: Color,
    onColorChange: (Color) -> Unit,
    colors: ThemeColors,
    cardBackground: Color,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    advancedPicker: Boolean = false,
    enabled: Boolean = true,
    oledMode: Boolean = false,
    filterExtremeForTheme: Boolean = false,
    isDarkTheme: Boolean = false,
    modifier: Modifier = Modifier
) {
    val ts = LocalTypeScale.current
    val landscapeGrid = LocalLandscapePanelGrid.current
    val animLevel = LocalAnimationLevel.current
    val animSpeed = LocalAnimationSpeed.current

    val allPresetColors = listOf(
        Color(0xFFEF4444), Color(0xFFF97316), Color(0xFFF59E0B),
        Color(0xFF22C55E), Color(0xFF14B8A6), Color(0xFF3B82F6),
        Color(0xFF8B5CF6), Color(0xFFEC4899),
        Color.White, Color.Black
    )
    val presetColors = remember(filterExtremeForTheme, isDarkTheme) {
        if (!filterExtremeForTheme) allPresetColors
        else allPresetColors.filterNot {
            (isDarkTheme && it.toArgb() == Color.Black.toArgb()) ||
            (!isDarkTheme && it.toArgb() == Color.White.toArgb())
        }
    }

    fun Color.toHexString(): String {
        val r = (red * 255).toInt()
        val g = (green * 255).toInt()
        val b = (blue * 255).toInt()
        return "#%02X%02X%02X".format(r, g, b)
    }

    var hexInput by remember(currentColor) { mutableStateOf(currentColor.toHexString()) }
    var hexError by remember { mutableStateOf(false) }

    val cardShape = RoundedCornerShape(28.dp)
    val cardScale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.90f,
        animationSpec = if (animLevel == 2) snap() else tween(MotionTokens.SpeedUtil.durationMs(260, animSpeed), easing = MotionTokens.Easing.emphasized),
        label = "accent_card_scale"
    )
    val cardAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(MotionTokens.SpeedUtil.durationMs(260, animSpeed), easing = MotionTokens.Easing.emphasized),
        label = "accent_card_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth(if (landscapeGrid) 0.48f else 1f)
            .graphicsLayer(scaleX = cardScale, scaleY = cardScale, alpha = cardAlpha)
            .clip(cardShape)
            .background(cardBackground)
            .border(1.dp, colors.primaryAccent.copy(alpha = if (enabled) 0.50f else 0.24f), cardShape)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = tween(durationMillis = MotionTokens.SpeedUtil.durationMs(560, animSpeed), easing = MotionTokens.Easing.velvet)
                )
                .then(if (!enabled) Modifier.pointerInput(enabled) { detectTapGestures { } } else Modifier)
                .padding(if (isSmallScreen) 18.dp else 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    color = colors.primaryAccent.copy(alpha = 0.7f),
                    fontSize = ts.labelLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    fontFamily = quicksandFontFamily
                )
                Text(
                    text = description,
                    color = colors.textSecondary,
                    fontSize = ts.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = quicksandFontFamily,
                    lineHeight = (ts.bodyMedium.value * 1.28f).sp
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                presetColors.chunked(if (isLandscape) 5 else 5).forEach { rowColors ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        rowColors.forEachIndexed { index, color ->
                            CompactColorBox(
                                color = color,
                                isSelected = !advancedPicker && color.toArgb() == currentColor.toArgb(),
                                onClick = {
                                    onColorChange(color)
                                    hexInput = color.toHexString()
                                    hexError = false
                                },
                                colors = colors,
                                isLandscape = false
                            )
                            if (index < rowColors.size - 1) {
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = advancedPicker,
                enter = fadeIn(animationSpec = tween(MotionTokens.SpeedUtil.durationMs(260, animSpeed), easing = MotionTokens.Easing.velvet)) +
                    expandVertically(animationSpec = tween(MotionTokens.SpeedUtil.durationMs(360, animSpeed), easing = MotionTokens.Easing.velvet)),
                exit = fadeOut(animationSpec = tween(MotionTokens.SpeedUtil.durationMs(180, animSpeed), easing = MotionTokens.Easing.exit)) +
                    shrinkVertically(animationSpec = tween(MotionTokens.SpeedUtil.durationMs(220, animSpeed), easing = MotionTokens.Easing.exit))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { input ->
                            val clean = input.uppercase().replace("#", "").take(6)
                            hexInput = if (input.startsWith("#")) "#$clean" else "#$clean"
                            if (clean.length == 6) {
                                try {
                                    val parsed = Color(android.graphics.Color.parseColor("#$clean"))
                                    onColorChange(parsed)
                                    hexError = false
                                } catch (_: Exception) {
                                    hexError = true
                                }
                            } else {
                                hexError = clean.isNotEmpty() && clean.length != 6
                            }
                        },
                        label = {
                            Text(
                                text = LocalStrings.current["colors.hex_label"].ifEmpty { "Hex Color" },
                                fontFamily = quicksandFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = ts.labelSmall
                            )
                        },
                        singleLine = true,
                        isError = hexError,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = quicksandFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedBorderColor = colors.primaryAccent,
                            unfocusedBorderColor = colors.border,
                            errorBorderColor = Color(0xFFEF4444),
                            cursorColor = colors.primaryAccent,
                            focusedLabelColor = colors.primaryAccent,
                            unfocusedLabelColor = colors.textSecondary,
                            errorLabelColor = Color(0xFFEF4444)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (hexError) {
                        Text(
                            text = LocalStrings.current["colors.hex_error"].ifEmpty { "Enter a valid 6-digit hex (for example #4895EF)." },
                            fontSize = ts.labelSmall,
                            color = Color(0xFFEF4444),
                            fontFamily = quicksandFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        if (!enabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(cardShape)
                    .background((if (isDarkTheme) Color.Black else Color.White).copy(alpha = 0.50f))
            )
        }
    }
}

@Composable
fun CompactColorBox(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    colors: ThemeColors,
    isLandscape: Boolean
) {
    val size = if (isLandscape) 34.dp else 38.dp
    val animLevel = LocalAnimationLevel.current
    val animSpeed = LocalAnimationSpeed.current
    var isPressed by remember { mutableStateOf(false) }
    val pressProgress = remember { Animatable(0f) }
    LaunchedEffect(isPressed) {
        pressProgress.animateTo(
            targetValue = if (isPressed) 1f else 0f,
            animationSpec = when (animLevel) {
                2 -> snap()
                1 -> tween(durationMillis = MotionTokens.SpeedUtil.durationMs(120, animSpeed), easing = MotionTokens.Easing.emphasized)
                else -> tween(durationMillis = MotionTokens.SpeedUtil.durationMs(180, animSpeed), easing = MotionTokens.Easing.velvet)
            }
        )
    }
    val pp = pressProgress.value
    val scale = if (isSelected) 1.06f - pp * 0.04f else 1f - pp * 0.05f
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(shape)
            .background(color)
            .background(colors.primaryAccent.copy(alpha = pp * 0.10f))
            .pressedAccentOutlineGlow(
                pressProgress = if (isSelected) maxOf(pp, 0.55f) else pp,
                color = colors.primaryAccent,
                cornerRadius = 12.dp,
                strokeWidth = 1.4.dp,
                glowRadius = 6.dp,
                maxAlpha = if (isSelected) 0.42f else 0.26f
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        val released = tryAwaitRelease()
                        isPressed = false
                        if (released) onClick()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Canvas(modifier = Modifier.size(size * 0.45f)) {
                val strokeWidth = 2.dp.toPx()
                val checkColor = if (color.luminance() > 0.5f) Color.Black else Color.White
                drawPath(
                    path = Path().apply {
                        moveTo(this@Canvas.size.width * 0.20f, this@Canvas.size.height * 0.52f)
                        lineTo(this@Canvas.size.width * 0.40f, this@Canvas.size.height * 0.72f)
                        lineTo(this@Canvas.size.width * 0.80f, this@Canvas.size.height * 0.30f)
                    },
                    color = checkColor,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}

