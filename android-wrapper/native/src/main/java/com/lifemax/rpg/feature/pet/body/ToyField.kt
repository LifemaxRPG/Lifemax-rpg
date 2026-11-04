package com.lifemax.rpg.feature.pet.body

/**
 * The toys of the house, shared by every cat. Free toys are moved by [ToyPhysics] on the same fixed
 * 1/120 s tick as the cat body: a cat's body steps the field once per tick it runs ([stepTo] counts
 * ticks, so two cats stepping together move the toys once), which keeps a toy tossed at 60 and at
 * 120 frames per second on the same path. Hosts that run no cat (a test of the physics alone) call
 * [advance] instead; never mix the two on one field.
 *
 * Toys are added up front (allocation belongs there); stepping allocates nothing.
 */
class ToyField {
    private val toys = ArrayList<ToyBody>()
    private var clock = 0.0

    /** Number of ticks the field has simulated. */
    var ticks = 0L
        private set

    /** Number of toys. */
    val count: Int
        get() = toys.size

    /** Add a toy of [kind] lying at [x] on [on] (depth [z] on the floor). */
    fun add(
        kind: ToyKind,
        x: Float,
        on: SurfaceId = SurfaceId.FLOOR,
        z: Float = World.ART_Z,
    ): ToyBody {
        val toy = ToyBody(toys.size, kind)
        toy.place(x, on, z)
        toys.add(toy)
        return toy
    }

    /** The toy with [id], or null. */
    operator fun get(id: Int): ToyBody? = toys.getOrNull(id)

    /** The nearest toy on [surface] a cat could pick up, or null. */
    fun nearestPickable(x: Float, surface: SurfaceId): ToyBody? {
        var best: ToyBody? = null
        var bestD = Float.MAX_VALUE
        for (i in toys.indices) {
            val t = toys[i]
            if (!t.canBePicked || t.surface != surface) continue
            val d = Mathx.abs(t.x - x)
            if (d < bestD) {
                bestD = d
                best = t
            }
        }
        return best
    }

    /** Bring the field to tick [n] (called by cat bodies; a tick already done is skipped). */
    internal fun stepTo(n: Long) {
        while (ticks < n) {
            ticks++
            stepAll()
        }
    }

    /** Standalone host: advance by [dt] seconds in whole ticks. */
    fun advance(dt: Float) {
        clock += maxOf(0f, minOf(dt, Tuning.Time.MAX_FRAME_DT)).toDouble()
        while (clock >= ToyTuning.DT - Tuning.Time.TOLERANCE) {
            clock -= ToyTuning.DT
            ticks++
            stepAll()
        }
    }

    private fun stepAll() {
        for (i in toys.indices) {
            val t = toys[i]
            if (t.state == ToyState.HELD || t.state == ToyState.LIFT) continue
            ToyPhysics.step(t, ToyTuning.DT)
            if (t.kind.thread) {
                Carry.stepThread(t, ToyTuning.DT, World.depthScale(t.z))
            }
        }
    }
}
