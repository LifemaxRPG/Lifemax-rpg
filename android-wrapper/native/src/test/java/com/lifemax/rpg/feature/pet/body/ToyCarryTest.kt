package com.lifemax.rpg.feature.pet.body

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pickup, carry, toss and set down: the cat and the toy together. */
class ToyCarryTest {
    private fun events(
        scene: ToyScene,
        types: MutableList<CatEventType>,
        frames: MutableList<Int>,
    ) {
        val p = scene.body.pose
        for (i in 0 until p.events.count) {
            types.add(p.events.type(i))
            frames.add(p.frame)
        }
    }

    @Test
    fun theLabRowsAgreeWithTheDirector() {
        val lab = ToyScene.labTable()
        assertEquals(ToyDirector.BITE_FRAME, lab["pickup"]!!.bite)
        assertEquals(ToyDirector.RELEASE_FRAME, lab["toss"]!!.release)
        assertEquals(Clips.PICKUP.frames, lab["pickup"]!!.frames)
        assertEquals(Clips.CARRY.frames, lab["carry"]!!.frames)
        assertTrue(ToyScene.anchors.isMeasured(Clips.CARRY.id, 0))
        assertTrue(!ToyScene.anchors.isMeasured(Clips.WALK.id, 0))
    }

    @Test
    fun theCatStopsAtReachNoticesThenBitesOnTheBiteFrame() {
        val c = ToyScenes.pickup()
        val s = c.scene
        val toyStart = s.toy.x
        s.plan(*c.plan.toTypedArray())
        val types = ArrayList<CatEventType>()
        val frames = ArrayList<Int>()
        var liftFrame = -1
        var grabFrame = -1
        var reachedClip = -1
        s.run(c.seconds) {
            events(it, types, frames)
            if (it.toy.state == ToyState.LIFT && liftFrame < 0) liftFrame = it.body.pose.frame
            if (CatEventType.ToyGrabbed in types && grabFrame < 0) grabFrame = it.body.pose.frame
            if (it.body.pose.clip == Clips.PICKUP.id && reachedClip < 0) reachedClip = it.frame
        }
        assertTrue(types.indexOf(CatEventType.ToyGrabbed) < types.indexOf(CatEventType.StepDone))
        assertEquals(
            "the lift starts one frame before the bite",
            ToyDirector.BITE_FRAME - 1,
            liftFrame,
        )
        assertTrue(
            "grabbed on the bite frame or just after: $grabFrame",
            grabFrame in ToyDirector.BITE_FRAME..5,
        )
        assertEquals(ToyState.HELD, s.toy.state)
        // the cat stopped where its mouth at the bite meets the toy
        val reach = (ToyScene.anchors.u(Clips.PICKUP.id, ToyDirector.BITE_FRAME) - 36f)
        assertEquals(toyStart, s.body.pose.x + reach, 1.5f)
    }

    @Test
    fun theToyRidesTheMouthAnchorWhileCarried() {
        val c = ToyScenes.carryTurn(ToyKind.BALL_A)
        val s = c.scene
        s.plan(*c.plan.toTypedArray())
        var worst = 0f
        var samples = 0
        var calmSince = 0f
        var lastFlip = false
        s.run(c.seconds) {
            val p = it.body.pose
            if (p.flip != lastFlip || p.clip != Clips.CARRY.id) calmSince = it.time
            lastFlip = p.flip
            if (
                it.toy.state == ToyState.HELD &&
                    p.clip == Clips.CARRY.id &&
                    it.time - calmSince > 0.5f
            ) {
                it.locateMouth()
                val dir = if (p.flip) -1 else 1
                val dx = it.toy.x - (it.mouth[0] + it.toy.kind.holdX * dir)
                val dy = it.toy.y - (it.mouth[1] + it.toy.kind.holdY)
                worst = maxOf(worst, Math.hypot(dx.toDouble(), dy.toDouble()).toFloat())
                samples++
            }
        }
        assertTrue("carried for a while: $samples", samples > 200)
        assertTrue("the ball strayed $worst px from the mouth", worst < 2.2f)
    }

