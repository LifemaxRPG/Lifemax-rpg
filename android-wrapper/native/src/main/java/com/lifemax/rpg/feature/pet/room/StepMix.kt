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
 * A piece between two steps ([StepBlend]) takes the bands of both: its base step's, then the
 * faded-in step's, each with its own [alpha]. The base's bands are drawn whole but for the pixels
 * the faded-in step does not cover ([VolumeStep.fade]), which fade out as it fades in, so the
 * result is a cross-fade of the two pictures: the base alone at weight 0, the other step alone at
 * weight 1, and nothing pops at either end. At the same floor row the base's bands come first.
 *
 * Making a mix allocates nothing (every array is sized once, from [VolumeSheets.capacity]) and it
 * is only re-sorted when some piece changes steps; while a piece fades between the same two steps
 * only the alphas change ([update] sets them every frame). Each band is drawn from the step it came
 * from ([source], [index], [slot]).
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

    /** How opaque band [b] is drawn (1 but in a cross-fade). */
    val alpha = FloatArray(capacity)

    /** The [VolumeStep.fade] bits that fade band [b] out with its piece's weight (0: it stays). */
    private val fades = ByteArray(capacity)

    /** Whether band [b] is of the faded-in step (drawn at its piece's weight). */
    private val faded = BooleanArray(capacity)

    private val chosen = IntArray(pieces) { NONE }
    private val chosenOver = IntArray(pieces) { NONE }
    private val chosenStep = arrayOfNulls<VolumeStep>(pieces)
    private val chosenOverStep = arrayOfNulls<VolumeStep>(pieces)

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

    /** Take each piece's bands from the step [steps] says, drawn alone (no cross-fade). */
    fun update(steps: IntArray): Boolean {
        for (p in 0 until pieces) {
            noOver[p] = NONE
            noWeight[p] = 0f
        }
        return update(steps, noOver, noWeight)
    }

    private val noOver = IntArray(pieces)
    private val noWeight = FloatArray(pieces)

    /**
     * Take each piece's bands from the step [base] says (an index into the sheets' steps per
     * piece), with the step [over] faded in at [weight] (-1 and 0 for none). The order is remade
     * only if some piece's steps changed (true if it was); the alphas are set every call.
     */
    fun update(base: IntArray, over: IntArray, weight: FloatArray): Boolean {
        var same = true
        for (p in 0 until pieces) {
            val o = if (weight[p] > 0f) over[p] else NONE
            if (
                base[p] != chosen[p] ||
                    o != chosenOver[p] ||
                    chosenStep[p] !== sheets.stepOrNull(base[p]) ||
                    (o != NONE && chosenOverStep[p] !== sheets.stepOrNull(o))
            ) {
                same = false
                break
            }
        }
        if (!same) remake(base, over, weight)
        for (p in 0 until pieces) {
            val w = if (chosenOver[p] == NONE) 0f else weight[p]
            for (b in first[p] until end[p]) {
                alpha[b] =
                    when {
                        faded[b] -> w
                        fades[b].toInt() != 0 -> 1f - w
                        else -> 1f
                    }
            }
        }
        return !same
    }

    private fun remake(base: IntArray, over: IntArray, weight: FloatArray) {
        count = 0
        for (p in 0 until pieces) {
            val i = base[p]
            val step = sheets.step(i)
            val o = if (weight[p] > 0f) over[p] else NONE
            val overStep = if (o == NONE) null else sheets.step(o)
            chosen[p] = i
            chosenStep[p] = step
            chosenOver[p] = o
            chosenOverStep[p] = overStep
            // the bits of the base's pixels that the faded-in step does not cover
            val bits =
                when (o) {
                    NONE -> 0
                    i + 1 -> VolumeStep.FADE_UP.toInt()
                    else -> VolumeStep.FADE_DOWN.toInt()
                }
            first[p] = count
            for (b in step.first[p] until step.end[p]) {
                add(p, step, b, i)
                fades[count - 1] = (step.fade[b].toInt() and bits).toByte()
            }
            if (overStep != null) {
                for (b in overStep.first[p] until overStep.end[p]) {
                    add(p, overStep, b, o)
                    faded[count - 1] = true
                }
            }
            end[p] = count
        }
        sortOrder()
    }

    private fun add(p: Int, step: VolumeStep, b: Int, i: Int) {
        piece[count] = p
        key[count] = step.key[b]
        source[count] = step
        index[count] = b
        slot[count] = i
        fades[count] = 0
        faded[count] = false
        count++
    }

    /** Forget the steps this mix draws from (the sheets let go of them), so they can be freed. */
    fun clear() {
        chosen.fill(NONE)
        chosenOver.fill(NONE)
        chosenStep.fill(null)
        chosenOverStep.fill(null)
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
