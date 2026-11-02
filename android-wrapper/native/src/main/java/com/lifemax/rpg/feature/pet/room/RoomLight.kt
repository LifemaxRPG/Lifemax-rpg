package com.lifemax.rpg.feature.pet.room

import kotlin.math.floor
import kotlin.math.max

/**
 * The room's light through the day, copied from the web room (`PET_SKY`, `petSkyAt` and `petTint`
 * in 16-runtime.js): the colours of the sky at an hour and the multiply tint laid over the whole
 * house. The arithmetic is done in doubles with JavaScript's rounding, because the page's own
 * pictures are the reference and a colour that is one off at every half step would show.
 *
 * Nothing here allocates: results are written into a caller's `IntArray` as `0xRRGGBB`.
 */
internal object RoomLight {
    /** A sky key: from this hour on, a top colour and a bottom colour (`0xRRGGBB`). */
    private class Key(val hour: Double, val top: Int, val bottom: Int)

    private val sky =
        arrayOf(
            Key(0.0, 0x0A0E2C, 0x1C265C),
            Key(5.0, 0x161A46, 0x604678),
            Key(6.2, 0x465AAA, 0xF6A078),
            Key(8.0, 0x68AAE8, 0xCEEAFF),
            Key(12.0, 0x58A6E8, 0xBEE4FF),
            Key(17.0, 0x6EA0E2, 0xFFDAA0),
            Key(18.8, 0x383880, 0xFF8A5A),
            Key(20.5, 0x161A48, 0x543C78),
            Key(22.0, 0x0A0E2C, 0x1C265C),
            Key(24.0, 0x0A0E2C, 0x1C265C),
        )

    /** Index of the top colour in the pair [skyAt] fills in; the bottom colour is index 1. */
    const val TOP = 0
    const val BOTTOM = 1

    /** True when the room is lit as night: stars are bright, no clouds, a moon instead of a sun. */
    fun isNight(hour: Double): Boolean = hour >= NIGHT_FROM || hour < NIGHT_UNTIL

    /**
     * The sky at [hour] (0 to 24): `out[TOP]` and `out[BOTTOM]` as `0xRRGGBB`. The two keys either
     * side of the hour are mixed channel by channel, rounded half up.
     */
    fun skyAt(hour: Double, out: IntArray) {
        var a = sky[0]
        var b = sky[1]
        for (i in 0 until sky.size - 1) {
            if (hour >= sky[i].hour && hour <= sky[i + 1].hour) {
                a = sky[i]
                b = sky[i + 1]
                break
            }
        }
        val t = (hour - a.hour) / max(MIN_SPAN, b.hour - a.hour)
        out[TOP] = mix(a.top, b.top, t)
        out[BOTTOM] = mix(a.bottom, b.bottom, t)
    }

    /**
     * Colour of one of the six bands a window's sky is cut into: the sky at [hour], [band] 0 (top)
     * to 5 (bottom).
     */
    fun skyBand(top: Int, bottom: Int, band: Int): Int = mix(top, bottom, band / (BANDS - 1.0))

    /** Number of bands in a window's sky. */
    const val BANDS = 6

    /**
     * The multiply tint at [hour] as `0xRRGGBB`: white by day, warm at dusk, blue-grey at night.
     */
    fun tint(hour: Double): Int {
        val r: Int
        val g: Int
        val b: Int
        when {
            hour >= 7.5 && hour < 17.0 -> return WHITE
            hour >= 17.0 && hour < 19.5 -> return DUSK
            hour >= 19.5 && hour < 21.5 -> {
                val t = (hour - 19.5) / 2.0
                r = jsRound(255.0 - t * 120.0)
                g = jsRound(226.0 - t * 98.0)
                b = jsRound(200.0 - t * 20.0)
            }
            hour >= 21.5 || hour < 5.0 -> return NIGHT
            hour >= 5.0 && hour < 7.5 -> {
                val t = (hour - 5.0) / 2.5
                r = jsRound(128.0 + t * 127.0)
                g = jsRound(138.0 + t * 117.0)
                b = jsRound(196.0 + t * 59.0)
            }
            else -> return WHITE
        }
        return (r shl 16) or (g shl 8) or b
    }

    /** True when the tint darkens the room enough for the page to draw it (and the lamp glow). */
    fun tinted(tint: Int): Boolean = ((tint shr 16) and 255) < 250 || ((tint shr 8) and 255) < 250

    /**
     * Alpha of the living room's lamp glow under [tint], 0 to 0.32: the darker and bluer the room,
     * the brighter the lamp.
     */
    fun lampAlpha(tint: Int): Double {
        val g = (tint shr 8) and 255
        val b = tint and 255
        return minOf(LAMP_MAX, (255 - b + 255 - g) / LAMP_DIVISOR)
    }

    /**
     * Hour of day, `0.0` to `24.0`, from a clock reading in local time ([localMillis] since
     * midnight UTC-as-local).
     */
    fun hourOf(localMillis: Long): Double {
        val day = Math.floorMod(localMillis, MILLIS_PER_DAY)
        return day / MILLIS_PER_HOUR
    }

    private fun mix(x: Int, y: Int, t: Double): Int {
        val r = channel(x shr 16, y shr 16, t)
        val g = channel(x shr 8, y shr 8, t)
        val b = channel(x, y, t)
        return (r shl 16) or (g shl 8) or b
    }

    private fun channel(x: Int, y: Int, t: Double): Int {
        val a = x and 255
        val b = y and 255
        return jsRound(a + (b - a) * t)
    }

    /** JavaScript's `Math.round`: halves go up. */
    fun jsRound(v: Double): Int = floor(v + 0.5).toInt()

    const val WHITE = 0xFFFFFF
    private const val DUSK = 0xFFE2C8
    private const val NIGHT = 0x808AC4
    private const val NIGHT_FROM = 19.5
    private const val NIGHT_UNTIL = 5.5
    private const val MIN_SPAN = 0.001
    private const val LAMP_MAX = 0.32
    private const val LAMP_DIVISOR = 420.0
    private const val MILLIS_PER_HOUR = 3_600_000.0
    private const val MILLIS_PER_DAY = 86_400_000L
}
