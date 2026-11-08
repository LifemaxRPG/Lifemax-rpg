package com.lifemax.rpg.core.quests

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonRead
import com.lifemax.rpg.core.save.JsonReader
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.save.JsonWriter
import java.security.MessageDigest

/** What reading the quest content gave: the typed content, or the first thing wrong with it. */
sealed interface QuestContentRead {
    data class Loaded(val content: QuestContent) : QuestContentRead

    /**
     * [path] names the place (`hand.templates[3].tier`) and [reason] the fault; neither quotes
     * content.
     */
    data class Refused(val path: String, val reason: String) : QuestContentRead
}

/**
 * Reads `quest-content.json` strictly: a key that is missing, a key that is not known, a value of
 * the wrong kind or out of range, a count or the content hash that does not match, a duplicate id,
 * or a reference to an unknown attribute refuses the whole file. Never throws; the content is
 * either complete or not returned.
 */
object QuestContentLoader {
    /** The file's place among the app's resources. */
    const val RESOURCE = "quests/quest-content.json"

    /** The content packaged with the app, read once; a refusal here means the build is broken. */
    val packaged: QuestContentRead by lazy {
        val bytes =
            QuestContentLoader::class.java.getResourceAsStream("/$RESOURCE")?.use { it.readBytes() }
        if (bytes == null) QuestContentRead.Refused(RESOURCE, "resource is missing")
        else read(String(bytes, Charsets.UTF_8))
    }

    fun read(text: String): QuestContentRead =
        when (val parsed = JsonReader.read(text)) {
            is JsonRead.Malformed ->
                QuestContentRead.Refused("(text)", "not JSON: ${parsed.reason} at ${parsed.offset}")
            is JsonRead.Parsed ->
                try {
                    QuestContentRead.Loaded(Decoder().decode(parsed.value))
                } catch (refusal: Refusal) {
                    QuestContentRead.Refused(refusal.path, refusal.reason)
                }
        }

    private class Refusal(val path: String, val reason: String) : Exception("$path: $reason")

    /** Decodes one parsed file; throws [Refusal] at the first fault, caught by [read]. */
    private class Decoder {
        fun decode(root: JsonValue): QuestContent {
            val top = fields(root, "", TOP)
            if (int(top.getValue("schemaVersion"), "schemaVersion") != QuestContent.SCHEMA_VERSION)
                fail("schemaVersion", "this build reads version ${QuestContent.SCHEMA_VERSION}")
            val attributes = strings(top.getValue("attributes"), "attributes")
            if (attributes.isEmpty() || attributes.toSet().size != attributes.size)
                fail("attributes", "must be a non-empty list of distinct ids")
            val content =
                QuestContent(
                    schemaVersion = int(top.getValue("schemaVersion"), "schemaVersion"),
                    contentHash = text(top.getValue("contentHash"), "contentHash"),
                    attributes = attributes,
                    hand = hand(top.getValue("hand"), attributes),
                    defaultQuests =
                        list(top.getValue("defaultQuests"), "defaultQuests") { v, p ->
                            quest(v, p, attributes, attributeRequired = false)
                        },
                    stageLevels =
                        list(top.getValue("stageLevels"), "stageLevels") { v, p -> int(v, p) },
                    classes = list(top.getValue("classes"), "classes", ::classInfo),
                    classQuests = classQuests(top.getValue("classQuests"), attributes),
                    economyHash = hash(top.getValue("economyHash"), "economyHash"),
                )
            checkCounts(top.getValue("counts"), content)
            checkHash(top, content.contentHash)
            checkReferences(content)
            return content
        }

