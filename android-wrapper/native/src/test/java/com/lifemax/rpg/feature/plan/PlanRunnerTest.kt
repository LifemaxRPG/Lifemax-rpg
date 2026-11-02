package com.lifemax.rpg.feature.plan

import com.lifemax.rpg.feature.plan.data.WebPlanRepository
import com.lifemax.rpg.feature.plan.data.toCommand
import com.lifemax.rpg.feature.plan.data.toPlanReply
import com.lifemax.rpg.feature.plan.domain.PipState
import com.lifemax.rpg.feature.plan.domain.PlanCommands
import com.lifemax.rpg.feature.plan.domain.PlanReply
import com.lifemax.rpg.feature.plan.domain.PlanWrite
import com.lifemax.rpg.feature.plan.domain.PlanWrites
import com.lifemax.rpg.feature.plan.domain.RunnerPhase
import com.lifemax.rpg.feature.plan.domain.RunnerWords
import com.lifemax.rpg.feature.plan.presentation.FieldKind
import com.lifemax.rpg.feature.plan.presentation.PlanViewModel
import com.lifemax.rpg.feature.plan.presentation.runner.keepDecimal
import com.lifemax.rpg.feature.plan.presentation.runner.nudged
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Workout plan's guided workout, fields, Leave plan and sign-up offers: the page's projection
 * decoded strictly (fixtures from the web app, tools/gen_plan_runner_fixtures.js) and the writes
 * the screen sends, with the page's replies.
 */
class PlanRunnerTest {
    private fun fixture(name: String) =
        JSONObject(javaClass.getResource("/fixtures/plan-$name.json")!!.readText())

    private val replies = fixture("replies")

    private fun reply(key: String, requestId: String): PlanReply =
        JSONObject(replies.getJSONObject(key).toString())
            .put("requestId", requestId)
            .toPlanReply()!!

    private class Sent(val requestId: String, val operation: String, val write: PlanWrite)

    private data class Rig(
        val repo: WebPlanRepository,
        val vm: PlanViewModel,
        val sent: MutableList<Sent>,
    )

    private fun rig(name: String = "midweek"): Rig {
        val repo = WebPlanRepository().apply { accept(fixture(name), true) }
        val sent = mutableListOf<Sent>()
        var ids = 0
        var ops = 0
        val vm =
            PlanViewModel(
                repo,
                PlanCommands { _, _, _ -> },
                { "r-${++ids}" },
                { 0L },
                PlanWrites { id, op, write -> sent += Sent(id, op, write) },
                { "op-${++ops}" },
            )
        repo.state.value?.let(vm::adopt)
        return Rig(repo, vm, sent)
    }

    private val today = "2026-10-28"

    // ---- decoding ----

    @Test
    fun everyRunnerFixtureDecodesToItsPhase() {
        val phases =
            mapOf(
                "intro" to RunnerPhase.INTRO,
                "ready" to RunnerPhase.READY,
                "active" to RunnerPhase.ACTIVE,
                "active-refused" to RunnerPhase.ACTIVE,
                "check" to RunnerPhase.READY,
                "timing" to RunnerPhase.TIMING,
                "timer-done" to RunnerPhase.TIMER_DONE,
                "timer-done-early" to RunnerPhase.TIMER_DONE,
                "rest" to RunnerPhase.REST,
                "partial" to RunnerPhase.COMPLETE,
                "partial-after" to RunnerPhase.READY,
                "complete" to RunnerPhase.COMPLETE,
            )
        for ((name, phase) in phases) {
            val repo = WebPlanRepository().apply { accept(fixture("runner-$name"), true) }
            val r = repo.state.value!!.runner!!
            assertEquals(name, phase, r.phase)
            assertTrue(name, repo.state.value!!.features.runner)
            assertEquals(r.itinerary.rows.size, r.route.size)
        }
    }

