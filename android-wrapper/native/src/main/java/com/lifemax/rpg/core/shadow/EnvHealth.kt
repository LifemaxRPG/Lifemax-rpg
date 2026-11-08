package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.adapters.HealthReads
import com.lifemax.rpg.core.economy.HealthConnect
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * One Health Connect read the page made during a sync, as its recording proxy kept it
 * (`50-native-shadow-points.js`, `recordingHealth`): the range it asked for and the plugin's
 * answer.
 */
class HealthRead<T>(val from: Long, val to: Long, val answer: T)

/**
 * `env.health` (ECONOMY_SHADOW_PLAN 2.3, N5) as an app that says `economy.shadow.day.v1` receives
 * it: whether `lmaPlugin('HealthSync')` answered at all ([plugin]), whether another sync was still
 * running ([busy], the page's `lmaHealthBusy`), the status answer and the instant the proxy took it
 * ([now]), every read in order with its range and answer, and the part that threw with its message
 * ([fail], [error]). Read strictly: an unknown key, a missing `plugin`, a wrong type or an answer
 * with fields the plugin does not send refuses the action ([InputRefused], `skipped.input`).
 */
class EnvHealth(
    val plugin: Boolean,
    val busy: Boolean,
    val now: Long?,
    val status: HealthConnect.Status?,
    val exercise: List<HealthRead<List<HealthConnect.Workout>>>,
    val steps: List<HealthRead<List<HealthConnect.StepDay>>>,
    val sleep: List<HealthRead<List<HealthConnect.SleepSession>>>,
    val weight: List<HealthRead<HealthConnect.Weight?>>,
    val fail: String?,
    val error: String,
) {
    /**
     * The instant the page's sync read the clock (`const now = Date.now()`, after the status
     * answer): every read's range ends there (`to: now`), so the first read gives it exactly;
     * without a read, the proxy's own reading right after the status answer (N5); without either,
     * null (the sync stopped before it read the clock).
     */
    val clock: Long?
        get() =
            listOf(exercise, steps, sleep, weight).firstNotNullOfOrNull { it.firstOrNull()?.to }
                ?: now

    companion object {
        private val KEYS =
            setOf(
                "plugin",
                "busy",
                "now",
                "status",
                "exercise",
                "steps",
                "sleep",
                "weight",
                "fail",
                "error",
            )
        private val STATUS = setOf("sdk", "granted")
        private val READ = setOf("from", "to", "answer")
        private val WORKOUT = setOf("id", "start", "end", "type", "title", "origin")
        private val STEP_DAY = setOf("date", "count")
        private val SLEEP = setOf("id", "start", "end", "origin")
        private val WEIGHT = setOf("kg", "time", "origin")

        /** The parts a read can fail in, the status first. */
        val PARTS = listOf("status", "exercise", "steps", "sleep", "weight")

        /** Most reads of one kind a sync can make (each is made once). */
        private const val MAX_READS = 1
        private const val MAX_ERROR_CHARS = 300

        /** `env.health`, strictly. */
        fun read(value: JsonValue): EnvHealth {
            val o = members(value, "env.health")
            for (k in o.keys) if (k !in KEYS) refuse("env.health")
            val plugin = (o["plugin"] as? JsonBool)?.value ?: refuse("env.health.plugin")
            val busy =
                when (val b = o["busy"]) {
                    null -> false
                    is JsonBool -> b.value
                    else -> refuse("env.health.busy")
                }
            val fail =
                o["fail"]?.let { (it as? JsonString)?.value?.takeIf { p -> p in PARTS } }
                    ?: if ("fail" in o) refuse("env.health.fail") else null
            val error =
                o["error"]?.let {
                    (it as? JsonString)?.value?.takeIf { e -> e.length <= MAX_ERROR_CHARS }
                } ?: if ("error" in o) refuse("env.health.error") else ""
            return EnvHealth(
                plugin = plugin,
                busy = busy,
                now = o["now"]?.let { whole(it, "env.health.now") },
                status = o["status"]?.let(::status),
                exercise =
                    reads(o["exercise"], "exercise") { a -> sessions(a, WORKOUT, ::workout) },
                steps = reads(o["steps"], "steps") { a -> days(a) },
                sleep = reads(o["sleep"], "sleep") { a -> sessions(a, SLEEP, ::sleepSession) },
                weight = reads(o["weight"], "weight") { a -> weight(a) },
                fail = fail,
                error = error,
            )
        }

        private fun status(value: JsonValue): HealthConnect.Status {
            val o = members(value, "env.health.status")
            for (k in o.keys) if (k !in STATUS) refuse("env.health.status")
            val sdk = (o["sdk"] as? JsonString)?.value ?: refuse("env.health.status.sdk")
            // the page reads `st.granted || []`
            val granted =
                when (val g = o["granted"]) {
                    null -> emptyList()
                    is JsonArray -> g.items.map { text(it, "env.health.status.granted") }
                    else -> refuse("env.health.status.granted")
                }
            return HealthConnect.Status(sdk, granted)
        }

        private fun <T> reads(
            value: JsonValue?,
            part: String,
            answer: (Map<String, JsonValue>) -> T,
        ): List<HealthRead<T>> {
            val at = "env.health.$part"
            val items = (value ?: return emptyList()) as? JsonArray ?: refuse(at)
            if (items.items.size > MAX_READS) refuse(at)
            return items.items.map {
                val r = members(it, at)
                if (r.keys != READ) refuse(at)
                HealthRead(
                    whole(r.getValue("from"), "$at.from"),
                    whole(r.getValue("to"), "$at.to"),
                    answer(members(r.getValue("answer"), "$at.answer")),
                )
            }
        }

        /** `{sessions: [...]}`: the page reads `r.sessions || []`. */
        private fun <T> sessions(
            answer: Map<String, JsonValue>,
            keys: Set<String>,
            one: (Map<String, JsonValue>) -> T,
        ): List<T> {
            for (k in answer.keys) if (k != "sessions") refuse("env.health.answer")
            val list = answer["sessions"] ?: return emptyList()
            return ((list as? JsonArray) ?: refuse("env.health.sessions")).items.map {
                val s = members(it, "env.health.sessions[]")
                for (k in s.keys) if (k !in keys) refuse("env.health.sessions[]")
                one(s)
            }
        }

        private fun workout(s: Map<String, JsonValue>) =
            HealthConnect.Workout(
                id = text(s["id"], "env.health.sessions[].id"),
                type = optional(s["type"]),
                start = whole(s["start"], "env.health.sessions[].start"),
                end = whole(s["end"], "env.health.sessions[].end"),
                title = optional(s["title"]),
                origin = optional(s["origin"]),
            )

        private fun sleepSession(s: Map<String, JsonValue>) =
            HealthConnect.SleepSession(
                id = text(s["id"], "env.health.sessions[].id"),
                start = whole(s["start"], "env.health.sessions[].start"),
                end = whole(s["end"], "env.health.sessions[].end"),
            )

        /** `{days: [{date, count}]}`: the page reads `r.days || []`. */
        private fun days(answer: Map<String, JsonValue>): List<HealthConnect.StepDay> {
            for (k in answer.keys) if (k != "days") refuse("env.health.answer")
            val list = answer["days"] ?: return emptyList()
            return ((list as? JsonArray) ?: refuse("env.health.days")).items.map {
                val d = members(it, "env.health.days[]")
                if (d.keys != STEP_DAY) refuse("env.health.days[]")
                val count = (d["count"] as? JsonNumber)?.value ?: refuse("env.health.days[].count")
                HealthConnect.StepDay(text(d["date"], "env.health.days[].date"), count)
            }
        }

        /**
         * `{}` without a reading, `{kg, time, origin}` with one; the page uses it when `kg` is set.
         */
        private fun weight(answer: Map<String, JsonValue>): HealthConnect.Weight? {
            for (k in answer.keys) if (k !in WEIGHT) refuse("env.health.weight")
            val kg = answer["kg"] ?: return null
            val value = (kg as? JsonNumber)?.value ?: refuse("env.health.weight.kg")
            val time = (answer["time"] as? JsonNumber)?.value ?: refuse("env.health.weight.time")
            return HealthConnect.Weight(value, time)
        }

        private fun members(value: JsonValue?, at: String): Map<String, JsonValue> =
            (value as? JsonObject)?.members ?: refuse(at)

        private fun text(value: JsonValue?, at: String): String =
            (value as? JsonString)?.value ?: refuse(at)

        private fun optional(value: JsonValue?): String? =
            when (value) {
                null -> null
                is JsonString -> value.value
                else -> refuse("env.health.sessions[]")
            }

        private fun whole(value: JsonValue?, at: String): Long {
            val n = (value as? JsonNumber)?.value ?: refuse(at)
            if (n != Math.rint(n) || n < 0 || n > MAX_INSTANT) refuse(at)
            return n.toLong()
        }

        /** Later than any instant a phone's clock reaches (year 275760). */
        private const val MAX_INSTANT = 8.64e15

        private fun refuse(at: String): Nothing = EntryInput.refuse(at)
    }
}

