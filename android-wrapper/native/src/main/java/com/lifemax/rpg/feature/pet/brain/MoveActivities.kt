package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.Rng
import com.lifemax.rpg.feature.pet.body.TailState
import com.lifemax.rpg.feature.pet.body.Tuning
import com.lifemax.rpg.feature.pet.body.World

/** Wander (plan 1.8): to another room's floor, sniffing about and sitting a while. */
internal object Wander : Activity {
    override val id = "wander"
    override val lively = true
    override val mainDrive = Drive.CURIOSITY
    override val exertion = 1.3
    override val arousal = 0.15
    override val cooldownMin = BrainTuning.Cooldowns.WANDER_MIN
    override val placeFlags = Spots.ROOM_FLOOR

    private const val ENERGY = 0.25
    private const val BASE = 0.45
    private const val CURIOSITY = 0.55
    private const val PLAY = 0.3
    private const val TERRITORY = 0.15
    private const val CURIOSITY_TAU = 5.0 * BrainTuning.Time.MINUTE
    private const val PLAY_TAU = 14.0 * BrainTuning.Time.MINUTE
    private val SIT_LO = BrainTuning.Roam.WANDER_SIT_LO_S
    private val SIT_HI = BrainTuning.Roam.WANDER_SIT_HI_S
    private const val STROLL_LO = 20f
    private const val STROLL_HI = 50f

    override fun satisfies(d: Drive) =
        when (d) {
            Drive.CURIOSITY -> CURIOSITY_TAU
            Drive.PLAY -> PLAY_TAU
            else -> 0.0
        }

    override fun eligible(m: MindView) = m.drives[Drive.ENERGY] > ENERGY && !m.persona.atBowlOnly

    override fun appeal(m: MindView): Double {
        val d = m.drives
        val want =
            CURIOSITY * d[Drive.CURIOSITY] + PLAY * d[Drive.PLAY] + TERRITORY * d[Drive.TERRITORY]
        return BASE * want * m.persona.exploreWeight * Roaming.boost(m)
    }

    override fun suits(m: MindView, spot: Spot): Boolean {
        val mate = m.mateX
        if (mate != null && Math.abs(spot.x - mate) < BrainTuning.Roam.MATE_GAP_PX) return false
        return spot.room != World.roomOf(m.x)
    }

    override fun reason(m: MindView) = "curious"

    override fun duration(m: MindView, rng: Rng): Double {
        val d = BrainTuning.Durations
        return Beats.minutes(m, rng, d.WANDER_LO_MIN, d.WANDER_HI_MIN)
    }

    override fun script(m: MindView, at: Spot?, seconds: Double, rng: Rng, out: Script) {
        var left = seconds
        var x = at?.x ?: m.x
        while (left > 0.0) {
            out.act(Rows.SNIFF, Rows.once(Rows.SNIFF))
            val sit = minOf(left, Beats.seconds(rng, SIT_LO, SIT_HI))
            out.act(Rows.SIT, sit)
            left -= sit + Rows.once(Rows.SNIFF)
            if (left <= 0.0) break
            x = World.clampHouse(x + rng.sign() * rng.range(STROLL_LO, STROLL_HI))
            out.stroll(x, null)
            left -= (STROLL_LO + STROLL_HI) / 2.0 / Tuning.Walk.WALK_SPEED
        }
    }
}

/** Window watch (plan 1.8): a sill, sitting and loafing with a twitching tip, the odd chatter. */
internal object WindowWatch : Activity {
    override val id = "window watch"
    override val mainDrive = Drive.CURIOSITY
    override val resumable = true
    override val arousal = 0.3
    override val tail = TailState.TIP_TWITCH
    override val cooldownMin = BrainTuning.Cooldowns.WINDOW_MIN
    override val placeFlags = Spots.WINDOW

    private const val ELIGIBLE = 0.3
    private const val BASE = 0.9
    private const val CURVE = 1.2
    private const val TAU = 3.5 * BrainTuning.Time.MINUTE
    private const val SIT_LO = 6.0
    private const val SIT_HI = 14.0
    private const val LOOK_LO = 3.0
    private const val LOOK_HI = 6.0
    private const val LOAF_LO = 15.0
    private const val LOAF_HI = 35.0
    private const val CHATTER = 0.15f

    override fun satisfies(d: Drive) = if (d == Drive.CURIOSITY) TAU else 0.0

    override fun eligible(m: MindView) = m.drives[Drive.CURIOSITY] > ELIGIBLE && !m.persona.weak

    override fun appeal(m: MindView) =
        BASE *
            Curves.rise(m.drives[Drive.CURIOSITY], CURVE) *
            Math.sqrt(m.persona.exploreWeight) *
            m.schedule.daylight(m.hour) *
            Roaming.boost(m)

    override fun placeFactor(m: MindView, spot: Spot) = Beats.taste(m, spot) * Beats.cosy(m, spot)

    override fun reason(m: MindView) =
        if (m.schedule.peak(m.hour) > HALF) "birds out" else "curious"

    override fun duration(m: MindView, rng: Rng): Double {
        val d = BrainTuning.Durations
        return Beats.minutes(m, rng, d.WINDOW_LO_MIN, d.WINDOW_HI_MIN)
    }

