package com.popovicialinc.gama

import android.app.Notification
import android.content.Context
import android.app.PendingIntent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import android.os.SystemClock
import kotlinx.coroutines.delay

/**
 * BootRendererWorker
 *
 * Scheduled by BootReceiver via WorkManager — survives process death and has
 * no ANR deadline, unlike goAsync() which gets killed after ~10 s.
 *
 * Retry policy: exponential backoff starting at 30 s, up to 5 attempts.
 * This covers the common case where Shizuku takes 60–120 s to start after
 * boot (wireless-debugging handshake, SystemUI init, etc.).
 *
 * On each attempt:
 *  1. Probe root, then poll Shizuku for up to 90 s (2 s interval).
 *  2. If ready → setprop → notify success → return SUCCESS.
 *  3. If not ready after 90 s → return RETRY (WorkManager reschedules).
 *  4. After all retries exhausted WorkManager gives up → notify failure.
 *
 * We deliberately do NOT write "OpenGL" to prefs on command failure so the UI keeps
 * showing the correct saved renderer rather than reverting unexpectedly.
 * The renderer pref is only corrected if we can actually verify via Shizuku
 * that the prop is still unset.
 */
class BootRendererWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val WORK_TAG = "gama_boot_renderer"
        // WorkManager retries until Result.failure() is returned.  We fire the
        // failure notification and stop retrying after this many attempts (0-indexed).
        private const val MAX_ATTEMPTS = 5
    }

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("gama_prefs", Context.MODE_PRIVATE)
        val savedRenderer = RendererState.getDesiredRenderer(prefs)

        if (savedRenderer != RendererState.RENDERER_VULKAN &&
            savedRenderer != RendererState.RENDERER_OPENGL
        ) return Result.success()

        // Root is checked first because it does not depend on Shizuku's daemon
        // settling after boot. If root is unavailable, wait for an already
        // authorised Shizuku backend. refreshRootAvailability() is the same
        // explicit backend probe used by the root access flow; a root manager
        // that has already granted GAMA access will answer without a prompt.
        val backendReady = ShizukuHelper.refreshRootAvailability() ||
            waitForShizuku(timeoutMs = 90_000L)

        if (!backendReady) {
            // Not ready yet — if we still have retries, WorkManager will reschedule.
            // Don't corrupt the prefs here; let the retry handle it.
            val isLastAttempt = runAttemptCount >= MAX_ATTEMPTS - 1
            if (isLastAttempt) {
                // All retries exhausted — give up and notify.
                // At this point the device is definitely post-boot and the
                // renderer property was never re-applied, so Android is using
                // its OpenGL default. Preserve Vulkan as the desired target,
                // but do not show it as the current renderer.
                RendererState.recordBootRestoreUnavailable(prefs)
                notifyBootResult(applicationContext, success = false, renderer = savedRenderer)
                RendererActionHistory.record(prefs, "Boot restore", savedRenderer, false, "No privileged backend became ready after boot.")
            }
            return if (isLastAttempt) Result.failure() else Result.retry()
        }

        // Shizuku binder responded, but give it a short settling window before
        // calling newProcess — the binder can ping OK ~300 ms before the remote
        // process spawner is actually accepting connections, causing setprop to
        // fail with a "broken pipe" error on the very first command.
        delay(500L)

        val result = RendererController.switch(
            applicationContext,
            RendererController.Request(savedRenderer, "Boot restore")
        )
        return if (result.verified) {
            notifyBootResult(applicationContext, success = true, renderer = savedRenderer)
            Result.success()
        } else {
            val isLastAttempt = runAttemptCount >= MAX_ATTEMPTS - 1
            if (isLastAttempt) {
                notifyBootResult(applicationContext, success = false, renderer = savedRenderer)
                RendererActionHistory.record(prefs, "Boot restore", savedRenderer, false, result.message)
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }

    /**
     * Poll Shizuku every 2 seconds until binder is up and permission is granted,
     * or until [timeoutMs] elapses.
     */
    private suspend fun waitForShizuku(timeoutMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (ShizukuHelper.checkBinder() && ShizukuHelper.checkPermission()) return true
            delay(2_000L)
        }
        return false
    }

    private fun notifyBootResult(context: Context, success: Boolean, renderer: String) {
        if (!ShizukuHelper.hasNotificationPermission(context)) return

        val prefs = context.getSharedPreferences("gama_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notif_enabled", false)) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                android.app.NotificationChannel(
                    "gama_boot",
                    localizedString(context, "notification", "channel_boot_name", "GAMA Boot Status"),
                    android.app.NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = localizedString(
                        context, "notification", "channel_boot_desc",
                        "Renderer re-apply status after reboot"
                    )
                }
            )
        }

        val (title, body) = if (success) {
            localizedString(context, "notification", "boot_restored_title", "GAMA ✓  %s restored")
                .replace("%s", renderer) to
                localizedString(
                    context, "notification", "boot_restored_body",
                    "%s renderer re-applied after reboot. Newly launched apps will use it."
                ).replace("%s", renderer)
        } else {
            localizedString(context, "notification", "boot_skipped_title", "GAMA · %s restore skipped")
                .replace("%s", renderer) to
                localizedString(
                    context, "notification", "boot_skipped_body",
                    "No privileged backend was ready after boot. Open GAMA and switch manually when you want."
                )
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        nm.notify(
            3001,
            android.app.Notification.Builder(context, "gama_boot")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(android.app.Notification.BigTextStyle().bigText(body))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
        )
    }
}
