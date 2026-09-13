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


// ============================================================
// Theme: fonts, type scale, motion tokens, colors, locals
// ============================================================

val quicksandFontFamily = FontFamily(
    Font(R.font.quicksand_light, FontWeight.Light),
    Font(R.font.quicksand_regular, FontWeight.Normal),
    Font(R.font.quicksand_medium, FontWeight.Medium),
    Font(R.font.quicksand_semibold, FontWeight.SemiBold),
    Font(R.font.quicksand_bold, FontWeight.Bold)
)


// ============================================================================
// ADAPTIVE TYPE SYSTEM
// ============================================================================
//
// Single source of truth for font sizing.  Every call site uses adaptiveSp()
// instead of raw hard-coded values so text scales with:
//   • Screen width / height (phones vs tablets vs foldables)
//   • System font-scale preference (accessibility)
//   • Orientation (landscape reduces available vertical space)
//   • DPI class (ldpi / mdpi / hdpi / xhdpi / …)
//
// Usage:
//   val ts = rememberAdaptiveType()
//   fontSize = ts.body           // or ts.label, ts.title, etc.
//
// The scale factor is kept in [0.82, 1.20] so nothing is ever unreadably
// small or comically large regardless of device/setting combination.

data class AdaptiveTypeScale(
    // Panel / dialog titles  (e.g. "SETTINGS", "RESOURCES")
    val displayLarge: TextUnit,   // ~40–48 sp at normal density
    val displayMedium: TextUnit,   // ~32–40 sp
    val displaySmall: TextUnit,   // ~26–34 sp

    // Section headings inside panels
    val headlineLarge: TextUnit,  // ~22–28 sp
    val headlineMedium: TextUnit,  // ~20–24 sp
    val headlineSmall: TextUnit,  // ~17–21 sp

    // Body / list content
    val bodyLarge: TextUnit,     // ~15–18 sp
    val bodyMedium: TextUnit,     // ~13–16 sp
    val bodySmall: TextUnit,     // ~12–14 sp

    // Buttons
    val buttonLarge: TextUnit,    // ~18–22 sp
    val buttonMedium: TextUnit,    // ~16–18 sp

    // Labels / captions / badges
    val labelLarge: TextUnit,     // ~13–15 sp
    val labelMedium: TextUnit,     // ~12–13 sp
    val labelSmall: TextUnit,     // ~11–12 sp
)

@Composable
fun rememberAdaptiveType(): AdaptiveTypeScale {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    return remember(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        configuration.fontScale,
        configuration.densityDpi,
        configuration.orientation
    ) {
        val widthDp = configuration.screenWidthDp.toFloat()
        val heightDp = configuration.screenHeightDp.toFloat()
        val fontScale = configuration.fontScale          // user accessibility pref
        val dpi = configuration.densityDpi         // physical DPI
        val isLandscape = widthDp > heightDp
        val isTablet = widthDp >= 600f && heightDp >= 600f

        // ── Layout scale: how much space we actually have ────────────────────
        // Normalised against a "reference" 400 dp wide portrait phone.
        // Tablets get a gentle boost; landscape phones get a mild reduction
        // because vertical space is tight and we don't want giant labels.
        val layoutScale = when {
            isTablet -> (widthDp / 720f).coerceIn(0.92f, 1.25f)
            isLandscape -> (widthDp / 640f).coerceIn(0.80f, 1.05f)
            else -> (widthDp / 400f).coerceIn(0.85f, 1.18f)
        }

        // ── DPI nudge: very high-DPI screens pack more pixels per dp so text
        // already looks sharp; very low-DPI screens need a touch more size ──
        val dpiNudge = when {
            dpi >= 560 -> 0.96f   // xxxhdpi — already crisp
            dpi >= 420 -> 0.98f   // xxhdpi
            dpi >= 280 -> 1.00f   // xhdpi / hdpi — reference
            dpi >= 200 -> 1.02f   // mdpi
            else -> 1.05f   // ldpi — needs help
        }

        // TextUnit.sp is resolved through LocalDensity below, where Android's
        // accessibility fontScale is already applied. Do not multiply it here:
        // doing so made large system fonts scale twice.
        val m = (layoutScale * dpiNudge).coerceIn(0.85f, 1.25f)

        fun Float.s() = (this * m).coerceIn(8f, 72f).sp

        AdaptiveTypeScale(
            displayLarge = 50f.s(),
            displayMedium = 44f.s(),
            displaySmall = 37f.s(),

            headlineLarge = 28f.s(),
            headlineMedium = 24f.s(),
            headlineSmall = 21f.s(),

            bodyLarge = 18f.s(),
            bodyMedium = 16f.s(),
            bodySmall = 14f.s(),

            buttonLarge = 21f.s(),
            buttonMedium = 17f.s(),

            labelLarge = 15f.s(),
            labelMedium = 13f.s(),
            labelSmall = 12f.s(),
        )
    }
}

