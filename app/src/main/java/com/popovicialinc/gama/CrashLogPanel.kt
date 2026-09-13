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



internal data class GamaCrashEntry(
    val timestamp: String,   // e.g. "2025-06-14 03:22:11"
    val thread: String,      // e.g. "main"
    val summary: String,     // first exception line, used as subtitle on list card
    val fullText: String     // the entire block text, saved verbatim to .txt
)

internal fun parseGamaCrashLog(raw: String): List<GamaCrashEntry> {
    if (raw.isBlank()) return emptyList()
    // Split on the separator line "── <timestamp> ──…"
    val sections = raw.split(Regex("(?=── \\d{4}-\\d{2}-\\d{2})"))
    return sections
        .filter { it.isNotBlank() }
        .mapNotNull { block ->
            val lines = block.lines()
            val headerLine = lines.firstOrNull()?.trim() ?: return@mapNotNull null
            // Extract timestamp from "── 2025-06-14 03:22:11 ──…"
            val ts = Regex("── (\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})")
                .find(headerLine)?.groupValues?.get(1) ?: "Unknown time"
            // Thread line is "Thread: <name>"
            val thread = lines.firstOrNull { it.startsWith("Thread:") }
                ?.removePrefix("Thread:")?.trim() ?: "unknown"
            // First exception or "at " line as the summary
            val summary = lines
                .firstOrNull { it.contains("Exception") || it.contains("Error:") || it.startsWith("\tat ") }
                ?.trim()
                ?: lines.firstOrNull { it.isNotBlank() && !it.startsWith("──") && !it.startsWith("Thread:") }
                    ?.trim()
                ?: "No details"
            GamaCrashEntry(
                timestamp = ts,
                thread    = thread,
                summary   = summary.take(160),
                fullText  = block.trim()
            )
        }
        // Newest first (the file is already written newest-first, but sort to be safe)
        .sortedByDescending { it.timestamp }
}

internal fun crashDateFromTimestamp(timestamp: String, fallback: String = "unknown date"): String {
    return timestamp.substringBefore(" ", fallback).ifBlank { fallback }
}

internal fun crashTimeFromTimestamp(timestamp: String, fallback: String = "unknown time"): String {
    return timestamp.substringAfter(" ", fallback).ifBlank { fallback }
}

internal fun crashDateFromMillis(timeMillis: Long, fallback: String = "unknown date"): String {
    return if (timeMillis > 0L) {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date(timeMillis))
    } else fallback
}

internal fun crashTimeFromMillis(timeMillis: Long, fallback: String = "unknown time"): String {
    return if (timeMillis > 0L) {
        java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date(timeMillis))
    } else fallback
}

@Composable
internal fun CrashLogEntryCard(
    title: String,
    sourceLabel: String,
    date: String,
    time: String,
    summary: String,
    onSave: () -> Unit,
    onView: () -> Unit,
    isSmallScreen: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    oledMode: Boolean
) {
    val ts = LocalTypeScale.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.15.dp, colors.primaryAccent.copy(alpha = 0.62f), RoundedCornerShape(28.dp))
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isSmallScreen) 18.dp else 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = title,
                            fontSize = ts.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = quicksandFontFamily,
                            color = colors.textPrimary,
                            letterSpacing = 1.1.sp,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = sourceLabel,
                            fontSize = ts.bodySmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = quicksandFontFamily,
                            color = colors.primaryAccent.copy(alpha = 0.72f),
                            letterSpacing = 1.4.sp,
                            maxLines = 1
                        )
                    }
                    Box(
                        modifier = Modifier
                            .border(1.dp, colors.primaryAccent.copy(alpha = 0.50f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = time.take(5),
                            fontSize = ts.bodySmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = quicksandFontFamily,
                            color = colors.textSecondary.copy(alpha = 0.90f),
                            maxLines = 1
                        )
                    }
                }

                Text(
                    text = date,
                    fontSize = ts.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = quicksandFontFamily,
                    color = colors.textSecondary
                )

                if (summary.isNotBlank()) {
                    Text(
                        text = summary,
                        fontSize = ts.bodySmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = quicksandFontFamily,
                        color = colors.textSecondary.copy(alpha = 0.82f),
                        maxLines = 3,
                        lineHeight = 18.sp
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FlatButton(
                        text = LocalStrings.current["crash_log.save_button"].ifEmpty { "SAVE" },
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                        accent = true,
                        colors = colors,
                        maxLines = 1,
                        oledMode = oledMode,
                        cornerRadius = 20.dp
                    )
                    FlatButton(
                        text = LocalStrings.current["crash_log.view_button"].ifEmpty { "VIEW LOG" },
                        onClick = onView,
                        modifier = Modifier.weight(1f),
                        accent = false,
                        colors = colors,
                        maxLines = 1,
                        oledMode = oledMode,
                        cornerRadius = 20.dp
                    )
                }
            }
        }
    }
}

