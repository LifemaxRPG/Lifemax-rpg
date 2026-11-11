package com.lifemax.rpg.core.lifedirector.host

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host commands checked by hand (LIFE_DIRECTOR_HOST_SPEC.md), with a hand-written fake host: the
 * words, the answers' shapes, the order of host calls, and that nothing throws.
 */
class HostHandTest {
    /** A small world: LifeMax not connected, a store in memory that can refuse, ids counted. */
    private class FakeHost(var stored: JsonValue? = null, var refuse: String? = null) :
        HostEffects {
        var ids = 0

        override fun connected() = false

        override fun toast(message: String) = true

        override fun playerId() = "player"

        override fun readQuests(
            now: Double,
            zone: String,
            overlay: JsonValue,
            revoked: JsonValue?,
        ) = emptyList<JsonValue>()

        override fun readPlanSessions(now: Double, zone: String, days: Double) =
            emptyList<JsonValue>()

        override fun awardActivity(event: JsonValue, title: String?) =
            AwardReply(false, "HOST_NOT_CONNECTED")

        override fun completeQuest(hostRef: JsonValue) = QuestCompletion(false)

        override fun activitiesSince(cursor: String?) = emptyList<HostActivity>()

        override fun activityExists(id: String) = false

        override fun milestones() = emptyList<JsonValue>()

        override val resolvesRetiredQuests = false

        override fun retiredUntimedQuest(ref: JsonValue): JsonValue? = null

        override fun newId() = "id-" + ++ids

        override fun deviceZone() = "UTC"

        override fun loadState(playerId: String, zone: String, now: Double) =
            LoadedState(
                stored ?: state(),
                if (stored == null) "NEW" else "lifemax_director_state_v1",
            )

        override fun saveState(state: JsonValue): SaveOutcome {
            refuse?.let {
                return SaveOutcome(false, it)
            }
            stored = state
            return SaveOutcome.OK
        }

        override fun clearState(playerId: String, zone: String, now: Double) = saveState(state())

        override fun exportBackup(state: JsonValue) = "{}"

        override fun importBackup(text: String) =
            ImportedBackup(null, "Backup is not valid: not an object")
    }

    companion object {
        private const val WED = 1_793_773_800_000.0 // Wed 4 Nov 2026 06:30 UTC

        private fun s(v: String) = JsonString(v)

        private fun n(v: Double) = JsonNumber(v)

        fun state(setup: Boolean = false): JsonObject =
            JsonObject(
                linkedMapOf(
                    "schemaVersion" to n(1.0),
                    "playerId" to s("player"),
                    "setupComplete" to JsonBool.of(setup),
                    "availability" to
                        JsonObject(
                            linkedMapOf(
                                "timezone" to s("UTC"),
                                "sleepRequirementMinutes" to n(480.0),
                                "rules" to JsonArray.EMPTY,
                                "blackouts" to JsonArray.EMPTY,
                            )
                        ),
                    "fixedCommitments" to JsonArray.EMPTY,
                    "goals" to JsonArray.EMPTY,
                    "prefs" to JsonObject.EMPTY,
                    "changeLog" to JsonArray.EMPTY,
                    "ui" to JsonObject(linkedMapOf("tab" to s("today"))),
                )
            )
    }

    private fun run(
        host: HostEffects,
        c: HostCommand,
        state: HostState = HostState(),
        at: Double = WED,
    ) = HostCommands.run(state, at, host, c)

    private fun message(o: HostOutcome) = ((o.reply as HostReply.Command).reply).message

    @Test
    fun wordsAreThePagesWords() {
        assertEquals("10:00 PM", HostWords.hm(1320.0))
        assertEquals("12:00 AM", HostWords.hm(0.0))
        assertEquals("12:00 PM", HostWords.hm(720.0))
        assertEquals("7:15 AM", HostWords.hm(435.0))
        assertEquals("Wed, Nov 4", HostWords.day(WED / 1000, "UTC"))
        assertEquals("Tue, Nov 3", HostWords.day(WED / 1000, "America/Los_Angeles"))
        assertEquals("2026-11-02", HostWords.weekKey(WED / 1000, "UTC"))
        assertEquals("Bedtime", HostWords.handleLabel("SLEEP", "Sleep", "start"))
        assertEquals("School / class ends", HostWords.handleLabel("CLASS", "", "end"))
        assertEquals("Commitment starts", HostWords.handleLabel("OTHER", null, "start"))
    }

