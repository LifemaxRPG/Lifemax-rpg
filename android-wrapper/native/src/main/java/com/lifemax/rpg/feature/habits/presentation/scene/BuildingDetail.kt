package com.lifemax.rpg.feature.habits.presentation.scene

import com.lifemax.rpg.feature.habits.domain.Ambient
import com.lifemax.rpg.feature.habits.domain.BuildingSpec as B
import com.lifemax.rpg.feature.habits.domain.HabitMaterial
import com.lifemax.rpg.feature.habits.domain.RuinWindow

/*
 * The architecture of a facade as plain shapes, worked out once per floor and per building and turned into paths by
 * BuildingArt: the surrounds of the windows (frames, lintels, keystones, sills and the shadow under each), the
 * string course at a floor's top, a drainpipe down one side with its brackets, the odd air conditioner or
 * balcony, the cornice's dentils and brackets, what stands on the roof, and what a run-down storey has lost. Pure
 * Kotlin (no Compose), so the same shapes are drawn by the painter and by the JVM harness that looks at them, and
 * the choices (which side the pipe is on, which floor has a balcony) are tested to be the same every time and
 * sparse.
 */

/** What a shape is drawn in: the facade's colours and the shades over them. */
internal enum class DetailInk {
    /** The facade's trim colour (frames, sills, lintels, the string course). */
    TRIM,

    /** White at a low alpha: the sky catching a sill's top, a keystone, a coping. */
    LIGHT,

    /** Black at a low alpha: the shadow line under a sill, a lintel, a bracket. */
    SHADE,

    /** Dark painted iron: pipes, railings, brackets. */
    IRON,

    /** The pale highlight along a pipe, a railing's top rail. */
    IRON_LIGHT,

    /** A pale grey box: an air conditioner, a dish. */
    BOX,

    /** The dark of a grille, a vent. */
    BOX_DARK,

    /** A spray-can tag. */
    SPRAY,
}

/** Rectangles and polylines by ink, in building units. Filled once, read every frame. */
internal class Shapes {
    private val rects = Array(DetailInk.entries.size) { FloatList() }
    private val lines = Array(DetailInk.entries.size) { ArrayList<FloatArray>() }

    fun rect(ink: DetailInk, x: Float, y: Float, w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return
        rects[ink.ordinal].add(x, y, w, h)
    }

    /** A polyline of (x, y) pairs, stroked. */
    fun line(ink: DetailInk, points: FloatArray) {
        lines[ink.ordinal].add(points)
    }

    fun rects(ink: DetailInk): FloatList = rects[ink.ordinal]

    fun lines(ink: DetailInk): List<FloatArray> = lines[ink.ordinal]

    fun isEmpty(ink: DetailInk): Boolean =
        rects[ink.ordinal].size == 0 && lines[ink.ordinal].isEmpty()

    /** The smallest rectangle round everything, as (left, top, right, bottom); nulls when empty. */
    fun bounds(): FloatArray? {
        var l = Float.MAX_VALUE
        var t = Float.MAX_VALUE
        var r = -Float.MAX_VALUE
        var b = -Float.MAX_VALUE
        var any = false
        for (list in rects) for (i in 0 until list.size / 4) {
            any = true
            l = minOf(l, list[i * 4])
            t = minOf(t, list[i * 4 + 1])
            r = maxOf(r, list[i * 4] + list[i * 4 + 2])
            b = maxOf(b, list[i * 4 + 1] + list[i * 4 + 3])
        }
        for (list in lines) for (p in list) for (i in 0 until p.size / 2) {
            any = true
            l = minOf(l, p[i * 2])
            t = minOf(t, p[i * 2 + 1])
            r = maxOf(r, p[i * 2])
            b = maxOf(b, p[i * 2 + 1])
        }
        return if (any) floatArrayOf(l, t, r, b) else null
    }
}

/** A growable list of floats (four to a rectangle). */
internal class FloatList {
    var data = FloatArray(32)
        private set

    var size = 0
        private set

