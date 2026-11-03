package com.lifemax.rpg.feature.pet.world

import com.lifemax.rpg.feature.pet.world.WorldFixtures.LILY
import com.lifemax.rpg.feature.pet.world.WorldFixtures.NOW
import com.lifemax.rpg.feature.pet.world.WorldFixtures.TED
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every command native sends to the page, written and read back as the page reads it, with
 * repeat-safe op ids; 40 malformed payloads refused; the page's answers read strictly.
 */
class WorldCommandJsonTest {
    private val all: List<WorldCommand> =
        listOf(
            SaveWorld("w:1", 0, 1, WorldFixtures.blob()),
            PetDone(OpIds.of(TED, "pet", 1), TED, 1_400),
            PlayDone(OpIds.of(TED, "play", 2), TED, "feather", true),
            GiftTaken(OpIds.of(LILY, "gift", 1), LILY),
            MomentReport(OpIds.firstMoment(TED, "first_glass"), TED, "first_glass", NOW, null),
            MomentReport(OpIds.quirk(TED, "lap_barnacle"), TED, "quirk", NOW, "lap_barnacle"),
            SocialReport(OpIds.of(TED, "social", 3), "nap", listOf(TED, LILY), null, NOW),
            SocialReport(OpIds.of(TED, "social", 4), "standoff", listOf(TED, LILY), TED, NOW),
            DwellReport(
                OpIds.of(LILY, "dwell", 9),
                LILY,
                WorldFixtures.DAY,
                mapOf("box" to 600),
                mapOf("mouse" to 45),
            ),
            CareFeed("feed-1"),
            CareVacation(true),
            CareRename("Lily"),
        )

    @Test
    fun everyCommandRoundTrips() {
        for (c in all) assertEquals(
            c.name,
            c,
            WorldCommandJson.decode(c.name, WorldCommandJson.encode(c)),
        )
        assertEquals(WorldCommandJson.NAMES, all.map { it.name }.toSet())
    }

    @Test
    fun opIdsAreStableAndInForm() {
        assertEquals("$TED:first_glass", OpIds.firstMoment(TED, "first_glass"))
        assertEquals(OpIds.firstMoment(TED, "first_co"), OpIds.firstMoment(TED, "first_co"))
        for (c in all.filter { it.op.isNotEmpty() }) assertTrue(
            c.op,
            WorldContract.OP_ID.matches(c.op),
        )
    }

