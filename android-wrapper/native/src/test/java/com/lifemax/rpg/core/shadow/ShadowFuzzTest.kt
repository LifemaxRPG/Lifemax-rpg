package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonValue
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP6f's fuzz (ECONOMY_SHADOW_PLAN WP6f, 2.4, 4): seeded, so a failure names its seed and repeats.
 * - Every message the page could send, broken at random (cut, characters dropped, inserted and
 *   swapped, numbers and strings replaced, keys doubled, values nested deep, random text): each
 *   reader answers a value or a typed refusal and never throws, and a session fed them counts every
 *   one and never throws out.
 * - The transport broken at the message level (envelopes dropped, repeated, swapped, an old epoch,
 *   base parts lost, cut or out of order, a reload in the middle): never a difference blamed on
 *   native, the copy always back in step after the next base, and the check never stuck.
 * - Bounds: thousands of kinds, lies and touch maps keep the ring under 128 KB, at most 64 kinds,
 *   200 entries and five cases.
 */
class ShadowFuzzTest {
    private val golden by lazy { GoldenActions.build().take(GOLDEN_ACTIONS) }

    @Test
    fun brokenMessagesAreRefusedWithAReasonAndNeverThrow() {
        val page = FakeShadowPage()
        val sources = ArrayList<String>()
        sources += page.base(golden.first().action.before, slice = 2_000).take(4)
        golden.forEach { sources += page.send(it.action) }
        sources += ShadowSessionTest.configure(ShadowSessionTest.FP)
        val reasons = sortedMapOf<String, Int>()
        var ok = 0
        val session = ShadowGoldenCompareTest.session()
        page.base(golden.first().action.before).forEach { session.base(it, 0) }
        for (seed in 1..SEEDS) {
            val r = Random(seed.toLong())
            val text = mutate(sources[r.nextInt(sources.size)], r)
            val decoded =
                try {
                    listOf(
                        ShadowEnvelopeJson.envelope(text),
                        ShadowEnvelopeJson.basePart(text),
                        ShadowEnvelopeJson.configure(text),
                    )
                } catch (e: Throwable) {
                    throw AssertionError("seed $seed: a reader threw ${e.javaClass.simpleName}", e)
                }
            for (d in decoded) when (d) {
                is ShadowDecode.Ok -> ok++
                is ShadowDecode.Refused -> reasons.merge(d.reason.word, 1, Int::plus)
            }
            val counted = session.ledger.seen + session.ledger.refused.values.sum()
            try {
                if (session.accept(text.length, false).accepted) {
                    session.observe(text, seed * ShadowLimits.HOUR_MS)
                    val now = session.ledger.seen + session.ledger.refused.values.sum()
                    assertEquals("seed $seed: every message counted", counted + 1, now)
                }
                // the page answers a refusal with a base before its next envelope
                if (!session.accept(0, false).accepted)
                    page.base(golden[seed % golden.size].action.before).forEach {
                        session.base(it, seed * ShadowLimits.HOUR_MS)
                    }
            } catch (e: Throwable) {
                throw AssertionError("seed $seed: the session threw ${e.javaClass.simpleName}", e)
            }
        }
        assertTrue("most broken messages refused: $reasons", reasons.values.sum() > SEEDS)
        assertTrue("typed reasons: $reasons", reasons.size >= MIN_REASONS)
        assertEquals(
            "never disabled by broken input: ${session.ledger.reason} ${session.ledger.errors}",
            ShadowState.ON,
            session.ledger.state,
        )
        println(
            "fuzz: $SEEDS broken messages, ${3 * SEEDS} reads: $ok decoded, refused $reasons; " +
                "session refused ${session.ledger.refused}, internal errors ${session.ledger.errors}"
        )
    }

    /**
     * The page has no build label, so its configure carries `build: ""` (`50-native-shadow.js`
     * warm(), and turning the switch off): accepted, the check on (the mutation sweep found every
     * other test configuring with a label).
     */
    @Test
    fun theConfigureTheRealPageSendsIsAccepted() {
        val session = ShadowSessionTest.session()
        val on =
            "{\"v\":1,\"enabled\":true,\"sid\":\"p-ab12cd34\",\"page\":{\"build\":\"\",\"schema\":1," +
                "\"fingerprint\":\"${ShadowSessionTest.FP}\",\"faults\":0}}"
        val answer = session.configure(on, false)
        assertTrue(answer.ok)
        assertEquals(ShadowState.ON, answer.state)
        val off =
            "{\"v\":1,\"enabled\":false,\"sid\":\"p-off\",\"page\":{\"build\":\"\",\"schema\":1,\"fingerprint\":\"\",\"faults\":0}}"
        assertEquals(ShadowState.OFF, session.configure(off, false).state)
    }

