package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.lifemax.rpg.feature.pet.art.IndexSheet
import com.lifemax.rpg.feature.pet.art.PageCoatSource
import com.lifemax.rpg.feature.pet.art.PetArt
import com.lifemax.rpg.feature.pet.art.PetArtResources
import com.lifemax.rpg.feature.pet.art.Recolour
import com.lifemax.rpg.feature.pet.art.SheetKind
import com.lifemax.rpg.feature.pet.body.Clips
import com.lifemax.rpg.feature.pet.room.RoomArt

/** The room's art and the bitmaps made from it, loaded together. */
internal class RoomKit(val art: RoomArt) {
    val bitmaps = RoomBitmaps(art)
}

/**
 * The cat art for the app: loaded once, off the main thread, from the shipped resources. The
 * sprites of the cats last asked for are kept (a card shows up to two cats, and the second may be a
 * different coat and growth stage). If the art cannot be loaded at all that is remembered and every
 * ask answers null, so callers fall back to the still portrait.
 *
 * The four legacy coats come with the app. A genetic (`dna_...`) coat is composed by the page: its
 * sheets are fetched through [coats] (the `coat` command), side view first, then the depth view,
 * and [revision] changes whenever something arrives, so a caller that got null asks again. Null
 * while a sheet is on its way, and for good if the page refuses it or sends one the sprite tables
 * cannot use: that cat is not drawn (the still portrait stays if it is the first).
 *
 * The room's own art (the three painted rooms, the props and the item atlas) is loaded the same
 * way, by [room].
 */
internal object CatArt {
    /** The page's genetic coats; the plugin connects it to the bridge ([PageCoatSource.bind]). */
    val coats = PageCoatSource()

    /** Changes each time a sheet or a refusal arrives from the page. */
    var revision by mutableIntStateOf(0)
        private set

    private val bump = Any()
    private var art: Result<PetArt>? = null
    private var room: Result<RoomKit>? = null

    /** The cats on screen, least recently used first; the oldest goes when there are too many. */
    private val entries =
        object : LinkedHashMap<Pair<String, Int>, Entry>(8, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<Pair<String, Int>, Entry>
            ) = size > MAX_CATS
        }

    init {
        coats.onChange { synchronized(bump) { revision += 1 } }
    }

    /**
     * One cat's sheets as they come in: where every clip's frames are (the same for any coat of a
     * growth stage), the recoloured side view, then the depth view.
     */
    private class Entry {
        var tables: Tables? = null
        var side: ImageBitmap? = null
        var depth: ImageBitmap? = null
        var built: CatSprites? = null
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
        val entry = entries.getOrPut(coat to stage) { Entry() }
        return try {
            assemble(entry, loaded, coat, stage)
        } catch (e: RuntimeException) {
            // a sheet that does not decode: for a page coat that is a refusal, for good
            coats.reject(coat, SheetKind.of(stage, false))
            null
        }
    }

    /** The room's art, or null if it cannot be loaded (the card then shows its plain strip). */
    @Synchronized
    fun room(): RoomKit? =
        (room ?: runCatching { RoomKit(PetArtResources.loadRoom()) }.also { room = it }).getOrNull()

    private fun assemble(entry: Entry, art: PetArt, coat: String, stage: Int): CatSprites? {
        val t = entry.tables ?: tablesFor(art, stage)?.also { entry.tables = it } ?: return null
        if (entry.side == null) {
            val kind = SheetKind.of(stage, false)
            val sheet = art.sheet(coat, kind) ?: return null
            val palette = art.palette(coat) ?: return null
            if (!t.fits(sheet, depthRows = false)) {
                coats.reject(coat, kind)
                return null
            }
            entry.side = bitmap(Recolour.sheet(sheet, palette))
        }
        if (entry.depth == null) {
            // the depth rows come right after the side view: the walk toward and away needs them at
            // any moment, and a reply takes longer than the cat takes to turn
            val kind = SheetKind.of(stage, true)
            val sheet = art.sheet(coat, kind)
            val palette = art.palette(coat)
            if (sheet != null && palette != null) {
                if (t.fits(sheet, depthRows = true)) {
                    entry.depth = bitmap(Recolour.sheet(sheet, palette))
                    entry.built = null
                } else {
                    coats.reject(coat, kind)
                }
            }
        }
        return entry.built
            ?: CatSprites(
                    t.frameWidth,
                    t.frameHeight,
                    entry.side!!,
                    entry.depth,
                    t.rows,
                    t.frames,
                    t.feet,
                    t.onDepth,
                )
                .also { entry.built = it }
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

    private const val MAX_CATS = 4
}
