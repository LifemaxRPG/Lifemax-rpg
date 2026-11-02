package com.lifemax.rpg.feature.pet.body

import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.fail

/**
 * Test support: runs a [CatBody] at a fixed host frame rate and prints what it did as a small text
 * "animation sheet", one row per sample. A human can read the sheet to eyeball a motion (frames
 * walking through the clip, the squash dipping, the mirror at the middle of a turn), and the same
 * text is stored as a golden file so an animation change shows up as a reviewable diff.
 *
 * Regenerate the goldens with `RECORD=1` in the environment (the repo's convention for goldens).
 */
object TraceSupport {
    /** One sample row. */
    fun row(t: Float, p: CatPose): String =
        String.format(
            Locale.ROOT,
            "%6.3f x=%7.2f y=%6.2f z=%5.1f %-10s f%d sx=%.3f sy=%.3f rot=%+.3f%s",
            t,
            p.x,
            p.y,
            p.z,
            p.clipName,
            p.frame,
            p.scaleX,
            p.scaleY,
            p.rotation,
            if (p.flip) " flip" else "",
        )

    /** A row about the secondary motion instead: ears, tail, whiskers, breath, look. */
    fun lifeRow(t: Float, p: CatPose): String =
        String.format(
            Locale.ROOT,
            "%6.3f %-10s f%d ear=%+.2f/%+.2f flat=%.2f tail=%+.2f/%+.2f whisk=%+.2f breath=%.4f look=%+.2f,%+.2f rot=%+.3f",
            t,
            p.clipName,
            p.frame,
            p.ear[0],
            p.ear[1],
            p.earFlat,
            p.tail[0],
            p.tail[p.tail.size - 1],
            p.whisker,
            p.breath,
            p.lookX,
            p.lookY,
            p.rotation,
        )

    /** One event row. */
    fun eventRow(t: Float, p: CatPose, i: Int): String =
        String.format(
            Locale.ROOT,
            "%6.3f   ! %s plan=%d step=%d%s",
            t,
            p.events.type(i),
            p.events.planId(i),
            p.events.stepIndex(i),
            if (p.events.isLastStep(i)) " last" else "",
        )

    /**
     * Step [body] for [seconds] at [hz] host frames per second and return the sheet: a sample row
     * every [sampleEveryFrames] frames, an event row whenever events fire. [before] runs ahead of
     * each frame with the frame number, to submit plans or reactions at chosen moments.
     */
    fun record(
        body: CatBody,
        seconds: Float,
        hz: Int = 60,
        sampleEveryFrames: Int = 6,
        format: (Float, CatPose) -> String = ::row,
        before: (Int) -> Unit = {},
    ): String {
        val out = StringBuilder()
        val frames = Math.round(seconds * hz)
        val dt = 1f / hz
        out.append(format(0f, body.pose)).append('\n')
        for (frame in 1..frames) {
            before(frame)
            body.step(dt)
            val t = frame.toFloat() / hz
            val p = body.pose
            for (i in 0 until p.events.count) out.append(eventRow(t, p, i)).append('\n')
            if (frame % sampleEveryFrames == 0) out.append(format(t, p)).append('\n')
        }
        return out.toString()
    }

    /** Compare [actual] with the stored golden [name], or rewrite it when `RECORD=1`. */
    fun checkGolden(name: String, actual: String) {
        if (System.getenv("RECORD") == "1") {
            val file = File(goldenDir(), "$name.txt")
            file.writeText(actual)
            return
        }
        val stream = TraceSupport::class.java.getResourceAsStream("/pet-traces/$name.txt")
        if (stream == null) {
            fail("missing golden pet-traces/$name.txt; run with RECORD=1")
            return
        }
        val expected = stream.bufferedReader().use { it.readText() }
        if (expected != actual) fail(firstDifference(name, expected, actual))
        assertEquals(expected, actual)
    }

    private fun firstDifference(name: String, expected: String, actual: String): String {
        val e = expected.lines()
        val a = actual.lines()
        for (i in 0 until maxOf(e.size, a.size)) {
            val want = e.getOrNull(i)
            val got = a.getOrNull(i)
            if (want != got) {
                return "trace $name differs at line ${i + 1} (RECORD=1 regenerates):\n" +
                    "  expected: $want\n  actual:   $got"
            }
        }
        return "trace $name differs"
    }

    private fun goldenDir(): File {
        val start = File(System.getProperty("user.dir")).absoluteFile
        val dir =
            generateSequence(start) { it.parentFile }
                .map { File(it, "android-wrapper/native/src/test/resources/pet-traces") }
                .firstOrNull { it.isDirectory }
                ?: error("pet-traces directory not found above $start")
        return dir
    }

    /** A cheap fingerprint of the pose, for equality across runs without comparing every field. */
    fun fingerprint(p: CatPose): String {
        val b = StringBuilder()
        b.append(p.x).append(',').append(p.y).append(',').append(p.z).append(',')
        b.append(p.clip).append(',').append(p.frame).append(',').append(p.framePhase).append(',')
        b.append(p.scaleX).append(',').append(p.scaleY).append(',').append(p.rotation).append(',')
        b.append(p.bob).append(',').append(p.ear[0]).append(',').append(p.ear[1]).append(',')
        b.append(p.earFlat).append(',').append(p.whisker).append(',').append(p.breath).append(',')
        for (v in p.tail) b.append(v).append(',')
        b.append(p.lookX).append(',').append(p.lookY).append(',').append(p.facing)
        return b.toString()
    }
}
