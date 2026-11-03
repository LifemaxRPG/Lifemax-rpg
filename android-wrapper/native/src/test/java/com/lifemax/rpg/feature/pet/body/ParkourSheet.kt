package com.lifemax.rpg.feature.pet.body

import com.lifemax.rpg.feature.pet.art.PngImage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * Test support: a trajectory contact sheet. Draws the house (the three painted backdrops side by
 * side, world x -192 to 384, washed out), the surfaces cats stand on (blue lines), the furniture
 * solids a leap must clear (red outlines) and the feet of real [CatBody]s as they carry out plans:
 * a dot every few ticks, green on the ground and the run's colour in the air, a black cross at
 * every take-off and landing. It is for looking at (a jump chain over the room), not an assertion.
 * Pixels only: no AWT (the unit-test classpath has none), the PNG is written with `java.util.zip`.
 */
object ParkourSheet {
    /** One scenario: a body, the plan it follows, and the colour of its flight (0xRRGGBB). */
    class Run(val body: CatBody, val steps: List<PlanStep>, val color: Int)

    private const val SCALE = 4
    private const val HOUSE_X0 = -192
    private const val ROOM_W = 192
    private const val ROOM_H = 144
    private const val ROOMS = 3
    private const val TICK = 1f / 120f
    private const val WIDTH = ROOMS * ROOM_W * SCALE
    private const val HEIGHT = ROOM_H * SCALE
    private const val WASH = 0.55f
    private const val GROUND_DOT = 0x2A9646
    private const val SURFACE = 0x1446C8
    private const val SOLID = 0xB43C3C

    private class Canvas {
        val rgb = IntArray(WIDTH * HEIGHT) { 0xFFFFFF }

        fun put(x: Int, y: Int, c: Int) {
            if (x in 0 until WIDTH && y in 0 until HEIGHT) rgb[y * WIDTH + x] = c
        }

        fun dot(x: Int, y: Int, r: Int, c: Int) {
            for (dy in -r..r) for (dx in -r..r) put(x + dx, y + dy, c)
        }

        fun line(x0: Int, y0: Int, x1: Int, y1: Int, c: Int, thick: Int = 0) {
            val n = maxOf(Math.abs(x1 - x0), Math.abs(y1 - y0), 1)
            for (i in 0..n) dot(x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * i / n, thick, c)
        }
    }

    /** Run every scenario for [seconds] and write the sheet to [out]. */
    fun render(runs: List<Run>, seconds: Float, out: File) {
        val c = Canvas()
        for ((i, name) in listOf("living", "bedroom", "kitchen").withIndex()) {
            val bytes =
                ParkourSheet::class.java.getResourceAsStream("/pet/room/$name.png")!!.readBytes()
            val room = PngImage.decode(bytes)
            for (y in 0 until ROOM_H * SCALE) for (x in 0 until ROOM_W * SCALE) {
                val o = ((y / SCALE) * ROOM_W + x / SCALE) * 4
                val px = IntArray(3) { (room.data[o + it].toInt() and 255) }
                val wash = IntArray(3) { (px[it] + (255 - px[it]) * WASH).toInt() }
                c.put(i * ROOM_W * SCALE + x, y, (wash[0] shl 16) or (wash[1] shl 8) or wash[2])
            }
        }
        for (s in Solids.all) {
            val x0 = px(s.x0)
            val x1 = px(s.x1)
            val y0 = (s.top * SCALE).toInt()
            val y1 = (s.bottom * SCALE).toInt()
            c.line(x0, y0, x1, y0, SOLID)
            c.line(x0, y1, x1, y1, SOLID)
            c.line(x0, y0, x0, y1, SOLID)
            c.line(x1, y0, x1, y1, SOLID)
        }
        for (id in SurfaceId.entries) {
            val s = World.surface(id)
            val y = (World.groundY(id, s.z) * SCALE).toInt()
            c.line(px(s.x0), y, maxOf(px(s.x1), px(s.x0) + 4), y, SURFACE, 1)
        }
        for (run in runs) trace(c, run, seconds)
        write(c, out)
    }

    private fun trace(c: Canvas, run: Run, seconds: Float) {
        run.body.replacePlan(1, run.steps)
        var wasAir = false
        for (f in 1..Math.round(seconds / TICK)) {
            run.body.step(TICK)
            val p = run.body.pose
            val air = p.y < p.shadowY - 0.5f
            val x = px(p.x)
            val y = (p.y * SCALE).toInt()
            if (f % 3 == 0) c.dot(x, y, 2, if (air) run.color else GROUND_DOT)
            if (air != wasAir) {
                c.line(x - 8, y - 8, x + 8, y + 8, 0, 1)
                c.line(x - 8, y + 8, x + 8, y - 8, 0, 1)
                wasAir = air
            }
        }
    }

    private fun px(x: Float) = ((x - HOUSE_X0) * SCALE).toInt()

    private fun write(c: Canvas, out: File) {
        val raw = ByteArray(HEIGHT * (WIDTH * 3 + 1))
        var o = 0
        for (y in 0 until HEIGHT) {
            raw[o++] = 0
            for (x in 0 until WIDTH) {
                val v = c.rgb[y * WIDTH + x]
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
            writeInt(WIDTH)
            writeInt(HEIGHT)
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
