package com.lifemax.rpg.feature.pet.body

/**
 * Every number that shapes how the cat feels, with a name. Tuning the cat means editing this file
 * and re-recording the traces; nothing else in the package hides a magic number. Distances are room
 * pixels, speeds px/s, angles radians, times seconds.
 */
object Tuning {
    /** The simulation clock. */
    object Time {
        /** Fixed internal sub-step, so results never depend on the device's frame rate. */
        const val SUBSTEP = 1f / 120f

        /** The most time one [CatBody.step] call will simulate (a stalled frame is clipped). */
        const val MAX_FRAME_DT = 0.25f

        /** Slack when deciding whether a whole sub-step has elapsed (absorbs float rounding). */
        const val TOLERANCE = 1e-5
    }

    /** Walking, trotting and running. Base speeds are the web room's. */
    object Walk {
        const val WALK_SPEED = 32f
        const val RUN_SPEED = 96f
        const val WEAK_SPEED = 17f

        /** Depth units per second at a 100 percent walk multiplier (the web's Z_SPEED). */
        const val Z_SPEED = 20f
        const val Z_RUN_MUL = 2.6f
        const val Z_WEAK_MUL = 0.55f
        const val ACCEL = 100f
        const val DECEL = 80f
        const val RUN_ACCEL = 220f
        const val RUN_DECEL = 420f
        const val Z_ACCEL = 60f

        /** Share of the braking power a stop plans to use, so tracking error never overshoots. */
        const val BRAKE_MARGIN = 0.85f

        /** Distance from the goal inside which the approach glides in proportionally. */
        const val LINEAR_ZONE_PX = 0.5f
        const val Z_DECEL = 80f

        /** Screen pixels covered by one depth unit near the art depth. */
        const val Z_PX_PER_UNIT = 0.44f
        const val ARRIVE_DISTANCE = 0.05f
        const val ARRIVE_SPEED = 12f

        /** Below this speed the legs stop; above [START_SPEED] they start (a dead band). */
        const val STOP_SPEED = 2.5f
        const val START_SPEED = 4f
        const val RUN_ENTER_SPEED = 56f
        const val RUN_EXIT_SPEED = 46f
        const val VERTICAL_ENTER_RATIO = 1.25f
        const val VERTICAL_EXIT_RATIO = 0.9f

        /** A stop that sheds at least this much speed per second, from a gallop, is a skid. */
        const val SKID_DECEL = 300f
        const val SKID_MIN_SPEED = 60f
        const val SKID_ROTATION_KICK = 1.6f
        const val SKID_SQUASH = 0.8f
        const val SKID_SECONDS = 0.35f

        /** Lean into acceleration (radians per px/s^2), capped. */
        const val LEAN_PER_ACCEL = 0.0004f
        const val LEAN_MAX = 0.1f

        /** How far acceleration lifts or drops the tail root (radians per px/s^2). */
        const val TAIL_INERTIA = 0.0015f
    }

    /**
     * Turning round, step by step with the lab's `turn` row (see [Turn]): the time of a whole turn
     * by how the cat was moving, and each step's share of it (the side view with the head turned,
     * the three-quarter view, the front view, the other three-quarter view, the side view facing
     * the new way).
     */
    object Turn {
        const val STILL_S = 0.38f
        const val WALK_S = 0.32f
        const val RUN_S = 0.27f

        /** The head-leading first step and the arriving last step are held a little longer. */
        val STEP_SHARES = floatArrayOf(0.24f, 0.17f, 0.18f, 0.17f, 0.24f)

        /** Braking (px/s^2) of a walking cat that stops to turn round: about two px of slowing. */
        const val PLANT_DECEL = 200f

        /** How long after a turn a stopped stride is held while the cat sets off again. */
        const val STRIDE_HOLD_S = 0.2f

        /** The longest a turn waits for the cat to stand before it turns anyway. */
        const val PLANT_MAX_S = 0.6f

        /** A target closer than this sideways does not make the cat turn round. */
        const val MIN_DISTANCE_PX = 3f
    }

