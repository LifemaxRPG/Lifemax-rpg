package com.lifemax.rpg.feature.pet.body

import com.lifemax.rpg.feature.pet.art.PngImage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * Test support: contact sheets of the toy scenes, for looking at. One PNG per scene: a trail panel
 * (the toy's path coloured by what it is doing: orange lifting, blue held, green flying, grey at
 * rest; red dots for the cat's mouth anchor; green ticks for its feet; blue lines for the surfaces
 * and red boxes for the furniture) over a filmstrip of cropped panels, each drawing the cat's sheet
 * frame (flat colour, as the sprite transform places it), the toy sprite with its pivot, rotation
 * and squash, the yarn's thread and the mouth anchor as a red cross. No AWT: the PNG is written
 * with `java.util.zip`.
 */
object ToySheet {
    private class Snap(
        val t: Float,
        val cat: CatPose,
        val toy: ToyBody,
        val mouthX: Float,
        val mouthY: Float,
    ) {
        val thread = toy.thread.copyOf()
        val threadCount = toy.threadCount
        val state = toy.state
        val drawX = toy.drawX
        val drawY = toy.drawY
        val angle = toy.drawAngle
        val sx = toy.drawScaleX
        val sy = toy.drawScaleY
        val ds = toy.depthScale
        val pu = toy.pivotU
        val pv = toy.pivotV
        val over = toy.overCat
    }

    private const val PANEL_W = 100
    private const val PANEL_H = 64
    private const val PANEL_SCALE = 4
    private const val PER_ROW = 5
    private const val TRAIL_SCALE_MAX = 5
    private const val MARGIN = 18
    private const val BACK = 0xF4EFE6
    private const val FLOOR = 0xB9A98E
    private const val SURFACE = 0x1446C8
    private const val SOLID = 0xE6A0A0
    private const val CAT = 0xD99A52
    private const val CAT_EDGE = 0x6B4423
    private const val RED = 0xD01818

    private val atlas: PngImage by lazy {
        PngImage.decode(
            ToySheet::class.java.getResourceAsStream("/pet/room/items.png")!!.readBytes()
        )
    }
    private val sheet: PngImage by lazy {
        PngImage.decode(
            ToySheet::class.java.getResourceAsStream("/pet/sheets/adult-ginger.png")!!.readBytes()
        )
    }

    private class Canvas(val w: Int, val h: Int) {
        val rgb = IntArray(w * h) { 0xFFFFFF }

        fun put(x: Int, y: Int, c: Int) {
            if (x in 0 until w && y in 0 until h) rgb[y * w + x] = c
        }

        fun rect(x: Int, y: Int, rw: Int, rh: Int, c: Int) {
            for (j in 0 until rh) for (i in 0 until rw) put(x + i, y + j, c)
        }

        fun line(x0: Int, y0: Int, x1: Int, y1: Int, c: Int) {
            val n = maxOf(Math.abs(x1 - x0), Math.abs(y1 - y0), 1)
            for (i in 0..n) put(x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * i / n, c)
        }
    }

    /** A view of the room painted into a canvas: room pixel (wx, wy) is canvas ([ox], [oy]). */
    private class View(
        val c: Canvas,
        val ox: Int,
        val oy: Int,
        val wx: Float,
        val wy: Float,
        val scale: Float,
    ) {
        fun px(x: Float) = ox + ((x - wx) * scale).toInt()

        fun py(y: Float) = oy + ((y - wy) * scale).toInt()

        fun block(x: Float, y: Float, size: Float, colour: Int) {
            val n = maxOf(1, Math.ceil((size * scale).toDouble()).toInt())
            c.rect(px(x) - n / 2, py(y) - n / 2, n, n, colour)
        }
    }

    /**
     * Run [case] and draw its sheet to [out]: the filmstrip shows [count] panels from time [from]
     * every [every] seconds.
     */
    fun render(case: ToyScenes.Case, from: Float, every: Float, count: Int, out: File) {
        val snaps = record(case)
        val rows = (count + PER_ROW - 1) / PER_ROW
        val trailH = 150 + 2 * MARGIN
        val w = PER_ROW * PANEL_W * PANEL_SCALE
        val h = trailH + rows * PANEL_H * PANEL_SCALE
        val c = Canvas(w, h)
        trail(c, snaps, trailH, w)
        for (i in 0 until count) {
            val snap = snaps.minByOrNull { Math.abs(it.t - (from + i * every)) } ?: continue
            val col = i % PER_ROW
            val row = i / PER_ROW
            val ox = col * PANEL_W * PANEL_SCALE
            val oy = trailH + row * PANEL_H * PANEL_SCALE
            panel(c, snap, ox, oy)
        }
        write(c, out)
    }

