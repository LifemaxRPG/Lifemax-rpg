package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.Rng

/** Shared pieces of the catalogue: durations, place taste and filling a stretch with beats. */
internal object Beats {
    /** [lo]..[hi] minutes as seconds, stretched by the cat's [Persona.durationMul]. */
    fun minutes(m: MindView, rng: Rng, lo: Double, hi: Double): Double =
        rng.range(lo.toFloat(), hi.toFloat()) * BrainTuning.Time.MINUTE * m.persona.durationMul

    /** [lo]..[hi] seconds. */
    fun seconds(rng: Rng, lo: Double, hi: Double): Double =
        rng.range(lo.toFloat(), hi.toFloat()).toDouble()

    /**
     * How a cat likes [spot] as a place to be: its learned liking, a hidden corner for a skittish
     * cat, a high perch for a dominant one (plan 1.4).
     */
    fun taste(m: MindView, spot: Spot): Double {
        val p = BrainTuning.Places
        var f = p.LIKE_FLOOR + p.LIKE_GAIN * m.book.like(m.slot, spot)
        f *=
            if (spot.has(Spots.HIDDEN)) m.persona.hiddenLike
            else Curves.lerp(1.0, p.NOT_HIDDEN_SKITTISH, m.persona.skittish)
        if (spot.has(Spots.HIGH)) f *= m.persona.highLike
        if (m.valence < 0.0 && spot.has(Spots.HIDDEN)) f *= 1.0 - m.valence
        return f
    }

    /** A soft spot, a sunny one at sunny hours. */
    fun cosy(m: MindView, spot: Spot): Double {
        var f = if (spot.has(Spots.SOFT)) BrainTuning.Places.SOFT_GAIN else 1.0
        if (spot.has(Spots.SUNNY)) f *= m.schedule.sunny(m.hour)
        return f
    }

    /**
     * Fills [seconds] of [out] with a varied rest (see [BrainTuning.Roam]): a chunk of loafing,
     * then a sit, a wash, a look about or a slow blink, so no posture is held long and the cat is
     * seen to be awake. A tail of less than a few seconds is left to the last beat.
     */
    fun loafFor(out: Script, seconds: Double, rng: Rng) {
        val r = BrainTuning.Roam
        var left = seconds
        var last = -1
        while (left > 0.0) {
            var pick =
                rng.nextFloat().toDouble() *
                    (r.REST_SHARE_LOAF + r.REST_SHARE_SIT + r.REST_SHARE_WASH + r.REST_SHARE_LOOK)
            var kind = 0
            val shares =
                doubleArrayOf(
                    r.REST_SHARE_LOAF,
                    r.REST_SHARE_SIT,
                    r.REST_SHARE_WASH,
                    r.REST_SHARE_LOOK,
                )
            while (kind < shares.size - 1 && pick >= shares[kind]) pick -= shares[kind++]
            // never the same beat twice running: it would be one long posture
            if (kind == last) kind = (kind + 1) % shares.size
            last = kind
            val chunk =
                when (kind) {
                    0 -> minOf(left, seconds(rng, r.REST_CHUNK_LO_S, r.REST_CHUNK_HI_S))
                    1 -> minOf(left, seconds(rng, r.REST_SIT_LO_S, r.REST_SIT_HI_S))
                    2 -> minOf(left, seconds(rng, r.REST_WASH_LO_S, r.REST_WASH_HI_S))
                    else -> minOf(left, seconds(rng, r.REST_LOOK_LO_S, r.REST_LOOK_HI_S))
                }
            when (kind) {
                0 -> out.act(Rows.LOAF, chunk)
                1 -> out.act(Rows.SIT, chunk)
                2 -> out.act(Rows.GROOM, chunk)
                else -> {
                    out.act(Rows.LOOKUP, chunk)
                    if (rng.chance(BLINK) && left - chunk > Rows.once(Rows.SLOWBLINK))
                        out.act(Rows.SLOWBLINK, Rows.once(Rows.SLOWBLINK))
                }
            }
            left -= chunk
        }
    }

