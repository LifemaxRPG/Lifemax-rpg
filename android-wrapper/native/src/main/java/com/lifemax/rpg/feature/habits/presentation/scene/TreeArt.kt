package com.lifemax.rpg.feature.habits.presentation.scene

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import com.lifemax.rpg.feature.habits.domain.TreeKind
import com.lifemax.rpg.feature.habits.domain.TreeShape
import kotlin.math.min

/**
 * One tree variant as paths, made once: the tapering [trunk] (with its limbs into the crown) and
 * its lit side, and the foliage in four tones: [dark] (every lobe and the ragged bumps round them:
 * the whole silhouette), [shade], [mid] and [light] (the lobes smaller and moved towards the light,
 * which comes from the upper left), and a few [glint]s on the lit edge. A pine's tiers are cut the
 * same way down its left half.
 */
internal class TreeArt(val shape: TreeShape) {
    val trunk = Path()
    val trunkLit = Path()
    val dark = Path()
    val shade = Path()
    val mid = Path()
    val light = Path()
    val glint: Path? = if (shape.kind == TreeKind.PINE) null else Path()

    init {
        trunk()
        if (shape.kind == TreeKind.PINE) pine() else crown()
    }

    private fun trunk() {
        val top = shape.crown
        trunk.apply {
            moveTo(-3.8f, 0f)
            quadraticBezierTo(-2.2f, -5f, -1.9f, top * .5f)
            lineTo(-1.5f, top)
            lineTo(1.5f, top)
            lineTo(1.9f, top * .5f)
            quadraticBezierTo(2.2f, -5f, 3.8f, 0f)
            close()
            if (shape.kind != TreeKind.PINE) {
                // a limb into each side of the crown
                moveTo(-1.2f, top * .86f)
                lineTo(-7.5f, top - 7f)
                lineTo(-6.2f, top - 8f)
                lineTo(-.3f, top * .8f)
                close()
                moveTo(1.2f, top * .8f)
                lineTo(7f, top - 5f)
                lineTo(5.8f, top - 6.2f)
                lineTo(.3f, top * .74f)
                close()
            }
        }
        trunkLit.apply {
            moveTo(-3.8f, 0f)
            quadraticBezierTo(-2.2f, -5f, -1.9f, top * .5f)
            lineTo(-1.5f, top)
            lineTo(-.3f, top)
            lineTo(-.5f, top * .5f)
            quadraticBezierTo(-1.1f, -5f, -1.4f, 0f)
            close()
        }
    }

    /** Round and tall trees: lobes in four tones, ragged bumps, glints on the lit edge. */
    private fun crown() {
        val l = shape.lobes
        val b = shape.bumps
        var k = 0
        while (k < l.size) {
            val cx = l[k]
            val cy = l[k + 1]
            val r = l[k + 2]
            dark.oval(cx, cy, r)
            shade.oval(cx, cy - r * .06f, r * .92f)
            mid.oval(cx - r * .16f, cy - r * .2f, r * .8f)
            light.oval(cx - r * .4f, cy - r * .44f, r * .46f)
            k += 3
        }
        var n = 0
        while (n < b.size) {
            val bx = b[n]
            val by = b[n + 1]
            val br = b[n + 2]
            dark.oval(bx, by, br)
            val lobe = n / 9
            // a bump on the lit side of its lobe gets a glint; the others lighten the shade tone
            if (bx < l[lobe * 3] - 1f && by < l[lobe * 3 + 1] + 2f)
                glint!!.oval(bx + br * .25f, by + br * .25f, br * .55f)
            else shade.oval(bx, by, br * .8f)
            n += 3
        }
    }

    /** A pine: the outline, the same a little narrower, its left half, a ridge of light. */
    private fun pine() {
        val o = shape.outline
        fun poly(path: Path, across: (Float) -> Float) {
            var i = 0
            while (i < o.size) {
                val x = across(o[i])
                if (i == 0) path.moveTo(x, o[i + 1]) else path.lineTo(x, o[i + 1])
                i += 2
            }
            path.close()
        }
        poly(dark) { it }
        poly(shade) { it * .86f }
        poly(mid) { min(it, 0f) * .9f }
        poly(light) { min(it, 0f) * .42f }
    }

    private fun Path.oval(cx: Float, cy: Float, r: Float) =
        addOval(Rect(cx - r, cy - r, cx + r, cy + r))
}
