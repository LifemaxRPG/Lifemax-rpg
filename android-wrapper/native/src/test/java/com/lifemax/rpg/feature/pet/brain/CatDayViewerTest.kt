package com.lifemax.rpg.feature.pet.brain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The day viewer runs a brain through a day: the stand-in's timetable comes out as lines and hour
 * strips, the same at any frame rate. With `CAT_DAY` naming a directory it also prints the day and
 * writes day.txt and day.svg there (look at them; nothing more is asserted), PARKOUR_SHEET-style:
 * CAT_DAY=/tmp/cat-days ./gradlew :app:testDebugUnitTest --tests '*CatDayViewerTest*'
 */
class CatDayViewerTest {
    private val midnight = 1_793_318_400_000L // 30 Oct 2026 00:00 UTC
    private val acts = listOf(OwnerAct(8 * HOUR, "feed"), OwnerAct(18 * HOUR, "feed"))

    private fun day(frameMs: Long = 16) =
        CatDayViewer.simulate(midnight, frameMs = frameMs, acts = acts) {
            StandInBrain(it, listOf("Ted", "Lily"))
        }

    @Test
    fun theViewerPrintsADay() {
        val d = day()
        assertEquals(2, d.strips.size)
        // the stand-in's timetable, hour by hour (16:00 is a wander, which has no letter)
        assertEquals("Ted   |SSSSSSSLLLWWGSSS.KKLLLSS|", d.strips[0])
        assertTrue(d.strips.all { it.length == "Ted   |".length + 24 + 1 })
        assertTrue(d.lines.any { it.startsWith("07:00 Ted   sleep→waking routine  bed") })
        assertTrue(d.lines.any { it.startsWith("07:30 Lily  sleep→waking routine") })
        assertTrue(d.lines.any { it == "08:00 owner feed" })
        assertTrue(d.lines.any { it.startsWith("08:00 Ted   loaf→eat  bowl") })
        assertTrue(d.svg.startsWith("<svg") && d.svg.contains("<title>zoomies</title>"))
    }

    @Test
    fun theDayIsTheSameAtAnyFrameRate() {
        val at60 = day(16)
        assertEquals(at60, day(8))
        assertEquals(at60, day(33))
    }

    @Test
    fun writesTheDayWhenAsked() {
        val dir = System.getenv("CAT_DAY")
        assumeTrue("set CAT_DAY to a directory to write the day", dir != null)
        val out = File(dir!!).also { it.mkdirs() }
        val d = day()
        print(d.text)
        File(out, "day.txt").writeText(d.text)
        File(out, "day.svg").writeText(d.svg)
    }

    private companion object {
        const val HOUR = 3_600_000L
    }
}
