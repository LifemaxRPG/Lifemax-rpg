package com.lifemax.rpg.screenshots

import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.lifemax.rpg.feature.plan.data.WebPlanRepository
import com.lifemax.rpg.feature.plan.domain.PlanCommands
import com.lifemax.rpg.feature.plan.domain.PlanWrites
import com.lifemax.rpg.feature.plan.presentation.FieldEdit
import com.lifemax.rpg.feature.plan.presentation.FieldKind
import com.lifemax.rpg.feature.plan.presentation.PlanActions
import com.lifemax.rpg.feature.plan.presentation.PlanOfferScreen
import com.lifemax.rpg.feature.plan.presentation.PlanScreen
import com.lifemax.rpg.feature.plan.presentation.PlanUiState
import com.lifemax.rpg.feature.plan.presentation.PlanViewModel
import com.lifemax.rpg.feature.plan.presentation.RunnerUi
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * The Workout plan's guided workout, field sheets, Leave plan's question and sign-up offer as the
 * phone draws them, from projections the web app produced (tools/gen_plan_runner_fixtures.js). The
 * clocks are held still: a screenshot shows the moment the page sent.
 */
class PlanRunnerScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, maxPercentDifference = 0.001)

    private fun fixture(name: String, light: Boolean, edit: (JSONObject) -> Unit = {}): JSONObject =
        JSONObject(javaClass.getResource("/fixtures/plan-$name.json")!!.readText())
            .put("light", light)
            .put("tokens", ScreenshotPalette.tokens(light))
            .also(edit)

    private fun vmFor(repo: WebPlanRepository) =
        PlanViewModel(repo, PlanCommands { _, _, _ -> }, writes = PlanWrites { _, _, _ -> }).also {
            repo.state.value?.let(it::adopt)
        }

    private fun runner(
        name: String,
        light: Boolean = false,
        ui: PlanUiState = PlanUiState(),
        edit: (JSONObject) -> Unit = {},
    ) {
        val repo = WebPlanRepository().apply { accept(fixture("runner-$name", light, edit), true) }
        val vm = vmFor(repo)
        val p = repo.state.value!!
        paparazzi.snapshot { PlanScreen(p, ui, PlanActions(vm) {}) }
    }

    /** What the player has put on the step the fixture shows (drafts belong to their step). */
    private fun typed(name: String, build: (token: String) -> RunnerUi): PlanUiState {
        val r = fixture("runner-$name", false).getJSONObject("runner")
        val step = r.optJSONObject("step")
        val token =
            "${r.getString("phase")}|${r.getString("date")}|${step?.getString("key").orEmpty()}|${step?.getInt("set") ?: 0}"
        return PlanUiState(runner = build(token))
    }

    private fun offer(name: String, light: Boolean = false, ui: PlanUiState = PlanUiState()) {
        val repo = WebPlanRepository().apply { accept(fixture("offer-$name", light), true) }
        val vm = vmFor(repo)
        val p = repo.offers.value!!
        paparazzi.snapshot { PlanOfferScreen(p, ui, PlanActions(vm) {}) }
    }

    @Test fun introDark() = runner("intro")

    @Test fun readyTimedDark() = runner("ready")

    @Test fun readyTimedLight() = runner("ready", light = true)

    @Test fun readyConfirmDark() = runner("check")

    @Test fun activeWeightedDark() = runner("active")

    @Test fun activeWeightedLight() = runner("active", light = true)

    /** The page refused the set: its words stand under the boxes. */
    @Test
    fun activeRefusedDark() =
        runner(
            "active-refused",
            ui =
                typed("active-refused") {
                    RunnerUi(it, error = "Enter what you actually completed")
                },
        )

    @Test
    fun activeTypedDark() =
        // the amount typed is the player's; the weight still shows the page's suggestion
        runner("active", ui = typed("active") { RunnerUi(it, amount = "9") })

    @Test fun timingDark() = runner("timing")

    @Test fun timerDoneStoppedEarlyDark() = runner("timer-done-early")

    @Test fun timerDoneFullDark() = runner("timer-done")

    @Test fun restDark() = runner("rest")

    @Test fun restLight() = runner("rest", light = true)

    @Test fun completeDark() = runner("complete")

    @Test fun partialDark() = runner("partial")

    @Test fun partialLight() = runner("partial", light = true)

    @Test
    fun itineraryDark() = runner("ready", ui = PlanUiState(runner = RunnerUi(itinerary = true)))

    @Test
    fun skipQuestionDark() =
        runner("ready", ui = typed("ready") { RunnerUi(it, confirmSkip = true) })

    @Test
    fun swapQuestionDark() =
        runner("ready", ui = typed("ready") { RunnerUi(it, swapping = "Leg press machine") })

    /** A timed exercise with no length of its own asks for one before the clock starts. */
    @Test
    fun durationQuestionDark() =
        runner(
            "ready",
            ui = typed("ready") { RunnerUi(it, duration = "5") },
            edit = { it.getJSONObject("runner").getJSONObject("step").put("askDuration", true) },
        )

    @Test fun offerAthleteDark() = offer("athlete")

    @Test fun offerAthleteLight() = offer("athlete", light = true)

    @Test fun offerScholarDark() = offer("scholar")

    @Test fun offerExplorerDark() = offer("explorer")

    @Test fun offerOtherDateDark() = offer("athlete", ui = PlanUiState(startDate = "2026-11-04"))

    private fun planWith(
        ui: (WebPlanRepository) -> PlanUiState,
        light: Boolean = false,
        scrolled: Boolean = true,
    ) {
        val repo =
            WebPlanRepository().apply {
                accept(fixture("runner-intro", light) { it.put("runner", JSONObject.NULL) }, true)
            }
        val vm = vmFor(repo)
        val p = repo.state.value!!
        paparazzi.snapshot { Plan(p, ui(repo), vm) }
    }

    @Composable
    private fun Plan(
        p: com.lifemax.rpg.feature.plan.domain.PlanProjection,
        ui: PlanUiState,
        vm: PlanViewModel,
    ) = PlanScreen(p, ui, PlanActions(vm) {})

    @Test
    fun valueSheetDark() =
        planWith({ repo ->
            val day = repo.state.value!!.day("2026-10-28")!!
            val item = day.items.first { it.value != null }
            PlanUiState(
                edit =
                    FieldEdit(
                        day.date,
                        item.key,
                        FieldKind.VALUE,
                        item.title,
                        item.value!!.placeholder,
                        item.value!!.placeholder,
                        item.value!!.text,
                    )
            )
        })

    @Test
    fun logSheetRefusedLight() =
        planWith(
            { repo ->
                val day = repo.state.value!!.day("2026-10-28")!!
                val item = day.items.first { it.value != null }
                PlanUiState(
                    edit =
                        FieldEdit(
                            day.date,
                            item.key,
                            FieldKind.LOG,
                            item.title,
                            "Session log",
                            "e.g. 3×10 @ 120lb, felt strong",
                            "Felt strong, added 5 lb",
                            error = "Could not save to this browser (storage full?)",
                        )
                )
            },
            light = true,
        )

    @Test fun leaveQuestionDark() = planWith({ PlanUiState(leaving = true) })

    @Test fun leaveQuestionLight() = planWith({ PlanUiState(leaving = true) }, light = true)
}
