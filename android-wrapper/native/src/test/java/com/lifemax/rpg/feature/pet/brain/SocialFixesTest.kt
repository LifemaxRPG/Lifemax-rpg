package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.PairPose
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.world.FakeTimeSource
import com.lifemax.rpg.feature.pet.world.WorldClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Scenes of the pair's moments that once went wrong, each forced ([SocialDirector.force]) and
 * watched strictly: a cat groomed where it lies keeps its place (the other never picks it and never
 * stands on it); two cats curled up on the sofa lie the way the room draws them (the cat in front
 * is the slot drawn in front); grooming that cannot end in a nap together is told once; a kitten's
 * shadowing does not quiet the pair.
 */
class SocialFixesTest {
    private val friendly =
        mapOf("sk" to 0.3, "ou" to 0.6, "dm" to 0.5, "sp" to 0.6, "fr" to 0.8, "vo" to 0.5)

    private fun world(
        rel: Double,
        fidelity: CatWorld.Fidelity = CatWorld.Fidelity.LIVE,
        kitten: Boolean = false,
    ): Pair<CatWorld, WorldClock> {
        val clock = WorldClock(FakeTimeSource(0L, CatDays.MIDNIGHT + 11 * CatDays.HOUR))
        val cats =
            listOf(
                CatDays.cat("Ted", friendly, id = "cmu9rkw00"),
                CatDays.cat(
                    "Lily",
                    CatDays.AVERAGE,
                    id = "cmv2cdxc0",
                    slot = 1,
                    stage = if (kitten) 1 else -1,
                ),
            )
        val w = CatWorld(clock, cats, fidelity)
        w.pair(PairInfo(rel = rel, records = true))
        return w to clock
    }

    /** Runs up to [maxS] seconds, calling [each] every quantum, until [until] answers true. */
    private fun run(
        w: CatWorld,
        c: WorldClock,
        maxS: Double,
        each: () -> Unit = {},
        until: () -> Boolean = { false },
    ): Boolean {
        val from = c.subSteps
        var done = false
        val target = (from + (maxS * STEPS_PER_S).toLong()) * NANOS_PER_S / STEPS_PER_S
        c.advanceTo(target + 1, Int.MAX_VALUE) { s, t ->
            if (done) return@advanceTo
            w.step(s, t)
            if (!t) return@advanceTo
            each()
            if (until()) done = true
        }
        return done
    }

    @Test
    fun aCatGroomedWhereItLiesKeepsItsPlaceAndIsNeverWalkedOnto() {
        val (w, c) = world(0.4)
        val lying = w.minds[1]
        lying.force("loaf", Spots.COUCH_R, 3600.0)
        assertTrue("it got to the sofa", run(w, c, 120.0, until = { lying.exec.atPlace }))
        run(w, c, 25.0) // settled
        assertTrue(w.social.force(Social.GROOM, 0))
        assertTrue("the grooming ended", run(w, c, 240.0, until = { w.social.playing == null }))
        val at = lying.exec.x
        assertEquals(SurfaceId.COUCH, lying.exec.surface)
        // the place is still the groomed cat's: the other cannot pick it
        assertFalse("the sofa end is free for the other", w.book.free(0, Spots.COUCH_R, at, 0))
        var walkedOnto = 0
        run(
            w,
            c,
            30 * 60.0,
            each = {
                val still =
                    lying.exec.surface == SurfaceId.COUCH && Math.abs(lying.exec.x - at) < 2f
                val other = w.minds[0].exec
                if (
                    still &&
                        !w.sharing() &&
                        other.surface == SurfaceId.COUCH &&
                        Math.abs(other.x - at) < BrainTuning.Apart.OVERLAP_PX
                )
                    walkedOnto++
            },
            until = { !(lying.exec.surface == SurfaceId.COUCH && Math.abs(lying.exec.x - at) < 2f) },
        )
        // the groomer lies beside the groomed cat; as the moment ends it gets up and goes (roaming:
        // it used to stay and rest), a quantum or two still beside the other
        assertTrue("quanta the other stood on the groomed cat: $walkedOnto", walkedOnto <= 4)
    }

    /** True when cat [front] lies ahead of the other along their facing (the spoon's front). */
    private fun ahead(w: CatWorld, front: Int): Boolean {
        val f = w.minds[front].exec.facing
        return (w.minds[front].exec.x - w.minds[1 - front].exec.x) * f > 0f
    }

    @Test
    fun twoCatsCurledUpOnTheSofaLieAsTheRoomDrawsThem() {
        // both come to the sofa, the moment begun by slot 0
        val (w, c) = world(0.8, CatWorld.Fidelity.FAST)
        assertTrue(w.social.force(Social.NAP, 0, Spots.COUCH_L))
        assertTrue(
            "they curled up",
            run(w, c, 300.0, until = { w.minds.all { it.runner.activity === CurlUp } }),
        )
        assertTrue(w.minds.all { it.exec.surface == SurfaceId.COUCH })
        assertTrue("the front cat is the one drawn in front", ahead(w, PairPose.DRAWN_FRONT_SLOT))

        // slot 1 joins slot 0 asleep on the sofa: it lies in front, where it is drawn
        val (w2, c2) = world(0.8, CatWorld.Fidelity.FAST)
        w2.minds[0].force("sleep", Spots.COUCH_L, 3600.0)
        assertTrue(run(w2, c2, 120.0, until = { w2.minds[0].exec.atPlace }))
        run(w2, c2, 25.0)
        assertTrue(w2.social.force(Social.NAP, 0))
        assertTrue(
            "the joiner curled up",
            run(w2, c2, 300.0, until = { w2.minds.all { it.runner.activity === CurlUp } }),
        )
        assertTrue(ahead(w2, PairPose.DRAWN_FRONT_SLOT))
    }

    @Test
    fun groomingThatCannotEndInANapTogetherIsToldOnce() {
        val (w, c) = world(0.8)
        for (m in w.minds) m.drives[Drive.ENERGY] = SLEEPY
        w.social.joinAfterGroom = { null } // the spoon pose does not fit there
        w.minds[1].force("loaf", Spots.LIVING_RUG, 3600.0)
        assertTrue(run(w, c, 120.0, until = { w.minds[1].exec.atPlace }))
        run(w, c, 25.0)
        assertTrue(w.social.force(Social.GROOM, 0))
        assertTrue(run(w, c, 300.0, until = { w.social.playing == null }))
        assertEquals(
            "one grooming: ${w.social.events}",
            1,
            w.social.events.count { it.kind == Social.GROOM },
        )
        assertEquals(1, w.takeReports().count { it.name == "social" })
    }

    @Test
    fun aKittensShadowingLeavesThePairUnquieted() {
        val (w, c) = world(0.8, CatWorld.Fidelity.FAST, kitten = true)
        w.minds[0].force("loaf", Spots.RUG, 3600.0) // the grown cat lies on the rug
        assertTrue(run(w, c, 120.0, until = { w.minds[0].exec.atPlace }))
        val quiet = w.social.quietEndsAt
        val seen = w.social.events.size
        assertTrue(w.social.force(Social.SHADOW, 1, seconds = 30.0))
        assertTrue(run(w, c, 300.0, until = { w.social.playing == null }))
        assertEquals(
            "only the shadowing happened",
            listOf(Social.SHADOW),
            w.social.events.drop(seen).map { it.kind },
        )
        assertEquals("no quiet after shadowing", quiet, w.social.quietEndsAt)
    }

    private companion object {
        const val STEPS_PER_S = 120L
        const val NANOS_PER_S = 1_000_000_000L
        const val SLEEPY = 0.3
    }
}
