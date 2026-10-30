package com.lifemax.rpg.screenshots

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.lifemax.rpg.feature.plan.data.WebPlanRepository
import com.lifemax.rpg.feature.plan.presentation.PlanActions
import com.lifemax.rpg.feature.plan.presentation.PlanScreen
import com.lifemax.rpg.feature.plan.presentation.PlanUiState
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * The Workout plan as the phone draws it, from projections the web app produced
 * (tools/gen_native_fixtures.js): the top of the screen, and scrolled to the days.
 */
class PlanScreenshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    private fun snap(
        fixture: String,
        light: Boolean = false,
        section: Int = TOP,
        scrolledDp: Int = 0,
        selected: String? = null,
        rulesOpen: Boolean = false,
    ) {
        val p =
            WebPlanRepository()
                .apply {
                    accept(
                        JSONObject(
                                javaClass.getResource("/fixtures/plan-$fixture.json")!!.readText()
                            )
                            .put("light", light)
                            .put("tokens", ScreenshotPalette.tokens(light)),
                        true,
                    )
                }
                .state
                .value!!
        paparazzi.snapshot {
            PlanScreen(
                p,
                PlanUiState(selected),
                PlanActions(),
                list =
                    rememberLazyListState(
                        section,
                        with(LocalDensity.current) { scrolledDp.dp.roundToPx() },
                    ),
                rulesOpen = rulesOpen,
            )
        }
    }

    @Test fun midweekDark() = snap("midweek")

    @Test fun midweekDayDark() = snap("midweek", section = DAY)

    @Test
    fun midweekLockedDayDark() =
        snap("midweek", section = CALENDAR, scrolledDp = 340, selected = "2026-10-31")

    @Test fun midweekDayLight() = snap("midweek", light = true, section = DAY)

    @Test fun completeDark() = snap("complete")

    @Test fun completeRulesDark() = snap("complete", section = RULES, rulesOpen = true)

    @Test fun scholarDark() = snap("scholar")

    @Test fun scholarDayDark() = snap("scholar", section = DAY)

    @Test fun scholarLight() = snap("scholar", light = true)

    private companion object {
        // the screen's sections: head, launcher, plan, calendar, day, rules
        const val TOP = 0
        const val CALENDAR = 3
        const val DAY = 4
        const val RULES = 5
    }
}
