package com.lifemax.rpg.screenshots

import androidx.compose.foundation.lazy.rememberLazyListState
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.feature.pet.body.Autopilot
import com.lifemax.rpg.feature.pet.body.HouseCat
import com.lifemax.rpg.feature.pet.body.HouseLife
import com.lifemax.rpg.feature.pet.body.PlanStep
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.World
import com.lifemax.rpg.feature.pet.data.WebPetRepository
import com.lifemax.rpg.feature.pet.domain.PetProjection
import com.lifemax.rpg.feature.pet.presentation.CatArt
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
 * The Cat tab with the developer "Demo parkour" button (`pet.demo.v1`, shown only while the page's
 * developer mode is on): under the Feed and Play row, beside the slow blink's quiet size, dark and
 * light, with large text, and the cards scrolled past the room. The page without the button is
 * unchanged (PetScreenshotTest keeps those images).
 */
class PetDemoScreenshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = phone(), maxPercentDifference = 0.001)

    private fun phone(fontScale: Float = 1f, tall: Int = TALL) =
        DeviceConfig.PIXEL_5.copy(
            density = Density(420),
            screenHeight = tall,
            fontScale = fontScale,
        )

    private fun projection(light: Boolean): PetProjection =
        WebPetRepository()
            .apply {
                accept(
                    JSONObject(javaClass.getResource("/fixtures/pet-demo.json")!!.readText())
                        .put("light", light)
                        .put("tokens", ScreenshotPalette.tokens(light)),
                    true,
                )
            }
            .state
            .value!!

    private fun stage(p: PetProjection): HouseStage {
        val house =
            HouseLife(
                p.cats.map { HouseCat(Autopilot.seedFor(it.name, it.coat), it.weak) },
                autopilot = false,
            )
        house.bodies[0].placeAt(SOFA_X, World.ART_Z, SurfaceId.FLOOR, 1)
        house.bodies[0].replacePlan(
            1,
            listOf(PlanStep.Hop(SurfaceId.COUCH, SOFA_X), PlanStep.Act("idle_sit", 30f)),
        )
        val stage = HouseStage(CatArt.room()!!, house) { NOON }
        p.cats.forEachIndexed { i, cat ->
            stage.setSprites(i, CatArt.sprites(cat.coat, cat.stage)!!)
        }
        stage.setFedHouse(true)
        stage.advance(SECONDS)
        return stage
    }

    private fun shoot(light: Boolean = false, fontScale: Float = 1f, pastTheRoom: Boolean = false) {
        val p = projection(light)
        val stage = stage(p)
        val tall =
            when {
                fontScale > 1f -> TALL_LARGE_TEXT
                pastTheRoom -> SHORT
                else -> TALL
            }
        if (tall != TALL) paparazzi.unsafeUpdateConfig(phone(fontScale, tall))
        paparazzi.snapshot {
            val list =
                rememberLazyListState(initialFirstVisibleItemIndex = if (pastTheRoom) 1 else 0)
            PetScreen(p, PetUi(), PetActions(), listState = list) { modifier ->
                RoomHost(RoomState.Ready(stage), running = false, still = true, modifier)
            }
        }
    }

    @Test fun demoButtonDark() = shoot()

    @Test fun demoButtonLight() = shoot(light = true)

    @Test fun demoButtonLargerText() = shoot(fontScale = 2f)

    @Test fun demoButtonScrolledPastTheRoom() = shoot(pastTheRoom = true)

    private companion object {
        const val SOFA_X = -118f
        const val NOON = 12.0
        const val SECONDS = 3f
        const val SHORT = 2340
        const val TALL = 3000
        const val TALL_LARGE_TEXT = 4800
    }
}
