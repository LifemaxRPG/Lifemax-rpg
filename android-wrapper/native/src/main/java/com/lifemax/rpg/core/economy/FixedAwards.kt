package com.lifemax.rpg.core.economy

import kotlin.math.max
import kotlin.math.min

/**
 * Section 11.7: fixed awards outside the cap. They never touch rested XP, the cap or stars, but
 * their XP counts toward later capped awards [D08].
 */
object FixedAwards {
    /** A day of the workout plan, as the plan tab generates it (outside the economy). */
    data class PlanDay(
        val kind: String,
        val label: String,
        val short: String,
        val icon: String,
        val xp: Double,
        val primary: String,
        val secondary: String,
        val items: List<PlanItem>,
    ) {
        val isRest: Boolean
            get() = kind == "rest"
    }

    /** An item of a plan day: [count] ticks complete it; an [optional] one is not needed. */
    data class PlanItem(val key: String, val count: Int, val optional: Boolean = false)

    /** PLAN-1's answer. */
    data class PlanResult(val saved: Boolean, val message: String)

    /**
     * PLAN-1: ticks one repetition; completing the day records its XP once (none when the linked
     * catalog entry was already recorded that day), reopening it removes the record.
     * [linkedCatalogIds] are the catalog entries the day's kind stands for.
     */
    fun tickPlan(
        save: EconomySave,
        date: String,
        day: PlanDay,
        itemKey: String,
        index: Int,
        checked: Boolean,
        linkedCatalogIds: List<String>,
        ctx: EconomyContext,
    ): Outcome<PlanResult> {
        val checks = save.planChecks[date] ?: PlanChecks()
        val wasDone = isDone(day, checks)
        val count = day.items.firstOrNull { it.key == itemKey }?.count ?: 0
        val ticks =
            (checks.ticks[itemKey].orEmpty() +
                    List(max(0, count - checks.ticks[itemKey].orEmpty().size)) { false })
                .toMutableList()
                .also { if (index in it.indices) it[index] = checked else it += checked }
        var next = checks.copy(ticks = checks.ticks + (itemKey to ticks))
        var after = save
        var message = ""
        val done = isDone(day, next)
        if (done && !wasDone && next.actId == null) {
            val linkedPaid =
                save.activities.any {
                    it.actId in linkedCatalogIds && ctx.clock.dayOf(it.date) == date
                }
            val xp = if (linkedPaid) 0.0 else day.xp
            val split =
                if (day.secondary.isNotEmpty())
                    jsRound(xp * 0.7).let {
                        listOf(XpShare(day.primary, it), XpShare(day.secondary, xp - it))
                    }
                else listOf(XpShare(day.primary, xp))
            val at =
                if (date < ctx.today) EconomyClock.iso(ctx.clock.at(date, "12:00:00") ?: ctx.now)
                else ctx.nowIso
            val id = "plan_$date"
            val record =
                Activity(
                    id = id,
                    type = "plan",
                    kind = day.kind,
                    planDate = date,
                    name = "Plan: ${day.label}",
                    icon = day.icon,
                    duration = 0.0,
                    skill = "Training",
                    primary = day.primary,
                    secondary = day.secondary,
                    xp = xp,
                    date = at,
                    notes = "",
                    split = split,
                )
            after = save.copy(activities = save.activities.filterNot { it.id == id } + record)
            next = next.copy(actId = id)
            message = "${day.short} complete · +${xp.text()} XP"
        } else if (!done && wasDone && next.actId != null) {
            val id = next.actId
            after = save.copy(activities = save.activities.filterNot { it.id == id })
            next = next.copy(actId = null)
            message = "Session reopened · XP removed"
        }
        val saved =
            SavePipeline.save(after.copy(planChecks = after.planChecks + (date to next)), ctx)
        val shown = if (saved.value && message.isNotEmpty()) saved.toast(message) else saved
        return shown.map { PlanResult(it, message) }
    }

    /**
     * PLAN-1: a day is done when every required item has all its ticks; a rest day never completes.
     */
    private fun isDone(day: PlanDay, checks: PlanChecks): Boolean =
        !day.isRest &&
            day.items
                .filterNot { it.optional }
                .all { item ->
                    val ticks = checks.ticks[item.key].orEmpty()
                    ticks.size >= item.count && ticks.take(item.count).all { it }
                }

    /** An entry of the attribute sheet (content outside the economy; its XP number is an input). */
    data class CatalogEntry(
        val id: String,
        val name: String,
        val attribute: String,
        val icon: String,
        val xp: Double,
        val skill: String? = null,
        val once: Boolean = false,
        val daily: Boolean = false,
        val weekly: Boolean = false,
        val milestone: Boolean = false,
        val minMinutes: Double? = null,
        /** Plan kinds that already count this entry (`LINKS`). */
        val planKinds: List<String> = emptyList(),
        /** The savings entry, which a deposit already counts. */
        val savings: Boolean = false,
        /** A developer boost of this many levels: no limits. */
        val boostLevels: Int? = null,
    )

