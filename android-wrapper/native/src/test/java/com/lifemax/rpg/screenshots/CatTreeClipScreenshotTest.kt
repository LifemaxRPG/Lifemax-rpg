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
import com.lifemax.rpg.feature.pet.body.World
import com.lifemax.rpg.feature.pet.brain.Spots
import com.lifemax.rpg.feature.pet.room.DepthLevel
import com.lifemax.rpg.feature.pet.room.RoomStyle
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * The owner's "the cat's ears are poking through the cat tree": a black cat sitting up at the foot
 * of the cat tree, on the Overview card in the 2.5D room at RICH, a kitten and a grown cat, with
 * the camera held at either end of its swing and at rest.
 *
 * At the cats' own floor depth the foot of the tree is under its middle platform, where no cat
 * sitting up fits: the platform's front was drawn across its head and its ears stood above it. The
 * room now draws a cat there in front of the tree (`AtTheTreeFoot`: a cat walking through, or at
 * the second bowl). The brain's place for scratching the post is in front of the tree
 * (`AtTheScratchPost`), so a cat rests there in plain view.
 */
class CatTreeClipScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(density = Density(420)),
            renderingMode = SessionParams.RenderingMode.SHRINK,
            maxPercentDifference = 0.001,
        )

    private fun card(stage: Int, sway: Float, z: Float = World.ART_Z) {
        val j =
            JSONObject(
                    javaClass
                        .getResource("/fixtures/overview-cards-two-cats-live.json")!!
                        .readText()
                )
                .put("light", false)
                .put("tokens", ScreenshotPalette.tokens(false))
        val cards = WebOverviewRepository().apply { accept(j, true) }.state.value!!
        val looks = listOf(LiveLook("Ted", COAT, stage, false))
        val spot = Spots.SCRATCH_TREE
        val stageView =
            LiveDepthShots.stage(
                looks,
                listOf(LiveSpot(spot.x, 1, z = z)),
                HOUR,
                SECONDS,
                true,
                RoomStyle.live(DepthLevel.RICH),
                sway,
            )
        paparazzi.snapshot() {
            OverviewTheme(cards.tokens, cards.light) {
                val c = LocalOverviewColors.current
                Box(Modifier.fillMaxWidth().background(c.bg).padding(12.dp)) {
                    CatCardView(
                        cards.tiles.cat!!,
                        cards.catPortrait,
                        {},
                        visible = false,
                        house = CatHousePreview(stageView),
                    )
                }
            }
        }
    }

    @Test fun kittenAtTheTreeFoot() = card(KITTEN, 0f)

    @Test fun kittenAtTheTreeFootPannedLeft() = card(KITTEN, -1f)

    @Test fun kittenAtTheTreeFootPannedRight() = card(KITTEN, 1f)

    @Test fun adultAtTheTreeFoot() = card(ADULT, 0f)

    @Test fun adultAtTheTreeFootPannedLeft() = card(ADULT, -1f)

    @Test fun adultAtTheTreeFootPannedRight() = card(ADULT, 1f)

    @Test fun kittenAtTheScratchPost() = card(KITTEN, 0f, Spots.SCRATCH_TREE.z)

    @Test fun adultAtTheScratchPost() = card(ADULT, 0f, Spots.SCRATCH_TREE.z)

    private companion object {
        const val COAT = "black"
        const val KITTEN = 2
        const val ADULT = -1
        const val HOUR = 12.0
        const val SECONDS = 4f
    }
}
