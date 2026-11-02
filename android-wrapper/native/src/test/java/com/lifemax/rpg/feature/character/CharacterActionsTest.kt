package com.lifemax.rpg.feature.character

import com.lifemax.rpg.feature.character.data.CharacterCommandSender
import com.lifemax.rpg.feature.character.data.WebCharacterRepository
import com.lifemax.rpg.feature.character.data.toCharacterReply
import com.lifemax.rpg.feature.character.domain.CharacterCommands
import com.lifemax.rpg.feature.character.domain.CharacterProjection
import com.lifemax.rpg.feature.character.presentation.CharacterActions
import com.lifemax.rpg.feature.character.presentation.CharacterSheet
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Character's actions: what the projection says about them (strictly, once the page speaks them),
 * the page's replies as the page sent them (tools/gen_character_fixtures.js), and what a tap does:
 * the sheet, the command with its operation id, and what a reply or the next projection settles.
 */
class CharacterActionsTest {
    private fun json(name: String) =
        JSONObject(javaClass.getResource("/fixtures/$name.json")!!.readText())

    private fun projection(name: String = "character-actions", seq: Long = 1) =
        WebCharacterRepository().run {
            accept(json(name).put("sequence", seq), true)
            state.value!!
        }

    private fun reply(name: String, id: String) =
        json("character-replies").getJSONObject(name).put("requestId", id).toCharacterReply()!!

    private class Recorder : CharacterCommands {
        val sent = ArrayList<String>()
        private var n = 0

        private fun note(s: String) = "r${++n}".also { sent += "$it $s" }

        override fun luck(op: String, day: String) = note("luck $op $day")

        override fun benchmarkAdd(op: String, name: String, value: String) =
            note("add $op [$name] [$value]")

        override fun benchmarkSet(index: Int, name: String, value: String) =
            note("set $index [$name] [$value]")

        override fun benchmarkRemove(index: Int, name: String) = note("remove $index [$name]")

        override fun log(attribute: String) = note("log $attribute")
    }

    private fun actions(p: CharacterProjection = projection()): Pair<CharacterActions, Recorder> {
        val sent = Recorder()
        var n = 0
        return CharacterActions(sent) { "op-${++n}" }.also { it.projected(p) } to sent
    }

    // ---- the projection ----

    @Test
    fun theActionsProjectionCarriesWhatTheScreenNeeds() {
        val p = projection()
        assertTrue(p.actions)
        assertEquals("2026-10-28", p.today)
        assertNull(p.luckRoll)
        assertEquals(60, p.limits.name)
        assertEquals(40, p.limits.value)
        assertEquals("Daily Luck", p.words.luck.heading)
        assertEquals("Benchmark name", p.words.benchmarks.nameLabel)
        assertEquals(3, p.benchmarks.size)
    }

    @Test
    fun aRolledProjectionCarriesTheDiceAndTheTiersOwnWords() {
        val roll = projection("character-actions-rolled").luckRoll!!
        assertEquals(listOf(3, 6, 1, 4), roll.dice)
        assertEquals("9,121", roll.score)
        assertEquals("Legendary", roll.tier)
        assertEquals("Off the charts. Buy two tickets.", roll.message)
        assertEquals(0xFFFFFFFFL, roll.color)
    }

    @Test
    fun aPageWithoutTheActionsIsReadAsBefore() {
        val old = WebCharacterRepository()
        old.accept(
            JSONObject(javaClass.getResource("/character-projection.json")!!.readText()),
            true,
        )
        assertFalse(old.state.value!!.actions)
        val (actions, sent) = actions(old.state.value!!)
        assertFalse(actions.handle("luck", ""))
        assertFalse(actions.handle("benchmarks", ""))
        assertFalse(actions.handle("log", "endurance"))
        assertNull(actions.sheet)
        assertTrue(sent.sent.isEmpty())
    }