    const val SIT_LO = 4.0
    const val SIT_HI = 8.0
    const val BLINK = 0.35f
}

/** Sleep (plan 1.8): a liked sleep spot, knead, turn, loaf, sleep; naps by day, bouts by night. */
internal object Sleep : Activity {
    override val id = "sleep"
    override val mainDrive = Drive.ENERGY
    override val asleep = true
    override val resumable = true
    override val arousal = -0.8
    override val cooldownMin = BrainTuning.Cooldowns.SLEEP_MIN
    override val next = WakingRoutine.id
    override val placeFlags = Spots.SLEEP
    override val maxMinutes = BrainTuning.Limits.SLEEP_MAX_MIN

    /** A sleeping cat on any spot slowly stops wanting comfort. */
    override fun satisfies(d: Drive) = if (d == Drive.COMFORT) COMFORT_TAU else 0.0

    private const val COMFORT_TAU = 12.0 * BrainTuning.Time.MINUTE
    private const val BASE = 1.0
    private const val COMFORT_SHARE = 0.12
    private const val ENERGY_CURVE = 1.6
    private const val NIGHT_BONUS = 0.5
    private const val KNEAD_TRUST = 0.5
    private const val SETTLE_LO = 3.0
    private const val SETTLE_HI = 5.0
    private const val NIGHT_JITTER_H = 0.3f
    private const val GROOM_AFTER = 0.25

    /**
     * Sleep only at night, when really tired, in a nap window when sleepy, or dozing after a meal
     * (roaming): a rested cat by day does not lie down to sleep, it rests a little and gets up.
     */
    override fun eligible(m: MindView): Boolean {
        val r = BrainTuning.Roam
        val energy = m.drives[Drive.ENERGY]
        if (m.schedule.inBed(m.hour) || energy < r.TIRED) return true
        val sleepy =
            if (m.persona.kitten) r.SLEEPY_KITTEN
            else r.SLEEPY_ADULT + BrainTuning.Persona.LAZY_SLEEPY * m.persona.lazy
        if (energy < sleepy && m.schedule.inNap(m.hour)) return true
        return m.sinceMealS < r.MEAL_DOZE_MIN * BrainTuning.Time.MINUTE &&
            energy < r.MEAL_DOZE_ENERGY
    }

    override fun appeal(m: MindView): Double {
        val night = if (m.schedule.inBed(m.hour)) NIGHT_BONUS else 0.0
        val need =
            Curves.fall(m.drives[Drive.ENERGY], ENERGY_CURVE) +
                COMFORT_SHARE * m.drives[Drive.COMFORT]
        val weak = if (m.persona.weak) BrainTuning.Persona.WEAK_SLEEP else 1.0
        return BASE * (need + night) * m.persona.restWeight * weak * m.schedule.sleepFactor(m.hour)
    }

    override fun placeFactor(m: MindView, spot: Spot) = Beats.taste(m, spot) * Beats.cosy(m, spot)

    override fun reason(m: MindView) =
        when {
            m.schedule.inBed(m.hour) -> "bed hours"
            m.schedule.inNap(m.hour) -> "nap window"
            else -> "tired"
        }

    override fun duration(m: MindView, rng: Rng): Double {
        val s = BrainTuning.Schedule
        val p = BrainTuning.Persona
        if (m.schedule.inBed(m.hour)) {
            val toWake = m.schedule.hoursToWake(m.hour) + rng.range(0f, NIGHT_JITTER_H)
            val bout = rng.range(s.NIGHT_BOUT_LO_H.toFloat(), s.NIGHT_BOUT_HI_H.toFloat())
            // a kitten sleeps the night in shorter bouts, twice its longest nap
            val kittenBout = 2 * p.KITTEN_NAP_MAX_MIN / BrainTuning.Time.MINUTE
            val most = if (m.persona.kitten) kittenBout else bout.toDouble()
            return minOf(toWake, most) * BrainTuning.Time.HOUR
        }
        if (m.persona.kitten)
            return Beats.minutes(m, rng, p.KITTEN_NAP_MIN_MIN, p.KITTEN_NAP_MAX_MIN)
        val d = BrainTuning.Durations
        return Beats.minutes(m, rng, d.NAP_LO_MIN, d.NAP_HI_MIN) * Math.sqrt(m.persona.restWeight)
    }

