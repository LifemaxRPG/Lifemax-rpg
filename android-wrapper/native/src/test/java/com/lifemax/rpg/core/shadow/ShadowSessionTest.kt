package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.save.JsonArray
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
 * The switch, the fingerprint, Battery Saver, the touch map and the self-disable rules
 * (ECONOMY_SHADOW_PLAN 2.3, 2.4, 4; WP6a).
 */
class ShadowSessionTest {
    private val start: Map<String, JsonValue> =
        mapOf(
            "sparks" to num(10),
            "attrXP" to obj("physique" to num(0), "endurance" to num(5)),
            "skillXP" to obj("Guitar practice" to num(3)),
            "activities" to JsonArray(listOf(obj("id" to JsonString("a1"), "xp" to num(5)))),
        )

    private fun on(): Pair<ShadowSession, FakeShadowPage> {
        val s = session()
        assertEquals(ShadowState.ON, s.configure(configure(FP), false).state)
        val page = FakeShadowPage()
        page.base(start).forEach { assertTrue(s.base(it, 0).ok) }
        return s to page
    }

    @Test
    fun offUntilTheSwitchSaysOnAndOffAgainWhenItSaysOff() {
        val s = session()
        assertEquals(ShadowState.OFF, s.handshake().state)
        assertEquals(
            listOf(ShadowLimits.CAPABILITY, ShadowLimits.CAPABILITY_QUESTS),
            s.handshake().capabilities,
        )
        assertEquals(FP, s.handshake().fingerprint)
        assertEquals(ShadowAccept(false, false, "off"), s.accept(10, false))
        assertEquals(ShadowState.ON, s.configure(configure(FP), false).state)
        assertEquals("noBase", s.accept(10, false).reason)
        assertEquals(ShadowState.OFF, s.configure(configure(FP, enabled = false), false).state)
        assertFalse(s.base(FakeShadowPage().base(start).first(), 0).ok)
    }

    @Test
    fun aPageWhoseRulesDifferIsStaleAndComparesNothing() {
        val s = session()
        val a = s.configure(configure("an-ota-page"), false)
        assertEquals(ShadowState.STALE, a.state)
        assertEquals("fingerprint", a.reason)
        assertEquals("stale", s.accept(10, false).reason)
    }

    @Test
    fun batterySaverPausesAndCountsTheDrop() {
        val s = session()
        assertEquals(ShadowState.PAUSED, s.configure(configure(FP), batterySaver = true).state)
        val (t, _) = on()
        val a = t.accept(10, batterySaver = true)
        assertEquals(ShadowAccept(false, true, "paused"), a)
        t.pausedForBattery()
        assertEquals(ShadowState.PAUSED, t.ledger.state)
        assertEquals(1, t.ledger.kinds.getValue(ShadowSession.ANY).skipped["battery"])
        // back on once the page configures again with Battery Saver off
        assertEquals(ShadowState.ON, t.configure(configure(FP), false).state)
    }

    @Test
    fun eachActionRecordsTheFieldsItTouchedWithOwnerWordsHashed() {
        val (s, page) = on()
        val after =
            start +
                mapOf(
                    "sparks" to num(25),
                    "skillXP" to obj("Guitar practice" to num(9)),
                    "activities" to
                        JsonArray(
                            (start["activities"] as JsonArray).items +
                                obj("id" to JsonString("a2"), "xp" to num(15))
                        ),
                )
        s.observe(page.envelope("log.record", start, after), T)
        val e = s.ledger.entries().single()
        assertEquals("log.record", e.kind)
        assertEquals(3, e.paths.size)
        assertTrue(e.paths.containsAll(listOf("activities[+1]", "sparks")))
        assertTrue(
            e.paths.single { it.startsWith("skillXP.") }.matches(Regex("skillXP\\.\\*#[0-9a-f]{6}"))
        )
        assertTrue(e.paths.none { "Guitar" in it })
        // the same kind and fields again: one entry, counted twice; times kept to the minute
        val again = after + ("sparks" to num(40)) + ("skillXP" to obj("Guitar practice" to num(12)))
        val more =
            again +
                ("activities" to
                    JsonArray(
                        (after["activities"] as JsonArray).items + obj("id" to JsonString("a3"))
                    ))
        s.observe(page.envelope("log.record", after, more, t0 = T + 61_000), T + 61_000)
        assertEquals(1, s.ledger.entries().size)
        assertEquals(2, s.ledger.entries().single().count)
        assertEquals(0L, s.ledger.entries().single().last % ShadowLimits.MINUTE_MS)
        assertEquals(2, s.summary().applied)
        assertEquals("log.record", s.summary().last?.kind)
    }

    @Test
    fun editsBetweenActionsAreRecordedApartAndUnknownOrInterleavedKindsAreCounted() {
        val (s, page) = on()
        val edited = start + ("attrXP" to obj("physique" to num(4), "endurance" to num(5)))
        s.observe(page.envelope("quest.toggle", edited, edited), T)
        assertEquals(
            listOf("attrXP.physique"),
            s.ledger.entries().single { it.kind == ShadowKeys.BETWEEN }.paths,
        )
        s.observe(page.envelope("cat.dance", edited, edited + ("sparks" to num(1))), T)
        assertEquals(1, s.ledger.kinds.getValue("cat.dance").skipped["unknownKind"])
        s.observe(page.envelope("health.sync", edited, edited, flags = listOf("interleaved")), T)
        assertEquals(1, s.ledger.kinds.getValue("health.sync").skipped["interleaved"])
        assertEquals(3, s.ledger.applied)
    }

