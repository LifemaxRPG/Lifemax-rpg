package com.lifemax.rpg.core.lifedirector.readmodels

import com.lifemax.rpg.core.lifedirector.PageRangeError
import com.lifemax.rpg.core.lifedirector.PageTypeError
import com.lifemax.rpg.core.lifedirector.PageValues.member
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.lifedirector.PageZone
import com.lifemax.rpg.core.lifedirector.PlanDay
import com.lifemax.rpg.core.save.JsonValue

/**
 * What a read model of part b answers: its value, or the error the page throws for the same state
 * (`TypeError`, `RangeError`). Nothing in [DirectorPanels] throws: a failure native has no page
 * counterpart for is [Threw] with a name starting `Native`, so a shadow check records it.
 */
sealed interface ReadAnswer<out T> {
    data class Value<out T>(val value: T) : ReadAnswer<T>

    data class Threw(val error: String) : ReadAnswer<Nothing>
}

/** `Host.capabilities()` as the read models use it: whether LifeMax is connected, what is missing. */
data class HostCapabilities(val connected: Boolean, val missing: List<String>)

/** One of `Host.milestones()`: a LifeMax milestone quest (title and description as LifeMax has them). */
data class Milestone(val title: String?, val desc: String?, val done: Boolean, val locked: Boolean)

/** What the read models ask LifeMax (logic.js `Host`): the page's `LifeMaxHost`, or native's. */
interface DirectorHostView {
    fun capabilities(): HostCapabilities

    fun milestones(): List<Milestone>
}

/**
 * The Life Director read models of WP-LD8b (LIFE_DIRECTOR_READMODELS_SPEC.md part b): from the same
 * inputs as part a ([models]: the saved state, the moment, the device's zone), what LifeMax answers
 * ([host]) and how the store loaded the state ([loadSource], `loadReport.source`, null for none),
 * exactly what the page's native projection and sheets carry for Goals, Quests, Settings, My time,
 * the forms, Something came up, Move, the work rules and shifts, and each day's dial handles and
 * stops. Only reads. Each answer's `json()` is the page's JSON for it, so a shadow check compares it
 * with the page's member for member.
 */
class DirectorPanels(
    val models: DirectorReadModels,
    val host: DirectorHostView,
    val loadSource: String?,
    val words: PanelWords = PanelWords.packaged,
) {
    internal val clock = ClockWords(words)
    internal val state: JsonValue?
        get() = models.state

    /** RM-23, RM-24 `nativeGoals()`. */
    fun goals(): ReadAnswer<GoalsPanel> = answer { Goals.panel(this) }

    /** RM-25 `nativeQuests()`. */
    fun quests(): ReadAnswer<QuestsPanel> = answer { Quests.panel(this) }

    /** RM-26, RM-27 `nativeSettings()`. */
    fun settings(): ReadAnswer<SettingsPanel> = answer { Settings.panel(this) }

    /** RM-28 to RM-30 `nativeTime()`. */
    fun time(): ReadAnswer<TimePanel> = answer { MyTime.panel(this) }

    /** RM-34 `nativeSheet('disrupt')`: null while set-up is unfinished or there is no plan. */
    fun disrupt(): ReadAnswer<DisruptSheet?> = answer {
        if (!planShown()) null else Sheets.disrupt(this)
    }

    /** RM-35 `nativeSheet('move', id)`: null without a plan. */
    fun move(id: String): ReadAnswer<MoveSheet?> = answer {
        if (!planShown()) null else Sheets.move(this, id)
    }

    /** RM-31 to RM-33 `nativeSheet(kind, id)` for `rule`, `goalType` and `goalForm`. */
    fun form(kind: String, id: String): ReadAnswer<FormSheet?> = answer { Forms.sheet(this, kind, id) }

    /** RM-36 `workRules()`. */
    fun workRules(): ReadAnswer<List<WorkRule>> = answer { Shifts.workRules(this) }

    /** RM-37 `shifts(fromEpoch, toEpoch)`. */
    fun shifts(fromEpoch: Double, toEpoch: Double): ReadAnswer<List<Shift>> = answer {
        Shifts.of(this, fromEpoch, toEpoch)
    }

    /**
     * RM-38 to RM-40: `nativeHandles(dialHandles(day), day.startEpoch, day.endEpoch)` for the day
     * [dayIndex] of the plan's days (part a's `horizonDays`; with no plan, `calendarDays(0)`, as
     * `nativeToday` picks them); null when there is no such day.
     */
    fun handles(dayIndex: Int): ReadAnswer<DayHandles?> = answer {
        val days =
            if (truthy(models.schedule)) models.horizonDays()
            else models.calendarDays(0) ?: emptyList()
        days.getOrNull(dayIndex)?.let { Dial.handles(this, it) }
    }

    /** [handles] for a day the caller already has. */
    fun handles(day: PlanDay): ReadAnswer<DayHandles> = answer { Dial.handles(this, day) }

    internal fun zone(): PageZone = models.zone()

    internal fun planShown(): Boolean =
        truthy(member(state, "setupComplete")) && truthy(models.schedule)

    internal fun mins(m: Double): String = Week.mins(m, models.words)

    /** `mins(value)` of a stored value: `value + 'm'` under an hour, its text as it is. */
    internal fun mins(value: JsonValue?): String {
        val n = com.lifemax.rpg.core.lifedirector.PageValues.number(value)
        return if (n >= HOUR) mins(n)
        else com.lifemax.rpg.core.lifedirector.PageValues.text(value) + models.words.phrase("minutes")
    }

    private inline fun <T> answer(f: () -> T): ReadAnswer<T> =
        try {
            ReadAnswer.Value(f())
        } catch (e: PageTypeError) {
            ReadAnswer.Threw("TypeError")
        } catch (e: PageRangeError) {
            ReadAnswer.Threw("RangeError")
        } catch (e: IllegalArgumentException) {
            ReadAnswer.Threw("RangeError")
        } catch (e: RuntimeException) {
            ReadAnswer.Threw("Native" + e.javaClass.simpleName)
        }

    private companion object {
        const val HOUR = 60.0
    }
}
