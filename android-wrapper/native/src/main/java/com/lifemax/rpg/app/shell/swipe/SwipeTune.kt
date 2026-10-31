package com.lifemax.rpg.app.shell.swipe

import org.json.JSONObject

/** Which haptic the catch plays, so the owner can compare kinds on One UI (the `haptic` key). */
enum class HapticStyle(val wire: String) {
    /** GESTURE_THRESHOLD_ACTIVATE / DEACTIVATE on API 34+, CLOCK_TICK below. */
    Threshold("threshold"),
    Tick("tick"),
    Click("click"),

    /** No detent haptic. A refusal still plays its REJECT. */
    Off("off");

    companion object {
        fun of(wire: String?): HapticStyle? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * The numbers behind the swipe's feel (ADR 0011). Kotlin holds the defaults; the page mirrors them
 * in `LMA_TABSWIPE` and may override any key in `swipe.tune`, which [parse] clamps, so the owner
 * can tune the feel over the air with no new APK. The shape of the curve, the gates and the clamps
 * themselves are not tunable.
 *
 * Fractions are of the screen width W; speeds are in dp/s; stiffness is in 1/s² (unit mass).
 */
data class SwipeTune(
    /** a: the screen follows the finger 1:1 up to here. */
    val knee: Float = 0.18f,
    /** b: the catch. Crossing it latches the screen to the finger. */
    val breakAt: Float = 0.45f,
    /** m: the slope of the curve at the catch (1 = no tension). */
    val heavy: Float = 0.25f,
    /** h: once latched, the catch re-engages only below b − h. */
    val hyst: Float = 0.06f,
    /** Damping ratio of the pop that carries the screen to the finger at the latch. */
    val popDamping: Float = 0.65f,
    /** Stiffness of the pop, and of the critically damped return at an unlatch. */
    val popStiffness: Float = 1200f,
    /** E: how far a wall or an end lets the screen go. */
    val wallMax: Float = 0.08f,
    /** κ: the rubber band's slope at the start. */
    val wallSlope: Float = 0.35f,
    /** A release at least this fast toward the neighbour commits without the latch... */
    val flingDp: Float = 1200f,
    /** ...after at least this much travel. */
    val flingMin: Float = 0.12f,
    /** A release at least this fast away from the neighbour cancels, even when latched. */
    val backFlingDp: Float = 600f,
    /** The least stiffness of the commit settle (it grows with the release speed). */
    val commitK: Float = 1000f,
    /** The least stiffness of the cancel settle. */
    val cancelK: Float = 800f,
    /**
     * The lock's slop, as a multiple of the platform's scaledTouchSlop. Never below 1: a child
     * scroller claims at the platform's slop, and it must be able to before the swipe does.
     */
    val slopFactor: Float = 1f,
    /** The lock needs |dx| ≥ lockRatio·|dy|. */
    val lockRatio: Float = 1.3f,
    /** The least edge zone, in dp, where a DOWN never starts a swipe (system Back). */
    val edgeDp: Float = 24f,
    val haptic: HapticStyle = HapticStyle.Threshold,
) {
    /** This tune with every value inside its clamp; the catch is clamped after the knee. */
    fun clamped(): SwipeTune {
        val a = knee.within(0.05f, 0.35f, DEFAULT.knee)
        return SwipeTune(
            knee = a,
            breakAt = breakAt.within(a + 0.10f, 0.70f, DEFAULT.breakAt),
            heavy = heavy.within(0.05f, 1f, DEFAULT.heavy),
            hyst = hyst.within(0f, 0.15f, DEFAULT.hyst),
            popDamping = popDamping.within(0.4f, 1f, DEFAULT.popDamping),
            popStiffness = popStiffness.within(400f, 3000f, DEFAULT.popStiffness),
            wallMax = wallMax.within(0f, 0.15f, DEFAULT.wallMax),
            wallSlope = wallSlope.within(0f, 0.6f, DEFAULT.wallSlope),
            flingDp = flingDp.within(500f, 3000f, DEFAULT.flingDp),
            flingMin = flingMin.within(0.05f, 0.35f, DEFAULT.flingMin),
            backFlingDp = backFlingDp.within(200f, 2000f, DEFAULT.backFlingDp),
            commitK = commitK.within(300f, MAX_STIFFNESS, DEFAULT.commitK),
            cancelK = cancelK.within(300f, MAX_STIFFNESS, DEFAULT.cancelK),
            slopFactor = slopFactor.within(1f, 2f, DEFAULT.slopFactor),
            lockRatio = lockRatio.within(1f, 3f, DEFAULT.lockRatio),
            edgeDp = edgeDp.within(16f, 48f, DEFAULT.edgeDp),
            haptic = haptic,
        )
    }

    /**
     * The page's keys and values: the shape of `LMA_TABSWIPE`, for the parity fixture. Each number
     * is the float's shortest decimal (0.18, not 0.18000000715), so it equals the page's own.
     */
    fun toJson(): JSONObject =
        JSONObject().apply {
            numbers.forEach { (key, read) -> put(key, read(this@SwipeTune).toString().toDouble()) }
            put("haptic", haptic.wire)
        }

    companion object {
        /** The defaults. The page's `LMA_TABSWIPE` must equal these (SwipeTuneTest). */
        val DEFAULT = SwipeTune()

        /** No settle spring is ever stiffer than this. */
        const val MAX_STIFFNESS = 4000f

        /** A settle snaps to its target after this much animation time. */
        const val SETTLE_CAP_SECONDS = 0.45f

        /** Every numeric key the page may send, in `LMA_TABSWIPE`'s order. */
        val numbers: List<Pair<String, (SwipeTune) -> Float>> =
            listOf(
                "knee" to SwipeTune::knee,
                "breakAt" to SwipeTune::breakAt,
                "heavy" to SwipeTune::heavy,
                "hyst" to SwipeTune::hyst,
                "popDamping" to SwipeTune::popDamping,
                "popStiffness" to SwipeTune::popStiffness,
                "wallMax" to SwipeTune::wallMax,
                "wallSlope" to SwipeTune::wallSlope,
                "flingDp" to SwipeTune::flingDp,
                "flingMin" to SwipeTune::flingMin,
                "backFlingDp" to SwipeTune::backFlingDp,
                "commitK" to SwipeTune::commitK,
                "cancelK" to SwipeTune::cancelK,
                "slopFactor" to SwipeTune::slopFactor,
                "lockRatio" to SwipeTune::lockRatio,
                "edgeDp" to SwipeTune::edgeDp,
            )

        /**
         * The page's overrides (`swipe.tune`) over the defaults, clamped. A missing key, a value
         * that is not a finite number and an unknown haptic keep the default; null gives [DEFAULT].
         */
        fun parse(json: JSONObject?): SwipeTune {
            if (json == null) return DEFAULT
            fun read(key: String, default: Float): Float =
                (json.opt(key) as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: default
            return SwipeTune(
                    knee = read("knee", DEFAULT.knee),
                    breakAt = read("breakAt", DEFAULT.breakAt),
                    heavy = read("heavy", DEFAULT.heavy),
                    hyst = read("hyst", DEFAULT.hyst),
                    popDamping = read("popDamping", DEFAULT.popDamping),
                    popStiffness = read("popStiffness", DEFAULT.popStiffness),
                    wallMax = read("wallMax", DEFAULT.wallMax),
                    wallSlope = read("wallSlope", DEFAULT.wallSlope),
                    flingDp = read("flingDp", DEFAULT.flingDp),
                    flingMin = read("flingMin", DEFAULT.flingMin),
                    backFlingDp = read("backFlingDp", DEFAULT.backFlingDp),
                    commitK = read("commitK", DEFAULT.commitK),
                    cancelK = read("cancelK", DEFAULT.cancelK),
                    slopFactor = read("slopFactor", DEFAULT.slopFactor),
                    lockRatio = read("lockRatio", DEFAULT.lockRatio),
                    edgeDp = read("edgeDp", DEFAULT.edgeDp),
                    haptic = HapticStyle.of(json.opt("haptic") as? String) ?: DEFAULT.haptic,
                )
                .clamped()
        }
    }
}

private fun Float.within(low: Float, high: Float, default: Float): Float =
    if (isFinite()) coerceIn(low, high) else default
