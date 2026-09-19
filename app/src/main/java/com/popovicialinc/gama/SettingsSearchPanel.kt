package com.popovicialinc.gama


import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt


internal class SettingsSearchItem(
    val id: String,
    val title: String,
    val keywords: List<String>,
    val path: String,         // breadcrumb path shown above the card, outside the shadow
    val enabledForShadow: () -> Boolean = { true },
    val render: @Composable () -> Unit
) {
    // Pre-computed once at construction — never rebuilt during scoring.
    val haystack: String = (listOf(id, title) + keywords).joinToString(" ").lowercase()
    val words: List<String> = haystack.split(nonAlphaNumRegex).filter { it.isNotBlank() }

    companion object {
        private val nonAlphaNumRegex = Regex("[^a-z0-9]+")
    }
}

internal fun levenshteinDistance(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length

    val previous = IntArray(b.length + 1) { it }
    val current = IntArray(b.length + 1)

    for (i in a.indices) {
        current[0] = i + 1
        for (j in b.indices) {
            val cost = if (a[i] == b[j]) 0 else 1
            current[j + 1] = minOf(
                current[j] + 1,
                previous[j + 1] + 1,
                previous[j] + cost
            )
        }
        for (j in previous.indices) previous[j] = current[j]
    }

    return previous[b.length]
}

internal fun settingsSearchScore(q: String, queryTokens: List<String>, item: SettingsSearchItem): Int {
    if (q.isBlank()) return 0

    if (item.haystack.contains(q)) return 120

    var best = 0
    for (token in queryTokens) {
        for (word in item.words) {
            if (word == token) {
                best = maxOf(best, 115); continue
            }
            if (word.startsWith(token)) best = maxOf(best, 96)
            else if (word.contains(token)) best = maxOf(best, 82)

            if (token.length >= 3 && word.length >= 3 && word[0] == token[0] && word[1] == token[1] && word[2] == token[2]) {
                best = maxOf(best, 72)
            } else if (token.length >= 2 && word.length >= 2 && word[0] == token[0] && word[1] == token[1]) {
                best = maxOf(best, 54)
            }

            val distance = levenshteinDistance(token, word)
            val maxLen = maxOf(token.length, word.length).coerceAtLeast(1)
            best = maxOf(best, ((1f - distance.toFloat() / maxLen) * 82).toInt())
        }
    }

    return best
}

@Composable
internal fun AnimatedSearchPanelTitle(
    titleKey: String,
    text: String,
    visible: Boolean,
    fontSize: androidx.compose.ui.unit.TextUnit,
    colors: ThemeColors,
    scrollState: androidx.compose.foundation.ScrollState?,
    modifier: Modifier = Modifier
) {
    // Key the title by its logical panel, not by the shared Search/Global
    // composable. This prevents the old SEARCH title from rendering for one
    // frame inside GLOBAL while the new title animation starts.
    key(titleKey) {
        val density = LocalDensity.current
        val animationLevel = LocalAnimationLevel.current
        val animSpeed = LocalAnimationSpeed.current
        val progress = remember { Animatable(if (visible) 0f else 0f) }
        val offsetYPx = with(density) {
            when (animationLevel) {
                0 -> 20.dp.toPx()
                1 -> 8.dp.toPx()
                else -> 0.dp.toPx()
            }
        }

        LaunchedEffect(visible, titleKey, text) {
            if (visible) {
                progress.snapTo(0f)
                if (animationLevel == 2) {
                    progress.snapTo(1f)
                } else {
                    progress.animateTo(
                        targetValue = 1f,
                        animationSpec = if (animationLevel == 0)
                            spring(dampingRatio = 0.55f, stiffness = MotionTokens.SpeedUtil.stiffness(260f, animSpeed))
                        else
                            tween(
                                durationMillis = MotionTokens.SpeedUtil.durationMs(210, animSpeed),
                                easing = MotionTokens.Easing.emphasizedDecelerate
                            )
                    )
                }
            } else {
                if (animationLevel == 2) {
                    progress.snapTo(0f)
                } else {
                    progress.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = MotionTokens.SpeedUtil.durationMs(85, animSpeed),
                            easing = MotionTokens.Easing.exit
                        )
                    )
                }
            }
        }

        Box(
            modifier = modifier.graphicsLayer {
                // Keep the spring's raw value for scale so the title retains
                // the same subtle overshoot/bounce as standard AnimatedElement.
                // Only alpha is clamped because it cannot render meaningfully
                // outside the [0, 1] range.
                val rawProgress = progress.value
                val p = rawProgress.coerceIn(0f, 1f)
                alpha = p
                scaleX = 0.94f + rawProgress * 0.06f
                scaleY = scaleX
                translationY = (1f - rawProgress) * offsetYPx
                clip = false
            }
        ) {
            CleanTitle(
                text = text,
                fontSize = fontSize,
                colors = colors,
                scrollState = scrollState
            )
        }
    }
}

