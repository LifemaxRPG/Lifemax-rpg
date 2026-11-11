package com.lifemax.rpg.core.lifedirector.timer

import com.lifemax.rpg.core.lifedirector.host.AwardReply
import com.lifemax.rpg.core.lifedirector.host.CommandReply
import com.lifemax.rpg.core.lifedirector.host.HostActivity
import com.lifemax.rpg.core.lifedirector.host.HostEffects
import com.lifemax.rpg.core.lifedirector.host.HostState
import com.lifemax.rpg.core.lifedirector.host.ImportedBackup
import com.lifemax.rpg.core.lifedirector.host.LoadedState
import com.lifemax.rpg.core.lifedirector.host.QuestCompletion
import com.lifemax.rpg.core.lifedirector.host.SaveOutcome
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonRead
import com.lifemax.rpg.core.save.JsonReader
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The timer, the wake calculator and the two-clock model checked by hand
 * (LIFE_DIRECTOR_TIMER_SPEC.md), with a hand-written fake host: the typed entry point's answers,
 * the instants, and that nothing throws.
 */
class TimerHandTest {
    private class FakeHost(var refuse: String? = null) : HostEffects {
        var saves = 0

        override fun connected() = true

        override fun toast(message: String) = true

        override fun playerId() = "player"

        override fun readQuests(
            now: Double,
            zone: String,
            overlay: JsonValue,
            revoked: JsonValue?,
        ) = emptyList<JsonValue>()

        override fun readPlanSessions(now: Double, zone: String, days: Double) =
            emptyList<JsonValue>()

        override fun awardActivity(event: JsonValue, title: String?) =
            AwardReply(true, activityId = "act-1", xp = 10.0)

        override fun completeQuest(hostRef: JsonValue) = QuestCompletion(false)

        override fun activitiesSince(cursor: String?) = emptyList<HostActivity>()

        override fun activityExists(id: String) = true

        override fun milestones() = emptyList<JsonValue>()

        override val resolvesRetiredQuests = false

        override fun retiredUntimedQuest(ref: JsonValue): JsonValue? = null

        override fun newId() = "id"

        override fun deviceZone() = "UTC"

        override fun loadState(playerId: String, zone: String, now: Double) =
            LoadedState(state(ZONE), "NEW")

        override fun saveState(state: JsonValue): SaveOutcome {
            saves++
            return refuse?.let { SaveOutcome(false, it) } ?: SaveOutcome.OK
        }

        override fun clearState(playerId: String, zone: String, now: Double) = SaveOutcome.OK

        override fun exportBackup(state: JsonValue) = "{}"

        override fun importBackup(text: String) = ImportedBackup(null, "no")
    }

    private val host = FakeHost()

    private fun run(s: HostState, at: Double, c: TimerCommand) = TimerMachine.run(s, at, host, c)

    private fun answer(o: TimerOutcome): CommandReply = (o.reply as TimerReply.Answer).reply

    private fun focus(s: HostState): JsonValue? =
        ((s.st as JsonObject)["ui"] as JsonObject)["focus"]

    private fun num(v: JsonValue?, k: String) = ((v as JsonObject)[k] as JsonNumber).value

    @Test
    fun startPauseResumeFinishLogsTheElapsedMinutes() {
        var s = HostState(st = state(ZONE))
        val t0 = (A + 60) * MS
        var o = run(s, t0, TimerCommand.Start(TimerCommand.id("s-a")))
        assertEquals(CommandReply(true, ""), answer(o))
        s = o.state
        assertEquals(t0, num(focus(s), "start"), 0.0)
        assertEquals(2_700_000.0, num(focus(s), "plannedMs"), 0.0)
        s = run(s, t0 + 600 * MS, TimerCommand.Pause).state
        s = run(s, t0 + 900 * MS, TimerCommand.Resume).state
        assertEquals(300_000.0, num(focus(s), "pausedMs"), 0.0)
        o = run(s, t0 + 1800 * MS, TimerCommand.Finish(TimerCommand.id("s-a"), TimerCommand.ms(t0)))
        assertEquals(CommandReply(true, ""), answer(o))
        assertEquals(JsonNull, focus(o.state))
        val history =
            (((o.state.st as JsonObject)["schedule"] as JsonObject)["historyInstances"]
                as JsonArray)
        assertEquals(25.0, num(history.items.single(), "actualMinutes"), 0.0)
        assertTrue(o.calls.map { it.fn }.containsAll(listOf("awardActivity", "store.save")))
        val again =
            run(
                o.state,
                t0 + 1860 * MS,
                TimerCommand.Finish(TimerCommand.id("s-a"), TimerCommand.ms(t0)),
            )
        assertEquals(CommandReply(true, TimerCommands.ALREADY, changed = false), answer(again))
    }

