package com.lifemax.rpg.feature.pet.art

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The page-backed [CoatSource] against a fake bridge: async answers, one request per sheet. */
class PageCoatSourceTest {
    private val calico = CoatFixtures.coats.getValue("calico")
    private val point = CoatFixtures.coats.getValue("point")
    private var now = 0L
    private val bridge = FakeCoatBridge()
    private var changes = 0
    private val source =
        PageCoatSource { now }
            .also {
                it.bind(bridge.requester)
                it.onChange { changes++ }
            }

    private fun answer(id: String, label: String, kind: String) =
        source.onReply(CoatFixtures.reply(label, kind, id))

    @Test
    fun anUnansweredAskIsNullAndSendsExactlyOneRequest() {
        assertNull(source.sheet(calico, SheetKind.Adult))
        assertNull(source.sheet(calico, SheetKind.Adult))
        assertNull(source.palette(calico))
        assertEquals(1, bridge.sent.size)
        val s = bridge.last()
        assertEquals(calico, s.coat)
        assertEquals("adult", s.kind.key)
        assertEquals(0, changes)
    }

    @Test
    fun theAnswerIsKeptAndAnAskIsThenALookup() {
        source.sheet(calico, SheetKind.Adult)
        assertTrue(answer(bridge.last().id, "calico", "adult"))
        assertEquals(1, changes)
        val bytes = source.sheet(calico, SheetKind.Adult)
        assertNotNull(bytes)
        assertArrayEquals(bytes, source.sheet(calico, SheetKind.Adult))
        assertNotNull(source.palette(calico))
        assertEquals(1, bridge.sent.size)
        assertFalse(source.isRefused(calico, SheetKind.Adult))
    }

    @Test
    fun eachKindAndEachCoatIsItsOwnRequest() {
        source.sheet(calico, SheetKind.Adult)
        source.sheet(calico, SheetKind.Depth)
        source.sheet(calico, SheetKind.Stage(1))
        source.sheet(point, SheetKind.Adult)
        assertEquals(listOf("adult", "depth", "st1", "adult"), bridge.sent.map { it.kind.key })
        assertEquals(listOf(calico, calico, calico, point), bridge.sent.map { it.coat })
        assertEquals(4, source.outstanding)
    }

    @Test
    fun aRefusalIsFinalAndNeverAskedAgain() {
        source.sheet(calico, SheetKind.Adult)
        val refusal =
            JSONObject()
                .put("command", "coat")
                .put("requestId", bridge.last().id)
                .put("ok", false)
                .put("message", "That coat is not one this page holds.")
        assertTrue(source.onReply(refusal))
        assertEquals(1, changes)
        assertTrue(source.isRefused(calico, SheetKind.Adult))
        now += 10 * PageCoatSource.TIMEOUT_MS
        repeat(3) { assertNull(source.sheet(calico, SheetKind.Adult)) }
        assertEquals(1, bridge.sent.size)
        // another kind of the same coat is still its own question
        source.sheet(calico, SheetKind.Depth)
        assertEquals(2, bridge.sent.size)
    }

    @Test
    fun aMalformedReplyIsARefusalToo() {
        val bad =
            listOf<(JSONObject) -> JSONObject>(
                { it.put("png", "!!not base64!!") },
                { it.put("png", "") },
                { it.put("coat", point) },
                { it.put("kind", "depth") },
                { it.put("extra", 1) },
                { it.put("palette", JSONObject()) },
                { it.put("palette", JSONObject().put("0300", "ffffff")) },
                { it.put("palette", JSONObject().put("10", "#1a101c")) },
                { it.put("ok", "yes") },
            )
        for ((i, edit) in bad.withIndex()) {
            val s = PageCoatSource { now }.also { it.bind(bridge.requester) }
            s.sheet(calico, SheetKind.Adult)
            val reply = edit(CoatFixtures.reply("calico", "adult", bridge.last().id))
            assertTrue("case $i", s.onReply(reply))
            assertNull("case $i", s.sheet(calico, SheetKind.Adult))
            assertTrue("case $i", s.isRefused(calico, SheetKind.Adult))
        }
    }

    @Test
    fun aReplyNobodyWaitsForIsDroppedWithoutEffect() {
        source.sheet(calico, SheetKind.Adult)
        assertTrue(answer("r99", "calico", "adult"))
        assertTrue(answer("", "calico", "adult"))
        assertNull(source.sheet(calico, SheetKind.Adult))
        assertEquals(0, changes)
        assertFalse(source.isRefused(calico, SheetKind.Adult))
        assertEquals(1, bridge.sent.size)
    }

    @Test
    fun aDuplicateReplyChangesNothing() {
        source.sheet(calico, SheetKind.Adult)
        val id = bridge.last().id
        answer(id, "calico", "adult")
        val first = source.sheet(calico, SheetKind.Adult)
        answer(id, "point", "adult") // the same id again, carrying other bytes: ignored
        assertArrayEquals(first, source.sheet(calico, SheetKind.Adult))
        assertEquals(1, changes)
    }

