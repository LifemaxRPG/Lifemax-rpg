package com.lifemax.rpg.feature.pet.body

/**
 * The numbers of petting, copied from the page (`PET_BRAIN` in 26-pet-brain.js and the hold in
 * 16-runtime.js) so the screen's reflex and the page's rules agree. Times are seconds.
 */
internal object TouchTuning {
    /** Saturation a touch adds when it lands, and per second it rests, before the cat's trait. */
    const val SAT_TAP = 0.12f
    const val SAT_HOLD = 0.09f

    /**
     * Ears go back at [SAT_WARN]; past [SAT_MAX] the cat leaves. Saturation tops out at [SAT_CAP].
     */
    const val SAT_WARN = 0.65f
    const val SAT_MAX = 0.85f
    const val SAT_CAP = 1.2f

    /** Saturation lost per second with no finger on the cat (0.15 a minute). */
    const val SAT_DECAY_PER_S = 0.15f / 60f

    /** Resting saturation grows slower for a trusting cat: `TRUST_BASE - TRUST_GAIN * trust`. */
    const val TRUST_BASE = 1.25f
    const val TRUST_GAIN = 0.5f

    /** A sleeping cat that lets itself be petted takes this share of the saturation. */
    const val ASLEEP_SHARE = 0.6f

    /** A touch shorter than this, that did not move, is a tap. */
    const val TAP_MAX_S = 0.45f

    /** A touch of at least this long is a pet the page counts (three a day, +2 bond). */
    const val PET_MIN_S = 1.1f

    /** A cat at trust level 1 gets up and leaves after this long under a finger. */
    const val SHY_LEAVES_S = 2.6f

    /** The purr starts after this long, and hearts rise from this long, a beat apart. */
    const val PURR_FROM_S = 0.45f
    const val HEARTS_FROM_S = 0.5f
    const val BEAT_S = 0.85f
    const val BEAT_SHY_S = 1.6f

    /** Purr strength by trust: devoted (4 and up), friend (3), the rest; asleep; warned. */
    const val PURR_DEVOTED = 0.95f
    const val PURR_FRIEND = 0.75f
    const val PURR_WARM = 0.5f
    const val PURR_ASLEEP = 0.3f
    const val PURR_WARNED = 0.25f
    const val PURR_WARNED_ASLEEP = 0.2f

    /** Going on this long after the ears went back counts as ignoring the cat. */
    const val IGNORED_AFTER_S = 1.5f

    /** Four touches in three seconds are spam; the page hears of it once in twenty seconds. */
    const val SPAM_TOUCHES = 4
    const val SPAM_WINDOW_S = 3f
    const val SPAM_QUIET_S = 20f

    /**
     * A projection does not reseed the saturation this soon after a touch (its answer is behind).
     */
    const val RESEED_QUIET_S = 3f

    /** From this trust level the hearts are big and notes rise too. */
    const val DEVOTED_LEVEL = 4

    /** From this trust level a sleeping cat stays asleep under a hand. */
    const val KEEPS_SLEEP_LEVEL = 3

    const val MS = 1000f
}

/**
 * What one finger resting on one cat is doing, as the page's rules say (`petPointer`,
 * `petUpdateHold` and `petBrainHold`): how long it has been there, how much petting the cat has had
 * ([saturation]), when its ears go back and when it has had enough. It is a plain state machine on
 * the `dt` it is given: no clock, no Android, no allocation while stepping. The screen reacts to
 * the flags [step] answers; the page decides what the touch counts for from the [Outcome].
 *
 * One model per cat: saturation belongs to the cat and wears off with time. The page owns the truth
 * ([seed] takes its number); between its projections this keeps the reflex honest.
 */
class TouchModel {
    /** Where the finger is in its life. */
    enum class Phase {
        /** No finger on this cat. */
        IDLE,

        /** A finger is on the cat. */
        HELD,

        /** The finger is still down but the cat has gone (it fled, or had enough). */
        LEFT,
    }

