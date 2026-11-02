package com.lifemax.rpg.feature.pet.presentation

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.lifemax.rpg.feature.pet.art.ArgbImage
import com.lifemax.rpg.feature.pet.art.PetArt
import com.lifemax.rpg.feature.pet.art.PetArtResources
import com.lifemax.rpg.feature.pet.art.Recolour
import com.lifemax.rpg.feature.pet.art.SheetKind
import com.lifemax.rpg.feature.pet.body.Clips

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
) {
    /** The sheet [clip] is on, or null when this cat has no such sheet (draw nothing). */
    fun sheet(clip: Int): ImageBitmap? = if (onDepthSheet[clip]) depth else side

    /** Sheet pixel row of the top of [clip]'s frames. */
    fun top(clip: Int): Int = rows[clip] * frameHeight

    /** The frame to show for a pose's frame number: wraps like the web's `% n`. */
    fun frame(clip: Int, frame: Int): Int = Math.floorMod(frame, frames[clip])

    /** Pixel row of the paws inside a frame (the cat is anchored by it). */
    fun feet(clip: Int): Int = feet[clip]
}

/**
 * The cat art for the app: loaded once, off the main thread, from the shipped resources, and the
 * sprites of the cat last asked for are kept (a card shows one cat). If the art cannot be loaded at
 * all that is remembered and every ask answers null, so callers fall back to the still portrait.
 */
internal object CatArt {
    private var art: Result<PetArt>? = null
    private var lastKey: Triple<String, Int, Boolean>? = null
    private var last: CatSprites? = null

    /** The sprites of [coat] at kitten [stage] (-1 grown), or null if native cannot draw it. */
    @Synchronized
    fun sprites(coat: String, stage: Int): CatSprites? {
        val key = Triple(coat, stage, true)
        if (key == lastKey) return last
        val loaded = (art ?: runCatching { PetArtResources.load() }.also { art = it }).getOrNull()
        val built = loaded?.let { runCatching { build(it, coat, stage) }.getOrNull() }
        lastKey = key
        last = built
        return built
    }

    private fun build(art: PetArt, coat: String, stage: Int): CatSprites? {
        val palette = art.palette(coat) ?: return null
        val side = art.sheet(coat, SheetKind.of(stage, false)) ?: return null
        val depth = art.sheet(coat, SheetKind.of(stage, true))
        val clips = Clips.all
        val rows = IntArray(clips.size)
        val frames = IntArray(clips.size)
        val feet = IntArray(clips.size)
        val onDepth = BooleanArray(clips.size)
        for (clip in clips) {
            val anim = art.growth.resolve(stage, clip.name, art.anims) ?: return null
            rows[clip.id] = anim.row
            frames[clip.id] = anim.frames
            feet[clip.id] = anim.bottom
            onDepth[clip.id] = anim.depth
        }
        return CatSprites(
            art.anims.frameWidth,
            art.anims.frameHeight,
            bitmap(Recolour.sheet(side, palette)),
            depth?.let { bitmap(Recolour.sheet(it, palette)) },
            rows,
            frames,
            feet,
            onDepth,
        )
    }

    private fun bitmap(image: ArgbImage): ImageBitmap =
        Bitmap.createBitmap(image.pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
            .asImageBitmap()
}
