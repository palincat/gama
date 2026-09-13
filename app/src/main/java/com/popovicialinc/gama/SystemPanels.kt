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
import androidx.compose.foundation.gestures.detectDragGestures
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



@Composable
fun SystemPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    onHapticsClick: () -> Unit,
    verboseMode: Boolean,
    onVerboseModeChange: (Boolean) -> Unit,
    dismissOnClickOutside: Boolean,
    onDismissOnClickOutsideChange: (Boolean) -> Unit,
    backButtonInversed: Boolean,
    onBackButtonInversedChange: (Boolean) -> Unit,
    onResetFloatingButtonPositions: () -> Unit,
    onNotificationsClick: () -> Unit,
    onBackupClick: () -> Unit,
    onCrashLogClick: () -> Unit,
    onLanguageClick: () -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    oledMode: Boolean,
    performHaptic: () -> Unit
) {
    val ts = LocalTypeScale.current
    val strings = LocalStrings.current
    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        oledMode = oledMode, colors = colors
    ) { _ ->
        CleanTitle(
            text = strings["system.title"].ifEmpty { "SYSTEM" },
            fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge,
            colors = colors
        )

        // Haptics belongs to the system/input feedback section and returns to
        // this panel when dismissed.
        ResponsiveSettingsCardGrid(
            isLandscape = isLandscape,
            cards = listOf(
                {
                    AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 1, totalItems = 8) {
                        SettingsNavigationCard(
                            title = strings["settings.haptics"].ifEmpty { "HAPTICS" },
                            description = strings["settings.haptics_desc"].ifEmpty { "Vibration feedback patterns and strengths" },
                            onClick = { performHaptic(); onHapticsClick() },
                            isSmallScreen = isSmallScreen, colors = colors, cardBackground = cardBackground, oledMode = oledMode
                        )
                    }
                },
                {
                    AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 2, totalItems = 8) {
                        SettingsNavigationCard(
                            title = strings["system.notifications"].ifEmpty { "NOTIFICATIONS" },
                            description = strings["system.notifications_desc"].ifEmpty { "Reminder alerts if you've left OpenGL running longer than intended" },
                            onClick = { performHaptic(); onNotificationsClick() },
                            isSmallScreen = isSmallScreen, colors = colors, cardBackground = cardBackground, oledMode = oledMode
                        )
                    }
                },
                {
                    AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 3, totalItems = 8) {
                        SettingsNavigationCard(
                            title = strings["system.backup"].ifEmpty { "BACKUP & RESTORE" },
                            description = strings["system.backup_desc"].ifEmpty { "Export all settings to a file, or restore from a previous backup" },
                            onClick = { performHaptic(); onBackupClick() },
                            isSmallScreen = isSmallScreen, colors = colors, cardBackground = cardBackground, oledMode = oledMode
                        )
                    }
                },
                {
                    AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 4, totalItems = 8) {
                        SettingsNavigationCard(
                            title = strings["settings.language"].ifEmpty { "LANGUAGE" },
                            description = strings["settings.language_desc"].ifEmpty { "Change the display language used throughout the app" },
                            onClick = { performHaptic(); onLanguageClick() },
                            isSmallScreen = isSmallScreen, colors = colors, cardBackground = cardBackground, oledMode = oledMode
                        )
                    }
                },
                {
                    AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 5, totalItems = 8) {
                        SettingsNavigationCard(
                            title = strings["system.crash_log"].ifEmpty { "LOGS" },
                            description = strings["system.crash_log_desc"].ifEmpty { "View recent reports and copy details for troubleshooting" },
                            onClick = { performHaptic(); onCrashLogClick() },
                            isSmallScreen = isSmallScreen, colors = colors, cardBackground = cardBackground, oledMode = oledMode
                        )
                    }
                }
            )
        )
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 6, totalItems = 8) {
            ToggleCard(
                title = LocalStrings.current["renderer.verbose_mode"].ifEmpty { "VERBOSE OUTPUT" },
                description = LocalStrings.current["renderer.verbose_mode_desc"].ifEmpty { "Shows the full shell command output when switching renderers" },
                checked = verboseMode,
                onCheckedChange = { performHaptic(); onVerboseModeChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode,
                accentBorder = true
            )
        }
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 7, totalItems = 8) {
            ToggleCard(
                title = LocalStrings.current["renderer.tap_outside_to_close"].ifEmpty { "TAP OUTSIDE TO CLOSE" },
                description = LocalStrings.current["renderer.tap_outside_to_close_desc"].ifEmpty { "Tap anywhere outside an open panel to dismiss it — turn off to require the back button instead" },
                checked = dismissOnClickOutside,
                onCheckedChange = { performHaptic(); onDismissOnClickOutsideChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode,
                accentBorder = true
            )
        }
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 7, totalItems = 7) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.primaryAccent.copy(alpha = 0.55f), RoundedCornerShape(28.dp)),
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = strings["system.back_button_position"].ifEmpty { "BACK BUTTON POSITION" },
                        fontSize = ts.labelLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        fontFamily = quicksandFontFamily,
                        color = colors.primaryAccent.copy(alpha = 0.7f)
                    )
                    Text(
                        text = strings["system.back_button_position_desc"].ifEmpty { "Choose which side gets the floating < button. Search and Global move to the opposite side." },
                        fontSize = ts.bodySmall,
                        color = colors.textSecondary,
                        fontFamily = quicksandFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                    GlideOptionSelector(
                        options = listOf(
                            strings["system.back_button_position_normal"].ifEmpty { "Normal" },
                            strings["system.back_button_position_inversed"].ifEmpty { "Inverted" }
                        ),
                        selectedIndex = if (backButtonInversed) 1 else 0,
                        onOptionSelected = { index -> performHaptic(); onBackButtonInversedChange(index == 1) },
                        colors = colors,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 8, totalItems = 8) {
            Button(
                onClick = { performHaptic(); onResetFloatingButtonPositions() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primaryAccent.copy(alpha = 0.16f),
                    contentColor = colors.primaryAccent
                ),
                border = BorderStroke(1.dp, colors.primaryAccent.copy(alpha = 0.55f))
            ) {
                Text(
                    text = strings["system.reset_floating_buttons"].ifEmpty { "RESET FLOATING BUTTON POSITIONS" },
                    fontFamily = quicksandFontFamily,
                    fontSize = ts.labelLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }
        }
    }
}

