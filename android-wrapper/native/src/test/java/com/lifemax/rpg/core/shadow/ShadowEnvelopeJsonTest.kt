package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.save.JsonWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The strict decoders (ECONOMY_SHADOW_PLAN 2.4): one valid envelope, base part and configure
 * message, and more than forty malformed variants, each refused with its typed reason and a place
 * named only by fixed keys.
 */
class ShadowEnvelopeJsonTest {
    private val page = FakeShadowPage()
    private val before: Map<String, JsonValue> =
        mapOf("sparks" to num(1), "activities" to JsonArray(listOf(obj("id" to str("a")))))
    private val valid: JsonObject =
        FakeShadowPage.parse(
            page.envelope(
                "quest.toggle",
                before,
                before +
                    ("sparks" to num(2)) +
                    ("activities" to
                        JsonArray(listOf(obj("id" to str("a")), obj("id" to str("b"))))),
                toasts = listOf("Quest complete · +15 XP"),
            )
        ) as JsonObject

    @Test
    fun aValidEnvelopeDecodesWithEveryPart() {
        val e = (ShadowEnvelopeJson.envelope(JsonWriter.write(valid)) as ShadowDecode.Ok).value
        assertEquals("quest.toggle", e.kind)
        assertEquals(1, e.seq)
        assertEquals(0, e.base)
        assertEquals(listOf(true), e.env.saves)
        assertEquals(mapOf("sparks" to num(2)), e.after.set)
        assertEquals(
            RecordDelta(2, listOf(RecordChange(1, obj("id" to str("b"))))),
            e.after.rec["activities"],
        )
        assertEquals(listOf("Quest complete · +15 XP"), e.toasts)
        assertEquals("UTC", e.zone)
    }

