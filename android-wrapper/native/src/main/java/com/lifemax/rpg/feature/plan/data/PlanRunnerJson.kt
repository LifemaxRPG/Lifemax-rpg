package com.lifemax.rpg.feature.plan.data

import com.lifemax.rpg.core.data.objects
import com.lifemax.rpg.feature.plan.domain.DurationAsk
import com.lifemax.rpg.feature.plan.domain.PipState
import com.lifemax.rpg.feature.plan.domain.PlanFeatures
import com.lifemax.rpg.feature.plan.domain.PlanOffer
import com.lifemax.rpg.feature.plan.domain.PlanOfferRow
import com.lifemax.rpg.feature.plan.domain.PlanRunner
import com.lifemax.rpg.feature.plan.domain.PlanWrite
import com.lifemax.rpg.feature.plan.domain.RunnerComplete
import com.lifemax.rpg.feature.plan.domain.RunnerDay
import com.lifemax.rpg.feature.plan.domain.RunnerIntro
import com.lifemax.rpg.feature.plan.domain.RunnerItinerary
import com.lifemax.rpg.feature.plan.domain.RunnerLogRow
import com.lifemax.rpg.feature.plan.domain.RunnerPhase
import com.lifemax.rpg.feature.plan.domain.RunnerReview
import com.lifemax.rpg.feature.plan.domain.RunnerRow
import com.lifemax.rpg.feature.plan.domain.RunnerSegment
import com.lifemax.rpg.feature.plan.domain.RunnerStep
import com.lifemax.rpg.feature.plan.domain.RunnerTimer
import com.lifemax.rpg.feature.plan.domain.RunnerWords
import com.lifemax.rpg.feature.plan.domain.WeightField
import org.json.JSONObject

/*
 * Strict decoders for what the page adds to the Workout plan projection when the screen takes over
 * the guided workout, the fields, Leave plan and the sign-up offers. A missing or wrong-kind field
 * throws, so the repository keeps its last good state; only `features` may be absent, which is
 * how a page without these parts looks.
 */

/**
 * What the page lets the screen take over; a page that sends none is the one before these parts.
 */
internal fun JSONObject.toFeatures(): PlanFeatures =
    optJSONObject("features")?.let {
        PlanFeatures(
            it.getBoolean("runner"),
            it.getBoolean("fields"),
            it.getBoolean("leave"),
            it.getBoolean("offers"),
        )
    } ?: PlanFeatures()

internal fun JSONObject.toOffer(): PlanOffer {
    val rows =
        getJSONArray("rows").objects().map {
            PlanOfferRow(it.getString("when"), it.getString("what"))
        }
    require(rows.isNotEmpty()) { "An offer without days" }
    return PlanOffer(
        getString("eyebrow"),
        getString("title"),
        getString("blurb"),
        rows,
        getString("note"),
        getString("startLabel"),
        getString("start").also { require(DATE.matches(it)) { "The offer's start is not a date" } },
        getString("button"),
    )
}

internal fun JSONObject.toRunner(): PlanRunner {
    val phase = RunnerPhase.of(getString("phase"))
    val elapsed = getJSONObject("elapsed")
    val progress = getJSONObject("progress")
    val route =
        getJSONArray("route").objects().map {
            RunnerSegment(it.getString("state"), it.getInt("weight"), it.getInt("fill"))
        }
    val itinerary = getJSONObject("itinerary")
    val rows =
        itinerary.getJSONArray("rows").objects().map {
            RunnerRow(
                it.getInt("number"),
                it.getString("name"),
                it.getString("status"),
                it.getBoolean("current"),
            )
        }
    require(route.isNotEmpty() && route.size == rows.size) { "The route and the itinerary differ" }
    val runner =
        PlanRunner(
            phase,
            getString("date"),
            getString("title"),
            elapsed.getLong("ms"),
            elapsed.getBoolean("running"),
            progress.getInt("done"),
            progress.getInt("total"),
            route,
            RunnerItinerary(itinerary.getString("title"), rows),
            optObject("intro")?.toIntro(),
            optObject("step")?.toStep(),
            optObject("timer")?.toTimer(),
            optObject("review")?.let { RunnerReview(it.getString("time"), it.getBoolean("full")) },
            optObject("rest")?.toTimer(),
            optObject("complete")?.toComplete(),
            getJSONObject("words").toWords(),
        )
    // each phase carries what it draws, and nothing else is needed
    when (phase) {
        RunnerPhase.INTRO -> requireNotNull(runner.intro) { "An intro without its briefing" }
        RunnerPhase.COMPLETE -> requireNotNull(runner.complete) { "A finish without its report" }
        RunnerPhase.REST -> {
            requireNotNull(runner.rest) { "A rest without its clock" }
            requireNotNull(runner.step) { "A rest without the set after it" }
        }
        RunnerPhase.TIMING -> {
            requireNotNull(runner.timer) { "A timer without its clock" }
            requireNotNull(runner.step) { "A timer without its set" }
        }
        RunnerPhase.TIMER_DONE -> {
            requireNotNull(runner.review) { "A review without its time" }
            requireNotNull(runner.step) { "A review without its set" }
        }
        RunnerPhase.READY,
        RunnerPhase.ACTIVE -> requireNotNull(runner.step) { "A set without its exercise" }
    }
    return runner
}

