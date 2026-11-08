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
import com.lifemax.rpg.feature.overview.domain.LiveCat
import com.lifemax.rpg.feature.overview.presentation.LocalOverviewColors
import com.lifemax.rpg.feature.overview.presentation.OverviewTheme
import com.lifemax.rpg.feature.overview.presentation.cards.CatCardView
import com.lifemax.rpg.feature.overview.presentation.cards.CatLivePreview
import com.lifemax.rpg.feature.pet.art.CoatFixtures
import com.lifemax.rpg.feature.pet.art.FakeCoatBridge
import com.lifemax.rpg.feature.pet.body.Autopilot
import com.lifemax.rpg.feature.pet.body.CatLife
import com.lifemax.rpg.feature.pet.body.PlanStep
import com.lifemax.rpg.feature.pet.presentation.CatArt
import com.lifemax.rpg.feature.pet.presentation.CatSprites
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * A cat turning round on the Overview's cat card, caught between the steps of the lab's `turn` row
 * (docs/specs/PET_ART_LAB.md): the front view, the three-quarter view of the far side, a kitten's
 * front view and a genetic coat's first three-quarter view.
 */
class CatTurnScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(density = Density(420)),
            renderingMode = SessionParams.RenderingMode.SHRINK,
            maxPercentDifference = 0.001,
        )

    /** A cat told to turn round, stepped [seconds] into the turn. */
    private fun turning(coat: String, seconds: Float): CatLife =
        CatLife(Autopilot.seedFor("Miso", coat), false, autopilot = false).also {
            it.body.replacePlan(1, listOf(PlanStep.Face(-it.body.pose.facing)))
            it.advance(seconds)
        }

    /** The sprites of [live]; a genetic coat is answered from the page's recordings. */
    private fun sprites(live: LiveCat): CatSprites {
        val label =
            CoatFixtures.coats.entries.firstOrNull { it.value == live.coat }?.key
                ?: return CatArt.sprites(live.coat, live.stage)!!
        val bridge = FakeCoatBridge()
        CatArt.coats.bind(bridge.requester)
        var answered = 0
        var found: CatSprites? = null
        repeat(SPRITE_ROUNDS) {
            found = CatArt.sprites(live.coat, live.stage)
            while (answered < bridge.sent.size) {
                val s = bridge.sent[answered++]
                if (s.kind.key in CoatFixtures.kinds) {
                    CatArt.coats.onReply(CoatFixtures.reply(label, s.kind.key, s.id))
                }
            }
        }
        return found!!
    }

    private fun shoot(fixture: String, life: CatLife) {
        val j =
            JSONObject(javaClass.getResource("/fixtures/overview-cards-$fixture.json")!!.readText())
                .put("light", false)
                .put("tokens", ScreenshotPalette.tokens(false))
        val cards = WebOverviewRepository().apply { accept(j, true) }.state.value!!
        val live = cards.tiles.cat!!.live!!.first
        val sprites = sprites(live)
        paparazzi.snapshot {
            OverviewTheme(cards.tokens, cards.light) {
                val c = LocalOverviewColors.current
                Box(Modifier.fillMaxWidth().background(c.bg).padding(12.dp)) {
                    CatCardView(
                        cards.tiles.cat!!,
                        cards.catPortrait,
                        {},
                        visible = false,
                        preview = CatLivePreview(sprites, life),
                    )
                }
            }
        }
    }

    @Test fun midTurnFrontView() = shoot("pet-live", turning("ginger", FRONT_S))

    @Test fun midTurnFarSide() = shoot("pet-live", turning("ginger", FAR_SIDE_S))

    @Test fun kittenMidTurnFrontView() = shoot("kitten-live", turning("ginger", FRONT_S))

    @Test fun geneticCoatTurnStarting() = shoot("pet-live-dna", turning("dna", NEAR_SIDE_S))

    private companion object {
        /** Into a turn on the spot (0.38 s): the three-quarter, front and far-side steps. */
        const val NEAR_SIDE_S = 0.12f
        const val FRONT_S = 0.2f
        const val FAR_SIDE_S = 0.27f
        const val SPRITE_ROUNDS = 4
    }
}
