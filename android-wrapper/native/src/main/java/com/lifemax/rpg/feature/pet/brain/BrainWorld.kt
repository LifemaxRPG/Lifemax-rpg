package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.world.BlobCat
import com.lifemax.rpg.feature.pet.world.BlobGift
import com.lifemax.rpg.feature.pet.world.BlobPair
import com.lifemax.rpg.feature.pet.world.BlobToy
import com.lifemax.rpg.feature.pet.world.WorldCat
import com.lifemax.rpg.feature.pet.world.WorldClock

/**
 * What the world host (feature/pet/host) needs of a brain beyond stepping it: to build one for the
 * living cats ([BrainFactory]), to save and restore its state ([BrainState]), and to fast-forward
 * over a gap ([CatchUp]). Every one has a do-nothing default on [Brain], so a brain grows into
 * them.
 */
fun interface BrainFactory {
    /** A brain for [cats] (one or two, in slot order) on [clock]; its state is restored after. */
    fun create(cats: List<WorldCat>, clock: WorldClock): Brain
}

/**
 * The brain's own state as the world blob keeps it (plan 2.3), without where the cats stand (the
 * bodies know that) and without the outbox (the host's): each cat by world id, the pair's last
 * activity, the toys about the house and a waiting gift.
 */
data class BrainState(
    val cats: Map<String, BlobCat>,
    val pair: BlobPair? = null,
    val toys: List<BlobToy> = emptyList(),
    val gift: BlobGift? = null,
)

/**
 * Where the catch-up story left cat [slot]: on [surface] (a body surface key) at house [x] (null
 * for the middle of the surface) and depth [z] (null for the surface's own), facing [dir], doing
 * [activity].
 */
data class CatchPlace(
    val slot: Int,
    val surface: String,
    val x: Double? = null,
    val z: Double? = null,
    val dir: Int = 1,
    val activity: String = "",
)

/**
 * The result of fast-forwarding a gap (plan 2.6): where each cat ended up and up to three lines
 * telling what happened, for the "while you were away" text. Data only.
 */
data class CatchUp(val places: List<CatchPlace>, val lines: List<String>) {
    companion object {
        val NONE = CatchUp(emptyList(), emptyList())
    }
}
