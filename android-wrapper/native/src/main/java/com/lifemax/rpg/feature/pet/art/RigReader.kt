package com.lifemax.rpg.feature.pet.art

/**
 * The rig of one kind of sheet (`adult`, `st0`..`st5`, `depth`, `dst0`..`dst5`), read from the file
 * `tools/rig_pack.js` writes (the layout is documented there). Frames are decoded the first time
 * they are asked for and kept, so drawing a frame twice costs a lookup.
 *
 * @param bytes the whole file
 */
class RigKind(private val bytes: ByteArray) {
    private var at = 0

    /** Frame width and height the rig was cut for. */
    val frameWidth: Int
    val frameHeight: Int

    private val names: Array<String>
    private val animFlags: IntArray
    private val offsets: Array<IntArray>
    private val base: Int
    private val frames: Array<Array<RigFrame?>>

    init {
        require(u8() == VERSION) { "unknown rig format" }
        frameWidth = u8()
        frameHeight = u8()
        val count = u8()
        names = Array(count) { "" }
        animFlags = IntArray(count)
        offsets = Array(count) { IntArray(0) }
        for (a in 0 until count) {
            val len = u8()
            names[a] = String(bytes, at, len, Charsets.US_ASCII)
            at += len
            animFlags[a] = u8()
            val n = u8()
            offsets[a] = IntArray(n) { u32() }
        }
        base = at
        frames = Array(count) { arrayOfNulls<RigFrame>(offsets[it].size) }
    }

    /** The index of the animation called [name] in this kind, or -1. */
    fun indexOf(name: String): Int = names.indexOf(name)

    /** How many frames the animation at [anim] has. */
    fun frameCount(anim: Int): Int = offsets[anim].size

    /** The rig of [frame] (wrapped like the art's `% n`) of the animation at [anim]. */
    fun frame(anim: Int, frame: Int): RigFrame {
        val f = Math.floorMod(frame, offsets[anim].size)
        return frames[anim][f] ?: decode(anim, f).also { frames[anim][f] = it }
    }

    private fun decode(anim: Int, f: Int): RigFrame {
        at = base + offsets[anim][f]
        val flags = u8()
        val foot = u8()
        var legFrom = -1
        var legTo = -1
        var torsoFrom = -1
        var torsoTo = -1
        if (flags and LEG != 0) {
            legFrom = u8()
            legTo = u8()
        }
        if (flags and TORSO != 0) {
            torsoFrom = u8()
            torsoTo = u8()
        }
        var headX = Double.NaN
        var headY = Double.NaN
        if (flags and HEAD != 0) {
            headX = tenths()
            headY = tenths()
        }
        val ears = Array(u8()) { part() }
        val whiskers = Array(u8()) { part() }
        val tail =
            if (flags and TAIL != 0 && animFlags[anim] and ANIM_TAIL != 0) tail()
            else skipTail(flags)
        return RigFrame(
            foot,
            legFrom,
            legTo,
            torsoFrom,
            torsoTo,
            animFlags[anim] and ANIM_LEGS != 0,
            animFlags[anim] and ANIM_BODY != 0,
            headX,
            headY,
            ears,
            whiskers,
            tail,
        )
    }

    /** A tail the animation does not use as a part is still in the file; read past it. */
    private fun skipTail(flags: Int): RigTail? {
        if (flags and TAIL != 0) tail()
        return null
    }

    private fun part(): RigPart {
        val bx = tenths() + HALF
        val by = tenths() + HALF
        val tx = tenths() + HALF
        val ty = tenths() + HALF
        return RigPart(runs(), bx, by, tx, ty)
    }

    private fun tail(): RigTail {
        val n = u8()
        val x = DoubleArray(n)
        val y = DoubleArray(n)
        for (i in 0 until n) {
            x[i] = tenths() + HALF
            y[i] = tenths() + HALF
        }
        val cum = DoubleArray(n)
        for (i in 1 until n) cum[i] = cum[i - 1] + hypot(x[i] - x[i - 1], y[i] - y[i - 1])
        return RigTail(runs(), x, y, cum)
    }

    /** The pixel indices of the runs, in the file's order (the page's `petRigIdx`). */
    private fun runs(): IntArray {
        val n = u16()
        val triples = IntArray(n * 3) { u8() }
        var total = 0
        for (i in 0 until n) total += triples[i * 3 + 2] - triples[i * 3 + 1] + 1
        val out = IntArray(total)
        var k = 0
        for (i in 0 until n) {
            val row = triples[i * 3]
            for (x in triples[i * 3 + 1]..triples[i * 3 + 2]) out[k++] = row * frameWidth + x
        }
        return out
    }

    private fun u8(): Int = bytes[at++].toInt() and 255

    private fun u16(): Int = u8() or (u8() shl 8)

    private fun u32(): Int = u16() or (u16() shl 16)

    private fun tenths(): Double = u16().toShort() / 10.0

    private companion object {
        const val VERSION = 1
        const val LEG = 1
        const val TORSO = 2
        const val TAIL = 4
        const val HEAD = 8
        const val ANIM_LEGS = 1
        const val ANIM_BODY = 2
        const val ANIM_TAIL = 4

        /** The page takes the centre of a pixel for its coordinates. */
        const val HALF = 0.5
    }
}

/**
 * The rigs of all kinds, read on demand through [read] (a kind's file name without the extension,
 * e.g. `adult`, to its bytes, or null). A kind that cannot be read or decoded is remembered as
 * missing, so a cat of that kind is drawn without a rig.
 */
class RigLibrary(private val read: (String) -> ByteArray?) {
    private val kinds = HashMap<String, RigKind?>()

    /** The rig of the sheet kind with key [key] (see [SheetKind.key]), or null. */
    fun kind(key: String): RigKind? =
        kinds.getOrPut(key) { runCatching { read(key)?.let { RigKind(it) } }.getOrNull() }
}

/**
 * The page's `Math.hypot` for two numbers: the larger magnitude times the root of the sum of the
 * squares of both divided by it. The page's engine computes it so, which can differ in the last bit
 * from a plain square root of the sum; the composer's pixel choices sit on exact ties often enough
 * (pixel centres against tenths of a pixel) that the last bit matters.
 */
internal fun hypot(x: Double, y: Double): Double {
    val ax = Math.abs(x)
    val ay = Math.abs(y)
    val max = Math.max(ax, ay)
    if (max == 0.0 || max.isInfinite()) return max
    val a = ax / max
    val b = ay / max
    return Math.sqrt(a * a + b * b) * max
}
