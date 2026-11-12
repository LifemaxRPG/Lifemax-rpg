package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.EarState
import com.lifemax.rpg.feature.pet.body.Rng
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.TailState
import com.lifemax.rpg.feature.pet.body.ToyBody

/**
 * What an activity may read about its cat when it is scored and scripted: the drives, character,
 * schedule and circumstances, all read-only. [CatMind] is the one implementation.
 */
interface MindView {
    val slot: Int
    val drives: Drives
    val persona: Persona
    val schedule: Schedule
    val book: SpotBook

    /** Local hour of day, with its fraction. */
    val hour: Double

    /** A view of the room is on screen (the owner is there). */
    val ownerPresent: Boolean

    /** On vacation, a sitter's meal is due (the bowl was filled and the cat has not eaten). */
    val sitterMeal: Boolean

    /** The relationship with the owner, 0..1. */
    val trust: Double

    /** Where the cat stands now, and the place it is at (null between places). */
    val surface: SurfaceId
    val x: Float
    val here: Spot?

    /** Valence and arousal, -1..1. */
    val valence: Double
    val arousal: Double

    /** The toy the owner offered for the game being played, or null. */
    val toy: ToyBody?

    /** Seconds the cat has been still: nothing lively and not on the move (roaming). */
    val stillS: Double

    /** Seconds since the cat last ate; large when it has not. */
    val sinceMealS: Double

    /** A toy lying on the floor that the cat could play with on its own, or null. */
    val looseToy: ToyBody?
}

/**
 * One kind of thing a cat does (plan 1.8): when it may be chosen, how much it appeals, where it
 * happens, how long it lasts, how the body plays it and what it does to the drives. Activities are
 * stateless and shared by every cat; the catalogue is an [ActivityRegistry], so later phases (toys,
 * pair, owner, cheers) add theirs without touching the selector or the runner.
 */
interface Activity {
    /** The catalogue name, as the day viewer and the explain ring print it. */
    val id: String

    /** The drive whose level the explain line shows. */
    val mainDrive: Drive

    /** True while the cat sleeps (energy recovers, the other drives slow down). */
    val asleep: Boolean
        get() = false

    /** Only started by a reflex or as another activity's aftermath, never by the selector. */
    val reflexOnly: Boolean
        get() = false

    /** Resumed after a reflex interrupted it, when still wanted (plan 1.5). */
    val resumable: Boolean
        get() = false

    /**
     * The cat is getting about (it explores, plays, travels, looks from a perch): it counts as
     * moving, not still, for its restlessness (see [BrainTuning.Roam]).
     */
    val lively: Boolean
        get() = false

    /** How hard it is on energy while awake (multiplies the drain). */
    val exertion: Double
        get() = 1.0

    /** Arousal the activity settles the cat toward (-1 calm .. 1 excited). */
    val arousal: Double
        get() = 0.0

    /** A tail the activity wants (a window watcher's twitching tip), or null for the mood's. */
    val tail: TailState?
        get() = null

    /** Ears the activity wants (a standoff's flattened ones), or null for the mood's. */
    val ears: EarState?
        get() = null

    /** Minutes before it may be chosen again after it ended. */
    val cooldownMin: Double
        get() = 0.0

    /**
     * The longest it may last, minutes, the way there included; the runner's watchdog ends it after
     * that (see [BrainTuning.Limits]).
     */
    val maxMinutes: Double
        get() = BrainTuning.Limits.DEFAULT_MAX_MIN

    /**
     * Taken up again where it was when the world is restored (false for a moment that does not
     * outlive the app: an owner's game, making way).
     */
    val resumesAfterRestart: Boolean
        get() = true

    /** Played on the floor: a cat up on a perch first gets down (an owner's game with a toy). */
    val onFloor: Boolean
        get() = false

    /** The activity that follows it when it ends by itself (sleep → waking routine), or null. */
    val next: String?
        get() = null

    /** The time constant (s) with which it satisfies [d] (pulls it toward 0), or 0 for none. */
    fun satisfies(d: Drive): Double = 0.0

    /** Whether the cat may do it now at all. */
    fun eligible(m: MindView): Boolean

    /** How much it appeals now, before place, travel, novelty and noise (≥ 0). */
    fun appeal(m: MindView): Double

    /** The places it can happen at (flags of [Spots] the spot must have), or 0 for in place. */
    val placeFlags: Int
        get() = 0

    /** Whether [spot] suits this activity for this cat now (beyond its flags). */
    fun suits(m: MindView, spot: Spot): Boolean = true

    /** The place's own factor for this activity (liking, softness, sun...). */
    fun placeFactor(m: MindView, spot: Spot): Double = 1.0

    /** A short reason for the explain ring. */
    fun reason(m: MindView): String

    /** How many seconds it lasts at its place this time. */
    fun duration(m: MindView, rng: Rng): Double

    /** The beats at the place, filling about [seconds]. */
    fun script(m: MindView, at: Spot?, seconds: Double, rng: Rng, out: Script)

    /** Its aftermath on the drives, when it ends by itself (a meal makes a cat want to wash). */
    fun after(drives: Drives) {}
}

/**
 * The catalogue the selector chooses from, in a stable order (ties go to the earlier). CB1 fills it
 * with [Catalogue.core]; a later phase calls [add] with its own activities.
 */
class ActivityRegistry(activities: List<Activity> = emptyList()) {
    private val list = ArrayList<Activity>(activities)

    /** Every activity, in order. */
    val all: List<Activity>
        get() = list

    /** Adds [a] after the others; an id may be registered only once. */
    fun add(a: Activity): ActivityRegistry {
        require(list.none { it.id == a.id }) { "activity ${a.id} is registered already" }
        list += a
        return this
    }

    /** The activity called [id], or null. */
    fun find(id: String): Activity? {
        for (i in list.indices) if (list[i].id == id) return list[i]
        return null
    }

    /** The index of [a] in [all] (its cooldown and novelty slot). */
    fun indexOf(a: Activity): Int = list.indexOf(a)
}
