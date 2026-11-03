package com.lifemax.rpg.feature.pet.presentation

import com.lifemax.rpg.feature.pet.data.PetFixtures
import com.lifemax.rpg.feature.pet.data.WebPetRepository
import com.lifemax.rpg.feature.pet.data.toPetReply
import com.lifemax.rpg.feature.pet.domain.NoPetCommands
import com.lifemax.rpg.feature.pet.domain.PetCommands
import com.lifemax.rpg.feature.pet.domain.PetReply
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the Cat tab's screen state does with the page's projections and replies: a command waits for
 * its answer, a meal keeps its operation id until the answer is final, the page's message and each
 * meal the page served are shown once, and nothing plays while the screen is behind the page.
 */
class PetViewModelTest {
    /** The commands the page was asked, with request ids "r1", "r2", ... */
    private class Page : PetCommands {
        val asked = ArrayList<String>()

        private fun ask(what: String) = "r${asked.size + 1}".also { asked += what }

        override fun feed(op: String) = ask("feed:$op")

        override fun blink() = ask("blink")

        override fun vacation(on: Boolean) = ask("vacation:$on")

        override fun rename(name: String) = ask("rename:$name")
    }

    private val repository = WebPetRepository()
    private val page = Page()
    private var ids = 0
    private val vm = PetViewModel(repository, page) { "op-${++ids}" }

    private fun open(name: String = "alive") {
        repository.accept(PetFixtures.projection(name), true)
        vm.opened()
    }

    private fun update(name: String, sequence: Long) {
        repository.accept(PetFixtures.projection(name).put("sequence", sequence))
        vm.projected()
    }

    private fun reply(key: String, requestId: String): PetReply =
        PetFixtures.reply(key).put("requestId", requestId).toPetReply()!!

    @Test
    fun theScreenShowsWhatThePageProjected() {
        assertNull(vm.pet)
        open()
        assertEquals("Miso", vm.pet!!.name)
    }

    @Test
    fun aCommandWaitsForItsAnswerAndOnlyOneRunsAtATime() {
        open()
        vm.feed()
        assertEquals("feed", vm.waiting)
        vm.blink()
        vm.vacation(true)
        assertEquals(listOf("feed:op-1"), page.asked)
        vm.onReply(reply("feed", "r1"))
        assertNull(vm.waiting)
        vm.blink()
        assertEquals("blink", vm.waiting)
    }

    @Test
    fun anAnswerToAnotherRequestIsNotOurs() {
        open()
        vm.feed()
        vm.onReply(reply("feed", "r9"))
        assertEquals("feed", vm.waiting)
    }

    @Test
    fun aMealKeepsItsOperationIdUntilThePageIsFinalAboutIt() {
        open()
        vm.feed()
        // the page could not store it: the same meal is sent again with the same id
        vm.onReply(reply("feedUnsaved", "r1"))
        vm.feed()
        assertEquals(listOf("feed:op-1", "feed:op-1"), page.asked)
        vm.onReply(reply("feed", "r2"))
        vm.feed()
        assertEquals("feed:op-2", page.asked.last())
    }

    @Test
    fun aRefusedMealIsFinalToo() {
        open()
        vm.feed()
        vm.onReply(reply("feedRefusedFull", "r1"))
        vm.feed()
        assertEquals("feed:op-2", page.asked.last())
    }

    @Test
    fun anAnswerThatNeverCameDoesNotLoseTheOperationId() {
        open()
        vm.feed()
        // the screen was away while the page answered; coming back stops the waiting
        vm.hidden()
        open()
        assertNull(vm.waiting)
        vm.feed()
        assertEquals(listOf("feed:op-1", "feed:op-1"), page.asked)
    }

    @Test
    fun theOtherCommandsAreSentAsAsked() {
        open()
        vm.vacation(true)
        vm.onReply(reply("vacationOn", "r1"))
        vm.rename("Mochi")
        assertEquals(listOf("vacation:true", "rename:Mochi"), page.asked)
    }

    @Test
    fun theCatBlinksBackWhenThePageSaysSo() {
        open()
        vm.blink()
        vm.onReply(reply("blink", "r1"))
        assertEquals(listOf(PetReaction.Blink(0)), vm.drainReactions())
        assertTrue(vm.drainReactions().isEmpty())
    }

    @Test
    fun aCatThatSquintsDoesNotBlinkAndTheScreenSaysWhy() {
        open()
        vm.blink()
        vm.onReply(reply("blinkSquint", "r1"))
        assertTrue(vm.drainReactions().isEmpty())
        assertTrue(vm.notice!!.text.contains("squints"))
    }

