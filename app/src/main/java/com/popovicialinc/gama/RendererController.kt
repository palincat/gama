package com.popovicialinc.gama

import android.content.Context
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one transaction boundary for renderer mutations.
 *
 * A property change, its read-back verification, and the durable state commit
 * must be indivisible from the perspective of another GAMA entry point.  In
 * particular, the commit happens before optional process restarts, which may
 * take SystemUI (and this process) down.
 */
object RendererController {
    private val switchMutex = Mutex()

    data class Request(
        val target: String,
        val source: String,
        val aggressiveMode: Boolean = false,
        val killLauncher: Boolean = false,
        val killKeyboard: Boolean = false,
        val excludedApps: Set<String> = emptySet()
    )

    data class Result(
        val target: String,
        val previous: String,
        val verified: Boolean,
        val message: String,
        val phase: RendererSwitchStateMachine.Phase
    )

    suspend fun switch(
        context: Context,
        request: Request,
        onStatusUpdate: (String) -> Unit = {},
        onVerboseOutput: ((String) -> Unit)? = null
    ): Result = switchMutex.withLock {
        // WorkManager may cancel a superseded request. Once setprop starts, do
        // not allow cancellation to split the property from its persisted state.
        withContext(NonCancellable) {
            val prefs = context.getSharedPreferences("gama_prefs", Context.MODE_PRIVATE)
            val previous = RendererState.getRenderer(prefs)
            val transaction = RendererSwitchStateMachine(request.target, previous)
            transaction.request()
            if (request.target != RendererState.RENDERER_VULKAN &&
                request.target != RendererState.RENDERER_OPENGL
            ) {
                transaction.fail("Unknown renderer request.")
                return@withContext Result(request.target, previous, false, "Unknown renderer request.", transaction.phase)
            }
            if (!ShizukuHelper.isBackendReady()) {
                transaction.fail("No privileged backend is available.")
                return@withContext Result(request.target, previous, false, "No privileged backend is available.", transaction.phase)
            }

            transaction.applying()
            val applied = ShizukuHelper.applyRenderer(
                target = request.target,
                context = context,
                aggressiveMode = request.aggressiveMode,
                killLauncher = request.killLauncher,
                killKeyboard = request.killKeyboard,
                excludedApps = request.excludedApps,
                onStatusUpdate = onStatusUpdate,
                onVerboseOutput = onVerboseOutput,
                onVerified = {
                    transaction.verifying()
                    transaction.verified()
                    RendererState.recordSwitch(prefs, request.target)
                }
            )
            if (!applied) {
                transaction.fail("The renderer property could not be verified.")
                return@withContext Result(
                    request.target, previous, false,
                    "The renderer property could not be verified.", transaction.phase
                )
            }

            transaction.complete()
            ShizukuHelper.refreshRendererViewSync(context)
            val message = "Renderer switch completed."
            RendererActionHistory.record(prefs, request.source, request.target, true, message)
            onStatusUpdate(
                localizedString(context, "renderer", "status_commands_executed", "%s commands executed!")
                    .replace("%s", request.target)
            )
            Result(request.target, previous, true, message, transaction.phase)
        }
    }
}
