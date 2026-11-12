package com.lifemax.rpg.feature.pet.brain

/**
 * Every number the cat brain behaves by (docs/specs/CAT_BRAIN_PLAN.md section 1), in one place so a
 * day can be retuned from the day viewer without hunting through the code. Times are seconds unless
 * a name says minutes (`_MIN`) or hours (`_H`); drives, likings and shares are 0..1. The knobs that
 * matter most for believability are listed in docs/specs/CAT_BRAIN_DAYS.md.
 */
object BrainTuning {
    /** Simulated time: a think quantum is 30 sub-steps of 1/120 s. */
    object Time {
        const val QUANTUM_S = 0.25
        const val MINUTE = 60.0
        const val HOUR = 3600.0
    }

    /**
     * How each drive moves (plan 1.3). A drive relaxes toward a target with a time constant, in
     * closed form per quantum: awake drives rise toward 1 with [RISE_TAU] (divided by the persona's
     * gain), an activity that satisfies one pulls it toward 0 with its own time constant.
     */
    object Drives {
        /** Awake, energy falls toward 0 with this time constant (hours ×3600), times exertion. */
        const val ENERGY_AWAKE_TAU = 4.3 * Time.HOUR

        /** Asleep, energy recovers toward 1; a soft spot recovers twice as fast. */
        const val ENERGY_SLEEP_TAU = 75.0 * Time.MINUTE
        const val SOFT_SLEEP_GAIN = 1.6

        /**
         * Rise time constants of the other drives while awake, by [Drive.ordinal] (energy unused).
         */
        val RISE_TAU =
            doubleArrayOf(
                0.0,
                40.0 * Time.MINUTE, // play (boredom); roaming: was 3 h
                30.0 * Time.MINUTE, // curiosity; roaming: was 95 min
                4.0 * Time.HOUR, // social (the owner): faster while the owner is there
                60.0 * Time.MINUTE, // company (the other cat); roaming: was 2.5 h
                110.0 * Time.MINUTE, // comfort
                70.0 * Time.MINUTE, // territory; roaming: was 2.6 h
                2.2 * Time.HOUR, // groom
            )

        /** Asleep, the awake drives rise this many times slower (a sleeping cat gets no ideas). */
        const val ASLEEP_SLOWDOWN = 5.0

        /** The owner's presence makes the wish for the owner rise this much faster. */
        const val OWNER_SOCIAL_GAIN = 6.0

        /** A weak (hungry) cat's energy never rises above this (plan 1.4). */
        const val WEAK_ENERGY_CAP = 0.5

        /** A drive never sits exactly on its bound, so curves keep a little slope. */
        const val FLOOR = 0.0
        const val CEIL = 1.0
    }

    /**
     * Roaming: the dials that make awake cats explore, play and move about instead of lying about
     * (docs/specs/CAT_TAB_PLAN.md "Roaming"). A cat that has been still for a while grows restless
     * (`1 - e^(-still/tau)`, quicker for a kitten, slower for a calm cat); restlessness, energy and
     * the time of day make up its urge to get up, and that urge scales every lively activity
     * (explore, zoomies, play, wander, window, perches) up and the rest (loaf) down. Rests are
     * short and varied. Sleep needs the night, a nap window, a meal or real tiredness. The other
     * retuned numbers (drive rise times, durations, cooldowns) are in [Drives], [Schedule],
     * [Durations] and [Cooldowns], marked "roaming". Every number here is seconds unless a name
     * says minutes (`_MIN`), a share or a gain.
     */
    object Roam {
        /** Still this long (s) and an adult is 63% restless; a kitten's time is [KITTEN_TAU]. */
        const val RESTLESS_TAU_S = 50.0
        const val KITTEN_TAU = 0.4

        /** A lazy cat's time grows by up to this share (a calm Lily waits longer than Ted). */
        const val LAZY_TAU_GAIN = 0.8

        /** Restlessness never falls below this (a cat just moved still has the odd itch). */
        const val RESTLESS_FLOOR = 0.5

