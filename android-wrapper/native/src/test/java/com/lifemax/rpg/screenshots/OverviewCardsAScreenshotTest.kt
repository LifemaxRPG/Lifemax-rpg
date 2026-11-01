package com.lifemax.rpg.screenshots

import androidx.compose.foundation.lazy.rememberLazyListState
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.feature.overview.data.WebOverviewRepository
import com.lifemax.rpg.feature.overview.presentation.OverviewActions
import com.lifemax.rpg.feature.overview.presentation.OverviewScreen
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * The Overview cards of Cards A (the head, the tip, Next moves, Level & today, Rewards, Daily luck,
 * Recent activity) as the phone draws them, on a 411 dp wide phone like the owner's, from the
 * page's projections (overview-cards-*.json, made from the page's own data functions). Each image
 * is a square screenful scrolled to a tile; the tiles Cards B draws are not drawn here.
 */
class OverviewCardsAScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(deviceConfig = phone(), useDeviceResolution = true, maxPercentDifference = 0.001)

    private fun phone(fontScale: Float = 1f) =
        DeviceConfig.PIXEL_5.copy(
            screenHeight = 1080,
            density = Density(420),
            fontScale = fontScale,
        )

    private fun fixture(name: String) =
        JSONObject(javaClass.getResource("/fixtures/overview-cards-$name.json")!!.readText())

    /** The screen from tile [tile] down ("" for the top), in [light] or dark. */
    private fun snap(
        name: String = "sample",
        light: Boolean = false,
        tile: String = "",
        edit: (JSONObject) -> Unit = {},
    ) {
        val j = fixture(name).put("light", light).put("tokens", ScreenshotPalette.tokens(light))
        edit(j)
        val cards = WebOverviewRepository().apply { accept(j, true) }.state.value!!
        val index =
            if (tile.isEmpty()) 0
            else 1 + (if (cards.tip != null) 1 else 0) + cards.shown.indexOf(tile)
        paparazzi.snapshot {
            OverviewScreen(cards, OverviewActions(), list = rememberLazyListState(index))
        }
    }

    /** The head (date, greeting, Edit, the crest) and Next moves, fading attributes first. */
    @Test fun topDark() = snap()

    @Test fun topLight() = snap(light = true)

    /** Level & today: the ring's arc, the badge, the XP bar, the streak and the foot. */
    @Test fun heroDark() = snap(tile = "hero")

    @Test fun heroLight() = snap(light = true, tile = "hero")

    /** Rewards with shields and rested XP, then Daily luck not rolled and Recent activity. */
    @Test fun rewardsLuckRecentDark() = snap(tile = "rewards")

    @Test fun rewardsLuckRecentLight() = snap(light = true, tile = "rewards")

    /** A new player: the feature tip under the head, and Next moves' first steps. */
    @Test fun newTipDark() = snap("new")

    @Test fun newTipLight() = snap("new", light = true)

    /** A new player's empty ring, then luck rolled (its tier's colour) and no activity yet. */
    @Test fun newHeroLuckDark() = snap("new", tile = "hero")

    @Test fun newHeroLuckLight() = snap("new", light = true, tile = "hero")

    /** Over the pace: the gold, glowing number and two attributes' arcs. */
    @Test
    fun overPaceDark() =
        snap(tile = "hero") { j ->
            val ring = j.getJSONObject("tiles").getJSONObject("hero").getJSONObject("ring")
            ring.put("today", 748).put("over", true).put("of", "of 60 XP · +1147%")
            ring.put(
                "arcs",
                JSONArray()
                    .put(JSONObject().put("color", 0xFFFF7A57).put("offset", 0).put("length", 80.5))
                    .put(
                        JSONObject()
                            .put("color", 0xFFA78BFA)
                            .put("offset", -82.9)
                            .put("length", 14.7)
                    ),
            )
        }

    /** Larger text (Settings, font size, at its largest). */
    @Test
    fun largeTextTop() {
        paparazzi.unsafeUpdateConfig(phone(fontScale = 2f))
        snap()
    }

    @Test
    fun largeTextHero() {
        paparazzi.unsafeUpdateConfig(phone(fontScale = 2f))
        snap(tile = "hero")
    }

    @Test
    fun largeTextLuck() {
        paparazzi.unsafeUpdateConfig(phone(fontScale = 2f))
        snap(tile = "luck")
    }
}
