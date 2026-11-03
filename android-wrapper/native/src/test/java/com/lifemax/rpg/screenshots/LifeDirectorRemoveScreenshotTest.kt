package com.lifemax.rpg.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.feature.lifedirector.DirectorRecorder
import com.lifemax.rpg.feature.lifedirector.data.WebLifeDirectorRepository
import com.lifemax.rpg.feature.lifedirector.domain.DragPoint
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorActions
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorUi
import com.lifemax.rpg.feature.lifedirector.presentation.LifeDirectorScreen
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette
import com.lifemax.rpg.feature.lifedirector.presentation.components.LocalWallClock
import com.lifemax.rpg.feature.lifedirector.presentation.drag.DragRemoveController
import com.lifemax.rpg.feature.lifedirector.presentation.drag.LIFT_SCALE
import com.lifemax.rpg.feature.lifedirector.presentation.drag.LiftedChip
import com.lifemax.rpg.feature.lifedirector.presentation.drag.RemoveTarget
import com.lifemax.rpg.feature.lifedirector.presentation.drag.UndoBar
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * Taking a session off the day (director.remove.v1), from the projection the web app produced for a
 * screen that says it can (director-remove.json): a session lifted off the running order and held
 * on its way to the trash, the trash idle and armed, in the dark and light themes and with larger
 * text, the lifted chip (the session's dot, name and time, small and just above the finger) as it
 * looks with motion (a little larger, with a shadow), and the message with Undo. The whole phone is
 * shown (1080 by 3600 px) so the closed-up row, the chip and the trash can be seen together.
 * Everything here is the reduced-motion look (nothing slides, grows or springs) except
 * [cardWithMotion].
 */
class LifeDirectorRemoveScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(deviceConfig = phone(), useDeviceResolution = true, maxPercentDifference = 0.001)

    private fun phone(fontScale: Float = 1f) =
        DeviceConfig.PIXEL_5.copy(
            screenHeight = 3600,
            density = Density(420),
            fontScale = fontScale,
        )

    private fun repository(light: Boolean) =
        WebLifeDirectorRepository().apply {
            accept(
                JSONObject(javaClass.getResource("/fixtures/director-remove.json")!!.readText())
                    .put("light", light)
                    .put("tokens", ScreenshotPalette.tokens(light)),
                true,
            )
        }

    /**
     * The screen with Resistance Training (named [title] when given) lifted by a finger [y] px
     * down, and the finger over the trash when [armed].
     */
    private fun lifted(
        name: String,
        light: Boolean = false,
        armed: Boolean = false,
        fontScale: Float = 1f,
        y: Float = 2000f,
        title: String? = null,
    ) {
        paparazzi.unsafeUpdateConfig(phone(fontScale))
        val repo = repository(light)
        val p = repo.state.value!!
        val drag =
            DragRemoveController(
                DirectorRecorder(),
                repo.state,
                MutableStateFlow(DirectorUi()),
                { true },
                {},
            )
        drag.layout(1080f, 3600f, 64f * DENSITY, 72f * DENSITY, DENSITY)
        val row =
            p.today!!
                .ahead
                .first { it.title == "Resistance Training" }
                .let { if (title == null) it else it.copy(title = title) }
        drag.lift(row, DragPoint(540f, y))
        if (armed) drag.move(drag.target.value!!.centre)
        paparazzi.snapshot(name) {
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

    @Test fun liftedDark() = lifted("dark")

    @Test fun liftedLight() = lifted("light", light = true)

    @Test fun armedDark() = lifted("dark", armed = true, y = 3250f)

    @Test fun armedLight() = lifted("light", light = true, armed = true, y = 3250f)

    /**
     * Larger text: the chip, the trash and its label grow with it, the closed-up list stays whole.
     */
    @Test fun largeText() = lifted("large", armed = true, fontScale = 1.3f, y = 3150f)

    /** The largest text the system offers: the chip grows but stays far smaller than the row. */
    @Test fun chipLargestText() = lifted("chip-2x", fontScale = 2f)

    /** A long session name is cut on one line, the chip stops at 60% of the list's width. */
    @Test
    fun chipLongName() =
        lifted(
            "chip-long",
            title = "Deep Focus Block: Quarterly Planning and Writing Review With The Whole Team",
        )

    /** The trash on its own, idle and armed. */
    @Test
    fun targets() {
        paparazzi.unsafeUpdateConfig(phone().copy(screenHeight = 760))
        paparazzi.snapshot {
            Column(
                Modifier.fillMaxSize().background(DirectorPalette.bg0).padding(top = 56.dp),
                verticalArrangement = Arrangement.spacedBy(72.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                for (armed in listOf(false, true)) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        RemoveTarget(
                            armed = armed,
                            idleLabel = "Drop to remove",
                            armedLabel = "Release to remove",
                            description = "Drop here to remove Resistance Training from today",
                            reduced = false,
                        )
                    }
                }
            }
        }
    }

    /** The lifted chip with motion: a little larger, with its shadow, over the running order. */
    @Test
    fun cardWithMotion() {
        val repo = repository(light = false)
        val p = repo.state.value!!
        val row = p.today!!.ahead.first { it.title == "Resistance Training" }
        paparazzi.snapshot {
            CompositionLocalProvider(LocalWallClock provides { p.today!!.now * 1000 }) {
                Box(Modifier.fillMaxSize()) {
                    LifeDirectorScreen(p, DirectorUi(), DirectorActions(), animate = false)
                    LiftedChip(
                        row,
                        Modifier.offset(x = 130.dp, y = 600.dp).graphicsLayer {
                            scaleX = LIFT_SCALE
                            scaleY = LIFT_SCALE
                        },
                    )
                }
            }
        }
    }

    /** What the page said about the removal, with Undo, in the toast's place. */
    @Test
    fun undo() {
        paparazzi.unsafeUpdateConfig(phone().copy(screenHeight = 500))
        paparazzi.snapshot {
            Column(
                Modifier.fillMaxSize().background(DirectorPalette.bg0).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                UndoBar("Skipped", {})
                UndoBar("Skipped · used a rest token · 2 re-placed in the week ahead", {})
                Box(Modifier.height(1.dp))
            }
        }
    }

    private companion object {
        const val DENSITY = 2.625f
    }
}