        /** Energy gate: no urge at [ENERGY_LO], full at [ENERGY_LO] + [ENERGY_SPAN]. */
        const val ENERGY_LO = 0.12
        const val ENERGY_SPAN = 0.4

        /** The urge in a nap window, in bed hours, and its ceiling at the dawn and dusk peaks. */
        const val NAP_WINDOW_URGE = 0.6
        const val BED_URGE = 0.25
        const val PEAK_URGE_MAX = 1.4

        /** How much of the rest (loaf) appeal restlessness takes away (0 none, 1 all). */
        const val REST_DAMP = 0.8

        /** A rest's length, seconds, before the persona; a kitten's is [KITTEN_REST] of that. */
        const val REST_LO_S = 15.0
        const val REST_HI_S = 60.0
        const val KITTEN_REST = 0.5

        /**
         * A tired cat rests longer: below [REST_TIRED_FROM] energy the length grows up to
         * ×(1+gain).
         */
        const val REST_TIRED_FROM = 0.6
        const val REST_TIRED_GAIN = 1.5

        /**
         * A rest is not one long lie: chunks of loafing (s), then a sit, a wash, a look about or a
         * slow blink, in these shares; no posture lasts longer than [REST_CHUNK_HI_S] (the
         * measurement's cap is a little more).
         */
        const val REST_CHUNK_LO_S = 12.0
        const val REST_CHUNK_HI_S = 35.0
        const val REST_SIT_LO_S = 5.0
        const val REST_SIT_HI_S = 14.0
        const val REST_WASH_LO_S = 4.0
        const val REST_WASH_HI_S = 10.0
        const val REST_LOOK_LO_S = 3.0
        const val REST_LOOK_HI_S = 6.0
        const val REST_SHARE_LOAF = 0.2
        const val REST_SHARE_SIT = 0.35
        const val REST_SHARE_WASH = 0.25
        const val REST_SHARE_LOOK = 0.2

        /** Explore: a look at a place (any perch, the window, a bowl, the rug), 10..38 s there. */
        const val EXPLORE_BASE = 1.0
        const val EXPLORE_ENERGY = 0.18
        const val EXPLORE_LO_S = 4.0
        const val EXPLORE_HI_S = 14.0

        /** Shorter sits in the older lively activities: a wander's, a perch visit's (s). */
        const val WANDER_SIT_LO_S = 4.0
        const val WANDER_SIT_HI_S = 10.0
        const val PERCH_SIT_LO_S = 8.0
        const val PERCH_SIT_HI_S = 20.0

        /** Curiosity's share of the appeal: [EXPLORE_CURIOSITY_FLOOR] at 0 up to 1 at full. */
        const val EXPLORE_CURIOSITY_FLOOR = 0.5

        /** Explore thinks of this share of the places at a time (and each one costs a route). */
        const val EXPLORE_SAMPLE = 0.4

        /** Another cat's bowl is worth a sniff, less than the cat's own places. */
        const val OTHER_BOWL = 0.5

        /**
         * A place visited lately is less interesting: × by how many visits ago (newest first), so a
         * cat goes round the whole house before it revisits the first place. Not visited: 1.
         */
        val REVISIT = doubleArrayOf(0.2, 0.35, 0.5, 0.65, 0.8, 0.9)

        /** Zoomies: a kitten's (or a playful cat's) mad dash across the floor. */
        const val ZOOM_BASE = 0.55
        const val ZOOM_ENERGY = 0.35
        const val ZOOM_LEGS_LO = 3
        const val ZOOM_LEGS_HI = 5
        const val ZOOM_LEG_MIN_PX = 70f
        const val ZOOM_LEG_MAX_PX = 260f

        /** A burst of running or stalking keeps this far from the other cat on the floor (px). */
        const val MATE_GAP_PX = 80f