    @Test
    fun aFullCatGoesToItsBowlAndTheScreenSaysSo() {
        open()
        vm.feed()
        vm.onReply(reply("feedRefusedFull", "r1"))
        assertEquals(listOf(PetReaction.Bowl(0)), vm.drainReactions())
        assertEquals("Miso is not hungry yet. A meal in about 3 h is just right.", vm.notice!!.text)
    }

    @Test
    fun theMealTheReplyAnnouncesIsPlayedByItsCueAndNotTwice() {
        open()
        vm.feed()
        // the reply and the projection that carries the meal arrive together; the reply adds
        // nothing
        update("fed", 2)
        vm.onReply(reply("feed", "r1"))
        assertEquals(listOf(PetReaction.Fed(0)), vm.drainReactions())
        update("fed", 3)
        assertTrue(vm.drainReactions().isEmpty())
    }

    @Test
    fun aMealFromBeforeTheScreenOpenedIsNotPlayed() {
        repository.accept(oldMeal(ago = 60_000), true)
        vm.opened()
        assertTrue(vm.drainReactions().isEmpty())
        // and still not when the same cue comes again
        update("alive", 2)
        assertTrue(vm.drainReactions().isEmpty())
    }

    @Test
    fun aMealThatJustHappenedIsWhyTheOwnerIsHere() {
        repository.accept(oldMeal(ago = 300), true)
        vm.opened()
        assertEquals(listOf(PetReaction.Fed(0)), vm.drainReactions())
    }

    @Test
    fun bothCatsFedAtOnceAreBothPlayed() {
        open("two-cats")
        val two =
            PetFixtures.projection("two-cats").put("sequence", 2).apply {
                getJSONObject("cue").put("fed", JSONArray().put(cue(0, 1)).put(cue(1, 2)))
            }
        repository.accept(two)
        vm.projected()
        assertEquals(listOf(PetReaction.Fed(0), PetReaction.Fed(1)), vm.drainReactions())
    }

    @Test
    fun aNewPageSessionStartsItsMealsAgain() {
        open("fed")
        vm.drainReactions()
        val reloaded =
            PetFixtures.projection("fed").put("webSessionId", "after-a-reload").put("sequence", 1)
        repository.accept(reloaded, true)
        vm.opened()
        // id 1 again, just served: a different page, a different meal
        assertEquals(listOf(PetReaction.Fed(0)), vm.drainReactions())
    }

    @Test
    fun nothingIsQueuedWhileTheScreenIsBehindThePage() {
        open()
        vm.hidden()
        update("fed", 2)
        assertTrue(vm.drainReactions().isEmpty())
        // it opens again later: that meal is not news
        vm.opened()
        assertTrue(vm.drainReactions().isEmpty())
    }

    @Test
    fun thePagesMessageShowsOncePerIdAndOnlyWhileShowing() {
        open()
        val said =
            PetFixtures.projection("alive")
                .put("sequence", 2)
                .put("notice", notice(7, "Miso purrs."))
        repository.accept(said)
        vm.projected()
        assertEquals("Miso purrs.", vm.notice!!.text)
        vm.noticeShown(vm.notice!!)
        assertNull(vm.notice)
        // the same toast in the next projection is not shown again
        repository.accept(
            PetFixtures.projection("alive")
                .put("sequence", 3)
                .put("notice", notice(7, "Miso purrs."))
        )
        vm.projected()
        assertNull(vm.notice)
        repository.accept(
            PetFixtures.projection("alive")
                .put("sequence", 4)
                .put("notice", notice(8, "Miso purrs."))
        )
        vm.projected()
        assertNotNull(vm.notice)
    }

    @Test
    fun aMessageForAScreenBehindThePageIsNotShown() {
        open()
        vm.hidden()
        repository.accept(
            PetFixtures.projection("alive").put("sequence", 2).put("notice", notice(1, "Hi"))
        )
        vm.projected()
        assertNull(vm.notice)
    }

    @Test
    fun aCommandTheSenderCouldNotSendDoesNotWait() {
        val silent = PetViewModel(repository, NoPetCommands)
        repository.accept(PetFixtures.projection("alive"), true)
        silent.opened()
        silent.feed()
        assertNull(silent.waiting)
    }

    private fun cue(cat: Int, id: Long) = JSONObject().put("cat", cat).put("id", id).put("ago", 0)

    private fun notice(id: Long, text: String) = JSONObject().put("id", id).put("text", text)

    private fun oldMeal(ago: Long): JSONObject =
        PetFixtures.projection("fed").apply {
            getJSONObject("cue").getJSONArray("fed").getJSONObject(0).put("ago", ago)
        }
}
