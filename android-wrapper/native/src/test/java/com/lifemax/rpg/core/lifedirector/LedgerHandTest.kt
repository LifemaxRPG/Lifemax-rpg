package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ledger's rules by hand (LIFE_DIRECTOR_LEDGER_SPEC.md), independent of the golden file: event
 * ids (with three ids the page gave), idempotency, tokens, compaction, misses and transitions.
 */
class LedgerHandTest {
    private val t0 = 1_793_620_800.0

    private fun s(v: String): JsonValue = JsonString(v)

    private fun n(v: Double): JsonValue = JsonNumber(v)

    private fun obj(vararg m: Pair<String, JsonValue?>): JsonObject =
        JsonObject(
            LinkedHashMap<String, JsonValue>().apply {
                m.forEach { (k, v) -> if (v != null) put(k, v) }
            }
        )

    private fun session(
        id: String,
        start: Double = t0,
        minutes: Double = 60.0,
        vararg more: Pair<String, JsonValue?>,
    ) =
        obj(
            "instanceId" to s(id),
            "title" to s("Lift"),
            "tag" to s("fitness.strength"),
            "linkedGoalIds" to JsonArray(listOf(s("g1"))),
            "linkedQuestRequirementIds" to JsonArray.EMPTY,
            "startEpoch" to n(start),
            "endEpoch" to n(start + minutes * 60),
            "tier" to n(3.0),
            "lifecycleState" to s("PLANNED"),
            "bwcId" to s("bwc-1"),
            "spacingKey" to s("g1:strength"),
            "source" to obj("id" to s("t1"), "title" to s("Lift")),
            *more,
        )

    private fun complete(
        ledger: JsonValue?,
        inst: JsonValue,
        minutes: Double = 60.0,
        now: Double = t0 + 3600,
    ) =
        CompletionLedger.record(
            obj(
                "ledger" to ledger,
                "playerId" to s("host"),
                "instance" to inst,
                "actualStartEpoch" to n(t0),
                "actualDurationMinutes" to n(minutes),
                "nowEpoch" to n(now),
            )
        )

    private fun member(v: JsonValue?, k: String) = (v as JsonObject)[k]

    @Test
    fun eventIdsAreThePagesAndNeverChangeForASession() {
        assertEquals(
            "ev_0u2hlfya15d2gasjdtg6m4",
            CompletionLedger.eventId(s("lm_3k9x0a7b2c1d"), s("i_abc"), JsonNull, null),
        )
        assertEquals(
            "ev_157x6ykqw2s1w3nmaa1jb7",
            CompletionLedger.eventId(s("lm_3k9x0a7b2c1d"), s("i_abc"), JsonNull, n(1.0)),
        )
        assertEquals(
            "ev_047cyw3guwb18cygblh849",
            CompletionLedger.eventId(s("lm_x"), JsonNull, s("host:a1"), null),
        )
        // a revision of 0 is no revision; the client id plays no part for a session
        assertEquals(
            CompletionLedger.eventId(s("p"), s("i"), s("c1"), n(0.0)),
            CompletionLedger.eventId(s("p"), s("i"), s("c2"), null),
        )
        assertEquals(
            CompletionLedger.eventId(s("p"), s("i"), null, n(2.0)),
            CompletionLedger.eventId(s("p"), s("i"), null, s("2")),
        )
    }

    @Test(expected = EngineRefusal::class)
    fun anAdHocCompletionNeedsAClientId() {
        CompletionLedger.eventId(s("p"), s(""), s(""), null)
    }

    @Test
    fun theLedgersPlayerIdWinsOverTheHosts() {
        val ledger = CompletionLedger.empty(s("lm_original"))
        val r = complete(ledger, session("i_1"))
        assertEquals(CompletionLedger.eventId(s("lm_original"), s("i_1"), null, null), r.eventId)
        assertEquals(s("lm_original"), member(r.event, "playerId"))
    }

    @Test
    fun aSecondCompletionIsRefusedAndChangesNothing() {
        val first = complete(null, session("i_1"))
        assertTrue(first.applied)
        val again = complete(first.ledger, session("i_1"))
        assertFalse(again.applied)
        assertEquals("DUPLICATE_EVENT_ID", again.reason)
        assertTrue(again.ledger === first.ledger)
        // a new revision is a new event, and is paid
        val redo = complete(first.ledger, session("i_1", t0, 60.0, "revision" to n(1.0)))
        assertTrue(redo.applied)
        assertEquals(2, (member(redo.ledger, "order") as JsonArray).items.size)
    }

    @Test
    fun aRevokedEventStaysAndItsIdStaysBurned() {
        val first = complete(null, session("i_1"))
        val (revoked, ok) = CompletionLedger.revoke(first.ledger, s(first.eventId), null, n(t0))
        assertTrue(ok)
        assertEquals(
            s("HOST_UNDO"),
            member(member(member(revoked, "revoked"), first.eventId), "reason"),
        )
        assertFalse(CompletionLedger.revoke(revoked, s(first.eventId), s("x"), n(t0)).second)
        assertFalse(complete(revoked, session("i_1")).applied)
        assertTrue(CompletionLedger.has(revoked, s(first.eventId)))
    }