    @Test
    fun onceThePageSpeaksTheActionsEveryFieldIsRequired() {
        for (field in listOf("today", "benchmarkLimits", "words", "luckRoll")) {
            val j = json("character-actions")
            j.remove(field)
            assertTrue("$field", runCatching { WebCharacterRepository().accept(j, true) }.isFailure)
        }
        val bad = json("character-actions")
        bad.getJSONObject("words").getJSONObject("luck").remove("retry")
        assertTrue(runCatching { WebCharacterRepository().accept(bad, true) }.isFailure)
        val dice = json("character-actions-rolled")
        dice.getJSONObject("luckRoll").put("dice", org.json.JSONArray(listOf(1, 2, 3, 7)))
        assertTrue(runCatching { WebCharacterRepository().accept(dice, true) }.isFailure)
    }

    @Test
    fun theRepliesAreReadAsThePageSentThem() {
        val fresh = reply("luckFresh", "r1")
        assertTrue(fresh.ok && fresh.changed)
        assertEquals("Legendary", fresh.luck!!.tier)
        val repeat = reply("luckRepeat", "r1")
        assertTrue(repeat.ok && !repeat.changed)
        assertEquals("Already rolled today.", repeat.message)
        assertEquals(
            "Could not save to this browser (storage full?)",
            reply("luckRefusedStore", "r1").message,
        )
        assertEquals("Enter a name and result", reply("benchmarkEmpty", "r1").message)
        val noLuck = JSONObject().put("command", "luck").put("requestId", "r1").put("ok", true)
        assertNull(noLuck.put("luck", "garbled").toCharacterReply()!!.luck)
        assertNull(JSONObject().toCharacterReply())
    }

    @Test
    fun theCommandsGoOutWithThePagesNames() {
        val sent = ArrayList<Pair<String, JSONObject>>()
        val s = CharacterCommandSender { c, p -> "r".also { sent += c to p } }
        s.luck("o", "d")
        s.benchmarkAdd("o", "n", "v")
        s.benchmarkSet(2, "n", "v")
        s.benchmarkRemove(1, "n")
        s.log("physique")
        assertEquals(
            listOf("luck", "benchmarkAdd", "benchmarkSet", "benchmarkRemove", "log"),
            sent.map { it.first },
        )
        assertEquals("o", sent[0].second.getString("op"))
        assertEquals("d", sent[0].second.getString("day"))
        assertEquals("v", sent[1].second.getString("value"))
        assertEquals(2, sent[2].second.getInt("index"))
        assertEquals("n", sent[3].second.getString("name"))
        assertEquals("physique", sent[4].second.getString("attribute"))
    }

    // ---- Luck ----

    @Test
    fun aLuckTapAsksThePageOnceWithTheDayAndShowsTheRollItAnswers() {
        val (a, sent) = actions()
        assertTrue(a.handle("luck", ""))
        assertEquals(listOf("r1 luck op-1 2026-10-28"), sent.sent)
        assertEquals(CharacterSheet.Luck(null, true), a.sheet)
        assertTrue(a.onReply(reply("luckFresh", "r1")))
        val shown = a.sheet as CharacterSheet.Luck
        assertEquals("9,121", shown.roll!!.score)
        assertFalse(shown.pending)
        assertNull(a.operation("luck", "2026-10-28"))
    }

    @Test
    fun aRefusedRollKeepsItsOperationAndTriesAgainWithTheSameId() {
        val (a, sent) = actions()
        a.handle("luck", "")
        a.onReply(reply("luckRefusedStore", "r1"))
        val s = a.sheet as CharacterSheet.Luck
        assertNull(s.roll)
        assertEquals("Could not save to this browser (storage full?)", s.error)
        assertEquals("op-1", a.operation("luck", "2026-10-28"))
        a.retryLuck()
        assertEquals("r2 luck op-1 2026-10-28", sent.sent.last())
        assertTrue((a.sheet as CharacterSheet.Luck).pending)
    }

