package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP6d's session behaviour beyond the goldens: a page that lies about a sync or a decay pass is
 * caught at the field it lied about; Health Connect asked otherwise than the page asked it is a
 * difference of its own (`reads.*`); each reason not to compare (an older page's shapes, a clock
 * outside the action, midnight, a short plan window, an interleaving action, a lost envelope) is
 * counted with the copy still in step; `info.clockJump`; no workout title, app or record name in
 * the ring; and a sync and a decay pass on a large ledger inside the worker's budget.
 */
class ShadowDaySessionTest {
    private val all = GoldenActions.build()

    private fun case(id: String) = all.last { it.caseId == id && it.recorded }

    /** A fresh session that has the action's before-state, and the ledger after [actions]. */
    private fun run(vararg actions: FakeShadowPage.Action): ShadowLedger {
        val s = ShadowGoldenCompareTest.session()
        val page = FakeShadowPage()
        for (part in page.base(actions.first().before)) assertTrue(s.base(part, 0).ok)
        for (a in actions) {
            GoldenActions.CONTENT.setup = GoldenActions.ECONOMY
            val text = page.send(a)
            assertTrue(s.accept(text.length, false).accepted)
            s.observe(text, FakeShadowPage.T0)
        }
        return s.ledger
    }

    private fun lie(a: FakeShadowPage.Action, key: String, change: (JsonValue?) -> JsonValue) =
        a.copy(after = a.after + (key to change(a.after[key])))

    private fun JsonValue?.members() = (this as? JsonObject)?.members.orEmpty()

    private fun health(
        a: FakeShadowPage.Action,
        change: (Map<String, JsonValue>) -> Map<String, JsonValue>,
    ) = a.copy(env = a.env + ("health" to JsonObject(change(a.env.getValue("health").members()))))

    @Test
    fun theGoldenActionsUsedHereMatchAsSent() {
        for (id in listOf(STEPS, WORKOUTS, WEIGHT, DECAY, SHIELD)) {
            val l = run(case(id).action)
            assertEquals(id, 1, l.compared)
            assertEquals(id, 0, l.different)
        }
    }

    @Test
    fun aPageThatLiesIsCaughtAtTheFieldItLiedAbout() {
        val sync = case(STEPS).action
        val pass = case(DECAY).action
        val tick = case(SHIELD).action
        val lies =
            listOf(
                lie(sync, "health") {
                    JsonObject(it.members() + ("msg" to JsonString("Up to date.")))
                } to Regex("health\\.msg"),
                lie(sync, "health") {
                    val seen = it.members()["seen"].members() + ("p2026-01-01" to JsonNumber(1.0))
                    JsonObject(it.members() + ("seen" to JsonObject(seen)))
                } to Regex("health\\.seen\\.\\*#[0-9a-f]{6}"),
                lie(sync, "activities") {
                    val items = (it as JsonArray).items
                    val last = items.last() as JsonObject
                    JsonArray(
                        items.dropLast(1) + JsonObject(last.members + ("xp" to JsonNumber(99.0)))
                    )
                } to Regex("activities\\[\\+\\d+]\\.xp"),
                sync.copy(toasts = listOf("Health sync: nothing new")) to Regex("toasts\\[0]"),
                lie(case(WEIGHT).action, "weight") { JsonNumber(170.0) } to Regex("weight"),
                pass.copy(returns = JsonBool.FALSE) to Regex("returns"),
                lie(pass, "decayApplied") {
                    JsonObject(it.members() + ("physique" to JsonNumber(1.0)))
                } to Regex("decayApplied\\.physique"),
                lie(pass, "restedXP") { JsonNumber(299.0) } to Regex("restedXP"),
                lie(tick, "shieldDays") { JsonObject(mapOf("2026-10-26" to JsonBool.TRUE)) } to
                    Regex("shieldDays.*"),
                tick.copy(
                    kind = "boot.decay",
                    returns = com.lifemax.rpg.core.save.JsonNull,
                    saves = listOf(true, true),
                ) to Regex("saves\\.count"),
            )
        for ((action, path) in lies) {
            val l = run(action)
            assertEquals("${action.kind} $path compared", 1, l.compared)
            assertEquals("${action.kind} $path unexplained", 1, l.different)
            val m = l.mismatches().single()
            assertTrue("${m.paths} names $path", m.paths.any(path::matches))
            assertTrue("no allowlist entry for $path", m.allow.isEmpty())
        }
    }