    @Test
    fun theCheckFinishesAtTheTargetWithThePlannedMinutes() {
        val t0 = A * MS
        val s =
            run(HostState(st = state(ZONE)), t0, TimerCommand.Start(TimerCommand.id("s-a"))).state
        val early = run(s, t0 + 2_700_000 - 1, TimerCommand.Check)
        assertEquals(TimerReply.Checked(FocusTick.Counting(2_699_999.0, 2_700_000.0)), early.reply)
        assertTrue(early.calls.isEmpty())
        val due = run(s, t0 + 9 * 3_600_000.0, TimerCommand.Check)
        assertEquals(TimerReply.Checked(FocusTick.AutoFinished(CommandReply(true, ""))), due.reply)
        val history =
            (((due.state.st as JsonObject)["schedule"] as JsonObject)["historyInstances"]
                as JsonArray)
        assertEquals(45.0, num(history.items.single(), "actualMinutes"), 0.0)
        assertEquals(
            TimerReply.Checked(FocusTick.Idle),
            run(due.state, t0 + 1, TimerCommand.Check).reply,
        )
        // a timer left on the session after it was done: found in history, told gone, kept (TM-61)
        val left = withFocus(due.state, focus(s))
        val gone = run(left, t0 + 9 * 3_600_000.0 + 1000, TimerCommand.Check)
        assertEquals(TimerReply.Checked(FocusTick.AutoFinished(CommandReply.GONE)), gone.reply)
        assertEquals(focus(s), focus(gone.state))
        assertTrue(gone.calls.isEmpty())
    }

    private fun withFocus(s: HostState, f: JsonValue?): HostState {
        val st = s.st as JsonObject
        val ui = st["ui"] as JsonObject
        return s.copy(
            st = JsonObject(st.members + ("ui" to JsonObject(ui.members + ("focus" to f!!))))
        )
    }

    @Test
    fun refusalsAndOddInputsAnswerWithoutThrowing() {
        val s = HostState(st = state(ZONE))
        val at = (A + 60) * MS
        assertEquals(
            CommandReply.GONE,
            answer(run(s, at, TimerCommand.Start(TimerCommand.id("nope")))),
        )
        assertEquals(
            CommandReply.CANNOT,
            answer(run(s, at, TimerCommand.Other(JsonString("explode")))),
        )
        assertEquals(CommandReply.CANNOT, answer(run(s, at, TimerCommand.Other(null))))
        assertEquals(
            CommandReply.no(TimerCommands.NOT_YET),
            answer(run(s, at, TimerCommand.Start(TimerCommand.id("s-c")))),
        )
        val running = run(s, at, TimerCommand.Start(TimerCommand.id("s-a"))).state
        assertEquals(
            CommandReply.no(TimerCommands.NOT_RUNNING),
            answer(
                run(running, at, TimerCommand.Cancel(TimerCommand.id("s-a"), TimerCommand.ms(1.0)))
            ),
        )
        assertEquals(
            CommandReply.no(TimerCommands.ANOTHER),
            answer(run(running, at, TimerCommand.Start(TimerCommand.id("s-b")))),
        )
        val noUi = JsonObject((state(ZONE) as JsonObject).members - "ui")
        val threw = run(HostState(st = noUi), at, TimerCommand.Pause).reply
        assertEquals("TypeError", (threw as TimerReply.Threw).name)
        assertEquals(
            TimerReply.Checked(FocusTick.Idle),
            run(HostState(st = noUi), at, TimerCommand.Check).reply,
        )
        val booted = run(HostState(), at, TimerCommand.Start(TimerCommand.id("s-a")))
        assertEquals(CommandReply(true, ""), answer(booted))
    }

