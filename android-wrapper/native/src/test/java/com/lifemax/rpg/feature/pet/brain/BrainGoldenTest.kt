package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.TraceSupport
import java.util.Locale
import org.junit.Test

/**
 * Golden traces of short scenes (plan 4.1), as text a person can read: the brain's activity with
 * its reason and the body's clip, frame, surface and position four times a second. A change to the
 * brain's tuning or choreography shows up as a reviewable diff; `RECORD=1` regenerates them into
 * src/test/resources/pet-traces/brain-*.txt.
 */
class BrainGoldenTest {
    private fun scene(
        cat: CatProfile,
        seconds: Double,
        atHour: Int,
        acts: List<OwnerAct> = emptyList(),
        setup: (CatWorld) -> Unit,
    ): String {
        val out = StringBuilder()
        var last: Doing? = null
        CatDays.run(
            listOf(cat),
            acts,
            hours = seconds / 3600.0,
            start = CatDays.MIDNIGHT + atHour * CatDays.HOUR,
            setup = setup,
        ) { world, step, think ->
            if (!think) return@run
            val d = world.now(0)
            if (d !== last) {
                last = d
                out.append(
                    String.format(
                        Locale.ROOT,
                        "%7.2f # %s @ %s (%s)\n",
                        step / 120.0,
                        d.activity,
                        d.place,
                        d.why,
                    )
                )
            }
            val p = world.minds[0].body!!.pose
            out.append(
                String.format(
                    Locale.ROOT,
                    "%7.2f %-11s f%d %-8s x=%7.2f z=%5.1f y=%6.2f%s\n",
                    step / 120.0,
                    p.clipName,
                    p.frame,
                    p.surface.key,
                    p.x,
                    p.z,
                    p.y,
                    if (p.flip) " flip" else "",
                )
            )
        }
        return out.toString()
    }

    @Test
    fun theWakingRoutine() {
        val cat = CatDays.cat("Ted", CatDays.LAZY)
        val sheet = scene(cat, 30.0, atHour = 7) { it.minds[0].force("sleep", null, 8.0) }
        TraceSupport.checkGolden("brain-waking-routine", sheet)
    }

    @Test
    fun beggingAtTheBowl() {
        val cat = CatDays.cat("Ted", CatDays.AVERAGE, hunger = 2)
        val sheet =
            scene(cat, 30.0, atHour = 10, acts = listOf(OwnerAct(2_000, "open"))) {
                it.minds[0].force("loaf", Spots.RUG, 600.0)
            }
        TraceSupport.checkGolden("brain-beg-at-the-bowl", sheet)
    }

    @Test
    fun aFridgeVisitByTheCounter() {
        val cat = CatDays.cat("Pip", CatDays.PLAYFUL)
        val sheet =
            scene(cat, 24.0, atHour = 10) { it.minds[0].force("parkour visit", Spots.FRIDGE, 10.0) }
        TraceSupport.checkGolden("brain-fridge-visit", sheet)
    }
}
