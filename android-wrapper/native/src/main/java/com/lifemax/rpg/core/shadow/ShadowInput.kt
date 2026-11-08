package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.adapters.PlanFacts
import com.lifemax.rpg.core.economy.jsNumber
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * Why an action's own input could not be read. [at] names the place with fixed words only
 * (`args[0].type`, `env.dom`), never a value; the action is counted `skipped.input` and not
 * compared.
 */
class InputRefused(val at: String) : Exception(at) {
    override fun fillInStackTrace(): Throwable = this
}

/**
 * The `env` parts one kind may carry beside `saves` (ECONOMY_SHADOW_PLAN 2.3): at most [confirms]
 * answers to `confirm()`, at most [prompts] answers to `prompt()`, the input values [dom] by
 * element id (or any id starting [domPrefix]) and the plan facts ([plan]: the kind records the full
 * window of 2.3; every other kind may record today's alone, `economy.shadow.quests.v1`). Anything
 * else refuses.
 */
data class EnvShape(
    val confirms: Int = 0,
    val prompts: Int = 0,
    val dom: Set<String> = emptySet(),
    val domPrefix: String? = null,
    val plan: Boolean = false,
) {
    companion object {
        /** Only `saves`. */
        val SAVES = EnvShape()
    }
}

/** `env.plan` (2.3): whether today's plan day is done, and its rest days in `[from, today]`. */
data class EnvPlan(val done: Boolean, val rest: Set<String>, val from: String)

/**
 * The plan as the page answered it for this action ([EnvPlan]), for the adapters' [PlanFacts]. A
 * question the recording cannot answer (another day's `dayDone`, a rest day before `from`) is
 * answered as the page's empty plan would and remembered in [outside], so the action is counted
 * `skipped.planWindow` instead of guessed. Without a recording every question is outside.
 */
class EnvPlanFacts(private val plan: EnvPlan?, private val today: String) : PlanFacts {
    /** Whether the economy asked something the recording does not hold. */
    var outside = false
        private set

    override fun isDone(day: String): Boolean {
        if (plan == null || day != today) return false.also { outside = outside || plan != null }
        return plan.done
    }

    override fun isRestDay(day: String): Boolean {
        if (plan == null || day < plan.from || day > today) {
            outside = true
            return false
        }
        return day in plan.rest
    }
}

/**
 * One action's arguments and outside answers, read strictly for its kind: the env parts must be the
 * kind's ([EnvShape]) with exactly the types the page records, and each argument the type the kind
 * reads. The JavaScript readings the page itself applies (`String(v)`, `Number(v)`, truthiness,
 * ECONOMY_SPEC NUM-3) are here so every kind reads alike. Throws [InputRefused] only.
 */
class EntryInput(envelope: ShadowEnvelope, shape: EnvShape) {
    /** The arguments in the page's JSON normal form. */
    val args: List<JsonValue> = envelope.args.items

    /** The owner's answers to `confirm()`, in order. */
    val confirms: List<Boolean>

    /** The owner's answers to `prompt()`, in order (null for a cancel). */
    val prompts: List<String?>

    /** The input values the entry read, by element id. */
    val dom: Map<String, JsonValue>

    /** The plan facts, when the kind records them. */
    val plan: EnvPlan?

    init {
        val rest = envelope.env.rest.members
        for (part in rest.keys) if (!allowed(part, shape)) refuse("env.$part")
        confirms =
            answers(rest["confirm"], "env.confirm", shape.confirms).map {
                (it as? JsonBool)?.value ?: refuse("env.confirm[]")
            }
        prompts =
            answers(rest["prompt"], "env.prompt", shape.prompts).map {
                when (it) {
                    is JsonString -> it.value
                    JsonNull -> null
                    else -> refuse("env.prompt[]")
                }
            }
        dom = dom(rest["dom"], shape)
        plan = rest["plan"]?.let(::plan)
    }

    /** Refuses unless there are between [min] and [max] arguments. */
    fun count(min: Int, max: Int = min) {
        if (args.size < min || args.size > max) refuse("args")
    }

    /** Argument [i], or null past the end (JavaScript's `undefined`, sent as nothing). */
    fun arg(i: Int): JsonValue? = args.getOrNull(i)

    /** Argument [i] as text, which it must be. */
    fun string(i: Int): String = (arg(i) as? JsonString)?.value ?: refuse("args[$i]")

    /** Argument [i] as an object, which it must be. */
    fun obj(i: Int): JsonObject = arg(i) as? JsonObject ?: refuse("args[$i]")

    /** Argument [i] as `String(v)` reads it; text, a number, a boolean or nothing only. */
    fun text(i: Int): String = textOf(arg(i), "args[$i]")

