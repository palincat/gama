package com.popovicialinc.gama

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
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
import androidx.compose.ui.graphics.drawscope.withTransform
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
// Particles: ParticleState, CelestialState, ParticlesOverlay
// ============================================================

data class ParticleState(
    var x: Float,
    var y: Float,
    val size: Float,
    val speed: Float,
    val alpha: Float
) {
    var velocityX = 0f
    var velocityY = 0f

    // Smoothed rotation values
    var smoothRotationX = 0f
    var smoothRotationY = 0f

    // Per-particle LCG seed for wrap randomisation — avoids locking the global
    // kotlin.random.Random instance (which has a synchronized internal state on JVM).
    // At 300 particles wrapping frequently, global Random lock contention is measurable.
    // This LCG (Lehmer-style) is fast, allocation-free, and good enough for visual scatter.
    private var lcgSeed: Int = (System.nanoTime() xor hashCode().toLong()).toInt().let {
        if (it == 0) 1 else it  // seed must be non-zero
    }

    // Returns a pseudo-random float in [0, 1) using a fast 32-bit LCG.
    // No allocation, no lock, no JNI — pure integer arithmetic on the calling thread.
    private fun nextRandFloat(): Float {
        lcgSeed = lcgSeed * 1664525 + 1013904223  // Numerical Recipes LCG constants
        return (lcgSeed ushr 8) / 16777216f        // 24-bit mantissa → [0, 1)
    }
    // Pre-baked star geometry — computed once at construction, used every draw frame.
    // starR  = size * 1.2  (arm half-length)
    // starRd = size * 1.2 * 0.68 = size * 0.816  (diagonal arm half-length)
    val starR:  Float = size * 1.2f
    val starRd: Float = size * 0.816f   // 1.2 * 0.68 collapsed to one constant

    // Pre-baked base alpha for star bucketing: alpha * 0.6 — alpha is a val so this
    // never changes. Eliminates one float multiplication per particle per draw frame.
    val baseStarAlpha: Float = alpha * 1.0f

    fun update(
        speedMultiplier: Float = 1f,
        rotationX: Float = 0f,
        rotationY: Float = 0f,
        deltaTime: Float = 0.016f,
        parallaxSensitivity: Float = 0.025f,
        // Pre-computed damping factor for this tick — avoids one Math.pow() per particle
        // Caller computes: Math.pow(0.94, (deltaTime * 60.0)).toFloat() once per tick
        frameDamping: Float = Math.pow(0.94, (deltaTime * 60.0)).toFloat(),
        // Pre-computed per-tick constants — caller derives these once outside the particle loop
        maxVelocity: Float = 15f * speedMultiplier,
        dt60: Float = deltaTime * 60f
    ) {
        val rawDeltaX = rotationX - smoothRotationX
        val rawDeltaY = rotationY - smoothRotationY

        // Clamp rotation deltas — prevents excessive speed at any phone orientation
        val deltaRotationX = rawDeltaX.coerceIn(-MAX_ROT_DELTA, MAX_ROT_DELTA)
        val deltaRotationY = rawDeltaY.coerceIn(-MAX_ROT_DELTA, MAX_ROT_DELTA)

        smoothRotationX = rotationX
        smoothRotationY = rotationY

        // naturalForceX was always 0f — removed.  Particles rise straight up; only
        // parallax adds horizontal drift.  Forces applied in one fused expression to
        // reduce intermediate val allocations and give the JIT a single compound expression.
        val speedFactor = speed * speedMultiplier
        velocityX = ((velocityX + deltaRotationY * parallaxSensitivity * speedFactor * 350f * dt60) * frameDamping)
            .coerceIn(-maxVelocity, maxVelocity)
        velocityY = ((velocityY + (-0.007f * speedFactor + (-deltaRotationX * parallaxSensitivity * speedFactor * 50f)) * dt60) * frameDamping)
            .coerceIn(-maxVelocity, maxVelocity)

        x += velocityX * deltaTime
        y += velocityY * deltaTime

        when {
            y < -0.1f -> { y = 1.1f;  x = nextRandFloat(); velocityX *= 0.5f; velocityY *= 0.5f }
            y >  1.1f -> { y = -0.1f; x = nextRandFloat(); velocityX *= 0.5f; velocityY *= 0.5f }
        }
        when {
            x < -0.1f -> x = 1.1f
            x >  1.1f -> x = -0.1f
        }
    }

    companion object {
        private const val MAX_ROT_DELTA = 0.1f
    }
}

// Celestial object state for sun and moon
data class CelestialState(
    val x: Float,
    val y: Float,
    val size: Float
)

