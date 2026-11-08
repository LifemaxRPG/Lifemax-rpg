package com.lifemax.rpg.feature.pet.brain

/**
 * Every number two cats live together by (docs/specs/CAT_BRAIN_PLAN.md 1.8 pair rows, CB5): when
 * the [SocialDirector] looks for a moment, how likely one is, which kinds a friendship allows, how
 * long each may take, the quiet after each kind, and the beats of the choreography. The friendship
 * gates and the hiss chance are the page's own (`PET_PAIR_CFG`, 29-pet-pair.js), so a pair does
 * what the page's director would let it; the rates are native (the native world runs all day, the
 * page's director only while its room was on screen). Times are seconds unless a name says minutes
 * (`_MIN`); rates are per hour.
 */
object SocialTuning {
    /** How often the director looks for something the two could do together (the page's 6 s). */
    const val CHECK_S = 6.0

    /**
     * The chance per hour a moment starts while both cats are free, before the urge: the company
     * drive, the friendliness of both, the friendship and the time of day multiply it.
     */
    const val RATE_PER_H = 1.7

    /** The urge's spread: company drive low..high, friendly low..high, friendship 0..1. */
    const val URGE_COMPANY_LO = 0.45
    const val URGE_COMPANY_HI = 1.6
    const val URGE_FRIENDLY_LO = 0.6
    const val URGE_FRIENDLY_HI = 1.4
    const val URGE_REL_BASE = 0.55
    const val URGE_REL_GAIN = 1.1

    /** A first meeting does not wait for the urge: it starts at the first check both are free. */
    const val FIRST_MEETING_P = 1.0

    /** Quiet after any moment together, minutes (lo..hi, drawn), before the next may start. */
    const val QUIET_LO_MIN = 14.0
    const val QUIET_HI_MIN = 35.0

    /** A proposal one cat turned down: the director waits this long before asking again. */
    const val DECLINED_S = 90.0

    /** The quiet after each kind (the blob's `pair.cool`), minutes. */
    val COOL_MIN =
        mapOf(
            Social.GREET to 45.0,
            Social.HISS to 90.0,
            Social.CHASE to 75.0,
            Social.GROOM to 110.0,
            Social.NAP to 240.0,
            Social.STANDOFF to 240.0,
            Social.SHADOW to 25.0,
        )

    /** The page's friendship gates (PET_PAIR_CFG): chase from, groom from, nap together from. */
    const val CHASE_REL = 0.15
    const val GROOM_REL = 0.35
    const val NAP_REL = 0.6
    const val GROOM_BACK_REL = 0.45

    /** The page's hiss chance at a nose touch: below [HISS_BELOW], `0.75 − 1.6·rel (+ shy)`. */
    const val HISS_BELOW = 0.25
    const val HISS_BASE = 0.75
    const val HISS_PER_REL = 1.6
    const val HISS_SHY = 0.15
    const val HISS_FRIENDS = 0.04

    /** A cat this skittish counts as the page's "shy" trait. */
    const val SHY_SCORE = 0.7

    /** The page's start friendship of a house with no record yet (PET_PAIR_CFG.REL_START). */
    const val REL_START = 0.1

    /** A nose touch is for cats that have been apart this long (plan 1.8: "met after ≥ 30 min"). */
    const val APART_MIN = 30.0

    /** The kinds' appeal (the page's weights, with native gates). */
    const val W_GREET = 1.3
    const val W_FIRST = 3.0
    const val W_STANDOFF_UNSETTLED = 0.3
    const val W_STANDOFF_SETTLED = 0.08
    const val W_CHASE_REST = 0.3
    const val W_CHASE_BASE = 0.6
    const val W_CHASE_KITTEN = 1.4
    const val W_CHASE_PLAYFUL = 1.5
    const val PLAYFUL_SCORE = 0.7
    const val W_GROOM_RESTING = 1.6
    const val W_GROOM_AWAKE = 0.7
    const val W_GROOM_CALM = 1.2
    const val W_GROOM_AROUSED = 0.6
    const val W_NAP = 2.2

    /** Chase and zoomy play lean on the dawn and dusk peaks, curling up on the nap windows. */
    const val PEAK_GAIN = 1.0
    const val NAP_WINDOW_GAIN = 1.6

    /**
     * A grown cat is sleepy enough to curl up below this energy (plus the lazy share): where a nap
     * becomes eligible for it on its own ([Sleep]).
     */
    const val SLEEPY_ENERGY = 0.65

    /** A day napper this rested may be woken by the other cat for a chase or a nose touch. */
    const val ROUSE_ENERGY = 0.75

    /** Such a wake-up call's appeal against the same moment with both cats up. */
    const val ROUSE_WEIGHT = 1.0

    /** A cat chases only with this much energy. */
    const val CHASE_ENERGY = 0.3

    /** A cat that began its activity this recently is not asked (it has only just settled). */
    const val SETTLED_S = 20.0

    /** A sleeper this deep in its nap is not joined (the page: at least a minute of it left). */
    const val JOIN_LEFT_S = 60.0

