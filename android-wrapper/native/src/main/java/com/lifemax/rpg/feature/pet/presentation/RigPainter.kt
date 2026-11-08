package com.lifemax.rpg.feature.pet.presentation

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.lifemax.rpg.feature.pet.art.RigFrames
import com.lifemax.rpg.feature.pet.art.RigGrid
import com.lifemax.rpg.feature.pet.body.CatPose
import com.lifemax.rpg.feature.pet.body.Tuning

/**
 * How the body's springs become the rig's pose. The body already moves ears, tail and whiskers on
 * springs ([CatPose.ear], [CatPose.earFlat], [CatPose.tail], [CatPose.whisker]); the rig draws
 * them. The brain's discrete states are the springs' targets, so a state change shows as a swing
 * that overshoots and settles; here only the springs' deviation from the art's own pose is read,
 * since every frame of the art already carries a neutral ear and tail.
 */
internal object RigMotion {
    /** Share of the tail root's deviation that is drawn: the art's tail is not a rigid stick. */
    const val TAIL_GAIN = 0.5

    /** Share of the tip segments' deviation drawn as a bend that grows toward the tip. */
    const val CURL_GAIN = 0.8

    /** Whisker length change per unit of whisker sweep from rest (-1 back, 1 forward). */
    const val WHISKER_GAIN = 0.35

    /** The axes of [values], in order. */
    const val EAR_LEFT = 0
    const val EAR_RIGHT = 1
    const val FLAT = 2
    const val TAIL = 3
    const val CURL = 4
    const val WHISKER = 5

    /** How many axes [values] fills. */
    const val AXES = 6

    /**
     * Read [pose] onto [grid] (each axis rounded to its nearest grid step); [kitten] ears and a
     * pose held still (reduced [motion]) are neutral.
     */
    fun read(pose: CatPose, kitten: Boolean, motion: Boolean, grid: RigGrid) {
        if (!motion) {
            grid.clear()
            return
        }
        val earsMove = !kitten
        var tip = 0f
        for (i in 1 until pose.tail.size) tip += pose.tail[i]
        grid.set(
            earLeftDeg = if (earsMove) Math.toDegrees(pose.ear[0].toDouble()) else 0.0,
            earRightDeg = if (earsMove) Math.toDegrees(pose.ear[1].toDouble()) else 0.0,
            flat = if (earsMove) pose.earFlat.toDouble() else 0.0,
            tailDeg =
                Math.toDegrees((pose.tail[0] - Tuning.Tail.BASE_NEUTRAL).toDouble()) * TAIL_GAIN,
            curlDeg = Math.toDegrees((tip - Tuning.Tail.CURL_NEUTRAL).toDouble()) * CURL_GAIN,
            whiskerScale = 1.0 + (pose.whisker - Tuning.Whiskers.REST) * WHISKER_GAIN,
        )
    }

    /**
     * The same reading as [read], before rounding: each axis in grid steps (a value of 1.4 is 1.4
     * steps of [RigGrid]), into [out] at the indices above. [SpriteSteady] holds them steady.
     */
    fun values(pose: CatPose, kitten: Boolean, out: DoubleArray) {
        val earsMove = !kitten
        var tip = 0f
        for (i in 1 until pose.tail.size) tip += pose.tail[i]
        out[EAR_LEFT] =
            if (earsMove) Math.toDegrees(pose.ear[0].toDouble()) / RigGrid.EAR_STEP_DEG else 0.0
        out[EAR_RIGHT] =
            if (earsMove) Math.toDegrees(pose.ear[1].toDouble()) / RigGrid.EAR_STEP_DEG else 0.0
        out[FLAT] = if (earsMove) pose.earFlat.toDouble() * RigGrid.FLAT_STEPS else 0.0
        out[TAIL] =
            Math.toDegrees((pose.tail[0] - Tuning.Tail.BASE_NEUTRAL).toDouble()) * TAIL_GAIN /
                RigGrid.TAIL_STEP_DEG
        out[CURL] =
            Math.toDegrees((tip - Tuning.Tail.CURL_NEUTRAL).toDouble()) * CURL_GAIN /
                RigGrid.CURL_STEP_DEG
        out[WHISKER] = (pose.whisker - Tuning.Whiskers.REST) * WHISKER_GAIN / RigGrid.WHISKER_STEP
    }
}

/**
 * Keeps the composed rig frames of [RigFrames] as bitmaps, one per cache slot, uploaded only when
 * the slot is filled. The same few poses come round again, so after a short while nothing is
 * composed, uploaded or allocated; the bitmaps are made as the cache fills and then re-used. Which
 * slot a cat draws is [SpritePlan]'s decision. Used from the main thread only.
 */
internal object RigPainter {
    /** The composed frames every cat on screen shares. */
    val frames = RigFrames()

    private val bitmaps = arrayOfNulls<Bitmap>(RigFrames.DEFAULT_CAPACITY)
    private val images = arrayOfNulls<ImageBitmap>(RigFrames.DEFAULT_CAPACITY)

    /**
     * The picture of [slot] of [frames]; [fresh] when the slot was just composed (its bitmap is out
     * of date).
     */
    fun image(slot: Int, fresh: Boolean): ImageBitmap {
        if (fresh || images[slot] == null) upload(slot)
        return images[slot]!!
    }

    private fun upload(slot: Int) {
        val bitmap =
            bitmaps[slot]
                ?: Bitmap.createBitmap(frames.width, frames.height, Bitmap.Config.ARGB_8888).also {
                    bitmaps[slot] = it
                    images[slot] = it.asImageBitmap()
                }
        bitmap.setPixels(frames.pixels(slot), 0, frames.width, 0, 0, frames.width, frames.height)
    }
}
