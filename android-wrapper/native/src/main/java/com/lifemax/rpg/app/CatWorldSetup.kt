package com.lifemax.rpg.app

import com.lifemax.rpg.feature.pet.brain.BrainFactory
import com.lifemax.rpg.feature.pet.brain.CatProfile
import com.lifemax.rpg.feature.pet.brain.CatWorld
import com.lifemax.rpg.feature.pet.host.AutopilotBrain
import com.lifemax.rpg.feature.pet.host.CatWorldHost

/**
 * The composition root of the cat world (docs/specs/CAT_BRAIN_PLAN.md 2.7): gives the process's one
 * [CatWorldHost] the native brain, once, from the first plugin that shows the cats (the Cat tab or
 * the Overview cards; both call [install] as they are created, before they register with the host).
 *
 * The host still starts only when the page sends a good `world` block (`pet.world.v1`, the Settings
 * switch "Native Cat (beta)" on); without one every screen keeps its shipped autopilot room. A cat
 * set the brain cannot take (it refuses to build) falls back to the [AutopilotBrain], which leaves
 * the bodies to the shipped autopilot but keeps the world's persistence.
 */
object CatWorldSetup {
    private var installed = false

    /** The native brain, live, on the house's own bodies; the autopilot if it cannot be built. */
    val factory = BrainFactory { cats, clock ->
        runCatching { CatWorld(clock, cats.map(CatProfile::of), CatWorld.Fidelity.LIVE) }
            .getOrElse { AutopilotBrain(cats.map { it.name }) }
    }

    /** Sets [CatWorldHost.shared]'s brain the first time it is called; later calls do nothing. */
    @Synchronized
    fun install(host: CatWorldHost = CatWorldHost.shared) {
        if (installed) return
        installed = true
        host.brainFactory = factory
    }
}
