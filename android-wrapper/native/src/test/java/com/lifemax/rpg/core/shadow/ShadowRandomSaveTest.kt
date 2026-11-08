package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.save.JsonWriter
import java.time.Instant
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP6f's fuzz of the comparison itself (ECONOMY_SHADOW_PLAN WP6f): random but valid saves (a golden
 * action's before-state with its ledger grown by copies of its own records, up to 12,000, dated
 * anywhere in the last 300 days, and its economy numbers moved), each taken through a golden action
 * of every compared kind, seeded. Native runs the action on the save; a page that did exactly what
 * native did then sends that outcome through the transport, and the comparison must call it the
 * same (or allowed by an allowlist id): no false difference from the diff, the id replay or the
 * instants on any save, no internal error, and every action inside the slow limit, the largest
 * ledgers included.
 */
class ShadowRandomSaveTest {
    @Test
    fun nativesOwnOutcomeOnRandomSavesIsAlwaysTheSame() {
        val actions =
            GoldenActions.build().filter {
                it.action.kind !in MINTS_TOKENS && it.action.saves.all { ok -> ok }
            }
        val runner = ShadowRunner(GoldenActions.CONTENT)
        val problems = ArrayList<String>()
        val skipped = sortedMapOf<String, Int>()
        val kinds = sortedMapOf<String, Int>()
        val times = ArrayList<Double>()
        var largest = 0
        for (seed in 1..SEEDS) {
            val r = Random(seed * 977L)
            val b = actions[r.nextInt(actions.size)]
            GoldenActions.CONTENT.setup = b.setup
            val save = randomSave(b.action.before, r, seed)
            largest = maxOf(largest, (save["activities"] as? JsonArray)?.items?.size ?: 0)
            val probe = b.action.copy(before = save, after = save)
            val scratch = FakeShadowPage().also { it.base(save) }
            val decoded =
                (ShadowEnvelopeJson.envelope(scratch.send(probe)) as? ShadowDecode.Ok)?.value
            if (decoded == null) {
                problems += "seed $seed ${b.caseId}: the probe does not decode"
                continue
            }
            val prediction =
                when (
                    val o =
                        runner.run(
                            decoded,
                            FakeShadowPage.shadow(save),
                            FakeShadowPage.shadow(save),
                        )
                ) {
                    is RunOutcome.Skipped -> {
                        skipped.merge(o.reason, 1, Int::plus)
                        continue
                    }
                    is RunOutcome.Ran -> o.prediction
                }
            val after = prediction.after ?: continue
            val minted =
                FakeShadowPage.members(
                    FakeShadowPage.parse(minted(JsonWriter.write(JsonObject(after))))
                )
            val page = save + minted
            val session = ShadowGoldenCompareTest.session()
            val fake = FakeShadowPage()
            fake.base(save).forEach { assertTrue(session.base(it, 0).ok) }
            val text =
                fake.send(
                    probe.copy(
                        after = page,
                        returns =
                            FakeShadowPage.parse(minted(JsonWriter.write(prediction.returns))),
                        toasts = prediction.toasts,
                        saves = List(prediction.saves) { true },
                    )
                )
            val t = System.nanoTime()
            session.observe(text, FakeShadowPage.T0)
            times += (System.nanoTime() - t) / 1e6
            val l = session.ledger
            kinds.merge(b.action.kind, 1, Int::plus)
            when {
                l.errors.isNotEmpty() ->
                    problems += "seed $seed ${b.caseId}: internal error ${l.errors}"
                l.compared != 1 ->
                    problems +=
                        "seed $seed ${b.caseId}: not compared ${l.kind(b.action.kind).skipped}"
                l.different != 0 ->
                    problems +=
                        "seed $seed ${b.caseId} (${b.action.kind}): ${l.mismatches().map { it.paths }}"
            }
        }
        assertEquals(problems.joinToString("\n"), emptyList<String>(), problems)
        val sorted = times.sorted()
        val p95 = sorted[sorted.size * 95 / 100]
        assertTrue(
            "every action inside the slow limit: p95 $p95 ms",
            p95 < ShadowLimits.SLOW_P95_MS,
        )
        assertTrue("large ledgers reached: $largest", largest >= LARGE)
        assertTrue("most kinds reached: $kinds", kinds.size >= MIN_KINDS)
        println(
            "random saves: $SEEDS seeds, ${times.size} compared and the same, skipped $skipped; " +
                "largest ledger $largest records; observe p50 ${"%.1f".format(sorted[sorted.size / 2])} ms " +
                "p95 ${"%.1f".format(p95)} ms max ${"%.1f".format(sorted.last())} ms; by kind $kinds"
        )
    }

    /** A valid save the page could hold: the before-state with a grown ledger and moved numbers. */
    private fun randomSave(
        before: Map<String, JsonValue>,
        r: Random,
        seed: Int,
    ): Map<String, JsonValue> {
        val out = LinkedHashMap(before)
        val records =
            (before["activities"] as? JsonArray)?.items.orEmpty().filterIsInstance<JsonObject>()
        if (records.isNotEmpty() && r.nextInt(3) > 0) {
            val count = if (seed % LARGE_EVERY == 0) LARGE else GROWTH[r.nextInt(GROWTH.size)]
            val now = Instant.ofEpochMilli(FakeShadowPage.T0)
            val grown =
                List(count) { i ->
                    val src = records[r.nextInt(records.size)]
                    val date =
                        now.minusSeconds(
                            r.nextInt(DAYS_BACK * SECONDS_PER_DAY).toLong() + SECONDS_PER_DAY
                        )
                    JsonObject(
                        src.members +
                            ("id" to JsonString("fz-$seed-$i")) +
                            ("date" to JsonString(date.toString())) +
                            ("sp" to JsonNumber(1.0))
                    )
                }
            out["activities"] = JsonArray(grown + records)
        }
        if (r.nextBoolean()) out["sparks"] = JsonNumber(r.nextInt(MAX_SPARKS).toDouble())
        if (r.nextBoolean()) out["restedXP"] = JsonNumber(r.nextInt(MAX_RESTED).toDouble())
        return out
    }

    /** Native's own new ids, as the page would have minted them (N1 takes them back in order). */
    private fun minted(text: String): String =
        Regex("shadow-extra-(\\d+)").replace(text) { m ->
            "00000000-0000-4000-8000-%012d".format(m.groupValues[1].toInt())
        }

    private companion object {
        const val SEEDS = 400
        const val LARGE = 12_000
        const val LARGE_EVERY = 50
        const val MIN_KINDS = 15
        const val DAYS_BACK = 300
        const val SECONDS_PER_DAY = 86_400
        const val MAX_SPARKS = 10_000
        const val MAX_RESTED = 600
        val GROWTH = listOf(0, 10, 200, 2_000)

        /** The catalog's four-token ids have no stand-in a test can mint after the fact. */
        val MINTS_TOKENS = setOf("fixed.catalog")
    }
}
