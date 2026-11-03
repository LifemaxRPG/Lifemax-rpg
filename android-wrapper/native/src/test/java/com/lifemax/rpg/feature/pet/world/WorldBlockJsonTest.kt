package com.lifemax.rpg.feature.pet.world

import com.lifemax.rpg.feature.pet.data.PetFixtures
import com.lifemax.rpg.feature.pet.data.WebPetRepository
import com.lifemax.rpg.feature.pet.world.WorldFixtures.LILY
import com.lifemax.rpg.feature.pet.world.WorldFixtures.TED
import com.lifemax.rpg.feature.pet.world.WorldFixtures.at
import com.lifemax.rpg.feature.pet.world.WorldFixtures.obj
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The page's `world` block read strictly: every block the real page made decodes to its values; 40
 * malformed variants of one are each refused, and the feed keeps the last good block through them;
 * seq and epoch order; a stored blob read per cat.
 */
class WorldBlockJsonTest {
    private val decode: (JSONObject) -> Unit = { WorldBlockJson.decode(it) }

    @Test
    fun everyBlockThePageMadeDecodes() {
        for (name in WorldFixtures.blockNames) {
            val b = WorldBlockJson.decode(WorldFixtures.block(name))
            assertEquals(name, 1, b.v)
            assertTrue(name, b.cats.isNotEmpty() && b.cats.all { it.legacy != null })
        }
    }

    @Test
    fun theBondedCatReadsAsThePageKeepsIt() {
        val ted = WorldBlockJson.decode(WorldFixtures.block("bonded")).cats.single()
        assertEquals(TED, ted.id)
        assertEquals(WorldContract.catId(ted.born), ted.id)
        assertEquals("Ted", ted.name)
        assertEquals(CatBond(5, 100), ted.bond)
        assertEquals(100, ted.zMax)
        assertTrue(ted.care.giftOk)
        assertFalse(ted.care.canPlay)
        assertEquals(mapOf("bed" to 100, "box" to 13, "couch" to 42), ted.likes.spots)
        assertEquals("feather", ted.likes.favToy)
        assertEquals(listOf("lap_barnacle"), ted.acquired)
        val l = ted.legacy!!
        assertEquals("brain", l.src)
        assertEquals(Trust(0.8, 0.74, 0.6, 0.55), l.trust)
        assertEquals(listOf("first_blink", "first_glass"), l.fx)
        assertEquals(listOf("dramatic_flopper", "chirp_greeter"), l.innate)
        assertEquals(LegacyPos(126, 61.5, 0), l.pos)
        assertEquals(24, l.rhythm.size)
    }

    @Test
    fun theTwoCatsAndTheirPair() {
        val b = WorldBlockJson.decode(WorldFixtures.block("twoCats"))
        assertEquals(listOf(TED, LILY), b.cats.map { it.id })
        assertEquals(listOf(0, 1), b.cats.map { it.slot })
        assertEquals(WorldPair(0.62, 3, 0, true, 0.11), b.pair)
        assertEquals(4, b.cats[1].stage)
    }

    @Test
    fun vacationAnnouncesNoNextStage() {
        val h = WorldBlockJson.decode(WorldFixtures.block("vacation")).cats.single().hunger
        assertTrue(h.vacation)
        assertNull(h.nextStageAt)
    }