        /** The floor depths a zoomie runs at (in front of the furniture), and a burst's length. */
        const val ZOOM_Z_LO = 50f
        const val ZOOM_Z_HI = 68f
        const val ZOOM_LO_S = 10.0
        const val ZOOM_HI_S = 22.0
        const val ZOOM_COOLDOWN_MIN = 5.0
        const val ZOOM_AFTER_SIT_S = 3.0
        const val ZOOM_AFTER_WASH_S = 5.0

        /** Zoomies lean on the dawn and dusk peaks by this gain on the peak (1 = no lean). */
        const val ZOOM_PEAK_GAIN = 1.8

        /** Solo play: with a toy lying about (rounds), or stalking something unseen. */
        const val SOLO_BASE = 0.7
        const val SOLO_ENERGY = 0.3
        const val SOLO_COOLDOWN_MIN = 4.0
        const val SOLO_LO_S = 12.0
        const val SOLO_HI_S = 25.0
        const val SOLO_ROUNDS_KITTEN = 2
        const val SOLO_ROUNDS_ADULT = 1
        const val SOLO_TOY_REACH_PX = 220f
        const val SOLO_STALK_S = 1.4
        const val SOLO_POUNCE_PX = 60f
        const val SOLO_SWAT_LO_S = 2.0
        const val SOLO_SWAT_HI_S = 4.5
        const val SOLO_ROLL_CHANCE = 0.4f

        /**
         * Sleep needs the night, or energy below [TIRED], or, in a nap window, below these (a lazy
         * cat's a little above), or a meal just eaten ([MEAL_DOZE_MIN]).
         */
        const val SLEEPY_ADULT = 0.5
        const val SLEEPY_KITTEN = 0.6
        const val TIRED = 0.28

        /** After a meal a cat dozes for this long if its energy is below [MEAL_DOZE_ENERGY]. */
        const val MEAL_DOZE_MIN = 30.0
        const val MEAL_DOZE_ENERGY = 0.75

        /** A meal makes a cat drowsy: energy goes this share of the way to 0. */
        const val MEAL_DROWSY = 0.12
    }

    /** Valence and arousal (plan 1.7): half-lives and baselines. */
    object Mood {
        const val VALENCE_HALF_LIFE = 4.0 * Time.MINUTE
        const val AROUSAL_HALF_LIFE = 2.0 * Time.MINUTE
        const val BASE_VALENCE = 0.15
        const val FRIENDLY_VALENCE = 0.25
        const val SKITTISH_VALENCE = 0.2
        const val HUNGER_VALENCE_PER_STAGE = 0.14
        const val TIRED_AROUSAL = 0.35
        const val EAT_VALENCE = 0.35
        const val EAT_AROUSAL = 0.15
        const val LAND_AROUSAL = 0.25
        const val BEG_VALENCE = -0.08

        /** Quanta an ear or tail state must be wanted before the body is told (no flicker). */
        const val AFFECT_HOLD_QUANTA = 8
        const val EARS_FLAT_V = -0.3
        const val EARS_FLAT_A = 0.2
        const val EARS_RELAXED_V = 0.2
        const val EARS_RELAXED_A = 0.2
        const val TAIL_FLAG_V = 0.4
        const val TAIL_FLAG_A = 0.2
        const val TAIL_SWISH_V = -0.2
        const val TAIL_SWISH_A = 0.3
        const val TAIL_TUCK_V = -0.3
    }

