package com.lifemax.rpg.feature.journal

import com.lifemax.rpg.core.designsystem.feedback.XpAward
import com.lifemax.rpg.core.designsystem.feedback.XpGain
import com.lifemax.rpg.feature.journal.data.WebJournalRepository
import com.lifemax.rpg.feature.journal.data.toJournalReply
import com.lifemax.rpg.feature.journal.data.toJson
import com.lifemax.rpg.feature.journal.domain.Answer
import com.lifemax.rpg.feature.journal.domain.CatchUpSentence
import com.lifemax.rpg.feature.journal.domain.ClaimChip
import com.lifemax.rpg.feature.journal.domain.DayEntry
import com.lifemax.rpg.feature.journal.domain.JournalCommand
import com.lifemax.rpg.feature.journal.domain.JournalCommands
import com.lifemax.rpg.feature.journal.domain.JournalFrame
import com.lifemax.rpg.feature.journal.domain.JournalReply
import com.lifemax.rpg.feature.journal.domain.Reflect
import com.lifemax.rpg.feature.journal.presentation.JournalViewModel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2 (library.journal.write.v1, ADR 0013): the frames of a screen that writes, the write
 * commands' wire and the page's replies as the real page recorded them (fixtures/journal-native-*,
 * journal-write-replies.json), and the view model's draft, flush, claims, catch-up and celebrations
 * on a fake clock.
 */
class JournalWriteTest {
    private class Recorder : JournalCommands {
        val sent = mutableListOf<Pair<JournalCommand, String>>()

        override fun send(command: JournalCommand, requestId: String) {
            sent += command to requestId
        }
    }

    private class Clock {
        private val queue = mutableListOf<Triple<Long, () -> Unit, BooleanArray>>()
        private var now = 0L

        fun schedule(ms: Long, run: () -> Unit): () -> Unit {
            val gone = BooleanArray(1)
            queue += Triple(now + ms, run, gone)
            return { gone[0] = true }
        }

        fun advance(ms: Long) {
            val to = now + ms
            while (true) {
                val next =
                    queue.filter { !it.third[0] && it.first <= to }.minByOrNull { it.first }
                        ?: break
                now = next.first
                queue.remove(next)
                next.second()
            }
            now = to
        }
    }

    private class Rig(
        val repo: WebJournalRepository,
        val vm: JournalViewModel,
        val rec: Recorder,
        val clock: Clock,
    ) {
        fun reply(n: Int, build: JSONObject.() -> Unit = {}): JournalReply {
            val (command, id) = rec.sent[n - 1]
            val j =
                JSONObject()
                    .put("command", command.name)
                    .put("requestId", id)
                    .put("ok", true)
                    .put("changed", true)
                    .apply(build)
            return j.toJournalReply()!!.also(vm::onReply)
        }
    }

    private fun rig(name: String = "native-today", sequence: Long = 1): Rig {
        val repo =
            WebJournalRepository().apply {
                accept(GeneratedJournal.json(name).put("sequence", sequence), true)
            }
        val rec = Recorder()
        val clock = Clock()
        var n = 0
        val vm = JournalViewModel(repo, rec, clock::schedule) { "req-${++n}" }
        vm.opened()
        vm.onProjection()
        return Rig(repo, vm, rec, clock)
    }

    private fun Rig.entry() = (repo.state.value as JournalFrame.Open).journal.entry!!

    private fun Rig.day() = entry() as DayEntry

    private fun Rig.accept(name: String, sequence: Long) {
        repo.accept(GeneratedJournal.json(name).put("sequence", sequence))
        vm.onProjection()
    }

    // ----- the frames and the wire -----

    @Test
    fun aFrameFromAScreenThatWritesCarriesTheWriteWords() {
        val j = GeneratedJournal.open("native-today")
        assertEquals("native", j.edit)
        val w = j.write!!
        assertEquals("One sentence: what have you been up to?", w.catchUp.prompt)
        assertEquals(12, w.catchUp.min)
        assertEquals("OK", w.catchUp.ok)
        assertNotNull(w.celebrationTarget)
        assertNull("the page's own field has no write words", GeneratedJournal.open("today").write)
        assertEquals("page", GeneratedJournal.open("today").edit)
    }

