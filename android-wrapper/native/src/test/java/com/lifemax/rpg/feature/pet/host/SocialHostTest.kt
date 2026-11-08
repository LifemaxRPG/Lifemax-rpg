package com.lifemax.rpg.feature.pet.host

import com.lifemax.rpg.feature.pet.brain.Catalogue
import com.lifemax.rpg.feature.pet.brain.Social
import com.lifemax.rpg.feature.pet.world.FakePage
import com.lifemax.rpg.feature.pet.world.FakeTimeSource
import com.lifemax.rpg.feature.pet.world.WorldFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pair's moments end to end (CB5, `social`, pet.social.v1): two cats in the real host against
 * the page through whole days with the page's meals and an owner's game; each completed moment
 * reaches the page, which records it by its own rules (the friendship within the day's cap, the
 * counts, the meeting), so the Together card's counts and its last line move; an older page (no
 * `met` in its pair) is told nothing; a refused save is retried until stored, once.
 */
class SocialHostTest {
    private fun page(rel: Double, met: Boolean = true, social: Boolean = true): FakePage =
        FakePage(WorldFixtures.NOW).also { p ->
            p.adopt(
                FakePage.Cat(
                    born = WorldFixtures.NOW - 40 * DAY,
                    name = "Ted",
                    lastFed = WorldFixtures.NOW - 5 * HOUR,
                )
            )
            p.adopt(
                FakePage.Cat(
                    born = WorldFixtures.NOW - 35 * DAY,
                    name = "Lily",
                    lastFed = WorldFixtures.NOW - 5 * HOUR,
                )
            )
            p.pair!!.rel = rel
            p.pair!!.met = met
            p.social = social
        }

    /**
     * [minutes] open from local [startH], the page's meals at [MEALS_H], a toy tossed at [toyH].
     */
    private fun day(w: HouseWatch, minutes: Int, toyH: Double = TOY_H) {
        var tossed = false
        repeat(minutes) {
            val h = Math.floorMod(w.time.epoch, DAY) / HOUR.toDouble()
            if (MEALS_H.any { m -> h >= m && h < m + 1.0 / MINUTES_PER_HOUR }) {
                for (slot in 0..1) w.page.cats[slot].lastFed = w.time.epoch
                w.project()
                for (slot in 0..1) w.host.ownerAct("feed:$slot")
            }
            if (!tossed && h >= toyH) {
                tossed = true
                w.host.offerToy("mouse")
            }
            w.minutes(1)
        }
    }

    private fun watch(p: FakePage, startH: Int): HouseWatch {
        val time = FakeTimeSource(0L, WorldFixtures.NOW + (startH - NOON) * HOUR)
        return HouseWatch(p, time).also { it.open() }
    }

    @Test
    fun friendsMomentsReachThePageAndTheCardMoves() {
        val p = page(0.7)
        val w = watch(p, MORNING_H)
        day(w, DAY_MIN)
        val pair = p.pair!!
        val counted = pair.n.filterKeys { it != "standoff" }.values.sum()
        assertTrue("the page counted the moments: ${pair.n}", counted >= MIN_A_DAY)
        assertTrue(
            "the friendship grew within the day's cap: ${pair.rel}",
            pair.rel > 0.7 && pair.rel <= 0.7 + 2 * FakePage.DAY_CAP + 1e-9,
        )
        assertTrue("the last moment is the page's: ${pair.last}", pair.last in Social.RECORDED)
        val sent = w.port.sent.count { it.first == "social" }
        assertTrue("sent $sent for ${pair.n}", sent >= counted)
        // every minute: apart, unless in a moment together; awake a realistic share
        val registry = Catalogue.core()
        for (slot in 0..1) {
            val rows = w.rows.filter { it.slot == slot }
            val awake = rows.count { !(registry.find(it.activity)?.asleep ?: false) }
            assertTrue(
                "cat $slot awake ${awake * 100 / rows.size} %",
                awake >= rows.size * MIN_AWAKE,
            )
        }
        val layered =
            w.rows
                .groupBy { it.minute }
                .filterValues { r ->
                    r.size == 2 &&
                        r[0].surface == r[1].surface &&
                        Math.abs(r[0].x - r[1].x) < OVERLAP_PX &&
                        Math.abs(r[0].z - r[1].z) < OVERLAP_Z &&
                        !(registry.find(r[0].activity).let { a -> a != null && Social.isPair(a) } &&
                            registry.find(r[1].activity).let { a -> a != null && Social.isPair(a) })
                }
        assertTrue("layered outside a moment at ${layered.keys}", layered.size <= MAX_PASSING)
        for (m in w.world.minds) assertEquals(0, m.runner.watchdogs)
    }

    @Test
    fun theFirstMeetingIsANoseTouchOnce() {
        val p = page(0.1, met = false)
        val w = watch(p, AFTERNOON_H)
        day(w, HOUR_MIN, toyH = 99.0)
        val pair = p.pair!!
        assertTrue("they met: ${pair.n}", pair.met)
        val meetings = (pair.n["greet"] ?: 0) + (pair.n["hiss"] ?: 0)
        assertEquals("one meeting in the first hour: ${pair.n}", 1, meetings)
    }

    @Test
    fun anOlderPageIsToldNothingAndARefusedSaveIsRetried() {
        val old = page(0.7, social = false)
        val w = watch(old, MORNING_H)
        day(w, DAY_MIN, toyH = 99.0)
        assertTrue(
            "the cats still live together: ${w.world.social.events}",
            w.world.social.events.isNotEmpty(),
        )
        assertTrue("no social to an older page", w.port.sent.none { it.first == "social" })
        assertEquals(0, old.pair!!.n.values.sum())

        val p = page(0.7)
        val w2 = watch(p, MORNING_H)
        p.saveFails = true // the first hour nothing can be stored
        day(w2, HOUR_MIN, toyH = 99.0)
        p.saveFails = false
        day(w2, DAY_MIN - HOUR_MIN, toyH = 99.0)
        val events = w2.world.social.events.count { it.kind != Social.SHADOW }
        assertTrue("moments happened", events > 0)
        // each moment the page stored once, even those whose first save failed
        assertEquals(events, p.pair!!.n.values.sum())
    }

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 86_400_000L
        const val NOON = 12
        const val MORNING_H = 7
        const val AFTERNOON_H = 14
        const val TOY_H = 10.5
        const val DAY_MIN = 24 * 60
        const val HOUR_MIN = 60
        const val MINUTES_PER_HOUR = 60.0
        val MEALS_H = doubleArrayOf(8.0, 18.25)
        const val MIN_A_DAY = 3
        const val MIN_AWAKE = 0.35
        const val OVERLAP_PX = 20f
        const val OVERLAP_Z = 14f
        const val MAX_PASSING = 3
    }
}
