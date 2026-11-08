package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Differences never carry the owner's words into the ring (ECONOMY_SHADOW_PLAN 3.2). Every golden
 * action is sent with a page that differs from native exactly where owner text lives (a skill's
 * name, a quest mark's key, a custom rule's id, a record's notes, a reflection's day) and the ring
 * text that results is searched for every string of every golden save and for the injected words:
 * none may appear; the differences show only fixed names and salted hashes.
 */
class ShadowRedactionTest {
    private val secret = "Private: saw Dr Jamie Rivera about my knee"
    private val skill = "Midnight Sculpting"
    private val mark = "q_secret_2026-10-28"
    private val day = "2026-10-27"

    @Test
    fun theRingHoldsNoOwnerText() {
        val built = GoldenActions.build()
        val session = ShadowGoldenCompareTest.session()
        val page = FakeShadowPage()
        for (part in page.base(built.first().action.before)) assertTrue(session.base(part, 0).ok)
        for (b in built) session.observe(
            page.send(b.action.copy(after = lie(b.action.after))),
            FakeShadowPage.T0,
        )
        val ring = session.ringText()
        assertTrue("the lies are recorded", session.ledger.different > built.size / 2)
        assertTrue(
            session.ledger.mismatches().any { m -> m.paths.any { it.startsWith("skillXP.*#") } }
        )
        assertTrue(
            session.ledger.mismatches().any { m -> m.paths.any { it.startsWith("questDone.*#") } }
        )
        val words =
            ownerStrings(built.flatMap { listOf(it.action.before, it.action.after) }) +
                listOf(secret, skill, mark, day)
        // a word is looked for as a whole name of the ring (between its quotes, dots and brackets);
        // a phrase or a long name anywhere in it
        val names = ring.split('"', '.', '[', ']', ',', ':', '{', '}', '#', '*').toSet()
        val found =
            words.filter { w ->
                if (w.length >= LONG || w.any { !it.isLetterOrDigit() }) w in ring else w in names
            }
        assertEquals("owner text in the ring", emptyList<String>(), found)
    }

    /** The page's after-state differing from native wherever owner text is a name or a value. */
    private fun lie(after: Map<String, JsonValue>): Map<String, JsonValue> {
        val out = LinkedHashMap(after)
        val skills = (after["skillXP"] as? JsonObject)?.members.orEmpty()
        out["skillXP"] =
            JsonObject(
                skills.mapValues { (_, v) -> JsonNumber(((v as? JsonNumber)?.value ?: 0.0) + 1) } +
                    (skill to JsonNumber(5.0))
            )
        out["questDone"] =
            JsonObject(
                (after["questDone"] as? JsonObject)?.members.orEmpty() +
                    (mark to JsonString(secret))
            )
        out["dayReflections"] =
            JsonObject(
                (after["dayReflections"] as? JsonObject)?.members.orEmpty() +
                    (day to JsonString(secret))
            )
        out["customRules"] =
            JsonObject(
                (after["customRules"] as? JsonObject)?.members.orEmpty() +
                    (skill to JsonObject(mapOf("name" to JsonString(secret))))
            )
        val items = (after["activities"] as? JsonArray)?.items.orEmpty()
        if (items.isNotEmpty()) {
            val last = (items.last() as? JsonObject)?.members.orEmpty()
            out["activities"] =
                JsonArray(
                    items.dropLast(1) +
                        JsonObject(
                            last + ("notes" to JsonString(secret)) + ("name" to JsonString(skill))
                        )
                )
        }
        return out
    }

    /**
     * Every string a golden save holds that the owner (or the calendar) chose: names, notes,
     * skills, results, descriptions, quest keys, days, ids, and the members of owner-keyed objects.
     */
    private fun ownerStrings(states: List<Map<String, JsonValue>>): Set<String> {
        val out = HashSet<String>()
        fun walk(v: JsonValue, ownerKeys: Boolean) {
            when (v) {
                is JsonString -> out += v.value
                is JsonArray -> v.items.forEach { walk(it, false) }
                is JsonObject ->
                    for ((k, x) in v.members) {
                        if (ownerKeys) out += k
                        walk(x, false)
                    }
                else -> Unit
            }
        }
        for (s in states) for ((key, v) in s) walk(v, key in TouchPaths.OWNER_KEYED)
        // the app's own words may appear as path names (attribute ids, fixed members, kinds)
        return out.filter { it.length >= MIN_CHARS && it !in APP_WORDS }.toSet()
    }

    private companion object {
        const val MIN_CHARS = 4
        const val LONG = 12
        val APP_WORDS: Set<String> =
            ShadowKeys.FIXED_MEMBERS +
                ShadowKeys.SHADOW +
                ShadowKeys.KINDS.flatMap { it.split('.') } +
                setOf("true", "false")
    }
}
