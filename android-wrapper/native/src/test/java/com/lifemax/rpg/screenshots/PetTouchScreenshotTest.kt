package com.lifemax.rpg.screenshots

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
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
import com.lifemax.rpg.feature.pet.presentation.PetFullRoomContent
import com.lifemax.rpg.feature.pet.presentation.PetScreen
import com.lifemax.rpg.feature.pet.presentation.PetUi
import com.lifemax.rpg.feature.pet.presentation.RoomHost
import com.lifemax.rpg.feature.pet.presentation.RoomState
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * The Cat tab with a hand on the cat, and the room over the whole screen (CT2). Every moment is
 * scripted: the cats placed, a finger put down with the room's clock stepped a fixed time, the hour
 * fixed, so each image is the same every time. A cat purring with hearts rising, a cat whose ears
 * went back, a stranger startling, the day's counts under the card in large text, the full-screen
 * room upright, wide, in the light and with large text on its close chip.
 */
class PetTouchScreenshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = phone(), maxPercentDifference = 0.001)

    private fun phone(fontScale: Float = 1f, tall: Int = TALL) =
        DeviceConfig.PIXEL_5.copy(
            density = Density(420),
            screenHeight = tall,
            fontScale = fontScale,
        )

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

    /** Where a cat stands: on the floor at [x], sitting. */
    private fun room(p: PetProjection, xs: List<Float>, hour: Double = 12.0): HouseStage {
        val house =
            HouseLife(
                p.cats.map { HouseCat(Autopilot.seedFor(it.name, it.coat), it.weak) },
                autopilot = false,
            )
        p.cats.indices.forEach {
            house.bodies[it].placeAt(xs[it], World.ART_Z, SurfaceId.FLOOR, if (it == 0) 1 else -1)
            house.bodies[it].replacePlan(1, listOf(PlanStep.Act("idle_sit", 60f)))
        }
        val stage = HouseStage(CatArt.room()!!, house) { hour }
        p.cats.forEachIndexed { i, cat ->
            stage.setSprites(i, CatArt.sprites(cat.coat, cat.stage)!!)
        }
        stage.setFedHouse(true)
        stage.advance(1f)
        return stage
    }

    /** Put a finger on cat 0 with the page's numbers for it, and let [seconds] go by. */
    private fun pet(
        stage: HouseStage,
        p: PetProjection,
        seconds: Float,
        satMul: Float? = null,
        trust: Float? = null,
    ) {
        p.touch!!.profiles.forEachIndexed { i, t ->
            stage.petting.profile(
                i,
                t.level,
                satMul ?: t.satMul.toFloat(),
                trust ?: t.trust.toFloat(),
                t.sat.toFloat(),
            )
        }
        val pose = stage.house.bodies[0].pose
        stage.petting.down(pose.x, pose.y - 12f * pose.depthScale)
        stage.advance(seconds)
    }

    private fun shoot(
        name: String,
        seconds: Float,
        light: Boolean = false,
        fontScale: Float = 1f,
        satMul: Float? = null,
        trust: Float? = null,
        x: Float = 60f,
    ) {
        val p = projection(name, light)
        val stage = room(p, listOf(x, x + 90f))
        pet(stage, p, seconds, satMul, trust)
        if (fontScale > 1f) paparazzi.unsafeUpdateConfig(phone(fontScale, TALL_LARGE_TEXT))
        paparazzi.snapshot {
            PetScreen(p, PetUi(), PetActions()) { modifier ->
                RoomHost(RoomState.Ready(stage), running = false, still = true, modifier)
            }
        }
    }

    @Test fun aHandOnADevotedCatHeartsRise() = shoot("touched", seconds = 2.6f)

    @Test fun aHandOnADevotedCatLight() = shoot("touched", seconds = 2.6f, light = true)

    @Test
    fun theEarsGoBackWhenItHasHadEnough() =
        shoot("touched", seconds = 3.9f, satMul = 1.3f, trust = 0f)

    @Test fun aStrangerStartlesAtTheHand() = shoot("stray", seconds = 0.35f)

    @Test fun theDaysCountsInLargeText() = shoot("touched", seconds = 0.5f, fontScale = 2f)

    // ------------------------------------------------------------------ the full-screen room

    private fun full(
        name: String,
        light: Boolean = false,
        seconds: Float = 0f,
        wide: Boolean = false,
        fontScale: Float = 1f,
    ) {
        val p = projection(name, light)
        val xs = listOf(-10f, 40f)
        val stage = room(p, xs)
        if (seconds > 0f) pet(stage, p, seconds)
        val config =
            if (wide)
                phone(fontScale, 1080)
                    .copy(orientation = ScreenOrientation.LANDSCAPE, screenWidth = 2340)
            else phone(fontScale, 2340)
        paparazzi.unsafeUpdateConfig(config)
        paparazzi.snapshot {
            PetFullRoomContent(
                p,
                RoomState.Ready(stage),
                running = false,
                still = true,
                onBowl = {},
                onClose = {},
            )
        }
    }

    @Test fun theFullScreenRoomUpright() = full("two-cats")

    @Test fun theFullScreenRoomWide() = full("two-cats", wide = true)

    @Test
    fun theFullScreenRoomInTheLightWithAHandOnTheCat() =
        full("touched", light = true, seconds = 2.6f)

    @Test fun theFullScreenRoomLargeText() = full("two-cats", fontScale = 2f)

    private companion object {
        const val TALL = 3000

        /** With large text the card's stats stack and everything is taller. */
        const val TALL_LARGE_TEXT = 4800
    }
}
