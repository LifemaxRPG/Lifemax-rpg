package com.lifemax.rpg.feature.journal

import com.lifemax.rpg.core.data.PAGE_TOKENS
import com.lifemax.rpg.feature.journal.data.WebJournalRepository
import com.lifemax.rpg.feature.journal.data.toEvent
import com.lifemax.rpg.feature.journal.data.toJournalReply
import com.lifemax.rpg.feature.journal.data.toJson
import com.lifemax.rpg.feature.journal.domain.DayEntry
import com.lifemax.rpg.feature.journal.domain.JournalAction
import com.lifemax.rpg.feature.journal.domain.JournalCommand
import com.lifemax.rpg.feature.journal.domain.JournalCommands
import com.lifemax.rpg.feature.journal.domain.JournalFrame
import com.lifemax.rpg.feature.journal.domain.JournalListItem
import com.lifemax.rpg.feature.journal.domain.JournalReply
import com.lifemax.rpg.feature.journal.domain.ReviewChart
import com.lifemax.rpg.feature.journal.domain.ReviewEntry
import com.lifemax.rpg.feature.journal.presentation.JournalViewModel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Screen A of the native Journal: the strict decoder, the lock's states (withheld, at rest, on stop
 * with its session gate) and the view model's commands. The fixtures are hand-made from plan §3.3
 * (fixtures/handmade, tools: none) until gen_native_fixtures.js journal() lands; their private
 * strings all start with CANARY.
 */
class JournalTest {
    private fun fixture(name: String) =
        JSONObject(javaClass.getResource("/fixtures/handmade/journal-a-$name.json")!!.readText())

    private class Recorder : JournalCommands {
        val sent = mutableListOf<Pair<JournalCommand, String>>()

        override fun send(command: JournalCommand, requestId: String) {
            sent += command to requestId
        }
    }

    private fun setUp(
        name: String = "open"
    ): Triple<WebJournalRepository, JournalViewModel, Recorder> {
        val repo = WebJournalRepository().apply { accept(fixture(name), true) }
        val rec = Recorder()
        var n = 0
        val vm = JournalViewModel(repo, rec) { "req-${++n}" }
        vm.opened()
        vm.onProjection()
        return Triple(repo, vm, rec)
    }

    private fun JSONObject.next(seq: Long) = JSONObject(toString()).put("sequence", seq)

    private fun reply(id: String, command: String, ok: Boolean = true, message: String = "") =
        JournalReply(command, id, ok, changed = ok, message = message)

    /** Everything a frame holds, as text, for the canary checks. */
    private fun JournalFrame?.text() = toString()

    // ----- the decoder -----

    @Test
    fun decodesTheOpenFrame() {
        val f = WebJournalRepository().apply { accept(fixture("open"), true) }.state.value
        val open = f as JournalFrame.Open
        assertEquals(listOf("media", "history"), open.chrome.panes.map { it.id })
        assertTrue(open.chrome.lock.on)
        assertEquals("Unlock", open.chrome.lock.card.unlock)
        assertEquals(PAGE_TOKENS.toSet(), open.chrome.tokens.keys)
        val j = open.journal
        assertEquals("page", j.edit)
        assertEquals("Journal", j.tip!!.title)
        assertEquals(listOf("5", "26", "1,055", "2"), j.hero.stats.map { it.value })
        assertEquals(28, j.hero.rhythm.cells.size)
        assertTrue(j.hero.rhythm.cells.last().today)
        assertNull(j.hero.rhythm.cells[3].color)
        assertEquals(
            setOf("2026-10-28", "2026-10-27", "W:2026-W43", "2026-10-26", "2026-10-21"),
            j.list.keys,
        )
        assertEquals(JournalListItem.Quiet("4 quiet days"), j.list.items[4])
        val day = j.entry as DayEntry
        assertEquals("2026-10-28", j.selection)
        assertEquals("Steady Stride", day.insight!!.label)
        assertEquals("PRM_C2", day.reflect.prompt!!.id)
        assertEquals(listOf("Bench press"), day.timeline.single().plan.map { it.label })
        assertNull(day.timeline.single().skill)
    }

    @Test
    fun decodesAReview() {
        val f = WebJournalRepository().apply { accept(fixture("review"), true) }.state.value
        val r = (f as JournalFrame.Open).journal.entry as ReviewEntry
        assertEquals("W43", r.head.badge)
        assertEquals(7, (r.chart as ReviewChart.Bars).bars.size)
        assertEquals("Write 50+ characters for +40 XP", r.counterText)
    }

