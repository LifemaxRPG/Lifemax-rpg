package com.lifemax.rpg.feature.pet.light

import com.lifemax.rpg.feature.pet.art.CatLightKey
import com.lifemax.rpg.feature.pet.art.LightTint
import com.lifemax.rpg.feature.pet.room.FloorLight
import com.lifemax.rpg.feature.pet.room.HouseMap
import com.lifemax.rpg.feature.pet.room.LightTuning
import com.lifemax.rpg.feature.pet.room.RoomLight
import kotlin.math.abs
import kotlin.math.floor

/** The time of day as the window light sees it. */
enum class LightPhase {
    DAY,
    TWILIGHT,
    NIGHT,
}

/** The light a cat should have, before it is held steady: filled by [HouseLight.target]. */
internal class CatTarget {
    /** Half-steps of shade, lighter positive ([CatLightKey]'s range). */
    var halfSteps = 0

    var tint = LightTint.NONE

    /** True if the light comes from the side the art was not drawn lit from. */
    var flatten = false

    /** What lit the cat, for the readout: "patch", "sill", a point light's id, "shade", "". */
    var source = ""
}

/**
 * The light of the house at one hour in one weather (docs/specs/CAT_LIGHTING_PLAN.md section 4),
 * and what it does to a cat standing somewhere in it. Room-wide light stays the room's own one
 * multiply over everything (the page's time-of-day tint); this is only the local difference a cat
 * shows against it: brighter and warmer in a window's sun patch or on a sunny sill, cool in a moon
 * patch, warm beside the lamp at night, a tone darker tucked in a box or under a slab, and a faint
 * blue lift for a dark coat at night.
 *
 * Everything a cat's light reads is quantised: the patch's slide with the hour is held on whole
 * pixels with hysteresis ([driftPx]), lights are whole half-steps, and the lamp's strength is one
 * of three steps. [version] changes only when one of those changes, so a mask made from this is
 * rebuilt a few times an hour at most.
 */
internal class HouseLight(private val data: LightData = LightData.shipped) {
    var phase = LightPhase.DAY
        private set

    var weather = Weather.CLEAR
        private set

    /** The window patches' slide from noon, in whole pixels (held). */
    var driftPx = 0
        private set

    /** The lamp's strength step: 0 off, 1 dim, 2 full. */
    var lampStep = 0
        private set

    /** The sun's (and the moon's) strength in this weather, 0..1. */
    var sun = 1.0
        private set

    /** Changes whenever anything above changes. */
    var version = 0
        private set

    private val drift = HeldStep()
    private var known = false

    /** Take the light of [hour] (0..24, local) in [weather]. */
    fun update(hour: Double, weather: Weather) {
        val phase =
            when {
                hour >= data.dayFrom && hour < data.dayUntil -> LightPhase.DAY
                RoomLight.isNight(hour) -> LightPhase.NIGHT
                else -> LightPhase.TWILIGHT
            }
        val px = drift.follow(FloorLight.drift(hour).toDouble())
        val lamp = RoomLight.lampAlpha(RoomLight.tint(hour)) / LAMP_MAX
        val lampStep =
            when {
                lamp >= LAMP_FULL -> 2
                lamp >= LAMP_DIM -> 1
                else -> 0
            }
        val sun = data.sun.getValue(weather)
        if (
            !known ||
                phase != this.phase ||
                px != driftPx ||
                lampStep != this.lampStep ||
                weather != this.weather
        ) {
            version++
        }
        known = true
        this.phase = phase
        this.weather = weather
        this.driftPx = px
        this.lampStep = lampStep
        this.sun = sun
    }

