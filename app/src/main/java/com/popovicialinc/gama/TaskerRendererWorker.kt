package com.popovicialinc.gama

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Executes Tasker renderer changes outside BroadcastReceiver's ANR deadline. */
class TaskerRendererWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val WORK_NAME = "gama_tasker_renderer"
        const val INPUT_RENDERER = "renderer"
        const val INPUT_AGGRESSIVE = "aggressive"
    }

    override suspend fun doWork(): Result {
        val target = inputData.getString(INPUT_RENDERER) ?: return Result.failure()
        val prefs = applicationContext.getSharedPreferences("gama_prefs", Context.MODE_PRIVATE)
        // Automations are conservative by default. They never inherit the
        // interactive UI's restart settings; only an explicit Tasker aggressive
        // extra can restart third-party apps.
        val aggressive = inputData.getBoolean(INPUT_AGGRESSIVE, false)
        val excludedApps = prefs.getStringSet("excluded_apps", emptySet()) ?: emptySet()

        if (!ShizukuHelper.isBackendReady()) {
            RendererActionHistory.record(prefs, "Tasker", target, false, "No privileged backend is available.")
            return Result.failure()
        }

        val result = RendererController.switch(
            applicationContext,
            RendererController.Request(
                target = target,
                source = "Tasker",
                aggressiveMode = aggressive,
                excludedApps = excludedApps
            )
        )
        if (!result.verified) {
            RendererActionHistory.record(prefs, "Tasker", target, false, "The renderer property could not be verified.")
            return Result.failure()
        }

        return Result.success()
    }
}
