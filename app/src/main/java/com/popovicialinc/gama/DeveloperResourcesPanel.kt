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


@Composable
fun DeveloperPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    onTestNotification: () -> Unit,
    onTestBootNotification: () -> Unit,
    userName: String,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    oledMode: Boolean,
    performHaptic: () -> Unit,
    timeOffsetHours: Float,
    onTimeOffsetChange: (Float) -> Unit
) {
    val ts = LocalTypeScale.current
    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        oledMode = oledMode, colors = colors
    ) { _ ->
        CleanTitle(
            text = LocalStrings.current["text_catalog.developer"].ifEmpty { "DEVELOPER" },
            fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge,
            colors = colors
        )

        PanelCaption(
            text = LocalStrings.current["renderer.verbose_mode_desc"].ifEmpty { "A little playground for testing things" },
            colors = colors
        )

        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 1, totalItems = 4) {
            FlatButton(
                text = LocalStrings.current["notifications.send_test"].ifEmpty { "Send Test Notification" },
                onClick = { performHaptic(); onTestNotification() },
                modifier = Modifier.fillMaxWidth(),
                accent = false,
                colors = colors,
                maxLines = 1
            )
        }
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 2, totalItems = 4) {
            FlatButton(
                text = LocalStrings.current["notifications.boot_notification"].ifEmpty { "Send Boot Notification" },
                onClick = { performHaptic(); onTestBootNotification() },
                modifier = Modifier.fillMaxWidth(),
                accent = false,
                colors = colors,
                maxLines = 1
            )
        }
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 3, totalItems = 4) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${LocalStrings.current["text_catalog.time_offset_label"].ifEmpty { "TIME OFFSET:" }} ${if (timeOffsetHours >= 0) "+" else ""}${timeOffsetHours.toInt()}h",
                    fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp, fontFamily = quicksandFontFamily,
                    color = colors.primaryAccent.copy(alpha = 0.7f)
                )
                Text(
                    text = LocalStrings.current["particles.time_mode_desc"].ifEmpty { "Shift the clock that time-mode particles use — lets you preview dawn, dusk, or midnight without waiting" },
                    fontSize = ts.bodySmall, color = colors.textSecondary,
                    fontFamily = quicksandFontFamily, fontWeight = FontWeight.Bold
                )
                Slider(
                    value = timeOffsetHours, onValueChange = onTimeOffsetChange,
                    valueRange = -12f..12f, steps = 23,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.primaryAccent,
                        activeTrackColor = colors.primaryAccent,
                        inactiveTrackColor = colors.primaryAccent.copy(alpha = 0.2f)
                    )
                )
            }
        }
    }
}

@Composable
fun ResourcesPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    onLinkSelected: (url: String, label: String, description: String) -> Unit,
    onInfoRequested: (title: String, body: String, copyText: String?, guideUrl: String?) -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    isBlurred: Boolean,
    oledMode: Boolean
) {
    val ts = LocalTypeScale.current
    val context = LocalContext.current

    val strings = LocalStrings.current

    data class Link(val title: String, val desc: String, val url: String, val linkDesc: String)

    val links = listOf(
        Link(
            strings["integrations.github"].ifEmpty { "GITHUB" },
            strings["integrations.github_desc"].ifEmpty { "Source code, issue tracker, and the latest releases" },
            "https://github.com/popovicialinc/gama",
            strings["integrations.github_link_desc"].ifEmpty { "Opens the GAMA GitHub repository where you can browse source code, report issues, and download releases." }),
        Link(
            strings["integrations.discord"].ifEmpty { "DISCORD" },
            strings["integrations.discord_desc"].ifEmpty { "Chat with the GAMA community — get help, share feedback, and stay updated" },
            "https://discord.gg/YYXSedBAS9",
            strings["integrations.discord_link_desc"].ifEmpty { "Opens the official GAMA Discord server — ask questions, share feedback, and stay up to date." }),
        Link(
            strings["integrations.shizuku"].ifEmpty { "SHIZUKU" },
            strings["integrations.shizuku_desc"].ifEmpty { "Required for GAMA to function — install this first if you haven't already" },
            "https://shizuku.rikka.app",
            strings["integrations.shizuku_link_desc"].ifEmpty { "Opens the official Shizuku website. Shizuku is required for GAMA to execute renderer switching commands." })
    )
    // 3 link cards + 3 integration cards = 6 total
    val totalItems = links.size + 2

    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        isBlurred = isBlurred, oledMode = oledMode, colors = colors,
        rootExitCascade = true,
    ) { scrollState ->
        CleanTitle(
            text = LocalStrings.current["resources.title"]
                .ifEmpty { "Library" }
                .uppercase(Locale.ROOT),
            fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge,
            colors = colors, scrollState = scrollState
        )

        val cards = buildList<@Composable () -> Unit> {
            links.forEachIndexed { index, link -> add {
                AnimatedElement(visible = visible, cardShadow = true, staggerIndex = index + 1, totalItems = totalItems) {
                    SettingsNavigationCard(
                        title = link.title, description = link.desc,
                        onClick = { onLinkSelected(link.url, link.title, link.linkDesc) },
                        isSmallScreen = isSmallScreen, colors = colors,
                        cardBackground = cardBackground, oledMode = oledMode
                    )
                }
            } }
            add {
                AnimatedElement(visible = visible, cardShadow = true, staggerIndex = links.size + 1, totalItems = totalItems) {
            IntegrationInfoCard(
                title = LocalStrings.current["integrations.tasker"].ifEmpty { "TASKER" },
                description = LocalStrings.current["integrations.tasker_desc"].ifEmpty { "Automate renderer switching based on app launch, time, WiFi, or any Tasker trigger via broadcast intents" },
                statusLabel = strings["integrations.tasker_status"].ifEmpty { "Available" },
                statusOk = true,
                actionLabel = strings["integrations.tasker_action"].ifEmpty { "Set up" },
                onAction = {
                    onInfoRequested(
                        strings["integrations.tasker"].ifEmpty { "Tasker" },
                        strings["integrations.tasker_dialog_body"].ifEmpty { "Your personal token lets Tasker control GAMA while preventing other apps from changing your renderer. Copy it first, then open the step-by-step guide and enter it exactly where shown." },
                        TaskerAuth.getOrCreateToken(context),
                        "https://github.com/popovicialinc/gama/blob/main/!assets/GAMA_Tasker_Guide.pdf"
                    )
                },
                colors = colors, cardBackground = cardBackground,
                oledMode = oledMode, isSmallScreen = isSmallScreen
            )
                }
            }

            add {
                AnimatedElement(visible = visible, cardShadow = true, staggerIndex = links.size + 2, totalItems = totalItems) {
            val tileAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
            IntegrationInfoCard(
                title = LocalStrings.current["integrations.qs_tiles"].ifEmpty { "QUICK SETTINGS TILES" },
                description = LocalStrings.current["integrations.qs_tiles_desc"].ifEmpty { "One quick-settings tile that toggles between Vulkan and OpenGL — tap to switch" },
                statusLabel = if (tileAvailable) strings["integrations.qs_tiles_available"].ifEmpty { "1 tile" } else strings["integrations.qs_tiles_unavailable"].ifEmpty { "Requires Android 7+" },
                statusOk = tileAvailable,
                actionLabel = if (tileAvailable) strings["integrations.qs_tiles_action"].ifEmpty { "Library" } else null,
                onAction = if (tileAvailable) ({
                    onInfoRequested(
                        strings["integrations.qs_tiles_dialog_title"].ifEmpty { "Adding QS Tiles" },
                        strings["integrations.qs_tiles_dialog_body"].ifEmpty { "Pull down your notification shade and tap the Edit button (pencil icon). Scroll through the available tiles until you find the GAMA one. Drag it into your active area, then tap Done. Tap the tile to switch between Vulkan and OpenGL — the subtitle shows the current renderer." },
                        null,
                        null
                    )
                }) else null,
                colors = colors, cardBackground = cardBackground,
                oledMode = oledMode, isSmallScreen = isSmallScreen
            )
                }
            }

        }
        ResponsiveSettingsCardGrid(isLandscape = isLandscape, cards = cards)
    }
}

