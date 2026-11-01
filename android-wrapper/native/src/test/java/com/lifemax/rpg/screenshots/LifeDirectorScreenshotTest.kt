package com.lifemax.rpg.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.feature.lifedirector.data.WebLifeDirectorRepository
import com.lifemax.rpg.feature.lifedirector.data.directorReply
import com.lifemax.rpg.feature.lifedirector.domain.DialPlace
import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorProjection
import com.lifemax.rpg.feature.lifedirector.presentation.DialAsk
import com.lifemax.rpg.feature.lifedirector.presentation.DialHold
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorActions
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorToast
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorUi
import com.lifemax.rpg.feature.lifedirector.presentation.LifeDirectorScreen
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette
import com.lifemax.rpg.feature.lifedirector.presentation.components.LocalWallClock
import com.lifemax.rpg.feature.lifedirector.presentation.dial.BuildRun
import com.lifemax.rpg.feature.lifedirector.presentation.dial.ChronoDial
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * The native Life Director Today from projections the web app produced (LifeDirector.native over
 * the lived-in day of tests/fixtures/director-day.js, by tools/gen_native_fixtures.js), on a 411 dp
 * wide phone like the owner's, where the dial is about 360 dp as on the web. Each image is one
 * screenful at full resolution (1080 by 1080 pixels: Paparazzi would shrink it to 1000 without
 * useDeviceResolution), the screen scrolled down to a given point, so a change of a dp or a colour
 * shows. The clock stands at the projection's own time.
 */
class LifeDirectorScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(deviceConfig = phone(), useDeviceResolution = true, maxPercentDifference = 0.001)

    private fun phone(fontScale: Float = 1f) =
        DeviceConfig.PIXEL_5.copy(
            screenHeight = 1080,
            density = Density(420),
            fontScale = fontScale,
        )

    /** A 393 dp wide phone (1081 pixels at 440 dpi), under the page's 400 px rules. */
    private fun narrowPhone() =
        DeviceConfig.PIXEL_5.copy(screenWidth = 1081, screenHeight = 1081, density = Density(440))

    private fun projection(fixture: String, light: Boolean = false) =
        WebLifeDirectorRepository()
            .apply {
                accept(
                    JSONObject(
                            javaClass.getResource("/fixtures/director-$fixture.json")!!.readText()
                        )
                        .put("light", light)
                        .put("tokens", ScreenshotPalette.tokens(light)),
                    true,
                )
            }
            .state
            .value!!

    /**
     * The screen scrolled down to each of [tops] (null: not scrolled, the message on top), with a
     * build stopped at [buildMs] when given.
     */
    private fun snap(
        p: LifeDirectorProjection,
        ui: DirectorUi,
        vararg tops: Pair<String, Dp?>,
        buildMs: Long? = null,
    ) {
        val run = buildMs?.let { ms -> BuildRun(p.today!!.dial.build!!, mutableLongStateOf(ms)) {} }
        for ((name, top) in tops) paparazzi.snapshot(name) {
            CompositionLocalProvider(LocalWallClock provides { (p.today?.now ?: 0) * 1000 }) {
                val screen =
                    @Composable {
                        LifeDirectorScreen(p, ui, DirectorActions(), animate = false, run = run)
                    }
                if (top == null) screen() else Scrolled(top) { screen() }
            }
        }
    }

    /** [label]'s handle held at [minute] ([held]), or let go there while the page answers. */
    private fun hold(p: LifeDirectorProjection, label: String, minute: Int, held: Boolean = true) =
        p.today!!.dial.let { d ->
            DialHold(
                d.handles.single { it.label == label },
                d.stops,
                d.dragWords,
                d.stops.indexOfFirst { it.m == minute },
                held,
            )
        }

    /** The page's confirmation [reply] (director-replies.json) for bedtime let go at [minute]. */
    private fun asked(p: LifeDirectorProjection, reply: String, minute: Int): DirectorUi {
        val replies =
            JSONObject(javaClass.getResource("/fixtures/director-replies.json")!!.readText())
        val confirm = directorReply(replies.getJSONObject(reply)).confirm!!
        val h = hold(p, "Bedtime", minute, held = false)
        return DirectorUi(hold = h, ask = DialAsk(h.handle.key, DialPlace.Stop(minute), confirm))
    }

    /**
     * Bedtime held at 10:00 PM: the rings dim, the arc it would give glows, the centre reads it.
     */
    private fun snapHeld(p: LifeDirectorProjection) =
        snap(p, DirectorUi(hold = hold(p, "Bedtime", 1320)), "dial" to DIAL)

    private fun snap(fixture: String, vararg tops: Pair<String, Dp?>) =
        snap(projection(fixture), DirectorUi(), *tops)

    /** All of Today, a screenful at a time. */
    @Test fun todayDark() = snap("today", *Array(6) { k -> "w$k" to SCREEN * k })

    /** Another day: its date in the centre, no time gone, the whole day as its running order. */
    @Test fun tomorrowDark() = snap("tomorrow", "top" to TOP, "dial" to DIAL, "order" to BELOW_DIAL)

    /** The light theme, which shows around the panel, with the earlier blocks open. */
    @Test
    fun todayLight() =
        snap(
            projection("today", light = true),
            DirectorUi(earlierOpen = true),
            "top" to TOP,
            "earlier" to TODAY_ORDER,
        )

    @Test
    fun todayDial() {
        val today = projection("today").today!!
        paparazzi.snapshot {
            Box(Modifier.fillMaxSize().background(DirectorPalette.bg0)) {
                ChronoDial(today.dial, today.centre, animate = false, onArc = {}, onTap = {})
            }
        }
    }

    /** A focus timer running, in the centre and in the up-next card. */
    @Test fun timer() = snap("timer", "dial" to DIAL, "upNext" to BELOW_DIAL)

    /** Two minutes before a session: the appointment wrapping up, the session about to start. */
    @Test fun soon() = snap("soon", "dial" to DIAL)

    /** A quarter of an hour into a session not started. */
    @Test fun late() = snap("late", "dial" to DIAL)

    /** Late evening: a session done in part, the last one done, nothing doable before tomorrow. */
    @Test
    fun evening() =
        snap(
            projection("evening"),
            DirectorUi(earlierOpen = true),
            "dial" to DIAL,
            "upNext" to BELOW_DIAL,
            "earlier" to 1800.dp, // the evening's sessions
        )

    /** A save that failed: the page's banner, and its message over the screen. */
    @Test
    fun banner() {
        val p = projection("banner")
        snap(p, DirectorUi(toast = DirectorToast(1, p.notice!!.text)), "screen" to null)
    }

    /** Asked for another day, before the page has answered: the strip shows it, the dial today. */
    @Test fun dayAskedFor() = snap(projection("today"), DirectorUi(pendingDay = 2), "top" to TOP)

    /** Life Director before it has a plan: only its frame. */
    @Test fun unplanned() = snap("unplanned", "screen" to null)

    /** Larger text (Settings, font size): the strip's chips and the section tabs grow with it. */
    @Test
    fun largeFont() {
        paparazzi.unsafeUpdateConfig(phone(fontScale = 1.3f))
        snap("today", "top" to TOP)
    }

    /** A handle mid-drag, in the dark theme. */
    @Test fun dragDark() = snapHeld(projection("today"))

    /** A handle mid-drag in the light theme, which shows only around the panel. */
    @Test fun dragLight() = snapHeld(projection("today", light = true))

    /** A handle mid-drag with larger text: the centre keeps to the dial's units. */
    @Test
    fun dragLargeFont() {
        paparazzi.unsafeUpdateConfig(phone(fontScale = 1.3f))
        snapHeld(projection("today"))
    }

    /** Work's start held on its end: too short, so no arc. */
    @Test
    fun dragTooShort() {
        val p = projection("today")
        snap(p, DirectorUi(hold = hold(p, "Work starts", 1020)), "dial" to DIAL)
    }

    /**
     * Bedtime held past midnight, at 1:00 AM: the six hours of sleep left before the wake-up time,
     * lit from 1 to 7 AM, as letting go there would ask.
     */
    @Test
    fun dragPastMidnight() {
        val p = projection("today")
        snap(p, DirectorUi(hold = hold(p, "Bedtime", 60)), "dial" to DIAL)
    }

    /** Let go at 10:30 PM: the page's confirmation over the screen. */
    @Test
    fun confirm() {
        val p = projection("today")
        snap(p, asked(p, "drop", 1350), "screen" to null)
    }

    /** Let go at 4:00 AM: a problem, and only OK. */
    @Test
    fun confirmProblem() {
        val p = projection("today")
        snap(p, asked(p, "dropWithProblem", 240), "screen" to null)
    }

    /** The repair after Change it, just after its session touched down: ripple, sparks, count. */
    @Test fun buildRepair() = snap(projection("build"), DirectorUi(), "dial" to DIAL, buildMs = 560)

    /** The same repair on a 393 dp phone, where the page's read-out has its smaller big line. */
    @Test
    fun buildRepairNarrow() {
        paparazzi.unsafeUpdateConfig(narrowPhone())
        snap(projection("build"), DirectorUi(), "dial" to DIAL, buildMs = 560)
    }

    /** A full build half way round its sweep: the day drawn up to the arm, the read-out. */
    @Test fun buildSweep() = snap(projection("replan"), DirectorUi(), "dial" to DIAL, buildMs = 700)

    /** A full build locking in: the shockwave, the hand swinging round, the handles popping in. */
    @Test fun buildLock() = snap(projection("replan"), DirectorUi(), "dial" to DIAL, buildMs = 2330)

    /**
     * The same Change it with reduced motion: the page sends no build, so the dial is drawn as a
     * build ends, the handle already at 10:30 PM.
     */
    @Test fun reduced() = snap("reduced", "dial" to DIAL)

    /** How far down the screen each image starts. */
    private companion object {
        val SCREEN = 400.dp
        val TOP = 0.dp
        val DIAL = 270.dp
        val BELOW_DIAL = 700.dp
        val TODAY_ORDER = 1200.dp
    }
}

/** [content] laid out on a screen taller than the longest day, seen through this one from [top]. */
@Composable
private fun Scrolled(top: Dp, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().clipToBounds()) {
        Box(
            Modifier.wrapContentHeight(Alignment.Top, unbounded = true)
                .requiredHeight(3600.dp)
                .offset(y = -top)
        ) {
            content()
        }
    }
}