    @Test
    fun hangingToysSwingAndSettleOnARodOfFixedLength() {
        for (kind in listOf(ToyKind.MOUSE, ToyKind.FEATHER)) {
            val c = ToyScenes.carryTurn(kind)
            val s = c.scene
            s.plan(*c.plan.toTypedArray())
            var swung = 0f
            var heldSince = -1f
            s.run(c.seconds) {
                if (it.toy.state == ToyState.HELD && heldSince < 0f) heldSince = it.time
                if (it.toy.state == ToyState.HELD && it.time - heldSince > 0.4f) {
                    val dx = it.toy.x - it.toy.pivotX
                    val dy = it.toy.y - it.toy.pivotY
                    val len = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                    assertTrue("$kind rod $len", len in kind.rodLength * 0.5f..kind.rodLength * 2f)
                    swung = maxOf(swung, Math.abs(dx))
                }
            }
            assertTrue("$kind swung: $swung", swung > 2f)
            // the cat stands still for a few seconds: the swing dies away
            s.run(SETTLE_S)
            assertTrue("${kind} still swinging at ${s.toy.speed()}", s.toy.speed() < 6f)
        }
    }

    @Test
    fun aTurnMakesTheToyLagBehindTheMouth() {
        val c = ToyScenes.carryTurn(ToyKind.MOUSE)
        val s = c.scene
        s.plan(*c.plan.toTypedArray())
        var maxAnchorVx = 0f
        var maxToyVx = 0f
        var flipped = false
        s.run(c.seconds) {
            if (it.body.pose.flip) flipped = true
            if (flipped && it.time < 6.2f && it.toy.state == ToyState.HELD) {
                maxToyVx = maxOf(maxToyVx, Math.abs(it.toy.vx))
                maxAnchorVx = maxOf(maxAnchorVx, Math.abs(it.toy.anchorVx))
            }
        }
        assertTrue("the anchor crosses the body, the toy follows it", maxAnchorVx > 40f)
        assertTrue(maxToyVx.isFinite())
    }

    @Test
    fun aTossKeepsTheSwingAndTheFlickAndTheToyComesToRest() {
        val c = ToyScenes.tossFloor()
        val s = c.scene
        s.plan(*c.plan.toTypedArray())
        var vxAtRelease = 0f
        s.run(c.seconds) {
            val p = it.body.pose
            for (i in 0 until p.events.count) {
                if (p.events.type(i) == CatEventType.ToyReleased) vxAtRelease = it.toy.vx
            }
        }
        assertTrue("flicked the way the cat faces (left): $vxAtRelease", vxAtRelease < -30f)
        assertEquals(ToyState.REST, s.toy.state)
        assertEquals(SurfaceId.FLOOR, s.toy.surface)
    }

    @Test
    fun aTossAtTheCounterEndsOnTheCounterAndNotOnTheSillAboveIt() {
        val c = ToyScenes.tossCounter()
        val s = c.scene
        s.plan(*c.plan.toTypedArray())
        s.run(c.seconds)
        assertEquals(SurfaceId.COUNTER, s.toy.surface)
        assertEquals(ToyState.REST, s.toy.state)
        assertEquals(World.surface(SurfaceId.COUNTER).y - s.toy.kind.halfHeight, s.toy.y, 0.01f)
    }

    @Test
    fun setDownLeavesTheToyOnThePerch() {
        val c = ToyScenes.carryToBed()
        val s = c.scene
        s.plan(*c.plan.toTypedArray())
        s.run(c.seconds)
        assertEquals(SurfaceId.BED, s.toy.surface)
        assertEquals(ToyState.REST, s.toy.state)
        assertNotNull(s.body.pose)
        assertTrue(s.body.heldToy == null)
    }

    @Test
    fun theBatKnocksTheToyAndTheChaseFollowsIt() {
        val c = ToyScenes.batChase()
        val s = c.scene
        s.plan(*c.plan.toTypedArray())
        val types = ArrayList<CatEventType>()
        val frames = ArrayList<Int>()
        var farthest = s.toy.x
        s.run(c.seconds) {
            events(it, types, frames)
            farthest = maxOf(farthest, it.toy.x)
        }
        assertTrue(types.count { it == CatEventType.ToyHit } >= 2)
        assertTrue("knocked well away: $farthest", farthest > s.toy.x || farthest > 120f)
        assertTrue("the cat followed: ${s.body.pose.x}", s.body.pose.x > 70f)
    }

    @Test
    fun aStepOnAMissingToyEndsAtOnce() {
        val s = ToyScene()
        s.plan(
            PlanStep.PickUp(7),
            PlanStep.Toss(1),
            PlanStep.SetDown,
            PlanStep.Bat(9),
            PlanStep.Chase(9),
        )
        val types = ArrayList<CatEventType>()
        val frames = ArrayList<Int>()
        s.run(2f) { events(it, types, frames) }
        assertEquals(5, types.count { it == CatEventType.StepDone })
    }

    private companion object {
        const val SETTLE_S = 5f
    }
}
