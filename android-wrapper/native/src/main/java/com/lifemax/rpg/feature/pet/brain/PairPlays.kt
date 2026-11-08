package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.Occluders
import com.lifemax.rpg.feature.pet.body.PairPose
import com.lifemax.rpg.feature.pet.body.Rng
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.World

/** One cat's part of a stage: what it plays ([activity]) and how ([script]). */
internal class Part(val activity: Activity, val script: Script)

/**
 * One stage of a moment together: a [Part] for each cat that has one this stage (by slot; null: the
 * cat goes on as it is, a sleeper being joined). A [final] stage ends the moment: each cat plays
 * its part to the end and then picks anew. A stage can [report] a kind to the page (with the
 * standoff's [winner] slot).
 */
internal class Stage(val final: Boolean) {
    val parts = arrayOfNulls<Part>(2)
    var report: String? = null
    var winner = -1
}

/**
 * The choreography of the moments two cats share (plan 1.8 pair rows), stage by stage, rewritten
 * natively after the page's pair director (29-pet-pair.js, 29-pet-rank.js) for the same effect: a
 * nose touch that early on ends in a hiss, a chase with role swaps, grooming with the favour
 * returned, curling up on one soft spot, a standoff over a perch. Poses together stand at the art
 * lab's offsets ([PairPose]), never on one point. Each builder reads where the cats are now (the
 * previous stage left them there) and answers null when the stage cannot be played (no way there),
 * which gives the moment up.
 */
internal class PairPlays(internal val minds: List<CatMind>, internal val rng: () -> Rng) {
    internal val t = SocialTuning

    // ---------------------------------------------------------------- geometry

    internal fun stage(m: CatMind): Int = m.persona.stage

    /** Where a cat stands on the floor, or the page's middle depth when it is up on something. */
    internal fun floorZ(m: CatMind): Float = if (m.exec.surface == SurfaceId.FLOOR) m.exec.z else Z0

    /** A floor depth both may stand at near [x], in plain view. */
    internal fun meetZ(x: Float, z: Float): Float =
        Occluders.visibleZ(x, z.coerceIn(t.MEET_Z_LO, t.MEET_Z_HI))

    internal fun inHouse(x: Float): Float =
        x.coerceIn(World.HOUSE_MIN_X + t.EDGE_PX, World.HOUSE_MAX_X - t.EDGE_PX)

    internal fun range(lo: Float, hi: Float): Float = rng().range(lo, hi)

    internal fun seconds(lo: Double, hi: Double): Double = Beats.seconds(rng(), lo, hi)

    /**
     * The way for cat [m] to ([x], [z]) on the floor into [s] (getting up properly first when it
     * slept), or false when it cannot get down.
     */
    internal fun toFloor(m: CatMind, x: Float, z: Float, s: Script, run: Boolean = false): Boolean {
        if (m.runner.activity.asleep) s.wake()
        if (m.exec.surface != SurfaceId.FLOOR) {
            val r = m.exec.route(SurfaceId.FLOOR, x) ?: return false
            s.route(r.steps)
        }
        if (run) s.dash(x, z) else s.approach(x, z)
        return true
    }

    /** The way for cat [m] to [x] on raised [surface] into [s], or false. */
    internal fun toSurface(m: CatMind, surface: SurfaceId, x: Float, s: Script): Boolean {
        if (m.runner.activity.asleep) s.wake()
        val r = m.exec.route(surface, x) ?: return false
        s.route(r.steps)
        return true
    }

    /** The way to ([x], [z]) on [surface] (the floor or a raised one). */
    internal fun goTo(m: CatMind, surface: SurfaceId, x: Float, z: Float, s: Script): Boolean =
        if (surface == SurfaceId.FLOOR) toFloor(m, x, z, s) else toSurface(m, surface, x, s)

    internal fun part(a: Activity, s: Script) = Part(a, s)

    internal fun script(build: Script.() -> Unit): Script = Script().apply(build)

    /** A few steps away from the other cat, on the floor (or nowhere when up on something). */
    internal fun Script.away(m: CatMind, from: Float, lo: Float, hi: Float, run: Boolean = false) {
        if (m.exec.surface != SurfaceId.FLOOR) return
        val dir = if (m.exec.x >= from) 1f else -1f
        var x = World.clampHouse(m.exec.x + dir * range(lo, hi))
        if (Math.abs(x - from) < lo) x = World.clampHouse(m.exec.x - dir * range(lo, hi))
        if (run) dash(x, m.exec.z) else stroll(x, m.exec.z)
    }

