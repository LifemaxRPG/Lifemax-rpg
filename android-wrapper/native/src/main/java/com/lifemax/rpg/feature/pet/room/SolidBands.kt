package com.lifemax.rpg.feature.pet.room

/**
 * What [DepthOrder] needs to know of the bands of the solid pieces: how many there are, the piece
 * and the floor row of each, where each piece's bands start and end, and the drawing order of the
 * bands and the atlas furniture without the cats ([DepthOrder.FURNITURE] plus an index for the
 * furniture, the band's number otherwise).
 */
internal interface BandSet {
    /** How many bands. */
    val count: Int

    /** The piece each band belongs to. */
    val piece: IntArray

    /** The floor row each band stands on. */
    val key: FloatArray

    /** Where each piece's bands start. */
    val first: IntArray

    /** Where each piece's bands end (exclusive). */
    val end: IntArray

    /** The drawing order: the first [orderSize] entries. */
    val order: IntArray

    /** How many entries of [order] are valid. */
    val orderSize: Int
}

/**
 * The solid pieces cut into bands of one floor row of depth, so a cat is drawn between the bands
 * nearer and farther than it (a z-buffer at the resolution of a floor row): band `k` of a piece
 * holds its depth strips `2k` and `2k + 1` ([SolidFaces]) and stands on the row `sortFront - (k +
 * 0.5)`. A piece's bands run from its deepest to its front (band 0 last), so its last band is the
 * one a cat on it reaches.
 *
 * The bands do not change with the camera (only where their strips are drawn does), so the order is
 * made once: back to front by the row each thing stands on, at the same row the bands before the
 * atlas furniture and then the manifest's order (so the room at rest is drawn exactly as painted).
 *
 * @param pieces the solid pieces, in manifest order (each has a volume).
 * @param furnitureKey the floor row each piece of atlas furniture sorts at, in the scene's order.
 */
internal class SolidBands(pieces: List<LayerPiece>, furnitureKey: FloatArray) : BandSet {
    override val count = pieces.sumOf { FaceView.bands(it.volume!!.model.depth) }
    override val piece = IntArray(count)

    /** Each band's number among its piece's bands: how many floor rows behind its front. */
    val band = IntArray(count)
    override val key = FloatArray(count)
    override val first = IntArray(pieces.size)
    override val end = IntArray(pieces.size)
    override val order: IntArray
    override val orderSize: Int

    init {
        var n = 0
        for ((p, it) in pieces.withIndex()) {
            val volume = it.volume!!
            first[p] = n
            for (k in FaceView.bands(volume.model.depth) - 1 downTo 0) {
                piece[n] = p
                band[n] = k
                key[n] = volume.sortFront - (k + 0.5f)
                n++
            }
            end[p] = n
        }
        fun keyOf(i: Int) = if (i < count) key[i] else furnitureKey[i - count]
        val sorted =
            (0 until count + furnitureKey.size).sortedWith(
                compareBy<Int>({ keyOf(it) }, { if (it < count) 0 else 1 }, { it })
            )
        order =
            IntArray(sorted.size) {
                val i = sorted[it]
                if (i < count) i else DepthOrder.FURNITURE + i - count
            }
        orderSize = order.size
    }
}