    fun add(a: Float, b: Float, c: Float, d: Float) {
        if (size + 4 > data.size) data = data.copyOf(data.size * 2)
        data[size] = a
        data[size + 1] = b
        data[size + 2] = c
        data[size + 3] = d
        size += 4
    }

    operator fun get(i: Int): Float = data[i]
}

/** What a floor adds beside its wall and windows, and which window has something in it. */
internal enum class FloorExtra {
    NONE,
    AIR_CON,
    BALCONY,
}

/** What a building keeps up on its roof, besides the water tank (and the sign). */
internal class RoofKit(
    val stack: Boolean,
    val skylight: Boolean,
    val dish: Boolean,
    val railing: Boolean,
)

/** The choices a whole building makes once, from its seed. */
internal class BuildingChoices(
    /** The drainpipe: down the left (-1), the right (1), or none (0). */
    val pipeSide: Int,
    val roof: RoofKit,
) {
    val pipeX: Float
        get() = pipeSide * (B.W / 2f - PIPE_IN)

    companion object {
        const val PIPE_IN = 4.5f
    }
}

internal object BuildingDetail {
    /** How much of the floors get an air conditioner in a window, and a balcony. */
    const val AIR_CON_SHARE = .22f
    const val BALCONY_SHARE = .14f

    /** The lintel's, the sill's and the frame's measures, in units. */
    const val SILL_H = 2f
    const val FRAME = 1f

    fun choices(seed: Int): BuildingChoices {
        val pipe = Ambient.hash(seed, 0, 500)
        val side = if (pipe < .15f) 0 else if (pipe < .55f) -1 else 1
        return BuildingChoices(
            side,
            RoofKit(
                stack = Ambient.hash(seed, 1, 500) < .55f,
                skylight = Ambient.hash(seed, 2, 500) < .4f,
                dish = Ambient.hash(seed, 3, 500) < .45f,
                railing = Ambient.hash(seed, 4, 500) < .5f,
            ),
        )
    }

    /**
     * What floor [seed] has beside its windows: an air conditioner (brick and stone floors), a
     * balcony (stone and gold), or nothing; only where a window is never lit, so nothing stands in
     * front of a room ([lit] of [count] windows light up; the last in the order never does unless
     * all do).
     */
    fun extra(seed: Int, material: HabitMaterial, lit: Int, count: Int): FloorExtra {
        if (lit >= count || count == 0) return FloorExtra.NONE
        val v = Ambient.hash(seed, 0, 510)
        return when (material) {
            HabitMaterial.CLAY -> if (v < AIR_CON_SHARE) FloorExtra.AIR_CON else FloorExtra.NONE
            HabitMaterial.STONE ->
                if (v < AIR_CON_SHARE * .6f) FloorExtra.AIR_CON
                else if (v < AIR_CON_SHARE * .6f + BALCONY_SHARE) FloorExtra.BALCONY
                else FloorExtra.NONE
            HabitMaterial.GOLD -> if (v < BALCONY_SHARE) FloorExtra.BALCONY else FloorExtra.NONE
            else -> FloorExtra.NONE
        }
    }