private fun JSONObject.optObject(key: String): JSONObject? =
    if (isNull(key)) null else getJSONObject(key)

private fun JSONObject.toIntro(): RunnerIntro {
    val days =
        getJSONArray("days").objects().map {
            RunnerDay(it.getString("date"), it.getString("label"), it.getBoolean("selected"))
        }
    require(days.count { it.selected } == 1) { "Exactly one day is chosen" }
    return RunnerIntro(
        getString("step"),
        getString("title"),
        getInt("exercises"),
        getInt("sets"),
        getInt("xp"),
        days,
        getJSONArray("log").objects().map {
            RunnerLogRow(
                it.getInt("number"),
                it.getString("name"),
                it.getBoolean("optional"),
                it.getString("prescription"),
                it.getBoolean("done"),
            )
        },
    )
}

private fun JSONObject.toStep(): RunnerStep {
    val ask = getJSONObject("durationAsk")
    val pips =
        getJSONArray("pips").let { a ->
            (0 until a.length()).map {
                when (a.getString(it)) {
                    "done" -> PipState.DONE
                    "current" -> PipState.CURRENT
                    "" -> PipState.TODO
                    else -> error("Unknown pip ${a.getString(it)}")
                }
            }
        }
    val sets = getInt("sets")
    require(pips.size == sets && getInt("set") in 1..sets) {
        "The set is not one of the exercise's"
    }
    val kind = getString("kind")
    require(kind in KINDS) { "Unknown kind of set $kind" }
    return RunnerStep(
        getString("key"),
        getInt("number"),
        getInt("count"),
        getString("heading"),
        getString("setText"),
        getBoolean("optional"),
        getString("name"),
        getString("prescription"),
        getInt("set"),
        sets,
        pips,
        kind,
        getString("unit"),
        getString("label"),
        getString("suggest"),
        getBoolean("timed"),
        getBoolean("askDuration"),
        DurationAsk(ask.getString("text"), ask.getInt("start"), ask.getInt("max")),
        getDouble("amountStep"),
        getDouble("amountMin"),
        optObject("weight")?.let {
            WeightField(
                it.getString("suggest"),
                it.getDouble("step"),
                it.getDouble("min"),
                it.getDouble("max"),
            )
        },
        getString("lastReady"),
        getString("lastActive"),
        getString("cue"),
        getString("skipAsk"),
        getString("swapAsk"),
        getString("swapFrom"),
    )
}

private fun JSONObject.toTimer(): RunnerTimer {
    val total = getLong("totalMs")
    require(total > 0 && getLong("remainingMs") >= 0) { "A clock without a length" }
    return RunnerTimer(getLong("remainingMs"), total)
}

private fun JSONObject.toComplete() =
    RunnerComplete(
        getBoolean("full"),
        getInt("xp"),
        getInt("sets"),
        getString("time"),
        getString("volume"),
    )

private fun JSONObject.toWords(): RunnerWords {
    val map = RunnerWords.REQUIRED.associateWith { getString(it) }
    return RunnerWords(map)
}

private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")
private val KINDS = setOf("reps", "seconds", "minutes", "check")

/**
 * The command the page reads for a write, with its operation id (the page remembers those that
 * landed).
 */
fun PlanWrite.toCommand(operation: String): Pair<String, JSONObject> {
    val payload = JSONObject()
    args.forEach { (k, v) -> payload.put(k, v ?: JSONObject.NULL) }
    payload.put("op", operation)
    return command to payload
}

/** Whether a command name is one of the writes (not the toggle) the page answers. */
internal val WRITE_COMMANDS = setOf("value", "log", "leave", "signUp", "runner")
