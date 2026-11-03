package com.lifemax.rpg.screenshots

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.feature.pet.art.CoatFixtures
import com.lifemax.rpg.feature.pet.art.FakeCoatBridge
import com.lifemax.rpg.feature.pet.body.Autopilot
import com.lifemax.rpg.feature.pet.body.CatBody
import com.lifemax.rpg.feature.pet.body.HouseCat
import com.lifemax.rpg.feature.pet.body.HouseLife
import com.lifemax.rpg.feature.pet.body.PlanStep
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.World
import com.lifemax.rpg.feature.pet.data.WebPetRepository
import com.lifemax.rpg.feature.pet.domain.PetCatLook
import com.lifemax.rpg.feature.pet.domain.PetProjection
import com.lifemax.rpg.feature.pet.presentation.CatArt
import com.lifemax.rpg.feature.pet.presentation.CatSprites
import com.lifemax.rpg.feature.pet.presentation.HouseStage
import com.lifemax.rpg.feature.pet.presentation.PetActions
import com.lifemax.rpg.feature.pet.presentation.PetScreen
import com.lifemax.rpg.feature.pet.presentation.PetUi
import com.lifemax.rpg.feature.pet.presentation.RoomHost
import com.lifemax.rpg.feature.pet.presentation.RoomState
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * The Cat tab as the phone draws it (Native Cat, beta), from the projections the page made
 * (`tools/gen_pet_fixtures.js`): the live room pinned at the top with the cat or cats in it, and
 * under it the card, the buttons, the stats and the care card. Every moment of the room is scripted
 * (cats placed and given plans, the stage stepped a fixed time, the hour fixed), so each image is
 * the same every time; the clock does not run on it. Content, hungry, very hungry (the weak rows),
 * weak, on vacation, a kitten, two cats, a genetic coat, large text, the care card open, dark and
 * light.
 */
class PetScreenshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = phone(), maxPercentDifference = 0.001)

    private fun phone(fontScale: Float = 1f, tall: Int = TALL) =
        DeviceConfig.PIXEL_5.copy(
            density = Density(420),
            screenHeight = tall,
            fontScale = fontScale,
        )

    /** Where a cat stands and what it is doing: a plan, or just sitting. */
    private class Spot(val x: Float, val facing: Int = 1, val plan: List<PlanStep> = emptyList())

    private fun onSofa(x: Float, facing: Int = 1) =
        Spot(x, facing, listOf(PlanStep.Hop(SurfaceId.COUCH, x), PlanStep.Act("idle_sit", 30f)))

    private fun atTheBowl(x: Float, facing: Int = 1) =
        Spot(x, facing, listOf(PlanStep.Act("idle_sit", 30f)))

    private fun sprites(look: PetCatLook): CatSprites {
        val label = CoatFixtures.coats.entries.firstOrNull { it.value == look.coat }?.key
        if (label == null) return CatArt.sprites(look.coat, look.stage)!!
        val bridge = FakeCoatBridge()
        CatArt.coats.bind(bridge.requester)
        var answered = 0
        var found: CatSprites? = null
        repeat(4) {
            found = CatArt.sprites(look.coat, look.stage)
            while (answered < bridge.sent.size) {
                val s = bridge.sent[answered++]
                if (s.kind.key in CoatFixtures.kinds) {
                    CatArt.coats.onReply(CoatFixtures.reply(label, s.kind.key, s.id))
                }
            }
        }
        return found!!
    }

    private fun projection(name: String, light: Boolean): PetProjection =
        WebPetRepository()
            .apply {
                accept(
                    JSONObject(javaClass.getResource("/fixtures/pet-$name.json")!!.readText())
                        .put("light", light)
                        .put("tokens", ScreenshotPalette.tokens(light)),
                    true,
                )
            }
            .state
            .value!!

    /**
     * The room for [p]'s cats, scripted to [spots] and stepped [seconds], the hour fixed at [hour].
     */
    private fun stage(
        p: PetProjection,
        spots: List<Spot>,
        hour: Double,
        seconds: Float,
    ): HouseStage {
        val house =
            HouseLife(
                p.cats.map { HouseCat(Autopilot.seedFor(it.name, it.coat), it.weak) },
                autopilot = false,
            )
        p.cats.indices.forEach { place(house.bodies[it], spots[it]) }
        val stage = HouseStage(CatArt.room()!!, house) { hour }
        p.cats.forEachIndexed { i, cat -> stage.setSprites(i, sprites(cat)) }
        stage.setFedHouse(p.cats.none { it.weak })
        stage.advance(seconds)
        return stage
    }

    private fun place(body: CatBody, spot: Spot) {
        body.placeAt(spot.x, World.ART_Z, SurfaceId.FLOOR, spot.facing)
        if (spot.plan.isNotEmpty()) body.replacePlan(1, spot.plan)
    }

    private fun shoot(
        name: String,
        spots: List<Spot>,
        hour: Double = 12.0,
        seconds: Float = 3f,
        light: Boolean = false,
        fontScale: Float = 1f,
        careOpen: Boolean = false,
        ui: PetUi = PetUi(),
        label: String? = null,
    ) {
        val p = projection(name, light)
        val stage = stage(p, spots, hour, seconds)
        val tall =
            when {
                fontScale > 1f -> TALL_LARGE_TEXT
                careOpen -> TALL_CARE_OPEN
                else -> TALL
            }
        if (tall != TALL) paparazzi.unsafeUpdateConfig(phone(fontScale, tall))
        paparazzi.snapshot(label) {
            PetScreen(p, ui, PetActions(), careOpen = careOpen) { modifier ->
                RoomHost(RoomState.Ready(stage), running = false, still = true, modifier)
            }
        }
    }

    @Test fun contentCatOnTheSofa() = shoot("alive", listOf(onSofa(-118f)))

    @Test fun contentCatOnTheSofaLight() = shoot("alive", listOf(onSofa(-118f)), light = true)

    @Test
    fun contentCatAtNight() =
        shoot(
            "content",
            listOf(
                Spot(36f, 1, listOf(PlanStep.Hop(SurfaceId.BED, 36f), PlanStep.Act("sleep", 30f)))
            ),
            hour = 23.0,
            seconds = 3.5f,
        )

    @Test fun hungryCatWaitsAtTheBowl() = shoot("hungry", listOf(atTheBowl(87f)))

    @Test fun veryHungryCatMovesSlowly() = shoot("very-hungry", listOf(atTheBowl(100f, -1)))

    @Test fun weakAndTiredCat() = shoot("weak", listOf(Spot(100f, -1)), light = true)

    @Test fun catOnVacation() = shoot("vacation", listOf(onSofa(-100f)))

    @Test fun aKittenWithItsGrowthLine() = shoot("kitten", listOf(Spot(60f, 1)))

    @Test
    fun twoCatsShareTheHouse() =
        shoot("two-cats", listOf(onSofa(-118f), onSofa(-78f, -1)), seconds = 3f)

    @Test fun aGeneticCoat() = shoot("dna", listOf(onSofa(-118f)))

    @Test fun aStrayWithNoHeartsYet() = shoot("stray", listOf(Spot(230f, -1)))

    @Test
    fun largerText() = shoot("two-cats", listOf(onSofa(-118f), onSofa(-78f, -1)), fontScale = 2f)

    @Test fun theCareCardOpen() = shoot("alive", listOf(onSofa(-118f)), careOpen = true)

    @Test
    fun theCareCardOpenOnVacationLight() =
        shoot("vacation", listOf(onSofa(-118f)), careOpen = true, light = true)

    @Test
    fun feedWaitingForThePage() =
        shoot("alive", listOf(onSofa(-118f)), ui = PetUi(waiting = "feed"))

    private companion object {
        /** Tall enough to show the whole screen in one image. */
        const val TALL = 3000

        /** With the care card open, which adds its text, the switch and the name. */
        const val TALL_CARE_OPEN = 3900

        /** And with large text, where the stats stack and everything is taller. */
        const val TALL_LARGE_TEXT = 4800
    }
}
