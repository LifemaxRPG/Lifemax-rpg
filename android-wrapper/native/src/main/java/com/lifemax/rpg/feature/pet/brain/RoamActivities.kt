package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.Occluders
import com.lifemax.rpg.feature.pet.body.PlanStep
import com.lifemax.rpg.feature.pet.body.Rng
import com.lifemax.rpg.feature.pet.body.World

/**
 * What makes a cat get up (roaming, [BrainTuning.Roam]): restlessness grows while the cat is still,
 * and with its energy and the time of day it is the urge that scales every lively activity up and
 * the rest down. Pure functions of the [MindView]; nothing here allocates.
 */
internal object Roaming {
    private val r = BrainTuning.Roam

    /** 0..1: how much the cat itches to move, from how long it has been still. */
    fun restless(m: MindView): Double {
        val p = m.persona
        var tau = r.RESTLESS_TAU_S * (1.0 + r.LAZY_TAU_GAIN * (p.lazy - HALF))
        if (p.kitten) tau *= r.KITTEN_TAU
        val grown = 1.0 - StrictMath.exp(-m.stillS / tau)
        return r.RESTLESS_FLOOR + (1.0 - r.RESTLESS_FLOOR) * grown
    }

    /** 0..1+: the urge to get up: restlessness, energy and the hour together. */
    fun urge(m: MindView): Double {
        val energy = ((m.drives[Drive.ENERGY] - r.ENERGY_LO) / r.ENERGY_SPAN).coerceIn(0.0, 1.0)
        val s = m.schedule
        val hour =
            when {
                s.inBed(m.hour) -> r.BED_URGE
                s.inNap(m.hour) -> r.NAP_WINDOW_URGE
                else -> minOf(s.activeFactor(m.hour), r.PEAK_URGE_MAX)
            }
        return restless(m) * energy * hour
    }

    /** The factor the older lively activities (wander, window, perches) are scaled by. */
    fun boost(m: MindView): Double = BOOST_MIN + BOOST_GAIN * urge(m)

    /** How long a rest lasts, seconds: short for a kitten, longer for a calm or tired cat. */
    fun restSeconds(m: MindView, rng: Rng): Double {
        var s = rng.range(r.REST_LO_S.toFloat(), r.REST_HI_S.toFloat()).toDouble()
        s *= Math.sqrt(m.persona.restWeight)
        if (m.persona.kitten) s *= r.KITTEN_REST
        val tired = (r.REST_TIRED_FROM - m.drives[Drive.ENERGY]).coerceAtLeast(0.0)
        return s * (1.0 + r.REST_TIRED_GAIN * tired / r.REST_TIRED_FROM)
    }

    private const val HALF = 0.5
    private const val BOOST_MIN = 0.5
    private const val BOOST_GAIN = 1.5
}

/**
 * Explore (roaming): a look at a place: any place of the table with [Spots.VISIT] (every perch, the
 * window sills, the bowls, the rugs, the box, the sofa), a sniff, a sit, a look about, ten to
 * thirty-eight seconds, then something else. The places a cat has been to lately are less
 * interesting ([SpotBook.fresh]), so it goes round the whole house. A new place in the table is
 * explored with no change here.
 */
internal object Explore : Activity {
    override val id = "explore"
    override val mainDrive = Drive.CURIOSITY
    override val lively = true
    override val exertion = 1.1
    override val arousal = 0.2
    override val placeFlags = Spots.VISIT

    private val r = BrainTuning.Roam
    private const val CURIOSITY_TAU = 150.0
    private const val SIT_LO = 3.0
    private const val SIT_HI = 7.0
    private const val LOOK_LO = 2.0
    private const val LOOK_HI = 4.0
    private const val SNIFF_AGAIN = 0.8f

    override fun satisfies(d: Drive) = if (d == Drive.CURIOSITY) CURIOSITY_TAU else 0.0

    override fun eligible(m: MindView) =
        m.drives[Drive.ENERGY] > r.EXPLORE_ENERGY && !m.persona.atBowlOnly

    override fun appeal(m: MindView): Double {
        val curious =
            r.EXPLORE_CURIOSITY_FLOOR +
                (1.0 - r.EXPLORE_CURIOSITY_FLOOR) * m.drives[Drive.CURIOSITY]
        return r.EXPLORE_BASE * Roaming.urge(m) * m.persona.exploreWeight * curious
    }

    override fun suits(m: MindView, spot: Spot) = spot !== m.here

    override fun placeFactor(m: MindView, spot: Spot): Double {
        val other = if (spot.has(Spots.BOWL) && spot !== Spots.bowl(m.slot)) r.OTHER_BOWL else 1.0
        return Beats.taste(m, spot) * m.book.fresh(m.slot, spot) * other
    }

