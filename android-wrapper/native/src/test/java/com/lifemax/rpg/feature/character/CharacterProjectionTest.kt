package com.lifemax.rpg.feature.character

import com.lifemax.rpg.feature.character.data.WebCharacterRepository
import com.lifemax.rpg.feature.character.presentation.CharacterViewModel
import org.junit.Assert.*
import org.junit.Test

class CharacterProjectionTest {
    @Test
    fun localSelectionNeverWritesTheRepository() {
        val repository = WebCharacterRepository()
        val vm = CharacterViewModel(repository)
        vm.select("physique")
        vm.compare(true)
        vm.sort("week")
        vm.skills()
        assertEquals("physique", vm.selection.value.selected)
        assertTrue(vm.selection.value.compare)
        assertEquals("week", vm.selection.value.sort)
        assertNull(repository.state.value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedSortIsRejected() {
        CharacterViewModel(WebCharacterRepository()).sort("xp-override")
    }
}