    @Test
    fun aCommandBootsFirstAndTalksToTheHostInThePagesOrder() {
        val o = run(FakeHost(), HostCommand.Today("done", JsonObject(mapOf("id" to s("nope")))))
        assertEquals("That is no longer on the plan.", message(o))
        assertEquals(
            listOf("playerId", "store.load", "store.save", "connected"),
            o.calls.map { it.fn },
        )
        assertEquals("setup", o.state.tab)
        assertEquals(JsonBool.TRUE, ((o.state.st as JsonObject)["ui"] as JsonObject)["lab"])
    }

    @Test
    fun refusalsAndRepeatsCarryThePagesShapes() {
        val host = FakeHost()
        val booted = run(host, HostCommand.Boot).state
        val sleep = JsonObject(mapOf("bed" to n(1380.0), "wake" to n(420.0)))
        val first = run(host, HostCommand.Do("sleepSave", sleep), booted)
        assertEquals(
            CommandReply(true, "", changed = true),
            (first.reply as HostReply.Command).reply,
        )
        assertEquals("Sleep saved", first.state.notice?.text)
        val again = run(host, HostCommand.Do("sleepSave", sleep), first.state)
        assertEquals(
            CommandReply(true, "", changed = false),
            (again.reply as HostReply.Command).reply,
        )
        val short =
            run(
                host,
                HostCommand.Do("sleepSave", JsonObject(mapOf("bed" to n(0.0), "wake" to n(240.0)))),
                again.state,
            )
        assertEquals(
            CommandReply.refuse("That is under 5 hours of sleep. Please check the times."),
            (short.reply as HostReply.Command).reply,
        )
        assertEquals(
            "Life Director cannot do that.",
            message(run(host, HostCommand.Today("timer", null), booted)),
        )
        assertEquals(
            "Life Director cannot do that.",
            message(run(host, HostCommand.Do("toString", null), booted)),
        )
    }

    @Test
    fun aRefusedSaveIsTheAnswerAndTheBanner() {
        val host = FakeHost()
        val booted = run(host, HostCommand.Boot).state
        host.refuse = "Storage is full."
        val o =
            run(host, HostCommand.Do("labToggle", JsonObject(mapOf("on" to JsonBool.TRUE))), booted)
        assertEquals(
            CommandReply(false, "Not saved: Storage is full."),
            (o.reply as HostReply.Command).reply,
        )
        assertEquals(
            JsonObject(linkedMapOf("kind" to s("bad"), "text" to s("Not saved: Storage is full."))),
            o.state.banner,
        )
        host.refuse = null
        val back =
            run(
                host,
                HostCommand.Do("labToggle", JsonObject(mapOf("on" to JsonBool.FALSE))),
                o.state,
            )
        assertEquals(null, back.state.banner)
    }

    @Test
    fun theBannerIsDismissedOnlyAsShown() {
        val host = FakeHost()
        val banner = JsonObject(linkedMapOf("kind" to s("warn"), "text" to s("Hello")))
        val booted = run(host, HostCommand.Boot).state.copy(banner = banner)
        val other =
            run(
                host,
                HostCommand.Today("dismissBanner", JsonObject(mapOf("text" to s("Hi")))),
                booted,
            )
        assertEquals(
            CommandReply(true, "", changed = false),
            (other.reply as HostReply.Command).reply,
        )
        assertEquals(banner, other.state.banner)
        val shown =
            run(
                host,
                HostCommand.Today("dismissBanner", JsonObject(mapOf("text" to s("Hello")))),
                booted,
            )
        assertEquals(
            CommandReply(true, "", changed = true),
            (shown.reply as HostReply.Command).reply,
        )
        assertEquals(null, shown.state.banner)
        assertTrue(shown.calls.none { it.fn == "store.save" })
    }

    @Test
    fun nothingThrows() {
        val host = FakeHost(stored = JsonObject(mapOf("schemaVersion" to n(1.0))))
        for (c in
            listOf(
                HostCommand.Boot,
                HostCommand.Tick,
                HostCommand.SyncHost,
                HostCommand.ProcessMisses,
                HostCommand.MaybeRollover,
            )) {
            val o =
                run(host, c, HostState(st = JsonObject(mapOf("availability" to JsonString("x")))))
            assertFalse(
                o.reply is HostReply.Command &&
                    (o.reply as HostReply.Command).reply.ok &&
                    c == HostCommand.MaybeRollover
            )
        }
        val o =
            run(
                host,
                HostCommand.Today("replan", null),
                HostState(st = state(setup = true).let { JsonObject(it.members - "ledger") }),
            )
        assertTrue(o.reply is HostReply.Threw)
        assertEquals("TypeError", (o.reply as HostReply.Threw).name)
    }
}
