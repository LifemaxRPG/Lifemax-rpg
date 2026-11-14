package com.lifemax.rpg.feature.habits

import com.lifemax.rpg.feature.habits.domain.CAMERA_MAX
import com.lifemax.rpg.feature.habits.domain.CAMERA_MIN
import com.lifemax.rpg.feature.habits.domain.CITY_LABEL
import com.lifemax.rpg.feature.habits.domain.CITY_MAX_SCALE
import com.lifemax.rpg.feature.habits.domain.CITY_MIN_SCALE
import com.lifemax.rpg.feature.habits.domain.Dice
import com.lifemax.rpg.feature.habits.domain.Hit
import com.lifemax.rpg.feature.habits.domain.HitKind
import com.lifemax.rpg.feature.habits.domain.PAGE_SHARE
import com.lifemax.rpg.feature.habits.domain.PieceKind
import com.lifemax.rpg.feature.habits.domain.Pose
import com.lifemax.rpg.feature.habits.domain.SkyPhase
import com.lifemax.rpg.feature.habits.domain.TowerSpec as T
import com.lifemax.rpg.feature.habits.domain.cityLayout
import com.lifemax.rpg.feature.habits.domain.crackLine
import com.lifemax.rpg.feature.habits.domain.focusCamera
import com.lifemax.rpg.feature.habits.domain.hit
import com.lifemax.rpg.feature.habits.domain.lapseFraction
import com.lifemax.rpg.feature.habits.domain.lapseWeeks
import com.lifemax.rpg.feature.habits.domain.orbAt
import com.lifemax.rpg.feature.habits.domain.pagerPose
import com.lifemax.rpg.feature.habits.domain.rubberBand
import com.lifemax.rpg.feature.habits.domain.settlePage
import com.lifemax.rpg.feature.habits.domain.skyPhase
import com.lifemax.rpg.feature.habits.domain.skyline
import com.lifemax.rpg.feature.habits.domain.stars
import com.lifemax.rpg.feature.habits.domain.towards
import com.lifemax.rpg.feature.habits.domain.towerAt
import com.lifemax.rpg.feature.habits.domain.towerLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The scene's geometry: stacking, the camera, the pager, the city, taps and the sky. */
class HabitsSceneTest {
    private val city = HabitsScenarios.projection("city")
    private val coffee = city.stacks[0]
    private val night = city.stacks[1]

    @Test
    fun aTowerStacksUpFromItsSlabInOrder() {
        val t = towerLayout(coffee)
        assertEquals(
            listOf(
                PieceKind.SLAB,
                PieceKind.BLOCK,
                PieceKind.BLOCK,
                PieceKind.BLOCK,
                PieceKind.BLOCK,
                PieceKind.REWARD,
            ),
            t.pieces.map { it.kind },
        )
        // each piece stands on the one below
        t.pieces.zipWithNext().forEach { (a, b) -> assertEquals(a.top, b.bottom, 0f) }
        assertEquals(0f, t.pieces[0].bottom, 0f)
        assertEquals(T.SLAB_H, t.pieces[0].height, 0f)
        assertEquals(T.W + 2 * T.SLAB_OVER, t.pieces[0].width, 0f)
        // a block's height is its size: stretch, normal, normal, two minutes
        assertEquals(
            listOf(T.HEIGHTS[2], T.HEIGHTS[1], T.HEIGHTS[1], T.HEIGHTS[0]),
            t.pieces.subList(1, 5).map { it.height },
        )
        // the two laid today stand; the two not laid are blueprints, and the roof is under them
        assertEquals(listOf(false, false, true, true), t.pieces.subList(1, 5).map { it.plan })
        assertEquals(T.SLAB_H + T.HEIGHTS[2] + T.HEIGHTS[1], t.roof, 0f)
        val all = T.SLAB_H + T.HEIGHTS[2] + 2 * T.HEIGHTS[1] + T.HEIGHTS[0]
        assertEquals(all + T.REWARD_H + T.DY, t.height, .001f)
        assertEquals(coffee.id, t.stackId)
        assertTrue(t.trees.isEmpty())
    }

