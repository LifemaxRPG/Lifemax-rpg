package com.lifemax.rpg.feature.pet.presentation

import com.lifemax.rpg.feature.pet.body.CatBody
import com.lifemax.rpg.feature.pet.body.CatPose
import com.lifemax.rpg.feature.pet.body.PlanStep
import com.lifemax.rpg.feature.pet.presentation.SteadinessProbe.Cause
import com.lifemax.rpg.feature.pet.presentation.SteadinessProbe.Look
import com.lifemax.rpg.feature.pet.presentation.SteadinessProbe.Renderer
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * A cat turning round step by step is drawn as steadily as a resting one (the rules of
 * docs/specs/PET_SPRITE_STEADINESS.md, measured the way [SteadyCatTest] does): while the turn row
 * is on screen the drawing changes only when its frame (or the way it faces) changes, or on a
 * breath or rig step; nothing scales or tilts the coat under it, and no step changes with no cause.
 * With `LMA_TURN_STRIPS=<dir>` it also writes frame strips of the turns to look at.
 */
class TurnSteadyTest {
    private val s = SteadinessScenes.CARD_SCALE

    /**
     * Two turns on the spot, then a walk that is sent back (a walking turn), sent forward again at
     * a gallop (a walking turn into a run) and sent back while galloping (a skid and a turn).
     */
    private val orders =
        listOf(
            0f to listOf(PlanStep.Face(-1)),
            1f to listOf(PlanStep.Face(1)),
            2f to listOf(PlanStep.Walk(300f)),
            4.5f to listOf(PlanStep.Walk(40f)),
            7f to listOf(PlanStep.Walk(330f, run = true)),
            9f to listOf(PlanStep.Walk(0f, run = true)),
        )

    /** Play [orders] for [look], drawing every frame at 120 Hz, and hand each pose to [each]. */
    private fun film(
        look: Look,
        each: (CatPose, SteadinessProbe.Run) -> Unit,
    ): SteadinessProbe.Run {
        val body = CatBody(seed = 5, startX = 60f)
        val run = SteadinessProbe.Run(look, Renderer.STEADY, s, 1)
        val dt = 1f / SteadinessProbe.HZ
        var next = 0
        repeat((SCENE_S * SteadinessProbe.HZ).toInt()) { tick ->
            if (next < orders.size && tick * dt >= orders[next].first) {
                body.replacePlan(next + 1, orders[next].second)
                next++
            }
            body.step(dt)
            run.frame(body.pose)
            each(body.pose, run)
        }
        return run
    }

    @Test
    fun aTurningCatChangesOnlyWithItsFrames() {
        for (look in SteadinessScenes.looks) {
            val inTurn = ArrayList<Boolean>()
            val run = film(look) { pose, _ -> inTurn.add(pose.clipName == "turn") }
            assertTrue("${look.label} turned", inTurn.count { it } > TURNS * MIN_TURN_TICKS)
            for (i in run.changed.indices) {
                assertTrue(
                    "${look.label} step $i changed with no cause",
                    run.causes[i] != Cause.NONE || run.changed[i] == 0,
                )
                // step i compares frame i + 1 with frame i
                val turning = inTurn[i] && inTurn[i + 1]
                if (turning && run.changed[i] > 0)
                    assertTrue(
                        "${look.label} step $i: the turn row changed by ${run.causes[i]}",
                        run.causes[i] == Cause.ART ||
                            run.causes[i] == Cause.BREATH ||
                            run.causes[i] == Cause.RIG,
                    )
            }
            val turnArt =
                run.changed.indices.count {
                    inTurn[it] && inTurn[it + 1] && run.causes[it] == Cause.ART
                }
            assertEquals("${look.label}: four frame changes per turn", TURNS * 4, turnArt)
        }
    }

    @Test
    fun writeTurnStrips() {
        val dir = System.getenv("LMA_TURN_STRIPS")
        assumeTrue(dir != null)
        val looks =
            listOf(SteadinessScenes.ginger, SteadinessScenes.tuxedoKitten, SteadinessScenes.calico)
        for (look in looks) {
            val cells = ArrayList<Cell>()
            var was = ""
            var before: Cell? = null
            film(look) { pose, run ->
                val key = "${pose.clipName}#${pose.frame}${if (pose.flip) "<" else ">"}"
                val cell =
                    Cell(
                        run.lastRaster,
                        Math.round(pose.x * s),
                        Math.round((pose.y + pose.bob) * s),
                    )
                val turning = pose.clipName == "turn"
                if (key != was) {
                    if (turning && !was.startsWith("turn")) before?.let { cells.add(it) }
                    if (turning || was.startsWith("turn")) cells.add(cell)
                }
                before = cell
                was = key
            }
            write(cells, File(dir, "turn-${look.label.replace(' ', '_')}.png"))
        }
    }

    /** One drawn frame and where its paws were. */
    private class Cell(val r: Raster, val footX: Int, val footY: Int)

    /** Lay the frames side by side, each in its own cell, paws on one ground line. */
    private fun write(cells: List<Cell>, file: File) {
        val w = CELL_W * s
        val h = CELL_H * s
        val perLine = 6
        val lines = (cells.size + perLine - 1) / perLine
        val iw = w * perLine
        val ih = h * lines
        val img = IntArray(iw * ih)
        for (y in 0 until ih) for (x in 0 until iw) {
            val checker = ((x / (s * 2)) + (y / (s * 2))) % 2 == 0
            img[y * iw + x] = if (checker) BG_A else BG_B
        }
        for ((n, cell) in cells.withIndex()) {
            val r = cell.r
            val ox = (n % perLine) * w
            val oy = (n / perLine) * h
            for (j in 0 until r.h) for (i in 0 until r.w) {
                val c = r.px[j * r.w + i]
                if (c ushr 24 == 0) continue
                val x = ox + w / 2 + (r.x0 + i - cell.footX)
                val y = oy + h - GROUND * s + (r.y0 + j - cell.footY)
                if (x in ox until ox + w && y in oy until oy + h) img[y * iw + x] = c
            }
        }
        file.parentFile.mkdirs()
        file.writeBytes(png(iw, ih, img))
    }

    /** A plain RGB PNG of [rgb] (no AWT in the unit tests). */
    private fun png(w: Int, h: Int, rgb: IntArray): ByteArray {
        val raw = ByteArray(h * (w * 3 + 1))
        var o = 0
        for (y in 0 until h) {
            raw[o++] = 0
            for (x in 0 until w) {
                val v = rgb[y * w + x]
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
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))
        val ihdr = ByteArrayOutputStream()
        DataOutputStream(ihdr).apply {
            writeInt(w)
            writeInt(h)
            write(byteArrayOf(8, 2, 0, 0, 0))
        }
        chunk(out, "IHDR", ihdr.toByteArray())
        chunk(out, "IDAT", packed.toByteArray())
        chunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
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

    private companion object {
        const val SCENE_S = 12f
        const val TURNS = 5
        const val MIN_TURN_TICKS = 25
        const val CELL_W = 80
        const val CELL_H = 56
        const val GROUND = 3
        const val BG_A = 0xFF2E3440.toInt()
        const val BG_B = 0xFF353B48.toInt()
    }
}