    override fun reason(m: MindView) = "has a look round"

    override fun duration(m: MindView, rng: Rng): Double =
        Beats.seconds(rng, r.EXPLORE_LO_S, r.EXPLORE_HI_S) * m.persona.durationMul

    override fun script(m: MindView, at: Spot?, seconds: Double, rng: Rng, out: Script) {
        val floor = (at ?: m.here)?.onFloor != false
        var left = seconds
        if (floor) {
            out.act(Rows.SNIFF, Rows.once(Rows.SNIFF))
            left -= Rows.once(Rows.SNIFF)
        }
        while (left > 0.0) {
            val pick = rng.nextFloat()
            when {
                pick < SIT_SHARE -> {
                    val sit = minOf(left, Beats.seconds(rng, SIT_LO, SIT_HI))
                    out.act(Rows.SIT, sit)
                    left -= sit
                }
                pick < SNIFF_AGAIN && floor -> {
                    out.act(Rows.SNIFF, Rows.once(Rows.SNIFF))
                    left -= Rows.once(Rows.SNIFF)
                }
                else -> {
                    val look = minOf(left, Beats.seconds(rng, LOOK_LO, LOOK_HI))
                    out.act(Rows.LOOKUP, look)
                    left -= look
                }
            }
        }
    }

    private const val SIT_SHARE = 0.3f
}

/**
 * Zoomies (roaming): a burst of mad galloping back and forth across the floor, three to five legs,
 * then a sit and a wash as if nothing happened. Kittens and playful cats, with energy, most at the
 * dawn and dusk peaks. Only on the floor (a cat up on a perch first gets down), only to places a
 * cat can be seen at.
 */
internal object Zoomies : Activity {
    override val id = "zoomies"
    override val mainDrive = Drive.PLAY
    override val lively = true
    override val onFloor = true
    override val exertion = 2.0
    override val arousal = 0.8
    override val cooldownMin = BrainTuning.Roam.ZOOM_COOLDOWN_MIN
    override val maxMinutes = BrainTuning.Limits.SHORT_MAX_MIN

    private val r = BrainTuning.Roam
    private const val PLAY_TAU = 50.0
    private const val PLAY_FLOOR = 0.3
    private const val EDGE_PX = 24f

    override fun satisfies(d: Drive) = if (d == Drive.PLAY) PLAY_TAU else 0.0

    override fun eligible(m: MindView) = m.drives[Drive.ENERGY] > r.ZOOM_ENERGY && !m.persona.weak

    override fun appeal(m: MindView): Double {
        val play = PLAY_FLOOR + (1.0 - PLAY_FLOOR) * m.drives[Drive.PLAY]
        val peak = Curves.lerp(1.0, r.ZOOM_PEAK_GAIN, m.schedule.peak(m.hour))
        val gain = m.persona.playGain
        return r.ZOOM_BASE * Roaming.urge(m) * play * gain * gain * peak
    }

    override fun reason(m: MindView) =
        if (m.persona.kitten) "kitten energy" else "a burst of energy"

    override fun duration(m: MindView, rng: Rng) = Beats.seconds(rng, r.ZOOM_LO_S, r.ZOOM_HI_S)

    override fun script(m: MindView, at: Spot?, seconds: Double, rng: Rng, out: Script) {
        out.act(Rows.CROUCH, Rows.once(Rows.CROUCH))
        val legs =
            r.ZOOM_LEGS_LO + (rng.nextFloat() * (r.ZOOM_LEGS_HI - r.ZOOM_LEGS_LO + 1)).toInt()
        var x = m.x
        var dir = if (x > (World.HOUSE_MIN_X + World.HOUSE_MAX_X) / 2) -1 else 1
        repeat(legs.coerceAtMost(r.ZOOM_LEGS_HI)) {
            var to = clamp(x + dir * rng.range(r.ZOOM_LEG_MIN_PX, r.ZOOM_LEG_MAX_PX))
            // a leg that the house's end cut short turns the other way
            if (Math.abs(to - x) < r.ZOOM_LEG_MIN_PX) {
                dir = -dir
                to = clamp(x + dir * rng.range(r.ZOOM_LEG_MIN_PX, r.ZOOM_LEG_MAX_PX))
            }
            val z = Occluders.visibleZ(to, rng.range(r.ZOOM_Z_LO, r.ZOOM_Z_HI))
            out.dash(to, z)
            x = to
            dir = -dir
        }
        out.act(Rows.SIT, r.ZOOM_AFTER_SIT_S)
        out.act(Rows.GROOM, r.ZOOM_AFTER_WASH_S)
    }

    private fun clamp(x: Float) =
        x.coerceIn(World.HOUSE_MIN_X + EDGE_PX, World.HOUSE_MAX_X - EDGE_PX)
}

