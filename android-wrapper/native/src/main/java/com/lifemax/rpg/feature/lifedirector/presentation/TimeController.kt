package com.lifemax.rpg.feature.lifedirector.presentation

import com.lifemax.rpg.feature.lifedirector.domain.DirectorTime
import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorProjection
import com.lifemax.rpg.feature.lifedirector.domain.RoutineRow
import com.lifemax.rpg.feature.lifedirector.domain.TimeSleep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * The sleep times on My time's card before Save sleep: the dial's handles and the four fields move
 * these, and nothing goes to the page until Save sleep.
 */
data class SleepDraft(
    val bed: Int,
    val wake: Int,
    val weekendOn: Boolean,
    val weekendBed: Int,
    val weekendWake: Int,
) {
    /** Whether the page already has exactly these times (its weekend times count only when on). */
    fun saved(s: TimeSleep): Boolean =
        s.saved &&
            bed == s.bed &&
            wake == s.wake &&
            weekendOn == s.weekend.on &&
            (!weekendOn || (weekendBed == s.weekend.bed && weekendWake == s.weekend.wake))

    companion object {
        fun of(s: TimeSleep) =
            SleepDraft(s.bed, s.wake, s.weekend.on, s.weekend.bed, s.weekend.wake)
    }
}

/**
 * What My time shows that the page does not have yet: the sleep times being set, and a routine's
 * minutes being stepped (by key). Kept outside the list, so they outlive a card scrolled away and
 * the screen hiding (A24 drafts).
 */
data class TimeDrafts(val sleep: SleepDraft? = null, val routines: Map<String, Int> = emptyMap())

/** Runs [run] after [ms] milliseconds; the function it returns cancels it. */
fun interface Later {
    fun after(ms: Long, run: () -> Unit): () -> Unit
}

/**
 * My time (phase 4B): its writes, each the page's own `do` command through the screen's change path
 * ([change]: one at a time, the control dimmed while it is out, the page's words when it refuses,
 * its notice when it has saved), and the sheets it opens (the commitment sheet and the goal kinds),
 * which another part of the screen draws once the page sends their models.
 *
 * A routine's − and + move a pending value at once and send it, absolute, after [QUIET_MS] of quiet
 * (plan §3.5 #13): quick taps add up to one re-plan, and a repeat of a value the page has is
 * nothing. The sleep times are a draft until Save sleep, which sends bed, wake and the weekend's
 * times (none when every night is the same).
 */
internal class TimeController(
    private val sheets: SheetController,
    private val projection: StateFlow<LifeDirectorProjection?>,
    private val ui: MutableStateFlow<DirectorUi>,
    private val change: (String, Map<String, Any>) -> Unit,
    /** nothing holds the screen and no change is out with the page */
    private val free: () -> Boolean,
    private val later: Later,
) {
    private val timers = HashMap<String, () -> Unit>()

    /** My time, while the page hands it to this screen. */
    val time: DirectorTime?
        get() = projection.value?.time

    private fun drafts(f: (TimeDrafts) -> TimeDrafts) =
        ui.update { it.copy(timeDrafts = f(it.timeDrafts)) }

    /** The sleep times shown now: the draft, or the page's. */
    fun sleepShown(): SleepDraft? {
        val s = time?.sleep ?: return null
        return ui.value.timeDrafts.sleep ?: SleepDraft.of(s)
    }

    /** The handles or the two weeknight fields moved. */
    fun setSleep(bed: Int, wake: Int) = edit {
        it.copy(bed = bed.dayMinute(), wake = wake.dayMinute())
    }

    fun setWeekend(on: Boolean) = edit { it.copy(weekendOn = on) }

    fun setWeekendTimes(bed: Int, wake: Int) = edit {
        it.copy(weekendBed = bed.dayMinute(), weekendWake = wake.dayMinute())
    }

    private fun edit(f: (SleepDraft) -> SleepDraft) {
        val now = sleepShown() ?: return
        drafts { it.copy(sleep = f(now)) }
    }

    /** Save sleep (also the first-time "Save sleep and continue"). */
    fun saveSleep() {
        val d = sleepShown() ?: return
        if (!free()) return
        val args = mutableMapOf<String, Any>("bed" to d.bed, "wake" to d.wake)
        // the page reads a missing weekend as null: every night the same
        if (d.weekendOn) args["weekend"] = mapOf("bed" to d.weekendBed, "wake" to d.weekendWake)
        change("sleepSave", args)
    }

    /** The minutes a routine shows now: its pending value, or the page's. */
    fun routineShown(row: RoutineRow): Int = ui.value.timeDrafts.routines[row.key] ?: row.value

    /**
     * − ([by] -1) or + (1) on routine [key]: within the page's limits, sent after a quiet spell.
     */
    fun stepRoutine(key: String, by: Int) {
        val row = row(key) ?: return
        if (row.on == false || !free()) return
        val v = (routineShown(row) + by * row.step).coerceIn(row.min, row.max)
        drafts { it.copy(routines = it.routines + (key to v)) }
        timers.remove(key)?.invoke()
        timers[key] = later.after(QUIET_MS) { commit(key) }
    }

    private fun commit(key: String) {
        timers.remove(key)
        val row = row(key)
        val v = ui.value.timeDrafts.routines[key]
        if (row == null || v == null || v == row.value) {
            drafts { it.copy(routines = it.routines - key) }
            return
        }
        // another change is out: this one goes once it is answered
        if (!free()) {
            timers[key] = later.after(QUIET_MS) { commit(key) }
            return
        }
        change("routine", mapOf("key" to key, "value" to v))
    }

    private fun row(key: String): RoutineRow? =
        time?.routines?.let { r -> (r.rows + r.meals).firstOrNull { it.key == key } }

    /** Meals on or off: the state wanted, so a repeat changes nothing. */
    fun meals(on: Boolean) {
        if (time != null && free()) change("meals", mapOf("on" to on))
    }

    /** Build my week, or Re-plan now once set up. */
    fun build() {
        if (time != null && free()) change("finishSetup", emptyMap())
    }

    /** A commitment's sheet; [id] empty for a new one. */
    fun openRule(id: String) = openSheet(RULE_SHEET, id)

    /** Add a goal: the goal kinds first. */
    fun addGoal() = openSheet(GOAL_TYPE_SHEET, "")

    private fun openSheet(kind: String, id: String) {
        if (time == null || !sheets.available || !free() || ui.value.sheet != null) return
        sheets.open(kind, id)
    }

    /** A new projection: a draft the page now has is done with. */
    fun projected() {
        val t = time ?: return
        val d = ui.value.timeDrafts
        val sleep = d.sleep?.takeUnless { it.saved(t.sleep) }
        val routines = d.routines.filter { (k, v) -> k in timers || row(k)?.value != v }
        if (sleep != d.sleep || routines != d.routines) drafts { TimeDrafts(sleep, routines) }
    }

    /**
     * The page answered change [name]: saved sleep is done with; a routine no longer waiting to go
     * shows the page's value again (refused, or saved: the projection says which).
     */
    fun settled(name: String?, ok: Boolean) {
        when (name) {
            "sleepSave" -> if (ok) drafts { it.copy(sleep = null) }
            "routine" -> drafts { it.copy(routines = it.routines.filterKeys { k -> k in timers }) }
        }
    }

    private fun Int.dayMinute() = mod(DAY)

    companion object {
        const val RULE_SHEET = "rule"
        const val GOAL_TYPE_SHEET = "goalType"
        /** the quiet after the last − or + before a routine's minutes go to the page */
        const val QUIET_MS = 400L
        private const val DAY = 1440
    }
}
