package com.lifemax.rpg.feature.pet.art

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Kitten growth against the page: which stage an age gets, each stage's scale and foot rows, and
 * the row a stage draws.
 */
class GrowthTest {
    private val art = PetArtResources.load()
    private val growth = art.growth
    private val web = PetArtFixtures.web

    @Test
    fun theStageForAnAgeIsWhatPetGrowIdxPicks() {
        val pairs = web.getJSONArray("ageIdx")
        assertEquals(15, pairs.length())
        for (i in 0 until pairs.length()) {
            val pair = pairs.getJSONArray(i)
            assertEquals(
                "age ${pair.getDouble(0)}",
                pair.getInt(1),
                growth.stageForAge(pair.getDouble(0)),
            )
        }
        assertEquals(web.getInt("growDays"), growth.growDays)
        assertEquals(0, growth.stageForAge(-3.0))
    }

    @Test
    fun stagesCarryTheWebsDaysAgeScaleAndFootRows() {
        val g = web.getJSONObject("growth")
        val days = g.getJSONArray("days")
        val stages = g.getJSONArray("stages")
        assertEquals(6, growth.stages.size)
        for (i in 0 until 6) {
            val want = stages.getJSONObject(i)
            val got = growth.stages[i]
            assertEquals(i, got.index)
            assertEquals(days.getInt(i), got.fromDay)
            assertEquals(want.getDouble("a"), got.age, 1e-12)
            assertEquals(want.getDouble("sx"), got.scale, 0.0)
            assertEquals(want.getDouble("sx"), web.getJSONArray("depthStageSx").getDouble(i), 0.0)
            val bottom = want.getJSONObject("bottom")
            assertEquals(bottom.length(), got.bottom.size)
            for (name in got.bottom.keys) assertEquals(
                "stage $i $name",
                bottom.getInt(name),
                got.bottom.getValue(name),
            )
        }
        assertEquals(1.0, growth.scale(Growth.ADULT), 0.0)
        assertEquals(0.6, growth.scale(0), 0.0)
    }

    @Test
    fun theRowAndFootRowDrawnAtEveryStageForEveryAnimMatchThePagesRule() {
        val rows = web.getJSONArray("stageRows")
        for (stage in 0 until 6) {
            val byName = rows.getJSONObject(stage)
            for (name in art.anims.adult.map { it.name }) {
                val want = byName.getJSONObject(name)
                val got = growth.resolve(stage, name, art.anims)!!
                val label = "stage $stage $name"
                assertEquals(label, want.getInt("row"), got.row)
                assertEquals(label, want.getInt("n"), got.frames)
                assertEquals(label, want.getDouble("fps"), got.fps, 0.0)
                assertEquals(label, want.getInt("bottom"), got.bottom)
            }
        }
    }

    @Test
    fun anAdultAndTheDepthRowsAreTheTablesOwn() {
        for (anim in art.anims.adult) assertEquals(
            anim,
            growth.resolve(Growth.ADULT, anim.name, art.anims),
        )
        for (anim in art.anims.depth) for (stage in -1..5) assertEquals(
            anim,
            growth.resolve(stage, anim.name, art.anims),
        )
    }

    @Test
    fun aKittenWithNoFootRowForANameDrawsIdleSit() {
        val got = growth.resolve(3, "scratch", art.anims)!!
        assertEquals("idle_sit", art.anims.adult.first { it.row == got.row }.name)
        assertEquals(growth.stages[3].bottom.getValue("idle_sit"), got.bottom)
        assertEquals(got, growth.resolve(3, "no_such_anim", art.anims))
    }

    @Test
    fun resolvingWithoutAnIdleSitIsNullNotACrash() {
        val bare = AnimTable(72, 48, 8, 6, emptyList(), emptyList())
        assertNull(growth.resolve(0, "walk", bare))
    }
}