// Function to calculate current celestial position based on real time
fun calculateCelestialPosition(screenWidth: Float, screenHeight: Float, timeOffsetHours: Float = 0f, isLandscape: Boolean = false): CelestialState? {
    val calendar = Calendar.getInstance()
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)

    // Convert time to decimal hours (0.0 - 24.0) and apply offset
    val baseTime = hour + (minute / 60f)
    var currentTime = (baseTime + timeOffsetHours) % 24f
    if (currentTime < 0f) currentTime += 24f

    // Define day and night periods
    val sunriseStart = 6.0f  // 6:00 AM - sun starts rising
    val sunriseEnd = 7.0f    // 7:00 AM - sun fully risen
    val sunsetStart = 18.0f  // 6:00 PM - sun starts setting
    val sunsetEnd = 19.0f    // 7:00 PM - sun fully set

    val moonriseStart = 19.0f  // 7:00 PM - moon starts rising
    val moonriseEnd = 20.0f    // 8:00 PM - moon fully risen
    val moonsetStart = 5.0f    // 5:00 AM - moon starts setting
    val moonsetEnd = 6.0f      // 6:00 AM - moon fully set

    // NEW TRAJECTORY: Arc matching the star pattern from reference image
    // Wider horizontal spread (almost edge to edge)
    // Higher peak in the center
    // Lower endpoints near the horizon

    // Calculate position and alpha based on time
    return when {
        // Daytime - Sun visible (7 AM to 6 PM)
        currentTime >= sunriseEnd && currentTime < sunsetStart -> {
            // Sun moves from left (7 AM) to center (12:30 PM) to right (6 PM)
            val dayDuration = sunsetStart - sunriseEnd // 11 hours
            val progress = (currentTime - sunriseEnd) / dayDuration

            // Wide arc matching the star pattern: endpoints closer to screen edges
            val startX = screenWidth * 0.08f  // Much closer to left edge
            val endX = screenWidth * 0.92f    // Much closer to right edge
            val lowestY = if (isLandscape) screenHeight * 0.30f else screenHeight * 0.25f
            val highestY = if (isLandscape) screenHeight * 0.12f else screenHeight * 0.08f

            // Calculate X position (linear interpolation)
            val x = startX + (endX - startX) * progress

            // Calculate Y position (smooth parabolic arc)
            // Use sine for smoother arc shape matching the star pattern
            val arcProgress = sin(progress * PI.toFloat())
            val y = lowestY - (lowestY - highestY) * arcProgress

            CelestialState(
                x = x,
                y = y,
                size = 48f  // Slightly larger sun
            )
        }

        // Sunrise transition (6 AM to 7 AM)
        currentTime >= sunriseStart && currentTime < sunriseEnd -> {
            val progress = (currentTime - sunriseStart) / (sunriseEnd - sunriseStart)
            val x = screenWidth * 0.08f
            val y = if (isLandscape) screenHeight * 0.30f else screenHeight * 0.25f

            CelestialState(
                x = x,
                y = y,
                size = 48f
            )
        }

        // Sunset transition (6 PM to 7 PM)
        currentTime >= sunsetStart && currentTime < sunsetEnd -> {
            val progress = (currentTime - sunsetStart) / (sunsetEnd - sunsetStart)
            val x = screenWidth * 0.92f
            val y = if (isLandscape) screenHeight * 0.30f else screenHeight * 0.25f

            CelestialState(
                x = x,
                y = y,
                size = 48f
            )
        }

        // Nighttime - Moon visible (8 PM to 5 AM)
        currentTime >= moonriseEnd || currentTime < moonsetStart -> {
            // Normalize time to 0-9 hour range (8 PM = 0, 5 AM = 9)
            val normalizedTime = if (currentTime >= moonriseEnd) {
                currentTime - moonriseEnd
            } else {
                currentTime + (24f - moonriseEnd)
            }

            val nightDuration = (24f - moonriseEnd) + moonsetStart // 9 hours total
            val progress = normalizedTime / nightDuration

            // Moon follows same wide arc pattern as sun
            val startX = screenWidth * 0.08f
            val endX = screenWidth * 0.92f
            val lowestY = if (isLandscape) screenHeight * 0.30f else screenHeight * 0.25f
            val highestY = if (isLandscape) screenHeight * 0.12f else screenHeight * 0.08f

            val x = startX + (endX - startX) * progress

            val arcProgress = sin(progress * PI.toFloat())
            val y = lowestY - (lowestY - highestY) * arcProgress

            CelestialState(
                x = x,
                y = y,
                size = 42f
            )
        }

        // Moonrise transition (7 PM to 8 PM)
        currentTime >= moonriseStart && currentTime < moonriseEnd -> {
            val progress = (currentTime - moonriseStart) / (moonriseEnd - moonriseStart)
            val x = screenWidth * 0.08f
            val y = if (isLandscape) screenHeight * 0.30f else screenHeight * 0.25f

            CelestialState(
                x = x,
                y = y,
                size = 42f
            )
        }

        // Moonset transition (5 AM to 6 AM)
        currentTime >= moonsetStart && currentTime < moonsetEnd -> {
            val progress = (currentTime - moonsetStart) / (moonsetEnd - moonsetStart)
            val x = screenWidth * 0.92f
            val y = if (isLandscape) screenHeight * 0.30f else screenHeight * 0.25f

            CelestialState(
                x = x,
                y = y,
                size = 42f
            )
        }

        else -> null
    }
}

// Helper function to determine if it's daytime
fun isDaytime(timeOffsetHours: Float = 0f): Boolean {
    val calendar = Calendar.getInstance()
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)
    val baseTime = hour + (minute / 60f)
    var currentTime = (baseTime + timeOffsetHours) % 24f
    if (currentTime < 0f) currentTime += 24f
    return currentTime >= 7f && currentTime < 19f // 7 AM to 7 PM
}

// ─────────────────────────────────────────────────────────────────────────────
// PhysicsInputs — volatile snapshot shared between the Compose main thread
// (writes) and the physics background thread (reads).
//
// Why @Volatile instead of AtomicXxx:
//   • Float/Boolean reads/writes are effectively atomic on 64-bit ARM (all
//     modern Android).  @Volatile adds the happens-before memory barrier so
//     the background thread always sees the latest value — no stale reads.
//   • Zero allocation, zero lock contention, zero GC pressure.
//   • Worst case of a racy read: one particle appears at its previous position
//     for one 16ms frame — completely invisible to the human eye.
// ─────────────────────────────────────────────────────────────────────────────
private class PhysicsInputs {
    @Volatile var rotX:  Float = 0f
    @Volatile var rotY:  Float = 0f
    @Volatile var sens:  Float = 0f
    @Volatile var speed: Float = 3f
    // star/timeMode/daytime removed — update() no longer uses them
}

// 4 buckets instead of 8: halves GPU draw calls in star mode (was ≤8, now ≤4).
// Alpha step of 0.25 is imperceptible for 2–14px sparkle particles.
// Circle mode still uses 4 buckets via circleBatchPaths (same constant).
private const val STAR_ALPHA_BUCKETS = 4
private const val STAR_DOT_SIZE_BUCKETS = 3

private fun readLiveDisplayRefreshRate(context: Context): Float {
    return try {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay
        }
        display?.refreshRate?.takeIf { it > 0f } ?: 60f
    } catch (_: Exception) {
        60f
    }
}