    @Test
    fun decodesTheWithheldFrameAsTheCard() {
        val f = WebJournalRepository().apply { accept(fixture("locked-error"), true) }.state.value
        val locked = f as JournalFrame.Locked
        assertEquals("Could not unlock. Try again.", locked.status.error)
        assertFalse(locked.status.prompting)
        assertEquals("Backup saved", locked.notice!!.text)
        assertEquals("Journal locked", locked.chrome.lock.card.title)
    }

    @Test
    fun refusesMalformedFramesAndKeepsTheLastGood() {
        val repo = WebJournalRepository().apply { accept(fixture("open"), true) }
        val good = repo.state.value
        val broken =
            listOf<(JSONObject) -> Unit>(
                { it.remove("hero") },
                { it.remove("notice") },
                { it.put("edit", "sideways") },
                { it.getJSONObject("tokens").remove("bg") },
                { it.getJSONObject("hero").getJSONArray("stats").remove(0) },
                {
                    it.getJSONObject("hero").getJSONObject("rhythm").getJSONArray("cells").remove(0)
                },
                { it.put("selection", "2026-01-01") },
                { it.getJSONObject("entry").put("key", "2026-10-27") },
                {
                    it.getJSONObject("entry")
                        .getJSONObject("reflect")
                        .put("label", "Reflection (optional)")
                },
                { it.getJSONObject("entry").getJSONObject("reflect").put("meter", 140) },
                { it.getJSONObject("entry").put("kind", "poem") },
                {
                    it.getJSONObject("entry")
                        .getJSONArray("timeline")
                        .getJSONObject(0)
                        .put("skill", 3)
                },
                {
                    it.getJSONObject("list")
                        .getJSONArray("items")
                        .getJSONObject(0)
                        .put("kind", "week")
                },
                {
                    it.getJSONObject("list")
                        .getJSONArray("items")
                        .getJSONObject(1)
                        .put("key", "2026-10-28")
                },
                {
                    it.getJSONObject("entry")
                        .put(
                            "care",
                            JSONObject().put("lead", "That sounds really heavy.").put("text", "…"),
                        )
                },
                { it.put("protocol", 2) },
                { it.put("webSessionId", "") },
            )
        broken.forEachIndexed { i, breakIt ->
            val bad = fixture("open").next(100L + i).also(breakIt)
            assertThrows("case $i", Exception::class.java) { repo.accept(bad) }
            assertSame("case $i", good, repo.state.value)
        }
    }

    @Test
    fun refusesAWithheldFrameThatCarriesPrivateData() {
        val repo = WebJournalRepository().apply { accept(fixture("locked"), true) }
        for (key in listOf("hero", "list", "entry", "selection", "tip", "empty")) {
            val leaky = fixture("locked").next(60).put(key, JSONObject.NULL)
            assertThrows(key, Exception::class.java) { repo.accept(leaky) }
        }
        val lockOff = fixture("locked").next(61)
        lockOff.getJSONObject("lock").put("on", false)
        assertThrows(Exception::class.java) { repo.accept(lockOff) }
    }

    @Test
    fun ordersFramesBySessionAndSequence() {
        val repo = WebJournalRepository().apply { accept(fixture("open"), true) }
        val now = repo.state.value
        repo.accept(fixture("locked").put("sequence", 40)) // older: ignored
        assertSame(now, repo.state.value)
        repo.accept(fixture("locked").put("webSessionId", "other")) // another session, no open
        assertSame(now, repo.state.value)
        repo.accept(fixture("locked").put("webSessionId", "other"), true)
        assertTrue(repo.state.value is JournalFrame.Locked)
    }

    // ----- the lock: no private text while locked -----

    @Test
    fun aWithheldFrameDropsTheOpenJournal() {
        val repo = WebJournalRepository().apply { accept(fixture("open"), true) }
        assertTrue(repo.state.value.text().contains("CANARY"))
        repo.accept(fixture("locked").next(50))
        assertTrue(repo.state.value is JournalFrame.Locked)
        assertFalse(repo.state.value.text().contains("CANARY"))
    }

    @Test
    fun atRestOnlyTheChromeIsLeftWithOrWithoutTheLock() {
        for (lockOn in listOf(true, false)) {
            val open = fixture("open")
            open.getJSONObject("lock").put("on", lockOn)
            val repo = WebJournalRepository().apply { accept(open, true) }
            repo.rest()
            val f = repo.state.value
            assertTrue(f is JournalFrame.Resting)
            assertFalse(f.text().contains("CANARY"))
            assertEquals(lockOn, f!!.chrome.lock.on)
        }
    }

