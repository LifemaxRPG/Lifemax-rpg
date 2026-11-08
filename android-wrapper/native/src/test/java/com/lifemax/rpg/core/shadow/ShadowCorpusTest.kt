package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The corpus (`tests/fixtures/shadow-corpus.json`, written by `tools/gen_shadow_corpus.js` from the
 * real built page on the established save): the base and envelopes exactly as the page sent them
 * for the boot and nineteen economy actions (Logs, a refused save and its take-back, undo confirmed
 * and declined, rest, care, a deposit, the shop, rules and decay settings, a welcome chip, voice,
 * Life Director, an ascension refused, a bare save). Replayed as the app would receive them, every
 * action of a kind WP6b compares must be compared and the same: real argument and env shapes, real
 * writes and toasts, the ids the page minted, the Daily Hand's auto-verify inside a save. The
 * boot's decay is WP6d's.
 */
class ShadowCorpusTest {
    @Test
    fun everyEconomyActionThePageSentMatches() {
        val messages =
            (FakeShadowPage.fixture("shadow-corpus.json") as JsonArray).items.map {
                it as JsonObject
            }
        val texts =
            messages.map { (it["m"] as JsonString).value to (it["text"] as JsonString).value }
        val sid = Regex("\"sid\":\"([^\"]+)\"").find(texts.first().second)!!.groupValues[1]
        val s =
            ShadowSession(
                ShadowSessionTest.FP,
                ShadowLedger(0, ByteArray(ShadowLimits.SALT_BYTES)),
                1,
                109,
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
        val l = s.ledger
        val envelopes = texts.count { it.first == "observe" }
        assertEquals(outcomes.joinToString("\n"), envelopes - LATER_IN_CORPUS, l.compared)
        assertEquals(outcomes.joinToString("\n"), 0, l.different)
        assertEquals(l.compared, l.same + l.allowed)
        assertEquals(mapOf("later" to 1), l.kind("boot.decay").skipped)
        assertTrue(l.refused.isEmpty() && l.errors.isEmpty())
        assertTrue("every compared kind in the corpus", l.kinds.keys.containsAll(KINDS))
        println(
            "shadow corpus: $envelopes envelopes, ${l.compared} compared, ${l.same} same, ${l.allowed} allowed; codec ${l.codec}"
        )
    }

    private companion object {
        /** The boot's decay, compared from WP6d on. */
        const val LATER_IN_CORPUS = 1

        val KINDS =
            setOf(
                "boot.load",
                "log.record",
                "log.delete",
                "rest.take",
                "bonus.care",
                "bonus.nutrition",
                "money.record",
                "spark.buy",
                "rules.reset",
                "rules.decay",
                "save.other",
                "coach.claim",
                "voice.log",
                "director.award",
                "director.quest",
                "ascend",
            )
    }
}