    /**
     * The shapes of one floor of [material] (a run-down storey when [ruin]), its front from [top]
     * to [bottom] (building units, y down), its [windows] as x, y (from the top), w, h in their
     * lighting order, [count] of them, [lit] the most that light. [wall] is drawn before the
     * windows (surrounds, the string course, the pipe), [front] over them (an air conditioner, a
     * balcony, a tag on a boarded window). [pipeX] is the drainpipe's x, NaN for none.
     */
    fun floor(
        material: HabitMaterial,
        ruin: Boolean,
        top: Float,
        bottom: Float,
        windows: FloatArray,
        count: Int,
        lit: Int,
        hall: Int,
        seed: Int,
        storey: Int,
        pipeX: Float,
        ruins: Array<RuinWindow>?,
        wall: Shapes,
        front: Shapes,
    ) {
        val half = B.W / 2f
        val gold = material == HabitMaterial.GOLD && !ruin
        val stone = (material == HabitMaterial.STONE || gold) && !ruin
        val glass = material == HabitMaterial.GLASS && !ruin
        // the string course along the floor's top: a band, the sky on its top, shade under it
        val course = if (gold) 3.2f else if (stone) 2.4f else 1.6f
        wall.rect(DetailInk.TRIM, -half - 1f, top, B.W + 2f, course)
        wall.rect(DetailInk.LIGHT, -half - 1f, top, B.W + 2f, .5f)
        wall.rect(DetailInk.SHADE, -half, top + course, B.W, if (stone) 1.4f else 1f)
        for (k in 0 until count) {
            val x = windows[k * 4]
            val y = top + windows[k * 4 + 1]
            val w = windows[k * 4 + 2]
            val h = windows[k * 4 + 3]
            surround(material, ruin, x, y, w, h, wall)
        }
        // the drainpipe, with a bracket near each end; a run-down storey has lost some of it
        if (!pipeX.isNaN() && !glass) {
            val keep = !ruin || Ambient.hash(seed, 1, 520) < .6f
            if (keep) pipe(pipeX, top, bottom, wall)
            else if (storey == 0) {
                // the bottom storey's length hangs loose from its top bracket
                val away = if (pipeX < 0f) 1f else -1f
                wall.rect(DetailInk.IRON, pipeX - 2f, top + 3f, 4f, 1.4f)
                wall.line(
                    DetailInk.IRON,
                    floatArrayOf(pipeX, top + 3f, pipeX + away * 7f, top + 15f),
                )
            }
        }
        if (ruin) {
            // tags sprayed on some of the boarded windows
            if (ruins != null)
                for (k in 0 until count) {
                    if (ruins[k] != RuinWindow.BOARDED) continue
                    if (Ambient.hash(seed, k, 530) >= .3f) continue
                    tag(
                        windows[k * 4],
                        top + windows[k * 4 + 1],
                        windows[k * 4 + 2],
                        windows[k * 4 + 3],
                        seed + k,
                        front,
                    )
                }
            return
        }
        when (extra(seed, material, lit, count)) {
            FloorExtra.AIR_CON -> {
                val k = count - 1
                if (k != hall)
                    airCon(
                        windows[k * 4],
                        top + windows[k * 4 + 1],
                        windows[k * 4 + 2],
                        windows[k * 4 + 3],
                        front,
                    )
            }
            FloorExtra.BALCONY -> {
                val k = count - 1
                if (k != hall)
                    balcony(
                        windows[k * 4],
                        top + windows[k * 4 + 1],
                        windows[k * 4 + 2],
                        windows[k * 4 + 3],
                        gold,
                        front,
                    )
            }
            FloorExtra.NONE -> {}
        }
    }

