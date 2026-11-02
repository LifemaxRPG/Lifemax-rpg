package com.lifemax.rpg.bridge.web

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.getcapacitor.annotation.CapacitorPlugin
import com.lifemax.rpg.app.JournalContainer
import com.lifemax.rpg.app.shell.SecureWindow
import com.lifemax.rpg.feature.journal.data.toEvent
import com.lifemax.rpg.feature.journal.data.toJournalReply
import com.lifemax.rpg.feature.journal.data.toJson
import com.lifemax.rpg.feature.journal.domain.JournalFrame
import com.lifemax.rpg.feature.journal.presentation.JournalRoute
import com.lifemax.rpg.feature.journal.presentation.JournalViewModel
import org.json.JSONObject

/**
 * The Library's Journal pane (view `history`), private behind the Journal lock (CONTRACT A35). The
 * page keeps the Journal, its rules, its lock and its save, and sends either the open Journal or,
 * while its lock is closed, a withheld frame with nothing of it, which draws the lock card. Here:
 * - **at rest** (the screen stops being drawn, or is removed) the frame drops to the switch alone
 *   and the window's secure hold is let go, whatever the lock;
 * - **on app stop** a showing Journal with the lock on turns into its card at once, and the page
 *   session's open frames wait for its relock (the repository's gate);
 * - **the secure hold**: while the screen draws a frame whose lock is on, the window stays secure,
 *   whatever the page's own flag says, until the screen rests.
 */
@CapacitorPlugin(name = "NativeJournal")
class NativeJournalPlugin : ComposeTabPlugin() {
    private val container = JournalContainer { command, requestId ->
        sendCommand(command.name, command.toJson(), requestId)
    }
    private val viewModel: JournalViewModel
        get() = ViewModelProvider(store, container.factory)[JournalViewModel::class.java]

    override val tabId = "history"
    override val capabilities = listOf("library.journal.projection.v1")

    override fun accept(snapshot: JSONObject, explicitOpen: Boolean) {
        try {
            container.repository.accept(snapshot, explicitOpen)
        } catch (e: Exception) {
            // org.json's messages name the offending value, which here could be a reflection:
            // nothing
            // of the cause goes on to Capacitor's log
            throw IllegalArgumentException("Invalid journal projection")
        }
        if (explicitOpen) viewModel.opened()
        viewModel.onProjection()
        secureFor(container.repository.state.value)
    }

    override fun onReply(reply: JSONObject) {
        reply.toJournalReply()?.let(viewModel::onReply)
    }

    override fun onRested() {
        container.repository.rest()
        viewModel.rested()
        SecureWindow.hold(activity, this, false)
    }

    override fun handleOnStop() {
        // stop runs on the main thread, as every accept does; only a showing screen locks itself
        if (visible && container.repository.stop()) viewModel.onProjection()
        super.handleOnStop()
    }

    @Composable
    override fun Content(
        topInsetDp: Float,
        bottomInsetDp: Float,
        emit: (action: String, id: String) -> Unit,
    ) =
        JournalRoute(viewModel, visible, topInsetDp, bottomInsetDp) { action ->
            val (name, id) = action.toEvent()
            emit(name, id)
        }

    /**
     * Holds the window secure while the frame drawn says the lock is on; lets go when it does not.
     */
    private fun secureFor(frame: JournalFrame?) {
        if (frame == null || frame is JournalFrame.Resting) return
        SecureWindow.hold(activity, this, frame.chrome.lock.on)
    }
}
