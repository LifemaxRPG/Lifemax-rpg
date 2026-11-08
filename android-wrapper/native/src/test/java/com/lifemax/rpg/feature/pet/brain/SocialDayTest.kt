package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.world.SocialReport
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two cats living together (CB5, the [SocialDirector]) over whole simulated days with the owner's
 * meals and visits, from several start times: moments come at a believable rate for the friendship
 * (friends meet, chase, groom and curl up several times a day; strangers only touch noses, mostly
 * hissing), nothing is shared except a moment's own poses, no moment outlives its longest or leaves
 * a cat stuck, and the cats stay awake a realistic share of the day. `SOCIAL_RATES=1` prints the
 * rates the report quotes.
 */
class SocialDayTest {
    private val friendly =
        mapOf("sk" to 0.3, "ou" to 0.6, "dm" to 0.5, "sp" to 0.6, "fr" to 0.8, "vo" to 0.5)

    private fun cats(stage: Int = -1, persona: Map<String, Double> = friendly) =
        listOf(
            CatDays.cat("Ted", persona, id = "cmu9rkw00"),
            CatDays.cat("Lily", CatDays.AVERAGE, id = "cmv2cdxc0", slot = 1, stage = stage),
        )

    /** Moments per kind over one day of [rel], from local [startH], in [fidelity]. */
    private class Day(val kinds: Map<String, Int>, val world: CatWorld, val awake: DoubleArray)

    private fun day(
        rel: Double,
        startH: Int,
        fidelity: CatWorld.Fidelity = CatWorld.Fidelity.FAST,
        stage: Int = -1,
        hours: Double = 24.0,
        probe: (CatWorld, Boolean) -> Unit = { _, _ -> },
    ): Day {
        val awake = DoubleArray(2)
        var quanta = 0
        val world =
            CatDays.run(
                cats(stage),
                CatDays.owner(cats = 2),
                hours = hours,
                start = CatDays.MIDNIGHT + startH * CatDays.HOUR,
                fidelity = fidelity,
                setup = { it.pair(PairInfo(rel = rel, records = true)) },
            ) { w, _, think ->
                probe(w, think)
                if (!think) return@run
                quanta++
                for (s in 0..1) if (!w.minds[s].runner.activity.asleep) awake[s]++
            }
        for (s in 0..1) awake[s] /= quanta
        val kinds = world.social.events.groupingBy { it.kind }.eachCount()
        return Day(kinds, world, awake)
    }

    private fun rates(rel: Double, label: String): Map<String, Double> {
        val total = HashMap<String, Int>()
        val days = ArrayList<Day>()
        for (start in START_HOURS) {
            val d = day(rel, start)
            d.kinds.forEach { (k, n) -> total.merge(k, n, Int::plus) }
            days += d
        }
        val perDay = total.mapValues { it.value.toDouble() / days.size }.toSortedMap()
        if (System.getenv("SOCIAL_RATES") != null)
            println(
                String.format(
                    Locale.ROOT,
                    "social rates %-14s rel %.2f: %s per day; awake %s",
                    label,
                    rel,
                    perDay,
                    days.map { d -> d.awake.map { String.format(Locale.ROOT, "%.2f", it) } },
                )
            )
        if (System.getenv("SOCIAL_RATES") != null)
            for ((i, d) in days.withIndex()) println(
                "  from ${START_HOURS[i]} h: " +
                    d.world.social.events.joinToString(" ") {
                        val h =
                            Math.floorMod(it.atMs - CatDays.MIDNIGHT, CatDays.HOUR * 24) / 60_000
                        String.format(Locale.ROOT, "%02d:%02d %s", h / 60, h % 60, it.kind)
                    }
            )
        for ((i, d) in days.withIndex()) {
            for (s in 0..1) assertTrue(
                "$label from ${START_HOURS[i]} h: cat $s awake ${d.awake[s]}",
                d.awake[s] >= MIN_AWAKE,
            )
            for (m in d.world.minds) assertEquals("$label: watchdog", 0, m.runner.watchdogs)
        }
        return perDay
    }

    private fun Map<String, Double>.of(k: String) = this[k] ?: 0.0

    @Test
    fun friendsShareSeveralMomentsADay() {
        val r = rates(0.7, "friends")
        val total = Social.RECORDED.sumOf { r.of(it) }
        assertTrue("friends: $r", total in 4.0..24.0)
        assertTrue("friends chase: $r", r.of(Social.CHASE) >= 0.5)
        assertTrue("friends groom: $r", r.of(Social.GROOM) >= 0.5)
        assertTrue("friends curl up: $r", r.of(Social.NAP) >= 0.3)
        assertTrue("friends rarely hiss: $r", r.of(Social.HISS) <= 0.4)
    }

