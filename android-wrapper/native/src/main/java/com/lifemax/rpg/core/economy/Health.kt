package com.lifemax.rpg.core.economy

import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** What the Health Connect plugin answers (outside the economy; section 1.2). */
interface HealthConnect {
    /**
     * Whether Health Connect is `available` (or needs an `update`), and the record types granted.
     */
    data class Status(val sdk: String, val granted: List<String>)

    /** An exercise session; times in epoch milliseconds. */
    data class Workout(
        val id: String,
        val type: String?,
        val start: Long,
        val end: Long,
        val title: String? = null,
        val origin: String? = null,
    )

    /** One local day's step count. */
    data class StepDay(val date: String, val count: Double)

    /** A sleep session; times in epoch milliseconds. */
    data class SleepSession(val id: String, val start: Long, val end: Long)

    /** The latest weight reading. */
    data class Weight(val kg: Double?, val time: Double)

    /** HEALTH-0. */
    fun status(): Status

    /** HEALTH-3: sessions between two instants. */
    fun exercise(from: Long, to: Long): List<Workout>

    /** HEALTH-4: the last [days] days. */
    fun steps(days: Int): List<StepDay>

    /** HEALTH-5: sessions between two instants. */
    fun sleep(from: Long, to: Long): List<SleepSession>

    /** HEALTH-6: the latest reading of the last [days] days, or null. */
    fun weight(days: Int): Weight?
}

/** Section 11.3: what Health Connect records pay, and the sync that reads them. */
object Health {
    private const val HOUR = 3_600_000L
    private const val MINUTE = 60_000L
    private const val NOTE = "Imported from Health Connect"
    private val RECOVERY = Regex("yoga|pilates|stretch|breathing|meditat|tai_chi")
    private val STRENGTH =
        Regex("weightlift|strength|calisthenics|boot_camp|gymnastic|martial|boxing|climb")
    private val PLACE = Regex("_treadmill|_stationary|_machine|_open_water|_pool")
    private val STEPS_RULE = Rule("", "target", Double.NaN, "Walking", "endurance", "")
    private val SLEEP_RULE = Rule("", "moon", Double.NaN, "Sleep", "wellbeing", "")
    private val APPS =
        mapOf(
            "com.sec.android.app.shealth" to "Samsung Health",
            "com.google.android.apps.fitness" to "Google Fit",
            "com.fitbit.FitbitMobile" to "Fitbit",
            "com.garmin.android.apps.connectmobile" to "Garmin",
        )

    /** One record for [award]: a workout, a day of steps or a night of sleep. */
    data class Import(
        val at: Long,
        val type: String,
        val name: String,
        val minutes: Double = 0.0,
        val key: String? = null,
        val note: String? = null,
        val rule: Rule? = null,
        val skill: String? = null,
        val primary: String? = null,
        /** Null takes the rule's secondary; empty means none. */
        val secondary: String? = null,
        /** A fixed amount instead of one scored from [minutes]. */
        val xp: Double? = null,
    )

    /**
     * HEALTH-1: one imported record through the pipeline on its own day (not manual), split,
     * starred and recorded. The value is the XP before stars [D11]; 0 when the pipeline paid
     * nothing.
     */
    fun award(save: EconomySave, record: Import, ctx: EconomyContext): Outcome<Double> {
        val rule = record.rule ?: save.rules.of(record.type)
        val total = record.xp ?: max(0.0, jsRound(rule.base * durationFactor(record.minutes)))
        // D34: steps and sleep go through the pipeline too, so passive data spends rested XP.
        val awarded =
            save.award(record.type, total, record.minutes, ctx, ctx.clock.localDate(record.at))
        val xp = awarded.value.xp
        if (xp <= 0) return save.result(0.0)
        val primary = record.primary?.takeIf { it.isNotEmpty() } ?: rule.primary
        val secondary = record.secondary ?: rule.secondary
        val parts = awarded.save.starred(splitXp(xp, primary, secondary))
        val activity =
            Activity(
                id = ctx.ids.uuid(),
                type = record.type,
                name = record.name,
                icon = rule.icon,
                duration = record.minutes,
                quality = 1.0,
                skill = record.skill?.takeIf { it.isNotEmpty() } ?: rule.skill,
                primary = primary,
                secondary = secondary,
                xp = parts.sumOf { it.xp },
                date = EconomyClock.iso(record.at),
                notes = record.note.orEmpty(),
                split = parts,
                source = Activity.HEALTH,
                healthKey = record.key,
            )
        return awarded.save.credited(activity, skillGain = xp).result(xp)
    }

    /**
     * HEALTH-0 to HEALTH-6, a sync: workouts, then steps, then sleep, then weight, each when
     * switched on and granted. [plugin] is null outside the Android app; [asked] is true when the
     * owner pressed Sync.
     */
    fun sync(
        save: EconomySave,
        plugin: HealthConnect?,
        asked: Boolean,
        ctx: EconomyContext,
    ): Outcome<Unit> {
        if (plugin == null)
            return if (asked) save.result(Unit, "Health sync works in the Android app")
            else save.unchanged()
        if (!save.health.on && !asked) return save.unchanged()
        return Sync(save, plugin, asked, ctx).run().then { SavePipeline.save(it, ctx) }.map {}
    }