    /** DNA scores to weights (plan 1.4): each is `lerp(lo, hi, score)`. */
    object Persona {
        const val DEFAULT_SCORE = 0.5
        const val CURIOSITY_LO = 0.7
        const val CURIOSITY_HI = 1.5
        const val TERRITORY_LO = 0.6
        const val TERRITORY_HI = 1.6
        const val PLAY_LO = 0.8
        const val PLAY_HI = 1.4
        const val SOCIAL_LO = 0.7
        const val SOCIAL_HI = 1.4
        const val DRAIN_LO = 0.75
        const val DRAIN_HI = 1.45
        const val TEMPERATURE_LO = 0.08
        const val TEMPERATURE_HI = 0.30
        const val DURATION_LO = 1.2
        const val DURATION_HI = 0.8
        const val HIDDEN_LO = 1.0
        const val HIDDEN_HI = 1.8
        const val HIGH_LO = 0.8
        const val HIGH_HI = 1.5
        const val SKITTISH_HIGH_LOSS = 0.35
        const val REST_LO = 0.75
        const val REST_HI = 1.6
        const val EXPLORE_LO = 0.6
        const val EXPLORE_HI = 1.5
        const val PARKOUR_LO = 0.5
        const val PARKOUR_HI = 1.6
        const val SKITTISH_PARKOUR_LOSS = 0.4
        const val VOCAL_BEG_LO = 0.8
        const val VOCAL_BEG_HI = 1.3

        /** Kittens (stage 0..5): play ×1.6, energy drains ×1.3, short naps. */
        const val KITTEN_PLAY = 1.6
        const val KITTEN_DRAIN = 2.0
        const val KITTEN_EXPLORE = 1.25
        const val KITTEN_NAP_MAX_MIN = 45.0
        const val KITTEN_NAP_MIN_MIN = 25.0

        /** A lazy cat is ready to nap sooner: sleep eligible below 0.65 + this × lazy energy. */
        const val LAZY_SLEEPY = 0.08

        /** A kitten is ready to nap sooner (sleep becomes eligible below this energy). */
        const val KITTEN_SLEEPY = Roam.SLEEPY_KITTEN // roaming: was 0.85

        /** A weak cat lies about more than it sleeps: loaf ×, sleep × (plan 1.4). */
        const val WEAK_LOAF = 1.5
        const val WEAK_SLEEP = 0.35

        /** Hunger stages: begs from 2, weak from 3, rests by the bowl from 4 (plan 1.4). */
        const val BEG_STAGE = 2
        const val WEAK_STAGE = 3
        const val BOWL_STAGE = 4

        /** A weak cat keeps within this of its bowl (px), and at the last stage this. */
        const val WEAK_BOWL_REACH = 60.0
        const val BOWL_REST_REACH = 30.0
    }

    /** The day (plan 1.6): per cat per day from the day seed. */
    object Schedule {
        const val WAKE_LO_H = 6.0
        const val WAKE_HI_H = 7.4
        const val WAKE_LAZY_H = 0.6
        const val BED_LO_H = 22.0
        const val BED_HI_H = 23.5
        const val DAWN_HALF_H = 0.75
        const val DUSK_LO_H = 17.5
        const val DUSK_HI_H = 20.0
        const val DUSK_HALF_H = 1.0
        const val NAPS_MIN = 2
        const val NAPS_MAX = 3 // roaming: was 4
        const val NAP_LEN_LO_H = 0.5 // roaming: was 0.8
        const val NAP_LEN_HI_H = 1.3 // roaming: was 1.8

        /** Nap window starts are drawn from these hours, weighted to early afternoon. */
        val NAP_STARTS = doubleArrayOf(10.0, 12.5, 13.5, 14.5, 15.5, 20.5)
        val NAP_WEIGHTS = doubleArrayOf(0.6, 1.2, 1.4, 1.2, 0.8, 0.5)

        const val SLEEP_IN_BED = 3.0
        const val SLEEP_IN_NAP = 1.8
        const val SLEEP_AT_PEAK = 0.45
        const val ACTIVE_AT_PEAK = 1.6
        const val ACTIVE_IN_BED = 0.3
        const val SUNNY_FROM_H = 10.0
        const val SUNNY_UNTIL_H = 16.0
        const val SUNNY_GAIN = 1.8
        const val WINDOW_DARK = 0.35
        const val LAMPS_SETTLE = 1.25

        /** A rest day lowers the energy target (drains faster) and doubles nap windows (1.9). */
        const val REST_DAY_DRAIN = 1.15

        /** A night sleep bout lasts this long (a cat stirs between bouts), hours. */
        const val NIGHT_BOUT_LO_H = 2.2
        const val NIGHT_BOUT_HI_H = 4.5
    }

