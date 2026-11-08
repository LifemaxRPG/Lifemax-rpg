package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.golden.GoldenFiles
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import java.io.File

/**
 * Replays WP6f's real-page proof corpora (`tools/gen_shadow_sim_corpus.js`: the seeded runs and the
 * fixture saves' scripted days) as the app receives them. A run is one seed: one native session
 * across its page sessions, each page session configured with its own `sid` (as the app sees a new
 * page after a reload), its base parts and envelopes in the order the page sent them, each envelope
 * observed at its own `t0` (so the resync and error windows run on the page's clock).
 */
internal object SimReplay {
    /** One run as the app saw it. */
    class Run(
        val name: String,
        val seed: Int,
        val ledger: ShadowLedger,
        /** Envelopes the page sent. */
        val envelopes: Int,
        /** One line per envelope: its kind, and for one not matched, why. */
        val outcomes: List<String>,
        /** The page's own cost per action, in ms, as the envelopes carry it. */
        val pageCosts: List<Double>,
        /** Envelopes whose writes the storage refused (the failure injection). */
        val refusedSaves: Int,
    ) {
        /** Envelopes skipped, by reason. */
        val skipped: Map<String, Int>
            get() =
                ledger.kinds.values
                    .flatMap { it.skipped.entries }
                    .groupingBy { it.key }
                    .fold(0) { a, e -> a + e.value }

        /** The unexplained differences' lines, for a failure message. */
        val unexplained: List<String>
            get() = ledger.mismatches().filter { it.unexplained }.map { "${it.kind} ${it.paths}" }
    }

    /**
     * Skips a run may have, each a rule of the plan, never a fault: a sync another action
     * interleaved (2.5), an action that crossed midnight (N3).
     */
    val EXPLAINED_SKIPS = setOf("interleaved", "midnight")

    /** The runs of a corpus: [override] (a file path, for one seed rerun) or the named fixture. */
    fun runs(
        name: String,
        override: String? = System.getProperty(FILE_PROPERTY),
    ): List<JsonObject> {
        val text =
            if (override.isNullOrEmpty()) GoldenFiles.text(name)
            else GoldenFiles.textOf(File(override))
        val all = (FakeShadowPage.parse(text) as JsonArray).items.map { it as JsonObject }
        val only = System.getProperty(SEED_PROPERTY)?.toIntOrNull() ?: return all
        return all.filter { seed(it) == only }
    }

    fun seed(run: JsonObject): Int = (run["seed"] as JsonNumber).value.toInt()

    /** Replays one run in a fresh native session. */
    fun replay(run: JsonObject, mutation: EntryMutation = EntryMutation.NONE): Run {
        val s =
            ShadowSession(
                ShadowSessionTest.FP,
                ShadowLedger(0, ByteArray(ShadowLimits.SALT_BYTES)),
                1,
                NATIVE_API,
                mutation = mutation,
            )
        val outcomes = ArrayList<String>()
        val costs = ArrayList<Double>()
        var envelopes = 0
        var refusedSaves = 0
        var now = 0L
        for (session in (run["sessions"] as JsonArray).items) {
            val messages = ((session as JsonObject)["messages"] as JsonArray).items
            val texts =
                messages.map {
                    val m = it as JsonObject
                    (m["m"] as JsonString).value to (m["text"] as JsonString).value
                }
            if (texts.isEmpty()) continue
            val sid = SID.find(texts.first().second)!!.groupValues[1]
            s.configure(ShadowSessionTest.configure(ShadowSessionTest.FP, sid = sid), false)
            for ((method, text) in texts) {
                if (method == "base") {
                    check(s.base(text, now).ok) { "${name(run)}: a base part refused" }
                    continue
                }
                envelopes++
                if (REFUSED.containsMatchIn(text)) refusedSaves++
                now = T0.find(text)?.groupValues?.get(1)?.toLong() ?: now
                COST.find(text)?.groupValues?.get(1)?.toDouble()?.let { costs += it }
                val kind = KIND.find(text)!!.groupValues[1]
                val l = s.ledger
                val was = l.same + l.allowed
                val accept = s.accept(text.length, false)
                if (!accept.accepted) {
                    outcomes += "$kind refused at once: ${accept.reason}"
                    continue
                }
                s.observe(text, now)
                outcomes +=
                    kind +
                        if (l.same + l.allowed > was) ""
                        else " " + l.kind(kind).skipped + l.mismatches().lastOrNull()?.paths
            }
        }
        return Run(name(run), seed(run), s.ledger, envelopes, outcomes, costs, refusedSaves)
    }

    private fun name(run: JsonObject) = (run["name"] as JsonString).value

    /** Every problem of a run: unexplained differences, skips the plan does not name, refusals. */
    fun problems(r: Run): List<String> {
        val out = ArrayList<String>()
        val l = r.ledger
        val explained = r.skipped.filterKeys { it in EXPLAINED_SKIPS }.values.sum()
        if (l.compared != r.envelopes - explained)
            out += "${r.name}: ${r.envelopes} sent, ${l.compared} compared, skipped ${r.skipped}"
        if (l.different != 0) out += "${r.name}: ${l.different} unexplained: ${r.unexplained}"
        if (l.refused.isNotEmpty() || l.errors.isNotEmpty())
            out += "${r.name}: refused ${l.refused} errors ${l.errors}"
        if (l.state != ShadowState.ON) out += "${r.name}: the check is ${l.state} (${l.reason})"
        return out
    }

    /** -Dshadow.sim.file=PATH replays a corpus written by `gen_shadow_sim_corpus.js --seed N`. */
    const val FILE_PROPERTY = "shadow.sim.file"

    /** -Dshadow.sim.seed=N replays only that seed of the corpus. */
    const val SEED_PROPERTY = "shadow.sim.seed"

    private const val NATIVE_API = 112
    private val SID = Regex("\"sid\":\"([^\"]+)\"")
    private val KIND = Regex("\"kind\":\"([^\"]+)\"")
    private val T0 = Regex("\"t0\":(\\d+)")
    private val REFUSED = Regex("\"saves\":\\[[^\\]]*\"ok\":false")
    private val COST = Regex("\"cost\":\\{\"ms\":([0-9.]+)}")
}
