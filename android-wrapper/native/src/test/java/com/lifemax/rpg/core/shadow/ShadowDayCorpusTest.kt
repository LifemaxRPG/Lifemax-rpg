package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP6d's real-page corpus (`tests/fixtures/shadow-day-corpus.json`,
 * `tools/gen_shadow_day_corpus.js` from the built page with an app that says
 * `economy.shadow.day.v1` and a Health Connect stand-in): thirteen page sessions, each replayed as
 * the app receives it in a session of its own. The day with the app open (syncs that pay, find
 * nothing new and fail partway, a rest day, the day rolling over with the app open, two syncs at
 * once), a boot after ten closed days (the decay walk, rested XP, a shield covering a missed day),
 * and the paused-clock zone matrix (daylight saving, a missing midnight, the date line, a leap day,
 * a year's end) with a boot, a sync, the next day's return and the clock moved back a day. Every
 * action is compared and the same, but the first of the two syncs at once, which the second
 * interleaves (`skipped.interleaved`).
 */
class ShadowDayCorpusTest {
    /** One page session of the corpus as the app saw it. */
    class Replayed(
        val name: String,
        val ledger: ShadowLedger,
        val outcomes: List<String>,
        val envelopes: Int,
    )

    @Test
    fun everyDayRollAndHealthSyncThePageSentMatches() {
        val replayed = replayAll()
        assertEquals(SCENARIOS, replayed.size)
        val problems = ArrayList<String>()
        var compared = 0
        var envelopes = 0
        val kinds = sortedMapOf<String, Int>()
        for (r in replayed) {
            val l = r.ledger
            val interleaved = l.kinds.values.sumOf { it.skipped["interleaved"] ?: 0 }
            if (l.compared != r.envelopes - interleaved)
                problems += "${r.name}: ${r.envelopes} sent, ${l.compared} compared: ${r.outcomes}"
            if (l.different != 0) problems += "${r.name}: ${l.different} different: ${r.outcomes}"
            if (l.refused.isNotEmpty() || l.errors.isNotEmpty())
                problems += "${r.name}: refused ${l.refused} errors ${l.errors}"
            compared += l.compared
            envelopes += r.envelopes
            for ((k, c) in l.kinds) kinds.merge(k, c.same + c.allowed, Int::plus)
        }
        assertEquals(problems.joinToString("\n"), emptyList<String>(), problems)
        // the first of the two syncs at once is the only one not compared
        assertEquals(envelopes - 1, compared)
        assertEquals(EXPECTED_KINDS, kinds)
        println("day corpus: $envelopes envelopes, $compared compared and matched; by kind $kinds")
    }

    @Test
    fun theDayRollsAreRealDayRolls() {
        val replayed = replayAll()
        // every zone's clock moved back a day, and nothing else jumped
        val jumps = replayed.sumOf { it.ledger.info["clockJump"] ?: 0 }
        assertEquals(ZONE_SCENARIOS, jumps)
        val opened = replayed.first()
        assertEquals(1, opened.ledger.kind("health.sync").skipped["interleaved"])
    }

    companion object {
        const val SCENARIOS = 13
        private const val ZONE_SCENARIOS = 11

        /** What each kind contributed, matched, over the thirteen sessions. */
        val EXPECTED_KINDS =
            sortedMapOf(
                "boot.decay" to 13,
                "boot.load" to 13,
                "decay.apply" to 24,
                "health.sync" to 28,
                "rest.take" to 1,
                "save.other" to 12,
            )

        /** Replays every scenario of the corpus file, each in a fresh session. */
        fun replayAll(
            mutation: EntryMutation = EntryMutation.NONE,
            file: String = "shadow-day-corpus.json",
        ): List<Replayed> =
            (FakeShadowPage.fixture(file) as JsonArray).items.map {
                val s = it as JsonObject
                val messages = (s["messages"] as JsonArray).items.map { m -> m as JsonObject }
                replay((s["name"] as JsonString).value, messages, mutation)
            }

        private fun replay(
            name: String,
            messages: List<JsonObject>,
            mutation: EntryMutation,
        ): Replayed {
            val texts =
                messages.map { (it["m"] as JsonString).value to (it["text"] as JsonString).value }
            val sid = Regex("\"sid\":\"([^\"]+)\"").find(texts.first().second)!!.groupValues[1]
            val s =
                ShadowSession(
                    ShadowSessionTest.FP,
                    ShadowLedger(0, ByteArray(ShadowLimits.SALT_BYTES)),
                    1,
                    111,
                    mutation = mutation,
                )
            s.configure(ShadowSessionTest.configure(ShadowSessionTest.FP, sid = sid), false)
            val outcomes = ArrayList<String>()
            for ((method, text) in texts) {
                if (method == "base") {
                    assertTrue(s.base(text, 0).ok)
                    continue
                }
                val kind = Regex("\"kind\":\"([^\"]+)\"").find(text)!!.groupValues[1]
                val l = s.ledger
                val was = l.same + l.allowed
                assertTrue(kind, s.accept(text.length, false).accepted)
                s.observe(text, 0)
                outcomes +=
                    kind +
                        if (l.same + l.allowed > was) ""
                        else " " + l.kind(kind).skipped + l.mismatches().lastOrNull()?.paths
            }
            return Replayed(name, s.ledger, outcomes, texts.count { it.first == "observe" })
        }
    }
}
