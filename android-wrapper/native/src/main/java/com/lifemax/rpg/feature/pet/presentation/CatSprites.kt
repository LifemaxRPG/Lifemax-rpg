package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.ui.graphics.ImageBitmap
import com.lifemax.rpg.feature.pet.art.CatLightKey
import com.lifemax.rpg.feature.pet.art.RigSet

/**
 * One cat's sprite sheets, recoloured once and ready to draw: the side-view sheet and the depth
 * sheet of its coat and growth stage, plus, for every clip of the body, where its frames are.
 * Looking a frame up is array indexing, so drawing allocates nothing.
 */
internal class CatSprites(
    val frameWidth: Int,
    val frameHeight: Int,
    private val side: ImageBitmap,
    private val depth: ImageBitmap?,
    private val rows: IntArray,
    private val frames: IntArray,
    private val feet: IntArray,
    private val onDepthSheet: BooleanArray,
    /** The rig that moves this cat's ears, tail and whiskers, or null: draw the sheet as it is. */
    val rig: RigSet? = null,
    /**
     * The cat's sheet rows lit by a light key (docs/specs/CAT_LIGHTING_PLAN.md WP-L3), or null: the
     * cat is always drawn as it is.
     */
    val lit: LitSheets? = null,
) {
    /** The same sprites with [rig] (or none): sheets and tables are shared. */
    fun withRig(rig: RigSet?) =
        CatSprites(frameWidth, frameHeight, side, depth, rows, frames, feet, onDepthSheet, rig, lit)

    /** The sheet [clip] is on, or null when this cat has no such sheet (draw nothing). */
    fun sheet(clip: Int): ImageBitmap? = if (onDepthSheet[clip]) depth else side

    /**
     * The row of [clip]'s frames lit by [light], its top at picture row 0; null for the neutral
     * light or when it cannot be had (then [sheet] at [top] is drawn, as always).
     */
    fun litRow(clip: Int, light: Int): ImageBitmap? =
        if (light == CatLightKey.NEUTRAL) null else lit?.image(onDepthSheet[clip], top(clip), light)

    /** Sheet pixel row of the top of [clip]'s frames. */
    fun top(clip: Int): Int = rows[clip] * frameHeight

    /** The frame to show for a pose's frame number: wraps like the web's `% n`. */
    fun frame(clip: Int, frame: Int): Int = Math.floorMod(frame, frames[clip])

    /** Pixel row of the paws inside a frame (the cat is anchored by it). */
    fun feet(clip: Int): Int = feet[clip]
}
