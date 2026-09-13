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
import androidx.compose.ui.draw.drawWithCache
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



internal fun String.withoutGreetingEmoji(): String = this
    .replace("☀️", "")
    .replace("🌙", "")
    .replace("👋", "")
    .replace("✅", "")
    .replace("⚠️", "")
    .replace("❌", "")
    .replace(Regex("\\s+"), " ")
    .trim()

fun Modifier.pressedAccentOutlineGlow(
    pressProgress: Float,
    color: Color,
    cornerRadius: Dp,
    strokeWidth: Dp = 2.dp,
    glowRadius: Dp = 9.dp,
    maxAlpha: Float = 0.70f
): Modifier = this.drawWithCache {
    val strokePx = strokeWidth.toPx().coerceAtLeast(1f)
    val blurPx = glowRadius.toPx().coerceAtLeast(0f)
    val glowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        this.strokeWidth = strokePx
        maskFilter = if (blurPx > 0f) BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL) else null
    }

    onDrawWithContent {
        drawContent()

        val alpha = (pressProgress * maxAlpha).coerceIn(0f, maxAlpha)
        if (alpha <= 0.01f || size.width <= 0f || size.height <= 0f) return@onDrawWithContent

    // Keep the glow centered on the same path as the solid outline.
    // The previous extra inset pushed the bloom inward and left a tiny dead gap.
    val inset = (strokePx / 2f).coerceAtMost(minOf(size.width, size.height) / 3f)
    val radiusPx = (cornerRadius.toPx() - strokePx / 2f).coerceAtLeast(0f)
        .coerceAtMost(minOf(size.width, size.height) / 2f)

    glowPaint.color = color.copy(alpha = alpha).toArgb()

    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawRoundRect(
            android.graphics.RectF(
                inset,
                inset,
                size.width - inset,
                size.height - inset
            ),
            radiusPx,
            radiusPx,
            glowPaint
        )
    }

    // Subtle interior spill that starts right at the outline, with no visual gap.
    val innerAlpha = (pressProgress * 0.060f).coerceIn(0f, 0.060f)
    if (innerAlpha <= 0.002f) return@onDrawWithContent

    val left = inset
    val top = inset
    val right = size.width - inset
    val bottom = size.height - inset
    val innerWidth = (right - left).coerceAtLeast(0f)
    val innerHeight = (bottom - top).coerceAtLeast(0f)
    if (innerWidth <= 0f || innerHeight <= 0f) return@onDrawWithContent

    val edgeHeight = minOf(innerHeight * 0.16f, 14.dp.toPx())
    val edgeWidth = minOf(innerWidth * 0.12f, 12.dp.toPx())
    val innerCorner = (radiusPx - inset).coerceAtLeast(0f)
    val clipShape = Path().apply {
        addRoundRect(
            RoundRect(
                left = left,
                top = top,
                right = right,
                bottom = bottom,
                cornerRadius = CornerRadius(innerCorner, innerCorner)
            )
        )
    }

    clipPath(clipShape) {
        if (edgeHeight > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        color.copy(alpha = innerAlpha),
                        color.copy(alpha = innerAlpha * 0.42f),
                        Color.Transparent
                    ),
                    startY = top,
                    endY = top + edgeHeight
                ),
                topLeft = Offset(left, top),
                size = Size(innerWidth, edgeHeight)
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        color.copy(alpha = innerAlpha * 0.42f),
                        color.copy(alpha = innerAlpha)
                    ),
                    startY = bottom - edgeHeight,
                    endY = bottom
                ),
                topLeft = Offset(left, bottom - edgeHeight),
                size = Size(innerWidth, edgeHeight)
            )
        }

        if (edgeWidth > 0f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        color.copy(alpha = innerAlpha),
                        color.copy(alpha = innerAlpha * 0.42f),
                        Color.Transparent
                    ),
                    startX = left,
                    endX = left + edgeWidth
                ),
                topLeft = Offset(left, top),
                size = Size(edgeWidth, innerHeight)
            )
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        color.copy(alpha = innerAlpha * 0.42f),
                        color.copy(alpha = innerAlpha)
                    ),
                    startX = right - edgeWidth,
                    endX = right
                ),
                topLeft = Offset(right - edgeWidth, top),
                size = Size(edgeWidth, innerHeight)
            )
        }
    }
    }
}

