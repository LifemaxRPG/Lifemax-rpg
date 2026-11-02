package com.lifemax.rpg.feature.journal

import com.lifemax.rpg.feature.journal.domain.Answer
import com.lifemax.rpg.feature.journal.domain.JournalCommand
import com.lifemax.rpg.feature.journal.domain.JournalReply
import com.lifemax.rpg.feature.journal.domain.Reflect
import com.lifemax.rpg.feature.journal.presentation.JournalDraftController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The words being typed and how they reach the page (JournalDraftController, ADR 0013), on a fake
 * clock: the autosave 1.5 s after the typing stops, the final write on leaving, one write at a
 * time, a conflict, a failed save, a lost answer.
 */
class JournalDraftTest {
    private val day = "2026-10-28"
    private val long =
        "A reflection long enough to be paid: the morning went well and I kept going."

    /** A clock that runs what was scheduled when it is advanced. */
    private class Clock {
        var now = 0L

        private class Job(val at: Long, val run: () -> Unit) {
            var cancelled = false
        }

        private val jobs = mutableListOf<Job>()
        val scheduled
            get() = jobs.count { !it.cancelled }

        fun schedule(ms: Long, run: () -> Unit): () -> Unit {
            val job = Job(now + ms, run)
            jobs += job
            return { job.cancelled = true }
        }

        fun advance(ms: Long) {
            val to = now + ms
            while (true) {
                val next =
                    jobs.filter { !it.cancelled && it.at <= to }.minByOrNull { it.at } ?: break
                now = next.at
                jobs.remove(next)
                next.run()
            }
            now = to
        }
    }

    private class Rig(owedFor: (String, String) -> Boolean) {
        val clock = Clock()
        val sent = mutableListOf<Pair<JournalCommand, String>>()
        var changes = 0
        var owed = owedFor
        val controller =
            JournalDraftController(
                send = { c ->
                    val id = "r${sent.size + 1}"
                    sent += c to id
                    id
                },
                schedule = clock::schedule,
                owes = { k, t -> owed(k, t) },
                changed = { changes++ },
            )

        fun reply(
            n: Int,
            ok: Boolean = true,
            changed: Boolean = true,
            conflict: Boolean = false,
            stored: String? = null,
        ) =
            controller.onReply(
                JournalReply(
                    sent[n - 1].first.name,
                    sent[n - 1].second,
                    ok,
                    changed,
                    "",
                    conflict = conflict,
                    stored = stored,
                )
            )
    }

    private fun rig(owed: Boolean = false) = Rig { _, text -> owed && text.trim().length >= 40 }

    private fun Rig.reflect(n: Int) = sent[n - 1].first as Reflect

    @Test
    fun theAutosaveComesOneAndAHalfSecondsAfterTheTypingStops() {
        val r = rig()
        r.controller.edit(day, "Hel", "")
        r.clock.advance(1_499)
        assertTrue(r.sent.isEmpty())
        r.clock.advance(1)
        assertEquals(1, r.sent.size)
        assertEquals(Reflect(day, "Hel", "", final = false), r.reflect(1))
    }

    @Test
    fun typingAgainStartsTheWaitOver() {
        val r = rig()
        r.controller.edit(day, "He", "")
        r.clock.advance(1_000)
        r.controller.edit(day, "Hello", "")
        r.clock.advance(1_000)
        assertTrue("1 s after the last key is too soon", r.sent.isEmpty())
        r.clock.advance(500)
        assertEquals(listOf(Reflect(day, "Hello", "", false)), r.sent.map { it.first })
    }

    @Test
    fun anAutosaveNeverIsTheFinalWriteWhateverTheLength() {
        val r = rig(owed = true)
        r.controller.edit(day, long, "")
        r.clock.advance(1_500)
        assertFalse(r.reflect(1).final)
    }