    /** One sync's working state; it ends in a single outcome. */
    private class Sync(
        start: EconomySave,
        val plugin: HealthConnect,
        val asked: Boolean,
        val ctx: EconomyContext,
    ) {
        var save = start
        val toasts = mutableListOf<String>()
        val now = ctx.now
        var health = start.health
        val bits = mutableListOf<String>()
        var xp = 0.0

        fun run(): Outcome<Unit> {
            try {
                val status = plugin.status()
                health = health.copy(sdk = status.sdk, granted = status.granted)
                if (status.sdk != "available") {
                    val message =
                        if (status.sdk == "update")
                            "Health Connect needs an update from the Play Store."
                        else "Health Connect is not available on this phone."
                    return finish(health.copy(message = message))
                }
                val wanted =
                    listOfNotNull(
                        "exercise".takeIf { health.exercise },
                        "steps".takeIf { health.steps },
                        "sleep".takeIf { health.sleep },
                        "weight".takeIf { health.weight },
                    )
                if (wanted.any { it !in status.granted } && wanted.none { it in status.granted })
                    return finish(health.copy(message = "Allow access to start syncing."))
                if (!(health.since > 0))
                    health = health.copy(since = (now - 2 * 24 * HOUR).toDouble())
                val from = max(health.since.orZero, (now - 3 * 24 * HOUR).toDouble())
                val workouts =
                    if (health.exercise && "exercise" in status.granted)
                        plugin.exercise(now - 7 * 24 * HOUR, now)
                    else emptyList()
                workouts(workouts, from)
                if (health.steps && "steps" in status.granted)
                    steps(plugin.steps(30), workouts, from)
                if (health.sleep && "sleep" in status.granted)
                    sleep(plugin.sleep(now - 8 * 24 * HOUR, now), workouts, from)
                if (health.weight && "weight" in status.granted) weight(plugin.weight(60))
                // D01: marks go after 21 days while steps are read for 30, so old days pay again.
                val prune = (now - 21 * 24 * HOUR).toDouble()
                health =
                    health.copy(
                        seen = health.seen.filterValues { !(it < prune) },
                        last = now.toDouble(),
                    )
                val gained = if (xp != 0.0) " (+${xp.text()} XP)" else ""
                health =
                    health.copy(
                        message =
                            if (bits.isEmpty()) "Up to date."
                            else "Added ${bits.joinToString(", ")}$gained."
                    )
                if (bits.isNotEmpty() || asked) {
                    val summary =
                        if (bits.isEmpty()) "nothing new"
                        else bits.joinToString(", ") + if (xp != 0.0) " · +${xp.text()} XP" else ""
                    toasts += "Health sync: $summary"
                }
            } catch (e: Exception) {
                health = health.copy(message = "Sync failed: " + e.message.orEmpty().take(100))
            }
            return finish(health)
        }

        fun finish(final: HealthState) = Outcome(Unit, save.copy(health = final), toasts)

        fun pay(record: Import): Double {
            save = save.copy(health = health)
            val paid = award(save, record, ctx)
            save = paid.save
            xp += paid.value
            return paid.value
        }

        fun count(n: Int, one: String, many: String) {
            if (n > 0) bits += "$n ${if (n == 1) one else many}"
        }

        /** HEALTH-3. */
        fun workouts(sessions: List<HealthConnect.Workout>, from: Double) {
            var added = 0
            for (w in sessions) {
                val key = "x${w.id}"
                val minutes = jsRound((w.end - w.start) / MINUTE.toDouble())
                if (key in health.seen || w.end > now || minutes < 5 || w.start < from) continue
                val kind = kindOf(w.type)
                // D37: a hand-logged record's time (when it was saved) against the workout's start.
                val byHand =
                    save.activities.any { a ->
                        a.source != Activity.HEALTH &&
                            a.type == kind &&
                            ctx.clock.parseInstant(a.date)?.let { abs(it - w.start) <= 2 * HOUR } ==
                                true
                    }
                health = health.copy(seen = health.seen + (key to w.end.toDouble()))
                if (byHand) continue
                val app = w.origin?.let(::appName)
                pay(
                    Import(
                        at = w.start,
                        type = kind,
                        name =
                            (w.title?.takeIf { it.isNotEmpty() } ?: nice(w.type)) +
                                (app?.let { " · $it" } ?: ""),
                        minutes = minutes,
                        key = key,
                        // D10: a walking workout gets skill Walking and so counts as passive.
                        skill =
                            if (kind == "running") nice(w.type?.replaceFirst(PLACE, "")) else null,
                        note = NOTE,
                    )
                )
                if (app != null) health = health.copy(source = app)
                added++
            }
            count(added, "workout", "workouts")
        }

        /** HEALTH-4. */
        fun steps(
            days: List<HealthConnect.StepDay>,
            workouts: List<HealthConnect.Workout>,
            from: Double,
        ) {
            val sinceDay = ctx.clock.localDate((health.since.takeIf { it > 0 } ?: from).toLong())
            var added = 0
            for (day in days) {
                if (day.date == ctx.today) {
                    health = health.copy(today = day)
                    continue
                }
                val key = "p${day.date}"
                if (key in health.seen || day.count < 2000 || day.date < sinceDay) continue
                val dayWorkouts = workouts.filter { ctx.clock.localDate(it.start) == day.date }
                val cardio =
                    dayWorkouts
                        .filter { kindOf(it.type) == "running" }
                        .sumOf { max(0.0, (it.end - it.start) / MINUTE.toDouble()) }
                var effective = day.count
                if (effective > 35000 && dayWorkouts.isEmpty()) effective = 8000.0
                effective -= jsRound(cardio) * 100
                health =
                    health.copy(
                        seen =
                            health.seen +
                                (key to (ctx.clock.at(day.date, "23:59:00") ?: 0L).toDouble())
                    )
                if (effective < 4000) continue
                pay(
                    Import(
                        at = ctx.clock.at(day.date, "20:00:00") ?: 0L,
                        type = "steps",
                        key = key,
                        rule = STEPS_RULE,
                        name =
                            "Daily steps: " +
                                NumberFormat.getIntegerInstance(ctx.locale).format(day.count),
                        skill = "Walking",
                        primary = "endurance",
                        secondary = "",
                        xp = min(15.0, jsRound((effective - 2000) / 1000 * 1.5)),
                        note = NOTE,
                    )
                )
                added++
            }
            count(added, "day of steps", "days of steps")
        }

        /** HEALTH-5. */
        fun sleep(
            sessions: List<HealthConnect.SleepSession>,
            workouts: List<HealthConnect.Workout>,
            from: Double,
        ) {
            val best = linkedMapOf<String, HealthConnect.SleepSession>()
            for (s in sessions) {
                if (s.end > now) continue
                val day = ctx.clock.localDate(s.end)
                val held = best[day]
                if (held == null || s.end - s.start > held.end - held.start) best[day] = s
            }
            var added = 0
            for (s in sessions) {
                val key = "z${s.id}"
                val hours = (s.end - s.start) / HOUR.toDouble()
                if (key in health.seen || s.end > now || hours < 3 || s.end < from) continue
                health = health.copy(seen = health.seen + (key to s.end.toDouble()))
                if (best[ctx.clock.localDate(s.end)] !== s) continue
                val busy =
                    workouts.any {
                        it.start < s.end - 30 * MINUTE && it.end > s.start + 30 * MINUTE
                    } ||
                        save.activities.any { a ->
                            a.source != Activity.HEALTH &&
                                ctx.clock.parseInstant(a.date)?.let {
                                    it > s.start + 30 * MINUTE && it < s.end - 30 * MINUTE
                                } == true
                        }
                val points =
                    when {
                        hours < 5.5 || hours > 12 || busy -> 0.0
                        hours < 6.5 -> 8.0
                        hours < 7.5 -> 12.0
                        hours <= 9.5 -> 15.0
                        else -> 12.0
                    }
                if (points <= 0) continue
                val whole = floor(hours)
                val minutes = jsRound((hours - whole) * 60) // D36: can be 60
                pay(
                    Import(
                        at = s.end,
                        type = "sleep",
                        key = key,
                        rule = SLEEP_RULE,
                        name = "Sleep: ${whole.text()}h ${minutes.text().padStart(2, '0')}m",
                        skill = "Sleep",
                        primary = "wellbeing",
                        secondary = "",
                        xp = points,
                        note = NOTE,
                    )
                )
                added++
            }
            count(added, "night of sleep", "nights of sleep")
        }

        /** HEALTH-6: weight pays nothing; a newer reading is kept, and a changed one reported. */
        fun weight(reading: HealthConnect.Weight?) {
            val kg = reading?.kg ?: return
            if (kg == 0.0 || kg.isNaN()) return
            val pounds = jsRound(kg * 2.20462 * 10) / 10
            if (reading.time > (health.weightTime ?: 0.0)) {
                val history =
                    (health.weightHistory.orEmpty() + WeightPoint(reading.time, pounds)).takeLast(
                        60
                    )
                health = health.copy(weightTime = reading.time, weightHistory = history)
                if (save.weight != pounds) {
                    save = save.copy(weight = pounds)
                    bits += "weight ${pounds.text()} lb"
                }
            }
        }
    }

    /** HEALTH-3: which rule a workout type scores by. */
    fun kindOf(type: String?): String {
        val t = type ?: "undefined"
        return when {
            RECOVERY.containsMatchIn(t) -> "recovery"
            STRENGTH.containsMatchIn(t) -> "strength"
            else -> "running"
        }
    }

    /**
     * HEALTH-3: a type's words capitalised, with HIIT shortened. A missing type reads `Undefined`.
     */
    fun nice(type: String?): String =
        (type ?: "undefined")
            .split("_")
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
            .replace("High Intensity Interval Training", "HIIT")

    /** HEALTH-3: the app a record came from. */
    fun appName(origin: String): String = APPS[origin] ?: origin.substringAfterLast('.')
}
