package com.lifemax.rpg.feature.habits.presentation.scene

import com.lifemax.rpg.feature.habits.domain.Ambient
import com.lifemax.rpg.feature.habits.domain.TreeKind
import com.lifemax.rpg.feature.habits.domain.Trees
import com.lifemax.rpg.feature.habits.domain.Wind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * How a street tree hangs in the wind ([Wind]) at one moment: the trunk leans as a stiff spring (a
 * shear about its foot); each mass of the crown swings about the top of the trunk as its bough
 * bends, a moment behind the trunk (the higher, the later) and further than the trunk's lean alone
 * would take it; its leaves flutter on their own a little out of step. Pure arithmetic into a kept
 * array (no Compose), so the street's painter and a test or a preview pose a tree alike.
 */
internal object TreeSway {
    /**
     * What [pose] writes: the trunk's shear, then for each mass its angle and its leaves' shift.
     */
    const val SHEAR = 0
    const val ANGLE = 1
    const val DX = 2
    const val DY = 3
    const val STRIDE = 3

    /** How far a tree leans for a push of one ([Wind.force]), degrees. */
    private const val LEAN = 3f

    /** The pose array big enough for any variant. */
    fun buffer(): FloatArray = FloatArray(1 + STRIDE * 16)

    /**
     * Writes into [out] the pose of the tree of [variant] standing at [x] along the street, for a
     * picture drawn with [dir] (1 as painted, -1 mirrored: what moves right on the street moves
     * left in the mirrored picture), at [size] of its painted size. Its trunk's shear at [SHEAR];
     * for mass k at 1 + k * [STRIDE]: the angle it swings about its pivot ([TreeRig.px], the
     * trunk's top for most; radians, clockwise), and its leaves' own shift (pixels of the picture).
     * All 0 when the wind is still.
     */
    fun pose(variant: Int, x: Float, dir: Float, wind: Wind, out: FloatArray) {
        java.util.Arrays.fill(out, 0f)
        if (!wind.moving) return
        val shape = Trees.shapes[variant]
        val rig = TreeSprites.rig(variant)
        val pine = shape.kind == TreeKind.PINE
        val shrub = shape.kind == TreeKind.SHRUB
        // a big crown sways slowly, a column a little quicker, a shrub quicker still
        val period =
            when (shape.kind) {
                TreeKind.ROUND -> 2.7f
                TreeKind.TALL -> 2.2f
                TreeKind.PINE -> 2.4f
                TreeKind.SHRUB -> 1.3f
            } * (.92f + .16f * Ambient.hash(variant, 1, 77))
        val stiff = if (pine) .55f else if (shrub) .6f else 1f
        val trunk = wind.swing(x, period, .2f)
        val rad = PI.toFloat() / 180f
        // the trunk itself bends least: most of the lean is the crown's
        val lean = tan(trunk * LEAN * stiff * rad)
        val k = lean * TRUNK_SHARE
        // a shear moves what is up the picture (y under 0) by shear * y: a lean to the right is
        // a negative one
        out[SHEAR] = -k * dir
        val ppu = rig.ppu
        val leafy = if (pine) .3f else 1f
        for (m in 0 until rig.clusters) {
            val up = rig.footY - rig.cy[m]
            val rise = rig.rise[m]
            // the higher a mass, the further along its boughs and the later it follows
            val lag =
                (.1f + .28f * rise + .12f * Ambient.hash(variant, m, 78)) * (if (pine) .5f else 1f)
            val then = tan(wind.lagged(lag) * LEAN * stiff * rad)
            // where its middle would be: bent over as the crown bends, more at the top
            val want = then * up * (.8f + .35f * rise)
            val extra = want - k * up
            val bx = rig.cx[m] - rig.px[m]
            val by = rig.cy[m] - rig.py[m]
            val reach = sqrt(bx * bx + by * by).coerceAtLeast(3f * ppu)
            val seed = variant * 31 + m * 7
            val rate = 1.5f + .9f * Ambient.hash(variant, m, 79)
            // the bough's own nod, out of step with the rest
            val nod = wind.flutter(x, seed, rate * .6f) * .35f * ppu * leafy
            out[1 + m * STRIDE] = ((extra + nod) / reach) * dir
            // and its leaves' flutter: mostly across, a little up and down
            out[1 + m * STRIDE + 1] = wind.flutter(x, seed + 3, rate) * FLUTTER * ppu * leafy * dir
            out[1 + m * STRIDE + 2] =
                wind.flutter(x, seed + 5, rate * 1.3f) * FLUTTER * .4f * ppu * leafy
        }
    }

    /** The crown's lean as one shear about the foot (its shadows, its back leaves). */
    fun crownShear(out: FloatArray): Float = out[SHEAR] / TRUNK_SHARE

    /** How much of the lean the trunk takes; the rest is the crown swinging about its top. */
    private const val TRUNK_SHARE = .4f

    /** How far the leaves flutter for a push of one, in units. */
    private const val FLUTTER = .45f

    /** The angle in [pose]'s [out] for mass [m], in degrees (Compose's rotate). */
    fun degrees(out: FloatArray, m: Int): Float = out[1 + m * STRIDE] * (180f / PI.toFloat())

    /** Whether a pose moves anything noticeably (a cheaper draw when it does not). */
    fun still(out: FloatArray): Boolean = abs(out[SHEAR]) < 1e-5f
}
