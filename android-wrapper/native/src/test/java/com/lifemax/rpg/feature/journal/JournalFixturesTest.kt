package com.lifemax.rpg.feature.journal

import com.lifemax.rpg.feature.journal.data.WebJournalRepository
import com.lifemax.rpg.feature.journal.data.toEvent
import com.lifemax.rpg.feature.journal.data.toJournalReply
import com.lifemax.rpg.feature.journal.data.toJson
import com.lifemax.rpg.feature.journal.domain.DayEntry
import com.lifemax.rpg.feature.journal.domain.JournalAction
import com.lifemax.rpg.feature.journal.domain.JournalCommand
import com.lifemax.rpg.feature.journal.domain.JournalFrame
import com.lifemax.rpg.feature.journal.domain.JournalListItem
import com.lifemax.rpg.feature.journal.domain.JournalReply
import com.lifemax.rpg.feature.journal.domain.ReviewChart
import com.lifemax.rpg.feature.journal.domain.ReviewEntry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decoder and the command wire against everything tools/gen_journal_fixtures.js records from
 * the real page: every frame must decode, with the kind of frame its name says, and every command's
 * payload and reply must be what the page sent and answered.
 */
class JournalFixturesTest {
    @Test
    fun everyOpenFrameDecodes() {
        for (name in GeneratedJournal.openNames) {
            val open = GeneratedJournal.frame(name) as? JournalFrame.Open
            assertTrue("$name is an open frame", open != null)
            val j = open!!.journal
            assertEquals("$name stats", 4, j.hero.stats.size)
            assertEquals("$name rhythm", 28, j.hero.rhythm.cells.size)
            assertEquals(listOf("media", "history"), open.chrome.panes.map { it.id })
            assertFalse(open.chrome.lock.on)
            if (j.empty != null) assertNull("$name entry", j.entry)
            else assertEquals("$name selection", j.selection, j.entry!!.key)
        }
    }

    @Test
    fun everyWithheldFrameDecodesAsTheCard() {
        for (name in GeneratedJournal.withheldNames) {
            val f = GeneratedJournal.frame(name) as? JournalFrame.Locked
            assertTrue("$name is the card", f != null)
            assertTrue(f!!.chrome.lock.on)
            assertEquals("Unlock", f.chrome.lock.card.unlock)
        }
        assertTrue(
            (GeneratedJournal.frame("locked-prompting") as JournalFrame.Locked).status.prompting
        )
        assertEquals(
            "Could not unlock. Try again.",
            (GeneratedJournal.frame("locked-error") as JournalFrame.Locked).status.error,
        )
        assertEquals(
            "Backup saved",
            (GeneratedJournal.frame("notice") as JournalFrame.Locked).notice!!.text,
        )
    }

    @Test
    fun theWithheldFramesCarryNothingOfTheJournal() {
        for (name in GeneratedJournal.withheldNames) {
            val text = GeneratedJournal.frame(name).toString()
            assertFalse("$name", text.contains("Canary", ignoreCase = true))
            assertFalse("$name", text.contains("hero"))
        }
    }

    @Test
    fun theShapesTheNativeSideHadWrong() {
        val today = GeneratedJournal.day("today")
        // a day's counter is four lines, a review's is the one line the page chose
        assertEquals("Saves for +15 XP", today.reflect.counter.ready)
        assertEquals("+30 XP claimed", GeneratedJournal.review("week").counter)
        // welcome has its tier; blank has icon, title and text; workout notes have items
        assertEquals(1, GeneratedJournal.day("welcome1").welcome!!.tier)
        assertEquals(2, GeneratedJournal.day("welcome2").welcome!!.tier)
        assertEquals(3, GeneratedJournal.day("welcome3").welcome!!.tier)
        val blank = GeneratedJournal.day("blank").blank!!
        assertEquals("Nothing logged this day yet", blank.title)
        assertEquals("A blank page is still a page.", blank.text)
        val notes = GeneratedJournal.day("notes").workoutNotes!!
        assertEquals("Workout notes", notes.title)
        assertEquals("Easy walking or cycling", notes.items.single().label)
        // the care note stands alone and the box has its plain label
        val care = GeneratedJournal.day("care")
        assertEquals("Reflection (optional)", care.reflect.label)
        assertNull(care.reflect.prompt)
        // a month's heat grid and a week's bars
        val month = GeneratedJournal.review("month")
        assertTrue(month.chart is ReviewChart.Heat)
        assertTrue(GeneratedJournal.review("week").chart is ReviewChart.Bars)
        assertTrue(month.grid.isNotEmpty())
        // an off day of the rhythm is colour 0 at opacity 0
        val off = GeneratedJournal.open("welcome1").hero.rhythm.cells.first { it.off }
        assertEquals(0L, off.color)
        assertEquals(0f, off.alpha)
    }

