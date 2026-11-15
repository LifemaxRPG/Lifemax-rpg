package com.lifemax.rpg.feature.habits.presentation.scene

import com.lifemax.rpg.feature.habits.domain.Activity
import com.lifemax.rpg.feature.habits.domain.Looks
import com.lifemax.rpg.feature.habits.domain.PeopleActivities
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The people's pre-rendered frames: every (look, activity, frame) is a small image, made on first
 * use from the rig ([PeopleActivities.figure]) and lit by [PeopleRaster]. All the frames of an
 * activity share one box (the union of its reach over every look and frame, so nobody jitters
 * between frames), cut as tight as the activity allows. Pure: it makes pixels; [SceneSprites] turns
 * them into images, keeps the pool and counts its bytes.
 */
internal object PeopleSprites {
    /**
     * Pixels to a unit: a little under what a phone shows at the focus camera (about 2.6 to 3.6);
     * the glow is soft, so it stands being drawn larger.
     */
    const val PPU = 1.9f

    /** Pixels left round a figure for its glow. */
    private const val PAD = 3

    /** One activity's frame box: its top-left from the feet's middle, in pixels, and its size. */
    class Box(val left: Int, val top: Int, val w: Int, val h: Int)

    private val boxes = arrayOfNulls<Box>(Activity.entries.size)

    fun box(a: Activity): Box =
        boxes[a.ordinal]
            ?: run {
                val r = PeopleActivities.reach(a)
                val left = floor(r[0] * PPU).toInt() - PAD
                val top = floor(r[1] * PPU).toInt() - PAD
                val right = ceil(r[2] * PPU).toInt() + PAD
                val bottom = ceil(r[3] * PPU).toInt() + PAD
                Box(left, top, right - left, bottom - top).also { boxes[a.ordinal] = it }
            }

    /** The frame [frame] of [a] for [look]. */
    fun render(look: Int, a: Activity, frame: Int): Sprite {
        val spec = PeopleActivities.of(a)
        val b = box(a)
        val fig = PeopleActivities.figure(look, a, frame)
        val shapes = fig.shapes()
        return PeopleRaster.figure(
            shapes,
            b.w,
            b.h,
            PPU,
            -b.left.toFloat(),
            -b.top.toFloat(),
            spec.glows,
        )
    }

    /** What every frame of [a] takes, for every look, in bytes. */
    fun bytes(a: Activity): Int {
        val b = box(a)
        return b.w * b.h * 4 * PeopleActivities.of(a).frames * Looks.COUNT
    }

    /** What the whole atlas would take if every frame of every look were made, in bytes. */
    fun worstCaseBytes(): Int = Activity.entries.sumOf { bytes(it) }
}