    @Test
    fun theGhostAndTheRewardAreTodaysOnly() {
        val live = towerLayout(coffee, ghost = true)
        assertEquals(PieceKind.GHOST, live.pieces.last().kind)
        assertEquals(
            live.pieces[live.pieces.size - 2].top + T.GHOST_GAP,
            live.pieces.last().bottom,
            .001f,
        )
        val past = towerLayout(city.towersAt(1)[0], live = false, ghost = true)
        assertTrue(
            past.pieces.none {
                it.kind == PieceKind.GHOST || it.kind == PieceKind.REWARD || it.plan
            }
        )
        // a stack with no reward has no capstone, and an empty one is only its slab
        assertTrue(towerLayout(night).pieces.none { it.kind == PieceKind.REWARD })
        assertEquals(listOf(PieceKind.SLAB), towerLayout(city.stacks[2]).pieces.map { it.kind })
        assertEquals(T.SLAB_H + T.DY, towerLayout(city.stacks[2]).height, 0f)
    }

    @Test
    fun aHabitBeingBrokenIsItsFloorsAndAClearedOneATree() {
        val t = towerLayout(night)
        val phone = night.blocks[0]
        val floors = t.pieces.filter { it.kind == PieceKind.FLOOR }
        assertEquals(phone.left, floors.size)
        assertTrue(floors.all { it.id == phone.id && it.height == T.FLOOR && !it.plan })
        // only the top floor carries the name
        assertEquals(listOf(floors.last()), floors.filter { it.labelled })
        assertEquals(floors.last(), t.labelled(phone.id))
        // the cleared habit is a tree on the lot, not a piece; the block after stands on the floors
        val snacks = night.blocks[1]
        assertTrue(t.pieces.none { it.id == snacks.id })
        assertEquals(listOf(snacks.id), t.trees.map { it.id })
        assertTrue(t.trees[0].x < -T.W / 2f)
        assertEquals(floors.last().top, t.pieces.first { it.kind == PieceKind.BLOCK }.bottom, 0f)
        // never more floors drawn than the cap
        val tall = towerLayout(night.copy(blocks = listOf(phone.copy(left = 40, floors = 60))))
        assertEquals(T.MAX_FLOORS, tall.pieces.count { it.kind == PieceKind.FLOOR })
        // the camera aims at the middle of the floors, and at a tree near the ground
        assertEquals((floors.first().bottom + floors.last().top) / 2f, t.aim(phone.id)!!, .001f)
        assertEquals(T.TREE_H / 2f, t.aim(snacks.id)!!, 0f)
        assertNull(t.aim("nope"))
    }

    @Test
    fun aTapHitsThePieceUnderIt() {
        val t = towerLayout(coffee, ghost = true)
        val (slab, read, journal) = t.pieces
        assertEquals(Hit(HitKind.SLAB, coffee.id), t.hit(0f, slab.middle))
        assertEquals(Hit(HitKind.SLAB, coffee.id), t.hit(T.W / 2f + T.SLAB_OVER - 1f, 2f))
        assertEquals(Hit(HitKind.BLOCK, read.id), t.hit(0f, read.middle))
        assertEquals(Hit(HitKind.BLOCK, read.id), t.hit(-T.W / 2f + 1f, read.bottom + 1f))
        assertEquals(Hit(HitKind.BLOCK, journal.id), t.hit(10f, journal.bottom + 1f))
        // a block's right side face belongs to it
        assertEquals(
            Hit(HitKind.BLOCK, read.id),
            t.hit(T.W / 2f + T.DX / 2f, read.middle + T.DY / 2f),
        )
        // the reward is narrower than the block under it, whose top face shows beside it
        val reward = t.pieces.first { it.kind == PieceKind.REWARD }
        assertEquals(Hit(HitKind.REWARD, coffee.id), t.hit(0f, reward.middle))
        assertEquals(
            Hit(HitKind.BLOCK, t.pieces[4].id),
            t.hit(T.REWARD_W / 2f + 20f, reward.bottom + 4f),
        )
        assertEquals(Hit(HitKind.GHOST, coffee.id), t.hit(0f, t.pieces.last().middle))
        // beside and above the tower there is nothing
        assertNull(t.hit(T.W, read.middle))
        assertNull(t.hit(0f, t.height + 30f))
        assertNull(t.hit(-T.W / 2f - T.SLAB_OVER - 4f, slab.middle))
        // a tree
        val lot = towerLayout(night)
        assertEquals(Hit(HitKind.TREE, night.blocks[1].id), lot.hit(lot.trees[0].x, 20f))
        assertNull(lot.hit(lot.trees[0].x, T.TREE_H + 10f))
    }

