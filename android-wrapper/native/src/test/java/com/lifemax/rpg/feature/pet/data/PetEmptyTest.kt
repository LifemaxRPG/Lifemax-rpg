package com.lifemax.rpg.feature.pet.data

import com.lifemax.rpg.feature.pet.domain.CatteryDetail
import com.lifemax.rpg.feature.pet.domain.CatteryScreen
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Cat tab with no cat at home (`pet.empty.v1`, work package C4), as the page projects it
 * (tools/gen_pet_cattery_fixtures.js): the first cat's adoption or the memorial in place of the
 * room, with no cat in the projection. Decoded strictly: anything the page might get wrong is
 * refused and the last good state is kept.
 */
class PetEmptyTest {
    private fun decode(name: String) =
        WebPetRepository().apply { accept(PetFixtures.projection(name), true) }.state.value!!

    private fun refused(change: (JSONObject) -> Unit): Boolean {
        val repo = WebPetRepository()
        repo.accept(PetFixtures.projection("alive"), true)
        val good = repo.state.value
        val j = PetFixtures.projection("empty-adopt").put("sequence", 2)
        change(j)
        val threw = runCatching { repo.accept(j) }.isFailure
        return threw && repo.state.value === good
    }

    @Test
    fun noCatAtAllIsTheFirstCatsAdoption() {
        val p = decode("empty-adopt")
        assertEquals(CatteryScreen.ADOPT, p.empty)
        assertTrue(p.cats.isEmpty())
        val c = p.cattery!!
        assertEquals(CatteryScreen.ADOPT, c.view)
        val d = c.detail as CatteryDetail.Adopt
        assertEquals("first", d.mode)
        assertEquals(4, d.kits.size)
        assertEquals("Welcome home", c.words.adopt)
    }

    @Test
    fun aCatThatHasPassedIsTheMemorialWithTheDoor() {
        val p = decode("empty-memorial")
        assertEquals(CatteryScreen.MEMORIAL, p.empty)
        val d = p.cattery!!.detail as CatteryDetail.Memorial
        assertEquals(1, d.entries.size)
        assertTrue(d.canReopen)
        assertEquals("Open the door again", p.cattery!!.words.reopen)
    }

    @Test
    fun aLivingCatsProjectionHasNoEmptyScreen() {
        val p = WebPetRepository().apply { accept(PetFixtures.projection("alive"), true) }
        assertNull(p.state.value!!.empty)
    }

    @Test
    fun theCatCardsFieldsAreBlankAndTheThemeIsKept() {
        val p = decode("empty-adopt")
        assertEquals("", p.name)
        assertNotNull(p.tokens["bg"])
        assertNull(p.growth)
        assertNull(p.touch)
        assertNull(p.panels)
    }

    @Test
    fun malformedEmptyProjectionsAreRefusedAndTheLastGoodStateKept() {
        assertTrue("an unknown key", refused { it.put("cats", JSONArray()) })
        assertTrue(
            "the lab is not a home screen",
            refused { it.getJSONObject("empty").put("screen", "lab") },
        )
        assertTrue("an unknown screen", refused { it.getJSONObject("empty").put("screen", "door") })
        assertTrue("an extra key in empty", refused { it.getJSONObject("empty").put("x", 1) })
        assertTrue("no cattery", refused { it.remove("cattery") })
        assertTrue("no tokens", refused { it.remove("tokens") })
        assertTrue("empty is not an object", refused { it.put("empty", "adopt") })
        assertTrue(
            "a screen of the wrong kind",
            refused { it.getJSONObject("empty").put("screen", 1) },
        )
    }
}
