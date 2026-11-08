package com.lifemax.rpg.core.quests

import com.lifemax.rpg.core.economy.Attribute
import com.lifemax.rpg.core.economy.Sparks
import com.lifemax.rpg.core.golden.GoldenFiles
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonRead
import com.lifemax.rpg.core.save.JsonReader
import com.lifemax.rpg.core.save.JsonWriter
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The quest content the page exports: the packaged copy is the page's file byte for byte, it
 * decodes, its counts and ids hold, and the strict decoder refuses every kind of damage.
 */
class QuestContentTest {
    private val fixture = GoldenFiles.text(FIXTURE)

    private fun loaded(text: String = fixture): QuestContent =
        when (val read = QuestContentLoader.read(text)) {
            is QuestContentRead.Loaded -> read.content
            is QuestContentRead.Refused -> error("refused at ${read.path}: ${read.reason}")
        }

    @Test
    fun packagedResourceIsTheFixtureByteForByte() {
        val packaged =
            QuestContentLoader::class
                .java
                .getResourceAsStream("/${QuestContentLoader.RESOURCE}")!!
                .use { it.readBytes() }
        assertTrue(
            "run node tools/gen_quest_content.js: the packaged copy differs from the page's file",
            packaged.contentEquals(fixture.toByteArray(Charsets.UTF_8)),
        )
        assertEquals(QuestContentRead.Loaded::class, QuestContentLoader.packaged::class)
    }

    @Test
    fun countsAndIdsHold() {
        val c = loaded()
        assertEquals(53, c.hand.templates.size)
        assertEquals((1..53).map { "t$it" }, c.hand.templates.map { it.id })
        assertEquals(listOf("q1", "q2", "q3", "q4", "q5", "q6"), c.defaultQuests.map { it.id })
        assertEquals(6, c.classes.size)
        assertEquals(4, c.classQuests.size)
        assertEquals(131, c.classQuests.values.sumOf { it.size })
        val ids = (c.defaultQuests + c.classQuests.values.flatten()).map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(listOf(1, 1, 3, 5, 8, 12), c.stageLevels)
    }

    @Test
    fun theContentAgreesWithTheEconomyCode() {
        val c = loaded()
        assertEquals(Attribute.ids, c.attributes)
        assertEquals(Sparks.REROLL_PRICE, c.hand.rerollCost.toDouble(), 0.0)
        assertEquals(1, c.hand.freeRerolls)
        for (t in c.hand.templates) assertEquals(t.id, t.tier * 10, t.xp)
    }

    @Test
    fun everyVerifyKindIsOneTheCheckKnows() {
        val arguments =
            mapOf(
                "self" to 0,
                "none" to 0,
                "plan" to 0,
                "any" to 1,
                "water" to 1,
                "pet" to 1,
                "act" to 2,
            )
        for (t in loaded().hand.templates) {
            val parts = t.verify.split(":")
            assertEquals(t.id, arguments[parts[0]], parts.size - 1)
            if (parts[0] == "pet") assertTrue(t.id, parts[1] in listOf("feed", "pet", "play"))
            if (parts[0] != "pet")
                parts.drop(if (parts[0] == "act") 2 else 1).forEach {
                    assertTrue("${t.id}: $it", it.toIntOrNull() != null)
                }
        }
    }

    @Test
    fun classAttributesCoverEveryClassWithContent() {
        val c = loaded()
        assertEquals(c.classQuests.keys, c.hand.classAttrs.keys)
        assertEquals(c.classes.filter { it.ready }.map { it.id }.toSet(), c.classQuests.keys)
        for ((id, attrs) in c.hand.classAttrs) {
            assertEquals(id, 3, attrs.size)
            // each attribute has templates a Class draw can use outside the rest-day exclusion
            for (a in attrs) assertTrue("$id/$a", c.templatesOf(a).any { it.tier < 3 })
        }
        for (a in c.attributes) assertTrue(a, c.templatesOf(a).any { it.tier < 3 })
    }

    @Test
    fun classQuestShapesMatchThePage() {
        val c = loaded()
        for (q in c.defaultQuests) assertTrue(
            q.id,
            q.attribute == null && q.day == null && q.stage == null,
        )
        for (q in c.classQuests.values.flatten()) {
            assertTrue(q.id, q.attribute != null)
            assertTrue(q.id, q.cadence in listOf("daily", "weekly", "epic"))
            assertTrue(q.id, q.day == null || q.stage == null)
            assertTrue(q.id, q.stage == null || q.stage in 1..5)
        }
        assertEquals(
            15,
            c.classQuests.values.flatten().count { it.day == null && it.stage == null },
        )
    }

    // ---- strictness: every kind of damage is refused, with a place and no exception ----

    private fun refusal(text: String): QuestContentRead.Refused =
        QuestContentLoader.read(text) as? QuestContentRead.Refused
            ?: error("the damaged file was accepted")

    /** [text] with the first [old] replaced; fails the test when [old] is not there. */
    private fun swap(old: String, new: String, text: String = fixture): String {
        assertTrue("fixture has no '$old'", text.contains(old))
        return text.replaceFirst(old, new)
    }