    @Test
    fun theCameraFillsTheViewAndPansATallTower() {
        // a short tower is enlarged, but never past the cap, and needs no pan
        val small = focusCamera(height = 100f, avail = 420f)
        assertEquals(CAMERA_MAX, small.scale, 0f)
        assertEquals(0f, small.maxPan, 0f)
        assertEquals(0f, small.pan, 0f)
        // one that fits exactly is drawn as it is
        val exact = focusCamera(height = 400f - T.HEADROOM, avail = 400f)
        assertEquals(1f, exact.scale, .0001f)
        assertEquals(0f, exact.maxPan, .001f)
        // a tall one keeps a readable size and is panned
        val tall = focusCamera(height = 900f, avail = 420f)
        assertEquals(CAMERA_MIN, tall.scale, 0f)
        assertEquals(CAMERA_MIN * (900f + T.HEADROOM) - 420f, tall.maxPan, .001f)
        // with nothing to aim at it shows its top
        assertEquals(tall.maxPan, tall.pan, 0f)
        // the block to lay next sits in the middle of the view
        val aimed = focusCamera(900f, 420f, aim = 500f)
        assertEquals(CAMERA_MIN * 500f - 210f, aimed.pan, .001f)
        assertEquals(210f, aimed.scale * 500f - aimed.pan, .001f)
        // an aim near the ground or the top is held to what there is
        assertEquals(0f, focusCamera(900f, 420f, aim = 20f).pan, 0f)
        assertEquals(tall.maxPan, focusCamera(900f, 420f, aim = 900f).pan, 0f)
        // the owner's own pan wins over the aim, and is held to the tower too
        assertEquals(120f, focusCamera(900f, 420f, aim = 500f, pan = 120f).pan, 0f)
        assertEquals(0f, focusCamera(900f, 420f, pan = -50f).pan, 0f)
        assertEquals(tall.maxPan, focusCamera(900f, 420f, pan = 5000f).pan, 0f)
        // the scale stays within its bounds whatever the sizes
        for (h in listOf(1f, 60f, 300f, 2000f)) for (a in listOf(120f, 400f, 900f)) assertTrue(
            focusCamera(h, a).scale in CAMERA_MIN..CAMERA_MAX
        )
    }

    @Test
    fun theTowersFollowTheFinger() {
        val w = 400f
        assertEquals(Pose(0f, 1f, 1f), pagerPose(1, 1f, w))
        // a neighbour is a page away, smaller and fainter; two away it is not drawn
        val next = pagerPose(2, 1f, w)
        assertEquals(w * PAGE_SHARE, next.x, .001f)
        assertTrue(next.scale < 1f && next.alpha < 1f && next.alpha > 0f)
        assertEquals(-next.x, pagerPose(0, 1f, w).x, .001f)
        assertEquals(0f, pagerPose(3, 1f, w).alpha, 0f)
        // half way between two towers both are equally far and equally small
        val a = pagerPose(1, 1.5f, w)
        val b = pagerPose(2, 1.5f, w)
        assertEquals(-a.x, b.x, .001f)
        assertEquals(a.scale, b.scale, .0001f)
        // dragging left (position rises) moves a tower left
        assertTrue(pagerPose(1, 1.2f, w).x < 0f)
        // between two poses
        assertEquals(Pose(50f, .75f, .5f), Pose(0f, 1f, 1f).towards(Pose(100f, .5f, 0f), .5f))
    }

