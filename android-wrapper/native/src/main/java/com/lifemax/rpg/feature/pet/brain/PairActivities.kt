package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.EarState
import com.lifemax.rpg.feature.pet.body.Rng
import com.lifemax.rpg.feature.pet.body.TailState

/**
 * The kinds of moment two cats share (the page's `state.petPair.n` keys and the `social` command's
 * kinds, plus [SHADOW], a kitten following the grown cat, which the page does not count).
 */
object Social {
    const val GREET = "greet"
    const val HISS = "hiss"
    const val CHASE = "chase"
    const val GROOM = "groom"
    const val NAP = "nap"
    const val STANDOFF = "standoff"
    const val SHADOW = "shadow"

    /** The kinds the page records (`social`), in the page's order. */
    val RECORDED = listOf(GREET, HISS, CHASE, GROOM, NAP, STANDOFF)

    /** Every pair activity of the catalogue, in registry order. */
    val ACTIVITIES: List<Activity> by lazy {
        listOf(PairGreet, PairChase, PairGroom, SettleIn, CurlUp, PairStandoff, Shadow)
    }

    /** The blob's word for two cats curled up together (the one moment a restart keeps). */
    val CURLED_ACT: String
        get() = MindState.actKey(CurlUp.id)

    /** True for the activities of a moment together (and a kitten's shadowing). */
    fun isPair(a: Activity): Boolean = a is PairActivity

    /**
     * Whether two cats doing [a] and [b] may lie or stand as close as their pose asks: only when
     * both play the same moment together (the curled-up nap included). Every other pair of
     * activities keeps [BrainTuning.Apart].
     */
    fun shares(a: Activity, b: Activity): Boolean =
        a is PairActivity && b is PairActivity && a.shared && b.shared
}

/**
 * One cat's part of a moment together (plan 1.8 pair rows): never chosen by the selector, only
 * started by the [SocialDirector], which writes the script of each stage itself; the activity names
 * the moment for the explain ring and the day viewer, satisfies the wish for the other cat, and
 * bounds the moment with its longest ([maxMinutes], the runner's watchdog). A moment does not
 * outlive the app (a restart begins afresh), except two cats curled up asleep ([CurlUp]).
 */
internal abstract class PairActivity(
    final override val id: String,
    /** The kind of moment (one of [Social]). */
    val kind: String,
) : Activity {
    override val mainDrive = Drive.COMPANY
    override val reflexOnly = true
    override val resumesAfterRestart = false
    override val maxMinutes = SocialTuning.MAX_MIN.getValue(kind)

    /** The two cats may stand closer than [BrainTuning.Apart] while both play it. */
    open val shared = true

    override fun satisfies(d: Drive) = if (d == Drive.COMPANY) SocialTuning.COMPANY_TAU else 0.0

    override fun eligible(m: MindView) = true

    override fun appeal(m: MindView) = 0.0

    override fun reason(m: MindView) = "together"

    override fun duration(m: MindView, rng: Rng) = 0.0

    /** A moment the director no longer runs (after a restart): the cat only sits up a moment. */
    override fun script(m: MindView, at: Spot?, seconds: Double, rng: Rng, out: Script) {
        out.act(Rows.SIT, Beats.SIT_LO)
    }
}

/** A nose touch (and, early on, a hiss). */
internal object PairGreet : PairActivity("greet", Social.GREET) {
    override val tail = TailState.QUESTION_MARK
}

/** Chase: stalk, pounce, a bolt across the room, a belly-up tussle, roles swapped. */
internal object PairChase : PairActivity("chase", Social.CHASE) {
    override val exertion = CHASE_EXERTION
    override val arousal = CHASE_AROUSAL
    override val tail = TailState.TIP_TWITCH

    override fun satisfies(d: Drive) =
        when (d) {
            Drive.COMPANY -> SocialTuning.COMPANY_TAU
            Drive.PLAY -> PLAY_TAU
            else -> 0.0
        }
}

/** Grooming each other: one licks, the other purrs, the favour returned. */
internal object PairGroom : PairActivity("groom each other", Social.GROOM) {
    override val arousal = -0.4

    override fun satisfies(d: Drive) =
        when (d) {
            Drive.COMPANY -> SocialTuning.COMPANY_TAU
            Drive.GROOM -> GROOM_TAU
            else -> 0.0
        }
}

/**
 * Curled up together (the spoon pose on one soft spot): a nap of its own, which ends with the
 * waking routine and, alone of the moments, is taken up again after a restart when both cats are
 * still lying together ([SocialDirector.restored]).
 */
internal object CurlUp : PairActivity("curl up", Social.NAP) {
    override val asleep = true
    override val resumesAfterRestart = true
    override val next = WakingRoutine.id
    override val arousal = -0.8

    override fun satisfies(d: Drive) =
        when (d) {
            Drive.COMPANY -> SocialTuning.COMPANY_TAU
            Drive.COMFORT -> COMFORT_TAU
            else -> 0.0
        }

    override fun duration(m: MindView, rng: Rng) =
        Beats.minutes(m, rng, SocialTuning.NAP_LO_MIN, SocialTuning.NAP_HI_MIN)

    override fun script(m: MindView, at: Spot?, seconds: Double, rng: Rng, out: Script) {
        out.act(Rows.SLEEP, maxOf(seconds, Rows.once(Rows.SLEEP)))
    }
}

/** Going to the spot where two cats will curl up, and lying down there (awake still). */
internal object SettleIn : PairActivity("settle in", Social.NAP) {
    override val arousal = -0.5
}

/** A standoff over a perch: a stare, ears back, one backs down. */
internal object PairStandoff : PairActivity("standoff", Social.STANDOFF) {
    override val arousal = 0.6
    override val tail = TailState.LOW_SWISH
    override val ears = EarState.AIRPLANE_FLAT
}

/**
 * A kitten shadowing the grown cat: it trots after it and does what it does a few steps behind,
 * never on top of it (so not [shared]).
 */
internal object Shadow : PairActivity("shadow", Social.SHADOW) {
    override val shared = false
}

private const val CHASE_EXERTION = 2.0
private const val CHASE_AROUSAL = 0.8
private const val PLAY_TAU = 60.0
private const val GROOM_TAU = 60.0
private const val COMFORT_TAU = 12.0 * 60.0
