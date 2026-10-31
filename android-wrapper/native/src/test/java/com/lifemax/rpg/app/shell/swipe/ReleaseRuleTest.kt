package com.lifemax.rpg.app.shell.swipe

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseRuleTest {
    private data class Row(
        val why: String,
        val wall: Boolean,
        val latched: Boolean,
        val u: Float,
        val dp: Float,
        val expect: Release,
    )

    private val table =
        listOf(
            Row("a wall never commits, latched or flung", true, true, 0.6f, 3000f, Release.Cancel),
            Row("a wall never commits on a fling", true, false, 0.3f, 3000f, Release.Cancel),
            Row("a back-fling cancels even when latched", false, true, 0.6f, -600f, Release.Cancel),
            Row("just slower than a back-fling", false, true, 0.6f, -599f, Release.CommitDrag),
            Row("latched and held still", false, true, 0.5f, 0f, Release.CommitDrag),
            Row("latched near the release point", false, true, 0.40f, 300f, Release.CommitDrag),
            Row("a fling past the least travel", false, false, 0.30f, 1200f, Release.CommitFling),
            Row("a fling at the least travel", false, false, 0.12f, 1200f, Release.CommitFling),
            Row("a fling with too little travel", false, false, 0.11f, 3000f, Release.Cancel),
            Row("slow, just under the fling speed", false, false, 0.40f, 1199f, Release.Cancel),
            Row("a casual flick springs back", false, false, 0.44f, 900f, Release.Cancel),
            Row("released short of the catch", false, false, 0.44f, 0f, Release.Cancel),
            Row("flung backward", false, false, 0.30f, -2000f, Release.Cancel),
        )

    @Test
    fun theReleaseRuleTable() {
        for (row in table) {
            assertEquals(
                row.why,
                row.expect,
                ReleaseRule.decide(row.wall, row.latched, row.u, row.dp, SwipeTune.DEFAULT),
            )
        }
    }

    @Test
    fun theTunedThresholdsApply() {
        val firm = SwipeTune(flingDp = 2000f, flingMin = 0.2f, backFlingDp = 300f)
        assertEquals(Release.Cancel, ReleaseRule.decide(false, false, 0.3f, 1500f, firm))
        assertEquals(Release.Cancel, ReleaseRule.decide(false, false, 0.15f, 2500f, firm))
        assertEquals(Release.CommitFling, ReleaseRule.decide(false, false, 0.2f, 2000f, firm))
        assertEquals(Release.Cancel, ReleaseRule.decide(false, true, 0.6f, -300f, firm))
    }

    @Test
    fun aCommitSaysHowItGotThere() {
        assertEquals(Via.Drag, Release.CommitDrag.via)
        assertEquals(Via.Fling, Release.CommitFling.via)
        assertEquals(null, Release.Cancel.via)
        assertEquals("fling", Via.Fling.wire)
    }
}