        private fun hand(value: JsonValue, attributes: List<String>): HandContent {
            val h = fields(value, "hand", HAND)
            val templates =
                list(h.getValue("templates"), "hand.templates") { v, p ->
                    template(v, p, attributes)
                }
            val classAttrs =
                (h.getValue("classAttrs") as? JsonObject
                        ?: fail("hand.classAttrs", "must be an object"))
                    .members
                    .mapValues { (id, v) ->
                        strings(v, "hand.classAttrs.$id").also { list ->
                            if (list.any { it !in attributes })
                                fail("hand.classAttrs.$id", "names an unknown attribute")
                        }
                    }
            return HandContent(
                freeRerolls = natural(h.getValue("freeRerolls"), "hand.freeRerolls"),
                rerollCost = natural(h.getValue("rerollCost"), "hand.rerollCost"),
                classAttrs = classAttrs,
                templates = templates,
            )
        }

        private fun template(
            value: JsonValue,
            path: String,
            attributes: List<String>,
        ): HandTemplate {
            val t = fields(value, path, TEMPLATE)
            val attr = text(t.getValue("attr"), "$path.attr")
            if (attr !in attributes) fail("$path.attr", "unknown attribute")
            val tier = int(t.getValue("tier"), "$path.tier")
            if (tier !in 1..MAX_TIER) fail("$path.tier", "must be 1 to $MAX_TIER")
            return HandTemplate(
                id = text(t.getValue("id"), "$path.id"),
                attr = attr,
                tier = tier,
                name = text(t.getValue("name"), "$path.name"),
                desc = text(t.getValue("desc"), "$path.desc"),
                verify = text(t.getValue("verify"), "$path.verify"),
                xp = natural(t.getValue("xp"), "$path.xp"),
            )
        }

        private fun quest(
            value: JsonValue,
            path: String,
            attributes: List<String>,
            attributeRequired: Boolean,
        ): ContentQuest {
            val q =
                fields(
                    value,
                    path,
                    QUEST_REQUIRED + if (attributeRequired) setOf("attribute") else emptySet(),
                    QUEST_OPTIONAL + if (attributeRequired) emptySet() else setOf("attribute"),
                )
            val attribute = q["attribute"]?.let { text(it, "$path.attribute") }
            if (attribute != null && attribute !in attributes)
                fail("$path.attribute", "unknown attribute")
            return ContentQuest(
                id = text(q.getValue("id"), "$path.id"),
                name = text(q.getValue("name"), "$path.name"),
                desc = text(q.getValue("desc"), "$path.desc"),
                cadence = text(q.getValue("cadence"), "$path.cadence"),
                xp = natural(q.getValue("xp"), "$path.xp"),
                kind = text(q.getValue("kind"), "$path.kind"),
                attribute = attribute,
                day = q["day"]?.let { text(it, "$path.day") },
                stage = q["stage"]?.let { natural(it, "$path.stage") },
            )
        }

        private fun classInfo(value: JsonValue, path: String): ClassInfo {
            val c = fields(value, path, setOf("id", "ready"))
            val ready =
                (c.getValue("ready") as? JsonBool)?.value
                    ?: fail("$path.ready", "must be true or false")
            return ClassInfo(text(c.getValue("id"), "$path.id"), ready)
        }

        private fun classQuests(value: JsonValue, attributes: List<String>) =
            (value as? JsonObject ?: fail("classQuests", "must be an object")).members.mapValues {
                (id, v) ->
                list(v, "classQuests.$id") { q, p ->
                    quest(q, p, attributes, attributeRequired = true)
                }
            }

        private fun checkCounts(value: JsonValue, c: QuestContent) {
            val counts = fields(value, "counts", COUNTS)
            val actual =
                mapOf(
                    "templates" to c.hand.templates.size,
                    "defaultQuests" to c.defaultQuests.size,
                    "classes" to c.classes.size,
                    "contentClasses" to c.classQuests.size,
                    "classQuests" to c.classQuests.values.sumOf { it.size },
                )
            for ((key, n) in actual) if (natural(counts.getValue(key), "counts.$key") != n)
                fail("counts.$key", "does not match the content")
        }

