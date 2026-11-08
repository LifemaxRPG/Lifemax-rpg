package com.lifemax.rpg.screenshots

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.android.resources.Density
import com.lifemax.rpg.feature.pet.body.Autopilot
import com.lifemax.rpg.feature.pet.body.HouseCat
import com.lifemax.rpg.feature.pet.body.HouseLife
import com.lifemax.rpg.feature.pet.body.PairPose
import com.lifemax.rpg.feature.pet.body.PlanStep
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.World
import com.lifemax.rpg.feature.pet.brain.Spots
import com.lifemax.rpg.feature.pet.data.WebPetRepository
import com.lifemax.rpg.feature.pet.presentation.CatArt
import com.lifemax.rpg.feature.pet.presentation.HouseStage
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
 * The owner's "still clipped into sofa", on the Cat tab's 2.5D room at RICH: a ginger kitten that
 * had slept at the sofa's end walked from the corner at the sofa's far end to behind the bed in a
 * straight line, through the sofa, and by the sofa's right arm only its ears showed above the arm.
 * Now the walk goes along the sofa's front and round its end ([com.lifemax.rpg.feature.pet.body.
 * Footprints]); the image is taken as the kitten passes the arm. And the places on the sofa: a
 * kitten and a grown cat asleep at the sofa's end, on the seat (the pink cushion standing on the
 * floor in front of the seat may cover a little of them, nothing of the sofa does), with the camera
 * at either end of its swing and at rest, and two cats curled up at the sofa's curl-up place as the
 * brain lays them.
 */
class SofaClipScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(density = Density(420)),
            renderingMode = SessionParams.RenderingMode.SHRINK,
            maxPercentDifference = 0.001,
        )

    private val page =
        WebPetRepository()
            .apply {
                accept(
                    JSONObject(javaClass.getResource("/fixtures/pet-two-cats.json")!!.readText())
                        .put("light", false)
                        .put("tokens", ScreenshotPalette.tokens(false)),
                    true,
                )
            }
            .state
            .value!!

    /** Ted as a kitten of [stage] (or grown) and Lily, out of the way unless [lily] says. */
    private fun shot(
        stage: Int,
        ted: LiveSpot,
        lily: LiveSpot = AWAY,
        sway: Float = 0f,
        until: ((HouseLife) -> Boolean)? = null,
    ) {
        val looks = listOf(LiveLook("Ted", COAT, stage, false), LiveLook("Lily", "gray", -1, false))
        val house =
            HouseLife(
                looks.map { HouseCat(Autopilot.seedFor(it.name, it.coat), it.weak) },
                autopilot = false,
            )
        listOf(ted, lily).forEachIndexed { i, s ->
            house.bodies[i].placeAt(s.x, s.z, s.surface, s.facing)
            house.bodies[i].replacePlan(1, s.plan)
        }
        val stage = HouseStage(CatArt.room()!!, house, RoomStyle.live(DepthLevel.RICH)) { HOUR }
        looks.forEachIndexed { i, look -> stage.setSprites(i, LiveDepthShots.sprites(look)) }
        stage.setFedHouse(true)
        if (until == null) {
            stage.advance(SECONDS)
        } else {
            var t = 0f
            while (!until(house) && t < MAX_WALK_S) {
                stage.advance(STEP_S)
                t += STEP_S
            }
        }
        stage.holdSway(sway)
        paparazzi.snapshot() {
            PetScreen(page, PetUi(), PetActions()) { modifier ->
                RoomHost(RoomState.Ready(stage), running = false, still = true, modifier)
            }
        }
    }

    @Test
    fun kittenWalksRoundTheSofaArm() {
        val from = Spots.BEHIND_CUSHION
        val to = Spots.BEHIND_BED
        shot(
            KITTEN,
            LiveSpot(from.x, 1, z = from.z, plan = listOf(PlanStep.Walk(to.x, to.z))),
            until = { it.bodies[0].pose.x >= OWNER_X },
        )
    }

    private fun asleepAtTheSofaEnd(stage: Int, sway: Float) {
        val spot = Spots.COUCH_R
        shot(stage, LiveSpot(spot.x, 1, SurfaceId.COUCH, plan = act("sleep")), sway = sway)
    }

    @Test fun kittenAsleepAtTheSofaEnd() = asleepAtTheSofaEnd(KITTEN, 0f)

    @Test fun kittenAsleepAtTheSofaEndPannedLeft() = asleepAtTheSofaEnd(KITTEN, -1f)

    @Test fun kittenAsleepAtTheSofaEndPannedRight() = asleepAtTheSofaEnd(KITTEN, 1f)

    @Test fun adultAsleepAtTheSofaEnd() = asleepAtTheSofaEnd(ADULT, 0f)

    @Test fun adultAsleepAtTheSofaEndPannedLeft() = asleepAtTheSofaEnd(ADULT, -1f)

    @Test fun adultAsleepAtTheSofaEndPannedRight() = asleepAtTheSofaEnd(ADULT, 1f)

    @Test
    fun curledUpAtTheSofasCurlPlace() {
        // as PairRests.curlCome lays them: the front cat a half gap off the place's middle, the
        // other a gap behind it, facing the same way; slot 1 is the one the room draws in front
        val spot = Spots.COUCH_L
        val pose = PairPose.SNUGGLE
        val gap = pose.gap(ADULT, ADULT, World.surface(SurfaceId.COUCH).z)
        val front = spot.x - gap / 2f
        shot(
            ADULT,
            LiveSpot(front + gap, 1, SurfaceId.COUCH, plan = act(pose.b)),
            LiveSpot(front, 1, SurfaceId.COUCH, plan = act(pose.a)),
        )
    }

    private fun act(row: String) = listOf(PlanStep.Act(row, HOLD_S))

    private companion object {
        const val COAT = "ginger"
        const val KITTEN = 3
        const val ADULT = -1
        const val HOUR = 17.0
        const val SECONDS = 2f
        const val HOLD_S = 60f
        const val STEP_S = 0.05f
        const val MAX_WALK_S = 30f

        /** Where the owner's kitten showed only its ears above the sofa's arm. */
        const val OWNER_X = -38f

        val AWAY = LiveSpot(250f, -1, plan = listOf(PlanStep.Act("loaf", HOLD_S)))
    }
}
