package com.lifemax.rpg.feature.pet.presentation

import com.lifemax.rpg.feature.pet.data.PetFixtures
import com.lifemax.rpg.feature.pet.data.WebPetRepository
import com.lifemax.rpg.feature.pet.data.toPetReply
import com.lifemax.rpg.feature.pet.domain.PetCommands
import com.lifemax.rpg.feature.pet.domain.PetReply
import com.lifemax.rpg.feature.pet.domain.PetStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the Cat tab's screen state does about touch: it never holds up Feed, each stroke goes to the
 * page with an operation id of its own, the page's word about a stroke comes back once as a message
 * and a level as a reaction, nothing is sent from a screen behind the page, and the full-screen
 * room is the screen's own.
 */
class PetTouchViewModelTest {
    private class Page : PetCommands {
        val asked = ArrayList<String>()
        val strokes = ArrayList<Pair<String, PetStroke>>()
        val purrs = ArrayList<Pair<Int, Float>>()

        private fun ask(what: String) = "r${asked.size + 1}".also { asked += what }

        override fun feed(op: String) = ask("feed:$op")

        override fun blink() = ask("blink")

        override fun vacation(on: Boolean) = ask("vacation:$on")

        override fun rename(name: String) = ask("rename:$name")

        override fun stroke(op: String, stroke: PetStroke) =
            ask("stroke:$op").also { strokes += op to stroke }

        override fun purr(cat: Int, level: Float) =
            ask("purr:$cat:$level").also { purrs += cat to level }
    }

    private val repository = WebPetRepository()
    private val page = Page()
    private var ids = 0
    private val vm = PetViewModel(repository, page) { "op-${++ids}" }

    private fun open(name: String = "touched") {
        repository.accept(PetFixtures.projection(name), true)
        vm.opened()
    }

    private val stroke = PetStroke(0, 1500, "", ignored = false, spam = false, keepSleep = false)

    private fun reply(key: String, requestId: String): PetReply =
        PetFixtures.reply(key).put("requestId", requestId).toPetReply()!!

    @Test
    fun aStrokeGoesToThePageWithAnOperationIdOfItsOwn() {
        open()
        vm.stroke(0, stroke)
        vm.stroke(0, stroke.copy(heldMs = 300))
        assertEquals(listOf("op-1", "op-2"), page.strokes.map { it.first })
        assertEquals(1500L, page.strokes[0].second.heldMs)
    }

    @Test
    fun touchNeverHoldsUpFeedOrTheOtherCommands() {
        open()
        vm.stroke(0, stroke)
        assertNull(vm.waiting)
        vm.feed()
        assertEquals("feed", vm.waiting)
        vm.stroke(0, stroke)
        vm.purr(0, 0.75f)
        assertEquals("feed", vm.waiting)
        vm.onReply(reply("strokePet", "r1"))
        assertEquals("a stroke's answer is not the feed's", "feed", vm.waiting)
        vm.onReply(reply("feed", "r2"))
        assertNull(vm.waiting)
    }

    @Test
    fun theWordsOfAStrokeShowOnceAndALevelIsCelebrated() {
        open()
        vm.stroke(0, stroke)
        vm.onReply(reply("strokeLevelUp", "r1"))
        assertTrue(vm.notice!!.text.contains("trusts you more now"))
        assertEquals(listOf(PetReaction.LevelUp(0, 4)), vm.drainReactions())
        vm.onReply(reply("strokeLevelUp", "r1")) // the same answer again is nobody's
        assertTrue(vm.drainReactions().isEmpty())
    }

    @Test
    fun aPlainPetSaysNothingAndAnUnsavedOneSaysWhy() {
        open()
        vm.stroke(0, stroke)
        vm.onReply(reply("strokePet", "r1"))
        assertNull(vm.notice)
        assertTrue(vm.drainReactions().isEmpty())
        vm.stroke(0, stroke)
        vm.onReply(reply("strokeUnsaved", "r2"))
        assertTrue(vm.notice!!.text.startsWith("Could not save"))
        assertTrue(vm.drainReactions().isEmpty())
    }

    @Test
    fun aScreenBehindThePageSendsNothing() {
        open()
        vm.hidden()
        vm.stroke(0, stroke)
        vm.purr(0, 0.5f)
        assertTrue(page.asked.isEmpty())
    }

    @Test
    fun aPageFarBehindHearsNoMoreThanADozenAtATime() {
        open()
        repeat(30) { vm.stroke(0, stroke) }
        assertEquals(12, page.strokes.size)
        vm.onReply(reply("strokePet", "r1"))
        vm.stroke(0, stroke)
        assertEquals(13, page.strokes.size)
    }

    @Test
    fun theSoundOfThePurrIsAskedOfThePage() {
        open()
        vm.purr(0, 0.75f)
        vm.purr(0, 0f)
        assertEquals(listOf(0 to 0.75f, 0 to 0f), page.purrs)
    }

    @Test
    fun theFullScreenRoomIsTheScreensOwnAndClosesWithTheScreen() {
        open()
        assertFalse(vm.fullRoom)
        vm.openRoom()
        assertTrue(vm.fullRoom)
        vm.closeRoom()
        assertFalse(vm.fullRoom)
        vm.openRoom()
        vm.hidden()
        assertFalse("the page took the view: the room closes", vm.fullRoom)
        vm.openRoom()
        assertFalse("a screen behind the page opens no room", vm.fullRoom)
        vm.opened()
        vm.openRoom()
        vm.opened() // the screen opens again: the room is not still open
        assertFalse(vm.fullRoom)
    }

    @Test
    fun strokesSentBeforeTheScreenWasHiddenAreNoLongerAwaited() {
        open()
        vm.stroke(0, stroke)
        vm.hidden()
        open()
        vm.onReply(reply("strokeLevelUp", "r1"))
        assertNull("nobody waits for it now", vm.notice)
        assertTrue(vm.drainReactions().isEmpty())
    }
}
