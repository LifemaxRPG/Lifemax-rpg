package com.lifemax.rpg.screenshots

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.android.resources.Density
import com.lifemax.rpg.feature.pet.art.Growth
import com.lifemax.rpg.feature.pet.body.Autopilot
import com.lifemax.rpg.feature.pet.body.HouseCat
import com.lifemax.rpg.feature.pet.body.HouseLife
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.World
import com.lifemax.rpg.feature.pet.presentation.CatArt
import com.lifemax.rpg.feature.pet.presentation.HouseStage
import com.lifemax.rpg.feature.pet.room.RoomStyle
import org.junit.Rule
import org.junit.Test

/**
 * The 2.5D room (CT9) to judge by eye: each of the three rooms drawn with the furniture as layers,
 * the camera swayed fully left, at rest and fully right, by day and by night, with and without
 * cats. One room's worth (192 art pixels) at four device pixels per art pixel. The classic room's
 * own pictures are the ones in [CatHouseScreenshotTest]; nothing here touches them.
 *
 * Every moment is scripted: the cats are placed, the stage is stepped a fixed time, the hour is
 * fixed and the sway is held at the value the image is named for.
 */
class Depth25ScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(density = Density(420)),
            renderingMode = SessionParams.RenderingMode.SHRINK,
            maxPercentDifference = 0.001,
        )

    /** A cat to place: where, on what, how deep, which way it faces. */
    private class Spot(
        val x: Float,
        val surface: SurfaceId = SurfaceId.FLOOR,
        val z: Float = World.ART_Z,
        val facing: Int = 1,
        val coat: String = "ginger",
    )

    private fun shoot(
        centre: Float,
        hour: Double,
        sway: Float,
        spots: List<Spot> = emptyList(),
        style: RoomStyle = RoomStyle.DEPTH25,
        name: String? = null,
    ) {
        val cats = spots.ifEmpty { listOf(Spot(0f)) }
        val house =
            HouseLife(
                cats.map { HouseCat(Autopilot.seedFor("Miso", it.coat), false) },
                autopilot = false,
            )
        cats.forEachIndexed { i, s -> house.bodies[i].placeAt(s.x, s.z, s.surface, s.facing) }
        val stage = HouseStage(CatArt.room()!!, house, style) { hour }
        // with no spots the one cat has no sprites: an empty room
        if (spots.isNotEmpty()) {
            spots.forEachIndexed { i, s ->
                stage.setSprites(i, CatArt.sprites(s.coat, Growth.ADULT))
            }
        }
        stage.setFedHouse(true)
        stage.advance(0.8f)
        stage.lookAt(centre)
        stage.holdSway(sway)
        paparazzi.snapshot(name) { Room(stage) }
    }

    @Composable
    private fun Room(stage: HouseStage) {
        val d = LocalDensity.current
        Box(Modifier.size(with(d) { (192 * SCALE).toDp() }, with(d) { (144 * SCALE).toDp() })) {
            Canvas(
                Modifier.size(with(d) { (192 * SCALE).toDp() }, with(d) { (144 * SCALE).toDp() })
            ) {
                stage.draw(this)
            }
        }
    }

    // ------------------------------------------------------------------ the living room

    @Test fun livingDayRestNoCats() = shoot(LIVING, 12.0, 0f)

    @Test fun livingDayLeftNoCats() = shoot(LIVING, 12.0, -1f)

    @Test fun livingDayRightNoCats() = shoot(LIVING, 12.0, 1f)

    @Test fun livingNightRightNoCats() = shoot(LIVING, 23.0, 1f)

    @Test
    fun livingDayRestWithCats() =
        shoot(
            LIVING,
            12.0,
            0f,
            listOf(Spot(-118f, SurfaceId.COUCH), Spot(-48f, SurfaceId.FLOOR, 50f, -1, "gray")),
        )

    @Test
    fun livingDayRightCatBehindTheSofa() =
        shoot(
            LIVING,
            12.0,
            1f,
            listOf(Spot(-150f, SurfaceId.FLOOR, 12f), Spot(-100f, SurfaceId.FLOOR, 60f, -1, "gray")),
        )

    @Test
    fun livingNightLeftWithCats() =
        shoot(
            LIVING,
            23.0,
            -1f,
            listOf(Spot(-100f, SurfaceId.COUCH), Spot(-30f, SurfaceId.FLOOR, 55f, -1, "gray")),
        )

    // ------------------------------------------------------------------ the bedroom (the cat tree)

    @Test fun bedroomDayRestNoCats() = shoot(BEDROOM, 12.0, 0f)

    @Test fun bedroomDayLeftNoCats() = shoot(BEDROOM, 12.0, -1f)

    @Test fun bedroomDayRightNoCats() = shoot(BEDROOM, 12.0, 1f)

    @Test fun bedroomNightRestNoCats() = shoot(BEDROOM, 23.0, 0f)

    @Test
    fun bedroomDayRestCatsOnTheTree() =
        shoot(
            BEDROOM,
            12.0,
            0f,
            listOf(Spot(152f, SurfaceId.MID), Spot(154f, SurfaceId.TOP, World.ART_Z, -1, "gray")),
        )

    @Test
    fun bedroomDayLeftCatsAroundTheTree() =
        shoot(
            BEDROOM,
            12.0,
            -1f,
            listOf(Spot(150f, SurfaceId.FLOOR, 30f), Spot(60f, SurfaceId.FLOOR, 60f, 1, "gray")),
        )

    // ------------------------------------------------------------------ the kitchen

    @Test fun kitchenDayRestNoCats() = shoot(KITCHEN, 12.0, 0f)

    @Test fun kitchenDayRightNoCats() = shoot(KITCHEN, 12.0, 1f)

    @Test fun kitchenNightLeftNoCats() = shoot(KITCHEN, 23.0, -1f)

    @Test
    fun kitchenDayRestWithCats() =
        shoot(
            KITCHEN,
            12.0,
            0f,
            listOf(Spot(310f, SurfaceId.COUNTER), Spot(236f, SurfaceId.FLOOR, 45f, 1, "gray")),
        )

    private companion object {
        const val SCALE = 4

        /** World x of the middle of each room: the camera looks at it. */
        const val LIVING = -96f
        const val BEDROOM = 96f
        const val KITCHEN = 288f
    }
}
