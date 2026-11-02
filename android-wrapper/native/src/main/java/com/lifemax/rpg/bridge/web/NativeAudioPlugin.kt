package com.lifemax.rpg.bridge.web

import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.lifemax.rpg.app.shell.NativeScreens
import com.lifemax.rpg.app.shell.audio.AudioAccept
import com.lifemax.rpg.app.shell.audio.AudioDockActions
import com.lifemax.rpg.app.shell.audio.AudioDockHost
import com.lifemax.rpg.app.shell.audio.AudioDockState

/**
 * The audiobook dock's bridge (not a tab; capability `shell.audio.v1`). The page keeps the player,
 * the file and the position (50-native-audio.js): it pushes the player's state with [state], tells
 * the plugin whether the owner switched the dock on with [configure] (and the page's accept list
 * for the file picker), and runs the dock's commands on its own functions. The dock itself is one
 * view the shell owns ([AudioDockHost]); this class only carries messages.
 */
@CapacitorPlugin(name = "NativeAudio")
class NativeAudioPlugin : Plugin() {
    @PluginMethod
    fun handshake(call: PluginCall) {
        call.resolve(JSObject().put("protocol", 1).put("capabilities", JSArray(listOf(CAPABILITY))))
    }

    /**
     * The owner's switch, and the page's accept list for the picker (an empty one changes nothing).
     */
    @PluginMethod
    fun configure(call: PluginCall) {
        val on = call.getBoolean("enabled")
        if (on == null) {
            call.reject("Missing enabled")
            return
        }
        val accept = call.getString("accept").orEmpty()
        activity.runOnUiThread {
            AudioDockHost.accept = if (on && accept.isNotBlank()) AudioAccept.of(accept) else null
            AudioDockHost.enable(activity, on)
            call.resolve()
        }
    }

    /** The page's player state (v1). Malformed state is rejected and the last good one kept. */
    @PluginMethod
    fun state(call: PluginCall) {
        val state =
            try {
                AudioDockState.decode(call.data, AudioDockHost.clock())
            } catch (e: Exception) {
                call.reject("Invalid audio state", e)
                return
            }
        activity.runOnUiThread {
            AudioDockHost.push(state)
            call.resolve()
        }
    }

    override fun load() {
        AudioDockHost.actions =
            AudioDockActions(
                toggle = { command("toggle") },
                skip = { command("skip", JSObject().put("sec", it)) },
                seek = { command("seek", JSObject().put("frac", it.toDouble())) },
                rate = { command("rate", JSObject().put("value", it)) },
                minimize = { command("minimize") },
                close = { command("close") },
            )
        AudioDockHost.onDock = { shown, height ->
            notifyListeners("dock", JSObject().put("shown", shown).put("height", height.toDouble()))
        }
        // a page that starts loading has lost its player: the dock goes until the new page says
        NativeScreens.add(this) { AudioDockHost.enable(activity, false) }
    }

    override fun handleOnDestroy() {
        NativeScreens.remove(this)
        activity.runOnUiThread { AudioDockHost.enable(activity, false) }
        super.handleOnDestroy()
    }

    private fun command(name: String, payload: JSObject = JSObject()) {
        notifyListeners("command", JSObject().put("command", name).put("payload", payload))
    }

    private companion object {
        const val CAPABILITY = "shell.audio.v1"
    }
}
