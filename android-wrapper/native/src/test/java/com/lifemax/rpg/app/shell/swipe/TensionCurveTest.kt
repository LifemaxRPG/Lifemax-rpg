package com.lifemax.rpg.app.shell.swipe

import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TensionCurveTest {
    private val tune = SwipeTune.DEFAULT
    private val ms = 1_000_000L

    @Test
    fun theScreenSitsWhereThePlanSays() {
        val golden =
            listOf(
                .10f to .100f,
                .25f to .243f,
                .30f to .280f,
                .35f to .310f,
                .40f to .333f,
                .45f to .349f,
            )
        for ((u, o) in golden) {
            assertEquals("hold($u)", o, TensionCurve.hold(u, tune), 1e-3f)
            assertEquals("o($u)", o, Tension().offset(u, 0L, tune, wall = false), 1e-3f)
        }
    }

    @Test
    fun theCatchIs42DpBehindTheFingerOnA412DpPhone() {
        val behind = (tune.breakAt - TensionCurve.hold(tune.breakAt, tune)) * 412f
        assertEquals(42f, behind, 0.5f)
    }

    @Test
    fun theSlopeIsOneAtTheKneeAndHeavyAtTheCatch() {
        assertEquals(1f, TensionCurve.holdSlope(tune.knee, tune), 1e-6f)
        assertEquals(tune.heavy, TensionCurve.holdSlope(tune.breakAt, tune), 1e-6f)
        // and the curve agrees with its slope on both sides of each joint
        val h = 1e-3f
        for (u in listOf(tune.knee, tune.breakAt)) {
            val left = (TensionCurve.hold(u, tune) - TensionCurve.hold(u - h, tune)) / h
            val right = (TensionCurve.hold(u + h, tune) - TensionCurve.hold(u, tune)) / h
            assertEquals(TensionCurve.holdSlope(u, tune), left, 2e-3f)
            assertEquals(TensionCurve.holdSlope(u, tune), right, 2e-3f)
        }
    }

    @Test
    fun everyClampedTuneGivesAMonotonicCurve() {
        val random = Random(7)
        repeat(200) {
            val t =
                SwipeTune(
                        knee = random.nextFloat() * 0.5f,
                        breakAt = random.nextFloat(),
                        heavy = random.nextFloat() * 1.2f,
                        wallMax = random.nextFloat() * 0.2f,
                        wallSlope = random.nextFloat() * 0.8f,
                    )
                    .clamped()
            var hold = TensionCurve.hold(0f, t)
            var wall = TensionCurve.wall(0f, t)
            for (i in 1..1000) {
                val u = i / 1000f
                val nextHold = TensionCurve.hold(u, t)
                val nextWall = TensionCurve.wall(u, t)
                assertTrue("hold rises at $u for $t", nextHold > hold)
                assertTrue("wall never falls at $u for $t", nextWall >= wall)
                hold = nextHold
                wall = nextWall
            }
        }
    }

    @Test
    fun theLatchEngagesAtTheCatchAndLetsGoOnlyBelowItsHysteresis() {
        val b = tune.breakAt
        val h = tune.hyst
        assertEquals(false, TensionCurve.latched(b - 1e-4f, false, tune))
        assertEquals(true, TensionCurve.latched(b, false, tune))
        assertEquals(true, TensionCurve.latched(b - h + 1e-4f, true, tune))
        assertEquals(false, TensionCurve.latched(b - h - 1e-4f, true, tune))
    }

    @Test
    fun jitterAroundTheCatchNeverChatters() {
        val random = Random(11)
        var tension = Tension()
        val flips = mutableListOf<Flip>()
        var t = 0L
        fun at(u: Float) {
            t += 4 * ms
            val followed = tension.follow(u, t, 1f, tune, wall = false)
            followed.flip?.let(flips::add)
            tension = followed.tension
        }
        for (i in 0..45) at(i / 100f)
        repeat(300) { at(tune.breakAt + (random.nextFloat() * 2f - 1f) * 0.01f) }
        assertEquals(listOf(Flip.Latch), flips)
        repeat(300) { at(tune.breakAt - tune.hyst + (random.nextFloat() * 2f - 1f) * 0.01f) }
        assertEquals(listOf(Flip.Latch, Flip.Unlatch), flips)
    }

    @Test
    fun theResidualKeepsTheScreenContinuousAtEveryFlip() {
        val random = Random(5)
        var tension = Tension()
        var t = 0L
        var flips = 0
        repeat(5000) { i ->
            t += random.nextLong(1, 12) * ms
            // a finger sweeping back and forth across the catch, with a tremor
            val sweep = 0.42f + 0.3f * sin(i / 40f)
            val u = (sweep + (random.nextFloat() - 0.5f) * 0.04f).coerceIn(0f, 1f)
            val before = tension.raw(u, t, tune, wall = false)
            val followed = tension.follow(u, t, 1f, tune, wall = false)
            if (followed.flip != null) flips++
            assertEquals(
                "o jumped at u=$u",
                before,
                followed.tension.raw(u, t, tune, wall = false),
                1e-5f,
            )
            tension = followed.tension
        }
        assertTrue("the walk crossed the catch", flips > 20)
    }

    @Test
    fun thePopOvershootsAboutSevenPercentAndPeaksAt120Ms() {
        val latched = Tension().follow(tune.breakAt, 0L, 1f, tune, wall = false)
        assertEquals(Flip.Latch, latched.flip)
        val jump = TensionCurve.hold(tune.breakAt, tune) - tune.breakAt
        var peak = 0f
        var peakAt = 0L
        for (step in 0..400) {
            val r = latched.tension.residualAt(step * ms)
            if (r > peak) {
                peak = r
                peakAt = step.toLong()
            }
        }
        assertEquals(0.068f, peak / -jump, 0.004f)
        assertEquals(120f, peakAt.toFloat(), 8f)
    }

    @Test
    fun theTensionComesBackWithoutASecondPop() {
        var tension = Tension().follow(0.6f, 0L, 1f, tune, wall = false).tension
        val unlatched = tension.follow(0.3f, 500 * ms, 1f, tune, wall = false)
        assertEquals(Flip.Unlatch, unlatched.flip)
        tension = unlatched.tension
        for (step in 500..1200) assertTrue(tension.residualAt(step * ms) >= -1e-6f)
    }

    @Test
    fun aWallGivesAtMostItsMaximumAndStartsAtItsSlope() {
        // below E over any travel a finger can make; far beyond, float rounding reaches E itself
        for (i in 0..150) assertTrue(TensionCurve.wall(i / 100f, tune) < tune.wallMax)
        for (i in 0..1000) assertTrue(TensionCurve.wall(i / 100f, tune) <= tune.wallMax)
        assertEquals(0f, TensionCurve.wall(0f, tune), 0f)
        assertEquals(tune.wallSlope, TensionCurve.wallSlope(0f, tune), 1e-6f)
        assertEquals(tune.wallSlope, TensionCurve.wall(1e-4f, tune) / 1e-4f, 1e-3f)
        assertEquals(0f, TensionCurve.wall(0.5f, tune.copy(wallMax = 0f)), 0f)
    }

    @Test
    fun aWallNeverLatches() {
        val followed = Tension().follow(0.9f, 0L, 1f, tune, wall = true)
        assertNull(followed.flip)
        assertEquals(
            TensionCurve.wall(0.9f, tune),
            followed.tension.offset(0.9f, 0L, tune, wall = true),
            0f,
        )
    }

    @Test
    fun turningToTheOtherSideMirrorsTheResidual() {
        val popped = Tension().follow(0.5f, 0L, 1f, tune, wall = false).tension
        val now = 30 * ms
        val turned = popped.reversed(now)
        assertEquals(false, turned.latched)
        assertEquals(-popped.residualAt(now), turned.residualAt(now), 1e-6f)
        assertEquals(-popped.residual!!.velocity(now), turned.residual!!.velocity(now), 1e-4f)
    }

    @Test
    fun theInverseOfTheCurveIsTheCurvesInverse() {
        for (i in 0..44) {
            val u = i / 100f
            assertEquals(
                "u=$u",
                u,
                TensionCurve.invertHold(TensionCurve.hold(u, tune), tune),
                2e-3f,
            )
        }
        // beyond what the curve reaches before the catch it stops at the catch
        assertEquals(tune.breakAt, TensionCurve.invertHold(0.9f, tune), 0f)
        // and a tune with no tension (m = 1) is the identity
        val free = tune.copy(heavy = 1f)
        assertEquals(0.3f, TensionCurve.invertHold(0.3f, free), 1e-6f)
    }

    @Test
    fun aHeldScreenStandsWhereItWasCaughtForEveryTuneAndOffset() {
        val random = Random(11)
        repeat(300) {
            val t =
                SwipeTune(
                        knee = random.nextFloat() * 0.5f,
                        breakAt = random.nextFloat(),
                        heavy = random.nextFloat() * 1.2f,
                        wallMax = random.nextFloat() * 0.2f,
                    )
                    .clamped()
            val o = random.nextFloat()
            val wall = random.nextInt(5) == 0
            val held = Tension.holding(o, 0L, 1f, t, wall)
            val shown = held.tension.offset(held.u, 0L, t, wall)
            assertEquals("o=$o wall=$wall $t", o, shown, 2e-3f)
        }
    }

    @Test
    fun aScreenCaughtPastTheCatchIsLatchedAndFollowsTheFingerOneToOne() {
        val held = Tension.holding(0.6f, 0L, 1f, tune, wall = false)
        assertTrue(held.tension.latched)
        assertEquals(0.6f, held.u, 0f)
        // pulling back under the catch's hysteresis lets the tension return, as any latched drag
        val back = held.tension.follow(tune.breakAt - tune.hyst - 0.01f, 0L, 1f, tune, wall = false)
        assertEquals(Flip.Unlatch, back.flip)
    }

    @Test
    fun aScreenCaughtBetweenTheCurveAndTheCatchSettlesOntoTheCurve() {
        val between = (TensionCurve.hold(tune.breakAt, tune) + tune.breakAt) / 2f
        val held = Tension.holding(between, 0L, 1f, tune, wall = false)
        assertFalse(held.tension.latched)
        assertNotNull("a residual takes up the gap", held.tension.residual)
        assertEquals(between, held.tension.offset(held.u, 0L, tune, wall = false), 1e-3f)
        // 300 ms later the residual is gone and the screen is on the curve under the finger
        val later = held.tension.offset(held.u, 300 * ms, tune, wall = false)
        assertEquals(TensionCurve.hold(held.u, tune), later, 2e-3f)
    }

    @Test
    fun scaleZeroMakesThePopInstant() {
        val latched = Tension().follow(tune.breakAt, 0L, 0f, tune, wall = false).tension
        assertEquals(tune.breakAt, latched.offset(tune.breakAt, 0L, tune, wall = false), 0f)
    }
}