    private fun record(case: ToyScenes.Case): List<Snap> {
        val scene = case.scene
        scene.plan(*case.plan.toTypedArray())
        val list = ArrayList<Snap>()
        scene.run(case.seconds) {
            it.locateMouth()
            val cat = CatPose()
            cat.copyFrom(it.body.pose)
            list.add(Snap(it.time, cat, it.toy, it.mouth[0], it.mouth[1]))
        }
        return list
    }

    // ---------------------------------------------------------------- the trail panel

    private fun trail(c: Canvas, snaps: List<Snap>, trailH: Int, w: Int) {
        var x0 = Float.MAX_VALUE
        var x1 = -Float.MAX_VALUE
        var y0 = Float.MAX_VALUE
        for (s in snaps) {
            x0 = minOf(x0, s.drawX, s.cat.x)
            x1 = maxOf(x1, s.drawX, s.cat.x)
            y0 = minOf(y0, s.drawY, s.mouthY)
        }
        x0 -= MARGIN
        x1 += MARGIN
        y0 -= MARGIN / 2
        val span = x1 - x0
        val scale = minOf(TRAIL_SCALE_MAX.toFloat(), (w - 2f * MARGIN) / span)
        val v = View(c, MARGIN, 0, x0, y0, scale)
        c.rect(0, 0, w, trailH, BACK)
        scenery(v, x0, x1, y0, y0 + trailH / scale)
        for ((i, s) in snaps.withIndex()) {
            if (i % 2 == 0) {
                val colour =
                    when (s.state) {
                        ToyState.LIFT -> 0xF07800
                        ToyState.HELD -> 0x2060D0
                        ToyState.FREE -> 0x20A040
                        ToyState.REST -> 0x909090
                    }
                v.block(s.drawX, s.drawY, 1.6f, colour)
            }
            if (i % 4 == 0) v.block(s.mouthX, s.mouthY, 1f, RED)
            if (i % 6 == 0) v.block(s.cat.x, s.cat.y + 2, 1f, 0x2A9646)
        }
    }

    private fun scenery(v: View, x0: Float, x1: Float, y0: Float, y1: Float) {
        val floorY = World.FLOOR_Y_ART
        v.c.line(v.px(x0), v.py(floorY), v.px(x1), v.py(floorY), FLOOR)
        for (s in Solids.all) {
            if (s.x1 < x0 || s.x0 > x1) continue
            v.c.line(v.px(s.x0), v.py(s.top), v.px(s.x1), v.py(s.top), SOLID)
            v.c.line(v.px(s.x0), v.py(s.top), v.px(s.x0), v.py(s.bottom), SOLID)
            v.c.line(v.px(s.x1), v.py(s.top), v.px(s.x1), v.py(s.bottom), SOLID)
        }
        for (id in SurfaceId.entries) {
            if (id == SurfaceId.FLOOR) continue
            val s = World.surface(id)
            if (s.y < y0 || s.y > y1) continue
            val a = ToyPhysics.spanStart(id)
            val b = ToyPhysics.spanEnd(id)
            if (b < x0 || a > x1) continue
            v.c.line(v.px(a), v.py(s.y), v.px(b), v.py(s.y), SURFACE)
        }
    }

    // ---------------------------------------------------------------- the filmstrip

    private fun panel(c: Canvas, s: Snap, ox: Int, oy: Int) {
        c.rect(ox, oy, PANEL_W * PANEL_SCALE, PANEL_H * PANEL_SCALE, BACK)
        val cx =
            if (s.state == ToyState.HELD || s.state == ToyState.LIFT) s.cat.x + 10
            else (s.cat.x + s.drawX) / 2
        val v =
            View(
                c,
                ox,
                oy,
                cx - PANEL_W / 2f,
                World.FLOOR_Y_ART - PANEL_H + 8f,
                PANEL_SCALE.toFloat(),
            )
        scenery(v, cx - PANEL_W, cx + PANEL_W, v.wy, v.wy + PANEL_H)
        val toyFirst = !s.over
        if (toyFirst) toy(v, s)
        cat(v, s)
        if (!toyFirst) toy(v, s)
        v.block(s.mouthX, s.mouthY, 0.7f, RED)
        c.rect(ox, oy, PANEL_W * PANEL_SCALE, 1, 0xCCCCCC)
        c.rect(ox, oy, 1, PANEL_H * PANEL_SCALE, 0xCCCCCC)
    }

