package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.PetArtResources
import com.lifemax.rpg.feature.pet.body.Footprints
import com.lifemax.rpg.feature.pet.body.Headroom
import com.lifemax.rpg.feature.pet.body.Occluders
import com.lifemax.rpg.feature.pet.body.PairPose
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.World
import com.lifemax.rpg.feature.pet.brain.Spot
import com.lifemax.rpg.feature.pet.brain.Spots
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every place the brain rests a cat at, measured in the room (the owner's "still clipped into
 * sofa"): each spot of [Spots] and both places of the pair's curl-up at each place two cats curl up
 * at, with a stand-in cat (a box the size of a grown cat or a kitten lying, loafing, sitting up and
 * asleep) on a software canvas, in the 2.5D room at RICH and MEDIUM with the camera held at either
 * end of its swing and at rest, and in the classic room.
 *
 * Furniture may cover a cat only when it stands in front of it: nearer than the cat and reaching
 * down to the cat's feet (the pink cushion before the sofa's seat, the bed's rim, the box's front,
 * the pouf before a cat on the rug). What else covers it (the seat or arm of the sofa it lies on, a
 * platform over its head, a piece behind it) is the cat sinking into furniture or clipped by it,
 * and no place may leave more than [MAX_WRONG] of a cat that way, in any pose the body plays there
 * ([Headroom]: under the cat tree's top platform a cat does not sit up; a scratching place is stood
 * at). No floor place is inside a piece of furniture ([Footprints]). Both tables (all the cover,
 * and the cover that is wrong) are written to the test's standard output; `SPOT_AUDIT_ALL=1` also
 * measures the poses the body does not play.
 */
class SpotAuditTest {
    private val art = PetArtResources.loadRoom()
    private val layers = art.layers!!
    private val scenes = STYLES.map { RoomScene(art, it) }

    /** A pose's box at full scale, art pixels: a grown cat; a kitten is [KITTEN] of it. */
    private class Pose(val name: String, val w: Int, val h: Int, val upright: Boolean = false)

    /** One cat to measure: where, on what. */
    private class Place(val label: String, val spot: Spot, val x: Float)

    private class One(
        private val x: Float,
        private val z: Float,
        private val feet: Float,
        private val surface: String,
        private val w: Int,
        private val h: Int,
    ) : CatLayer {
        lateinit var canvas: RoomCanvas
        override val count = 1

        override fun depth(i: Int) = z

        override fun feet(i: Int) = feet

        override fun x(i: Int) = x

        override fun surface(i: Int) = surface

        override fun draw(i: Int) = draw(i, 0, RoomStyle.CLASSIC, canvas)

        override fun draw(i: Int, shift: Int, style: RoomStyle, canvas: RoomCanvas) {
            val r = box()
            canvas.fill(r[0] + shift, r[1], r[2], r[3], CAT)
        }

        fun box(): IntArray {
            val k = World.depthScale(z)
            val bw = Math.round(w * k)
            val bh = Math.round(h * k)
            val house = Math.round(x - RoomLayout.HOUSE_X0)
            return intArrayOf(house - bw / 2, Math.round(feet) - bh, bw, bh)
        }
    }

    /** The row a cat on [spot] sorts at (as [DepthScene] places it) and the pieces it stands on. */
    private fun sortRow(spot: Spot, z: Float): Pair<Float, Set<String>> {
        val on = layers.pieces.filter { it.volume?.surfaces?.containsKey(spot.surface.key) == true }
        val v = on.lastOrNull()?.volume ?: return World.floorY(z) to emptySet()
        return v.sortFront - v.surfaces.getValue(spot.surface.key) to on.map { it.name }.toSet()
    }

    /** The pieces in front of a cat sorting at [key] with feet on [feet] (not those under it). */
    private fun inFront(key: Float, feet: Float, under: Set<String>): List<Occluders.Piece> =
        Occluders.pieces.filter {
            it.name !in under &&
                it.bottom >= feet - TOUCH_ROWS &&
                (!it.layered || it.front > key + CAT_AFTER)
        }

    /** [all the cover, the wrong cover] of a cat at [place] in [pose], the worst of all views. */
    private fun cover(place: Place, pose: Pose, scale: Float): FloatArray {
        val spot = place.spot
        val z = if (spot.onFloor) spot.z else World.surface(spot.surface).z
        val feet = World.groundY(spot.surface, z)
        val (key, under) = sortRow(spot, z)
        val front = inFront(key, feet, under)
        val w = Math.round(pose.w * scale)
        val h = Math.round(pose.h * scale)
        val worst = FloatArray(2)
        for (scene in scenes) {
            for (pan in if (scene.style.volume) PANS else floatArrayOf(0f)) {
                val cat = One(place.x, z, feet, spot.surface.key, w, h)
                scene.depth?.sway?.restAt(pan)
                val canvas = SoftCanvas(art)
                cat.canvas = canvas
                scene.paintBackdrop(canvas, HOUR, 0.0, 0)
                scene.paintScene(canvas, cat)
                // a cat on a piece moves with the piece, not with the floor at its depth: the box
                // is found where it was drawn, a few pixels either side of the floor's shift
                val shift = scene.depth?.shiftAtZ(z) ?: 0
                val reach = if (scene.depth == null) 0 else SEARCH_PX
                val share =
                    (-reach..reach)
                        .map { measure(canvas, cat.box(), shift + it, front) }
                        .minByOrNull { it[0] }!!
                worst[0] = maxOf(worst[0], share[0])
                worst[1] = maxOf(worst[1], share[1])
            }
        }
        return worst
    }

