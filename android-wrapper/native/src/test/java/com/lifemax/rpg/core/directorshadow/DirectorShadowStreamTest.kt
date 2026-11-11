package com.lifemax.rpg.core.directorshadow

import com.lifemax.rpg.core.shadow.ShadowState
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The real page's messages for the lived-in day (saves, plans, a repair and read checks) through
 * native's real checks: every check agrees with the page (SH-9 to SH-17), and nothing the report or
 * the ring holds names the owner's words (SH-31).
 */
class DirectorShadowStreamTest {
    private fun counts(s: DirectorShadowSession) =
        s.ledger.kinds.entries.joinToString("; ") { (k, c) ->
            "$k seen ${c.seen} compared ${c.compared} same ${c.same} different ${c.different} skipped ${c.skipped}"
        } +
            " | " +
            s.ledger.mismatches().joinToString("; ") { "${it.kind}: ${it.paths}" } +
            " | refused ${s.ledger.refused} errors ${s.ledger.errors}"

    @Test
    fun everyCheckOfTheDayAgreesWithThePage() {
        val s = DirectorShadowStream.session()
        DirectorShadowStream.play(s)
        println(counts(s))
        assertEquals(counts(s), ShadowState.ON, s.ledger.state)
        assertEquals(counts(s), 0, s.ledger.different)
        assertTrue(counts(s), s.ledger.refused.isEmpty() && s.ledger.errors.isEmpty())
        for (kind in DirectorShadowJson.KINDS) {
            val c = s.ledger.kinds[kind] ?: error("no $kind: " + counts(s))
            assertTrue(
                "$kind: " + counts(s),
                c.compared > 0 && c.same == c.compared && c.skipped.isEmpty(),
            )
        }
        assertTrue(counts(s), s.ledger.codec.isEmpty()) // the page's saves read strictly too
    }

    @Test
    fun aReadAcrossASecondIsSkipped() {
        val s = DirectorShadowStream.session()
        DirectorShadowStream.play(s) {
            if (it.contains("\"kind\":\"director.read\""))
                it.replace("\"flags\":[]", "\"flags\":[\"second\"]")
            else it
        }
        val c = s.ledger.kinds[DirectorShadowJson.READ]!!
        assertEquals(0, c.compared)
        assertEquals(mapOf("second" to c.seen), c.skipped)
    }

    @Test
    fun theStreamCoversEachKind() {
        val kinds =
            DirectorShadowJson.KINDS.associateWith { DirectorShadowStream.envelopes(it).size }
        assertTrue(kinds.toString(), kinds.values.all { it > 0 })
    }

    @Test
    fun nothingTheCheckKeepsNamesTheOwnersWords() {
        val s = DirectorShadowStream.session()
        // the page's week said something else: a difference whose path is shown, never the words
        DirectorShadowStream.play(s) { it.replace("\"fitText\":\"", "\"fitText\":\"Build muscle ") }
        assertTrue(
            counts(s),
            s.ledger.different > 0 &&
                s.ledger.mismatches().any { m -> m.paths.contains("read.week.fitText") },
        )
        val kept = s.ringText() + s.report(DirectorShadowStream.T, ZoneOffset.UTC)
        for (word in
            listOf(
                "Build muscle",
                "Learn Spanish",
                "Read more",
                "Dentist",
                "g-muscle",
                "spanish",
                "player",
            )) assertFalse(word, kept.contains(word))
    }
}