    @Test
    fun malformedBlocksAreRefused() {
        val base = WorldFixtures.block("twoCats")
        val cat = "cats.0"
        val v =
            linkedMapOf<String, (JSONObject) -> Unit>(
                "unknown top key" to { it.put("mind", 1) },
                "missing seq" to { it.remove("seq") },
                "missing stored" to { it.remove("stored") },
                "version 2" to { it.put("v", 2) },
                "seq zero" to { it.put("seq", 0) },
                "seq a string" to { it.put("seq", "5") },
                "seq a fraction" to { it.put("seq", 1.5) },
                "epoch negative" to { it.put("epoch", -1) },
                "clock shift of years" to { it.put("clockOffsetMs", 9e12) },
                "three cats" to { it.getJSONArray("cats").put(obj(it, cat)) },
                "the same cat twice" to { it.getJSONArray("cats").put(1, obj(it, cat)) },
                "cats out of slot order" to
                    {
                        obj(it, cat).put("slot", 1)
                        obj(it, "cats.1").put("slot", 0)
                    },
                "a cat is a string" to { it.getJSONArray("cats").put(0, "Ted") },
                "unknown cat key" to { obj(it, cat).put("mood", "happy") },
                "id not from its birth" to { obj(it, cat).put("id", "c1") },
                "id out of form" to { obj(it, cat).put("id", "Ted!") },
                "slot 2" to { obj(it, cat).put("slot", 2) },
                "blank name" to { obj(it, cat).put("name", "  ") },
                "name too long" to { obj(it, cat).put("name", "x".repeat(41)) },
                "name a number" to { obj(it, cat).put("name", 7) },
                "sex x" to { obj(it, cat).put("sex", "x") },
                "coat out of form" to { obj(it, cat).put("coat", "Ginger Tabby") },
                "stage 6" to { obj(it, cat).put("stage", 6) },
                "grown before born" to { obj(it, cat).put("grownAt", 5) },
                "persona missing a score" to { obj(it, "$cat.ps").remove("vo") },
                "persona over 1" to { obj(it, "$cat.ps").put("sk", 1.2) },
                "persona as words" to { obj(it, "$cat.ps").put("sk", "NaN") },
                "morph absurd" to { obj(it, "$cat.morph").put("lg", 9) },
                "bond level 6" to { obj(it, "$cat.bond").put("lvl", 6) },
                "bond pct 101" to { obj(it, "$cat.bond").put("pct", 101) },
                "zMax 120" to { obj(it, cat).put("zMax", 120) },
                "hunger stage 6" to { obj(it, "$cat.hunger").put("stage", 6) },
                "vacation not a boolean" to { obj(it, "$cat.hunger").put("vacation", 1) },
                "next stage missing" to { obj(it, "$cat.hunger").remove("nextStageAt") },
                "a counter negative" to { obj(it, "$cat.care").put("petsToday", -1) },
                "giftOk a string" to { obj(it, "$cat.care").put("giftOk", "true") },
                "a place the page does not know" to { obj(it, "$cat.likes.spots").put("roof", 50) },
                "a liking over 100" to { obj(it, "$cat.likes.toys").put("mouse", 150) },
                "a favourite toy unknown" to { obj(it, "$cat.likes").put("favToy", "laser") },
                "a toy twice" to { (at(it, "$cat.toys") as JSONArray).put("mouse") },
                "an innate quirk as acquired" to
                    {
                        (at(it, "$cat.quirks.acq") as JSONArray).put("chirp_greeter")
                    },
                "legacy unknown key" to { obj(it, "$cat.legacy").put("ep", JSONArray()) },
                "legacy rhythm 23 hours" to
                    {
                        (at(it, "$cat.legacy.rhythm") as JSONArray).remove(0)
                    },
                "legacy trust over 1" to { obj(it, "$cat.legacy.trust").put("tru", 1.5) },
                "legacy src unknown" to { obj(it, "$cat.legacy").put("src", "cloud") },
                "legacy toy out of the house" to
                    {
                        (at(it, "$cat.legacy.toys") as JSONArray).put(
                            JSONObject().put("id", "mouse").put("x", 900).put("z", 1)
                        )
                    },
                "pair with an unknown key" to { obj(it, "pair").put("n", 3) },
                "pair rel over 1" to { obj(it, "pair").put("rel", 1.01) },
                "pair lead 2" to { obj(it, "pair").put("lead", 2) },
                "pair in a house of one" to { it.getJSONArray("cats").remove(1) },
                "context timer missing" to { obj(it, "context").remove("timer") },
                "cue with a mind stream" to { obj(it, "cue").put("mind", JSONArray()) },
                "fed cue out of order" to
                    {
                        obj(it, "cue").put("fed", JSONArray().put(fed(0, 5)).put(fed(1, 4)))
                    },
                "five fed cues" to
                    {
                        obj(it, "cue").put("fed", JSONArray((1..5).map { n -> fed(0, n.toLong()) }))
                    },
                "a cheer of an unknown kind" to
                    {
                        obj(it, "cue")
                            .put(
                                "cheer",
                                JSONArray()
                                    .put(
                                        JSONObject()
                                            .put("id", 1)
                                            .put("kind", "streak")
                                            .put("ago", 0)
                                    ),
                            )
                    },
                "stored without a revision" to
                    {
                        it.put("stored", JSONObject().put("epoch", 0).put("blob", JSONObject()))
                    },
                "oversize block" to
                    {
                        obj(it, "$cat.likes")
                            .put("spots", JSONObject((1..2000).associate { n -> "s$n" to 1 }))
                    },
            )
        val codes = WorldFixtures.assertAllRefused(base, v, decode)
        assertTrue(codes.size >= 30)
        assertEquals("version", codes["version 2"])
        assertEquals("type", codes["seq a string"])
        assertEquals("range", codes["stage 6"])
        assertEquals("size", codes["oversize block"])
    }

