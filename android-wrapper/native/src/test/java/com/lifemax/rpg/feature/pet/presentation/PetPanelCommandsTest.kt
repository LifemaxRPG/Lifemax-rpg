package com.lifemax.rpg.feature.pet.presentation

import com.lifemax.rpg.feature.pet.data.PetCommandSender
import com.lifemax.rpg.feature.pet.data.PetFixtures
import com.lifemax.rpg.feature.pet.data.WebPetRepository
import com.lifemax.rpg.feature.pet.data.toPetReply
import com.lifemax.rpg.feature.pet.domain.NoPetCommands
import com.lifemax.rpg.feature.pet.domain.PetReply
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Cat tab's panel commands (`pet.panels.v1`): the wire each one is sent in, that the view model
 * sends each one at a time and shows the page's answer, and that the replies the page gave (the
 * fixtures) decode as the screen's.
 */
class PetPanelCommandsTest {
    private val sent = ArrayList<Pair<String, JSONObject>>()
    private val sender = PetCommandSender { command, payload ->
        sent += command to payload
        "r${sent.size}"
    }
    private val repository = WebPetRepository()
    private val vm = PetViewModel(repository, sender)

    private fun open(name: String) {
        repository.accept(PetFixtures.projection("panels-$name"), true)
        vm.opened()
    }

    private fun answer(key: String, requestId: String): PetReply =
        PetFixtures.panelReply(key).put("requestId", requestId).toPetReply()!!

    @Test
    fun focusIsSentWithTheCatsIndex() {
        open("twoCats")
        vm.focus(1)
        assertEquals("focus", sent.single().first)
        assertEquals(1, sent.single().second.getInt("i"))
        assertEquals("focus", vm.waiting)
    }

    @Test
    fun buyToyIsSentWithTheToysId() {
        open("stray")
        vm.buyToy("feather")
        assertEquals("buyToy", sent.single().first)
        assertEquals("feather", sent.single().second.getString("id"))
    }

    @Test
    fun theGenomeTableOpensAndClosesWithABoolean() {
        open("geneticFemale")
        vm.expandGenome(true)
        assertEquals("expand", sent.single().first)
        assertTrue(sent.single().second.getBoolean("open"))
    }

    @Test
    fun oneCommandRunsAtATimeAndTheAnswerEndsTheWait() {
        open("twoCats")
        vm.focus(1)
        vm.buyToy("feather")
        vm.expandGenome(true)
        assertEquals(1, sent.size)
        vm.onReply(answer("focusSecond", "r1"))
        assertNull(vm.waiting)
        vm.buyToy("feather")
        assertEquals(2, sent.size)
    }

    @Test
    fun theFocusRepliesDecode() {
        val second = answer("focusSecond", "r1")
        assertTrue(second.ok)
        assertTrue(second.changed)
        val again = answer("focusAgain", "r1")
        assertTrue(again.ok)
        assertFalse(again.changed)
        assertEquals("Already recorded.", again.message)
        assertFalse(answer("focusNoSuchCat", "r1").ok)
    }

    @Test
    fun theToyShopsAnswersAreShownInTheShopsWords() {
        open("stray")
        vm.buyToy("spring")
        vm.onReply(answer("buyToyPoor", "r1"))
        assertEquals("Not enough Sparks", vm.notice!!.text)
        vm.noticeShown(vm.notice!!)
        vm.buyToy("feather")
        vm.onReply(answer("buyToy", "r2"))
        assertEquals("Toy unlocked for your cat", vm.notice!!.text)
    }

    @Test
    fun aToyThatIsNotForSaleIsRefused() {
        val reply = answer("buyToyNotForSale", "r1")
        assertFalse(reply.ok)
        assertTrue(reply.message.isNotEmpty())
    }

    @Test
    fun theGenomeAndMalformedExpandRepliesDecode() {
        assertTrue(answer("expandOpen", "r1").ok)
        assertFalse(answer("expandBad", "r1").ok)
    }

    @Test
    fun theRepliesOfThePanelCommandsAreReadByTheReplyDecoder() {
        for (key in listOf("focusSecond", "buyToy", "expandOpen")) {
            assertNotNull(key, PetFixtures.panelReply(key).put("requestId", "x").toPetReply())
        }
    }

    @Test
    fun aSenderThatCannotSendDoesNotWait() {
        val silent = PetViewModel(repository, NoPetCommands)
        repository.accept(PetFixtures.projection("panels-twoCats"), true)
        silent.opened()
        silent.focus(1)
        silent.buyToy("feather")
        silent.expandGenome(true)
        assertNull(silent.waiting)
    }
}
