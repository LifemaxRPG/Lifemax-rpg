package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.adapters.at
import com.lifemax.rpg.core.economy.Decay
import com.lifemax.rpg.core.economy.Health
import com.lifemax.rpg.core.economy.Storage
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * The kinds WP6d compares (ECONOMY_SHADOW_PLAN 1.2): the Health Connect sync and the day roll's
 * decay pass, at boot and on a return to the app. Each is the economy's own code ([Health.sync],
 * [Decay.apply], which ticks the streak shields first) with the page's glue around it:
 * - `health.sync` (`lmaHealthSync(manual)`, async): `lmaHealthCfg()` first ([healthDefaults]), then
 *   nothing more when the plugin is missing (a toast when asked), when the sync is off and was not
 *   asked for, or when another sync is still running (`env.health.busy`); otherwise the sync over
 *   the page's recorded plugin answers ([RecordedHealth]) on the clock the page read (N5,
 *   [EnvHealth.clock]), and the `save()` of its `finally`, composed as every save is (the Daily
 *   Hand's checks included). The page's display snapshot (`health.snap`) and steps goal
 *   (`health.goal`) are not economy fields (`SaveFields.PARTIAL`), so they are not compared.
 * - `decay.apply` (`applyDecay()` when the page becomes visible): the pass and its return value;
 *   the page's `save()` after a pass that changed something is a `save.other` of its own.
 * - `boot.decay` (the boot's `applyDecay()`, SAVE-6, D29): the pass, then one plain `writeSave()`
 *   when it changed something (no rebuild, no Daily Hand check); the boot returns nothing.
 *
 * Both decay kinds need the plan's rest days over the whole walk (`env.plan`, from the earliest day
 * the walk or the shield tick can visit); an envelope without them comes from a page older than
 * `economy.shadow.day.v1` and is not compared (`skipped.input`).
 */
internal object DayEntries {
    val ENTRIES: List<ShadowEntry> =
        listOf(
            ShadowEntry("health.sync", EnvShape(health = true), prepare = ::healthDefaults) {
                input,
                s,
                ctx,
                _ ->
                input.count(0, MAX_IGNORED_ARGS)
                val recorded = input.health ?: EntryInput.refuse("env.health")
                val env = recorded.env
                if (env.plugin && env.busy) EntryRun(s, JsonNull, emptyList())
                else {
                    val plugin = if (env.plugin) recorded.at(ctx.clock) else null
                    EntryRun.nothing(Health.sync(s, plugin, input.truthy(0), ctx))
                }
            },
            ShadowEntry("decay.apply", EnvShape(plan = true)) { input, s, ctx, _ ->
                input.count(0, MAX_IGNORED_ARGS)
                if (input.plan == null) EntryInput.refuse("env.plan")
                EntryRun.of(Decay.apply(s, ctx)) { JsonBool.of(it) }
            },
            ShadowEntry("boot.decay", EnvShape(plan = true)) { input, s, ctx, _ ->
                input.count(0)
                if (input.plan == null) EntryInput.refuse("env.plan")
                val pass = Decay.apply(s, ctx)
                // `if(_dc){state.customRules=rules;writeSave()}`: a plain write, SAVE-6
                if (pass.value) ctx.attempts?.attempted(ctx.storage == Storage.Writable)
                EntryRun(pass.save, JsonNull, pass.toasts)
            },
        )

    /**
     * `lmaHealthCfg()`, which every sync runs first: a missing or non-object `health` becomes the
     * page's default settings, a falsy `seen` an empty object and a `granted` that is not a list an
     * empty list. A `health` the page holds as a list (JavaScript's `typeof` says object, and
     * `JSON.stringify` keeps none of the members set on it) is not compared.
     */
    fun healthDefaults(state: JsonObject): JsonObject {
        val health = state["health"]
        if (health is JsonArray) EntryInput.refuse("health")
        val h = (health as? JsonObject)?.members ?: return state.with("health", DEFAULT_HEALTH)
        val seen = EntryInput.truthy(h["seen"])
        val granted = h["granted"] is JsonArray
        if (seen && granted) return state
        val out = LinkedHashMap(h)
        if (!seen) out["seen"] = JsonObject.EMPTY
        if (!granted) out["granted"] = JsonArray(emptyList())
        return state.with("health", JsonObject(out))
    }

    private fun JsonObject.with(key: String, value: JsonValue): JsonObject =
        JsonObject(LinkedHashMap(members).apply { put(key, value) })

    /** `lmaHealthCfg()`'s new settings, in the page's key order. */
    private val DEFAULT_HEALTH =
        JsonObject(
            linkedMapOf<String, JsonValue>(
                "on" to JsonBool.FALSE,
                "ex" to JsonBool.TRUE,
                "steps" to JsonBool.TRUE,
                "sleep" to JsonBool.TRUE,
                "wt" to JsonBool.TRUE,
                "seen" to JsonObject.EMPTY,
                "last" to JsonNumber(0.0),
                "since" to JsonNumber(0.0),
                "msg" to JsonString(""),
                "sdk" to JsonString(""),
                "granted" to JsonArray(emptyList()),
                "src" to JsonString(""),
                "today" to JsonNull,
            )
        )

    /** A timer or listener may pass the entry an event; it is ignored. */
    private const val MAX_IGNORED_ARGS = 1
}