/**
 * Solo play (roaming): with a toy lying about on the floor, a cat takes it up, carries it, tosses
 * it, chases, pounces and bats it, sets it down and has a wash (the beats of the owner's game, but
 * nobody asked and nothing is reported to the page); with none, it stalks something unseen: a
 * crouch, a short dash, a swat and sometimes a roll. A kitten plays two rounds, an adult one.
 */
internal object SoloPlay : Activity {
    override val id = "solo play"
    override val mainDrive = Drive.PLAY
    override val lively = true
    override val onFloor = true
    override val exertion = 1.6
    override val arousal = 0.6
    override val cooldownMin = BrainTuning.Roam.SOLO_COOLDOWN_MIN
    override val maxMinutes = BrainTuning.Limits.SHORT_MAX_MIN

    private val r = BrainTuning.Roam
    private const val PLAY_TAU = 40.0
    private const val PLAY_FLOOR = 0.4
    private const val TOSS_POWER = 0.5f
    private const val CHASE_S = 5.0
    private const val PICK_S = 3.0
    private const val CARRY_S = 3.0
    private const val TOSS_S = 1.2
    private const val POUNCE_S = 2.5
    private const val BAT_S = 1.2
    private const val SET_S = 1.5
    private const val GROOM_S = 3.0
    private const val CARRY_PX = 40f
    private const val ROLL_S = 3.0
    private const val SIT_S = 2.0
    private const val EDGE_PX = 24f

    override fun satisfies(d: Drive) = if (d == Drive.PLAY) PLAY_TAU else 0.0

    override fun eligible(m: MindView) = m.drives[Drive.ENERGY] > r.SOLO_ENERGY && !m.persona.weak

    override fun appeal(m: MindView): Double {
        val play = PLAY_FLOOR + (1.0 - PLAY_FLOOR) * m.drives[Drive.PLAY]
        return r.SOLO_BASE * Roaming.urge(m) * play * m.persona.playGain
    }

    override fun reason(m: MindView) = if (reachable(m)) "plays with a toy" else "stalks a shadow"

    override fun duration(m: MindView, rng: Rng) = Beats.seconds(rng, r.SOLO_LO_S, r.SOLO_HI_S)

    private fun reachable(m: MindView): Boolean {
        val toy = m.looseToy ?: return false
        return Math.abs(toy.x - m.x) <= r.SOLO_TOY_REACH_PX
    }

    override fun script(m: MindView, at: Spot?, seconds: Double, rng: Rng, out: Script) {
        val toy = m.looseToy
        if (toy != null && reachable(m)) return withToy(m, toy.id, toy.x, rng, out)
        val dir = rng.sign()
        out.act(Rows.CROUCH, r.SOLO_STALK_S.toDouble())
        val to =
            (m.x + dir * r.SOLO_POUNCE_PX).coerceIn(
                World.HOUSE_MIN_X + EDGE_PX,
                World.HOUSE_MAX_X - EDGE_PX,
            )
        out.dash(to, null)
        out.act(Rows.BAT, Beats.seconds(rng, r.SOLO_SWAT_LO_S, r.SOLO_SWAT_HI_S))
        if (rng.chance(r.SOLO_ROLL_CHANCE)) out.act(Rows.ROLL, ROLL_S)
        out.act(Rows.SIT, SIT_S)
    }

    private fun withToy(m: MindView, id: Int, at: Float, rng: Rng, out: Script) {
        var dir = if (at < (World.HOUSE_MIN_X + World.HOUSE_MAX_X) / 2) 1 else -1
        val rounds = if (m.persona.kitten) r.SOLO_ROUNDS_KITTEN else r.SOLO_ROUNDS_ADULT
        repeat(rounds) { round ->
            val to = World.clampHouse(at + (if (round % 2 == 0) dir * CARRY_PX else 0f))
            out.toy(PlanStep.PickUp(id), PICK_S)
            out.toy(PlanStep.Carry(to), CARRY_S)
            out.toy(PlanStep.Toss(dir, TOSS_POWER), TOSS_S)
            out.toy(PlanStep.Chase(id, CHASE_S.toFloat()), CHASE_S)
            out.toy(PlanStep.Pounce(id), POUNCE_S)
            out.toy(PlanStep.Bat(id, BrainTuning.Play.BAT_POWER), BAT_S)
            dir = -dir
        }
        out.toy(PlanStep.PickUp(id), PICK_S)
        out.toy(PlanStep.Carry(World.clampHouse(at + dir * CARRY_PX)), CARRY_S)
        out.toy(PlanStep.SetDown, SET_S)
        out.act(Rows.GROOM, GROOM_S)
    }
}
