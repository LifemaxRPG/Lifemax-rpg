package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.save.sameStoredValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fake page replays the recorded golden cases through the transport as one long page session
 * (economy 880, quests 842, board 1011, device context 98: every case that carries a state), and
 * after every envelope native's in-memory copy must equal the page's state, key for key. The
 * sequences cross case boundaries, so each before-delta also carries everything that changed
 * between two actions, as on the phone.
 */
class ShadowReplayTest {
    private val fingerprint = "fp-test"

    @Test
    fun economyGoldenCasesKeepTheCopyEqual() {
        val cases = (FakeShadowPage.fixture("economy-golden.json") as JsonArray).items
        val fresh = FakeShadowPage.members(input(cases[0])["state"])
        val states = ArrayList<Pair<Map<String, JsonValue>, Map<String, JsonValue>>>()
        for (case in cases) {
            val inState = input(case)["state"] as? JsonObject ?: continue
            val before = fresh + inState.members
            val out = (case as JsonObject)["output"] as JsonObject
            val deleted =
                (out["deleted"] as? JsonArray)?.items?.map {
                    (it as com.lifemax.rpg.core.save.JsonString).value
                } ?: emptyList()
            val after = (before + FakeShadowPage.members(out["state"])) - deleted.toSet()
            states += before to after
        }
        assertTrue(states.size > 400)
        replay(states, "economy")
    }

    @Test
    fun questsAndBoardGoldenCasesKeepTheCopyEqual() {
        for (name in
            listOf(
                "quests-golden.json",
                "quests-board-golden.json",
                "device-context-golden.json",
            )) {
            val states = ArrayList<Pair<Map<String, JsonValue>, Map<String, JsonValue>>>()
            for (case in (FakeShadowPage.fixture(name) as JsonArray).items) {
                var now = FakeShadowPage.members(input(case)["state"])
                if (now.isEmpty()) continue
                val calls = ((case as JsonObject)["output"] as JsonObject)["calls"] as JsonArray
                for (call in calls.items) {
                    val changed = FakeShadowPage.members((call as JsonObject)["state"])
                    val next = now + changed
                    states += now to next
                    now = next
                }
            }
            assertTrue(name, states.size > 60)
            replay(states, name)
        }
    }

    @Test
    fun aRealSaveAsTheBaseThenEveryEstablishedRecordAppendedOneByOne() {
        val save =
            FakeShadowPage.members(
                FakeShadowPage.members(FakeShadowPage.fixture("saves/established.json"))["state"]
            )
        val all = (save["activities"] as JsonArray).items
        val start = save + ("activities" to JsonArray(all.take(all.size - 40)))
        val states = ArrayList<Pair<Map<String, JsonValue>, Map<String, JsonValue>>>()
        var now = start
        for (n in all.size - 39..all.size) {
            val next = now + ("activities" to JsonArray(all.take(n)))
            states += now to next
            now = next
        }
        replay(states, "log.record", base = start)
    }

    private fun replay(
        states: List<Pair<Map<String, JsonValue>, Map<String, JsonValue>>>,
        label: String,
        base: Map<String, JsonValue> = states.first().first,
    ) {
        val session = ShadowSessionTest.session(fingerprint)
        session.configure(ShadowSessionTest.configure(fingerprint), batterySaver = false)
        val page = FakeShadowPage()
        for (part in page.base(base)) assertTrue(session.base(part, 0).ok)
        assertSame(label, FakeShadowPage.shadow(base), session.mirrorSnapshot())
        for ((i, s) in states.withIndex()) {
            val text = page.envelope("save.other", s.first, s.second)
            assertTrue("$label #$i accepted", session.accept(text.length, false).accepted)
            session.observe(text, FakeShadowPage.T0)
            assertSame("$label #$i", FakeShadowPage.shadow(s.second), session.mirrorSnapshot())
        }
        assertEquals(states.size, session.ledger.applied)
        assertTrue(session.ledger.refused.isEmpty())
    }

    private fun input(case: JsonValue): Map<String, JsonValue> =
        FakeShadowPage.members((case as JsonObject)["input"])

    companion object {
        fun assertSame(
            label: String,
            expected: Map<String, JsonValue>,
            actual: Map<String, JsonValue>,
        ) {
            assertEquals("$label: keys", expected.keys, actual.keys)
            for ((k, v) in expected) assertTrue("$label: $k", v.sameStoredValue(actual.getValue(k)))
        }
    }
}