    override fun script(m: MindView, at: Spot?, seconds: Double, rng: Rng, out: Script) {
        var left = seconds
        if (at != null && at.has(Spots.SOFT) && m.trust >= KNEAD_TRUST) {
            val k =
                Beats.seconds(
                    rng,
                    BrainTuning.Durations.KNEAD_LO_S,
                    BrainTuning.Durations.KNEAD_HI_S,
                )
            out.act(Rows.KNEAD, k)
            left -= k
        }
        out.act(Rows.TURN, Rows.once(Rows.TURN))
        val settle = Beats.seconds(rng, SETTLE_LO, SETTLE_HI)
        out.act(Rows.LOAF, settle)
        out.act(Rows.SLEEP, maxOf(1.0, left - settle - Rows.once(Rows.TURN)))
    }

    override fun after(drives: Drives) = drives.nudge(Drive.GROOM, 1.0, GROOM_AFTER)
}

/** Waking up (plan 1.8): yawn, stretch, shake, and half the time a quick wash. */
internal object WakingRoutine : Activity {
    override val id = "waking routine"
    override val mainDrive = Drive.ENERGY
    override val reflexOnly = true
    override val arousal = 0.1
    override val maxMinutes = BrainTuning.Limits.SHORT_MAX_MIN

    private const val GROOM_CHANCE = 0.5f
    private const val GROOM_LO = 2.0
    private const val GROOM_HI = 5.0
    private const val GROOM_EASED = 0.2

    override fun eligible(m: MindView) = true

    override fun appeal(m: MindView) = 0.0

    override fun reason(m: MindView) = "woke"

    override fun duration(m: MindView, rng: Rng) = 0.0

    override fun script(m: MindView, at: Spot?, seconds: Double, rng: Rng, out: Script) {
        out.act(Rows.YAWN, Rows.once(Rows.YAWN))
        out.act(Rows.STRETCH, Rows.once(Rows.STRETCH))
        out.act(Rows.SHAKE, Rows.once(Rows.SHAKE))
        if (rng.chance(GROOM_CHANCE)) out.act(Rows.GROOM, Beats.seconds(rng, GROOM_LO, GROOM_HI))
        else out.act(Rows.SIT, Beats.seconds(rng, GROOM_LO, GROOM_HI))
    }

    override fun after(drives: Drives) = drives.nudge(Drive.GROOM, 0.0, GROOM_EASED)
}

/** Loaf / rest (plan 1.8): a liked rest spot, loafing with sits and slow blinks. */
internal object Loaf : Activity {
    override val id = "loaf"
    override val mainDrive = Drive.COMFORT
    override val resumable = true
    override val arousal = -0.4
    override val exertion = 0.6
    override val cooldownMin = BrainTuning.Cooldowns.LOAF_MIN
    override val placeFlags = Spots.REST

    private const val BASE = 0.5
    private const val COMFORT_FLOOR = 0.2
    private const val TIRED_FLOOR = 0.55
    private const val SIT_LO = 3.0
    private const val SIT_HI = 6.0
    private const val COMFORT_TAU = 7.0 * BrainTuning.Time.MINUTE

    override fun satisfies(d: Drive) = if (d == Drive.COMFORT) COMFORT_TAU else 0.0

    override fun eligible(m: MindView) = true

