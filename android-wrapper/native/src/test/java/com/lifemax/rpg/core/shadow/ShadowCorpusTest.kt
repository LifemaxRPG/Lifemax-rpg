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
        val (l, outcomes, envelopes) = replay("shadow-corpus.json")
        assertEquals(outcomes.joinToString("\n"), envelopes - LATER_IN_CORPUS, l.compared)
        assertEquals(outcomes.joinToString("\n"), 0, l.different)
        assertEquals(l.compared, l.same + l.allowed)
        assertEquals(mapOf("later" to 1), l.kind("boot.decay").skipped)
        assertTrue(l.refused.isEmpty() && l.errors.isEmpty())
        assertTrue("every compared kind in the corpus", l.kinds.keys.containsAll(KINDS))
        println(
            "shadow corpus: $envelopes envelopes, ${l.compared} compared, ${l.same} same, " +
                "${l.allowed} allowed; codec ${l.codec}"
        )
    }

    /**
     * WP6c's corpus (`tests/fixtures/shadow-quests-corpus.json`, `gen_shadow_corpus.js --quests`):
     * the real page with an app that says `economy.shadow.quests.v1`, driven through the Daily Hand
     * and the quest board (the board drawn, free and paid rerolls, a Log that fulfils a card, a
     * card claimed, a quest ticked with a note and unticked, a quest made and one refused, a class
     * switch, the day rolling over with the app open, a reroll and a Log on the new day). Every
     * action but the boot's decay is compared and the same: the real page's composition of save and
     * Daily Hand, write for write.
     */
    @Test
    fun everyQuestAndDailyHandActionThePageSentMatches() {
        val (l, outcomes, envelopes) = replay("shadow-quests-corpus.json")
        assertEquals(outcomes.joinToString("\n"), envelopes - LATER_IN_CORPUS, l.compared)
        assertEquals(outcomes.joinToString("\n"), 0, l.different)
        assertEquals(l.compared, l.same + l.allowed)
        assertTrue(l.refused.isEmpty() && l.errors.isEmpty())
        assertTrue("every quest kind in the corpus", l.kinds.keys.containsAll(QUEST_KINDS))
        println(
            "shadow quests corpus: $envelopes envelopes, ${l.compared} compared, ${l.same} same, " +
                "${l.allowed} allowed"
        )
    }

    /** Replays a corpus file as the app receives it: the ledger, a line per envelope, the count. */
    private fun replay(file: String): Triple<ShadowLedger, List<String>, Int> {
        val messages = (FakeShadowPage.fixture(file) as JsonArray).items.map { it as JsonObject }
        val texts =
            messages.map { (it["m"] as JsonString).value to (it["text"] as JsonString).value }
        val sid = Regex("\"sid\":\"([^\"]+)\"").find(texts.first().second)!!.groupValues[1]
        val s =
            ShadowSession(
                ShadowSessionTest.FP,
                ShadowLedger(0, ByteArray(ShadowLimits.SALT_BYTES)),
                1,
                110,
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
        return Triple(s.ledger, outcomes, texts.count { it.first == "observe" })
    }

    private companion object {
        val QUEST_KINDS =
            setOf(
                "hand.verify",
                "hand.reroll",
                "log.record",
                "quest.toggle",
                "quest.create",
                "quest.pack",
                "save.other",
            )

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
