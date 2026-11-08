package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.economy.GoldenPaths
import com.lifemax.rpg.core.economy.SaveCodec
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * WP6d's golden cases as the page's transport sends them ([GoldenActions] builds the envelopes):
 * - a `lmaHealthSync` call's `env.health` in the page's recording form
 *   (`50-native-shadow-points.js`, `recordingHealth`, with `economy.shadow.day.v1`): the golden
 *   case records the plugin's answers by part (`{status, exercise, steps, sleep, weight, fail}`);
 *   the page's proxy records each read it made, in order, with the range it asked for. Which reads
 *   the page made follows from the case's state and answers as `lmaHealthSync` decides them
 *   (HEALTH-0: on or asked, the status, a grant of something wanted, then each type switched on and
 *   granted, stopping at the read that throws);
 * - an `ecoShieldTick` case as the decay pass with decay switched off: `applyDecay()` is then
 *   exactly `ecoShieldTick()` and `return false` (DECAY-3 step 1 and 2), so the shield tick's own
 *   golden cases reach native through the shadow point the page has for it.
 */
internal object DayGoldenActions {
    /** The page's `lmaHealthSync` reads, in order: toggle, part, days back, answer's list key. */
    private val READS =
        listOf(
            Triple("ex", "exercise", 7L) to "sessions",
            Triple("steps", "steps", 30L) to "days",
            Triple("sleep", "sleep", 8L) to "sessions",
            Triple("wt", "weight", 60L) to null,
        )

    private const val DAY_MILLIS = 86_400_000L

    /** The shield tick's golden entry, sent as the decay pass. */
    const val SHIELD_TICK = "ecoShieldTick"

    /**
     * `env.health` for one golden `lmaHealthSync(manual)` call at [now] on the page's state
     * [state]; [present] says whether the golden env names `health` at all (null there is "no
     * plugin").
     */
    fun health(
        golden: Map<String, Any?>?,
        state: Map<String, JsonValue>,
        manual: Boolean,
        now: Long,
    ): JsonObject {
        val out = LinkedHashMap<String, JsonValue>()
        out["plugin"] = JsonBool.of(golden != null)
        if (golden == null) return JsonObject(out)
        val h = (DayEntries.healthDefaults(JsonObject(state))["health"] as JsonObject).members
        fun on(key: String) = EntryInput.truthy(h[key])
        if (!on("on") && !manual) return JsonObject(out)
        val fail = golden["fail"] as? String
        if (fail == "status") return failed(out, "status")
        val status = SaveCodec.json(golden["status"]) as JsonObject
        out["status"] = status
        out["now"] = JsonNumber(now.toDouble())
        if ((status["sdk"] as? JsonString)?.value != "available") return JsonObject(out)
        val granted =
            (status["granted"] as? JsonArray)?.items.orEmpty().map { (it as JsonString).value }
        val want = READS.filter { on(it.first.first) }.map { it.first.second }
        if (want.any { it !in granted } && want.none { it in granted }) return JsonObject(out)
        for ((read, list) in READS) {
            val (toggle, part, days) = read
            if (!on(toggle) || part !in granted) continue
            if (fail == part) return failed(out, part)
            val answer =
                if (list == null) (SaveCodec.json(golden[part] ?: emptyMap<String, Any?>()))
                else FakeShadowPage.obj(list to SaveCodec.json(golden[part] ?: emptyList<Any?>()))
            out[part] =
                JsonArray(
                    listOf(
                        FakeShadowPage.obj(
                            "from" to JsonNumber((now - days * DAY_MILLIS).toDouble()),
                            "to" to JsonNumber(now.toDouble()),
                            "answer" to answer,
                        )
                    )
                )
        }
        return JsonObject(out)
    }

    private fun failed(out: LinkedHashMap<String, JsonValue>, part: String): JsonObject {
        out["fail"] = JsonString(part)
        out["error"] = JsonString(GoldenPaths.GoldenHealthReads.FAIL)
        return JsonObject(out)
    }

    /**
     * [case] with its shield ticks as decay passes over a state whose decay is off; any other case
     * as it is.
     */
    fun shieldAsDecay(case: Map<String, Any?>): Map<String, Any?> {
        val input = plain(case["input"])
        val calls = (input["calls"] as List<*>).map(::plain)
        if (calls.none { it["fn"] == SHIELD_TICK }) return case
        val state = plain(input["state"])
        val decay = plain(state["decay"]) + ("on" to false)
        val output = plain(case["output"])
        val returns =
            (output["returns"] as List<*>).mapIndexed { i, r ->
                if (calls[i]["fn"] == SHIELD_TICK) false else r
            }
        return case +
            mapOf(
                "input" to
                    input +
                        mapOf(
                            "state" to state + ("decay" to decay),
                            "calls" to
                                calls.map {
                                    if (it["fn"] == SHIELD_TICK) it + ("fn" to "applyDecay") else it
                                },
                        ),
                "output" to output + ("returns" to returns),
            )
    }

    @Suppress("UNCHECKED_CAST")
    private fun plain(v: Any?): Map<String, Any?> = v as? Map<String, Any?> ?: emptyMap()
}
