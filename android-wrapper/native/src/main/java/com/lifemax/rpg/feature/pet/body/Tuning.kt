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

    /** Turning: a short dip with the mirror at the middle. */
    object Turn {
        const val DURATION_S = 0.16f
        const val MIN_SCALE_X = 0.45f
        const val DIP_Y = 0.06f

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
}
