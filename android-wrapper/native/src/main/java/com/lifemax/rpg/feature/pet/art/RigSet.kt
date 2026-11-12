package com.lifemax.rpg.feature.pet.art

/** One sheet (side view or depth view) of a cat with its rig and colours. */
class RigLayer(val kind: RigKind, val sheet: IndexSheet, val palette: CatPalette)

/**
 * The rig of one cat: its proportions ([morph]), and for each of the clips the body can play, which
 * rig animation and which art frame it draws. [anims] holds the animation each clip id draws at
 * this growth stage (as [Growth.resolve] says), so the rig sees the same rows the sheet does.
 *
 * The art's frames are recoloured the first time the rig needs them and then kept (a frame is 72 x
 * 48 pixels); nothing else is stored here.
 *
 * @property id a number that no other live set has, part of the cache key
 * @property kitten a kitten's ears stay as drawn (its head is too small for the ear redraw, as in
 *   the web); its tail, whiskers and proportions move as an adult's do
 * @param side the side-view layer
 * @param depth the depth-view layer, or null while it has not arrived
 */
class RigSet(
    val id: Int,
    val morph: RigMorph,
    val kitten: Boolean,
    private val table: AnimTable,
    private val anims: Array<Anim>,
    private val side: RigLayer,
    private val depth: RigLayer?,
) {
    private val maxFrames = anims.maxOf { it.frames }
    private val sources = arrayOfNulls<IntArray>(anims.size * maxFrames)
    private val animIndex =
        IntArray(anims.size) { clip -> layerOf(anims[clip])?.kind?.indexOf(anims[clip].name) ?: -1 }

    /** How many clips the set knows. */
    val clipCount: Int
        get() = anims.size

    private fun layerOf(anim: Anim): RigLayer? = if (anim.depth) depth else side

    /** True if the rig has art for [clip]. */
    fun has(clip: Int): Boolean = animIndex[clip] >= 0

    /** The number of frames of [clip]. */
    fun frames(clip: Int): Int = anims[clip].frames

    /** The rig of [frame] of [clip]; [has] must be true. */
    fun rigFrame(clip: Int, frame: Int): RigFrame =
        layerOf(anims[clip])!!.kind.frame(animIndex[clip], frame)

    /**
     * The art frame [frame] of [clip] in ARGB, recoloured once and kept; [has] must be true. With a
     * [light] other than [CatLightKey.NEUTRAL] the frame is lit (docs/specs/CAT_LIGHTING_PLAN.md
     * WP-L3) and kept beside the unlit one; the neutral frame is exactly [Recolour]'s, as always.
     */
    fun source(clip: Int, frame: Int, light: Int = CatLightKey.NEUTRAL): IntArray {
        val anim = anims[clip]
        val f = Math.floorMod(frame, anim.frames)
        val slot = clip * maxFrames + f
        val store =
            if (light == CatLightKey.NEUTRAL) sources
            else lit.getOrPut(light) { arrayOfNulls(sources.size) }
        store[slot]?.let {
            return it
        }
        val layer = layerOf(anim)!!
        val rect = table.frameRect(anim, f)
        val image =
            if (light == CatLightKey.NEUTRAL) Recolour.region(layer.sheet, layer.palette, rect)
            else {
                val look = looks.getOrPut(layer) { CoatLook.of(layer.palette) }
                LitRecolour.region(layer.sheet, layer.palette, rect, CatLightKey.light(light, look))
            }
        return image.pixels.also { store[slot] = it }
    }

    /** The lit art frames by light key, made as the rig needs them. */
    private val lit = HashMap<Int, Array<IntArray?>>()
    private val looks = HashMap<RigLayer, CoatLook>()
}

/**
 * Composes and remembers frames of the rig: the one place a cat's secondary motion becomes pixels.
 * A frame is looked up by its set, clip, frame and grid point ([RigGrid]); a miss composes it into
 * the least recently used slot of the cache. [slotFor] returns that slot, or -1 for "draw the plain
 * sheet" (the cat is the art as drawn and nothing is moved, or the rig has nothing for the clip).
 * After the cache has filled, a miss re-uses a slot's pixels and a hit is a scan of the keys: no
 * allocation either way (the set's art frames are recoloured once, the first time they are seen).
 *
 * @param capacity how many composed frames to keep; one cat in motion uses a few dozen
 */
class RigFrames(capacity: Int = DEFAULT_CAPACITY) {
    private val composer = RigCompose(FRAME_WIDTH, FRAME_HEIGHT)
    private val cache = RigCache(capacity, composer.canvasWidth * composer.canvasHeight)
    private val pose = RigPose()

    /** Width of a composed picture. */
    val width: Int
        get() = composer.canvasWidth

    /** Height of a composed picture. */
    val height: Int
        get() = composer.canvasHeight

    /** How many frames were composed since the start (a cache miss each), for tests. */
    var composed = 0L
        private set

    /**
     * The slot of the composed frame of [set] for [clip] and [frame] at [grid] under [light] (a
     * [CatLightKey]; the neutral key gives the very key the cache always had), or -1. A fresh slot
     * (see [wasComposed]) has to be uploaded by the caller; its pixels are [pixels].
     */
    fun slotFor(
        set: RigSet,
        clip: Int,
        frame: Int,
        grid: RigGrid,
        light: Int = CatLightKey.NEUTRAL,
    ): Int {
        fresh = false
        if (!set.has(clip) || (set.morph.neutral && grid.neutral)) return -1
        val f = Math.floorMod(frame, set.frames(clip))
        val key = keyOf(set.id, clip, f, grid) + light * LIGHT_STRIDE
        val hit = cache.find(key)
        if (hit >= 0) return hit
        val slot = cache.claim(key)
        grid.toPose(pose)
        composer.compose(
            set.source(clip, f, light),
            set.rigFrame(clip, f),
            set.morph,
            pose,
            cache.pixels(slot),
        )
        composed++
        fresh = true
        return slot
    }

    private var fresh = false

    /** True if the latest [slotFor] composed its frame (so a bitmap of the slot is out of date). */
    val wasComposed: Boolean
        get() = fresh

    /** The pixels of a slot [slotFor] returned. */
    fun pixels(slot: Int): IntArray = cache.pixels(slot)

    private fun keyOf(set: Int, clip: Int, frame: Int, grid: RigGrid): Long {
        require(clip < CLIP_SPAN && frame < FRAME_SPAN) { "clip $clip frame $frame" }
        var k = (set % SET_SPAN).toLong()
        k = k * CLIP_SPAN + clip
        k = k * FRAME_SPAN + frame
        return k * RigGrid.POINTS + grid.key()
    }

    companion object {
        /** The art's frame size (the rig is cut for it). */
        const val FRAME_WIDTH = 72
        const val FRAME_HEIGHT = 48

        /** Composed frames kept: about 90 KB each. */
        const val DEFAULT_CAPACITY = 96

        private const val SET_SPAN = 1 shl 14
        private const val CLIP_SPAN = 64
        private const val FRAME_SPAN = 16

        /** The light key is the top part of the cache key, so the neutral key (0) adds nothing. */
        private const val LIGHT_STRIDE: Long =
            1L * SET_SPAN * CLIP_SPAN * FRAME_SPAN * RigGrid.POINTS
    }
}
