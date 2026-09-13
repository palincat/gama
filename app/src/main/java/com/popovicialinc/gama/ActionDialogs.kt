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
fun SuccessDialog(
    visible: Boolean,
    message: String,
    userName: String,
    isSwitching: Boolean = false,
    onDismiss: () -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color
) {
    val ts        = LocalTypeScale.current
    val animLevel = LocalAnimationLevel.current
    val animSpeed = LocalAnimationSpeed.current

    // ── Spinner — Animatable loop that only ticks while isSwitching is true ────
    // The previous infiniteRepeatable kept ticking even after isSwitching became
    // false (it just froze at targetValue=0), wasting animator budget on idle.
    // This Animatable loops in a coroutine that exits the moment isSwitching ends.
    val spinnerAngle = remember { Animatable(0f) }
    LaunchedEffect(isSwitching) {
        if (isSwitching) {
            // Loop until isSwitching goes false (LaunchedEffect cancels this coroutine)
            while (true) {
                spinnerAngle.snapTo(0f)
                spinnerAngle.animateTo(
                    targetValue   = 360f,
                    animationSpec = tween(durationMillis = 900, easing = LinearEasing)
                )
            }
        } else {
            spinnerAngle.snapTo(0f)  // reset so checkmark starts clean
        }
    }

    // ── Checkmark draw progress (0 → 1 once switching finishes) ──────────────
    // Driven by animateFloatAsState so it eases in smoothly after the spinner stops.
    val checkmarkProgress by animateFloatAsState(
        targetValue   = if (isSwitching) 0f else 1f,
        animationSpec = if (animLevel == 2) snap()
        else tween(durationMillis = MotionTokens.SpeedUtil.durationMs(420, animSpeed), easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)),
        label         = "checkmark_draw"
    )

    BouncyDialog(
        visible   = visible,
        // Block back-tap while the command is running — there is nothing to dismiss yet
        onDismiss = { if (!isSwitching) onDismiss() }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape && !isTablet) 0.6f else 0.9f)
                .widthIn(max = 500.dp)
                .directionalShadow(cornerRadius = 28.dp)
                .border(
                    width = 1.dp,
                    color = colors.primaryAccent.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(28.dp)
                )
                .pointerInput(Unit) {
                    detectTapGestures { /* Block taps on card from dismissing */ }
                },
            colors    = CardDefaults.cardColors(containerColor = cardBackground),
            shape     = RoundedCornerShape(40.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .padding(if (isSmallScreen) 22.dp else 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 20.dp else 24.dp)
            ) {
                // ── Icon: spinner arc while running, drawing checkmark on completion ──
                Canvas(modifier = Modifier.size(if (isSmallScreen) 56.dp else 64.dp)) {
                    val iconColor  = colors.primaryAccent
                    val strokePx   = 3.dp.toPx()
                    val strokeWide = 4.dp.toPx()
                    val radius     = size.minDimension / 2f

                    if (isSwitching) {
                        // Faint background track
                        drawCircle(
                            color  = iconColor.copy(alpha = 0.15f),
                            radius = radius,
                            style  = Stroke(width = strokePx)
                        )
                        // 270° spinning arc
                        drawArc(
                            color      = iconColor.copy(alpha = 0.85f),
                            startAngle = spinnerAngle.value - 90f,
                            sweepAngle = 270f,
                            useCenter  = false,
                            style      = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )
                    } else {
                        // Full circle fades in with the checkmark draw progress
                        drawCircle(
                            color  = iconColor.copy(alpha = checkmarkProgress),
                            radius = radius,
                            style  = Stroke(width = strokePx)
                        )

                        // Animated path: draw tick stroke proportionally to checkmarkProgress.
                        // Two segments: p1→p2 (downstroke), p2→p3 (upstroke).
                        val p1 = Offset(size.width * 0.25f, size.height * 0.50f)
                        val p2 = Offset(size.width * 0.45f, size.height * 0.70f)
                        val p3 = Offset(size.width * 0.75f, size.height * 0.35f)

                        val seg1 = kotlin.math.sqrt(
                            ((p2.x - p1.x).toDouble().let { it * it } + (p2.y - p1.y).toDouble().let { it * it })
                        ).toFloat()
                        val seg2 = kotlin.math.sqrt(
                            ((p3.x - p2.x).toDouble().let { it * it } + (p3.y - p2.y).toDouble().let { it * it })
                        ).toFloat()
                        val total  = seg1 + seg2
                        val drawn  = total * checkmarkProgress

                        if (drawn > 0f) {
                            val path = Path()
                            if (drawn <= seg1) {
                                val t = drawn / seg1
                                path.moveTo(p1.x, p1.y)
                                path.lineTo(p1.x + (p2.x - p1.x) * t, p1.y + (p2.y - p1.y) * t)
                            } else {
                                val t = (drawn - seg1) / seg2
                                path.moveTo(p1.x, p1.y)
                                path.lineTo(p2.x, p2.y)
                                path.lineTo(p2.x + (p3.x - p2.x) * t, p2.y + (p3.y - p2.y) * t)
                            }
                            drawPath(
                                path  = path,
                                color = iconColor,
                                style = Stroke(width = strokeWide, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }
                }

                // ── Status text — crossfades between "Applying changes…" and final message ──
                AnimatedContent(
                    targetState  = isSwitching,
                    transitionSpec = { fadeIn(tween(MotionTokens.SpeedUtil.durationMs(250, animSpeed))) togetherWith fadeOut(tween(MotionTokens.SpeedUtil.durationMs(150, animSpeed))) },
                    label        = "success_msg"
                ) { switching ->
                    Text(
                        text        = if (switching) LocalStrings.current["main.applying_changes"].ifEmpty { "Applying changes…" } else message,
                        fontSize    = ts.bodyLarge,
                        lineHeight  = (ts.bodyLarge.value * 1.4f).sp,
                        color       = colors.textPrimary.copy(alpha = if (switching) 0.55f else 0.9f),
                        modifier    = Modifier.fillMaxWidth(),
                        fontFamily  = quicksandFontFamily,
                        textAlign   = TextAlign.Center,
                        fontWeight  = FontWeight.Bold
                    )
                }

                // ── OK button — only appears once the switch is confirmed ─────────────────
                AnimatedVisibility(
                    visible = !isSwitching,
                    enter   = fadeIn(tween(MotionTokens.SpeedUtil.durationMs(280, animSpeed))) + expandVertically(tween(MotionTokens.SpeedUtil.durationMs(280, animSpeed), easing = FastOutSlowInEasing)),
                    exit    = fadeOut(tween(MotionTokens.SpeedUtil.durationMs(150, animSpeed)))
                ) {
                    DialogButton(
                        text           = if (userName.isNotEmpty()) LocalStrings.current["dialogs.btn_okay_named"].replace("%s", userName).ifEmpty { "Okay, $userName!" } else LocalStrings.current["dialogs.btn_okay"].ifEmpty { "OK" },
                        onClick        = onDismiss,
                        modifier       = Modifier.fillMaxWidth(),
                        colors         = colors,
                        cardBackground = cardBackground,
                        accent         = true
                    )
                }
            }
        }
    }
}

