package com.lifemax.rpg.feature.pet.art

/**
 * The cat's art, loaded: the tables (rows, growth, room atlas, palettes) and a lookup of decoded
 * sheets by coat.
 *
 * A coat is looked up in the shipped assets first (the four legacy coats), then in [extra], the
 * page's composer of genetic coats. Decoded sheets are cached; sheets that came from [extra] are
 * kept in a small least-recently-used cache, since there can be many coats and each is only needed
 * while a cat of that coat is on screen.
 *
 * Obtain one with [PetArtResources.load] (Android and JVM); build one by hand for tests.
 */
class PetArt(
    val anims: AnimTable,
    val growth: Growth,
    val room: RoomAtlas,
    private val shipped: Map<String, CatPalette>,
    private val shippedSheet: (String, SheetKind) -> ByteArray?,
    private val extra: CoatSource? = null,
) {
    private val shippedCache = HashMap<Pair<String, SheetKind>, IndexSheet>()
    private val extraCache =
        object : LinkedHashMap<Pair<String, SheetKind>, IndexSheet>(16, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<Pair<String, SheetKind>, IndexSheet>
            ) = size > EXTRA_CACHE_SIZE
        }

    /** The four coats that ship with the app. */
    val legacyCoats: List<String>
        get() = shipped.keys.sorted()

    /** The colours of [coat], or null if neither the app nor [extra] knows it. */
    fun palette(coat: String): CatPalette? = shipped[coat] ?: extra?.palette(coat)

    /** The index sheet of [coat] for [kind], or null if it is not available (yet). */
    fun sheet(coat: String, kind: SheetKind): IndexSheet? {
        val key = coat to kind
        shippedCache[key]?.let {
            return it
        }
        if (coat in shipped) {
            val bytes = shippedSheet(coat, kind) ?: return null
            return SheetDecoder.decode(bytes).also { shippedCache[key] = it }
        }
        extraCache[key]?.let {
            return it
        }
        val bytes = extra?.sheet(coat, kind) ?: return null
        return SheetDecoder.decode(bytes, CoatReply.MAX_PIXELS).also { extraCache[key] = it }
    }

    /**
     * One recoloured frame of [coat], or null if the coat's sheet or palette is not available.
     * [stage] is a kitten stage ([Growth.ADULT] for a grown cat); the animation is resolved as the
     * web does ([Growth.resolve]).
     */
    fun frame(coat: String, stage: Int, animName: String, frame: Int): ArgbImage? {
        val anim = growth.resolve(stage, animName, anims) ?: return null
        val sheet = sheet(coat, SheetKind.of(stage, anim.depth)) ?: return null
        val palette = palette(coat) ?: return null
        return Recolour.region(sheet, palette, anims.frameRect(anim, frame))
    }

    private companion object {
        const val EXTRA_CACHE_SIZE = 12
    }
}