@Composable
private fun rememberLiveDisplayRefreshRate(context: Context): Float {
    var refreshRate by remember(context) {
        mutableFloatStateOf(readLiveDisplayRefreshRate(context))
    }

    DisposableEffect(context) {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay
        }
        val displayId = display?.displayId ?: Display.DEFAULT_DISPLAY
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(changedDisplayId: Int) {
                if (changedDisplayId == displayId) {
                    refreshRate = readLiveDisplayRefreshRate(context)
                }
            }
        }

        displayManager?.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        onDispose { displayManager?.unregisterDisplayListener(listener) }
    }

    return refreshRate.coerceIn(1f, 240f)
}

// Standalone Particles Overlay Component
@Composable
fun ParticlesOverlay(
    enabled: Boolean,
    color: Color,
    particleSpeed: Int = 1, // 0=low, 1=medium, 2=high
    parallaxEnabled: Boolean = true,
    particleCount: Int = 1, // 0=low(75), 1=medium(150), 2=high(300), 3=custom
    particleCountCustom: Int = 150,
    parallaxSensitivity: Float = 0.025f, // New parameter for sensitivity (0.0 to 1.0) - reduced for subtlety
    starMode: Boolean = false, // Star mode toggle
    timeModeEnabled: Boolean = false, // Time-based sun & moon system
    timeOffsetHours: Float = 0f, // Developer: hours to add to current time
    blurRadius: Dp = 0.dp, // Panel blur radius — animated in lock-step with the main content blur
    isLandscape: Boolean = false, // NEW: Constrain celestials to left half in landscape
    nativeRefreshRate: Boolean = false, // true = render every vsync; false = skip every other (default, saves battery)
    quarterRefreshRate: Boolean = false  // true = render at 1/4 native rate; only applies when nativeRefreshRate is false
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val displayHz = rememberLiveDisplayRefreshRate(context)

    // Calculate screen width in pixels for cloud updates
    val screenWidthPx = remember(configuration) {
        with(density) { configuration.screenWidthDp.dp.toPx() }
    }
    val screenHeightPx = remember(configuration) {
        with(density) { configuration.screenHeightDp.dp.toPx() }
    }

    // Accent changes must never rebuild / respawn the particle field.
    // Keep particle positions in their own remembered array and only animate the
    // paint colour used during draw. This means ACCENT COLOR changes ease smoothly
    // over the existing stars/celestials instead of creating a fresh particle set.
    val renderedParticleColor by animateColorAsState(
        targetValue = lerp(color, Color.White, 0.42f),
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "particles_render_color"
    )

    // The sun and moon use the RAW accent colour — no white blend (the particle
    // colour is lerped 42% toward white; the celestials must stay true accent).
    val renderedCelestialColor by animateColorAsState(
        targetValue = color,
        animationSpec = tween(durationMillis = 620, easing = FastOutSlowInEasing),
        label = "celestial_render_color"
    )

    // Celestial position — recomputed at most once per second via a tick counter.
    // Previously computed inside Canvas on every draw frame (60–120× per second),
    // which allocated a Calendar object and ran trigonometry every frame needlessly
    // since the sun/moon only moves visibly once per minute.
    var celestialTickSecond by remember { mutableStateOf(0L) }
    val celestialState = remember(celestialTickSecond, timeModeEnabled) {
        if (!timeModeEnabled) null
        else calculateCelestialPosition(screenWidthPx, screenHeightPx, timeOffsetHours)
    }

    // ── Device capability cap ─────────────────────────────────────────────────
    // On constrained devices (low-RAM or single/dual-core) the user's selected
    // particle count is silently capped so the physics thread never competes badly
    // with the main thread.  Thresholds are conservative:
    //   ≤ 1 GB total RAM  → max 50  (entry-level: Unisoc, old MediaTek)
    //   ≤ 2 GB total RAM  → max 100 (mid-range: SD450, Helio P22)
    //   ≤ 3 GB total RAM  → max 150 (lower-mid: SD625, SD660)
    //   > 3 GB            → no cap  (SD700+, Dimensity 900+, etc.)
    // We use totalMem from ActivityManager — the only reliable cross-API source.
    val deviceParticleCap = remember {
        try {
            val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE)
                    as android.app.ActivityManager
            val info = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            val totalGb = info.totalMem / (1024.0 * 1024.0 * 1024.0)
            when {
                totalGb <= 1.0 -> 50
                totalGb <= 2.0 -> 100
                totalGb <= 3.0 -> 150
                else           -> Int.MAX_VALUE
            }
        } catch (_: Exception) { Int.MAX_VALUE } // fail open — don't cap if detection fails
    }

    // Calculate actual particle count based on setting, then apply device cap
    val actualParticleCount = (when (particleCount) {
        0 -> 75  // Low
        1 -> 150 // Medium
        2 -> 300 // High
        3 -> particleCountCustom.coerceIn(1, 500) // Custom (clamped to 1-500)
        else -> 150
    }).coerceAtMost(deviceParticleCap)

    val particles: Array<ParticleState> = remember(actualParticleCount, particleSpeed, parallaxSensitivity) {
        // Array instead of List: forEach on a List allocates an Iterator object every call.
        // At 60Hz physics + 60Hz render that's 120 iterator allocations/sec feeding the GC.
        // Array iteration (for loop or forEach on Array) is zero-allocation.
        val rng = kotlin.random.Random(System.nanoTime())  // isolated instance — no global lock
        Array(actualParticleCount) {
            val spd = rng.nextFloat() * 1.2f + 0.3f   // 0.3–1.5
            ParticleState(
                x     = rng.nextFloat(),
                y     = rng.nextFloat(),
                size  = rng.nextFloat() * 6f + 1f,
                speed = spd,
                alpha = rng.nextFloat() * 0.7f + 0.3f
            ).also { p ->
                p.velocityX = (rng.nextFloat() - 0.5f) * spd * 0.4f
                p.velocityY = -spd * 0.8f
            }
        }
    }

    // Track device rotation for parallax
    var rotationX by remember { mutableStateOf(0f) }
    var rotationY by remember { mutableStateOf(0f) }

    // Animated rotation values that smoothly transition when parallax is disabled
    // Stiffness raised 80→200: the old value kept this spring running for 3+ seconds
    // after every tilt, firing the physicsInputs LaunchedEffect on each frame.
    // 200 settles in ~0.8s — still smooth, but stops animating much sooner.
    val animatedRotationX by animateFloatAsState(
        targetValue = if (parallaxEnabled) rotationX else 0f,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = 200f
        ),
        label = "rotation_x"
    )
    val animatedRotationY by animateFloatAsState(
        targetValue = if (parallaxEnabled) rotationY else 0f,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = 200f
        ),
        label = "rotation_y"
    )

    // Calibration state — plain vars, not mutableStateOf.
    // These are written from the sensor callback thread and read only within that same callback.
    // Making them mutableStateOf was triggering Compose recompositions from a non-main thread,
    // which is both incorrect and causes unnecessary recomposition overhead.
    var calibrationComplete = false
    var initialRotationX = 0f
    var initialRotationY = 0f
    var calibrationSamples = 0

    DisposableEffect(parallaxEnabled, enabled, nativeRefreshRate, quarterRefreshRate, displayHz) {
        if (!parallaxEnabled || !enabled) {
            // Reset rotation values when parallax is disabled or overlay is disabled
            rotationX = 0f
            rotationY = 0f
            // Must return an onDispose result here
            return@DisposableEffect onDispose { }
        }

        // Reset calibration when parallax is enabled/re-enabled
        calibrationComplete = false
        calibrationSamples = 0
        initialRotationX = 0f
        initialRotationY = 0f

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager

        // Try rotation vector first, fall back to accelerometer+magnetometer
        val rotationSensor = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_GAME_ROTATION_VECTOR)

        if (rotationSensor == null) {
            return@DisposableEffect onDispose { }
        }

        val listener = object : android.hardware.SensorEventListener {
            // Low-pass filter for smoothing
            private val lpAlpha = 0.8f
            private var filteredX = 0f
            private var filteredY = 0f

            // Pre-allocated arrays — reused every sensor event.
            // Previously allocated fresh on every onSensorChanged call (up to 15 Hz):
            //   3 FloatArrays × 15 Hz = 45 short-lived allocations/sec → GC pressure.
            private val rotationMatrix = FloatArray(9)
            private val remappedMatrix = FloatArray(9)
            private val orientation    = FloatArray(3)

            override fun onSensorChanged(event: android.hardware.SensorEvent?) {
                event?.let {
                    try {
                        android.hardware.SensorManager.getRotationMatrixFromVector(rotationMatrix, it.values)
                        android.hardware.SensorManager.remapCoordinateSystem(
                            rotationMatrix,
                            android.hardware.SensorManager.AXIS_X,
                            android.hardware.SensorManager.AXIS_Z,
                            remappedMatrix
                        )
                        android.hardware.SensorManager.getOrientation(remappedMatrix, orientation)

                        val pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
                        val roll  = Math.toDegrees(orientation[2].toDouble()).toFloat()

                        filteredX = lpAlpha * filteredX + (1f - lpAlpha) * pitch
                        filteredY = lpAlpha * filteredY + (1f - lpAlpha) * roll

                        if (!calibrationComplete) {
                            if (calibrationSamples < 10) {
                                initialRotationX += filteredX
                                initialRotationY += filteredY
                                calibrationSamples++
                            } else {
                                initialRotationX /= 10f
                                initialRotationY /= 10f
                                calibrationComplete = true
                            }
                        }

                        if (calibrationComplete) {
                            rotationX = (filteredX - initialRotationX) * 0.15f
                            rotationY = (filteredY - initialRotationY) * 0.15f
                        }
                    } catch (_: Exception) { }
                }
            }

            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) { }
        }

        val divisor = when {
            nativeRefreshRate -> 1
            quarterRefreshRate -> 4
            else -> 2
        }
        val pollingHz = (displayHz / divisor).coerceIn(15f, 144f)
        val samplingPeriodUs = (1_000_000f / pollingHz).roundToInt().coerceIn(6_900, 66_666)
        sensorManager?.registerListener(listener, rotationSensor, samplingPeriodUs)

        onDispose {
            sensorManager?.unregisterListener(listener)
            // Reset calibration so next registration starts fresh
            calibrationComplete = false
            calibrationSamples = 0
            initialRotationX = 0f
            initialRotationY = 0f
        }
    }


    // Animated alpha for fade in/out
    // Particles stay visible when panels are open (only fade if disabled)
    val particleAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        animationSpec = tween(
            durationMillis = MotionTokens.Duration.expansive,
            easing = MotionTokens.Easing.silk
        ),
        label = "particle_fade"
    )

    // Speed multiplier — converted from the 0/1/2 setting once here so the
    // physics thread and inputs-sync can both reference the same value.
    val speedMultiplier = when (particleSpeed) {
        0 -> 0.12f   // Slow  — 1× base speed (very gentle drift)
        1 -> 0.36f   // Medium — 3× Slow
        2 -> 0.72f   // Fast  — 6× Slow
        else -> 0.22f
    }

    // frameCount — incremented by the render-trigger LaunchedEffect on the main
    // thread each time we want the Canvas to redraw.  Canvas reads this value to
    // establish a Compose snapshot dependency; it redraws every time it changes.
    var frameCount by remember { mutableLongStateOf(0L) }


    // frameIsDaytime — updated by the render-trigger at most once per second.
    // Kept as Compose state so Canvas reads it correctly via snapshot.
    var frameIsDaytime by remember { mutableStateOf(true) }

    // ── Thread-safe input snapshot ────────────────────────────────────────────
    // Written on the Compose main thread; read on the physics background thread.
    val physicsInputs = remember { PhysicsInputs() }

    // ── Inputs sync — main thread ─────────────────────────────────────────────
    // SideEffect runs synchronously after every successful recomposition that
    // changes any of these values — no coroutine launch/cancel overhead,
    // no key-based cancellation noise on every rotation spring frame.
    SideEffect {
        physicsInputs.rotX  = if (parallaxEnabled) animatedRotationX else 0f
        physicsInputs.rotY  = if (parallaxEnabled) animatedRotationY else 0f
        physicsInputs.sens  = if (parallaxEnabled) parallaxSensitivity else 0f
        physicsInputs.speed = speedMultiplier
        // star/timeMode/daytime removed — update() no longer needs them
    }

    // ── Actual display refresh rate ───────────────────────────────────────────
    //
    // This is the live rate selected by Android's display governor, not the
    // panel's maximum supported mode. The listener above updates it when the
    // device changes modes, allowing LTPO / adaptive-refresh displays to idle
    // down when appropriate and ramp up when animation needs it.

    // ── Vsync divisor ─────────────────────────────────────────────────────────
    //
    // Single number that controls how many display vsyncs are skipped between
    // physics ticks and canvas redraws.  Everything flows from this:
    //
    //   nativeRefreshRate = true            → 1  (tick every vsync — full rate)
    //   quarterRefreshRate = true           → 4  (tick every 4th vsync — 1/4 rate)
    //   capable/mid device, half rate       → 2  (tick every other vsync)
    //   very weak device (≤2 GB), half      → 3  (tick every third vsync)
    //
    // quarterRefreshRate is ignored when nativeRefreshRate is true.
    //
    // Example results at any display Hz:
    //   120Hz display, native   → 120Hz physics, 8.3ms  render interval
    //   120Hz display, half     → 60Hz  physics, 16.7ms render interval
    //   120Hz display, quarter  → 30Hz  physics, 33.3ms render interval
    //   90Hz  display, native   → 90Hz  physics, 11.1ms render interval
    //   90Hz  display, half     → 45Hz  physics, 22.2ms render interval
    //   90Hz  display, quarter  → 22Hz  physics, 44.4ms render interval
    //   60Hz  display, quarter  → 15Hz  physics, 66.7ms render interval
    val vsyncDivisor: Int = when {
        nativeRefreshRate  -> 1
        quarterRefreshRate -> 4
        else               -> 2
    }

    // Physics target Hz: display rate divided by divisor.
    // Clamped so extreme displays (24Hz film, 240Hz gaming) stay sane.
    val physicsHz: Long = (displayHz / vsyncDivisor).toLong().coerceIn(8L, 144L)

    // Render interval in nanoseconds.
    // At 90Hz + divisor 2: 2 * 1e9 / 90 = 22.2ms. Always correct, any Hz.
    val renderIntervalNs: Long = (vsyncDivisor * 1_000_000_000L / displayHz).toLong()

    // ── Physics loop — Dispatchers.Default (background thread) ───────────────
    //
    // Ticks at physicsHz derived from the actual display rate above.
    // MAX_DELTA clamp prevents position jumps if the thread wakes late.
    LaunchedEffect(particles, enabled, physicsHz) {
        if (!enabled) return@LaunchedEffect

        withContext(Dispatchers.Default) {
            val TARGET_NS = 1_000_000_000L / physicsHz
            val MAX_DELTA = 1f / physicsHz.toFloat()
            var lastPhysicsNs = 0L

            while (isActive) {
                val now     = System.nanoTime()
                val elapsed = if (lastPhysicsNs == 0L) TARGET_NS else now - lastPhysicsNs

                if (elapsed >= TARGET_NS) {
                    val delta = (elapsed / 1_000_000_000f).coerceAtMost(MAX_DELTA)
                    lastPhysicsNs = now

                    // Pre-compute damping ONCE per tick instead of inside every particle.
                    // Math.pow is a JNI transcendental — at 300 particles × 60Hz this saves
                    // ~18,000 Math.pow() calls per second with zero visual difference.
                    val tickDamping = Math.pow(0.94, (delta * 60.0)).toFloat()
                    val tickDt60 = delta * 60f

                    // Read the volatile snapshot once (one memory barrier for the batch)
                    val inp  = physicsInputs
                    val spd  = inp.speed
                    val rotX = inp.rotX
                    val rotY = inp.rotY
                    val sens = inp.sens

                    // Pre-compute per-tick constants that are uniform across all particles
                    val tickMaxVelocity = 15f * spd
                    // star/timeMode/daytime removed — no longer passed to update()

                    particles.forEach { p ->
                        p.update(spd, rotX, rotY, delta, sens, tickDamping, tickMaxVelocity, tickDt60)
                    }
                }

                // Sleep until the next physics tick
                val sleepMs = maxOf(1L, (TARGET_NS - (System.nanoTime() - lastPhysicsNs)) / 1_000_000L)
                delay(sleepMs)
            }
        }
    }

    // ── Render trigger — main thread ──────────────────────────────────────────
    //
    // withFrameNanos fires at the display's native refresh rate. We compare
    // elapsed time against renderIntervalNs to skip vsyncs we don't need,
    // matching canvas redraws to the physics tick rate exactly.
    // Because renderIntervalNs flows from displayHz, this works correctly
    // for any refresh rate without any special-casing.
    LaunchedEffect(enabled, renderIntervalNs, timeOffsetHours) {
        if (!enabled) return@LaunchedEffect

        val TARGET_RENDER_NS = renderIntervalNs
        var lastRenderNs     = 0L

        while (isActive) {
            withFrameNanos { time ->
                if (lastRenderNs > 0L && time - lastRenderNs < TARGET_RENDER_NS) {
                    return@withFrameNanos
                }
                lastRenderNs = time

                // Update once-per-second state (celestial position + daytime flag).
                // isDaytime() calls Calendar.getInstance() + trig — run it at most
                // once per second inside this gate instead of every render frame.
                val nowSec = time / 1_000_000_000L
                if (nowSec != celestialTickSecond) {
                    celestialTickSecond = nowSec
                    frameIsDaytime = isDaytime(timeOffsetHours)
                }

                // Invalidate Canvas — particles have moved since the last render
                frameCount++
            }
        }
    }

    // Animated alpha for celestial objects (sun/moon) with smooth ease-in-out fade
    val celestialAlpha by animateFloatAsState(
        targetValue = if (timeModeEnabled) 1f else 0f,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "celestial_alpha"
    )

    // remember blocks MUST be called unconditionally (Compose rules), so they live
    // outside the particleAlpha > 0.01f guard below.
    // Reusable paths and Paint objects — allocated once, never recreated.
    //
    // ── Star batching paths ───────────────────────────────────────────────────
    // Previously: ONE reusableStarPath was reset() + rebuilt with 8 lineTo calls
    // per particle per frame, then drawPath() was called 150× per frame.
    // GPU has to tessellate each star polygon separately — 150 draw calls/frame.
    //
    // Now: TWO batch paths accumulate ALL star geometry for all alpha buckets,
    // then drawPath() is called ONCE per alpha bucket (2 calls total).
    // GPU tessellates one combined path — massively cheaper.
    //
    // Alpha bucketing: stars vary in alpha per particle. We can't batch them all
    // into one drawPath call with a single color. The fix: quantize alpha into
    // N buckets and draw one batched path per bucket. 8 buckets covers the full
    // [0,1] range with steps of 0.125 — visually indistinguishable from per-particle
    // alpha, but reduces draw calls from 150 to ≤ 8.
    // ── Star line-draw buffers ────────────────────────────────────────────────
    // Stars are no longer drawn as filled concave polygons via Compose drawPath.
    // Instead each star is 4 line segments (H + V + 2 diagonals) with ROUND caps,
    // batched into a FloatArray per alpha bucket and dispatched as a single
    // nativeCanvas.drawLines() call — a direct GPU primitive with zero tessellation.
    //
    // Why this matters:
    //   OLD: Compose drawPath(concave polygon) → CPU ear-clip tessellation per frame
    //        = 3000+ path ops + 16 draw calls + full tessellation overhead
    //   NEW: nativeCanvas.drawLines(FloatArray) → GPU line primitive, no tessellation
    //        = ≤8 native draw calls regardless of particle count
    //
    // Each star needs 4 lines × 4 floats (x1,y1,x2,y2) = 16 floats.
    // Pre-allocate worst-case: actualParticleCount × 16 floats per bucket.
    val starLineBuffers = remember(actualParticleCount) {
        Array(STAR_ALPHA_BUCKETS) { FloatArray(actualParticleCount * 16) }
    }
    val starLineCounts  = remember { IntArray(STAR_ALPHA_BUCKETS) }
    val nativeStarPaint = remember {
        android.graphics.Paint().apply {
            // No ANTI_ALIAS_FLAG — GPU anti-aliasing on 600 line endpoints per frame
            // is expensive. At star sizes of 2–14px the difference is invisible.
            // SQUARE caps replace ROUND: no sub-pixel rounding at each endpoint,
            // so the GPU treats lines as simple rasterised rectangles — much cheaper.
            // The ✦ sparkle shape is fully preserved; only the 1px cap corners differ.
            style      = android.graphics.Paint.Style.STROKE
            strokeCap  = android.graphics.Paint.Cap.SQUARE
            strokeJoin = android.graphics.Paint.Join.MITER
            strokeWidth = 2.5f
        }
    }
    // Star style is now rendered as tiny round points instead of star polygons/lines.
    // This keeps the brighter "stars" look, but uses nativeCanvas.drawPoints() with
    // alpha + size buckets, avoiding Path tessellation and hundreds of tiny ovals.
    val starDotPointBuffers = remember(actualParticleCount) {
        Array(STAR_ALPHA_BUCKETS * STAR_DOT_SIZE_BUCKETS) { FloatArray(actualParticleCount * 2) }
    }
    val starDotPointCounts = remember { IntArray(STAR_ALPHA_BUCKETS * STAR_DOT_SIZE_BUCKETS) }
    val nativeStarDotPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
        }
    }
    // circleBatchPaths still used for circle (non-star) particle mode
    val circleBatchPaths = remember { Array(STAR_ALPHA_BUCKETS) { Path() } }
    val reusableRayPath   = remember { Path() }
    val reusableFullDisc  = remember { android.graphics.Path() }
    val reusableBitePath  = remember { android.graphics.Path() }
    val reusableCrescent  = remember { android.graphics.Path() }

    // Reusable Paint objects for the moon crescent — previously allocated fresh
    // inside drawIntoCanvas on every draw frame (60-120x/sec), each constructing
    // a new Paint + RadialGradient shader.  Cached here; the shader is rebuilt
    // inside the draw block only when moonCenter/moonR/color/alpha actually change.
    val reusableGradPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    val reusableRimPaint  = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
        }
    }
    // Cache the last BlurMaskFilter radius so we only reconstruct it when moonR
    // actually changes (i.e. never at runtime — moonR is constant per celestial state).
    // BlurMaskFilter allocates a native object; rebuilding it every draw frame at
    // 30fps was the single biggest GC source on older JIT-based devices.
    var cachedBlurRadius  = remember { -1f }
    var cachedBlurFilter  = remember<android.graphics.BlurMaskFilter?> { null }
    // Cached alpha for the moon's RadialGradient — only rebuild shader when alpha changes.
    // effectiveAlpha is stable most of the time (only changes during fade-in/out transitions),
    // so this skips the shader allocation entirely during steady-state rendering.
    var cachedMoonGradAlphaInt = remember { -1 }

    // Crater data: constant fractions of moonR — list allocated once, not per frame.
    val craterData = remember {
        listOf(
            Triple(-0.30f, -0.20f, 0.09f),
            Triple(-0.20f,  0.28f, 0.07f),
            Triple(-0.42f,  0.08f, 0.06f)
        )
    }

    if (particleAlpha > 0.01f) {
        // Pre-compute base RGB int (alpha stripped) so we can reconstruct any alpha
        // variant with Color((alpha shl 24) or colorArgb) inside the draw loop —
        // avoiding color.copy(alpha=…) which allocates a new Color object per call.
        // At 150 particles × 120fps this saves ~18,000 allocations/sec.
        val colorArgb = renderedParticleColor.toArgb() and 0x00FFFFFF

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // Use graphicsLayer alpha instead of Modifier.alpha() — graphicsLayer
                // composites on the GPU without an extra offscreen render pass.
                .graphicsLayer(alpha = particleAlpha)
        ) {
            // Reading frameCount tells Compose this Canvas depends on it,
            // so it redraws every time the physics loop increments it.
            @Suppress("UNUSED_VARIABLE") val frame = frameCount
            val useStarDots = starMode || (timeModeEnabled && !frameIsDaytime)
            if (useStarDots) {
                // Fast star style: bright tiny circles, rendered like a matrix-style primitive.
                // drawPoints() avoids per-particle Paths/Ovals and keeps the GPU pipeline simple.
                for (i in 0 until STAR_ALPHA_BUCKETS * STAR_DOT_SIZE_BUCKETS) {
                    starDotPointCounts[i] = 0
                }

                particles.forEach { particle ->
                    val a = particle.baseStarAlpha * particleAlpha
                    if (a < 0.008f) return@forEach

                    val alphaBucket = (a * (STAR_ALPHA_BUCKETS - 1) + 0.5f).toInt()
                        .coerceIn(0, STAR_ALPHA_BUCKETS - 1)
                    val sizeBucket = when {
                        particle.size < 2.8f -> 0
                        particle.size < 5.2f -> 1
                        else -> 2
                    }
                    val bucket = alphaBucket * STAR_DOT_SIZE_BUCKETS + sizeBucket
                    val buf = starDotPointBuffers[bucket]
                    var idx = starDotPointCounts[bucket]

                    buf[idx] = size.width * particle.x
                    buf[idx + 1] = size.height * particle.y
                    starDotPointCounts[bucket] = idx + 2
                }

                val nc = drawContext.canvas.nativeCanvas
                nativeStarDotPaint.color = colorArgb

                for (alphaBucket in 0 until STAR_ALPHA_BUCKETS) {
                    nativeStarDotPaint.alpha = when (alphaBucket) {
                        0 -> 72
                        1 -> 132
                        2 -> 204
                        else -> 255
                    }

                    for (sizeBucket in 0 until STAR_DOT_SIZE_BUCKETS) {
                        val bucket = alphaBucket * STAR_DOT_SIZE_BUCKETS + sizeBucket
                        val count = starDotPointCounts[bucket]
                        if (count == 0) continue

                        nativeStarDotPaint.strokeWidth = when (sizeBucket) {
                            0 -> 3.2f
                            1 -> 5.4f
                            else -> 7.6f
                        }
                        nc.drawPoints(starDotPointBuffers[bucket], 0, count, nativeStarDotPaint)
                    }
                }
            } else {
                // Circle mode — batch into alpha buckets exactly like star mode.
                // Reduces N draw calls (one per particle) to ≤ STAR_ALPHA_BUCKETS GPU draw calls.
                for (i in 0 until STAR_ALPHA_BUCKETS) circleBatchPaths[i].reset()

                particles.forEach { particle ->
                    val a = particle.baseStarAlpha * (5f / 6f) * particleAlpha
                    if (a < 0.008f) return@forEach
                    val bucket = (a * (STAR_ALPHA_BUCKETS - 1) + 0.5f).toInt()
                        .coerceIn(0, STAR_ALPHA_BUCKETS - 1)
                    val cx = size.width  * particle.x
                    val cy = size.height * particle.y
                    val r  = particle.size
                    circleBatchPaths[bucket].addOval(
                        androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r)
                    )
                }

                for (bucket in 0 until STAR_ALPHA_BUCKETS) {
                    if (circleBatchPaths[bucket].isEmpty) continue
                    val aInt = when (bucket) {
                        0    -> (0x00 shl 24) or colorArgb
                        1    -> (0x55 shl 24) or colorArgb
                        2    -> (0xAA shl 24) or colorArgb
                        else -> (0xFF shl 24) or colorArgb
                    }
                    drawPath(path = circleBatchPaths[bucket], color = Color(aInt))
                }
            }

        }

        // Celestial objects (sun/moon) — drawn in two layers so blur can cross-
        // fade smoothly when panels open/close without a jarring snap.
        //
        //  Layer 1 (sharp):  graphicsLayer alpha = celestialAlpha * (1 − blurAlpha)
        //  Layer 2 (blurred): graphicsLayer alpha = celestialAlpha * blurAlpha
        //
        // Both layers share identical draw code via the celestialDrawContent lambda.
        if (timeModeEnabled && celestialAlpha > 0.01f) {

            // Shared draw logic — called once per active layer
            val drawCelestial: DrawScope.() -> Unit = drawLambda@{
                @Suppress("UNUSED_VARIABLE") val frame = frameCount
                val cel = celestialState ?: return@drawLambda
                val adjustedX = if (isLandscape) cel.x * 0.5f else cel.x
                val isSun = frameIsDaytime
                // Fully opaque — celestialAlpha only covers the enable/panel fade.
                val effectiveAlpha = celestialAlpha

                // Pre-compute base RGB int (alpha stripped) — same technique as the
                // particle draw loop. The sun and moon are always 100% opaque and
                // exactly the accent colour.
                val colorRgb = renderedCelestialColor.toArgb() and 0x00FFFFFF
                // Helper: assemble a Color from a pre-stripped RGB int + float alpha [0,1]
                fun colorWithAlpha(alpha: Float): Color =
                    Color((((alpha * 255f + 0.5f).toInt().coerceIn(0, 255) shl 24) or colorRgb))

                    if (isSun) {
                        // Draw beautiful sun with rays
                        val sunCenter = Offset(adjustedX, cel.y)

                        // Draw sun rays — all 8 accumulated into reusableRayPath, then ONE drawPath call.
                        // Previously: 8 separate drawPath calls (reset + draw per ray).
                        val numRays = 8
                        val rayLength = cel.size * 1.8f
                        val rayWidth = cel.size * 0.18f

                        reusableRayPath.reset()  // single reset before all 8 rays
                        for (i in 0 until numRays) {
                            val angle = (i * 2 * PI / numRays).toFloat()
                            val cosA = cos(angle)
                            val sinA = sin(angle)
                            val rayStart = Offset(
                                sunCenter.x + cosA * cel.size * 1.1f,
                                sunCenter.y + sinA * cel.size * 1.1f
                            )
                            val rayEnd = Offset(
                                sunCenter.x + cosA * rayLength,
                                sunCenter.y + sinA * rayLength
                            )

                            val perpAngle = angle + (PI / 2).toFloat()
                            val cosPa = cos(perpAngle)
                            val sinPa = sin(perpAngle)
                            val baseWidth = rayWidth
                            val tipWidth = rayWidth * 0.3f
                            reusableRayPath.moveTo(
                                rayStart.x + cosPa * baseWidth,
                                rayStart.y + sinPa * baseWidth
                            )
                            reusableRayPath.lineTo(
                                rayEnd.x + cosPa * tipWidth,
                                rayEnd.y + sinPa * tipWidth
                            )
                            reusableRayPath.lineTo(
                                rayEnd.x - cosPa * tipWidth,
                                rayEnd.y - sinPa * tipWidth
                            )
                            reusableRayPath.lineTo(
                                rayStart.x - cosPa * baseWidth,
                                rayStart.y - sinPa * baseWidth
                            )
                            reusableRayPath.close()
                        }
                        // Single draw call for all 8 rays
                        drawPath(
                            path = reusableRayPath,
                            color = colorWithAlpha(effectiveAlpha)
                        )

                        // Draw main sun body
                        drawCircle(
                            color = colorWithAlpha(effectiveAlpha),
                            radius = cel.size,
                            center = sunCenter
                        )

                        // Draw bright core
                        drawCircle(
                            color = colorWithAlpha(effectiveAlpha),
                            radius = cel.size * 0.6f,
                            center = sunCenter
                        )
                    } else {
                        // Draw a proper crescent moon using canvas path clipping
                        val moonCenter = Offset(adjustedX, cel.y)
                        val moonR = cel.size * 2f
                        val biteR = moonR * 0.82f
                        val biteCenter = Offset(moonCenter.x + moonR * 0.48f, moonCenter.y - moonR * 0.12f)

                        // Draw crescent using drawIntoCanvas with native path clipping
                        drawIntoCanvas { canvas ->
                            val nCanvas = canvas.nativeCanvas
                            nCanvas.save()

                            reusableFullDisc.reset()
                            reusableFullDisc.addCircle(moonCenter.x, moonCenter.y, moonR, android.graphics.Path.Direction.CW)

                            reusableBitePath.reset()
                            reusableBitePath.addCircle(biteCenter.x, biteCenter.y, biteR, android.graphics.Path.Direction.CW)

                            reusableCrescent.reset()
                            reusableCrescent.op(reusableFullDisc, reusableBitePath, android.graphics.Path.Op.DIFFERENCE)

                            nCanvas.clipPath(reusableCrescent)

                            // Fill the crescent — rebuild RadialGradient only when effectiveAlpha
                            // changes meaningfully. At steady state alpha is stable → zero rebuilds.
                            // Previously allocated a new native shader object every frame.
                            val alphaInt255 = (effectiveAlpha * 255f + 0.5f).toInt().coerceIn(0, 255)
                            if (alphaInt255 != cachedMoonGradAlphaInt) {
                                cachedMoonGradAlphaInt = alphaInt255
                                reusableGradPaint.shader = android.graphics.RadialGradient(
                                    moonCenter.x - moonR * 0.2f,
                                    moonCenter.y - moonR * 0.2f,
                                    moonR * 1.1f,
                                    intArrayOf(
                                        colorWithAlpha(effectiveAlpha).toArgb(),
                                        colorWithAlpha(effectiveAlpha).toArgb()
                                    ),
                                    floatArrayOf(0f, 1f),
                                    android.graphics.Shader.TileMode.CLAMP
                                )
                            }
                            nCanvas.drawPath(reusableCrescent, reusableGradPaint)

                            // Rim glow — reuse cached Paint, update stroke/color for this frame.
                            // BlurMaskFilter is only rebuilt when moonR changes (never at runtime).
                            reusableRimPaint.strokeWidth = moonR * 0.06f
                            reusableRimPaint.color = colorWithAlpha(effectiveAlpha).toArgb()
                            val blurRadiusPx = moonR * 0.12f
                            if (blurRadiusPx != cachedBlurRadius) {
                                cachedBlurRadius = blurRadiusPx
                                cachedBlurFilter = android.graphics.BlurMaskFilter(
                                    blurRadiusPx, android.graphics.BlurMaskFilter.Blur.NORMAL
                                )
                            }
                            reusableRimPaint.maskFilter = cachedBlurFilter
                            nCanvas.drawPath(reusableCrescent, reusableRimPaint)

                            nCanvas.restore()
                        }

                        // 3. Small craters
                        craterData.forEach { (offsetX, offsetY, craterSize) ->
                            val craterCenter = Offset(
                                moonCenter.x + moonR * offsetX,
                                moonCenter.y + moonR * offsetY
                            )
                            drawCircle(
                                color = Color(((effectiveAlpha * 0.18f * 255f + 0.5f).toInt().coerceIn(0,255) shl 24) or 0x000000),
                                radius = moonR * craterSize,
                                center = craterCenter
                            )
                            drawCircle(
                                color = colorWithAlpha(effectiveAlpha * 0.25f),
                                radius = moonR * craterSize * 0.7f,
                                center = Offset(craterCenter.x - moonR * craterSize * 0.15f, craterCenter.y - moonR * craterSize * 0.15f)
                            )
                        }
                    } // end else (moon)
            } // end drawCelestial lambda

            // Single Canvas. The blur radius comes straight from GamaUI's animated
            // mainMenuBlurRadius (0.dp ↔ 20.dp, same tween), so the sun/moon de-blur
            // in perfect lock-step with the text behind them — no crossfade ghosting.
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = celestialAlpha)
                    .then(
                        if (blurRadius > 0.dp && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                            Modifier.blur(blurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                        else Modifier
                    )
            ) { drawCelestial() }
        } // end if timeModeEnabled
    }
}


