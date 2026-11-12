package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.lifemax.rpg.feature.pet.art.Anim
import com.lifemax.rpg.feature.pet.art.IndexSheet
import com.lifemax.rpg.feature.pet.art.OutlineGaps
import com.lifemax.rpg.feature.pet.art.PageCoatSource
import com.lifemax.rpg.feature.pet.art.PetArt
import com.lifemax.rpg.feature.pet.art.PetArtResources
import com.lifemax.rpg.feature.pet.art.Recolour
import com.lifemax.rpg.feature.pet.art.RestingFloor
import com.lifemax.rpg.feature.pet.art.RestingShade
import com.lifemax.rpg.feature.pet.art.RigLayer
import com.lifemax.rpg.feature.pet.art.RigLibrary
import com.lifemax.rpg.feature.pet.art.RigMorph
import com.lifemax.rpg.feature.pet.art.RigSet
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
 *
 * The side view's resting rows are drawn mended ([RestingFloor]): a frame whose tail sinks a pixel
 * through the floor shows the band of its neighbour there, in the plain sprite and the rig alike.
 * Every row also has its outline ring closed ([OutlineGaps]) and the resting rows their coat shades
 * held steady where nothing around a pixel moves ([RestingShade]).
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
    private var rigs: RigLibrary? = null
    private var nextRigSet = 0

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
        /** The side view's index sheet as drawn: mended by [RestingFloor]. */
        var sideSheet: IndexSheet? = null
        var side: ImageBitmap? = null
        var depth: ImageBitmap? = null
        var built: CatSprites? = null
        var rigged: CatSprites? = null
        var riggedMorph: RigMorph? = null
        var riggedWithDepth = false
    }

    /** Where every clip's frames are, for a growth stage (the same for any coat). */
    private class Tables(
        val frameWidth: Int,
        val frameHeight: Int,
        val rows: IntArray,
        val frames: IntArray,
        val feet: IntArray,
        val onDepth: BooleanArray,
        val anims: Array<Anim>,
    ) {
        /** True if every clip on this kind of sheet lies inside [sheet]. */
        fun fits(sheet: IndexSheet, depthRows: Boolean): Boolean =
            rows.indices.all {
                onDepth[it] != depthRows ||
                    ((rows[it] + 1) * frameHeight <= sheet.height &&
                        frames[it] * frameWidth <= sheet.width)
            }

        /** Every side-view row, for [OutlineGaps]. */
        fun sideRows(): List<Anim> = anims.filterIndexed { id, _ -> !onDepth[id] }

        /** The side-view rows of the clips a cat rests in, for [RestingFloor]. */
        fun resting(): List<Anim> =
            Clips.all.filter { it.isHoldable && !onDepth[it.id] }.map { anims[it.id] }
    }

    /**
     * The sprites of [coat] at kitten [stage] (-1 grown), or null if native cannot draw it (yet). A
     * null is never remembered: ask again when [revision] has changed.
     *
     * With a [morph] the sprites carry a rig: the cat's ears, tail and whiskers move with its body
     * springs, and its proportions follow its genes (see [RigSet]). Without one, or when the rig's
     * files cannot be read, they draw the sheet as it is.
     */
    @Synchronized
    fun sprites(coat: String, stage: Int, morph: RigMorph? = null): CatSprites? {
        val loaded =
            (art ?: runCatching { PetArtResources.load(coats) }.also { art = it }).getOrNull()
                ?: return null
        val entry = entries.getOrPut(coat to stage) { Entry() }
        return try {
            val built = assemble(entry, loaded, coat, stage)
            if (built != null && morph != null) rigged(entry, built, loaded, coat, stage, morph)
            else built
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
            val raw = art.sheet(coat, kind) ?: return null
            val palette = art.palette(coat) ?: return null
            if (!t.fits(raw, depthRows = false)) {
                coats.reject(coat, kind)
                return null
            }
            val closed = OutlineGaps.close(raw, art.anims, t.sideRows())
            val floored = RestingFloor.mend(closed, art.anims, t.resting())
            val sheet = RestingShade.freeze(floored, art.anims, t.resting())
            entry.sideSheet = sheet
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

    /** [built] with the rig of this cat; [built] itself if the rig cannot be had. */
    private fun rigged(
        entry: Entry,
        built: CatSprites,
        art: PetArt,
        coat: String,
        stage: Int,
        morph: RigMorph,
    ): CatSprites {
        val withDepth = entry.depth != null
        val kept = entry.rigged
        if (kept != null && entry.riggedMorph == morph && entry.riggedWithDepth == withDepth) {
            return kept
        }
        val library = rigs ?: PetArtResources.loadRig().also { rigs = it }
        val palette = art.palette(coat) ?: return built
        fun layer(depth: Boolean): RigLayer? {
            val kind = SheetKind.of(stage, depth)
            return RigLayer(
                library.kind(kind.key) ?: return null,
                (if (depth) art.sheet(coat, kind) else entry.sideSheet) ?: return null,
                palette,
            )
        }
        val side = layer(depth = false) ?: return built
        val set =
            RigSet(
                nextRigSet++,
                morph,
                stage >= 0,
                art.anims,
                entry.tables!!.anims,
                side,
                if (withDepth) layer(depth = true) else null,
            )
        return built.withRig(set).also {
            entry.rigged = it
            entry.riggedMorph = morph
            entry.riggedWithDepth = withDepth
        }
    }

    private fun tablesFor(art: PetArt, stage: Int): Tables? {
        val clips = Clips.all
        val rows = IntArray(clips.size)
        val frames = IntArray(clips.size)
        val feet = IntArray(clips.size)
        val onDepth = BooleanArray(clips.size)
        val anims = ArrayList<Anim>(clips.size)
        for (clip in clips) {
            val anim = art.growth.resolve(stage, clip.name, art.anims) ?: return null
            anims.add(anim)
            rows[clip.id] = anim.row
            frames[clip.id] = anim.frames
            feet[clip.id] = anim.bottom
            onDepth[clip.id] = anim.depth
        }
        return Tables(
            art.anims.frameWidth,
            art.anims.frameHeight,
            rows,
            frames,
            feet,
            onDepth,
            anims.toTypedArray(),
        )
    }

    private const val MAX_CATS = 4
}
