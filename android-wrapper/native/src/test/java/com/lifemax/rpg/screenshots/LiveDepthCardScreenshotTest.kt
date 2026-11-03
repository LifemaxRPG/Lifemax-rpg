package com.lifemax.rpg.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.android.resources.Density
import com.lifemax.rpg.feature.overview.data.WebOverviewRepository
import com.lifemax.rpg.feature.overview.presentation.LocalOverviewColors
import com.lifemax.rpg.feature.overview.presentation.OverviewTheme
import com.lifemax.rpg.feature.overview.presentation.cards.CatCardView
import com.lifemax.rpg.feature.overview.presentation.cards.CatHousePreview
import com.lifemax.rpg.feature.pet.body.SurfaceId
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * The Overview's cat card with the live room drawn in the 2.5D style it has everywhere (MEDIUM, the
 * camera-follow parallax). Day and night, dark and light, a cat on the fridge, a cat behind the
 * sofa's arm, cats in the tree, and the camera panned to either end of the house (the pieces left
 * of the middle of the screen show their right sides and the reverse, the near rows shift against
 * the far wall). Every moment is scripted (cats placed, the stage stepped a fixed time, the hour
 * fixed, the idle drift held at rest where the image is about the pan), so each image is the same
 * every time; the clock does not run on it. The classic card's own images are the ones in
 * [CatHouseScreenshotTest].
 */
class LiveDepthCardScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(density = Density(420)),
            renderingMode = SessionParams.RenderingMode.SHRINK,
            maxPercentDifference = 0.001,
        )

    private fun card(
        spots: List<LiveSpot>,
        hour: Double = 12.0,
        light: Boolean = false,
        hold: Boolean = false,
    ) {
        val j =
            JSONObject(
                    javaClass
                        .getResource("/fixtures/overview-cards-two-cats-live.json")!!
                        .readText()
                )
                .put("light", light)
                .put("tokens", ScreenshotPalette.tokens(light))
        val cards = WebOverviewRepository().apply { accept(j, true) }.state.value!!
        val cats = cards.tiles.cat!!.live!!.house
        val looks = cats.map { LiveLook(it.name ?: "cat", it.coat, it.stage, it.weak) }
        val stage = LiveDepthShots.stage(looks, spots, hour, 4f, hold)
        paparazzi.snapshot() {
            OverviewTheme(cards.tokens, cards.light) {
                val c = LocalOverviewColors.current
                Box(Modifier.fillMaxWidth().background(c.bg).padding(12.dp)) {
                    CatCardView(
                        cards.tiles.cat!!,
                        cards.catPortrait,
                        {},
                        visible = false,
                        house = CatHousePreview(stage),
                    )
                }
            }
        }
    }

    @Test fun livingRoomDay() = card(listOf(onSofa(-118f), onSofa(-78f, -1)))

    @Test fun livingRoomNight() = card(listOf(onSofa(-112f), LiveSpot(-70f, -1)), hour = 22.5)

    @Test fun livingRoomDayLight() = card(listOf(onSofa(-118f), onSofa(-78f, -1)), light = true)

    @Test
    fun kitchenCatOnTheFridge() =
        card(listOf(LiveSpot(226f, -1, SurfaceId.FRIDGE), LiveSpot(300f, -1)))

    @Test
    fun catBehindTheSofa() =
        card(listOf(LiveSpot(-150f, 1, z = 12f), LiveSpot(-100f, -1, z = 60f)), hold = true)

    @Test
    fun cameraPannedLeft() = card(listOf(LiveSpot(-128f, 1), LiveSpot(-100f, -1)), hold = true)

    @Test
    fun cameraPannedRight() = card(listOf(LiveSpot(290f, -1), LiveSpot(330f, -1)), hold = true)

    @Test
    fun bedroomCatsInTheTreeAtNight() =
        card(
            listOf(LiveSpot(152f, 1, SurfaceId.MID), LiveSpot(154f, -1, SurfaceId.TOP)),
            hour = 23.0,
        )

    private fun onSofa(x: Float, facing: Int = 1) = LiveSpot.onSofa(x, facing)
}
