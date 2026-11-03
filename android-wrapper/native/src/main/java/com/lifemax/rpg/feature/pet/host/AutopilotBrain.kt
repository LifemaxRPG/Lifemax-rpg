package com.lifemax.rpg.feature.pet.host

import com.lifemax.rpg.feature.pet.brain.Brain
import com.lifemax.rpg.feature.pet.brain.BrainFactory
import com.lifemax.rpg.feature.pet.brain.Doing

/**
 * The brain the host runs until the native cat brain replaces it (the app sets
 * [CatWorldHost.brainFactory]): it decides nothing, so the host's bodies are kept busy by the
 * shipped autopilot as in every screen's own room, but the cats are the one pair of the process,
 * their places are saved with the page and restored on the next open, and the world's start,
 * persistence and panels work end to end.
 */
class AutopilotBrain(override val names: List<String>) : Brain {
    override fun step(step: Long, think: Boolean) {}

    override fun now(slot: Int) = IDLE

    companion object {
        private val IDLE = Doing("idle", "floor", 0.0, "the shipped autopilot")

        /** Builds one for the living cats. */
        val factory = BrainFactory { cats, _ -> AutopilotBrain(cats.map { it.name }) }
    }
}