    @Test
    fun malformedEnvelopesAreRefusedWithTheirReason() {
        val cases =
            listOf<Triple<String, ShadowRefusal, (JsonObject) -> JsonValue>>(
                Triple("v2", ShadowRefusal.UNSUPPORTED) { it.with("v", num(2)) },
                Triple("v text", ShadowRefusal.UNSUPPORTED) { it.with("v", str("1")) },
                Triple("scope", ShadowRefusal.UNSUPPORTED) { it.with("scope", str("director")) },
                Triple("extra key", ShadowRefusal.UNKNOWN_KEY) { it.with("extra", num(1)) },
                Triple("no kind", ShadowRefusal.MISSING_KEY) { it.without("kind") },
                Triple("no after", ShadowRefusal.MISSING_KEY) { it.without("after") },
                Triple("no flags", ShadowRefusal.MISSING_KEY) { it.without("flags") },
                Triple("seq 0", ShadowRefusal.OUT_OF_RANGE) { it.with("seq", num(0)) },
                Triple("seq negative", ShadowRefusal.OUT_OF_RANGE) { it.with("seq", num(-3)) },
                Triple("seq fraction", ShadowRefusal.WRONG_TYPE) { it.with("seq", num(2.5)) },
                Triple("seq text", ShadowRefusal.WRONG_TYPE) { it.with("seq", str("2")) },
                Triple("seq huge", ShadowRefusal.WRONG_TYPE) { it.with("seq", num(1e300)) },
                Triple("base not below seq", ShadowRefusal.OUT_OF_RANGE) {
                    it.with("base", num(2))
                },
                Triple("epoch negative", ShadowRefusal.OUT_OF_RANGE) { it.with("epoch", num(-1)) },
                Triple("kind upper", ShadowRefusal.BAD_KIND) {
                    it.with("kind", str("Quest.Toggle"))
                },
                Triple("kind spaces", ShadowRefusal.BAD_KIND) {
                    it.with("kind", str("quest toggle"))
                },
                Triple("kind empty", ShadowRefusal.OUT_OF_RANGE) { it.with("kind", str("")) },
                Triple("kind long", ShadowRefusal.OUT_OF_RANGE) {
                    it.with("kind", str("a".repeat(41)))
                },
                Triple("sid bad", ShadowRefusal.OUT_OF_RANGE) { it.with("sid", str("p 1")) },
                Triple("sid number", ShadowRefusal.WRONG_TYPE) { it.with("sid", num(1)) },
                Triple("args object", ShadowRefusal.WRONG_TYPE) { it.with("args", obj()) },
                Triple("args large", ShadowRefusal.TOO_LARGE) {
                    it.with("args", JsonArray(listOf(str("x".repeat(ShadowLimits.MAX_ARGS_CHARS)))))
                },
                Triple("t1 before t0", ShadowRefusal.BAD_TIME) {
                    it.with("t1", num(FakeShadowPage.T0 - 1))
                },
                Triple("t0 negative", ShadowRefusal.OUT_OF_RANGE) { it.with("t0", num(-1)) },
                Triple("zone unknown", ShadowRefusal.BAD_ZONE) {
                    it.with("zone", str("Mars/Olympus"))
                },
                Triple("zone empty", ShadowRefusal.OUT_OF_RANGE) { it.with("zone", str("")) },
                Triple("offset far", ShadowRefusal.OUT_OF_RANGE) {
                    it.with("offset", num(24 * 60))
                },
                Triple("locale odd", ShadowRefusal.OUT_OF_RANGE) {
                    it.with("locale", str("en_GB.UTF-8"))
                },
                Triple("env unknown part", ShadowRefusal.UNKNOWN_KEY) {
                    it.with("env", obj("secret" to num(1)))
                },
                Triple("env array", ShadowRefusal.WRONG_TYPE) { it.with("env", JsonArray.EMPTY) },
                Triple("env large", ShadowRefusal.TOO_LARGE) {
                    it.with("env", obj("dom" to str("x".repeat(ShadowLimits.MAX_ENV_CHARS))))
                },
                Triple("saves too many", ShadowRefusal.TOO_LARGE) {
                    it.with(
                        "env",
                        obj("saves" to JsonArray(List(9) { obj("ok" to JsonBool.TRUE) })),
                    )
                },
                Triple("save ok text", ShadowRefusal.WRONG_TYPE) {
                    it.with("env", obj("saves" to JsonArray(listOf(obj("ok" to str("yes"))))))
                },
                Triple("save extra key", ShadowRefusal.UNKNOWN_KEY) {
                    it.with(
                        "env",
                        obj(
                            "saves" to
                                JsonArray(listOf(obj("ok" to JsonBool.TRUE, "why" to str("full"))))
                        ),
                    )
                },
                Triple("delta extra key", ShadowRefusal.UNKNOWN_KEY) {
                    it.with("after", delta(extra = true))
                },
                Triple("set not a shadow key", ShadowRefusal.NOT_SHADOW_KEY) {
                    it.with("after", delta(set = obj("mediaItems" to JsonArray.EMPTY)))
                },
                Triple("del not a shadow key", ShadowRefusal.NOT_SHADOW_KEY) {
                    it.with("before", delta(del = JsonArray(listOf(str("journal")))))
                },
                Triple("del number", ShadowRefusal.WRONG_TYPE) {
                    it.with("before", delta(del = JsonArray(listOf(num(1)))))
                },
                Triple("rec of a plain key", ShadowRefusal.NOT_SHADOW_KEY) {
                    it.with("after", delta(rec = obj("sparks" to rec(1, listOf(0 to num(1))))))
                },
                Triple("key twice", ShadowRefusal.BAD_DELTA) {
                    it.with(
                        "after",
                        delta(set = obj("sparks" to num(3)), del = JsonArray(listOf(str("sparks")))),
                    )
                },
                Triple("rec index past len", ShadowRefusal.BAD_DELTA) {
                    it.with("after", delta(rec = obj("quests" to rec(1, listOf(1 to obj())))))
                },
                Triple("rec index twice", ShadowRefusal.BAD_DELTA) {
                    it.with(
                        "after",
                        delta(rec = obj("quests" to rec(3, listOf(0 to obj(), 0 to obj())))),
                    )
                },
                Triple("rec pair short", ShadowRefusal.WRONG_TYPE) {
                    it.with(
                        "after",
                        delta(
                            rec =
                                obj(
                                    "quests" to
                                        obj(
                                            "len" to num(1),
                                            "at" to JsonArray(listOf(JsonArray(listOf(num(0))))),
                                        )
                                )
                        ),
                    )
                },
                Triple("rec too many", ShadowRefusal.TOO_LARGE) {
                    it.with(
                        "after",
                        delta(
                            rec = obj("quests" to rec(300, (0 until 257).map { i -> i to obj() }))
                        ),
                    )
                },
                Triple("rec len negative", ShadowRefusal.OUT_OF_RANGE) {
                    it.with("after", delta(rec = obj("quests" to rec(-1, emptyList()))))
                },
                Triple("toasts too many", ShadowRefusal.TOO_LARGE) {
                    it.with("toasts", JsonArray(List(21) { str("t") }))
                },
                Triple("toast too long", ShadowRefusal.TOO_LARGE) {
                    it.with("toasts", JsonArray(listOf(str("t".repeat(301)))))
                },
                Triple("toast number", ShadowRefusal.WRONG_TYPE) {
                    it.with("toasts", JsonArray(listOf(num(1))))
                },
                Triple("cost negative", ShadowRefusal.OUT_OF_RANGE) {
                    it.with("cost", obj("ms" to num(-1)))
                },
                Triple("cost extra", ShadowRefusal.UNKNOWN_KEY) {
                    it.with("cost", obj("ms" to num(1), "cpu" to num(1)))
                },
                Triple("flag unknown", ShadowRefusal.OUT_OF_RANGE) {
                    it.with("flags", JsonArray(listOf(str("urgent"))))
                },
                Triple("flag twice", ShadowRefusal.OUT_OF_RANGE) {
                    it.with("flags", JsonArray(listOf(str("threw"), str("threw"))))
                },
                Triple("not an object", ShadowRefusal.NOT_AN_OBJECT) { JsonArray.EMPTY },
                Triple("null", ShadowRefusal.NOT_AN_OBJECT) { JsonNull },
            )
        assertTrue(cases.size >= 40)
        for ((name, reason, change) in cases) {
            val d = ShadowEnvelopeJson.envelope(JsonWriter.write(change(valid)))
            assertTrue("$name: $d", d is ShadowDecode.Refused && d.reason == reason)
            assertTrue("$name names no value", !(d as ShadowDecode.Refused).at.contains("x"))
        }
    }

