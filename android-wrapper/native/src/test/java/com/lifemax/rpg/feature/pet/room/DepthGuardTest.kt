package com.lifemax.rpg.feature.pet.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The performance guard as pure logic with a fake clock: it steps the depth down MEDIUM, SUBTLE,
 * off when frames run long, ignores warm-up and spikes, keeps a hysteresis band between down and
 * up, and steps up only after a long calm (with a back-off if the step up does not hold).
 */
class DepthGuardTest {
    private var now = 0L
    private val budget = 1000f / 60f

    private fun guard(best: DepthLevel = DepthLevel.MEDIUM) = DepthGuard(best) { now }

    /** Feed [frames] frames of [cost] budgets each, a frame of the budget's length apart. */
    private fun DepthGuard.feed(frames: Int, cost: Float, hz: Int = 60) {
        val ms = 1000f / hz
        repeat(frames) {
            now += ms.toLong().coerceAtLeast(1)
            record(cost * ms, ms, ms)
        }
    }

    private val warm = GuardTuning.WARMUP_FRAMES
    private val window = GuardTuning.WINDOW

    @Test
    fun itStartsAtMediumAndStaysThereWhileFramesAreFast() {
        val g = guard()
        g.feed(3000, 0.3f)
        assertEquals(DepthLevel.MEDIUM, g.level)
        assertEquals(0, g.changes)
    }

    @Test
    fun slowFramesStepDownToSubtleThenOffAndNoFurther() {
        val g = guard()
        g.feed(warm + window - 1, 1.2f)
        assertEquals("the window is not full yet", DepthLevel.MEDIUM, g.level)
        g.feed(1, 1.2f)
        assertEquals(DepthLevel.SUBTLE, g.level)
        // the hold, then the window refills with the new level's frames
        g.feed(HOLD_FRAMES + warm + window, 1.2f)
        assertNull(g.level)
        g.feed(3000, 1.5f)
        assertNull(g.level)
        assertEquals(2, g.changes)
    }

    @Test
    fun theFirstFramesAreNotCounted() {
        val g = guard()
        g.feed(warm, 8f) // the room making its pictures
        g.feed(window, 0.3f)
        assertEquals(DepthLevel.MEDIUM, g.level)
    }

    @Test
    fun aFewLongFramesAreNotATrend() {
        val g = guard()
        g.feed(warm, 0.3f)
        repeat(20) {
            g.feed(80, 0.3f)
            g.feed(1, 6f) // a stall now and then
        }
        assertEquals(DepthLevel.MEDIUM, g.level)
    }

    @Test
    fun aFrameOnlyCountsSixBudgets() {
        val g = guard()
        g.feed(warm, 0.3f)
        g.feed(window - 1, 0.3f)
        g.record(10_000f, 10_000f, budget) // an hour-long stall: one frame
        assertEquals(DepthLevel.MEDIUM, g.level)
    }

    @Test
    fun aStalledFrameCostsItsOverrunEvenWithLittleWork() {
        val g = guard()
        g.feed(warm, 0.3f)
        // little work, but every frame lasts four budgets: the phone drops frames. (A frame of two
        // or three budgets is the system's adaptive refresh rate, not a slow room: see
        // ViewAngleTest.)
        repeat(window) {
            now += 66
            g.record(2f, 4 * budget, budget)
        }
        assertEquals(DepthLevel.SUBTLE, g.level)
    }

    @Test
    fun theBudgetIsTheDisplaysButNeverTighterThan60Hz() {
        // 7 ms of work is a normal cost of the room on any display; 11 ms is not
        for (budgetMs in floatArrayOf(16.7f, 8.3f)) {
            val ok = guard()
            ok.feed(warm + window, 0f)
            repeat(3 * window) {
                now += 8
                ok.record(7f, budgetMs, budgetMs)
            }
            assertEquals("7 ms on a $budgetMs ms frame", DepthLevel.MEDIUM, ok.level)
            val slow = guard()
            slow.feed(warm + window, 0f)
            repeat(3 * window) {
                now += 8
                slow.record(11f, budgetMs, budgetMs)
            }
            assertEquals("11 ms on a $budgetMs ms frame", DepthLevel.SUBTLE, slow.level)
        }
    }

    @Test
    fun betweenTheThresholdsNothingChangesEitherWay() {
        val g = guard()
        g.feed(warm + window, 1.2f)
        assertEquals(DepthLevel.SUBTLE, g.level)
        g.feed(HOUR_FRAMES, 0.45f) // neither slow enough to drop nor calm enough to rise
        assertEquals(DepthLevel.SUBTLE, g.level)
        assertEquals(1, g.changes)
    }

