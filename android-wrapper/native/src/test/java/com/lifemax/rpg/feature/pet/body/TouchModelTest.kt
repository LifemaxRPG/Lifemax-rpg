package com.lifemax.rpg.feature.pet.body

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The touch rules the page applies (26-pet-brain.js, 16-runtime.js), as the screen's reflex plays
 * them: when a finger is a tap and when a pet, what saturates a cat, when its ears go back and when
 * it leaves, the belly of a sleeper, the shy cat's, the stranger's. All on explicit `dt`s.
 */
class TouchModelTest {
    private fun model(
        level: Int = 3,
        weak: Boolean = false,
        satMul: Float = 1f,
        trust: Float = 0.5f,
    ) = TouchModel().also { it.profile(level, weak, satMul, trust) }

    /**
     * Step for [seconds] at [hz]; every flag raised on the way, and the time the first of [watch]
     * came.
     */
    private class Run(val flags: Int, val firstAt: Map<Int, Float>)

    private fun run(
        m: TouchModel,
        seconds: Float,
        hz: Int = 60,
        watch: List<Int> = emptyList(),
    ): Run {
        var all = 0
        val first = HashMap<Int, Float>()
        val frames = Math.round(seconds * hz)
        for (i in 1..frames) {
            val f = m.step(1f / hz)
            all = all or f
            for (w in watch) if (f and w != 0 && w !in first) first[w] = i / hz.toFloat()
        }
        return Run(all, first)
    }

    @Test
    fun aShortStillTouchIsATapAndNotAPet() {
        val m = model()
        assertEquals(0, m.begin(asleep = false))
        run(m, 0.2f)
        val out = m.end()
        assertTrue(out.tap)
        assertFalse(out.counts)
        assertEquals(200L, out.heldMs)
        // a landing adds the tap share (0.12) and the hold adds 0.09 a second, calmed by trust
        assertEquals(0.12f + 0.09f * 0.2f * (1.25f - 0.25f), out.saturation, 0.002f)
    }

    @Test
    fun aTouchIsAPetFromOnePointOneSeconds() {
        val short = model().also { it.begin(false) }
        run(short, 1.0f)
        assertFalse(short.end().counts)
        val long = model().also { it.begin(false) }
        run(long, 1.2f)
        val out = long.end()
        assertTrue(out.counts)
        assertFalse(out.tap)
    }

    @Test
    fun aMovingFingerIsNotATapEvenWhenShort() {
        val m = model().also { it.begin(false) }
        m.move()
        run(m, 0.2f)
        val out = m.end()
        assertTrue(out.moved)
        assertFalse(out.tap)
    }

    @Test
    fun heartsRiseABeatApartAndNotBeforeHalfASecond() {
        val m = model().also { it.begin(false) }
        val beats = ArrayList<Float>()
        var t = 0f
        repeat(205) {
            t += 1f / 60
            if (m.step(1f / 60) and TouchModel.BEAT != 0) beats += t
        }
        assertEquals(4, beats.size)
        assertEquals(0.85f, beats[0], 0.02f)
        assertEquals(1.7f, beats[1], 0.02f)
    }

    @Test
    fun theWaryCatGetsUpAndLeavesAfterTwoPointSix() {
        val m = model(level = 1).also { it.begin(false) }
        val r = run(m, 3f, watch = listOf(TouchModel.LEFT_SHY, TouchModel.BEAT))
        assertEquals(2.6f, r.firstAt[TouchModel.LEFT_SHY]!!, 0.03f)
        assertEquals(1.6f, r.firstAt[TouchModel.BEAT]!!, 0.03f) // its hearts come slower
        assertEquals(TouchModel.Phase.LEFT, m.phase)
        val out = m.end()
        assertFalse("it left: no pet counted", out.counts)
        assertTrue(out.leftShy)
    }

    @Test
    fun aStrangerStartlesAtOnceAndNothingCounts() {
        val m = model(level = 0)
        val flags = m.begin(asleep = false)
        assertTrue(flags and TouchModel.STARTLE != 0)
        assertEquals(TouchModel.Phase.LEFT, m.phase)
        run(m, 2f)
        val out = m.end()
        assertFalse(out.counts)
        assertEquals(0f, out.saturation, 0f)
    }

    @Test
    fun theEarsGoBackAtWarnAndTheCatLeavesPastMax() {
        val m = model(satMul = 1.3f, trust = 0f).also { it.begin(false) }
        val r = run(m, 6f, watch = listOf(TouchModel.WARNED, TouchModel.LEFT_OVER))
        val warn = r.firstAt[TouchModel.WARNED]!!
        val leave = r.firstAt[TouchModel.LEFT_OVER]!!
        // from 0.156 at 0.146 a second: (0.65 - 0.156) / 0.146 and (0.85 - 0.156) / 0.146
        assertEquals(3.38f, warn, 0.05f)
        assertEquals(4.75f, leave, 0.05f)
        assertTrue(warn < leave)
        val out = m.end()
        assertTrue(out.over)
        assertFalse("a cat that left is not a pet", out.counts)
    }

    @Test
    fun goingOnLongAfterTheEarsWentBackIsIgnoringTheCat() {
        // a calm, trusting cat takes long to go from warned to done: over 1.5 s
        val m = model(satMul = 0.8f, trust = 1f).also { it.begin(false) }
        run(m, 20f)
        assertTrue(m.end().ignored)
        val quick = model(satMul = 1.3f, trust = 0f).also { it.begin(false) }
        run(quick, 20f)
        assertFalse("1.4 s between the ears and the exit", quick.end().ignored)
    }

