package com.lifemax.rpg.feature.pet.data

import com.lifemax.rpg.feature.pet.brain.PlayAnswer
import com.lifemax.rpg.feature.pet.domain.NoPetCommands
import com.lifemax.rpg.feature.pet.presentation.PetViewModel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Native Play's wire and screen state (`pet.play.v1`): the page's toy tray as it makes it (only the
 * toys the cat may play with, in the page's words), read strictly; an older page sends none and
 * Play still goes to the page; the screen's Play opens its own tray, a toy picked is tossed to the
 * cats, and the brain's honest line shows when no cat comes; a cat too hungry is the page's words.
 */
class PetPlayTest {
    private fun decode(name: String) =
        WebPetRepository().apply { accept(PetFixtures.projection(name), true) }.state.value!!

    @Test
    fun aScreenWithPlayGetsTheTrayInThePagesWords() {
        val tray = decode("play").play!!
        assertEquals("Let Miso pick", tray.pick)
        assertEquals("", tray.tired)
        assertTrue(tray.toys.isNotEmpty())
        assertTrue(tray.toys.all { it.name.isNotBlank() && it.word.isNotBlank() })
        assertEquals("Miso is too tired to play. A meal first.", decode("play-tired").play!!.tired)
    }

    @Test
    fun anOlderPageSendsNoTrayAndPlayStaysThePages() {
        assertNull(decode("touched").play)
        val repo = WebPetRepository().apply { accept(PetFixtures.projection("touched"), true) }
        val vm = PetViewModel(repo, NoPetCommands) { "op" }
        vm.opened()
        assertFalse("the page's own tray", vm.playPressed())
    }

    private class Break(val what: String, val change: JSONObject.() -> Unit)

    private fun JSONObject.tray() = getJSONObject("play")

    private val breaks =
        listOf(
            Break("an unknown key") { tray().put("price", 3) },
            Break("a missing key") { tray().remove("tired") },
            Break("a toy the page does not have") {
                tray().getJSONArray("toys").getJSONObject(0).put("id", "laser")
            },
            Break("a toy twice") {
                val toys = tray().getJSONArray("toys")
                toys.put(JSONObject(toys.getJSONObject(0).toString()))
            },
            Break("a toy with more to it") {
                tray().getJSONArray("toys").getJSONObject(0).put("price", 1)
            },
            Break("a nameless toy") {
                tray().getJSONArray("toys").getJSONObject(0).put("name", " ")
            },
            Break("a number for words") { tray().put("title", 3) },
        )

    @Test
    fun everyMalformedTrayIsRefusedAndTheLastGoodStateKept() {
        for (b in breaks) {
            val repo = WebPetRepository().apply { accept(PetFixtures.projection("play"), true) }
            val bad = PetFixtures.projection("play").put("sequence", 2).apply(b.change)
            assertTrue("accepted: ${b.what}", runCatching { repo.accept(bad) }.isFailure)
            assertEquals("kept: ${b.what}", 1L, repo.state.value!!.sequence)
            assertNotNull(repo.state.value!!.play)
        }
    }

    @Test
    fun playOpensTheScreensTrayAndAToyPickedIsTossedToTheCats() {
        val offered = ArrayList<String>()
        var answer: PlayAnswer? = PlayAnswer(0, "")
        val repo = WebPetRepository().apply { accept(PetFixtures.projection("play"), true) }
        val vm =
            PetViewModel(
                repo,
                NoPetCommands,
                {
                    offered += it
                    answer
                },
            ) {
                "op"
            }
        vm.opened()
        assertTrue(vm.playPressed())
        assertTrue(vm.toyTray)
        vm.playToy("mouse")
        assertFalse("the tray closes", vm.toyTray)
        assertEquals(listOf("mouse"), offered)
        assertNull("a cat came: nothing to say", vm.notice)
        // "let the cat pick": its favourite, else one it loves, else the first
        vm.playToy(null)
        assertEquals("ball_a", offered.last())
        // a toy the tray does not hold is the cat's pick, never sent as is
        vm.playToy("laser")
        assertEquals("ball_a", offered.last())
        // no cat comes: the brain's line shows
        answer = PlayAnswer(-1, "Ted is fast asleep and does not stir.")
        vm.playToy("yarn")
        assertEquals("Ted is fast asleep and does not stir.", vm.notice!!.text)
        // no shared room runs yet
        answer = null
        vm.playToy("yarn")
        assertTrue(vm.notice!!.text.startsWith("The cats are not ready"))
    }

    @Test
    fun aCatTooHungryToPlayIsThePagesRefusalAndNoToyIsTossed() {
        val offered = ArrayList<String>()
        val repo = WebPetRepository().apply { accept(PetFixtures.projection("play-tired"), true) }
        val vm =
            PetViewModel(
                repo,
                NoPetCommands,
                {
                    offered += it
                    PlayAnswer(0, "")
                },
            ) {
                "op"
            }
        vm.opened()
        assertTrue(vm.playPressed())
        assertFalse(vm.toyTray)
        assertEquals("Miso is too tired to play. A meal first.", vm.notice!!.text)
        vm.playToy("mouse")
        assertTrue(offered.isEmpty())
    }
}
