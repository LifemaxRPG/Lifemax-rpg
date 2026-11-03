package com.lifemax.rpg.feature.pet.body

/**
 * Fill the pose's ground shadow: under ([x], [row]) at [halfWidth] art pixels, smaller and fainter
 * the higher the cat is [lift] pixels off the ground.
 */
internal fun CatPose.setShadow(x: Float, row: Float, lift: Float, halfWidth: Float) {
    val units = lift * Tuning.Shadow.UNITS_PER_PX
    shadowX = x
    shadowY = row
    shadowScale =
        maxOf(Tuning.Shadow.MIN_SCALE, depthScale - units * Tuning.Shadow.SCALE_LOSS_PER_UNIT)
    val fade = Mathx.clamp(lift / Tuning.Shadow.FADE_HEIGHT_PX, 0f, 1f) * Tuning.Shadow.FADE_SHARE
    shadowAlpha = Tuning.Shadow.ALPHA * (1f - fade)
    shadowHalfWidth = halfWidth
}

/**
 * Interrupts happen between steps; they are held here and reported at the start of the next. A
 * fixed buffer, so noting one allocates nothing.
 */
internal class EarlyInterrupts {
    private val plans = IntArray(CAPACITY)
    private val indexes = IntArray(CAPACITY)
    private var count = 0

    /** Remember an interrupt of plan [plan] at step [index] (dropped when the buffer is full). */
    fun note(plan: Int, index: Int) {
        if (count == CAPACITY) return
        plans[count] = plan
        indexes[count] = index
        count++
    }

    /** Report every noted interrupt into [events] and forget them. */
    fun flush(events: CatEvents) {
        for (i in 0 until count) events.add(CatEventType.Interrupted, plans[i], indexes[i], false)
        count = 0
    }

    private companion object {
        const val CAPACITY = 4
    }
}
