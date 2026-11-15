package com.lifemax.rpg.feature.habits.presentation.scene

import com.lifemax.rpg.feature.habits.domain.PeopleActivities
import com.lifemax.rpg.feature.habits.domain.RoomKind
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A room as two overlays on a lit window: what is behind the person ([back]) and what is in front
 * of them.
 */
internal class Room(val back: Sprite, val front: Sprite)

/**
 * The rooms behind the windows, painted once each (per window height) as two overlays: the ceiling
 * light's warm cone, a back wall that darkens to the sides and towards the floor, the furniture of
 * the room's activity in warm dark shapes (desk, shelf, armchair, treadmill, mat, counter, sofa,
 * bed, piano, whiteboard), a lamp's glow, and, in front of the person, the desk or counter and the
 * soft light that spills along the sill. The overlays go over the plain lit window, so the same
 * room serves by day and by night. Pure Kotlin, drawn like the figures.
 */
internal object RoomSprites {
    const val PPU = 1.6f

    /** A window's width in units, what the rooms are painted for. */
    const val WIN_W = 17f

    /** How big a person is in a window [h] tall: whole in a tall one, smaller in a short one. */
    fun personScale(h: Float): Float = ((h - 2.5f) / 26f).coerceIn(.62f, 1f)

    /** What one room's two overlays take for a window [h] tall, in bytes. */
    fun bytes(h: Float): Int = ceil(WIN_W * PPU).toInt() * ceil(h * PPU).toInt() * 4 * 2

    private const val FURN = 0x3B2415
    private const val FURN2 = 0x5E3A22
    private const val DARK = 0x2A1810
    private const val LEAF = 0x3A4A22
    private const val LEAF2 = 0x566B2E
    private const val POT = 0x6B3A24
    private const val SHADE = 0xFFE7A8
    private const val PAPER = 0xFFF3DC
    private const val MIRROR = 0xE3F0FF
    private const val SOFA = 0x7A3D2C
    private const val MAT = 0x5B3550
    private const val TEAL = 0x2F5560

    /** The room [kind] for a window [h] tall. */
    fun render(kind: RoomKind, h: Float): Room {
        val w = ceil(WIN_W * PPU).toInt()
        val hp = ceil(h * PPU).toInt()
        val cv =
            Cv(
                w,
                hp,
                h,
                PeopleActivities.shift(kind),
                personScale(h) * PeopleActivities.roomFit(kind),
            )
        cv.layer = 0
        cv.wall()
        furniture(cv, kind)
        cv.layer = 1
        front(cv, kind)
        cv.sill()
        return Room(Sprite(w, hp, PPU, 0f, 0f, cv.out(0)), Sprite(w, hp, PPU, 0f, 0f, cv.out(1)))
    }