    /** Hops. Duration and height follow the web room's formula. */
    object Jump {
        const val FACE_MIN_DISTANCE = 1f
        const val BASE_S = 0.42f
        const val PER_DX_S = 0.0035f
        const val PER_DY_S = 0.004f
        const val BASE_HEIGHT = 10f
        const val RISE_SHARE = 0.35f
        const val ANTICIPATE_BASE_S = 0.18f
        const val ANTICIPATE_PER_PX = 0.0012f
        const val ANTICIPATE_MAX_S = 0.32f
        const val IMPACT_BASE = 0.75f
        const val IMPACT_PER_PX = 0.03f
        const val IMPACT_MAX = 1.7f
        const val WIGGLE_HZ = 9f
        const val WIGGLE_AMPLITUDE = 0.035f

        /** 0 is a plain parabola; 0.5 slows the top of the arc to half speed (the hang). */
        const val HANG = 0.5f

        /** Nose-up tilt at take-off, nose-down at touchdown. */
        const val TILT = 0.22f
        const val LAND_S = 0.16f
        const val LAUNCH_STRETCH = 1f

        /** Flight progress after which the cat shows the falling frame. */
        const val APEX_FRAME_SWITCH = 0.45f
    }

    /**
     * Parkour: what a cat can jump (see [JumpCapability]), how the planner weighs a route
     * ([ParkourPlanner]) and how a leap is acted out ([LeapDirector]). Heights and reaches are art
     * pixels at the art depth. The room's real rungs, for scale (feet rows, floor 132): bed 128,
     * cat tree mid 100 and top 64, sofa seat 109, counter 91, window sills 73 and 74, fridge top
     * 31, bookcase top 33, side table 105. A bed, pouf or box is a step; the sofa is 23 up, the
     * counter 41, a window sill 58, the tree top 68 and the fridge top 101.
     */
    object Parkour {
        /**
         * Downward acceleration of a flight, px/s^2: snappy but floaty enough to read as a leap.
         */
        const val GRAVITY = 520f

        // ---- the adult cat

        /**
         * Highest apex above the take-off feet from a standing start (sill 58 fits, tree top 68
         * not).
         */
        const val APEX_STAND = 68f

        /**
         * Extra apex a full-speed run-up adds: small on purpose, 68 + 3 stays under 68 + CLEARANCE.
         */
        const val APEX_RUN_BONUS = 3f

        /** Fastest sideways speed of a standing take-off, px/s. */
        const val VX_STAND = 60f

        /** Share of the ground speed at take-off that carries into the flight. */
        const val RUN_CARRY = 0.9f

        /**
         * Furthest sideways distance of any flight, take-off to landing: a gap of two body lengths.
         */
        const val REACH_MAX = 56f

        /**
         * Furthest drop landed safely: counter from the fridge (60) yes, floor from it (101) no.
         */
        const val MAX_DROP = 84f

        // ---- growing up and being weak

        /** Kitten stage 0..5 body scale (growth.json `scale`; a test pins them to Growth). */
        val STAGE_SCALE = floatArrayOf(0.6f, 0.6667f, 0.7333f, 0.8f, 0.8667f, 0.9333f)

        /** Apex and drop scale with body size to this power: a small kitten jumps lower. */
        const val SIZE_APEX_EXPONENT = 1f

        /** Reach speed scales with size to this power (similar bodies: speed goes as the root). */
        const val SIZE_VX_EXPONENT = 0.5f

        /** A weak (hungry) cat's share of apex, reach and drop. */
        const val WEAK_SHARE = 0.6f

        /** A weak cat does not run up to a jump. */
        const val WEAK_RUN_CARRY = 0f

        /** Rig morph multipliers (`lg` leg length, `bl` body length) act with these exponents. */
        const val LEG_APEX_EXPONENT = 0.8f
        const val BODY_VX_EXPONENT = 0.4f
        const val MORPH_MIN = 0.7f
        const val MORPH_MAX = 1.4f

        /**
         * Tiredness (0..1) takes up to this share off apex and reach; mood (-1..1) moves it a
         * little.
         */
        const val FATIGUE_LOSS = 0.25f
        const val MOOD_GAIN = 0.05f

        // ---- running start

        /** Top ground speed of a run-up (the gallop's) and how fast the cat gets there. */
        const val RUN_SPEED = 96f
        const val RUN_ACCEL = 220f

