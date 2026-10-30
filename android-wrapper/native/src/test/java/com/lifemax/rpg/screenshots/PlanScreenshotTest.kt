package com.lifemax.rpg.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.lifemax.rpg.feature.plan.data.WebPlanRepository
import com.lifemax.rpg.feature.plan.domain.PlanProjection
import com.lifemax.rpg.feature.plan.domain.SetRef
import com.lifemax.rpg.feature.plan.presentation.PlanActions
import com.lifemax.rpg.feature.plan.presentation.PlanScreen
import com.lifemax.rpg.feature.plan.presentation.PlanTheme
import com.lifemax.rpg.feature.plan.presentation.PlanUiState
import com.lifemax.rpg.feature.plan.presentation.components.LocalPlanColors
import com.lifemax.rpg.feature.plan.presentation.components.PlanToast
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * The Workout plan as the phone draws it, from projections the web app produced
 * (tools/gen_native_fixtures.js): the top of the screen, scrolled to the days and to the exercises,
 * and the toast.
 */
class PlanScreenshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    private fun projection(fixture: String, light: Boolean): PlanProjection =
        WebPlanRepository()
            .apply {
                accept(
                    JSONObject(javaClass.getResource("/fixtures/plan-$fixture.json")!!.readText())
                        .put("light", light)
                        .put("tokens", ScreenshotPalette.tokens(light)),
                    true,
                )
            }
            .state
            .value!!

    private fun snap(
        fixture: String,
        light: Boolean = false,
        section: Int = TOP,
        scrolledDp: Int = 0,
        selected: String? = null,
        pending: Map<SetRef, Boolean> = emptyMap(),
        rulesOpen: Boolean = false,
    ) {
        val p = projection(fixture, light)
        paparazzi.snapshot {
            PlanScreen(
                p,
                PlanUiState(selected, pending),
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

    /** Today's deadlift: two of three sets saved and the third waiting for the page. */
    @Test
    fun midweekExercisesDark() =
        snap(
            "midweek",
            section = DAY,
            scrolledDp = 640,
            pending = mapOf(SetRef("2026-10-28", "E0", 2) to true),
        )

    @Test
    fun midweekExercisesLight() = snap("midweek", light = true, section = DAY, scrolledDp = 640)

    /** A done day's exercise with its weight, its log and the previous session's. */
    @Test fun completeExercisesDark() = snap("complete", section = DAY, scrolledDp = 900)

    /** A day to come: locked fields, the optional finisher, last time's entries. */
    @Test
    fun completeOptionalDark() =
        snap("complete", section = DAY, scrolledDp = 700, selected = "2026-10-31")

    @Test
    fun toasts() {
        val message = "Strength B complete · +40 XP"
        paparazzi.snapshot {
            Column(Modifier.fillMaxSize()) {
                for (light in listOf(false, true)) {
                    val p = projection("midweek", light)
                    PlanTheme(p.tokens, p.light) {
                        Box(
                            Modifier.weight(1f)
                                .fillMaxWidth()
                                .background(LocalPlanColors.current.bg)
                                .padding(12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            PlanToast(message)
                        }
                    }
                }
            }
        }
    }

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