    @Test
    fun theEmptyJournalHasNoDays() {
        val j = GeneratedJournal.open("empty")
        assertTrue(j.list.items.isEmpty())
        assertNull(j.selection)
        assertEquals("No journal entries yet", j.empty!!.title)
    }

    @Test
    fun theLongStripHoldsHundredsOfDaysEachOnce() {
        val days =
            GeneratedJournal.open("longstrip").list.items.filterIsInstance<JournalListItem.Day>()
        assertTrue(days.size > 300)
        assertEquals(days.size, days.map { it.key }.toSet().size)
    }

    @Test
    fun theTipComesWithItsDismissWords() {
        val tip = GeneratedJournal.open("tip").tip!!
        assertEquals("Dismiss tip", tip.dismiss)
        assertNull(GeneratedJournal.open("today").tip)
    }

    @Test
    fun entriesAreOfTheirKind() {
        assertTrue(GeneratedJournal.open("pastday").entry is DayEntry)
        assertTrue(GeneratedJournal.open("week").entry is ReviewEntry)
        assertTrue(GeneratedJournal.open("month").entry is ReviewEntry)
    }

    @Test
    fun aListSharedByRevIsKept() {
        val repo = WebJournalRepository().apply { accept(GeneratedJournal.json("today"), true) }
        val list = (repo.state.value as JournalFrame.Open).journal.list
        repo.accept(GeneratedJournal.json("today").put("sequence", 2))
        assertTrue(list === (repo.state.value as JournalFrame.Open).journal.list)
    }

    // ----- the commands and the page's replies -----

    private fun command(name: String, payload: JSONObject): JournalCommand =
        when (name) {
            "select" -> JournalCommand.Select(payload.getString("key"))
            "write" -> JournalCommand.Write(payload.getString("key"))
            "swap" -> JournalCommand.Swap(payload.getString("d"), payload.getString("from"))
            "unlock" -> JournalCommand.Unlock
            "backup" -> JournalCommand.Backup
            "tip" -> JournalCommand.DismissTip
            else -> throw AssertionError("unknown command $name")
        }

    @Test
    fun everyCommandSendsWhatThePageReadsAndReadsItsReply() {
        val replies = GeneratedJournal.replies()
        var cases = 0
        for (case in replies.keys().asSequence().filter { it != "actions" }) {
            val c = replies.getJSONObject(case)
            val name = c.getString("command")
            val payload = c.getJSONObject("payload")
            val sent = command(name, payload)
            assertEquals(case, name, sent.name)
            val wire = sent.toJson()
            assertEquals(
                "$case keys",
                payload.keys().asSequence().toSet(),
                wire.keys().asSequence().toSet(),
            )
            for (k in payload.keys()) assertEquals("$case $k", payload.get(k), wire.get(k))
            val r = c.getJSONObject("reply")
            val expected = r.optString("message")
            assertEquals(
                case,
                JournalReply(
                    name,
                    r.getString("requestId"),
                    r.getBoolean("ok"),
                    r.getBoolean("changed"),
                    expected,
                ),
                r.toJournalReply(),
            )
            cases++
        }
        assertTrue(cases >= 20)
    }

    @Test
    fun theLockedRefusalsSayUnlockFirst() {
        val replies = GeneratedJournal.replies()
        for (case in
            listOf("selectLocked", "writeLocked", "swapLocked", "backupLocked", "tipLocked")) {
            val r = replies.getJSONObject(case).getJSONObject("reply").toJournalReply()!!
            assertFalse(case, r.ok)
            assertFalse(case, r.changed)
            assertEquals(case, "Unlock the Journal first.", r.message)
        }
    }

    @Test
    fun theActionsAreTheOnesThePageRuns() {
        val actions = GeneratedJournal.replies().getJSONObject("actions")
        assertEquals("claim", actions.getJSONObject("claimPet").getString("action"))
        assertEquals("claim" to "pet", JournalAction.Claim("pet").toEvent())
        assertEquals("pet", actions.getJSONObject("claimPet").getString("id"))
        assertEquals("claim" to "fresh", JournalAction.Claim("fresh").toEvent())
        assertEquals("fresh", actions.getJSONObject("claimFresh").getString("id"))
        assertEquals("catchUp" to "", JournalAction.CatchUp.toEvent())
        assertEquals("catchUp", actions.getJSONObject("catchUp").getString("action"))
        assertEquals("media", actions.getJSONObject("toMedia").getString("view"))
        assertEquals("navigate" to "media", JournalAction.OpenView("media").toEvent())
        assertEquals("life-director", actions.getJSONObject("toDirector").getString("view"))
        assertEquals(
            "navigate" to "life-director",
            JournalAction.OpenView("life-director").toEvent(),
        )
    }
}