@Composable
fun DeveloperMenuDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onTestNotification: () -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color
) {
    val ts = LocalTypeScale.current
    BouncyDialog(
        visible = visible,
        onDismiss = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape && !isTablet) 0.6f else 0.9f)
                .widthIn(max = 500.dp)
                .directionalShadow(cornerRadius = 28.dp)
                .border(
                    width = 1.dp,
                    color = colors.primaryAccent.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(28.dp)
                )
                .pointerInput(Unit) {
                    detectTapGestures { /* Block taps on card from dismissing dialog */ }
                },
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isSmallScreen) 22.dp else 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 20.dp else 24.dp)
            ) {
                Text(
                    text = LocalStrings.current["dialogs.developer_title"].ifEmpty { "Developer Mode" },
                    fontSize = ts.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    fontFamily = quicksandFontFamily,
                    color = colors.textPrimary
                )

                Text(
                    text = LocalStrings.current["dialogs.developer_subtitle"].ifEmpty { "Developer tools for testing and debugging." },
                    fontSize = ts.bodyMedium,
                    color = colors.textSecondary,
                    fontFamily = quicksandFontFamily,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold // Force Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                FlatButton(
                    text = LocalStrings.current["notifications.send_test"].ifEmpty { "Send Test Notification" },
                    onClick = onTestNotification,
                    modifier = Modifier.fillMaxWidth(),
                    accent = false,
                    enabled = true,
                    colors = colors,
                    maxLines = 1
                )

                DialogButton(
                    text = LocalStrings.current["dialogs.btn_close"].ifEmpty { "Close" },
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

@Composable
fun GitHubDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onVisitGitHub: () -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color
) {
    val ts = LocalTypeScale.current
    BouncyDialog(
        visible = visible,
        onDismiss = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape && !isTablet) 0.6f else 0.9f)
                .widthIn(max = 500.dp)
                .directionalShadow(cornerRadius = 28.dp)
                .border(
                    width = 1.dp,
                    color = colors.primaryAccent.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(28.dp)
                )
                .pointerInput(Unit) {
                    detectTapGestures { /* Block taps on card from dismissing dialog */ }
                },
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isSmallScreen) 22.dp else 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 20.dp else 24.dp)
            ) {
                // MODIFIED: Minimalist Circle Icon with Arrow
                Canvas(modifier = Modifier.size(if (isSmallScreen) 56.dp else 64.dp)) {
                    val sizePx = size.minDimension
                    val stroke = 3.dp.toPx()
                    val color = colors.primaryAccent

                    // 1. Minimal circle
                    drawCircle(
                        color = color,
                        radius = sizePx / 2,
                        style = Stroke(width = stroke)
                    )

                    // 2. Simple arrow pointing top-right (External Link style)
                    val arrowPath = Path().apply {
                        // Start bottom-left-ish
                        moveTo(sizePx * 0.35f, sizePx * 0.65f)
                        // Line to top-right
                        lineTo(sizePx * 0.65f, sizePx * 0.35f)
                    }

                    // Arrowhead
                    val arrowHeadPath = Path().apply {
                        moveTo(sizePx * 0.45f, sizePx * 0.35f) // left of top-right
                        lineTo(sizePx * 0.65f, sizePx * 0.35f) // point
                        lineTo(sizePx * 0.65f, sizePx * 0.55f) // down from top-right
                    }

                    drawPath(
                        path = arrowPath,
                        color = color,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )

                    drawPath(
                        path = arrowHeadPath,
                        color = color,
                        style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = LocalStrings.current["dialogs.github_title"].ifEmpty { "Would you like to visit the GAMA repository on GitHub?" },
                        fontSize = ts.bodyLarge,
                        lineHeight = (ts.bodyLarge.value * 1.4f).sp,
                        color = colors.textPrimary.copy(alpha = 0.9f),
                        modifier = Modifier.fillMaxWidth(),
                        fontFamily = quicksandFontFamily,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold // Force Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DialogButton(
                        text = LocalStrings.current["dialogs.btn_cancel"].ifEmpty { "Cancel" },
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = colors,
                        cardBackground = cardBackground
                    )
                    DialogButton(
                        text = LocalStrings.current["dialogs.btn_sure"].ifEmpty { "Sure!" },
                        onClick = onVisitGitHub,
                        modifier = Modifier.weight(1f),
                        colors = colors,
                        cardBackground = cardBackground,
                        accent = true
                    )
                }
            }
        }
    }
}

