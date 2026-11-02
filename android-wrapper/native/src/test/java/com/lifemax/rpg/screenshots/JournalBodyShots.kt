package com.lifemax.rpg.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.core.designsystem.PageTheme
import com.lifemax.rpg.feature.journal.presentation.body.JournalBodyColors
import com.lifemax.rpg.feature.journal.presentation.body.LocalJournalBodyColors

/**
 * The Journal body's screenshot set-up, shared by its test classes: the owner's 411 dp phone, a
 * square screenful at the device's resolution, the page's palettes, and the Library's 12 dp sides
 * and 16 dp top (MediaScreen's list), scrolled [scrolledDp] down.
 */
internal object JournalBodyShots {
    fun phone(fontScale: Float = 1f) =
        DeviceConfig.PIXEL_5.copy(
            screenHeight = 1080,
            density = Density(420),
            fontScale = fontScale,
        )

    fun paparazzi() =
        Paparazzi(deviceConfig = phone(), useDeviceResolution = true, maxPercentDifference = 0.001)

    fun Paparazzi.largeText() = unsafeUpdateConfig(phone(fontScale = 2f))

    fun Paparazzi.shot(
        light: Boolean = false,
        scrolledDp: Int = 0,
        content: @Composable ColumnScope.() -> Unit,
    ) = snapshot { Frame(light, scrolledDp, content) }

    @Composable
    fun Frame(light: Boolean, scrolledDp: Int, content: @Composable ColumnScope.() -> Unit) {
        val c = JournalBodyColors(ScreenshotPalette.tokenMap(light), light)
        PageTheme(c) {
            CompositionLocalProvider(LocalJournalBodyColors provides c) {
                val scroll =
                    rememberScrollState(with(LocalDensity.current) { scrolledDp.dp.roundToPx() })
                Column(
                    Modifier.fillMaxSize()
                        .background(c.bg)
                        .verticalScroll(scroll)
                        .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
                    content = content,
                )
            }
        }
    }
}
