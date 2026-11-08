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
 * The developer parkour demo's wire and screen state (`pet.demo.v1`): the page sends `demo` {label,
 * hint} only to a screen that said the capability and only while its developer mode is on, read
 * strictly (an absent field is a hidden button, an older page behaves as before); the screen's tap
 * goes to the cat world and never to the page, shows the brain's one line when no cat can, and is
 * repeat-safe.
 */
class PetDemoTest {
    private fun decode(name: String) =
        WebPetRepository().apply { accept(PetFixtures.projection(name), true) }.state.value!!

    @Test
    fun theButtonComesInThePagesWordsAndAnOlderPageHasNone() {
        val demo = decode("demo").demo!!
        assertEquals("Demo parkour", demo.label)
        assertTrue(demo.hint.startsWith("Developer demo"))
        // developer mode off, or a page of before the capability: no field, no button
        assertNull(decode("play").demo)
        assertNull(decode("touched").demo)
        assertNull(decode("alive").demo)
    }

    private class Break(val what: String, val change: JSONObject.() -> Unit)

    private val breaks =
        listOf(
            Break("an unknown key") { getJSONObject("demo").put("both", true) },
            Break("a missing key") { getJSONObject("demo").remove("hint") },
            Break("a number for the label") { getJSONObject("demo").put("label", 3) },
            Break("a blank label") { getJSONObject("demo").put("label", " ") },
            Break("a blank hint") { getJSONObject("demo").put("hint", "") },
            Break("a very long label") { getJSONObject("demo").put("label", "x".repeat(500)) },
            Break("a boolean instead of the object") { put("demo", true) },
            Break("null instead of the object") { put("demo", JSONObject.NULL) },
        )

    @Test
    fun everyMalformedButtonIsRefusedAndTheLastGoodStateKept() {
        for (b in breaks) {
            val repo = WebPetRepository().apply { accept(PetFixtures.projection("demo"), true) }
            val bad = PetFixtures.projection("demo").put("sequence", 2).apply(b.change)
            assertTrue("accepted: ${b.what}", runCatching { repo.accept(bad) }.isFailure)
            assertEquals("kept: ${b.what}", 1L, repo.state.value!!.sequence)
            assertNotNull(repo.state.value!!.demo)
        }
    }

    private fun viewModel(shown: String, ask: () -> PlayAnswer?): PetViewModel {
        val repo = WebPetRepository().apply { accept(PetFixtures.projection(shown), true) }
        return PetViewModel(repo, NoPetCommands, { null }, ask) { "op" }.also { it.opened() }
    }

    @Test
    fun theTapGoesToTheCatWorldAndShowsTheBrainsLineWhenNoCatCan() {
        var asked = 0
        var answer: PlayAnswer? = PlayAnswer(0, "")
        val vm =
            viewModel("demo") {
                asked++
                answer
            }
        vm.demoPressed()
        assertEquals(1, asked)
        assertNull("a cat went: nothing to say", vm.notice)
        // repeat-safe: another tap asks again and the brain answers (it refuses a second tour)
        answer = PlayAnswer(-1, "A parkour demo is already running.")
        vm.demoPressed()
        assertEquals(2, asked)
        assertEquals("A parkour demo is already running.", vm.notice!!.text)
        answer = PlayAnswer(-1, "Ted is asleep.")
        vm.demoPressed()
        assertEquals("Ted is asleep.", vm.notice!!.text)
        // no shared room is running
        answer = null
        vm.demoPressed()
        assertTrue(vm.notice!!.text.startsWith("The cats are not ready"))
        assertNull("nothing went to the page", vm.waiting)
    }

    @Test
    fun withoutTheButtonATapDoesNothing() {
        var asked = 0
        val vm =
            viewModel("play") {
                asked++
                PlayAnswer(0, "")
            }
        vm.demoPressed()
        assertEquals("a hidden button cannot start a demo", 0, asked)
        assertFalse(vm.toyTray)
        assertNull(vm.notice)
    }
}
