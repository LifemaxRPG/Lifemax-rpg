package com.lifemax.rpg.core.directorshadow

import com.lifemax.rpg.core.shadow.ShadowLedger
import com.lifemax.rpg.core.shadow.ShadowLimits
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

/** What the report is built from beyond the ledger (SH-35). */
data class DirectorReportFacts(
    val versionCode: Long,
    val nativeApi: Int,
    val pageBuild: String,
    val nativeFingerprint: String,
    val pageFingerprint: String,
    val nativeCosts: List<Long>,
    val pageCosts: List<Double>,
    val now: Long,
    val zone: ZoneId,
)

/**
 * The plain-text report the Life Director check's Copy report puts on the clipboard (SH-35): the
 * builds, the fingerprints, the counts per check (compared, same, allowed, different, and why any
 * were skipped), the store codec's strict findings, refusals and errors by name, the costs, the
 * self-disable history and the latest differences with their redacted paths and allowlist ids.
 * Built only from the ledger (no owner text, SH-31) and [DirectorReportFacts]; never longer than
 * [ShadowLimits.REPORT_MAX_CHARS].
 */
object DirectorShadowReport {
    fun text(l: ShadowLedger, f: DirectorReportFacts): String {
        val head = buildString {
            val days = (f.now - l.since).coerceAtLeast(0) / DAY_MS
            line(
                "LifeMax Life Director check v1 · app ${f.versionCode} (api ${f.nativeApi}) · page " +
                    label(f.pageBuild).ifEmpty { "unlabelled" } +
                    " · since ${day(l.since, f.zone)} ($days ${if (days == 1L) "day" else "days"})"
            )
            line(
                "state ${l.state.word}${l.reason?.let { " ($it)" } ?: ""} · written ${time(f.now, f.zone)}"
            )
            val match =
                when {
                    f.pageFingerprint.isEmpty() -> "page not told yet"
                    f.pageFingerprint == f.nativeFingerprint -> "same"
                    else -> "different: the page was updated without the app"
                }
            line(
                "engine and store app ${f.nativeFingerprint} · page ${label(f.pageFingerprint).ifEmpty { "none" }} ($match)"
            )
            val skipped =
                sortedMapOf<String, Int>().apply {
                    for (k in l.kinds.values) for ((w, c) in k.skipped) merge(w, c, Int::plus)
                }
            line(
                "checks ${n(l.seen)} · compared ${n(l.compared)} · same ${n(l.same)} · allowed ${n(l.allowed)} · different ${n(l.different)}" +
                    " · skipped ${n(skipped.values.sum())}" +
                    list(skipped, " (", ")")
            )
            line("by check: compared/same/allowed/different, then skips")
            for ((kind, k) in l.kinds) line(
                "  $kind ${n(k.compared)}/${n(k.same)}/${n(k.allowed)}/${n(k.different)}" +
                    list(k.skipped, " · skipped ", "")
            )
            if (l.kinds.isEmpty()) line("  none yet")
            line("store strict:" + list(l.codec, " ", "").ifEmpty { " none" })
            line("info:" + list(l.info, " ", "").ifEmpty { " none" })
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
        val all = l.mismatches()
        var count = minOf(all.size, ShadowLimits.REPORT_DIFFERENCES)
        while (true) {
            val shown = all.takeLast(count).reversed()
            val text =
                head +
                    buildString {
                        line(
                            if (all.isEmpty()) "differences: none"
                            else
                                "differences: the latest ${shown.size} of ${all.size} kept (* = not explained by the allowlist)"
                        )
                        for (m in shown) {
                            line(
                                "${m.id} x${m.count} ${m.kind}${if (m.unexplained) "*" else ""} ${time(m.first, f.zone)} -> ${time(m.last, f.zone)}"
                            )
                            line("     paths ${m.paths.joinToString("; ")}")
                            if (m.allow.isNotEmpty())
                                line("     allowlist ${m.allow.joinToString(", ")}")
                        }
                    }
            if (text.length <= ShadowLimits.REPORT_MAX_CHARS || count == 0)
                return text.take(ShadowLimits.REPORT_MAX_CHARS)
            count--
        }
    }

    private fun percentiles(costs: List<Double>): String {
        if (costs.isEmpty()) return "not measured yet"
        val s = costs.sorted()
        fun at(p: Int) = s[(s.size - 1) * p / 100].roundToLong()
        return "p50 ${at(50)} ms p95 ${at(95)} ms (last ${s.size})"
    }

    private fun list(map: Map<String, Int>, open: String, close: String): String =
        if (map.isEmpty()) ""
        else map.entries.joinToString(" · ", open, close) { "${it.key} ${n(it.value)}" }

    private fun n(v: Int): String = String.format(Locale.UK, "%,d", v)

    private fun label(build: String): String =
        build.filter { it.isLetterOrDigit() && it.code < ASCII || it in "._-/" }.take(MAX_LABEL)

    private fun day(t: Long, zone: ZoneId) = DAY.format(Instant.ofEpochMilli(t).atZone(zone))

    private fun time(t: Long, zone: ZoneId) = TIME.format(Instant.ofEpochMilli(t).atZone(zone))

    private fun StringBuilder.line(text: String) {
        append(text).append('\n')
    }

    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val ASCII = 128
    private const val MAX_LABEL = 40
    private val DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.UK)
    private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.UK)
}