// ============================================================================
// PREMIUM ANIMATION SYSTEM - Motion Design Tokens
// ============================================================================

object MotionTokens {
    object Duration {
        const val instant = 0
        const val flash = 150
        const val quick = 300
        const val brief = 450
        const val short = 600
        const val snappy = 750
        const val medium = 900
        const val moderate = 1050
        const val balanced = 1200
        const val leisurely = 1350
        const val long = 1500
        const val extended = 1800
        const val expansive = 2100
        const val dramatic = 2400
        const val epic = 3000
    }

    object Easing {
        val emphasized = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
        val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f) // refined: softer deceleration landing
        val emphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f) // refined: sharper initial kick
        val silk = CubicBezierEasing(0.45f, 0.05f, 0.55f, 0.95f)
        val butter = CubicBezierEasing(0.35f, 0.0f, 0.1f, 1.0f)  // refined: more pronounced ease-out
        val velvet = CubicBezierEasing(0.37f, 0.0f, 0.63f, 1.0f)

        // Enter curve: fast lift-off, gentle deceleration into final position
        val enter = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)

        // Exit curve: immediate acceleration, swift departure
        val exit = CubicBezierEasing(0.55f, 0.0f, 1.0f, 0.45f)

        // Overshoot: like enter but with a subtle anticipation kick
        val overshoot = CubicBezierEasing(0.34f, 1.2f, 0.64f, 1.0f)
    }

    object Springs {
        data class SpringConfig(val dampingRatio: Float, val stiffness: Float)

        val silk = SpringConfig(0.82f, 280f)   // slightly more damped — settles with no ringing
        val smooth = SpringConfig(0.76f, 380f)
        val gentle = SpringConfig(0.72f, 480f)
        val balanced = SpringConfig(0.62f, 580f)
        val responsive = SpringConfig(0.56f, 680f)
        val playful = SpringConfig(0.44f, 230f)   // softer stiffness — longer, dreamier bounce
        val snappy = SpringConfig(0.88f, 950f)    // fractionally less stiff — avoids a hard-mechanical feel

        // Press-down: immediate, no bounce. Release: overshoots past rest, settles with one clean bounce.
        val pressDown = SpringConfig(0.92f, 1500f)   // marginally higher damping — zero micro-oscillation on press
        val pressUp = SpringConfig(0.46f, 440f)   // slightly less stiff — bounce travels a hair further before settling

        // Snap-back: used after swipe-to-dismiss cancel; confident return to rest
        val snapBack = SpringConfig(0.72f, 600f)
    }

    object Scale {
        const val subtle = 0.97f
        const val mild = 0.95f
        const val moderate = 0.90f
        const val dramatic = 0.85f
    }

    object SpeedUtil {
        fun durationMs(baseMs: Int, speed: Int): Int {
            return when (speed) {
                0 -> (baseMs * 3 / 2).coerceAtMost(9000)
                1 -> baseMs
                2 -> (baseMs * 7 / 10).coerceAtLeast(50)
                else -> baseMs
            }
        }

        fun stiffness(baseStiffness: Float, speed: Int): Float {
            return when (speed) {
                0 -> baseStiffness * 0.8f
                1 -> baseStiffness
                2 -> baseStiffness * 1.2f
                else -> baseStiffness
            }
        }
    }
}


