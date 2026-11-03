package com.lifemax.rpg.feature.pet.data

import com.lifemax.rpg.feature.pet.domain.PetStroke
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The touch part of the Cat tab's wire (`pet.touch.v1`): the projection's touch fields as the page
 * makes them, read as strictly as the rest; the page's answers to a stroke (and a level it won);
 * and the payloads of the two touch commands.
 */
class PetTouchTest {
    private fun decode(name: String) =
        WebPetRepository().apply { accept(PetFixtures.projection(name), true) }.state.value!!

    @Test
    fun aShyCatThatIsPettedTwiceTodayCarriesItsTouchNumbers() {
        val touch = decode("touched").touch!!
        val cat = touch.profiles.single()
        assertEquals(4, cat.level)
        assertEquals(1.3, cat.satMul, 0.0)
        assertTrue(cat.trust in 0.0..1.0)
        assertEquals(0.0, cat.sat, 0.0)
        assertEquals(2, touch.counts.pets)
        assertEquals(3, touch.counts.petsMax)
        assertEquals(1, touch.counts.plays)
        assertEquals(2, touch.counts.playsMax)
        assertEquals("Close room", touch.words.closeRoom)
        assertEquals("Petted 2 of 3 · Played 1 of 2 today", touch.words.countsLine)
        assertNotNull("the owner's haptic strength", touch.haptics)
        assertEquals(150, touch.haptics!!.amp)
    }

    @Test
    fun everyCatOfTheHouseHasItsOwnProfile() {
        val touch = decode("two-cats").touch!!
        assertEquals(2, touch.profiles.size)
        assertEquals(1.0, touch.profiles[0].satMul, 0.0)
        assertEquals("", touch.words.countsLine)
    }

    @Test
    fun aPettedCatCarriesTheSaturationTheBrainHolds() {
        val touch = decode("petted").touch!!
        assertTrue("saturation after a pet: ${touch.profiles[0].sat}", touch.profiles[0].sat > 0.0)
        assertEquals(1, touch.counts.pets)
    }

    @Test
    fun theFirstReleasesAppIsSentNoTouchFieldsAndReadsNone() {
        assertNull(decode("no-touch").touch)
        val keys = PetFixtures.projection("no-touch").keys().asSequence().toSet()
        assertTrue(keys.none { it in TOUCH_KEYS })
    }

    @Test
    fun everyFixtureOfThisReleaseHasTheTouchFields() {
        for (name in PetFixtures.STATES) assertNotNull(name, decode(name).touch)
    }

    private class Break(val what: String, val change: JSONObject.() -> Unit)

    private val breaks =
        listOf(
            Break("touch without the counts") { remove("counts") },
            Break("counts without the touch") { remove("touch") },
            Break("touch without the words") { remove("touchWords") },
            Break("touch without haptics") { remove("haptics") },
            Break("two profiles for one cat") {
                val list = getJSONArray("touch")
                list.put(list.getJSONObject(0))
            },
            Break("no profile for the cat") { put("touch", JSONArray()) },
            Break("an unknown key in a profile") {
                getJSONArray("touch").getJSONObject(0).put("mood", 1)
            },
            Break("a missing key in a profile") {
                getJSONArray("touch").getJSONObject(0).remove("sat")
            },
            Break("a level of 6") { getJSONArray("touch").getJSONObject(0).put("level", 6) },
            Break("a level in words") { getJSONArray("touch").getJSONObject(0).put("level", "4") },
            Break("a trust over one") { getJSONArray("touch").getJSONObject(0).put("trust", 1.5) },
            Break("saturation over its cap") {
                getJSONArray("touch").getJSONObject(0).put("sat", 2)
            },
            Break("a negative saturation") {
                getJSONArray("touch").getJSONObject(0).put("sat", -0.1)
            },
            Break("a trait share of 9") { getJSONArray("touch").getJSONObject(0).put("satMul", 9) },
            Break("saturation as a word") {
                getJSONArray("touch").getJSONObject(0).put("sat", "0.5")
            },
            Break("an unknown key in the counts") { getJSONObject("counts").put("gifts", 1) },
            Break("a count of a hundred") { getJSONObject("counts").put("pets", 100) },
            Break("a daily limit of nothing") { getJSONObject("counts").put("petsMax", 0) },
            Break("a fractional count") { getJSONObject("counts").put("plays", 0.5) },
            Break("a word missing") { getJSONObject("touchWords").remove("closeRoom") },
            Break("a number for a word") { getJSONObject("touchWords").put("countsLine", 3) },
            Break("a haptic strength of nothing") { getJSONObject("haptics").put("amp", 0) },
        )