    @Test
    fun healthConnectAskedOtherwiseIsADifference() {
        val sync = case(STEPS).action
        // the page asked for steps over a day less
        val range =
            health(sync) { h ->
                val read = ((h["steps"] as JsonArray).items[0] as JsonObject).members
                h +
                    ("steps" to
                        JsonArray(
                            listOf(
                                JsonObject(
                                    read +
                                        ("from" to
                                            JsonNumber((read["from"] as JsonNumber).value + DAY))
                                )
                            )
                        ))
            }
        assertEquals(listOf("reads.steps.range"), run(range).mismatches().single().paths)
        // the page read the weight, which native's sync (weight switched off in the case) does not
        val extra =
            health(sync) { h ->
                val now = (h["now"] as JsonNumber)
                h +
                    ("weight" to
                        JsonArray(
                            listOf(
                                FakeShadowPage.obj(
                                    "from" to JsonNumber(now.value - 60 * DAY),
                                    "to" to now,
                                    "answer" to JsonObject.EMPTY,
                                )
                            )
                        ))
            }
        val l = run(extra)
        assertEquals(1, l.different)
        assertEquals(listOf("reads.weight"), l.mismatches().single().paths)
    }

    @Test
    fun whatCannotBeComparedIsCountedAndTheCopyStaysInStep() {
        val sync = case(STEPS).action
        val pass = case(DECAY).action
        val skips =
            listOf(
                health(sync) { it - "plugin" } to
                    "input", // a page older than economy.shadow.day.v1
                health(sync) { it + ("mood" to JsonString("x")) } to "input",
                health(sync) {
                    it - listOf("exercise", "steps", "sleep", "weight") +
                        ("now" to JsonNumber(sync.t0 + 10.0 * DAY))
                } to "input",
                sync.copy(t0 = MIDNIGHT - 2, t1 = MIDNIGHT + 2) to "midnight",
                pass.copy(
                    kind = "boot.decay",
                    env = pass.env - "plan",
                    returns = com.lifemax.rpg.core.save.JsonNull,
                    saves = listOf(true),
                ) to "input",
                pass.copy(env = pass.env + ("plan" to today(pass))) to "planWindow",
                sync.copy(flags = listOf(ShadowFlags.NESTED_ASYNC, ShadowFlags.INTERLEAVED)) to
                    "interleaved",
            )
        for ((action, why) in skips) {
            val after = case(WEIGHT).action
            val l = run(action, after)
            assertEquals(
                "$why: ${l.kind(action.kind).skipped}",
                1,
                l.kind(action.kind).skipped[why],
            )
            assertEquals("$why: the next action is compared", 1, l.kind(after.kind).compared)
            assertEquals(0, l.different)
        }
    }

    @Test
    fun aLostEnvelopeAsksForABaseAndTheNextAfterItIsCompared() {
        val s = ShadowGoldenCompareTest.session()
        val page = FakeShadowPage()
        val a = case(DECAY).action
        for (part in page.base(a.before)) s.base(part, 0)
        page.send(a) // never delivered
        val text = page.send(case(STEPS).action)
        s.observe(text, FakeShadowPage.T0)
        assertEquals(0, s.ledger.compared)
        assertTrue(!s.accept(text.length, false).accepted)
        for (part in page.base(case(SHIELD).action.before)) s.base(part, 0)
        s.observe(page.send(case(SHIELD).action), FakeShadowPage.T0)
        assertEquals(1, s.ledger.compared)
        assertEquals(0, s.ledger.different)
    }

    @Test
    fun aClockMovedBackIsNotedAndAnAsyncSyncIsNot() {
        val tick = case(SHIELD).action
        val sync = case(STEPS).action
        val later = tick.copy(t0 = tick.t0 + HOUR, t1 = tick.t0 + HOUR)
        val back =
            tick.copy(
                before = tick.after,
                after = tick.after,
                t0 = tick.t0 - HOUR,
                t1 = tick.t0 - HOUR,
                toasts = emptyList(),
            )
        // the sync started before the pass that ran while it waited
        val waited = sync.copy(before = tick.after, flags = listOf(ShadowFlags.NESTED_ASYNC))
        val l = run(later, waited, back)
        assertEquals(1, l.info["clockJump"])
    }