    /**
     * Fill [out] with the light of a cat whose feet are at world ([x], [y]) on surface [surface]
     * (its key, "floor" on the floor), facing left when [flip].
     */
    fun target(x: Float, y: Float, surface: String, flip: Boolean, out: CatTarget) {
        out.halfSteps = 0
        out.tint = LightTint.NONE
        out.flatten = false
        out.source = ""
        var side = 0
        // the window's patch on the floor, and a sill in the window
        val patch =
            if (phase == LightPhase.DAY) data.patchDay
            else if (phase == LightPhase.NIGHT) data.patchNight else null
        if (patch != null && surface == FLOOR) {
            val w = patchWindow(x, y)
            if (w != Int.MIN_VALUE && offer(patch, weathered = true, out)) {
                out.source = "patch"
                side = sideOf(w + LightTuning.GLASS_HALF.toFloat(), x)
            }
        }
        if (surface in data.sillSurfaces) {
            val sill =
                when (phase) {
                    LightPhase.DAY -> data.sillDay
                    LightPhase.TWILIGHT -> data.sillTwilight
                    LightPhase.NIGHT -> data.sillNight
                }
            if (offer(sill, weathered = phase != LightPhase.TWILIGHT, out)) {
                out.source = "sill"
                side = 0 // the window is behind the cat: lit from the front, no side
            }
        }
        // a lamp's pool
        val map = HouseMap.current
        val roomIndex = map.roomOf(x)
        val roomId = map.rooms[roomIndex].id
        for (p in data.points) {
            if (p.room != roomId || map.indexOf(p.room) < 0) continue
            val strength = if (p.whenDark) lampStep else 2
            if (strength == 0) continue
            val d = abs(x - p.x)
            if (d >= p.reach) continue
            var h = p.dose.halfSteps - (if (d >= p.inner) 1 else 0) - (2 - strength)
            if (h <= 0) continue
            h = h.coerceAtMost(p.dose.halfSteps)
            if (h > out.halfSteps) {
                out.halfSteps = h
                out.tint = p.dose.tint
                out.source = p.id
                side = sideOf(p.x.toFloat(), x)
            }
        }
        // tucked away, and the room's own bias
        val shade = data.shade[surface] ?: 0
        if (shade != 0) {
            out.halfSteps += shade
            if (out.source.isEmpty()) out.source = "shade"
        }
        out.halfSteps += data.rooms[roomId] ?: 0
        out.halfSteps =
            out.halfSteps.coerceIn(CatLightKey.MIN_HALF_STEPS, CatLightKey.MAX_HALF_STEPS)
        if (out.halfSteps > 0 && side != 0) {
            // the art is lit from the upper left facing right, from the upper right mirrored
            out.flatten = (side > 0) != flip
        }
        if (out.halfSteps == 0 && out.tint == LightTint.NONE && phase == LightPhase.NIGHT) {
            out.tint = data.nightTint
        }
    }

    /** Takes [dose] (scaled by the weather's sun if [weathered]) if it beats what [out] has. */
    private fun offer(dose: LightDose, weathered: Boolean, out: CatTarget): Boolean {
        val s = if (weathered) sun else 1.0
        val h = floor(dose.halfSteps * s + HALF).toInt()
        if (h <= out.halfSteps) return false
        out.halfSteps = h
        out.tint = if (s >= TINT_SUN) dose.tint else LightTint.NONE
        return true
    }

    /**
     * The window (world x of its sky offset) whose floor patch covers the feet at ([x], [y]), or
     * [Int.MIN_VALUE]. The patch is the four panes' footprint, bars included: a cat is wider than a
     * bar, and must not flick on and off crossing one.
     */
    fun patchWindow(x: Float, y: Float): Int {
        if (y < LightTuning.LIGHT_TOP || y > LightTuning.LIGHT_BOTTOM) return Int.MIN_VALUE
        val lean =
            LightTuning.LIGHT_LEAN * (y - LightTuning.LIGHT_TOP) /
                (LightTuning.LIGHT_BOTTOM - LightTuning.LIGHT_TOP)
        val lit = HouseMap.current.floorLights
        for (w in lit) {
            val left = w + driftPx + LightTuning.LIGHT_LEFT + lean
            val right = w + driftPx + LightTuning.LIGHT_RIGHT + lean
            if (x >= left && x < right) return w
        }
        return Int.MIN_VALUE
    }

    /** -1 a light at [lightX] is left of a cat at [x], 1 right of it, 0 straight above. */
    private fun sideOf(lightX: Float, x: Float): Int {
        val d = lightX - x
        return if (abs(d) < LightTuning.RIM_DEAD_ZONE) 0 else if (d > 0) 1 else -1
    }

    /** A value held on whole steps with hysteresis (as the sprite's [SteadyStep] holds its own). */
    private class HeldStep {
        private var index = 0
        private var fresh = true

        fun follow(v: Double): Int {
            if (fresh || abs(v - index) > HALF + HOLD) index = floor(v + HALF).toInt()
            fresh = false
            return index
        }
    }

    private companion object {
        const val FLOOR = "floor"
        const val HALF = 0.5
        const val HOLD = 0.25
        const val LAMP_MAX = 0.32
        const val LAMP_FULL = 0.6
        const val LAMP_DIM = 0.25

        /** Below this share of the sun a light loses its colour (an overcast patch is grey). */
        const val TINT_SUN = 0.5
    }
}