    @Test
    fun everyMalformedTouchProjectionIsRefusedAndTheLastGoodStateKept() {
        for (b in breaks) {
            val repo = WebPetRepository().apply { accept(PetFixtures.projection("touched"), true) }
            val bad = PetFixtures.projection("touched").put("sequence", 2).apply(b.change)
            assertTrue("accepted: ${b.what}", runCatching { repo.accept(bad) }.isFailure)
            assertEquals("kept: ${b.what}", 1L, repo.state.value!!.sequence)
            assertNotNull(repo.state.value!!.touch)
        }
    }

    @Test
    fun theOwnersHapticsMayBeOffAndThatIsNull() {
        val j = PetFixtures.projection("touched").put("haptics", JSONObject.NULL)
        assertNull(WebPetRepository().apply { accept(j, true) }.state.value!!.touch!!.haptics)
    }

    // ----------------------------------------------------------------------- replies

    private fun reply(key: String) = PetFixtures.reply(key).toPetReply()

    @Test
    fun aPetTheDayCountedAndOneItDidNot() {
        assertTrue(reply("strokePet")!!.changed)
        assertEquals(0, reply("strokePet")!!.levelUp)
        assertFalse(reply("strokeTap")!!.changed)
        assertFalse(reply("strokeLeft")!!.changed)
        assertEquals("Already recorded.", reply("strokePetAgain")!!.message)
    }

    @Test
    fun aLevelTheStrokeWonComesWithTheWordsOfThePage() {
        val won = reply("strokeLevelUp")!!
        assertTrue(won.ok && won.changed)
        assertEquals(4, won.levelUp)
        assertTrue(won.message.contains("trusts you more now"))
    }

    @Test
    fun aRefusedStrokeAndOneTheBrowserCouldNotSaveAreNotOk() {
        assertFalse(reply("strokeRefused")!!.ok)
        val unsaved = reply("strokeUnsaved")!!
        assertFalse(unsaved.ok || unsaved.changed)
        assertTrue(unsaved.message.startsWith("Could not save"))
    }

    @Test
    fun theSoundCommandsAnswerOk() {
        assertEquals("touch", reply("purrOn")!!.command)
        assertTrue(reply("purrOff")!!.ok)
    }

    @Test
    fun aMalformedLevelIsDropped() {
        val bad =
            listOf<JSONObject.() -> Unit>(
                { put("levelUp", 6) },
                { put("levelUp", -1) },
                { put("levelUp", "4") },
                { put("levelUp", 1.5) },
                { put("level", 4) },
            )
        for (change in bad) assertNull(
            PetFixtures.reply("strokeLevelUp").apply(change).toPetReply()
        )
    }

    // ----------------------------------------------------------------------- commands

    @Test
    fun theTouchCommandsAreSentWithTheirPayloads() {
        val sent = ArrayList<Pair<String, JSONObject>>()
        val commands = PetCommandSender { command, payload ->
            sent += command to payload
            "id-${sent.size}"
        }
        assertEquals("id-1", commands.stroke("op-9", PetStroke(1, 1500, "over", true, false, true)))
        assertEquals("id-2", commands.purr(0, 0.75f))
        assertEquals("id-3", commands.purr(0, 0f))
        assertEquals(setOf("touch"), sent.map { it.first }.toSet())
        val stroke = sent[0].second
        assertEquals(
            setOf("kind", "op", "cat", "heldMs", "left", "ignored", "spam", "keepSleep"),
            stroke.keys().asSequence().toSet(),
        )
        assertEquals("stroke", stroke.getString("kind"))
        assertEquals("op-9", stroke.getString("op"))
        assertEquals(1, stroke.getInt("cat"))
        assertEquals(1500L, stroke.getLong("heldMs"))
        assertEquals("over", stroke.getString("left"))
        assertTrue(stroke.getBoolean("ignored") && stroke.getBoolean("keepSleep"))
        assertFalse(stroke.getBoolean("spam"))
        assertEquals(
            setOf("kind", "cat", "on", "level"),
            sent[1].second.keys().asSequence().toSet(),
        )
        assertTrue(sent[1].second.getBoolean("on"))
        assertEquals(0.75, sent[1].second.getDouble("level"), 0.0)
        assertFalse(sent[2].second.getBoolean("on"))
    }
}