    private fun cat(v: View, s: Snap) {
        val p = s.cat
        val clip = Clips.all[p.clip]
        val out = FloatArray(2)
        val grey = sheet.data
        for (py in 0 until 48) for (pu in 0 until 72) {
            val i = (clip.row * 48 + py) * sheet.width + p.frame * 72 + pu
            if (grey[i].toInt() == 0) continue
            val edge =
                pu == 0 ||
                    py == 0 ||
                    pu == 71 ||
                    py == 47 ||
                    grey[i - 1].toInt() == 0 ||
                    grey[i + 1].toInt() == 0 ||
                    grey[i - sheet.width].toInt() == 0 ||
                    grey[i + sheet.width].toInt() == 0
            MouthAnchors.locate(
                pu + 0.5f,
                py + 0.5f,
                clip.bottom,
                p.flip,
                p.scaleX,
                p.scaleY,
                p.rotation,
                p.depthScale,
                p.x,
                p.y + p.bob,
                out,
            )
            v.block(out[0], out[1], p.depthScale, if (edge) CAT_EDGE else CAT)
        }
    }

    private fun toy(v: View, s: Snap) {
        val kind = s.toy.kind
        if (s.threadCount > 0) {
            for (i in 0 until s.threadCount) v.block(
                s.thread[i * 2],
                s.thread[i * 2 + 1],
                1f,
                0xC04070,
            )
        }
        val rect = ATLAS[kind.key]
        val cos = Mathx.cos(s.angle)
        val sin = Mathx.sin(s.angle)
        for (j in 0 until kind.height) for (i in 0 until kind.width) {
            val colour = if (rect != null) atlasPixel(rect, i, j) else proceduralPixel(kind, i, j)
            if (colour == 0) continue
            val lx = (i + 0.5f - s.pu) * s.sx
            val ly = (j + 0.5f - s.pv) * s.sy
            val x = s.drawX + (lx * cos - ly * sin) * s.ds
            val y = s.drawY + (lx * sin + ly * cos) * s.ds
            v.block(x, y, s.ds, colour)
        }
    }

    private val ATLAS =
        mapOf(
            "ball_a" to intArrayOf(171, 0),
            "ball_b" to intArrayOf(181, 0),
            "mouse" to intArrayOf(191, 0),
            "feather" to intArrayOf(206, 0),
        )

    private fun atlasPixel(origin: IntArray, i: Int, j: Int): Int {
        val o = ((origin[1] + j) * atlas.width + origin[0] + i) * 4
        val d = atlas.data
        if ((d[o + 3].toInt() and 255) < 128) return 0
        return ((d[o].toInt() and 255) shl 16) or
            ((d[o + 1].toInt() and 255) shl 8) or
            (d[o + 2].toInt() and 255)
    }

    /** Yarn and spring have no atlas sprite: a pink ball and a grey coil stand in. */
    private fun proceduralPixel(kind: ToyKind, i: Int, j: Int): Int {
        if (kind == ToyKind.YARN) {
            val dx = i + 0.5f - kind.width / 2f
            val dy = j + 0.5f - kind.height / 2f
            return if (dx * dx + dy * dy <= 4.5f * 4.5f) 0xE070A0 else 0
        }
        return if (j % 3 != 2) 0x8890A0 else 0
    }

    // ---------------------------------------------------------------- PNG

    private fun write(c: Canvas, out: File) {
        out.parentFile?.mkdirs()
        val raw = ByteArray(c.h * (c.w * 3 + 1))
        var o = 0
        for (y in 0 until c.h) {
            raw[o++] = 0
            for (x in 0 until c.w) {
                val v = c.rgb[y * c.w + x]
                raw[o++] = (v shr 16).toByte()
                raw[o++] = (v shr 8).toByte()
                raw[o++] = v.toByte()
            }
        }
        val z = Deflater()
        z.setInput(raw)
        z.finish()
        val packed = ByteArrayOutputStream()
        val buf = ByteArray(1 shl 16)
        while (!z.finished()) packed.write(buf, 0, z.deflate(buf))
        val png = ByteArrayOutputStream()
        png.write(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))
        val ihdr = ByteArrayOutputStream()
        DataOutputStream(ihdr).apply {
            writeInt(c.w)
            writeInt(c.h)
            write(byteArrayOf(8, 2, 0, 0, 0))
        }
        chunk(png, "IHDR", ihdr.toByteArray())
        chunk(png, "IDAT", packed.toByteArray())
        chunk(png, "IEND", ByteArray(0))
        out.writeBytes(png.toByteArray())
    }

    private fun chunk(to: ByteArrayOutputStream, type: String, body: ByteArray) {
        val d = DataOutputStream(to)
        d.writeInt(body.size)
        val t = type.toByteArray(Charsets.ISO_8859_1)
        d.write(t)
        d.write(body)
        val crc = CRC32()
        crc.update(t)
        crc.update(body)
        d.writeInt(crc.value.toInt())
    }
}
