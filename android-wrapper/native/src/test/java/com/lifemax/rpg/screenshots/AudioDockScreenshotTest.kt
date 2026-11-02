package com.lifemax.rpg.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.app.shell.audio.AudioDock
import com.lifemax.rpg.app.shell.audio.AudioDockActions
import com.lifemax.rpg.app.shell.audio.AudioDockMetrics
import com.lifemax.rpg.app.shell.audio.AudioDockState
import com.lifemax.rpg.feature.library.data.WebMediaRepository
import com.lifemax.rpg.feature.library.presentation.MediaActions
import com.lifemax.rpg.feature.library.presentation.MediaScreen
import com.lifemax.rpg.feature.library.presentation.MediaViewModel
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * The audiobook dock as the phone draws it, over the Media screen with the bottom bar's place
 * marked, on a 411 dp wide phone. The screen clears the dock the way the shell makes it (the bars'
 * inset plus the dock's height), so each image also shows the list ending above the dock. The dock
 * has no part in the bars: it is its own view above them.
 */
class AudioDockScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(deviceConfig = phone(), useDeviceResolution = true, maxPercentDifference = 0.001)

    private fun phone(fontScale: Float = 1f) =
        DeviceConfig.PIXEL_5.copy(
            screenHeight = 1080,
            density = Density(420),
            fontScale = fontScale,
        )

    private fun playing(light: Boolean) =
        AudioDockState(
            visible = true,
            mediaId = "m_book",
            title = "Project Hail Mary",
            sub = "hail-mary.m4b · 480.0 MB",
            playing = true,
            position = 15_000.0,
            duration = 58_200.0,
            rate = 1.25,
            rates = listOf(0.8, 1.0, 1.25, 1.5, 1.75, 2.0),
            tokens = ScreenshotPalette.tokenMap(light),
            light = light,
            atMs = 0,
        )

    private fun model(light: Boolean) =
        MediaViewModel(
                WebMediaRepository().apply {
                    accept(
                        JSONObject(javaClass.getResource("/fixtures/media-full.json")!!.readText())
                            .put("light", light)
                            .put("tokens", ScreenshotPalette.tokens(light)),
                        true,
                    )
                }
            )
            .apply { onProjection() }

    /** The shell's own bar is 72 dp here; the dock sits on it, the list clears both. */
    private fun snap(state: AudioDockState, nowMs: Long = 0) {
        val vm = model(state.light)
        val bar = 72f
        paparazzi.snapshot {
            Box(Modifier.fillMaxSize()) {
                MediaScreen(
                    vm.ui!!,
                    MediaActions(),
                    bottomInsetDp = bar + state.heightDp,
                    list =
                        rememberLazyListState(
                            FIRST_ROW,
                            with(LocalDensity.current) { 40.dp.roundToPx() },
                        ),
                )
                Bar(Modifier.align(Alignment.BottomCenter), bar, state)
                Box(
                    Modifier.align(Alignment.BottomCenter)
                        .padding(bottom = (bar + AudioDockMetrics.GAP_DP).dp)
                        .height(
                            (if (state.minimized) AudioDockMetrics.MINIMIZED_DP
                                else AudioDockMetrics.EXPANDED_DP)
                                .dp
                        )
                ) {
                    AudioDock(state, nowMs, AudioDockActions())
                }
            }
        }
    }

    @Composable
    private fun Bar(modifier: Modifier, heightDp: Float, state: AudioDockState) {
        val c = ScreenshotPalette.tokenMap(state.light)
        Box(modifier.fillMaxWidth().height(heightDp.dp).background(Color(c.getValue("bg-elev"))))
    }

    @Test fun expandedDark() = snap(playing(false))

    @Test fun expandedLight() = snap(playing(true))

    /** Four seconds later at 1.25x: the bar and the time have moved by the dock's own clock. */
    @Test fun extrapolatedDark() = snap(playing(false), nowMs = 4_000)

    @Test fun pausedDark() = snap(playing(false).copy(playing = false))

    @Test fun minimizedDark() = snap(playing(false).copy(minimized = true))

    @Test fun minimizedLight() = snap(playing(true).copy(minimized = true))

    /** A book being swapped: the dock stays up and says so. */
    @Test
    fun loadingDark() =
        snap(playing(false).copy(playing = false, loading = true, sub = "Loading…", position = 0.0))

    /**
     * Larger text: the dock's text stops at 1.15x so its height, which the screens clear, holds.
     */
    @Test
    fun largeText() {
        paparazzi.unsafeUpdateConfig(phone(fontScale = 2f))
        snap(playing(false))
    }

    private companion object {
        /** the Media list item of the first row, as MediaScreenshotTest counts */
        const val FIRST_ROW = 4
    }
}
