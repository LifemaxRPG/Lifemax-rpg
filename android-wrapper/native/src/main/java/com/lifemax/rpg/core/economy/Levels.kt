package com.lifemax.rpg.core.economy

import kotlin.math.max
import kotlin.math.min

/** Where a running total sits on a level curve (CURVE-2). */
data class LevelProgress(val level: Int, val into: Double, val needed: Double, val pct: Double)

/** The two curves of section 4. */
enum class Curve(val step: (Int) -> Double) {
    /** CURVE-1: attributes and skills. */
    Attribute({ level -> 100.0 + 25.0 * (level - 1) }),

    /** CURVE-3: the character. */
    Character({ level -> Eco.CHAR_BASE + Eco.CHAR_SLOPE * (level - 1) });

    /** CURVE-2. A NaN total stays at level 1 with NaN progress. */
    fun progress(total: Double): LevelProgress {
        var level = 1
        var left = max(0.0, total)
        while (left >= step(level) && level < MAX_LEVEL) {
            left -= step(level)
            level += 1
        }
        return LevelProgress(level, left, step(level), min(100.0, left / step(level) * 100))
    }

    companion object {
        const val MAX_LEVEL = 1000
    }
}

/** The curves' other uses: the character's cumulative XP, dev boosts, quest stage locks. */
object Levels {
    /** CURVE-4: the XP that reaches character level [level]. */
    fun characterCumulative(level: Int): Double = 37.5 * level * level + 87.5 * level - 125

    /** CURVE-8: the XP that lifts an attribute holding [total] by [levels] levels. */
    fun xpToGain(total: Double, levels: Int): Double {
        val now = Curve.Attribute.progress(max(0.0, total.orZero))
        var need = max(0.0, now.needed - now.into)
        var level = now.level + 1
        repeat(levels - 1) {
            need += Curve.Attribute.step(level)
            level += 1
        }
        return max(1.0, need)
    }

    /** CURVE-9: the character level a quest stage needs; stages without an entry need level 1. */
    fun stageLevel(stage: Int?): Int = STAGE_LEVELS.getOrNull(stage ?: -1)?.takeIf { it > 0 } ?: 1

    private val STAGE_LEVELS = listOf(0, 1, 3, 5, 8, 12)
}
