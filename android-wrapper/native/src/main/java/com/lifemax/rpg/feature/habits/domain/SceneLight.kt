package com.lifemax.rpg.feature.habits.domain

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The street's lighting, with no drawing in it. One key light at a time: the sun by day, the moon by night,
 * standing where the sky draws it ([orbAt]), behind the buildings. Its light travels sideways (away from the
 * side it stands on), downward, and out of the facades towards the viewer, so everything that stands up
 * throws a shadow forward onto the pavement that leans away from the light: long and leaning right in the
 * morning, short at noon, long and leaning left in the evening, gone as the sun sets, then faint and cool
 * under the moon. The same projection serves every caster (a lamp post, a swaying tree, a whole building),
 * so their shadows agree with one another and with the hour.
 */

/**
 * The key light at one moment. Light travels along ([across], -[down], [out]), a unit vector:
 * [across] sideways (positive to the right: the light stands on the left), [down] downward, [out]
 * out of the facades towards the viewer. [strength] is how hard it is, 1 full sun to 0 none (the
 * hand-over at the horizon). [cool] is 0 for the sun's shade and 1 for the moon's.
 */
class KeyLight(
    val across: Float,
    val down: Float,
    val out: Float,
    val strength: Float,
    val cool: Float,
) {
    /** How far a shadow leans sideways per unit of the caster's height (positive: to the right). */
    val lean: Float
        get() = across / down

    /**
     * How far down the pavement a shadow reaches per unit of the caster's height, on screen: the
     * ground is seen at a slant ([SceneLight.FLAT]), so a shadow is shorter on screen than it is.
     */
    val reach: Float
        get() = SceneLight.FLAT * out / down
}

object SceneLight {
    /** The ground's foreshortening: a unit of depth along the pavement is this much on screen. */
    const val FLAT = .5f

    /** The sun's (or moon's) elevation at the ends of its arc and at the top of it, in degrees. */
    const val LOW = 10f
    const val HIGH = 52f

    /** How hard the moon's light is next to the sun's. */
    const val MOON = .3f

    /** A low sun at dusk is this much of a noon sun; it brightens with the sky's tone. */
    const val DUSK = .45f

    /** The key light at [hour]. */
    fun key(hour: Float): KeyLight {
        val night = skyPhase(hour) == SkyPhase.NIGHT
        val (acrossSky, up) = orbAt(hour)
        // on the left of the sky the light travels right, and the more it is to one side the more
        // sideways it travels (never quite flat along the facades)
        val side = ((acrossSky - .5f) * 2.2f).coerceIn(-.92f, .92f)
        val elevation = Math.toRadians((LOW + (HIGH - LOW) * up).toDouble())
        val level = cos(elevation).toFloat()
        val across = -side * level
        val out = sqrt(max(0f, 1f - side * side)) * level
        val down = sin(elevation).toFloat()
        // it comes on as it rises, so there is no shadow to pop when the sun hands over to the moon
        val rise = smooth((up / .3f).coerceIn(0f, 1f))
        val strength =
            if (night) MOON * rise
            else rise * (DUSK + (1f - DUSK) * (skyTone(hour) - 1f).coerceIn(0f, 1f))
        return KeyLight(across, down, out, strength, if (night) 1f else 0f)
    }

    /** How dark a cloud's shadow is at [light]: only the sun's clouds shade the street. */
    fun cloudShade(light: KeyLight): Float = CLOUD_SHADE * light.strength * (1f - light.cool)

    /** How far a cloud's shadow stands from the cloud, sideways, in dp: it leans with the light. */
    fun cloudDrop(light: KeyLight): Float = light.lean * CLOUD_HEIGHT

    /** The deepest a cloud's shadow is. */
    const val CLOUD_SHADE = .2f

    /** How high the clouds hang over the street, for where their shadows fall, in dp. */
    const val CLOUD_HEIGHT = 90f

    private fun smooth(t: Float) = t * t * (3f - 2f * t)
}
