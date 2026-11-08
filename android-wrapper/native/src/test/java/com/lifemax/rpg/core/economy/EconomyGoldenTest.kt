package com.lifemax.rpg.core.economy

import com.lifemax.rpg.core.golden.GoldenFiles
import com.lifemax.rpg.core.golden.GoldenJson
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

/**
 * Every golden case recorded from the real page (ECONOMY_SPEC section 21) reproduces exactly: each
 * call's return value, the toasts, and every recorded save field afterwards. One test per case,
 * named by its id.
 */
@RunWith(Parameterized::class)
class EconomyGoldenTest(private val id: String, private val case: Map<String, Any?>) {
    @Test
    fun reproduces() {
        val differences = differences(case)
        if (differences.isNotEmpty())
            fail("$id (${case["rule"]})\n  " + differences.joinToString("\n  "))
    }

    companion object {
        const val FILE = "economy-golden.json"

        @Suppress("UNCHECKED_CAST")
        val cases: List<Map<String, Any?>> by lazy {
            GoldenJson.plain(GoldenFiles.read(FILE)) as List<Map<String, Any?>>
        }

        /**
         * The fresh save every other case's state is written against: the input state of the first
         * case.
         */
        private val fresh: Map<String, Any?> by lazy { cases.first()["input"].obj()["state"].obj() }

        private val DEFAULT_NOW = Instant.parse("2026-10-28T12:00:00.000Z").toEpochMilli()

        @JvmStatic
        @Parameters(name = "{0}")
        fun parameters(): List<Array<Any>> =
            cases.filterNot(::composed).map { arrayOf(it["id"] as String, it) }

        /**
         * The `save/compose-*` family (WP6c): the Daily Hand's deal and checks composed with the
         * save as the page runs them. The economy alone does not deal; the economy shadow's
         * composition replays them (`core/shadow` SaveComposeGoldenTest and
         * ShadowGoldenCompareTest).
         */
        fun composed(case: Map<String, Any?>): Boolean =
            (case["id"] as String).startsWith(COMPOSE_FAMILY)

        const val COMPOSE_FAMILY = "save/compose-"

        /** Runs one case and lists what differs from the recording; empty when it reproduces. */
        internal fun differences(
            case: Map<String, Any?>,
            mutate: (GoldenRun) -> Unit = {},
        ): List<String> {
            val input = case["input"].obj()
            val output = case["output"].obj()
            val start = fresh + input["state"].obj()
            val run =
                GoldenRun(
                    fresh = fresh,
                    start = start,
                    now =
                        (input["now"] as? String)?.let { Instant.parse(it).toEpochMilli() }
                            ?: DEFAULT_NOW,
                    zone = ZoneId.of((input["zone"] as? String) ?: "UTC"),
                )
            mutate(run)
            val returns = input["calls"].list().map { run.call(it.obj()) }
            val problems = mutableListOf<String>()
            val expectedReturns = output["returns"].list()
            if (returns.size != expectedReturns.size)
                problems += "returns: expected ${expectedReturns.size} values, got ${returns.size}"
            returns.zip(expectedReturns).forEachIndexed { i, (actual, expected) ->
                GoldenJson.firstDifference(actual, expected, "return[$i]")?.let { problems += it }
            }
            GoldenJson.firstDifference(
                    LooseFields.toasts(run.toasts),
                    LooseFields.toasts(output["toasts"].list()),
                    "toasts",
                )
                ?.let { problems += it }
            if (input.containsKey("state")) problems += stateDifferences(run, start, output)
            return problems
        }

        /**
         * Every tracked field: the recorded value when it changed, absent when deleted, else as it
         * started.
         */
        private fun stateDifferences(
            run: GoldenRun,
            start: Map<String, Any?>,
            output: Map<String, Any?>,
        ): List<String> {
            val changed = output["state"].obj()
            val deleted = output["deleted"].list().map(::text).toSet()
            val untracked = (changed.keys + deleted).filterNot { it in SaveCodec.TRACKED }
            val after = run.encoded()
            // The recorder lists dailyDraw only for the cases that set one up
            // (gen_economy_golden.js
            // header), so it is compared only there.
            val compared =
                SaveCodec.TRACKED.filter { it != "dailyDraw" || it in start || it in changed }
            return untracked.map { "state.$it is recorded but not compared" } +
                compared.mapNotNull { key ->
                    val expected =
                        when (key) {
                            in changed -> changed[key]
                            in deleted -> SaveCodec.Absent
                            // A stored null stays null: missing and null are different saves.
                            in start -> start[key]
                            else -> SaveCodec.Absent
                        }
                    GoldenJson.firstDifference(
                        LooseFields.of(key, after[key]),
                        LooseFields.of(key, expected),
                        "state.$key",
                    )
                }
        }
    }
}

/** The golden file holds one case per line; every one of them runs, under its own id. */
class EconomyGoldenCountTest {
    @Test
    fun everyCaseInTheFileRuns() {
        val lines =
            GoldenFiles.text(EconomyGoldenTest.FILE).lines().count {
                it.trimStart('[', ',').startsWith("{\"id\"")
            }
        val ids = EconomyGoldenTest.parameters().map { it[0] }
        val composed = EconomyGoldenTest.cases.count(EconomyGoldenTest::composed)
        assertEquals(lines, ids.size + composed)
        assertEquals("case ids are unique", ids.size, ids.toSet().size)
    }
}

/**
 * The only parts of a recording compared loosely, each with the spec section that allows it
 * (section 21: page state outside the economy). Whole cases are never left out.
 */
internal object LooseFields {
    /**
     * `journalCoach`: the journal's drawing stores day and pattern memory of its own (`days`
     * entries without `xpAwarded`, `pats`, `cooldown`) with a plain write; only the economy's
     * once-only marks are compared (COACH-1, section 21).
     */
    fun of(key: String, value: Any?): Any? = if (key == "journalCoach") coachMarks(value) else value

    /**
     * Toasts: a failed save says "not saved" and its take-back says it again, so the message can
     * repeat; a repeat right after itself counts once (section 21).
     */
    fun toasts(toasts: List<Any?>): List<Any?> =
        toasts.filterIndexed { i, t -> i == 0 || t != toasts[i - 1] || t !in UNSAVED }

    private val UNSAVED = setOf(SavePipeline.SAVE_FULL, SavePipeline.SAVE_READ_ONLY)

    private fun coachMarks(value: Any?): Any? =
        if (value !is Map<*, *>) value
        else
            mapOf(
                "days" to value["days"].obj().filterValues { truthy(it.obj()["xpAwarded"]) },
                "reviews" to value["reviews"],
                "welcome" to value["welcome"],
            )
}