    private fun furniture(c: Cv, kind: RoomKind) {
        when (kind) {
            RoomKind.OFFICE -> {
                chair(c)
                c.rect(-3f, -24f, 9.5f, -23.2f, FURN2, .85f)
                c.rect(-4.4f, -21f, .2f, -15.6f, FURN, .8f)
                c.rect(-3.8f, -20.4f, -.4f, -16.2f, PAPER, .5f)
                c.glow(2f, -27f, 11f, SHADE, .4f)
            }
            RoomKind.DESK -> {
                chair(c)
                c.capsule(9.2f, -9.4f, .35f, 8.2f, -17.6f, .25f, FURN, .9f)
                c.poly(
                    floatArrayOf(6.2f, -17.4f, 10.4f, -17.4f, 9.4f, -20.6f, 7.2f, -20.6f),
                    SHADE,
                    .95f,
                )
                c.glow(8f, -15f, 9f, SHADE, .55f)
                c.rect(-5f, -22f, -1f, -17f, FURN2, .7f)
            }
            RoomKind.STUDY -> {
                c.rect(-7.6f, -17f, -3.2f, -3.6f, FURN2, .95f)
                c.ellipse(-5.4f, -17f, 2.2f, 1.2f, FURN2, .95f)
                c.rect(-6.8f, -6.4f, 2.4f, -3.4f, FURN, .95f)
                c.rect(9.2f, -21f, 9.8f, 0f, DARK, .85f)
                c.poly(floatArrayOf(7f, -19.4f, 12f, -19.4f, 11f, -23.6f, 8f, -23.6f), SHADE, .95f)
                c.glow(9.6f, -18f, 10f, SHADE, .5f)
                // shelves of books
                c.rect(-9f, -25f, -3f, -24.4f, FURN, .8f)
                for (i in 0 until 6) c.rect(
                    -8.6f + i * 1f,
                    -28f + (i % 2) * .6f,
                    -7.9f + i * 1f,
                    -25f,
                    if (i % 2 == 0) SOFA else TEAL,
                    .75f,
                )
            }
            RoomKind.GYM -> {
                c.rect(-9f, -26f, 1f, -7f, MIRROR, .14f)
                c.rect(-1.5f, -26f, -1f, -7f, MIRROR, .16f)
                c.rect(-7f, -1.4f, 6.5f, 0f, DARK, .85f)
                c.rect(5.6f, -15f, 6.4f, -1.4f, DARK, .85f)
                c.rect(3.6f, -17.6f, 8.4f, -14.4f, FURN, .95f)
                c.rect(4.2f, -17f, 7.8f, -15.4f, 0x7FB8FF, .7f)
                c.rect(8.5f, -13.6f, 12.5f, -13f, DARK, .8f)
                c.rect(8.5f, -7.6f, 12.5f, -7f, DARK, .8f)
                c.rect(8.6f, -13.6f, 9.1f, 0f, DARK, .8f)
                c.rect(12f, -13.6f, 12.5f, 0f, DARK, .8f)
                for (i in 0 until 3) c.ellipse(9.8f + i * 1.1f, -14.4f, .6f, .6f, FURN, .9f)
            }
            RoomKind.YOGA -> {
                c.ellipse(0f, -.3f, 9.5f, 1.1f, MAT, .9f)
                c.rect(8.3f, -5f, 10.8f, 0f, POT, .9f)
                leaves(c, 9.5f, -5f, 6, 2.2f)
                c.glow(-8.5f, -1.2f, 3f, SHADE, .55f)
                c.rect(-9.2f, -2f, -7.6f, 0f, FURN2, .9f)
            }
            RoomKind.KITCHEN -> {
                c.rect(-10f, -28f, 13f, -19f, FURN2, .9f)
                for (x in listOf(-4f, 2.5f, 8f)) c.rect(x, -28f, x + .4f, -19f, DARK, .6f)
                c.poly(floatArrayOf(9f, -9.5f, 11.4f, -9.5f, 11f, -13.6f, 9.4f, -13.6f), DARK, .85f)
                c.glow(2f, -29f, 10f, SHADE, .45f)
                c.rect(-9f, -17.4f, -4.4f, -17f, DARK, .6f)
            }
            RoomKind.BATH -> {
                c.rect(1f, -22f, 10.4f, -12.4f, MIRROR, .5f)
                c.rect(.7f, -22.3f, 10.7f, -21.8f, FURN, .8f)
                c.rect(.7f, -12.7f, 10.7f, -12.2f, FURN, .8f)
                c.rect(-9f, -17f, -3f, -16.5f, FURN2, .85f)
                c.poly(
                    floatArrayOf(-8f, -16.5f, -4f, -16.5f, -4.4f, -9.5f, -7.6f, -9.5f),
                    TEAL,
                    .85f,
                )
                c.glow(5f, -25f, 9f, 0xFFFFFF, .35f)
            }
            RoomKind.BEDROOM -> {
                c.rect(11f, -16f, 13.4f, -3f, FURN, .95f)
                c.rect(3f, -6.8f, 13f, -3.6f, SOFA, .9f)
                c.rect(3f, -3.6f, 13f, -1f, DARK, .85f)
                c.ellipse(11.2f, -8.2f, 1.9f, 1.1f, PAPER, .7f)
                c.rect(-9.4f, -7.6f, -5.6f, 0f, FURN2, .95f)
                c.capsule(-7.5f, -7.6f, .3f, -7.5f, -11f, .25f, FURN, .9f)
                c.poly(
                    floatArrayOf(-9.4f, -11f, -5.6f, -11f, -6.4f, -14.4f, -8.6f, -14.4f),
                    SHADE,
                    .95f,
                )
                c.glow(-7.5f, -11f, 9f, SHADE, .5f)
            }
            RoomKind.LIVING -> {
                c.rect(3.2f, -13.4f, 13.4f, -5.6f, SOFA, .92f)
                c.rect(3.2f, -7.6f, 13.4f, -3.6f, 0x92503A, .95f)
                c.rect(12.2f, -9.6f, 14.2f, -3.4f, SOFA, .95f)
                c.rect(3.6f, -3.6f, 13f, 0f, DARK, .8f)
                c.rect(-9.4f, -4.4f, -6.4f, 0f, POT, .9f)
                leaves(c, -7.9f, -4.4f, 7, 3.4f)
                c.rect(-5.5f, -23f, -.4f, -17f, FURN2, .8f)
                c.rect(-5f, -22.4f, -.9f, -17.6f, PAPER, .45f)
            }
            RoomKind.MUSIC -> {
                c.rect(7.2f, -9f, 11.8f, 0f, DARK, .9f)
                c.ellipse(9.5f, -4.6f, 1.6f, 1.6f, FURN2, .8f)
                c.rect(-6f, -22f, -1f, -14.4f, FURN2, .8f)
                c.rect(-5.4f, -21.4f, -1.6f, -15f, 0xE88A4A, .45f)
                c.rect(-5f, -6.4f, 1.6f, -5.6f, FURN, .9f)
                c.rect(-2.6f, -5.6f, -1.8f, 0f, DARK, .85f)
                c.glow(3f, -27f, 10f, SHADE, .35f)
            }
            RoomKind.PIANO -> {
                c.rect(1.6f, -18f, 6.6f, -11.4f, PAPER, .75f)
                c.rect(-5f, -6.4f, 1.6f, -5.6f, FURN, .9f)
                c.rect(-2.6f, -5.6f, -1.8f, 0f, DARK, .85f)
                c.glow(5f, -17f, 9f, SHADE, .42f)
                c.rect(-9f, -24f, -3f, -23.4f, FURN2, .8f)
            }
            RoomKind.PLANTS -> {
                c.rect(.8f, -10.4f, 15f, -9.6f, FURN2, .95f)
                for ((i, x) in listOf(2.6f, 7f, 11.4f).withIndex()) {
                    c.rect(x - 1.1f, -12.6f - i * .4f, x + 1.1f, -10f, POT, .92f)
                    leaves(c, x, -12.6f - i * .4f, 7, 3.4f + (i % 2))
                }
                c.capsule(-6f, -28f, .2f, -6f, -20f, .2f, DARK, .6f)
                leaves(c, -6f, -19f, 5, 2.2f)
                c.rect(-9.2f, -3.2f, -6.2f, 0f, POT, .9f)
                leaves(c, -7.7f, -3.2f, 5, 2.6f)
            }
            RoomKind.BOARD -> {
                c.rect(1.6f, -25f, 14.6f, -10f, PAPER, .82f)
                c.rect(1.2f, -25.4f, 15f, -24.8f, FURN2, .8f)
                c.rect(1.2f, -10.4f, 15f, -9.8f, FURN2, .8f)
                for (i in 0 until 5) c.rect(
                    3f,
                    -22.6f + i * 2.2f,
                    7f + (i * 3) % 5,
                    -22.2f + i * 2.2f,
                    DARK,
                    .6f,
                )
                c.rect(10.4f, -23f, 13.4f, -20.4f, 0xFFC94A, .85f)
                c.rect(10.4f, -19.6f, 13.4f, -17f, 0x6FB7FF, .8f)
                c.rect(10.4f, -16.2f, 13.4f, -13.6f, 0xFF8E7A, .8f)
                c.glow(8f, -30f, 10f, SHADE, .35f)
            }
            RoomKind.HALL -> {
                c.rect(5.4f, -20f, 11.4f, 0f, FURN, .95f)
                c.rect(6.2f, -19f, 10.6f, -.6f, FURN2, .7f)
                c.ellipse(9.8f, -9.4f, .4f, .4f, SHADE, .9f)
                c.rect(-9.4f, -3.8f, -6.6f, 0f, POT, .9f)
                leaves(c, -8f, -3.8f, 6, 3f)
                c.rect(-6f, -24f, -1.6f, -18f, FURN2, .75f)
                c.rect(-5.4f, -23.4f, -2.2f, -18.6f, PAPER, .35f)
                c.glow(0f, -29f, 11f, SHADE, .35f)
            }
        }
    }

