package com.lifemax.rpg.core.lifedirector.timer

import com.lifemax.rpg.core.lifedirector.host.HostReplay
import com.lifemax.rpg.core.lifedirector.host.HostState
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wake calculator against the page (TM-80 to TM-84): each `wake` case of the timer golden ends
 * with the page's own check run a millisecond before an instant and at it (the timer's check, the
 * misses, the rollover). The page changed nothing at the first and something at the second; the
 * calculator, given the state before them and the clock then, must name exactly that second instant
 * for the case's kind.
 */
class WakeGoldenTest {
    @Test
    fun theCalculatorNamesTheInstantThePageActsAt() {
        val wrong = ArrayList<String>()
        var probed = 0
        HostReplay.forEachCase(TimerGoldenTest.FILE) { c ->
            if (c.family != "wake") return@forEachCase
            val states = ArrayList<HostState>()
            HostReplay.replay(c) { _, s -> states += s }
                ?.let {
                    wrong += it
                    return@forEachCase
                }
            val n = c.out.size
            val before = states[n - 3]
            val nowMs = at(c.out[n - 3])
            val miss = at(c.out[n - 2])
            val hit = at(c.out[n - 1])
            val kind = KINDS.getValue(c.id.substringAfter('/').substringBefore('@'))
            val plan = Wakes.of(before.st, nowMs, "UTC")
            val wake = plan.of(kind)
            probed++
            when {
                changed(c.out[n - 2]) -> wrong += "${c.id}: the page acted a millisecond early"
                !changed(c.out[n - 1]) -> wrong += "${c.id}: the page did not act at the instant"
                hit - miss != 1.0 -> wrong += "${c.id}: the probes are not a millisecond apart"
                wake == null -> wrong += "${c.id}: no $kind wake (${plan.wakes})"
                wake.atMs != hit ->
                    wrong += "${c.id}: $kind at ${wake.atMs}, the page acted at $hit"
            }
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
        assertEquals(40, probed)
    }

    private fun at(o: JsonObject) = (o["at"] as JsonNumber).value

    /** Whether a step changed `ST` or saved. */
    private fun changed(o: JsonObject): Boolean {
        val set = (o["state"] as JsonObject)["set"] as JsonObject
        val calls = (o["calls"] as JsonArray).items
        return set.members.keys.any { it != "updatedEpoch" } ||
            calls.any { (it as JsonObject)["fn"] == JsonString("store.save") } ||
            o["reply"] == com.lifemax.rpg.core.save.JsonBool.TRUE
    }

    private companion object {
        val KINDS: Map<String, WakeKind> =
            mapOf(
                "timer" to WakeKind.TIMER_END,
                "miss" to WakeKind.MISS,
                "week" to WakeKind.WEEK_ROLLOVER,
                "horizon" to WakeKind.HORIZON_LOW,
                "light" to WakeKind.LIGHT_END,
            )
    }
}
