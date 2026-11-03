package com.lifemax.rpg.feature.pet.body

import com.lifemax.rpg.feature.pet.room.RoomLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cats under a finger: which cat a touch lands on (the front one where two overlap), what the
 * body does by itself while it is petted (clip, purr, ears, hearts, buzzes), how it leaves when it
 * has had enough, and what the page is told when the finger lifts.
 */
class PettingTest {
    /** Everything a touch asked of the screen. */
    private class Log : TouchSink {
        val bursts = ArrayList<Burst>()
        val buzzes = ArrayList<Beat>()
        val purrs = ArrayList<Pair<Int, Float>>()
        val strokes = ArrayList<Pair<Int, TouchModel.Outcome>>()

        override fun burst(kind: Burst, x: Float, y: Float) {
            bursts += kind
        }

        override fun buzz(beat: Beat) {
            buzzes += beat
        }

        override fun purr(cat: Int, level: Float) {
            purrs += cat to level
        }

        override fun stroke(cat: Int, outcome: TouchModel.Outcome) {
            strokes += cat to outcome
        }
    }

    private val log = Log()

    /** A house of [n] cats standing still (no autopilot) at [xs], in depth [zs]. */
    private fun house(
        n: Int = 1,
        xs: List<Float> = listOf(40f, 40f),
        zs: List<Float> = listOf(35f, 35f),
    ) =
        HouseLife(List(n) { HouseCat(it + 7, false) }, autopilot = false).also { h ->
            for (i in 0 until n) h.bodies[i].placeAt(xs[i], zs[i], SurfaceId.FLOOR, 1)
        }

    private fun petting(
        h: HouseLife,
        level: Int = 3,
        satMul: Float = 1f,
        trust: Float = 0.5f,
        sat: Float = 0f,
    ) =
        Petting(h, log).also { p ->
            for (i in 0 until h.count) p.profile(i, level, satMul, trust, sat)
        }

    private fun HouseLife.advance(p: Petting, seconds: Float, hz: Int = 60) {
        repeat(Math.round(seconds * hz)) {
            step(1f / hz)
            p.step(1f / hz)
        }
    }

    private fun HouseLife.chest(i: Int): Pair<Float, Float> {
        val pose = bodies[i].pose
        return pose.x to pose.y - 12f * pose.depthScale
    }

    private fun HouseLife.clip(i: Int) = bodies[i].pose.clipName

    // ------------------------------------------------------------------ which cat

    @Test
    fun aFingerOnTheCatIsTheCatAndOnTheEmptyFloorIsTheRoom() {
        val h = house()
        val (x, y) = h.chest(0)
        assertEquals(0, TouchHit.at(h.bodies, x, y))
        assertEquals(TouchHit.ROOM, TouchHit.at(h.bodies, x + 120f, y))
        assertEquals(TouchHit.ROOM, TouchHit.at(h.bodies, x, y - 80f))
    }

    @Test
    fun theBoxIsFiftyTwoWideAndScalesWithDepth() {
        val near = house(zs = listOf(100f, 100f)).bodies[0].pose
        val far = house(zs = listOf(0f, 0f)).bodies[0].pose
        assertTrue(TouchHit.onCat(near, near.x + 26f * near.depthScale - 1f, near.y - 5f))
        assertFalse(TouchHit.onCat(near, near.x + 26f * near.depthScale + 2f, near.y - 5f))
        assertTrue(TouchHit.onCat(far, far.x + 26f * far.depthScale - 1f, far.y - 5f))
        assertFalse(TouchHit.onCat(far, far.x + 30f, far.y - 5f))
    }

    @Test
    fun whereTwoCatsOverlapTheOneInFrontIsTouched() {
        val h = house(2, xs = listOf(60f, 70f), zs = listOf(40f, 50f))
        val x = 65f
        // a point at the shallower cat's chest, which the deeper cat's box covers too
        val y = h.bodies[0].pose.y - 8f
        val both = h.bodies.indices.filter { TouchHit.onCat(h.bodies[it].pose, x, y) }
        assertEquals(listOf(0, 1), both)
        // the deeper cat is drawn last, so it is the one the finger meets
        assertEquals(1, TouchHit.at(h.bodies, x, y))
        // the same two, swapped in depth
        val swapped = house(2, xs = listOf(60f, 70f), zs = listOf(50f, 40f))
        assertEquals(0, TouchHit.at(swapped.bodies, x, swapped.bodies[1].pose.y - 8f))
        // away from the deeper cat only the shallower is there
        assertEquals(0, TouchHit.at(h.bodies, 60f - 25f, h.bodies[0].pose.y - 8f))
    }