    /**
     * The same text with its content hash made right again, so only the damage itself is refused.
     */
    private fun rehash(text: String): String {
        val parsed = (JsonReader.read(text) as JsonRead.Parsed).value as JsonObject
        val hashed = JsonObject(parsed.members.filterKeys { it != "contentHash" && it != "counts" })
        val hex =
            MessageDigest.getInstance("SHA-256")
                .digest(JsonWriter.write(hashed).toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        return text.replace(Regex("\"contentHash\":\"[0-9a-f]{64}\""), "\"contentHash\":\"$hex\"")
    }

    private fun refusedAt(path: String, text: String) {
        val r = refusal(text)
        assertEquals(r.reason, path, r.path)
    }

    @Test
    fun anUnrelatedTextAndATruncatedFileAreRefused() {
        refusal("not json")
        refusal("")
        refusal(fixture.dropLast(40))
        refusedAt("(file)", "[]")
    }

    @Test
    fun aChangedValueFailsTheHash() {
        refusedAt("contentHash", swap("Move your body", "Move your bodY"))
    }

    @Test
    fun unknownAndMissingKeysAreRefused() {
        refusedAt("surprise", rehash(swap("\"attributes\":", "\"surprise\":1,\"attributes\":")))
        refusedAt("hand.templates[0].extra", rehash(swap("\"tier\":1,", "\"extra\":0,\"tier\":1,")))
        refusedAt(
            "hand.templates[0].xp",
            rehash(
                swap("\"verify\":\"act:strength:10\",\"xp\":10", "\"verify\":\"act:strength:10\"")
            ),
        )
        refusedAt(
            "defaultQuests[0].colour",
            rehash(swap("\"kind\":\"learning\"", "\"kind\":\"learning\",\"colour\":\"red\"")),
        )
        refusedAt(
            "classQuests.athlete[0].attribute",
            rehash(
                swap(
                    "\"attribute\":\"physique\",\"stage\":1,\"id\":\"cc_athlete_01\"",
                    "\"stage\":1,\"id\":\"cc_athlete_01\"",
                )
            ),
        )
        refusedAt(
            "classes[0].extra",
            rehash(
                swap(
                    "{\"id\":\"athlete\",\"ready\":true}",
                    "{\"id\":\"athlete\",\"ready\":true,\"extra\":1}",
                )
            ),
        )
        refusedAt("counts.extra", swap("\"counts\":{", "\"counts\":{\"extra\":1,"))
    }

    /** The file without its top-level [key] (the text is one member per line). */
    private fun dropKey(key: String): String =
        fixture.lines().filterNot { it.trimStart().startsWith("\"$key\":") }.joinToString("\n")

    @Test
    fun aMissingTopLevelKeyIsRefused() {
        refusedAt("stageLevels", dropKey("stageLevels"))
        refusedAt("counts", dropKey("counts"))
    }

    @Test
    fun wrongKindsAndRangesAreRefused() {
        refusedAt("hand.templates[0].tier", rehash(swap("\"tier\":1,", "\"tier\":4,")))
        refusedAt("hand.templates[0].tier", rehash(swap("\"tier\":1,", "\"tier\":\"1\",")))
        refusedAt(
            "hand.templates[0].xp",
            rehash(
                swap(
                    "\"verify\":\"act:strength:10\",\"xp\":10",
                    "\"verify\":\"act:strength:10\",\"xp\":1.5",
                )
            ),
        )
        refusedAt(
            "hand.templates[0].xp",
            rehash(
                swap(
                    "\"verify\":\"act:strength:10\",\"xp\":10",
                    "\"verify\":\"act:strength:10\",\"xp\":-1",
                )
            ),
        )
        refusedAt(
            "hand.templates[0].attr",
            rehash(swap("\"attr\":\"physique\"", "\"attr\":\"strength\"")),
        )
        refusedAt(
            "hand.templates[0].name",
            rehash(swap("\"name\":\"Move your body\"", "\"name\":null")),
        )
        refusedAt(
            "hand.classAttrs.athlete",
            rehash(swap("\"athlete\":[\"physique\"", "\"athlete\":[\"muscle\"")),
        )
        refusedAt(
            "classes[0].ready",
            rehash(swap("{\"id\":\"athlete\",\"ready\":true}", "{\"id\":\"athlete\",\"ready\":1}")),
        )
        refusedAt("schemaVersion", rehash(swap("\"schemaVersion\":1", "\"schemaVersion\":2")))
        refusedAt("schemaVersion", swap("\"schemaVersion\":1", "\"schemaVersion\":\"1\""))
        refusedAt(
            "attributes",
            rehash(
                swap("\"attributes\":[\"physique\",", "\"attributes\":[\"physique\",\"physique\",")
            ),
        )
    }

    @Test
    fun brokenReferencesAreRefused() {
        refusedAt("hand.templates", rehash(swap("\"id\":\"t2\"", "\"id\":\"t1\"")))
        refusedAt("quests", rehash(swap("\"id\":\"cc_athlete_02\"", "\"id\":\"cc_athlete_01\"")))
        refusedAt("quests", rehash(swap("\"id\":\"cc_athlete_02\"", "\"id\":\"q1\"")))
        refusedAt("counts.templates", swap("\"templates\":53", "\"templates\":52"))
        val extraClass = rehash(swap("\"classQuests\":{", "\"classQuests\":{\"druid\":[],"))
        refusedAt("classQuests", swap("\"contentClasses\":4", "\"contentClasses\":5", extraClass))
    }

    private companion object {
        const val FIXTURE = "quest-content.json"
    }
}