@Composable
fun ExternalLinkConfirmDialog(
    visible: Boolean,
    label: String,
    description: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color
) {
    val ts = LocalTypeScale.current
    BouncyDialog(
        visible = visible,
        onDismiss = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape && !isTablet) 0.6f else 0.9f)
                .widthIn(max = 500.dp)
                .directionalShadow(cornerRadius = 28.dp)
                .border(1.dp, colors.primaryAccent.copy(alpha = 0.55f), RoundedCornerShape(28.dp))
                .pointerInput(Unit) { detectTapGestures { } },
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isSmallScreen) 22.dp else 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 20.dp else 24.dp)
            ) {
                // External link / arrow-out-of-box icon
                Canvas(modifier = Modifier.size(if (isSmallScreen) 56.dp else 64.dp)) {
                    val s = size.minDimension
                    val stroke = 3.dp.toPx()
                    val color = colors.primaryAccent
                    // Circle
                    drawCircle(color = color, radius = s / 2f, style = Stroke(width = stroke))
                    // Arrow shaft: bottom-left → top-right
                    drawLine(
                        color = color,
                        start = androidx.compose.ui.geometry.Offset(s * 0.33f, s * 0.67f),
                        end   = androidx.compose.ui.geometry.Offset(s * 0.67f, s * 0.33f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                    // Arrowhead
                    val headPath = Path().apply {
                        moveTo(s * 0.46f, s * 0.33f)
                        lineTo(s * 0.67f, s * 0.33f)
                        lineTo(s * 0.67f, s * 0.54f)
                    }
                    drawPath(headPath, color = color,
                        style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }

                // Title — matches ShizukuHelpDialog style exactly
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = LocalStrings.current["dialogs.external_link_title"].ifEmpty { "You're leaving GAMA" },
                        fontSize = ts.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = quicksandFontFamily,
                        color = colors.primaryAccent
                    )
                }

                Text(
                    text = description,
                    fontSize = ts.bodyLarge,
                    lineHeight = (ts.bodyLarge.value * 1.4f).sp,
                    color = colors.textPrimary.copy(alpha = 0.85f),
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
                        text = LocalStrings.current["dialogs.btn_close"].ifEmpty { "Close" },
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = colors,
                        cardBackground = cardBackground
                    )
                    DialogButton(
                        text = LocalStrings.current["dialogs.btn_open"].ifEmpty { "Open" },
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        colors = colors,
                        cardBackground = cardBackground,
                        accent = true
                    )
                }
            }
        }
    }
}