    @Test
    fun aFailedSaveKeepsThePreviousTimerOnReplaceAndReset() {
        val at = A * MS
        val s =
            run(HostState(st = state(ZONE)), at, TimerCommand.Start(TimerCommand.id("s-a"))).state
        host.refuse = "full"
        val o =
            run(s, at + 60_000, TimerCommand.Replace(TimerCommand.id("s-b"), TimerCommand.ms(at)))
        assertEquals(CommandReply.no(TimerCommands.NOT_SAVED_NEW), answer(o))
        assertEquals(JsonString("s-a"), (focus(o.state) as JsonObject)["id"])
        val r = run(s, at + 60_000, TimerCommand.Reset(TimerCommand.id("s-a"), TimerCommand.ms(at)))
        assertEquals(CommandReply.no(TimerCommands.NOT_RESET), answer(r))
        assertEquals(at, num(focus(r.state), "start"), 0.0)
        host.refuse = null
    }

    @Test
    fun wakesOfARunningTimerAndAPlan() {
        val t0 = A * MS
        val s =
            run(HostState(st = state(ZONE)), t0, TimerCommand.Start(TimerCommand.id("s-a"))).state
        val plan = Wakes.of(s.st, t0 + 1000, "UTC", tickAnchorMs = t0 - 60_000)
        assertEquals(t0 + 2_700_000, plan.of(WakeKind.TIMER_END)!!.atMs, 0.0)
        assertEquals((A + 2700 + 3600 + 1) * MS, plan.of(WakeKind.MISS)!!.atMs, 0.0)
        assertEquals("s-a", plan.of(WakeKind.MISS)!!.sessionId)
        assertEquals(1_794_182_400_000.0, plan.of(WakeKind.WEEK_ROLLOVER)!!.atMs, 0.0)
        assertEquals((H0 + 7 * DAY - 2 * DAY + 1) * MS, plan.of(WakeKind.HORIZON_LOW)!!.atMs, 0.0)
        assertEquals((H0 + DAY) * MS, plan.of(WakeKind.DAY_END)!!.atMs, 0.0)
        assertEquals(t0 + 240_000, plan.of(WakeKind.TICK)!!.atMs, 0.0)
        assertEquals((B - 300) * MS, plan.of(WakeKind.SESSION_ALERT)!!.atMs, 0.0)
        assertEquals(WakeKind.TIMER_END, plan.nextBackground!!.kind)
        assertTrue(plan.due.isEmpty())
        assertEquals(plan.wakes.sortedBy { it.atMs }, plan.wakes)
        val paused = run(s, t0 + 1000, TimerCommand.Pause).state
        assertNull(Wakes.of(paused.st, t0 + 2000, "UTC").of(WakeKind.TIMER_END))
        val late = Wakes.of(s.st, t0 + 9 * 3_600_000.0, "UTC")
        assertEquals(listOf(WakeKind.TIMER_END, WakeKind.MISS), late.due.map { it.kind })
    }