    @Test
    fun aBrokenTransportNeverBlamesNativeAndAlwaysRecovers() {
        var resyncs = 0
        for (seed in 1..TRANSPORT_SEEDS) {
            val r = Random(seed * 31L)
            val session = ShadowGoldenCompareTest.session()
            var page = FakeShadowPage(sid = "p-$seed")
            var needBase = true
            // the page's clock: an action every twenty minutes (the resync rule counts per hour)
            var clock = FakeShadowPage.T0
            for (b in golden) {
                clock += STEP_MS
                GoldenActions.CONTENT.setup = b.setup
                if (needBase) {
                    val parts = page.base(b.action.before, slice = 1_000 + r.nextInt(30_000))
                    for (part in broken(parts, r)) session.base(part, clock)
                    needBase = false
                }
                val text = page.send(b.action)
                val sent =
                    when (r.nextInt(10)) {
                        0 -> emptyList() // lost
                        1 -> listOf(text, text) // repeated
                        2 -> listOf(text.replace("\"epoch\":0", "\"epoch\":7")) // a new epoch
                        3 -> {
                            // a reload: a new page session from here
                            session.configure(
                                ShadowSessionTest.configure(
                                    ShadowSessionTest.FP,
                                    sid = "p-$seed-r",
                                ),
                                false,
                            )
                            page = FakeShadowPage(sid = "p-$seed-r")
                            needBase = true
                            emptyList()
                        }
                        else -> listOf(text)
                    }
                for (t in sent) {
                    if (session.accept(t.length, false).accepted) session.observe(t, clock)
                }
                if (!session.accept(0, false).accepted) {
                    needBase = true
                    resyncs++
                }
                assertEquals(
                    "seed $seed ${b.caseId}: no difference blamed on native",
                    0,
                    session.ledger.different,
                )
            }
            // never stuck: one clean base and action are compared and match
            val last = golden.last()
            GoldenActions.CONTENT.setup = last.setup
            val clean = FakeShadowPage(sid = "p-$seed-clean")
            session.configure(
                ShadowSessionTest.configure(ShadowSessionTest.FP, sid = "p-$seed-clean"),
                false,
            )
            clean.base(last.action.before).forEach {
                assertTrue(
                    "seed $seed: ${session.ledger.state} ${session.ledger.reason}",
                    session.base(it, clock).ok,
                )
            }
            val was = session.ledger.same + session.ledger.allowed
            session.observe(clean.send(last.action), FakeShadowPage.T0)
            assertEquals(
                "seed $seed: compared after recovery",
                was + 1,
                session.ledger.same + session.ledger.allowed,
            )
            ShadowReplayTest.assertSame(
                "seed $seed",
                FakeShadowPage.shadow(last.action.after),
                session.mirrorSnapshot(),
            )
            assertTrue("${session.ledger.errors}", session.ledger.errors.isEmpty())
        }
        println(
            "fuzz transport: $TRANSPORT_SEEDS seeds of ${golden.size} actions, $resyncs resyncs, no difference blamed on native"
        )
    }

    @Test
    fun thousandsOfKindsLiesAndEntriesStayInsideTheBounds() {
        val r = Random(BOUNDS_SEED)
        val session = ShadowGoldenCompareTest.session()
        val page = FakeShadowPage()
        page.base(golden.first().action.before).forEach { session.base(it, 0) }
        repeat(BOUNDS_ACTIONS) { n ->
            val b = golden[n % golden.size]
            GoldenActions.CONTENT.setup = b.setup
            val action =
                when (n % 3) {
                    // a kind native does not know (an open set: counted, never compared)
                    0 ->
                        b.action.copy(
                            kind =
                                "k" +
                                    Integer.toString(r.nextInt(1_000_000), 36)
                                        .map { c -> if (c.isDigit()) 'a' + (c - '0') else c }
                                        .joinToString("")
                        )
                    // a lie at a random economy number
                    1 -> b.action.copy(after = lie(b.action.after, r))
                    else -> b.action
                }
            session.observe(page.send(action), FakeShadowPage.T0 + n * 60_000L)
        }
        val l = session.ledger
        val ring = session.ringText()
        assertTrue("ring ${ring.length}", ring.toByteArray().size <= ShadowLimits.RING_MAX_BYTES)
        assertTrue("kinds ${l.kinds.size}", l.kinds.size <= ShadowLimits.MAX_KINDS + 1)
        assertTrue(l.entries().size <= ShadowLimits.RING_MAX_ENTRIES)
        assertTrue(l.mismatches().size <= ShadowLimits.RING_MAX_ENTRIES)
        assertTrue(session.summary().cases.size <= ShadowLimits.CASES_KEPT)
        assertTrue(
            "lies were noticed: ${l.state} ${l.reason} compared ${l.compared} same ${l.same} ${l.kinds.keys.take(5)} ${l.refused}",
            l.different > 0,
        )
        assertTrue(l.errors.isEmpty())
        val report = session.report(FakeShadowPage.T0, java.time.ZoneOffset.UTC)
        assertTrue("report ${report.length}", report.length <= ShadowLimits.REPORT_MAX_CHARS)
        println(
            "fuzz bounds: $BOUNDS_ACTIONS actions, ${l.kinds.size} kinds kept, ${l.different} " +
                "unexplained, ring ${ring.length} chars, report ${report.length} chars"
        )
    }

