package com.lifemax.rpg.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.feature.lifedirector.data.WebLifeDirectorRepository
import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorProjection
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorActions
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorToast
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorUi
import com.lifemax.rpg.feature.lifedirector.presentation.LifeDirectorScreen
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette
import com.lifemax.rpg.feature.lifedirector.presentation.components.LocalWallClock
import com.lifemax.rpg.feature.lifedirector.presentation.dial.ChronoDial
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * The native Life Director Today from projections the web app produced (LifeDirector.native over
 * the lived-in day of tests/fixtures/director-day.js, by tools/gen_native_fixtures.js), on a 411 dp
 * wide phone like the owner's, where the dial is about 360 dp as on the web. Each image is one
 * screenful at full resolution, the screen scrolled down to a given point, so a change of a dp or a
 * colour shows. The clock stands at the projection's own time.
 */
class LifeDirectorScreenshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = phone())

    private fun phone(fontScale: Float = 1f) =
        DeviceConfig.PIXEL_5.copy(
            screenHeight = 1080,
            density = Density(420),
            fontScale = fontScale,
        )

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

    /** The screen scrolled down to each of [tops] (null: not scrolled, the message on top). */
    private fun snap(p: LifeDirectorProjection, ui: DirectorUi, vararg tops: Pair<String, Dp?>) {
        for ((name, top) in tops) paparazzi.snapshot(name) {
            CompositionLocalProvider(LocalWallClock provides { (p.today?.now ?: 0) * 1000 }) {
                if (top == null) LifeDirectorScreen(p, ui, DirectorActions(), animate = false)
                else Scrolled(top) { LifeDirectorScreen(p, ui, DirectorActions(), animate = false) }
            }
        }
    }

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