    @Test
    fun noWorkoutTitleAppOrRecordNameReachesTheRing() {
        val words =
            listOf(
                "Morning flow",
                "shealth",
                "Samsung Health",
                "Google Fit",
                "Garmin",
                "w-run",
                "z-night",
                "Seed",
                "Daily steps",
            )
        // every sync of the corpus made to differ in its record names and its message
        val mutation = EntryMutation { kind, _, run, _ ->
            if (kind != "health.sync") run
            else
                EntryRun(
                    run.save.copy(
                        activities =
                            run.save.activities.map { it.copy(name = it.name + " Garmin") },
                        health = run.save.health.copy(message = "Morning flow"),
                    ),
                    run.returns,
                    run.toasts,
                )
        }
        val replayed = ShadowDayCorpusTest.replayAll(mutation)
        assertTrue(replayed.sumOf { it.ledger.different } > 0)
        for (r in replayed) {
            val ring = ShadowLedgerJson.write(r.ledger)
            for (w in words) assertTrue("$w in ${r.name}'s ring", w !in ring)
        }
    }

    @Test
    fun aSyncAndADecayPassOnALargeLedgerStayInsideTheBudget() {
        val sync = case(WORKOUTS).action
        val pass = case(DECAY).action
        // old records outside any rule these actions apply: no attribute, an existing skill, 0 XP
        fun records(skill: String) =
            (0 until LARGE).map { i ->
                FakeShadowPage.obj(
                    "id" to JsonString("big-$i"),
                    "type" to JsonString("reading"),
                    "name" to JsonString("Old"),
                    "duration" to JsonNumber(30.0),
                    "quality" to JsonNumber(1.0),
                    "skill" to JsonString(skill),
                    "primary" to JsonString(""),
                    "secondary" to JsonString(""),
                    "xp" to JsonNumber(0.0),
                    "date" to JsonString("2025-0${1 + i % 9}-1${i % 9}T09:00:00.000Z"),
                    "xpSplit" to JsonArray.EMPTY,
                    // already counted for Sparks, as an old record is
                    "sp" to JsonNumber(1.0),
                )
            }
        for (a in listOf(sync, pass)) {
            val old = records(a.after["skillXP"].members().keys.firstOrNull().orEmpty())
            fun big(state: Map<String, JsonValue>) =
                state +
                    ("activities" to
                        JsonArray(old + (state["activities"] as? JsonArray)?.items.orEmpty()))
            val action = a.copy(before = big(a.before), after = big(a.after))
            val costs = ArrayList<Long>()
            repeat(ROUNDS) {
                val s = ShadowGoldenCompareTest.session()
                val page = FakeShadowPage()
                for (part in page.base(action.before)) s.base(part, 0)
                val text = page.send(action)
                val t = System.nanoTime()
                s.observe(text, FakeShadowPage.T0)
                costs += (System.nanoTime() - t) / NANOS_PER_MS
                assertEquals(1, s.ledger.compared)
                assertEquals(
                    "${a.kind} ${s.ledger.mismatches().map { it.paths }}",
                    0,
                    s.ledger.different,
                )
            }
            val sorted = costs.sorted()
            val p95 = sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.size - 1)]
            println("${a.kind} on $LARGE records: p50 ${sorted[sorted.size / 2]} ms, p95 $p95 ms")
            assertTrue("p95 $p95 ms under the 2 s limit", p95 < SLOW_LIMIT_MS)
        }
    }

    /** `env.plan` for today alone: too short for a walk over idle days. */
    private fun today(a: FakeShadowPage.Action): JsonObject {
        val plan = a.env.getValue("plan") as JsonObject
        val day =
            java.time.Instant.ofEpochMilli(a.t0)
                .atZone(java.time.ZoneOffset.UTC)
                .toLocalDate()
                .toString()
        return JsonObject(plan.members + ("from" to JsonString(day)) + ("rest" to JsonArray.EMPTY))
    }

    private companion object {
        const val STEPS = "health/steps"
        const val WORKOUTS = "health/workout-kinds"
        const val WEIGHT = "health/weight"
        const val DECAY = "decay/rested-after-7-idle"
        const val SHIELD = "shield/gap-covered"
        const val DAY = 86_400_000.0
        const val HOUR = 3_600_000L
        /** Midnight UTC after the goldens' clock (2026-10-28T12:00Z). */
        const val MIDNIGHT = 1_793_232_000_000L
        const val LARGE = 10_000
        const val ROUNDS = 12
        const val NANOS_PER_MS = 1_000_000L
        const val SLOW_LIMIT_MS = 2_000L
    }
}