    @Test
    fun leavingSendsTheFinalWriteAtOnceAndCancelsTheWait() {
        val r = rig(owed = true)
        r.controller.edit(day, long, "")
        r.controller.leave()
        assertEquals(1, r.sent.size)
        assertEquals(Reflect(day, long, "", final = true), r.reflect(1))
        r.clock.advance(5_000)
        assertEquals("the wait was cancelled", 1, r.sent.size)
        assertEquals(0, r.clock.scheduled)
    }

    @Test
    fun leavingAfterAnAutosaveStillSendsTheFinalWriteThatPays() {
        val r = rig(owed = true)
        r.controller.edit(day, long, "")
        r.clock.advance(1_500)
        r.reply(1)
        assertNotNull("words the page holds but owes a reward for are kept", r.controller.draft)
        r.controller.leave()
        assertEquals(2, r.sent.size)
        assertEquals(Reflect(day, long, long, final = true), r.reflect(2))
    }

    @Test
    fun leavingWithNothingNewAndNothingOwedSendsNothing() {
        val r = rig(owed = false)
        r.controller.edit(day, "Short", "")
        r.clock.advance(1_500)
        r.reply(1)
        r.controller.leave()
        assertEquals(1, r.sent.size)
        assertNull("clean and owed nothing: let go", r.controller.draft)
    }

    @Test
    fun theFinalWriteIsSentOncePerText() {
        val r = rig(owed = true)
        r.controller.edit(day, long, "")
        r.controller.leave()
        r.reply(1)
        r.controller.leave()
        r.controller.leave()
        assertEquals(1, r.sent.size)
        assertNull(r.controller.draft)
    }

    @Test
    fun typingMoreAfterTheFinalWriteWritesAgain() {
        val r = rig(owed = true)
        r.controller.edit(day, long, "")
        r.controller.leave()
        r.reply(1)
        r.controller.edit(day, "$long And more.", long)
        r.controller.leave()
        assertEquals(2, r.sent.size)
        assertEquals(Reflect(day, "$long And more.", long, final = true), r.reflect(2))
    }

    @Test
    fun oneWriteAtATimeWhatIsTypedMeanwhileFollowsItsAnswer() {
        val r = rig()
        r.controller.edit(day, "One", "")
        r.clock.advance(1_500)
        r.controller.edit(day, "One two", "")
        r.clock.advance(1_500)
        assertEquals("the second waits for the first's answer", 1, r.sent.size)
        r.reply(1)
        assertEquals(2, r.sent.size)
        assertEquals(Reflect(day, "One two", "One", final = false), r.reflect(2))
    }

    @Test
    fun aFinalWriteAskedForWhileOneIsInFlightGoesWhenItAnswers() {
        val r = rig(owed = true)
        r.controller.edit(day, long, "")
        r.clock.advance(1_500)
        r.controller.leave()
        assertEquals(1, r.sent.size)
        r.reply(1)
        assertEquals(2, r.sent.size)
        assertTrue(r.reflect(2).final)
    }

    @Test
    fun aReviewIsAnAnswer() {
        val r = rig()
        r.controller.edit("W:2026-W43", "Better sleep", "")
        r.controller.leave()
        assertEquals(Answer("W:2026-W43", "Better sleep", "", final = true), r.sent[0].first)
    }

    @Test
    fun aConflictKeepsTheOwnersWordsRebasesAndSendsNothingByItself() {
        val r = rig()
        r.controller.edit(day, "Mine", "Old")
        r.controller.leave()
        r.reply(1, ok = false, changed = false, conflict = true, stored = "Old plus a sentence")
        val d = r.controller.draft!!
        assertEquals("Mine", d.text)
        assertEquals("Old plus a sentence", d.base)
        r.clock.advance(10_000)
        assertEquals("nothing is sent again by itself", 1, r.sent.size)
        r.controller.leave()
        assertEquals(Reflect(day, "Mine", "Old plus a sentence", true), r.reflect(2))
    }

