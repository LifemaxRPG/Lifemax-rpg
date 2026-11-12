package com.lifemax.rpg.feature.pet.presentation

import com.lifemax.rpg.feature.pet.data.PetCommandSender
import com.lifemax.rpg.feature.pet.data.PetFixtures
import com.lifemax.rpg.feature.pet.data.WebPetRepository
import com.lifemax.rpg.feature.pet.data.toPetReply
import com.lifemax.rpg.feature.pet.domain.CatteryCommand
import com.lifemax.rpg.feature.pet.domain.CatteryScreen
import com.lifemax.rpg.feature.pet.domain.PetReply
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cattery's commands (`pet.cattery.v1`): the wire each one is sent in (exactly the keys the
 * page takes), that a command that changes things carries an operation id which a repeat of the
 * same tap reuses until the page answers, that a screen the page refuses closes, and that the
 * replies the page gave (the fixtures) decode as the screen's.
 */
class PetCatteryCommandsTest {
    private val sent = ArrayList<Pair<String, JSONObject>>()
    private val sender = PetCommandSender { command, payload ->
        sent += command to payload
        "r${sent.size}"
    }
    private val repository = WebPetRepository()
    private var ids = 0
    private val vm = PetViewModel(repository, sender, newId = { "id-${++ids}" })

    private fun open(name: String = "lab") {
        repository.accept(PetFixtures.cattery(name), true)
        vm.opened()
    }

    private fun answer(key: String, requestId: String): PetReply =
        PetFixtures.catteryReply(key).put("requestId", requestId).toPetReply()!!

    private fun last() = sent.last().second

    private fun keys(j: JSONObject) = j.keys().asSequence().sorted().joinToString(",")

    @Test
    fun openingAScreenAsksThePageForItsDetail() {
        open("entry")
        vm.openCattery(CatteryScreen.LAB)
        assertEquals("cattery", sent.single().first)
        assertEquals("view", last().getString("kind"))
        assertEquals("lab", last().getString("view"))
        assertEquals(CatteryScreen.LAB, vm.catteryScreen)
    }

    @Test
    fun backClosesTheScreenAndTellsThePage() {
        open("entry")
        vm.openCattery(CatteryScreen.LAB)
        vm.onReply(answer("view", "r1"))
        vm.closeCattery()
        assertNull(vm.catteryScreen)
        assertEquals("", last().getString("view"))
    }

    @Test
    fun nothingIsSentFromAPageThatSendsNoCattery() {
        repository.accept(PetFixtures.projection("alive"), true)
        vm.opened()
        vm.openCattery(CatteryScreen.LAB)
        assertTrue(sent.isEmpty())
        assertNull(vm.catteryScreen)
    }

    @Test
    fun eachCommandIsSentWithExactlyTheKeysThePageTakes() {
        open()
        vm.openCattery(CatteryScreen.LAB)
        val cases =
            listOf<Pair<CatteryCommand, String>>(
                CatteryCommand.Goal("lg-") to "kind,value",
                CatteryCommand.Pick("dad", "c1") to "key,kind,who",
                CatteryCommand.Breed("h0", "c1") to "dad,kind,mom,op",
                CatteryCommand.Keep(5L, 1) to "at,i,kind,op",
                CatteryCommand.Drop(5L, 0) to "at,i,kind,op",
                CatteryCommand.KeepAll(5L) to "at,kind,op",
                CatteryCommand.DropAll(5L) to "at,kind,op",
                CatteryCommand.Release("c1") to "id,kind,op",
                CatteryCommand.Rename("c1", "Bas") to "id,kind,name,op",
                CatteryCommand.Home("c2") to "id,kind,op",
                CatteryCommand.Stray to "kind,op",
                CatteryCommand.NewLitter to "kind",
                CatteryCommand.PickKit(2) to "i,kind",
                CatteryCommand.Adopt("pair", 1, "Nori") to "kind,kit,mode,name,op",
                CatteryCommand.Reopen to "kind,op",
            )
        for ((c, shape) in cases) {
            vm.onReply(answer("view", "r${sent.size}"))
            vm.cattery(c)
            assertEquals(c.toString(), shape, keys(last()))
        }
        val keep = sent.first { it.second.optString("kind") == "keep" }.second
        assertEquals(5L, keep.getLong("at"))
        assertEquals(1, keep.getInt("i"))
        val adopt = sent.first { it.second.optString("kind") == "adopt" }.second
        assertEquals("Nori", adopt.getString("name"))
    }

