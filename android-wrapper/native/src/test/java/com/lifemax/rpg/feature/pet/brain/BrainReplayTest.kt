package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.TraceSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replays (plan 1.2, 4.1): a morning of two cats gives the same trace (every activity begun, and
 * every pose field once a second, bitwise) at 60 and 120 Hz, on jittered frames and across a 2 s
 * stall; the same cats replay the same day, other cats live another; and a reflex interrupts and
 * the interrupted activity resumes where it was, for the time it had left.
 */
class BrainReplayTest {
    private val cats =
        listOf(
            CatDays.cat("Ted", CatDays.PLAYFUL, id = "cmu9rkw00"),
            CatDays.cat("Lily", CatDays.SHY, id = "cmv2cdxc0", slot = 1),
        )
    private val start = CatDays.MIDNIGHT + 6 * CatDays.HOUR
    private val acts =
        listOf(
            OwnerAct(30 * CatDays.MINUTE, "open"),
            OwnerAct(40 * CatDays.MINUTE, "feed"),
            OwnerAct(55 * CatDays.MINUTE, "close"),
        )

    private fun trace(
        cats: List<CatProfile> = this.cats,
        frameMs: Long = 16,
        frames: LongArray? = null,
    ): String {
        val out = StringBuilder()
        val last = arrayOfNulls<Doing>(cats.size)
        CatDays.run(cats, acts, hours = 2.5, start = start, frameMs = frameMs, frames = frames) {
            world,
            step,
            think ->
            for (i in cats.indices) {
                val d = world.now(i)
                if (think && d !== last[i]) {
                    last[i] = d
                    out.append(step).append(' ').append(i).append(' ').append(d).append('\n')
                }
                if (step % 120L == 0L)
                    out.append(TraceSupport.fingerprint(world.minds[i].body!!.pose)).append('\n')
            }
        }
        return out.toString()
    }

    @Test
    fun theSameMorningAtAnyFrameRate() {
        val at60 = trace(frameMs = 16)
        assertTrue(at60.lines().size > 10_000)
        assertEquals("120 Hz", at60, trace(frameMs = 8))
        assertEquals("jittered", at60, trace(frames = longArrayOf(11, 33, 7, 25, 16, 30, 9)))
        assertEquals(
            "a 2 s stall",
            at60,
            trace(frames = LongArray(90) { if (it == 89) 2000L else 16L }),
        )
    }

    @Test
    fun theSameCatsReplayTheirDayAndOthersLiveAnother() {
        val a = trace()
        assertEquals(a, trace())
        assertNotEquals(a, trace(cats.map { it.copy(id = it.id + "x") }))
    }

    @Test
    fun aMealInterruptsSleepAndTheSleepResumes() {
        val cat = CatDays.cat("Ted", CatDays.LAZY)
        var resumedFor = 0.0
        val seen = ArrayList<Pair<Long, Doing>>()
        val world =
            CatDays.run(
                listOf(cat),
                listOf(OwnerAct(10 * CatDays.MINUTE, "feed")),
                hours = 1.0,
                start = CatDays.MIDNIGHT + 13 * CatDays.HOUR,
                setup = { it.minds[0].force("sleep", Spots.BED, 40.0 * 60) },
            ) { world, step, think ->
                if (!think) return@run
                val d = world.now(0)
                if (seen.isEmpty() || seen.last().second !== d) {
                    seen += step to d
                    if (d.why == "resume") resumedFor = world.minds[0].runner.seconds
                }
            }
        val names = seen.map { it.second.activity }
        assertEquals(listOf("sleep", "eat", "sleep"), names.take(3))
        val (eatAt, eat) = seen[1]
        // the reflex comes within one think quantum of the cue (10 min = 72 000 sub-steps)
        assertTrue("eat began at $eatAt", eatAt in 72_000L..72_030L)
        assertEquals("fed", eat.why)
        val resumed = seen[2].second
        assertEquals("resume", resumed.why)
        assertEquals("bed", resumed.place)
        // it had about 30 min of its 40 left; the resumed sleep plays that, not a fresh nap
        // (read from the changes seen: the explain ring holds only the last picks, and the cat now
        // picks something new every half minute)
        val explained = seen.map { it.second.why }
        assertTrue(explained.toString(), explained.containsAll(listOf("forced", "fed", "resume")))
        assertTrue("resumed for $resumedFor s", resumedFor in 25.0 * 60..31.0 * 60)
    }
}
