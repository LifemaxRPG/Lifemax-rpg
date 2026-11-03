package com.lifemax.rpg.feature.pet.presentation

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.lifemax.rpg.feature.pet.art.RigFrames
import com.lifemax.rpg.feature.pet.art.RigGrid
import com.lifemax.rpg.feature.pet.art.RigSet
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

    /**
     * Read [pose] onto [grid]; [kitten] ears and a pose held still (reduced [motion]) are neutral.
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
}

/**
 * Draws composed rig frames: asks [RigFrames] for the picture of a pose and keeps the pixels in
 * bitmaps, one per cache slot, uploaded only when the slot is filled. The same few poses come round
 * again, so after a short while nothing is composed, uploaded or allocated; the bitmaps are made as
 * the cache fills and then re-used. Used from the main thread only.
 */
internal object RigPainter {
    private val frames = RigFrames()
    private val grid = RigGrid()
    private val bitmaps = arrayOfNulls<Bitmap>(RigFrames.DEFAULT_CAPACITY)
    private val images = arrayOfNulls<ImageBitmap>(RigFrames.DEFAULT_CAPACITY)

    /** The composed picture's size: it is the sheet frame plus the margins (see [RigFrames]). */
    val width: Int
        get() = frames.width

    val height: Int
        get() = frames.height

    /**
     * The picture to draw for [pose] of [rig], or null to draw the sheet's own frame (a cat that is
     * the art as drawn, standing in the art's pose). With [motion] false the ears, tail and
     * whiskers stay as drawn and only the cat's proportions apply.
     */
    fun picture(rig: RigSet, pose: CatPose, motion: Boolean): ImageBitmap? {
        RigMotion.read(pose, rig.kitten, motion, grid)
        val slot = frames.slotFor(rig, pose.clip, pose.frame, grid)
        if (slot < 0) return null
        if (frames.wasComposed || images[slot] == null) upload(slot)
        return images[slot]
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
