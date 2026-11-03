package com.lifemax.rpg.screenshots

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.feature.pet.body.PlanStep
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.data.WebPetRepository
import com.lifemax.rpg.feature.pet.presentation.PetActions
import com.lifemax.rpg.feature.pet.presentation.PetScreen
import com.lifemax.rpg.feature.pet.presentation.PetUi
import com.lifemax.rpg.feature.pet.presentation.RoomHost
import com.lifemax.rpg.feature.pet.presentation.RoomState
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * The Cat tab's room with the live 2.5D style (MEDIUM, the camera-follow parallax), first in the
 * scrolling list as the phone draws it. Day and night, dark and light, a cat on the fridge and on
 * the bookcase, a cat behind the sofa's arm, and the camera panned to either end of the house.
 * Every moment is scripted (see [LiveDepthCardScreenshotTest]); the classic tab's own images are
 * the ones in [PetScreenshotTest].
 */
class LiveDepthTabScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(density = Density(420), screenHeight = SHORT),
            maxPercentDifference = 0.001,
        )

    private fun tab(
        spots: List<LiveSpot>,
        fixture: String = "alive",
        hour: Double = 12.0,
        light: Boolean = false,
        hold: Boolean = false,
    ) {
        val p =
            WebPetRepository()
                .apply {
                    accept(
                        JSONObject(
                                javaClass.getResource("/fixtures/pet-$fixture.json")!!.readText()
                            )
                            .put("light", light)
                            .put("tokens", ScreenshotPalette.tokens(light)),
                        true,
                    )
                }
                .state
                .value!!
        val looks = p.cats.map { LiveLook(it.name, it.coat, it.stage, it.weak) }
        val stage = LiveDepthShots.stage(looks, spots, hour, 4f, hold)
        paparazzi.snapshot() {
            PetScreen(p, PetUi(), PetActions()) { modifier ->
                RoomHost(RoomState.Ready(stage), running = false, still = true, modifier)
            }
        }
    }

    @Test fun livingRoomDay() = tab(listOf(LiveSpot.onSofa(-118f)))

    @Test fun livingRoomDayLight() = tab(listOf(LiveSpot.onSofa(-118f)), light = true)

    @Test
    fun bedroomAtNight() =
        tab(
            listOf(LiveSpot(36f, 1, SurfaceId.BED, plan = listOf(PlanStep.Act("sleep", 60f)))),
            fixture = "content",
            hour = 23.0,
        )

    @Test fun catOnTheFridge() = tab(listOf(LiveSpot(226f, -1, SurfaceId.FRIDGE)))

    @Test
    fun catOnTheBookcase() =
        tab(listOf(LiveSpot(122f, 1, SurfaceId.BOOKCASE)), light = true, hour = 17.0)

    @Test fun catBehindTheSofa() = tab(listOf(LiveSpot(-150f, 1, z = 12f)), hold = true)

    @Test fun cameraPannedLeft() = tab(listOf(LiveSpot(-130f, 1)), hold = true)

    @Test fun cameraPannedRight() = tab(listOf(LiveSpot(300f, -1)), hold = true)

    private companion object {
        /** A phone's own height: the room first in the list, the cards starting under it. */
        const val SHORT = 2340
    }
}