    private fun front(c: Cv, kind: RoomKind) {
        when (kind) {
            RoomKind.OFFICE,
            RoomKind.DESK -> {
                c.rect(2.2f, -9.2f, 13f, -8.2f, FURN2, 1f)
                c.rect(3f, -8.2f, 12.4f, 0f, FURN, 1f)
                c.rect(2.2f, -9.2f, 13f, -9.0f, SHADE, .3f)
            }
            RoomKind.KITCHEN -> {
                c.rect(2.5f, -9.6f, 13.5f, -8.7f, 0xB89B7A, .95f)
                c.rect(2.8f, -8.7f, 13.2f, 0f, FURN2, 1f)
                c.rect(7.7f, -8.4f, 8f, 0f, DARK, .5f)
            }
            RoomKind.BATH -> {
                c.rect(2.5f, -8.9f, 12.5f, -8.2f, 0xE8DCC8, .95f)
                c.rect(2.8f, -8.2f, 12.2f, 0f, FURN2, 1f)
                c.capsule(4.6f, -9.4f, .3f, 4.6f, -11.6f, .25f, 0xB9C2CC, .9f)
                c.capsule(4.6f, -11.6f, .25f, 6f, -11.8f, .22f, 0xB9C2CC, .9f)
            }
            RoomKind.PIANO -> {
                c.rect(2f, -9f, 13.4f, 0f, DARK, 1f)
                c.rect(2f, -9f, 7f, -8.3f, PAPER, .9f)
                for (i in 0 until 4) c.rect(2.8f + i * 1.1f, -9f, 3.3f + i * 1.1f, -8.6f, DARK, 1f)
            }
            else -> {}
        }
    }