private enum class FloatingAnchorSide { LEFT, RIGHT }

@Composable
private fun FloatingButtonPositionCard(
    leftX: Float,
    leftY: Float,
    rightX: Float,
    rightY: Float,
    onLeftPositionChange: (Float, Float) -> Unit,
    onRightPositionChange: (Float, Float) -> Unit,
    colors: ThemeColors,
    cardBackground: Color,
    oledMode: Boolean,
    strings: GamaStrings,
    typeScale: AdaptiveTypeScale
) {
    val density = LocalDensity.current
    var activeSide by remember { mutableStateOf<FloatingAnchorSide?>(null) }
    val shape = RoundedCornerShape(28.dp)
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, colors.primaryAccent.copy(alpha = 0.55f), shape),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = strings["system.floating_button_positions"].ifEmpty { "FLOATING BUTTON POSITIONS" },
                fontSize = typeScale.labelLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                fontFamily = quicksandFontFamily,
                color = colors.primaryAccent.copy(alpha = 0.7f)
            )
            Text(
                text = strings["system.floating_button_positions_desc"].ifEmpty {
                    "Drag each anchor. Every floating control on that half of the screen uses its exact position."
                },
                fontSize = typeScale.bodySmall,
                color = colors.textSecondary,
                fontFamily = quicksandFontFamily,
                fontWeight = FontWeight.Bold
            )
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(164.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (oledMode) Color(0xFF080808) else colors.primaryAccent.copy(alpha = 0.08f))
                    .border(1.dp, colors.primaryAccent.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                    .pointerInput(leftX, leftY, rightX, rightY) {
                        detectDragGestures(
                            onDragStart = { start ->
                                val halfWidth = size.width / 2f
                                val leftCenter = Offset(halfWidth * leftX, size.height * leftY)
                                val rightCenter = Offset(halfWidth + halfWidth * rightX, size.height * rightY)
                                val targetRadius = with(density) { 34.dp.toPx() }
                                activeSide = when {
                                    (start - leftCenter).getDistance() <= targetRadius -> FloatingAnchorSide.LEFT
                                    (start - rightCenter).getDistance() <= targetRadius -> FloatingAnchorSide.RIGHT
                                    else -> null
                                }
                            },
                            onDragEnd = { activeSide = null },
                            onDragCancel = { activeSide = null }
                        ) { change, _ ->
                            val side = activeSide ?: return@detectDragGestures
                            val halfWidth = size.width / 2f
                            val normalizedY = (change.position.y / size.height).coerceIn(0.08f, 0.92f)
                            if (side == FloatingAnchorSide.LEFT) {
                                onLeftPositionChange((change.position.x / halfWidth).coerceIn(0.16f, 0.84f), normalizedY)
                            } else {
                                onRightPositionChange(((change.position.x - halfWidth) / halfWidth).coerceIn(0.16f, 0.84f), normalizedY)
                            }
                        }
                    }
            ) {
                Box(
                    Modifier.align(Alignment.Center).fillMaxHeight().width(1.dp)
                        .background(colors.primaryAccent.copy(alpha = 0.35f))
                )
                FloatingAnchorMarker(
                    label = strings["system.floating_button_left"].ifEmpty { "LEFT" },
                    x = maxWidth * 0.5f * leftX,
                    y = maxHeight * leftY,
                    colors = colors
                )
                FloatingAnchorMarker(
                    label = strings["system.floating_button_right"].ifEmpty { "RIGHT" },
                    x = maxWidth * (0.5f + 0.5f * rightX),
                    y = maxHeight * rightY,
                    colors = colors
                )
            }
        }
    }
}