    @Test
    fun theFoodBowlIsHitOnlyWhereNoCatIs() {
        val h = house(xs = listOf(-100f, 0f))
        assertEquals(
            TouchHit.BOWL,
            TouchHit.at(h.bodies, RoomLayout.BOWL_FOOD_X.toFloat(), RoomLayout.BOWL_TOP + 4f),
        )
        assertEquals(
            TouchHit.ROOM,
            TouchHit.at(h.bodies, RoomLayout.BOWL_FOOD_X + 30f, RoomLayout.BOWL_TOP + 4f),
        )
        val near = house(xs = listOf(RoomLayout.BOWL_FOOD_X.toFloat(), 0f), zs = listOf(100f, 100f))
        val pose = near.bodies[0].pose
        assertEquals(0, TouchHit.at(near.bodies, pose.x, pose.y - 5f))
    }

    // ------------------------------------------------------------------ a hand on the cat

    @Test
    fun aHandOnTheCatHoldsItStillAndItPurrsAndHeartsRise() {
        val h = house()
        val p = petting(h)
        val (x, y) = h.chest(0)
        assertEquals(0, p.down(x, y))
        h.advance(p, 0.3f)
        assertEquals("purr", h.clip(0))
        assertEquals(listOf(Beat.DOWN), log.buzzes)
        h.advance(p, 3f)
        assertEquals(listOf(0 to 0.75f), log.purrs)
        assertEquals(3, log.bursts.count { it == Burst.HEART })
        assertEquals(3, log.buzzes.count { it == Beat.HEART })
        assertTrue("the body vibrates", h.bodies[0].pose.vibration != 0f || h.clip(0) == "purr")
    }

    @Test
    fun theTrustingCatsHeartsAreBigAndTheWaryCatSitsAndBlinks() {
        val h = house()
        val devoted = petting(h, level = 5)
        val (x, y) = h.chest(0)
        devoted.down(x, y)
        h.advance(devoted, 2f)
        assertTrue(log.bursts.contains(Burst.HEART_BIG))
        devoted.up()
        val h2 = house()
        val fond = petting(h2, level = 2)
        fond.down(x, y)
        h2.advance(fond, 0.5f)
        assertEquals("slowblink", h2.clip(0))
        fond.up()
        val h1 = house()
        val wary = petting(h1, level = 1)
        wary.down(x, y)
        h1.advance(wary, 0.5f)
        assertEquals("idle_sit", h1.clip(0))
    }

    @Test
    fun liftingTheHandStopsThePurrAndTellsThePageWhatTheTouchCameTo() {
        val h = house()
        val p = petting(h)
        val (x, y) = h.chest(0)
        p.down(x, y)
        h.advance(p, 2f)
        p.up()
        assertEquals(0f, log.purrs.last().second, 0f)
        val (cat, out) = log.strokes.single()
        assertEquals(0, cat)
        assertEquals(2000.0, out.heldMs.toDouble(), 20.0)
        assertTrue(out.counts)
        assertFalse(out.over)
        assertEquals(-1, p.touching)
        h.advance(p, 0.5f)
        assertTrue("it sits a moment after", h.clip(0) == "slowblink" || h.clip(0) == "idle_sit")
    }

    @Test
    fun aTapIsReportedAsATapAndCountsForNothing() {
        val h = house()
        val p = petting(h)
        val (x, y) = h.chest(0)
        p.down(x, y)
        h.advance(p, 0.15f)
        p.up()
        val out = log.strokes.single().second
        assertTrue(out.tap)
        assertFalse(out.counts)
    }

    @Test
    fun aCatThatHasHadEnoughGetsUpAndLeavesWhileTheFingerStays() {
        val h = house()
        val p = petting(h, satMul = 1.3f, trust = 0f)
        val (x, y) = h.chest(0)
        val before = h.bodies[0].pose.x
        p.down(x, y)
        h.advance(p, 5.5f) // the ears at 3.4 s, gone at 4.75 s
        assertTrue("an exclamation", log.bursts.contains(Burst.EXCLAIM))
        h.advance(p, 3f)
        assertTrue(
            "it walked away: ${h.bodies[0].pose.x} from $before",
            Math.abs(h.bodies[0].pose.x - before) > 20f,
        )
        assertEquals(0f, log.purrs.last().second, 0f)
        p.up()
        val out = log.strokes.single().second
        assertTrue(out.over)
        assertFalse(out.counts)
    }

