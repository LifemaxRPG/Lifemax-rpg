package com.lifemax.rpg.feature.pet.host

import com.lifemax.rpg.feature.pet.domain.PetBar
import com.lifemax.rpg.feature.pet.domain.PetInner
import com.lifemax.rpg.feature.pet.domain.PetMind
import com.lifemax.rpg.feature.pet.presentation.PanelSource
import com.lifemax.rpg.feature.pet.world.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The mind row and inner life read the page's projection unless the cat world is active, then the
 * world's own mood, threads, comfort place and away story (plan 2.8).
 */
class PanelSourceTest {
    private val mind = PetMind("bold", "Bold", "calm", "", "Ted is fine.", "")
    private val inner =
        PetInner(
            lead = "lead",
            body = "body",
            quirksTitle = "q",
            memoriesTitle = "m",
            threads =
                listOf(
                    PetBar("Familiarity", 10, "page word"),
                    PetBar("Trust", 20, "page word"),
                    PetBar("Security", 30, "page word"),
                    PetBar("Independence", 40, "page word"),
                ),
            quirks = emptyList(),
            memories = emptyList(),
            stats = "stats",
            comfort = "page comfort",
        )

    @Test
    fun withoutAWorldThePageIsTheSource() {
        val rig = WorldRig(cats = 1) // no block yet: the host is inactive
        assertSame(mind, PanelSource.mind(mind, 0, rig.host))
        assertSame(inner, PanelSource.inner(inner, 0, rig.host))
        assertEquals(null, PanelSource.mind(null, 0, rig.host))
    }

    @Test
    fun withAWorldTheMoodThreadsComfortAndAwayComeFromIt() {
        val rig = WorldRig(cats = 1)
        rig.project()
        rig.host.attach()
        rig.minutes(1)
        val m = PanelSource.mind(mind, 0, rig.host)!!
        assertEquals("Bold", m.styleName) // the page's words stay
        assertEquals("Ted is fine.", m.text)
        // the fresh cat's imported mood (v 0.2, a -0.1) is content
        assertEquals("content", m.mood)
        val i = PanelSource.inner(inner, 0, rig.host)!!
        assertEquals(listOf(40, 40, 40, 70), i.threads.map { it.pct })
        assertEquals(listOf("Growing", "Growing", "Growing", "Strong"), i.threads.map { it.word })
        assertEquals(inner.threads.map { it.label }, i.threads.map { it.label })
        assertEquals("page comfort", i.comfort) // the fresh cat has no favourite place yet
        assertEquals("stats", i.stats)
    }

    @Test
    fun theAwayStoryReachesTheMindRow() {
        val rig = WorldRig(cats = 1)
        rig.project()
        val view = rig.host.attach()
        rig.minutes(1)
        rig.host.detach(view)
        rig.time.advanceMs(3 * 3_600_000L)
        rig.host.attach()
        rig.frame()
        val m = PanelSource.mind(mind, 0, rig.host)!!
        assertEquals("While you were away", m.awayTitle)
        assertEquals(rig.host.awayStory()!!.lines.joinToString(" "), m.away)
    }

    @Test
    fun theMoodWordsFollowValenceAndArousal() {
        assertEquals("content", PanelSource.moodWord(Mood(0.25, -0.1)))
        assertEquals("playful", PanelSource.moodWord(Mood(0.6, 0.5)))
        assertEquals("bright", PanelSource.moodWord(Mood(0.6, 0.15)))
        assertEquals("drowsy", PanelSource.moodWord(Mood(0.0, -0.6)))
        assertEquals("uneasy", PanelSource.moodWord(Mood(-0.4, 0.0)))
        assertEquals("overstimulated", PanelSource.moodWord(Mood(-0.5, 0.6)))
        assertEquals("calm", PanelSource.moodWord(Mood(0.05, 0.0)))
    }
}
