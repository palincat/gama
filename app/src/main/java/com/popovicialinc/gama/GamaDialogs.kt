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

// ── RootAccessDialog — shown when no backend is ready, offers Shizuku or root ─
@Composable
fun RootAccessDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onUseShizuku: () -> Unit,
    onUseRoot: () -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    backendName: String = "Shizuku"
) {
    val ts = LocalTypeScale.current
    BouncyDialog(visible = visible, onDismiss = onDismiss) {
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
                // Terminal prompt icon
                Canvas(modifier = Modifier.size(if (isSmallScreen) 56.dp else 64.dp)) {
                    val strokeWidth = 3.dp.toPx()
                    val cornerRadius = 12.dp.toPx()
                    val color = colors.primaryAccent
                    // Terminal window frame
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(size.width * 0.15f, size.height * 0.2f),
                        size = Size(size.width * 0.7f, size.height * 0.6f),
                        cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                        style = Stroke(width = strokeWidth)
                    )
                    // Header line
                    drawLine(
                        color = color,
                        start = Offset(size.width * 0.15f, size.height * 0.35f),
                        end = Offset(size.width * 0.85f, size.height * 0.35f),
                        strokeWidth = strokeWidth
                    )
                    // Root prompt "$" before cursor
                    val promptPath = Path().apply {
                        moveTo(size.width * 0.25f, size.height * 0.52f)
                        lineTo(size.width * 0.32f, size.height * 0.58f)
                        lineTo(size.width * 0.25f, size.height * 0.64f)
                    }
                    drawPath(
                        path = promptPath,
                        color = color,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    // Cursor line
                    drawLine(
                        color = color,
                        start = Offset(size.width * 0.37f, size.height * 0.58f),
                        end = Offset(size.width * 0.72f, size.height * 0.58f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = LocalStrings.current["dialogs.root_title"].ifEmpty { "Choose your access method" },
                        fontSize = ts.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = quicksandFontFamily,
                        color = colors.primaryAccent
                    )
                }

                Text(
                    text = LocalStrings.current["dialogs.root_body"]
                        .ifEmpty { "GAMA needs shell access to switch the renderer. Grant it via $backendName, or — if your device is rooted — use the root backend (Magisk / KernelSU)." }
                        .replace("Shizuku", backendName),
                    fontSize = ts.bodyLarge,
                    lineHeight = (ts.bodyLarge.value * 1.4f).sp,
                    color = colors.textPrimary.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth(),
                    fontFamily = quicksandFontFamily,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DialogButton(
                        text = LocalStrings.current["dialogs.btn_use_shizuku"]
                            .ifEmpty { "Use $backendName" }
                            .replace("Shizuku", backendName),
                        onClick = onUseShizuku,
                        modifier = Modifier.fillMaxWidth(),
                        colors = colors,
                        cardBackground = cardBackground,
                        accent = true
                    )
                    DialogButton(
                        text = LocalStrings.current["dialogs.btn_use_root"].ifEmpty { "Use Root (su)" },
                        onClick = onUseRoot,
                        modifier = Modifier.fillMaxWidth(),
                        colors = colors,
                        cardBackground = cardBackground,
                        accent = false
                    )
                    DialogButton(
                        text = LocalStrings.current["dialogs.btn_cancel"].ifEmpty { "Cancel" },
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = colors,
                        cardBackground = cardBackground,
                        accent = false
                    )
                }
            }
        }
    }
}