@Composable
private fun FloatingAnchorMarker(label: String, x: Dp, y: Dp, colors: ThemeColors) {
    Box(
        modifier = Modifier
            .offset(x = x - 24.dp, y = y - 24.dp)
            .size(48.dp)
            .clip(CircleShape)
            .background(colors.primaryAccent.copy(alpha = 0.22f))
            .border(1.5.dp, colors.primaryAccent.copy(alpha = 0.85f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.primaryAccent, fontFamily = quicksandFontFamily)
    }
}

@Composable
fun NotificationsPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    notificationsEnabled: Boolean,
    onNotificationsEnabledChange: (Boolean) -> Unit,
    notifIntervalIndex: Int,
    onNotifIntervalChange: (Int) -> Unit,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onTestNotification: () -> Unit,
    currentRenderer: String,
    userName: String,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    oledMode: Boolean
) {
    val ts = LocalTypeScale.current
    val intervalLabels = listOf("2 h", "4 h", "6 h", "12 h", "24 h")

    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        oledMode = oledMode, colors = colors
    ) { _ ->
        CleanTitle(text = LocalStrings.current["notifications.title"].ifEmpty { "ALERTS" }, fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge, colors = colors)

        PanelCaption(
            text = LocalStrings.current["notifications.subtitle"].ifEmpty { "GAMA can ping you if you've left OpenGL running and haven't switched back to Vulkan" },
            colors = colors
        )

        if (!hasPermission) {
            AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 1, totalItems = 5) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.25.dp, colors.errorColor.copy(alpha = 0.62f), RoundedCornerShape(28.dp)),
                    // This must remain opaque: a translucent warning card becomes
                    // unreadable over the animated background.
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(colors.errorColor.copy(alpha = 0.16f), CircleShape)
                                    .border(1.dp, colors.errorColor.copy(alpha = 0.58f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("!", color = colors.errorColor, fontWeight = FontWeight.ExtraBold, fontSize = ts.headlineSmall)
                            }
                            Text(
                                text = LocalStrings.current["notifications.permission_required"].ifEmpty { "ACCESS NEEDED" }, fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp, fontFamily = quicksandFontFamily, color = colors.errorColor
                            )
                        }
                        Text(
                            text = LocalStrings.current["notifications.permission_required_desc"].ifEmpty { "GAMA needs permission to send you notifications — tap below to grant it." },
                            fontSize = ts.bodyMedium, color = colors.textSecondary,
                            fontFamily = quicksandFontFamily, fontWeight = FontWeight.Bold
                        )
                        FlatButton(
                            text = LocalStrings.current["notifications.grant_permission"].ifEmpty { "Grant Permission" }, onClick = onRequestPermission,
                            modifier = Modifier.fillMaxWidth(), accent = true, colors = colors, maxLines = 1,
                            cornerRadius = 16.dp
                        )
                    }
                }
            }
        }

        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 2, totalItems = 4, enabled = hasPermission) {
            ToggleCard(
                title = LocalStrings.current["notifications.reminders"].ifEmpty { "REMINDERS" },
                description = if (currentRenderer == "OpenGL") tr("text_catalog.you_re_on_opengl_right_now_reminder_is_active", "You're on OpenGL right now — reminder is active") else tr("notifications.reminders_off_desc", "Sends an alert if you switch to OpenGL and forget to switch back"),
                checked = notificationsEnabled, onCheckedChange = onNotificationsEnabledChange,
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen,
                oledMode = oledMode, enabled = hasPermission
            )
        }
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 3, totalItems = 4, enabled = hasPermission && notificationsEnabled) {
            val intervalEnabled = hasPermission && notificationsEnabled
            val intervalScale by animateFloatAsState(
                targetValue = if (intervalEnabled) 1f else 0.90f,
                animationSpec = spring(
                    dampingRatio = MotionTokens.Springs.gentle.dampingRatio,
                    stiffness = MotionTokens.SpeedUtil.stiffness(MotionTokens.Springs.gentle.stiffness, LocalAnimationSpeed.current)
                ),
                label = "interval_scale"
            )
            val intervalAlpha by animateFloatAsState(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 300, easing = MotionTokens.Easing.velvet),
                label = "interval_alpha"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(scaleX = intervalScale, scaleY = intervalScale, alpha = intervalAlpha)
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
                        .then(if (!intervalEnabled) Modifier.pointerInput(intervalEnabled) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                }
                            }
                        } else Modifier),
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = LocalStrings.current["notifications.interval"].ifEmpty { "REMINDER INTERVAL" }, fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp, fontFamily = quicksandFontFamily,
                            color = colors.primaryAccent.copy(alpha = 0.7f)
                        )
                        GlideOptionSelector(
                            options = intervalLabels,
                            selectedIndex = notifIntervalIndex.coerceIn(0, intervalLabels.size - 1),
                            onOptionSelected = onNotifIntervalChange,
                            colors = colors, modifier = Modifier.fillMaxWidth(),
                            enabled = true
                        )
                    }
                }
                DisabledCardWash(intervalEnabled, oledMode)
            }
        }
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 4, totalItems = 4, enabled = hasPermission) {
            val testBtnEnabled = hasPermission
            val testBtnScale by animateFloatAsState(
                targetValue = if (testBtnEnabled) 1f else 0.90f,
                animationSpec = spring(
                    dampingRatio = MotionTokens.Springs.gentle.dampingRatio,
                    stiffness = MotionTokens.SpeedUtil.stiffness(MotionTokens.Springs.gentle.stiffness, LocalAnimationSpeed.current)
                ),
                label = "test_btn_scale"
            )
            val testBtnAlpha by animateFloatAsState(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 300, easing = MotionTokens.Easing.velvet),
                label = "test_btn_alpha"
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(scaleX = testBtnScale, scaleY = testBtnScale, alpha = testBtnAlpha)
                    .border(1.dp, colors.primaryAccent.copy(alpha = 0.32f), RoundedCornerShape(28.dp)),
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = LocalStrings.current["system.test_alert"].ifEmpty { "TEST ALERT" },
                        fontSize = ts.labelLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        fontFamily = quicksandFontFamily,
                        color = colors.primaryAccent.copy(alpha = 0.78f)
                    )
                    Text(
                        text = LocalStrings.current["system.test_alert_desc"].ifEmpty { "Send a sample alert to check that delivery is working." },
                        fontSize = ts.bodySmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = quicksandFontFamily,
                        color = colors.textSecondary
                    )
                    FlatButton(
                        text = LocalStrings.current["notifications.send_test"].ifEmpty { "SEND TEST ALERT" },
                        onClick = onTestNotification,
                        modifier = Modifier.fillMaxWidth(),
                        accent = true,
                        enabled = testBtnEnabled,
                        colors = colors,
                        maxLines = 1,
                        oledMode = oledMode,
                        cornerRadius = 16.dp
                    )
                }
                    DisabledCardWash(testBtnEnabled, oledMode)
                }
            }
        }
    }
}

