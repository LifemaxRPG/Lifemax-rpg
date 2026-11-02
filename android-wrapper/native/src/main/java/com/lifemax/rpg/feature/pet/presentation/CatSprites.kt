package com.lifemax.rpg.feature.pet.presentation

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.lifemax.rpg.feature.pet.art.ArgbImage
import com.lifemax.rpg.feature.pet.art.IndexSheet
import com.lifemax.rpg.feature.pet.art.PageCoatSource
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
 *
 * The four legacy coats come with the app. A genetic (`dna_...`) coat is composed by the page: its
 * sheets are fetched through [coats] (the `coat` command), side view first, then the depth view,
 * and [revision] changes whenever something arrives, so a caller that got null asks again. Null
 * while a sheet is on its way, and for good if the page refuses it or sends one the sprite tables
 * cannot use: the still portrait stays.
 */
internal object CatArt {
    /** The page's genetic coats; the plugin connects it to the bridge ([PageCoatSource.bind]). */
    val coats = PageCoatSource()

    /** Changes each time a sheet or a refusal arrives from the page. */
    var revision by mutableIntStateOf(0)
        private set

    private val bump = Any()
    private var art: Result<PetArt>? = null
    private var key: Pair<String, Int>? = null
    private var tables: Tables? = null
    private var side: ImageBitmap? = null
    private var depth: ImageBitmap? = null
    private var built: CatSprites? = null

    init {
        coats.onChange { synchronized(bump) { revision += 1 } }
    }

    /** Where every clip's frames are, for a growth stage (the same for any coat). */
    private class Tables(
        val frameWidth: Int,
        val frameHeight: Int,
        val rows: IntArray,
        val frames: IntArray,
        val feet: IntArray,
        val onDepth: BooleanArray,
    ) {
        /** True if every clip on this kind of sheet lies inside [sheet]. */
        fun fits(sheet: IndexSheet, depthRows: Boolean): Boolean =
            rows.indices.all {
                onDepth[it] != depthRows ||
                    ((rows[it] + 1) * frameHeight <= sheet.height &&
                        frames[it] * frameWidth <= sheet.width)
            }
    }

    /**
     * The sprites of [coat] at kitten [stage] (-1 grown), or null if native cannot draw it (yet). A
     * null is never remembered: ask again when [revision] has changed.
     */
    @Synchronized
    fun sprites(coat: String, stage: Int): CatSprites? {
        val loaded =
            (art ?: runCatching { PetArtResources.load(coats) }.also { art = it }).getOrNull()
                ?: return null
        if (key != coat to stage) {
            key = coat to stage
            tables = null
            side = null
            depth = null
            built = null
        }
        return try {
            assemble(loaded, coat, stage)
        } catch (e: RuntimeException) {
            // a sheet that does not decode: for a page coat that is a refusal, for good
            SheetKind.of(stage, false).let { coats.reject(coat, it) }
            null
        }
    }

    private fun assemble(art: PetArt, coat: String, stage: Int): CatSprites? {
        val t = tables ?: tablesFor(art, stage)?.also { tables = it } ?: return null
        if (side == null) {
            val kind = SheetKind.of(stage, false)
            val sheet = art.sheet(coat, kind) ?: return null
            val palette = art.palette(coat) ?: return null
            if (!t.fits(sheet, depthRows = false)) {
                coats.reject(coat, kind)
                return null
            }
            side = bitmap(Recolour.sheet(sheet, palette))
        }
        if (depth == null) {
            // the depth rows come right after the side view: the walk toward and away needs them at
            // any moment, and a reply takes longer than the cat takes to turn
            val kind = SheetKind.of(stage, true)
            val sheet = art.sheet(coat, kind)
            val palette = art.palette(coat)
            if (sheet != null && palette != null) {
                if (t.fits(sheet, depthRows = true)) {
                    depth = bitmap(Recolour.sheet(sheet, palette))
                    built = null
                } else {
                    coats.reject(coat, kind)
                }
            }
        }
        return built
            ?: CatSprites(
                    t.frameWidth,
                    t.frameHeight,
                    side!!,
                    depth,
                    t.rows,
                    t.frames,
                    t.feet,
                    t.onDepth,
                )
                .also { built = it }
    }

    private fun tablesFor(art: PetArt, stage: Int): Tables? {
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
        return Tables(art.anims.frameWidth, art.anims.frameHeight, rows, frames, feet, onDepth)
    }

    private fun bitmap(image: ArgbImage): ImageBitmap =
        Bitmap.createBitmap(image.pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
            .asImageBitmap()
}
