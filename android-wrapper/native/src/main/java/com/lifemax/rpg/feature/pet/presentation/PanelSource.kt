package com.lifemax.rpg.feature.pet.presentation

import com.lifemax.rpg.feature.pet.domain.PetBar
import com.lifemax.rpg.feature.pet.domain.PetInner
import com.lifemax.rpg.feature.pet.domain.PetMind
import com.lifemax.rpg.feature.pet.host.CatWorldHost
import com.lifemax.rpg.feature.pet.world.BlobCat
import com.lifemax.rpg.feature.pet.world.Mood
import kotlin.math.roundToInt

/**
 * Where the Cat tab's mind row and inner-life card come from (docs/specs/CAT_BRAIN_PLAN.md 2.8):
 * from the page's projection, unchanged, unless the cat world is active, and then from the world's
 * own state for the cat whose card shows (its mood, its four attachment threads, its comfort place
 * and what it did while the owner was away). Everything else on the cards stays the page's (words,
 * quirks, memories), and a panel the page did not send stays absent. The strict decoders of the
 * projection are untouched; this only chooses the values the cards show.
 */
object PanelSource {
    private const val PERCENT = 100.0
    private const val AWAY_TITLE = "While you were away"
    private const val COMFORT_LEAD = "Likes to rest on "

    /** Mood words, tested in order; the first that fits wins (valence v and arousal a, -1..1). */
    private val MOODS: List<Pair<String, (Mood) -> Boolean>> =
        listOf(
            "overstimulated" to { m -> m.v < -0.3 && m.a > 0.2 },
            "uneasy" to { m -> m.v < -0.1 },
            "drowsy" to { m -> m.a < -0.3 },
            "playful" to { m -> m.v >= 0.3 && m.a >= 0.3 },
            "bright" to { m -> m.v >= 0.3 && m.a >= 0.1 },
            "content" to { m -> m.v >= 0.2 },
            "calm" to { _ -> true },
        )

    /** How strong an attachment thread is, in words, by its share. */
    private val TIERS =
        listOf(25 to "Just starting", 50 to "Growing", 75 to "Strong", 101 to "Deep")

    /** The page's places (PET_SPOTS), as a sentence names them. */
    private val SPOT_NAMES =
        mapOf(
            "bed" to "the bed",
            "sill" to "the bedroom sill",
            "tree" to "the cat tree",
            "rug" to "the rug",
            "couch" to "the sofa",
            "lsill" to "the living room sill",
            "lrug" to "the living room rug",
            "pouf" to "the pouf",
            "cushion" to "the cushion",
            "counter" to "the counter",
            "ksill" to "the kitchen sill",
            "box" to "the box",
            "kitchen" to "the kitchen floor",
        )

    /** The mood word for a mood. */
    fun moodWord(m: Mood): String = MOODS.first { it.second(m) }.first

    /**
     * The mind row for cat [slot]: the page's [page] row with the mood word and the away story from
     * the world, or [page] itself when the world is not active, has no state for the cat, or the
     * page sent no row.
     */
    fun mind(page: PetMind?, slot: Int, host: CatWorldHost): PetMind? {
        val cat = state(slot, host) ?: return page
        val row = page ?: return null
        val away = host.awayStory()?.lines?.joinToString(" ").orEmpty()
        return row.copy(
            mood = moodWord(cat.mood),
            awayTitle = row.awayTitle.ifEmpty { if (away.isEmpty()) "" else AWAY_TITLE },
            away = away,
        )
    }

    /**
     * The inner-life card for cat [slot]: the four threads from the world's trust (same order and
     * labels as the page's) and the comfort place from its spots; the rest is the page's.
     */
    fun inner(page: PetInner?, slot: Int, host: CatWorldHost): PetInner? {
        val cat = state(slot, host) ?: return page
        val card = page ?: return null
        val shares =
            listOf(cat.trust.fam, cat.trust.tru, cat.trust.sec, cat.trust.aut).map {
                (it.coerceIn(0.0, 1.0) * PERCENT).roundToInt()
            }
        val threads =
            if (card.threads.size == shares.size)
                card.threads.mapIndexed { i, bar -> PetBar(bar.label, shares[i], tier(shares[i])) }
            else card.threads
        val top = cat.spots.maxByOrNull { it.value }?.key?.let(SPOT_NAMES::get)
        return card.copy(
            threads = threads,
            comfort = if (top != null) COMFORT_LEAD + top else card.comfort,
        )
    }

    private fun tier(pct: Int) = TIERS.first { pct < it.first }.second

    private fun state(slot: Int, host: CatWorldHost): BlobCat? {
        if (!host.active) return null
        val cat = host.cats.getOrNull(slot) ?: return null
        return host.stateOf(cat.id)
    }
}
