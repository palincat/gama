package com.popovicialinc.gama

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.ClipData
import android.content.ClipDescription
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
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
// Integrations: Tasker and Quick Settings panels + shared cards
// ============================================================

@Composable
fun IntegrationsPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    onLinkSelected: (url: String, label: String, description: String) -> Unit,
    onInfoRequested: (title: String, body: String) -> Unit,
    isBlurred: Boolean = false,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    oledMode: Boolean = false
) {
    val ts = LocalTypeScale.current

    // Hoisted above BouncyDialog so they exist before enter animation starts
    val scrollState = rememberScrollState()
    val backPadding by animateDpAsState(
        targetValue = if (visible) (if (isSmallScreen) 72.dp else 84.dp) else 0.dp,
        animationSpec = tween(durationMillis = 500, easing = MotionTokens.Easing.emphasized),
        label = "integrations_back_padding"
    )

    BouncyDialog(visible = visible, onDismiss = onDismiss, fullScreen = true) {
        val animLevel = LocalAnimationLevel.current
        val dismissOnClickOutside = LocalDismissOnClickOutside.current

        val blurAmount by animateDpAsState(
            targetValue = if (isBlurred) 20.dp else 0.dp,
            animationSpec = if (animLevel == 2) snap<Dp>() else tween(durationMillis = 400, easing = FastOutSlowInEasing),
            label = "integrations_blur"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (blurAmount > 0.dp) Modifier.blur(blurAmount) else Modifier)
                .pointerInput(Unit) {
                    if (dismissOnClickOutside) detectTapGestures { onDismiss() }
                    else detectTapGestures { }
                },
            contentAlignment = Alignment.Center
        ) {

            Column(
                modifier = Modifier
                    .widthIn(max = if (isLandscape) 800.dp else 500.dp)
                    .verticalScroll(scrollState)
                    .padding(horizontal = if (isLandscape) 32.dp else 24.dp)
                    .padding(bottom = backPadding)
                    .pointerInput(Unit) { detectTapGestures { } },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 16.dp else 20.dp)
            ) {
                Spacer(modifier = Modifier.height(if (isLandscape) 24.dp else 40.dp))

                CleanTitle(
                    text = LocalStrings.current["integrations.title"].ifEmpty { "INTEGRATIONS" },
                    fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge,
                    colors = colors,
                    reverseGradient = false,
                    scrollState = scrollState
                )

                Text(
                    text = LocalStrings.current["integrations.subtitle"].ifEmpty { "Plug GAMA into your existing Android automations and shortcuts" },
                    fontSize = ts.labelLarge,
                    color = colors.textSecondary,
                    fontFamily = quicksandFontFamily,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )

                // ── Tasker ──────────────────────────────────────────────────
                AnimatedElement(visible = visible, staggerIndex = 1, totalItems = 3) {
                    if (isLandscape) {
                        // Two-column: TASKER | QUICK SETTINGS TILES
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                IntegrationInfoCard(
                                    title = LocalStrings.current["integrations.tasker"].ifEmpty { "TASKER" },
                                    description = LocalStrings.current["integrations.tasker_desc"].ifEmpty { "Use broadcast intents to switch renderers automatically based on time, app launch, WiFi, or anything Tasker can do" },
                                    statusLabel = "Available",
                                    statusOk = true,
                                    actionLabel = "Open Guide",
                                    onAction = {
                                        onLinkSelected(
                                            "https://github.com/popovicialinc/gama/blob/main/!assets/GAMA_Tasker_Guide.pdf",
                                            "Tasker Guide",
                                            "This will open the GAMA Tasker integration guide on GitHub. It covers how to use broadcast intents to automate renderer switching based on time, app launch, WiFi network, and more."
                                        )
                                    },
                                    colors = colors,
                                    cardBackground = cardBackground,
                                    oledMode = oledMode,
                                    isSmallScreen = isSmallScreen
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                val tileAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                                IntegrationInfoCard(
                                    title = LocalStrings.current["integrations.qs_tiles"].ifEmpty { "QUICK SETTINGS TILES" },
                                    description = LocalStrings.current["integrations.qs_tiles_desc"].ifEmpty { "One tile that toggles between Vulkan and OpenGL — tap to switch, the subtitle shows the current renderer" },
                                    statusLabel = if (tileAvailable) "1 tile" else "Requires Android 7+",
                                    statusOk = tileAvailable,
                                    actionLabel = if (tileAvailable) "How to add" else null,
                                    onAction = if (tileAvailable) ({
                                        onInfoRequested(
                                            "Adding QS Tiles",
                                            "Pull down your notification shade and tap the Edit button (pencil icon). Scroll through the available tiles until you find the GAMA tile. Drag it into your active area, then tap Done. Tap the tile to switch between Vulkan and OpenGL; its subtitle shows the current renderer."
                                        )
                                    }) else null,
                                    colors = colors,
                                    cardBackground = cardBackground,
                                    oledMode = oledMode,
                                    isSmallScreen = isSmallScreen
                                )
                            }
                        }
                    } else {
                        IntegrationInfoCard(
                            title = LocalStrings.current["integrations.tasker"].ifEmpty { "TASKER" },
                            description = LocalStrings.current["integrations.tasker_desc"].ifEmpty { "Use broadcast intents to switch renderers automatically based on time, app launch, WiFi, or anything Tasker can do" },
                            statusLabel = "Available",
                            statusOk = true,
                            actionLabel = "Open Guide",
                            onAction = {
                                onLinkSelected(
                                    "https://github.com/popovicialinc/gama/blob/main/!assets/GAMA_Tasker_Guide.pdf",
                                    "Tasker Guide",
                                    "This will open the GAMA Tasker integration guide on GitHub. It covers how to use broadcast intents to automate renderer switching based on time, app launch, WiFi network, and more."
                                )
                            },
                            colors = colors,
                            cardBackground = cardBackground,
                            oledMode = oledMode,
                            isSmallScreen = isSmallScreen
                        )
                    }
                }

                // ── Quick Settings Tile ─────────────────────────────────────
                AnimatedElement(visible = visible, staggerIndex = 2, totalItems = 3) {
                    if (!isLandscape) {
                        val tileAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                        IntegrationInfoCard(
                            title = LocalStrings.current["integrations.qs_tiles"].ifEmpty { "QUICK SETTINGS TILES" },
                            description = LocalStrings.current["integrations.qs_tiles_desc"].ifEmpty { "One tile that toggles between Vulkan and OpenGL — tap to switch, the subtitle shows the current renderer" },
                            statusLabel = if (tileAvailable) "1 tile" else "Requires Android 7+",
                            statusOk = tileAvailable,
                            actionLabel = if (tileAvailable) "How to add" else null,
                            onAction = if (tileAvailable) ({
                                onInfoRequested(
                                    "Adding QS Tiles",
                                    "Pull down your notification shade and tap the Edit button (pencil icon). Scroll through the available tiles until you find the GAMA tile. Drag it into your active area, then tap Done. Tap the tile to switch between Vulkan and OpenGL; its subtitle shows the current renderer."
                                )
                            }) else null,
                            colors = colors,
                            cardBackground = cardBackground,
                            oledMode = oledMode,
                            isSmallScreen = isSmallScreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            PanelBackButton(
                onClick = onDismiss,
                colors = colors,
                oledMode = oledMode,
                isSmallScreen = isSmallScreen,
                scrollState = scrollState,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 28.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// IntegrationInfoDialog — info-only popup for integration instructions
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun IntegrationInfoDialog(
    visible: Boolean,
    title: String,
    body: String,
    copyText: String? = null,
    guideUrl: String? = null,
    onDismiss: () -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color
) {
    val ts = LocalTypeScale.current
    val context = LocalContext.current
    val dialogPadding = when {
        isLandscape -> 16.dp
        isSmallScreen -> 22.dp
        else -> 30.dp
    }
    val itemSpacing = when {
        isLandscape -> 10.dp
        isSmallScreen -> 18.dp
        else -> 22.dp
    }

    BouncyDialog(visible = visible, onDismiss = onDismiss) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(if (isLandscape && !isTablet) 0.64f else 0.9f)
                    .widthIn(max = 540.dp)
                    .heightIn(max = maxHeight * 0.90f)
                    .border(0.75.dp, colors.primaryAccent.copy(alpha = 0.3f), RoundedCornerShape(28.dp))
                    .pointerInput(Unit) { detectTapGestures { } },
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(dialogPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(itemSpacing)
                ) {
                Text(
                    text = title,
                    fontSize = if (isLandscape) ts.headlineMedium else ts.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    fontFamily = quicksandFontFamily,
                    color = colors.primaryAccent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = body,
                    fontSize = if (isLandscape) ts.bodyMedium else ts.bodyLarge,
                    lineHeight = ((if (isLandscape) ts.bodyMedium else ts.bodyLarge).value * 1.35f).sp,
                    color = colors.textPrimary.copy(alpha = 0.85f),
                    fontFamily = quicksandFontFamily,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                if (copyText != null) {
                    DialogButton(
                        text = LocalStrings.current["integrations.copy_token"].ifEmpty { "Copy token" },
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                            val clip = ClipData.newPlainText(
                                localizedString(context, "integrations", "tasker_token_label", "GAMA Tasker token"),
                                copyText
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                clip.description.extras = PersistableBundle().apply {
                                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                                }
                            }
                            clipboard.setPrimaryClip(clip)
                            android.widget.Toast.makeText(
                                context,
                                localizedString(context, "integrations", "token_copied", "Tasker token copied"),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = colors,
                        cardBackground = cardBackground,
                        accent = true
                    )
                }
                if (guideUrl != null) {
                    DialogButton(
                        text = LocalStrings.current["integrations.open_full_guide"].ifEmpty { "Open full guide" },
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(guideUrl)))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = colors,
                        cardBackground = cardBackground
                    )
                }
                DialogButton(
                    text = LocalStrings.current["dialogs.btn_close"].ifEmpty { "Close" },
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = colors,
                    cardBackground = cardBackground
                )
                }
            }
        }
    }
}

@Composable
fun IntegrationInfoCard(
    title: String,
    description: String,
    statusLabel: String,
    statusOk: Boolean,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    colors: ThemeColors,
    cardBackground: Color,
    oledMode: Boolean,
    isSmallScreen: Boolean
) {
    val ts = LocalTypeScale.current
    var isPressed by remember { mutableStateOf(false) }
    val enabled = onAction != null

    // Keep this outline mathematically identical to SettingsNavigationCard,
    // so TASKER / QUICK SETTINGS TILES / HOME SCREEN WIDGET match
    // GITHUB / DISCORD / SHIZUKU in color, thickness, radius, and press behavior.
    val pressProgress = remember { Animatable(0f) }
    val animLevel = LocalAnimationLevel.current

    LaunchedEffect(isPressed, enabled) {
        val target = if (isPressed && enabled) 1f else 0f
        if (animLevel == 2) {
            pressProgress.snapTo(target)
        } else {
            pressProgress.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
                    stiffness = if (isPressed) MotionTokens.Springs.pressDown.stiffness else MotionTokens.Springs.pressUp.stiffness
                )
            )
        }
    }

    val p = pressProgress.value
    val pressScale = 1f - p * (1f - MotionTokens.Scale.subtle)
    val baseBorderWidth = if (oledMode) 0.75f else 1f
    val cardBorderWidth = (baseBorderWidth + p * baseBorderWidth).dp
    val cardBorderColor = if (isPressed && enabled) {
        colors.primaryAccent
    } else {
        colors.primaryAccent.copy(alpha = 0.55f)
    }
    val statusColor = if (statusOk) colors.successColor else Color(0xFFF59E0B)
    val minCardHeight = if (LocalConfiguration.current.screenWidthDp.dp < 360.dp) 72.dp else 80.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minCardHeight)
            .graphicsLayer(
                scaleX = pressScale,
                scaleY = pressScale,
                clip = false
            )
            .pressedAccentOutlineGlow(
                pressProgress = p,
                color = colors.primaryAccent,
                cornerRadius = 28.dp,
                strokeWidth = cardBorderWidth,
                glowRadius = 11.dp
            )
            .border(
                width = cardBorderWidth,
                color = cardBorderColor,
                shape = RoundedCornerShape(28.dp)
            )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minCardHeight)
                .then(
                    if (enabled) {
                        Modifier.pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isPressed = true
                                    val released = tryAwaitRelease()
                                    isPressed = false
                                    if (released) onAction?.invoke()
                                }
                            )
                        }
                    } else {
                        Modifier
                    }
                ),
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minCardHeight)
                    .background(colors.primaryAccent.copy(alpha = if (enabled) p * 0.10f else 0f))
                    .padding(if (isSmallScreen) 20.dp else 24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        color = colors.primaryAccent.copy(alpha = 0.7f),
                        fontSize = ts.labelLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        fontFamily = quicksandFontFamily,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Box(
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = statusLabel,
                            color = statusColor,
                            fontSize = ts.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = quicksandFontFamily,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = description,
                    color = colors.textSecondary,
                    fontSize = ts.bodyMedium,
                    fontFamily = quicksandFontFamily,
                    fontWeight = FontWeight.Bold,
                    lineHeight = (ts.bodyMedium.value * 1.4f).sp
                )

                if (actionLabel != null && onAction != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "$actionLabel  →",
                            color = colors.primaryAccent,
                            fontSize = ts.labelMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = quicksandFontFamily
                        )
                    }
                }
            }
        }
    }
}
