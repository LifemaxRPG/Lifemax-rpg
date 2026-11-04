package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.Tuning
import com.lifemax.rpg.feature.pet.world.WorldCat
import com.lifemax.rpg.feature.pet.world.WorldContract

/**
 * What the brain needs to know about one cat from the page (plan 2.1): its world [id] (the album's
 * `c<born base36>`, which seeds it, so a rename never changes its habits), [name] (for the day
 * viewer only), [slot], the DNA scores [persona] (sk, ou, dm, sp, fr, vo, 0..1), growth [stage] (-1
 * grown, 0..5 a kitten), the rig's [legMorph] and [bodyMorph], the page's hunger [hungerStage]
 * (0..5) and [vacation], the page's place likings [likes] (0..100 by PET_SPOTS id) and the
 * relationship [trust] (0..1).
 */
data class CatProfile(
    val id: String,
    val name: String,
    val slot: Int,
    val persona: Map<String, Double> = emptyMap(),
    val stage: Int = WorldContract.STAGE_GROWN,
    val legMorph: Double = 1.0,
    val bodyMorph: Double = 1.0,
    val hungerStage: Int = 0,
    val vacation: Boolean = false,
    val likes: Map<String, Int> = emptyMap(),
    val trust: Double = DEFAULT_TRUST,
) {
    companion object {
        const val DEFAULT_TRUST = 0.6

        /** The brain's view of a decoded page cat. */
        fun of(cat: WorldCat): CatProfile =
            CatProfile(
                id = cat.id,
                name = cat.name,
                slot = cat.slot,
                persona = cat.persona,
                stage = cat.stage,
                legMorph = cat.legMorph,
                bodyMorph = cat.bodyMorph,
                hungerStage = cat.hunger.stage,
                vacation = cat.hunger.vacation,
                likes = cat.likes.spots,
                trust = cat.legacy?.trust?.tru ?: DEFAULT_TRUST,
            )
    }
}

/**
 * A cat's character as weights (plan 1.4): each DNA score becomes `lerp(lo, hi, score)` with the
 * bounds in [BrainTuning.Persona], plus the kitten and hunger modifiers. Constant for a cat except
 * through [hungerStage] and [vacation], which the page changes.
 */
class Persona(profile: CatProfile) {
    private val t = BrainTuning.Persona

    private fun score(key: String, p: CatProfile) =
        (p.persona[key] ?: t.DEFAULT_SCORE).coerceIn(0.0, 1.0)

    val skittish = score("sk", profile)
    val outgoing = score("ou", profile)
    val dominant = score("dm", profile)
    val spontaneous = score("sp", profile)
    val friendly = score("fr", profile)
    val vocal = score("vo", profile)

    /** The page's own trait rule: lazy = 1 − (ou + sp) / 2. */
    val lazy = 1.0 - (outgoing + spontaneous) / 2.0

    /** Growth stage, -1 grown. */
    val stage = profile.stage
    val kitten = stage in 0..WorldContract.STAGE_MAX

    /** A kitten's jumping size (Tuning.Parkour.STAGE_SCALE), 1 grown. */
    val growthScale: Float =
        if (kitten)
            Tuning.Parkour.STAGE_SCALE[stage.coerceIn(0, Tuning.Parkour.STAGE_SCALE.size - 1)]
        else 1f

    var hungerStage = profile.hungerStage
    var vacation = profile.vacation

    /** Hunger stage 3 and up: the weak look, no parkour, near the bowl. */
    val weak: Boolean
        get() = !vacation && hungerStage >= t.WEAK_STAGE

    /** Hunger stage 4 and up: only rests by the bowl. */
    val atBowlOnly: Boolean
        get() = !vacation && hungerStage >= t.BOWL_STAGE

    /** Hungry enough to beg when the owner is there. */
    val begs: Boolean
        get() = !vacation && hungerStage >= t.BEG_STAGE

    val curiosityGain = Curves.lerp(t.CURIOSITY_LO, t.CURIOSITY_HI, outgoing)
    val territoryGain = Curves.lerp(t.TERRITORY_LO, t.TERRITORY_HI, dominant)
    val playGain =
        Curves.lerp(t.PLAY_LO, t.PLAY_HI, spontaneous) * (if (kitten) t.KITTEN_PLAY else 1.0)
    val socialGain = Curves.lerp(t.SOCIAL_LO, t.SOCIAL_HI, friendly)
    val energyDrain =
        Curves.lerp(t.DRAIN_LO, t.DRAIN_HI, lazy) * (if (kitten) t.KITTEN_DRAIN else 1.0)

    /** Softmax temperature of the pick: spontaneous cats are less predictable. */
    val temperature = Curves.lerp(t.TEMPERATURE_LO, t.TEMPERATURE_HI, spontaneous)

    /** Activity length multiplier: spontaneous cats switch sooner. */
    val durationMul = Curves.lerp(t.DURATION_LO, t.DURATION_HI, spontaneous)

    /** Liking of hidden places (box, behind the bed). */
    val hiddenLike = Curves.lerp(t.HIDDEN_LO, t.HIDDEN_HI, skittish)

    /** Liking of high places (tree top, fridge, bookcase). */
    val highLike =
        Curves.lerp(t.HIGH_LO, t.HIGH_HI, dominant) * (1.0 - t.SKITTISH_HIGH_LOSS * skittish)

    val restWeight = Curves.lerp(t.REST_LO, t.REST_HI, lazy)
    val exploreWeight =
        Curves.lerp(t.EXPLORE_LO, t.EXPLORE_HI, outgoing) * (if (kitten) t.KITTEN_EXPLORE else 1.0)
    val parkourWeight =
        Curves.lerp(t.PARKOUR_LO, t.PARKOUR_HI, (dominant + spontaneous) / 2.0) *
            (1.0 - t.SKITTISH_PARKOUR_LOSS * skittish)
    val begWeight = Curves.lerp(t.VOCAL_BEG_LO, t.VOCAL_BEG_HI, vocal)
}