    // ---------------------------------------------------------------- nose touch

    /**
     * They meet face to face at the greeting pose's distance, between them (or near the one far).
     */
    fun greetMeet(o: Moment): Stage? {
        val a = minds[o.a]
        val b = minds[o.b]
        val ax = a.exec.x
        val bx = b.exec.x
        val far = Math.abs(ax - bx) > t.MEET_FAR_PX
        val mid = inHouse(if (far) bx + Math.signum(ax - bx) * t.MEET_NEAR_PX else (ax + bx) / 2f)
        val z = meetZ(mid, (floorZ(a) + floorZ(b)) / 2f)
        val gap = PairPose.GREET.gap(stage(a), stage(b), z)
        val side = if (ax <= bx) -1 else 1
        o.mx = mid
        o.mz = z
        val st = Stage(final = false)
        val sa = Script()
        if (!toFloor(a, mid + side * gap / 2f, z, sa)) return null
        sa.face(-side)
        val sb = Script()
        if (!toFloor(b, mid - side * gap / 2f, z, sb)) return null
        sb.face(side)
        st.parts[o.a] = part(PairGreet, sa)
        st.parts[o.b] = part(PairGreet, sb)
        return st
    }

    /** Noses touch (the lab's nose_touch pose); friends then rub cheeks. */
    fun greetTouch(o: Moment): Stage {
        val st = Stage(final = false)
        for (slot in intArrayOf(o.a, o.b)) {
            val s = script {
                act(PairPose.GREET.a, t.TOUCH_S)
                if (!o.hiss) act(Rows.RUB, t.RUB_S)
            }
            st.parts[slot] = part(PairGreet, s)
        }
        return st
    }

    /** How the nose touch ends: a hiss early on, else a slow blink or a cheek rub (the page's). */
    fun greetEnd(o: Moment): Stage {
        val st = Stage(final = true)
        val a = minds[o.a]
        val b = minds[o.b]
        if (o.hiss) {
            st.report = Social.HISS
            st.parts[o.b] =
                part(
                    PairGreet,
                    script {
                        act(Rows.MEOW, HISS_MEOW_S)
                        away(b, a.exec.x, t.HISS_RUN_LO, t.HISS_RUN_HI, run = true)
                        act(Rows.SIT, AFTER_SIT_S)
                        act(Rows.GROOM, seconds(GROOM_LO_S, GROOM_HI_S))
                    },
                )
            st.parts[o.a] =
                part(
                    PairGreet,
                    script {
                        act(Rows.SIT, SHORT_SIT_S)
                        act(Rows.GROOM, seconds(GROOM_LO_S - 1.0, GROOM_HI_S - 2.0))
                        act(Rows.SIT, seconds(t.AFTER_LO_S, t.AFTER_HI_S))
                    },
                )
            return st
        }
        st.report = Social.GREET
        if (o.friends) {
            st.parts[o.a] =
                part(
                    PairGreet,
                    script {
                        act(Rows.RUB, RUB_LONG_S)
                        act(Rows.SIT, seconds(AFTER_SIT_S * 2, AFTER_SIT_S * 3.6))
                        act(Rows.GROOM, seconds(GROOM_LO_S - 1.0, GROOM_HI_S - 2.0))
                    },
                )
            st.parts[o.b] =
                part(
                    PairGreet,
                    script {
                        act(Rows.SLOWBLINK, RUB_LONG_S)
                        act(Rows.SIT, seconds(AFTER_SIT_S * 2.4, AFTER_SIT_S * 4))
                    },
                )
            return st
        }
        st.parts[o.a] =
            part(
                PairGreet,
                script {
                    act(Rows.SIT, SHORT_SIT_S)
                    act(Rows.SLOWBLINK, Rows.once(Rows.SLOWBLINK))
                    act(Rows.SIT, seconds(t.AFTER_LO_S, t.AFTER_HI_S))
                },
            )
        st.parts[o.b] =
            part(
                PairGreet,
                script {
                    act(Rows.SIT, SHORT_SIT_S)
                    away(b, a.exec.x, t.PART_LO, t.PART_HI)
                    act(Rows.LOAF, seconds(LOAF_LO_S, LOAF_HI_S))
                },
            )
        return st
    }

    // ---------------------------------------------------------------- chase