    @Test
    fun theRunnerCarriesWhatEachScreenDraws() {
        fun runner(name: String) =
            WebPlanRepository()
                .apply { accept(fixture("runner-$name"), true) }
                .state
                .value!!
                .runner!!
        val intro = runner("intro")
        assertEquals(1, intro.intro!!.days.count { it.selected })
        assertEquals(intro.intro!!.log.size, intro.intro!!.exercises)
        val timing = runner("timing")
        assertEquals("Warm-up", timing.step!!.name)
        assertTrue(timing.step!!.timed)
        assertTrue(timing.timer!!.remainingMs in 1..timing.timer!!.totalMs)
        val rest = runner("rest")
        assertTrue(rest.rest!!.totalMs >= 15_000)
        assertEquals("Up next", rest.words["upNext"])
        val active = runner("active")
        assertEquals("Weight (lb)", active.words["weight"])
        assertNotNull(active.step!!.weight)
        assertEquals(5.0, active.step!!.weight!!.step, 0.0)
        assertTrue(active.step!!.pips.contains(PipState.CURRENT))
        val check = runner("check")
        assertTrue(check.step!!.isCheck)
        assertTrue(runner("complete").complete!!.full)
        assertFalse(runner("partial").complete!!.full)
        assertEquals(
            "Stopped early",
            runner("timer-done-early").words["stoppedEarly"].substringBefore("."),
        )
        assertFalse(runner("timer-done-early").review!!.full)
        assertTrue(runner("timer-done").review!!.full)
    }

    @Test
    fun everyWordTheScreensUseIsSentAndAMissingOneIsRefused() {
        val r =
            WebPlanRepository()
                .apply { accept(fixture("runner-ready"), true) }
                .state
                .value!!
                .runner!!
        for (key in RunnerWords.REQUIRED) assertTrue(key, r.words[key].isNotEmpty())
        val json = fixture("runner-ready")
        json.getJSONObject("runner").getJSONObject("words").remove("logSet")
        assertThrows(Exception::class.java) { WebPlanRepository().accept(json, true) }
    }

    @Test
    fun aRunnerThatContradictsItsPhaseIsRefusedAndTheLastStateKept() {
        val repo = WebPlanRepository().apply { accept(fixture("runner-ready"), true) }
        val before = repo.state.value
        val noStep = fixture("runner-ready").put("sequence", 2)
        noStep.getJSONObject("runner").put("step", JSONObject.NULL)
        assertThrows(Exception::class.java) { repo.accept(noStep) }
        val rest = fixture("runner-rest").put("sequence", 3)
        rest.getJSONObject("runner").put("rest", JSONObject.NULL)
        assertThrows(Exception::class.java) { repo.accept(rest) }
        val pip = fixture("runner-ready").put("sequence", 4)
        pip.getJSONObject("runner").getJSONObject("step").getJSONArray("pips").put(0, "sideways")
        assertThrows(Exception::class.java) { repo.accept(pip) }
        val kind = fixture("runner-ready").put("sequence", 5)
        kind.getJSONObject("runner").getJSONObject("step").put("kind", "furlongs")
        assertThrows(Exception::class.java) { repo.accept(kind) }
        assertEquals(before, repo.state.value)
    }

    @Test
    fun aPageWithoutTheseHandOversLooksLikeBefore() {
        val repo = WebPlanRepository().apply { accept(fixture("midweek"), true) }
        val old = JSONObject(fixture("midweek").toString())
        old.remove("features")
        old.remove("runner")
        old.remove("offer")
        old.remove("leaveAsk")
        old.remove("logMax")
        old.put("sequence", 2)
        repo.accept(old)
        val p = repo.state.value!!
        assertFalse(p.features.runner || p.features.fields || p.features.leave || p.features.offers)
        assertNull(p.runner)
        assertEquals(400, p.logMax)
    }

    @Test
    fun theOfferReplacesThePlanAndTheOtherWayRound() {
        val repo = WebPlanRepository().apply { accept(fixture("midweek"), true) }
        assertNotNull(repo.state.value)
        assertNull(repo.offers.value)
        val offer =
            fixture("offer-athlete")
                .put("sequence", 2)
                .put("webSessionId", repo.state.value!!.session)
        repo.accept(offer)
        assertNull(repo.state.value)
        val o = repo.offers.value!!.offer
        assertEquals("AVAILABLE PLAN", o.eyebrow)
        assertEquals("Sign up for this plan", o.button)
        assertEquals("Athlete · 8-Week Athletic Foundation Program", o.title)
        assertEquals(5, o.rows.size)
        assertEquals("2026-10-28", o.start)
        assertEquals("Tue · Sat", o.rows[1].whenText)
        // an older projection of the plan does not bring the plan back
        repo.accept(
            fixture("midweek").put("sequence", 1).put("webSessionId", repo.offers.value!!.session)
        )
        assertNull(repo.state.value)
        repo.accept(
            fixture("midweek").put("sequence", 3).put("webSessionId", repo.offers.value!!.session)
        )
        assertNotNull(repo.state.value)
        assertNull(repo.offers.value)
    }

