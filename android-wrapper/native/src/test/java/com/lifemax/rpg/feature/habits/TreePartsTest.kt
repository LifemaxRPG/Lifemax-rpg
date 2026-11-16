package com.lifemax.rpg.feature.habits

import com.lifemax.rpg.feature.habits.domain.Trees
import com.lifemax.rpg.feature.habits.domain.WeatherState
import com.lifemax.rpg.feature.habits.domain.Wind
import com.lifemax.rpg.feature.habits.presentation.scene.AwningSway
import com.lifemax.rpg.feature.habits.presentation.scene.TreePart
import com.lifemax.rpg.feature.habits.presentation.scene.TreeSeason
import com.lifemax.rpg.feature.habits.presentation.scene.TreeSprites
import com.lifemax.rpg.feature.habits.presentation.scene.TreeSway
import kotlin.math.abs
import kotlin.math.max
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A street tree cut into the parts that sway: the same every time, the parts together are the whole
 * tree, the pit's grate is a frame round an opening; posed in the wind it holds still with motion
 * off, sways gently in a breeze and further in a storm (mirrored with the picture); the awning's
 * cloth likewise; and it all stays inside its memory budget.
 */
class TreePartsTest {
    private fun alpha(c: Int) = c ushr 24

    private fun paste(into: IntArray, w: Int, p: TreePart?) {
        if (p == null) return
        for (y in 0 until p.h) for (x in 0 until p.w) {
            val a = alpha(p.pixels[0][y * p.w + x])
            val i = (y + p.y) * w + x + p.x
            into[i] = max(into[i], a)
        }
    }

    @Test
    fun thePartsAreTheSameEveryTimeOnePerMass() {
        for (v in 0 until Trees.VARIANTS) {
            val r = TreeSprites.rig(v)
            assertEquals(Trees.shapes[v].clumps.size / 4, r.clusters)
            assertEquals(r.clusters, r.order.toSet().size)
            val a = TreeSprites.leaves(v, TreeSeason.SUMMER)
            val b = TreeSprites.leaves(v, TreeSeason.SUMMER)
            assertEquals(r.clusters, a.size)
            for (m in a.indices) {
                val p = a[m]!!
                assertEquals(3, p.pixels.size)
                for (l in 0 until 3) assertArrayEquals(p.pixels[l], b[m]!!.pixels[l])
                // inside the picture
                assertTrue(p.x >= 0 && p.y >= 0 && p.x + p.w <= r.w && p.y + p.h <= r.h)
            }
            assertArrayEquals(TreeSprites.trunk(v).pixels[1], TreeSprites.trunk(v).pixels[1])
            // the bare tree has no leaves; a pine has no twigs
            assertTrue(
                TreeSprites.leaves(v, TreeSeason.BARE).all { it == null } != Trees.evergreen(v)
            )
            assertEquals(Trees.evergreen(v), TreeSprites.twigs(v).all { it == null })
        }
    }

    @Test
    fun thePartsTogetherAreTheWholeTree() {
        for (season in listOf(TreeSeason.SUMMER, TreeSeason.AUTUMN, TreeSeason.BARE)) for (v in
            0 until Trees.VARIANTS) {
            val whole = TreeSprites.generate(v, season)
            val r = TreeSprites.rig(v)
            val parts = IntArray(r.w * r.h)
            paste(parts, r.w, TreeSprites.trunk(v))
            for (p in TreeSprites.twigs(v)) paste(parts, r.w, p)
            if (season == TreeSeason.BARE && !Trees.evergreen(v))
                for (p in TreeSprites.fine(v)) paste(parts, r.w, p)
            for (p in TreeSprites.leaves(v, season)) paste(parts, r.w, p)
            var solid = 0
            var missing = 0
            var extra = 0
            // above the contact shadow at the foot, which is the whole picture's alone
            for (y in 0 until (r.footY - 2f * r.ppu).toInt()) for (x in 0 until r.w) {
                val i = y * r.w + x
                val a = alpha(whole.pixels[0][i])
                if (a > 200) {
                    solid++
                    if (parts[i] < 100) missing++
                }
                if (a == 0 && parts[i] > 0) extra++
            }
            assertTrue("variant $v $season: $missing of $solid missing", missing * 200 <= solid)
            assertEquals("variant $v $season: parts outside the tree", 0, extra)
        }
    }

    @Test
    fun theGrateIsAFrameRoundTheTrunk() {
        val g = TreeSprites.grate()
        assertArrayEquals(g, TreeSprites.grate())
        val n = TreeSprites.GRATE_W
        assertEquals(n * TreeSprites.GRATE_H, g.size)
        // solid at its kerb, open where the trunk stands, clear of its border
        assertEquals(255, alpha(g[(n / 2) * n + (TreeSprites.GRATE_PPU * 1.2f).toInt()]))
        assertTrue(alpha(g[(n / 2) * n + n / 2]) < 40)
        for (k in 0 until n) assertEquals(0, alpha(g[k]))
    }

