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
import java.util.Locale



// ─────────────────────────────────────────────────────────────────────────────
// SettingsPanel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun VisualEffectsPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    themePreference: Int,
    onThemeChange: (Int) -> Unit,
    animationLevel: Int,
    onAnimationLevelChange: (Int) -> Unit,
    uiScale: Int,
    onUiScaleChange: (Int) -> Unit,
    userName: String,
    onUserNameChange: (String) -> Unit,
    oledMode: Boolean,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    staggerEnabled: Boolean,
    onStaggerEnabledChange: (Boolean) -> Unit,
    backButtonAvoidanceEnabled: Boolean,
    onBackButtonAvoidanceEnabledChange: (Boolean) -> Unit,
    shadowsEnabled: Boolean,
    onShadowsEnabledChange: (Boolean) -> Unit,
    onEffectsClick: () -> Unit,
    onColorsClick: () -> Unit,
    onOledModeChange: (Boolean) -> Unit,
    performHaptic: () -> Unit
) {
    val ts = LocalTypeScale.current

    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        oledMode = oledMode, colors = colors
    ) { scrollState ->
        CleanTitle(
            text = LocalStrings.current["particles.appearance_title"].ifEmpty { "VISUALS" },
            fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge,
            colors = colors, scrollState = scrollState
        )

        // EFFECTS and COLORS — at the top so they're always easy to reach
        ResponsiveSettingsCardGrid(
            isLandscape = isLandscape,
            cards = listOf(
                {
                    AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 1, totalItems = 8) {
                        SettingsNavigationCard(
                            title = LocalStrings.current["effects.title"].ifEmpty { "EFFECTS" },
                            description = LocalStrings.current["effects.effects_desc"].ifEmpty { "Background gradient, frosted glass blur, and floating particles" },
                            onClick = { performHaptic(); onEffectsClick() },
                            isSmallScreen = isSmallScreen, colors = colors,
                            cardBackground = cardBackground, oledMode = oledMode
                        )
                    }
                },
                {
                    AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 2, totalItems = 8) {
                        SettingsNavigationCard(
                            title = LocalStrings.current["colors.title"].ifEmpty { "COLORS" },
                            description = LocalStrings.current["colors.colors_desc"].ifEmpty { "Accent color, gradient palette, and Material You theming" },
                            onClick = { performHaptic(); onColorsClick() },
                            isSmallScreen = isSmallScreen, colors = colors,
                            cardBackground = cardBackground, oledMode = oledMode
                        )
                    }
                }
            )
        )

        // Theme — always available. Dark mode itself now uses pure OLED black.
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 3, totalItems = 8) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = colors.primaryAccent.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(28.dp))
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            shape = RoundedCornerShape(28.dp)
                            clip = false
                        },
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = LocalStrings.current["appearance.theme"].ifEmpty { "THEME" }, fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp, fontFamily = quicksandFontFamily,
                            color = colors.primaryAccent.copy(alpha = 0.7f)
                        )
                        GlideOptionSelector(
                            options = listOf(tr("appearance.theme_auto", "Auto"), tr("appearance.theme_dark", "Dark"), tr("appearance.theme_light", "Light")),
                            selectedIndex = themePreference,
                            onOptionSelected = { performHaptic(); onThemeChange(it) },
                            colors = colors, modifier = Modifier.fillMaxWidth(),
                            enabled = true
                        )
                    }
                }
            } // end Box
        }

        // Animations
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 4, totalItems = 8) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = colors.primaryAccent.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(28.dp))
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            shape = RoundedCornerShape(28.dp)
                            clip = false
                        },
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = LocalStrings.current["appearance.animations"].ifEmpty { "ANIMATIONS" }, fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp, fontFamily = quicksandFontFamily,
                            color = colors.primaryAccent.copy(alpha = 0.7f)
                        )
                        GlideOptionSelector(
                            options = listOf(tr("appearance.anim_full", "Full"), tr("appearance.anim_reduced", "Reduced"), tr("appearance.anim_off", "Off")),
                            selectedIndex = animationLevel,
                            onOptionSelected = { performHaptic(); onAnimationLevelChange(it) },
                            colors = colors, modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } // end Box
        }

        // UI Scale
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 5, totalItems = 8) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = colors.primaryAccent.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(28.dp))
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            shape = RoundedCornerShape(28.dp)
                            clip = false
                        },
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = LocalStrings.current["appearance.ui_scale"].ifEmpty { "UI SCALE" }, fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp, fontFamily = quicksandFontFamily,
                            color = colors.primaryAccent.copy(alpha = 0.7f)
                        )
                        GlideOptionSelector(
                            options = listOf(
                                LocalStrings.current["appearance.scale_75"].ifEmpty { "75%" },
                                LocalStrings.current["appearance.scale_100"].ifEmpty { "100%" },
                                LocalStrings.current["appearance.scale_125"].ifEmpty { "125%" }
                            ),
                            selectedIndex = uiScale,
                            onOptionSelected = { performHaptic(); onUiScaleChange(it) },
                            colors = colors, modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } // end Box
        }

        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 6, totalItems = 8) {
            ToggleCard(
                title = LocalStrings.current["appearance.stagger_animations"].ifEmpty { "STAGGER ANIMATIONS" },
                description = LocalStrings.current["appearance.stagger_animations_desc"].ifEmpty { "Panel cards animate in one by one — turn off for instant panel opens" },
                checked = staggerEnabled,
                onCheckedChange = { performHaptic(); onStaggerEnabledChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode
            )
        }

        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 7, totalItems = 8) {
            ToggleCard(
                title = LocalStrings.current["appearance.back_button_avoidance"].ifEmpty { "BACK BUTTON AVOIDANCE" },
                description = LocalStrings.current["appearance.back_button_avoidance_desc"].ifEmpty { "Cards duck left when the floating < button would overlap them. Turn off to let the button float above the UI." },
                checked = backButtonAvoidanceEnabled,
                onCheckedChange = { performHaptic(); onBackButtonAvoidanceEnabledChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode
            )
        }

        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 8, totalItems = 8) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = colors.primaryAccent.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(28.dp))
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            shape = RoundedCornerShape(28.dp)
                            clip = false
                        },
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = LocalStrings.current["appearance.your_name"].ifEmpty { "YOUR NAME" }, fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp, fontFamily = quicksandFontFamily,
                            color = colors.primaryAccent.copy(alpha = 0.7f)
                        )
                        Text(
                            text = LocalStrings.current["appearance.your_name_desc"].ifEmpty { "Used in greetings and notifications — completely optional" },
                            fontSize = ts.bodySmall, color = colors.textSecondary,
                            fontFamily = quicksandFontFamily, fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = userName,
                            onValueChange = { onUserNameChange(it) },
                            placeholder = {
                                Text(
                                    LocalStrings.current["appearance.your_name_placeholder"].ifEmpty { "e.g. Alex" },
                                    fontFamily = quicksandFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = ts.bodyMedium,
                                    color = colors.textSecondary.copy(alpha = 0.5f)
                                )
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = quicksandFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = ts.bodyMedium,
                                color = colors.textPrimary
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = colors.primaryAccent,
                                unfocusedBorderColor = colors.border.copy(alpha = 0.4f),
                                cursorColor          = colors.primaryAccent
                            ),
                            shape  = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// OLEDPanel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun GradientPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    gradientEnabled: Boolean,
    onGradientChange: (Boolean) -> Unit,
    customGradientStart: Color,
    onGradientStartChange: (Color) -> Unit,
    customGradientEnd: Color,
    onGradientEndChange: (Color) -> Unit,
    useDynamicColor: Boolean,
    advancedColorPicker: Boolean,
    oledMode: Boolean,
    darkModeActive: Boolean,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    performHaptic: () -> Unit
) {
    val ts = LocalTypeScale.current
    val dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val gradientAvailable = !darkModeActive
    val pickersEnabled = (!useDynamicColor || !dynamicColorAvailable) && gradientAvailable

    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        oledMode = oledMode, colors = colors
    ) { _ ->
        CleanTitle(
            text = LocalStrings.current["colors.gradient_background"].ifEmpty { "BACKGROUND GRADIENT" },
            fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge,
            colors = colors
        )

        // ── Enable toggle ─────────────────────────────────────────────────────
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 1, totalItems = 4, enabled = gradientAvailable) {
            val gradientCardScale by animateFloatAsState(
                targetValue = if (gradientAvailable) 1f else 0.80f,
                animationSpec = spring(
                    dampingRatio = MotionTokens.Springs.smooth.dampingRatio,
                    stiffness = MotionTokens.Springs.smooth.stiffness
                ),
                label = "gradient_card_dark_scale"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(
                        scaleX = gradientCardScale,
                        scaleY = gradientCardScale,
                        alpha = if (gradientAvailable) 1f else 0.50f
                    )
            ) {
                ToggleCard(
                    title = LocalStrings.current["effects.gradient_background"].ifEmpty { "GRADIENT BACKGROUND" },
                    description = when {
                        oledMode -> LocalStrings.current["effects.gradient_background_oled_desc"].ifEmpty { "Unavailable in dark mode — background is pure black" }
                        darkModeActive -> tr("text_catalog.unavailable_in_dark_mode_background_is_pure_black", "Unavailable in Dark mode — background is pure black")
                        else -> LocalStrings.current["effects.gradient_background_on_desc"].ifEmpty { "A slow-shifting color gradient behind your home screen" }
                    },
                    checked = gradientEnabled && gradientAvailable,
                    onCheckedChange = { if (gradientAvailable) { performHaptic(); onGradientChange(it) } },
                    colors = colors, cardBackground = cardBackground,
                    isSmallScreen = isSmallScreen, oledMode = oledMode,
                    enabled = gradientAvailable
                )
            }
        }

        // ── Gradient start color ───────────────────────────────────────────────
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 2, totalItems = 4,
            enabled = pickersEnabled) {
            CompactColorPickerCard(
                title = LocalStrings.current["colors.gradient_start"].ifEmpty { "GRADIENT START" },
                description = when {
                    oledMode -> LocalStrings.current["colors.gradient_start_oled_desc"].ifEmpty { "Disabled in dark mode" }
                    darkModeActive -> tr("text_catalog.disabled_in_dark_mode", "Disabled in Dark mode")
                    useDynamicColor && dynamicColorAvailable -> LocalStrings.current["colors.gradient_start_dynamic_desc"].ifEmpty { "Controlled by Dynamic Color — disable it to set a custom color" }
                    else -> LocalStrings.current["colors.gradient_start_desc"].ifEmpty { "The color the background gradient fades from at the top of the screen" }
                },
                currentColor = customGradientStart,
                onColorChange = onGradientStartChange,
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, isLandscape = isLandscape,
                advancedPicker = advancedColorPicker,
                enabled = pickersEnabled, oledMode = oledMode
            )
        }

        // ── Gradient end color ─────────────────────────────────────────────────
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 3, totalItems = 4,
            enabled = pickersEnabled) {
            CompactColorPickerCard(
                title = LocalStrings.current["colors.gradient_end"].ifEmpty { "GRADIENT END" },
                description = when {
                    oledMode -> LocalStrings.current["colors.gradient_end_oled_desc"].ifEmpty { "Disabled in dark mode" }
                    darkModeActive -> tr("text_catalog.disabled_in_dark_mode", "Disabled in Dark mode")
                    useDynamicColor && dynamicColorAvailable -> LocalStrings.current["colors.gradient_end_dynamic_desc"].ifEmpty { "Controlled by Dynamic Color — disable it to set a custom color" }
                    else -> LocalStrings.current["colors.gradient_end_desc"].ifEmpty { "The color the background gradient fades into at the bottom of the screen" }
                },
                currentColor = customGradientEnd,
                onColorChange = onGradientEndChange,
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, isLandscape = isLandscape,
                advancedPicker = advancedColorPicker,
                enabled = pickersEnabled, oledMode = oledMode
            )
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// FunctionalityPanel  — top-level nav hub
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RendererPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    aggressiveMode: Boolean,
    onAggressiveModeChange: (Boolean) -> Unit,
    killLauncher: Boolean,
    onKillLauncherChange: (Boolean) -> Unit,
    killKeyboard: Boolean,
    onKillKeyboardChange: (Boolean) -> Unit,
    showGpuWatchButton: Boolean,
    onShowGpuWatchButtonChange: (Boolean) -> Unit,
    onShowAggressiveWarning: () -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    oledMode: Boolean,
    performHaptic: () -> Unit
) {
    val ts = LocalTypeScale.current
    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        oledMode = oledMode, colors = colors,
    ) { _ ->
        CleanTitle(
            text = LocalStrings.current["renderer.title"]
                .ifEmpty { "RENDERER" }
                .uppercase(Locale.ROOT),
            fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge,
            colors = colors
        )

        ResponsiveSettingsCardGrid(
            isLandscape = isLandscape,
            cards = listOf(
                {
                    AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 1, totalItems = 4) {
                        ToggleCard(
                            title = LocalStrings.current["renderer.aggressive_mode"].ifEmpty { "AGGRESSIVE MODE" },
                            description = LocalStrings.current["renderer.aggressive_mode_desc"].ifEmpty { "Restarts third-party apps only so they pick up the renderer — read the warning first" },
                            checked = aggressiveMode,
                            onCheckedChange = { performHaptic(); onAggressiveModeChange(it) },
                            colors = colors, cardBackground = cardBackground,
                            isSmallScreen = isSmallScreen, oledMode = oledMode, accentBorder = true
                        )
                    }
                },
                {
                    AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 2, totalItems = 4) {
                        ToggleCard(
                            title = LocalStrings.current["renderer.kill_launcher"].ifEmpty { "RESTART LAUNCHER & SYSTEM UI" },
                            description = LocalStrings.current["renderer.kill_launcher_desc"].ifEmpty { "Restarts the launcher and System UI after switching so the new renderer applies to the system chrome too — leave off on Xiaomi / MIUI" },
                            checked = killLauncher,
                            onCheckedChange = { performHaptic(); onKillLauncherChange(it) },
                            colors = colors, cardBackground = cardBackground,
                            isSmallScreen = isSmallScreen, oledMode = oledMode, accentBorder = true
                        )
                    }
                },
                {
                    AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 3, totalItems = 4) {
                        ToggleCard(
                            title = LocalStrings.current["renderer.kill_keyboard"].ifEmpty { "RESTART KEYBOARD ON SWITCH" },
                            description = LocalStrings.current["renderer.kill_keyboard_desc"].ifEmpty { "Force-stops the currently selected keyboard after applying the renderer, so it reloads with the new graphics API" },
                            checked = killKeyboard,
                            onCheckedChange = { performHaptic(); onKillKeyboardChange(it) },
                            colors = colors, cardBackground = cardBackground,
                            isSmallScreen = isSmallScreen, oledMode = oledMode, accentBorder = true
                        )
                    }
                },
                {
                    AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 4, totalItems = 4) {
                        ToggleCard(
                            title = LocalStrings.current["renderer.show_gpuwatch_toggle"].ifEmpty { "GPUWATCH SHORTCUT" },
                            description = LocalStrings.current["renderer.show_gpuwatch_desc"].ifEmpty { "Adds an Open GPUWatch button on the main screen. Samsung devices only." },
                            checked = showGpuWatchButton,
                            onCheckedChange = { performHaptic(); onShowGpuWatchButtonChange(it) },
                            colors = colors, cardBackground = cardBackground,
                            isSmallScreen = isSmallScreen, oledMode = oledMode, accentBorder = true
                        )
                    }
                }
            )
        )
    }
}


