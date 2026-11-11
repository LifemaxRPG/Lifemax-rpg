package com.lifemax.rpg.core.directorshadow

import com.lifemax.rpg.core.shadow.ShadowLimits
import com.lifemax.rpg.core.shadow.ShadowState
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** SH-2, SH-4, SH-7, SH-8, SH-20 to SH-29: the switch, the copy's order and the self-disable rules. */
class DirectorShadowSessionTest {
    private val t = DirectorShadowStream.T
    private fun configure(enabled: Boolean = true, fingerprint: String = DirectorShadowLimits.FINGERPRINT, sid: String = "d-test") =
        """{"v":1,"scope":"director","enabled":$enabled,"sid":"$sid","page":{"build":"b1","fingerprint":"$fingerprint","faults":0}}"""
    private fun base(seq: Long, text: String, part: Int = 0, parts: Int = 1, sid: String = "d-test") =
        """{"v":1,"scope":"director","sid":"$sid","seq":$seq,"part":$part,"parts":$parts,"text":${com.lifemax.rpg.core.save.JsonWriter.write(com.lifemax.rpg.core.save.JsonString(text))}}"""
    private val ops = """{"ops":[["o",[],["schemaVersion","playerId","availability","goals"]],["t",["schemaVersion"],"1"],["t",["playerId"],"\"p\""],["t",["availability"],"{\"timezone\":\"UTC\",\"rules\":[]}"],["t",["goals"],"[]"]]}"""
    private val stateText = """{"schemaVersion":1,"playerId":"p","availability":{"timezone":"UTC","rules":[]},"goals":[]}"""
    private fun save(seq: Long, base: Long, ops: String = "[]", bytes: Int = stateText.length) =
        """{"v":1,"scope":"director","sid":"d-test","seq":$seq,"base":$base,"kind":"director.save","t0":1,"t1":2,"zone":"UTC","ops":$ops,"bytes":$bytes,"cost":{"ms":1},"flags":[]}"""
    private fun plan(seq: Long, offset: Int = 0) =
        """{"v":1,"scope":"director","sid":"d-test","seq":$seq,"base":1,"kind":"director.plan","t0":1,"t1":2,"zone":"UTC","ops":[],"engine":{"planZone":"UTC","planOffset":$offset,"input":{"currentTimeEpoch":1793000000},"output":{}},"cost":{"ms":1},"flags":[]}"""

    private fun on(s: DirectorShadowSession = DirectorShadowStream.session()): DirectorShadowSession {
        assertTrue(s.configure(configure(), false).ok)
        assertTrue(s.base(base(1, ops), t).ok)
        return s
    }

    @Test
    fun offUntilTheOwnerTurnsItOn() {
        val s = DirectorShadowStream.session()
        assertEquals("off", s.accept(10, false).reason)
        s.configure(configure(enabled = false), false)
        assertEquals(ShadowState.OFF, s.ledger.state)
        assertFalse(s.base(base(1, ops), t).ok)
    }

    @Test
    fun anotherPageIsStale() {
        val s = DirectorShadowStream.session()
        s.configure(configure(fingerprint = "2.0.0/1"), false)
        assertEquals(ShadowState.STALE, s.ledger.state)
        assertEquals("stale", s.accept(10, false).reason)
    }

    @Test
    fun batterySaverPauses() {
        val s = DirectorShadowStream.session()
        s.configure(configure(), true)
        assertEquals(ShadowState.PAUSED, s.ledger.state)
        val r = on().accept(10, true)
        assertTrue(!r.accepted && r.resync && r.reason == "paused")
    }

    @Test
    fun withoutACopyThePageIsAskedForABaseUnlessOneIsOnItsWay() {
        val s = DirectorShadowStream.session()
        s.configure(configure(), false)
        val r = s.accept(10, false)
        assertTrue(!r.accepted && r.resync && r.reason == "noBase")
        assertTrue(s.accept(10, false, basePending = true).accepted)
        s.base(base(1, ops), t)
        assertTrue(s.accept(10, false).accepted)
    }

