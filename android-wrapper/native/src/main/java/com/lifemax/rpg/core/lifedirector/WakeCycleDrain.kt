package com.lifemax.rpg.core.lifedirector

/**
 * LDE-3: the slot arrays the energy model reads (`createGrid`'s typed arrays): per slot its code
 * ([type]), shift load class, painting block ([tag], -1 none), and a placed task's physical and
 * cognitive intensity; whether each painted block is restorative; and what [WakeCycleDrain] writes:
 * the drain before each slot, kept as 32-bit floats as the page's `Float32Array` keeps it, and the
 * free runs from and up to each slot, as 16-bit integers.
 */
class EnergyGrid(size: Int) {
    val type = IntArray(size)
    val load = IntArray(size)
    val tag = IntArray(size) { -1 }
    val taskPhysical = IntArray(size)
    val taskCognitive = IntArray(size)
    val drainPhysical = FloatArray(size)
    val drainCognitive = FloatArray(size)
    val runAhead = ShortArray(size)
    val runBack = ShortArray(size)
    var restorative: List<Boolean> = emptyList()
}

/** LDE-3 `recomputeBwc`: one waking day's drain and free runs. */
object WakeCycleDrain {
    private const val SLOTS_PER_HOUR = 4
    private const val TASK_DRAIN = 0.015
    private const val RECOVERY_RELIEF = 0.06

    /**
     * For each slot from [startSlot] up to [endSlot]: the drain so far is stored, then grows by the
     * shift's hourly drain (by its load class, from the page's `LOAD_DRAIN`) for a commitment slot
     * with a load, by `0.015 * intensity` per hour for a task or kept session, or falls by 0.06 per
     * hour (never below 0) for a recovery slot or a restorative maintenance or commute block; each
     * a quarter of the hourly figure per slot, summed in doubles. Then each slot's free run ahead
     * and back: consecutive free slots at or after [nowSlot] within the day. A load class without a
     * table entry, or a tag naming no block, stops the page with a `TypeError`.
     */
    fun recompute(g: EnergyGrid, nowSlot: Double, startSlot: Int, endSlot: Int) {
        val drain = DemandTables.packaged.loadDrain
        var physical = 0.0
        var cognitive = 0.0
        for (i in startSlot until endSlot) {
            g.drainPhysical[i] = physical.toFloat()
            g.drainCognitive[i] = cognitive.toFloat()
            val code = g.type[i]
            if (code == SlotCode.HARD.code && g.load[i] != 0) {
                val r = drain[g.load[i]] ?: throw PageTypeError("LOAD_DRAIN[load] is undefined")
                physical += r.first / SLOTS_PER_HOUR
                cognitive += r.second / SLOTS_PER_HOUR
            } else if (code == SlotCode.TASK.code || code == SlotCode.LOCKED.code) {
                physical += TASK_DRAIN * g.taskPhysical[i] / SLOTS_PER_HOUR
                cognitive += TASK_DRAIN * g.taskCognitive[i] / SLOTS_PER_HOUR
            } else if (code == SlotCode.RECOVERY.code || (restorativeBlock(g, i, code))) {
                physical = maxOf(0.0, physical - RECOVERY_RELIEF / SLOTS_PER_HOUR)
                cognitive = maxOf(0.0, cognitive - RECOVERY_RELIEF / SLOTS_PER_HOUR)
            }
        }
        var run = 0
        for (i in endSlot - 1 downTo startSlot) {
            run = if (free(g, i, nowSlot)) run + 1 else 0
            g.runAhead[i] = run.toShort()
        }
        run = 0
        for (i in startSlot until endSlot) {
            run = if (free(g, i, nowSlot)) run + 1 else 0
            g.runBack[i] = run.toShort()
        }
    }

    private fun restorativeBlock(g: EnergyGrid, i: Int, code: Int): Boolean {
        if (code != SlotCode.MAINT.code && code != SlotCode.COMMUTE.code) return false
        val tag = g.tag[i]
        if (tag < 0) return false
        return g.restorative.getOrNull(tag) ?: throw PageTypeError("blocks[tag] is undefined")
    }

    private fun free(g: EnergyGrid, i: Int, nowSlot: Double): Boolean =
        g.type[i] == SlotCode.FREE.code && i.toDouble() >= nowSlot
}