        private fun checkHash(top: Map<String, JsonValue>, expected: String) {
            val hashed = JsonObject(top.filterKeys { it != "contentHash" && it != "counts" })
            val digest =
                MessageDigest.getInstance("SHA-256")
                    .digest(JsonWriter.write(hashed).toByteArray(Charsets.UTF_8))
            if (digest.joinToString("") { "%02x".format(it) } != expected)
                fail("contentHash", "does not match the content")
        }

        private fun checkReferences(c: QuestContent) {
            val templateIds = c.hand.templates.map { it.id }
            if (templateIds.toSet().size != templateIds.size)
                fail("hand.templates", "template ids must be distinct")
            val questIds = (c.defaultQuests + c.classQuests.values.flatten()).map { it.id }
            if (questIds.toSet().size != questIds.size) fail("quests", "quest ids must be distinct")
            val classIds = c.classes.map { it.id }
            if (classIds.toSet().size != classIds.size)
                fail("classes", "class ids must be distinct")
            if (c.classQuests.keys.any { it !in classIds })
                fail("classQuests", "names a class that is not in classes")
            if (c.hand.classAttrs.keys.any { it !in c.classQuests })
                fail("hand.classAttrs", "names a class without content")
        }

        // ---- typed reads -------------------------------------------------------------------

        /**
         * The members of [value], which must be an object with every [required] key, any [optional]
         * one, and no other.
         */
        private fun fields(
            value: JsonValue,
            path: String,
            required: Set<String>,
            optional: Set<String> = emptySet(),
        ): Map<String, JsonValue> {
            val members =
                (value as? JsonObject ?: fail(path.ifEmpty { "(file)" }, "must be an object"))
                    .members
            (members.keys - required - optional).firstOrNull()?.let {
                fail(join(path, it), "unknown key")
            }
            (required - members.keys).firstOrNull()?.let { fail(join(path, it), "missing key") }
            return members
        }

        private fun <T> list(
            value: JsonValue,
            path: String,
            read: (JsonValue, String) -> T,
        ): List<T> =
            (value as? JsonArray ?: fail(path, "must be a list")).items.mapIndexed { i, v ->
                read(v, "$path[$i]")
            }

        private fun strings(value: JsonValue, path: String): List<String> =
            list(value, path) { v, p -> text(v, p) }

        private fun text(value: JsonValue, path: String): String =
            (value as? JsonString ?: fail(path, "must be text")).value

        /** 64 lower-case hex digits: a SHA-256 written by the generator. */
        private fun hash(value: JsonValue, path: String): String =
            text(value, path).also {
                if (it.length != SHA256_HEX || !it.all { c -> c in '0'..'9' || c in 'a'..'f' })
                    fail(path, "must be a SHA-256 in hex")
            }

        private fun int(value: JsonValue, path: String): Int {
            val d = (value as? JsonNumber ?: fail(path, "must be a number")).value
            if (d != Math.floor(d) || d < Int.MIN_VALUE || d > Int.MAX_VALUE)
                fail(path, "must be a whole number")
            return d.toInt()
        }

        private fun natural(value: JsonValue, path: String): Int =
            int(value, path).also { if (it < 0) fail(path, "must not be negative") }

        private fun join(path: String, key: String) = if (path.isEmpty()) key else "$path.$key"

        private fun fail(path: String, reason: String): Nothing = throw Refusal(path, reason)
    }

    private const val MAX_TIER = 3
    private const val SHA256_HEX = 64
    private val TOP =
        setOf(
            "schemaVersion",
            "contentHash",
            "counts",
            "attributes",
            "hand",
            "defaultQuests",
            "stageLevels",
            "classes",
            "classQuests",
            "economyHash",
        )
    private val HAND = setOf("freeRerolls", "rerollCost", "classAttrs", "templates")
    private val TEMPLATE = setOf("id", "attr", "tier", "name", "desc", "verify", "xp")
    private val COUNTS =
        setOf("templates", "defaultQuests", "classes", "contentClasses", "classQuests")
    private val QUEST_REQUIRED = setOf("id", "name", "desc", "cadence", "xp", "kind")
    private val QUEST_OPTIONAL = setOf("day", "stage")
}
