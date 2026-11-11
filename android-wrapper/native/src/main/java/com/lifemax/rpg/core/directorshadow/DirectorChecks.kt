package com.lifemax.rpg.core.directorshadow

import com.lifemax.rpg.core.directorstore.DirectorStoreCodec
import com.lifemax.rpg.core.directorstore.PageJson
import com.lifemax.rpg.core.directorstore.StoreDecode
import com.lifemax.rpg.core.directorstore.StoreEncode
import com.lifemax.rpg.core.directorstore.StoreReading
import com.lifemax.rpg.core.directorstore.StoreRefusal
import com.lifemax.rpg.core.lifedirector.PlanSchedule
import com.lifemax.rpg.core.lifedirector.Reschedule
import com.lifemax.rpg.core.lifedirector.readmodels.ReadModelWords
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonRead
import com.lifemax.rpg.core.save.JsonReader
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.shadow.Difference
import com.lifemax.rpg.core.shadow.ShadowRedactor
import com.lifemax.rpg.core.shadow.Verdict
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId

/** A check's verdict, and what it found that is not a difference ([notes]: `strict.*`, SH-11). */
data class CheckResult(val verdict: Verdict, val notes: List<String> = emptyList())

/**
 * The three Life Director checks (SH-9 to SH-17). Each runs native's own code on what the page
 * worked from and compares with what the page got; each only reads its inputs and writes nothing.
 * - [save]: the store codec's round trip (WP-LD7) on the exact text the page's store just wrote.
 * - [read]: the day, now and week read models (WP-LD8a) on the page's state at the moment the page
 *   computed its own.
 * - [engine]: placement and local repair (WP-LD4, WP-LD5) on the engine input the page planned
 *   from.
 *
 * @param words the page words the read models use (the packaged export in the app).
 * @param zoneOffset native's offset, in seconds, of a zone at an instant (SH-18), null when native
 *   does not know the zone.
 */
