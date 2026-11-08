package com.lifemax.rpg.core.economy

import java.util.Locale

/**
 * What an action produced: its [value], the save it leaves behind, and the toasts the owner saw, in
 * order. Every action in this package is a pure function from a save to an outcome.
 */
data class Outcome<out T>(
    val value: T,
    val save: EconomySave,
    val toasts: List<String> = emptyList(),
) {
    /** Runs [next] on the resulting save and keeps both actions' toasts, these first. */
    fun <R> then(next: (EconomySave) -> Outcome<R>): Outcome<R> =
        next(save).let { it.copy(toasts = toasts + it.toasts) }

    /** The same outcome with its value transformed. */
    fun <R> map(transform: (T) -> R): Outcome<R> = Outcome(transform(value), save, toasts)

    /** The same outcome with one more toast at the end. */
    fun toast(message: String): Outcome<T> = copy(toasts = toasts + message)
}

internal fun EconomySave.unchanged(): Outcome<Unit> = Outcome(Unit, this)

internal fun <T> EconomySave.result(value: T, vararg toasts: String): Outcome<T> =
    Outcome(value, this, toasts.toList())

/** Where new ids come from: random in the app, counted in tests. */
interface IdSource {
    /** A random UUID (record ids, LOG-2). */
    fun uuid(): String

    /** Four random base-36 characters (catalog record ids). */
    fun token(): String
}

/** Hears each write an action attempts (see [EconomyContext.attempts]). */
fun interface SaveAttempts {
    fun attempted(stored: Boolean)
}

/** How a save attempt ends (SAVE-3). */
enum class Storage {
    Writable,
    Full,
    ReadOnly,
}

/**
 * The world an action runs in: the clock, an id source, and the state of the storage it will be
 * written to.
 */
data class EconomyContext(
    val clock: EconomyClock,
    val ids: IdSource,
    val storage: Storage = Storage.Writable,
    val handFacts: DailyHandFacts = DailyHandFacts(),
    /** The device locale, for grouped numbers and currency in names and messages. */
    val locale: Locale = Locale.US,
    /**
     * Whether the workout plan makes a day a rest day (REST-DAY-1; the plan is outside the
     * economy).
     */
    val isPlanRestDay: (String) -> Boolean = { false },
    /**
     * Told of every write the action attempts (SAVE-3: `writeSave()`, inside a save or a plain
     * write), with whether it stored; the economy shadow compares their number with the page's.
     * Production actions pass none.
     */
    val attempts: SaveAttempts? = null,
    /**
     * The page's Daily Hand check around SAVE-2 as the economy shadow composes it (WP6c): the deal
     * the page runs before it verifies the cards, and whether it verifies at all. With one, a save
     * checks twice, in the page's order: the redraw inside the save, then the save's wrapper.
     * Production actions pass none, and a save then verifies once with no deal.
     */
    val handCheck: HandCheck? = null,
) {
    val now: Long
        get() = clock.now

    val today: String
        get() = clock.today

    val nowIso: String
        get() = EconomyClock.iso(clock.now)
}

/**
 * Facts outside the economy that Daily Hand verification reads (SAVE-2): water, the cat, the plan.
 */
data class DailyHandFacts(
    val waterPercent: Double = 0.0,
    val petCounts: Map<String, Int> = emptyMap(),
    val planDone: Boolean = false,
)

/**
 * The Daily Hand's own step before each SAVE-2 verification, as the page runs it: `dhAutoVerify`
 * first calls `dhEnsure()` (QUESTS_SPEC QST-41 to QST-43), which may deal a new day's cards, and
 * does not verify at all while the hand is busy or when that deal throws.
 */
fun interface HandCheck {
    /** The save after the page's deal, and whether its cards are verified next. */
    fun deal(save: EconomySave): Dealt
}

/** What [HandCheck.deal] leaves: the [save] and whether the cards are then verified ([verify]). */
data class Dealt(val save: EconomySave, val verify: Boolean)
