package com.lifemax.rpg.core.shadow

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * What the report is built from beyond the ledger: the app and page builds, both content
 * fingerprints, the recent costs (memory only) and the moment and zone the report is written in.
 */
data class ReportFacts(
    val versionCode: Long,
    val nativeApi: Int,
    val pageBuild: String,
    val nativeFingerprint: String,
    val pageFingerprint: String,
    /** The worker's time for the latest envelopes, in milliseconds. */
    val nativeCosts: List<Long>,
    /** The page's own main-thread time for the latest envelopes, in milliseconds. */
    val pageCosts: List<Double>,
    val now: Long,
    val zone: ZoneId,
)

/**
 * The plain-text report "Copy report" puts on the clipboard for the owner to paste to the assistant
 * (ECONOMY_SHADOW_PLAN 3.4): builds, content fingerprints, the counts per kind (compared, same,
 * allowed, different) and the reasons actions were skipped, the `info` lines (`d13Kept`, `zoneLag`,
 * `clockJump` always shown), Health Connect reads that differed, the codec's counts, decoder
 * refusals and internal errors by class name, the self-disable history, and the latest
 * [ShadowLimits.REPORT_DIFFERENCES] differences with their redacted field paths, allowlist ids and
 * both sides' XP, Sparks and rested changes.
 *
 * Built only from the [ShadowLedger] (which holds no owner text, 3.2) and [ReportFacts]; the page
 * build is reduced to plain label characters. Never longer than [ShadowLimits.REPORT_MAX_CHARS]:
 * the oldest listed differences go first.
 */
object ShadowReport {
    /** The report's text. */
    fun text(ledger: ShadowLedger, facts: ReportFacts): String {
        val head = head(ledger, facts)
        val shown = ledger.mismatches().takeLast(ShadowLimits.REPORT_DIFFERENCES).reversed()
        var count = shown.size
        while (true) {
            val text = head + differences(ledger, shown.take(count), facts.zone)
            if (text.length <= ShadowLimits.REPORT_MAX_CHARS || count == 0)
                return text.take(ShadowLimits.REPORT_MAX_CHARS)
            count--
        }
    }

    private fun head(l: ShadowLedger, f: ReportFacts): String = buildString {
        val days = (f.now - l.since).coerceAtLeast(0) / DAY_MS
        line(
            "LifeMax economy check v1 · app ${f.versionCode} (api ${f.nativeApi}) · page " +
                label(f.pageBuild).ifEmpty { "unlabelled" } +
                " · since ${day(l.since, f.zone)} ($days ${if (days == 1L) "day" else "days"})"
        )
        line(
            "state ${l.state.word}${l.reason?.let { " ($it)" } ?: ""} · written ${time(f.now, f.zone)}"
        )
        val app = f.nativeFingerprint.take(ShadowLimits.FINGERPRINT_DIGITS)
        val page = f.pageFingerprint.take(ShadowLimits.FINGERPRINT_DIGITS)
        val match =
            when {
                page.isEmpty() -> "page not told yet"
                f.nativeFingerprint == f.pageFingerprint -> "same"
                else -> "different: the page was updated without the app"
            }
        line("content app ${app.ifEmpty { "none" }} · page ${page.ifEmpty { "none" }} ($match)")
        val skipped = merged(l.kinds.values.flatMap { it.skipped.entries })
        line(
            "actions ${n(l.seen)} · compared ${n(l.compared)} · same ${n(l.same)} · allowed " +
                "${n(l.allowed)} · different ${n(l.different)} · skipped ${n(skipped.values.sum())}" +
                list(skipped, " (", ")")
        )
        line("by kind: compared/same/allowed/different, then skips")
        for ((kind, k) in l.kinds) {
            val skips = list(k.skipped, " · skipped ", "")
            line(
                "  $kind ${n(k.compared)}/${n(k.same)}/${n(k.allowed)}/${n(k.different)}" +
                    (if (k.seen != k.applied) " · seen ${n(k.seen)}" else "") +
                    skips
            )
        }
        if (l.kinds.isEmpty()) line("  none yet")
        val info = sortedMapOf(D13 to 0, ZONE_LAG to 0, CLOCK_JUMP to 0)
        for ((word, c) in l.info) if (!word.startsWith("$D13.")) info[word] = c
        val d13 = l.info.filterKeys { it.startsWith("$D13.") }
        info[D13] = d13.values.sum()
        line(
            "info:" +
                list(info, " ", "") +
                list(d13.mapKeys { it.key.removePrefix("$D13.") }, " (", ")")
        )
        line("reads: " + reads(l))
        line(
            "codec:" +
                list(
                    sortedMapOf(STRICT to 0, ENCODE to 0, NON_FINITE to 0).apply {
                        putAll(l.codec)
                    },
                    " ",
                    "",
                )
        )
        line("refused:" + list(l.refused, " ", "").ifEmpty { " none" })
        line(
            "errors: native ${n(l.nativeFaults)} · page ${n(l.pageFaults)}" +
                list(l.errors, " (", ")")
        )
        line(
            "cost: page ${percentiles(f.pageCosts)} · native ${percentiles(f.nativeCosts.map { it.toDouble() })}"
        )
        val history = l.disables()
        line(
            "stopped itself: " +
                if (history.isEmpty()) "never"
                else history.joinToString(" · ") { "${time(it.at, f.zone)} ${it.reason}" }
        )
    }