    /** How far mass [m] of [v] is moved by [pose] (its swing and its flutter), in units. */
    private fun moves(v: Int, pose: FloatArray, m: Int): Float {
        val r = TreeSprites.rig(v)
        val dx = r.cx[m] - r.px[m]
        val dy = r.cy[m] - r.py[m]
        val arc = abs(pose[1 + m * TreeSway.STRIDE]) * kotlin.math.sqrt(dx * dx + dy * dy)
        val own = abs(pose[2 + m * TreeSway.STRIDE]) + abs(pose[3 + m * TreeSway.STRIDE])
        return (arc + own) / r.ppu
    }

    @Test
    fun aTreeHoldsStillWithMotionOffAndSwaysGentlyInABreeze() {
        val pose = TreeSway.buffer()
        val wind = Wind()
        val clear = WeatherState.CLEAR
        val storm = WeatherState(1f, 1f, 1f, 0f)
        for (v in 0 until Trees.VARIANTS) {
            wind.update(0f, storm)
            TreeSway.pose(v, 120f, 1f, wind, pose)
            assertTrue(pose.all { it == 0f })
            var calmMost = 0f
            var stormMost = 0f
            var calmShear = 0f
            var moved = 0f
            var last = 0f
            for (k in 0 until 600) {
                val t = 10f + k * .1f
                wind.update(t, clear)
                TreeSway.pose(v, 120f, 1f, wind, pose)
                calmShear = max(calmShear, abs(pose[TreeSway.SHEAR]))
                for (m in 0 until TreeSprites.rig(v).clusters) calmMost =
                    max(calmMost, moves(v, pose, m))
                moved += abs(pose[TreeSway.SHEAR] - last)
                last = pose[TreeSway.SHEAR]
                wind.update(t, storm)
                TreeSway.pose(v, 120f, 1f, wind, pose)
                for (m in 0 until TreeSprites.rig(v).clusters) stormMost =
                    max(stormMost, moves(v, pose, m))
                // the same moment, mirrored, is the same pose the other way
                val a = pose[1]
                TreeSway.pose(v, 120f, -1f, wind, pose)
                assertEquals(-a, pose[1], 1e-6f)
            }
            assertTrue("variant $v: calm sways $moved", moved > 0f)
            assertTrue("variant $v: the trunk leans $calmShear", calmShear < .03f)
            // a mass moves a unit or so in a breeze's gust, a few in a storm's
            assertTrue("variant $v: a breeze moves a mass $calmMost", calmMost < 1.8f)
            assertTrue("variant $v: a storm moves a mass $stormMost", stormMost < 7f)
            assertTrue("variant $v: calm $calmMost storm $stormMost", stormMost > calmMost)
        }
    }

    @Test
    fun theAwningHoldsStillWithMotionOffAndFlutters() {
        val pose = AwningSway.buffer()
        val wind = Wind()
        wind.update(0f, WeatherState.CLEAR)
        AwningSway.pose(50f, 3, wind, pose)
        assertTrue(pose.all { it == 0f })
        var moved = 0f
        for (k in 0 until 500) {
            wind.update(5f + k * .1f, WeatherState(1f, 1f, 1f, 0f))
            AwningSway.pose(50f, 3, wind, pose)
            assertTrue(pose[0] in -.02f..0.1f)
            for (s in 0 until AwningSway.STRIPS) {
                assertTrue(pose[1 + 2 * s] in 0f..0.4f)
                assertTrue(pose[2 + 2 * s] in -1.5f..2.5f)
            }
            moved += pose[1]
        }
        assertTrue(moved > 1f)
        // its strips are cut at the scallops' points, across the whole awning
        assertTrue(AwningSway.stripX(AwningSway.STRIPS) >= 92f - 1f)
    }

    @Test
    fun theStreetTreesStayInsideTheirMemoryBudget() {
        // the summer pool (every variant's wood, leaves and shape) and each season's leaves
        assertTrue("summer ${TreeSprites.bytes()}", TreeSprites.bytes() < 2_600_000)
        for (s in TreeSeason.entries) if (s != TreeSeason.SUMMER)
            assertTrue("$s ${TreeSprites.bytes(s)}", TreeSprites.bytes(s) < 1_400_000)
        assertTrue("snow ${TreeSprites.snowBytes()}", TreeSprites.snowBytes() < 450_000)
        assertTrue(TreeSprites.grateBytes() < 20_000)
    }
}
