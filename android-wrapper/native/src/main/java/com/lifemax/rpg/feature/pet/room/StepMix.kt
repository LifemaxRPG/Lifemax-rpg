package com.lifemax.rpg.feature.pet.room

/**
 * The solid pieces of one frame, each at its own camera step: with the camera-follow parallax a
 * piece left of the middle of the screen shows its right side and one right of it its left side
 * ([PanFollow.perspective]), so the pieces cannot all be cut from one [VolumeStep]. A mix takes
 * each piece's bands from the step chosen for it and puts them, with the atlas furniture, in one
 * drawing order (back to front by the floor row each band stands on, ties by the manifest's order,
 * exactly as a single step does it), so [DepthOrder] can put the cats between them as it does for a
 * step.
 *
 * Making a mix allocates nothing (every array is sized once, from [VolumeSheets.capacity]) and it
 * is only remade when some piece changes step, which is rare: the pieces move one step at a time
 * and a step is a quarter of the way across the sway. Each band is drawn from the step it came from
 * ([source], [index], [slot]).
 *
 * @param furnitureKey the floor row each piece of atlas furniture sorts at, in the scene's order.
 */
internal class StepMix(private val sheets: VolumeSheets, private val furnitureKey: FloatArray) :
    BandSet {
    private val capacity = sheets.capacity
    private val pieces = sheets.pieces.size

    override var count = 0
        private set

    override val piece = IntArray(capacity)
    override val key = FloatArray(capacity)
    override val first = IntArray(pieces)
    override val end = IntArray(pieces)
    override val order = IntArray(capacity)
    override var orderSize = 0
        private set

    /** The step band `b` is drawn from, its number among that step's bands, and its step index. */
    private val source = arrayOfNulls<VolumeStep>(capacity)
    private val index = IntArray(capacity)

    /** The index (the canvas slot) of the step band [b] is drawn from. */
    val slot = IntArray(capacity)

    private val chosen = IntArray(pieces) { NONE }
    private val chosenStep = arrayOfNulls<VolumeStep>(pieces)

    /** The pictures band [b] is cut from. */
    fun image(b: Int) = source[b]!!.image

    /** Band [b]'s rectangle in [image]: where it starts, and how big it is. */
    fun sx(b: Int) = source[b]!!.sx[index[b]]

    fun sy(b: Int) = source[b]!!.sy[index[b]]

    fun w(b: Int) = source[b]!!.w[index[b]]

    fun h(b: Int) = source[b]!!.h[index[b]]

    /** Where band [b] is drawn, from its piece's top left. */
    fun dx(b: Int) = source[b]!!.dx[index[b]]

    fun dy(b: Int) = source[b]!!.dy[index[b]]

    /** The number of band [b] among its piece's bands (its depth in floor rows). */
    fun band(b: Int) = source[b]!!.band[index[b]]

    /**
     * Take each piece's bands from the step [steps] says (an index into the sheets' steps per
     * piece). Remade only if something changed; true if it was.
     */
    fun update(steps: IntArray): Boolean {
        var same = true
        for (p in 0 until pieces) {
            if (steps[p] != chosen[p] || chosenStep[p] !== sheets.stepOrNull(steps[p])) {
                same = false
                break
            }
        }
        if (same) return false
        count = 0
        for (p in 0 until pieces) {
            val i = steps[p]
            val step = sheets.step(i)
            chosen[p] = i
            chosenStep[p] = step
            first[p] = count
            for (b in step.first[p] until step.end[p]) {
                piece[count] = p
                key[count] = step.key[b]
                source[count] = step
                index[count] = b
                slot[count] = i
                count++
            }
            end[p] = count
        }
        sortOrder()
        return true
    }

    /** Forget the steps this mix draws from (the sheets let go of them), so they can be freed. */
    fun clear() {
        chosen.fill(NONE)
        chosenStep.fill(null)
        source.fill(null)
        count = 0
        orderSize = 0
    }

    /** Back to front by key; at the same key bands before furniture, then by number. */
    private fun sortOrder() {
        val n = count + furnitureKey.size
        for (k in 0 until n) {
            // insertion: place code k among the sorted ones before it
            var at = k
            while (at > 0 && before(k, order[at - 1])) {
                order[at] = order[at - 1]
                at--
            }
            order[at] = k
        }
        orderSize = n
        for (k in 0 until n) order[k] = codeOf(order[k])
    }

    /** Whether entry [a] sorts before entry [b] (entries: bands first, then the furniture). */
    private fun before(a: Int, b: Int): Boolean {
        val ka = keyOf(a)
        val kb = keyOf(b)
        if (ka != kb) return ka < kb
        val fa = a >= count
        val fb = b >= count
        if (fa != fb) return fb
        return a < b
    }

    private fun keyOf(i: Int) = if (i < count) key[i] else furnitureKey[i - count]

    private fun codeOf(i: Int) = if (i < count) i else VolumeStep.FURNITURE + i - count

    private companion object {
        const val NONE = -1
    }
}