    override fun appeal(m: MindView): Double {
        val comfort = COMFORT_FLOOR + (1 - COMFORT_FLOOR) * m.drives[Drive.COMFORT]
        val tired = TIRED_FLOOR + (1 - TIRED_FLOOR) * Curves.fall(m.drives[Drive.ENERGY])
        val weak = if (m.persona.weak) BrainTuning.Persona.WEAK_LOAF else 1.0
        return BASE *
            comfort *
            tired *
            m.persona.restWeight *
            weak *
            (1.0 - BrainTuning.Roam.REST_DAMP * Roaming.restless(m))
    }

    override fun placeFactor(m: MindView, spot: Spot) = Beats.taste(m, spot) * Beats.cosy(m, spot)

    override fun reason(m: MindView) =
        if (m.drives[Drive.COMFORT] > HALF) "wants comfort" else "rests"

    override fun duration(m: MindView, rng: Rng): Double {
        return Roaming.restSeconds(m, rng)
    }

    override fun script(m: MindView, at: Spot?, seconds: Double, rng: Rng, out: Script) {
        val sit = Beats.seconds(rng, SIT_LO, SIT_HI)
        out.act(Rows.SIT, sit)
        Beats.loafFor(out, seconds - sit, rng)
    }

    private const val HALF = 0.5
}

/** Grooming (plan 1.8): washes in place, in bouts. */
internal object Groom : Activity {
    override val id = "groom"
    override val mainDrive = Drive.GROOM
    override val arousal = -0.2
    override val cooldownMin = BrainTuning.Cooldowns.GROOM_MIN

    private const val ELIGIBLE = 0.45
    private const val BASE = 0.95
    private const val CURVE = 2.0
    private const val TAU = 45.0
    private const val BOUT_LO = 18.0
    private const val BOUT_HI = 40.0
    private const val PAUSE_LO = 2.0
    private const val PAUSE_HI = 4.0

    override fun satisfies(d: Drive) = if (d == Drive.GROOM) TAU else 0.0

    override fun eligible(m: MindView) = m.drives[Drive.GROOM] > ELIGIBLE

    override fun appeal(m: MindView) = BASE * Curves.rise(m.drives[Drive.GROOM], CURVE)

    override fun reason(m: MindView) = "wants a wash"

    override fun duration(m: MindView, rng: Rng): Double {
        val d = BrainTuning.Durations
        return Beats.minutes(m, rng, d.GROOM_LO_MIN, d.GROOM_HI_MIN)
    }

    override fun script(m: MindView, at: Spot?, seconds: Double, rng: Rng, out: Script) {
        var left = seconds
        while (left > 0.0) {
            val bout = minOf(left, Beats.seconds(rng, BOUT_LO, BOUT_HI))
            out.act(Rows.GROOM, bout)
            left -= bout
            if (left > PAUSE_HI) {
                val p = Beats.seconds(rng, PAUSE_LO, PAUSE_HI)
                out.act(Rows.SIT, p)
                left -= p
            }
        }
    }
}

/** Kneading (plan 1.8): on a soft spot, when content and trusting. */
internal object Knead : Activity {
    override val id = "knead"
    override val mainDrive = Drive.COMFORT
    override val arousal = -0.3
    override val cooldownMin = BrainTuning.Cooldowns.KNEAD_MIN

    private const val TRUST = 0.5
    private const val COMFORT = 0.3
    private const val BASE = 0.22
    private const val EASED = 0.3

    override fun eligible(m: MindView): Boolean {
        val here = m.here ?: return false
        return here.has(Spots.SOFT) && m.trust >= TRUST && m.drives[Drive.COMFORT] > COMFORT
    }

    override fun appeal(m: MindView) = BASE * m.drives[Drive.COMFORT]

    override fun reason(m: MindView) = "content on a soft spot"

    override fun duration(m: MindView, rng: Rng) =
        Beats.seconds(rng, BrainTuning.Durations.KNEAD_LO_S, BrainTuning.Durations.KNEAD_HI_S)

    override fun script(m: MindView, at: Spot?, seconds: Double, rng: Rng, out: Script) =
        out.act(Rows.KNEAD, seconds)

    override fun after(drives: Drives) = drives.nudge(Drive.COMFORT, 0.0, EASED)
}
