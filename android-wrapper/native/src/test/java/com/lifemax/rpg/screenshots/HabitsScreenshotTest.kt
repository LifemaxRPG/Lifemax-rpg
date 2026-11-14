package com.lifemax.rpg.screenshots

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.feature.habits.HabitsScenarios
import com.lifemax.rpg.feature.habits.MemoryHabitsStorage
import com.lifemax.rpg.feature.habits.TestClock
import com.lifemax.rpg.feature.habits.domain.HabitsLoad
import com.lifemax.rpg.feature.habits.presentation.HabitsScreen
import com.lifemax.rpg.feature.habits.presentation.HabitsViewModel
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Rule
import org.junit.Test

/**
 * Habits, drawn from the rules' own states (HabitsScenarios) on a 411 dp wide phone like the
 * owner's, at a fixed day and hour, so the sky, the reps and the cracks are the same every time.
 * The screen's state comes from its own view model (a block tapped, a sheet opened, the city and
 * its time-lapse); motion is reduced, so every picture is the scene at rest. Nothing is written to
 * a view model or a state after the frame is taken (the lesson of CatLightingScreenshotTest).
 */
class HabitsScreenshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = phone(), maxPercentDifference = 0.001)

    private fun phone(fontScale: Float = 1f) =
        DeviceConfig.PIXEL_5.copy(
            screenHeight = 1620,
            density = Density(420),
            fontScale = fontScale,
        )

    /** Scenario [name] at [hour], after [use]. */
    private fun snap(
        name: String,
        hour: Int = 14,
        light: Boolean = false,
        opened: HabitsLoad? = null,
        use: HabitsViewModel.() -> Unit = {},
    ) {
        val clock = TestClock(ZonedDateTime.of(2026, 3, 2, hour, 0, 0, 0, ZoneOffset.UTC))
        val surroundings =
            HabitsScenarios.surroundings(
                light = light,
                tokens = ScreenshotPalette.tokens(light),
                pet = true,
                reducedMotion = true,
            )
        val repository =
            HabitsScenarios.repository(
                MemoryHabitsStorage(HabitsScenarios.save(name), opened),
                clock,
                surroundings,
            )
        val vm = HabitsViewModel(repository, repository).apply { onProjection() }.apply(use)
        val p = repository.state.value!!
        val ui = vm.ui.value
        paparazzi.snapshot { HabitsScreen(p, ui) }
    }

    private fun HabitsViewModel.block(stack: Int, block: Int) =
        select(projection.value!!.stacks[stack].blocks[block].id)

    /** Nothing built yet: the empty lot and the first step. */
    @Test fun emptyDay() = snap("empty")

    /**
     * Two blocks laid (gold, stone with its seam), a cracked blueprint glowing as the next, a
     * wobbling one, the locked reward.
     */
    @Test fun cityDay() = snap("city")

    @Test fun cityLight() = snap("city", light = true)

    @Test fun cityDusk() = snap("city", hour = 19)

    @Test fun cityNight() = snap("city", hour = 23)

    /**
     * The tear-down tower in front: nine floors with their windows, a tree for the habit that is
     * gone.
     */
    @Test fun tearDownNight() = snap("city", hour = 22) { focus(projection.value!!.stacks[1].id) }

    /** An empty foundation: the button offers the first habit. */
    @Test fun emptyFoundation() = snap("city") { focus(projection.value!!.stacks[2].id) }

    /** The stack finished: the reward glows and the button claims it. */
    @Test fun rewardClaimable() = snap("done") { focus(projection.value!!.stacks[0].id) }

    /** Claimed: the flag is up and the button goes on to the next stack. */
    @Test fun rewardClaimed() = snap("claimed") { focus(projection.value!!.stacks[0].id) }

    /** A rough day: the blueprints at their two-minute size, the switch on. */
    @Test fun roughDay() = snap("rough")

    /**
     * Nine habits of every material: the tower keeps a readable size and the camera stands on the
     * next block.
     */
    @Test fun tallTower() = snap("tall")

    @Test fun tallTowerLight() = snap("tall", light = true)

    /** A block's dials, the scene still bright behind them. */
    @Test fun dials() = snap("city") { block(0, 0) }

    @Test fun dialsLight() = snap("city", light = true) { block(0, 2) }

    /** The dials of a habit being broken: its floors instead of a size. */
    @Test fun dialsTearDown() = snap("city", hour = 22) { block(1, 0) }

    @Test fun sheetNewFoundation() = snap("city") { newStack() }

    @Test
    fun sheetNewHabitToBreak() =
        snap("city", light = true) {
            newHabit(projection.value!!.stacks[2].id)
            type(0, "Doomscrolling")
            kind(true)
        }

    /** A refusal on the sheet that asked: the names sheet with the normal name emptied. */
    @Test
    fun sheetNamesRefused() =
        snap("city") {
            block(0, 0)
            names()
            type(1, "")
            submit()
        }

    @Test
    fun sheetRetire() =
        snap("city") {
            block(0, 3)
            retire()
        }

    /** The city: every tower side by side, today. */
    @Test fun cityView() = snap("city") { city(true) }

    /** The time-lapse six weeks back: shorter towers of younger materials. */
    @Test
    fun cityViewSixWeeksAgo() =
        snap("city", light = true) {
            city(true)
            lapse(6)
        }

    /** A habits file from a newer app: the plain message, and nothing to change. */
    @Test
    fun fileFromANewerApp() =
        snap(
            "empty",
            opened =
                HabitsLoad.Blocked(
                    "Your habits were saved by a newer version of LifeMax. Update the app to see and change them. Nothing has been touched."
                ),
        )

    @Test
    fun largeText() {
        paparazzi.unsafeUpdateConfig(phone(fontScale = 2f))
        snap("city")
    }

    @Test
    fun largeTextDials() {
        paparazzi.unsafeUpdateConfig(phone(fontScale = 2f))
        snap("city") { block(0, 0) }
    }
}
