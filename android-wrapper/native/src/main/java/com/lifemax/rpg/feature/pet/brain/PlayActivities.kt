package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.PlanStep
import com.lifemax.rpg.feature.pet.body.Rng
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.ToyBody
import com.lifemax.rpg.feature.pet.body.ToyField
import com.lifemax.rpg.feature.pet.body.ToyKind
import com.lifemax.rpg.feature.pet.body.ToyState
import com.lifemax.rpg.feature.pet.body.World
import com.lifemax.rpg.feature.pet.world.WorldContract

/**
 * The owner's game (plan 1.8 "owner play", CB4 part B's first slice): the owner tossed a toy into
 * the room from the Cat tab's toy tray and the cat that came plays with it, with the body's own toy
 * acts: it notices the toy and walks up to it, picks it up, carries it a little way (the toy swings
 * from its mouth), tosses it, chases it, pounces on it and bats it about, one to three rounds, and
 * at the end carries it a step and sets it down and has a wash. A cat up on a perch gets down to
 * the floor first. Only a reflex starts it ([CatMind.play]); who comes and who will not is
 * [refusal]. The game is reported to the page when it ends (the `play` command).
 */
internal object OwnerPlay : Activity {
    override val id = "play"
    override val mainDrive = Drive.PLAY
    override val reflexOnly = true
    override val resumesAfterRestart = false
    override val onFloor = true
    override val arousal = 0.7
    override val exertion = BrainTuning.Play.EXERTION
    override val maxMinutes = BrainTuning.Play.MAX_MIN

    /** The explain ring's reason for a game. */
    const val REASON = "the owner tossed a toy"

    private val p = BrainTuning.Play

    override fun satisfies(d: Drive) = if (d == Drive.PLAY) p.SATISFY_TAU else 0.0

    override fun eligible(m: MindView) = m.toy != null

    override fun appeal(m: MindView) = 0.0

    override fun reason(m: MindView) = REASON

    override fun duration(m: MindView, rng: Rng) = 0.0

    /**
     * Why cat [m] will not come now (its [activity], [atPlaceS] into it), with [PlayWords.NAME] for
     * the name, or null when it comes.
     */
    fun refusal(m: MindView, activity: Activity, atPlaceS: Double): String? {
        val energy = m.drives[Drive.ENERGY]
        return when {
            m.persona.weak -> PlayWords.HUNGRY
            activity === Eat -> PlayWords.EATING
            activity.asleep && atPlaceS > BrainTuning.Care.DEEP_SLEEP_S -> PlayWords.DEEP
            activity.asleep && energy < p.WAKE_ENERGY -> PlayWords.DROWSY
            !activity.asleep && energy < p.TIRED_ENERGY -> PlayWords.TIRED
            else -> null
        }
    }

    /** One round for a cat that is just about willing, up to three for a rested, playful one. */
    private fun rounds(m: MindView): Int {
        var n = 1
        if (m.drives[Drive.ENERGY] > p.RESTED) n++
        if (m.drives[Drive.PLAY] > p.PLAYFUL || m.persona.kitten) n++
        if (m.valence < p.GLUM) n = 1
        return n.coerceAtMost(p.ROUNDS_MAX)
    }