class DirectorChecks(
    redactor: ShadowRedactor,
    private val words: () -> ReadModelWords = { ReadModelWords.packaged },
    private val zoneOffset: (String, Long) -> Int? = ::javaOffset,
) {
    private val paths = JsonPaths(redactor)
    private val commands = CommandCheck(paths)

    /**
     * SH-9 to SH-11. [text] is the stitched text; the page said its store wrote [bytes] characters.
     */
    fun save(text: String): CheckResult {
        val notes = ArrayList<String>()
        val decoded =
            when (val d = DirectorStoreCodec.decode(text, StoreReading.AS_PAGE)) {
                is StoreDecode.Refused ->
                    return CheckResult(
                        if (d.refusal == StoreRefusal.TooDeep)
                            differed(
                                listOf(Difference("store.decode.tooDeep", "store.decode.tooDeep")),
                                setOf(DA_DEEP),
                            )
                        else
                            differed(
                                listOf(
                                    Difference("store.decode." + word(d.refusal), "store.decode")
                                )
                            )
                    )
                is StoreDecode.Decoded -> d.state
            }
        (DirectorStoreCodec.decode(text, StoreReading.STRICT) as? StoreDecode.Refused)?.let {
            notes += "strict." + word(it.refusal)
        }
        val found = ArrayList<Difference>()
        val written = PageJson.text(decoded.document)
        if (written != text)
            found += Difference("store.text" + firstMember(decoded.document, text), "store.text")
        when (val e = DirectorStoreCodec.encode(decoded.document)) {
            // an owned field of a kind the page never writes there is the strict reader's finding
            // (above), not a difference
            is StoreEncode.Refused ->
                if (e.refusal is StoreRefusal.WrongType) notes += "encode." + word(e.refusal)
                else found += Difference("store.encode." + word(e.refusal), "store.encode")
            is StoreEncode.Encoded ->
                if (e.text != text) found += Difference("store.encode", "store.encode")
        }
        return CheckResult(if (found.isEmpty()) Verdict.Same else differed(found), notes)
    }

    /** The top-level member whose text differs first (the page's order), as `.name`. */
    private fun firstMember(doc: JsonObject, text: String): String {
        val page = (JsonReader.read(text) as? JsonRead.Parsed)?.value as? JsonObject ?: return ""
        for ((k, v) in page.members) {
            val mine = doc.members[k] ?: return "." + paths.member(k)
            if (PageJson.text(mine) != PageJson.preserving(v)) return "." + paths.member(k)
        }
        return ""
    }

    /** SH-12 to SH-14: every read model the page computed, at the page's moment, on [state]. */
    fun read(state: JsonValue, facts: ReadFacts): CheckResult {
        tzdata(facts.planZone, facts.nowMillis, facts.planOffset)?.let {
            return CheckResult(it)
        }
        val mine = DirectorReads.compute(state, facts, words())
        val diffs = ArrayList<PathDifference>()
        for (section in DirectorReads.SECTIONS) {
            if (section !in facts.out.members && section !in mine.members) continue
            diffs += paths.diff(facts.out.members[section], mine.members[section], "read.$section")
        }
        return CheckResult(verdict(diffs))
    }

    /** SH-15 to SH-17: the plan (or repair) native makes from the page's engine input. */
    fun engine(kind: String, facts: EngineFacts): CheckResult {
        val now =
            (facts.input as? JsonObject)?.let { input ->
                val engineInput =
                    if (kind == DirectorShadowJson.REPAIR) input.members["engineInput"] else input
                ((engineInput as? JsonObject)?.members?.get("currentTimeEpoch")
                        as? com.lifemax.rpg.core.save.JsonNumber)
                    ?.value
            }
        if (now != null && now.isFinite())
            tzdata(facts.planZone, (now * 1000).toLong(), facts.planOffset)?.let {
                return CheckResult(it)
            }
        val mine =
            DirectorReads.section {
                if (kind == DirectorShadowJson.PLAN) withoutTime(PlanSchedule.generate(facts.input))
                else repairJson(facts.input)
            }
        val page =
            if (kind == DirectorShadowJson.PLAN) withoutTime(facts.output)
            else normalRepair(facts.output)
        val at = if (kind == DirectorShadowJson.PLAN) "plan" else "repair"
        return CheckResult(verdict(paths.diff(page, mine, at)))
    }

    /**
     * SH-36 to SH-46: the command (or tick) native runs on [before] with the page's host answers,
     * compared with the page's reply, host calls, memory and [after]; [t1] is when the page's
     * command ended; [known] gives a take-back sent in full earlier.
     */
    fun command(
        before: JsonValue,
        after: JsonValue,
        facts: CommandFacts,
        t1: Long,
        known: (String) -> com.lifemax.rpg.core.lifedirector.host.TakeBackState?,
    ): CommandResult {
        tzdata(facts.planZone, facts.nowMs, facts.planOffset)?.let {
            return CommandResult(it)
        }
        return commands.run(before, after, facts, t1, known)
    }

    private fun repairJson(input: JsonValue): JsonValue {
        val r = Reschedule.run(input)
        return normalRepair(
            JsonObject(
                linkedMapOf(
                    "updatedSchedule" to r.updatedSchedule,
                    "engineInput" to (r.engineInput ?: JsonNull),
                    "changeRecord" to (r.changeRecord ?: JsonNull),
                    "rejectedMove" to (r.rejectedMove ?: JsonNull),
                )
            )
        )
    }

    /** `rescheduleActivity`'s answer with no times and no empty members (SH-16). */
    private fun normalRepair(v: JsonValue): JsonValue {
        val o = v as? JsonObject ?: return v
        val m = LinkedHashMap<String, JsonValue>()
        for ((k, x) in o.members) {
            if (x == JsonNull) continue
            m[k] = if (k == "updatedSchedule" || k == "changeRecord") withoutTime(x) else x
        }
        return JsonObject(m)
    }

    private fun withoutTime(v: JsonValue): JsonValue =
        (v as? JsonObject)?.let {
            JsonObject(LinkedHashMap(it.members).also { m -> m.remove(TIME) })
        } ?: v

    /** SH-18: the plan zone's offset at the moment, page's and native's; different is `tzdata`. */
    private fun tzdata(zone: String, millis: Long, page: Int?): Verdict? {
        page ?: return null
        val mine = zoneOffset(zone, millis) ?: return Verdict.Skipped(TZDATA)
        return if (mine != page) Verdict.Skipped(TZDATA) else null
    }

    private fun verdict(diffs: List<PathDifference>): Verdict {
        if (diffs.isEmpty()) return Verdict.Same
        val found = diffs.map { Difference(it.path, if (it.lastBit) LAST_BIT else it.path) }
        val unexplained = diffs.filter { !it.lastBit }.map { Difference(it.path, it.path) }
        return Verdict.Differed(
            found,
            unexplained,
            if (diffs.any { it.lastBit }) setOf(DA_FLOAT) else emptySet(),
            null,
            null,
        )
    }

    private fun differed(found: List<Difference>, allowed: Set<String> = emptySet()): Verdict =
        Verdict.Differed(found, if (allowed.isEmpty()) found else emptyList(), allowed, null, null)

    private fun word(r: StoreRefusal): String =
        when (r) {
            is StoreRefusal.NotJson -> "notJson"
            StoreRefusal.TooDeep -> "tooDeep"
            is StoreRefusal.PageWouldThrow -> "pageWouldThrow"
            is StoreRefusal.Invalid -> "invalid"
            is StoreRefusal.WrongType ->
                "wrongType." + r.path.split('.').joinToString(".") { paths.member(it) }
            is StoreRefusal.NonFinite -> "nonFinite"
            is StoreRefusal.NotFaithful -> "notFaithful"
        }

    companion object {
        /** Allowlist (SH-33): a text deeper than native reads, which the page reads (DV-1). */
        const val DA_DEEP = "DA-1"

        /** Allowlist (SH-33): a number one unit in the last place apart (OQ-12, OQ-15). */
        const val DA_FLOAT = "DA-2"
        private const val LAST_BIT = "lastBit"
        private const val TZDATA = "tzdata"
        private const val TIME = "computeTimeMs"

        fun javaOffset(zone: String, millis: Long): Int? =
            try {
                ZoneId.of(zone).rules.getOffset(Instant.ofEpochMilli(millis)).totalSeconds
            } catch (_: DateTimeException) {
                null
            }
    }
}