    @Test
    fun theEndsResistAndAReleaseSettlesOnANeighbour() {
        assertEquals(1.4f, rubberBand(1.4f, 3), 0f)
        assertTrue(rubberBand(-.5f, 3) > -.5f && rubberBand(-.5f, 3) < 0f)
        assertTrue(rubberBand(2.6f, 3) < 2.6f && rubberBand(2.6f, 3) > 2f)
        assertEquals(0f, rubberBand(0f, 1), 0f)
        assertTrue(rubberBand(.5f, 1) < .5f)
        // a short drag falls back, one past half way goes on
        assertEquals(1, settlePage(1.3f, 0f, 1, 4))
        assertEquals(2, settlePage(1.6f, 0f, 1, 4))
        assertEquals(0, settlePage(.4f, 0f, 1, 4))
        // a flick carries it, whichever way, but never more than one tower
        assertEquals(2, settlePage(1.1f, 4f, 1, 4))
        assertEquals(0, settlePage(.95f, -4f, 1, 4))
        assertEquals(2, settlePage(1.2f, 40f, 1, 4))
        // a flick against a drag past half way comes back
        assertEquals(1, settlePage(1.6f, -4f, 1, 4))
        // the ends hold
        assertEquals(0, settlePage(-.2f, -9f, 0, 4))
        assertEquals(3, settlePage(3.2f, 9f, 3, 4))
        assertEquals(0, settlePage(0f, 3f, 0, 1))
        assertEquals(0, settlePage(0f, 0f, 0, 0))
    }

    @Test
    fun aCityFitsEveryTower() {
        val heights = city.stacks.map { towerLayout(it).height }
        val c = cityLayout(heights, width = 411f, avail = 420f)
        assertEquals(3, c.xs.size)
        assertTrue(c.scale in CITY_MIN_SCALE..CITY_MAX_SCALE)
        // evenly spaced, in order, within the view
        assertEquals(c.xs[1] - c.xs[0], c.xs[2] - c.xs[1], .001f)
        assertEquals(c.slot, c.xs[1] - c.xs[0], .001f)
        assertTrue(
            c.xs.first() - c.slot / 2f >= -411f / 2f && c.xs.last() + c.slot / 2f <= 411f / 2f
        )
        // the tallest fits above its name
        assertTrue(heights.max() * c.scale <= 420f - CITY_LABEL)
        // one tower stands in the middle at the largest size its height allows
        val one = cityLayout(listOf(200f), 411f, 420f)
        assertEquals(CITY_MAX_SCALE, one.scale, 0f)
        assertTrue(kotlin.math.abs(one.xs[0]) < T.DX)
        // many towers shrink, a very tall one too, but never under the floor
        assertTrue(cityLayout(List(8) { 200f }, 411f, 420f).scale < c.scale)
        assertTrue(
            cityLayout(listOf(200f, 1500f), 411f, 420f).scale <
                cityLayout(listOf(200f, 300f), 411f, 420f).scale
        )
        assertEquals(CITY_MIN_SCALE, cityLayout(List(40) { 200f }, 411f, 420f).scale, 0f)
        assertTrue(cityLayout(emptyList(), 411f, 420f).xs.isEmpty())
        // a tap picks the tower under it
        assertEquals(0, c.towerAt(c.xs[0] + 3f))
        assertEquals(2, c.towerAt(c.xs[2] - c.slot / 2f + 1f))
        assertNull(c.towerAt(c.xs[2] + c.slot))
    }