    /** One random break of a message's text. */
    private fun mutate(text: String, r: Random): String {
        val i = if (text.isEmpty()) 0 else r.nextInt(text.length)
        return when (r.nextInt(11)) {
            0 -> text.substring(0, i)
            1 -> text.removeRange(i, minOf(text.length, i + 1 + r.nextInt(8)))
            2 -> text.substring(0, i) + TOKENS[r.nextInt(TOKENS.size)] + text.substring(i)
            3 ->
                NUMBER.replace(text) { m ->
                    if (r.nextInt(4) == 0) NUMBERS[r.nextInt(NUMBERS.size)] else m.value
                }
            4 ->
                STRING.replace(text) { m ->
                    if (r.nextInt(6) == 0) "\"" + randomText(r) + "\"" else m.value
                }
            5 -> text.replaceFirst("\"seq\":", "\"seq\":1,\"seq\":")
            6 ->
                text.replaceFirst(
                    "\"returns\":",
                    "\"returns\":" + "[".repeat(DEEP) + "]".repeat(DEEP) + ",\"x\":",
                )
            7 -> randomText(r)
            8 -> if (text.length < 2) text else text.substring(0, i) + text.substring(i).reversed()
            9 -> text.replaceFirst(KEY_REGEX, "\"" + KEYS[r.nextInt(KEYS.size)] + "\":")
            else -> text.replace("\"v\":1", "\"v\":" + (r.nextInt(5) - 1))
        }
    }

    private fun randomText(r: Random): String {
        val b = StringBuilder()
        repeat(r.nextInt(40)) { b.append((r.nextInt(0x3000)).toChar()) }
        return b.toString().replace("\\", "").replace("\"", "")
    }

    /** Base parts lost, cut, repeated or out of order, one time in four. */
    private fun broken(parts: List<String>, r: Random): List<String> =
        when (r.nextInt(8)) {
            0 -> if (parts.size > 1) parts.drop(1) else parts
            1 ->
                parts.mapIndexed { i, p ->
                    if (i == parts.size - 1) p.substring(0, p.length / 2) else p
                }
            2 -> if (parts.size > 1) parts.reversed() else parts
            3 -> parts + parts
            else -> parts
        }

    /** The after-state with one economy number moved: a lie the comparison must notice. */
    private fun lie(after: Map<String, JsonValue>, r: Random): Map<String, JsonValue> {
        val key = LIE_KEYS[r.nextInt(LIE_KEYS.size)]
        val now = (after[key] as? JsonNumber)?.value ?: 0.0
        return after + (key to JsonNumber(now + 1 + r.nextInt(50)))
    }

    private companion object {
        const val SEEDS = 3_000
        const val TRANSPORT_SEEDS = 40
        const val GOLDEN_ACTIONS = 60
        const val BOUNDS_ACTIONS = 3_000
        const val BOUNDS_SEED = 4242L
        const val STEP_MS = 20 * 60_000L
        const val MIN_REASONS = 6
        /** Deeper than the reader's 512 levels, inside the 256 KB envelope limit. */
        const val DEEP = 2_000
        val TOKENS =
            listOf(
                "{",
                "}",
                "[",
                "]",
                ",",
                ":",
                "\"",
                "\\",
                "\u0000",
                "1e999",
                "-0",
                "NaN",
                "null",
                "true",
                "\uD800",
                "\"\\u00",
                "Infinity",
            )
        val NUMBERS = listOf("-1", "1e308", "1.5", "-0", "9007199254740993", "0.1", "1e999")
        val NUMBER = Regex("(?<=:)-?\\d+(\\.\\d+)?")
        val STRING = Regex("\"[A-Za-z0-9 ._:/-]{1,40}\"")
        val KEY_REGEX = Regex("\"[a-z]+\":")
        val KEYS =
            listOf(
                "v",
                "kind",
                "seq",
                "base",
                "set",
                "rec",
                "len",
                "at",
                "keys",
                "unknown",
                "sparks",
                "__proto__",
            )
        val LIE_KEYS = listOf("sparks", "restedXP", "streakShields")
    }
}