    @Test
    fun itStepsBackUpOnlyAfterALongCalmAndOneLevelAtATime() {
        val g = guard()
        g.feed(warm + window, 1.2f)
        g.feed(HOLD_FRAMES + warm + window, 1.2f)
        assertNull(g.level)
        val start = now
        g.feed(CALM_FRAMES - 600, 0.2f)
        assertTrue("not yet: ${now - start} ms", now - start < GuardTuning.CALM_MS)
        assertNull(g.level)
        g.feed(1200, 0.2f)
        assertEquals("one step up after the calm", DepthLevel.SUBTLE, g.level)
        g.feed(warm + window + HOLD_FRAMES, 0.2f)
        assertEquals("and not another at once", DepthLevel.SUBTLE, g.level)
        g.feed(CALM_FRAMES + 600, 0.2f)
        assertEquals(DepthLevel.MEDIUM, g.level)
    }

    @Test
    fun aBusyMomentRestartsTheCalm() {
        val g = guard()
        g.feed(warm + window, 1.2f)
        assertEquals(DepthLevel.SUBTLE, g.level)
        g.feed(CALM_FRAMES - 3000, 0.2f)
        g.feed(window, 0.45f) // not slow, not calm
        g.feed(CALM_FRAMES - 3000, 0.2f)
        assertEquals("the calm started again", DepthLevel.SUBTLE, g.level)
    }

    @Test
    fun aStepUpThatDoesNotHoldDoublesTheCalmNeeded() {
        val g = guard()
        g.feed(warm + window, 1.2f)
        g.feed(CALM_FRAMES + 1200, 0.2f)
        assertEquals(DepthLevel.MEDIUM, g.level)
        // slow again within the probation: down, and the next calm is twice as long
        g.feed(60, 1.2f) // enough slow frames to tip the average (above 0.6), no more
        assertEquals(DepthLevel.SUBTLE, g.level)
        g.feed(CALM_FRAMES + 1200, 0.2f)
        assertEquals("one calm is no longer enough", DepthLevel.SUBTLE, g.level)
        g.feed(CALM_FRAMES + 1200, 0.2f)
        assertEquals(DepthLevel.MEDIUM, g.level)
    }

    /**
     * The room-shake check (PET_TAB_INVENTORY.md): every change of level redraws the room at other
     * gains, a visible jump, so a phone on the edge (too slow at MEDIUM, fast enough at SUBTLE)
     * must not make the room flick between levels. In half an hour it tries MEDIUM again three
     * times, each time after a longer calm, and no two changes are closer than the hold.
     */
    @Test
    fun aPhoneOnTheEdgeSettlesInsteadOfFlickeringBetweenLevels() {
        val g = guard()
        var last = Long.MIN_VALUE
        var closest = Long.MAX_VALUE
        repeat(HOUR_FRAMES / 2) {
            val before = g.level
            g.feed(1, if (g.level == DepthLevel.MEDIUM) 0.9f else 0.2f)
            if (g.level != before) {
                if (last != Long.MIN_VALUE) closest = minOf(closest, now - last)
                last = now
            }
        }
        assertTrue("changes in half an hour: ${g.changes}", g.changes in 1..14)
        assertTrue("closest changes $closest ms apart", closest >= GuardTuning.HOLD_MS)
    }

    @Test
    fun itNeverRisesAboveTheBestLevel() {
        val g = guard(DepthLevel.SUBTLE)
        g.feed(10 * CALM_FRAMES, 0.1f)
        assertEquals(DepthLevel.SUBTLE, g.level)
    }

    @Test
    fun resumingStartsTheAveragesAfresh() {
        val g = guard()
        g.feed(warm + window - 5, 1.2f)
        g.resume()
        g.feed(warm + window - 1, 1.2f)
        assertEquals(DepthLevel.MEDIUM, g.level)
        g.feed(1, 1.2f)
        assertEquals(DepthLevel.SUBTLE, g.level)
    }

    @Test
    fun theLoadIsTheAverageOnceTheWindowIsFull() {
        val g = guard()
        g.feed(warm, 0f)
        g.feed(window - 1, 0.5f)
        assertFalse(g.load > 0f)
        g.feed(1, 0.5f)
        assertEquals(0.5f, g.load, 0.01f)
    }

    private companion object {
        /** Frames in the hold after a change, at 60 Hz (the fake clock moves 16 ms a frame). */
        const val HOLD_FRAMES = (GuardTuning.HOLD_MS / 16L).toInt() + 1
        const val CALM_FRAMES = (GuardTuning.CALM_MS / 16L).toInt()
        const val HOUR_FRAMES = 3_600_000 / 16
    }
}