    @Test
    fun theTimeLapseSliderEndsOnToday() {
        assertEquals(0, lapseWeeks(1f, 12))
        assertEquals(12, lapseWeeks(0f, 12))
        assertEquals(6, lapseWeeks(.5f, 12))
        assertEquals(0, lapseWeeks(4f, 12))
        assertEquals(12, lapseWeeks(-1f, 12))
        for (w in 0..12) assertEquals(w, lapseWeeks(lapseFraction(w, 12), 12))
        assertEquals(1f, lapseFraction(0, 12), 0f)
        assertEquals(0f, lapseFraction(30, 12), 0f)
        assertEquals(1f, lapseFraction(3, 0), 0f)
    }

    @Test
    fun theSkyFollowsTheHour() {
        assertEquals(SkyPhase.NIGHT, skyPhase(0f))
        assertEquals(SkyPhase.NIGHT, skyPhase(5.4f))
        assertEquals(SkyPhase.DUSK, skyPhase(5.5f))
        assertEquals(SkyPhase.DUSK, skyPhase(7.4f))
        assertEquals(SkyPhase.DAY, skyPhase(7.5f))
        assertEquals(SkyPhase.DAY, skyPhase(17.9f))
        assertEquals(SkyPhase.DUSK, skyPhase(18f))
        assertEquals(SkyPhase.DUSK, skyPhase(20.4f))
        assertEquals(SkyPhase.NIGHT, skyPhase(20.5f))
        assertEquals(SkyPhase.NIGHT, skyPhase(23.9f))
        // the sun crosses from sunrise to sunset and stands highest at one in the afternoon
        assertEquals(0f, orbAt(5.5f).first, .001f)
        assertEquals(1f, orbAt(20.4f).first, .01f)
        assertEquals(.5f, orbAt(13f).first, .001f)
        assertEquals(1f, orbAt(13f).second, .001f)
        assertTrue(orbAt(8f).second < orbAt(11f).second)
        // the moon rises at half past eight and is highest at one in the morning
        assertEquals(0f, orbAt(20.5f).first, .001f)
        assertEquals(.5f, orbAt(1f).first, .001f)
        assertEquals(1f, orbAt(1f).second, .001f)
        assertTrue(orbAt(5.4f).first > .95f)
    }

    @Test
    fun marksAreTheSameEveryTimeAndTheirOwn() {
        val a = Dice("s1h4")
        val b = Dice("s1h4")
        val first = List(6) { a.next() }
        assertEquals(first, List(6) { b.next() })
        assertTrue(first.all { it >= 0f && it < 1f })
        assertNotEquals(first, Dice("s1h5").let { d -> List(6) { d.next() } })
        // a crack runs from the top edge to the bottom through the right-hand third, zigzagging
        val crack = crackLine("s1h6", tall = false)
        assertEquals(5, crack.size)
        assertEquals(0f, crack.first().second, 0f)
        assertEquals(1f, crack.last().second, 0f)
        assertTrue(crack.all { it.first in .7f..(.9f) })
        assertTrue(crack.zipWithNext().all { (p, q) -> q.second > p.second })
        assertEquals(crack, crackLine("s1h6", tall = false))
        assertNotEquals(crack, crackLine("s1h7", tall = false))
        assertEquals(7, crackLine("s1h6", tall = true).size)
    }

    @Test
    fun theSkylineAndTheStarsAreFixed() {
        val row = skyline(5, 26, 64, -130f, 530f)
        assertEquals(row, skyline(5, 26, 64, -130f, 530f))
        assertNotEquals(row, skyline(23, 26, 64, -130f, 530f))
        assertEquals(-130f, row.first().x, 0f)
        assertTrue(row.last().x + row.last().w >= 530f)
        // the buildings touch, and each is within its sizes
        row.zipWithNext().forEach { (p, q) -> assertEquals(p.x + p.w, q.x, .001f) }
        assertTrue(row.all { it.w in 12f..31f && it.h >= 26f && it.h < 64f })
        val sky = stars(46)
        assertEquals(46, sky.size)
        assertEquals(sky, stars(46))
        assertTrue(sky.all { it.x in 0f..1f && it.y in 0f..1f && it.alpha in 0f..1f })
        assertTrue(sky.any { it.twinkles })
        assertFalse(sky.all { it.twinkles })
    }
}