    @Test
    fun malformedPayloadsAreRefused() {
        val cases =
            listOf<Triple<String, WorldCommand, (JSONObject) -> Unit>>(
                Triple("world: unknown key", all[0]) { it.put("force", true) },
                Triple("world: rev 0", all[0]) { it.put("rev", 0) },
                Triple("world: epoch negative", all[0]) { it.put("epoch", -1) },
                Triple("world: blob a string", all[0]) { it.put("blob", "{}") },
                Triple("world: blob malformed", all[0]) { it.getJSONObject("blob").remove("cats") },
                Triple("world: op too long", all[0]) { it.put("op", "x".repeat(81)) },
                Triple("world: op with a space", all[0]) { it.put("op", "a b") },
                Triple("world: op empty", all[0]) { it.put("op", "") },
                Triple("pet: held a fraction", all[1]) { it.put("heldMs", 1.5) },
                Triple("pet: held for an hour", all[1]) { it.put("heldMs", 3_600_000) },
                Triple("pet: cat by slot", all[1]) { it.put("cat", 0) },
                Triple("pet: cat by name", all[1]) { it.put("cat", "Ted") },
                Triple("pet: missing op", all[1]) { it.remove("op") },
                Triple("play: unknown toy", all[2]) { it.put("toy", "laser") },
                Triple("play: played a string", all[2]) { it.put("played", "yes") },
                Triple("play: a counted flag", all[2]) { it.put("counted", true) },
                Triple("gift: a toy named", all[3]) { it.put("toy", "mouse") },
                Triple("moment: unknown kind", all[4]) { it.put("kind", "first_flight") },
                Triple("moment: a recovery (page only)", all[4]) { it.put("kind", "recovery") },
                Triple("moment: a quirk on a first moment", all[4]) {
                    it.put("quirk", "lap_barnacle")
                },
                Triple("moment: a quirk without its id", all[5]) { it.remove("quirk") },
                Triple("moment: an innate quirk acquired", all[5]) {
                    it.put("quirk", "chirp_greeter")
                },
                Triple("moment: at negative", all[4]) { it.put("at", -5) },
                Triple("moment: at a string", all[4]) { it.put("at", "now") },
                Triple("social: unknown kind", all[6]) { it.put("kind", "dance") },
                Triple("social: one cat", all[6]) { it.put("cats", JSONArray().put(TED)) },
                Triple("social: the same cat twice", all[6]) {
                    it.put("cats", JSONArray().put(TED).put(TED))
                },
                Triple("social: three cats", all[6]) {
                    it.put("cats", JSONArray().put(TED).put(LILY).put("c1"))
                },
                Triple("social: a winner of a nap", all[6]) { it.put("winner", TED) },
                Triple("social: a standoff without a winner", all[7]) { it.remove("winner") },
                Triple("social: a winner outside the pair", all[7]) { it.put("winner", "c1") },
                Triple("dwell: a place the page does not know", all[8]) {
                    it.getJSONObject("spots").put("roof", 5)
                },
                Triple("dwell: more than a day", all[8]) {
                    it.getJSONObject("spots").put("box", 90_000)
                },
                Triple("dwell: negative seconds", all[8]) {
                    it.getJSONObject("toys").put("mouse", -1)
                },
                Triple("dwell: day out of form", all[8]) { it.put("day", "today") },
                Triple("dwell: spots a list", all[8]) { it.put("spots", JSONArray()) },
                Triple("feed: no op", all[9]) { it.remove("op") },
                Triple("vacation: on a string", all[10]) { it.put("on", "true") },
                Triple("rename: blank", all[11]) { it.put("name", "   ") },
                Triple("rename: fifteen characters", all[11]) { it.put("name", "x".repeat(15)) },
                Triple("rename: an extra key", all[11]) { it.put("cat", TED) },
            )
        var refused = 0
        for ((name, command, mutate) in cases) {
            val p = WorldCommandJson.encode(command)
            mutate(p)
            try {
                WorldCommandJson.decode(command.name, p)
                throw AssertionError("'$name' was accepted")
            } catch (e: WorldFormatException) {
                refused++
            }
        }
        assertTrue(refused >= 30)
        try {
            WorldCommandJson.decode("mind", JSONObject())
            throw AssertionError("an unknown command was accepted")
        } catch (e: WorldFormatException) {
            assertEquals("shape", e.code)
        }
    }

    @Test
    fun thePagesAnswersAreReadStrictly() {
        val ok =
            JSONObject()
                .put("command", "world")
                .put("requestId", "pet-3")
                .put("ok", true)
                .put("changed", true)
                .put("message", "")
                .put("why", "")
                .put("epoch", 1)
                .put("rev", 4)
        assertEquals(
            WorldReply("world", "pet-3", true, true, "", "", 1, 4),
            WorldCommandJson.reply(ok),
        )
        val stale =
            JSONObject(ok.toString()).put("ok", false).put("changed", false).put("why", "stale")
        assertEquals("stale", WorldCommandJson.reply(stale)!!.why)
        assertNull(
            "a word the page never gives",
            WorldCommandJson.reply(JSONObject(ok.toString()).put("why", "busy")),
        )
        assertNull(
            "a key it does not know",
            WorldCommandJson.reply(JSONObject(ok.toString()).put("buzz", 1)),
        )
        assertNull(
            "ok missing",
            WorldCommandJson.reply(JSONObject(ok.toString()).apply { remove("ok") }),
        )
        assertNull(
            "the care commands' answers are the Cat tab's",
            WorldCommandJson.reply(JSONObject(ok.toString()).put("command", "feed")),
        )
        val levelUp =
            JSONObject(ok.toString()).put("command", "pet").put("levelUp", 4).apply {
                remove("epoch")
                remove("rev")
            }
        assertEquals(4, WorldCommandJson.reply(levelUp)!!.levelUp)
    }
}
