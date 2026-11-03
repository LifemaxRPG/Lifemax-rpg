package com.lifemax.rpg.feature.pet.body

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The toy scenes replay exactly, and 60 and 120 Hz hosts see the same toy at the same times. */
class ToyDeterminismTest {
    private class Sample(
        val t: Float,
        val x: Float,
        val y: Float,
        val catX: Float,
        val state: ToyState,
    )

    private fun samples(case: ToyScenes.Case): List<Sample> {
        val s = case.scene
        s.plan(*case.plan.toTypedArray())
        val out = ArrayList<Sample>()
        val every = s.hz / 10
        s.run(case.seconds) {
            if (it.frame % every == 0)
                out.add(Sample(it.time, it.toy.x, it.toy.y, it.body.pose.x, it.toy.state))
        }
        return out
    }

    @Test
    fun aSceneReplaysExactly() {
        for (make in listOf(ToyScenes::pickup, ToyScenes::tossFloor, ToyScenes::batChase)) {
            val a = make(ToyKind.MOUSE, 60)
            val b = make(ToyKind.MOUSE, 60)
            a.scene.plan(*a.plan.toTypedArray())
            b.scene.plan(*b.plan.toTypedArray())
            assertEquals(a.scene.trace(a.seconds), b.scene.trace(b.seconds))
        }
    }

    @Test
    fun sixtyAndOneTwentyHertzAgree() {
        val cases =
            listOf(
                ToyScenes.pickup(ToyKind.BALL_A, 60) to ToyScenes.pickup(ToyKind.BALL_A, 120),
                ToyScenes.carryTurn(ToyKind.MOUSE, 60) to ToyScenes.carryTurn(ToyKind.MOUSE, 120),
                ToyScenes.tossFloor(ToyKind.FEATHER, 60) to
                    ToyScenes.tossFloor(ToyKind.FEATHER, 120),
                ToyScenes.tossCounter(ToyKind.BALL_A, 60) to
                    ToyScenes.tossCounter(ToyKind.BALL_A, 120),
                ToyScenes.batChase(ToyKind.BALL_B, 60) to ToyScenes.batChase(ToyKind.BALL_B, 120),
                ToyScenes.kick(ToyKind.SPRING, 60) to ToyScenes.kick(ToyKind.SPRING, 120),
                ToyScenes.pounce(ToyKind.MOUSE, 60) to ToyScenes.pounce(ToyKind.MOUSE, 120),
            )
        for ((slow, fast) in cases) {
            val a = samples(slow)
            val b = samples(fast)
            assertEquals(slow.name, a.size, b.size)
            for (i in a.indices) {
                val dx = Math.abs(a[i].x - b[i].x)
                val dy = Math.abs(a[i].y - b[i].y)
                assertTrue(
                    "${slow.name} t=${a[i].t}: toy ${a[i].x},${a[i].y} vs ${b[i].x},${b[i].y}",
                    dx < TOL && dy < TOL,
                )
                assertTrue("${slow.name} cat", Math.abs(a[i].catX - b[i].catX) < TOL)
                assertEquals("${slow.name} state at ${a[i].t}", a[i].state, b[i].state)
            }
        }
    }

    private companion object {
        const val TOL = 0.05f
    }
}
