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
 * The live cat with a genetic coat, as the phone draws it once the page has handed over the sheets
 * (`overview.cat.coat.v1`): the sheets here are what the page recorded for the coat
 * (`tools/gen_pet_coat_fixtures.js`), delivered through a fake bridge the way replies arrive, one
 * request at a time. A calico, a colour-point (mid-walk, light) and a long-haired kitten.
 */
class CatLiveCoatScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(density = Density(420)),
            renderingMode = SessionParams.RenderingMode.SHRINK,
            maxPercentDifference = 0.001,
        )

    private fun life(seconds: Float, script: (CatLife) -> Unit = {}): CatLife =
        CatLife(Autopilot.seedFor("Miso", "dna"), false, autopilot = false).also {
            script(it)
            it.advance(seconds)
        }

    /** The sprites of [live], asking the way the card does and answering from the recordings. */
    private fun sprites(live: LiveCat): CatSprites {
        val label = CoatFixtures.coats.entries.first { it.value == live.coat }.key
        val bridge = FakeCoatBridge()
        CatArt.coats.bind(bridge.requester)
        var answered = 0
        var found: CatSprites? = null
        repeat(4) {
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

    private fun shoot(fixture: String, life: CatLife, light: Boolean = false) {
        val j =
            JSONObject(javaClass.getResource("/fixtures/overview-cards-$fixture.json")!!.readText())
                .put("light", light)
                .put("tokens", ScreenshotPalette.tokens(light))
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

    @Test fun calicoSitting() = shoot("pet-live-dna", life(1.2f))

    @Test
    fun colourPointMidWalkLight() =
        shoot(
            "point-live-dna",
            life(0.9f) { it.body.replacePlan(1, listOf(PlanStep.Walk(130f))) },
            light = true,
        )

    @Test fun longHairedKitten() = shoot("kitten-live-dna", life(1.2f))
}