    @Test
    fun aSaveOnTheCopyIsCheckedAndOneOnAnotherIsAResync() {
        val s = on()
        s.observe(save(2, 1), t)
        assertEquals(1, s.ledger.kinds[DirectorShadowJson.SAVE]!!.same)
        s.observe(save(4, 3), t)
        assertEquals(mapOf("resync" to 1), s.ledger.kinds[DirectorShadowJson.SAVE]!!.skipped)
        assertTrue(!s.accept(10, false).accepted)
    }

    @Test
    fun aRepeatIsIgnored() {
        val s = on()
        s.observe(save(2, 1), t)
        s.observe(save(2, 1), t)
        assertEquals(1, s.ledger.kinds[DirectorShadowJson.SAVE]!!.compared)
        assertEquals(1, s.ledger.info["duplicate"])
    }

    @Test
    fun aLengthOtherThanTheStoresIsACaptureSkip() {
        val s = on()
        s.observe(save(2, 1, bytes = stateText.length + 1), t)
        assertEquals(mapOf("capture" to 1), s.ledger.kinds[DirectorShadowJson.SAVE]!!.skipped)
    }

    @Test
    fun aChangeAtAPlaceTheCopyLacksIsRefused() {
        val s = on()
        s.observe(save(2, 1, ops = "[[\"t\",[\"nope\"],\"1\"]]"), t)
        assertEquals(1, s.ledger.refused["badDelta"])
    }

    @Test
    fun fiveErrorsInADayStopTheCheckAndANewAppTriesAgain() {
        val s = on(DirectorShadowStream.session { r -> DirectorChecks(r, zoneOffset = { _, _ -> error("boom") }) })
        for (i in 0 until 5) s.observe(plan(2L + i), t + i)
        assertEquals(ShadowState.DISABLED, s.ledger.state)
        assertEquals("errors", s.ledger.reason)
        s.configure(configure(), false)
        assertEquals(ShadowState.DISABLED, s.ledger.state)
        val again = DirectorShadowSession(s.ledger, 2, 135)
        assertEquals(ShadowState.OFF, again.ledger.state)
    }

    @Test
    fun aSlowWorkerStopsTheCheck() {
        val s = on()
        repeat(ShadowLimits.SLOW_WINDOW) { s.timed(ShadowLimits.SLOW_P95_MS + 1, t) }
        assertEquals("slow", s.ledger.reason)
    }

    @Test
    fun aSlowPlanMakesNativeSamplePlans() {
        val s = on(DirectorShadowStream.session { r -> DirectorChecks(r, zoneOffset = { _, _ -> Thread.sleep(DirectorShadowLimits.ENGINE_SLOW_MS + 50); 0 }) })
        for (i in 0 until 4) s.observe(plan(2L + i), t)
        val c = s.ledger.kinds[DirectorShadowJson.PLAN]!!
        assertEquals(mapOf("sampled" to DirectorShadowLimits.ENGINE_SAMPLE - 1), c.skipped)
        assertEquals(1, c.compared)
    }

    @Test
    fun aStateTooLargeStopsTheCheck() {
        val big = "\"" + "x".repeat(DirectorShadowLimits.MAX_STATE_CHARS) + "\""
        val text = "{\"ops\":[[\"o\",[],[\"a\"]],[\"t\",[\"a\"],${com.lifemax.rpg.core.save.JsonWriter.write(com.lifemax.rpg.core.save.JsonString(big))}]]}"
        val s = DirectorShadowStream.session()
        s.configure(configure(), false)
        val size = DirectorShadowLimits.MAX_BASE_PART_CHARS - 1000
        val parts = text.chunked(size)
        parts.forEachIndexed { i, p -> s.base(base(1, p, i, parts.size), t) }
        assertEquals("tooLarge", s.ledger.reason)
    }

    @Test
    fun theReportAndSummaryAreCountsOnly() {
        val s = on()
        s.observe(save(2, 1), t)
        val sum = s.summary()
        assertEquals(1, sum.compared)
        assertEquals(1, sum.same)
        val report = s.report(t, ZoneOffset.UTC)
        assertTrue(report, report.startsWith("LifeMax Life Director check v1") && report.contains("director.save 1/1/0/0"))
        s.clear()
        assertEquals(0, s.summary().compared)
        assertEquals(ShadowState.ON, s.ledger.state)
    }
}
