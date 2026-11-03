package com.lifemax.rpg.feature.pet.body

import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Draws the contact sheets of the parkour chains over the room when `PARKOUR_SHEET` names a
 * directory; otherwise does nothing. Look at the PNGs; nothing is asserted. Colours: orange the
 * adult's flights, purple the smallest kitten's, blue the bookcase route.
 */
class ParkourSheetTest {
    private fun cat(seed: Int, x: Float, scale: Float = 1f) =
        CatBody(seed = seed, startX = x).also { it.growthScale = scale }

    private fun run(body: CatBody, to: SurfaceId, x: Float, color: Int) =
        ParkourSheet.Run(body, body.routeTo(to, x), color)

    @Test
    fun theChainsOverTheRoom() {
        val dir = System.getenv("PARKOUR_SHEET")
        assumeTrue("set PARKOUR_SHEET to a directory to draw the sheets", dir != null)
        val out = File(dir!!)
        out.mkdirs()
        val adult = cat(1, 262f)
        val kitten = cat(2, 262f, Tuning.Parkour.STAGE_SCALE[0])
        ParkourSheet.render(
            listOf(
                run(adult, SurfaceId.FRIDGE, 226f, ORANGE),
                run(kitten, SurfaceId.COUNTER, 310f, PURPLE),
            ),
            15f,
            File(out, "kitchen.png"),
        )
        val tree = cat(3, 92f)
        val shelf = cat(4, 20f)
        ParkourSheet.render(
            listOf(
                run(tree, SurfaceId.TOP, 154f, ORANGE),
                run(shelf, SurfaceId.BOOKCASE, 120f, BLUE),
            ),
            22f,
            File(out, "bedroom.png"),
        )
        val sofa = cat(5, -20f)
        ParkourSheet.render(
            listOf(run(sofa, SurfaceId.LSILL, -100f, ORANGE)),
            14f,
            File(out, "living.png"),
        )
    }

    private companion object {
        const val ORANGE = 0xE66E00
        const val PURPLE = 0xA028A0
        const val BLUE = 0x0078BE
    }
}
