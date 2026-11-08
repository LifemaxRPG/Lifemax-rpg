package com.lifemax.rpg.screenshots

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.android.resources.Density
import com.lifemax.rpg.feature.pet.body.PairPose
import com.lifemax.rpg.feature.pet.body.PlanStep
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.World
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
 * The three poses two cats share (CB5, PET_TAB_INVENTORY.md section 17), on the Cat tab's 2.5D room
 * at RICH, each at the distance the brain puts them ([PairPose], the art lab's offsets): the nose
 * touch on the floor, one grooming the other on the floor, and the two curled up on the sofa. The
 * cats meet without standing on one point.
 */
class PairPosesScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(density = Density(420)),
            renderingMode = SessionParams.RenderingMode.SHRINK,
            maxPercentDifference = 0.001,
        )

    private fun shot(a: LiveSpot, b: LiveSpot, seconds: Float) {
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
                listOf(a, b),
                HOUR,
                seconds,
                true,
                RoomStyle.live(DepthLevel.RICH),
            )
        paparazzi.snapshot() {
            PetScreen(p, PetUi(), PetActions()) { modifier ->
                RoomHost(RoomState.Ready(stage), running = false, still = true, modifier)
            }
        }
    }

    private fun act(row: String) = listOf(PlanStep.Act(row, HOLD_S))

    @Test
    fun noseTouch() {
        val pose = PairPose.GREET
        val gap = pose.gap(ADULT, ADULT, FLOOR_Z)
        shot(
            LiveSpot(FLOOR_X, 1, z = FLOOR_Z, plan = act(pose.a)),
            LiveSpot(FLOOR_X + gap, -1, z = FLOOR_Z, plan = act(pose.b)),
            CONTACT_S,
        )
    }

    @Test
    fun groomingTheOther() {
        val pose = PairPose.GROOM
        val gap = pose.gap(ADULT, ADULT, FLOOR_Z)
        shot(
            LiveSpot(FLOOR_X, 1, z = FLOOR_Z, plan = act(pose.a)),
            LiveSpot(FLOOR_X + gap, -1, z = FLOOR_Z, plan = act(pose.b)),
            LOOP_S,
        )
    }

    @Test
    fun curledUpOnTheSofa() {
        val pose = PairPose.SNUGGLE
        val z = World.surface(SurfaceId.COUCH).z
        val gap = pose.gap(ADULT, ADULT, z)
        shot(
            LiveSpot(SOFA_X, 1, SurfaceId.COUCH, plan = act(pose.a)),
            LiveSpot(SOFA_X + gap, 1, SurfaceId.COUCH, plan = act(pose.b)),
            LOOP_S,
        )
    }

    private companion object {
        const val HOUR = 12.0
        const val ADULT = -1
        const val FLOOR_X = 60f
        const val FLOOR_Z = 50f
        const val SOFA_X = -84f
        const val HOLD_S = 60f
        const val CONTACT_S = 0.42f
        const val LOOP_S = 2f
    }
}