    @Test
    fun wakesFollowTheZoneAndThePreferences() {
        val sydney = state("Australia/Sydney", prefs = """{"missGraceMinutes":15}""")
        val now = 1_793_700_000_000.0
        val w = Wakes.of(sydney, now, "UTC")
        // Monday 9 November 2026 00:00 in Sydney (UTC+11) is 8 November 13:00 UTC
        assertEquals(1_794_142_800_000.0, w.of(WakeKind.WEEK_ROLLOVER)!!.atMs, 0.0)
        val endA = (w.of(WakeKind.MISS)!!.atMs / MS) - 15 * 60 - 1
        assertEquals("s-a", w.of(WakeKind.MISS)!!.sessionId)
        assertTrue(endA > 0)
        // New York falls back on 1 November: the week still starts at Monday's local midnight (EST)
        val ny =
            Wakes.of(state("America/New_York", weekKey = "2026-10-26"), 1_793_500_000_000.0, "UTC")
        assertEquals(1_793_595_600_000.0, ny.of(WakeKind.WEEK_ROLLOVER)!!.atMs, 0.0)
        // the plan made in another zone is due now; so is a missing plan, and a stale week
        val moved = Wakes.of(state(ZONE, zoneOfPlan = "Europe/London"), now, "UTC")
        assertEquals(now, moved.of(WakeKind.REPLAN_NOW)!!.atMs, 0.0)
        val stale = Wakes.of(state(ZONE, weekKey = "2026-10-26"), now, "UTC")
        assertEquals(now, stale.of(WakeKind.WEEK_ROLLOVER)!!.atMs, 0.0)
        val notSetUp = Wakes.of(state(ZONE, setup = false), now, "UTC")
        assertNull(notSetUp.of(WakeKind.WEEK_ROLLOVER))
        assertTrue(Wakes.of(null, now, "UTC").wakes.isEmpty())
        assertTrue(Wakes.of(state("Not/AZone"), now, "UTC").of(WakeKind.WEEK_ROLLOVER) == null)
        assertEquals(1_000_000.0 + 600_000, Wakes.nextTick(1_000_000.0, 1_300_000.0), 0.0)
        assertEquals(1_000_000.0 + 300_000, Wakes.nextTick(1_000_000.0, 1_000_000.0), 0.0)
    }

    @Test
    fun theMonotonicClockSeesWhatTheWallClockMisses() {
        val start = ClockReading(wallMs = 1_000_000.0, monoMs = 50_000.0, boot = 3)
        val focus =
            obj("""{"id":"s-a","start":1000000,"paused":null,"pausedMs":0,"plannedMs":2700000}""")
        val record = FocusClock.follow(null, null, focus, start)!!
        assertEquals(FocusClock.begin(start), record)
        // 30 minutes later the owner sets the clock back an hour: the page counts nothing
        val back = ClockReading(1_000_000.0 + 1_800_000 - 3_600_000, 50_000.0 + 1_800_000, 3)
        val e = FocusClock.elapsed(focus, record, back)
        assertEquals(0.0, e.pageMs, 0.0)
        assertEquals(1_800_000.0, e.monoMs!!, 0.0)
        assertTrue(e.clockChanged())
        assertEquals(
            back.wallMs + 900_000,
            FocusClock.endAtWallMs(focus, record, 2_700_000.0, back)!!,
            0.0,
        )
        // pause and resume by the page's record; the monotonic one follows
        val paused =
            obj(
                """{"id":"s-a","start":1000000,"paused":1600000,"pausedMs":0,"plannedMs":2700000}"""
            )
        val p = FocusClock.follow(record, focus, paused, ClockReading(1_600_000.0, 650_000.0, 3))!!
        assertEquals(650_000.0, p.pausedAtMono!!, 0.0)
        val resumed =
            obj(
                """{"id":"s-a","start":1000000,"paused":null,"pausedMs":120000,"plannedMs":2700000}"""
            )
        val q = FocusClock.follow(p, paused, resumed, ClockReading(1_720_000.0, 770_000.0, 3))!!
        assertEquals(120_000.0, q.pausedTotalMs, 0.0)
        // a second pause adds to the first
        val again =
            obj(
                """{"id":"s-a","start":1000000,"paused":1800000,"pausedMs":120000,"plannedMs":2700000}"""
            )
        val q2 = FocusClock.follow(q, resumed, again, ClockReading(1_800_000.0, 850_000.0, 3))!!
        val back2 =
            obj(
                """{"id":"s-a","start":1000000,"paused":null,"pausedMs":180000,"plannedMs":2700000}"""
            )
        val q3 = FocusClock.follow(q2, again, back2, ClockReading(1_860_000.0, 910_000.0, 3))!!
        assertEquals(180_000.0, q3.pausedTotalMs, 0.0)
        val same = FocusClock.elapsed(resumed, q, ClockReading(1_900_000.0, 950_000.0, 3))
        assertEquals(same.pageMs, same.monoMs!!, 0.0)
        assertTrue(!same.clockChanged())
        // another boot: the monotonic reading means nothing; the page's is trusted
        val reboot = FocusClock.elapsed(resumed, q, ClockReading(1_900_000.0, 10.0, 4))
        assertNull(reboot.monoMs)
        assertEquals(reboot.pageMs, reboot.trustedMs, 0.0)
        assertNull(FocusClock.follow(q, resumed, JsonNull, ClockReading(2_000_000.0, 1.0, 4)))
        assertNull(FocusClock.endAtWallMs(paused, p, 2_700_000.0, start))
        assertEquals(2_700_000.0, FocusClock.plannedMs(focus)!!, 0.0)
    }