    @Test
    fun theThresholdsFallInTheSameFrameAtSixtyAndAHundredTwentyHertz() {
        val watch =
            listOf(
                TouchModel.BEAT,
                TouchModel.WARNED,
                TouchModel.LEFT_OVER,
                TouchModel.PURR_CHANGED,
            )
        val at60 = run(model(satMul = 1.3f, trust = 0f).also { it.begin(false) }, 5f, 60, watch)
        val at120 = run(model(satMul = 1.3f, trust = 0f).also { it.begin(false) }, 5f, 120, watch)
        for (w in watch) {
            assertTrue("$w came", w in at60.firstAt && w in at120.firstAt)
            assertTrue(
                "$w: ${at60.firstAt[w]} vs ${at120.firstAt[w]}",
                abs(at60.firstAt[w]!! - at120.firstAt[w]!!) <= 1f / 60 + 1e-4f,
            )
        }
    }

    @Test
    fun theHoldIsTheSameWhateverTheFrameRate() {
        val a = model().also { it.begin(false) }
        val b = model().also { it.begin(false) }
        run(a, 2f, 60)
        run(b, 2f, 120)
        assertEquals(a.saturation, b.saturation, 0.002f)
        assertEquals(a.end().heldMs.toFloat(), b.end().heldMs.toFloat(), 17f)
    }

    @Test
    fun purrGrowsWithTrustAndIsSilentForTheWaryAndTheHungry() {
        fun purrAfter(level: Int, weak: Boolean): Float {
            val m = model(level = level, weak = weak).also { it.begin(false) }
            run(m, 0.6f)
            return m.purr
        }
        assertEquals(0f, purrAfter(1, false), 0f)
        assertEquals(0.5f, purrAfter(2, false), 0f)
        assertEquals(0.75f, purrAfter(3, false), 0f)
        assertEquals(0.95f, purrAfter(4, false), 0f)
        assertEquals(0.95f, purrAfter(5, false), 0f)
        assertEquals("a hungry cat does not purr", 0f, purrAfter(4, true), 0f)
        val early = model().also { it.begin(false) }
        run(early, 0.4f)
        assertEquals("not before 0.45 s", 0f, early.purr, 0f)
    }

    @Test
    fun aWarnedCatPurrsThinlyOrNotAtAll() {
        val m = model(satMul = 1.3f, trust = 0f).also { it.begin(false) }
        run(m, 3.8f) // past the warning
        assertTrue(m.warned)
        assertEquals(0.25f, m.purr, 0f)
    }

    @Test
    fun aSleeperWhoTrustsYouStaysAsleepAndNeverLeaves() {
        val m = model(level = 3).also { assertEquals(0, it.begin(asleep = true)) }
        assertTrue(m.keepSleep)
        val r = run(m, 120f, watch = listOf(TouchModel.LEFT_OVER, TouchModel.WARNED))
        assertFalse("never leaves", TouchModel.LEFT_OVER in r.firstAt)
        assertTrue("saturation tops out", m.saturation <= 1.2f)
        assertEquals(TouchModel.Phase.HELD, m.phase)
        val out = m.end()
        assertTrue(out.keepSleep)
        assertTrue(out.counts)
    }

    @Test
    fun aSleepersSaturationRisesSlower() {
        val awake = model().also { it.begin(false) }
        val asleep = model().also { it.begin(true) }
        run(awake, 2f)
        run(asleep, 2f)
        assertTrue(asleep.saturation < awake.saturation)
        assertEquals(0.3f, asleep.purr, 0f)
    }

    @Test
    fun aLighterSleeperIsWokenAndLosesTheSleep() {
        val m = model(level = 2)
        val flags = m.begin(asleep = true)
        assertTrue(flags and TouchModel.WOKE != 0)
        assertFalse(m.keepSleep)
    }

    @Test
    fun saturationWearsOffByAFifteenthOfAPointPerMinuteAndTheScreenTakesThePagesNumber() {
        val m = model().also { it.begin(false) }
        run(m, 4f)
        m.end()
        val before = m.saturation
        m.seed(0f) // the page's number just after a touch is behind: not taken
        assertEquals(before, m.saturation, 0f)
        run(m, 60f)
        assertEquals(before - 0.15f, m.saturation, 0.004f)
        m.seed(0.5f)
        assertEquals(0.5f, m.saturation, 0f)
        m.seed(9f)
        assertEquals(1.2f, m.saturation, 0f)
    }

    @Test
    fun theFourthTouchInThreeSecondsIsSpamOnceInTwentySeconds() {
        val m = model()
        val spam = ArrayList<Boolean>()
        repeat(5) {
            spam += m.begin(false) and TouchModel.SPAM != 0
            run(m, 0.1f)
            m.end()
            run(m, 0.3f)
        }
        assertEquals(listOf(false, false, false, true, false), spam)
        run(m, 21f) // the twenty quiet seconds pass
        val again = ArrayList<Boolean>()
        repeat(4) {
            again += m.begin(false) and TouchModel.SPAM != 0
            run(m, 0.1f)
            m.end()
            run(m, 0.3f)
        }
        assertEquals(listOf(false, false, false, true), again)
    }

    @Test
    fun theLevelAThingBeganAtHoldsForTheTouch() {
        val m = model(level = 2).also { it.begin(false) }
        m.profile(5, false, 1f, 0.5f)
        assertEquals(2, m.level)
        m.end()
        m.profile(5, false, 1f, 0.5f)
        assertEquals(5, m.level)
    }
}
