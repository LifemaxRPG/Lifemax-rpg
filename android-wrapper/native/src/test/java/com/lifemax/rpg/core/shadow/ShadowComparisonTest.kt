package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.economy.FixedAwards
import com.lifemax.rpg.core.economy.SavePipeline
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The comparison's rules one by one (ECONOMY_SHADOW_PLAN 1.3, 1.4, 5.2), on golden actions the page
 * really performed: a page that lies about its outcome is caught at the right path; each allowlist
 * entry explains only what it names (and none is dead); the normalisations N1 to N4; every reason
 * an action is not compared, with the mirror still in step afterwards; internal errors counted and
 * the check disabled after five.
 */
class ShadowComparisonTest {
    private val built = GoldenActions.build()

    /** A golden Log that stored a new record. */
    private val log =
        built.first {
            it.recorded &&
                it.action.kind == "log.record" &&
                it.action.saves.all { ok -> ok } &&
                records(it.action.after) > records(it.action.before)
        }

    private fun records(state: Map<String, JsonValue>) =
        (state["activities"] as? JsonArray)?.items?.size ?: 0

    private fun session(content: ShadowContent = GoldenActions.CONTENT) =
        ShadowSession(
                ShadowSessionTest.FP,
                ShadowLedger(
                    ShadowSessionTest.T,
                    ByteArray(ShadowLimits.SALT_BYTES) { it.toByte() },
                ),
                1,
                109,
                content,
            )
            .also { it.configure(ShadowSessionTest.configure(ShadowSessionTest.FP), false) }

    /** Sends [actions] in one page session; the first one's before-state is the base. */
    private fun run(
        vararg actions: FakeShadowPage.Action,
        content: ShadowContent = GoldenActions.CONTENT,
    ): ShadowSession {
        GoldenActions.CONTENT.setup = GoldenActions.ECONOMY
        val s = session(content)
        val page = FakeShadowPage()
        for (part in page.base(actions.first().before)) assertTrue(s.base(part, 0).ok)
        for (a in actions) {
            val text = page.send(a)
            assertTrue(s.accept(text.length, false).accepted)
            s.observe(text, FakeShadowPage.T0)
        }
        return s
    }

    private fun lastPaths(s: ShadowSession) = s.ledger.mismatches().last().paths

    private fun FakeShadowPage.Action.afterWith(key: String, value: JsonValue?) =
        copy(after = if (value == null) after - key else after + (key to value))

    private fun newRecord(
        a: FakeShadowPage.Action,
        change: (Map<String, JsonValue>) -> Map<String, JsonValue>,
    ): FakeShadowPage.Action {
        val items = (a.after["activities"] as JsonArray).items
        val last = (items.last() as JsonObject).members
        return a.afterWith("activities", JsonArray(items.dropLast(1) + JsonObject(change(last))))
    }

    @Test
    fun theGoldenLogMatchesAsRecorded() {
        val s = run(log.action)
        assertEquals(1, s.ledger.compared)
        assertEquals(1, s.ledger.same)
    }

    @Test
    fun aPageThatLiesIsCaughtAtThePath() {
        val sparks = (log.action.after["sparks"] as JsonNumber).value
        val lies =
            listOf(
                log.action.afterWith("sparks", JsonNumber(sparks + 1)) to "sparks",
                log.action.copy(toasts = log.action.toasts + "Saved!") to "toasts[#]",
                log.action.copy(
                    returns =
                        JsonObject(
                            (log.action.returns as JsonObject).members + ("xp" to JsonNumber(99.0))
                        )
                ) to "returns.xp",
                log.action.copy(saves = log.action.saves + true) to "saves.count",
                newRecord(log.action) { it + ("xp" to JsonNumber(1.0)) } to "activities[+1].xp",
                newRecord(log.action) { it + ("notes" to JsonString("a private note")) } to
                    "activities[+1].notes",
                log.action.afterWith("activities", log.action.before["activities"]) to
                    "activities[#]",
                log.action.afterWith("restedXP", JsonNumber(999.0)) to "restedXP",
            )
        for ((lie, path) in lies) {
            val s = run(lie)
            assertEquals(path, 1, s.ledger.different)
            assertTrue("$path in ${lastPaths(s)}", path in lastPaths(s))
            assertTrue(s.ledger.mismatches().last().unexplained)
        }
    }