fun Modifier.directionalShadow(
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.18f),
    dx: Dp = 4.dp,
    dy: Dp = 6.dp,
    blurRadius: Dp = 12.dp,
    cornerRadius: Dp = 28.dp,
    deferUntilSettled: Boolean = false
): Modifier = composed {
    val density = LocalDensity.current
    val shadowsEnabled = LocalShadowsEnabled.current
    val cardProgress   = LocalCardProgress.current
    val cardSettled    = LocalCardSettled.current
    val cardEnabled    = LocalCardEnabled.current
    val dxPx           = with(density) { dx.toPx() }
    val dyPx           = with(density) { dy.toPx() }
    val blurPx         = with(density) { blurRadius.toPx() }
    val cornerPx       = with(density) { cornerRadius.toPx() }
    val animSpeed      = LocalAnimationSpeed.current

    // Enabled cards may ease shadows in, but disabled cards lose shadows immediately.
    val enabledAlpha by animateFloatAsState(
        targetValue = if (cardEnabled) 1f else 0f,
        animationSpec = if (cardEnabled) {
            tween(durationMillis = MotionTokens.SpeedUtil.durationMs(180, animSpeed), easing = MotionTokens.Easing.velvet)
        } else {
            snap()
        },
        label = "shadowEnabledAlpha"
    )

    // Animate the shadows-enabled toggle so shadows fade in/out smoothly rather than snapping.
    val shadowsAlpha by animateFloatAsState(
        targetValue = if (shadowsEnabled) 1f else 0f,
        animationSpec = tween(durationMillis = MotionTokens.SpeedUtil.durationMs(600, animSpeed), easing = LinearOutSlowInEasing),
        label = "shadowsToggleAlpha"
    )

    // Search/global result cards cannot draw their shadow while the parent card is
    // scaling, because Android briefly rasterizes that blurred layer as a box.
    // But snapping the shadow on after settle looks cheap. So for deferred cards,
    // keep the shadow fully hidden during movement, then ease it in after landing.
    val settledShadowAlpha by animateFloatAsState(
        targetValue = if (!deferUntilSettled || cardSettled) 1f else 0f,
        animationSpec = if (!deferUntilSettled || cardSettled) {
            tween(durationMillis = MotionTokens.SpeedUtil.durationMs(190, animSpeed), easing = FastOutSlowInEasing)
        } else {
            snap()
        },
        label = "settledShadowAlpha"
    )

    // ── Cached native objects — allocated ONCE, never per frame ──────────────
    // Previously: android.graphics.Paint() + BlurMaskFilter() were constructed
    // inside drawBehind on every draw call (60–120×/sec × number of visible cards).
    // On a screen with 8 AnimatedElements that's ~960 Paint allocations/sec at 60Hz,
    // each triggering a GC pause on old JIT-based devices.
    //
    // Now: one Paint per Modifier instance. BlurMaskFilter is rebuilt only when
    // blurPx changes (never at runtime — blurRadius is a constant parameter).
    val cachedShadowPaint = remember { android.graphics.Paint().apply { isAntiAlias = true } }
    // Track last blur radius so we only rebuild BlurMaskFilter when it actually changes.
    var cachedBlurFraction = remember { -1f }

    // Pre-extract color channels so we avoid per-frame Color field reads inside draw
    val colorR = (color.red   * 255).toInt()
    val colorG = (color.green * 255).toInt()
    val colorB = (color.blue  * 255).toInt()
    val colorA = color.alpha

    drawBehind {
        if (cardProgress <= 0f || enabledAlpha <= 0f || shadowsAlpha <= 0f) return@drawBehind

        // Search/global result cards animate inside a scaled parent. Drawing the
        // native blurred shadow during that scale transition can make Android
        // briefly rasterize the blur against rectangular layer bounds. For those
        // cards, the card body performs the stagger first, then the final rounded
        // shadow eases in after landing. Regular panels keep the normal progressive
        // shadow fade that follows cardProgress.
        val motionProgress = if (deferUntilSettled) settledShadowAlpha else cardProgress
        val fraction = (motionProgress * motionProgress * enabledAlpha * shadowsAlpha).coerceIn(0f, 1f)
        if (fraction <= 0.001f) return@drawBehind

        // Only rebuild BlurMaskFilter when the effective blur radius changes.
        // Because fraction animates smoothly this will update on most frames, BUT
        // BlurMaskFilter on Android is resolved to a fixed-radius GPU convolution at
        // draw time — rebuilding it is cheap (~microseconds) compared to the old
        // approach of also constructing a new Paint every frame.
        // Keep blur radius stable while alpha fades in. Animating blur from ~0 made
        // newly appearing result cards briefly draw a hard, boxy shadow before it
        // softened into the real rounded card shadow.
        val newBlurFraction = blurPx
        if (newBlurFraction != cachedBlurFraction) {
            cachedBlurFraction = newBlurFraction
            cachedShadowPaint.maskFilter = BlurMaskFilter(
                newBlurFraction.coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL
            )
        }

        val shadowArgb = android.graphics.Color.argb(
            (colorA * fraction * 255).toInt().coerceIn(0, 255),
            colorR, colorG, colorB
        )
        cachedShadowPaint.color = shadowArgb

        drawIntoCanvas { canvas ->
            // Keep the shadow geometry completely stable during card enter/exit.
            // Animating the shadow rect/offset together with alpha looked fine at
            // the first and last frames, but in the middle Android would briefly
            // rasterize the blur against the layer bounds and the shadow looked
            // square-ish. Only alpha animates now; the rounded shadow shape stays
            // locked to the final card shape for the entire stagger.
            canvas.nativeCanvas.drawRoundRect(
                dxPx,
                dyPx,
                size.width + dxPx,
                size.height + dyPx,
                cornerPx, cornerPx,
                cachedShadowPaint
            )
        }
    }
}