    /** The chaser (a) lines up behind the runner (b), who comes down to the floor if need be. */
    fun chaseLine(o: Moment): Stage? {
        val c = minds[o.a]
        val r = minds[o.b]
        val rx = inHouse(r.exec.x)
        var f = if (rx >= c.exec.x) 1 else -1
        val reach = t.CHASE_RUN_HI
        if (
            rx + f * reach > World.HOUSE_MAX_X - t.EDGE_PX ||
                rx + f * reach < World.HOUSE_MIN_X + t.EDGE_PX
        )
            f = -f
        val xc = inHouse(rx - f * t.CHASE_LINE_PX)
        val z = meetZ(rx, floorZ(r))
        o.f = f
        o.mz = z
        val st = Stage(final = false)
        val sc = Script()
        if (!toFloor(c, xc, z, sc)) return null
        sc.face(f)
        val sr = Script()
        val there =
            r.exec.surface == SurfaceId.FLOOR &&
                Math.abs(r.exec.x - rx) < 2f &&
                Math.abs(r.exec.z - z) < 1f
        if (!there && !toFloor(r, rx, z, sr)) return null
        sr.face(-f)
        st.parts[o.a] = part(PairChase, sc)
        st.parts[o.b] = part(PairChase, sr)
        return st
    }

    /** The stalk: a crouch and a wiggle; the runner sits, watching. */
    fun chaseStalk(o: Moment): Stage {
        val st = Stage(final = false)
        st.parts[o.a] =
            part(
                PairChase,
                script {
                    act(Rows.CROUCH, seconds(t.STALK_LO, t.STALK_HI))
                    act(Rows.CROUCH, t.WIGGLE_S)
                },
            )
        st.parts[o.b] = part(PairChase, script { act(Rows.SIT, RUNNER_SIT_S) })
        return st
    }

    /** The pounce and the bolt: both gallop across the room, the chaser close behind. */
    fun chaseGo(o: Moment): Stage {
        val c = minds[o.a]
        val r = minds[o.b]
        val f = o.f
        val target = inHouse(r.exec.x + f * range(t.CHASE_RUN_LO, t.CHASE_RUN_HI))
        val z = meetZ(target, o.mz + range(-DEPTH_WOBBLE, DEPTH_WOBBLE))
        val size = (c.persona.growthScale + r.persona.growthScale) / 2f
        val gap = t.CHASE_CATCH_PX * World.depthScale(z) * size
        o.tx = target
        val st = Stage(final = false)
        st.parts[o.a] =
            part(
                PairChase,
                script {
                    act(Rows.CROUCH, POUNCE_S)
                    dash(World.clampHouse(target - f * gap), z)
                    face(f)
                },
            )
        st.parts[o.b] =
            part(
                PairChase,
                script {
                    act(Rows.SIT, BOLT_S)
                    dash(target, z)
                    face(-f)
                },
            )
        return st
    }

    /** The tussle: the chaser bats and pounces, the runner rolls belly-up. */
    fun chaseTussle(o: Moment): Stage {
        val st = Stage(final = false)
        st.parts[o.a] =
            part(
                PairChase,
                script {
                    act(Rows.BAT, BAT_S)
                    act(Rows.PLAY, t.TUSSLE_S - BAT_S)
                },
            )
        st.parts[o.b] = part(PairChase, script { act(Rows.ROLL, t.TUSSLE_S) })
        return st
    }

    /** Another round with the roles swapped (up to three), or the end: a wash and a stretch. */
    fun chaseMore(o: Moment): Stage? {
        o.rounds++
        if (o.rounds < t.CHASE_ROUNDS && rng().chance(t.CHASE_SWAP_P)) {
            val was = o.a
            o.a = o.b
            o.b = was
            o.queue.addFirst(::chaseMore)
            o.queue.addFirst(::chaseTussle)
            o.queue.addFirst(::chaseGo)
            o.queue.addFirst(::chaseStalk)
            return chaseLine(o)
        }
        val st = Stage(final = true)
        st.report = Social.CHASE
        val b = minds[o.b]
        st.parts[o.a] =
            part(
                PairChase,
                script {
                    act(Rows.SIT, AFTER_SIT_S)
                    act(Rows.GROOM, seconds(GROOM_LO_S, GROOM_HI_S - 1.0))
                    act(Rows.SIT, seconds(AFTER_SIT_S * 2.4, AFTER_SIT_S * 4.8))
                },
            )
        st.parts[o.b] =
            part(
                PairChase,
                script {
                    act(Rows.STRETCH, Rows.once(Rows.STRETCH))
                    away(b, minds[o.a].exec.x, t.PART_LO, t.PART_HI + 5f)
                    act(Rows.GROOM, seconds(GROOM_LO_S, GROOM_HI_S - 1.0))
                },
            )
        return st
    }

