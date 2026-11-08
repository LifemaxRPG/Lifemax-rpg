package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonValue

/** The economy numbers an entry reports for one side (3.1): changes of total XP, Sparks, rested. */
data class EconomyChange(val xp: Double, val sparks: Double, val rested: Double) {
    companion object {
        fun of(before: Map<String, JsonValue>, after: Map<String, JsonValue>?): EconomyChange? {
            after ?: return null
            fun total(state: Map<String, JsonValue>) =
                (state["attrXP"] as? JsonObject)?.members?.values?.sumOf { number(it) } ?: 0.0
            fun field(state: Map<String, JsonValue>, key: String) = number(state[key])
            return EconomyChange(
                total(after) - total(before),
                field(after, "sparks") - field(before, "sparks"),
                field(after, "restedXP") - field(before, "restedXP"),
            )
        }

        private fun number(v: JsonValue?): Double =
            (v as? JsonNumber)?.value?.takeIf { it.isFinite() } ?: 0.0
    }
}

/** How one action came out (ECONOMY_SHADOW_PLAN 5.2). */
sealed interface Verdict {
    /** Not compared, for [reason]. */
    data class Skipped(val reason: String) : Verdict

    /** Native predicted exactly what the page did. */
    data object Same : Verdict

    /**
     * Native differed from the page at [found] (shown names), and [allowed] explains each of them
     * when [unexplained] is empty; otherwise [unexplained] are the paths no entry explains.
     */
    data class Differed(
        val found: List<Difference>,
        val unexplained: List<Difference>,
        val allowed: Set<String>,
        val page: EconomyChange?,
        val native: EconomyChange?,
    ) : Verdict {
        val explained: Boolean
            get() = unexplained.isEmpty()
    }
}

/**
 * The whole comparison of one action (ECONOMY_SHADOW_PLAN 1.3): runs native ([ShadowRunner]) and
 * compares, output by output, the tracked state (with the codec's encode check), the return value,
 * the toasts, the number of writes and the ids asked for; then classifies what differs against
 * [KnownDifferences]. Reads the two states, writes nothing.
 */
class ShadowComparison(redactor: ShadowRedactor, private val runner: ShadowRunner) {
    private val diff = ShadowDiff(redactor)

    /** The verdict for [envelope], with the page's [before] and [after]; [ran] sees the run. */
    fun compare(
        envelope: ShadowEnvelope,
        before: Map<String, JsonValue>,
        after: Map<String, JsonValue>,
        ran: (Prediction) -> Unit = {},
    ): Verdict {
        val prediction =
            when (val r = runner.run(envelope, before, after)) {
                is RunOutcome.Skipped -> return Verdict.Skipped(r.reason)
                is RunOutcome.Ran -> r.prediction
            }
        ran(prediction)
        val found = ArrayList<Difference>()
        val native = prediction.after
        if (native == null)
            found += Difference("codec.encode.${prediction.encodeRefusal}", CODEC_ENCODE)
        else found += diff.state(before, after, native, prediction.anchor, envelope.t1)
        for (read in prediction.reads) found += Difference(read, read)
        found += diff.value(RETURNS, envelope.returns, prediction.returns)
        val toastAllowed = toasts(envelope.toasts, prediction.toasts, found)
        if (envelope.env.saves.size != prediction.saves) found += Difference(SAVES, SAVES)
        // the journal coach's own plain writes are page state outside the economy (A-06)
        if (envelope.env.journal.isNotEmpty()) found += Difference(JOURNAL, JOURNAL)
        if (prediction.extraIds > 0) found += Difference(IDS_EXTRA, IDS_EXTRA)
        if (found.isEmpty() && !toastAllowed) return Verdict.Same
        val unexplained =
            found.filter { KnownDifferences.explaining(envelope.kind, it, before) == null }
        val allowed = sortedSetOf<String>()
        for (d in found) KnownDifferences.explaining(envelope.kind, d, before)?.let {
            allowed += it.id
        }
        if (toastAllowed) allowed += KnownDifferences.DOUBLED_UNSAVED
        val shown = if (toastAllowed) found + Difference(TOASTS, TOASTS) else found
        return Verdict.Differed(
            shown,
            unexplained,
            allowed,
            EconomyChange.of(before, after),
            EconomyChange.of(before, native),
        )
    }

    /**
     * Adds where the toasts differ; answers true when they differ only as A-04 allows (a "not
     * saved" repeated right after itself).
     */
    private fun toasts(
        page: List<String>,
        native: List<String>,
        found: MutableList<Difference>,
    ): Boolean {
        if (page == native) return false
        if (KnownDifferences.singleUnsaved(page) == KnownDifferences.singleUnsaved(native))
            return true
        if (page.size != native.size) found += Difference("$TOASTS[#]", "$TOASTS[#]")
        for (i in 0 until minOf(page.size, native.size)) if (page[i] != native[i])
            found += Difference("$TOASTS[$i]", "$TOASTS[]")
        return false
    }

    companion object {
        const val RETURNS = "returns"
        const val TOASTS = "toasts"
        const val SAVES = "saves.count"
        const val JOURNAL = "saves.journal"
        const val IDS_EXTRA = "ids.extra"
        const val CODEC_ENCODE = "codec.encode"
    }
}
