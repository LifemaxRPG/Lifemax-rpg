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
import com.lifemax.rpg.feature.pet.room.DepthLevel
import com.lifemax.rpg.feature.pet.room.RoomStyle
import org.junit.Rule
import org.junit.Test

/**
 * The 2.5D room with its furniture as small solids (CT9), to judge by eye: each room with its cats
 * at each [DepthLevel], the camera swayed fully left, at rest and fully right (at rest every level
 * is the painted room; the sway shows the sides and tops). Then the hard cases at MEDIUM: a cat
 * behind the sofa's arm, cats around the tree at night. One room (192 art pixels) at four device
 * pixels per art pixel. Image names end `_<scene>_<level>_<left|rest|right>`.
 *
 * Every moment is scripted: the cats are placed, the stage is stepped a fixed time, the hour is
 * fixed and the sway is held at the value the image is named for. The classic room's own pictures
 * are the ones in [CatHouseScreenshotTest]; nothing here touches them.
 */
class VolumeScreenshotTest {
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

    private fun shootLevels(scene: String, centre: Float, spots: List<Spot>, hour: Double = 12.0) {
        for (level in DepthLevel.entries) shootSways(scene, centre, spots, level, hour)
    }

    private fun shootSways(
        scene: String,
        centre: Float,
        spots: List<Spot>,
        level: DepthLevel,
        hour: Double = 12.0,
    ) {
        for ((pose, sway) in listOf("left" to -1f, "rest" to 0f, "right" to 1f)) {
            shoot(centre, hour, sway, spots, level, "${scene}_${level.name.lowercase()}_$pose")
        }
    }

    private fun shoot(
        centre: Float,
        hour: Double,
        sway: Float,
        spots: List<Spot>,
        level: DepthLevel,
        name: String,
    ) {
        val house =
            HouseLife(
                spots.map { HouseCat(Autopilot.seedFor("Miso", it.coat), false) },
                autopilot = false,
            )
        spots.forEachIndexed { i, s -> house.bodies[i].placeAt(s.x, s.z, s.surface, s.facing) }
        val stage = HouseStage(CatArt.room()!!, house, RoomStyle.depth25(level)) { hour }
        spots.forEachIndexed { i, s -> stage.setSprites(i, CatArt.sprites(s.coat, Growth.ADULT)) }
        stage.setFedHouse(true)
        stage.advance(0.8f)
        stage.lookAt(centre)
        stage.holdSway(sway)
        paparazzi.snapshot(name) { Room(stage) }
    }

    /**
     * The camera caught between two of the pieces' camera steps while it moves: one frame drawn at
     * [from] and the next at [to], so the pieces are a cross-fade of the steps on either side of
     * [to] (a side half faded in), as the room draws them while the camera pans.
     */
    private fun shootMoving(
        centre: Float,
        from: Float,
        to: Float,
        spots: List<Spot>,
        name: String,
    ) {
        val house =
            HouseLife(
                spots.map { HouseCat(Autopilot.seedFor("Miso", it.coat), false) },
                autopilot = false,
            )
        spots.forEachIndexed { i, s -> house.bodies[i].placeAt(s.x, s.z, s.surface, s.facing) }
        val stage =
            HouseStage(CatArt.room()!!, house, RoomStyle.depth25(DepthLevel.MEDIUM)) { 12.0 }
        spots.forEachIndexed { i, s -> stage.setSprites(i, CatArt.sprites(s.coat, Growth.ADULT)) }
        stage.setFedHouse(true)
        stage.advance(0.8f)
        stage.lookAt(centre)
        paparazzi.snapshot(name) {
            val density = LocalDensity.current
            Canvas(
                Modifier.size(
                    with(density) { (192 * SCALE).toDp() },
                    with(density) { (144 * SCALE).toDp() },
                )
            ) {
                stage.holdSway(from)
                stage.draw(this)
                stage.holdSway(to) // the camera moved: the next frame is drawn between steps
                stage.draw(this)
            }
        }
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

    @Test
    fun livingRoom() =
        shootLevels(
            "living",
            LIVING,
            listOf(Spot(-118f, SurfaceId.COUCH), Spot(-48f, SurfaceId.FLOOR, 50f, -1, "gray")),
        )

    @Test
    fun bedroomCatsInTheTree() =
        shootLevels(
            "bedroom",
            BEDROOM,
            listOf(Spot(152f, SurfaceId.MID), Spot(154f, SurfaceId.TOP, World.ART_Z, -1, "gray")),
        )

    @Test
    fun kitchenCatOnTheCounter() =
        shootLevels(
            "kitchen",
            KITCHEN,
            listOf(Spot(310f, SurfaceId.COUNTER), Spot(236f, SurfaceId.FLOOR, 45f, 1, "gray")),
        )

    @Test
    fun aCatBehindTheSofaArm() =
        shootSways(
            "behindsofa",
            LIVING,
            listOf(
                Spot(-150f, SurfaceId.FLOOR, 12f),
                Spot(-100f, SurfaceId.FLOOR, 60f, -1, "gray"),
            ),
            DepthLevel.MEDIUM,
        )

    @Test
    fun catsAroundTheTreeAtNight() =
        shootSways(
            "treenight",
            BEDROOM,
            listOf(Spot(150f, SurfaceId.FLOOR, 30f), Spot(60f, SurfaceId.FLOOR, 60f, 1, "gray")),
            DepthLevel.MEDIUM,
            23.0,
        )

    @Test
    fun livingRoomWithTheCameraBetweenTwoSteps() =
        // MEDIUM has four steps a side: 0.625 is half-way from the step at 0.5 to the one at 0.75
        shootMoving(
            LIVING,
            0.5f,
            0.625f,
            listOf(Spot(-118f, SurfaceId.COUCH), Spot(-48f, SurfaceId.FLOOR, 50f, -1, "gray")),
            "living_medium_betweensteps",
        )

    private companion object {
        const val SCALE = 4

        /** World x of the middle of each room: the camera looks at it. */
        const val LIVING = -96f
        const val BEDROOM = 96f
        const val KITCHEN = 288f
    }
}
