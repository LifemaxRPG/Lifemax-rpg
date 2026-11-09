package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.PageValues.clamp
import com.lifemax.rpg.core.save.JsonValue

/**
 * LDG-3: what fills a slot, the page's codes (`SLOT`, `engine.js:22-25`). A lower code is stronger:
 * painting never covers a stronger code. [pageName] is the page's long name (`SLOT_NAMES`).
 */
enum class SlotCode(val code: Int, val pageName: String) {
    FREE(0, "FREE"),
    EMERGENCY(1, "UNAVAILABLE_EMERGENCY"),
    HARD(2, "HARD_COMMITMENT"),
    COMMUTE(3, "COMMUTE_AND_TRANSITION"),
    SLEEP(4, "PROTECTED_SLEEP"),
    MAINT(5, "ESSENTIAL_MAINTENANCE"),
    LOCKED(6, "USER_LOCKED_ACTIVITY"),
    RECOVERY(7, "RECOVERY_PROTECTED"),
    PERSONAL(8, "PERSONAL_UNAVAILABLE"),
    TASK(9, "SCHEDULED_TASK"),
}

/**
 * LDG-3: what a painted block says about itself. [kind] and [title] are kept as given (a
 * commitment's are stored values); the others are set only where the page sets them.
 */
data class BlockMeta(
    val kind: JsonValue? = null,
    val title: JsonValue? = null,
    val refId: String? = null,
    val load: Int? = null,
    val restorative: Boolean? = null,
    val protected: Boolean? = null,
)

/** LDG-3: one call of [AvailabilityGrid.paint] that reached the grid, slot-aligned. */
data class GridBlock(
    val code: Int,
    val startEpoch: Double,
    val endEpoch: Double,
    val meta: BlockMeta?,
)

/**
 * LDG-3, LDG-4: the plan's slots (LDT-16) with what availability paints into them: [type] the slot
 * code of each slot, [tag] the index in [blocks] of the block that painted it (-1 none), [load] the
 * shift load class of a commitment slot, [wakeCycle] the index of the waking day it belongs to (-1
 * none), [preferred] whether a PREFERRED rule covers it. Slot numbers are doubles, as the page's
 * arithmetic gives them (a NaN slot paints nothing).
 */
class AvailabilityGrid(val slots: SlotGrid) {
    private val size = slots.count.toInt()

    val type = IntArray(size)
    val load = IntArray(size)
    val tag = IntArray(size) { NONE }
    val wakeCycle = IntArray(size) { NONE }
    val preferred = BooleanArray(size)
    val blocks = ArrayList<GridBlock>()

    /** The number of slots, as a double for slot arithmetic. */
    val count: Double
        get() = slots.count

    /** The code in slot [slot] (a whole number inside the grid). */
    fun codeAt(slot: Double): Int = type[slot.toInt()]

    /**
     * LDG-3 `paint`: the slots from [startEpoch] (rounded down to a slot) to [endEpoch] (rounded
     * up), cut to the grid. When that leaves at least one slot (or a NaN bound, which is never
     * "empty") a block is recorded, slot-aligned, even if it writes nothing; each slot that is FREE
     * or holds a weaker code takes [code] and the block's index, and for [SlotCode.HARD] with a
     * truthy load also that load. Returns the block's index when any slot was written, else null.
     */
    fun paint(startEpoch: Double, endEpoch: Double, code: Int, meta: BlockMeta?): Int? {
        val a = clamp(slots.slotAt(startEpoch), 0.0, count)
        val b = clamp(slots.slotAtOrAfter(endEpoch), 0.0, count)
        if (b <= a) return null
        val index = blocks.size
        blocks += GridBlock(code, slots.epochAt(a), slots.epochAt(b), meta)
        var wrote = 0
        var i = a
        while (i < b) {
            val k = i.toInt()
            val current = type[k]
            if (current == SlotCode.FREE.code || code < current) {
                type[k] = code
                tag[k] = index
                wrote++
                val shift = meta?.load
                if (code == SlotCode.HARD.code && shift != null && shift != 0) load[k] = shift
            }
            i += 1
        }
        return if (wrote > 0) index else null
    }

    /** [paint] with a slot code. */
    fun paint(startEpoch: Double, endEpoch: Double, code: SlotCode, meta: BlockMeta): Int? =
        paint(startEpoch, endEpoch, code.code, meta)

    /** The number of FREE slots from [from] up to [until]. */
    fun freeBetween(from: Double, until: Double): Int {
        var free = 0
        var i = from
        while (i < until) {
            if (type[i.toInt()] == SlotCode.FREE.code) free++
            i += 1
        }
        return free
    }

    companion object {
        /** No block or no waking day. */
        const val NONE = -1
    }
}