    /** What a touch came to, for the page. */
    class Outcome
    internal constructor(
        /** The cat's trust level, 0 to 5. */
        val level: Int,
        /** How long the finger stayed. */
        val heldMs: Long,
        val moved: Boolean,
        /** The cat was asleep and stayed asleep. */
        val keepSleep: Boolean,
        /** The cat left because it had had enough. */
        val over: Boolean,
        /** The finger stayed on well after the ears went back. */
        val ignored: Boolean,
        /** This was the touch that made four in three seconds. */
        val spam: Boolean,
        /** The saturation when the finger lifted. */
        val saturation: Float,
        /** A wary cat (trust 1) got up and left. */
        val leftShy: Boolean,
    ) {
        /** A pet the page counts: held long enough, and the cat did not leave first. */
        val counts: Boolean
            get() = heldMs >= (TouchTuning.PET_MIN_S * TouchTuning.MS).toLong() && !over && !leftShy

        /** A touch too short to be anything but a tap. */
        val tap: Boolean
            get() = heldMs < (TouchTuning.TAP_MAX_S * TouchTuning.MS).toLong() && !moved
    }

    var phase = Phase.IDLE
        private set

    /** Seconds the finger has been on the cat. */
    var held = 0f
        private set

    /** How much petting the cat has had, 0 to [TouchTuning.SAT_CAP]. */
    var saturation = 0f
        private set

    /** The purr the cat should be making now, 0 (none) to 1. */
    var purr = 0f
        private set

    /** The cat's ears are back: it is asking you to stop. */
    val warned: Boolean
        get() = saturation >= TouchTuning.SAT_WARN

    /** The finger moved while it was on the cat. */
    var moved = false
        private set

    /** The cat is asleep and trusts you enough to stay asleep. */
    var keepSleep = false
        private set

    /** The cat's trust level the touch began at. */
    var level = 0
        private set

    private var weak = false
    private var satMul = 1f
    private var trust = 0f
    private var clock = 0f
    private var sinceEnd = Float.MAX_VALUE
    private var warnedAt = NEVER
    private var beatIndex = 0
    private var over = false
    private var ignored = false
    private var shy = false
    private var spam = false
    private var spamAt = NEVER
    private val touches = FloatArray(TouchTuning.SPAM_TOUCHES)
    private var touchCount = 0

    /** How this cat takes touch: its trust [level] (0 to 5), hunger, trait and trust factors. */
    fun profile(level: Int, weak: Boolean, satMul: Float, trust: Float) {
        if (phase == Phase.IDLE) this.level = level.coerceIn(0, MAX_LEVEL)
        this.weak = weak
        this.satMul = satMul
        this.trust = trust.coerceIn(0f, 1f)
    }

    /** Take the page's saturation, unless a touch has only just ended (its number is behind). */
    fun seed(saturation: Float) {
        if (phase != Phase.IDLE || sinceEnd < TouchTuning.RESEED_QUIET_S) return
        this.saturation = saturation.coerceIn(0f, TouchTuning.SAT_CAP)
    }

    /**
     * A finger lands on the cat. [asleep] says it is sleeping. The answer holds [STARTLE] for a
     * stranger (trust 0), which flees at once, [WOKE] when the touch wakes the cat, and [SPAM] when
     * this is the fourth touch in three seconds.
     */
    fun begin(asleep: Boolean): Int {
        var flags = 0
        held = 0f
        moved = false
        over = false
        ignored = false
        shy = false
        purr = 0f
        beatIndex = 0
        warnedAt = NEVER
        keepSleep = asleep && level >= TouchTuning.KEEPS_SLEEP_LEVEL
        spam = noteTouch()
        if (spam) flags = flags or SPAM
        if (level == 0) {
            phase = Phase.LEFT
            return flags or STARTLE
        }
        phase = Phase.HELD
        saturation = minOf(TouchTuning.SAT_CAP, saturation + TouchTuning.SAT_TAP * satMul)
        if (asleep && !keepSleep) flags = flags or WOKE
        return flags
    }

    /** The finger moved while it was down. */
    fun move() {
        if (phase == Phase.HELD) moved = true
    }

