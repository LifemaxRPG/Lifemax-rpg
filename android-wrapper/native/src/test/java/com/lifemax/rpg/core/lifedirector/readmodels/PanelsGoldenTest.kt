package com.lifemax.rpg.core.lifedirector.readmodels

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replays every case of `ld-readmodels-b-golden.json.gz` (WP-LD8b) and requires the page's answer
 * for every section (as part a's [ReadModelsGoldenTest] does), and that native asks LifeMax the
 * same questions the page asked. Each family's case count is fixed, so a lost or added case fails
 * here as well as in the web test that regenerates the file.
 */
class PanelsGoldenTest {
    @Test
    fun everyCaseGivesThePagesAnswer() {
        val wrong = ArrayList<String>()
        val counts = LinkedHashMap<String, Int>()
        val ids = HashSet<String>()
        for (c in PanelsReplay.cases) {
            counts[c.family] = (counts[c.family] ?: 0) + 1
            assertTrue("repeated id ${c.id}", ids.add(c.id))
            assertTrue(
                "${c.id} cites a rule outside RM",
                c.rule.split(' ').all { RULE.matches(it) },
            )
            PanelsReplay.difference(c)?.let { wrong += it }
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
                .flatMap {
                    sequenceOf(
                        File(
                            it,
                            "android-wrapper/native/src/main/resources/" + PanelWords.RESOURCE,
                        ),
                        File(it, "src/main/resources/" + PanelWords.RESOURCE),
                    )
                }
                .first { it.isFile }
        val words = PanelWords.read(file.readText())
        assertEquals(PanelWords.packaged.dayNames, words.dayNames)
        assertEquals(PanelWords.packaged.engineVersion, words.engineVersion)
    }

    /** A file that is not exactly the export is refused. */
    @Test
    fun aChangedWordsFileIsRefused() {
        val text =
            PanelWords::class.java.classLoader!!.getResourceAsStream(PanelWords.RESOURCE)!!.use {
                it.readBytes().toString(Charsets.UTF_8)
            }
        for (bad in
            listOf(
                text.replace("\"tooShort\"", "\"tooshort\""),
                text.replace("\"fallback\": \"target\"", "\"fallback\": 1"),
                text.replace("\"h12\": true", "\"h12\": \"yes\""),
                text.replace("\"keySep\": \"|\"", "\"keySep\": \"|\", \"extra\": \"x\""),
            )) {
            assertTrue(bad != text)
            assertTrue(runCatching { PanelWords.read(bad) }.isFailure)
        }
    }

    companion object {
        private const val SHOWN = 12
        private val RULE = Regex("""RM-\d+""")

        /** Cases per family, as tools/ld_readmodels_b_cases.js defines them. */
        val FAMILY_COUNTS =
            mapOf(
                "seed" to 42,
                "goals" to 213,
                "quests" to 200,
                "settings" to 146,
                "time" to 144,
                "forms" to 132,
                "move" to 142,
                "shifts" to 122,
                "handles" to 140,
                "dst" to 60,
                "host" to 60,
                "fuzz" to 200,
            )
    }
}
