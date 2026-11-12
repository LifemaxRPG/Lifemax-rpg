package com.lifemax.rpg.feature.pet.presentation

import com.lifemax.rpg.feature.pet.data.PetFixtures
import com.lifemax.rpg.feature.pet.data.WebPetRepository
import com.lifemax.rpg.feature.pet.domain.NoPetCommands
import com.lifemax.rpg.feature.pet.domain.PetCommands
import com.lifemax.rpg.feature.pet.domain.PetProjection
import com.lifemax.rpg.feature.pet.room.CameraDirector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * One source of truth for the selected cat: the page's focus. The camera frames `focus`, a tap on a
 * cat in the room asks the page for the same `focus` command the switcher's pills send (and only
 * when there is something to switch), and the view model never keeps a selection of its own.
 */
class CatSelectionTest {
    private fun decode(name: String): PetProjection =
        WebPetRepository().apply { accept(PetFixtures.projection(name), true) }.state.value!!

    private fun panels(name: String) = decode("panels-$name")

    @Test
    fun theCameraFramesTheCatTheCardShows() {
        assertEquals(0, CatSelection.cameraCat(panels("twoCats")))
        val second = panels("twoCatsSecond")
        assertEquals(1, second.focus)
        assertEquals(second.focus, CatSelection.cameraCat(second))
    }

    @Test
    fun withOneCatTheCameraKeepsItsOwnRule() {
        assertEquals(CameraDirector.NONE, CatSelection.cameraCat(decode("alive")))
        assertEquals(CameraDirector.NONE, CatSelection.cameraCat(panels("bonded")))
    }

    @Test
    fun aTapOnTheOtherCatAsksForItsFocusAndATapOnTheFocusedCatAsksForNothing() {
        val first = panels("twoCats")
        assertEquals(1, CatSelection.tapped(first, 1))
        assertNull(CatSelection.tapped(first, 0))
        val second = panels("twoCatsSecond")
        assertEquals(0, CatSelection.tapped(second, 0))
        assertNull(CatSelection.tapped(second, 1))
    }

    @Test
    fun aTapOnNoCatOrWithNoSwitcherAsksForNothing() {
        val first = panels("twoCats")
        assertNull(CatSelection.tapped(first, 2))
        assertNull(CatSelection.tapped(first, -1))
        // two cats in the room but a page with no pair panel (an older page): no switcher
        assertNull(CatSelection.tapped(decode("two-cats"), 1))
        assertNull(CatSelection.tapped(decode("alive"), 0))
    }

    @Test
    fun theTapSendsThePagesFocusCommandAndTheSelectionFollowsTheProjection() {
        val repository = WebPetRepository()
        val asked = ArrayList<Int>()
        val commands =
            object : PetCommands by NoPetCommands {
                override fun focus(index: Int): String {
                    asked += index
                    return "r${asked.size}"
                }
            }
        val vm = PetViewModel(repository, commands) { "op" }
        repository.accept(PetFixtures.projection("panels-twoCats"), true)
        vm.opened()
        // a finger lands on the second cat: the same command as the pill
        CatSelection.tapped(vm.pet!!, 1)?.let(vm::focus)
        assertEquals(listOf(1), asked)
        // nothing changed yet: the card and the camera still say the first cat
        assertEquals(0, CatSelection.cameraCat(vm.pet!!))
        // the page answers with its new projection: the card and the camera move together
        repository.accept(PetFixtures.projection("panels-twoCatsSecond").put("sequence", 2))
        vm.projected()
        assertEquals(1, vm.pet!!.focus)
        assertEquals(1, CatSelection.cameraCat(vm.pet!!))
    }
}