    /** Advance by [dt] seconds; the answer is a set of the flags below, 0 when nothing happened. */
    fun step(dt: Float): Int {
        clock += dt
        if (phase != Phase.HELD) {
            sinceEnd += dt
            saturation = maxOf(0f, saturation - TouchTuning.SAT_DECAY_PER_S * dt)
            return 0
        }
        held += dt
        val share = if (keepSleep) TouchTuning.ASLEEP_SHARE else 1f
        val calm = TouchTuning.TRUST_BASE - TouchTuning.TRUST_GAIN * trust
        saturation =
            minOf(
                TouchTuning.SAT_CAP,
                saturation + TouchTuning.SAT_HOLD * dt * satMul * calm * share,
            )
        if (level == 1 && held > TouchTuning.SHY_LEAVES_S) return leave(shy = true)
        var flags = 0
        if (saturation >= TouchTuning.SAT_WARN && warnedAt == NEVER) {
            warnedAt = clock
            flags = flags or WARNED
        }
        if (saturation > TouchTuning.SAT_MAX && !keepSleep) return flags or leave(shy = false)
        flags = flags or beats()
        val wanted = purrWanted()
        if (wanted != purr) {
            purr = wanted
            flags = flags or PURR_CHANGED
        }
        return flags
    }

    /** The finger lifted. What the touch came to, for the page. */
    fun end(): Outcome {
        val outcome =
            Outcome(
                level,
                Math.round(held * TouchTuning.MS).toLong(),
                moved,
                keepSleep,
                over,
                ignored,
                spam,
                saturation,
                shy,
            )
        phase = Phase.IDLE
        purr = 0f
        sinceEnd = 0f
        return outcome
    }

    private fun leave(shy: Boolean): Int {
        this.shy = shy
        over = !shy
        ignored = !shy && warnedAt != NEVER && clock - warnedAt > TouchTuning.IGNORED_AFTER_S
        phase = Phase.LEFT
        val wasPurring = purr > 0f
        purr = 0f
        return (if (shy) LEFT_SHY else LEFT_OVER) or (if (wasPurring) PURR_CHANGED else 0)
    }

    private fun beats(): Int {
        val beat = if (level == 1) TouchTuning.BEAT_SHY_S else TouchTuning.BEAT_S
        val index = (held / beat).toInt()
        if (index == beatIndex) return 0
        beatIndex = index
        // ears back: no hearts, but the count goes on so a calmer cat does not burst
        return if (warned || held <= TouchTuning.HEARTS_FROM_S) 0 else BEAT
    }

    private fun purrWanted(): Float {
        if (warned)
            return if (keepSleep) TouchTuning.PURR_WARNED_ASLEEP else TouchTuning.PURR_WARNED
        if (level < 2 || weak || held <= TouchTuning.PURR_FROM_S) return 0f
        return when {
            keepSleep -> TouchTuning.PURR_ASLEEP
            level >= TouchTuning.DEVOTED_LEVEL -> TouchTuning.PURR_DEVOTED
            level == 3 -> TouchTuning.PURR_FRIEND
            else -> TouchTuning.PURR_WARM
        }
    }

    /**
     * Remember a touch; true when it is the fourth in three seconds and the page is due to hear.
     */
    private fun noteTouch(): Boolean {
        val n = TouchTuning.SPAM_TOUCHES
        if (touchCount == n) {
            for (i in 1 until n) touches[i - 1] = touches[i]
            touchCount--
        }
        touches[touchCount++] = clock
        var recent = 0
        for (i in 0 until touchCount) if (clock - touches[i] <= TouchTuning.SPAM_WINDOW_S) recent++
        if (recent < n || clock - spamAt <= TouchTuning.SPAM_QUIET_S) return false
        spamAt = clock
        return true
    }

    companion object {
        /** [begin]: a stranger startles and runs. */
        const val STARTLE = 1

        /** [begin]: the touch wakes a sleeping cat. */
        const val WOKE = 2

        /** [begin]: spam. */
        const val SPAM = 4

        /** [step]: a heart is due. */
        const val BEAT = 8

        /** [step]: the ears went back. */
        const val WARNED = 16

        /** [step]: [purr] changed. */
        const val PURR_CHANGED = 32

        /** [step]: the cat had enough and left. */
        const val LEFT_OVER = 64

        /** [step]: a shy cat got up and left. */
        const val LEFT_SHY = 128

        const val MAX_LEVEL = 5
        private const val NEVER = -1e9f
    }
}