    @Test
    fun fiveInternalErrorsInADayDisableUntilTheSwitchIsTurnedOffAndOn() {
        val (s, _) = on()
        repeat(4) { s.fault(IllegalStateException("x"), T + it) }
        assertEquals(ShadowState.ON, s.ledger.state)
        s.fault(IllegalStateException("x"), T + 5)
        assertEquals(ShadowState.DISABLED, s.ledger.state)
        assertEquals("errors", s.ledger.reason)
        assertEquals(5, s.ledger.errors["IllegalStateException"])
        assertEquals("disabled", s.accept(10, false).reason)
        assertEquals(ShadowState.DISABLED, s.configure(configure(FP), false).state)
        s.configure(configure(FP, enabled = false), false)
        assertEquals(ShadowState.ON, s.configure(configure(FP), false).state)
    }

    @Test
    fun errorsSpreadOverMoreThanADayDoNotDisable() {
        val (s, _) = on()
        repeat(6) { s.fault(RuntimeException(), T + it * ShadowLimits.ERROR_WINDOW_MS / 4) }
        assertEquals(ShadowState.ON, s.ledger.state)
    }

    @Test
    fun aNewAppVersionRetriesACheckThatStoppedItself() {
        val (s, _) = on()
        repeat(5) { s.fault(RuntimeException(), T) }
        val next = ShadowSession(FP, s.ledger, versionCode = 2, nativeApi = 106)
        assertEquals(ShadowState.OFF, next.ledger.state)
        assertNull(next.ledger.reason)
    }

    @Test
    fun tooManyResyncsAnHourDisable() {
        val s = session()
        s.configure(configure(FP), false)
        val page = FakeShadowPage()
        repeat(ShadowLimits.RESYNCS_PER_HOUR + 2) { i ->
            page.base(start).forEach { s.base(it, T + i) }
        }
        assertEquals(ShadowState.DISABLED, s.ledger.state)
        assertEquals("resyncLoop", s.ledger.reason)
    }

    @Test
    fun aSlowWorkerDisables() {
        val (s, _) = on()
        repeat(ShadowLimits.SLOW_WINDOW) { s.timed(if (it < 48) 10 else 3_000) }
        assertEquals(
            ShadowState.ON,
            s.ledger.state,
        ) // 2 slow of 50: the 95th percentile is still fast
        repeat(ShadowLimits.SLOW_WINDOW) { s.timed(3_000) }
        assertEquals("slow", s.ledger.reason)
    }

    @Test
    fun aBaseTooLargeToHoldStopsWithTooLarge() {
        val s = session()
        s.configure(configure(FP), false)
        val big = mapOf("dayReflections" to JsonString("x".repeat(ShadowLimits.MAX_HELD_CHARS)))
        val parts = FakeShadowPage().base(big)
        assertTrue(parts.size <= ShadowLimits.MAX_BASE_PARTS)
        parts.forEach { s.base(it, T) }
        assertEquals(ShadowState.DISABLED, s.ledger.state)
        assertEquals("tooLarge", s.ledger.reason)
        assertTrue(s.mirrorSnapshot().isEmpty())
    }

    @Test
    fun aMalformedEnvelopeIsCountedAndAsksForABase() {
        val (s, page) = on()
        s.observe("{\"v\":1}", T)
        assertEquals(1, s.ledger.refused["missingKey"])
        assertEquals("noBase", s.accept(10, false).reason)
        page.base(start).forEach { s.base(it, T) }
        assertTrue(s.accept(10, false).accepted)
    }

    @Test
    fun clearEmptiesTheCountsButKeepsTheSalt() {
        val (s, page) = on()
        s.observe(page.envelope("log.record", start, start + ("sparks" to num(1))), T)
        val salt = s.ledger.salt.copyOf()
        s.clear()
        assertTrue(s.ledger.entries().isEmpty())
        assertEquals(0, s.ledger.seen)
        assertTrue(salt.contentEquals(s.ledger.salt))
    }

    companion object {
        const val FP = "fp-test"
        const val T = 1_793_700_000_000L

        fun session(fingerprint: String = FP) =
            ShadowSession(
                fingerprint,
                ShadowLedger(T, ByteArray(ShadowLimits.SALT_BYTES) { it.toByte() }),
                1,
                106,
            )

        fun configure(fingerprint: String, enabled: Boolean = true, sid: String = "p-test") =
            "{\"v\":1,\"enabled\":$enabled,\"sid\":\"$sid\",\"page\":{\"build\":\"b1\",\"schema\":1,\"fingerprint\":\"$fingerprint\",\"faults\":0}}"

        fun num(n: Number) = JsonNumber(n.toDouble())

        fun obj(vararg m: Pair<String, JsonValue>) = JsonObject(linkedMapOf(*m))
    }
}
