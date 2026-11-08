package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sequence, duplicates, gaps, epochs, sessions, base parts and size caps of the in-memory copy
 * (ECONOMY_SHADOW_PLAN 2.2, 2.4).
 */
class ShadowMirrorTest {
    private val redactor = ShadowRedactor(ByteArray(ShadowLimits.SALT_BYTES))
    private val s0: Map<String, JsonValue> =
        mapOf("sparks" to num(1), "quests" to JsonArray(listOf(obj("id" to JsonString("q1")))))
    private val s1 = s0 + ("sparks" to num(2))
    private val s2 =
        s1 +
            ("quests" to
                JsonArray((s1["quests"] as JsonArray).items + obj("id" to JsonString("q2"))))

    private fun ready(page: FakeShadowPage = FakeShadowPage()): Pair<ShadowMirror, FakeShadowPage> {
        val m = ShadowMirror(redactor)
        page.base(s0).forEach { m.base(part(it)) }
        assertTrue(m.ready)
        return m to page
    }

    @Test
    fun envelopesApplyInOrderAndARepeatIsIgnored() {
        val (m, page) = ready()
        val e1 = page.envelope("save.other", s0, s1)
        assertTrue(m.apply(env(e1)) is MirrorStep.Applied)
        assertEquals(MirrorStep.Duplicate, m.apply(env(e1)))
        assertTrue(m.apply(env(page.envelope("save.other", s1, s2))) is MirrorStep.Applied)
        ShadowReplayTest.assertSame("after two", s2, m.snapshot())
        assertEquals(3, m.seq)
    }

    @Test
    fun aGapAnotherSessionOrAnotherEpochDiscardsTheCopy() {
        for (case in listOf("gap", "session", "epoch", "noBase")) {
            val (m, page) = ready()
            val text =
                when (case) {
                    "gap" ->
                        page
                            .envelope("save.other", s0, s1)
                            .also { page.envelope("x", s1, s2) }
                            .let { page.envelope("save.other", s1, s2) }
                    "session" ->
                        FakeShadowPage("p-other")
                            .also { it.base(s0) }
                            .envelope("save.other", s0, s1)
                    "epoch" ->
                        FakeShadowPage(epoch = 1)
                            .also { it.base(s0) }
                            .envelope("save.other", s0, s1)
                    else -> page.envelope("save.other", s0, s1).also { m.discard() }
                }
            assertEquals(case, MirrorStep.Resync(case), m.apply(env(text)))
            assertFalse(m.ready)
            assertTrue(m.snapshot().isEmpty())
        }
    }

    @Test
    fun aBaseComesInOrderedPartsAndAnythingElseIsRefused() {
        val big = mapOf("dayReflections" to JsonString("y".repeat(500_000)))
        val parts = FakeShadowPage().base(big)
        assertTrue(parts.size > 2)
        val m = ShadowMirror(redactor)
        assertEquals(BaseStep.Pending, m.base(part(parts[0])))
        assertEquals(BaseStep.Refused(ShadowRefusal.BAD_DELTA), m.base(part(parts[2])))
        assertFalse(m.ready)
        parts.dropLast(1).forEach { assertEquals(BaseStep.Pending, m.base(part(it))) }
        assertTrue(m.base(part(parts.last())) is BaseStep.Ready)
        assertEquals(big, m.snapshot())
        // the same base again changes nothing; an older one is refused
        assertEquals(BaseStep.Duplicate, m.base(part(parts.last())))
    }

    @Test
    fun aBaseOverTheHeldLimitIsRefusedAsTooLargeAndNothingIsHeld() {
        val parts =
            FakeShadowPage()
                .base(
                    mapOf("dayReflections" to JsonString("z".repeat(ShadowLimits.MAX_HELD_CHARS)))
                )
        val m = ShadowMirror(redactor)
        val steps = parts.map { m.base(part(it)) }
        assertEquals(
            BaseStep.Refused(ShadowRefusal.TOO_LARGE),
            steps.last { it !is BaseStep.Pending },
        )
        assertFalse(m.ready)
    }

    @Test
    fun anEightMegabyteBaseInThirtyTwoPartsIsTheTransportLimit() {
        val tooMany =
            FakeShadowPage()
                .base(mapOf("dayReflections" to JsonString("w".repeat(9 * 1024 * 1024))))
        assertTrue(tooMany.size > ShadowLimits.MAX_BASE_PARTS)
        val d = ShadowEnvelopeJson.basePart(tooMany.first())
        assertEquals(ShadowRefusal.TOO_LARGE, (d as ShadowDecode.Refused).reason)
    }

    @Test
    fun aRecordDeltaThatLeavesAHoleIsRefusedAndDiscards() {
        val (m, page) = ready()
        val holed =
            page
                .envelope("save.other", s0, s1)
                .replace("\"rec\":{}}", "\"rec\":{\"quests\":{\"len\":3,\"at\":[[2,{}]]}}}")
        assertEquals(MirrorStep.Refused(ShadowRefusal.BAD_DELTA), m.apply(env(holed)))
        assertFalse(m.ready)
    }

    @Test
    fun touchedPathsNameRecordsByPositionAndShrinking() {
        val (m, page) = ready()
        val step = m.apply(env(page.envelope("quest.create", s0, s2))) as MirrorStep.Applied
        assertEquals(listOf("quests[+1]", "sparks"), step.touched)
        val shrunk =
            s2 + ("quests" to JsonArray(listOf(obj("id" to JsonString("q1"), "done" to num(1)))))
        val again = m.apply(env(page.envelope("quest.toggle", s2, shrunk))) as MirrorStep.Applied
        assertEquals(listOf("quests[-]", "quests[].done"), again.touched)
    }

    private fun part(text: String) = (ShadowEnvelopeJson.basePart(text) as ShadowDecode.Ok).value

    private fun env(text: String) = (ShadowEnvelopeJson.envelope(text) as ShadowDecode.Ok).value

    private fun num(n: Number) = JsonNumber(n.toDouble())

    private fun obj(vararg m: Pair<String, JsonValue>) = JsonObject(linkedMapOf(*m))
}
