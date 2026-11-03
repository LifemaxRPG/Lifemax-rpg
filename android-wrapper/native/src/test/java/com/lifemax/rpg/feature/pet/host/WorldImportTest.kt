package com.lifemax.rpg.feature.pet.host

import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.World
import com.lifemax.rpg.feature.pet.world.BlobCat
import com.lifemax.rpg.feature.pet.world.LegacyPos
import com.lifemax.rpg.feature.pet.world.WorldBlobJson
import com.lifemax.rpg.feature.pet.world.WorldBlockJson
import com.lifemax.rpg.feature.pet.world.WorldFixtures
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The first open of the cats: the page's `legacy` block (three synthetic web brains, as the page's
 * own golden writes them) becomes the starting world, a cat without a brain starts from defaults, a
 * stored cat is kept untouched. Never real data.
 */
class WorldImportTest {
    private val day = "2026-10-30"

    private fun blockWith(legacy: String): com.lifemax.rpg.feature.pet.world.WorldBlock {
        val j: JSONObject = WorldFixtures.block("kitten")
        j.getJSONArray("cats").getJSONObject(0).put("legacy", WorldFixtures.legacy(legacy))
        return WorldBlockJson.decode(j)
    }

    private fun import(name: String): BlobCat {
        val b = blockWith(name)
        return WorldImport.build(b.cats[0], day, 12.0, WorldFixtures.NOW)
    }

    @Test
    fun aRichWebBrainCarriesItsCharacterOver() {
        val b = blockWith("rich")
        val c = WorldImport.build(b.cats[0], day, 12.0, WorldFixtures.NOW)
        val old = b.cats[0].legacy!!
        assertEquals(old.mood, c.mood)
        assertEquals(old.trust, c.trust)
        assertEquals(old.spots, c.spots)
        assertEquals(old.innate, c.innate)
        assertEquals(old.counters, c.counters)
        assertEquals(old.rhythm, c.rhythm)
        assertEquals(old.fx, c.fx)
        assertEquals(old.voice, c.voice, 0.0)
        assertEquals(old.energy, c.drives.en, 0.0)
        assertTrue("the glass visits raise the wish for the owner", c.drives.so > 0.3)
        // the saved place (x -29, depth 44, three pixels up) is the floor, not a surface
        assertEquals("floor", c.pos.s)
        assertEquals(-29.0, c.pos.x, 0.0)
        assertEquals(44.0, c.pos.z, 0.0)
    }

    @Test
    fun theThreeSyntheticBrainsAllMakeAValidWorld() {
        for (name in listOf("rich", "damaged", "v77", "fresh")) {
            val b = blockWith(name)
            val blob = WorldImport.merge(b, null, emptySet(), day, 12.0, WorldFixtures.NOW)
            // what native would write decodes strictly: the import is a legal blob
            val back = WorldBlobJson.decode(WorldBlobJson.encode(blob))
            assertEquals(name, blob.cats, back.cats)
        }
    }

    @Test
    fun theStartIsMarkedByWhereItCameFrom() {
        val rich = WorldImport.merge(blockWith("rich"), null, emptySet(), day, 12.0, 0L)
        assertEquals("legacy1", rich.imported)
        val fresh = WorldImport.merge(blockWith("fresh"), null, emptySet(), day, 12.0, 0L)
        assertEquals("fresh", fresh.imported)
        // the web brain's loose toys lie about the house
        assertEquals(listOf("ball_a", "mouse"), rich.toys.map { it.id })
    }

    @Test
    fun aStoredCatIsKeptAndANewOneImported() {
        val b = WorldBlockJson.decode(WorldFixtures.block("twoCats"))
        val stored =
            WorldFixtures.blob()
                .copy(cats = mapOf(b.cats[0].id to WorldFixtures.cat(mapOf("tree" to 90))))
        val merged = WorldImport.merge(b, stored, emptySet(), day, 12.0, 5L)
        assertEquals(mapOf("tree" to 90), merged.cats[b.cats[0].id]!!.spots)
        assertNotEquals(stored.cats.values.first(), merged.cats[b.cats[1].id])
        assertEquals("legacy1", merged.imported)
        // a cat whose entry the page could not read is derived afresh
        val again = WorldImport.merge(b, stored, setOf(b.cats[0].id), day, 12.0, 5L)
        assertNotEquals(mapOf("tree" to 90), again.cats[b.cats[0].id]!!.spots)
    }

    @Test
    fun theStreamsAreSeededFromTheCatAndTheDay() {
        val c = import("rich")
        val again = import("rich")
        assertEquals(c.rng, again.rng)
        assertEquals(3, c.rng.toSet().size)
        assertNotEquals(
            c.rng,
            WorldImport.build(blockWith("rich").cats[0], "2026-10-31", 12.0, 0L).rng,
        )
    }

    @Test
    fun aCatWithNoSavedPlaceSleepsOnTheBedAtNightAndStandsByTheBowlByDay() {
        assertEquals("bed", WorldImport.scheduled(0, 23.5).s)
        assertEquals("bed", WorldImport.scheduled(1, 3.0).s)
        assertEquals("floor", WorldImport.scheduled(0, 12.0).s)
        assertEquals(HostTuning.BOWL_X_SECOND, WorldImport.scheduled(1, 12.0).x, 0.0)
    }

    @Test
    fun aSavedElevationFindsTheSurfaceItWasOn() {
        val z = World.ART_Z
        val onCouch = World.floorY(z) - World.surface(SurfaceId.COUCH).y
        assertEquals(
            "couch",
            WorldImport.position(LegacyPos(-100, z.toDouble(), onCouch.toInt())).s,
        )
        val onTree = World.floorY(z) - World.surface(SurfaceId.TOP).y
        assertEquals("top", WorldImport.position(LegacyPos(154, z.toDouble(), onTree.toInt())).s)
        // too high for anything under it, or off every surface: the floor
        assertEquals("floor", WorldImport.position(LegacyPos(100, z.toDouble(), 300)).s)
        assertEquals("floor", WorldImport.position(LegacyPos(100, z.toDouble(), 0)).s)
        // out of the house is brought in
        assertTrue(WorldImport.position(LegacyPos(9999, 50.0, 0)).x <= World.HOUSE_MAX_X)
    }
}
