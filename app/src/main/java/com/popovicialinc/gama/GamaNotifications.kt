package com.popovicialinc.gama

import android.app.Notification
import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
// Notifications: boot + OpenGL reminder senders
// ============================================================


// ── Localization helper for non-composable notification functions ─────────────
private fun Context.notifString(section: String, key: String, fallback: String): String {
    val code = getSharedPreferences("gama_prefs", android.content.Context.MODE_PRIVATE)
        .getString("selected_language", "en") ?: "en"
    return LocalizationManager.getStringBlocking(this, code, section, key, fallback)
}


fun sendBootNotification(
    context: Context,
    userName: String = "",
    onRequestPermission: () -> Unit = {}
) {
    // Check permission first
    if (!ShizukuHelper.hasNotificationPermission(context)) {
        // Request permission instead of silently failing
        onRequestPermission()
        return
    }

    val channelId = "gama_boot_channel"
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId,
            context.notifString("notification", "channel_alerts_name", "GAMA Notifications"),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.notifString("notification", "channel_alerts_desc", "Notifications from GAMA")
            enableVibration(true)
            enableLights(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    val title = if (userName.isNotEmpty()) context.notifString("notification","boot_title_named","Hey, $userName! 👋").replace("%s", userName) else context.notifString("notification","boot_title_unnamed","Hey! 👋")
    val bootBody = context.notifString("notification","boot_body","Is now a good time to switch to Vulkan?")

    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    val pendingIntent = PendingIntent.getActivity(
        context, 0, launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val builder = android.app.Notification.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(bootBody)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)

    try {
        notificationManager.notify(1001, builder.build())
    } catch (e: Exception) {
        // Handle failures gracefully
    }
}

// Send a periodic reminder when the renderer is still OpenGL
fun sendOpenGLReminderNotification(context: Context, userName: String = ""): Boolean {
    if (!ShizukuHelper.hasNotificationPermission(context)) return false

    val channelId = "gama_reminder_channel"
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val ch = NotificationChannel(
            channelId,
            context.notifString("notification", "channel_reminders_name", "Renderer Reminders"),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.notifString(
                "notification", "channel_reminders_desc",
                "Periodic reminders to switch to Vulkan when OpenGL is active"
            )
            enableVibration(false)
        }
        nm.createNotificationChannel(ch)
    }

    val title = if (userName.isNotEmpty()) context.notifString("notification","boot_title_named","Hey, $userName! 👋").replace("%s", userName) else context.notifString("notification","boot_title_unnamed","Hey! 👋")
    val messages = listOf(
        context.notifString("notification","reminder_msg_1","Is now a good time to switch to Vulkan? ⚡"),
        if (userName.isNotEmpty()) context.notifString("notification","reminder_msg_2_named","Still on OpenGL, $userName - Vulkan is ready whenever you are!").replace("%s",userName) else context.notifString("notification","reminder_msg_2_unnamed","Still on OpenGL - Vulkan is ready whenever you are!"),
        if (userName.isNotEmpty()) context.notifString("notification","reminder_msg_3_named","A small alert, $userName - you might get a speed boost from Vulkan!").replace("%s",userName) else context.notifString("notification","reminder_msg_3_unnamed","A small alert - you might get a speed boost from Vulkan!"),
        if (userName.isNotEmpty()) context.notifString("notification","reminder_msg_4_named","Psst, $userName… Vulkan is just one tap away").replace("%s",userName) else context.notifString("notification","reminder_msg_4_unnamed","Psst… Vulkan is just one tap away"),
        if (userName.isNotEmpty()) context.notifString("notification","reminder_msg_5_named","A friendly nudge, $userName - Vulkan could make things snappier!").replace("%s",userName) else context.notifString("notification","reminder_msg_5_unnamed","A friendly nudge - Vulkan could make things snappier!")
    )

    return try {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        nm.notify(
            2001,
            android.app.Notification.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(messages.random())
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
        )
        true
    } catch (e: Exception) { false }
}

// GrainEffect removed — was causing main-thread stall on first composition