@Composable
fun VerbosePanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    verboseOutput: String,
    isSmallScreen: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    blurEnabled: Boolean,
    oledMode: Boolean
) {
    val ts = LocalTypeScale.current
    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = false, isSmallScreen = isSmallScreen,
        oledMode = oledMode, colors = colors,
        rootExitCascade = true,
    ) { _ ->
        CleanTitle(
            text = LocalStrings.current["renderer.verbose_mode"].ifEmpty { "VERBOSE OUTPUT" },
            fontSize = ts.displaySmall,
            colors = colors
        )

        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 1, totalItems = 1) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 260.dp, max = 460.dp)
                    .border(1.5.dp, colors.primaryAccent.copy(alpha = 0.70f), RoundedCornerShape(30.dp)),
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                shape = RoundedCornerShape(30.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.primaryAccent.copy(alpha = 0.08f))
                            .border(1.dp, colors.primaryAccent.copy(alpha = 0.28f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = LocalStrings.current["text_catalog.shell_output"].ifEmpty { "SHELL OUTPUT" },
                            fontSize = ts.bodySmall,
                            fontFamily = quicksandFontFamily,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp,
                            color = colors.primaryAccent.copy(alpha = 0.85f)
                        )
                        Text(
                            text = "${
                                verboseOutput.lines().filter { it.isNotBlank() }.size
                            } ${LocalStrings.current["text_catalog.lines_suffix"].ifEmpty { "LINES" }}",
                            fontSize = ts.bodySmall,
                            fontFamily = quicksandFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = colors.textSecondary.copy(alpha = 0.85f)
                        )
                    }

                    val innerScroll = rememberScrollState(Int.MAX_VALUE)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 190.dp, max = 360.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color.Black.copy(alpha = if (oledMode) 0.35f else 0.16f))
                            .border(1.dp, colors.primaryAccent.copy(alpha = 0.22f), RoundedCornerShape(22.dp))
                    ) {
                        Text(
                            text = verboseOutput
                                .replace("Output: Success", "Success")
                                .replace(
                                    "Output: Error: process hasn't exited",
                                    LocalStrings.current["system.cmd_running"].ifEmpty { "Command still running — waiting for shell output" }
                                )
                                .replace(
                                    "Output: Error: command timed out",
                                    LocalStrings.current["system.cmd_timeout"].ifEmpty { "Command timed out — Shizuku did not return output in time" }
                                )
                                .ifEmpty { LocalStrings.current["system.no_verbose_output"].ifEmpty { "No output yet. Run a renderer switch to see verbose logs." } },
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(innerScroll)
                                .padding(18.dp),
                            fontSize = ts.bodyMedium,
                            lineHeight = 22.sp,
                            fontFamily = quicksandFontFamily,
                            color = colors.textSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
