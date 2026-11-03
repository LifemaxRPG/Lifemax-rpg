package com.lifemax.rpg.screenshots

import com.lifemax.rpg.feature.pet.art.CoatFixtures
import com.lifemax.rpg.feature.pet.art.FakeCoatBridge
import com.lifemax.rpg.feature.pet.body.Autopilot
import com.lifemax.rpg.feature.pet.body.HouseCat
import com.lifemax.rpg.feature.pet.body.HouseLife
import com.lifemax.rpg.feature.pet.body.PlanStep
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.World
import com.lifemax.rpg.feature.pet.presentation.CatArt
import com.lifemax.rpg.feature.pet.presentation.CatSprites
import com.lifemax.rpg.feature.pet.presentation.HouseStage
import com.lifemax.rpg.feature.pet.room.DepthLevel
import com.lifemax.rpg.feature.pet.room.RoomStyle

/** A cat to place: where, on what, how deep, which way it faces, what it does. */
internal class LiveSpot(
    val x: Float,
    val facing: Int = 1,
    val surface: SurfaceId = SurfaceId.FLOOR,
    val z: Float = World.ART_Z,
    val plan: List<PlanStep> = listOf(PlanStep.Act("idle_sit", 60f)),
) {
    companion object {
        /** Up on the sofa at [x], sitting. */
        fun onSofa(x: Float, facing: Int = 1) = LiveSpot(x, facing, SurfaceId.COUCH)
    }
}

/** What the room needs to know of a cat of a fixture. */
internal class LiveLook(val name: String, val coat: String, val stage: Int, val weak: Boolean)

/** The scripted live room of [LiveDepthCardScreenshotTest] and [LiveDepthTabScreenshotTest]. */
internal object LiveDepthShots {
    fun sprites(look: LiveLook): CatSprites {
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

    /**
     * The room for [looks]: the cats on [spots], the live style at MEDIUM, stepped [seconds] so the
     * camera has followed and the follow has settled. With [hold] the idle drift is put at rest, so
     * the image shows what the pan does and nothing else.
     */
    fun stage(
        looks: List<LiveLook>,
        spots: List<LiveSpot>,
        hour: Double,
        seconds: Float,
        hold: Boolean,
    ): HouseStage {
        val house =
            HouseLife(
                looks.map { HouseCat(Autopilot.seedFor(it.name, it.coat), it.weak) },
                autopilot = false,
            )
        looks.indices.forEach {
            val s = spots[it]
            house.bodies[it].placeAt(s.x, s.z, s.surface, s.facing)
            if (s.plan.isNotEmpty()) house.bodies[it].replacePlan(1, s.plan)
        }
        val stage = HouseStage(CatArt.room()!!, house, RoomStyle.live(DepthLevel.MEDIUM)) { hour }
        looks.forEachIndexed { i, look -> stage.setSprites(i, sprites(look)) }
        stage.setFedHouse(looks.none { it.weak })
        stage.advance(seconds)
        if (hold) stage.holdSway(0f)
        return stage
    }
}
