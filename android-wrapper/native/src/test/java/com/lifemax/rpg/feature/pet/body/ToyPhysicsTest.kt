package com.lifemax.rpg.feature.pet.body

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The physics of a free toy: it never passes through the floor or the furniture and it settles. */
class ToyPhysicsTest {
    private fun finite(t: ToyBody): Boolean =
        listOf(t.x, t.y, t.z, t.vx, t.vy, t.angle, t.drawX, t.drawY).all { it.isFinite() }

    /**
     * True when the toy went through the side of a solid this tick: it was outside the solid's
     * sides and is now inside them, level with its body (entering from above is a landing on it).
     */
    private fun crossedASide(t: ToyBody, px: Float, prevFoot: Float): Boolean {
        val hw = t.kind.width / 2f * World.depthScale(t.z)
        val top = t.y - t.kind.halfHeight * World.depthScale(t.z)
        for (s in Solids.all) {
            val level = prevFoot > s.top + 3f && top < s.bottom - 5f
            val wasOut = px <= s.x0 + hw || px >= s.x1 - hw
            val isIn = t.x > s.x0 + hw + 0.6f && t.x < s.x1 - hw - 0.6f
            if (level && wasOut && isIn) return true
        }
        return false
    }

    /** Throw every kind from a grid of floor places and velocities and check the invariants. */
    @Test
    fun nothingPassesThroughTheFloorOrTheFurnitureAndEverythingSettles() {
        val field = ToyField()
        val starts = floatArrayOf(0f, 20f, 40f, 90f, 196f)
        val vxs = floatArrayOf(-160f, -60f, 0f, 70f, 160f)
        val vys = floatArrayOf(-220f, -120f, -30f)
        for (kind in ToyKind.entries) for (x0 in starts) for (vx in vxs) for (vy in vys) {
            val t = field.add(kind, x0)
            t.launch(vx, vy)
            val before = field.ticks
            var lastVy = 0f
            var px = t.x
            var prevFoot = t.footY()
            while (field.ticks - before < SETTLE_TICKS && t.state != ToyState.REST) {
                field.advance(ToyTuning.DT)
                assertTrue("finite $kind", finite(t))
                val floor = World.floorY(t.z)
                assertTrue(
                    "$kind below the floor at ${t.x},${t.footY()}",
                    t.footY() <= floor + 0.01f,
                )
                assertFalse(
                    "$kind went through furniture at ${t.x},${t.y} from $px, foot $prevFoot, v ${t.vx},${t.vy} start $x0,$vx,$vy",
                    crossedASide(t, px, prevFoot),
                )
                px = t.x
                prevFoot = t.footY()
                assertTrue("$kind left the house", t.x in World.HOUSE_MIN_X..World.HOUSE_MAX_X)
                if (lastVy > 0f && t.vy < 0f && t.airborne) {
                    // a bounce: it keeps no more than the toy's share of the speed
                    assertTrue("$kind bounced too high", -t.vy <= lastVy * kind.restitution + 4f)
                }
                lastVy = t.vy
            }
            assertEquals("$kind from $x0 at $vx,$vy never rested", ToyState.REST, t.state)
        }
    }

    /** Energy never grows: every bounce loses speed and the toy ends lower than it began. */
    @Test
    fun bouncesLoseEnergy() {
        val field = ToyField()
        val t = field.add(ToyKind.SPRING, 70f)
        t.y -= 100f
        t.launch(0f, 0f)
        var peakBefore = t.y
        var peaks = 0
        var last = 0f
        var vy = 0f
        repeat(600) {
            field.advance(ToyTuning.DT)
            if (vy < 0f && t.vy >= 0f) {
                // apex of a bounce: each is lower than the one before (smaller y is higher)
                if (peaks > 0) assertTrue("apex $last -> ${t.y}", t.y >= last - 0.01f)
                last = t.y
                peaks++
            }
            vy = t.vy
            peakBefore = minOf(peakBefore, t.y)
        }
        assertTrue("the spring bounced several times", peaks >= 3)
    }

    /** A toy dropped onto each surface rests on it, at the right height. */
    @Test
    fun landsOnEverySurfaceItIsOver() {
        for (id in SurfaceId.entries) {
            if (id == SurfaceId.FLOOR || World.surface(id).prop) continue
            val s = World.surface(id)
            val field = ToyField()
            val t = field.add(ToyKind.BALL_B, (s.x0 + s.x1) / 2f)
            t.y = s.y - 12f
            t.launch(0f, 0f)
            repeat(600) { field.advance(ToyTuning.DT) }
            assertEquals("$id", id, t.surface)
            assertEquals("$id rests", ToyState.REST, t.state)
            assertEquals("$id height", s.y - t.kind.halfHeight, t.y, 0.01f)
        }
    }

    /** A toy that rolls off the end of a surface falls to the floor. */
    @Test
    fun rollsOffTheEndAndFalls() {
        val field = ToyField()
        val s = World.surface(SurfaceId.FRIDGE)
        val t = field.add(ToyKind.YARN, s.x1 - 6f, SurfaceId.FRIDGE)
        t.launch(90f, 0f)
        repeat(1_200) { field.advance(ToyTuning.DT) }
        assertTrue(
            "fell to the floor or the worktop: ${t.surface}",
            t.surface == SurfaceId.FLOOR || t.surface == SurfaceId.COUNTER,
        )
        assertEquals(ToyState.REST, t.state)
    }

    /** The ends of the house and the side of the counter turn a toy back. */
    @Test
    fun wallsTurnTheToyBack() {
        val field = ToyField()
        val t = field.add(ToyKind.BALL_A, World.HOUSE_MAX_X - 20f)
        t.launch(200f, 0f)
        repeat(30) { field.advance(ToyTuning.DT) }
        assertTrue(t.x <= World.HOUSE_MAX_X - ToyTuning.WALL_MARGIN + 0.01f)
        val counter = Solids.all.first { it.name == "counter" }
        val u = field.add(ToyKind.BALL_A, counter.x0 - 30f)
        u.y -= 30f
        u.launch(140f, -20f)
        repeat(240) { field.advance(ToyTuning.DT) }
        assertTrue("stopped at the counter's side, at ${u.x}", u.x < counter.x0)
    }

    /** A feather falls slower than a ball, through the same drop. */
    @Test
    fun lightToysFloatDown() {
        fun fallTime(kind: ToyKind): Int {
            val field = ToyField()
            val t = field.add(kind, 60f)
            t.y -= 150f
            t.launch(0f, 0f)
            var n = 0
            while (t.airborne && t.vy >= 0f && n < 2_000) {
                field.advance(ToyTuning.DT)
                n++
            }
            return n
        }
        assertTrue(fallTime(ToyKind.FEATHER) > fallTime(ToyKind.BALL_A) * 3 / 2)
    }

    /** The aim solver puts a toss where it says. */
    @Test
    fun aimedTossesLand() {
        val out = FloatArray(3)
        val kind = ToyKind.BALL_A
        val field = ToyField()
        val t = field.add(kind, 10f)
        val x1 = 80f
        val y1 = World.FLOOR_Y_ART - kind.halfHeight
        t.y = 100f
        ToyPhysics.aim(kind, t.x, t.y, x1, y1, 20f, out)
        t.launch(out[0], out[1])
        t.vx = out[0]
        var landed = false
        repeat(Math.round(out[2] / ToyTuning.DT) + 2) {
            field.advance(ToyTuning.DT)
            if (!landed && !t.airborne) landed = true
        }
        assertEquals(x1, t.x, 6f)
    }

    private companion object {
        const val SETTLE_TICKS = 120 * 40
    }
}
