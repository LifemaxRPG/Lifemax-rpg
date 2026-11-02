package com.lifemax.rpg.app.shell.swipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a screen rests: it leaves Shown while not open (CONTRACT A35 item 2). */
class RestRuleTest {
    private fun scene(vararg panes: Pair<String, PaneVisibility>) =
        StageScene(panes.associate { (p, v) -> p to PaneStage(0, v) }, null)

    @Test
    fun leavingShownWhileNotOpenRests() {
        assertTrue(rests(PaneVisibility.Shown, PaneVisibility.Parked, open = false))
        assertTrue(rests(PaneVisibility.Shown, PaneVisibility.Gone, open = false))
    }

    @Test
    fun aSourceStillSlidingOutDoesNotRest() {
        // closed, but still drawn while it slides off: Shown at a moving tx
        assertFalse(rests(PaneVisibility.Shown, PaneVisibility.Shown, open = false))
        val sliding = StageScene(mapOf("history" to PaneStage(-300, PaneVisibility.Shown)), null)
        assertEquals(
            emptySet<String>(),
            sliding.restedSince(scene("history" to PaneVisibility.Shown), emptyList()),
        )
    }

    @Test
    fun anOpenScreenAndOneNeverShownDoNotRest() {
        assertFalse(rests(PaneVisibility.Shown, PaneVisibility.Parked, open = true))
        assertFalse(rests(null, PaneVisibility.Gone, open = false))
        assertFalse(rests(PaneVisibility.Parked, PaneVisibility.Gone, open = false))
    }

    @Test
    fun aSceneNamesThePanesThatRest() {
        val before =
            scene(
                "history" to PaneVisibility.Shown,
                "media" to PaneVisibility.Shown,
                "plan" to PaneVisibility.Parked,
            )
        val after =
            scene(
                "history" to PaneVisibility.Parked,
                "media" to PaneVisibility.Shown,
                "plan" to PaneVisibility.Gone,
            )
        assertEquals(setOf("history"), after.restedSince(before, open = listOf("media")))
    }
}
