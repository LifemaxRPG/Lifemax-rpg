package com.lifemax.rpg.bridge.web

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.getcapacitor.annotation.CapacitorPlugin
import com.lifemax.rpg.app.OverviewContainer
import com.lifemax.rpg.feature.overview.data.OverviewCommandSender
import com.lifemax.rpg.feature.overview.data.overviewReply
import com.lifemax.rpg.feature.overview.presentation.OverviewRoute
import com.lifemax.rpg.feature.overview.presentation.OverviewViewModel
import org.json.JSONObject

/**
 * The Overview's cards (Native Overview cards, beta): the page decides and words every card and
 * stays the only writer; the screen draws its projection (`overview.cards.v1`), hands page views
 * over with "navigate" and asks the page for everything else with commands (plan, attr, luck, shop,
 * health, appLaunch, appAdd, appsEdit, tip, edit). The page's live Overview (NativeOverviewPlugin)
 * is a separate drawing of the same view; this one uses the shared bars like every other native
 * tab.
 */
@CapacitorPlugin(name = "NativeOverviewCards")
class NativeOverviewCardsPlugin : ComposeTabPlugin() {
    private var nextRequest = 0L
    private val container =
        OverviewContainer(
            OverviewCommandSender { command, payload ->
                "overview-${++nextRequest}".also { sendCommand(command, payload, it) }
            }
        )
    private val viewModel: OverviewViewModel
        get() = ViewModelProvider(store, container.factory)[OverviewViewModel::class.java]

    override val tabId = "dashboard"
    override val capabilities = listOf("overview.cards.v1")

    override fun accept(snapshot: JSONObject, explicitOpen: Boolean) {
        container.repository.accept(snapshot, explicitOpen)
        if (explicitOpen) viewModel.opened() else viewModel.projected()
    }

    override fun onReply(reply: JSONObject) = viewModel.onReply(overviewReply(reply))

    @Composable
    override fun Content(
        topInsetDp: Float,
        bottomInsetDp: Float,
        emit: (action: String, id: String) -> Unit,
    ) = OverviewRoute(viewModel, visible, topInsetDp, bottomInsetDp, emit)
}
