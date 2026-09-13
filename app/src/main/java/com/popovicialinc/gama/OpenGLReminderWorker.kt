package com.popovicialinc.gama

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Keeps OpenGL reminders reliable when GAMA is not open. */
class OpenGLReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("gama_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notif_enabled", false) ||
            !ShizukuHelper.hasNotificationPermission(applicationContext)
        ) return Result.success()

        RendererState.reconcileRebootReset(prefs)
        if (RendererState.getRenderer(prefs) != RendererState.RENDERER_OPENGL ||
            RendererState.getDesiredRenderer(prefs) != RendererState.RENDERER_OPENGL
        ) return Result.success()

        val intervalHours = when (prefs.getInt("notif_interval_idx", 2)) {
            0 -> 2L
            1 -> 4L
            3 -> 12L
            4 -> 24L
            else -> 6L
        }
        val now = System.currentTimeMillis()
        val dueAt = prefs.getLong("notif_last_sent", 0L) + TimeUnit.HOURS.toMillis(intervalHours)
        if (now >= dueAt && sendOpenGLReminderNotification(
                applicationContext,
                prefs.getString("user_name", "") ?: ""
            )
        ) {
            prefs.edit().putLong("notif_last_sent", now).apply()
        }
        return Result.success()
    }
}

object OpenGLReminderScheduler {
    private const val WORK_NAME = "gama_opengl_reminder"

    fun sync(context: Context, enabled: Boolean? = null) {
        val workManager = WorkManager.getInstance(context)
        val notificationsEnabled = enabled ?: context
            .getSharedPreferences("gama_prefs", Context.MODE_PRIVATE)
            .getBoolean("notif_enabled", false)
        if (!notificationsEnabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<OpenGLReminderWorker>(15, TimeUnit.MINUTES).build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
