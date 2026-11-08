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

    private companion object {
        const val THREE_MB = 3 * 1024 * 1024
        const val ACTIONS = 50
        const val BASE_BOUND_MS = 5_000.0
        const val ENVELOPE_BOUND_MS = 200.0
    }
}
