package com.lifemax.rpg.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.android.resources.Density
import com.lifemax.rpg.core.designsystem.PageColors
import com.lifemax.rpg.core.designsystem.PageTheme
import com.lifemax.rpg.feature.overview.domain.AppsTile
import com.lifemax.rpg.feature.overview.domain.HealthTile
import com.lifemax.rpg.feature.overview.domain.WeekTile
import com.lifemax.rpg.feature.overview.presentation.cards.AppsCard
import com.lifemax.rpg.feature.overview.presentation.cards.AttributesCard
import com.lifemax.rpg.feature.overview.presentation.cards.CatCard
import com.lifemax.rpg.feature.overview.presentation.cards.HealthCard
import com.lifemax.rpg.feature.overview.presentation.cards.WeekCard
import com.lifemax.rpg.screenshots.OverviewCardsBFixtures as F
import org.junit.Rule
import org.junit.Test

/**
 * Cards B (attributes, the week chart, health, apps and the cat) as the phone draws them: each card
 * on its own, as tall as it draws, on a phone like the owner's (411 dp wide) with the page's 12 px
 * side margins, so each image lies beside the page's own card at 412 CSS px. Dark and light, the
 * extras states (a cat, two cats, a cat that has passed, the stray kitten, health off, waiting and
 * goal reached, apps with icons and none, over pace, a picked day, an empty week) and twice the
 * text size.
 */
class OverviewCardsBScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = phone(),
            renderingMode = SessionParams.RenderingMode.SHRINK,
            maxPercentDifference = 0.001,
        )

    private fun phone(fontScale: Float = 1f) =
        DeviceConfig.PIXEL_5.copy(density = Density(420), fontScale = fontScale)

    private fun card(light: Boolean, name: String? = null, content: @Composable () -> Unit) =
        paparazzi.snapshot(name) {
            val colors = PageColors(ScreenshotPalette.tokenMap(light), light)
            PageTheme(colors) {
                Box(Modifier.fillMaxWidth().background(colors.bg).padding(12.dp)) { content() }
            }
        }

    private fun attrs(light: Boolean, name: String? = null) =
        card(light, name) { AttributesCard(F.attrs, {}, {}) }

    private fun week(
        light: Boolean,
        tile: WeekTile = F.week,
        selected: Int = tile.selected,
        name: String? = null,
    ) = card(light, name) { WeekCard(tile, selected, null, {}) }

    private fun health(light: Boolean, tile: HealthTile = F.healthOn, name: String? = null) =
        card(light, name) { HealthCard(tile, {}, {}) }

    private fun apps(light: Boolean, tile: AppsTile = F.apps, name: String? = null) =
        card(light, name) { AppsCard(tile, icons::get, {}, {}, {}) }

    @Test fun attributesDark() = attrs(false)

    @Test fun attributesLight() = attrs(true)

    @Test fun weekDark() = week(false)

    @Test fun weekLight() = week(true)

    @Test fun weekOverPaceDark() = week(false, F.weekOverPace)

    @Test fun weekOverPaceLight() = week(true, F.weekOverPace)

    @Test fun weekPickedDay() = week(false, selected = 3)

    @Test fun weekEmpty() = week(false, F.weekEmpty)

    @Test fun healthOnDark() = health(false)

    @Test fun healthOnLight() = health(true)

    @Test fun healthOffDark() = health(false, F.healthOff)

    @Test fun healthOffLight() = health(true, F.healthOff)

    @Test fun healthWaiting() = health(false, F.healthWaiting)

    @Test fun healthGoalReached() = health(false, F.healthGoal)

    @Test fun appsDark() = apps(false)

    @Test fun appsLight() = apps(true)

    @Test fun appsEmptyDark() = apps(false, F.appsEmpty)

    @Test fun appsEmptyLight() = apps(true, F.appsEmpty)

    @Test fun catDark() = card(false) { CatCard(F.pet, {}) }

    @Test fun catLight() = card(true) { CatCard(F.pet, {}) }

    @Test fun twoCats() = card(false) { CatCard(F.twoCats, {}) }

    @Test fun catGoneDark() = card(false) { CatCard(F.catGone, {}) }

    @Test fun catGoneLight() = card(true) { CatCard(F.catGone, {}) }

    @Test fun strayKitten() = card(false) { CatCard(F.stray, {}) }

    @Test
    fun largerText() {
        paparazzi.unsafeUpdateConfig(phone(fontScale = 2f))
        attrs(false, "attributes")
        week(false, F.weekOverPace, name = "week")
        health(false, name = "health")
        apps(false, name = "apps")
        card(false, "cat") { CatCard(F.twoCats, {}) }
    }

    private companion object {
        /** App icons as the launcher sends them (84 px), plain squares of colour. */
        val icons: Map<String, ImageBitmap> =
            mapOf(
                "icon-green" to square(Color(0xFF0B8A12)),
                "icon-blue" to square(Color(0xFF1A73E8)),
                "icon-orange" to square(Color(0xFFF57C00)),
            )

        fun square(color: Color): ImageBitmap =
            ImageBitmap(84, 84).also {
                Canvas(it)
                    .drawRect(
                        Rect(Offset.Zero, Size(84f, 84f)),
                        Paint().apply { this.color = color },
                    )
            }
    }
}
