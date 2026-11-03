package com.lifemax.rpg.feature.pet.world

import com.lifemax.rpg.core.golden.GoldenFiles
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.fail

/**
 * Test support for the cat world contract: the page's own blocks and imports
 * (tests/fixtures/cat-world-golden.json, written by tests/native-cat-world.test.js through the real
 * page), a valid world blob, and the malformed-variant runner every decoder test uses.
 */
internal object WorldFixtures {
    private val golden by lazy { JSONObject(GoldenFiles.text("cat-world-golden.json")) }

    /** The page's block for state [name] (kitten, bonded, hungry, vacation, dna, twoCats). */
    fun block(name: String): JSONObject =
        JSONObject(golden.getJSONObject("blocks").getJSONObject(name).toString())

    val blockNames: List<String>
        get() = golden.getJSONObject("blocks").keys().asSequence().sorted().toList()

    /** The page's import of web brain [name] (rich, damaged, v77, fresh). */
    fun legacy(name: String): JSONObject =
        JSONObject(golden.getJSONObject("legacy").getJSONObject(name).toString())

    const val TED = "cmu9rkw00"
    const val LILY = "cmv2cdxc0"
    const val DAY = "2026-10-30"
    const val NOW = 1_793_361_600_000L

    fun cat(spots: Map<String, Int> = mapOf("bed" to 80, "couch" to 30)) =
        BlobCat(
            pos = BlobPos("bed", 36.0, 40.5, 1),
            act = "sleep",
            actAt = NOW - 60_000,
            drives = Drives(0.8, 0.2, 0.31, 0.4, 0.1, 0.25, 0.5, 0.125),
            mood = Mood(0.3, -0.4),
            trust = Trust(0.8, 0.74, 0.6, 0.55),
            spots = spots,
            innate = listOf("dramatic_flopper", "chirp_greeter"),
            counters = QuirkCounters(3, 7, 21, 2),
            rhythm = List(24) { it * 4 },
            fx = listOf("first_glass"),
            rng = listOf(12345, -7, Int.MAX_VALUE),
            day = DAY,
            wake = 6.5,
            bed = 22.75,
            voice = 1.043,
            cheerSeen = listOf(1_793_361_000_000L),
        )

    /** A full world of Ted and Lily, every part filled in. */
    fun blob(): WorldBlob =
        WorldBlob(
            v = 1,
            imported = "legacy1",
            cats = mapOf(TED to cat(), LILY to cat(mapOf("box" to 100))),
            pair = BlobPair("groom", NOW - 3_600_000, mapOf("chase" to NOW + 600_000)),
            toys =
                listOf(
                    BlobToy("ball_a", "floor", 140.0, 62.3, null),
                    BlobToy("mouse", "floor", 20.0, 50.0, TED),
                ),
            gift = BlobGift(LILY, "feather", 10.0, 95.0, DAY),
            outbox =
                listOf(
                    OutboxEntry(
                        MomentReport(
                            OpIds.firstMoment(TED, "first_glass"),
                            TED,
                            "first_glass",
                            NOW,
                            null,
                        )
                    ),
                    OutboxEntry(
                        SocialReport(
                            OpIds.of(TED, "social", 4),
                            "standoff",
                            listOf(TED, LILY),
                            LILY,
                            NOW,
                        )
                    ),
                ),
            away = Away(NOW - 7_200_000, listOf("Ted slept on the sofa most of the afternoon")),
        )

    /** A deep copy of [j] (org.json has none). */
    fun copy(j: JSONObject) = JSONObject(j.toString())

    /** Walks [path] ("cats.0.hunger") to the object or list it names. */
    fun at(root: JSONObject, path: String): Any =
        path
            .split('.')
            .filter { it.isNotEmpty() }
            .fold(root as Any) { node, key ->
                when (node) {
                    is JSONObject -> node.get(key)
                    is JSONArray -> node.get(key.toInt())
                    else -> error("no $key in $path")
                }
            }

    fun obj(root: JSONObject, path: String) = at(root, path) as JSONObject

    /**
     * Runs every variant on a fresh copy of [base] and asserts [decode] throws for each, with an
     * error code; answers the codes seen (by variant), so a test can check one or two of them.
     */
    fun assertAllRefused(
        base: JSONObject,
        variants: Map<String, (JSONObject) -> Unit>,
        decode: (JSONObject) -> Unit,
    ): Map<String, String> {
        decode(copy(base)) // the base itself is good
        val codes = LinkedHashMap<String, String>()
        for ((name, mutate) in variants) {
            val j = copy(base)
            mutate(j)
            try {
                decode(j)
                fail("variant '$name' was accepted")
            } catch (e: WorldFormatException) {
                codes[name] = e.code
            } catch (e: org.json.JSONException) {
                codes[name] = "json"
            }
        }
        assertEquals(variants.keys, codes.keys)
        return codes
    }
}
