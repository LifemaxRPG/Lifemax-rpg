package com.lifemax.rpg.feature.character

import com.lifemax.rpg.feature.character.data.WebCharacterRepository
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CharacterRepositoryTest {
    private fun fixture(): JSONObject =
        JSONObject(javaClass.getResource("/character-projection.json")!!.readText())

    @Test
    fun oldUpdatesCannotReplaceNewSnapshots() {
        val repo = WebCharacterRepository()
        val first = fixture()
        repo.accept(first, true)
        repo.accept(fixture().put("sequence", 2).put("name", "Updated"))
        repo.accept(first)
        assertEquals("Updated", repo.state.value!!.name)
    }

    @Test
    fun foreignSessionRequiresAnExplicitOpen() {
        val repo = WebCharacterRepository()
        repo.accept(fixture(), true)
        val foreign = fixture().put("webSessionId", "other").put("name", "Foreign")
        repo.accept(foreign)
        assertEquals("Fixture", repo.state.value!!.name)
        repo.accept(foreign, true)
        assertEquals("Foreign", repo.state.value!!.name)
    }

    @Test
    fun malformedProjectionLeavesLastGoodStateIntact() {
        val repo = WebCharacterRepository()
        repo.accept(fixture(), true)
        try {
            repo.accept(fixture().put("sequence", 2).put("attributes", org.json.JSONArray()))
            fail("Malformed data accepted")
        } catch (_: IllegalArgumentException) {}
        assertEquals(1, repo.state.value!!.sequence.toInt())
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedProtocolRejected() {
        WebCharacterRepository().accept(fixture().put("protocol", 99), true)
    }
}