    @Test
    fun eachAllowlistEntryExplainsOnlyWhatItNames() {
        val today = "2026-10-28"
        val coach =
            JsonObject(
                mapOf(
                    "days" to JsonObject(mapOf(today to JsonObject(mapOf("seen" to JsonBool.TRUE))))
                )
            )
        val failed =
            built.first {
                it.recorded &&
                    it.action.kind == "log.record" &&
                    it.action.saves.any { ok -> !ok } &&
                    it.action.toasts.isNotEmpty()
            }
        val unsaved =
            failed.action.toasts.last {
                it == SavePipeline.SAVE_FULL || it == SavePipeline.SAVE_READ_ONLY
            }
        val save =
            built
                .first { it.action.kind == "save.other" && it.action.saves.all { ok -> ok } }
                .action
        val withoutMoney =
            save.copy(
                before = save.before - "money",
                after = save.after + ("money" to JsonObject.EMPTY),
            )
        val withoutFood =
            save.copy(
                before = save.before - "foodLog",
                after = save.after + ("foodLog" to JsonArray.EMPTY),
            )
        val cases =
            listOf(
                log.action.copy(
                    before =
                        log.action.before +
                            ("journalCoach" to JsonObject(mapOf("days" to JsonObject.EMPTY))),
                    after = log.action.after + ("journalCoach" to coach),
                ) to "A-01",
                log.action.copy(
                    before = log.action.before + ("plan" to plan(today, JsonObject.EMPTY)),
                    after =
                        log.action.after +
                            ("plan" to
                                plan(
                                    today,
                                    JsonObject(mapOf("C1" to JsonArray(listOf(JsonBool.TRUE)))),
                                )),
                ) to "A-02",
                withoutMoney to "A-03",
                failed.action.copy(toasts = failed.action.toasts + unsaved) to "A-04",
                withoutFood to "A-05",
                log.action.copy(
                    env =
                        log.action.env +
                            ("journal" to
                                JsonArray(listOf(FakeShadowPage.obj("ok" to JsonBool.TRUE))))
                ) to "A-06",
            )
        val seen = sortedSetOf<String>()
        for ((action, id) in cases) {
            val s = run(action)
            assertEquals(id, 0, s.ledger.different)
            assertEquals(id, 1, s.ledger.allowed)
            assertEquals(id, listOf(id), s.ledger.mismatches().last().allow)
            seen += id
        }
        assertEquals(
            "no allowlist entry is dead",
            KnownDifferences.ENTRIES.map { it.id }.toSortedSet(),
            seen,
        )
        // the same money created where the before-state had one, or by another kind, is unexplained
        assertEquals(1, run(save.afterWith("money", JsonObject.EMPTY)).ledger.different)
        assertEquals(
            1,
            run(
                    log.action.copy(
                        before = log.action.before - "money",
                        after = log.action.after + ("money" to JsonObject.EMPTY),
                    )
                )
                .ledger
                .different,
        )
        // a "not saved" said twice where native said it once is not the only difference allowed
        assertEquals(
            1,
            run(failed.action.copy(toasts = failed.action.toasts + unsaved + "and more"))
                .ledger
                .different,
        )
    }

    private fun plan(day: String, ticks: JsonObject) =
        JsonObject(
            mapOf(
                "checks" to
                    JsonObject(
                        mapOf(day to JsonObject(mapOf("s" to ticks, "v" to JsonObject.EMPTY)))
                    )
            )
        )

    @Test
    fun instantsAndClockIdsMayLieInTheActionsWindow() {
        val t0 = log.action.t0
        val late = java.time.Instant.ofEpochMilli(t0 + 3).toString()
        val inWindow = newRecord(log.action) { it + ("date" to JsonString(late)) }.copy(t1 = t0 + 5)
        assertEquals(1, run(inWindow).ledger.same)
        val outside = inWindow.copy(t1 = t0 + 2)
        assertTrue("activities[+1].date" in lastPaths(run(outside)))
        val care =
            built
                .first {
                    it.recorded &&
                        it.action.kind == "bonus.care" &&
                        records(it.action.after) > records(it.action.before)
                }
                .action
        val laterId =
            newRecord(care) { it + ("id" to JsonString("care_${care.t0 + 4}")) }
                .copy(t1 = care.t0 + 4)
        assertEquals(1, run(laterId).ledger.same)
        assertTrue("activities[+1].id" in lastPaths(run(laterId.copy(t1 = care.t0 + 3))))
    }