    /** CATALOG-1, an entry's status: [blocked] is the reason, empty when open. */
    data class CatalogStatus(val blocked: String, val factor: Double, val n: Int, val xp: Double)

    /** CATALOG-1: the owner's own base when set, else the entry's. */
    fun baseXp(save: EconomySave, entry: CatalogEntry): Double =
        max(1.0, save.actXp[entry.id].orNaN.or(entry.xp))

    /**
     * CATALOG-1: whether the entry can be recorded now, and for how much (1, 0.5, 0.25 of the
     * base).
     */
    fun status(save: EconomySave, entry: CatalogEntry, ctx: EconomyContext): CatalogStatus {
        val records = save.activities.filter { it.actId == entry.id }
        val today = records.count { ctx.clock.dayOf(it.date) == ctx.today }
        entry.boostLevels?.let { levels ->
            val boost = max(1.0, entry.xp.or(Levels.xpToGain(save.attrXp(entry.attribute), levels)))
            return CatalogStatus("", 1.0, today, boost)
        }
        val base = baseXp(save, entry)
        var blocked =
            when {
                entry.once && records.isNotEmpty() -> "Already recorded"
                entry.daily && today > 0 -> "Done today"
                entry.weekly && records.any { ctx.clock.dayOf(it.date) >= ctx.clock.weekMonday } ->
                    "Done this week"
                today >= 3 -> "Daily limit reached"
                else -> ""
            }
        val todays = save.activities.onDay(ctx.today, ctx.clock)
        if (
            blocked.isEmpty() &&
                todays.any { it.type == "plan" && it.kind in entry.planKinds && it.xp > 0 }
        )
            blocked = "Already counted via your workout plan"
        if (blocked.isEmpty() && entry.savings && todays.any { it.type == "money" })
            blocked = "Already counted via the Money tab"
        val factor = if (blocked.isEmpty()) listOf(1.0, 0.5, 0.25)[today] else 1.0
        return CatalogStatus(blocked, factor, today, max(1.0, jsRound(base * factor)))
    }

    /** What the owner typed beside an entry. */
    data class CatalogInput(
        val minutes: String = "",
        val result: String = "",
        val note: String = "",
    )

    /** CATALOG-1: records the entry once its checks pass. */
    fun confirm(
        save: EconomySave,
        entry: CatalogEntry,
        input: CatalogInput,
        ctx: EconomyContext,
    ): Outcome<Unit> {
        val status = status(save, entry, ctx)
        if (status.blocked.isNotEmpty()) return save.result(Unit, status.blocked)
        val minutes = entry.minMinutes?.let { jsNumber(input.minutes.trim()).orZero } ?: 0.0
        entry.minMinutes?.let {
            if (minutes < it) return save.result(Unit, "Needs at least ${it.text()} minutes")
        }
        val result = input.result.trim()
        val records = save.activities.filter { it.actId == entry.id }
        if (entry.milestone) {
            if (result.isEmpty()) return save.result(Unit, "Name the milestone first")
            if (records.any { it.result.orEmpty().lowercase() == result.lowercase() })
                return save.result(Unit, "That milestone is already recorded")
        }
        val last = records.lastOrNull()?.let { ctx.clock.parseInstant(it.date) }
        if (last != null && ctx.now - last < 2500) return save.unchanged()
        val record =
            Activity(
                id = "act_${ctx.now}_${ctx.ids.token()}",
                type = "catalog",
                actId = entry.id,
                link = entry.id,
                name = entry.name,
                icon = entry.icon,
                duration = minutes,
                result = result,
                skill = entry.skill?.takeIf { it.isNotEmpty() } ?: "Practice",
                primary = entry.attribute,
                secondary = "",
                xp = status.xp,
                date = ctx.nowIso,
                notes = input.note.trim(),
                split = listOf(XpShare(entry.attribute, status.xp)),
            )
        return SavePipeline.save(save.copy(activities = save.activities + record), ctx)
            .map {}
            .toast("${entry.name} · +${status.xp.text()} XP")
    }

    /** CATALOG-1, undo: removes the entry's last record of today. */
    fun undo(save: EconomySave, entryId: String, ctx: EconomyContext): Outcome<Unit> {
        val last =
            save.activities.lastOrNull {
                it.actId == entryId && ctx.clock.dayOf(it.date) == ctx.today
            } ?: return save.unchanged()
        return SavePipeline.save(save.copy(activities = save.activities - last), ctx)
            .map {}
            .toast("Undone · XP removed")
    }

    /**
     * CATALOG-1: the owner's own base for an entry (1 to 200), or back to the default when [input]
     * is null.
     */
    fun setBase(
        save: EconomySave,
        entryId: String,
        input: String?,
        ctx: EconomyContext,
    ): Outcome<Unit> {
        val next =
            if (input == null) save.copy(actXp = save.actXp - entryId)
            else
                save.copy(
                    actXp =
                        save.actXp +
                            (entryId to min(200.0, max(1.0, jsRound(jsNumber(input).orZero))))
                )
        return SavePipeline.save(next, ctx)
            .map {}
            .toast(if (input == null) "XP reset to default" else "XP updated")
    }
}
