package com.lifemax.rpg.feature.pet.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The owner's "the 3D room looks flat": the permanent view angle gives the layers a minimum offset
 * at rest, the guard keeps the strongest level under a normal phone's frame costs (also with the
 * display running slower than its nominal rate) and only drops under a sustained overload.
 */
class ViewAngleTest {
    private var now = 0L

    private fun guard() = DepthGuard(LiveDepthTuning.BEST_LEVEL) { now }

    /** [n] frames of a phone: [workMs] of main-thread work, a frame every [intervalMs]. */
    private fun DepthGuard.frames(n: Int, workMs: Float, intervalMs: Float, budgetMs: Float) {
        repeat(n) {
            now += intervalMs.toLong().coerceAtLeast(1)
            record(workMs, intervalMs, budgetMs)
        }
    }

    // ------------------------------------------------------------------ the skew in pixels

    @Test
    fun theViewAngleOffsetsTheNearLayersFromTheWallByAtLeastFivePixelsAtRich() {
        val o = LayerOffsets.at(DepthLevel.RICH)
        println("RICH at rest, view angle ${LiveDepthTuning.VIEW_ANGLE}: $o")
        assertTrue("wall at least 1 px: $o", o.wall >= 1)
        assertTrue("near row against the wall at least 5 px: $o", o.between >= 5)
        val mid = Parallax.shiftAtRow(LiveDepthTuning.VIEW_ANGLE, 130f, DepthLevel.RICH)
        assertTrue("mid floor ${mid - o.wall} px from the wall", mid - o.wall >= 2)
    }

    @Test
    fun everyLevelOffsetsItsLayersAtRestAndRichMostOfAll() {
        val offsets = DepthLevel.entries.map { LayerOffsets.at(it).between }
        println("offset near-wall at rest, SUBTLE MEDIUM RICH: $offsets")
        assertTrue(offsets.all { it >= 3 })
        assertEquals(offsets.sorted(), offsets)
    }

    @Test
    fun onA1080By2340PhoneTheOffsetIsMoreThanTwentyFiveDevicePixels() {
        // the full-screen room draws 9 device pixels per room pixel there, the card 5
        val between = LayerOffsets.at(DepthLevel.RICH).between
        println("device pixels near-wall: room ${between * 9}, card ${between * 5}")
        assertTrue(between * 5 >= 25)
        assertTrue(between * 9 >= 45)
    }

    @Test
    fun theViewAngleAndThePanAtTheHouseEndStayInsideTheSwayRange() {
        assertTrue(LiveDepthTuning.VIEW_ANGLE + PanTuning.POSITION_GAIN <= 1f + 1e-6f)
    }

    // ------------------------------------------------------------------ the sway

    @Test
    fun aCameraAtRestStaysOnTheViewAngleAndDrawsTheSameValuesForever() {
        val s = DepthSway(false, 0f, LiveDepthTuning.VIEW_ANGLE)
        assertEquals(LiveDepthTuning.VIEW_ANGLE, s.shown, 0f)
        repeat(120 * 30) {
            s.follow(PanTuning.CENTRE_X)
            s.step(1f / 120f)
            assertEquals("held still on the angle", LiveDepthTuning.VIEW_ANGLE, s.shown, 0f)
            assertEquals(LiveDepthTuning.VIEW_ANGLE, s.shownIdle, 0f)
        }
    }

    @Test
    fun reducedMotionKeepsTheRestPicture() {
        val s = DepthSway(true, 0f, LiveDepthTuning.VIEW_ANGLE)
        repeat(240) {
            s.follow(PanTuning.CENTRE_X + 100f)
            s.step(1f / 120f)
        }
        assertEquals(0f, s.shown, 0f)
        assertTrue(RoomStyle.live(DepthLevel.RICH, true, 0.6f).reducedMotion)
    }

    @Test
    fun theLiveStyleCarriesTheAngleAndTheOldStyleDoesNot() {
        assertEquals(0.6f, RoomStyle.live(DepthLevel.RICH, false, 0.6f).viewAngle, 0f)
        assertEquals(0f, RoomStyle.live(DepthLevel.RICH).viewAngle, 0f)
        assertEquals(DepthLevel.RICH, LiveDepthTuning.BEST_LEVEL)
    }

    // ------------------------------------------------------------------ the guard

    @Test
    fun aNormalPhoneAt120HzKeepsRichForAnHour() {
        val g = guard()
        // 3 ms of room work per 8.3 ms frame, with now and then a 12 ms frame
        repeat(120 * 3600 / 100) {
            g.frames(99, 3f, 8.3f, 8.3f)
            g.frames(1, 12f, 8.3f, 8.3f)
        }
        assertEquals(DepthLevel.RICH, g.level)
        assertEquals(0, g.changes)
    }

    @Test
    fun aPhoneWhoseAdaptiveRefreshRunsTheScreenSlowerThanNominalKeepsRich() {
        // nominal 120 Hz (budget 8.3 ms) but the system shows 60, 48 or 30 frames a second
        for (interval in floatArrayOf(16.7f, 20.8f, 33.3f)) {
            now = 0
            val g = guard()
            g.frames(60 * 600, 3f, interval, 8.3f)
            assertEquals("interval $interval ms", DepthLevel.RICH, g.level)
            assertEquals(0, g.changes)
        }
    }

    @Test
    fun theFirstFramesWhileTheRoomMakesItsPicturesAreIgnored() {
        val g = guard()
        g.frames(GuardTuning.WARMUP_FRAMES, 60f, 60f, 8.3f)
        g.frames(2000, 3f, 8.3f, 8.3f)
        assertEquals(DepthLevel.RICH, g.level)
    }

    @Test
    fun aSustainedOverloadStepsDownOneLevelAtATimeAndKeepsSubtleUntilItIsHopeless() {
        val g = guard()
        g.frames(2000, 14f, 16.7f, 16.7f) // 84 % of a frame: too slow for RICH and MEDIUM
        assertEquals(DepthLevel.SUBTLE, g.level)
        assertEquals(2, g.changes)
        g.frames(5000, 14f, 16.7f, 16.7f)
        assertEquals("SUBTLE is the floor while the room can be drawn", DepthLevel.SUBTLE, g.level)
        g.frames(5000, 24f, 33f, 16.7f) // the room's own work longer than a frame
        assertNull("only a phone that cannot draw the room goes flat", g.level)
        assertFalse(g.reason.isEmpty())
    }

    @Test
    fun theGuardSaysWhyInAFewWords() {
        val g = guard()
        assertTrue(g.reason.startsWith("start"))
        g.frames(2000, 14f, 16.7f, 16.7f)
        println(g.reason)
        assertTrue(g.reason, g.reason.startsWith("down: load"))
    }

    @Test
    fun afterAnOverloadItClimbsBackToTheStrongestLevelOnItsOwn() {
        val g = guard()
        g.frames(2000, 14f, 16.7f, 16.7f)
        assertEquals(DepthLevel.SUBTLE, g.level)
        g.frames(60 * 60 * 5, 2f, 16.7f, 16.7f)
        assertEquals(DepthLevel.RICH, g.level)
    }
}