    /**
     * A kitten is a cat at least this much younger than the other (stages) for the page's rules.
     */
    const val KITTEN_GAP = 0.2

    /** One stage of a moment that takes longer than this is given up (the page's STAGE_MAX_S). */
    const val STAGE_MAX_S = 26.0

    /** What a cat that finished its part of a stage holds while the other finishes (s). */
    const val HOLD_S = 40.0

    /** The longest whole moment of each kind, minutes, the way there included (the watchdog). */
    val MAX_MIN =
        mapOf(
            Social.GREET to 2.0,
            Social.CHASE to 3.0,
            Social.GROOM to 4.0,
            Social.NAP to BrainTuning.Limits.SLEEP_MAX_MIN,
            Social.STANDOFF to 2.0,
            Social.SHADOW to 6.0,
        )

    /** Chase: rounds at most, the chance of another with roles swapped, the run's length. */
    const val CHASE_ROUNDS = 3
    const val CHASE_SWAP_P = 0.55f
    const val CHASE_LINE_PX = 56f
    const val CHASE_RUN_LO = 80f
    const val CHASE_RUN_HI = 125f
    const val CHASE_CATCH_PX = 36f
    const val TUSSLE_S = 2.4
    const val STALK_LO = 0.5
    const val STALK_HI = 0.8
    const val WIGGLE_S = 0.8

    /** Groom: the licking, the return of the favour, its chance. */
    const val GROOM_LO_S = 10.0
    const val GROOM_HI_S = 20.0
    const val GROOM_BACK_LO_S = 8.0
    const val GROOM_BACK_HI_S = 15.0
    const val GROOM_BACK_P = 0.45f

    /** Curl up: the nap's length (minutes), the second cat's offset in it, the settling knead. */
    const val NAP_LO_MIN = 20.0
    const val NAP_HI_MIN = 90.0
    const val NAP_OFFSET_LO_MIN = -10.0
    const val NAP_OFFSET_HI_MIN = 10.0
    const val NAP_SHORTEST_MIN = 15.0
    const val KNEAD_LO_S = 2.5
    const val KNEAD_HI_S = 4.0

    /** The furthest back (depth units) a spooned cat lies. */
    const val SPOON_BACK_MAX_Z = 6f

    /** Greet: the touch, the rub, the slow blink. */
    const val TOUCH_S = 1.6
    const val RUB_S = 1.8
    const val HISS_RUN_LO = 50f
    const val HISS_RUN_HI = 80f
    const val PART_LO = 30f
    const val PART_HI = 55f
    const val AFTER_LO_S = 4.0
    const val AFTER_HI_S = 8.0

    /** Two meeting further apart than this meet near the one that stays. */
    const val MEET_FAR_PX = 140f
    const val MEET_NEAR_PX = 40f

    /** Standoff: the stare, the loser's retreat. */
    const val STARE_S = 2.6
    const val STANDOFF_CROUCH_S = 1.1
    const val RETREAT_LO = 60f
    const val RETREAT_HI = 95f
    const val PERCH_FOOT_PX = 18f

    /**
     * The page's contest (PET_RANK_CFG): power = dm·[W_DM] + growth·[W_AGE] + confidence·[W_CF];
     * the challenger's edge, the slope. Confidence is the page's; native estimates it from the DNA
     * as the page starts it (petRankBase) and gives the page's leader [LEAD_CF] more.
     */
    const val RANK_K = 9.0
    const val W_DM = 0.36
    const val W_AGE = 0.26
    const val W_CF = 0.38
    const val EDGE = 0.03
    const val LEAD_CF = 0.12

    /** Kitten shadowing: how often, how far behind, how long, when it follows anew. */
    const val SHADOW_RATE_PER_H = 1.6
    const val SHADOW_BEHIND_PX = 52f
    const val SHADOW_LO_MIN = 1.0
    const val SHADOW_HI_MIN = 3.0
    const val SHADOW_FOLLOW_PX = 60f

    /** What the moments do to the mood (the page's petPairFeel values). */
    const val FEEL_GREET_V = 0.04
    const val FEEL_HISSER_V = -0.06
    const val FEEL_HISSER_A = 0.15
    const val FEEL_HISSED_V = -0.03
    const val FEEL_HISSED_A = 0.08
    const val FEEL_CHASE_V = 0.06
    const val FEEL_CHASE_A = 0.1
    const val FEEL_GROOMER_V = 0.05
    const val FEEL_GROOMER_A = -0.05
    const val FEEL_GROOMED_V = 0.07
    const val FEEL_GROOMED_A = -0.08
    const val FEEL_NAP_V = 0.05
    const val FEEL_NAP_A = -0.08
    const val FEEL_WIN_V = 0.05
    const val FEEL_WIN_A = 0.1
    const val FEEL_LOSE_V = -0.07
    const val FEEL_LOSE_A = 0.12

    /** A moment together pulls the company drive down with this time constant (s). */
    const val COMPANY_TAU = 90.0

    /** The floor's depth band a pair meets in, and the edges of the house they keep clear of. */
    const val MEET_Z_LO = 30f
    const val MEET_Z_HI = 60f
    const val EDGE_PX = 30f
}