    override fun script(m: MindView, at: Spot?, seconds: Double, rng: Rng, out: Script) {
        val toy = m.toy
        if (toy == null) {
            // a game does not outlive the app: a cat restored mid-game only sits up a moment
            out.act(Rows.SIT, Beats.SIT_LO)
            return
        }
        val id = toy.id
        val start = toy.x
        val power = Curves.lerp(p.TOSS_LO.toDouble(), p.TOSS_HI.toDouble(), (m.arousal + 1) / 2)
        // toward the open middle of the house first, then back
        var dir = if (start < (World.HOUSE_MIN_X + World.HOUSE_MAX_X) / 2) 1 else -1
        repeat(rounds(m)) { r ->
            val to = World.clampHouse(start + (if (r % 2 == 0) dir * p.CARRY_PX else 0f))
            out.toy(PlanStep.PickUp(id), p.PICK_S)
            out.toy(PlanStep.Carry(to), p.CARRY_S)
            out.toy(PlanStep.Toss(dir, power.toFloat()), p.TOSS_S)
            out.toy(PlanStep.Chase(id, p.CHASE_S.toFloat()), p.CHASE_S)
            out.toy(PlanStep.Pounce(id), p.POUNCE_S)
            out.toy(PlanStep.Bat(id, p.BAT_POWER), p.BAT_S)
            dir = -dir
        }
        out.toy(PlanStep.PickUp(id), p.PICK_S)
        out.toy(PlanStep.Carry(World.clampHouse(start + dir * p.SETTLE_PX)), p.CARRY_S)
        out.toy(PlanStep.SetDown, p.SET_S)
        out.act(Rows.GROOM, p.GROOM_AFTER_S)
    }
}

/**
 * Making way (the overlap guard, [BrainTuning.Apart]): the runner has walked the cat aside (see
 * [ActivityRunner.makeWay]); here it sits a moment and has a short wash before it picks anew.
 */
internal object MakeWay : Activity {
    override val id = "make way"
    override val mainDrive = Drive.COMFORT
    override val reflexOnly = true
    override val resumesAfterRestart = false
    override val maxMinutes = BrainTuning.Limits.SHORT_MAX_MIN

    override fun eligible(m: MindView) = true

    override fun appeal(m: MindView) = 0.0

    override fun reason(m: MindView) = "the other cat is there"

    override fun duration(m: MindView, rng: Rng) = 0.0

    override fun script(m: MindView, at: Spot?, seconds: Double, rng: Rng, out: Script) {
        out.act(Rows.SIT, BrainTuning.Apart.ASIDE_SIT_S)
        out.act(Rows.GROOM, BrainTuning.Apart.ASIDE_GROOM_S)
    }
}

/**
 * Where an offered toy lands (the owner's toss from the tray): on the floor in front of a cat, the
 * house's own toy of that kind moved there when it lies about (a toy no cat holds), else a new one
 * added while the field has room. The wand is drawn as its feather.
 */
internal object ToyDrop {
    /** The toy of the page's [key] lying in front of [cat] in [field], or null (none can be). */
    fun near(field: ToyField, key: String, cat: Executor): ToyBody? {
        val kind = ToyKind.of(key) ?: if (key == WAND) ToyKind.FEATHER else return null
        val ahead = cat.facing * BrainTuning.Play.DROP_AHEAD_PX
        val x = World.clampHouse(cat.x + ahead)
        val z = if (cat.surface == SurfaceId.FLOOR) cat.z else World.ART_Z
        var toy: ToyBody? = null
        for (i in 0 until field.count) {
            val t = field[i] ?: continue
            if (t.kind == kind && t.state != ToyState.HELD && t.state != ToyState.LIFT) toy = t
        }
        if (toy == null && field.count < WorldContract.MAX_TOYS_ON_FLOOR)
            return field.add(kind, x, SurfaceId.FLOOR, z)
        toy?.place(x, SurfaceId.FLOOR, z)
        return toy
    }

    private const val WAND = "wand"
}

/**
 * The brain's own short lines about a toy the owner offered: why no cat comes ([NAME] stands for
 * the cat's name). Honest: a cat that will not play says so instead of pretending.
 */
object PlayWords {
    const val NAME = "{name}"
    const val HUNGRY = "$NAME is too hungry to play. A meal first."
    const val EATING = "$NAME is eating and will not leave the bowl."
    const val DEEP = "$NAME is fast asleep and does not stir."
    const val DROWSY = "$NAME opens one eye, then goes back to sleep."
    const val TIRED = "$NAME is too sleepy to play right now."
    const val NO_TOY = "That toy is not in the room."
    const val NOT_HERE = "The cats cannot play here yet."
}