    /**
     * The share of the box that is not the cat's colour, and of the box the share no piece in
     * [front] stands over (its box, give or take what the sway moves it by).
     */
    private fun measure(
        c: SoftCanvas,
        b: IntArray,
        shift: Int,
        front: List<Occluders.Piece>,
    ): FloatArray {
        var covered = 0
        var wrong = 0
        var area = 0
        for (y in b[1] until b[1] + b[3]) {
            for (x in b[0] + shift until b[0] + shift + b[2]) {
                if (x < 0 || x >= c.width || y < 0 || y >= c.height) continue
                area++
                if (c.pixels[y * c.width + x] == CAT) continue
                covered++
                val wx = x + RoomLayout.HOUSE_X0
                val stands =
                    front.any {
                        wx >= it.x0 - SWAY_PX &&
                            wx < it.x1 + SWAY_PX &&
                            y >= it.top - 1 &&
                            y < it.bottom + 1
                    }
                if (!stands) wrong++
            }
        }
        if (area == 0) return FloatArray(2)
        return floatArrayOf(covered.toFloat() / area, wrong.toFloat() / area)
    }

    /** Every spot, and both cats of a curl-up at each place two cats curl up at, either way. */
    private fun places(): List<Place> {
        val out = ArrayList<Place>()
        for (s in Spots.all) out += Place(s.id, s, s.x)
        for (s in CURL_SPOTS) {
            val z = if (s.onFloor) s.z else World.surface(s.surface).z
            val gap = PairPose.SNUGGLE.gap(ADULT, ADULT, z)
            for (f in intArrayOf(1, -1)) {
                val side = if (f > 0) "R" else "L"
                val front = fit(s, s.x - f * gap / 2f) ?: continue
                val behind = fit(s, front + f * gap) ?: continue
                out += Place("${s.id} curl front $side", s, front)
                out += Place("${s.id} curl behind $side", s, behind)
            }
        }
        return out
    }

    private fun fit(s: Spot, x: Float): Float? {
        if (s.surface == SurfaceId.FLOOR) return x
        val sf = World.surface(s.surface)
        return if (x >= sf.x0 && x <= sf.x1) x else null
    }

    /** Whether the body plays [pose] for a cat of [size] at [spot]. */
    private fun played(pose: Pose, spot: Spot, size: Float): Boolean =
        System.getenv("SPOT_AUDIT_ALL") != null ||
            if (pose.upright) Occluders.CAT_UPRIGHT_H * size <= Headroom.of(spot.surface)
            else !spot.has(Spots.SCRATCH)

    @Test
    fun everyRestingPlaceShowsTheCatOnItsSurface() {
        val head = StringBuilder(String.format("%-26s", "place (grown | kitten)"))
        for (size in SIZES) for (p in POSES) head.append(String.format(" %7s", p.name))
        val total = StringBuilder("all the cover\n").append(head).append('\n')
        val wrong = StringBuilder("the wrong cover (limit ${(MAX_WRONG * 100).toInt()} %)\n")
        wrong.append(head).append('\n')
        val bad = ArrayList<String>()
        for (p in places()) {
            total.append(String.format("%-26s", p.label))
            wrong.append(String.format("%-26s", p.label))
            for (size in SIZES) {
                for (pose in POSES) {
                    if (!played(pose, p.spot, size)) {
                        total.append("     n/a")
                        wrong.append("     n/a")
                        continue
                    }
                    val v = cover(p, pose, size)
                    total.append(String.format(" %6.0f%%", v[0] * 100))
                    wrong.append(String.format(" %6.0f%%", v[1] * 100))
                    if (v[1] > MAX_WRONG) bad += "${p.label} ${pose.name} x$size"
                }
            }
            total.append('\n')
            wrong.append('\n')
            if (p.spot.onFloor && Footprints.insideAny(p.x, p.spot.z) != null) {
                bad += "${p.label} inside furniture"
            }
        }
        println(total)
        println(wrong)
        assertTrue("clipped past the limit: $bad\n$wrong", bad.isEmpty())
    }

    private companion object {
        const val CAT = 0xFFFF00FF.toInt()
        const val HOUR = 12.0
        const val ADULT = -1
        const val CAT_AFTER = 0.001f

        /** A piece standing this many rows short of a cat's feet still stands in front of it. */
        const val TOUCH_ROWS = 1f

        /** How far from the floor's shift a cat on a piece may be drawn, art pixels. */
        const val SEARCH_PX = 3

        /** How far the sway moves a piece against a cat near it, art pixels. */
        const val SWAY_PX = 6

        /** The most of a cat furniture may cover that does not stand in front of it. */
        const val MAX_WRONG = 0.15f

        /** A kitten's box is this much of a grown cat's. */
        const val KITTEN = 0.75f
        val SIZES = floatArrayOf(1f, KITTEN)

        val PANS = floatArrayOf(-1f, 0f, 1f)
        val STYLES =
            listOf(
                RoomStyle.live(DepthLevel.RICH),
                RoomStyle.live(DepthLevel.MEDIUM),
                RoomStyle.CLASSIC,
            )
        val POSES =
            listOf(
                Pose("lying", 38, 18),
                Pose("loaf", 32, 20),
                Pose("sitting", 24, 44, upright = true),
                Pose("asleep", 40, 16),
            )
        val CURL_SPOTS = listOf(Spots.COUCH_L, Spots.LIVING_RUG, Spots.RUG)
    }
}