    @Test
    fun aRollThatWasAlreadyMadeIsShownWithoutAskingAgain() {
        val (a, sent) = actions(projection("character-actions-rolled"))
        a.handle("luck", "")
        assertTrue(sent.sent.isEmpty())
        assertEquals("9,121", (a.sheet as CharacterSheet.Luck).roll!!.score)
    }

    @Test
    fun theNextProjectionSettlesARollWhoseReplyNeverCame() {
        val (a, _) = actions()
        a.handle("luck", "")
        a.projected(projection("character-actions-rolled", 2))
        val s = a.sheet as CharacterSheet.Luck
        assertEquals("9,121", s.roll!!.score)
        assertFalse(s.pending)
        assertNull(a.operation("luck", "2026-10-28"))
    }

    @Test
    fun aRepeatReplyShowsTheSameRollWithThePagesNote() {
        val (a, _) = actions()
        a.handle("luck", "")
        a.onReply(reply("luckRepeat", "r1"))
        assertEquals("Already rolled today.", (a.sheet as CharacterSheet.Luck).notice)
    }

    @Test
    fun aScreenThatOpensAgainStopsWaitingButKeepsItsOperation() {
        val (a, sent) = actions()
        a.handle("luck", "")
        a.opened()
        assertFalse((a.sheet as CharacterSheet.Luck).pending)
        assertFalse(a.onReply(reply("luckFresh", "r1")))
        a.retryLuck()
        assertEquals("r2 luck op-1 2026-10-28", sent.sent.last())
    }

    // ---- benchmarks ----

    @Test
    fun addingABenchmarkSendsItOnceWithAnOperationAndClearsTheDraftOnOk() {
        val (a, sent) = actions()
        a.handle("benchmarks", "")
        a.change(name = "Squat", value = "300 lb")
        a.save()
        assertEquals("r1 add op-1 [Squat] [300 lb]", sent.sent.single())
        a.save() // pending: a second tap sends nothing
        assertEquals(1, sent.sent.size)
        a.onReply(reply("benchmarkAdded", "r1"))
        assertEquals(CharacterSheet.Benchmarks(), a.sheet)
        assertNull(a.operation("benchmarkAdd", "Squat"))
    }

    @Test
    fun aRefusalIsShownInThePagesWordsAndTheDraftAndOperationStay() {
        val (a, sent) = actions()
        a.handle("benchmarks", "")
        a.change(name = "Plank", value = "")
        a.save()
        a.onReply(reply("benchmarkEmpty", "r1"))
        val s = a.sheet as CharacterSheet.Benchmarks
        assertEquals("Enter a name and result", s.error)
        assertEquals("Plank", s.name)
        assertFalse(s.pending)
        a.change(value = "2:00")
        a.save()
        assertEquals("r2 add op-1 [Plank] [2:00]", sent.sent.last())
    }

    @Test
    fun aRepeatedAddSaysItWasRecorded() {
        val (a, _) = actions()
        a.handle("benchmarks", "")
        a.change("Squat", "300 lb")
        a.save()
        a.onReply(reply("benchmarkRepeat", "r1"))
        assertEquals("Already recorded.", (a.sheet as CharacterSheet.Benchmarks).notice)
    }

    @Test
    fun editingAResultNamesTheBenchmarkItSawAndTheNameCannotChange() {
        val (a, sent) = actions()
        a.handle("benchmarks", "")
        a.edit(1)
        assertEquals(
            CharacterSheet.Benchmarks(editing = 1, name = "Bench press", value = "185 lb"),
            a.sheet,
        )
        a.change(value = "205 lb")
        a.save()
        assertEquals("r1 set 1 [Bench press] [205 lb]", sent.sent.single())
        a.onReply(reply("benchmarkSet", "r1"))
        assertEquals(CharacterSheet.Benchmarks(), a.sheet)
        a.edit(0)
        a.cancelEdit()
        assertEquals(CharacterSheet.Benchmarks(), a.sheet)
    }