    @Test
    fun strangersOnlyTouchNosesAndMostlyHiss() {
        val r = rates(0.1, "strangers")
        for (k in listOf(Social.CHASE, Social.GROOM, Social.NAP)) assertEquals(
            "strangers $k: $r",
            0.0,
            r.of(k),
            0.0,
        )
        val meet = r.of(Social.GREET) + r.of(Social.HISS)
        assertTrue("strangers meet: $r", meet in 1.0..10.0)
        assertTrue("strangers mostly hiss: $r", r.of(Social.HISS) >= r.of(Social.GREET))
    }

    @Test
    fun catsGettingAlongChaseAndGroomButDoNotCurlUp() {
        val r = rates(0.4, "getting along")
        assertEquals("no nap below the page's gate: $r", 0.0, r.of(Social.NAP), 0.0)
        assertTrue("getting along: $r", r.of(Social.CHASE) + r.of(Social.GROOM) >= 1.0)
        assertTrue("getting along hardly hiss: $r", r.of(Social.HISS) <= 0.4)
    }

    @Test
    fun aKittenChasesBeforeTheyAreFriendsAndShadowsTheGrownCat() {
        val d = day(0.1, 7, stage = 1)
        assertTrue(
            "a kitten chases at any friendship: ${d.kinds}",
            (d.kinds[Social.CHASE] ?: 0) >= 1,
        )
        assertTrue("a kitten shadows: ${d.kinds}", (d.kinds[Social.SHADOW] ?: 0) >= 1)
    }

    @Test
    fun aLiveDayKeepsApartExceptInAMomentAndNeverOverruns() {
        var layered = 0
        var together = 0
        val longest = HashMap<String, Double>()
        val idle = IntArray(2)
        val worstIdle = IntArray(2)
        val d =
            day(0.7, 7, CatWorld.Fidelity.LIVE) { w, think ->
                for (m in w.minds) {
                    val b = m.body!!
                    idle[m.slot] = if (b.isIdle) idle[m.slot] + 1 else 0
                    worstIdle[m.slot] = maxOf(worstIdle[m.slot], idle[m.slot])
                }
                if (!think) return@day
                if (w.sharing()) together++ else if (overlapping(w)) layered++
                for (m in w.minds) longest.merge(m.runner.activity.id, m.runner.elapsedS, ::maxOf)
            }
        // a cat passing the other can overlap for a moment; never for long
        assertTrue("layered for $layered quanta outside a moment", layered <= MAX_PASSING_QUANTA)
        assertTrue("they shared poses for $together quanta", together > 0)
        for ((id, s) in longest) {
            val a = Catalogue.core().find(id)!!
            assertTrue("$id lasted ${s / 60} min", s <= a.maxMinutes * 60 + ROUTE_S + 1)
        }
        for (s in 0..1) {
            assertTrue("cat $s idle ${worstIdle[s]} sub-steps", worstIdle[s] < STUCK_STEPS)
            assertEquals(0, d.world.minds[s].runner.watchdogs)
        }
        val reports = d.world.takeReports().filterIsInstance<SocialReport>()
        assertEquals(
            "every recorded moment is told to the page",
            d.world.social.events.count { it.kind != Social.SHADOW },
            reports.size,
        )
        assertEquals("op ids are unique", reports.size, reports.map { it.op }.toSet().size)
    }

    /** Two cats nearer than the overlap guard's distance on one surface. */
    private fun overlapping(w: CatWorld): Boolean {
        val a = w.minds[0].exec
        val b = w.minds[1].exec
        if (a.surface != b.surface || Math.abs(a.x - b.x) >= BrainTuning.Apart.OVERLAP_PX)
            return false
        return a.surface != SurfaceId.FLOOR || Math.abs(a.z - b.z) < BrainTuning.Apart.OVERLAP_Z
    }

    @Test
    fun aRestartNeverLeavesAMomentHalfDoneButKeepsTwoCatsCurledUp() {
        var mid: BrainState? = null
        var curled: BrainState? = null
        day(0.7, 12, CatWorld.Fidelity.FAST, hours = 30.0) { w, think ->
            if (!think) return@day
            val playing = w.social.playing
            if (mid == null && playing != null && playing != Social.NAP && playing != Social.SHADOW)
                mid = w.export()
            if (curled == null && w.sharing() && w.minds.all { it.runner.activity === CurlUp })
                curled = w.export()
        }
        val half = requireNotNull(mid) { "no moment was caught under way" }
        val back = CatDays.run(cats(), hours = 0.0, fidelity = CatWorld.Fidelity.FAST)
        back.restore(half)
        assertEquals("no moment survives a restore", null, back.social.playing)
        for (m in back.minds) assertTrue(m.runner.activity.id, !Social.isPair(m.runner.activity))
        // the cats get on with their day (no one waits for a partner that is gone)
        val after =
            CatDays.run(
                cats(),
                hours = 1.0,
                fidelity = CatWorld.Fidelity.FAST,
                setup = { it.restore(half) },
            )
        for (m in after.minds) assertEquals(0, m.runner.watchdogs)

        if (curled == null) {
            // no nap together that day: two curled up on the sofa on purpose
            CatDays.run(
                cats(),
                hours = CURL_H,
                start = CatDays.MIDNIGHT + 14 * CatDays.HOUR,
                fidelity = CatWorld.Fidelity.FAST,
                setup = {
                    it.pair(PairInfo(rel = 0.8, records = true))
                    assertTrue(it.social.force(Social.NAP, 0, Spots.COUCH_L))
                },
            ) { w, _, think ->
                if (
                    think &&
                        curled == null &&
                        w.sharing() &&
                        w.minds.all { it.runner.activity === CurlUp }
                )
                    curled = w.export()
            }
        }
        val nap = requireNotNull(curled) { "the friends never curled up" }
        val again = CatDays.run(cats(), hours = 0.0, fidelity = CatWorld.Fidelity.FAST)
        again.restore(nap)
        assertTrue("two cats restored curled up lie together still", again.sharing())
        assertTrue(again.minds.all { it.runner.activity === CurlUp })
    }