    @Test
    fun theStoredWordsOfAnEarlierLostWriteAreNoConflict() {
        val r = rig()
        r.controller.edit(day, "First", "")
        r.clock.advance(1_500)
        r.controller.lostReply() // the screen opened again: the answer will not come
        r.controller.edit(day, "First second", "")
        r.clock.advance(1_500)
        assertEquals(Reflect(day, "First second", "", false), r.reflect(2))
        // the page holds "First": the lost write had landed
        r.reply(2, ok = false, changed = false, conflict = true, stored = "First")
        assertEquals(3, r.sent.size)
        assertEquals(Reflect(day, "First second", "First", false), r.reflect(3))
    }

    @Test
    fun aFailedSaveKeepsTheWordsAndTheNextTriggerSendsThemAgain() {
        val r = rig()
        r.controller.edit(day, "Typed", "")
        r.clock.advance(1_500)
        r.reply(1, ok = false, changed = true)
        assertEquals("Typed", r.controller.draft!!.text)
        r.clock.advance(10_000)
        assertEquals(1, r.sent.size)
        r.controller.leave()
        assertEquals(2, r.sent.size)
        assertEquals("Typed", r.reflect(2).text)
        assertEquals("the page holds them in memory now", "Typed", r.reflect(2).base)
    }

    @Test
    fun aLostAnswerIsSettledByThePageHoldingTheWords() {
        val r = rig()
        r.controller.edit(day, "Typed", "")
        r.controller.leave()
        r.controller.lostReply()
        assertNotNull(r.controller.draft)
        r.controller.page(day, "Typed")
        assertNull("the page has them: the draft is the page's", r.controller.draft)
    }

    @Test
    fun aLostAnswerIsSentAgainOnTheNextLeave() {
        val r = rig()
        r.controller.edit(day, "Typed", "")
        r.controller.leave()
        r.controller.lostReply()
        r.controller.leave()
        assertEquals(2, r.sent.size)
        assertEquals(r.reflect(1), r.reflect(2))
    }

    @Test
    fun theOwnersWordsAreNotReplacedWhileTheyAreNotThePages() {
        val r = rig()
        r.controller.edit(day, "Typed", "")
        r.controller.page(day, "Something the page added")
        assertEquals("Typed", r.controller.draft!!.text)
        assertEquals(0, r.controller.draft!!.rev)
    }

    @Test
    fun wordsTheOwnerHasNotChangedTakeInThePagesNewWords() {
        val r = rig(owed = true)
        r.controller.edit(day, "Same", "Same")
        r.controller.edit(day, "Same", "Same") // no change
        r.controller.page(day, "Same\nA catch-up sentence")
        val d = r.controller.draft!!
        assertEquals("Same\nA catch-up sentence", d.text)
        assertEquals("Same\nA catch-up sentence", d.base)
        assertEquals(1, d.rev)
    }

    @Test
    fun restingLetsGoOfWhatThePageHoldsAndKeepsWhatItMayNot() {
        val r = rig()
        r.controller.edit(day, "Kept", "")
        r.controller.rested()
        assertEquals("unsent words stay (A24)", "Kept", r.controller.draft!!.text)
        r.controller.leave()
        r.reply(1)
        r.controller.rested()
        assertNull(r.controller.draft)
        assertEquals(0, r.clock.scheduled)
    }

    @Test
    fun anEntryLeftWhileItsWriteIsInFlightDoesNotTakeThatAnswerForItsOwn() {
        val r = rig()
        r.controller.edit(day, "Old day words", "")
        r.controller.leave() // the select
        r.controller.edit("2026-10-27", "Other", "")
        r.reply(1)
        assertEquals("Other", r.controller.draft!!.text)
        assertEquals("", r.controller.draft!!.base)
    }

    @Test
    fun theCounterCountsCharactersOnlyOnceTheOwnerTyped() {
        val r = rig()
        r.controller.page(day, "x")
        r.controller.edit(day, "Hi", "")
        assertTrue(r.controller.draft!!.typed)
    }
}