internal fun DrawScope.drawIconShapeInline(iconType: String, w: Float, h: Float, color: Color) {
    val stroke = Stroke(width = w * 0.095f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val strokeThin = Stroke(width = w * 0.07f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (iconType) {
        "vulkan" -> {
            // Lightning bolt tracing the reference image exactly:
            //   Upper body: wide parallelogram, top-right to top-left, sweeping down-left
            //   Step: sharp horizontal notch — right protrusion at mid-height
            //   Lower spike: long narrow triangle pointing to bottom-left tip
            //
            //   P1 top-right corner of upper body   (0.72, 0.04)
            //   P2 top-left  corner of upper body   (0.38, 0.04)
            //   P3 left edge at step height          (0.18, 0.50)
            //   P4 notch inner-left (step base)      (0.42, 0.50)
            //   P5 bottom spike tip                  (0.28, 0.97)
            //   P6 notch outer-right (step tip)      (0.82, 0.44)
            //   P7 right edge of upper body          (0.58, 0.44)  ← back up to close
            drawPath(Path().apply {
                moveTo(w * 0.72f, h * 0.04f)   // P1: top-right
                lineTo(w * 0.38f, h * 0.04f)   // P2: top-left
                lineTo(w * 0.18f, h * 0.50f)   // P3: lower-left of upper body
                lineTo(w * 0.42f, h * 0.50f)   // P4: step inner corner
                lineTo(w * 0.28f, h * 0.97f)   // P5: bottom spike tip
                lineTo(w * 0.82f, h * 0.44f)   // P6: step outer-right tip
                lineTo(w * 0.58f, h * 0.44f)   // P7: right edge of upper body
                close()
            }, color = color)
        }
        "opengl" -> {
            fun hexPath(cx: Float, cy: Float, r: Float): Path {
                val pts = (0..5).map { i -> val a = Math.toRadians(60.0 * i - 30.0); Offset((cx + r * cos(a)).toFloat(), (cy + r * sin(a)).toFloat()) }
                return Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close() }
            }
            drawPath(hexPath(w*0.5f, h*0.5f, w*0.46f), color = color, style = stroke)
            drawPath(hexPath(w*0.5f, h*0.5f, w*0.26f), color = color.copy(alpha = 0.55f), style = strokeThin)
            drawCircle(color = color.copy(alpha = 0.8f), radius = w * 0.09f, center = Offset(w*0.5f, h*0.5f))
        }
        "resources" -> {
            val sw = w * 0.09f
            listOf(Triple(w*0.12f, w*0.88f, h*0.25f), Triple(w*0.12f, w*0.72f, h*0.50f), Triple(w*0.12f, w*0.56f, h*0.75f))
                .forEachIndexed { i, (x1, x2, y) -> drawLine(color.copy(alpha = 1f - i * 0.18f), Offset(x1, y), Offset(x2, y), sw, StrokeCap.Round) }
            drawCircle(color = color, radius = w * 0.10f, center = Offset(w * 0.84f, h * 0.75f))
        }
        "gpuwatch" -> {
            drawPath(Path().apply {
                moveTo(w*0.04f, h*0.50f); lineTo(w*0.28f, h*0.50f); lineTo(w*0.40f, h*0.18f)
                lineTo(w*0.52f, h*0.82f); lineTo(w*0.64f, h*0.50f); lineTo(w*0.96f, h*0.50f)
            }, color = color, style = Stroke(width = w * 0.105f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

internal fun DrawScope.drawIconShape(iconType: String, canvasSize: Size, color: Color) {
    val w = canvasSize.width; val h = canvasSize.height
    when (iconType) {
        "vulkan" -> {
            val path = Path().apply {
                moveTo(w * 0.72f, h * 0.04f)   // P1: top-right
                lineTo(w * 0.38f, h * 0.04f)   // P2: top-left
                lineTo(w * 0.18f, h * 0.50f)   // P3: lower-left of upper body
                lineTo(w * 0.42f, h * 0.50f)   // P4: step inner corner
                lineTo(w * 0.28f, h * 0.97f)   // P5: bottom spike tip
                lineTo(w * 0.82f, h * 0.44f)   // P6: step outer-right tip
                lineTo(w * 0.58f, h * 0.44f)   // P7: right edge of upper body
                close()
            }
            drawPath(path, color = color)
        }
        "opengl" -> {
            fun hexPath(cx: Float, cy: Float, r: Float): Path {
                val pts = (0..5).map { i ->
                    val a = Math.toRadians(60.0 * i - 30.0)
                    Offset((cx + r * cos(a)).toFloat(), (cy + r * sin(a)).toFloat())
                }
                return Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close() }
            }
            drawPath(hexPath(w*0.5f,h*0.5f,w*0.46f), color=color, style=Stroke(w*0.10f, cap=StrokeCap.Round, join=StrokeJoin.Round))
            drawPath(hexPath(w*0.5f,h*0.5f,w*0.26f), color=color.copy(alpha=0.65f), style=Stroke(w*0.075f, cap=StrokeCap.Round, join=StrokeJoin.Round))
            drawCircle(color=color, radius=w*0.09f, center=Offset(w*0.5f,h*0.5f))
        }
    }
}

