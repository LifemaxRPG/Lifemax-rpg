package com.lifemax.rpg.core.economy

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Sections 15 and 16: decay, rested accrual, rest days, the streak and streak shields. */
object Decay {
    /** DECAY-1: the stored settings over the defaults (on, 4 grace days, 3%). */
    fun settings(save: EconomySave): DecaySettings = (save.decay ?: DecaySettings()).effective()

    /**
     * DECAY-1, saving the settings: grace 1 to 14 days, rate 1 to 10% (blank or 0 means the
     * default).
     */
    fun saveSettings(
        save: EconomySave,
        on: Boolean,
        grace: String,
        rate: String,
        ctx: EconomyContext,
    ): Outcome<Unit> {
        val settings =
            DecaySettings(
                on = on,
                grace = min(14.0, max(1.0, jsNumber(grace).or(4.0))),
                rate = min(10.0, max(1.0, jsNumber(rate).or(3.0))),
            )
        return SavePipeline.save(save.copy(decay = settings), ctx)
            .map {}
            .toast("Decay settings saved")
    }

    /**
     * DECAY-2: the last day an attribute was trained by a non-passive record, or null. Quests,
     * nutrition, cat care and deposits count through their attributes [D35].
     */
    fun lastTrained(save: EconomySave, attr: String, clock: EconomyClock): String? =
        save.activities
            .filter { !it.isPassive && (it.primary == attr || it.secondary == attr) }
            .maxOfOrNull { clock.dayOf(it.date) }

    /**
     * REST-DAY-1: a rest day taken, a rest day in the plan, or a recovery session of 20+ minutes.
     */
    fun isRestDay(save: EconomySave, day: String, ctx: EconomyContext): Boolean =
        save.restDays[day] == true ||
            ctx.isPlanRestDay(day) ||
            save.activities.any {
                ctx.clock.dayOf(it.date) == day &&
                    it.type == "recovery" &&
                    !it.isPassive &&
                    it.duration.orZero >= 20
            }

    /** REST-DAY-2: rest days taken in the last 7 days. */
    fun restDaysUsed(save: EconomySave, clock: EconomyClock): Int {
        val since = clock.daysAgo(6)
        return save.restDays.keys.count { it >= since }
    }

    /** REST-DAY-2, taking a rest day: two free rest days a week, then rest tokens. */
    fun takeRest(save: EconomySave, ctx: EconomyContext): Outcome<Unit> {
        if (isRestDay(save, ctx.today, ctx)) return save.result(Unit, "Today is already a rest day")
        var next = save
        val toasts = mutableListOf<String>()
        if (restDaysUsed(save, ctx.clock) >= Eco.REST_PER_WEEK) {
            if (!(save.restTokens.orZero > 0))
                return save.result(Unit, "Two rest days per week are used")
            next = next.copy(restTokens = save.restTokens.orZero - 1)
            toasts += "Rest token used"
        }
        next = next.copy(restDays = next.restDays + (ctx.today to true))
        return Outcome(Unit, next, toasts)
            .then { SavePipeline.save(it, ctx) }
            .map {}
            .toast("Rest day taken. No decay today.")
    }

