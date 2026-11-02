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
import com.lifemax.rpg.feature.overview.presentation.cards.CatHousePreview
import com.lifemax.rpg.feature.pet.art.CoatFixtures
import com.lifemax.rpg.feature.pet.art.FakeCoatBridge
import com.lifemax.rpg.feature.pet.body.Autopilot
import com.lifemax.rpg.feature.pet.body.CatBody
import com.lifemax.rpg.feature.pet.body.HouseCat
import com.lifemax.rpg.feature.pet.body.HouseLife
import com.lifemax.rpg.feature.pet.body.PlanStep
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.World
import com.lifemax.rpg.feature.pet.presentation.CatArt
import com.lifemax.rpg.feature.pet.presentation.HouseStage
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * The live room on the Overview's cat card (Native cats, beta), as the phone draws it: the house
 * with its three rooms, the light of the hour, and the card's one or two cats living in it. Every
 * moment is scripted (cats placed and given plans, the stage stepped a fixed time, the hour fixed)
 * so each image is the same every time; the clock does not run on it. Day and night, the living
 * room and the kitchen window, dark and light, a genetic second cat, and large text. The card's
 * name and hearts are the page's.
 */
class CatHouseScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = phone(),
            renderingMode = SessionParams.RenderingMode.SHRINK,
            maxPercentDifference = 0.001,
        )

    private fun phone(fontScale: Float = 1f) =
        DeviceConfig.PIXEL_5.copy(density = Density(420), fontScale = fontScale)

    /** Where a cat stands and what it is doing: a plan, or just sitting. */
    private class Spot(val x: Float, val facing: Int = 1, val plan: List<PlanStep> = emptyList())

    private fun sprites(live: LiveCat) = run {
        val label = CoatFixtures.coats.entries.firstOrNull { it.value == live.coat }?.key
        if (label == null) return@run CatArt.sprites(live.coat, live.stage)!!
        val bridge = FakeCoatBridge()
        CatArt.coats.bind(bridge.requester)
        var answered = 0
        var found: com.lifemax.rpg.feature.pet.presentation.CatSprites? = null
        repeat(4) {
            found = CatArt.sprites(live.coat, live.stage)
            while (answered < bridge.sent.size) {
                val s = bridge.sent[answered++]
                if (s.kind.key in CoatFixtures.kinds) {
                    CatArt.coats.onReply(CoatFixtures.reply(label, s.kind.key, s.id))
                }
            }
        }
        found!!
    }

    private fun shoot(
        fixture: String,
        hour: Double,
        spots: List<Spot>,
        seconds: Float = 1.2f,
        light: Boolean = false,
        fontScale: Float = 1f,
        name: String? = null,
    ) {
        val j =
            JSONObject(javaClass.getResource("/fixtures/overview-cards-$fixture.json")!!.readText())
                .put("light", light)
                .put("tokens", ScreenshotPalette.tokens(light))
        val cards = WebOverviewRepository().apply { accept(j, true) }.state.value!!
        val cats = cards.tiles.cat!!.live!!.house
        val house =
            HouseLife(
                cats.map { HouseCat(Autopilot.seedFor(it.name ?: "cat", it.coat), it.weak) },
                autopilot = false,
            )
        cats.indices.forEach { place(house.bodies[it], spots[it]) }
        val stage = HouseStage(CatArt.room()!!, house) { hour }
        cats.forEachIndexed { i, cat -> stage.setSprites(i, sprites(cat)) }
        stage.setFedHouse(cats.none { it.weak })
        stage.advance(seconds)
        if (fontScale != 1f) paparazzi.unsafeUpdateConfig(phone(fontScale))
        paparazzi.snapshot(name) {
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

    private fun place(body: CatBody, spot: Spot) {
        body.placeAt(spot.x, World.ART_Z, SurfaceId.FLOOR, spot.facing)
        if (spot.plan.isNotEmpty()) body.replacePlan(1, spot.plan)
    }

    /** Up on the sofa at [x], sitting. */
    private fun onSofa(x: Float, facing: Int = 1) =
        Spot(x, facing, listOf(PlanStep.Hop(SurfaceId.COUCH, x), PlanStep.Act("idle_sit", 30f)))

    @Test
    fun livingRoomDay() =
        shoot("two-cats-live", 12.0, listOf(onSofa(-118f), onSofa(-78f, -1)), seconds = 3f)

    @Test
    fun livingRoomDayLight() =
        shoot(
            "two-cats-live",
            12.0,
            listOf(onSofa(-118f), onSofa(-78f, -1)),
            seconds = 3f,
            light = true,
        )

    @Test
    fun livingRoomNight() =
        shoot("two-cats-live", 23.0, listOf(onSofa(-112f), Spot(-70f, -1)), seconds = 3f)

    @Test
    fun livingRoomLampAtDusk() =
        shoot("two-cats-live", 19.0, listOf(onSofa(-118f), onSofa(-78f, -1)), seconds = 3f)

    @Test
    fun kitchenWindowDay() = shoot("two-cats-live", 12.0, listOf(Spot(236f, 1), Spot(330f, -1)))

    @Test
    fun kitchenWindowNightLight() =
        shoot("two-cats-live", 23.0, listOf(Spot(236f, 1), Spot(330f, -1)), light = true)

    @Test
    fun bedroomDuskOneWalkingOneSitting() =
        shoot(
            "two-cats-live",
            18.75,
            listOf(Spot(82f, 1, listOf(PlanStep.Walk(135f))), Spot(170f, -1)),
            seconds = 1.4f,
        )

    @Test
    fun sleepingOnTheBedAndTheOtherAtTheBowlsAtNight() =
        shoot(
            "two-cats-live",
            23.0,
            listOf(
                Spot(36f, 1, listOf(PlanStep.Hop(SurfaceId.BED, 36f), PlanStep.Act("sleep", 30f))),
                Spot(121f, -1, listOf(PlanStep.Act("eat", 30f))),
            ),
            seconds = 3.5f,
        )

    @Test
    fun aSecondCatWithAGeneticCoat() =
        shoot("two-cats-live-dna", 12.0, listOf(onSofa(-118f), onSofa(-78f, -1)), seconds = 3f)

    @Test
    fun oneCatAloneInTheBedroomAtTheBowls() =
        shoot("pet-live", 12.0, listOf(Spot(87f, 1, listOf(PlanStep.Act("eat", 6f)))))

    @Test
    fun largerText() =
        shoot(
            "two-cats-live",
            12.0,
            listOf(onSofa(-118f), onSofa(-78f, -1)),
            seconds = 3f,
            fontScale = 2f,
        )
}
