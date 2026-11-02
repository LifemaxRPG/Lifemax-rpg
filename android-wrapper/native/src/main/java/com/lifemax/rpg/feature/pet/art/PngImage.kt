package com.lifemax.rpg.feature.pet.art

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/**
 * A decoded 8-bit PNG: [channels] is 1 (grey) or 4 (RGBA), [data] holds `height * width * channels`
 * bytes, row by row.
 *
 * This is a tiny decoder for exactly the files the cat's art ships as (see
 * `tools/gen_pet_native_assets.js`): 8 bits per channel, grey or RGBA, not interlaced. It uses only
 * `java.util.zip`, so the art can be decoded and tested on the JVM without any Android class.
 */
class PngImage(val width: Int, val height: Int, val channels: Int, val data: ByteArray) {

    companion object {
        private val SIGNATURE = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
        private const val GREY = 0
        private const val RGBA = 6

        /**
         * Decodes [bytes], or throws [IllegalArgumentException] for anything but 8-bit grey/RGBA,
         * non-interlaced.
         */
        fun decode(bytes: ByteArray): PngImage {
            require(bytes.size > 8 && bytes.copyOfRange(0, 8).contentEquals(SIGNATURE)) {
                "not a PNG"
            }
            var width = 0
            var height = 0
            var channels = 0
            val compressed = ByteArrayOutputStream()
            var pos = 8
            while (pos + 8 <= bytes.size) {
                val length = int(bytes, pos)
                val type = String(bytes, pos + 4, 4, Charsets.ISO_8859_1)
                val body = pos + 8
                when (type) {
                    "IHDR" -> {
                        width = int(bytes, body)
                        height = int(bytes, body + 4)
                        val depth = bytes[body + 8].toInt()
                        val colour = bytes[body + 9].toInt()
                        val interlace = bytes[body + 12].toInt()
                        channels =
                            when {
                                depth != 8 || interlace != 0 -> 0
                                colour == GREY -> 1
                                colour == RGBA -> 4
                                else -> 0
                            }
                        require(channels != 0) {
                            "unsupported PNG: need 8-bit grey or RGBA, not interlaced"
                        }
                    }
                    "IDAT" -> compressed.write(bytes, body, length)
                }
                pos = body + length + 4
            }
            require(channels != 0) { "PNG has no IHDR" }
            return PngImage(
                width,
                height,
                channels,
                unfilter(inflate(compressed.toByteArray()), width, height, channels),
            )
        }

        private fun int(b: ByteArray, at: Int): Int =
            ((b[at].toInt() and 255) shl 24) or
                ((b[at + 1].toInt() and 255) shl 16) or
                ((b[at + 2].toInt() and 255) shl 8) or
                (b[at + 3].toInt() and 255)

        private fun inflate(packed: ByteArray): ByteArray {
            val inflater = Inflater()
            inflater.setInput(packed)
            val out = ByteArrayOutputStream(packed.size * 4)
            val buffer = ByteArray(1 shl 16)
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                require(n > 0 || !inflater.needsInput()) { "truncated PNG data" }
                out.write(buffer, 0, n)
            }
            inflater.end()
            return out.toByteArray()
        }

        /**
         * Undoes the five PNG row filters; [raw] is one filter byte followed by the row, for every
         * row.
         */
        private fun unfilter(raw: ByteArray, width: Int, height: Int, channels: Int): ByteArray {
            val stride = width * channels
            require(raw.size >= height * (stride + 1)) { "PNG data is shorter than its size" }
            val out = ByteArray(height * stride)
            for (y in 0 until height) {
                val filter = raw[y * (stride + 1)].toInt()
                val src = y * (stride + 1) + 1
                val dst = y * stride
                for (i in 0 until stride) {
                    val left = if (i >= channels) out[dst + i - channels].toInt() and 255 else 0
                    val up = if (y > 0) out[dst - stride + i].toInt() and 255 else 0
                    val upLeft =
                        if (y > 0 && i >= channels) out[dst - stride + i - channels].toInt() and 255
                        else 0
                    val predicted =
                        when (filter) {
                            0 -> 0
                            1 -> left
                            2 -> up
                            3 -> (left + up) shr 1
                            4 -> paeth(left, up, upLeft)
                            else -> throw IllegalArgumentException("bad PNG filter $filter")
                        }
                    out[dst + i] = ((raw[src + i].toInt() and 255) + predicted).toByte()
                }
            }
            return out
        }

        private fun paeth(a: Int, b: Int, c: Int): Int {
            val p = a + b - c
            val pa = kotlin.math.abs(p - a)
            val pb = kotlin.math.abs(p - b)
            val pc = kotlin.math.abs(p - c)
            return if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
        }
    }
}