@Composable
fun CrashLogPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    oledMode: Boolean,
    onExportCrashLog: (content: String, fileName: String) -> Unit = { _, _ -> },
    onExportSupportBundle: () -> Unit = {}
) {
    val ts = LocalTypeScale.current
    val context = LocalContext.current

    var gamaCrashes by remember { mutableStateOf<List<GamaCrashEntry>>(emptyList()) }
    var rawLogExists by remember { mutableStateOf(false) }
    var systemCrashes by remember { mutableStateOf<List<ShizukuHelper.CrashEntry>>(emptyList()) }
    var systemCrashesLoading by remember { mutableStateOf(false) }
    var pendingSystemExport by remember { mutableStateOf<Pair<String, String>?>(null) }
    var systemLogsAvailable by remember { mutableStateOf(false) }

    // Which detail sub-panel is open: "gama:<index>" or "system:<index>" or null
    var openedEntryKey by remember { mutableStateOf<String?>(null) }

    // Re-read both sources every time the panel becomes visible
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        openedEntryKey = null

        // GAMA file log — read & parse on IO
        val rawText = withContext(Dispatchers.IO) {
            try {
                val f = java.io.File(context.filesDir, "crash_log.txt")
                if (f.exists() && f.length() > 0) f.readText() else ""
            } catch (_: Exception) { "" }
        }
        rawLogExists = rawText.isNotEmpty()
        gamaCrashes = parseGamaCrashLog(rawText)

        // Resolve backend state once per panel open instead of interrogating
        // binder/root state while composing every list section.
        systemLogsAvailable = withContext(Dispatchers.IO) {
            ShizukuHelper.isRootAvailable() ||
                (ShizukuHelper.checkBinder() && ShizukuHelper.checkPermission())
        }

        // System dropbox crashes are available through either already-approved
        // backend. This must not probe root or display an approval prompt.
        if (systemLogsAvailable) {
            systemCrashesLoading = true
            systemCrashes = withContext(Dispatchers.IO) {
                ShizukuHelper.fetchCrashLogs()
            }
            systemCrashesLoading = false
        }
    }

    // anyDetailOpen drives both the blur on the list panel AND the stagger
    // re-trigger: when it flips false the list cards see visible=true again
    // after having seen visible=false, so AnimatedElement re-runs its enter cascade.
    val anyDetailOpen = openedEntryKey != null

    pendingSystemExport?.let { (content, fileName) ->
        AlertDialog(
            onDismissRequest = { pendingSystemExport = null },
            title = { Text(LocalStrings.current["crash_log.export_system_title"].ifEmpty { "Export system diagnostic data?" }) },
            text = {
                Text(LocalStrings.current["crash_log.export_system_body"].ifEmpty { "System crash logs can contain diagnostic information about other apps and your device. Review the file before sharing it." })
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingSystemExport = null
                    onExportCrashLog(content, fileName)
                }) { Text(LocalStrings.current["crash_log.export"].ifEmpty { "Export" }) }
            },
            dismissButton = {
                TextButton(onClick = { pendingSystemExport = null }) { Text(LocalStrings.current["dialogs.btn_cancel"].ifEmpty { "Cancel" }) }
            }
        )
    }

    // ── Detail panels (one per entry type) ───────────────────────────────────
    // Each detail panel gets its own BackHandler so the system back press only
    // closes the detail, never the whole crash log behind it.
    val openedGamaIndex = openedEntryKey?.takeIf { it.startsWith("gama:") }
        ?.substringAfter(":")?.toIntOrNull()
    val openedGamaEntry = openedGamaIndex?.let { gamaCrashes.getOrNull(it) }
    if (openedGamaIndex != null && openedGamaEntry != null) {
        BackHandler(enabled = true) { openedEntryKey = null }
        CrashDetailPanel(
            visible       = true,
            onDismiss     = { openedEntryKey = null },
            title         = "${LocalStrings.current["crash_log.gama_section"].ifEmpty { "GAMA LOGS" }.removeSuffix("S")} #${(openedGamaIndex + 1).toString().padStart(3, '0')}",
            date          = crashDateFromTimestamp(openedGamaEntry.timestamp, LocalStrings.current["crash_log.unknown_date"].ifEmpty { "unknown date" }),
            time          = crashTimeFromTimestamp(openedGamaEntry.timestamp, LocalStrings.current["crash_log.unknown_time"].ifEmpty { "unknown time" }),
            fullText      = openedGamaEntry.fullText,
            isSystemUI    = false,
            isSmallScreen = isSmallScreen,
            isLandscape   = isLandscape,
            colors        = colors,
            cardBackground = cardBackground,
            oledMode      = oledMode
        )
    }
    val openedSystemIndex = openedEntryKey?.takeIf { it.startsWith("system:") }
        ?.substringAfter(":")?.toIntOrNull()
    val openedSystemEntry = openedSystemIndex?.let { systemCrashes.getOrNull(it) }
    if (openedSystemIndex != null && openedSystemEntry != null) {
        BackHandler(enabled = true) { openedEntryKey = null }
        CrashDetailPanel(
            visible       = true,
            onDismiss     = { openedEntryKey = null },
            title         = "${LocalStrings.current["crash_log.system_section"].ifEmpty { "SYSTEM LOGS" }.removeSuffix("S")} #${(openedSystemIndex + 1).toString().padStart(3, '0')}",
            date          = crashDateFromMillis(openedSystemEntry.timeMillis, LocalStrings.current["crash_log.unknown_date"].ifEmpty { "unknown date" }),
            time          = crashTimeFromMillis(openedSystemEntry.timeMillis, LocalStrings.current["crash_log.unknown_time"].ifEmpty { "unknown time" }),
            fullText      = openedSystemEntry.fullText,
            isSystemUI    = openedSystemEntry.isSystemUI,
            isSmallScreen = isSmallScreen,
            isLandscape   = isLandscape,
            colors        = colors,
            cardBackground = cardBackground,
            oledMode      = oledMode
        )
    }

    // ── List panel ────────────────────────────────────────────────────────────
    // listItemsVisible flips false when a detail opens and back to true when it
    // closes — this is what makes AnimatedElement re-run its stagger cascade on
    // return, since LaunchedEffect(visible) only fires on value changes.
    val listItemsVisible = visible && !anyDetailOpen

    // Compute a stable total item count up-front so every AnimatedElement in
    // this panel shares the same value (required for the exit stagger to work).
    val gamaCount   = gamaCrashes.size
    val systemCount = if (!systemCrashesLoading && systemLogsAvailable)
        systemCrashes.size.coerceAtMost(20) else 0
    // Slots: 1 section header + gamaCount cards (or 1 empty card) + clear button
    //      + 1 section header + systemCount cards (or 1 status card)
    val emptyGamaSlots  = if (gamaCount == 0) 1 else gamaCount + 1   // cards + clear btn
    val systemSlots     = if (systemCount == 0) 1 else systemCount
    val totalListItems  = 1 + emptyGamaSlots + 1 + systemSlots        // two section headers

    PanelScaffold(
        visible   = visible && !anyDetailOpen,
        onDismiss = onDismiss,
        isLandscape   = isLandscape,
        isSmallScreen = isSmallScreen,
        isBlurred = false,
        oledMode  = oledMode,
        colors    = colors
    ) { _ ->
        CleanTitle(text = LocalStrings.current["crash_log.title"].ifEmpty { "LOGS" }, fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge, colors = colors)

        FlatButton(
            text = LocalStrings.current["crash_log.export_support_bundle"].ifEmpty { "Export support bundle" },
            onClick = onExportSupportBundle,
            modifier = Modifier.fillMaxWidth(),
            accent = true,
            colors = colors,
            maxLines = 1
        )

        // ── GAMA crashes section header ───────────────────────────────────────
        AnimatedElement(visible = listItemsVisible, staggerIndex = 0, totalItems = totalListItems) {
            Text(
                text = LocalStrings.current["crash_log.gama_section"].ifEmpty { "GAMA CRASHES" },
                fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp, fontFamily = quicksandFontFamily,
                color = colors.primaryAccent.copy(alpha = 0.7f)
            )
        }

        if (gamaCount == 0) {
            AnimatedElement(visible = listItemsVisible, staggerIndex = 1, totalItems = totalListItems) {
                Box(modifier = Modifier.fillMaxWidth()
                    .border(1.dp, colors.primaryAccent.copy(alpha = 0.55f), RoundedCornerShape(28.dp))
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = cardBackground),
                        shape = RoundedCornerShape(28.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Text(
                            text = LocalStrings.current["crash_log.no_gama_crashes"].ifEmpty { "No GAMA crashes recorded." },
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            fontSize = ts.labelSmall, fontFamily = quicksandFontFamily,
                            color = colors.textSecondary, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            gamaCrashes.forEachIndexed { idx, entry ->
                // staggerIndex: header=0, cards start at 1
                AnimatedElement(visible = listItemsVisible, staggerIndex = idx + 1, totalItems = totalListItems) {
                    CrashLogEntryCard(
                        title = "${LocalStrings.current["crash_log.gama_section"].ifEmpty { "GAMA LOGS" }.removeSuffix("S")} #${(idx + 1).toString().padStart(3, '0')}",
                        sourceLabel = LocalStrings.current["crash_log.app_crash"].ifEmpty { "APP CRASH" } + " • ${entry.thread.uppercase()}",
                        date = crashDateFromTimestamp(entry.timestamp, LocalStrings.current["crash_log.unknown_date"].ifEmpty { "unknown date" }),
                        time = crashTimeFromTimestamp(entry.timestamp, LocalStrings.current["crash_log.unknown_time"].ifEmpty { "unknown time" }),
                        summary = entry.summary.takeUnless { it == "No details" }
                            ?: LocalStrings.current["crash_log.no_details"].ifEmpty { "No details" },
                        onSave = { onExportCrashLog(entry.fullText, "GAMA_crash_${entry.timestamp.replace(" ", "_").replace(":", "-")}.txt") },
                        onView = { openedEntryKey = "gama:$idx" },
                        isSmallScreen = isSmallScreen,
                        colors = colors,
                        cardBackground = cardBackground,
                        oledMode = oledMode
                    )
                }
            }
            // Clear button sits right after the last card
            AnimatedElement(visible = listItemsVisible, staggerIndex = gamaCount + 1, totalItems = totalListItems) {
                FlatButton(
                    text = LocalStrings.current["crash_log.clear_all"].ifEmpty { "Clear All GAMA Logs" },
                    onClick = {
                        try {
                            java.io.File(context.filesDir, "crash_log.txt").delete()
                            gamaCrashes = emptyList()
                            rawLogExists = false
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.fillMaxWidth(),
                    accent = false, colors = colors, maxLines = 1
                )
            }
        }

        // ── System crashes section header ─────────────────────────────────────
        // Its staggerIndex is always right after all the gama slots
        val systemHeaderIdx = 1 + emptyGamaSlots
        AnimatedElement(visible = listItemsVisible, staggerIndex = systemHeaderIdx, totalItems = totalListItems) {
            Text(
                text = LocalStrings.current["crash_log.system_section"].ifEmpty { "SYSTEM CRASHES" },
                fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp, fontFamily = quicksandFontFamily,
                color = colors.primaryAccent.copy(alpha = 0.7f)
            )
        }

        when {
            systemCrashesLoading -> {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.primaryAccent, modifier = Modifier.size(28.dp))
                }
            }
            !systemLogsAvailable -> {
                AnimatedElement(visible = listItemsVisible, staggerIndex = systemHeaderIdx + 1, totalItems = totalListItems) {
                    Box(modifier = Modifier.fillMaxWidth()
                        .border(1.dp, colors.primaryAccent.copy(alpha = 0.55f), RoundedCornerShape(28.dp))
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = cardBackground),
                            shape = RoundedCornerShape(28.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Text(
                                text = LocalStrings.current["crash_log.shizuku_required"].ifEmpty { "Shizuku or approved root access is required to read system crash logs." },
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                fontSize = ts.labelSmall, fontFamily = quicksandFontFamily,
                                color = colors.textSecondary, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            systemCrashes.isEmpty() -> {
                AnimatedElement(visible = listItemsVisible, staggerIndex = systemHeaderIdx + 1, totalItems = totalListItems) {
                    Box(modifier = Modifier.fillMaxWidth()
                        .border(1.dp, colors.primaryAccent.copy(alpha = 0.55f), RoundedCornerShape(28.dp))
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = cardBackground),
                            shape = RoundedCornerShape(28.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Text(
                                text = LocalStrings.current["crash_log.no_system_crashes"].ifEmpty { "No relevant system crashes found." },
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                fontSize = ts.labelSmall, fontFamily = quicksandFontFamily,
                                color = colors.textSecondary, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            else -> {
                systemCrashes.take(20).forEachIndexed { idx, entry ->
                    val fileTimestamp = remember(entry.timeMillis) {
                        if (entry.timeMillis > 0L)
                            java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.US)
                                .format(java.util.Date(entry.timeMillis))
                        else "unknown"
                    }
                    AnimatedElement(
                        visible      = listItemsVisible,
                        staggerIndex = systemHeaderIdx + 1 + idx,
                        totalItems   = totalListItems
                    ) {
                        CrashLogEntryCard(
                            title = "${LocalStrings.current["crash_log.system_section"].ifEmpty { "SYSTEM LOGS" }.removeSuffix("S")} #${(idx + 1).toString().padStart(3, '0')}",
                            sourceLabel = entry.tag.uppercase(),
                            date = crashDateFromMillis(entry.timeMillis, LocalStrings.current["crash_log.unknown_date"].ifEmpty { "unknown date" }),
                            time = crashTimeFromMillis(entry.timeMillis, LocalStrings.current["crash_log.unknown_time"].ifEmpty { "unknown time" }),
                            summary = entry.summary.takeUnless { it == "No details" }
                                ?: LocalStrings.current["crash_log.no_details"].ifEmpty { "No details" },
                            onSave = {
                                pendingSystemExport = entry.fullText to
                                    "GAMA_system_crash_${entry.tag}_$fileTimestamp.txt"
                            },
                            onView = { openedEntryKey = "system:$idx" },
                            isSmallScreen = isSmallScreen,
                            colors = if (entry.isSystemUI)
                                colors.copy(border = colors.primaryAccent.copy(alpha = 0.4f))
                            else colors,
                            cardBackground = cardBackground,
                            oledMode = oledMode
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CrashDetailPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    date: String,
    time: String,
    fullText: String,
    isSystemUI: Boolean,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    oledMode: Boolean
) {
    val ts = LocalTypeScale.current
    val logScrollState = rememberScrollState()

    LaunchedEffect(visible, fullText) {
        if (visible) logScrollState.scrollTo(0)
    }

    PanelScaffold(
        visible   = visible,
        onDismiss = onDismiss,
        isLandscape   = isLandscape,
        isSmallScreen = isSmallScreen,
        oledMode  = oledMode,
        colors    = colors,
        reserveBackButtonSpace = true,
        contentAvoidsBackButton = false,
        contentScrollable = false
    ) { _ ->
        CleanTitle(
            text     = title,
            fontSize = if (isLandscape) ts.displaySmall else ts.displayMedium,
            colors   = colors
        )

        AnimatedElement(visible = visible, cardShadow = false, staggerIndex = 1, totalItems = 4) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = date,
                    fontSize = ts.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    fontFamily = quicksandFontFamily,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = time,
                    fontSize = ts.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    fontFamily = quicksandFontFamily,
                    color = colors.textSecondary.copy(alpha = 0.82f),
                    textAlign = TextAlign.Center
                )
            }
        }

        AnimatedElement(
            visible = visible,
            cardShadow = false,
                staggerIndex = 2,
                totalItems = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 1.25.dp,
                        color = colors.primaryAccent.copy(alpha = if (isSystemUI) 0.76f else 0.68f),
                        shape = RoundedCornerShape(28.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    text = fullText,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(logScrollState)
                        .padding(20.dp),
                    fontSize = ts.labelSmall,
                    fontFamily = quicksandFontFamily,
                    color = colors.textSecondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

