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
 * The same three scenes at each [DepthLevel] (SUBTLE, MEDIUM, RICH), at rest and with the camera
 * swayed fully right, to choose the strength of the 2.5D room by eye: the bedroom with cats on the
 * cat tree, the living room with both cats, and a cat behind the sofa's arm. One room (192 art
 * pixels) at four device pixels per art pixel. Image names end `_<scene>_<level>_<rest|right>`.
 */
class Depth25LevelsScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(density = Density(420)),
            renderingMode = SessionParams.RenderingMode.SHRINK,
            maxPercentDifference = 0.001,
        )

    private class Spot(
        val x: Float,
        val surface: SurfaceId = SurfaceId.FLOOR,
        val z: Float = World.ART_Z,
        val facing: Int = 1,
        val coat: String = "ginger",
    )

    private fun shootAllLevels(scene: String, centre: Float, spots: List<Spot>) {
        for (level in DepthLevel.entries) {
            for ((pose, sway) in listOf("rest" to 0f, "right" to 1f)) {
                shoot(centre, sway, spots, level, "${scene}_${level.name.lowercase()}_$pose")
            }
        }
    }

    private fun shoot(
        centre: Float,
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
        val stage = HouseStage(CatArt.room()!!, house, RoomStyle.depth25(level)) { 12.0 }
        spots.forEachIndexed { i, s -> stage.setSprites(i, CatArt.sprites(s.coat, Growth.ADULT)) }
        stage.setFedHouse(true)
        stage.advance(0.8f)
        stage.lookAt(centre)
        stage.holdSway(sway)
        paparazzi.snapshot(name) { Room(stage) }
    }

    @Composable
    private fun Room(stage: HouseStage) {
        val d = LocalDensity.current
        val w = with(d) { (192 * SCALE).toDp() }
        val h = with(d) { (144 * SCALE).toDp() }
        Box(Modifier.size(w, h)) { Canvas(Modifier.size(w, h)) { stage.draw(this) } }
    }

    @Test
    fun bedroomCatsOnTheTree() =
        shootAllLevels(
            "tree",
            96f,
            listOf(Spot(152f, SurfaceId.MID), Spot(154f, SurfaceId.TOP, World.ART_Z, -1, "gray")),
        )

    @Test
    fun livingRoomBothCats() =
        shootAllLevels(
            "living",
            -96f,
            listOf(Spot(-118f, SurfaceId.COUCH), Spot(-48f, SurfaceId.FLOOR, 50f, -1, "gray")),
        )

    @Test
    fun catBehindTheSofaArm() =
        shootAllLevels(
            "behindsofa",
            -96f,
            listOf(Spot(-150f, SurfaceId.FLOOR, 12f), Spot(-100f, SurfaceId.FLOOR, 60f, -1, "gray")),
        )

    private companion object {
        const val SCALE = 4
    }
}