data class ThemeColors(
    val background: Color,
    val cardBackground: Color,
    val primaryAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val successColor: Color,
    val errorColor: Color,
    val gradientStart: Color,
    val gradientEnd: Color
) {
    companion object {
        fun dark(
            accent: Color = Color(0xFF4895EF),
            gradStart: Color = Color(0xFF000000),
            gradEnd: Color = Color(0xFF000000)
        ) = ThemeColors(
            background = Color(0xFF000000),
            cardBackground = Color(0xFF000000),
            primaryAccent = accent,
            textPrimary = Color.White,
            textSecondary = Color.White.copy(alpha = 0.7f),
            border = accent.copy(alpha = 0f),
            successColor = accent,
            errorColor = accent,
            gradientStart = Color(0xFF000000),
            gradientEnd = Color(0xFF000000)
        )

        fun light(
            accent: Color = Color(0xFF1D4ED8),
            gradStart: Color = Color(0xFF3B6FD4),
            gradEnd: Color = Color(0xFFF0F4FF)
        ) = ThemeColors(
            // Warm off-white base — less clinical than pure white, more depth than pale blue
            background = Color(0xFFF5F6FA),
            // Cards sit slightly above the background with a warm paper-white tone
            cardBackground = Color(0xFFFCFCFE),
            primaryAccent = accent,
            // Near-black with a tiny cool tint — reads sharper than neutral dark grey
            textPrimary = Color(0xFF0F172A),
            // Slate-blue secondary — more character than a flat grey, harmonises with accent
            textSecondary = Color(0xFF64748B),
            // Slightly stronger border so cards have clear definition on the light surface
            border = accent.copy(alpha = 0.22f),
            successColor = accent,
            errorColor = Color(0xFFB91C1C),
            gradientStart = gradStart,
            gradientEnd = gradEnd
        )

        // OLED-Optimized Theme with customizable accent color
        fun oledMode(accentColor: Color = Color(0xFF4895EF)) = ThemeColors(
            background = Color(0xFF000000), // Pure black for OLED
            cardBackground = Color(0xFF000000), // Pure black for OLED
            primaryAccent = accentColor, // Accent color for text elements
            textPrimary = Color.White, // Perfect white for text
            textSecondary = Color.White.copy(alpha = 0.7f), // Dimmed white for secondary text
            border = accentColor.copy(alpha = 0f), // No visible borders in OLED — accent-transparent so animation fades cleanly
            successColor = accentColor,
            errorColor = accentColor,
            gradientStart = Color(0xFF000000), // Pure black
            gradientEnd = Color(0xFF000000) // Pure black
        )
    }
}


val LocalAnimationsEnabled = compositionLocalOf { true }
val LocalAnimationLevel = compositionLocalOf { 0 }
val LocalAnimationSpeed = compositionLocalOf { 1 } // 0=slow, 1=normal, 2=fast
val LocalThemeColors = compositionLocalOf { ThemeColors.dark() }
val LocalUIScale = compositionLocalOf { 1 } // 0=75%, 1=100%, 2=125%
val LocalDismissOnClickOutside = compositionLocalOf { true } // New global setting for back behavior
val LocalStaggerEnabled = compositionLocalOf { true } // true = cascading stagger, false = simultaneous fade+scale
val LocalBackButtonAvoidanceEnabled = compositionLocalOf { true } // true = cards duck away from floating back button
val LocalBackButtonInversed = compositionLocalOf { false } // false = back button on right, true = back button on left
/** Normalized positions: X is within its half of the screen; Y spans the screen. */
data class FloatingButtonAnchors(
    val leftX: Float = 0.30f,
    val leftY: Float = 0.94f,
    val rightX: Float = 0.70f,
    val rightY: Float = 0.94f,
    /** Landscape anchors use the full screen width; portrait anchors use their half. */
    val fullWidth: Boolean = false
)
val LocalFloatingButtonAnchors = compositionLocalOf { FloatingButtonAnchors() }

