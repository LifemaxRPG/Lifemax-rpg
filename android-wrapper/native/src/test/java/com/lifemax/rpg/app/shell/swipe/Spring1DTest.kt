package com.lifemax.rpg.app.shell.swipe

import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Spring1DTest {
    private val ms = 1_000_000L
    private val tune = SwipeTune.DEFAULT
    private val max = SwipeTune.MAX_STIFFNESS

    private fun commit(from: Float, velocity: Float, scale: Float = 1f) =
        Spring1D.settle(from, velocity, 1f, tune.commitK, max, 0L, scale)

    private fun cancel(from: Float, velocity: Float, scale: Float = 1f) =
        Spring1D.settle(from, velocity, 0f, tune.cancelK, max, 0L, scale)

    /** The first millisecond at which the spring has covered 95% of the way. */
    private fun ninetyFive(spring: Spring1D): Long {
        val span = abs(spring.target - spring.start)
        return (0L..5000L).first { abs(spring.position(it * ms) - spring.target) <= 0.05f * span }
    }

    @Test
    fun aCommitNeverPassesTheFarEdge() {
        for (width in listOf(1080f, 1440f)) {
            for (from in listOf(0f, 0.1f, 0.35f, 0.5f, 0.9f, 0.99f)) {
                for (px in listOf(0f, 500f, 2000f, 5000f, 10_000f, 20_000f)) {
                    val spring = commit(from, px / width)
                    // the cap holds on its own; the guard is only a guard
                    val unguarded = spring.copy(guarded = false)
                    for (t in 0..600) {
                        assertTrue("$from at $px px/s", spring.position(t * ms) <= 1f)
                        assertTrue(
                            "$from at $px px/s, unguarded",
                            unguarded.position(t * ms) <= 1f + 1e-5f,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun aCancelNeverShowsTheOtherSide() {
        for (from in listOf(0.01f, 0.2f, 0.349f, 0.6f, 0.95f)) {
            for (px in listOf(-20_000f, -5000f, -600f, 0f, 800f, 3000f)) {
                val spring = cancel(from, px / 1440f)
                val unguarded = spring.copy(guarded = false)
                for (t in 0..600) {
                    assertTrue("$from at $px px/s", spring.position(t * ms) >= 0f)
                    assertTrue("$from at $px px/s, unguarded", unguarded.position(t * ms) >= -1e-5f)
                }
            }
        }
    }

    @Test
    fun fromRestBothSettlesAre95PercentThereWithin120To200Ms() {
        val commitTime = ninetyFive(commit(0.5f, 0f))
        val cancelTime = ninetyFive(cancel(0.5f, 0f))
        assertEquals(150f, commitTime.toFloat(), 2f)
        assertEquals(168f, cancelTime.toFloat(), 2f)
        for (time in listOf(commitTime, cancelTime)) assertTrue("$time ms", time in 120..200)
    }

    @Test
    fun theStiffnessGrowsWithTheReleaseSpeedUpToTheCap() {
        assertEquals(tune.commitK, commit(0.5f, 0f).stiffness, 0f)
        assertEquals(1600f, commit(0.5f, 20f).stiffness, 1e-2f)
        val fast = commit(0.5f, 100f)
        assertEquals(max, fast.stiffness, 0f)
        assertEquals(sqrt(max) * 0.5f, fast.startVelocity, 1e-4f)
    }

    @Test
    fun scaleZeroLandsAtOnce() {
        assertEquals(1f, commit(0.3f, 2f, scale = 0f).position(0L), 0f)
        assertEquals(0f, cancel(0.3f, 2f, scale = 0f).position(0L), 0f)
        val free = Spring1D(0.3f, 0f, 0f, 1200f, 0.65f, 0L, durationScale = 0f)
        assertEquals(0f, free.position(0L), 0f)
        assertEquals(0f, free.velocity(0L), 0f)
    }

    @Test
    fun scaleTwoDoublesTheTime() {
        for (velocity in listOf(0f, 3f)) {
            val once = ninetyFive(commit(0.4f, velocity))
            val twice = ninetyFive(commit(0.4f, velocity, scale = 2f))
            assertEquals(2f * once, twice.toFloat(), 1f)
        }
    }

    @Test
    fun aRetargetKeepsPositionAndVelocity() {
        val pop = Spring1D(-0.1f, 0f, 0f, 1200f, 0.65f, 0L)
        val now = 37 * ms
        val turned = pop.restart(now, target = 0.3f, stiffness = 500f, dampingRatio = 1f)
        assertEquals(pop.position(now), turned.position(now), 1e-6f)
        assertEquals(pop.velocity(now), turned.velocity(now), 1e-5f)
        // a refusal: the commit settle retargeted to 0 from where it is
        val settle = commit(0.4f, 2f)
        val refused =
            Spring1D.settle(settle.position(now), settle.velocity(now), 0f, tune.cancelK, max, now)
        assertEquals(settle.position(now), refused.position(now), 1e-6f)
        assertEquals(settle.velocity(now), refused.velocity(now), 1e-5f)
        assertTrue("the refusal really turns back", refused.position(now + 300 * ms) < 0.01f)
    }

    @Test
    fun theVelocityIsTheDerivativeOfThePosition() {
        val springs =
            listOf(
                Spring1D(-0.1f, 0.5f, 0f, 1200f, 0.65f, 0L),
                Spring1D(0.4f, 2f, 1f, 1000f, 1f, 0L),
                Spring1D(0.4f, 2f, 1f, 1000f, 1f, 0L, durationScale = 2f),
            )
        for (spring in springs) {
            for (t in listOf(5L, 20L, 60L, 150L)) {
                val h = 10_000L
                val numeric =
                    (spring.position(t * ms + h) - spring.position(t * ms - h)) / (2 * h / 1e9f)
                // per second of animation time
                val animation = spring.velocity(t * ms) / spring.durationScale
                assertEquals("$spring at $t ms", animation, numeric, 2e-2f * (1f + abs(numeric)))
            }
        }
    }

    @Test
    fun anUnderdampedSpringOvershootsByItsDampingRatio() {
        val pop = Spring1D(-1f, 0f, 0f, 1200f, 0.65f, 0L)
        val peak = (0..400).maxOf { pop.position(it * ms) }
        assertEquals(0.068f, peak, 0.003f)
    }

    @Test
    fun aSpringComesToRest() {
        val spring = commit(0.5f, 0f)
        assertTrue(!spring.isAtRest(100 * ms, 0.5f / 1440f))
        assertTrue(spring.isAtRest(450 * ms, 0.5f / 1440f))
        assertTrue(commit(1f, 0f).isAtRest(0L, 0.5f / 1440f))
    }

    @Test
    fun timeBeforeTheStartCountsAsTheStart() {
        val spring = Spring1D(0.2f, 1f, 1f, 1000f, 1f, 100 * ms)
        assertEquals(0.2f, spring.position(50 * ms), 1e-6f)
        assertEquals(0f, spring.time(50 * ms), 0f)
    }
}
