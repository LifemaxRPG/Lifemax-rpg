package com.lifemax.rpg.feature.pet.presentation

import com.lifemax.rpg.feature.pet.art.FrameRect
import com.lifemax.rpg.feature.pet.body.Burst
import com.lifemax.rpg.feature.pet.body.Rng
import com.lifemax.rpg.feature.pet.room.RoomArt
import com.lifemax.rpg.feature.pet.room.RoomCanvas
import com.lifemax.rpg.feature.pet.room.RoomImage
import com.lifemax.rpg.feature.pet.room.RoomLayout

/**
 * The hearts, notes and marks a touch makes, rising and gone: the web's `petSpawn` particles. A
 * fixed pool of [CAPACITY] (a burst over a full pool replaces the oldest), so spawning, stepping
 * and painting allocate nothing. Pictures come from the room's own atlas and are drawn centred on
 * their position, in room pixels, over the cats.
 */
internal class TouchParticles(art: RoomArt, seed: Int = 7) {
    private val rects: Array<FrameRect> = Array(Burst.entries.size) { art.item(NAMES[it]) }
    private val rng = Rng(seed)
    private val kind = IntArray(CAPACITY)
    private val x = FloatArray(CAPACITY)
    private val y = FloatArray(CAPACITY)
    private val vx = FloatArray(CAPACITY)
    private val vy = FloatArray(CAPACITY)
    private val age = FloatArray(CAPACITY) { 1f }
    private val life = FloatArray(CAPACITY)
    private var next = 0

    /** How many particles are alive. */
    var count = 0
        private set

    /** Let a [what] rise from room position ([px], [py]). */
    fun spawn(what: Burst, px: Float, py: Float) {
        val i = next
        next = (next + 1) % CAPACITY
        if (age[i] >= life[i]) count++
        kind[i] = what.ordinal
        x[i] = px
        y[i] = py
        age[i] = 0f
        when (what) {
            Burst.HEART,
            Burst.HEART_BIG -> launch(i, rng.range(-SIDE_SPEED, SIDE_SPEED), -RISE, HEART_S)
            Burst.NOTE -> launch(i, NOTE_DRIFT, -NOTE_RISE, NOTE_S)
            Burst.EXCLAIM -> launch(i, 0f, -EXCLAIM_RISE, EXCLAIM_S)
            Burst.SPARKLE ->
                launch(
                    i,
                    rng.range(-SPARK_SIDE, SPARK_SIDE),
                    rng.range(-SPARK_UP, -SPARK_LOW),
                    SPARK_S,
                )
        }
    }

    private fun launch(i: Int, speedX: Float, speedY: Float, seconds: Float) {
        vx[i] = speedX
        vy[i] = speedY
        life[i] = seconds
    }

    /** Move every particle on by [dt] seconds. */
    fun step(dt: Float) {
        if (count == 0) return
        var alive = 0
        for (i in 0 until CAPACITY) {
            if (age[i] >= life[i]) continue
            age[i] += dt
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            if (age[i] < life[i]) alive++
        }
        count = alive
    }

    /** Draw the live ones onto [c] (house pixels). */
    fun paint(c: RoomCanvas) {
        if (count == 0) return
        for (i in 0 until CAPACITY) {
            if (age[i] >= life[i]) continue
            val r = rects[kind[i]]
            val dx = Math.round(x[i] - r.width / 2f) - RoomLayout.HOUSE_X0
            val dy = Math.round(y[i] - r.height / 2f)
            c.image(RoomImage.ATLAS, r.x, r.y, r.width, r.height, dx, dy, r.width, r.height)
        }
    }

    private companion object {
        const val CAPACITY = 24
        val NAMES = arrayOf("heart", "heart_big", "note", "exclaim", "sparkle")

        // the web's petSpawn launches (16-runtime.js): speeds in room pixels a second
        const val SIDE_SPEED = 5f
        const val RISE = 14f
        const val HEART_S = 1.5f
        const val NOTE_DRIFT = 2f
        const val NOTE_RISE = 10f
        const val NOTE_S = 1.4f
        const val EXCLAIM_RISE = 6f
        const val EXCLAIM_S = 0.8f
        const val SPARK_SIDE = 10f
        const val SPARK_UP = 24f
        const val SPARK_LOW = 10f
        const val SPARK_S = 1.2f
    }
}