    @Test
    fun aRepeatedTapKeepsItsOperationIdUntilThePageAnswers() {
        open()
        vm.openCattery(CatteryScreen.LAB)
        vm.onReply(answer("view", "r1"))
        vm.cattery(CatteryCommand.Breed("h0", "c1"))
        val first = last().getString("op")
        vm.hidden() // the screen went away before the answer came
        vm.opened()
        vm.openCattery(CatteryScreen.LAB)
        vm.onReply(answer("view", "r3"))
        vm.cattery(CatteryCommand.Breed("h0", "c1"))
        assertEquals("the same tap, the same operation id", first, last().getString("op"))
        vm.onReply(answer("breed", "r4"))
        vm.cattery(CatteryCommand.Breed("h0", "c1"))
        assertNotEquals(
            "a new tap after the answer is a new operation",
            first,
            last().getString("op"),
        )
    }

    @Test
    fun differentCommandsHaveDifferentOperationIds() {
        open()
        vm.openCattery(CatteryScreen.LAB)
        vm.onReply(answer("view", "r1"))
        vm.cattery(CatteryCommand.Keep(5L, 0))
        val a = last().getString("op")
        vm.onReply(answer("keepStale", "r2"))
        vm.cattery(CatteryCommand.Keep(5L, 1))
        assertNotEquals(a, last().getString("op"))
    }

    @Test
    fun oneCommandRunsAtATimeAndTheAnswerShowsItsWords() {
        open()
        vm.openCattery(CatteryScreen.LAB)
        vm.onReply(answer("view", "r1"))
        vm.cattery(CatteryCommand.Breed("h0", "c1"))
        vm.cattery(CatteryCommand.Stray)
        assertEquals(2, sent.size)
        assertEquals("cattery", vm.waiting)
        vm.onReply(answer("breed", "r2"))
        assertNull(vm.waiting)
        assertTrue(vm.notice!!.text.endsWith("kittens"))
    }

    @Test
    fun aRefusalShowsThePagesWordsAndChangesNothingHere() {
        open()
        vm.openCattery(CatteryScreen.LAB)
        vm.onReply(answer("view", "r1"))
        vm.cattery(CatteryCommand.Keep(1L, 0))
        vm.onReply(answer("keepStale", "r2"))
        assertEquals("That litter has changed.", vm.notice!!.text)
        assertEquals(CatteryScreen.LAB, vm.catteryScreen)
    }

    @Test
    fun aScreenThePageRefusesToOpenCloses() {
        open("entry")
        vm.openCattery(CatteryScreen.ADOPT)
        vm.onReply(answer("keepStale", "r1")) // not ok
        assertNull(vm.catteryScreen)
    }

    @Test
    fun commandsAreIgnoredWithNoScreenOpen() {
        open("entry")
        vm.cattery(CatteryCommand.Stray)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun aHiddenScreenOpensNothing() {
        open("entry")
        vm.hidden()
        vm.openCattery(CatteryScreen.LAB)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun theRepliesThePageGaveDecode() {
        for (key in listOf("view", "breed", "breedAgain", "keepStale", "release", "renameBlank")) {
            val r = answer(key, "x")
            assertEquals("cattery", r.command)
        }
        assertTrue(answer("breed", "x").changed)
        assertEquals("Already recorded.", answer("breedAgain", "x").message)
        assertFalse(answer("renameBlank", "x").ok)
        assertNotNull(answer("release", "x"))
    }
}
