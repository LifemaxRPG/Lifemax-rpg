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
import com.lifemax.rpg.feature.pet.body.PlanStep
import com.lifemax.rpg.feature.pet.brain.Spots
import com.lifemax.rpg.feature.pet.data.WebPetRepository
import com.lifemax.rpg.feature.pet.presentation.PetActions
import com.lifemax.rpg.feature.pet.presentation.PetScreen
import com.lifemax.rpg.feature.pet.presentation.PetUi
import com.lifemax.rpg.feature.pet.presentation.RoomHost
import com.lifemax.rpg.feature.pet.presentation.RoomState
import com.lifemax.rpg.feature.pet.room.DepthLevel
import com.lifemax.rpg.feature.pet.room.RoomStyle
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * The owner's "the cats clip behind the sofa": the two living-room places by the sofa the brain
 * rests a cat at (the tucked-away corner at its far end and the sofa's front), each with a cat
 * loafing there, on the Overview card and on the Cat tab, in the 2.5D room at RICH and in the
 * classic room. Both cats are in plain sight; the corner was behind the sofa before, where the 2.5D
 * room hid all of a cat (OccludersTest measures it).
 */
class SofaVisibilityScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(density = Density(420)),
            renderingMode = SessionParams.RenderingMode.SHRINK,
            maxPercentDifference = 0.001,
        )

    private val resting =
        listOf(
            LiveSpot(Spots.BEHIND_CUSHION.x, 1, z = Spots.BEHIND_CUSHION.z, plan = loaf),
            LiveSpot(Spots.SCRATCH_SOFA.x, -1, z = Spots.SCRATCH_SOFA.z, plan = loaf),
        )

    private fun card(style: RoomStyle) {
        val j =
            JSONObject(
                    javaClass
                        .getResource("/fixtures/overview-cards-two-cats-live.json")!!
                        .readText()
                )
                .put("light", false)
                .put("tokens", ScreenshotPalette.tokens(false))
        val cards = WebOverviewRepository().apply { accept(j, true) }.state.value!!
        val cats = cards.tiles.cat!!.live!!.house
        val looks = cats.map { LiveLook(it.name ?: "cat", it.coat, it.stage, it.weak) }
        val stage = LiveDepthShots.stage(looks, resting, HOUR, SECONDS, true, style)
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

    @Test fun cardRichRestingBySofa() = card(RoomStyle.live(DepthLevel.RICH))

    @Test fun cardClassicRestingBySofa() = card(RoomStyle.CLASSIC)

    @Test
    fun tabRichRestingBySofa() {
        val p =
            WebPetRepository()
                .apply {
                    accept(
                        JSONObject(
                                javaClass.getResource("/fixtures/pet-two-cats.json")!!.readText()
                            )
                            .put("light", false)
                            .put("tokens", ScreenshotPalette.tokens(false)),
                        true,
                    )
                }
                .state
                .value!!
        val looks = p.cats.map { LiveLook(it.name, it.coat, it.stage, it.weak) }
        val stage =
            LiveDepthShots.stage(
                looks,
                resting,
                HOUR,
                SECONDS,
                true,
                RoomStyle.live(DepthLevel.RICH),
            )
        paparazzi.snapshot() {
            PetScreen(p, PetUi(), PetActions()) { modifier ->
                RoomHost(RoomState.Ready(stage), running = false, still = true, modifier)
            }
        }
    }

    private companion object {
        val loaf = listOf(PlanStep.Act("loaf", 60f))
        const val HOUR = 12.0
        const val SECONDS = 4f
    }
}