    @Test
    fun everyFifthCompletionEarnsATokenUpToThree() {
        var ledger: JsonValue? = CompletionLedger.empty(s("p"))
        val earned = ArrayList<Boolean>()
        for (k in 0 until 15) {
            val r = complete(ledger, session("i_$k", t0 + k * 7200))
            ledger = r.ledger
            earned += r.tokenEarned
        }
        assertEquals(listOf(4, 9), earned.withIndex().filter { it.value }.map { it.index })
        assertEquals(n(3.0), member(ledger, "restTokens"))
        assertEquals(n(15.0), member(member(ledger, "streaks"), "g1:strength"))
    }

    @Test
    fun aPartialCompletionLeavesTheRestAsATask() {
        val r = complete(null, session("i_1", t0, 60.0), minutes = 20.0)
        assertEquals("PARTIALLY_COMPLETED", r.lifecycleState)
        val rest = r.residualTask as JsonObject
        assertEquals(n(45.0), rest["dOpt"])
        assertEquals(s("Lift (finish)"), rest["title"])
        assertEquals(s("i_1"), rest["parentInstanceId"])
        assertNull(member(member(r.ledger, "streaks"), "g1:strength"))
    }

    /** LG01: an archived id answers `has`, yet the same completion is applied again. */
    @Test
    fun anArchivedIdIsPaidAgain() {
        val id = CompletionLedger.eventId(s("p"), s("i_1"), null, null)
        val ledger =
            LedgerValues.put(
                CompletionLedger.empty(s("p")),
                "archivedIds",
                JsonArray(listOf(s(id))),
            )
        assertTrue(CompletionLedger.has(ledger, s(id)))
        assertTrue(complete(ledger, session("i_1")).applied)
    }

    @Test
    fun compactionKeepsIdsOnceTheLedgerIsLong() {
        val events = LinkedHashMap<String, JsonValue>()
        val order = ArrayList<JsonValue>()
        for (i in 0 until 2000) {
            events["e$i"] = obj("recordedAtEpoch" to n(if (i < 10) 0.0 else t0))
            order += s("e$i")
        }
        val ledger = obj("events" to JsonObject(events), "order" to JsonArray(order))
        val out = CompletionLedger.compact(ledger, n(t0))
        assertEquals(1990, (member(out, "order") as JsonArray).items.size)
        assertEquals(10, (member(out, "archivedIds") as JsonArray).items.size)
        assertTrue(CompletionLedger.has(out, s("e3")))
        val short = obj("events" to JsonObject.EMPTY, "order" to JsonArray(order.take(1999)))
        assertTrue(CompletionLedger.compact(short, n(t0)) === short)
    }

    @Test
    fun aMissPaysOncePerSessionAndPerDayATokenFirst() {
        val prefs = obj("consequenceMode" to s("MODERATE"))
        val ledger = CompletionLedger.empty(s("p"))
        val first = MissConsequences.apply(ledger, session("i_1"), prefs, n(t0), s("MISS"))
        assertEquals("EXCUSED_BY_TOKEN", first.state)
        assertEquals(n(0.0), member(first.ledger, "restTokens"))
        val again = MissConsequences.apply(first.ledger, session("i_1"), prefs, n(t0), s("MISS"))
        assertEquals("ALREADY_PROCESSED", again.note)
        val sameDay = MissConsequences.apply(first.ledger, session("i_2"), prefs, n(t0), s("MISS"))
        assertEquals("ONE_PER_DAY_LIMIT", sameDay.note)
        val third =
            MissConsequences.apply(
                sameDay.ledger,
                session("i_3", t0, 60.0, "bwcId" to s("bwc-2")),
                prefs,
                n(t0),
                s("MISS"),
            )
        assertTrue(third.regroupPrompt)
        assertEquals(s("STREAK_DECAY"), member(third.consequence, "type"))
        val lenient = MissConsequences.apply(ledger, session("i_1"), null, n(t0), s("MISS"))
        assertEquals("LENIENT_MODE", lenient.note)
    }

    @Test
    fun missesWaitForTheGrace() {
        val list =
            JsonArray(
                listOf(
                    session("i_1"),
                    session("i_2", t0, 60.0, "lifecycleState" to s("IN_PROGRESS")),
                )
            )
        assertTrue(MissConsequences.detect(list, n(t0 + 7200), null).isEmpty())
        assertEquals(1, MissConsequences.detect(list, n(t0 + 7201), n(0.0)).size)
        assertEquals(1, MissConsequences.detect(list, n(t0 + 3601), n(0.001)).size)
    }

    @Test
    fun transitionsAreTheTables() {
        val all = MissConsequences.TRANSITIONS.values.sumOf { it.size }
        assertEquals(20, all)
        assertTrue(MissConsequences.canTransition(s("MISSED_UNEXPLAINED"), s("COMPLETED_LATE")))
        assertFalse(MissConsequences.canTransition(s("COMPLETED_LATE"), s("PLANNED")))
        assertTrue(
            MissConsequences.canTransition(JsonArray(listOf(s("PLANNED"))), s("IN_PROGRESS"))
        )
    }

    @Test(expected = PageTypeError::class)
    fun anInheritedStateNameThrowsAsOnThePage() {
        MissConsequences.canTransition(s("constructor"), s("PLANNED"))
    }
}