    @Test
    fun aMealServedMidMomentEndsItAndNobodyWaits() {
        var fedAt = -1L
        var after = -1
        var stuckPartner = 0
        day(0.7, 9, hours = 10.0) { w, think ->
            if (!think) return@day
            val playing = w.social.playing
            if (fedAt < 0 && playing == Social.CHASE) {
                fedAt = w.minds[0].nowMs
                w.owner(OwnerAct(0, "feed:0"))
                after = 0
            } else if (after >= 0 && after < CHECK_QUANTA) {
                after++
                if (after == CHECK_QUANTA) {
                    if (w.social.playing != null) stuckPartner++
                    if (Social.isPair(w.minds[1].runner.activity)) stuckPartner++
                }
            }
        }
        assertTrue("no chase was caught", fedAt >= 0)
        assertEquals("the moment ended and the other cat went on", 0, stuckPartner)
    }

    @Test
    fun aHungryCatIsNeverAskedAndTheStoryTellsWhatTheyDidTogether() {
        val hungry = cats().map { it.copy(hungerStage = 3) }
        val w =
            CatDays.run(
                hungry,
                hours = 6.0,
                fidelity = CatWorld.Fidelity.FAST,
                setup = { it.pair(PairInfo(rel = 0.8, records = true)) },
            )
        assertEquals("hungry cats share no moment", 0, w.social.events.size)

        assertEquals(
            "They napped together",
            PairLines.line(listOf("greet", "nap"), listOf("Ted", "Lily")),
        )
        assertEquals(
            "Ted and Lily played chase",
            PairLines.line(listOf("hiss", "chase"), listOf("Ted", "Lily")),
        )
        assertEquals(null, PairLines.line(listOf("shadow"), listOf("Ted", "Lily")))
        // a catch-up over a friendly hour tells the moments to the page, dated in the gap
        var told = 0
        for (start in CATCH_UP_HOURS) {
            val live =
                CatDays.run(
                    cats(),
                    hours = 0.1,
                    start = CatDays.MIDNIGHT + start * CatDays.HOUR,
                    fidelity = CatWorld.Fidelity.FAST,
                    setup = { it.pair(PairInfo(rel = 0.8, records = true)) },
                )
            // rested and wanting company: the hour is theirs to spend
            for (m in live.minds) {
                m.drives[Drive.ENERGY] = RESTED
                m.drives[Drive.COMPANY] = RESTED
            }
            val now = live.minds[0].nowMs
            val story = live.catchUp(CatDays.HOUR)
            val reports = live.takeReports().filterIsInstance<SocialReport>()
            if (System.getenv("SOCIAL_RATES") != null)
                println("catch-up from $start h: ${story.lines} reports ${reports.map { it.kind }}")
            if (reports.isNotEmpty()) {
                told++
                assertTrue(
                    "the story names it: ${story.lines}",
                    story.lines.size <= 3 &&
                        story.lines.any { it.contains("Lily") || it.startsWith("They") },
                )
                assertTrue(
                    reports.all { it.at <= CatDays.MIDNIGHT + start * CatDays.HOUR + now + 1 }
                )
            }
        }
        assertTrue("some catch-up had a moment together", told >= 1)
    }

    private companion object {
        val START_HOURS = listOf(0, 7, 19)
        val CATCH_UP_HOURS = listOf(9, 11, 13, 15, 17, 19)
        // the host's floor is 35 %; this keeps a margin over it. Moving the tree scratching place
        // in
        // front of the tree reshuffled the friends' day from 7 h: its second cat is awake 37.3 %
        const val MIN_AWAKE = 0.37
        const val MAX_PASSING_QUANTA = 400
        const val ROUTE_S = 120.0
        const val STUCK_STEPS = 20 * 120
        const val CHECK_QUANTA = 60
        const val RESTED = 0.95
        const val CURL_H = 0.2
    }
}