        /**
         * A run-up shorter than the minimum is not taken (the cat stands); the maximum is the
         * longest.
         */
        const val RUN_UP_MIN_PX = 10f
        const val RUN_UP_MAX_PX = 40f

        /**
         * The planner adds a minimum run-up to a jump that takes more than this share of the
         * best...
         */
        const val RUN_WHEN_EFFORT = 0.7f

        /** ...when that makes it at least this much easier. */
        const val RUN_GAIN = 0.1f

        // ---- shapes of a leap

        /** Feet clearance over the target surface at the top of the arc. */
        const val CLEARANCE = 4f

        /** Apex above the take-off of a drop, and of a step. */
        const val DROP_APEX = 6f
        const val STEP_APEX = 8f

        /** A step is at most this far across and this high in the air. */
        const val STEP_DX_MAX = 18f
        const val STEP_APEX_MAX = 22f

        /** A rise or fall up to this is a step, not a leap or a drop. */
        const val STEP_MAX = 10f

        /** Planned flights use this share of the speed limit, so a leap has airtime and an apex. */
        const val COMFORT = 0.9f

        /** Distances from take-off to landing the planner tries, nearest first. */
        val TRY_DX = floatArrayOf(14f, 24f, 36f, 52f, 70f)

        /** A landing is this far in from a surface's ends (when it is wide enough). */
        const val LANDING_INSET = 3f

        /** Arc samples checked against solids, and the margin round a solid. */
        const val ARC_SAMPLES = 24
        const val SOLID_MARGIN = 2f

        // ---- planner costs (seconds, or seconds-equivalent)

        const val HOP_COST_S = 0.7f

        /**
         * A walk between two leaps costs this many times a walk at the end: chains want no walk.
         */
        const val CHAIN_WALK_MUL = 4f
        const val EFFORT_COST_S = 3f
        const val WEAK_EFFORT_MUL = 3f
        const val FIXED_LEAP_S = 0.9f
        const val TIE_BREAK = 0.0007f

        // ---- acting a leap out

        /** A leap that uses this share of the cat's best is taken after a pause and a look up. */
        const val HESITATE_FROM = 0.55f
        const val HESITATE_BASE_S = 0.25f
        const val HESITATE_PER_EFFORT_S = 0.7f

        /** Near the limit the cat also measures the jump: a wiggle of the hindquarters. */
        const val MEASURE_FROM = 0.85f
        const val MEASURE_S = 0.4f
        const val MEASURE_HZ = 7f
        const val MEASURE_ROTATION = 0.05f
        const val LOOK_UP = -0.7f
        const val LOOK_FORWARD = 0.7f

        /** The crouch before the push: standing, in a chain, and off a running start. */
        const val CROUCH_S = 0.28f
        const val CROUCH_CHAIN_S = 0.14f
        const val CROUCH_RUNNING_S = 0.1f

        /**
         * Landing squash, the same in a chain, the settle on the new surface, and a step's landing.
         */
        const val LAND_S = 0.25f
        const val LAND_CHAIN_S = 0.1f
        const val SETTLE_S = 0.12f
        const val STEP_LAND_S = 0.12f

        /** Body tilt along an arc: the lab art already leans, so only a little. */
        const val TILT = 0.06f

        /** Flight progress after which the falling frame shows (a rising frame before). */
        const val FALL_FRAME_AT = 0.5f

        const val IMPACT_BASE = 0.7f
        const val IMPACT_PER_PX_S = 0.0035f
        const val IMPACT_MAX = 1.7f
        const val LAUNCH_STRETCH = 0.9f
    }

    /** Squash and stretch of the whole body. */
    object Squash {
        /** The most a scale may stray from 1, however many kicks pile up. */
        const val LIMIT = 0.35f
        const val POSTURE_DEADBAND = 0.02f
        const val POSTURE_KICK = 20f

        /** How much of an axis's change the other axis takes the opposite way. */
        const val VOLUME_SHARE = 0.5f
        const val LAND_Y = 0.2f
        const val STRETCH_Y = 0.15f
    }

    /** The vertical body bob. */
    object Bob {
        const val FOOTFALL_KICK = 30f
    }

