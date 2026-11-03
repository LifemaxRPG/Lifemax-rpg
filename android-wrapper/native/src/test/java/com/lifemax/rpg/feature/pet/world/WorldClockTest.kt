package com.lifemax.rpg.feature.pet.world

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The world clock: the same sub-steps at the same simulated moments whatever the frame rate (60,
 * 120, jittered, a two-second stall), a frame time reported twice or out of order runs nothing, a
 * think quantum every 30 sub-steps, wall time and local days from the clock alone.
 */
class WorldClockTest {
    private val start = 1_793_361_600_000L // 30 Oct 2026 12:00 UTC

    /**
     * Steps ten simulated seconds with frames [frameNanos] long; records (step, epoch ms) pairs.
     */
    private fun run(frames: Sequence<Long>): List<Pair<Long, Long>> {
        val src = FakeTimeSource(mono = 5_000_000_000L, epoch = start)
        val clock = WorldClock(src)
        val seen = ArrayList<Pair<Long, Long>>()
        var mono = src.mono
        for (dt in frames) {
            mono += dt
            clock.advanceTo(mono) { step, _ -> seen += step to clock.epochMs() }
            if (mono - src.mono >= TEN_S) break
        }
        return seen
    }

    @Test
    fun anyFrameRateGivesTheSameSubSteps() {
        val at120 = run(generateSequence { 8_333_333L })
        val at60 = run(generateSequence { 16_666_667L })
        var seed = 7L
        val jittered =
            run(
                generateSequence {
                    seed = (seed * 6364136223846793005L + 1442695040888963407L)
                    11_111_111L + Math.floorMod(seed ushr 33, 22_222_222L)
                }
            )
        val stall = run(sequenceOf(8_333_333L, 2_000_000_000L) + generateSequence { 8_333_333L })
        val n = 1150
        for (other in listOf(at60, jittered, stall)) assertEquals(at120.take(n), other.take(n))
        assertEquals((1L..n).toList(), at120.take(n).map { it.first })
        assertEquals(start + 1000, at120.first { it.first == 120L }.second)
    }

    @Test
    fun aRepeatedOrOlderFrameTimeRunsNothing() {
        val src = FakeTimeSource(mono = 1_000L, epoch = start)
        val clock = WorldClock(src)
        assertEquals(12, clock.advanceTo(1_000L + 100_000_000L))
        assertEquals(0, clock.advanceTo(1_000L + 100_000_000L))
        assertEquals(0, clock.advanceTo(1_000L + 50_000_000L))
        assertEquals(12L, clock.subSteps)
    }

    @Test
    fun aThinkQuantumEveryThirtySubSteps() {
        val clock = WorldClock(FakeTimeSource(epoch = start))
        val thinks = ArrayList<Long>()
        clock.advanceTo(1_000_000_000L) { step, think -> if (think) thinks += step }
        assertEquals(listOf(30L, 60L, 90L, 120L), thinks)
    }

    @Test
    fun aLongStallIsLeftToTheCatchUp() {
        val clock = WorldClock(FakeTimeSource(epoch = start))
        assertEquals(WorldClock.MAX_LIVE_STEPS, clock.advanceTo(60_000_000_000L))
        assertEquals(start + 2000, clock.epochMs())
        assertEquals(1, clock.advanceTo(60_000_000_000L + 8_333_334L))
        val gap = clock.anchorTo(61_000_000_000L, start + 61_000)
        assertEquals(61_000L - 2008, gap)
        assertEquals(start + 61_000, clock.epochMs())
    }

    @Test
    fun wallTimeLocalHoursAndDaysComeFromTheClock() {
        val clock =
            WorldClock(
                FakeTimeSource(epoch = start),
                zoneOffsetMin = 13 * 60,
                clockOffsetMs = 3_600_000L,
            )
        assertEquals(start + 3_600_000L, clock.epochMs())
        assertEquals(
            2.0,
            clock.hourOfDay(),
            1e-9,
        ) // 12:00 UTC + 1 h shift + 13 h zone = 02:00 next day
        assertEquals("2026-10-31", clock.dayKey())
        val west = WorldClock(FakeTimeSource(epoch = start), zoneOffsetMin = -13 * 60)
        assertEquals("2026-10-29", west.dayKey())
        assertEquals(23.0, west.hourOfDay(), 1e-9)
    }

    private companion object {
        const val TEN_S = 10_000_000_000L
    }
}