class FloatingButtonHoldState {
    var suppressTap by mutableStateOf(false)
    var repositioning by mutableStateOf(false)
    var holdProgress by mutableFloatStateOf(0f)
    var holdStartedAtMs by mutableLongStateOf(0L)
    var holding by mutableStateOf(false)
    var dragTranslationX by mutableFloatStateOf(0f)
    var dragTranslationY by mutableFloatStateOf(0f)
}

class FloatingButtonLivePositions {
    var leftX by mutableStateOf<Float?>(null)
    var leftY by mutableStateOf<Float?>(null)
    var rightX by mutableStateOf<Float?>(null)
    var rightY by mutableStateOf<Float?>(null)
    var settingsX by mutableStateOf<Float?>(null)
    var settingsY by mutableStateOf<Float?>(null)

    fun set(isLeftSide: Boolean, isSettingsButton: Boolean, x: Float, y: Float) {
        when {
            isSettingsButton -> { settingsX = x; settingsY = y }
            isLeftSide -> { leftX = x; leftY = y }
            else -> { rightX = x; rightY = y }
        }
    }

    fun clear(isLeftSide: Boolean, isSettingsButton: Boolean) {
        when {
            isSettingsButton -> { settingsX = null; settingsY = null }
            isLeftSide -> { leftX = null; leftY = null }
            else -> { rightX = null; rightY = null }
        }
    }

    fun clearAll() {
        leftX = null
        leftY = null
        rightX = null
        rightY = null
        settingsX = null
        settingsY = null
    }
}

data class FloatingButtonPositionController(
    val leftX: Float = 0.30f,
    val leftY: Float = 0.94f,
    val rightX: Float = 0.70f,
    val rightY: Float = 0.94f,
    val fullWidth: Boolean = false,
    val settingsX: Float = 0.84f,
    val settingsY: Float = 0.94f,
    val settingsFullWidth: Boolean = false,
    val livePositions: FloatingButtonLivePositions = FloatingButtonLivePositions(),
    val onLeftMove: (Float, Float) -> Unit = { _, _ -> },
    val onRightMove: (Float, Float) -> Unit = { _, _ -> },
    val onSettingsMove: (Float, Float) -> Unit = { _, _ -> }
)
val LocalFloatingButtonPositionController = compositionLocalOf { FloatingButtonPositionController() }
val LocalShadowsEnabled =
    compositionLocalOf { true } // true = card elevation shadows, false = flat (no shadow blur pass)
val LocalCardSettled = compositionLocalOf { true }  // false while AnimatedElement is mid-stagger, true once it lands
val LocalCardProgress =
    compositionLocalOf { 1f }    // mirrors AnimatedElement's progress [0,1]; drives directional shadow intensity
val LocalCardEnabled =
    compositionLocalOf { true }  // false when the card is disabled; drives shadow fade-out with ease-in-out animation
val LocalPanelScrollState = compositionLocalOf<androidx.compose.foundation.ScrollState?> { null }
// True only while PanelScaffold is arranging ordinary settings cards in its
// two-column landscape grid.
val LocalLandscapePanelGrid = compositionLocalOf { false }
val LocalTypeScale = compositionLocalOf {
    AdaptiveTypeScale(
        displayLarge = 50.sp, displayMedium = 44.sp, displaySmall = 37.sp,
        headlineLarge = 28.sp, headlineMedium = 24.sp, headlineSmall = 21.sp,
        bodyLarge = 18.sp, bodyMedium = 16.sp, bodySmall = 14.sp,
        buttonLarge = 21.sp, buttonMedium = 17.sp,
        labelLarge = 15.sp, labelMedium = 13.sp, labelSmall = 12.sp,
    )
}


// Theme color scheme