    /** Choosing (plan 1.5). */
    object Select {
        /** `score /= 1 + travelSeconds / TRAVEL_S + effort²`. */
        const val TRAVEL_S = 30.0
        val NOVELTY = doubleArrayOf(0.5, 0.7, 0.85)
        const val NOISE = 0.06

        /** The same activity again somewhere else (loaf, then loaf on the next cushion): ×. */
        const val SAME_ELSEWHERE = 0.4
        const val TOP = 4

        /** A suspended activity is resumed after a reflex when it is younger than this. */
        const val RESUME_MAX_S = 15.0 * Time.MINUTE
        const val RESUME_MIN_LEFT_S = 30.0

        /** The watchdog ends an activity whose script ran this much past its time. */
        const val WATCHDOG_SLACK_S = 120.0

        /** Route costs are cached per standing spot rounded to this many px. */
        const val ROUTE_CACHE_PX =
            40f // roaming: was 12; this only prices a way, the real one is planned exactly
        const val ROUTE_CACHE_MAX = 4096 // roaming: was 512; cats pick every half minute now
    }

    /** Place liking (plan 1.5 memory): page likes and the world's own dwell memory. */
    object Places {
        const val DEFAULT_LIKE = 0.3
        const val LIKE_FLOOR = 0.6
        const val LIKE_GAIN = 0.8
        const val SOFT_GAIN = 1.4
        const val DWELL_LEARN_PER_H = 0.06
        const val NOT_HIDDEN_SKITTISH = 0.8
        const val FLOOR_GAP = 40.0
    }

    /**
     * Two cats never lie on top of each other. Cats closer than [OVERLAP_PX] on one surface (and,
     * on the floor, within [OVERLAP_Z] of depth) for [OVERLAP_QUANTA] think quanta in a row are
     * overlapping: one of them makes way, walking at least [ASIDE_PX] from the other first (a cat
     * passing another on its way is not held up: that takes less than the quanta). The bowls stand
     * further apart than [OVERLAP_PX], so two cats eat side by side. A deliberate pose together
     * (the pair's curl-up, a later phase) is side by side at least [OVERLAP_PX] apart, each in its
     * own pose.
     */
    object Apart {
        const val OVERLAP_PX = 20f
        const val OVERLAP_Z = 14f
        const val OVERLAP_QUANTA = 20
        const val ASIDE_PX = 48f
        const val ASIDE_SIT_S = 2.5
        const val ASIDE_GROOM_S = 3.0
    }

    /**
     * The longest each kind of activity may last, minutes, the way there included: the runner's
     * watchdog ends anything older (it never fires in a healthy brain; [ActivityRunner.watchdogs]
     * counts it). A night's sleep bout is at most 4.5 h plus its jitter; a day nap is 120 minutes
     * at most, stretched by a lazy, steady cat's persona.
     */
    object Limits {
        const val DEFAULT_MAX_MIN = 60.0
        const val SLEEP_MAX_MIN = 300.0
        const val SHORT_MAX_MIN = 10.0
    }

    /**
     * The developer's parkour demo ([DemoParkour]): a tour of up to [STOPS] perches, [STOP_S]
     * seconds at each (a survey and a look, then on), the next one chosen for the most jumps.
     */
    object Demo {
        const val STOPS = 3
        const val STOP_S = 6.0
    }

    /**
     * The owner's game (plan 1.8 "owner play"): who comes, how long it lasts, how it feels. A cat
     * asleep longer than [BrainTuning.Care.DEEP_SLEEP_S] does not stir; one dozing wakes only with
     * [WAKE_ENERGY] left; one awake plays from [TIRED_ENERGY]. Rounds (pick up, carry, toss, chase,
     * pounce, bat) are 1 to [ROUNDS_MAX], more for a rested, playful, happy cat.
     */
    object Play {
        const val WAKE_ENERGY = 0.45
        const val TIRED_ENERGY = 0.15
        const val ROUNDS_MAX = 3
        const val RESTED = 0.45
        const val PLAYFUL = 0.5
        const val GLUM = -0.3

