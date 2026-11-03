package com.lifemax.rpg.feature.pet.data

import com.lifemax.rpg.feature.pet.domain.PetReply
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which projection the repository keeps, and how the page's replies and the commands are read. */
class PetRepositoryTest {
    private fun alive() = PetFixtures.projection("alive")

    @Test
    fun oldUpdatesCannotReplaceNewSnapshots() {
        val repo = WebPetRepository()
        val first = alive()
        repo.accept(first, true)
        repo.accept(alive().put("sequence", 3).put("hint", "Third"))
        repo.accept(alive().put("sequence", 2).put("hint", "Second"))
        repo.accept(first)
        assertEquals("Third", repo.state.value!!.hint)
        assertEquals(3L, repo.state.value!!.sequence)
    }

    @Test
    fun aRepeatOfTheSameSequenceChangesNothing() {
        val repo = WebPetRepository()
        repo.accept(alive(), true)
        repo.accept(alive().put("hint", "Again"))
        assertEquals(
            "Try dragging your finger across the floor. Miso may follow.",
            repo.state.value!!.hint,
        )
    }

    @Test
    fun aForeignSessionNeedsAnExplicitOpen() {
        val repo = WebPetRepository()
        repo.accept(alive(), true)
        val other = alive().put("webSessionId", "other").put("name", "Other")
        repo.accept(other)
        assertEquals("Miso", repo.state.value!!.name)
        repo.accept(other, true)
        assertEquals("Other", repo.state.value!!.name)
    }

    @Test
    fun theFirstProjectionNeedsNoExplicitOpenToBeKept() {
        val repo = WebPetRepository()
        repo.accept(alive())
        assertNotNull(repo.state.value)
    }

    @Test
    fun theFedProjectionFollowsTheOneItWasMadeFrom() {
        val repo = WebPetRepository()
        repo.accept(PetFixtures.projection("alive"), true)
        repo.accept(PetFixtures.projection("fed"))
        assertEquals(2L, repo.state.value!!.sequence)
        assertEquals(1, repo.state.value!!.fedCues.size)
    }

    // ----------------------------------------------------------------------- replies

    private fun reply(key: String): PetReply? = PetFixtures.reply(key).toPetReply()

    @Test
    fun aMealThatLandedAndOneThatWasRefused() {
        val fed = reply("feed")!!
        assertTrue(fed.ok && fed.changed)
        assertEquals("feed", fed.command)
        assertEquals("req-feed", fed.requestId)
        val full = reply("feedRefusedFull")!!
        assertTrue(!full.ok)
        assertEquals("full", full.why)
        assertEquals(3.0, full.inHours, 0.001)
        assertEquals("bowl", full.reaction)
        assertEquals("Miso is not hungry yet. A meal in about 3 h is just right.", full.message)
        val vacation = reply("feedRefusedVacation")!!
        assertEquals("vacation", vacation.why)
        assertEquals("", vacation.reaction)
    }

    @Test
    fun aRepeatIsAnsweredWithoutChangingAnything() {
        val again = reply("feedAgain")!!
        assertTrue(again.ok)
        assertTrue(!again.changed)
        assertEquals("Already recorded.", again.message)
    }

    @Test
    fun aBlinkAndTheCatThatSquints() {
        assertEquals("blink", reply("blink")!!.reaction)
        val squint = reply("blinkSquint")!!
        assertEquals("", squint.reaction)
        assertTrue(squint.message.contains("squints"))
    }

    @Test
    fun aSaveThatFailedIsNotOk() {
        val failed = reply("feedUnsaved")!!
        assertTrue(!failed.ok && !failed.changed)
        assertTrue(failed.message.startsWith("Could not save"))
    }

    @Test
    fun theRepliesToTheOtherCommands() {
        assertEquals("rename", reply("rename")!!.command)
        assertEquals("vacation", reply("vacationOn")!!.command)
        assertTrue(!reply("renameEmpty")!!.ok)
    }

    @Test
    fun aReplyToAnotherCommandIsNotOurs() {
        assertNull(reply("unknown"))
        assertNull(
            JSONObject()
                .put("command", "coat")
                .put("requestId", "x")
                .put("ok", false)
                .put("message", "no")
                .toPetReply()
        )
    }

    @Test
    fun aMalformedReplyIsDropped() {
        val bad =
            listOf<JSONObject.() -> Unit>(
                { put("extra", 1) },
                { remove("why") },
                { put("why", "tired") },
                { put("reaction", "dance") },
                { put("inHours", -1) },
                { put("inHours", "3") },
                { put("ok", "yes") },
                { put("message", 7) },
                { put("requestId", 4) },
            )
        for (change in bad) assertNull(PetFixtures.reply("feed").apply(change).toPetReply())
    }

    // ----------------------------------------------------------------------- commands

    @Test
    fun theCommandsAreSentWithTheirPayloads() {
        val sent = ArrayList<Pair<String, String>>()
        val commands = PetCommandSender { command, payload ->
            sent += command to payload.toString()
            "id-${sent.size}"
        }
        assertEquals("id-1", commands.feed("op-1"))
        assertEquals("id-2", commands.blink())
        assertEquals("id-3", commands.vacation(true))
        assertEquals("id-4", commands.rename("Mochi"))
        assertEquals(
            listOf(
                "feed" to """{"op":"op-1"}""",
                "blink" to "{}",
                "vacation" to """{"on":true}""",
                "rename" to """{"name":"Mochi"}""",
            ),
            sent,
        )
    }
}
