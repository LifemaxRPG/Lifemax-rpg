package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The quest and Daily Hand kinds in the session (ECONOMY_SHADOW_PLAN WP6c): a page that lies about
 * the hand is caught at the field it lied about and never allowed; an envelope that cannot be
 * compared (interleaved, across midnight, a storage mix, a seq gap) is counted and leaves the copy
 * in step; the ring holds none of the quest names or notes the owner wrote; the heaviest composed
 * action on a large save stays well under the worker's slow limit.
 */
class ShadowHandSessionTest {
    private val built = HandGoldenActions.build()
    private val reroll =
        built.first { it.action.kind == "hand.reroll" && !it.threw && it.action.saves.isNotEmpty() }
    private val ensure =
        built.first {
            it.action.kind == "hand.ensure" &&
                !it.threw &&
                it.action.after["dailyDraw"] != it.action.before["dailyDraw"]
        }
    private val pack =
        built.first {
            it.action.kind == "quest.pack" && !it.threw && it.action.after != it.action.before
        }

    private fun run(vararg actions: FakeShadowPage.Action): ShadowSession {
        GoldenActions.CONTENT.setup = HandGoldenActions.SETUP
        val s = ShadowGoldenCompareTest.session()
        val page = FakeShadowPage()
        for (part in page.base(actions.first().before)) assertTrue(s.base(part, 0).ok)
        for (a in actions) {
            val text = page.send(a)
            assertTrue(s.accept(text.length, false).accepted)
            s.observe(text, FakeShadowPage.T0)
        }
        return s
    }

    private fun lastPaths(s: ShadowSession) = s.ledger.mismatches().last().paths

    private fun FakeShadowPage.Action.afterWith(key: String, value: JsonValue) =
        copy(after = after + (key to value))

    @Test
    fun theRealActionsMatch() {
        for (b in listOf(reroll, ensure, pack)) assertEquals(b.caseId, 1, run(b.action).ledger.same)
    }

    @Test
    fun aPageThatLiesAboutTheHandIsCaughtWhereItLied() {
        val draw = ensure.action.after["dailyDraw"] as JsonObject
        val cards = draw["cards"] as JsonArray
        val first = cards.items[0] as JsonObject
        val lies =
            listOf(
                ensure.action.afterWith(
                    "dailyDraw",
                    JsonObject(
                        draw.members +
                            ("cards" to
                                JsonArray(
                                    listOf(
                                        JsonObject(first.members + ("tpl" to JsonString("t999")))
                                    ) + cards.items.drop(1)
                                ))
                    ),
                ) to Regex("dailyDraw\\.cards\\[.*]\\.tpl"),
                ensure.action.afterWith(
                    "dailyDraw",
                    JsonObject(draw.members + ("nonce" to JsonNumber(7.0))),
                ) to Regex("dailyDraw\\.nonce"),
                ensure.action.afterWith("dhMig", JsonNumber(2.0)) to Regex("dhMig"),
                reroll.action.afterWith("sparks", JsonNumber(12345.0)) to Regex("sparks"),
                reroll.action.copy(toasts = listOf("Rerolled for 5 Sparks")) to Regex("toasts.*"),
                reroll.action.copy(saves = emptyList()) to Regex("saves\\.count"),
                pack.action.afterWith("classContent", JsonString("someone-else")) to
                    Regex("classContent"),
                pack.action.afterWith("quests", JsonArray.EMPTY) to Regex("quests.*"),
            )
        for ((lie, at) in lies) {
            val s = run(lie)
            assertEquals("$at: unexplained", 1, s.ledger.different)
            assertEquals("$at: never allowed", 0, s.ledger.allowed)
            assertTrue("$at in ${lastPaths(s)}", lastPaths(s).any(at::matches))
        }
    }

    @Test
    fun aHandActionThatCannotBeComparedIsCountedAndTheCopyStaysInStep() {
        val midnight = java.time.Instant.parse("2026-11-08T23:59:59.999Z").toEpochMilli()
        val a = reroll.action
        val skips =
            listOf(
                a.copy(flags = listOf(ShadowFlags.INTERLEAVED)) to "interleaved",
                a.copy(t0 = midnight, t1 = midnight + 2, zone = "UTC") to "midnight",
                a.copy(saves = listOf(true, false)) to "mixedStorage",
                a.copy(args = listOf(JsonObject.EMPTY, JsonObject.EMPTY)) to "input",
                a.copy(env = a.env - "plan" + ("plan" to JsonString("x"))) to "input",
                ensure.action.copy(env = emptyMap()) to "planWindow",
            )
        for ((action, why) in skips) {
            val next = a.copy(before = action.after, after = action.after, toasts = emptyList())
            val s = run(action, next)
            assertEquals(why, 1, s.ledger.kind(action.kind).skipped[why])
            assertEquals(
                "$why: the next action is applied on the page's state",
                2,
                s.ledger.applied,
            )
        }
        // a hand action the page threw out of is compared: native throws at the same place
        val threw = built.first { it.threw && it.action.kind == "hand.ensure" }
        assertEquals(1, run(threw.action).ledger.same)
        // an envelope after one the app never received asks for a base again
        GoldenActions.CONTENT.setup = HandGoldenActions.SETUP
        val s = ShadowGoldenCompareTest.session()
        val page = FakeShadowPage()
        for (part in page.base(a.before)) s.base(part, 0)
        page.send(a) // lost on the way
        val gap = page.send(a.copy(before = a.after, after = a.after))
        assertTrue(s.accept(gap.length, false).accepted)
        s.observe(gap, FakeShadowPage.T0)
        assertEquals(0, s.ledger.compared)
    }

