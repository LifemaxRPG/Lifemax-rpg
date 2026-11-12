package com.lifemax.rpg.feature.pet.presentation

import com.lifemax.rpg.feature.pet.data.PetCommandSender
import com.lifemax.rpg.feature.pet.data.PetFixtures
import com.lifemax.rpg.feature.pet.data.WebPetRepository
import com.lifemax.rpg.feature.pet.domain.CatteryCommand
import com.lifemax.rpg.feature.pet.domain.CatteryScreen
import com.lifemax.rpg.feature.pet.domain.PetReply
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The Cat tab with no cat at home (`pet.empty.v1`, work package C4): the screen the page names is
 * asked for once, not again after a refusal, and a first cat adopted or the door opened again
 * leaves the screen it came from.
 */
class PetEmptyViewModelTest {
    private val sent = ArrayList<Pair<String, JSONObject>>()
    private val sender = PetCommandSender { command, payload ->
        sent += command to payload
        "r${sent.size}"
    }
    private val repository = WebPetRepository()
    private var ids = 0
    private val vm = PetViewModel(repository, sender, newId = { "id-${++ids}" })

    private fun open(name: String) {
        repository.accept(PetFixtures.projection(name), true)
        vm.opened()
    }

    private fun reply(requestId: String, ok: Boolean = true, changed: Boolean = true) =
        PetReply("cattery", requestId, ok, changed, "", "", 0.0, "")

    @Test
    fun theScreenThePageNamesIsAskedForOnce() {
        open("empty-adopt")
        vm.openEmpty(CatteryScreen.ADOPT)
        assertEquals(CatteryScreen.ADOPT, vm.catteryScreen)
        assertEquals("view", sent.single().second.getString("kind"))
        assertEquals("adopt", sent.single().second.getString("view"))
        vm.openEmpty(CatteryScreen.ADOPT)
        assertEquals("asked once", 1, sent.size)
    }

    @Test
    fun aRefusedScreenIsNotAskedForInALoop() {
        open("empty-adopt")
        vm.openEmpty(CatteryScreen.ADOPT)
        vm.onReply(reply("r1", ok = false, changed = false))
        assertNull(vm.catteryScreen)
        vm.openEmpty(CatteryScreen.ADOPT)
        assertEquals("not again", 1, sent.size)
    }

    @Test
    fun aLivingCatsTabAsksForNothing() {
        repository.accept(PetFixtures.projection("alive"), true)
        vm.opened()
        vm.openEmpty(CatteryScreen.ADOPT)
        assertEquals(0, sent.size)
    }

    @Test
    fun aFirstCatAdoptedLeavesTheAdoptionAndClosesThePagesScreen() {
        open("empty-adopt")
        vm.openEmpty(CatteryScreen.ADOPT)
        vm.onReply(reply("r1"))
        vm.cattery(CatteryCommand.Adopt("first", 1, "Mango"))
        assertEquals("adopt", sent.last().second.getString("kind"))
        vm.onReply(reply("r2"))
        assertNull(vm.catteryScreen)
        // the page is told the screen is closed
        assertEquals("view", sent.last().second.getString("kind"))
        assertEquals("", sent.last().second.getString("view"))
    }

    @Test
    fun theDoorOpenedAgainTurnsTheMemorialIntoTheAdoption() {
        open("empty-memorial")
        vm.openEmpty(CatteryScreen.MEMORIAL)
        vm.onReply(reply("r1"))
        vm.cattery(CatteryCommand.Reopen)
        vm.onReply(reply("r2"))
        assertNull(vm.catteryScreen)
        // the next projection names the adoption, which is then asked for
        repository.accept(PetFixtures.projection("empty-adopt").put("sequence", 2))
        vm.projected()
        vm.openEmpty(CatteryScreen.ADOPT)
        assertEquals(CatteryScreen.ADOPT, vm.catteryScreen)
    }

    @Test
    fun reopeningTheTabAsksAgain() {
        open("empty-adopt")
        vm.openEmpty(CatteryScreen.ADOPT)
        vm.hidden()
        open("empty-adopt")
        vm.openEmpty(CatteryScreen.ADOPT)
        assertEquals(2, sent.size)
    }
}