    override fun script(m: MindView, at: Spot?, seconds: Double, rng: Rng, out: Script) {
        var left = seconds
        while (left > 0.0) {
            val sit = minOf(left, Beats.seconds(rng, SIT_LO, SIT_HI))
            out.act(Rows.SIT, sit)
            val look = Beats.seconds(rng, LOOK_LO, LOOK_HI)
            out.act(Rows.LOOKUP, look)
            left -= sit + look
            if (rng.chance(CHATTER)) {
                out.act(Rows.MEOW, Rows.once(Rows.MEOW))
                left -= Rows.once(Rows.MEOW)
            }
            if (left <= 0.0) break
            val loaf = minOf(left, Beats.seconds(rng, LOAF_LO, LOAF_HI))
            out.act(Rows.LOAF, loaf)
            left -= loaf
        }
    }

    private const val HALF = 0.5
}

/** Parkour visit (plan 1.8): up to the fridge, the bookcase, the tree top or the side table. */
internal object ParkourVisit : Activity {
    override val id = "parkour visit"
    override val mainDrive = Drive.TERRITORY
    override val exertion = 1.6
    override val arousal = 0.45
    override val cooldownMin = BrainTuning.Cooldowns.PARKOUR_MIN
    override val placeFlags = Spots.PARKOUR

    private const val ENERGY = 0.5
    private const val BASE = 0.35
    private const val CURIOSITY = 0.7
    private const val PLAY = 0.6
    private const val TERRITORY_TAU = 3.0 * BrainTuning.Time.MINUTE
    private const val CURIOSITY_TAU = 8.0 * BrainTuning.Time.MINUTE
    private const val PLAY_TAU = 15.0 * BrainTuning.Time.MINUTE
    private const val SURVEY_LO = 5.0
    private const val SURVEY_HI = 8.0
    private const val LOOK_LO = 3.0
    private const val LOOK_HI = 5.0
    private val SIT_LO = BrainTuning.Roam.PERCH_SIT_LO_S
    private val SIT_HI = BrainTuning.Roam.PERCH_SIT_HI_S

    override fun satisfies(d: Drive) =
        when (d) {
            Drive.TERRITORY -> TERRITORY_TAU
            Drive.CURIOSITY -> CURIOSITY_TAU
            Drive.PLAY -> PLAY_TAU
            else -> 0.0
        }

    override fun eligible(m: MindView) = m.drives[Drive.ENERGY] > ENERGY && !m.persona.weak

    override fun appeal(m: MindView): Double {
        val d = m.drives
        val want = maxOf(d[Drive.TERRITORY], CURIOSITY * d[Drive.CURIOSITY], PLAY * d[Drive.PLAY])
        return BASE * want * m.persona.parkourWeight * Roaming.boost(m)
    }

    override fun placeFactor(m: MindView, spot: Spot) = Beats.taste(m, spot)

    override fun reason(m: MindView) =
        if (m.drives[Drive.TERRITORY] >= m.drives[Drive.CURIOSITY]) "surveys its house"
        else "curious"

    override fun duration(m: MindView, rng: Rng): Double {
        val d = BrainTuning.Durations
        return Beats.minutes(m, rng, d.PARKOUR_LO_MIN, d.PARKOUR_HI_MIN)
    }

    override fun script(m: MindView, at: Spot?, seconds: Double, rng: Rng, out: Script) {
        val survey = Beats.seconds(rng, SURVEY_LO, SURVEY_HI)
        val look = Beats.seconds(rng, LOOK_LO, LOOK_HI)
        out.act(Rows.SIT, survey)
        out.act(Rows.LOOKUP, look)
        var left = seconds - survey - look
        val sit = minOf(maxOf(0.0, left), Beats.seconds(rng, SIT_LO, SIT_HI))
        if (sit > 0.0) out.act(Rows.SIT, sit)
        left -= sit
        if (left > 0.0) Beats.loafFor(out, left, rng)
    }
}

/** Scratching (plan 1.8): the tree post or the sofa arm, marking the house. */
internal object Scratch : Activity {
    override val id = "scratch"
    override val lively = true
    override val mainDrive = Drive.TERRITORY
    override val exertion = 1.2
    override val arousal = 0.35
    override val cooldownMin = BrainTuning.Cooldowns.SCRATCH_MIN
    override val placeFlags = Spots.SCRATCH

    private const val ELIGIBLE = 0.55
    private const val BASE = 0.85
    private const val CURVE = 2.0
    private const val EASED = 0.6

    override fun eligible(m: MindView) = m.drives[Drive.TERRITORY] > ELIGIBLE && !m.persona.weak

    override fun appeal(m: MindView) =
        BASE * Curves.rise(m.drives[Drive.TERRITORY], CURVE) * Math.sqrt(m.persona.territoryGain)

    override fun reason(m: MindView) = "marks its house"

    override fun duration(m: MindView, rng: Rng) =
        Beats.seconds(rng, BrainTuning.Durations.SCRATCH_LO_S, BrainTuning.Durations.SCRATCH_HI_S)

    override fun script(m: MindView, at: Spot?, seconds: Double, rng: Rng, out: Script) {
        out.face(at?.face ?: 0)
        out.act(Rows.SCRATCH_IN, Rows.once(Rows.SCRATCH_IN))
        out.act(Rows.SCRATCH, seconds)
        out.act(Rows.SCRATCH_OUT, Rows.once(Rows.SCRATCH_OUT))
    }

    override fun after(drives: Drives) = drives.nudge(Drive.TERRITORY, 0.0, EASED)
}