    @Test
    fun theRingHoldsNoQuestNameOrNoteTheOwnerWrote() {
        val name = "Call Grandma Odile about the will"
        val note = "Felt dizzy again after the pills"
        val session =
            run(
                *built
                    .filter { it.action.kind == "quest.create" && !it.threw }
                    .take(SAMPLE)
                    .map { b ->
                        val q =
                            (b.action.after["quests"] as JsonArray).items.map {
                                val o = it as? JsonObject ?: return@map it
                                if ((o["kind"] as? JsonString)?.value == "custom")
                                    JsonObject(
                                        o.members +
                                            ("name" to JsonString(name)) +
                                            ("notes" to JsonString(note))
                                    )
                                else o
                            }
                        b.action.copy(after = b.action.after + ("quests" to JsonArray(q)))
                    }
                    .toTypedArray()
            )
        assertTrue("the lies are recorded", session.ledger.different > 0)
        val ring = session.ringText()
        for (word in listOf(name, note, "Grandma", "Odile", "dizzy", "Walk", "Read")) assertTrue(
            "$word is not in the ring",
            word !in ring,
        )
    }

    @Test
    fun aLargeSaveStaysInsideTheBudgetWithTheHandComposed() {
        val log = GoldenActions.build().first { it.caseId == "save/compose-log-pays-two-cards" }
        // the case's own Log, as the page wrote it, copied into earlier days at no XP (so the
        // page's recorded totals stay right) and read strictly, as a real ledger is
        val logged =
            (log.action.after["activities"] as JsonArray).items.first {
                (it as JsonObject)["type"] == JsonString("strength")
            } as JsonObject
        val records =
            (0 until LARGE).map { i ->
                JsonObject(
                    logged.members +
                        listOf(
                            "id" to JsonString("big-$i"),
                            "xp" to JsonNumber(0.0),
                            "xpSplit" to JsonArray.EMPTY,
                            "date" to JsonString("2026-0${1 + i % 9}-1${i % 9}T09:00:00.000Z"),
                        )
                )
            }
        fun big(state: Map<String, JsonValue>): Map<String, JsonValue> {
            val own = (state["activities"] as? JsonArray)?.items.orEmpty()
            return state + ("activities" to JsonArray(records + own))
        }
        GoldenActions.CONTENT.setup = log.setup
        val action = log.action.copy(before = big(log.action.before), after = big(log.action.after))
        val costs = ArrayList<Long>()
        repeat(ROUNDS) {
            val s = ShadowGoldenCompareTest.session()
            val page = FakeShadowPage()
            for (part in page.base(action.before)) s.base(part, 0)
            val text = page.send(action)
            val t = System.nanoTime()
            s.observe(text, FakeShadowPage.T0)
            costs += (System.nanoTime() - t) / NANOS_PER_MS
            assertEquals("round $it compared", 1, s.ledger.compared)
            assertEquals(
                "round $it: the page's composition, on a large save",
                0,
                s.ledger.different,
            )
        }
        val sorted = costs.sorted()
        val p95 = sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.size - 1)]
        println("hand composed on $LARGE records: p50 ${sorted[sorted.size / 2]} ms, p95 $p95 ms")
        // the heaviest composed action: a Log whose save pays two cards, each card's toggle saving
        // again (three rebuilds of the whole ledger, two checks scanning it per card); the common
        // action that pays no card is ShadowPerfTest's (p95 under 200 ms on 11,000 records)
        assertTrue("p95 $p95 ms under the self-disable limit of 2 s", p95 < SLOW_LIMIT_MS)
    }

    private companion object {
        const val SAMPLE = 20
        const val LARGE = 10_000
        const val ROUNDS = 20
        const val NANOS_PER_MS = 1_000_000L
        /** The worker's slow limit (ECONOMY_SHADOW_PLAN section 4): p95 over 2 s disables. */
        const val SLOW_LIMIT_MS = 2_000L
    }
}
