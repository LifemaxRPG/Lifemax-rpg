package com.lifemax.rpg.feature.pet.art

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The PNG decoder on small synthetic images, one per row filter, plus the sheet decoder's refusals.
 */
class PngImageTest {

    private fun chunk(out: ByteArrayOutputStream, type: String, body: ByteArray) {
        fun int(v: Int) =
            byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
        out.write(int(body.size))
        val typed = type.toByteArray(Charsets.ISO_8859_1) + body
        out.write(typed)
        out.write(int(CRC32().also { it.update(typed) }.value.toInt()))
    }

    /**
     * Encodes [rows] (each `width * channels` bytes) with the filter in [filters] for every row.
     */
    private fun png(
        width: Int,
        channels: Int,
        rows: List<IntArray>,
        filters: List<Int>,
    ): ByteArray {
        val raw = ByteArrayOutputStream()
        for ((y, row) in rows.withIndex()) {
            raw.write(filters[y])
            for (i in row.indices) {
                val left = if (i >= channels) row[i - channels] else 0
                val up = if (y > 0) rows[y - 1][i] else 0
                val upLeft = if (y > 0 && i >= channels) rows[y - 1][i - channels] else 0
                val predicted =
                    when (filters[y]) {
                        0 -> 0
                        1 -> left
                        2 -> up
                        3 -> (left + up) / 2
                        else -> {
                            val p = left + up - upLeft
                            val pa = Math.abs(p - left)
                            val pb = Math.abs(p - up)
                            val pc = Math.abs(p - upLeft)
                            if (pa <= pb && pa <= pc) left else if (pb <= pc) up else upLeft
                        }
                    }
                raw.write((row[i] - predicted) and 255)
            }
        }
        val deflater = Deflater()
        deflater.setInput(raw.toByteArray())
        deflater.finish()
        val packed = ByteArray(raw.size() + 64)
        val n = deflater.deflate(packed)
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))
        val header = ByteArrayOutputStream()
        for (v in intArrayOf(width, rows.size)) header.write(
            byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
        )
        header.write(byteArrayOf(8, if (channels == 1) 0 else 6, 0, 0, 0))
        chunk(out, "IHDR", header.toByteArray())
        chunk(out, "IDAT", packed.copyOf(n))
        chunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    private val grey =
        listOf(
            intArrayOf(0, 10, 20, 30, 250),
            intArrayOf(5, 200, 7, 0, 99),
            intArrayOf(210, 210, 1, 2, 3),
            intArrayOf(0, 255, 0, 255, 0),
            intArrayOf(9, 8, 7, 6, 5),
        )

    @Test
    fun everyRowFilterDecodesToTheSamePixels() {
        for (filter in 0..4) {
            val image = PngImage.decode(png(5, 1, grey, List(5) { filter }))
            assertEquals(5, image.width)
            assertEquals(5, image.height)
            assertEquals(1, image.channels)
            val got = IntArray(25) { image.data[it].toInt() and 255 }
            assertArrayEquals("filter $filter", grey.flatMap { it.toList() }.toIntArray(), got)
        }
    }

    @Test
    fun rowsMayMixFiltersAndRgbaDecodes() {
        val rgba =
            listOf(
                intArrayOf(1, 2, 3, 255, 4, 5, 6, 0),
                intArrayOf(9, 8, 7, 128, 200, 100, 50, 255),
                intArrayOf(0, 0, 0, 0, 255, 255, 255, 255),
            )
        val image = PngImage.decode(png(2, 4, rgba, listOf(4, 3, 1)))
        assertEquals(4, image.channels)
        assertArrayEquals(
            rgba.flatMap { it.toList() }.toIntArray(),
            IntArray(24) { image.data[it].toInt() and 255 },
        )
    }

    @Test
    fun sheetDecoderGivesTheIndicesAndRefusesColour() {
        val sheet = SheetDecoder.decode(png(5, 1, grey, List(5) { 4 }))
        assertEquals(25, sheet.indices.size)
        assertEquals(250, sheet.indices[4])
        assertEquals(255, sheet.indices[16])
        val colour = png(2, 4, listOf(intArrayOf(0, 0, 0, 0, 0, 0, 0, 0)), listOf(0))
        try {
            SheetDecoder.decode(colour)
            throw AssertionError("an RGBA PNG is not a cat sheet")
        } catch (expected: IllegalArgumentException) {
            // refused
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun bytesThatAreNotAPngAreRefused() {
        PngImage.decode(ByteArray(40))
    }
}