    @Test
    fun theFeedKeepsTheLastGoodBlockAndCountsTheRest() {
        val feed = WorldFeed()
        val good = WorldFixtures.block("bonded")
        assertTrue(feed.offer(good))
        val held = feed.latest
        val bad = WorldFixtures.copy(good).put("seq", good.getLong("seq") + 1).put("mind", 1)
        assertFalse(feed.offer(bad))
        assertFalse(feed.offer(WorldFixtures.copy(good).put("seq", "x")))
        assertEquals(held, feed.latest)
        assertEquals(mapOf("shape" to 1, "type" to 1), feed.errors)
        assertFalse("the same seq again is no news", feed.offer(WorldFixtures.copy(good)))
        assertFalse("an older seq is ignored", feed.offer(WorldFixtures.copy(good).put("seq", 2)))
        assertTrue(feed.offer(WorldFixtures.copy(good).put("seq", good.getLong("seq") + 1)))
        val restored = good.getLong("seq") + 2
        assertFalse(
            "a block of a new epoch still comes in seq order (a late one from before is old news)",
            feed.offer(WorldFixtures.copy(good).put("seq", 3).put("epoch", 1)),
        )
        assertTrue(
            "the page's block after a restore: a new epoch, newer",
            feed.offer(WorldFixtures.copy(good).put("seq", restored).put("epoch", 1)),
        )
        assertFalse(
            "a late block from before the restore does not bring the old epoch back",
            feed.offer(WorldFixtures.copy(good).put("seq", restored - 1)),
        )
        assertFalse(
            "a projection without a block changes nothing",
            feed.offerFrom(JSONObject().put("protocol", 1)),
        )
        assertEquals(1L, feed.latest!!.epoch)
    }

    @Test
    fun aStoredWorldIsReadCatByCat() {
        val base = WorldFixtures.block("twoCats")
        val blob = WorldBlobJson.encode(WorldFixtures.blob())
        val ok = WorldBlockJson.decode(WorldFixtures.copy(base).put("stored", stored(blob)))
        assertEquals(WorldFixtures.blob(), ok.stored!!.blob)
        assertTrue(ok.stored!!.rejectedCats.isEmpty())
        blob.getJSONObject("cats").getJSONObject(LILY).put("voice", 7)
        val partly = WorldBlockJson.decode(WorldFixtures.copy(base).put("stored", stored(blob)))
        assertEquals(setOf(LILY), partly.stored!!.rejectedCats)
        assertEquals(setOf(TED), partly.stored!!.blob!!.cats.keys)
        val newer =
            WorldBlockJson.decode(WorldFixtures.copy(base).put("stored", stored(blob.put("v", 2))))
        assertNotNull("the block stands; the stored world is unreadable", newer.stored)
        assertNull(newer.stored!!.blob)
    }

    @Test
    fun theBlockSizeLeavesTheStoredBlobOut() {
        val blob =
            WorldBlobJson.encode(WorldFixtures.blob())
                .put(
                    "away",
                    JSONObject().put("from", 1).put("lines", JSONArray(List(3) { "y".repeat(160) })),
                )
        val b = WorldFixtures.copy(WorldFixtures.block("twoCats")).put("stored", stored(blob))
        assertTrue(jsonBytes(b) - jsonBytes(blob) <= WorldContract.BLOCK_MAX_BYTES)
        assertNotNull(WorldBlockJson.decode(b).stored!!.blob)
    }

    @Test
    fun theCatTabsCardDrawsAsItDidWithTheBlockBesideIt() {
        val plain =
            WebPetRepository()
                .apply { accept(PetFixtures.projection("two-cats"), true) }
                .state
                .value
        val withWorld =
            PetFixtures.projection("two-cats")
                .put("world", WorldFixtures.block("twoCats").put("mind", 1))
        val beside = WebPetRepository().apply { accept(withWorld, true) }.state.value
        assertEquals("even a malformed block leaves the card as it was", plain, beside)
    }

    private fun stored(blob: JSONObject) =
        JSONObject().put("epoch", 0).put("rev", 3).put("blob", blob)

    private fun fed(cat: Int, id: Long) = JSONObject().put("cat", cat).put("id", id).put("ago", 0)
}