    @Test
    fun stopWithTheLockOnLocksAndGatesTheSession() {
        val repo = WebJournalRepository().apply { accept(fixture("open"), true) }
        assertTrue(repo.stop())
        assertTrue(repo.state.value is JournalFrame.Locked)
        assertFalse(repo.state.value.text().contains("CANARY"))
        assertEquals("history-web-fixture", repo.relockFor)
        // an open frame built before the page's relock, landing late: refused, still the card
        repo.accept(fixture("open").next(42))
        assertTrue(repo.state.value is JournalFrame.Locked)
        assertFalse(repo.state.value.text().contains("CANARY"))
        // even after a rest, a refused open frame shows the card, never a blank page or the text
        repo.rest()
        repo.accept(fixture("open").next(43), true)
        assertTrue(repo.state.value is JournalFrame.Locked)
        // the page's relock (its withheld frame) clears the gate; then the unlock's open frame is
        // taken
        repo.accept(fixture("locked").next(44))
        assertNull(repo.relockFor)
        repo.accept(fixture("open").next(45))
        assertTrue(repo.state.value is JournalFrame.Open)
    }

    @Test
    fun anOpenFrameOlderThanTheRelockIsIgnored() {
        val repo = WebJournalRepository().apply { accept(fixture("open"), true) }
        repo.stop()
        repo.accept(fixture("locked").next(50))
        repo.accept(fixture("open").next(49))
        assertTrue(repo.state.value is JournalFrame.Locked)
    }

    @Test
    fun stopWithTheLockOffKeepsTheFrameAndSetsNoGate() {
        val open = fixture("open")
        open.getJSONObject("lock").put("on", false)
        val repo = WebJournalRepository().apply { accept(open, true) }
        val before = repo.state.value
        assertFalse(repo.stop())
        assertSame(before, repo.state.value)
        assertNull(repo.relockFor)
    }

    @Test
    fun aNewSessionClearsTheGate() {
        val repo = WebJournalRepository().apply { accept(fixture("open"), true) }
        repo.stop()
        repo.accept(
            fixture("open").put("webSessionId", "history-web-reloaded").put("sequence", 1),
            true,
        )
        assertNull(repo.relockFor)
        assertTrue(repo.state.value is JournalFrame.Open)
    }

    @Test
    fun theListObjectIsKeptWhileItsRevIsUnchanged() {
        val repo = WebJournalRepository().apply { accept(fixture("open"), true) }
        val list = (repo.state.value as JournalFrame.Open).journal.list
        repo.accept(fixture("open").next(42))
        assertSame(list, (repo.state.value as JournalFrame.Open).journal.list)
        val changed = fixture("open").next(43)
        changed.getJSONObject("list").put("rev", "13|2026-10-28|0")
        repo.accept(changed)
        assertFalse(list === (repo.state.value as JournalFrame.Open).journal.list)
    }

    // ----- the counter, as jcCounterText -----

    @Test
    fun theCounterChoosesAsThePage() {
        val box =
            (WebJournalRepository().apply { accept(fixture("open"), true) }.state.value
                    as JournalFrame.Open)
                .journal
                .entry
                .let { (it as DayEntry).reflect }
        val fresh = box.copy(awarded = false)
        assertEquals("+15 XP & +2 bond claimed", box.counterFor(3, typing = true))
        assertEquals("Write 40+ characters for +15 XP (12)", fresh.counterFor(12, typing = true))
        assertEquals("Write 40+ characters for +15 XP", fresh.counterFor(12, typing = false))
        assertEquals("Saves for +15 XP", fresh.counterFor(40, typing = true))
        assertEquals(
            "Reflections on older days earn no XP",
            fresh.copy(canPay = false).counterFor(80, true),
        )
        assertEquals(50f, fresh.meterFor(20))
        assertEquals(100f, box.meterFor(0))
    }

    // ----- the view model -----

    @Test
    fun aTapHighlightsAtOnceThenFollowsThePage() {
        val (repo, vm, rec) = setUp()
        vm.select("2026-10-27")
        assertEquals("2026-10-27", vm.ui.value.selection)
        assertEquals(JournalCommand.Select("2026-10-27") to "req-1", rec.sent.single())
        // a refresh still on the old day keeps the tapped highlight
        repo.accept(fixture("open").next(42))
        vm.onProjection()
        assertEquals("2026-10-27", vm.ui.value.selection)
        // the answer lands with the page's selection
        repo.accept(
            fixture("open").next(43).put("selection", "2026-10-27").also {
                it.getJSONObject("entry").put("key", "2026-10-27")
            }
        )
        vm.onProjection()
        vm.onReply(reply("req-1", "select"))
        assertEquals("2026-10-27", vm.ui.value.selection)
        // a select the page refused: the page's selection again, and its words
        vm.select("W:2026-W43")
        vm.onReply(
            reply("req-2", "select", ok = false, message = "That day is not in your Journal.")
        )
        assertEquals("2026-10-27", vm.ui.value.selection)
        assertEquals("That day is not in your Journal.", vm.ui.value.toast!!.text)
    }