    /** Spring personalities. Frequency in Hz, damping ratio (1 is critical, below 1 rings). */
    object Springs {
        val SQUASH = SpringParams(6.5f, 0.5f)
        val ROTATION = SpringParams(4f, 0.55f)
        val BOB = SpringParams(8f, 0.4f)
        val EAR = SpringParams(7f, 0.4f)
        val EAR_FLAT = SpringParams(5f, 0.7f)
        val TAIL_ROOT = SpringParams(2.2f, 0.5f)
        val TAIL_TIP = SpringParams(4f, 0.3f)
        val WHISKER = SpringParams(5f, 0.45f)
        val LOOK = SpringParams(3.5f, 0.85f)
        val LEAN = SpringParams(3f, 0.8f)
    }

    object Ears {
        /** 36 degrees, the web's ear tilt for airplane ears. */
        const val OUT_ANGLE = 0.63f
        const val RADAR_AMPLITUDE = 0.5f
        const val RADAR_HZ_LEFT = 0.45f
        const val RADAR_HZ_RIGHT = 0.33f
        const val RADAR_PHASE = 1.7f
        const val RELAXED_SHARE = 0.45f
        const val RELAXED_FLAT = 0.25f
        const val SPEED_SPLAY = 0.12f
        const val SPEED_FLATTEN = 0.35f
        const val LOOK_LEAN = 0.2f
        const val FLICK_VELOCITY = 9f
        const val PIN_VELOCITY = 10f
    }

    object Whiskers {
        const val REST = 0.15f
        const val LOOK_FORWARD = 0.4f
        const val EARS_BACK = 0.8f
        const val SPEED_BACK = 0.5f
        const val JOLT_VELOCITY = 8f
    }

    object Tail {
        const val SEGMENTS = 4
        const val BASE_NEUTRAL = 0.2f
        const val BASE_LOW = -0.35f
        const val BASE_TUCKED = -0.6f
        const val BASE_QUESTION = 1.1f
        const val BASE_FLAG = 1.3f
        const val CURL_NEUTRAL = 0.15f
        const val CURL_LOW = 0.1f
        const val CURL_TUCKED = 1.5f
        const val CURL_QUESTION = -1.1f
        const val CURL_FLAG = 0.1f
        const val SWISH_AMPLITUDE = 0.35f
        const val SWISH_HZ = 0.6f
        const val SWISH_TIP_SHARE = 0.5f

        /** Share of a parent's lag a segment inherits: this is what sends waves down the tail. */
        const val COUPLING = 0.45f
        const val TURN_KICK = 2.2f
        const val TWITCH_KICK = 7f
        const val TWITCH_SPILL = 0.4f
        const val TWITCH_GAP_NEUTRAL_MIN = 4f
        const val TWITCH_GAP_NEUTRAL_MAX = 10f
        const val TWITCH_GAP_ACTIVE_MIN = 0.8f
        const val TWITCH_GAP_ACTIVE_MAX = 2.4f
        const val TWITCH_GAP_QUIET_MIN = 10f
        const val TWITCH_GAP_QUIET_MAX = 20f
    }

    object Breath {
        const val AWAKE_HZ = 0.45f
        const val ASLEEP_HZ = 0.25f
        const val AWAKE_DEPTH = 0.012f
        const val ASLEEP_DEPTH = 0.03f

        /** Seconds the rhythm takes to glide between awake and asleep. */
        const val GLIDE_S = 2.5f
    }

    /** Idle blinks, the web's gap of 4 to 11 seconds. */
    object Blink {
        const val GAP_MIN_S = 4f
        const val GAP_MAX_S = 11f

        /**
         * Seconds a sitting cat keeps one of its open-eyed frames before the next (the web changed
         * it every 0.29 s, which on the cat tree read as a head that never settled: see
         * docs/specs/PET_SPRITE_STEADINESS.md, "A sitting cat's head").
         */
        const val OPEN_HOLD_S = 6f
    }

    object Look {
        const val REACTION_DELAY_S = 0.12f
        const val SOFT_X_PX = 40f
        const val SOFT_Z_UNITS = 25f
        const val LEAN_RADIANS = 0.08f
    }

