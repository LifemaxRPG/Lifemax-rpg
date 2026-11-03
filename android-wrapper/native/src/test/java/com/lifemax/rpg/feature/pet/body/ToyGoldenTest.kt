package com.lifemax.rpg.feature.pet.body

import org.junit.Test

/** Golden traces of the toy scenes (RECORD=1 regenerates them): pickup, carry, tosses, play. */
class ToyGoldenTest {
    private fun golden(c: ToyScenes.Case) {
        c.scene.plan(*c.plan.toTypedArray())
        TraceSupport.checkGolden("toy-${c.name}", c.scene.trace(c.seconds))
    }

    @Test fun pickup() = golden(ToyScenes.pickup())

    @Test fun carryWithTurn() = golden(ToyScenes.carryTurn())

    @Test fun tossToFloor() = golden(ToyScenes.tossFloor())

    @Test fun tossToCounter() = golden(ToyScenes.tossCounter())

    @Test fun batAndChase() = golden(ToyScenes.batChase())

    @Test fun kick() = golden(ToyScenes.kick())

    @Test fun pounce() = golden(ToyScenes.pounce())

    @Test fun carryToBed() = golden(ToyScenes.carryToBed())
}
