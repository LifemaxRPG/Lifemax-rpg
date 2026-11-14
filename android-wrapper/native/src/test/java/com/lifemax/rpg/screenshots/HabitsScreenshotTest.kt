package com.lifemax.rpg.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.feature.habits.HabitsScenarios
import com.lifemax.rpg.feature.habits.MemoryHabitsStorage
import com.lifemax.rpg.feature.habits.TestClock
import com.lifemax.rpg.feature.habits.domain.Ambient
import com.lifemax.rpg.feature.habits.domain.HabitsLoad
import com.lifemax.rpg.feature.habits.domain.Pass
import com.lifemax.rpg.feature.habits.presentation.HabitsScreen
import com.lifemax.rpg.feature.habits.presentation.HabitsViewModel
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Rule
import org.junit.Test

/**
 * Habits, drawn from the rules' own states (HabitsScenarios) on a 411 dp wide phone like the
 * owner's, at a fixed day and hour, so the sky, the reps and the cracks are the same every time.
 * The screen's state comes from its own view model (a floor tapped, a sheet opened, the zoomed-out
 * street and its time-lapse); motion is reduced, so every picture is the street at rest, and the
 * street's own life (stars, people, planes) is held at one moment: a still picture with nothing
 * passing, or, for the sky pictures, the moment [HabitsScenarios.NIGHT_SKY_AT] or
 * [HabitsScenarios.DAY_SKY_AT]. No Compose state is written by a test: the view model is set up
 * before the frame is taken, and nothing is written after it (the lesson of
 * CatLightingScreenshotTest).
 */
private const val PHONE_DP = 616

class HabitsScreenshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = phone(), maxPercentDifference = 0.001)

    private fun phone(fontScale: Float = 1f) =
        DeviceConfig.PIXEL_5.copy(
            screenHeight = 1620,
            density = Density(420),
            fontScale = fontScale,
        )

    /** Scenario [name] at [hour], after [use], with the street's life held at [ambientAt]. */
    private fun snap(
        name: String,
        hour: Int = 14,
        light: Boolean = false,
        opened: HabitsLoad? = null,
        keyboardDp: Int = 0,
        ambientAt: Float? = null,
        topInsetDp: Float = 64f,
        use: HabitsViewModel.() -> Unit = {},
    ) {
        val clock = TestClock(ZonedDateTime.of(2026, 3, 2, hour, 0, 0, 0, ZoneOffset.UTC))
        val surroundings =
            HabitsScenarios.surroundings(
                light = light,
                tokens = ScreenshotPalette.tokens(light),
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
        paparazzi.snapshot {
            // the keyboard up: the activity makes the screen as tall as what is above it, so the
            // picture is the screen that much shorter (no inset is simulated)
            if (keyboardDp == 0) HabitsScreen(p, ui, topInsetDp = topInsetDp, ambientAt = ambientAt)
            else
                Box(Modifier.fillMaxWidth().height((PHONE_DP - keyboardDp).dp)) {
                    HabitsScreen(
                        p,
                        ui,
                        topInsetDp = topInsetDp,
                        imeUp = true,
                        ambientAt = ambientAt,
                    )
                }
        }
    }

    private fun HabitsViewModel.block(stack: Int, block: Int) =
        select(projection.value!!.stacks[stack].blocks[block].id)

    /** Nothing built yet: the fenced-off lot and the first step. */
    @Test fun emptyDay() = snap("empty")

    /**
     * The street by day with the coffee building in front: two floors lit today (gold with all its
     * windows and people, stone with its gold seam), a cracked floor with a flickering light as the
     * next, one missed twice with its warning sign, the rooftop sign dark (locked); the lights-out
     * building and the empty lot beside it.
     */
    @Test fun streetDay() = snap("city")

    @Test fun streetLight() = snap("city", light = true)

    @Test fun streetDusk() = snap("city", hour = 19)

    /** At night: lamps and their pools, lit windows, the failing lights of the missed floors. */
    @Test fun streetNight() = snap("city", hour = 23)

    /** A busy, established building at night: gold and stone floors, every window lit, people. */
    @Test fun busyBuildingNight() = snap("tall", hour = 22)

    /** A brand-new building at dusk: steel and glass with scaffolding, one window, one person. */
    @Test fun brandNewBuilding() = snap("fresh", hour = 19)

    /**
     * The run-down building in front: nine boarded and dimming storeys coming down, the floor being
     * built over them, a tree in the park for the habit that is gone.
     */
    @Test fun tearDownNight() = snap("city", hour = 22) { focus(projection.value!!.stacks[1].id) }

    /** A habit broken for good: the lobby and its park. */
    @Test fun parkDay() = snap("park")

    /** An empty foundation: a lobby, and the button offers the first floor. */
    @Test fun emptyFoundation() = snap("city") { focus(projection.value!!.stacks[2].id) }

    /** The building done today: the rooftop sign lit with its bulbs, the button claims it. */
    @Test fun rewardClaimable() = snap("done", hour = 21) { focus(projection.value!!.stacks[0].id) }

    /** Claimed: the flag is up beside the sign and the button goes on to the next building. */
    @Test fun rewardClaimed() = snap("claimed") { focus(projection.value!!.stacks[0].id) }

    /** A rough day: the floors at their two-minute height, the switch on. */
    @Test fun roughDay() = snap("rough")

    /** Nine habits of every material: the camera stands on the next floor. */
    @Test fun tallBuilding() = snap("tall")

    @Test fun tallBuildingLight() = snap("tall", light = true)

    /** A floor's dials, the street still bright behind them. */
    @Test fun dials() = snap("city") { block(0, 0) }

    @Test fun dialsLight() = snap("city", light = true) { block(0, 2) }

    /** The dials of a habit being broken: its storeys instead of a size. */
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

    /** A foundation being typed: both sides of the moment it names are offered. */
    @Test
    fun sheetSuggestFoundation() =
        snap("city") {
            newStack()
            type(0, "show")
        }

    /** A habit being typed: the catalog's habits, each with its three sizes behind its chip. */
    @Test
    fun sheetSuggestHabit() =
        snap("city") {
            newHabit(projection.value!!.stacks[2].id)
            type(0, "pu")
        }

    /**
     * A habit being typed with the keyboard up (about 300 dp of the 616): the sheet stays in view
     * above the keyboard with the field and the chips, and the bar's room is gone.
     */
    @Test
    fun sheetNewHabitKeyboard() =
        snap("city", keyboardDp = 300) {
            newHabit(projection.value!!.stacks[2].id)
            type(0, "pu")
        }

    /** A habit to break being typed. */
    @Test
    fun sheetSuggestBreak() =
        snap("city") {
            newHabit(projection.value!!.stacks[2].id)
            kind(true)
            type(0, "ph")
        }

    /** Zoomed out: the whole street, today, each building named under it. */
    @Test fun streetView() = snap("city") { city(true) }

    /** The time-lapse six weeks back: shorter buildings of younger materials. */
    @Test
    fun streetViewSixWeeksAgo() =
        snap("city", light = true) {
            city(true)
            lapse(6)
        }

    /** The night sky's life held at one moment: an airplane's lights and a shooting star. */
    @Test
    fun nightSkyPlaneAndShootingStar() =
        snap("city", hour = 23, ambientAt = HabitsScenarios.NIGHT_SKY_AT)

    /** The day sky's: an airplane with its contrail, a flock of birds, people at work. */
    @Test fun daySkyBirdsAndContrail() = snap("city", ambientAt = HabitsScenarios.DAY_SKY_AT)

    /** The first moment a blimp is crossing, about half way over (a bright day). */
    private fun blimpMoment(): Float {
        val seed = HabitsScenarios.TODAY.hashCode()
        val pass = Pass()
        var t = 1f
        while (t < 20000f) {
            if (Ambient.blimp(t, seed, pass) && pass.t in .45f..0.55f) return t
            t += .5f
        }
        return 0f
    }

    /**
     * The blimp at its best, by day: gradient envelope with its highlight and seams, fins, the
     * gondola and its window, soft volumetric clouds (a heap, a streak, puffs) round it.
     */
    @Test fun blimpAndCloudsDay() = snap("city", ambientAt = blimpMoment())

    /** Dusk: clouds with warm rosy undersides, the sun's corona, trees lit warm. */
    @Test fun cloudsAndTreesDusk() = snap("park", hour = 19)

    /** Night: silver-blue clouds with moonlit rims, the cratered crescent moon, lamp-lit trees. */
    @Test fun cloudsAndTreesNight() = snap("park", hour = 23)

    /**
     * The page's header band (the status bar, the picture, the name, the buttons are the page's,
     * drawn over this screen) is filled with the app's own surface and the sky starts under it:
     * dark, light, and with the keyboard sheet open.
     */
    @Test fun headerBandDark() = snap("city", topInsetDp = 110f)

    @Test fun headerBandLight() = snap("city", light = true, topInsetDp = 110f)

    @Test
    fun headerBandKeyboard() =
        snap("city", keyboardDp = 300, topInsetDp = 110f) {
            newHabit(projection.value!!.stacks[2].id)
            type(0, "pu")
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