    /** Argument [i] as `Number(v)` reads it ([NaN] when absent); a scalar only. */
    fun number(i: Int): Double = numberOf(arg(i), i < args.size, "args[$i]")

    /** Argument [i] as JavaScript truthiness reads it. */
    fun truthy(i: Int): Boolean = truthy(arg(i))

    /** The `i`th confirm answer; true when the page did not ask (nothing to refuse). */
    fun confirmed(i: Int = 0): Boolean = confirms.getOrElse(i) { true }

    /** A dom value as `el.value` (text) reads it; blank when the element was not there. */
    fun domText(id: String): String = textOf(dom[id], "env.dom")

    /** A checkbox's `checked`; false when the element was not there. */
    fun domChecked(id: String): Boolean = (dom[id] as? JsonBool)?.value ?: false

    private fun allowed(part: String, shape: EnvShape): Boolean =
        when (part) {
            "confirm" -> shape.confirms > 0
            "prompt" -> shape.prompts > 0
            "dom" -> shape.dom.isNotEmpty() || shape.domPrefix != null
            // from WP6c every kind may carry today's plan facts: any save can deal and verify
            // the Daily Hand, which asks whether today is a rest day and whether it is done
            "plan" -> true
            else -> false
        }

    /** The recorded answers of one kind of dialog: a list of at most [max]. */
    private fun answers(value: JsonValue?, at: String, max: Int): List<JsonValue> {
        if (value == null) return emptyList()
        val items = (value as? JsonArray)?.items ?: refuse(at)
        if (items.size > max) refuse(at)
        return items
    }

    private fun dom(value: JsonValue?, shape: EnvShape): Map<String, JsonValue> {
        if (value == null) return emptyMap()
        val o = (value as? JsonObject)?.members ?: refuse("env.dom")
        for ((id, v) in o) {
            val known = id in shape.dom || shape.domPrefix?.let { id.startsWith(it) } == true
            if (!known || !(v is JsonString || v is JsonBool) || id.length > MAX_ID_CHARS)
                refuse("env.dom")
        }
        return o
    }

    private fun plan(value: JsonValue): EnvPlan {
        val o = (value as? JsonObject)?.members ?: refuse("env.plan")
        if (o.keys != PLAN_KEYS) refuse("env.plan")
        val done = (o["done"] as? JsonBool)?.value ?: refuse("env.plan.done")
        val from =
            (o["from"] as? JsonString)?.value?.takeIf(DAY::matches) ?: refuse("env.plan.from")
        val rest =
            ((o["rest"] as? JsonArray)?.items ?: refuse("env.plan.rest")).map {
                (it as? JsonString)?.value?.takeIf(DAY::matches) ?: refuse("env.plan.rest[]")
            }
        if (rest.size > MAX_PLAN_DAYS) refuse("env.plan.rest")
        return EnvPlan(done, rest.toSet(), from)
    }

    companion object {
        private val PLAN_KEYS = setOf("done", "rest", "from")
        private val DAY = Regex("\\d{4}-\\d\\d-\\d\\d")
        private const val MAX_PLAN_DAYS = 401
        private const val MAX_ID_CHARS = 64

        /**
         * `String(v)` of a form value or argument: blank for nothing, whole numbers without `.0`.
         */
        fun textOf(value: JsonValue?, at: String): String =
            when (value) {
                null,
                JsonNull -> ""
                is JsonString -> value.value
                is JsonBool -> value.value.toString()
                is JsonNumber -> numberText(value.value)
                else -> refuse(at)
            }

        /** `Number(v)` (NUM-3): absent is NaN, null and blank are 0. */
        fun numberOf(value: JsonValue?, present: Boolean, at: String): Double =
            when {
                !present || value == null -> Double.NaN
                value == JsonNull -> 0.0
                value is JsonNumber -> value.value
                value is JsonBool -> if (value.value) 1.0 else 0.0
                value is JsonString -> jsNumber(value.value)
                else -> refuse(at)
            }

        /** JavaScript truthiness of a JSON value. */
        fun truthy(value: JsonValue?): Boolean =
            when (value) {
                null,
                JsonNull -> false
                is JsonBool -> value.value
                is JsonNumber -> value.value != 0.0 && !value.value.isNaN()
                is JsonString -> value.value.isNotEmpty()
                else -> true
            }

        /** A member of [o] as text (`String(v)`), refusing a structure. */
        fun member(o: JsonObject, key: String, at: String): String = textOf(o[key], "$at.$key")

        private fun numberText(n: Double): String =
            if (n == Math.rint(n) && !n.isInfinite() && Math.abs(n) < WHOLE_LIMIT)
                n.toLong().toString()
            else n.toString()

        private const val WHOLE_LIMIT = 1e21

        fun refuse(at: String): Nothing = throw InputRefused(at)
    }
}