// ─────────────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────────────────────
// MatrixSettingsPanel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MatrixSettingsPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    particlesEnabled: Boolean,
    onAppearanceClick: () -> Unit,
    onMotionClick: () -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    performHaptic: () -> Unit,
    oledMode: Boolean
) {
    val ts = LocalTypeScale.current
    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        oledMode = oledMode, colors = colors
    ) { _ ->
        CleanTitle(
            text     = LocalStrings.current["particles.matrix_settings_title"].ifEmpty { "MATRIX\nSETTINGS" },
            fontSize = if (isLandscape) ts.displaySmall else ts.displayMedium,
            colors   = colors
        )

        val navAlpha by animateFloatAsState(
            targetValue   = if (particlesEnabled) 1f else 0.50f,
            animationSpec = tween(280, easing = MotionTokens.Easing.silk),
            label         = "ms_nav_alpha"
        )

        ResponsiveSettingsCardGrid(isLandscape, listOf(
            {
                AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 1, totalItems = 2, enabled = particlesEnabled) {
                    Box(modifier = Modifier.fillMaxWidth().graphicsLayer(alpha = navAlpha)) {
                        SettingsNavigationCard(
                            title = LocalStrings.current["particles.glyph_settings_card_title"].ifEmpty { "GLYPH SETTINGS" },
                            description = LocalStrings.current["matrix.appearance_desc"].ifEmpty { "Glyph font size and background opacity" },
                            onClick = { if (particlesEnabled) { performHaptic(); onAppearanceClick() } },
                            isSmallScreen = isSmallScreen, colors = colors, cardBackground = cardBackground, oledMode = oledMode, enabled = particlesEnabled
                        )
                    }
                }
            },
            {
                AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 2, totalItems = 2, enabled = particlesEnabled) {
                    Box(modifier = Modifier.fillMaxWidth().graphicsLayer(alpha = navAlpha)) {
                        SettingsNavigationCard(
                            title = LocalStrings.current["particles.motion_title"].ifEmpty { "MOTION" },
                            description = LocalStrings.current["matrix.motion_desc"].ifEmpty { "Fall speed, column density, and trail fade length" },
                            onClick = { if (particlesEnabled) { performHaptic(); onMotionClick() } },
                            isSmallScreen = isSmallScreen, colors = colors, cardBackground = cardBackground, oledMode = oledMode, enabled = particlesEnabled
                        )
                    }
                }
            }
        ))
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// MatrixAppearancePanel  — colors, glyph size, background opacity
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MatrixAppearancePanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    particlesEnabled: Boolean,
    matrixFontSize: Int,
    onMatrixFontSizeChange: (Int) -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    performHaptic: () -> Unit,
    oledMode: Boolean
) {
    val ts = LocalTypeScale.current
    val enabled = particlesEnabled

    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        oledMode = oledMode, colors = colors
    ) { _ ->
        CleanTitle(
            text     = LocalStrings.current["particles.glyph_settings"].ifEmpty { "GLYPH\nSETTINGS" },
            fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge,
            colors   = colors
        )

        // ── Glyph size ────────────────────────────────────────────────────────
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 1, totalItems = 1, enabled = enabled) {
            val cardAlpha by animateFloatAsState(1f, tween(260, easing = MotionTokens.Easing.velvet), label = "ma_font_a")
            Box(
                modifier = Modifier.fillMaxWidth().graphicsLayer {
                    alpha = cardAlpha
                    scaleX = if (enabled) 1f else 0.90f
                    scaleY = if (enabled) 1f else 0.90f
                }
                    .then(if (enabled) Modifier.border(
                        width = 1.dp,
                        color = colors.primaryAccent.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(28.dp)) else Modifier)
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                }
                Card(
                    modifier = Modifier.fillMaxWidth().graphicsLayer { shape = RoundedCornerShape(28.dp); clip = false },
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    shape = RoundedCornerShape(28.dp), elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(LocalStrings.current["matrix.font_size"].ifEmpty { "GLYPH SIZE" },
                            fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp, fontFamily = quicksandFontFamily,
                            color = colors.primaryAccent.copy(alpha = 0.7f))
                        Text(LocalStrings.current["matrix.font_size_desc"].ifEmpty { "Small (11 sp) · Medium (15 sp) · Large (20 sp)" },
                            fontSize = ts.bodySmall, color = colors.textSecondary,
                            fontFamily = quicksandFontFamily, fontWeight = FontWeight.Bold)
                        GlideOptionSelector(
                            options = listOf(tr("text_catalog.small", "Small"), tr("particles.sensitivity_medium", "Medium"), tr("text_catalog.large", "Large")),
                            selectedIndex = matrixFontSize.coerceIn(0, 2),
                            onOptionSelected = { performHaptic(); onMatrixFontSizeChange(it) },
                            colors = colors, modifier = Modifier.fillMaxWidth(), enabled = enabled
                        )
                    }
                }
                DisabledCardWash(enabled, oledMode)
            }
        }

    }
}


