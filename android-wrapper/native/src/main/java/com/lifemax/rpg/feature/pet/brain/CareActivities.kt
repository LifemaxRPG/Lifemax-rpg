package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.Feeding
import com.lifemax.rpg.feature.pet.body.Rng
import com.lifemax.rpg.feature.pet.body.TailState

/**
 * Eating (plan 1.8): to its own bowl, face it, eat 7.5 s and wash 4.5 s, as the web room does. The
 * page's `fed` cue starts it as a reflex; on vacation a sitter's meal lets the cat choose it.
 */
internal object Eat : Activity {
    override val id = "eat"
    override val mainDrive = Drive.GROOM
    override val arousal = 0.3
    override val placeFlags = Spots.BOWL

    private const val SITTER_APPEAL = 3.0
    private const val GROOM_AFTER = 0.5

    override fun eligible(m: MindView) = m.sitterMeal

    override fun appeal(m: MindView) = SITTER_APPEAL

    override fun suits(m: MindView, spot: Spot) = spot === Spots.bowl(m.slot)

    override fun reason(m: MindView) = if (m.sitterMeal) "the sitter filled the bowl" else "fed"

    override fun duration(m: MindView, rng: Rng) =
        (Feeding.EAT_SECONDS + Feeding.GROOM_SECONDS).toDouble()

    override fun script(m: MindView, at: Spot?, seconds: Double, rng: Rng, out: Script) {
        out.face(Feeding.facing(m.slot))
        out.act(Rows.EAT, Feeding.EAT_SECONDS.toDouble())
        out.act(Rows.GROOM, Feeding.GROOM_SECONDS.toDouble())
    }

    override fun after(drives: Drives) {
        drives.nudge(Drive.GROOM, 1.0, GROOM_AFTER)
        // a full belly: the cat is drowsy (roaming)
        drives.nudge(Drive.ENERGY, 0.0, BrainTuning.Roam.MEAL_DROWSY)
    }
}

/**
 * Begging (plan 1.8): hunger stage 2 and up, the owner there, not on vacation: sits at its bowl and
 * meows with begging bobs. Only food helps (plan 1.4: "a hungry cat tells you with behaviour").
 */
internal object Beg : Activity {
    override val id = "beg"
    override val mainDrive = Drive.SOCIAL
    override val arousal = 0.5
    override val tail = TailState.QUESTION_MARK
    override val cooldownMin = BrainTuning.Cooldowns.BEG_MIN
    override val placeFlags = Spots.BOWL

    private const val BASE = 2.5
    private const val PER_STAGE = 0.3
    private const val SIT_LO = 4.0
    private const val SIT_HI = 7.0

    override fun eligible(m: MindView) = m.persona.begs && m.ownerPresent

    override fun appeal(m: MindView) =
        BASE *
            m.persona.begWeight *
            (1.0 + PER_STAGE * (m.persona.hungerStage - BrainTuning.Persona.BEG_STAGE))

    override fun suits(m: MindView, spot: Spot) = spot === Spots.bowl(m.slot)

    override fun reason(m: MindView) = "hungry, owner here"

    override fun duration(m: MindView, rng: Rng): Double {
        val d = BrainTuning.Durations
        return Beats.minutes(m, rng, d.BEG_LO_MIN, d.BEG_HI_MIN)
    }

    override fun script(m: MindView, at: Spot?, seconds: Double, rng: Rng, out: Script) {
        out.face(Feeding.facing(m.slot))
        var left = seconds
        while (left > 0.0) {
            out.act(Rows.MEOW, Rows.once(Rows.MEOW), Cue.BEG)
            val sit = Beats.seconds(rng, SIT_LO, SIT_HI)
            out.act(Rows.SIT, sit)
            left -= sit + Rows.once(Rows.MEOW)
        }
    }
}

/**
 * The CB1 catalogue (plan 1.8 rows sleep..knead). Later phases add theirs to the registry: toys and
 * box (CB4), pair (CB5), owner interaction (CB3), cheers and zoomies (CB6).
 */
object Catalogue {
    /** The reflex activities the runner starts by id. */
    const val EAT = "eat"
    const val LOAF = "loaf"

    /** A fresh registry with the core activities, in tie-break order. */
    fun core(): ActivityRegistry =
        ActivityRegistry(
            listOf(
                Sleep,
                WakingRoutine,
                Loaf,
                Groom,
                Wander,
                WindowWatch,
                ParkourVisit,
                Eat,
                Beg,
                Scratch,
                Knead,
                // roaming: looking about, zoomies and play on its own
                Explore,
                Zoomies,
                SoloPlay,
                // reflexes only (the selector passes them by): the owner's game, making way
                OwnerPlay,
                MakeWay,
            ) + Social.ACTIVITIES // the moments two cats share, started by the SocialDirector
        )
}