/**
 * The plugin as the page met it, for native's sync ([HealthReads] behind the economy's
 * `HealthAdapter`): each read native makes is answered with the page's recorded answer for that
 * part, in order, and checked against it. A read the page did not make, a range other than the
 * page's, or a recorded read native never makes is a difference (`reads.<part>`, [problems]): the
 * comparison names it, because native then asked Health Connect something the page did not. The
 * part that threw throws again, with the page's message, so native's `Sync failed: ...` is checked
 * too.
 */
class RecordedHealth(val env: EnvHealth) : HealthReads {
    private val used = mutableMapOf<String, Int>()

    /** Where native's reads differed from the page's, as difference paths. */
    val problems = ArrayList<String>()

    override fun status(): HealthConnect.Status {
        used["status"] = (used["status"] ?: 0) + 1
        if (env.fail == "status") throw ReadFailed(env.error)
        return env.status
            ?: HealthConnect.Status(UNANSWERED, emptyList()).also { problems += "reads.status" }
    }

    override fun exercise(from: Long, to: Long) =
        next("exercise", env.exercise, from, to, emptyList())

    override fun steps(from: Long, to: Long) = next("steps", env.steps, from, to, emptyList())

    override fun sleep(from: Long, to: Long) = next("sleep", env.sleep, from, to, emptyList())