// ─────────────────────────────────────────────────────────────────────────────
// MatrixMotionPanel  — speed, density, trail length
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MatrixMotionPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    particlesEnabled: Boolean,
    matrixSpeed: Int,
    onMatrixSpeedChange: (Int) -> Unit,
    matrixDensity: Int,
    onMatrixDensityChange: (Int) -> Unit,
    matrixFadeLength: Int,
    onMatrixFadeLengthChange: (Int) -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    performHaptic: () -> Unit,
    oledMode: Boolean
) {
    val ts = LocalTypeScale.current
    val enabled = particlesEnabled

    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        oledMode = oledMode, colors = colors
    ) { _ ->
        CleanTitle(
            text     = LocalStrings.current["particles.motion_title"].ifEmpty { "MOTION" },
            fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge,
            colors   = colors
        )

        // ── Speed ─────────────────────────────────────────────────────────────
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 1, totalItems = 4, enabled = enabled) {
            val cardAlpha by animateFloatAsState(1f, tween(260, easing = MotionTokens.Easing.velvet), label = "mm_speed_a")
            Box(
                modifier = Modifier.fillMaxWidth().graphicsLayer {
                    alpha = cardAlpha
                    scaleX = if (enabled) 1f else 0.90f
                    scaleY = if (enabled) 1f else 0.90f
                }
                    .then(if (enabled) Modifier.border(
                        width = 1.dp,
                        color = colors.primaryAccent.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(28.dp)) else Modifier)
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                }
                Card(
                    modifier = Modifier.fillMaxWidth().graphicsLayer { shape = RoundedCornerShape(28.dp); clip = false },
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    shape = RoundedCornerShape(28.dp), elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(LocalStrings.current["particles.speed"].ifEmpty { "SPEED" },
                            fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp, fontFamily = quicksandFontFamily,
                            color = colors.primaryAccent.copy(alpha = 0.7f))
                        Text(LocalStrings.current["matrix.speed_desc"].ifEmpty { "How fast characters fall down the screen" },
                            fontSize = ts.bodySmall, color = colors.textSecondary,
                            fontFamily = quicksandFontFamily, fontWeight = FontWeight.Bold)
                        GlideOptionSelector(
                            options = listOf(tr("particles.speed_slow", "Slow"), tr("particles.speed_medium", "Medium"), tr("particles.speed_fast", "Fast")),
                            selectedIndex = matrixSpeed.coerceIn(0, 2),
                            onOptionSelected = { performHaptic(); onMatrixSpeedChange(it) },
                            colors = colors, modifier = Modifier.fillMaxWidth(), enabled = enabled
                        )
                    }
                }
                DisabledCardWash(enabled, oledMode)
            }
        }

        // ── Density ───────────────────────────────────────────────────────────
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 2, totalItems = 4, enabled = enabled) {
            val cardAlpha by animateFloatAsState(1f, tween(260, easing = MotionTokens.Easing.velvet), label = "mm_den_a")
            Box(
                modifier = Modifier.fillMaxWidth().graphicsLayer {
                    alpha = cardAlpha
                    scaleX = if (enabled) 1f else 0.90f
                    scaleY = if (enabled) 1f else 0.90f
                }
                    .then(if (enabled) Modifier.border(
                        width = 1.dp,
                        color = colors.primaryAccent.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(28.dp)) else Modifier)
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                }
                Card(
                    modifier = Modifier.fillMaxWidth().graphicsLayer { shape = RoundedCornerShape(28.dp); clip = false },
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    shape = RoundedCornerShape(28.dp), elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(LocalStrings.current["matrix.density"].ifEmpty { "DENSITY" },
                            fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp, fontFamily = quicksandFontFamily,
                            color = colors.primaryAccent.copy(alpha = 0.7f))
                        Text(LocalStrings.current["matrix.density_desc"].ifEmpty { "How many columns of glyphs appear across the screen" },
                            fontSize = ts.bodySmall, color = colors.textSecondary,
                            fontFamily = quicksandFontFamily, fontWeight = FontWeight.Bold)
                        GlideOptionSelector(
                            options = listOf(tr("text_catalog.sparse", "Sparse"), tr("particles.sensitivity_medium", "Medium"), tr("text_catalog.dense", "Dense")),
                            selectedIndex = matrixDensity.coerceIn(0, 2),
                            onOptionSelected = { performHaptic(); onMatrixDensityChange(it) },
                            colors = colors, modifier = Modifier.fillMaxWidth(), enabled = enabled
                        )
                    }
                }
                DisabledCardWash(enabled, oledMode)
            }
        }

        // ── Trail length ──────────────────────────────────────────────────────
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 3, totalItems = 4, enabled = enabled) {
            val cardAlpha by animateFloatAsState(1f, tween(260, easing = MotionTokens.Easing.velvet), label = "mm_trail_a")
            Box(
                modifier = Modifier.fillMaxWidth().graphicsLayer {
                    alpha = cardAlpha
                    scaleX = if (enabled) 1f else 0.90f
                    scaleY = if (enabled) 1f else 0.90f
                }
                    .then(if (enabled) Modifier.border(
                        width = 1.dp,
                        color = colors.primaryAccent.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(28.dp)) else Modifier)
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                }
                Card(
                    modifier = Modifier.fillMaxWidth().graphicsLayer { shape = RoundedCornerShape(28.dp); clip = false },
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    shape = RoundedCornerShape(28.dp), elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(LocalStrings.current["matrix.trail_length"].ifEmpty { "TRAIL LENGTH" },
                            fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp, fontFamily = quicksandFontFamily,
                            color = colors.primaryAccent.copy(alpha = 0.7f))
                        Text(LocalStrings.current["matrix.trail_length_desc"].ifEmpty { "How far the fading tail extends behind each falling column" },
                            fontSize = ts.bodySmall, color = colors.textSecondary,
                            fontFamily = quicksandFontFamily, fontWeight = FontWeight.Bold)
                        GlideOptionSelector(
                            options = listOf(tr("text_catalog.short", "Short"), tr("particles.sensitivity_medium", "Medium"), tr("appearance.anim_full", "Full")),
                            selectedIndex = matrixFadeLength.coerceIn(0, 2),
                            onOptionSelected = { performHaptic(); onMatrixFadeLengthChange(it) },
                            colors = colors, modifier = Modifier.fillMaxWidth(), enabled = enabled
                        )
                    }
                }
                DisabledCardWash(enabled, oledMode)
            }
        }
    }
}



// ─────────────────────────────────────────────────────────────────────────────