    @Test
    fun textThatIsNotJsonOrTooLargeIsRefusedBeforeReading() {
        for (t in listOf("", "{", "{\"v\":1,}", "NaN", "{\"v\":1} trailing", "﻿{}")) assertEquals(
            t,
            ShadowRefusal.NOT_JSON,
            (ShadowEnvelopeJson.envelope(t) as ShadowDecode.Refused).reason,
        )
        val big = " ".repeat(ShadowLimits.MAX_ENVELOPE_CHARS + 1)
        assertEquals(
            ShadowRefusal.TOO_LARGE,
            (ShadowEnvelopeJson.envelope(big) as ShadowDecode.Refused).reason,
        )
    }

    @Test
    fun basePartsAndConfigureAreStrictToo() {
        val part = FakeShadowPage.parse(FakeShadowPage().base(before).single()) as JsonObject
        assertTrue(ShadowEnvelopeJson.basePart(JsonWriter.write(part)) is ShadowDecode.Ok)
        val badParts =
            listOf(
                part.with("part", num(1)) to ShadowRefusal.OUT_OF_RANGE,
                part.with("parts", num(33)) to ShadowRefusal.TOO_LARGE,
                part.with("parts", num(0)) to ShadowRefusal.OUT_OF_RANGE,
                part.with("text", num(1)) to ShadowRefusal.WRONG_TYPE,
                part.with("more", num(1)) to ShadowRefusal.UNKNOWN_KEY,
                part.without("epoch") to ShadowRefusal.MISSING_KEY,
            )
        for ((p, reason) in badParts) assertEquals(
            reason,
            (ShadowEnvelopeJson.basePart(JsonWriter.write(p)) as ShadowDecode.Refused).reason,
        )
        assertEquals(
            ShadowRefusal.NOT_SHADOW_KEY,
            (ShadowEnvelopeJson.baseKeys("{\"keys\":{\"mediaItems\":[]}}") as ShadowDecode.Refused)
                .reason,
        )
        assertEquals(
            ShadowRefusal.UNKNOWN_KEY,
            (ShadowEnvelopeJson.baseKeys("{\"keys\":{},\"extra\":1}") as ShadowDecode.Refused)
                .reason,
        )
        val config = FakeShadowPage.parse(ShadowSessionTest.configure("f")) as JsonObject
        assertTrue(ShadowEnvelopeJson.configure(JsonWriter.write(config)) is ShadowDecode.Ok)
        assertEquals(
            ShadowRefusal.WRONG_TYPE,
            (ShadowEnvelopeJson.configure(JsonWriter.write(config.with("enabled", num(1))))
                    as ShadowDecode.Refused)
                .reason,
        )
        assertEquals(
            ShadowRefusal.MISSING_KEY,
            (ShadowEnvelopeJson.configure(
                    JsonWriter.write(config.with("page", obj("build" to str(""))))
                ) as ShadowDecode.Refused)
                .reason,
        )
    }

    private fun delta(
        set: JsonObject = obj(),
        del: JsonArray = JsonArray.EMPTY,
        rec: JsonObject = obj(),
        extra: Boolean = false,
    ): JsonObject {
        val m = linkedMapOf<String, JsonValue>("set" to set, "del" to del, "rec" to rec)
        if (extra) m["sum"] = num(1)
        return JsonObject(m)
    }

    private fun rec(len: Int, at: List<Pair<Int, JsonValue>>) =
        obj("len" to num(len), "at" to JsonArray(at.map { (i, v) -> JsonArray(listOf(num(i), v)) }))

    private fun JsonObject.with(key: String, value: JsonValue) =
        JsonObject(LinkedHashMap(members).apply { put(key, value) })

    private fun JsonObject.without(key: String) =
        JsonObject(LinkedHashMap(members).apply { remove(key) })

    private fun num(n: Number) = JsonNumber(n.toDouble())

    private fun str(s: String) = JsonString(s)

    private fun obj(vararg m: Pair<String, JsonValue>) = JsonObject(linkedMapOf(*m))
}
