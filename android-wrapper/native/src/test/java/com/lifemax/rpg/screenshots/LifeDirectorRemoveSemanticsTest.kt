package com.lifemax.rpg.screenshots

import androidx.compose.runtime.CompositionLocalProvider
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.feature.lifedirector.DirectorRecorder
import com.lifemax.rpg.feature.lifedirector.data.WebLifeDirectorRepository
import com.lifemax.rpg.feature.lifedirector.domain.DragPoint
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorActions
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorUi
import com.lifemax.rpg.feature.lifedirector.presentation.LifeDirectorScreen
import com.lifemax.rpg.feature.lifedirector.presentation.components.LocalWallClock
import com.lifemax.rpg.feature.lifedirector.presentation.drag.DragRemoveController
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * What TalkBack is given for taking a session off the day: a custom action "Remove from today" on
 * each session the page says can be removed, and on no other row, and the trash announcing itself
 * while a session is held. No image is written (SemanticsProbe); what only a phone can say (how
 * TalkBack speaks the actions) is listed in the Life Director inventory.
 */
class LifeDirectorRemoveSemanticsTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 7000, density = Density(420)),
            useDeviceResolution = true,
            snapshotHandler = NoImages,
        )

    private fun read(hold: Boolean): List<A11yNode> {
        val repo =
            WebLifeDirectorRepository().apply {
                accept(
                    JSONObject(javaClass.getResource("/fixtures/director-remove.json")!!.readText())
                        .put("tokens", ScreenshotPalette.tokens(false))
                        .put("light", false),
                    true,
                )
            }
        val p = repo.state.value!!
        val drag =
            DragRemoveController(
                DirectorRecorder(),
                repo.state,
                MutableStateFlow(DirectorUi()),
                { true },
                {},
            )
        drag.layout(1080f, 7000f, 168f, 189f, 2.625f)
        if (hold)
            drag.lift(
                p.today!!.ahead.first { it.title == "Resistance Training" },
                DragPoint(540f, 2000f),
            )
        val probe = SemanticsProbe()
        paparazzi.snapshot {
            probe.Watch {
                CompositionLocalProvider(LocalWallClock provides { p.today!!.now * 1000 }) {
                    LifeDirectorScreen(
                        p,
                        DirectorUi(),
                        DirectorActions(),
                        animate = false,
                        removal = drag,
                    )
                }
            }
        }
        return probe.nodes
    }

    private companion object {
        const val SEGMENT_DP = 48f
    }

    private fun List<A11yNode>.row(title: String) =
        firstOrNull { it.text.contains(title) } ?: error("no row \"$title\"")

    @Test
    fun eachRemovableSessionOffersRemoveFromTodayAndNothingElseDoes() {
        val nodes = read(hold = false)
        // the running order's rows (a dial segment is a 48 dp square; see the next test)
        val offering =
            nodes.filter { "Remove from today" in it.actions && it.heightDp != SEGMENT_DP }
        assertTrue(offering.all { it.actions == listOf("Remove from today") })
        // the sessions the page says can be removed, and no other row (a session that cannot be,
        // a meal, work, the dentist) and not the up-next card
        assertEquals(2, offering.size)
        assertTrue(offering[0].text.contains("Resistance Training"))
        assertTrue(offering[1].text.contains("Focused Reading"))
    }

    /**
     * The dial's segments are individually focusable (48 dp nodes with the session's name and
     * state), so each session the page says can be removed offers the same action as its row, and
     * no other segment (a session that cannot be, a meal, work, sleep) does.
     */
    @Test
    fun eachRemovableSessionsSegmentOnTheDialOffersRemoveFromTodayToo() {
        val segments =
            read(hold = false).filter { it.heightDp == SEGMENT_DP && it.text.isNotEmpty() }
        val offering = segments.filter { "Remove from today" in it.actions }
        assertEquals(2, offering.size)
        assertTrue(offering[0].text.contains("Resistance Training"))
        assertTrue(offering[1].text.contains("Focused Reading"))
        assertTrue(offering.all { it.actions == listOf("Remove from today") && it.clickable })
        assertTrue(segments.size > offering.size)
    }

    @Test
    fun theTrashIsNotThereUntilASessionIsLifted() {
        assertFalse(read(hold = false).any { it.text.contains("to remove Resistance Training") })
    }

    @Test
    fun theTrashAnnouncesItselfWhileASessionIsHeld() {
        val trash = read(hold = true).row("Drop here to remove Resistance Training from today")
        assertTrue("a live region, so a change is read out", trash.live)
    }
}
