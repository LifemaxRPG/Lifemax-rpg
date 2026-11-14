package com.lifemax.rpg.feature.habits

import com.lifemax.rpg.feature.habits.domain.AIM_AIR
import com.lifemax.rpg.feature.habits.domain.CAMERA_MAX
import com.lifemax.rpg.feature.habits.domain.CAMERA_MIN
import com.lifemax.rpg.feature.habits.domain.CITY_LABEL
import com.lifemax.rpg.feature.habits.domain.CITY_MAX_SCALE
import com.lifemax.rpg.feature.habits.domain.CITY_MIN_SCALE
import com.lifemax.rpg.feature.habits.domain.CRACK_DEPTH
import com.lifemax.rpg.feature.habits.domain.CRACK_LEFT
import com.lifemax.rpg.feature.habits.domain.CRACK_RIGHT
import com.lifemax.rpg.feature.habits.domain.Dice
import com.lifemax.rpg.feature.habits.domain.Hit
import com.lifemax.rpg.feature.habits.domain.HitKind
import com.lifemax.rpg.feature.habits.domain.ORB_EDGE
import com.lifemax.rpg.feature.habits.domain.PAGE_SHARE
import com.lifemax.rpg.feature.habits.domain.PieceKind
import com.lifemax.rpg.feature.habits.domain.Pose
import com.lifemax.rpg.feature.habits.domain.SceneWindow
import com.lifemax.rpg.feature.habits.domain.SkyPhase
import com.lifemax.rpg.feature.habits.domain.TOP_FADE
import com.lifemax.rpg.feature.habits.domain.TowerSpec as T
import com.lifemax.rpg.feature.habits.domain.cityLayout
import com.lifemax.rpg.feature.habits.domain.crackLine
import com.lifemax.rpg.feature.habits.domain.focusCamera
import com.lifemax.rpg.feature.habits.domain.hit
import com.lifemax.rpg.feature.habits.domain.lapseFraction
import com.lifemax.rpg.feature.habits.domain.lapseWeeks
import com.lifemax.rpg.feature.habits.domain.orbAt
import com.lifemax.rpg.feature.habits.domain.orbSpot
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
        // the camera aims at all the floors, and at a tree near the ground
        assertEquals(floors.first().bottom to floors.last().top, t.span(phone.id))
        assertEquals((floors.first().bottom + floors.last().top) / 2f, t.aim(phone.id)!!, .001f)
        assertEquals(0f to T.TREE_H, t.span(snacks.id))
        assertEquals(T.TREE_H / 2f, t.aim(snacks.id)!!, 0f)
        assertNull(t.aim("nope"))
        assertNull(t.span("nope"))
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
    fun aTapOnASunkTowerHitsWhatShowsThere() {
        val t = towerLayout(coffee, ghost = true)
        val (slab, read, journal) = t.pieces
        // slid down by most of the first block: the slab is where it was
        val sink = read.height - 10f
        assertEquals(Hit(HitKind.SLAB, coffee.id), t.hit(0f, slab.middle, sink))
        // the first block's last strip shows just over the slab, the second right above it
        assertEquals(Hit(HitKind.BLOCK, read.id), t.hit(0f, slab.top + 5f, sink))
        assertEquals(Hit(HitKind.BLOCK, journal.id), t.hit(0f, slab.top + 15f, sink))
        // what has gone behind the slab is not there: beside the slab's front, where the first
        // block's side face would have been
        assertEquals(Hit(HitKind.BLOCK, read.id), t.hit(T.W / 2f + 14f, 60.5f))
        assertNull(t.hit(T.W / 2f + 14f, .5f, 60f))
        // a point is hit by the piece the same distance further up when nothing is sunk
        assertEquals(t.hit(0f, journal.middle + 40f), t.hit(0f, journal.middle, 40f))
        // the trees stand on the lot, which does not move
        val lot = towerLayout(night)
        assertEquals(Hit(HitKind.TREE, night.blocks[1].id), lot.hit(lot.trees[0].x, 20f, 60f))
    }

    @Test
    fun theCameraFillsTheWindowAndSlidesATallTower() {
        // a short tower is enlarged, but never past the cap, and needs no pan
        val small = focusCamera(height = 100f, avail = 420f)
        assertEquals(CAMERA_MAX, small.scale, 0f)
        assertEquals(0f, small.maxPan, 0f)
        assertEquals(0f, small.pan, 0f)
        // one that fits exactly is drawn as it is
        val exact = focusCamera(height = 400f - T.HEADROOM, avail = 400f)
        assertEquals(1f, exact.scale, .0001f)
        assertEquals(0f, exact.maxPan, .001f)
        // one a little too tall is made smaller until it fits, and still needs no pan
        val fitted = focusCamera(height = 500f - T.HEADROOM, avail = 400f)
        assertEquals(.8f, fitted.scale, .0001f)
        assertEquals(0f, fitted.maxPan, .001f)
        // a tall one keeps a readable size and is slid down behind its slab
        val tall = focusCamera(height = 900f, avail = 420f)
        assertEquals(CAMERA_MIN, tall.scale, 0f)
        assertEquals(CAMERA_MIN * (900f + T.HEADROOM) - 420f, tall.maxPan, .001f)
        assertEquals(tall.pan / tall.scale, tall.sink, .001f)
        // with nothing to aim at it shows its top, the headroom's air over it
        assertEquals(tall.maxPan, tall.pan, 0f)
        assertEquals(420f - CAMERA_MIN * T.HEADROOM, tall.above(900f), .001f)
        // the block to lay next shows whole, with air over it, and the tower is slid no further
        val aimed = focusCamera(900f, 420f, aim = 560f to 626f)
        assertEquals(CAMERA_MIN * (626f + AIM_AIR) - 420f, aimed.pan, .001f)
        assertEquals(420f - CAMERA_MIN * AIM_AIR, aimed.above(626f), .001f)
        assertTrue(aimed.above(560f) > aimed.slabTop)
        // one low on the tower leaves it on its foundation
        assertEquals(0f, focusCamera(900f, 420f, aim = T.SLAB_H to 90f).pan, 0f)
        assertEquals(0f, focusCamera(900f, 420f, aim = 300f to 366f).pan, 0f)
        // one at the very top is held to what there is
        assertEquals(tall.maxPan, focusCamera(900f, 420f, aim = 834f to 900f).pan, 0f)
        // one taller than the window keeps its foot on the slab rather than its top in view
        val giant = focusCamera(2000f, 300f, aim = 500f to 1400f)
        assertEquals(giant.slabTop, giant.above(500f), .001f)
        // the owner's own pan wins over the aim, and is held to the tower too
        assertEquals(120f, focusCamera(900f, 420f, aim = 560f to 626f, pan = 120f).pan, 0f)
        assertEquals(0f, focusCamera(900f, 420f, pan = -50f).pan, 0f)
        assertEquals(tall.maxPan, focusCamera(900f, 420f, pan = 5000f).pan, 0f)
        // the scale stays within its bounds whatever the sizes
        for (h in listOf(1f, 60f, 300f, 2000f)) for (a in listOf(120f, 400f, 900f)) assertTrue(
            focusCamera(h, a).scale in CAMERA_MIN..CAMERA_MAX
        )
    }

    /** The views the scene is checked in: (height, heading's band, controls' band), in dp. */
    private val views =
        listOf(
            SceneWindow(617f, 126f, 230f), // the screenshots' phone
            SceneWindow(617f, 190f, 300f), // and at twice the text size
            SceneWindow(890f, 126f, 230f),
            SceneWindow(1200f, 140f, 250f),
            SceneWindow(480f, 126f, 230f), // too small for both bands
        )

    private val towers =
        listOf("city", "tall", "done")
            .flatMap { HabitsScenarios.projection(it).stacks }
            .map { it to towerLayout(it, ghost = true) }

    @Test
    fun theWindowKeepsBetweenTheHeadingAndTheControls() {
        val w = SceneWindow(617f, 126f, 230f)
        assertEquals(617f - 230f - SceneWindow.GROUND_MARGIN, w.ground, 0f)
        assertEquals(w.ground - 126f, w.avail, 0f)
        assertEquals(126f, w.windowTop, 0f)
        assertEquals(w.ground - 50f, w.y(50f), 0f)
        // a view too small for both bands still gives a tower some room, from the ground up
        val tiny = SceneWindow(480f, 126f, 230f)
        assertEquals(SceneWindow.MIN_AVAIL, tiny.avail, 0f)
        assertEquals(tiny.ground - SceneWindow.MIN_AVAIL, tiny.windowTop, 0f)
        for (v in views) {
            assertTrue(v.ground <= v.height - v.bottom - SceneWindow.GROUND_MARGIN)
            assertEquals(v.avail, v.ground - v.windowTop, .001f)
        }
    }

    @Test
    fun theFoundationAlwaysStandsClearOfTheControls() {
        for (v in views) for ((stack, t) in towers) {
            val aims = listOf(null) + stack.blocks.mapNotNull { t.span(it.id) }
            for (aim in aims) {
                val probe = focusCamera(t.height, v.avail, aim)
                val pans = listOf(null, 0f, probe.maxPan / 2f, probe.maxPan, 9999f)
                for (pan in pans) {
                    val c = focusCamera(t.height, v.avail, aim, pan)
                    // the slab, and so its name, is drawn whole between the window's top and the
                    // ground line, which is over the controls' band, wherever the camera is
                    val slabTop = v.y(c.slabTop)
                    val slabBottom = v.y(0f)
                    assertTrue(slabBottom <= v.height - v.bottom - SceneWindow.GROUND_MARGIN)
                    assertTrue(slabTop >= v.windowTop && slabTop < slabBottom)
                    assertEquals(c.scale * T.SLAB_H, c.slabTop, .001f)
                    assertTrue(c.pan in 0f..c.maxPan)
                }
            }
        }
    }

    @Test
    fun theTopOfATowerKeepsUnderTheHeadingUnlessItIsSlid() {
        for (v in views) for ((stack, t) in towers) {
            val aim = stack.next.takeIf { it.isNotEmpty() }?.let(t::span)
            val c = focusCamera(t.height, v.avail, aim)
            val topAtRest = v.y(c.scale * t.height)
            if (c.maxPan == 0f) {
                // it fits: its very top (the outline for the next habit, the reward under it) is
                // under the window's top, clear of the fade there
                assertTrue(topAtRest >= v.windowTop + TOP_FADE)
            } else {
                // it does not: it is at its smallest, and slid all the way its top is in view
                assertEquals(CAMERA_MIN, c.scale, 0f)
                val slid = focusCamera(t.height, v.avail, aim, c.maxPan)
                assertTrue(v.y(slid.above(t.height)) >= v.windowTop + TOP_FADE)
                // the block to lay next shows whole under the window's top and over the slab,
                // unless it is taller than the window itself
                if (aim != null && c.scale * (aim.second - aim.first) + c.slabTop <= v.avail) {
                    assertTrue(v.y(c.above(aim.second)) >= v.windowTop)
                    assertTrue(c.above(aim.first) >= c.slabTop - .001f)
                }
            }
        }
        // the headroom is enough for the fade at any scale
        assertTrue(CAMERA_MIN * T.HEADROOM >= TOP_FADE)
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
        // the tallest fits under the strip kept for the sun and the moon
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
    fun theSunAndTheMoonKeepBesideTheTowerAndUnderTheHeading() {
        val width = 411f
        val top = 126f
        val horizon = 327f
        val column = 110f
        for (tenth in 0 until 240) {
            val hour = tenth / 10f
            val (x, y) = orbSpot(hour, width, top, horizon, column)
            // beside the tower's column, a disc and more away from it, and on the screen
            assertTrue(kotlin.math.abs(x - width / 2f) >= column + 13f)
            assertTrue(x >= ORB_EDGE && x <= width - ORB_EDGE)
            // under the heading's band and over the horizon
            assertTrue(y - 13f >= top)
            assertTrue(y + 13f <= horizon)
            // in the city it stands in the strip kept clear over the towers
            val (_, high) = orbSpot(hour, width, top, horizon, column, city = 1f)
            assertTrue(high - 13f >= top && high + 13f <= top + CITY_LABEL)
        }
        // left before the middle of its arc, right after it; higher towards the middle
        assertTrue(orbSpot(9f, width, top, horizon, column).first < width / 2f)
        assertTrue(orbSpot(16f, width, top, horizon, column).first > width / 2f)
        assertTrue(
            orbSpot(12f, width, top, horizon, column).second <
                orbSpot(8f, width, top, horizon, column).second
        )
        // a window with no room between its top and the horizon still keeps it under the heading
        assertTrue(orbSpot(8f, width, 300f, 310f, column).second >= 300f)
        // a tower as wide as the view leaves no lane: the orb stays on the screen
        assertEquals(ORB_EDGE, orbSpot(9f, 200f, top, horizon, column).first, 0f)
    }

    @Test
    fun marksAreTheSameEveryTimeAndTheirOwn() {
        val a = Dice("s1h4")
        val b = Dice("s1h4")
        val first = List(6) { a.next() }
        assertEquals(first, List(6) { b.next() })
        assertTrue(first.all { it >= 0f && it < 1f })
        assertNotEquals(first, Dice("s1h5").let { d -> List(6) { d.next() } })
        val crack = crackLine("s1h6", tall = false)
        assertEquals(crack, crackLine("s1h6", tall = false))
        assertNotEquals(crack, crackLine("s1h7", tall = false))
    }

    @Test
    fun aCrackBelongsToItsOwnBlock() {
        val keys = List(400) { "s${it % 7}h$it" }
        for (key in keys) for (tall in listOf(false, true)) {
            val crack = crackLine(key, tall)
            assertTrue(crack.size in (if (tall) 5..6 else 4..5))
            // it starts on the block's top edge and goes down, turn after turn
            assertEquals(0f, crack.first().second, 0f)
            assertTrue(crack.zipWithNext().all { (p, q) -> q.second > p.second })
            // it keeps inside the face, in its right-hand half, clear of the name
            assertTrue(crack.all { it.first in CRACK_LEFT..CRACK_RIGHT })
            // and stops inside it: it never reaches the bottom edge, where the crack of the block
            // underneath would begin, so two cracks never read as one
            assertTrue(crack.last().second <= CRACK_DEPTH)
            assertTrue(crack.last().second >= .3f)
            // it zigzags: each turn goes the other way
            val turns = crack.zipWithNext().drop(1).map { (p, q) -> q.first > p.first }
            assertTrue(turns.zipWithNext().all { (p, q) -> p != q })
        }
        // each block's is its own: where it starts and how deep it goes differ from block to block
        assertTrue(keys.map { crackLine(it, false).first().first }.toSet().size > 300)
        val starts = keys.map { crackLine(it, false).first().first }
        assertTrue(starts.max() - starts.min() > .15f)
        val depths = keys.map { crackLine(it, false).last().second }
        assertTrue(depths.max() - depths.min() > .2f)
        // the three cracked blocks of the screenshots' tower do not line up
        val mine =
            coffee.blocks.drop(1).map { crackLine(coffee.id + it.id, tall = true).first().first }
        assertEquals(3, mine.toSet().size)
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
