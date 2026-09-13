package com.popovicialinc.gama

import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

// ── Tile localization helper ──────────────────────────────────────────────────
private fun android.content.Context.tileStr(section: String, key: String, fallback: String): String {
    val code = getSharedPreferences("gama_prefs", android.content.Context.MODE_PRIVATE)
        .getString("selected_language", "en") ?: "en"
    return LocalizationManager.getStringBlocking(this, code, section, key, fallback)
}

/**
 * Single Quick Settings tile that switches between the two renderers —
 * replaces the old separate Vulkan and OpenGL tiles.
 *
 * Tap → Vulkan if currently OpenGL, OpenGL if currently Vulkan.
 * The subtitle always shows the current renderer plus a hint of the next one.
 */
@RequiresApi(Build.VERSION_CODES.N)
class RendererToggleTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onClick() {
        super.onClick()

        val prefs = getSharedPreferences("gama_prefs", Context.MODE_PRIVATE)
        RendererState.reconcileRebootReset(prefs)
        val current = RendererState.getRenderer(prefs)
        val targetVulkan = current != RendererState.RENDERER_VULKAN
        val targetName = if (targetVulkan) RendererState.RENDERER_VULKAN else RendererState.RENDERER_OPENGL

        scope.launch {
            // Tapping a system surface must not trigger an unexpected Magisk /
            // KernelSU prompt. Root is usable here only when this process already
            // has a cached, user-approved root backend.
            if (!ShizukuHelper.isBackendReady()) {
                RendererActionHistory.record(prefs, "Quick Settings", targetName, false, "No privileged backend is available.")
                val backendName = ShizukuBackend.installed(applicationContext)?.displayName ?: "Shizuku"
                setTile(
                    Tile.STATE_INACTIVE,
                    applicationContext.tileStr("tile", "state_backend_not_running", "%s isn't running")
                        .replace("%s", backendName)
                )
                return@launch
            }

            setTile(Tile.STATE_ACTIVE, applicationContext.tileStr("tile", "state_switching", "Switching…"))

            try {
                val result = RendererController.switch(
                    applicationContext,
                    RendererController.Request(targetName, "Quick Settings")
                )
                if (result.verified) {
                    setTile(Tile.STATE_ACTIVE, null)
                } else {
                    RendererActionHistory.record(prefs, "Quick Settings", targetName, false, "The renderer property could not be verified.")
                    setTile(
                        Tile.STATE_INACTIVE,
                        applicationContext.tileStr("tile", "state_failed", "Failed — tap to retry")
                    )
                }
            } catch (_: Exception) {
                RendererActionHistory.record(prefs, "Quick Settings", targetName, false, "The renderer command failed unexpectedly.")
                setTile(Tile.STATE_INACTIVE, applicationContext.tileStr("tile", "state_failed", "Failed — tap to retry"))
            }
        }
    }

    private fun setTile(state: Int, subtitle: String?) {
        val tile = qsTile ?: return
        tile.label = applicationContext.tileStr("tile", "label", "GAMA · Renderer")
        tile.state = state
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle ?: currentRendererSubtitle()
        }
        tile.updateTile()
    }

    private fun currentRendererSubtitle(): String {
        val prefs = getSharedPreferences("gama_prefs", Context.MODE_PRIVATE)
        RendererState.reconcileRebootReset(prefs)
        val renderer = RendererState.getRenderer(prefs)
        return if (renderer == RendererState.RENDERER_VULKAN) {
            applicationContext.tileStr("tile", "state_vulkan", "Vulkan · tap for OpenGL")
        } else {
            applicationContext.tileStr("tile", "state_opengl", "OpenGL · tap for Vulkan")
        }
    }

    private fun refreshTile() {
        setTile(Tile.STATE_ACTIVE, null)
    }
}