    /** A window's frame, lintel and sill, by the material. */
    private fun surround(
        material: HabitMaterial,
        ruin: Boolean,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        s: Shapes,
    ) {
        val gold = material == HabitMaterial.GOLD && !ruin
        val stone = (material == HabitMaterial.STONE || gold) && !ruin
        val glass = material == HabitMaterial.GLASS && !ruin
        val f = if (glass) .7f else FRAME
        // the frame round the pane
        s.rect(DetailInk.TRIM, x - f, y - f, f, h + 2f * f)
        s.rect(DetailInk.TRIM, x + w, y - f, f, h + 2f * f)
        s.rect(DetailInk.TRIM, x, y - f, w, f)
        s.rect(DetailInk.TRIM, x, y + h, w, f)
        // the reveal: the pane sits back from the wall, a shade along its top and lit side
        s.rect(DetailInk.SHADE, x, y, w, .8f)
        s.rect(DetailInk.SHADE, x, y, .6f, h)
        // the sill: a shelf under the frame, the sky on it, its shadow on the wall under it
        val sill = if (stone) SILL_H + .4f else SILL_H
        val reach = if (glass) 1f else 1.5f
        s.rect(DetailInk.TRIM, x - reach, y + h, w + 2f * reach, sill)
        s.rect(DetailInk.LIGHT, x - reach, y + h, w + 2f * reach, .5f)
        s.rect(DetailInk.SHADE, x - reach, y + h + sill, w + 2f * reach, if (stone) 1f else .8f)
        if (glass) return
        // the lintel over it: a flat arch with a keystone in stone, a soldier course in brick, a
        // plain beam on a run-down storey
        when {
            stone -> {
                s.rect(DetailInk.TRIM, x - 2.4f, y - 3.2f, w + 4.8f, 2.6f)
                s.rect(DetailInk.LIGHT, x - 2.4f, y - 3.2f, w + 4.8f, .5f)
                s.rect(DetailInk.SHADE, x - 2.4f, y - .6f, w + 4.8f, .6f)
                s.rect(DetailInk.TRIM, x + w / 2f - 1.6f, y - 4f, 3.2f, 3.6f)
                s.rect(DetailInk.LIGHT, x + w / 2f - 1.6f, y - 4f, 3.2f, 1.1f)
            }
            ruin -> {
                s.rect(DetailInk.TRIM, x - 1f, y - 2.4f, w + 2f, 1.6f)
                s.rect(DetailInk.SHADE, x - 1f, y - .8f, w + 2f, .8f)
            }
            else -> {
                // brick: a course of bricks on end over the window, the joints between them
                s.rect(DetailInk.TRIM, x - 2f, y - 3f, w + 4f, 2.4f)
                var bx = x - 2f + 2.6f
                while (bx < x + w + 2f - .6f) {
                    s.rect(DetailInk.SHADE, bx, y - 3f, .5f, 2.4f)
                    bx += 2.6f
                }
                s.rect(DetailInk.SHADE, x - 2f, y - .6f, w + 4f, .6f)
            }
        }
    }

    private fun pipe(px: Float, top: Float, bottom: Float, s: Shapes) {
        s.rect(DetailInk.IRON, px - 1f, top, 2f, bottom - top)
        s.rect(DetailInk.IRON_LIGHT, px - .7f, top, .6f, bottom - top)
        s.rect(DetailInk.IRON, px - 2f, top + 5f, 4f, 1.4f)
        s.rect(DetailInk.IRON, px - 2f, bottom - 7f, 4f, 1.4f)
    }

    /** An air conditioner in the lower part of a window: a box with a grille, its drip tray. */
    private fun airCon(x: Float, y: Float, w: Float, h: Float, s: Shapes) {
        val top = y + h * .52f
        s.rect(DetailInk.IRON, x + .6f, top - .6f, w - 1.2f, h * .48f + .6f)
        s.rect(DetailInk.BOX, x + 1.2f, top, w - 2.4f, h * .48f - .8f)
        s.rect(DetailInk.LIGHT, x + 1.2f, top, w - 2.4f, .6f)
        var gy = top + 2f
        while (gy < y + h - 3f) {
            s.rect(DetailInk.BOX_DARK, x + 2.6f, gy, w - 5.2f, .6f)
            gy += 1.5f
        }
        s.rect(DetailInk.BOX_DARK, x + w - 5f, top + 1.4f, 2.4f, 2.4f)
    }

    /** A balcony across the lower part of a window: a slab, a railing of balusters. */
    private fun balcony(x: Float, y: Float, w: Float, h: Float, gold: Boolean, s: Shapes) {
        val rail = if (gold) DetailInk.TRIM else DetailInk.IRON
        val top = y + h * .55f
        s.rect(DetailInk.TRIM, x - 3f, y + h - 1f, w + 6f, 2.4f)
        s.rect(DetailInk.LIGHT, x - 3f, y + h - 1f, w + 6f, .5f)
        s.rect(DetailInk.SHADE, x - 3f, y + h + 1.4f, w + 6f, 1.4f)
        s.rect(rail, x - 2.6f, top, w + 5.2f, .9f)
        s.rect(if (gold) DetailInk.LIGHT else DetailInk.IRON_LIGHT, x - 2.6f, top, w + 5.2f, .35f)
        var bx = x - 2.4f
        while (bx <= x + w + 2.4f) {
            s.rect(rail, bx, top, .7f, y + h - 1f - top)
            bx += 2.8f
        }
    }