    @Test
    fun anActionThatCannotBeComparedIsCountedAndTheMirrorStaysInStep() {
        val midnight = java.time.Instant.parse("2026-10-28T23:59:59.999Z").toEpochMilli()
        val rest = built.first { it.action.kind == "rest.take" }.action
        val skips =
            listOf(
                log.action.copy(t0 = midnight, t1 = midnight + 2) to "midnight",
                log.action.copy(offset = 60) to "tzdata",
                log.action.copy(flags = listOf(ShadowFlags.INTERLEAVED)) to "interleaved",
                log.action.copy(flags = listOf(ShadowFlags.THREW)) to "threw",
                log.action.copy(saves = listOf(true, false)) to "mixedStorage",
                log.action.copy(kind = "health.sync") to "later",
                log.action.copy(kind = "coach.reflect") to "notNative",
                log.action.copy(kind = "some.future.kind") to "unknownKind",
                log.action.copy(args = listOf(JsonNumber(5.0))) to "input",
                log.action.copy(env = mapOf("dom" to JsonObject(mapOf("x" to JsonString("1"))))) to
                    "input",
                log.action.copy(
                    args =
                        listOf(
                            JsonObject(
                                (log.action.args[0] as JsonObject).members +
                                    ("extra" to JsonString("?"))
                            )
                        )
                ) to "input",
                rest.copy(env = rest.env - "plan") to "planWindow",
            )
        for ((action, why) in skips) {
            val s =
                run(
                    action,
                    log.action.copy(
                        before = action.after,
                        after = action.after,
                        toasts = emptyList(),
                    ),
                )
            val counts = s.ledger.kind(action.kind)
            assertEquals(why, 1, counts.skipped[why])
            assertEquals(
                "$why: the next action is compared on the page's state",
                2,
                s.ledger.applied,
            )
        }
        val plan = built.first { it.action.kind == "fixed.plan" }.action
        assertEquals(
            1,
            run(plan, content = ShadowContent.NONE).ledger.kind("fixed.plan").skipped["content"],
        )
    }

    @Test
    fun fiveInternalErrorsDisableTheCheck() {
        val plan = built.first { it.action.kind == "fixed.plan" }.action
        val broken =
            object : ShadowContent by GoldenActions.CONTENT {
                override fun planDay(date: String): FixedAwards.PlanDay? =
                    throw IllegalStateException("content")
            }
        val s =
            run(
                plan,
                plan.copy(before = plan.after),
                plan.copy(before = plan.after),
                plan.copy(before = plan.after),
                content = broken,
            )
        assertEquals(4, s.ledger.errors["IllegalStateException"])
        assertEquals(ShadowState.ON, s.ledger.state)
        val page = FakeShadowPage()
        val s2 = session(broken)
        for (part in page.base(plan.before)) s2.base(part, 0)
        repeat(ShadowLimits.ERRORS_BEFORE_DISABLE) {
            s2.observe(
                page.send(plan.copy(before = plan.before, after = plan.before)),
                FakeShadowPage.T0,
            )
        }
        assertEquals(ShadowState.DISABLED, s2.ledger.state)
        assertEquals("errors", s2.ledger.reason)
        assertFalse(s2.accept(10, false).accepted)
    }

    @Test
    fun aStrictRefusalIsCountedAndTheActionStillCompared() {
        val bad =
            log.action.copy(
                before = log.action.before + ("sparks" to JsonString("12")),
                after = log.action.after,
            )
        val s = run(bad)
        assertEquals(1, s.ledger.codec["strict"])
        assertEquals(1, s.ledger.codec["strict.sparks"])
        assertEquals(1, s.ledger.compared)
    }
}