    override fun weight(from: Long, to: Long) = next("weight", env.weight, from, to, null)

    /** Recorded reads native did not make, added to [problems]; call once, after the run. */
    fun finish(): List<String> {
        if (env.status != null && (used["status"] ?: 0) == 0) problems += "reads.status"
        for ((part, list) in
            listOf(
                "exercise" to env.exercise,
                "steps" to env.steps,
                "sleep" to env.sleep,
                "weight" to env.weight,
            )) if ((used[part] ?: 0) < list.size) problems += "reads.$part"
        return problems
    }

    private fun <T> next(
        part: String,
        reads: List<HealthRead<T>>,
        from: Long,
        to: Long,
        none: T,
    ): T {
        val i = used[part] ?: 0
        used[part] = i + 1
        val read = reads.getOrNull(i)
        if (read == null) {
            if (env.fail == part) throw ReadFailed(env.error)
            problems += "reads.$part"
            return none
        }
        if (read.from != from || read.to != to) problems += "reads.$part.range"
        return read.answer
    }

    /** A read that threw on the page, thrown again with the page's message. */
    class ReadFailed(message: String) : Exception(message) {
        override fun fillInStackTrace(): Throwable = this
    }

    private companion object {
        /** What native is told when it asks a status the page never asked for: nothing to sync. */
        const val UNANSWERED = "unavailable"
    }
}