@Composable
internal fun SearchInputCard(
    query: String,
    onQueryChange: (String) -> Unit,
    colors: ThemeColors,
    cardBackground: Color,
    isSmallScreen: Boolean,
    modifier: Modifier = Modifier
) {
    val ts = LocalTypeScale.current
    val strings = LocalStrings.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, colors.primaryAccent.copy(alpha = 0.55f), RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(cardBackground)
            .padding(horizontal = if (isSmallScreen) 18.dp else 22.dp, vertical = if (isSmallScreen) 14.dp else 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Canvas(modifier = Modifier.size(if (isSmallScreen) 21.dp else 24.dp)) {
                val strokeWidth = 2.2.dp.toPx()
                val radius = size.minDimension * 0.29f
                val center = Offset(size.width * 0.43f, size.height * 0.43f)
                drawCircle(
                    color = colors.primaryAccent.copy(alpha = 0.85f),
                    radius = radius,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = strokeWidth,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
                drawLine(
                    color = colors.primaryAccent.copy(alpha = 0.85f),
                    start = Offset(center.x + radius * 0.70f, center.y + radius * 0.70f),
                    end = Offset(size.width * 0.80f, size.height * 0.80f),
                    strokeWidth = strokeWidth,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = colors.textPrimary,
                    fontSize = ts.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    fontFamily = quicksandFontFamily
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                cursorBrush = SolidColor(colors.primaryAccent),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (query.isBlank()) {
                            Text(
                                text = strings["search.placeholder"].ifEmpty { "Search settings, toggles, sliders..." },
                                color = colors.textSecondary.copy(alpha = 0.68f),
                                fontSize = ts.bodyLarge,
                                fontFamily = quicksandFontFamily,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun SearchSelectorCard(
    title: String,
    description: String,
    colors: ThemeColors,
    cardBackground: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    val ts = LocalTypeScale.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.primaryAccent.copy(alpha = 0.55f), RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(cardBackground)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(colors.primaryAccent)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = ts.labelLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        fontFamily = quicksandFontFamily,
                        color = colors.primaryAccent.copy(alpha = 0.7f)
                    )
                    Text(
                        text = description,
                        fontSize = ts.bodySmall,
                        color = colors.textSecondary,
                        fontFamily = quicksandFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                    content()
                }
            }
        }
    }
}

// Keyword index for a search entry, read from the active translation. The stored
// value is a comma-separated list; `english` is the untranslated fallback used
// when the key is missing.
@Composable
private fun searchKeywords(id: String, english: String): List<String> =
    LocalStrings.current["search_keywords.$id"].ifEmpty { english }
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

@Composable
fun SettingsSearchPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    onAppearanceClick: () -> Unit,
    onColorCustomizationClick: () -> Unit,
    onGradientClick: () -> Unit,
    onEffectsClick: () -> Unit,
    onParticlesClick: () -> Unit,
    onRendererClick: () -> Unit,
    onSystemClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onBackupClick: () -> Unit,
    onCrashLogClick: () -> Unit,
    onLanguageClick: () -> Unit,
    onHapticsClick: () -> Unit,
    // ── APPEARANCE / Appearance ──────────────────────────────────────────────────
    themePreference: Int,
    onThemeChange: (Int) -> Unit,
    animationLevel: Int,
    onAnimationLevelChange: (Int) -> Unit,
    uiScale: Int,
    onUiScaleChange: (Int) -> Unit,
    staggerEnabled: Boolean,
    onStaggerEnabledChange: (Boolean) -> Unit,
    backButtonAvoidanceEnabled: Boolean,
    onBackButtonAvoidanceEnabledChange: (Boolean) -> Unit,
    backButtonInversed: Boolean,
    onBackButtonInversedChange: (Boolean) -> Unit,
    shadowsEnabled: Boolean,
    onShadowsEnabledChange: (Boolean) -> Unit,
    // ── Colors ────────────────────────────────────────────────────────────────
    oledMode: Boolean,
    darkModeActive: Boolean,
    onOledModeChange: (Boolean) -> Unit,
    useDynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    advancedColorPicker: Boolean,
    onAdvancedColorPickerChange: (Boolean) -> Unit,
    gradientEnabled: Boolean,
    onGradientChange: (Boolean) -> Unit,
    customAccentColor: Color,
    onAccentColorChange: (Color) -> Unit,
    customGradientStart: Color,
    onGradientStartChange: (Color) -> Unit,
    customGradientEnd: Color,
    onGradientEndChange: (Color) -> Unit,
    // ── Effects ───────────────────────────────────────────────────────────────
    blurEnabled: Boolean,
    onBlurChange: (Boolean) -> Unit,
    // ── Particles ─────────────────────────────────────────────────────────────
    particlesEnabled: Boolean,
    onParticlesChange: (Boolean) -> Unit,
    matrixMode: Boolean,
    onMatrixModeChange: (Boolean) -> Unit,
    particleStarMode: Boolean,
    onParticleStarModeChange: (Boolean) -> Unit,
    particleTimeMode: Boolean,
    onParticleTimeModeChange: (Boolean) -> Unit,
    particleParallaxEnabled: Boolean,
    onParticleParallaxEnabledChange: (Boolean) -> Unit,
    particleParallaxSensitivity: Int,
    onParticleParallaxSensitivityChange: (Int) -> Unit,
    particleCount: Int,
    onParticleCountChange: (Int) -> Unit,
    particleSpeed: Int,
    onParticleSpeedChange: (Int) -> Unit,
    nativeRefreshRate: Boolean,
    onNativeRefreshRateChange: (Boolean) -> Unit,
    quarterRefreshRate: Boolean,
    onQuarterRefreshRateChange: (Boolean) -> Unit,
    matrixSpeed: Int,
    onMatrixSpeedChange: (Int) -> Unit,
    matrixDensity: Int,
    onMatrixDensityChange: (Int) -> Unit,
    matrixFontSize: Int,
    onMatrixFontSizeChange: (Int) -> Unit,
    matrixFadeLength: Int,
    onMatrixFadeLengthChange: (Int) -> Unit,
    // ── Renderer ──────────────────────────────────────────────────────────────
    aggressiveMode: Boolean,
    onAggressiveModeChange: (Boolean) -> Unit,
    killLauncher: Boolean,
    onKillLauncherChange: (Boolean) -> Unit,
    killKeyboard: Boolean,
    onKillKeyboardChange: (Boolean) -> Unit,
    showGpuWatchButton: Boolean,
    onShowGpuWatchButtonChange: (Boolean) -> Unit,
    // ── System / App ──────────────────────────────────────────────────────────
    verboseMode: Boolean,
    onVerboseModeChange: (Boolean) -> Unit,
    dismissOnClickOutside: Boolean,
    onDismissOnClickOutsideChange: (Boolean) -> Unit,
    notificationsEnabled: Boolean,
    onNotificationsEnabledChange: (Boolean) -> Unit,
    notifIntervalIndex: Int,
    onNotifIntervalChange: (Int) -> Unit,
    // ── Common ────────────────────────────────────────────────────────────────
    oledModeLocked: Boolean = false,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    performHaptic: () -> Unit
) {
    val ts = LocalTypeScale.current
    val strings = LocalStrings.current
    val animSpeed = LocalAnimationSpeed.current
    var query by remember { mutableStateOf("") }
    var committedQuery by remember { mutableStateOf("") }
    var showAllSettings by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            query = ""
            committedQuery = ""
            showAllSettings = false
        }
    }

    // Keep text input instant, but delay expensive result composition very slightly.
    // This prevents the first keystroke from scoring + composing result cards in
    // the same frame as keyboard/input work, which caused the search-panel hitch.
    LaunchedEffect(query) {
        val nextQuery = query.trim()
        if (nextQuery.isBlank()) {
            committedQuery = ""
        } else {
            delay(90)
            committedQuery = nextQuery
        }
    }
    val dynamicColorAvailable = Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    val customColorControlsEnabled = !useDynamicColor || !dynamicColorAvailable

    fun openSearchDestination(action: () -> Unit) {
        action()
        onDismiss()
    }

    // ── Helper: a breadcrumb pill shown above each result ─────────────────────
    // When the pill overlaps the floating back button, it fades out with
    // ease-in-out transparency instead of wrapping or displacing the card.
    @Composable
    fun PathLabel(path: String) {
        val segments = path.split("→").map { it.trim() }
        val ts = LocalTypeScale.current
        val backButtonAvoidance = LocalFloatingBackButtonAvoidance.current
        val density = LocalDensity.current
        val view = LocalView.current
        val animLevel = LocalAnimationLevel.current
        val animSpeed = LocalAnimationSpeed.current
        var overlapsFloatingBackButton by remember { mutableStateOf(false) }

        val pathAlpha by animateFloatAsState(
            targetValue = if (backButtonAvoidance.enabled && overlapsFloatingBackButton) 0f else 1f,
            animationSpec = if (animLevel == 2) snap() else tween(
                durationMillis = MotionTokens.SpeedUtil.durationMs(300, animSpeed),
                easing = MotionTokens.Easing.emphasized
            ),
            label = "path_avoidance_alpha"
        )

        val overlapDetector = if (backButtonAvoidance.enabled) {
            Modifier.onGloballyPositioned { coordinates ->
                val position = coordinates.positionInWindow()
                val itemTop = position.y
                val itemBottom = itemTop + coordinates.size.height
                val screenHeight = view.height.toFloat().takeIf { it > 0f } ?: return@onGloballyPositioned

                val avoidZoneTop = screenHeight - with(density) {
                    (backButtonAvoidance.bottomPadding + backButtonAvoidance.buttonSize + 16.dp).toPx()
                }

                val overlaps = itemBottom > avoidZoneTop && itemTop < screenHeight
                if (overlapsFloatingBackButton != overlaps) overlapsFloatingBackButton = overlaps
            }
        } else Modifier

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(overlapDetector)
                .graphicsLayer(alpha = pathAlpha),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier
                    .padding(start = 2.dp, bottom = 6.dp)
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, colors.primaryAccent.copy(alpha = 0.28f), RoundedCornerShape(50))
                    .background(colors.primaryAccent.copy(alpha = 0.08f))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                segments.forEachIndexed { index, segment ->
                    Text(
                        text = segment,
                        fontSize = ts.bodySmall,
                        color = colors.primaryAccent.copy(alpha = if (index == segments.lastIndex) 0.85f else 0.5f),
                        fontFamily = quicksandFontFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (index < segments.lastIndex) {
                        Text(
                            text = "  ›  ",
                            fontSize = ts.bodySmall,
                            color = colors.primaryAccent.copy(alpha = 0.35f),
                            fontFamily = quicksandFontFamily,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    fun tr(key: String, fallback: String): String = strings[key].ifEmpty { fallback }
    fun trPath(vararg parts: String): String = parts.joinToString(" → ")

    val pathSettings = tr("settings.title", "SETTINGS")
    val pathAppearance = tr("settings.appearance", "VISUALS")
    val pathEffects = tr("effects.title", "EFFECTS")
    val pathColors = tr("colors.title", "COLORS")
    val pathParticles = tr("particles.title", "PARTICLES")
    val pathRenderer = tr("renderer.title", "RENDERER")
    val pathSystem = tr("settings.system", "SYSTEM")
    val pathNotifications = tr("system.notifications", "NOTIFICATIONS")
    val pathShapeAndLook =
        tr("text_catalog.visuals_effects_particles_shape_look", "SHAPE & LOOK").substringAfterLast("→").trim()
            .ifEmpty { "SHAPE & LOOK" }
    val pathMotion = tr("particles.motion_title", "MOTION")
    val pathPerformance = tr("particles.performance_title", "PERFORMANCE")
    val pathMatrixSettings = tr("particles.matrix_settings_title", "MATRIX SETTINGS")

    // Search results must render live state. Do not remember these lambdas:
    // if a ToggleCard or GlideOptionSelector changes a setting, the result card
    // needs to recompose with the new checked/selected value immediately.
    val items = listOf(
        // ── PANEL SHORTCUTS ───────────────────────────────────────────────────
        SettingsSearchItem(
            id = "appearance_panel",
            title = strings["settings.appearance"].ifEmpty { "APPEARANCE" },
            keywords = searchKeywords("appearance_panel", "appearance,visuals,theme,ui,colors,effects,particles,look,settings section,aspect,temă,culori,efecte,particule"),
            path = strings["settings.title"].ifEmpty { "SETTINGS" }
        ) {
            Column {
                SettingsNavigationCard(
            title = strings["settings.appearance"].ifEmpty { "VISUALS" },
                    description = strings["text_catalog.theme_color_effects_particles_and_visual_behavior"].ifEmpty { "Theme, color, effects, particles, and visual behavior." },
                    onClick = { openSearchDestination(onAppearanceClick) },
                    isSmallScreen = isSmallScreen, colors = colors,
                    cardBackground = cardBackground, oledMode = oledMode
                )
            }
        },
        SettingsSearchItem(
            id = "colors_panel",
            title = strings["colors.title"].ifEmpty { "COLORS" },
            keywords = searchKeywords("colors_panel", "colors,colour,accent,dynamic color,color picker,palette,custom color,culori,accent,paletă,culoare"),
            path = trPath(pathSettings, pathAppearance)
        ) {
            Column {
                SettingsNavigationCard(
                    title = strings["colors.title"].ifEmpty { "COLORS" },
                    description = strings["text_catalog.dynamic_color_accent_color_and_background_gradient_controls"].ifEmpty { "Dynamic color, accent color, and background gradient controls." },
                    onClick = { openSearchDestination(onColorCustomizationClick) },
                    isSmallScreen = isSmallScreen, colors = colors,
                    cardBackground = cardBackground, oledMode = oledMode
                )
            }
        },
        SettingsSearchItem(
            id = "effects_panel",
            title = strings["effects.title"].ifEmpty { "EFFECTS" },
            keywords = searchKeywords("effects_panel", "effects,blur,shadows,visual effects,glass,cards,efecte,umbre,sticlă,carduri"),
            path = trPath(pathSettings, pathAppearance)
        ) {
            Column {
                SettingsNavigationCard(
                    title = strings["effects.title"].ifEmpty { "EFFECTS" },
                    description = strings["text_catalog.blur_and_card_shadow_controls"].ifEmpty { "Blur and card-shadow controls." },
                    onClick = { openSearchDestination(onEffectsClick) },
                    isSmallScreen = isSmallScreen, colors = colors,
                    cardBackground = cardBackground, oledMode = oledMode
                )
            }
        },
        SettingsSearchItem(
            id = "particles_panel",
            title = strings["particles.title"].ifEmpty { "PARTICLES" },
            keywords = searchKeywords("particles_panel", "particles,stars,matrix,background,rain,parallax,motion,particule,stele,fundal,mișcare"),
            path = trPath(pathSettings, pathAppearance)
        ) {
            Column {
                SettingsNavigationCard(
                    title = strings["particles.title"].ifEmpty { "PARTICLES" },
                    description = strings["text_catalog.particle_style_motion_appearance_and_performance_controls"].ifEmpty { "Particle style, motion, appearance, and performance controls." },
                    onClick = { openSearchDestination(onParticlesClick) },
                    isSmallScreen = isSmallScreen, colors = colors,
                    cardBackground = cardBackground, oledMode = oledMode
                )
            }
        },
        SettingsSearchItem(
            id = "renderer_panel",
            title = strings["renderer.title"].ifEmpty { "RENDERER" },
            keywords = searchKeywords("renderer_panel", "renderer,opengl,vulkan,switch,aggressive,launcher,keyboard,gpuwatch,redare,motor,grafic,schimbare,tastatură"),
            path = strings["settings.title"].ifEmpty { "SETTINGS" }
        ) {
            Column {
                SettingsNavigationCard(
                    title = strings["renderer.title"].ifEmpty { "RENDERER" },
                    description = strings["text_catalog.renderer_switch_behavior_and_advanced_switching_options"].ifEmpty { "Renderer-switch behavior and advanced switching options." },
                    onClick = { openSearchDestination(onRendererClick) },
                    isSmallScreen = isSmallScreen, colors = colors,
                    cardBackground = cardBackground, oledMode = oledMode
                )
            }
        },
        SettingsSearchItem(
            id = "system_panel",
            title = strings["settings.system"].ifEmpty { "SYSTEM" },
            keywords = searchKeywords("system_panel", "app,system,notifications,backup,language,logs,verbose,tap outside,sistem,notificări,limbă,jurnale"),
            path = strings["settings.title"].ifEmpty { "SETTINGS" }
        ) {
            Column {
                SettingsNavigationCard(
                    title = strings["settings.system"].ifEmpty { "SYSTEM" },
                    description = strings["text_catalog.notifications_backups_language_logs_and_app_behavior"].ifEmpty { "Notifications, backups, language, logs, and app behavior." },
                    onClick = { openSearchDestination(onSystemClick) },
                    isSmallScreen = isSmallScreen, colors = colors,
                    cardBackground = cardBackground, oledMode = oledMode
                )
            }
        },
        SettingsSearchItem(
            id = "notifications_panel",
            title = strings["system.notifications"].ifEmpty { "NOTIFICATIONS" },
            keywords = searchKeywords("notifications_panel", "notifications,reminders,alerts,open gl reminder,opengl reminder,notificări,memento,alerte"),
            path = trPath(pathSettings, pathSystem)
        ) {
            Column {
                SettingsNavigationCard(
                    title = strings["system.notifications"].ifEmpty { "NOTIFICATIONS" },
                    description = strings["text_catalog.reminder_alerts_if_opengl_is_left_enabled"].ifEmpty { "Reminder alerts if OpenGL is left enabled." },
                    onClick = { openSearchDestination(onNotificationsClick) },
                    isSmallScreen = isSmallScreen, colors = colors,
                    cardBackground = cardBackground, oledMode = oledMode
                )
            }
        },
        SettingsSearchItem(
            id = "backup_panel",
            title = strings["system.backup"].ifEmpty { "BACKUP & RESTORE" },
            keywords = searchKeywords("backup_panel", "backup,restore,export,import,settings backup,save settings,copie,rezervă,restaurare,salvare"),
            path = trPath(pathSettings, pathSystem)
        ) {
            Column {
                SettingsNavigationCard(
                    title = strings["system.backup"].ifEmpty { "BACKUP & RESTORE" },
                    description = strings["text_catalog.export_settings_or_restore_them_from_a_backup_file"].ifEmpty { "Export settings or restore them from a backup file." },
                    onClick = { openSearchDestination(onBackupClick) },
                    isSmallScreen = isSmallScreen, colors = colors,
                    cardBackground = cardBackground, oledMode = oledMode
                )
            }
        },
        SettingsSearchItem(
            id = "language_panel",
            title = strings["settings.language"].ifEmpty { "LANGUAGE" },
            keywords = searchKeywords("language_panel", "language,translation,locale,english,romanian,romana,limba,limbă,traducere"),
            path = trPath(pathSettings, pathSystem)
        ) {
            Column {
                SettingsNavigationCard(
                    title = strings["settings.language"].ifEmpty { "LANGUAGE" },
                    description = strings["text_catalog.change_the_display_language_used_throughout_gama"].ifEmpty { "Change the display language used throughout GAMA." },
                    onClick = { openSearchDestination(onLanguageClick) },
                    isSmallScreen = isSmallScreen, colors = colors,
                    cardBackground = cardBackground, oledMode = oledMode
                )
            }
        },
        SettingsSearchItem(
            id = "logs_panel",
            title = strings["system.crash_log"].ifEmpty { "LOGS" },
            keywords = searchKeywords("logs_panel", "logs,log,crash,crashes,crash log,debug,reports,system crash,jurnale,rapoarte,eroare"),
            path = trPath(pathSettings, pathSystem)
        ) {
            Column {
                SettingsNavigationCard(
                    title = strings["system.crash_log"].ifEmpty { "LOGS" },
                    description = strings["text_catalog.view_recent_reports_and_copy_details_for_troubleshooting"].ifEmpty { "View recent reports and copy details for troubleshooting." },
                    onClick = { openSearchDestination(onCrashLogClick) },
                    isSmallScreen = isSmallScreen, colors = colors,
                    cardBackground = cardBackground, oledMode = oledMode
                )
            }
        },

        // ── APPEARANCE ───────────────────────────────────────────────────────────
        SettingsSearchItem(
            id = "theme",
            title = tr("appearance.theme", "THEME"),
            keywords = searchKeywords("theme", "theme,mode,auto,dark,light,appearance,visuals,color mode,them,thme,teme,night"),
            path = pathAppearance
        ) {
            Column {
                SearchSelectorCard(
                    title = tr("appearance.theme", "THEME"),
                    description = tr(
                        "text_catalog.choose_how_gama_follows_light_dark_or_system_appearance",
                        "Choose how GAMA follows light, dark, or system appearance."
                    ),
                    colors = colors,
                    cardBackground = cardBackground
                ) {
                    GlideOptionSelector(
                        options = listOf(
                        tr("appearance.theme_auto", "Auto"),
                        tr("appearance.theme_dark", "Dark"),
                        tr("appearance.theme_light", "Light")
                    ),
                        selectedIndex = themePreference,
                        onOptionSelected = { performHaptic(); onThemeChange(it) },
                        colors = colors,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = true
                    )
                }
            }
        },
        SettingsSearchItem(
            id = "animations",
            title = tr("appearance.animations", "ANIMATIONS"),
            keywords = searchKeywords("animations", "animations,animation,motion,movement,reduce motion,off,full,anim,smooth,transition,reduced"),
            path = pathAppearance
        ) {
            Column {
                SearchSelectorCard(
                    title = tr("appearance.animations", "ANIMATIONS"),
                    description = tr(
                        "text_catalog.control_how_much_motion_the_interface_uses",
                        "Control how much motion the interface uses."
                    ),
                    colors = colors,
                    cardBackground = cardBackground
                ) {
                    GlideOptionSelector(
                        options = listOf(
                        tr("appearance.anim_full", "Full"),
                        tr("appearance.anim_reduced", "Reduced"),
                        tr("appearance.anim_off", "Off")
                    ),
                        selectedIndex = animationLevel,
                        onOptionSelected = { performHaptic(); onAnimationLevelChange(it) },
                        colors = colors,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        SettingsSearchItem(
            id = "ui_scale",
            title = tr("appearance.ui_scale", "UI SCALE"),
            keywords = searchKeywords("ui_scale", "ui,scale,size,interface size,zoom,75,100,125,text size,font size,big,small,large"),
            path = pathAppearance
        ) {
            Column {
                SearchSelectorCard(
                    title = tr("appearance.ui_scale", "UI SCALE"),
                    description = tr(
                        "text_catalog.adjust_the_size_of_the_interface",
                        "Adjust the size of the interface."
                    ),
                    colors = colors,
                    cardBackground = cardBackground
                ) {
                    GlideOptionSelector(
                        options = listOf(
                            tr("appearance.scale_75", "75%"),
                            tr("appearance.scale_100", "100%"),
                            tr("appearance.scale_125", "125%")
                        ),
                        selectedIndex = uiScale,
                        onOptionSelected = { performHaptic(); onUiScaleChange(it) },
                        colors = colors,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        SettingsSearchItem(
            id = "stagger_animations",
            title = tr("appearance.stagger_animations", "STAGGER ANIMATIONS"),
            keywords = searchKeywords("stagger_animations", "stagger,stag,stager,staggered,cards,cascade,panel cards,entrance,animations,motion,one by one,instant"),
            path = pathAppearance
        ) {
            Column {
                ToggleCard(
                    title = tr("appearance.stagger_animations", "STAGGER ANIMATIONS"),
                    description = tr(
                        "text_catalog.panel_cards_animate_in_one_by_one_turn_off_for_instant_panel",
                        "Panel cards animate in one by one — turn off for instant panel opens."
                    ),
                    checked = staggerEnabled,
                    onCheckedChange = { performHaptic(); onStaggerEnabledChange(it) },
                    colors = colors,
                    cardBackground = cardBackground,
                    isSmallScreen = isSmallScreen,
                    oledMode = oledMode
                )
            }
        },
        SettingsSearchItem(
            id = "back_button_avoidance",
            title = tr("appearance.back_button_avoidance", "BACK BUTTON AVOIDANCE"),
            keywords = searchKeywords("back_button_avoidance", "back,button,avoid,avoidance,duck,rescale,layout,overlap,floating,arrow"),
            path = pathAppearance
        ) {
            Column {
                ToggleCard(
                    title = tr("appearance.back_button_avoidance", "BACK BUTTON AVOIDANCE"),
                    description = tr(
                        "text_catalog.cards_duck_left_when_the_floating_button_would_overlap_them_",
                        "Cards duck left when the floating < button would overlap them. Turn off if you prefer the button to float over the UI."
                    ),
                    checked = backButtonAvoidanceEnabled,
                    onCheckedChange = { performHaptic(); onBackButtonAvoidanceEnabledChange(it) },
                    colors = colors,
                    cardBackground = cardBackground,
                    isSmallScreen = isSmallScreen,
                    oledMode = oledMode
                )
            }
        },
        SettingsSearchItem(
            id = "back_button_position",
            title = tr("system.back_button_position", "BACK BUTTON POSITION"),
            keywords = searchKeywords("back_button_position", "back,button,position,side,left,right,inverted,inversed,inverse,search button,global button"),
            path = pathSystem
        ) {
            Column {
                SearchSelectorCard(
                    title = tr("system.back_button_position", "BACK BUTTON POSITION"),
                    description = tr(
                        "system.back_button_position_desc",
                        "Choose which side gets the floating < button. Search and Global move to the opposite side."
                    ),
                    colors = colors,
                    cardBackground = cardBackground
                ) {
                    GlideOptionSelector(
                        options = listOf(
                            tr("system.back_button_position_normal", "Normal"),
                            tr("system.back_button_position_inversed", "Inverted")
                        ),
                        selectedIndex = if (backButtonInversed) 1 else 0,
                        onOptionSelected = { index -> performHaptic(); onBackButtonInversedChange(index == 1) },
                        colors = colors,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        SettingsSearchItem(
            id = "card_shadows",
            title = tr("appearance.card_shadows", "CARD SHADOWS"),
            keywords = searchKeywords("card_shadows", "card,cards,shadow,shadows,drop shadow,gpu,performance,visual,depth,elevation"),
            path = trPath(pathAppearance, pathEffects),
            enabledForShadow = { !oledMode }
        ) {
            Column {
                ToggleCard(
                    title = tr("appearance.card_shadows", "CARD SHADOWS"),
                    description = tr(
                        "text_catalog.drop_shadows_under_cards_disable_to_reduce_gpu_load_or_fix_v",
                        "Drop shadows under cards — disable to reduce GPU load or fix visual glitches during animations."
                    ),
                    checked = shadowsEnabled && !oledMode,
                    onCheckedChange = { performHaptic(); onShadowsEnabledChange(it) },
                    colors = colors,
                    cardBackground = cardBackground,
                    isSmallScreen = isSmallScreen,
                    oledMode = oledMode,
                    enabled = !oledMode
                )
            }
        },
        // ── COLORS ────────────────────────────────────────────────────────────
        SettingsSearchItem(
            id = "dynamic_color",
            title = tr("colors.dynamic_color", "DYNAMIC COLOR"),
            keywords = searchKeywords("dynamic_color", "dynamic,material you,wallpaper,accent,monet,android 12,auto color,system color,automatic"),
            path = trPath(pathAppearance, pathColors),
            enabledForShadow = { dynamicColorAvailable }
        ) {
            Column {
                ToggleCard(
                    title = tr("colors.dynamic_color", "DYNAMIC COLOR"),
                    description = if (dynamicColorAvailable) tr(
                        "text_catalog.picks_accent_colors_from_your_wallpaper_automatically_via_ma",
                        "Picks accent colors from your wallpaper automatically via Material You"
                    ) else tr("colors.dynamic_color_unavailable", "Requires Android 12 or newer"),
                    checked = useDynamicColor && dynamicColorAvailable,
                    onCheckedChange = { performHaptic(); onDynamicColorChange(it) },
                    colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen,
                    oledMode = oledMode, enabled = dynamicColorAvailable
                )
            }
        },
        SettingsSearchItem(
            id = "advanced_color_picker",
            title = tr("colors.advanced_picker", "HEX COLOR PICKER"),
            keywords = searchKeywords("advanced_color_picker", "hex,color picker,hex input,custom color,type color,#,picker,colour"),
            path = trPath(pathAppearance, pathColors),
            enabledForShadow = { !useDynamicColor || !dynamicColorAvailable }
        ) {
            Column {
                ToggleCard(
                    title = tr("colors.advanced_picker", "HEX COLOR PICKER"),
                    description = tr(
                        "text_catalog.adds_a_hex_input_field_to_the_color_pickers_type_any_color_d",
                        "Adds a hex input field to the color pickers — type any color directly, e.g. #4895EF"
                    ),
                    checked = advancedColorPicker,
                    onCheckedChange = { performHaptic(); onAdvancedColorPickerChange(it) },
                    colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen,
                    oledMode = oledMode, enabled = !useDynamicColor || !dynamicColorAvailable
                )
            }
        },
        SettingsSearchItem(
            id = "accent_color",
            title = tr("colors.accent_color", "ACCENT COLOR"),
            keywords = searchKeywords("accent_color", "accent,accent color,custom accent,color,colour,highlight,buttons,borders,picker,hex,primary color"),
            path = trPath(pathAppearance, pathColors),
            enabledForShadow = { customColorControlsEnabled }
        ) {
            Column {
                CompactColorPickerCard(
                    title = tr("colors.accent_color", "ACCENT COLOR"),
                    description = if (customColorControlsEnabled) tr(
                        "text_catalog.the_highlight_color_used_on_buttons_borders_and_interactive_",
                        "The highlight color used on buttons, borders, and interactive elements"
                    ) else tr(
                        "text_catalog.controlled_by_dynamic_color_disable_it_to_set_a_custom_accen",
                        "Controlled by Dynamic Color — disable it to set a custom accent"
                    ),
                    currentColor = customAccentColor,
                    onColorChange = { color: Color -> performHaptic(); onAccentColorChange(color) },
                    colors = colors,
                    cardBackground = cardBackground,
                    isSmallScreen = isSmallScreen,
                    isLandscape = isLandscape,
                    advancedPicker = advancedColorPicker,
                    enabled = customColorControlsEnabled,
                    oledMode = oledMode
                )
            }
        },


        // ── EFFECTS ───────────────────────────────────────────────────────────
        SettingsSearchItem(
            id = "blur",
            title = tr("blur.title", "BLUR"),
            keywords = searchKeywords("blur", "blur,frosted,glass,frosted glass,panel blur,backdrop,depth,premium,translucent,blured"),
            path = trPath(pathAppearance, pathEffects)
        ) {
            Column {
                ToggleCard(
                    title = tr("blur.title", "BLUR"),
                    description = tr(
                        "text_catalog.frosted_glass_behind_panels_and_dialogs_subtle_depth_that_ma",
                        "Frosted glass behind panels and dialogs — subtle depth that makes the UI feel premium"
                    ),
                    checked = blurEnabled, onCheckedChange = { performHaptic(); onBlurChange(it) },
                    colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen, oledMode = oledMode
                )
            }
        },
        // ── PARTICLES ─────────────────────────────────────────────────────────
        SettingsSearchItem(
            id = "particles",
            title = tr("particles.title", "PARTICLES"),
            keywords = searchKeywords("particles", "particles,stars,floating,dots,particle,animation,background animation,star,sparkle,living,feel"),
            path = trPath(pathAppearance, pathEffects, pathParticles)
        ) {
            Column {
                ToggleCard(
                    title = tr("particles.title", "PARTICLES"),
                    description = tr(
                        "text_catalog.animates_the_background_with_floating_particles_or_matrix_ra",
                        "Animates the background with floating particles or Matrix rain"
                    ),
                    checked = particlesEnabled, onCheckedChange = { performHaptic(); onParticlesChange(it) },
                    colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen, oledMode = oledMode
                )
            }
        },
        SettingsSearchItem(
            id = "particle_style",
            title = tr("text_catalog.particle_style", "PARTICLE STYLE"),
            keywords = searchKeywords("particle_style", "style,stars,matrix,rain,matrix rain,digital rain,glyphs,particle style,mode"),
            path = trPath(pathAppearance, pathEffects, pathParticles),
            enabledForShadow = { particlesEnabled }
        ) {
            Column {
                SearchSelectorCard(
                    title = tr("text_catalog.particle_style", "PARTICLE STYLE"),
                    description = if (matrixMode) tr("particles.style_matrix_desc", "Cascading columns of glyphs — the classic Matrix digital rain effect") else tr("particles.style_particles_desc", "Twinkling stars that float and shift with device tilt via parallax"),
                    colors = colors, cardBackground = cardBackground
                ) {
                    GlideOptionSelector(
                        options = listOf(
                            tr("particles.style_particles", "Stars"),
                            tr("particles.style_matrix", "Matrix")
                        ), selectedIndex = if (matrixMode) 1 else 0,
                        onOptionSelected = { performHaptic(); if (particlesEnabled) onMatrixModeChange(it == 1) },
                        colors = colors, modifier = Modifier.fillMaxWidth(), enabled = particlesEnabled
                    )
                }
            }
        },
        SettingsSearchItem(
            id = "star_mode",
            title = tr("particles.star_mode", "STAR MODE"),
            keywords = searchKeywords("star_mode", "star,star mode,stars,shape,look,glow,twinkle,sparkle,star shape"),
            path = trPath(pathAppearance, pathEffects, pathParticles, pathShapeAndLook),
            enabledForShadow = { particlesEnabled && !matrixMode }
        ) {
            Column {
                ToggleCard(
                    title = tr("particles.star_mode", "STAR MODE"),
                    description = tr(
                        "text_catalog.renders_particles_as_glowing_5_pointed_stars_instead_of_soft",
                        "Renders particles as glowing 5-pointed stars instead of soft dots"
                    ),
                    checked = particleStarMode, onCheckedChange = { performHaptic(); onParticleStarModeChange(it) },
                    colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen,
                    oledMode = oledMode, enabled = particlesEnabled && !matrixMode
                )
            }
        },
        SettingsSearchItem(
            id = "time_mode",
            title = tr("particles.time_mode", "TIME MODE"),
            keywords = searchKeywords("time_mode", "time,time mode,sun,moon,day,night,sky,real time,clock,daytime,sunrise,sunset"),
            path = trPath(pathAppearance, pathEffects, pathParticles, pathShapeAndLook),
            enabledForShadow = { particlesEnabled && !matrixMode }
        ) {
            Column {
                ToggleCard(
                    title = tr("particles.time_mode", "TIME MODE"),
                    description = tr(
                        "particles.time_mode_desc",
                        "A sun and moon travel across the sky in sync with the real time of day"
                    ),
                    checked = particleTimeMode, onCheckedChange = { performHaptic(); onParticleTimeModeChange(it) },
                    colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen,
                    oledMode = oledMode, enabled = particlesEnabled && !matrixMode
                )
            }
        },
        SettingsSearchItem(
            id = "parallax",
            title = tr("particles.parallax", "PARALLAX"),
            keywords = searchKeywords("parallax", "parallax,tilt,gyro,gyroscope,motion,sensor,depth,3d,perspective"),
            path = trPath(pathAppearance, pathEffects, pathParticles, pathMotion),
            enabledForShadow = { particlesEnabled && !matrixMode }
        ) {
            Column {
                ToggleCard(
                    title = tr("particles.parallax", "PARALLAX"),
                    description = tr(
                        "text_catalog.particles_shift_with_device_tilt_via_the_gyroscope_for_a_dep",
                        "Particles shift with device tilt via the gyroscope for a depth effect"
                    ),
                    checked = particleParallaxEnabled,
                    onCheckedChange = { performHaptic(); onParticleParallaxEnabledChange(it) },
                    colors = colors,
                    cardBackground = cardBackground,
                    isSmallScreen = isSmallScreen,
                    oledMode = oledMode,
                    enabled = particlesEnabled && !matrixMode
                )
            }
        },
        SettingsSearchItem(
            id = "parallax_sensitivity",
            title = tr("particles.parallax_sensitivity", "PARALLAX SENSITIVITY"),
            keywords = searchKeywords("parallax_sensitivity", "parallax,sensitivity,tilt,gyro,strength,intensity,low,medium,high,motion"),
            path = trPath(pathAppearance, pathEffects, pathParticles, pathMotion),
            enabledForShadow = { particlesEnabled && particleParallaxEnabled && !matrixMode }
        ) {
            Column {
                SearchSelectorCard(
                    title = tr("particles.parallax_sensitivity", "PARALLAX SENSITIVITY"),
                    description = tr(
                        "text_catalog.how_strongly_the_particles_react_to_device_tilt",
                        "How strongly the particles react to device tilt."
                    ),
                    colors = colors,
                    cardBackground = cardBackground
                ) {
                    GlideOptionSelector(
                        options = listOf(
                            tr("particles.sensitivity_low", "Low"),
                            tr("particles.sensitivity_medium", "Medium"),
                            tr("particles.sensitivity_high", "High")
                        ),
                        selectedIndex = particleParallaxSensitivity.coerceIn(0, 2),
                        onOptionSelected = { performHaptic(); onParticleParallaxSensitivityChange(it) },
                        colors = colors,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = particlesEnabled && particleParallaxEnabled && !matrixMode
                    )
                }
            }
        },
        SettingsSearchItem(
            id = "particle_speed",
            title = tr("text_catalog.particle_speed", "PARTICLE SPEED"),
            keywords = searchKeywords("particle_speed", "speed,fast,slow,velocity,float speed,particle speed,drift"),
            path = trPath(pathAppearance, pathEffects, pathParticles, pathMotion),
            enabledForShadow = { particlesEnabled && !matrixMode }
        ) {
            Column {
                SearchSelectorCard(
                    title = tr("text_catalog.particle_speed", "PARTICLE SPEED"),
                    description = tr(
                        "text_catalog.how_fast_particles_drift_across_the_screen",
                        "How fast particles drift across the screen."
                    ),
                    colors = colors,
                    cardBackground = cardBackground
                ) {
                    GlideOptionSelector(
                        options = listOf(
                            tr("particles.sensitivity_low", "Low"),
                            tr("particles.sensitivity_medium", "Medium"),
                            tr("particles.sensitivity_high", "High")
                        ), selectedIndex = particleSpeed.coerceIn(0, 2),
                        onOptionSelected = { performHaptic(); onParticleSpeedChange(it) },
                        colors = colors, modifier = Modifier.fillMaxWidth(), enabled = particlesEnabled && !matrixMode
                    )
                }
            }
        },
        SettingsSearchItem(
            id = "particle_count",
            title = tr("text_catalog.particle_count", "PARTICLE COUNT"),
            keywords = searchKeywords("particle_count", "count,number,amount,density,particles,how many,more,less,performance,75,150,300"),
            path = trPath(pathAppearance, pathEffects, pathParticles, pathPerformance),
            enabledForShadow = { particlesEnabled && !matrixMode }
        ) {
            Column {
                SearchSelectorCard(
                    title = tr("text_catalog.particle_count", "PARTICLE COUNT"),
                    description = tr("particles.count_desc", "Low = 75 · Medium = 150 · High = 300"),
                    colors = colors,
                    cardBackground = cardBackground
                ) {
                    GlideOptionSelector(
                        options = listOf(
                            tr("particles.sensitivity_low", "Low"),
                            tr("particles.sensitivity_medium", "Medium"),
                            tr("particles.sensitivity_high", "High")
                        ), selectedIndex = particleCount.coerceIn(0, 2),
                        onOptionSelected = { performHaptic(); onParticleCountChange(it) },
                        colors = colors, modifier = Modifier.fillMaxWidth(), enabled = particlesEnabled && !matrixMode
                    )
                }
            }
        },
        SettingsSearchItem(
            id = "particle_refresh_rate",
            title = tr("text_catalog.particle_refresh_rate", "PARTICLE REFRESH RATE"),
            keywords = searchKeywords("particle_refresh_rate", "refresh,refresh rate,fps,frame,native,performance,battery,poll,sensor"),
            path = trPath(pathAppearance, pathEffects, pathParticles, pathPerformance),
            enabledForShadow = { particlesEnabled && !matrixMode }
        ) {
            Column {
                val refreshOption = when {
                    nativeRefreshRate -> 0; quarterRefreshRate -> 2; else -> 1
                }
                SearchSelectorCard(
                    title = tr("text_catalog.particle_refresh_rate", "PARTICLE REFRESH RATE"),
                    description = tr(
                        "text_catalog.controls_how_often_particles_update_native_every_frame_1_2_e",
                        "Controls how often particles update. Native = every frame, 1/2 = every other, 1/4 = every fourth."
                    ),
                    colors = colors,
                    cardBackground = cardBackground
                ) {
                    GlideOptionSelector(
                        options = listOf(tr("text_catalog.native", "Native"), "1/2", "1/4"),
                        selectedIndex = refreshOption,
                        onOptionSelected = { opt ->
                            performHaptic()
                            when (opt) {
                                0 -> {
                                    onNativeRefreshRateChange(true); onQuarterRefreshRateChange(false)
                                }

                                2 -> {
                                    onNativeRefreshRateChange(false); onQuarterRefreshRateChange(true)
                                }

                                else -> {
                                    onNativeRefreshRateChange(false); onQuarterRefreshRateChange(false)
                                }
                            }
                        },
                        colors = colors,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = particlesEnabled && !matrixMode
                    )
                }
            }
        },
        SettingsSearchItem(
            id = "matrix_speed",
            title = tr("text_catalog.matrix_speed", "MATRIX SPEED"),
            keywords = searchKeywords("matrix_speed", "matrix,speed,rain,fall speed,cascade,slow,fast,velocity"),
            path = trPath(pathAppearance, pathEffects, pathParticles, pathMatrixSettings),
            enabledForShadow = { particlesEnabled && matrixMode }
        ) {
            Column {
                SearchSelectorCard(
                    title = tr("text_catalog.matrix_speed", "MATRIX SPEED"),
                    description = tr("matrix.speed_desc", "How fast glyphs fall down the screen."),
                    colors = colors,
                    cardBackground = cardBackground
                ) {
                    GlideOptionSelector(
                        options = listOf(
                            tr("particles.speed_slow", "Slow"),
                            tr("particles.speed_medium", "Medium"),
                            tr("particles.speed_fast", "Fast")
                        ), selectedIndex = matrixSpeed.coerceIn(0, 2),
                        onOptionSelected = { performHaptic(); onMatrixSpeedChange(it) },
                        colors = colors, modifier = Modifier.fillMaxWidth(), enabled = particlesEnabled && matrixMode
                    )
                }
            }
        },
        SettingsSearchItem(
            id = "matrix_density",
            title = tr("text_catalog.matrix_density", "MATRIX DENSITY"),
            keywords = searchKeywords("matrix_density", "matrix,density,rain,columns,sparse,dense,column density,coverage"),
            path = trPath(pathAppearance, pathEffects, pathParticles, pathMatrixSettings),
            enabledForShadow = { particlesEnabled && matrixMode }
        ) {
            Column {
                SearchSelectorCard(
                    title = tr("text_catalog.matrix_density", "MATRIX DENSITY"),
                    description = tr("matrix.density_desc", "How many columns of glyphs appear on screen."),
                    colors = colors,
                    cardBackground = cardBackground
                ) {
                    GlideOptionSelector(
                        options = listOf(
                            tr("text_catalog.sparse", "Sparse"),
                            tr("particles.sensitivity_medium", "Medium"),
                            tr("text_catalog.dense", "Dense")
                        ), selectedIndex = matrixDensity.coerceIn(0, 2),
                        onOptionSelected = { performHaptic(); onMatrixDensityChange(it) },
                        colors = colors, modifier = Modifier.fillMaxWidth(), enabled = particlesEnabled && matrixMode
                    )
                }
            }
        },
        SettingsSearchItem(
            id = "matrix_font_size",
            title = tr("text_catalog.matrix_font_size", "MATRIX FONT SIZE"),
            keywords = searchKeywords("matrix_font_size", "matrix,font,size,glyph,text size,characters,small,large,big"),
            path = trPath(pathAppearance, pathEffects, pathParticles, pathMatrixSettings),
            enabledForShadow = { particlesEnabled && matrixMode }
        ) {
            Column {
                SearchSelectorCard(
                    title = tr("text_catalog.matrix_font_size", "MATRIX FONT SIZE"),
                    description = tr("matrix.font_size_desc", "The size of individual glyphs in the rain."),
                    colors = colors,
                    cardBackground = cardBackground
                ) {
                    GlideOptionSelector(
                        options = listOf(
                            tr("text_catalog.small", "Small"),
                            tr("particles.sensitivity_medium", "Medium"),
                            tr("text_catalog.large", "Large")
                        ), selectedIndex = matrixFontSize.coerceIn(0, 2),
                        onOptionSelected = { performHaptic(); onMatrixFontSizeChange(it) },
                        colors = colors, modifier = Modifier.fillMaxWidth(), enabled = particlesEnabled && matrixMode
                    )
                }
            }
        },
        SettingsSearchItem(
            id = "matrix_fade",
            title = tr("text_catalog.matrix_trail_length", "MATRIX TRAIL LENGTH"),
            keywords = searchKeywords("matrix_fade", "matrix,fade,trail,length,tail,ghost,streak,short,long,full"),
            path = trPath(pathAppearance, pathEffects, pathParticles, pathMatrixSettings),
            enabledForShadow = { particlesEnabled && matrixMode }
        ) {
            Column {
                SearchSelectorCard(
                    title = tr("text_catalog.matrix_trail_length", "MATRIX TRAIL LENGTH"),
                    description = tr(
                        "text_catalog.how_long_the_glowing_trail_behind_each_glyph_is",
                        "How long the glowing trail behind each glyph is."
                    ),
                    colors = colors,
                    cardBackground = cardBackground
                ) {
                    GlideOptionSelector(
                        options = listOf(
                            tr("text_catalog.short", "Short"),
                            tr("particles.sensitivity_medium", "Medium"),
                            tr("appearance.anim_full", "Full")
                        ), selectedIndex = matrixFadeLength.coerceIn(0, 2),
                        onOptionSelected = { performHaptic(); onMatrixFadeLengthChange(it) },
                        colors = colors, modifier = Modifier.fillMaxWidth(), enabled = particlesEnabled && matrixMode
                    )
                }
            }
        },
        // ── RENDERER ──────────────────────────────────────────────────────────
        SettingsSearchItem(
            id = "aggressive_mode",
            title = tr("renderer.aggressive_mode", "AGGRESSIVE MODE"),
            keywords = searchKeywords("aggressive_mode", "aggressive,mode,renderer,all packages,coverage,broad,every app,all apps,force,global"),
            path = pathRenderer
        ) {
            Column {
                ToggleCard(
                    title = tr("renderer.aggressive_mode", "AGGRESSIVE MODE"),
                    description = tr(
                        "renderer.aggressive_mode_desc",
                        "Restarts third-party apps only so they pick up the renderer — read the warning first"
                    ),
                    checked = aggressiveMode,
                    onCheckedChange = { performHaptic(); onAggressiveModeChange(it) },
                    colors = colors,
                    cardBackground = cardBackground,
                    isSmallScreen = isSmallScreen,
                    oledMode = oledMode,
                    accentBorder = true
                )
            }
        },
        SettingsSearchItem(
            id = "restart_launcher",
            title = tr("renderer.kill_launcher", "RESTART LAUNCHER & SYSTEM UI"),
            keywords = searchKeywords("restart_launcher", "restart,launcher,switch,kill,force stop,miui,xiaomi,immediately,reload"),
            path = pathRenderer
        ) {
            Column {
                ToggleCard(
                    title = tr("renderer.kill_launcher", "RESTART LAUNCHER & SYSTEM UI"),
                    description = tr(
                        "renderer.kill_launcher_desc",
                        "Restarts the launcher and System UI after switching — leave off on Xiaomi / MIUI"
                    ),
                    checked = killLauncher,
                    onCheckedChange = { performHaptic(); onKillLauncherChange(it) },
                    colors = colors,
                    cardBackground = cardBackground,
                    isSmallScreen = isSmallScreen,
                    oledMode = oledMode,
                    accentBorder = true
                )
            }
        },
        SettingsSearchItem(
            id = "restart_keyboard",
            title = tr("renderer.kill_keyboard", "RESTART KEYBOARD ON SWITCH"),
            keywords = searchKeywords("restart_keyboard", "keyboard,input method,ime,restart,kill,force stop,gboard,samsung keyboard,after applying api,renderer"),
            path = pathRenderer
        ) {
            Column {
                ToggleCard(
                    title = tr("renderer.kill_keyboard", "RESTART KEYBOARD ON SWITCH"),
                    description = tr(
                        "renderer.kill_keyboard_desc",
                        "Force-stops the currently selected keyboard after applying the renderer, so it reloads with the new graphics API"
                    ),
                    checked = killKeyboard,
                    onCheckedChange = { performHaptic(); onKillKeyboardChange(it) },
                    colors = colors,
                    cardBackground = cardBackground,
                    isSmallScreen = isSmallScreen,
                    oledMode = oledMode,
                    accentBorder = true
                )
            }
        },
        SettingsSearchItem(
            id = "gpuwatch_shortcut",
            title = tr("text_catalog.gpuwatch_shortcut", "GPUWATCH SHORTCUT"),
            keywords = searchKeywords("gpuwatch_shortcut", "gpuwatch,gpu watch,samsung,shortcut,gpu,button,monitor,overlay"),
            path = pathRenderer
        ) {
            Column {
                ToggleCard(
                    title = tr("text_catalog.gpuwatch_shortcut", "GPUWATCH SHORTCUT"),
                    description = tr(
                        "text_catalog.adds_an_open_gpuwatch_button_on_the_main_screen_samsung_devi",
                        "Adds an Open GPUWatch button on the main screen. Samsung devices only."
                    ),
                    checked = showGpuWatchButton,
                    onCheckedChange = { performHaptic(); onShowGpuWatchButtonChange(it) },
                    colors = colors,
                    cardBackground = cardBackground,
                    isSmallScreen = isSmallScreen,
                    oledMode = oledMode,
                    accentBorder = true
                )
            }
        },
        // ── SYSTEM ──────────────────────────────────────────────────────
        SettingsSearchItem(
            id = "verbose_output",
            title = tr("text_catalog.verbose_output", "VERBOSE OUTPUT"),
            keywords = searchKeywords("verbose_output", "verbose,output,log,shell,command,debug,terminal,output,show output"),
            path = pathSystem
        ) {
            Column {
                ToggleCard(
                    title = tr("text_catalog.verbose_output", "VERBOSE OUTPUT"),
                    description = tr(
                        "text_catalog.shows_the_full_shell_command_output_when_switching_renderers",
                        "Shows the full shell command output when switching renderers"
                    ),
                    checked = verboseMode, onCheckedChange = { performHaptic(); onVerboseModeChange(it) },
                    colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen, oledMode = oledMode
                )
            }
        },
        SettingsSearchItem(
            id = "tap_outside_to_close",
            title = tr("renderer.tap_outside_to_close", "TAP OUTSIDE TO CLOSE"),
            keywords = searchKeywords("tap_outside_to_close", "tap,outside,close,dismiss,panel,back button,click outside,touch outside,gesture"),
            path = pathSystem
        ) {
            Column {
                ToggleCard(
                    title = tr("renderer.tap_outside_to_close", "TAP OUTSIDE TO CLOSE"),
                    description = tr(
                        "renderer.tap_outside_to_close_desc",
                        "Tap anywhere outside an open panel to dismiss it — turn off to require the back button instead"
                    ),
                    checked = dismissOnClickOutside,
                    onCheckedChange = { performHaptic(); onDismissOnClickOutsideChange(it) },
                    colors = colors,
                    cardBackground = cardBackground,
                    isSmallScreen = isSmallScreen,
                    oledMode = oledMode
                )
            }
        },
        SettingsSearchItem(
            id = "notifications_reminders",
            title = tr("text_catalog.notifications_reminders", "NOTIFICATIONS / REMINDERS"),
            keywords = searchKeywords("notifications_reminders", "notifications,reminders,alert,notify,opengl,reminder,ping,notification,notif"),
            path = trPath(pathSystem, pathNotifications)
        ) {
            Column {
                ToggleCard(
                    title = tr("notifications.reminders", "REMINDERS"),
                    description = tr(
                        "notifications.reminders_off_desc",
                        "Sends an alert if you switch to OpenGL and forget to switch back"
                    ),
                    checked = notificationsEnabled,
                    onCheckedChange = { performHaptic(); onNotificationsEnabledChange(it) },
                    colors = colors,
                    cardBackground = cardBackground,
                    isSmallScreen = isSmallScreen,
                    oledMode = oledMode
                )
            }
        },
        SettingsSearchItem(
            id = "reminder_interval",
            title = tr("notifications.interval", "REMINDER INTERVAL"),
            keywords = searchKeywords("reminder_interval", "interval,reminder,frequency,how often,notification,2h,4h,6h,12h,24h,hours"),
            path = trPath(pathSystem, pathNotifications),
            enabledForShadow = { notificationsEnabled }
        ) {
            Column {
                SearchSelectorCard(
                    title = tr("notifications.interval", "REMINDER INTERVAL"),
                    description = tr(
                        "text_catalog.how_often_gama_reminds_you_that_you_re_on_opengl",
                        "How often GAMA reminds you that you're on OpenGL."
                    ),
                    colors = colors,
                    cardBackground = cardBackground
                ) {
                    GlideOptionSelector(
                        options = listOf(
                            tr("text_catalog.2_h", "2 h"),
                            tr("text_catalog.4_h", "4 h"),
                            tr("text_catalog.6_h", "6 h"),
                            tr("text_catalog.12_h", "12 h"),
                            tr("text_catalog.24_h", "24 h")
                        ),
                        selectedIndex = notifIntervalIndex.coerceIn(0, 4),
                        onOptionSelected = { performHaptic(); onNotifIntervalChange(it) },
                        colors = colors,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = notificationsEnabled
                    )
                }
            }
        }
    )

    // Search scoring is intentionally cheap and synchronous now. The previous
    // debounced remembered result list kept old render lambdas alive, so controls
    // inside search results could update the real setting but stay visually stale.
    val results = if (showAllSettings) {
        items
    } else {
        val q = committedQuery
        if (q.isBlank()) {
            emptyList()
        } else {
            val qLower = q.lowercase()
            val queryTokens = qLower.split(Regex("\\s+")).filter { it.isNotBlank() }
            items
                .map { it to settingsSearchScore(qLower, queryTokens, it) }
                .filter { (_, score) -> score >= 45 }
                .sortedByDescending { (_, score) -> score }
                .take(8) // Compose only the most relevant cards. Keeps search instant on slower phones.
                .map { it.first }
        }
    }
    val itemById = remember(items) { items.associateBy { it.id } }

    val globalShortcutVisible = !showAllSettings && query.trim().isBlank() && committedQuery.isBlank()
    val globalFloatingButton: (@Composable (Modifier, FloatingButtonHoldState, Boolean) -> Unit)? = if (globalShortcutVisible) {
        { floatingModifier, holdState, isLeftSide ->
            PanelGlobalButton(
                onClick = {
                    performHaptic()
                    query = ""
                    committedQuery = ""
                    showAllSettings = true
                },
                colors = colors,
                oledMode = oledMode,
                isSmallScreen = isSmallScreen,
                enabled = visible && globalShortcutVisible,
                floatingHoldState = holdState,
                isLeftSide = isLeftSide,
                modifier = floatingModifier
            )
        }
    } else null

    PanelScaffold(
        visible = visible,
        onDismiss = {
            if (showAllSettings) showAllSettings = false else onDismiss()
        },
        isLandscape = isLandscape,
        isSmallScreen = isSmallScreen,
        oledMode = oledMode,
        colors = colors,
        leadingFloatingButton = globalFloatingButton,
        edgeSpacers = false,
        reserveBackButtonSpace = false,
        contentScrollable = false
    ) { scrollState ->
        val panelTitleKey = if (showAllSettings) "global" else "search"
        val panelTitleText = if (showAllSettings)
            strings["global.title"].ifEmpty { "GLOBAL" }
        else
            strings["search.title"].ifEmpty { "SEARCH" }

        val searchTyped = !showAllSettings && query.trim().isNotBlank()
        val searchCommitted = !showAllSettings && committedQuery.isNotBlank()
        var releaseSearchResults by remember { mutableStateOf(false) }

        LaunchedEffect(showAllSettings, query, committedQuery) {
            releaseSearchResults = false
            if (showAllSettings) {
                releaseSearchResults = true
            } else if (query.trim().isNotBlank() && committedQuery.isNotBlank()) {
                // Search has a two-stage choreography:
                // 1) title + field glide from center to top
                // 2) only then do the matching cards cascade underneath
                delay(if (animationLevel == 0) 230 else if (animationLevel == 1) 150 else 0)
                releaseSearchResults = true
            }
        }

        val resultTargetReady = showAllSettings || (!showAllSettings && searchCommitted && releaseSearchResults)
        val targetResultIds = if (resultTargetReady) results.map { it.id } else emptyList()
        val targetShowsNoResults =
            resultTargetReady && !showAllSettings && query.trim().isNotBlank() && results.isEmpty()
        val targetResultsKey = when {
            !resultTargetReady -> "hidden"
            targetShowsNoResults -> "empty:${committedQuery}"
            else -> "items:${targetResultIds.joinToString("|")}"
        }
        var displayedResultsKey by remember { mutableStateOf("hidden") }
        var displayedResultIds by remember { mutableStateOf<List<String>>(emptyList()) }
        var displayedShowsNoResults by remember { mutableStateOf(false) }
        var resultSetVisible by remember { mutableStateOf(false) }

        LaunchedEffect(visible, targetResultsKey) {
            val exitWait = when (animationLevel) {
                2 -> 0L
                1 -> 150L
                else -> 220L
            }

            if (!visible) {
                resultSetVisible = false
                delay(exitWait)
                displayedResultsKey = "hidden"
                displayedResultIds = emptyList()
                displayedShowsNoResults = false
                return@LaunchedEffect
            }

            if (targetResultsKey == "hidden") {
                // While the header is moving, a fresh query is debouncing, or the
                // user clears the field, keep the old result set alive just long
                // enough to fade/scale away. The spotlight header checks
                // displayedResultsKey/resultSetVisible, so it will not glide back
                // to center until these cards are actually gone.
                resultSetVisible = false
                delay(exitWait)
                displayedResultsKey = "hidden"
                displayedResultIds = emptyList()
                displayedShowsNoResults = false
                return@LaunchedEffect
            }

            val hadDisplayedContent =
                displayedResultsKey != "hidden" && (displayedResultIds.isNotEmpty() || displayedShowsNoResults)
            if (!hadDisplayedContent) {
                displayedResultsKey = targetResultsKey
                displayedResultIds = targetResultIds
                displayedShowsNoResults = targetShowsNoResults
                resultSetVisible = true
            } else if (displayedResultsKey != targetResultsKey) {
                // Search result replacement choreography:
                // old cards fade/scale out -> data swaps -> new cards stagger in.
                resultSetVisible = false
                delay(exitWait)
                displayedResultsKey = targetResultsKey
                displayedResultIds = targetResultIds
                displayedShowsNoResults = targetShowsNoResults
                delay(if (animationLevel == 2) 0L else 35L)
                resultSetVisible = true
            } else {
                // Same result identity, but live card state may have changed.
                displayedResultIds = targetResultIds
                displayedShowsNoResults = targetShowsNoResults
                resultSetVisible = true
            }
        }

        val displayedResults = displayedResultIds.mapNotNull { itemById[it] }
        val searchResultsStillLeaving = !showAllSettings && (
                resultSetVisible ||
                        displayedResultsKey != "hidden" ||
                        displayedResults.isNotEmpty() ||
                        displayedShowsNoResults
                )
        val spotlightMode = !showAllSettings && query.trim().isBlank() && !searchResultsStillLeaving

        val resultsScrollState = rememberScrollState()
        LaunchedEffect(showAllSettings, committedQuery, results.size) {
            resultsScrollState.scrollTo(0)
        }

        if (!showAllSettings) {
            val searchActive = query.trim().isNotBlank() || committedQuery.isNotBlank() || searchResultsStillLeaving
            val density = LocalDensity.current
            val configuration = LocalConfiguration.current
            val headerProgress by animateFloatAsState(
                targetValue = if (spotlightMode) 0f else 1f,
                animationSpec = when (animationLevel) {
                    2 -> snap()
                    0 -> spring(dampingRatio = 0.54f, stiffness = MotionTokens.SpeedUtil.stiffness(410f, animSpeed))
                    else -> tween(
                        durationMillis = MotionTokens.SpeedUtil.durationMs(210, animSpeed),
                        easing = MotionTokens.Easing.emphasizedDecelerate
                    )
                },
                label = "search_spotlight_to_header_progress"
            )

            // Blank SEARCH must be centered from the very first frame.
            // The measured heights arrive one frame later via onGloballyPositioned;
            // if we start from 0px, the title appears at the top, then corrects
            // itself to the center. Seed the layout with a realistic screen-height
            // fallback so the first draw already matches the spotlight state.
            val fallbackSearchAreaHeightPx = remember(configuration.screenHeightDp, density) {
                with(density) { configuration.screenHeightDp.dp.toPx().roundToInt() }
            }
            var searchAreaHeightPx by remember(fallbackSearchAreaHeightPx) {
                mutableStateOf(fallbackSearchAreaHeightPx)
            }
            var searchHeaderHeightPx by remember { mutableStateOf(0) }
            val fallbackSearchHeaderHeight = if (isSmallScreen) 128.dp else 144.dp
            val centerSpacerDp by remember(
                searchAreaHeightPx,
                searchHeaderHeightPx,
                density,
                fallbackSearchHeaderHeight
            ) {
                derivedStateOf {
                    with(density) {
                        val areaPx = searchAreaHeightPx.coerceAtLeast(0)
                        val headerPx = if (searchHeaderHeightPx > 0) {
                            searchHeaderHeightPx
                        } else {
                            fallbackSearchHeaderHeight.toPx().roundToInt()
                        }
                        ((areaPx - headerPx).coerceAtLeast(0) / 2f).toDp()
                    }
                }
            }
            val normalPanelTitleTopSpacer = if (isLandscape) 24.dp else 40.dp
            val animatedTopSpacer by animateDpAsState(
                targetValue = if (spotlightMode) centerSpacerDp else normalPanelTitleTopSpacer,
                animationSpec = when (animationLevel) {
                    2 -> snap()
                    0 -> spring(dampingRatio = 0.54f, stiffness = MotionTokens.SpeedUtil.stiffness(410f, animSpeed))
                    else -> tween(
                        durationMillis = MotionTokens.SpeedUtil.durationMs(210, animSpeed),
                        easing = MotionTokens.Easing.emphasizedDecelerate
                    )
                },
                label = "search_spotlight_scroll_spacer"
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onGloballyPositioned { coordinates -> searchAreaHeightPx = coordinates.size.height }
                    .verticalScroll(resultsScrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 16.dp else 20.dp)
            ) {
                Spacer(modifier = Modifier.height(animatedTopSpacer.coerceAtLeast(0.dp)))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates -> searchHeaderHeightPx = coordinates.size.height }
                        .graphicsLayer {
                            val scale = 0.985f + ((1f - headerProgress) * 0.015f)
                            scaleX = scale
                            scaleY = scale
                            clip = false
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 16.dp else 20.dp)
                ) {
                    AnimatedSearchPanelTitle(
                        titleKey = panelTitleKey,
                        text = panelTitleText,
                        visible = visible,
                        fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge,
                        colors = colors,
                        scrollState = resultsScrollState
                    )

                    AnimatedElement(
                        visible = visible,
                        cardShadow = true,
                        staggerIndex = 1,
                        totalItems = displayedResults.size + 3
                    ) {
                        SearchInputCard(
                            query = query,
                            onQueryChange = { query = it },
                            colors = colors,
                            cardBackground = cardBackground,
                            isSmallScreen = isSmallScreen
                        )
                    }
                }

                if (displayedShowsNoResults) {
                    AnimatedElement(
                        visible = visible && resultSetVisible,
                        cardShadow = true,
                        staggerIndex = 2,
                        totalItems = 3
                    ) {
                        SettingsNavigationCard(
                            title = strings["search.no_results"].ifEmpty { "NO RESULTS" },
                            description = strings["search.no_results_desc"].ifEmpty { "Try: blur, theme, dark, matrix, particles, aggressive, notifications, gradient…" },
                            onClick = {},
                            isSmallScreen = isSmallScreen,
                            colors = colors,
                            cardBackground = cardBackground,
                            oledMode = oledMode
                        )
                    }
                } else {
                    displayedResults.forEachIndexed { index, item ->
                        AnimatedElement(
                            visible = visible && resultSetVisible,
                            cardShadow = false,
                            avoidBackButton = true,
                            staggerIndex = index + 2,
                            totalItems = displayedResults.size + 2,
                            enabled = item.enabledForShadow()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                PathLabel(item.path)
                                Box(modifier = Modifier.fillMaxWidth().directionalShadow(deferUntilSettled = true)) {
                                    item.render()
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(if (isSmallScreen) 88.dp else 96.dp))
            }
        } else {
            val normalPanelTitleTopSpacer = if (isLandscape) 24.dp else 40.dp

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(resultsScrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 16.dp else 20.dp)
            ) {
                Spacer(modifier = Modifier.height(normalPanelTitleTopSpacer))

                // GLOBAL must behave like a normal panel, not like fixed chrome.
                // Keep the title and caption inside the same scroll/avoidance flow
                // as the cards so nothing gets clipped behind the floating < button.
                AnimatedElement(
                    visible = visible,
                    cardShadow = false,
                    avoidBackButton = true,
                    staggerIndex = 0,
                    totalItems = displayedResults.size + 2
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 16.dp else 20.dp)
                    ) {
                        AnimatedSearchPanelTitle(
                            titleKey = panelTitleKey,
                            text = panelTitleText,
                            visible = visible,
                            fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge,
                            colors = colors,
                            scrollState = resultsScrollState
                        )

                        PanelCaption(
                            text = strings["global.caption"].ifEmpty { "Every setting in one flattened list. Browse everything without guessing the name." },
                            colors = colors
                        )
                    }
                }

                displayedResults.forEachIndexed { index, item ->
                    AnimatedElement(
                        visible = visible && resultSetVisible,
                        cardShadow = false,
                        avoidBackButton = true,
                        staggerIndex = index + 1,
                        totalItems = displayedResults.size + 2,
                        enabled = item.enabledForShadow()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            PathLabel(item.path)
                            Box(modifier = Modifier.fillMaxWidth().directionalShadow(deferUntilSettled = true)) {
                                item.render()
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(if (isSmallScreen) 88.dp else 96.dp))
            }
        }

    }
}