    /** Idle life. Intervals are divided by the cat's own fidget factor. */
    object Idle {
        const val FIDGET_MIN = 0.6f
        const val FIDGET_MAX = 1.6f
        const val SHIFT_MIN_S = 5f
        const val SHIFT_MAX_S = 13f
        const val FLICK_MIN_S = 2.5f
        const val FLICK_MAX_S = 8f
        const val GLANCE_MIN_S = 7f
        const val GLANCE_MAX_S = 18f
        const val DREAM_FLICK_CHANCE = 0.25f
        const val SHIFT_ROTATION_VELOCITY = 0.5f
        const val SHIFT_SQUASH_VELOCITY = 0.6f
        const val GLANCE_X = 0.8f
        const val GLANCE_Y = 0.3f
        const val GLANCE_HOLD_MIN_S = 0.6f
        const val GLANCE_HOLD_MAX_S = 1.5f
    }

    object Reaction {
        const val STARTLE_S = 1f
        const val STARTLE_EARS_S = 0.7f
        const val STARTLE_STRETCH = 1.2f
        const val STARTLE_HOP_VELOCITY = 150f
        const val STARTLE_TAIL_FLICK = 3f
        const val SLOW_BLINK_S = 1.4f
        const val BLINK_CLOSE_END = 0.25f
        const val BLINK_HOLD_END = 0.5f
        const val PURR_S = 3f
        const val PURR_RAMP_S = 0.4f
        const val PURR_AMPLITUDE = 0.3f
        const val PURR_HZ = 18f
        const val BEG_S = 2.4f
        const val BEG_PULSE_S = 0.6f
        const val BEG_BOB_VELOCITY = 40f
        const val BEG_LOOK_UP = -0.6f
    }

    object Interrupt {
        /** The longest an interrupt waits for a cheap beat (a footfall or a blink) to end. */
        const val BEAT_MAX_S = 0.35f
    }

    /** The ground shadow, after the web's: alpha 0.28, shrinking and fading as the cat rises. */
    object Shadow {
        const val ALPHA = 0.28f
        const val MIN_SCALE = 0.3f

        /** Elevation units per pixel (the web maps 68 px to 40 units). */
        const val UNITS_PER_PX = 40f / 68f

        /** Scale lost per elevation unit (the web: 2 percent). */
        const val SCALE_LOSS_PER_UNIT = 0.02f
        const val FADE_HEIGHT_PX = 40f
        const val FADE_SHARE = 0.5f
    }

    /**
     * What a cat does with an empty day (see [Autopilot]): relative weights, times and distances.
     */
    object Autopilot {
        const val FIRST_PAUSE_MIN_S = 1.5f
        const val REST_MIN_S = 1.5f
        const val REST_MAX_S = 6f

        const val W_REST = 2.5f
        const val W_WANDER = 3f
        const val W_TURN = 0.8f
        const val W_GROOM = 2f
        const val W_STRETCH = 1f
        const val W_LOOK_UP = 1f
        const val W_SLOW_BLINK = 0.8f
        const val W_LOAF = 1.2f
        const val W_NAP = 0.5f

        /** In the house: how keen a cat is on each of the house's places. */
        const val W_TRAVEL = 1.4f
        const val W_EAT = 0.6f
        const val W_EAT_HUNGRY = 3f
        const val W_BED = 0.6f
        const val W_COUCH = 0.7f
        const val W_TREE = 0.6f
        const val W_SILL = 0.4f

        /**
         * Parkour goals: the fridge top (via the counter), the bookcase top, a kitchen or
         * living-room window sill.
         */
        const val W_FRIDGE = 0.35f
        const val W_BOOKCASE = 0.3f
        const val W_WINDOW = 0.35f

        /** After this long awake the pull toward a nap is at its full [NAP_GAIN]. */
        const val SLEEPY_AFTER_S = 120f
        const val NAP_GAIN = 3f

        /** A hungry cat: how many times more it rests and the shorter way it walks. */
        const val WEAK_LAZINESS = 2.2f
        const val WEAK_NAP_FACTOR = 1.5f

        const val WALK_MIN_PX = 24f
        const val WALK_MAX_PX = 80f
        const val WEAK_WALK_MAX_PX = 36f
        const val WALK_TWICE_CHANCE = 0.35f
        const val WALK_AGAINST_ROOM_CHANCE = 0.2f

        const val GROOM_MIN_S = 3.5f
        const val NAP_MIN_S = 14f
        const val NAP_MAX_S = 28f
    }
}