    @Test
    fun aReplyForTheWrongSheetIsRefusedNotTaken() {
        source.sheet(calico, SheetKind.Adult)
        answer(bridge.last().id, "point", "adult")
        assertNull(source.sheet(calico, SheetKind.Adult))
        assertTrue(source.isRefused(calico, SheetKind.Adult))
        assertNull(source.palette(point))
    }

    @Test
    fun aSilentRequestIsAskedAgainAfterTheTimeoutAndALateAnswerIsDropped() {
        source.sheet(calico, SheetKind.Adult)
        val first = bridge.last().id
        now += PageCoatSource.TIMEOUT_MS - 1
        assertNull(source.sheet(calico, SheetKind.Adult))
        assertEquals(1, bridge.sent.size)
        now += 1
        assertNull(source.sheet(calico, SheetKind.Adult))
        assertEquals(2, bridge.sent.size)
        val second = bridge.last().id
        assertTrue(first != second)
        answer(first, "calico", "adult") // the first request's late answer finds nobody waiting
        assertNull(source.sheet(calico, SheetKind.Adult))
        assertEquals(0, changes)
        answer(second, "calico", "adult")
        assertNotNull(source.sheet(calico, SheetKind.Adult))
    }

    @Test
    fun aPageThatNeverAnswersIsGivenUpOnAfterThreeTries() {
        repeat(PageCoatSource.MAX_TRIES) {
            source.sheet(calico, SheetKind.Adult)
            now += PageCoatSource.TIMEOUT_MS
        }
        assertNull(source.sheet(calico, SheetKind.Adult))
        assertEquals(PageCoatSource.MAX_TRIES, bridge.sent.size)
        assertTrue(source.isRefused(calico, SheetKind.Adult))
        now += 100 * PageCoatSource.TIMEOUT_MS
        source.sheet(calico, SheetKind.Adult)
        assertEquals(PageCoatSource.MAX_TRIES, bridge.sent.size)
    }

    @Test
    fun onlyGeneticKeysAreEverAskedFor() {
        val keys =
            listOf(
                "ginger",
                "gray",
                "black",
                "tuxedo",
                "",
                "dna_",
                "DNA_x",
                "dna_A",
                "dna_a b",
                "../dna_a",
                "x".repeat(70),
                "dna_" + "a".repeat(60),
            )
        for (key in keys) assertNull(key, source.sheet(key, SheetKind.Adult))
        assertTrue(bridge.sent.isEmpty())
        assertTrue(PageCoatSource.isDnaKey(calico))
    }

    @Test
    fun withoutABridgeThereIsNothingToAskAndNothingBreaks() {
        val lone = PageCoatSource { now }
        assertNull(lone.sheet(calico, SheetKind.Adult))
        assertNull(lone.palette(calico))
        assertFalse(lone.isRefused(calico, SheetKind.Adult))
        lone.bind(bridge.requester)
        assertNull(lone.sheet(calico, SheetKind.Adult))
        assertEquals(1, bridge.sent.size)
    }

    @Test
    fun noMoreThanEightRequestsAreOutAtOnce() {
        for (i in 0 until 20) source.sheet("dna_k$i", SheetKind.Adult)
        assertEquals(PageCoatSource.MAX_PENDING, bridge.sent.size)
        assertEquals(PageCoatSource.MAX_PENDING, source.outstanding)
    }

    @Test
    fun otherRepliesAreNotTaken() {
        val tip = JSONObject().put("command", "tip").put("requestId", "r1").put("ok", true)
        assertFalse(source.onReply(tip))
        assertFalse(source.onReply(JSONObject()))
        assertEquals(0, changes)
    }

    @Test
    fun theSheetCacheIsBoundedAndAnEvictedSheetIsAskedForAgain() {
        // twelve sheets are kept: ask for thirteen kinds of the one coat and answer each (the
        // source takes a reply for the kind it asked, whatever the pixels: the png is checked by
        // its header here and decoded when the art uses it)
        val kinds =
            (0..5).map { SheetKind.Stage(it) } +
                (0..5).map { SheetKind.DepthStage(it) } +
                SheetKind.Adult
        assertEquals(PageCoatSource.MAX_SHEETS + 1, kinds.size)
        for (kind in kinds) {
            source.sheet(calico, kind)
            source.onReply(
                CoatFixtures.reply("calico", "adult", bridge.last().id).put("kind", kind.key)
            )
        }
        assertEquals(kinds.size, bridge.sent.size)
        assertNotNull(source.sheet(calico, kinds.last()))
        val before = bridge.sent.size
        assertNull(source.sheet(calico, kinds.first()))
        assertEquals(before + 1, bridge.sent.size)
    }
}
