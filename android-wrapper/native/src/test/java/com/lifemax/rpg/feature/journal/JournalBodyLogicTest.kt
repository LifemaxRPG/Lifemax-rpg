package com.lifemax.rpg.feature.journal

import com.lifemax.rpg.feature.journal.domain.HeatCell
import com.lifemax.rpg.feature.journal.domain.JournalListItem
import com.lifemax.rpg.feature.journal.domain.WelcomeChip
import com.lifemax.rpg.feature.journal.presentation.body.ChipTap
import com.lifemax.rpg.feature.journal.presentation.body.chipTap
import com.lifemax.rpg.feature.journal.presentation.body.counterLine
import com.lifemax.rpg.feature.journal.presentation.body.counterOn
import com.lifemax.rpg.feature.journal.presentation.body.heatRows
import com.lifemax.rpg.feature.journal.presentation.body.selectedIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The choices the Journal's body makes from the page's words, against the page's own answers. */
class JournalBodyLogicTest {
    private val base = JournalBodyFixtures.today.reflect

    /** jcCounterText(awarded, canPay, n) for stored text, as the page's box shows it. */
    @Test
    fun counterFollowsThePage() {
        val c = base.counter
        assertEquals(c.short, counterLine(base.copy(text = "")))
        assertEquals(c.short, counterLine(base.copy(text = "x".repeat(39))))
        // the page counts trimmed characters
        assertEquals(c.short, counterLine(base.copy(text = "  " + "x".repeat(39) + "   ")))
        assertEquals(c.ready, counterLine(base.copy(text = "x".repeat(40))))
        assertEquals(c.old, counterLine(base.copy(text = "x".repeat(80), canPay = false)))
        // claimed wins over an older day and over a short text
        assertEquals(c.awarded, counterLine(base.copy(text = "", canPay = false, awarded = true)))
        // a day too old to pay, as the page sent it
        assertEquals(
            "Reflections on older days earn no XP",
            counterLine(JournalBodyFixtures.blankDay.reflect),
        )
    }

    /** .jc-count.on: claimed, or long enough whatever the day. */
    @Test
    fun counterTurnsGreen() {
        assertFalse(counterOn(base.copy(text = "short")))
        assertTrue(counterOn(base.copy(text = "x".repeat(40))))
        assertTrue(counterOn(base.copy(text = "x".repeat(40), canPay = false)))
        assertTrue(counterOn(base.copy(text = "", awarded = true)))
    }

    /**
     * JC_CHIP_DO: pet and fresh claim, line catches up, director hands over; claimed does nothing.
     */
    @Test
    fun chipsMapAsThePageDoes() {
        assertEquals(ChipTap.Claim("pet"), chipTap(WelcomeChip("pet", "Claim +10 XP", false)))
        assertEquals(
            ChipTap.Claim("fresh"),
            chipTap(WelcomeChip("fresh", "Start fresh · +25 XP", false)),
        )
        assertEquals(
            ChipTap.CatchUp,
            chipTap(WelcomeChip("line", "One-sentence catch-up · +20 XP", false)),
        )
        assertEquals(
            ChipTap.Open("life-director"),
            chipTap(WelcomeChip("director", "Open Life Director", false)),
        )
        assertNull(chipTap(WelcomeChip("fresh", "Start fresh · +25 XP", true)))
        assertNull(chipTap(WelcomeChip("unknown", "?", false)))
    }

    /** September 2026 starts on a Tuesday: one void place, then 30 days in five weeks. */
    @Test
    fun heatGridStartsOnTheRightWeekday() {
        val heat =
            JournalReviewFixtures.month.chart
                as com.lifemax.rpg.feature.journal.domain.ReviewChart.Heat
        val rows = heatRows(heat.lead, heat.cells)
        assertEquals(1, heat.lead)
        assertEquals(5, rows.size)
        assertNull(rows[0][0])
        assertEquals("1", rows[0][1]!!.day)
        assertEquals(listOf("29", "30"), rows[4].map { it!!.day }.takeLast(2))
        assertEquals(3, rows[4].size)
        // a lead outside a week is clamped, never more than six voids
        val cell = HeatCell("1", 0, 1f, false, "")
        assertEquals(6, heatRows(9, listOf(cell))[0].count { it == null })
        assertEquals(0, heatRows(-2, listOf(cell))[0].count { it == null })
    }

    @Test
    fun selectedTileIsFoundByKey() {
        val tiles = JournalBodyFixtures.tiles
        assertEquals(0, selectedIndex(tiles, "2026-10-28"))
        assertEquals(3, selectedIndex(tiles, "W:2026-W43"))
        assertEquals(-1, selectedIndex(tiles, "2020-01-01"))
        assertEquals(-1, selectedIndex(tiles, null))
        assertTrue(tiles.any { it is JournalListItem.Quiet })
    }
}