    @Test
    fun everyClassOffersItsOwnWords() {
        val titles =
            listOf("athlete", "scholar", "explorer").map {
                WebPlanRepository()
                    .apply { accept(fixture("offer-$it"), true) }
                    .offers
                    .value!!
                    .offer
                    .title
            }
        assertEquals(3, titles.toSet().size)
        assertTrue(titles[1].startsWith("Scholar"))
        assertTrue(titles[2].startsWith("Explorer"))
    }

    @Test
    fun anOfferTheScreenWasNotToldToDrawIsRefused() {
        val json = fixture("offer-athlete")
        json.getJSONObject("features").put("offers", false)
        assertThrows(Exception::class.java) { WebPlanRepository().accept(json, true) }
        val noRows = fixture("offer-athlete")
        noRows.getJSONObject("offer").put("rows", org.json.JSONArray())
        assertThrows(Exception::class.java) { WebPlanRepository().accept(noRows, true) }
        val badDate = fixture("offer-athlete")
        badDate.getJSONObject("offer").put("start", "tomorrow")
        assertThrows(Exception::class.java) { WebPlanRepository().accept(badDate, true) }
    }

    @Test
    fun theWriteRepliesDecodeWithTheirCommandAndCelebration() {
        val last = reply("logLast", "x")
        assertEquals("runner", last.command)
        assertTrue(last.ok && last.changed)
        assertEquals(40, last.celebrations.single().xp)
        assertEquals("Physique", last.celebrations.single().gains.first().short)
        val repeat = reply("valueRepeat", "x")
        assertEquals("value", repeat.command)
        assertFalse(repeat.changed)
        assertEquals("Already recorded.", repeat.message)
        assertEquals("leave", reply("leave", "x").command)
        assertEquals("signUp", reply("signUp", "x").command)
        // a command the screen did not send is not its reply
        assertNull(JSONObject("""{"command":"other","requestId":"x","ok":true}""").toPlanReply())
    }

    // ---- fields ----

    @Test
    fun savingAFieldSendsThePagesCommandAndTheSheetClosesOnItsAnswer() {
        val (repo, vm, sent) = rig()
        val p = repo.state.value!!
        val day = p.day(today)!!
        val item = day.items.first { it.value != null }
        vm.actions.editField(day, item, FieldKind.VALUE)
        val edit = vm.ui.value.edit!!
        assertEquals("value", edit.kind.command)
        assertEquals(item.value!!.text, edit.draft)
        vm.actions.draft("72.5 lb")
        vm.actions.saveEdit()
        val s = sent.single()
        assertEquals("value", s.write.command)
        assertEquals("72.5 lb", s.write.args["text"])
        assertEquals(today, s.write.args["date"])
        assertEquals(item.key, s.write.args["key"])
        assertTrue(vm.ui.value.busy)
        vm.onReply(reply("value", s.requestId))
        assertNull(vm.ui.value.edit)
        assertFalse(vm.ui.value.busy)
    }

    @Test
    fun aRefusedFieldStaysOpenWithThePagesWords() {
        val (repo, vm, sent) = rig()
        val day = repo.state.value!!.day(today)!!
        val item = day.items.first()
        vm.actions.editField(day, item, FieldKind.LOG)
        vm.actions.draft("x".repeat(401))
        assertEquals(400, vm.ui.value.edit!!.draft.length) // the box stops at the page's limit
        vm.actions.saveEdit()
        vm.onReply(reply("logTooLong", sent.single().requestId))
        assertEquals(
            "A session log holds 400 characters at most. Nothing was changed.",
            vm.ui.value.edit!!.error,
        )
        assertNull(vm.ui.value.feedback) // told on the sheet, not twice
        vm.actions.draft("shorter")
        assertNull(vm.ui.value.edit!!.error)
    }

    @Test
    fun aLockedDayOpensNoField() {
        val (repo, vm, _) = rig()
        val future =
            repo.state.value!!
                .weeks
                .flatMap { it.days }
                .filterNotNull()
                .first { it.locked && !it.rest }
        vm.actions.editField(future, future.items.first { it.value != null }, FieldKind.VALUE)
        assertNull(vm.ui.value.edit)
    }

    @Test
    fun anExerciseWithoutAValueBoxOpensNoValueField() {
        val (repo, vm, _) = rig()
        val day = repo.state.value!!.day(today)!!
        vm.actions.editField(day, day.items.first { it.value == null }, FieldKind.VALUE)
        assertNull(vm.ui.value.edit)
    }

    // ---- operations ----

