package com.lifemax.rpg.feature.habits

import com.lifemax.rpg.feature.habits.domain.WeatherState
import com.lifemax.rpg.feature.habits.domain.Wind
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The street's wind: the same at the same time and place, never more than its most, gusts that
 * reach what stands downwind later, a spring that lags and overshoots its push, stronger under a
 * grey sky and in the rain, calm in a mist, and still with motion off.
 */
class WindTest {
    private val clear = WeatherState.CLEAR
    private val rain = WeatherState(.9f, .8f, .8f, 0f)
    private val mist = WeatherState(.3f, 0f, 0f, 1f)

    private fun at(seconds: Float, w: WeatherState = clear) = Wind().apply { update(seconds, w) }

    @Test
    fun theSameMomentIsTheSameWind() {
        val a = Wind()
        val b = Wind()
        for (k in 0 until 400) {
            val t = 3.7f + k * .37f
            a.update(t, rain)
            b.update(t, rain)
            for (x in floatArrayOf(-900f, 0f, 120f, 2500f)) {
                assertEquals(a.force(x), b.force(x), 0f)
                assertEquals(a.flutter(x, 9, 2f), b.flutter(x, 9, 2f), 0f)
                assertEquals(a.swing(x, 2.5f, .2f), b.swing(x, 2.5f, .2f), 0f)
                assertEquals(a.lagged(.3f), b.lagged(.3f), 0f)
            }
        }
        // reading one place does not change what another reads
        a.update(50f, rain)
        val first = a.swing(10f, 2f, .2f)
        a.swing(900f, 1.3f, .4f)
        assertEquals(first, a.swing(10f, 2f, .2f), 0f)
    }

    @Test
    fun itIsBounded() {
        val w = Wind()
        for (k in 0 until 4000) {
            w.update(.5f + k * .173f, rain)
            val f = w.force(k * 7f)
            assertTrue("force $f", f in 0f..Wind.MAX)
            assertTrue(abs(w.flutter(k * 7f, k, 2.4f)) <= Wind.MAX)
        }
        for (k in 0 until 600) {
            w.update(1f + k * .61f, rain)
            val s = w.swing(k * 3f, 2.6f, .2f)
            // a spring overshoots its push, but not by much
            assertTrue("swing $s", s > -.6f && s < Wind.MAX * 1.6f)
        }
    }

    @Test
    fun aGustReachesWhatStandsDownwindLater() {
        val near = 0f
        val far = 330f
        val delay = (far - near) / Wind.SPEED
        val w = Wind()
        var biggest = 0f
        for (k in 0 until 2000) {
            val t = 20f + k * .1f
            w.update(t, rain)
            val here = w.force(near)
            // what the air does here now, it does downwind a moment later
            w.update(t + delay, rain)
            assertEquals(here, w.force(far), 1e-4f)
            biggest = maxOf(biggest, here)
        }
        // and there are gusts to see: the push varies by far more than its eddies
        assertTrue("gusts reach $biggest", biggest > .9f)
        // the rise of a gust: find where the push here first climbs past a level, then check
        // downwind is still below it at that moment and crosses it the delay later
        var rise = -1f
        var prev = 0f
        for (k in 0 until 4000) {
            val t = 20f + k * .05f
            w.update(t, rain)
            val f = w.force(near)
            if (prev < .9f && f >= .9f) {
                rise = t
                break
            }
            prev = f
        }
        assertTrue(rise > 0f)
        w.update(rise, rain)
        assertTrue(w.force(far) < .9f)
        w.update(rise + delay, rain)
        assertTrue(w.force(far) >= .9f - 1e-3f)
    }

    @Test
    fun aSpringLagsItsPushAndRuns() {
        // the push and a slow spring over a stormy minute: the spring follows, late
        val w = Wind()
        val n = 1200
        val push = FloatArray(n)
        val spring = FloatArray(n)
        for (k in 0 until n) {
            w.update(30f + k * .05f, rain)
            push[k] = w.force(0f)
            spring[k] = w.swing(0f, 2.6f, .2f)
        }
        // best match at a delay, not at none
        fun mismatch(d: Int): Float {
            var sum = 0f
            for (k in 200 until n - 40) sum += abs(spring[k] - push[k - d])
            return sum
        }
        val best = (0 until 40).minByOrNull { mismatch(it) }!!
        assertTrue("lag of $best steps", best >= 2)
        // and it is not the push itself: it moves in its own way
        assertTrue(mismatch(0) > 1f)
        // its trail is where it stood: the latest step is now, earlier ones lag
        w.update(57f, rain)
        val now = w.swing(0f, 2.6f, .2f)
        assertEquals(now, w.lagged(0f), 1e-5f)
    }

    @Test
    fun theWeatherSetsHowWindyItIs() {
        assertTrue(Wind.levelOf(rain) > Wind.levelOf(clear))
        assertTrue(Wind.levelOf(WeatherState(1f, 0f, 0f, 0f)) > Wind.levelOf(clear))
        assertTrue(Wind.levelOf(mist) < Wind.levelOf(clear) * .4f)
        fun mean(w: WeatherState): Float {
            var sum = 0f
            for (k in 0 until 3000) sum += at(5f + k * .21f, w).force(0f)
            return sum / 3000
        }
        val calm = mean(clear)
        val stormy = mean(rain)
        val misty = mean(mist)
        assertTrue("clear $calm rain $stormy", stormy > calm * 2f)
        assertTrue("clear $calm mist $misty", misty < calm * .4f)
        // a clear day is a breeze: gentle on average
        assertTrue("clear $calm", calm in .05f..0.4f)
    }

    @Test
    fun itIsStillWithMotionOff() {
        for (w in listOf(clear, rain, mist)) {
            val wind = at(0f, w)
            assertTrue(!wind.moving)
            for (x in floatArrayOf(-300f, 0f, 77f, 4000f)) {
                assertEquals(0f, wind.force(x), 0f)
                assertEquals(0f, wind.flutter(x, 3, 2f), 0f)
                assertEquals(0f, wind.swing(x, 2f, .2f), 0f)
                assertEquals(0f, wind.lagged(.4f), 0f)
                assertEquals(0f, wind.drift(x, 1f, 5f), 0f)
            }
        }
    }

    @Test
    fun whatTheAirCarriesOnlyGrowsAndMovesSmoothly() {
        val w = Wind()
        var last = 0f
        for (k in 1 until 400) {
            val t = 40f + k * .02f
            w.update(t, rain)
            val d = w.drift(25f, 40f, t)
            assertTrue(d >= last - 1e-5f)
            // no more than the strongest push could carry in a frame
            assertTrue(d - last <= Wind.MAX * .02f + 1e-4f)
            last = d
        }
        assertTrue(last > 0f)
    }
}
