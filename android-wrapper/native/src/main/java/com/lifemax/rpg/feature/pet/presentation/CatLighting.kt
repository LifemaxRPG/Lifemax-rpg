package com.lifemax.rpg.feature.pet.presentation

import com.lifemax.rpg.feature.pet.art.CatLightKey
import com.lifemax.rpg.feature.pet.body.CatPose
import com.lifemax.rpg.feature.pet.light.CatTarget
import com.lifemax.rpg.feature.pet.light.HouseLight
import com.lifemax.rpg.feature.pet.light.Weather

/**
 * The cats' light in one room stage (docs/specs/CAT_LIGHTING_PLAN.md WP-L3): once a frame [begin]
 * takes the hour and the weather, then [keyFor] gives each cat the light key it is drawn in, held
 * steady by the cat's [SpriteSteady.light]. While [on] answers false every cat is drawn in
 * [CatLightKey.NEUTRAL], which is the drawing exactly as it was before lighting existed.
 *
 * @param weatherNow the weather outside, asked once a frame (a test or the developer picker fixes
 *   it)
 * @param on whether cat lighting is on now (the developer switch, and the performance guard's best
 *   level)
 */
internal class CatLighting(private val weatherNow: () -> Weather, private val on: () -> Boolean) {
    /** The house's light at the hour of the latest [begin]. */
    val house = HouseLight()

    private val target = CatTarget()

    /** The hour of the latest [begin]. */
    var hour = 0.0
        private set

    /** Whether lighting was on at the latest [begin]. */
    var active = false
        private set

    /** The latest key and what lit it, per cat slot, for the readout (-1: no cat drawn there). */
    private val keys = IntArray(MAX_NOTES) { -1 }
    private val sources = Array(MAX_NOTES) { "" }

    /** Take the light of [hour] for this frame. */
    fun begin(hour: Double) {
        this.hour = hour
        active = on()
        if (active) house.update(hour, weatherNow())
    }

    /**
     * The light key to draw the cat of [pose] in at stage time [now] (seconds); [slot] numbers the
     * cat for the readout.
     */
    fun keyFor(pose: CatPose, now: Double, slot: Int = 0): Int {
        val hold = SpriteSteady.of(pose).light
        if (!active) {
            // the next time lighting is on, the cat's light starts afresh
            hold.reset()
            return CatLightKey.NEUTRAL
        }
        house.target(pose.x, pose.y, pose.surface.key, pose.flip, target)
        val key = hold.follow(target.halfSteps.toDouble(), target.tint, target.flatten, now)
        if (slot in keys.indices) {
            // kept as they are (no string is made per frame); the readout words them
            keys[slot] = key
            sources[slot] = target.source
        }
        return key
    }

    /** One line for the developer readout. */
    fun readout(): String {
        if (!active) return "Cat light off"
        val h = house
        val cats =
            keys.indices
                .filter { keys[it] >= 0 }
                .joinToString(", ") {
                    val from = sources[it]
                    CatLightKey.label(keys[it]) + if (from.isEmpty()) "" else " ($from)"
                }
        return "Cat light ${h.phase.name.lowercase()} ${h.weather.key}, slide ${h.driftPx}px, " +
            "lamp ${h.lampStep}, v${h.version}; cats $cats"
    }

    private companion object {
        const val MAX_NOTES = 4
    }
}