    @Test
    fun aBenchmarkThatChangedUnderTheSheetIsRefusedAndTheEditStays() {
        val (a, _) = actions()
        a.handle("benchmarks", "")
        a.edit(0)
        a.change(value = "1")
        a.save()
        a.onReply(reply("benchmarkChanged", "r1"))
        val s = a.sheet as CharacterSheet.Benchmarks
        assertEquals("That benchmark changed. Nothing was changed.", s.error)
        assertEquals(0, s.editing)
    }

    @Test
    fun removingABenchmarkAsksThePageWithTheNameSeen() {
        val (a, sent) = actions()
        a.handle("benchmarks", "")
        a.remove(2)
        assertEquals("r1 remove 2 [Pull-ups]", sent.sent.single())
        a.remove(2) // pending
        assertEquals(1, sent.sent.size)
        a.onReply(reply("benchmarkRemoved", "r1"))
        assertFalse((a.sheet as CharacterSheet.Benchmarks).pending)
        a.remove(9) // not in the list
        assertEquals(1, sent.sent.size)
    }

    // ---- Log, replies and the page ----

    @Test
    fun aLogTapAsksThePagePrefillAndGoesOnlyOnceUntilItAnswers() {
        val (a, sent) = actions()
        assertTrue(a.handle("log", "endurance"))
        assertTrue(a.handle("log", "endurance"))
        assertEquals(listOf("r1 log endurance"), sent.sent)
        assertNull(a.sheet)
        a.onReply(reply("logPrefilled", "r1"))
        a.handle("log", "physique")
        assertEquals("r2 log physique", sent.sent.last())
    }

    @Test
    fun anAttributeTapStaysWithThePage() {
        val (a, sent) = actions()
        assertFalse(a.handle("attribute", "physique"))
        assertTrue(sent.sent.isEmpty())
    }

    @Test
    fun aReplyThatIsNotOursIsNotTaken() {
        val (a, _) = actions()
        assertFalse(a.onReply(reply("luckFresh", "elsewhere")))
    }

    @Test
    fun aCelebrationInAReplyPlaysOnceAndNotWhenHidden() {
        val (a, _) = actions()
        a.handle("luck", "")
        val withXp =
            JSONObject()
                .put("command", "luck")
                .put("requestId", "r1")
                .put("ok", true)
                .put(
                    "celebrations",
                    org.json
                        .JSONArray()
                        .put(
                            JSONObject()
                                .put("xp", 12)
                                .put(
                                    "gains",
                                    org.json
                                        .JSONArray()
                                        .put(
                                            JSONObject()
                                                .put("attribute", "wellbeing")
                                                .put("short", "WLB")
                                                .put("xp", 12)
                                                .put("color", 4294967295L)
                                        ),
                                )
                        ),
                )
        a.onReply(withXp.toCharacterReply()!!)
        val c = a.celebration!!
        assertEquals(12, c.awards.single().xp)
        a.celebrated(c.id)
        assertNull(a.celebration)
        a.handle("luck", "")
        a.onReply(withXp.put("requestId", "r2").toCharacterReply()!!)
        a.hidden()
        assertNull(a.celebration)
    }

    @Test
    fun anotherPageSessionStartsClean() {
        val (a, _) = actions()
        a.handle("benchmarks", "")
        a.change("Squat", "1")
        a.save()
        val other = WebCharacterRepository()
        other.accept(json("character-actions").put("webSessionId", "another"), true)
        a.projected(other.state.value!!)
        assertNull(a.sheet)
        assertNull(a.operation("benchmarkAdd", "Squat"))
    }

    @Test
    fun theSheetClosesWhenThePageStopsSpeakingTheActions() {
        val (a, _) = actions()
        a.handle("benchmarks", "")
        a.projected(projection().copy(actions = false, sequence = 2))
        assertNull(a.sheet)
    }
}
