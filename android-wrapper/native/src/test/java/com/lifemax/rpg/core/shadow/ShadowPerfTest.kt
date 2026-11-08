package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.save.JsonWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Size and time on a realistic large save (ECONOMY_SHADOW_PLAN 4, budgets): a 3 MB ledger (the
 * established fixture's records repeated to about 9,500) goes over as a base in parts under the
 * 32-part limit, and fifty Log actions on it are each decoded and applied well inside the native
 * budget (p95 under 200 ms; the JVM here is no phone, so the bound only catches a gross
 * regression).
 */
class ShadowPerfTest {
    @Test
    fun aThreeMegabyteSaveCrossesAndEachActionStaysSmallAndQuick() {
        val save =
            FakeShadowPage.members(
                FakeShadowPage.members(FakeShadowPage.fixture("saves/established.json"))["state"]
            )
        val records = (save["activities"] as JsonArray).items
        val average = JsonWriter.write(JsonArray(records)).length.toDouble() / records.size
        val count = (THREE_MB / average).toInt() + 1
        val many: List<JsonValue> =
            List(count) { i ->
                JsonObject(
                    (records[i % records.size] as JsonObject).members +
                        ("id" to JsonString("big-$i"))
                )
            }
        val big = save + ("activities" to JsonArray(many))
        val baseChars = JsonWriter.write(JsonObject(FakeShadowPage.shadow(big))).length
        assertTrue("about 3 MB: $baseChars", baseChars in THREE_MB..THREE_MB + THREE_MB / 2)

        val session = ShadowSessionTest.session()
        session.configure(ShadowSessionTest.configure(ShadowSessionTest.FP), false)
        val page = FakeShadowPage()
        val parts = page.base(big)
        assertTrue(parts.size <= ShadowLimits.MAX_BASE_PARTS)
        val t0 = System.nanoTime()
        parts.forEach { assertTrue(session.base(it, 0).ok) }
        val baseMs = (System.nanoTime() - t0) / 1e6
        assertTrue("base took $baseMs ms", baseMs < BASE_BOUND_MS)

        var now = big
        val times = ArrayList<Double>()
        val sizes = ArrayList<Int>()
        repeat(ACTIONS) { n ->
            val acts = (now["activities"] as JsonArray).items
            val next =
                now +
                    ("activities" to
                        JsonArray(
                            acts +
                                JsonObject(
                                    (acts[n] as JsonObject).members + ("id" to JsonString("new-$n"))
                                )
                        ))
            val text = page.envelope("log.record", now, next)
            sizes += text.length
            val t = System.nanoTime()
            session.observe(text, ShadowSessionTest.T)
            times += (System.nanoTime() - t) / 1e6
            now = next
        }
        assertEquals(ACTIONS, session.ledger.applied)
        assertTrue("envelopes stay small: ${sizes.max()}", sizes.max() < 20 * 1024)
        val p95 = times.sorted()[ACTIONS * 95 / 100 - 1]
        assertTrue("p95 $p95 ms", p95 < ENVELOPE_BOUND_MS)
        ShadowReplayTest.assertSame("3 MB", FakeShadowPage.shadow(now), session.mirrorSnapshot())
        println(
            "shadow perf: base ${parts.size} parts, ${baseChars / 1024} KB in ${"%.0f".format(baseMs)} ms; envelope p95 ${"%.1f".format(p95)} ms, largest ${sizes.max()} chars"
        )
    }

    /**
     * WP6b's comparison on the same 3 MB save: fifty real Logs (a golden draft, after five that
     * warm the JIT), each decoded, run natively, merged, compared (and one in sixteen written and
     * read back by the codec's full encode check) within the native budget: p95 under 200 ms on
     * this JVM; the phone's numbers come from the owner's report. The page's after-state is
     * native's own here, so every action must also come out the same.
     */
    @Test
    fun aThreeMegabyteSaveIsComparedWithinTheBudget() {
        val save =
            FakeShadowPage.members(
                FakeShadowPage.members(FakeShadowPage.fixture("saves/established.json"))["state"]
            )
        val records = (save["activities"] as JsonArray).items
        val average = JsonWriter.write(JsonArray(records)).length.toDouble() / records.size
        val count = (THREE_MB / average).toInt() + 1
        val many =
            List(count) { i ->
                JsonObject(
                    (records[i % records.size] as JsonObject).members +
                        ("id" to JsonString("big-$i"))
                )
            }
        val big = FakeShadowPage.shadow(save + ("activities" to JsonArray(many)))
        val log =
            GoldenActions.build()
                .first {
                    it.recorded &&
                        it.action.kind == "log.record" &&
                        it.action.saves.all { ok -> ok }
                }
                .action
        val session = ShadowSessionTest.session()
        session.configure(ShadowSessionTest.configure(ShadowSessionTest.FP), false)
        val page = FakeShadowPage()
        page.base(big).forEach { assertTrue(session.base(it, 0).ok) }
        val runner = ShadowRunner()
        var now = big
        val times = ArrayList<Double>()
        repeat(WARM_UP + ACTIONS) { n ->
            val t0 = log.t0 + n * 60_000L
            val probe = log.copy(before = now, after = now, t0 = t0, t1 = t0, saves = listOf(true))
            val scratch = FakeShadowPage().also { it.base(now) }
            val decoded =
                (ShadowEnvelopeJson.envelope(scratch.send(probe)) as ShadowDecode.Ok).value
            val p = (runner.run(decoded, now, now) as RunOutcome.Ran).prediction
            // the probe had no record to take an id from: the page's new record gets a UUID of its
            // own
            val uuid = "00000000-0000-4000-8000-%012d".format(n)
            val minted =
                FakeShadowPage.parse(
                    JsonWriter.write(JsonObject(p.after!!)).replace("shadow-extra-1", uuid)
                )
            val next = now + FakeShadowPage.members(minted)
            val text =
                page.send(
                    probe.copy(
                        after = next,
                        returns = p.returns,
                        toasts = p.toasts,
                        saves = List(p.saves) { true },
                    )
                )
            val t = System.nanoTime()
            session.observe(text, ShadowSessionTest.T)
            if (n >= WARM_UP) times += (System.nanoTime() - t) / 1e6
            now = next
        }
        val l = session.ledger
        assertEquals(WARM_UP + ACTIONS, l.compared)
        assertEquals(l.mismatches().map { it.paths }.toString(), WARM_UP + ACTIONS, l.same)
        val p50 = times.sorted()[ACTIONS / 2]
        val p95 = times.sorted()[ACTIONS * 95 / 100 - 1]
        println(
            "shadow compare perf: $count records; observe and compare p50 ${"%.1f".format(p50)} ms, p95 ${"%.1f".format(p95)} ms"
        )
        assertTrue("compare p95 $p95 ms", p95 < ENVELOPE_BOUND_MS)
    }

    private companion object {
        const val THREE_MB = 3 * 1024 * 1024
        const val ACTIONS = 50
        const val WARM_UP = 5
        const val BASE_BOUND_MS = 5_000.0
        const val ENVELOPE_BOUND_MS = 200.0
    }
}