    @Test
    fun aWriteWhoseAnswerWasLostIsSentAgainWithTheSameOperation() {
        val (repo, vm, sent) = rig()
        val day = repo.state.value!!.day(today)!!
        val item = day.items.first { it.value != null }
        vm.actions.editField(day, item, FieldKind.VALUE)
        vm.actions.draft("70 lb")
        vm.actions.saveEdit()
        vm.onOpen() // the screen hid and came back before the answer
        vm.actions.editField(day, item, FieldKind.VALUE)
        vm.actions.draft("70 lb")
        vm.actions.saveEdit()
        assertEquals(2, sent.size)
        assertEquals(sent[0].operation, sent[1].operation)
        vm.onReply(reply("value", sent[1].requestId))
        vm.actions.editField(day, item, FieldKind.VALUE)
        vm.actions.saveEdit()
        assertNotEquals(sent[1].operation, sent[2].operation) // after an answer the next tap is new
    }

    @Test
    fun oneWriteAtATime() {
        val (repo, vm, sent) = rig()
        val day = repo.state.value!!.day(today)!!
        vm.actions.editField(day, day.items.first { it.value != null }, FieldKind.VALUE)
        vm.actions.saveEdit()
        vm.actions.saveEdit()
        vm.actions.askLeave()
        vm.actions.leave()
        assertEquals(1, sent.size)
        // an answer to something else settles nothing
        vm.onReply(reply("leave", "not-mine"))
        assertTrue(vm.ui.value.busy)
        vm.onReply(reply("value", sent[0].requestId))
        assertFalse(vm.ui.value.busy)
    }

    @Test
    fun theCommandCarriesTheOperationAndNullsAsJson() {
        val (name, payload) =
            PlanWrite("runner", "log:x", mapOf("act" to "log", "amount" to "8", "weight" to null))
                .toCommand("op-9")
        assertEquals("runner", name)
        assertEquals("op-9", payload.getString("op"))
        assertEquals("log", payload.getString("act"))
        assertEquals("8", payload.getString("amount"))
        assertTrue(payload.isNull("weight"))
    }

    // ---- Leave plan and the sign-up ----

    @Test
    fun leavingAsksInThePagesWordsAndSendsLeaveOnce() {
        val (repo, vm, sent) = rig()
        assertEquals(
            "Leave this plan? Your checklist progress for the plan is removed. XP you already earned stays.",
            repo.state.value!!.leaveAsk,
        )
        vm.actions.askLeave()
        assertTrue(vm.ui.value.leaving)
        vm.actions.cancelLeave()
        assertFalse(vm.ui.value.leaving)
        assertTrue(sent.isEmpty())
        vm.actions.askLeave()
        vm.actions.leave()
        assertEquals("leave", sent.single().write.command)
        vm.onReply(reply("leave", sent.single().requestId))
        assertFalse(vm.ui.value.leaving)
        assertEquals("Plan ended", vm.ui.value.feedback!!.message)
    }

    @Test
    fun signingUpSendsTheStartDateChosen() {
        val (repo, vm, sent) = rig("offer-athlete")
        val offer = repo.offers.value!!.offer
        vm.actions.signUp(offer.start)
        assertEquals(offer.start, sent[0].write.args["start"])
        vm.onReply(reply("signUpRefused", sent[0].requestId))
        assertEquals("Choose a start date.", vm.ui.value.feedback!!.message)
        vm.actions.startDate("2026-11-04")
        vm.actions.signUp(offer.start)
        assertEquals("2026-11-04", sent[1].write.args["start"])
        vm.onReply(reply("signUp", sent[1].requestId))
        assertEquals("Plan started · good luck!", vm.ui.value.feedback!!.message)
    }

    // ---- the runner ----

    private fun runnerRig(name: String): Rig = rig("runner-$name")

    @Test
    fun theLauncherStartsTheDayOrResumesTheSavedSession() {
        val (_, vm, sent) = rig()
        vm.actions.launch(today, resume = false)
        vm.actions.launch(today, resume = true) // the first is still awaited
        val s = sent.single()
        assertEquals("runner", s.write.command)
        assertEquals("start", s.write.args["act"])
        assertEquals(today, s.write.args["date"])
        vm.onReply(reply("start", s.requestId))
        vm.actions.launch(today, resume = true)
        assertEquals("resume", sent[1].write.args["act"])
    }