    // ---------------------------------------------------------------- grooming

    /**
     * The groomer (a) comes alongside the resting cat (b), at the groom pose's distance, facing it;
     * the resting cat turns to face it (the pose's B is mirrored). On the floor or the sofa.
     */
    fun groomLine(o: Moment): Stage? {
        val g = minds[o.a]
        val r = minds[o.b]
        val surface = r.exec.surface
        val rx = r.exec.x
        val z = if (surface == SurfaceId.FLOOR) r.exec.z else World.surface(surface).z
        val off = PairPose.GROOM.gap(stage(g), stage(r), z)
        var f = if (g.exec.x <= rx) 1 else -1
        val gx = fit(surface, rx - f * off) ?: fit(surface, rx + f * off).also { f = -f }
        gx ?: return null
        o.f = f
        val st = Stage(final = false)
        val sg = Script()
        if (!goTo(g, surface, gx, z, sg)) return null
        sg.face(f)
        st.parts[o.a] = part(PairGroom, sg)
        st.parts[o.b] =
            part(
                PairGroom,
                script {
                    face(-f)
                    act(Rows.LOAF, LOAF_BEAT_S)
                },
            )
        o.surface = surface
        return st
    }

    /** [x] on [surface] when a cat fits there, else null. */
    internal fun fit(surface: SurfaceId, x: Float): Float? {
        if (surface == SurfaceId.FLOOR) return if (x == inHouse(x)) x else null
        val s = World.surface(surface)
        return if (x >= s.x0 && x <= s.x1) x else null
    }

    /** The licking (groom_other) and the purring, mirrored. */
    fun groomDo(o: Moment): Stage {
        val st = Stage(final = false)
        val s = seconds(t.GROOM_LO_S, t.GROOM_HI_S)
        st.parts[o.a] = part(PairGroom, script { act(PairPose.GROOM.a, s) })
        st.parts[o.b] = part(PairGroom, script { act(PairPose.GROOM.b, s) })
        return st
    }

    /** Friends return the favour: the groomed cat licks back, the groomer purrs. */
    fun groomBack(o: Moment, rel: Double): Stage? {
        if (rel < t.GROOM_BACK_REL || !rng().chance(t.GROOM_BACK_P)) return null
        o.back = true
        val st = Stage(final = false)
        val s = seconds(t.GROOM_BACK_LO_S, t.GROOM_BACK_HI_S)
        st.parts[o.a] = part(PairGroom, script { act(PairPose.GROOM.b, s) })
        st.parts[o.b] = part(PairGroom, script { act(PairPose.GROOM.a, s) })
        return st
    }

    /** After grooming: the groomer moves off a little and both rest. */
    fun groomEnd(o: Moment): Stage {
        val st = Stage(final = true)
        st.report = Social.GROOM
        val g = minds[o.a]
        st.parts[o.a] =
            part(
                PairGroom,
                script {
                    act(Rows.SIT, AFTER_SIT_S)
                    away(g, minds[o.b].exec.x, t.PART_LO, t.PART_HI)
                    act(Rows.LOAF, seconds(LOAF_LO_S * 1.4, LOAF_HI_S * 1.4))
                },
            )
        st.parts[o.b] =
            part(PairGroom, script { act(Rows.LOAF, seconds(LOAF_LO_S * 1.4, LOAF_HI_S * 1.4)) })
        return st
    }

    internal companion object {
        const val Z0 = 40f
        const val HISS_MEOW_S = 0.9
        const val AFTER_SIT_S = 2.5
        const val SHORT_SIT_S = 1.4
        const val RUB_LONG_S = 2.4
        const val GROOM_LO_S = 4.0
        const val GROOM_HI_S = 7.0
        const val LOAF_LO_S = 15.0
        const val LOAF_HI_S = 30.0
        const val LOAF_BEAT_S = 1.5
        const val RUNNER_SIT_S = 1.1
        const val POUNCE_S = 0.25
        const val BOLT_S = 0.15
        const val BAT_S = 0.8
        const val DEPTH_WOBBLE = 12f
        const val PRIZE_WAIT_S = 4.0
    }
}