        /** An offered toy lands this far in front of the cat. */
        const val DROP_AHEAD_PX = 30f

        /** A cat coming down from a perch to play lands this far beside the toy. */
        const val APPROACH_PX = 28f

        /** A game this long at the toy is play the page counts (less: a sniff and a pat). */
        const val PLAYED_MIN_S = 6.0

        /** How far a cat carries the toy before it tosses it, and where it leaves it. */
        const val CARRY_PX = 46f
        const val SETTLE_PX = 24f

        /** Toss power: a calm cat's flick to an excited one's. */
        const val TOSS_LO = 0.35f
        const val TOSS_HI = 0.8f
        const val BAT_POWER = 0.5f
        const val CHASE_S = 5.0

        /** Rough seconds of each toy beat (they bound the watchdog, the body decides). */
        const val PICK_S = 3.0
        const val CARRY_S = 3.0
        const val TOSS_S = 1.2
        const val POUNCE_S = 2.5
        const val BAT_S = 1.2
        const val SET_S = 1.5
        const val GROOM_AFTER_S = 3.0

        /** What the game does to the mood when it starts. */
        const val VALENCE = 0.25
        const val AROUSAL = 0.35

        /** Play pulls the boredom drive down with this time constant (s). */
        const val SATISFY_TAU = 40.0
        const val EXERTION = 1.8
        const val MAX_MIN = 10.0
    }

    /** Durations, minutes (plan 1.8 table), before the persona's duration multiplier. */
    object Durations {
        const val NAP_LO_MIN = 20.0 // roaming: was 30
        const val NAP_HI_MIN = 70.0 // roaming: was 120
        const val LOAF_LO_MIN = 8.0
        const val LOAF_HI_MIN = 35.0
        const val GROOM_LO_MIN = 0.4 // roaming: was 0.6
        const val GROOM_HI_MIN = 1.5 // roaming: was 3
        const val WANDER_LO_MIN = 0.3 // roaming: was 1
        const val WANDER_HI_MIN = 1.0 // roaming: was 4
        const val WINDOW_LO_MIN = 1.0 // roaming: was 6
        const val WINDOW_HI_MIN = 3.5 // roaming: was 25
        const val PARKOUR_LO_MIN = 0.8 // roaming: was 3
        const val PARKOUR_HI_MIN = 3.0 // roaming: was 12
        const val BEG_LO_MIN = 0.5
        const val BEG_HI_MIN = 2.0
        const val SCRATCH_LO_S = 6.0
        const val SCRATCH_HI_S = 15.0
        const val KNEAD_LO_S = 4.0
        const val KNEAD_HI_S = 12.0
    }

    /** Care the page owns, as the brain sees it. */
    object Care {
        /** On vacation the sitter fills the bowl at these local hours. */
        val SITTER_MEAL_H = doubleArrayOf(7.5, 18.0)

        /** A sleeping cat is in deep sleep after this long; a hungry one is not woken to beg. */
        const val DEEP_SLEEP_S = 6.0 * Time.MINUTE

        /** The drives of a cat the world starts without a stored state. */
        val START_DRIVES = doubleArrayOf(0.6, 0.3, 0.3, 0.2, 0.2, 0.5, 0.3, 0.3)
    }

    /** Cooldowns after an activity ends, minutes. */
    object Cooldowns {
        const val GROOM_MIN = 20.0
        const val WANDER_MIN = 3.0 // roaming: was 15
        const val WINDOW_MIN = 8.0 // roaming: was 30
        const val PARKOUR_MIN = 10.0 // roaming: was 120
        const val SCRATCH_MIN = 25.0 // roaming: was 90
        const val KNEAD_MIN = 60.0
        const val BEG_MIN = 3.0
        const val LOAF_MIN = 0.0
        const val SLEEP_MIN = 10.0
    }
}