    /** A spray-can tag on a boarded window: a loose scrawl of three or four strokes. */
    private fun tag(x: Float, y: Float, w: Float, h: Float, seed: Int, s: Shapes) {
        val n = 3 + (Ambient.hash(seed, 0, 540) * 2f).toInt()
        val pts = FloatArray((n + 1) * 2)
        for (i in 0..n) {
            pts[i * 2] =
                x + 2f + (w - 4f) * (i.toFloat() / n) + (Ambient.hash(seed, i, 541) - .5f) * 3f
            pts[i * 2 + 1] = y + h * (.3f + .4f * Ambient.hash(seed, i, 542))
        }
        s.line(DetailInk.SPRAY, pts)
    }

    /**
     * The cornice of a building whose top storey is [crown]: its coping, the dentils or brackets
     * under it, in a band [B.CORNICE] tall over [y] (the roof line), [half] + 3 each side.
     */
    fun cornice(crown: HabitMaterial, y: Float, s: Shapes) {
        val half = B.W / 2f + 3f
        val top = y - B.CORNICE
        val stone = crown == HabitMaterial.STONE || crown == HabitMaterial.GOLD
        s.rect(DetailInk.LIGHT, -half, top, 2f * half, 1.4f)
        s.rect(DetailInk.SHADE, -half, top + 1.4f, 2f * half, .6f)
        if (stone) {
            // brackets under the slab, the shade between them
            s.rect(DetailInk.SHADE, -half, y - 3.2f, 2f * half, 2.6f)
            var x = -half + 4f
            while (x <= half - 4f) {
                s.rect(DetailInk.TRIM, x - 1.6f, y - 3.6f, 3.2f, 3.2f)
                s.rect(DetailInk.LIGHT, x - 1.6f, y - 3.6f, 3.2f, .6f)
                x += 11.5f
            }
        } else {
            // dentils: small blocks along the fascia's foot
            s.rect(DetailInk.SHADE, -half, y - 2.6f, 2f * half, 2.2f)
            var x = -half + 2f
            while (x <= half - 3f) {
                s.rect(DetailInk.TRIM, x, y - 2.6f, 2.2f, 2f)
                x += 4.4f
            }
        }
    }

    /**
     * The lobby's shapes: the plinth's joints, the pipe's foot, the shopfront's reveals and the
     * awning's brackets; its wall runs from [top] (negative) to 0. [pipeX] NaN for no pipe.
     */
    fun lobby(top: Float, pipeX: Float, s: Shapes) {
        val half = B.W / 2f
        // the plinth: a stone base with its joints, a chamfer catching the sky
        s.rect(DetailInk.LIGHT, -half, -10f, B.W, .7f)
        var x = -half + 11.5f
        while (x < half) {
            s.rect(DetailInk.SHADE, x, -9.3f, .7f, 9.3f)
            x += 23f
        }
        s.rect(DetailInk.SHADE, -half, -5.2f, B.W, .6f)
        if (!pipeX.isNaN()) {
            s.rect(DetailInk.IRON, pipeX - 1f, top, 2f, B.LOBBY_H - 6f)
            s.rect(DetailInk.IRON_LIGHT, pipeX - .7f, top, .6f, B.LOBBY_H - 6f)
            s.rect(DetailInk.IRON, pipeX - 2f, top + 4f, 4f, 1.4f)
            // the shoe at its foot, turned out to the pavement
            s.rect(DetailInk.IRON, pipeX - 1.6f, -6.5f, 3.2f, 3f)
        }
        // the awning's brackets: a strut from the wall to the awning's front edge at each end
        val a0 = top + 21f
        for (side in intArrayOf(-1, 1)) {
            val bx = side * (half - 10f)
            s.line(DetailInk.IRON, floatArrayOf(bx, a0 + 13f, bx - side * 1f, a0 + 1.5f))
        }
    }
}
