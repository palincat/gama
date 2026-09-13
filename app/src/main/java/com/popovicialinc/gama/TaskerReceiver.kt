package com.popovicialinc.gama

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

/**
 * Tasker entry point. Configure Tasker's Send Intent action with:
 *
 * Package: com.popovicialinc.gama
 * Class: com.popovicialinc.gama.TaskerReceiver
 * Action: com.popovicialinc.gama.ACTION_SET_RENDERER
 * Target: Broadcast Receiver
 * Extras: renderer:vulkan (or opengl), token:<GAMA Tasker token>
 *         aggressive:true (optional)
 */
class TaskerReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SET_RENDERER = "com.popovicialinc.gama.ACTION_SET_RENDERER"
        const val EXTRA_RENDERER = "renderer"
        const val EXTRA_AGGRESSIVE = "aggressive"
        const val EXTRA_TOKEN = "token"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SET_RENDERER) return
        if (!TaskerAuth.isValid(context, intent.getStringExtra(EXTRA_TOKEN))) return

        val target = when (intent.getStringExtra(EXTRA_RENDERER)?.lowercase()?.trim()) {
            "vulkan" -> RendererState.RENDERER_VULKAN
            "opengl" -> RendererState.RENDERER_OPENGL
            else -> {
                Toast.makeText(
                    context,
                    localizedString(context, "dialogs", "tasker_bad_renderer", "GAMA: use renderer:vulkan or renderer:opengl"),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
        }

        val request = OneTimeWorkRequestBuilder<TaskerRendererWorker>()
            .setInputData(workDataOf(
                TaskerRendererWorker.INPUT_RENDERER to target,
                TaskerRendererWorker.INPUT_AGGRESSIVE to intent.getBooleanExtra(EXTRA_AGGRESSIVE, false)
            ))
            .build()

        // A newer automation request supersedes an older pending renderer change.
        WorkManager.getInstance(context).enqueueUniqueWork(
            TaskerRendererWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