    private fun differences(l: ShadowLedger, shown: List<MismatchEntry>, zone: ZoneId): String =
        buildString {
            val all = l.mismatches().size
            line(
                when {
                    all == 0 -> "differences: none"
                    shown.size < all ->
                        "differences: the latest ${shown.size} of $all kept (* = not explained by the allowlist)"
                    else -> "differences: $all kept (* = not explained by the allowlist)"
                }
            )
            for (m in shown) {
                line(
                    "${m.id} x${m.count} ${m.kind}${if (m.unexplained) "*" else ""} " +
                        "${time(m.first, zone)} -> ${time(m.last, zone)}" +
                        (label(m.pageBuild).takeIf { it.isNotEmpty() }?.let { " (page $it)" } ?: "")
                )
                line("     paths ${m.paths.joinToString("; ")}")
                if (m.allow.isNotEmpty()) line("     allowlist ${m.allow.joinToString(", ")}")
                line("     page ${change(m.page)} · native ${change(m.native)}")
            }
        }

    /** The Health Connect reads that differed (`reads.<part>` paths), by path and kind. */
    private fun reads(l: ShadowLedger): String {
        val counts = sortedMapOf<String, Int>()
        for (m in l.mismatches()) for (p in m.paths) if (p.startsWith(READS))
            counts.merge("$p (${m.kind})", m.count, Int::plus)
        return if (counts.isEmpty()) "no difference" else list(counts, "", "").trim()
    }

    private fun change(c: EconomyChange?): String =
        if (c == null) "not run"
        else "xp ${signed(c.xp)} sparks ${signed(c.sparks)} rested ${signed(c.rested)}"

    private fun signed(v: Double): String {
        val r = (v * 100).roundToLong() / 100.0
        val text = if (r == Math.floor(r) && abs(r) < 1e15) r.toLong().toString() else r.toString()
        return if (r > 0) "+$text" else text
    }

    private fun percentiles(costs: List<Double>): String {
        if (costs.isEmpty()) return "not measured yet"
        val s = costs.sorted()
        fun at(p: Int) = s[((s.size - 1) * p / 100)].roundToLong()
        return "p50 ${at(50)} ms p95 ${at(95)} ms (last ${s.size})"
    }

    private fun list(map: Map<String, Int>, open: String, close: String): String =
        if (map.isEmpty()) ""
        else map.entries.joinToString(" · ", open, close) { "${it.key} ${n(it.value)}" }

    private fun merged(entries: List<Map.Entry<String, Int>>): Map<String, Int> =
        sortedMapOf<String, Int>().apply { for ((k, v) in entries) merge(k, v, Int::plus) }

    private fun n(v: Int): String = String.format(Locale.UK, "%,d", v)

    /** A page build reduced to the characters a build label uses. */
    private fun label(build: String): String =
        build.filter { it.isLetterOrDigit() && it.code < ASCII || it in "._-" }.take(MAX_LABEL)

    private fun day(t: Long, zone: ZoneId) = DAY.format(Instant.ofEpochMilli(t).atZone(zone))

    private fun time(t: Long, zone: ZoneId) = TIME.format(Instant.ofEpochMilli(t).atZone(zone))

    private fun StringBuilder.line(text: String) {
        append(text).append('\n')
    }

    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val ASCII = 128
    private const val MAX_LABEL = 40
    private const val READS = "reads."
    private const val D13 = "d13Kept"
    private const val ZONE_LAG = "zoneLag"
    private const val CLOCK_JUMP = "clockJump"
    private const val STRICT = "strict"
    private const val ENCODE = "encode"
    private const val NON_FINITE = "nonFinite"
    private val DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.UK)
    private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.UK)
}
