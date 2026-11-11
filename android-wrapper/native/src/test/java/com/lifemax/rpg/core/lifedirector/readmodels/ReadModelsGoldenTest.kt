package com.lifemax.rpg.core.lifedirector.readmodels

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replays every case of `ld-readmodels-golden.json.gz` (WP-LD8a) and requires the page's answer
 * for every section: strings and booleans equal, numbers equal as doubles bit for bit, arrays in
 * order, objects member for member (a member the page leaves out must be absent), a throw of the
 * same error name, and the same XP preview calls in the same order. Each family's case count is
 * fixed, so a lost or added case fails here as well as in the web test that regenerates the file.
 */
class ReadModelsGoldenTest {
    @Test
    fun everyCaseGivesThePagesAnswer() {
        val wrong = ArrayList<String>()
        val counts = LinkedHashMap<String, Int>()
        val ids = HashSet<String>()
        for (c in ReadModelsReplay.golden.cases) {
            counts[c.family] = (counts[c.family] ?: 0) + 1
            assertTrue("repeated id ${c.id}", ids.add(c.id))
            assertTrue("${c.id} cites a rule outside RM", c.rule.split(' ').all { RULE.matches(it) })
            ReadModelsReplay.difference(c)?.let { wrong += it }
        }
        assertTrue(
            "${wrong.size} of ${counts.values.sum()} cases differ:\n" +
                wrong.take(SHOWN).joinToString("\n"),
            wrong.isEmpty(),
        )
        assertEquals(FAMILY_COUNTS, counts)
    }

    /** The packaged words are the file the page's recorder writes, and every phrase is read. */
    @Test
    fun theWordsAreThePagesExport() {
        val start = File(System.getProperty("user.dir") ?: ".").absoluteFile
        val file =
            generateSequence(start) { it.parentFile }
                .map { File(it, "android-wrapper/native/src/main/resources/" + ReadModelWords.RESOURCE) }
                .firstOrNull { it.isFile }
                ?: generateSequence(start) { it.parentFile }
                    .map { File(it, "src/main/resources/" + ReadModelWords.RESOURCE) }
                    .first { it.isFile }
        val words = ReadModelWords.read(file.readText())
        assertEquals(7, words.dayNames.size)
        assertEquals(words.phrases, ReadModelWords.packaged.phrases)
    }

    companion object {
        private const val SHOWN = 12
        private val RULE = Regex("""RM-\d+""")

        /** Cases per family, as tools/ld_readmodels_cases.js defines them. */
        val FAMILY_COUNTS =
            mapOf(
                "seed" to 70,
                "scenario" to 330,
                "dst" to 240,
                "plan" to 284,
                "reschedule" to 202,
                "now" to 650,
                "focus" to 150,
                "zone" to 51,
                "life" to 134,
                "calendar" to 42,
                "host" to 31,
                "week" to 40,
                "fuzz" to 300,
            )
    }
}
