package com.lifemax.rpg.core.directorshadow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** SH-5 to SH-7: the copy's parts, laid and stitched as the page's JSON.stringify writes the whole. */
class TextMirrorTest {
    private fun o(path: List<Any>, vararg keys: String) = TextOp('o', path, keys = keys.toList())
    private fun a(path: List<Any>, n: Int) = TextOp('a', path, length = n)
    private fun t(path: List<Any>, text: String) = TextOp('t', path, text = text)

    private fun base(): TextMirror =
        TextMirror().apply {
            assertTrue(
                apply(
                    listOf(
                        o(listOf(), "v", "schedule", "changeLog"),
                        t(listOf("v"), "1"),
                        o(listOf("schedule"), "scheduledInstances", "h"),
                        a(listOf("schedule", "scheduledInstances"), 2),
                        t(listOf("schedule", "scheduledInstances", 0), "{\"a\":1}"),
                        t(listOf("schedule", "scheduledInstances", 1), "{\"a\":2}"),
                        t(listOf("schedule", "h"), "null"),
                        a(listOf("changeLog"), 0),
                    ),
                    seq = 1,
                    base = true,
                )
            )
        }

    @Test
    fun aBaseStitchesToThePagesText() {
        assertEquals("{\"v\":1,\"schedule\":{\"scheduledInstances\":[{\"a\":1},{\"a\":2}],\"h\":null},\"changeLog\":[]}", base().stitch())
    }

    @Test
    fun changesKeepWhatTheyDoNotName() {
        val m = base()
        assertTrue(m.apply(listOf(a(listOf("schedule", "scheduledInstances"), 3), t(listOf("schedule", "scheduledInstances", 2), "{\"a\":3}")), 2, false))
        assertEquals("{\"v\":1,\"schedule\":{\"scheduledInstances\":[{\"a\":1},{\"a\":2},{\"a\":3}],\"h\":null},\"changeLog\":[]}", m.stitch())
        assertTrue(m.apply(listOf(a(listOf("schedule", "scheduledInstances"), 1)), 3, false))
        assertEquals("{\"v\":1,\"schedule\":{\"scheduledInstances\":[{\"a\":1}],\"h\":null},\"changeLog\":[]}", m.stitch())
        assertEquals(3L, m.seq)
    }

    @Test
    fun membersFollowThePagesOrderAndDropTheRest() {
        val m = base()
        assertTrue(m.apply(listOf(o(listOf(), "changeLog", "v", "w"), t(listOf("w"), "\"é\\n\"")), 2, false))
        assertEquals("{\"changeLog\":[],\"v\":1,\"w\":\"é\\n\"}", m.stitch())
    }

    @Test
    fun aMemberNamedWithoutItsTextLeavesTheCopyIncomplete() {
        val m = base()
        assertTrue(m.apply(listOf(o(listOf(), "v", "schedule", "changeLog", "new")), 2, false))
        assertNull(m.stitch())
        val n = base()
        assertTrue(n.apply(listOf(a(listOf("changeLog"), 1)), 2, false))
        assertNull(n.stitch())
    }

    @Test
    fun aChangeAtAPlaceTheCopyLacksDropsTheCopy() {
        for (bad in listOf(
            t(listOf("nope"), "1"),
            t(listOf("schedule", "scheduledInstances", 5), "1"),
            t(listOf("v", "x"), "1"),
            t(listOf(), "{}"),
            t(listOf("schedule", "scheduledInstances", "x"), "1"),
            TextOp('z', listOf("v")),
        )) {
            val m = base()
            assertFalse(bad.toString(), m.apply(listOf(bad), 2, false))
            assertTrue(m.empty)
            assertEquals(0L, m.seq)
        }
    }

    @Test
    fun aKeyIsWrittenAsJsonStringifyWritesIt() {
        val m = TextMirror()
        assertTrue(m.apply(listOf(o(listOf(), "a\"b", "\u0001"), t(listOf("a\"b"), "1"), t(listOf("\u0001"), "2")), 1, true))
        assertEquals("{\"a\\\"b\":1,\"\\u0001\":2}", m.stitch())
    }

    @Test
    fun aTextPastTheLimitIsNotStitched() {
        assertNull(base().stitch(limit = 10))
    }
}