    @Test
    fun theWarnedCatPutsItsEarsBackAndTheyComeForwardAgainLater() {
        val h = house()
        val p = petting(h, satMul = 1.3f, trust = 0f)
        val (x, y) = h.chest(0)
        p.down(x, y)
        h.advance(p, 3.8f)
        val flat = h.bodies[0].pose.earFlat
        assertTrue("ears back: $flat", flat > 0.5f)
        p.up()
        h.advance(p, 10f)
        assertTrue("ears forward again", h.bodies[0].pose.earFlat < 0.3f)
    }

    @Test
    fun aStrangerStartlesAndRunsFromTheFinger() {
        val h = house()
        val p = petting(h, level = 0)
        val (x, y) = h.chest(0)
        val before = h.bodies[0].pose.x
        p.down(x - 5f, y) // the finger lands on its left
        h.advance(p, 2.5f)
        p.up()
        assertTrue("it ran to the right", h.bodies[0].pose.x > before + 20f)
        assertTrue(log.bursts.contains(Burst.EXCLAIM))
        assertEquals(0, log.strokes.single().second.level)
        assertTrue(log.purrs.isEmpty())
    }

    @Test
    fun aSleeperWhoTrustsYouStaysAsleepAndGoesBackToSleep() {
        val h = house()
        h.direct(0, listOf(PlanStep.Act("sleep", 100f)), h.bodies[0].pose.x)
        val p = petting(h, level = 4)
        h.advance(p, 1f)
        assertTrue(h.isAsleep(0))
        val (x, y) = h.chest(0)
        p.down(x, y)
        h.advance(p, 2f)
        assertTrue("still asleep under the hand", h.isAsleep(0))
        assertEquals(0.3f, log.purrs.last().second, 0f)
        p.up()
        h.advance(p, 1f)
        assertTrue("and asleep after", h.isAsleep(0))
        assertTrue(log.strokes.single().second.keepSleep)
    }

    @Test
    fun aLighterSleeperWakesWithAStretch() {
        val h = house()
        h.direct(0, listOf(PlanStep.Act("sleep", 100f)), h.bodies[0].pose.x)
        val p = petting(h, level = 2)
        h.advance(p, 1f)
        val (x, y) = h.chest(0)
        p.down(x, y)
        h.advance(p, 1.5f)
        assertFalse(h.isAsleep(0))
        p.up()
        h.advance(p, 0.5f)
        assertEquals("stretch", h.clip(0))
    }

    @Test
    fun aFingerDraggedFromTheFloorOntoTheCatPetsIt() {
        val h = house()
        val p = petting(h)
        val (x, y) = h.chest(0)
        assertEquals(TouchHit.ROOM, p.down(x + 90f, y))
        assertEquals(-1, p.touching)
        p.move(x + 50f, y)
        assertEquals(-1, p.touching)
        p.move(x + 10f, y)
        assertEquals(0, p.touching)
        h.advance(p, 1.5f)
        p.up()
        assertTrue(log.strokes.single().second.counts)
    }

    @Test
    fun aFingerOnTheBowlIsNotACatAndTellsThePageNothing() {
        val h = house(xs = listOf(-100f, -90f))
        val p = petting(h)
        val hit = p.down(RoomLayout.BOWL_FOOD_X.toFloat(), RoomLayout.BOWL_TOP + 4f)
        p.up()
        assertEquals(TouchHit.BOWL, hit)
        assertTrue(log.strokes.isEmpty())
    }

    @Test
    fun twoCatsHaveTheirOwnSaturationAndTheOneTouchedIsTheOneReported() {
        val h = house(2, xs = listOf(30f, 150f))
        val p = petting(h)
        val (x, y) = h.chest(1)
        p.down(x, y)
        h.advance(p, 2f)
        p.up()
        assertEquals(1, log.strokes.single().first)
        assertTrue(p.saturation(1) > 0.2f)
        assertEquals(0f, p.saturation(0), 0f)
    }

    @Test
    fun thePageSaysAnotherCatLeveledUpAndTheRoomCelebrates() {
        val h = house()
        val p = petting(h)
        p.levelUp(0)
        assertEquals(5, log.bursts.count { it == Burst.HEART_BIG })
        assertEquals(listOf(Beat.LEVEL_UP), log.buzzes)
        p.levelUp(4) // no such cat: nothing
        assertEquals(5, log.bursts.count { it == Burst.HEART_BIG })
    }

    @Test
    fun theSameTouchesReplayTheSameBodyAtAnyFrameRate() {
        fun play(hz: Int): Float {
            val h = house()
            val p = petting(h, level = 4)
            val (x, y) = h.chest(0)
            p.down(x, y)
            h.advance(p, 2f, hz)
            p.up()
            h.advance(p, 2f, hz)
            return p.saturation(0)
        }
        assertEquals(play(60), play(120), 0.01f)
    }
}
