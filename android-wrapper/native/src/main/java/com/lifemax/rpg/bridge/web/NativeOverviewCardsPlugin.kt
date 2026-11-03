package com.lifemax.rpg.bridge.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import com.getcapacitor.annotation.CapacitorPlugin
import com.lifemax.rpg.app.OverviewContainer
import com.lifemax.rpg.core.designsystem.feedback.LocalPageBuzz
import com.lifemax.rpg.feature.overview.data.OverviewCommandSender
import com.lifemax.rpg.feature.overview.data.overviewReply
import com.lifemax.rpg.feature.overview.presentation.OverviewRoute
import com.lifemax.rpg.feature.overview.presentation.OverviewViewModel
import com.lifemax.rpg.feature.pet.host.CatWorldHost
import com.lifemax.rpg.feature.pet.host.WorldPort
import com.lifemax.rpg.feature.pet.presentation.CatArt
import com.lifemax.rpg.feature.pet.world.WorldContract
import com.lifemax.rpg.feature.pet.world.WorldFeed
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject

/**
 * The Overview's cards (Native Overview cards, beta): the page decides and words every card and
 * stays the only writer; the screen draws its projection (`overview.cards.v1`), hands page views
 * over with "navigate" and asks the page for everything else with commands (plan, attr, luck, shop,
 * health, appLaunch, appAdd, appsEdit, tip, layout, layoutReset). The page's live Overview
 * (NativeOverviewPlugin) is a separate drawing of the same view; this one uses the shared bars like
 * every other native tab.
 */
@CapacitorPlugin(name = "NativeOverviewCards")
class NativeOverviewCardsPlugin : ComposeTabPlugin() {
    private var nextRequest = 0L
    private val nextCoatRequest = AtomicLong()
    private val container =
        OverviewContainer(
            OverviewCommandSender { command, payload ->
                "overview-${++nextRequest}".also { sendCommand(command, payload, it) }
            }
        )
    private val viewModel: OverviewViewModel
        get() = ViewModelProvider(store, container.factory)[OverviewViewModel::class.java]

    override val tabId = "dashboard"
    override val capabilities =
        listOf(
            "overview.cards.v1",
            "overview.cat.live.v1",
            "overview.cat.live.v2",
            "overview.cat.coat.v1",
            "overview.cat.rig.v1",
            WorldContract.CAPABILITY,
        )

    init {
        bindCoats()
    }

    /**
     * The live cat's genetic coat: the page composes it and answers `coat {key, kind}` with the
     * sheet. Asked from the cat's worker thread, so the command is only queued, on the UI thread.
     * One source serves every screen with a room (the Cat tab's too), so the screen that opens
     * takes the questions.
     */
    private fun bindCoats() {
        CatArt.coats.bind { coat, kind ->
            "coat-${nextCoatRequest.incrementAndGet()}"
                .also { id ->
                    activity.runOnUiThread {
                        sendCommand("coat", JSONObject().put("key", coat).put("kind", kind.key), id)
                    }
                }
        }
    }

    /**
     * This screen's way to the page for the cat world's commands: heard only while it shows (the
     * page's answer reaches a showing screen), so the world writes while a screen is in front and
     * flushes once as it goes.
     */
    private val worldPort =
        object : WorldPort {
            override val visible: Boolean
                get() = this@NativeOverviewCardsPlugin.visible

            override fun send(command: String, payload: JSONObject, requestId: String): Boolean {
                sendCommand(command, payload, requestId)
                return true
            }
        }

    init {
        CatWorldHost.shared.registerPort("dashboard", worldPort)
    }

    override fun handleOnStop() {
        CatWorldHost.shared.onStop() // the app stopped: the world is saved at once
        super.handleOnStop()
    }

    override fun handleOnDestroy() {
        CatWorldHost.shared.unregisterPort("dashboard")
        super.handleOnDestroy()
    }

    override fun accept(snapshot: JSONObject, explicitOpen: Boolean) {
        // pet.world.v1: the same cats' world as the Cat tab's (CatWorldHost)
        val accepted = WorldFeed.shared.offerFrom(snapshot)
        CatWorldHost.shared.onProjection(snapshot.has(WorldFeed.KEY), accepted)
        if (explicitOpen) bindCoats()
        container.repository.accept(snapshot, explicitOpen)
        if (explicitOpen) viewModel.opened() else viewModel.projected()
    }

    override fun onReply(reply: JSONObject) {
        if (CatWorldHost.shared.onReply(reply)) return
        if (!CatArt.coats.onReply(reply)) viewModel.onReply(overviewReply(reply))
    }

    @Composable
    override fun Content(
        topInsetDp: Float,
        bottomInsetDp: Float,
        emit: (action: String, id: String) -> Unit,
    ) {
        CompositionLocalProvider(LocalPageBuzz provides container.buzz(LocalContext.current)) {
            OverviewRoute(viewModel, visible, topInsetDp, bottomInsetDp, emit)
        }
    }
}