@Composable
fun BackupPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    oledMode: Boolean
) {
    val ts = LocalTypeScale.current
    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        oledMode = oledMode, colors = colors
    ) { _ ->
        CleanTitle(text = LocalStrings.current["backup.title"].ifEmpty { "BACKUP" }, fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge, colors = colors)

        PanelCaption(
            text = LocalStrings.current["backup.subtitle"].ifEmpty { "Save all your GAMA settings to a file, or load them back from a previous backup — useful before reinstalling or switching devices" },
            colors = colors
        )

        ResponsiveSettingsCardGrid(isLandscape, listOf(
            {
                AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 1, totalItems = 2) {
                    SettingsNavigationCard(
                        title = LocalStrings.current["backup.export"].ifEmpty { "EXPORT BACKUP" }, description = LocalStrings.current["backup.export_desc"].ifEmpty { "Saves your theme, preferences, and excluded apps to a JSON file" },
                        onClick = onExport, isSmallScreen = isSmallScreen,
                        colors = colors, cardBackground = cardBackground, oledMode = oledMode
                    )
                }
            },
            {
                AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 2, totalItems = 2) {
                    SettingsNavigationCard(
                        title = LocalStrings.current["backup.restore"].ifEmpty { "RESTORE BACKUP" }, description = LocalStrings.current["backup.restore_desc"].ifEmpty { "Load a backup file to bring all your settings back exactly as they were" },
                        onClick = onImport, isSmallScreen = isSmallScreen,
                        colors = colors, cardBackground = cardBackground, oledMode = oledMode
                    )
                }
            }
        ))
    }
}

