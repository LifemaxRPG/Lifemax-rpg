package com.lifemax.rpg.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.android.resources.Density
import com.lifemax.rpg.feature.overview.data.WebOverviewRepository
import com.lifemax.rpg.feature.overview.domain.OverviewCards
import com.lifemax.rpg.feature.overview.presentation.LocalOverviewColors
import com.lifemax.rpg.feature.overview.presentation.OverviewImages
import com.lifemax.rpg.feature.overview.presentation.OverviewTheme
import com.lifemax.rpg.feature.overview.presentation.cards.AppsCardView
import com.lifemax.rpg.feature.overview.presentation.cards.AttributesCardView
import com.lifemax.rpg.feature.overview.presentation.cards.CatCardView
import com.lifemax.rpg.feature.overview.presentation.cards.HealthCardView
import com.lifemax.rpg.feature.overview.presentation.cards.WeekCardView
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * Cards B (attributes, the week chart, health, apps and the cat) as the phone draws them, from the
 * page's own projections (overview-cards-*.json): each card on its own, as tall as it draws, on a
 * phone like the owner's (411 dp wide) with the page's 12 px side margins, so each image lies
 * beside the page's own card at 412 CSS px. Dark and light, the states the page's fixtures hold (a
 * cat with its portrait, two cats, a cat that has passed, health off, on and goal reached, apps
 * with an icon and none, over pace, a picked day, an empty week) and twice the text size.
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

    private fun cards(name: String, light: Boolean): OverviewCards {
        val j =
            JSONObject(javaClass.getResource("/fixtures/overview-cards-$name.json")!!.readText())
                .put("light", light)
                .put("tokens", ScreenshotPalette.tokens(light))
        return WebOverviewRepository().apply { accept(j, true) }.state.value!!
    }

    private fun card(
        fixture: String,
        light: Boolean = false,
        name: String? = null,
        content: @Composable (OverviewCards) -> Unit,
    ) {
        val p = cards(fixture, light)
        paparazzi.snapshot(name) {
            OverviewTheme(p.tokens, p.light) {
                val c = LocalOverviewColors.current
                Box(Modifier.fillMaxWidth().background(c.bg).padding(12.dp)) { content(p) }
            }
        }
    }

    private fun attrs(light: Boolean, name: String? = null) =
        card("established", light, name) { AttributesCardView(it.tiles.attrs, {}, {}) }

    private fun week(
        fixture: String = "established",
        light: Boolean = false,
        selected: Int? = null,
        name: String? = null,
    ) =
        card(fixture, light, name) {
            WeekCardView(it.tiles.week, selected ?: it.tiles.week.selected, null, {})
        }

    private fun health(fixture: String, light: Boolean = false, name: String? = null) =
        card(fixture, light, name) { HealthCardView(it.tiles.health!!, {}, {}) }

    private fun apps(fixture: String, light: Boolean = false, name: String? = null) =
        card(fixture, light, name) { p ->
            val images = remember { OverviewImages() }
            AppsCardView(p.tiles.apps!!, { images.get(p.images, it) }, {}, {}, {})
        }

    private fun cat(fixture: String, light: Boolean = false, name: String? = null) =
        card(fixture, light, name) { CatCardView(it.tiles.cat!!, it.catPortrait, {}) }

    @Test fun attributesDark() = attrs(false)

    @Test fun attributesLight() = attrs(true)

    @Test fun weekDark() = week()

    @Test fun weekLight() = week(light = true)

    @Test fun weekOverPaceDark() = week("over-pace")

    @Test fun weekOverPaceLight() = week("over-pace", light = true)

    @Test fun weekPickedDay() = week(selected = 3)

    @Test fun weekEmpty() = week("new")

    @Test fun healthOnDark() = health("health")

    @Test fun healthOnLight() = health("health", light = true)

    @Test fun healthOffDark() = health("established")

    @Test fun healthOffLight() = health("established", light = true)

    @Test fun healthGoalReached() = health("health-goal")

    @Test fun appsDark() = apps("health")

    @Test fun appsLight() = apps("health", light = true)

    @Test fun appsEmptyDark() = apps("established")

    @Test fun appsEmptyLight() = apps("established", light = true)

    @Test fun catDark() = cat("pet")

    @Test fun catLight() = cat("pet", light = true)

    @Test fun twoCats() = cat("two-cats")

    @Test fun catGoneDark() = cat("cat-gone")

    @Test fun catGoneLight() = cat("cat-gone", light = true)

    @Test
    fun largerText() {
        paparazzi.unsafeUpdateConfig(phone(fontScale = 2f))
        attrs(false, "attributes")
        week("over-pace", name = "week")
        health("health", name = "health")
        apps("health", name = "apps")
        cat("two-cats", name = "cat")
    }
}