    @Test
    fun theWriteWordsBelongToANativeEditorOnly() {
        val native = GeneratedJournal.json("native-today")
        val a = WebJournalRepository().apply { accept(native, true) }
        assertTrue(a.state.value is JournalFrame.Open)
        val page = GeneratedJournal.json("today").put("edit", "native")
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            WebJournalRepository().accept(page, true)
        }
        val noWords = GeneratedJournal.json("native-today").put("write", JSONObject.NULL)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            WebJournalRepository().accept(noWords, true)
        }
        val short = GeneratedJournal.json("native-today")
        short.getJSONObject("write").getJSONObject("catchUp").put("min", 0)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            WebJournalRepository().accept(short, true)
        }
    }

    private fun command(name: String, p: JSONObject): JournalCommand =
        when (name) {
            "reflect" ->
                Reflect(
                    p.getString("d"),
                    p.getString("text"),
                    p.getString("base"),
                    p.getBoolean("final"),
                )
            "answer" ->
                Answer(
                    p.getString("key"),
                    p.getString("text"),
                    p.getString("base"),
                    p.getBoolean("final"),
                )
            "claim" -> ClaimChip(p.getString("kind"))
            "catchUp" -> CatchUpSentence(p.getString("text"))
            else -> throw AssertionError("unknown command $name")
        }

    @Test
    fun everyWriteCommandSendsWhatThePageReadsAndReadsItsReply() {
        val cases = GeneratedJournal.writeReplies()
        var n = 0
        for (case in cases.keys()) {
            val c = cases.getJSONObject(case)
            val payload = c.getJSONObject("payload")
            val sent = command(c.getString("command"), payload)
            assertEquals(case, c.getString("command"), sent.name)
            val wire = sent.toJson()
            assertEquals(
                case,
                payload.keys().asSequence().toSet(),
                wire.keys().asSequence().toSet(),
            )
            for (k in payload.keys()) assertEquals("$case $k", payload.get(k), wire.get(k))
            val r = c.getJSONObject("reply").toJournalReply()!!
            assertEquals(case, c.getJSONObject("reply").getBoolean("ok"), r.ok)
            assertEquals(case, c.getJSONObject("reply").optString("message"), r.message)
            n++
        }
        assertTrue(n >= 18)
    }

    @Test
    fun theRepliesCarryWhatTheDraftNeeds() {
        val cases = GeneratedJournal.writeReplies()
        fun reply(case: String) =
            cases.getJSONObject(case).getJSONObject("reply").toJournalReply()!!
        val autosave = reply("reflectAutosave")
        assertTrue(autosave.ok && autosave.changed && !autosave.paid)
        assertTrue(autosave.celebrations.isEmpty())
        val paid = reply("reflectFinalPays")
        assertTrue(paid.ok && paid.paid)
        assertEquals(15, paid.celebrations.single().xp)
        assertTrue(paid.celebrations.single().gains.single().color > 0)
        val conflict = reply("reflectConflict")
        assertTrue(!conflict.ok && conflict.conflict)
        assertTrue(conflict.stored!!.startsWith("Reflecting on the whole day"))
        assertEquals(
            "a conflict while locked names no stored words",
            "",
            reply("reflectConflictLocked").stored,
        )
        assertTrue(reply("answerFinalPays").paid)
        assertEquals(50, reply("answerFinalPays").celebrations.single().xp)
        assertEquals("Already recorded.", reply("claimPetAgain").message)
        assertEquals("Write a full sentence (12+ characters)", reply("catchUpShort").message)
        assertTrue(reply("catchUp").paid)
        assertTrue(reply("reflectLocked").celebrations.isEmpty())
    }

    @Test
    fun aMalformedCelebrationIsLeftOutAndTheReplyStillSettlesTheWrite() {
        val j =
            JSONObject()
                .put("command", "reflect")
                .put("requestId", "r1")
                .put("ok", true)
                .put("changed", true)
                .put("celebrations", org.json.JSONArray().put(JSONObject().put("xp", "many")))
        val r = j.toJournalReply()!!
        assertTrue(r.ok)
        assertTrue(r.celebrations.isEmpty())
    }

    // ----- the view model -----

    @Test
    fun typingMakesADraftAndTheAutosaveSendsItAfterOneAndAHalfSeconds() {
        val r = rig()
        val page = r.day().reflect.text
        r.vm.edit(r.day().key, "Typed")
        assertEquals("Typed", r.vm.ui.value.draft!!.text)
        r.clock.advance(1_499)
        assertTrue(r.rec.sent.isEmpty())
        r.clock.advance(1)
        assertEquals(Reflect(r.day().key, "Typed", page, final = false), r.rec.sent.single().first)
    }

    @Test
    fun aScreenThatDoesNotWriteIgnoresTyping() {
        val r = rig("today")
        r.vm.edit(r.day().key, "Typed")
        assertNull(r.vm.ui.value.draft)
        r.clock.advance(5_000)
        assertTrue(r.rec.sent.isEmpty())
    }

    @Test
    fun leavingTheFieldPaysOnlyWhatIsOwed() {
        val r = rig()
        val d = r.day()
        assertFalse("today's lived reflection is unpaid in the fixture", d.reflect.awarded)
        r.vm.edit(d.key, d.reflect.text + " And a little more.")
        r.vm.leaveField()
        val sent = r.rec.sent.single().first as Reflect
        assertTrue(sent.final)
    }

    @Test
    fun theFinalWriteGoesBeforeAnotherEntryIsSelected() {
        val r = rig()
        val d = r.day()
        r.vm.edit(d.key, "Words typed before the tap")
        val other =
            (r.repo.state.value as JournalFrame.Open).journal.list.keys.first { it != d.key }
        r.vm.select(other)
        assertEquals(2, r.rec.sent.size)
        assertTrue((r.rec.sent[0].first as Reflect).final)
        assertEquals(
            com.lifemax.rpg.feature.journal.domain.JournalCommand.Select(other),
            r.rec.sent[1].first,
        )
    }

    @Test
    fun hidingFlushesTheWordsAsTheFinalWrite() {
        val r = rig()
        r.vm.edit(r.day().key, "Typed as the tab was left")
        r.vm.hidden()
        val sent = r.rec.sent.single().first as Reflect
        assertTrue(sent.final)
        assertEquals("Typed as the tab was left", sent.text)
        r.clock.advance(5_000)
        assertEquals("the autosave was cancelled", 1, r.rec.sent.size)
    }

    @Test
    fun theDraftSurvivesTheScreenOpeningAgainAndIsSentAgain() {
        val r = rig()
        r.vm.edit(r.day().key, "Typed")
        r.vm.hidden()
        r.vm.rested() // the page closed it: nothing came back
        assertEquals("unconfirmed words are kept", "Typed", r.vm.ui.value.draft!!.text)
        r.vm.opened()
        r.vm.leaveField()
        assertEquals(2, r.rec.sent.size)
        assertEquals(r.rec.sent[0].first, r.rec.sent[1].first)
    }

    @Test
    fun theDraftIsLetGoWhenThePageHoldsTheWords() {
        val r = rig()
        r.vm.edit(r.day().key, "Short note")
        r.vm.leaveField()
        r.reply(1)
        // the page's next frame has the words
        val j = GeneratedJournal.json("native-today").put("sequence", 5)
        j.getJSONObject("entry").getJSONObject("reflect").put("text", "Short note")
        r.repo.accept(j)
        r.vm.onProjection()
        assertNull(r.vm.ui.value.draft)
    }

    @Test
    fun aReplyThatPaidStartsTheCelebrationOnceAndPlayedOrHiddenItGoes() {
        val r = rig()
        r.vm.edit(r.day().key, "Typed")
        r.vm.leaveField()
        r.reply(1) {
            put("paid", true)
            put(
                "celebrations",
                org.json
                    .JSONArray()
                    .put(
                        JSONObject()
                            .put("xp", 15)
                            .put(
                                "gains",
                                org.json
                                    .JSONArray()
                                    .put(
                                        JSONObject()
                                            .put("attribute", "wellbeing")
                                            .put("short", "WLB")
                                            .put("xp", 15)
                                            .put("color", 0xFF34D399L)
                                    ),
                            )
                    ),
            )
        }
        val c = r.vm.ui.value.celebration!!
        assertEquals(
            listOf(XpAward(15, listOf(XpGain("wellbeing", "WLB", 15, 0xFF34D399L)))),
            c.awards,
        )
        r.vm.celebrated(c.id)
        assertNull(r.vm.ui.value.celebration)
        // and a celebration cut short by hiding is not replayed
        r.vm.edit(r.day().key, "Typed more")
        r.vm.leaveField()
        r.reply(2) {
            put(
                "celebrations",
                org.json
                    .JSONArray()
                    .put(
                        JSONObject()
                            .put("xp", 15)
                            .put(
                                "gains",
                                org.json
                                    .JSONArray()
                                    .put(
                                        JSONObject()
                                            .put("attribute", "a")
                                            .put("short", "A")
                                            .put("xp", 15)
                                            .put("color", 1L)
                                    ),
                            )
                    ),
            )
        }
        assertNotNull(r.vm.ui.value.celebration)
        r.vm.hidden()
        assertNull(r.vm.ui.value.celebration)
    }

    @Test
    fun aRefusedWriteToastsThePagesWordsAndAKeptWriteWithoutItsRewardToo() {
        val r = rig()
        r.vm.edit(r.day().key, "Typed")
        r.vm.leaveField()
        r.reply(1) {
            put("ok", false)
            put("changed", false)
            put("message", "This day was changed elsewhere.")
            put("conflict", true)
            put("stored", "Theirs")
        }
        assertEquals("This day was changed elsewhere.", r.vm.ui.value.toast!!.text)
        r.vm.edit(r.day().key, "Typed again")
        r.vm.leaveField()
        r.reply(2) {
            put("paid", false)
            put("message", "Reflection saved · XP not saved (storage full?)")
        }
        assertEquals("Reflection saved · XP not saved (storage full?)", r.vm.ui.value.toast!!.text)
        r.vm.edit(r.day().key, "Typed a third time")
        r.vm.leaveField()
        r.reply(3) {
            put("changed", false)
            put("message", "Already recorded.")
        }
        assertEquals("a repeat says nothing", 2L, r.vm.ui.value.toast!!.id)
    }

    @Test
    fun aClaimIsOneCommandWhileItWaits() {
        val r = rig("native-welcome2")
        r.vm.claim("pet")
        r.vm.claim("pet")
        assertEquals(listOf(ClaimChip("pet")), r.rec.sent.map { it.first })
        r.reply(1)
        r.vm.claim("pet")
        assertEquals(2, r.rec.sent.size)
    }

    @Test
    fun theCatchUpOpensItsFieldSendsTheSentenceAndClosesOnTheOk() {
        val r = rig("native-welcome2")
        assertFalse(r.vm.ui.value.catchUp.open)
        r.vm.openCatchUp()
        assertTrue(r.vm.ui.value.catchUp.open)
        r.vm.sendCatchUp("I was travelling for work all week long.")
        r.vm.sendCatchUp("again") // one at a time
        assertEquals(
            listOf(CatchUpSentence("I was travelling for work all week long.")),
            r.rec.sent.map { it.first },
        )
        assertTrue(r.vm.ui.value.catchUp.sending)
        r.reply(1)
        assertEquals(com.lifemax.rpg.feature.journal.domain.CatchUpUi(), r.vm.ui.value.catchUp)
    }

    @Test
    fun aRefusedCatchUpKeepsItsFieldAndSaysWhy() {
        val r = rig("native-welcome2")
        r.vm.openCatchUp()
        r.vm.sendCatchUp("too short")
        r.reply(1) {
            put("ok", false)
            put("changed", false)
            put("message", "Write a full sentence (12+ characters)")
        }
        assertTrue(r.vm.ui.value.catchUp.open)
        assertFalse(r.vm.ui.value.catchUp.sending)
        assertEquals("Write a full sentence (12+ characters)", r.vm.ui.value.toast!!.text)
    }

    @Test
    fun aCatchUpWaitingWhenTheScreenOpensAgainStopsWaiting() {
        val r = rig("native-welcome2")
        r.vm.openCatchUp()
        r.vm.sendCatchUp("I was travelling for work all week long.")
        r.vm.opened()
        r.vm.onProjection() // the plugin accepts the frame right after
        assertFalse(r.vm.ui.value.catchUp.sending)
        r.vm.openCatchUp()
        r.vm.sendCatchUp("I was travelling for work all week long.")
        assertEquals("the retry sends the same sentence", 2, r.rec.sent.size)
        assertEquals(r.rec.sent[0].first, r.rec.sent[1].first)
    }

    @Test
    fun restingKeepsNoJournalDataButTheOwnersOwnUnsentWords() {
        // privacy: once the screen is locked or resting, the frame holds none of the Journal
        val r = rig()
        r.vm.edit(r.day().key, "Typed")
        r.vm.hidden()
        r.repo.rest()
        r.vm.rested()
        val frame = r.vm.ui.value.frame
        assertTrue(frame is JournalFrame.Resting)
        assertFalse(frame.toString().contains("Canary"))
        assertTrue("the owner's own unsent words wait in the draft", r.vm.ui.value.draft != null)
    }

    @Test
    fun aReviewAnswerIsAnAnswerAndPaysOnLeaving() {
        val r = rig("native-week")
        val review = r.entry()
        assertTrue(review.key.startsWith("W:"))
        r.vm.edit(review.key, "A new answer")
        r.vm.leaveField()
        val sent = r.rec.sent.single().first as Answer
        assertEquals(review.key, sent.key)
        assertTrue(sent.final)
    }

    @Test
    fun theCounterLineFollowsTheWordsAsTheyAreTyped() {
        val r = rig()
        val reflect = r.day().reflect
        assertEquals(
            reflect.counter.ready,
            reflect.counterFor(reflect.text.trim().length, typing = true),
        )
        assertEquals("${reflect.counter.short} (5)", reflect.counterFor(5, typing = true))
        assertEquals(reflect.counter.short, reflect.counterFor(5, typing = false))
    }
}
