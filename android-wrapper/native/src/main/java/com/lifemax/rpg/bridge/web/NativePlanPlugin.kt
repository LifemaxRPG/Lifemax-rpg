package com.lifemax.rpg.bridge.web

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.getcapacitor.annotation.CapacitorPlugin
import com.lifemax.rpg.app.PlanContainer
import com.lifemax.rpg.feature.plan.data.toCommand
import com.lifemax.rpg.feature.plan.data.toEvent
import com.lifemax.rpg.feature.plan.data.toPlanReply
import com.lifemax.rpg.feature.plan.data.toToggleJson
import com.lifemax.rpg.feature.plan.presentation.PlanRoute
import com.lifemax.rpg.feature.plan.presentation.PlanViewModel
import org.json.JSONObject

/**
 * Workout plan: the screen selects days and weeks itself and ticks sets through the page's own
 * planToggle (the toggle command). The guided workout (plan.runner.v1), the value and session-log
 * fields (plan.fields.v1), Leave plan (plan.leave.v1) and the sign-up offers (plan.offers.v1) are
 * drawn here too and are commands the page answers; a page that does not offer them keeps handing
 * each over to its own handler, after the screen steps aside.
 */
@CapacitorPlugin(name = "NativePlan")
class NativePlanPlugin : ComposeTabPlugin() {
    private val container =
        PlanContainer(
            { requestId, set, checked ->
                sendCommand("toggle", set.toToggleJson(checked), requestId)
            },
            { requestId, operation, write ->
                val (command, payload) = write.toCommand(operation)
                sendCommand(command, payload, requestId)
            },
        )
    private val viewModel: PlanViewModel
        get() = ViewModelProvider(store, container.factory)[PlanViewModel::class.java]

    override val tabId = "plan"
    override val capabilities =
        listOf(
            "plan.projection.v1",
            "plan.runner.v1",
            "plan.fields.v1",
            "plan.leave.v1",
            "plan.offers.v1",
        )

    override fun accept(snapshot: JSONObject, explicitOpen: Boolean) {
        container.repository.accept(snapshot, explicitOpen)
        if (explicitOpen) viewModel.onOpen() else viewModel.onUpdate()
    }

    override fun onReply(reply: JSONObject) {
        reply.toPlanReply()?.let(viewModel::onReply)
    }

    @Composable
    override fun Content(
        topInsetDp: Float,
        bottomInsetDp: Float,
        emit: (action: String, id: String) -> Unit,
    ) =
        PlanRoute(viewModel, topInsetDp, bottomInsetDp, visible) { action ->
            val (name, id) = action.toEvent()
            emit(name, id)
        }
}
