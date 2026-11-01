package com.lifemax.rpg.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.core.data.objects
import com.lifemax.rpg.feature.lifedirector.data.WebLifeDirectorRepository
import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorProjection
import com.lifemax.rpg.feature.lifedirector.presentation.GuideRun
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorText
import com.lifemax.rpg.feature.lifedirector.presentation.guide.CoachMarks
import com.lifemax.rpg.feature.lifedirector.presentation.guide.GuideAnchors
import com.lifemax.rpg.feature.lifedirector.presentation.guide.guideAnchor
import com.lifemax.rpg.feature.lifedirector.presentation.time.MiniDial
import com.lifemax.rpg.feature.lifedirector.presentation.time.MiniDialWords
import com.lifemax.rpg.feature.lifedirector.presentation.time.MiniSleepDial
import com.lifemax.rpg.feature.lifedirector.presentation.time.MinuteSpan
import com.lifemax.rpg.feature.lifedirector.presentation.time.miniGuideTags
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * Set-up's guide drawn natively (phase 4B), from the projection the page gives a screen that draws
 * My time (director-guide-setup.json): the sleep step, with the screen dimmed around the mini sleep
 * dial through a round spotlight, the page's Bedtime and Wake up tags pinned on the dial, and the
 * card in the page's words. My time itself is another screen's; here the sleep card is stood in for
 * by its title and the mini dial, named time.sleep as My time names it. A whole phone screen.
 */
class LifeDirectorSetupGuideScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 2340, density = Density(420)),
            useDeviceResolution = true,
            maxPercentDifference = 0.001,
        )

    private val raw =
        JSONObject(javaClass.getResource("/fixtures/director-guide-setup.json")!!.readText())
            .put("light", false)
            .put("tokens", ScreenshotPalette.tokens(false))

    private val projection: LifeDirectorProjection =
        WebLifeDirectorRepository().apply { accept(raw, true) }.state.value!!

    /** the main dial's face (radii, hour words), from the lived-in day, as My time borrows it */
    private val face =
        WebLifeDirectorRepository()
            .apply {
                accept(
                    JSONObject(javaClass.getResource("/fixtures/director-today.json")!!.readText())
                        .put("light", false)
                        .put("tokens", ScreenshotPalette.tokens(false)),
                    true,
                )
            }
            .state
            .value!!
            .today!!
            .dial

    /** the page's time.sleep, read straight from the fixture until My time's decoder lands */
    private val dial: MiniDial =
        raw.getJSONObject("time").getJSONObject("sleep").let { s ->
            val labels = s.getJSONObject("labels")
            fun strings(key: String) =
                s.getJSONArray(key).let { a -> (0 until a.length()).map { a.getString(it) } }
            MiniDial(
                bed = s.getInt("bed"),
                wake = s.getInt("wake"),
                bedText = s.getString("bedText"),
                wakeText = s.getString("wakeText"),
                length = s.getString("length"),
                step = s.getInt("step"),
                context =
                    s.getJSONArray("context").objects().map {
                        MinuteSpan(it.getInt("from"), it.getInt("to"))
                    },
                clock = strings("clock"),
                lengths = strings("lengths"),
                words =
                    MiniDialWords(
                        labels.getString("centre"),
                        labels.getString("bedHandle"),
                        labels.getString("wakeHandle"),
                        "Sleep window",
                    ),
            )
        }

    private fun snap(step: Int, name: String) {
        val p = projection
        val run = GuideRun("setup", step)
        paparazzi.snapshot(name) {
            val anchors = remember { GuideAnchors() }
            Box(Modifier.fillMaxSize().background(DirectorPalette.bg0)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 96.dp)) {
                    Column(
                        Modifier.clip(RoundedCornerShape(18.dp))
                            .background(DirectorPalette.sheetTop)
                            .padding(16.dp)
                    ) {
                        Text(
                            raw.getJSONObject("time").getJSONObject("sleep").getString("title"),
                            style = DirectorText,
                            color = DirectorPalette.ink,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        MiniSleepDial(
                            dial,
                            dial.bed,
                            dial.wake,
                            face.radii,
                            face.hourWords,
                            { _, _ -> },
                            reducedMotion = true,
                            tags = miniGuideTags(p, run),
                            dialModifier = Modifier.guideAnchor(anchors, "time.sleep"),
                        )
                    }
                }
                CoachMarks(p.guide!!, step, anchors, {}, {}, {})
            }
        }
    }

    /** "Drag to set your sleep": the mini dial lit, Bedtime and Wake up pinned on its handles. */
    @Test fun sleep() = snap(1, "setup-sleep")

    /** The first step, whose part (My time's steps) is not drawn here: the card alone, centred. */
    @Test fun intro() = snap(0, "setup-intro")
}