    private fun chair(c: Cv) {
        c.rect(-5.4f, -6.6f, 1.8f, -5.6f, FURN, .95f)
        c.rect(-5.8f, -14f, -4.6f, -5.6f, FURN, .95f)
        c.rect(-3.6f, -5.6f, -2.6f, 0f, DARK, .9f)
    }

    private fun leaves(c: Cv, cx: Float, cy: Float, n: Int, r: Float) {
        for (i in 0 until n) {
            val a = -150f + 120f * i / max(1, n - 1)
            val ra = Math.toRadians(a.toDouble())
            val lx = cx + (kotlin.math.sin(ra) * r).toFloat()
            val ly = cy - (kotlin.math.cos(ra) * r).toFloat() * 1.05f
            c.ellipse(lx, ly, r * .5f, r * .27f, if (i % 2 == 0) LEAF else LEAF2, .92f, a - 90f)
        }
    }

    /**
     * A painting surface for one window: [out] gives its two layers. Shapes are given in the rig's
     * units (the floor on y = 0, up negative), moved by [dx] and drawn at [s] times their size, so
     * a room's furniture keeps its proportions to the person in a short window.
     */
    class Cv(
        val w: Int,
        val h: Int,
        private val hUnits: Float,
        private val dx: Float,
        private val s: Float,
    ) {
        var layer = 0
        private val r = Array(2) { FloatArray(w * h) }
        private val g = Array(2) { FloatArray(w * h) }
        private val b = Array(2) { FloatArray(w * h) }
        private val a = Array(2) { FloatArray(w * h) }

        private fun px(x: Float) = (s * (x + dx) + WIN_W / 2f) * PPU

        private fun py(y: Float) = (hUnits + s * y) * PPU

        fun out(l: Int): IntArray =
            IntArray(w * h) {
                PeopleRaster.argb(a[l][it], r[l][it] * 255f, g[l][it] * 255f, b[l][it] * 255f)
            }

        private fun over(i: Int, color: Int, alpha: Float) {
            if (alpha <= 0f) return
            val l = layer
            val sr = ((color shr 16) and 255) / 255f
            val sg = ((color shr 8) and 255) / 255f
            val sb = (color and 255) / 255f
            val da = a[l][i]
            val oa = alpha + da * (1f - alpha)
            r[l][i] = (sr * alpha + r[l][i] * da * (1f - alpha)) / oa
            g[l][i] = (sg * alpha + g[l][i] * da * (1f - alpha)) / oa
            b[l][i] = (sb * alpha + b[l][i] * da * (1f - alpha)) / oa
            a[l][i] = oa
        }

        /**
         * The wall: darker to the sides, the ceiling and the floor, with the ceiling lamp's warm
         * cone.
         */
        fun wall() {
            for (y in 0 until h) for (x in 0 until w) {
                val nx = (x + .5f) / w * 2f - 1f
                val yy = (y + .5f) / PPU
                var dark = .04f + .3f * nx * nx * nx * nx + .1f * nx * nx
                if (yy > hUnits - 2f) dark += .3f * (yy - (hUnits - 2f)) / 2f
                if (yy < 1.6f) dark += .3f * (1.6f - yy) / 1.6f
                over(y * w + x, 0x7A3A12, dark.coerceIn(0f, .6f))
                val dxn = nx * 8.5f
                val dyn = yy - 1f
                val d = sqrt(dxn * dxn + dyn * dyn * .6f) / 13f
                if (d < 1f) over(y * w + x, 0xFFF1C8, (1f - d) * (1f - d) * .5f)
            }
        }

        /** The light spilling along the sill, in front of everything. */
        fun sill() {
            layer = 1
            for (y in 0 until h) for (x in 0 until w) {
                val yy = (h - y - .5f) / PPU
                if (yy < 2.4f) over(y * w + x, 0xFFE2A0, (1f - yy / 2.4f) * (1f - yy / 2.4f) * .5f)
            }
        }

        fun glow(cx: Float, cy: Float, radius: Float, color: Int, k: Float) {
            val x0 = px(cx)
            val y0 = py(cy)
            val rr = radius * s * PPU
            for (y in max(0, floor(y0 - rr).toInt())..min(h - 1, ceil(y0 + rr).toInt())) for (x in
                max(0, floor(x0 - rr).toInt())..min(w - 1, ceil(x0 + rr).toInt())) {
                val d = sqrt((x + .5f - x0) * (x + .5f - x0) + (y + .5f - y0) * (y + .5f - y0)) / rr
                if (d < 1f) over(y * w + x, color, (1f - d) * (1f - d) * k)
            }
        }

        fun rect(x0: Float, y0: Float, x1: Float, y1: Float, color: Int, alpha: Float) =
            poly(floatArrayOf(x0, y0, x1, y0, x1, y1, x0, y1), color, alpha)

        fun ellipse(
            cx: Float,
            cy: Float,
            rx: Float,
            ry: Float,
            color: Int,
            alpha: Float,
            rot: Float = 0f,
        ) =
            poly(
                com.lifemax.rpg.feature.habits.domain.FigBuilder.ellipse(cx, cy, rx, ry, rot, 14),
                color,
                alpha,
            )

        fun capsule(
            x0: Float,
            y0: Float,
            r0: Float,
            x1: Float,
            y1: Float,
            r1: Float,
            color: Int,
            alpha: Float,
        ) =
            poly(
                com.lifemax.rpg.feature.habits.domain.FigBuilder.capsule(x0, y0, r0, x1, y1, r1, 5),
                color,
                alpha,
            )

        /** Fills a polygon (rig units) with [color] at [alpha], anti-aliased. */
        fun poly(pts: FloatArray, color: Int, alpha: Float) {
            val n = pts.size / 2
            val q = FloatArray(pts.size)
            var x0 = 1e9f
            var y0 = 1e9f
            var x1 = -1e9f
            var y1 = -1e9f
            for (i in 0 until n) {
                q[i * 2] = px(pts[i * 2])
                q[i * 2 + 1] = py(pts[i * 2 + 1])
                x0 = min(x0, q[i * 2])
                x1 = max(x1, q[i * 2])
                y0 = min(y0, q[i * 2 + 1])
                y1 = max(y1, q[i * 2 + 1])
            }
            val bx = max(0, floor(x0).toInt())
            val by = max(0, floor(y0).toInt())
            val ex = min(w, ceil(x1).toInt())
            val ey = min(h, ceil(y1).toInt())
            if (ex <= bx || ey <= by) return
            val pl = PeopleRaster.Planes((ex - bx) * PeopleRaster.SS, (ey - by) * PeopleRaster.SS)
            PeopleRaster.fill(pl, q, 1f, -bx.toFloat(), -by.toFloat(), 1f)
            val ss = PeopleRaster.SS
            for (y in by until ey) for (x in bx until ex) {
                var c = 0f
                for (j in 0 until ss) for (i in 0 until ss) c +=
                    pl.alpha[((y - by) * ss + j) * pl.w + (x - bx) * ss + i]
                if (c > 0f) over(y * w + x, color, alpha * c / (ss * ss))
            }
        }
    }
}
