package com.lifemax.rpg.core.save

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Seeded random JSON (deterministic: the same trees on every run): text round trips, number
 * lexemes, mangled texts, and saves carrying random fields the economy does not model.
 */
class SaveCodecFuzzTest {
    private class Gen(seed: Long) {
        val r = Random(seed)

        private val edgeNumbers =
            doubleArrayOf(
                0.0,
                -0.0,
                1.0,
                -1.0,
                0.1,
                0.1 + 0.2,
                5e-324,
                -5e-324,
                2.2250738585072014e-308,
                Double.MAX_VALUE,
                -Double.MAX_VALUE,
                9007199254740992.0,
                9007199254740993.0,
                1e21,
                1e-7,
                123456789012345680000.0,
                31.499999999999996,
            )

        private val edgeLexemes =
            listOf("-0", "1e400", "-1e400", "1e-400", "1.0", "1E2", "0.10", "1e+2", "-0.0", "0e0")

        private val units =
            charArrayOf(
                '"',
                '\\',
                '/',
                '\n',
                '\u0000',
                '\u001f',
                ' ',
                'a',
                '\u007f',
                'é',
                ' ',
                '\ud800',
                '\udbff',
                '\udc00',
                '\udfff',
                '﻿',
                '中',
            )

        fun number(): JsonNumber =
            when (r.nextInt(6)) {
                0 -> JsonNumber(edgeNumbers[r.nextInt(edgeNumbers.size)])
                1 ->
                    JsonNumber(java.lang.Double.longBitsToDouble(r.nextLong())).let {
                        if (it.value.isFinite()) it else JsonNumber(0.5)
                    }
                2 -> JsonNumber(r.nextInt(1_000_000) / 100.0)
                3 -> edgeLexemes[r.nextInt(edgeLexemes.size)].let { JsonNumber(it.toDouble(), it) }
                else -> lexeme().let { JsonNumber(it.toDouble(), it) }
            }

        /** A number text by the JSON grammar: sign, integer, fraction, exponent. */
        fun lexeme(): String = buildString {
            if (r.nextBoolean()) append('-')
            if (r.nextInt(4) == 0) append('0')
            else {
                append('1' + r.nextInt(9))
                repeat(r.nextInt(25)) { append('0' + r.nextInt(10)) }
            }
            if (r.nextBoolean()) {
                append('.')
                repeat(1 + r.nextInt(20)) { append('0' + r.nextInt(10)) }
            }
            if (r.nextBoolean()) {
                append(if (r.nextBoolean()) 'e' else 'E')
                when (r.nextInt(3)) {
                    0 -> append('+')
                    1 -> append('-')
                }
                append(r.nextInt(if (r.nextInt(5) == 0) 500 else 30))
            }
        }

        fun string(): String = buildString {
            repeat(r.nextInt(8)) {
                append(
                    if (r.nextInt(3) == 0) r.nextInt(0x10000).toChar()
                    else units[r.nextInt(units.size)]
                )
            }
        }

        fun key(): String =
            when (r.nextInt(6)) {
                0 -> listOf("__proto__", "", "constructor", "id", "0", "toString")[r.nextInt(6)]
                else -> string()
            }

        fun value(depth: Int = 0): JsonValue =
            when (if (depth > 4) 2 + r.nextInt(4) else r.nextInt(6)) {
                0 ->
                    JsonObject(
                        LinkedHashMap<String, JsonValue>().apply {
                            repeat(r.nextInt(5)) { put(key(), value(depth + 1)) }
                        }
                    )
                1 -> JsonArray(List(r.nextInt(5)) { value(depth + 1) })
                2 -> JsonString(string())
                3 -> number()
                4 -> JsonBool.of(r.nextBoolean())
                else -> JsonNull
            }
    }

    /**
     * What the page stores for a value: a computed `-0` reads back as 0, NaN and infinities null.
     */
    private fun stored(v: JsonValue): JsonValue =
        when (v) {
            is JsonNumber ->
                when {
                    v.lexeme != null -> v
                    !v.value.isFinite() -> JsonNull
                    v.value == 0.0 -> JsonNumber(0.0)
                    else -> v
                }
            is JsonObject -> JsonObject(v.members.mapValuesTo(LinkedHashMap()) { stored(it.value) })
            is JsonArray -> JsonArray(v.items.map(::stored))
            else -> v
        }

    @Test
    fun randomTreesRoundTripThroughText() {
        val gen = Gen(SEED)
        repeat(3000) {
            val v = gen.value()
            val text = JsonWriter.write(v)
            val back = SaveTestData.parse(text)
            assertEquals(text, stored(v), back)
            assertEquals("written again, the same text", text, JsonWriter.write(back))
            assertTrue(back.sameStoredValue(v))
        }
    }

