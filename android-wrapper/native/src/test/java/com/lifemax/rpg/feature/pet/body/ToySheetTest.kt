package com.lifemax.rpg.feature.pet.body

import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Draws the toy contact sheets when `TOY_SHEET` names a directory; otherwise does nothing. Look at
 * the PNGs; nothing is asserted.
 */
class ToySheetTest {
    private fun sheet(name: String, case: ToyScenes.Case, from: Float, every: Float, count: Int) {
        val dir = System.getenv("TOY_SHEET")
        assumeTrue("set TOY_SHEET to a directory to draw the sheets", dir != null)
        ToySheet.render(case, from, every, count, File(dir!!, "$name.png"))
    }

    @Test fun pickupBall() = sheet("pickup-ball", ToyScenes.pickup(ToyKind.BALL_A), 1.55f, 0.1f, 15)

    @Test
    fun pickupMouse() = sheet("pickup-mouse", ToyScenes.pickup(ToyKind.MOUSE), 1.55f, 0.1f, 15)

    @Test
    fun carryTurnMouse() =
        sheet("carry-turn-mouse", ToyScenes.carryTurn(ToyKind.MOUSE), 4.9f, 0.14f, 15)

    @Test
    fun carryTurnFeather() =
        sheet("carry-turn-feather", ToyScenes.carryTurn(ToyKind.FEATHER), 4.9f, 0.14f, 15)

    @Test
    fun carryYarn() = sheet("carry-turn-yarn", ToyScenes.carryTurn(ToyKind.YARN), 2.4f, 0.28f, 15)

    @Test
    fun carrySpring() =
        sheet("carry-turn-spring", ToyScenes.carryTurn(ToyKind.SPRING), 2.4f, 0.28f, 15)

    @Test fun tossFloor() = sheet("toss-floor", ToyScenes.tossFloor(), 3.7f, 0.12f, 15)

    @Test fun tossCounter() = sheet("toss-counter", ToyScenes.tossCounter(), 3.7f, 0.12f, 15)

    @Test fun batChase() = sheet("bat-chase", ToyScenes.batChase(), 1.4f, 0.2f, 15)

    @Test fun kick() = sheet("kick", ToyScenes.kick(), 1.8f, 0.3f, 15)

    @Test fun pounce() = sheet("pounce", ToyScenes.pounce(), 2.2f, 0.12f, 15)

    @Test fun carryToBed() = sheet("carry-bed", ToyScenes.carryToBed(), 3.6f, 0.15f, 15)
}