    /**
     * DECAY-3: after the grace days, an idle attribute loses a share of its level step per day,
     * never below its level, skipping rest days; losses on an attribute idle a week or more feed
     * rested XP (DECAY-5). The value says whether anything was lost.
     */
    fun apply(save: EconomySave, ctx: EconomyContext): Outcome<Boolean> {
        val ticked = runCatching { shieldTick(save, ctx) }.getOrElse { save.unchanged() }
        var s = ticked.save
        val settings = settings(s)
        if (settings.on != true) return ticked.map { false }
        val today = EconomyClock.dayNumber(ctx.today) ?: return ticked.map { false }
        s = s.copy(decayApplied = s.decayApplied.orEmpty(), decayLog = s.decayLog.orEmpty())
        val grace = settings.grace ?: 4.0
        val rate = settings.rate ?: 3.0
        var changed = false
        for (attr in Attribute.ids) {
            val trained = lastTrained(s, attr, ctx.clock) ?: continue
            val trainedDay = EconomyClock.dayNumber(trained) ?: continue
            val iron =
                if (s.hasPerk("iron") && (attr == "physique" || attr == "endurance")) 2 else 0
            val from =
                max(trainedDay + grace + iron + 1, s.decayApplied.orEmpty()[attr].orNaN.orZero + 1)
            var lost = 0.0
            var n = from
            while (n <= today) {
                if (!isRestDay(s, EconomyClock.dayKey(n.toLong()), ctx)) {
                    val xp = s.attrXp(attr)
                    if (xp <= 0) break
                    val level = Curve.Attribute.progress(xp)
                    if (level.into <= 0) break
                    val loss =
                        min(
                            level.into,
                            max(
                                1.0,
                                min(15.0, jsRound(Curve.Attribute.step(level.level) * rate / 100)),
                            ),
                        )
                    s = s.copy(attrXp = s.attrXp + (attr to xp - loss))
                    lost += loss
                }
                n += 1
            }
            // D21: rounded per application, so how often decay runs changes what is banked.
            if (lost > 0 && today - trainedDay >= 7)
                s =
                    s.copy(
                        restedXp =
                            min(Eco.RESTED_CAP, s.restedXp.orZero + jsRound(lost * Eco.RESTED_RATE))
                    )
            if (today >= from)
                s = s.copy(decayApplied = s.decayApplied.orEmpty() + (attr to today.toDouble()))
            if (lost > 0) {
                changed = true
                s =
                    s.copy(
                        decayTotal =
                            s.decayTotal + (attr to s.decayTotal[attr].orNaN.orZero + lost),
                        decayLog = listOf(DecayEntry(ctx.today, attr, lost)) + s.decayLog.orEmpty(),
                    )
            }
        }
        return Outcome(changed, s.copy(decayLog = s.decayLog.orEmpty().take(30)), ticked.toasts)
    }

    /**
     * STREAK-1: consecutive days with training, a rest day or a shield; a single missed day does
     * not break it.
     */
    fun streak(save: EconomySave, ctx: EconomyContext): Int {
        val days =
            save.activities.filterNot { it.isPassive }.map { ctx.clock.dayOf(it.date) }.toSet() +
                save.restDays.keys +
                save.shields.days.orEmpty().keys
        var streak = 0
        var misses = 0
        for (back in 0L until 400L) {
            if (ctx.clock.daysAgo(back) in days) {
                streak += 1
                misses = 0
            } else if (++misses >= 2) break
        }
        return streak
    }

    /** SHIELD-1: a day with a non-passive record. */
    fun isActive(save: EconomySave, day: String, clock: EconomyClock): Boolean =
        save.activities.any { !it.isPassive && clock.dayOf(it.date) == day }

    /**
     * SHIELD-2: a shield covers the second missed day in a row when someone trained in the last 30
     * days; every 7 days of streak earns one back, up to two.
     */
    fun shieldTick(save: EconomySave, ctx: EconomyContext): Outcome<Unit> {
        val today = EconomyClock.dayNumber(ctx.today)?.toDouble() ?: return save.unchanged()
        val held = save.shields
        val tick = held.tick ?: return save.copy(shields = held.copy(tick = today - 1)).unchanged()
        var miss = held.miss.orZero
        var count = held.count.orZero
        var shieldDays = held.days ?: emptyMap()
        val toasts = mutableListOf<String>()
        val trainingDays =
            save.activities
                .filterNot { it.isPassive }
                .mapNotNull { EconomyClock.dayNumber(ctx.clock.dayOf(it.date)) }
        var n = tick + 1
        while (n <= today - 1) {
            val key = EconomyClock.dayKey(n.toLong())
            if (
                isActive(save, key, ctx.clock) ||
                    isRestDay(save, key, ctx) ||
                    shieldDays[key] == true
            )
                miss = 0.0
            else {
                miss += 1
                if (miss >= 2 && count > 0 && trainingDays.any { it >= n - 30 }) {
                    shieldDays = shieldDays + (key to true)
                    count -= 1
                    miss = 1.0
                    toasts += "A streak shield covered a missed day"
                }
            }
            n += 1
        }
        val ticked =
            save.copy(shields = held.copy(tick = today - 1, miss = miss, days = shieldDays))
        // D28: the milestone follows the streak down, so a rebuilt streak earns shields again.
        val milestone = floor(streak(ticked, ctx) / 7.0)
        val reached = held.milestone.orZero
        if (milestone > reached) {
            count = min(2.0, count + milestone - reached)
            toasts += "Streak shield earned"
        }
        return Outcome(
            Unit,
            ticked.copy(shields = ticked.shields.copy(milestone = milestone, count = count)),
            toasts,
        )
    }
}
