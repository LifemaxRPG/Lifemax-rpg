package com.lifemax.rpg.feature.pet.brain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The day viewer runs the real brain ([CatWorld]) through a day: lines, hour strips, totals and an
 * SVG, the same at any frame rate. With `CAT_DAY` naming a directory it runs the six cases of
 * [CatDays.CASES] for 24 hours and writes `<case>.txt` and `<case>.svg` there (look at them):
 * CAT_DAY=/tmp/cat-days ./gradlew :app:testDebugUnitTest --tests '*CatDayViewerTest*'
 */
class CatDayViewerTest {
    @Test
    fun theViewerPrintsAMorning() {
        val d = CatDays.viewerDay(CatDays.case("two cats"), hours = 10)
        assertEquals(2, d.strips.size)
        assertTrue(d.strips.all { it.length == "Ted   |".length + 10 + 1 })
        // both cats start the night asleep and wake with the routine
        val cats = d.lines.filter { !it.contains(" owner ") }
        assertTrue(cats[0], cats[0].startsWith("00:00 Ted   sleep"))
        assertTrue(d.lines.any { it.contains("Ted   sleep→waking routine") })
        assertTrue(d.lines.any { it.contains("Lily  sleep→waking routine") })
        assertTrue(d.lines.any { it.contains("→eat  bowl") && it.contains("why: fed") })
        assertTrue(d.strips[0].substring(7, 12).all { it == 'S' })
        assertTrue(d.svg.startsWith("<svg") && d.svg.contains("<title>sleep</title>"))
        assertTrue(d.totals[0].startsWith("Ted   sleep"))
    }

    @Test
    fun theDayIsTheSameAtAnyFrameRate() {
        val case = CatDays.case("two cats")
        val at60 = CatDays.viewerDay(case, hours = 9, frameMs = 16)
        assertEquals(at60, CatDays.viewerDay(case, hours = 9, frameMs = 8))
        assertEquals(at60, CatDays.viewerDay(case, hours = 9, frameMs = 33))
    }

    @Test
    fun writesTheDaysWhenAsked() {
        val dir = System.getenv("CAT_DAY")
        assumeTrue("set CAT_DAY to a directory to write the days", dir != null)
        val out = File(dir!!).also { it.mkdirs() }
        for (case in CatDays.CASES) {
            val d = CatDays.viewerDay(case)
            val name = case.name.replace(' ', '-')
            File(out, "$name.txt").writeText("# ${case.name}\n" + d.text)
            File(out, "$name.svg").writeText(d.svg)
        }
    }
}