    companion object {
        private const val ZONE = "UTC"
        private const val MS = 1000.0
        private const val DAY = 86_400.0
        private const val H0 = 1_793_750_400.0 // 4 November 2026 00:00 UTC
        private const val A = H0 + 600 * 60 // s-a, 10:00
        private const val B = H0 + 1080 * 60 // s-b, 18:00

        private fun obj(text: String): JsonValue = (JsonReader.read(text) as JsonRead.Parsed).value

        private fun session(id: String, start: Double, minutes: Int) =
            """{"instanceId":"$id","taskId":"t-$id","templateId":"g-0:strength","title":"Session $id",""" +
                """"tag":"fitness.strength","kind":"GOAL","goalId":"g-0","linkedGoalIds":["g-0"],""" +
                """"linkedQuestRequirementIds":[],"startEpoch":${start.toLong()},""" +
                """"endEpoch":${(start + minutes * 60).toLong()},"durationSlots":${minutes / 15},""" +
                """"tier":3,"IP":4,"IC":1,"spacingKey":"g-0:strength","lifecycleState":"PLANNED",""" +
                """"isUserLocked":false,"revision":0,"explanation":{"tier":3,"summary":"x","factors":[]}}"""

        /** Life Director's state over a plan made by hand for 4 November 2026 in [zone]. */
        fun state(
            zone: String,
            prefs: String = "{}",
            zoneOfPlan: String = zone,
            weekKey: String = "2026-11-02",
            setup: Boolean = true,
        ): JsonValue {
            val shift =
                when (zone) {
                    "Australia/Sydney" -> -11 * 3600.0
                    "America/New_York" -> 5 * 3600.0
                    else -> 0.0
                }
            val h0 = H0 + shift
            val list =
                listOf(
                    session("s-a", h0 + 600 * 60, 45),
                    session("s-b", h0 + 1080 * 60, 30),
                    session("s-c", h0 + DAY + 600 * 60, 60),
                )
            return obj(
                """{"schemaVersion":1,"playerId":"player","setupComplete":$setup,""" +
                    """"availability":{"timezone":"$zone","sleepRequirementMinutes":480,"blackouts":[],"rules":[]},""" +
                    """"goals":[],"fixedCommitments":[],"prefs":$prefs,"changeLog":[],"questOverlay":{},""" +
                    """"hostSync":{"cursor":null,"seen":{}},"ledger":null,"lastGeneratedWeekKey":"$weekKey",""" +
                    """"ui":{"tab":"today"},"schedule":{"horizonStartEpoch":${h0.toLong()},""" +
                    """"horizonEndEpoch":${(h0 + 7 * DAY).toLong()},"timezone":"$zoneOfPlan",""" +
                    """"scheduledInstances":[${list.joinToString(",")}],"historyInstances":[]}}"""
            )
        }
    }
}