    @Test
    fun numberTextsKeepTheirValueAndText() {
        val gen = Gen(SEED + 1)
        repeat(5000) {
            val lexeme = gen.lexeme()
            val n = SaveTestData.parse("[$lexeme]") as JsonArray
            val read = n.items.single() as JsonNumber
            assertEquals(lexeme, lexeme.toDouble().toBits(), read.value.toBits())
            assertEquals("[$lexeme]", JsonWriter.write(n))
            if (read.value.isFinite()) {
                val again = (SaveTestData.parse(jsNumberText(read.value)) as JsonNumber).value
                assertEquals(
                    lexeme,
                    (if (read.value == 0.0) 0.0 else read.value).toBits(),
                    again.toBits(),
                )
            }
        }
    }

    @Test
    fun mangledTextIsRefusedNeverThrown() {
        val gen = Gen(SEED + 2)
        val base = JsonWriter.write(SaveTestData.fresh)
        repeat(2000) {
            val at = gen.r.nextInt(base.length)
            val text =
                when (gen.r.nextInt(3)) {
                    0 -> base.removeRange(at, at + 1)
                    1 ->
                        base.substring(0, at) +
                            MANGLE[gen.r.nextInt(MANGLE.length)] +
                            base.substring(at)
                    else -> base.substring(0, at)
                }
            when (val d = EconomySaveCodec.decode(text)) {
                is SaveDecoding.Decoded ->
                    assertEquals(text, SaveTestData.encoded(d.document, d.save).text)
                is SaveDecoding.Refused ->
                    assertTrue(
                        d.refusal.reason in
                            setOf(
                                SaveRefusalReason.NOT_JSON,
                                SaveRefusalReason.WRONG_TYPE,
                                SaveRefusalReason.NOT_AN_OBJECT,
                            )
                    )
            }
        }
    }

    /** Random untracked values at the top level and inside every tracked object and record. */
    @Test
    fun savesKeepEveryValueTheEconomyDoesNotModel() {
        val gen = Gen(SEED + 3)
        val accepted =
            SaveTestData.goldenCases.filter {
                (it["id"] as JsonString).value !in SaveCodecGoldenTest.MALFORMED
            }
        repeat(300) {
            val case = accepted[gen.r.nextInt(accepted.size)]
            val start = SaveTestData.startOf(case)
            val save = LinkedHashMap(start.members)
            repeat(1 + gen.r.nextInt(4)) { save["x_" + gen.string()] = gen.value() }
            for (key in SaveFields.TRACKED) save[key] = sprinkle(save[key] ?: continue, gen, key)
            val stored = SaveTestData.parse(JsonWriter.write(JsonObject(save))) as JsonObject
            SaveTestData.checkRoundTrip(stored, SaveReading.STRICT)
        }
    }

    /**
     * Adds `x_`-named keys to every record-like object (never to a map whose every key the codec
     * reads, such as `attrXP` or `questDone`, where a stray key would be a tracked value).
     */
    private fun sprinkle(v: JsonValue, gen: Gen, path: String): JsonValue =
        when (v) {
            is JsonObject -> {
                val map = path in MAPS
                JsonObject(
                    LinkedHashMap(
                            v.members.mapValues { (k, x) ->
                                sprinkle(x, gen, if (map) "$path.*" else "$path.$k")
                            }
                        )
                        .apply {
                            if (!map && gen.r.nextBoolean()) put("x_" + gen.string(), gen.value(2))
                        }
                )
            }
            is JsonArray -> JsonArray(v.items.map { sprinkle(it, gen, "$path[]") })
            else -> v
        }

    private companion object {
        const val SEED = 20261108L

        /** Characters that break JSON when dropped into it. */
        const val MANGLE = "{[\",:}]\\e-0x\u0000"

        /** Tracked objects whose keys are all the codec's (`*` for a key the owner chose). */
        val MAPS =
            setOf(
                "attrXP",
                "skillXP",
                "attrStars",
                "ascended",
                "decayTotal",
                "decayApplied",
                "customRules",
                "questDone",
                "restDays",
                "shieldDays",
                "toysBought",
                "nutriDone",
                "actXP",
                "dayReflections",
                "health.seen",
                "plan.checks",
                "plan.checks.*.s",
                "journalCoach.days",
                "journalCoach.reviews",
                "journalCoach.welcome",
                "journalCoach.welcome.*.claimed",
            )
    }
}