    @Test
    fun selectIgnoresTheSelectedDayUnknownKeysAndTheCard() {
        val (_, vm, rec) = setUp()
        vm.select("2026-10-28")
        vm.select("2025-01-01")
        assertTrue(rec.sent.isEmpty())
        val (_, locked, rec2) = setUp("locked")
        locked.select("2026-10-27")
        locked.backup()
        locked.swap("2026-10-28", "PRM_C2")
        assertTrue(rec2.sent.isEmpty())
    }

    @Test
    fun commandsCarryThePagesWireNames() {
        val (_, vm, rec) = setUp()
        vm.swap("2026-10-28", "PRM_C2")
        vm.swap("2026-10-28", "PRM_C2") // waits for the first
        vm.backup()
        vm.dismissTip()
        vm.write("2026-10-28")
        vm.write("2026-10-28") // one hand-over per opening
        assertEquals(listOf("swap", "backup", "tip", "write"), rec.sent.map { it.first.name })
        val swap = rec.sent[0].first.toJson()
        assertEquals(
            listOf("2026-10-28", "PRM_C2"),
            listOf(swap.getString("d"), swap.getString("from")),
        )
        assertEquals(2, swap.length())
        assertEquals("2026-10-28", rec.sent[3].first.toJson().getString("key"))
        assertEquals(0, JournalCommand.Unlock.toJson().length())
        assertEquals("claim" to "pet", JournalAction.Claim("pet").toEvent())
        assertEquals("catchUp" to "", JournalAction.CatchUp.toEvent())
        assertEquals("navigate" to "media", JournalAction.OpenView("media").toEvent())
        val r =
            JSONObject()
                .put("command", "swap")
                .put("requestId", "req-1")
                .put("ok", true)
                .toJournalReply()
        assertEquals(JournalReply("swap", "req-1", true, false, ""), r)
        assertNull(JSONObject().put("ok", true).toJournalReply())
    }

    @Test
    fun handOversGoOncePerOpening() {
        val (_, vm, _) = setUp()
        assertTrue(vm.handOver(JournalAction.Claim("pet")))
        assertFalse(vm.handOver(JournalAction.CatchUp))
        vm.opened()
        vm.onProjection()
        assertTrue(vm.handOver(JournalAction.OpenView("media")))
        val (_, locked, _) = setUp("locked")
        assertFalse(locked.handOver(JournalAction.Claim("pet")))
        assertTrue(locked.handOver(JournalAction.OpenView("media")))
    }

    @Test
    fun unlockIsSentOnceAndWaitsForThePrompt() {
        val (repo, vm, rec) = setUp("locked")
        vm.unlock()
        vm.unlock()
        assertEquals(listOf<JournalCommand>(JournalCommand.Unlock), rec.sent.map { it.first })
        assertTrue(vm.ui.value.unlocking)
        repo.accept(fixture("locked-prompting").put("sequence", 50))
        vm.onProjection()
        vm.onReply(reply("req-1", "unlock", ok = false))
        assertFalse(vm.ui.value.unlocking)
        vm.unlock() // the prompt is up: the card's button waits
        assertEquals(1, rec.sent.size)
        repo.accept(fixture("locked-error").put("sequence", 51))
        vm.onProjection()
        vm.unlock()
        assertEquals(2, rec.sent.size)
    }

    @Test
    fun theNoticeIsShownOnceEvenInWithheldFrames() {
        val (repo, vm, _) = setUp("locked-error")
        val toast = vm.ui.value.toast
        assertEquals("Backup saved", toast!!.text)
        vm.toastShown(toast.id)
        repo.accept(fixture("locked-error").put("sequence", 60))
        vm.onProjection()
        assertNull(vm.ui.value.toast)
    }

    @Test
    fun restingDropsTheHighlightAndTheToast() {
        val (repo, vm, _) = setUp("locked-error")
        repo.rest()
        vm.rested()
        assertTrue(vm.ui.value.frame is JournalFrame.Resting)
        assertNull(vm.ui.value.toast)
        assertNull(vm.ui.value.selection)
        assertFalse(vm.ui.value.toString().contains("CANARY"))
    }

    @Test
    fun theViewModelHoldsNoPrivateTextOnceLocked() {
        val (repo, vm, _) = setUp()
        vm.select("2026-10-27")
        assertTrue(vm.ui.value.toString().contains("CANARY"))
        repo.stop()
        vm.onProjection()
        assertNotNull(vm.ui.value.frame as? JournalFrame.Locked)
        assertFalse(vm.ui.value.toString().contains("CANARY"))
        assertNull(vm.ui.value.selection)
    }
}
