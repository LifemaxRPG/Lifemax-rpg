package com.lifemax.rpg.feature.pet.world

import com.lifemax.rpg.feature.pet.world.WorldFixtures.LILY
import com.lifemax.rpg.feature.pet.world.WorldFixtures.TED
import com.lifemax.rpg.feature.pet.world.WorldFixtures.at
import com.lifemax.rpg.feature.pet.world.WorldFixtures.obj
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The world blob (state.petWorld's contents): written and read back unchanged, three decimals, well
 * under 8 KB; 40 malformed variants refused by the strict read; the restoring read keeps the good
 * cat when the other is damaged and refuses a newer version or an oversize blob whole.
 */
class WorldBlobJsonTest {
    private val decode: (JSONObject) -> Unit = { WorldBlobJson.decode(it) }
    private val base: JSONObject
        get() = WorldBlobJson.encode(WorldFixtures.blob())

    @Test
    fun aBlobRoundTrips() {
        val blob = WorldFixtures.blob()
        assertEquals(blob, WorldBlobJson.decode(WorldBlobJson.encode(blob)))
        assertTrue(jsonBytes(WorldBlobJson.encode(blob)) < WorldContract.BLOB_MAX_BYTES / 2)
    }

    @Test
    fun numbersAreSavedWithThreeDecimals() {
        val c = WorldFixtures.cat().copy(mood = Mood(0.123456, -0.98765), voice = 1.0004)
        val j = WorldBlobJson.encode(WorldFixtures.blob().copy(cats = mapOf(TED to c)))
        val cat = j.getJSONObject("cats").getJSONObject(TED)
        assertEquals(0.123, cat.getJSONObject("mood").getDouble("v"), 0.0)
        assertEquals(-0.988, cat.getJSONObject("mood").getDouble("a"), 0.0)
        assertEquals(1.0, cat.getDouble("voice"), 0.0)
    }

    @Test
    fun malformedBlobsAreRefused() {
        val ted = "cats.$TED"
        val v =
            linkedMapOf<String, (JSONObject) -> Unit>(
                "unknown top key" to { it.put("mind", 1) },
                "missing outbox" to { it.remove("outbox") },
                "version 0" to { it.put("v", 0) },
                "version 2" to { it.put("v", 2) },
                "unknown start" to { it.put("imported", "cloud") },
                "three cats" to { it.getJSONObject("cats").put("c999", obj(it, ted)) },
                "a cat id out of form" to
                    {
                        it.getJSONObject("cats").put("Ted", obj(it, ted)).remove(TED)
                    },
                "a cat is a list" to { it.getJSONObject("cats").put(TED, JSONArray()) },
                "unknown cat key" to { obj(it, ted).put("brain", 1) },
                "missing drives" to { obj(it, ted).remove("dr") },
                "a drive over 1" to { obj(it, "$ted.dr").put("en", 1.01) },
                "a drive as words" to { obj(it, "$ted.dr").put("pl", "NaN") },
                "a drive missing" to { obj(it, "$ted.dr").remove("gr") },
                "hunger saved here" to { obj(it, "$ted.dr").put("hu", 0.5) },
                "an unknown surface" to { obj(it, "$ted.pos").put("s", "roof") },
                "x out of the house" to { obj(it, "$ted.pos").put("x", 700) },
                "z past the glass" to { obj(it, "$ted.pos").put("z", 101) },
                "dir 0" to { obj(it, "$ted.pos").put("dir", 0) },
                "act out of form" to { obj(it, ted).put("act", "Sleep!") },
                "mood under -1" to { obj(it, "$ted.mood").put("v", -1.5) },
                "trust a string" to { obj(it, "$ted.trust").put("fam", "high") },
                "seven spots" to
                    {
                        obj(it, "$ted.spots")
                            .put("box", 1)
                            .put("tree", 1)
                            .put("sill", 1)
                            .put("rug", 1)
                            .put("pouf", 1)
                    },
                "a spot the page does not know" to { obj(it, "$ted.spots").put("attic", 4) },
                "a spot over 100" to { obj(it, "$ted.spots").put("bed", 101) },
                "three innate quirks" to
                    {
                        (at(it, "$ted.qk.inn") as JSONArray).put("shadow_chaser")
                    },
                "an acquired quirk as innate" to
                    {
                        (at(it, "$ted.qk.inn") as JSONArray).put(0, "toy_hoarder")
                    },
                "a counter negative" to { obj(it, "$ted.qk.c").put("g", -1) },
                "rhythm 25 hours" to { (at(it, "$ted.rhythm") as JSONArray).put(1) },
                "rhythm over 100" to { (at(it, "$ted.rhythm") as JSONArray).put(3, 140) },
                "an unknown first moment" to
                    {
                        (at(it, "$ted.fx") as JSONArray).put("first_flight")
                    },
                "a first moment twice" to { (at(it, "$ted.fx") as JSONArray).put("first_glass") },
                "two rng streams" to { (at(it, "$ted.rng") as JSONArray).remove(2) },
                "an rng over 32 bits" to { (at(it, "$ted.rng") as JSONArray).put(0, 1L shl 40) },
                "day out of form" to { obj(it, ted).put("day", "30/10/2026") },
                "bed time 40 h" to { obj(it, "$ted.sched").put("bed", 40) },
                "voice 3" to { obj(it, ted).put("voice", 3) },
                "seventeen cheers seen" to
                    {
                        obj(it, ted).put("cheerSeen", JSONArray((1..17).toList()))
                    },
                "pair cool of an unknown kind" to { obj(it, "pair.cool").put("fight", 1) },
                "pair last kind unknown" to { obj(it, "pair.last").put("kind", "dance") },
                "nine toys" to { it.put("toys", JSONArray((1..9).map { _ -> at(it, "toys.0") })) },
                "a toy the page does not know" to { obj(it, "toys.0").put("id", "laser") },
                "a toy held by a word" to { obj(it, "toys.1").put("by", "Ted") },
                "gift with an unknown key" to { obj(it, "gift").put("note", "hi") },
                "outbox: an op twice" to
                    {
                        (at(it, "outbox") as JSONArray).put(at(it, "outbox.0"))
                    },
                "outbox: a world save queued" to { obj(it, "outbox.0").put("cmd", "world") },
                "outbox: an op that is not its command's" to
                    {
                        obj(it, "outbox.0").put("op", "other")
                    },
                "outbox: a malformed command" to { obj(it, "outbox.1.args").remove("winner") },
                "away: four lines" to
                    {
                        (at(it, "away.lines") as JSONArray).put("a").put("b").put("c")
                    },
                "away: a control character" to
                    {
                        (at(it, "away.lines") as JSONArray).put(0, "a\u0007b")
                    },
                "oversize" to { pad(it) },
            )
        val codes = WorldFixtures.assertAllRefused(base, v, decode)
        assertTrue(codes.size >= 30)
        assertEquals("version", codes["version 2"])
        assertEquals("size", codes["oversize"])
    }

    @Test
    fun theRestoringReadKeepsTheGoodCat() {
        val j = base
        obj(j, "cats.$LILY").put("dr", "tired")
        val r = WorldBlobJson.read(j)
        assertEquals(setOf(TED), r.blob!!.cats.keys)
        assertEquals(setOf(LILY), r.rejectedCats)
        assertEquals(WorldFixtures.blob().cats[TED], r.blob!!.cats[TED])
    }

    @Test
    fun theRestoringReadRefusesANewerOrOversizeBlobWhole() {
        val newer = WorldBlobJson.read(base.put("v", 2))
        assertNull(newer.blob)
        assertEquals("version", newer.error)
        val big = base
        pad(big)
        assertEquals("size", WorldBlobJson.read(big).error)
        assertEquals("shape", WorldBlobJson.read(base.put("extra", 1)).error)
    }

    /**
     * Fills every list to its limit with well-formed entries (sixteen dwell reports of every place
     * and toy, eight toys, three long away lines, sixteen cheers seen): over 8 KB, each part legal.
     */
    private fun pad(j: JSONObject) {
        j.put("toys", JSONArray((1..WorldContract.MAX_TOYS_ON_FLOOR).map { _ -> at(j, "toys.0") }))
        j.getJSONObject("away")
            .put(
                "lines",
                JSONArray(
                    List(WorldContract.MAX_AWAY_LINES) { "z".repeat(WorldContract.MAX_AWAY_LINE) }
                ),
            )
        for (id in listOf(TED, LILY)) obj(j, "cats.$id")
            .put(
                "cheerSeen",
                JSONArray((1..WorldContract.MAX_CHEER_SEEN).map { 1_793_361_000_000L + it }),
            )
        val all = JSONArray()
        for (n in 1..WorldContract.MAX_OUTBOX) {
            val c =
                DwellReport(
                    OpIds.of(TED, "dwell", n.toLong()),
                    TED,
                    WorldFixtures.DAY,
                    WorldContract.SPOTS.associateWith { 86_000 },
                    WorldContract.TOYS.associateWith { 86_000 },
                )
            all.put(
                JSONObject()
                    .put("op", c.op)
                    .put("cmd", c.name)
                    .put("args", WorldCommandJson.encode(c))
            )
        }
        j.put("outbox", all)
        assertTrue(jsonBytes(j) > WorldContract.BLOB_MAX_BYTES)
    }
}