    @Test
    fun logSendsTheBoxesAsTheyStandAndTheSuggestionsUntilTyped() {
        val (repo, vm, sent) = runnerRig("active")
        val step = repo.state.value!!.runner!!.step!!
        vm.actions.log()
        val first = sent.single().write.args
        assertEquals("log", first["act"])
        assertEquals(step.suggest, first["amount"])
        assertEquals(step.weight!!.suggest, first["weight"])
        vm.onReply(reply("logRefused", sent.single().requestId))
        assertEquals("Enter what you actually completed", vm.ui.value.runner.error)
        vm.actions.amount("9")
        vm.actions.weight("67.5")
        assertNull(vm.ui.value.runner.error)
        vm.actions.log()
        val typed = sent[1].write.args
        assertEquals("9", typed["amount"])
        assertEquals("67.5", typed["weight"])
        // a failed first try's operation is not reused: the answer ended it
        assertNotEquals(sent[0].operation, sent[1].operation)
    }

    @Test
    fun aSetThatOnlyNeedsConfirmingSendsNoAmount() {
        val (repo, vm, sent) = runnerRig("check")
        assertTrue(repo.state.value!!.runner!!.step!!.isCheck)
        vm.actions.go()
        assertEquals("go", sent.single().write.args["act"])
        assertFalse(sent.single().write.args.containsKey("amount"))
    }

    @Test
    fun aTimerWithNoLengthAsksForOneBeforeItStarts() {
        val json = fixture("runner-ready")
        val step = json.getJSONObject("runner").getJSONObject("step")
        step.put("askDuration", true)
        val repo = WebPlanRepository().apply { accept(json, true) }
        val sent = mutableListOf<Sent>()
        val vm =
            PlanViewModel(
                repo,
                PlanCommands { _, _, _ -> },
                { "r" },
                { 0L },
                PlanWrites { id, op, w -> sent += Sent(id, op, w) },
            )
        vm.actions.go()
        assertTrue(sent.isEmpty())
        assertEquals(
            step.getJSONObject("durationAsk").getInt("start").toString(),
            vm.ui.value.runner.duration,
        )
        vm.actions.duration("7")
        vm.actions.go()
        assertEquals("7", sent.single().write.args["amount"])
        vm.actions.cancelDuration()
        assertNull(vm.ui.value.runner.duration)
    }

    @Test
    fun skipAndSwapAskFirstAndTheCelebrationOfTheLastSetIsKept() {
        val (repo, vm, sent) = runnerRig("ready")
        vm.actions.askSkip()
        assertTrue(vm.ui.value.runner.confirmSkip)
        assertTrue(repo.state.value!!.runner!!.step!!.skipAsk.startsWith("Skip "))
        vm.actions.cancelSkip()
        assertFalse(vm.ui.value.runner.confirmSkip)
        vm.actions.askSwap()
        assertEquals(repo.state.value!!.runner!!.step!!.swapFrom, vm.ui.value.runner.swapping)
        vm.actions.swapDraft("Leg press machine")
        vm.actions.swap()
        assertEquals("Leg press machine", sent.single().write.args["name"])
        vm.onReply(reply("start", sent.single().requestId))
        assertNull(vm.ui.value.runner.swapping)
        // the last set's answer carries the XP for the screen to draw
        vm.actions.pause()
        vm.onReply(reply("logLast", sent[1].requestId))
        assertEquals(40, vm.ui.value.feedback!!.awards.single().xp)
    }

    @Test
    fun theTypedAmountBelongsToItsSet() {
        val (_, vm, sent) = runnerRig("active")
        vm.actions.amount("12")
        assertEquals("12", vm.ui.value.runner.amount)
        // the drafts are keyed by the set they were typed for; another set's token starts clean
        assertTrue(vm.ui.value.runner.token.startsWith("active|"))
        vm.actions.log()
        assertEquals("12", sent.single().write.args["amount"])
    }

    // ---- the page's steppers ----

    @Test
    fun theSteppersStepAsThePagesDo() {
        // the same cases as the page's gwrNudged (native-plan-runner.test.js)
        assertEquals("5", nudged("", 5.0, 0.0))
        assertEquals("55", nudged("60", -5.0, 0.0))
        assertEquals("0", nudged("2", -5.0, 0.0))
        assertEquals("7.8", nudged("7.25", 0.5, 1.0))
        assertEquals("102.5", nudged("100", 2.5, 0.0))
        assertEquals("1", nudged("0.2", -1.0, 1.0))
        assertEquals("12", keepDecimal("1a2"))
        assertEquals("1.25", keepDecimal("1.2.5"))
    }
}
