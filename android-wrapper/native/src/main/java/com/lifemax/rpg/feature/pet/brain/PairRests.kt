package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.PairPose
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.World
import com.lifemax.rpg.feature.pet.brain.PairPlays.Companion.AFTER_SIT_S
import com.lifemax.rpg.feature.pet.brain.PairPlays.Companion.GROOM_HI_S
import com.lifemax.rpg.feature.pet.brain.PairPlays.Companion.GROOM_LO_S
import com.lifemax.rpg.feature.pet.brain.PairPlays.Companion.HISS_MEOW_S
import com.lifemax.rpg.feature.pet.brain.PairPlays.Companion.LOAF_BEAT_S
import com.lifemax.rpg.feature.pet.brain.PairPlays.Companion.LOAF_HI_S
import com.lifemax.rpg.feature.pet.brain.PairPlays.Companion.LOAF_LO_S
import com.lifemax.rpg.feature.pet.brain.PairPlays.Companion.PRIZE_WAIT_S
import com.lifemax.rpg.feature.pet.brain.PairPlays.Companion.SHORT_SIT_S
import com.lifemax.rpg.feature.pet.brain.PairPlays.Companion.Z0

/*
 * The quieter moments of [PairPlays] (plan 1.8 pair rows): curling up together in the spoon pose,
 * the standoff over a perch, and a kitten shadowing the grown cat. Same rules as the rest: poses
 * together at the lab's offsets, every way there planned, null when a stage cannot be played.
 */
// ---------------------------------------------------------------- curling up

/**
 * Where two cats curl up: the front cat (a) at ([o].mx, [o].mz) on [o].surface facing [o].f, the
 * other just behind it the same way round (the spoon pose's offset, a little further back on the
 * floor). Answers false when the second does not fit there.
 */
private fun PairPlays.spoon(o: Moment): Pair<Float, Float>? {
    val a = minds[o.a]
    val b = minds[o.b]
    val k = PairPose.SNUGGLE
    val gap = k.gap(stage(a), stage(b), o.mz)
    val bx = fit(o.surface, o.mx + o.f * gap) ?: return null
    val back =
        if (o.surface == SurfaceId.FLOOR) k.backZ(stage(b), o.mz).coerceAtMost(t.SPOON_BACK_MAX_Z)
        else 0f
    return bx to (o.mz - back)
}

/** Both walk to one soft spot ([o].spot) and settle side by side. */
internal fun PairPlays.curlCome(o: Moment): Stage? {
    val spot = o.spot ?: return null
    // on furniture both lie at one depth and the room draws one slot over the other: that one is
    // the front cat (a), so the cat meant to lie behind is drawn behind
    if (!spot.onFloor && o.a != PairPose.DRAWN_FRONT_SLOT) {
        o.b = o.a
        o.a = PairPose.DRAWN_FRONT_SLOT
    }
    o.surface = spot.surface
    o.mz = if (spot.onFloor) spot.z else World.surface(spot.surface).z
    o.f = if (minds[o.a].exec.x <= spot.x) 1 else -1
    // the front cat lies a little off the spot's middle so the other fits behind it
    val k = PairPose.SNUGGLE
    val half = k.gap(stage(minds[o.a]), stage(minds[o.b]), o.mz) / 2f
    o.mx = fit(spot.surface, spot.x - o.f * half) ?: spot.x
    var place = spoon(o)
    if (place == null) {
        o.f = -o.f
        o.mx = fit(spot.surface, spot.x - o.f * half) ?: spot.x
        place = spoon(o) ?: return null
    }
    val st = Stage(final = false)
    val sa = Script()
    if (!goTo(minds[o.a], spot.surface, o.mx, o.mz, sa)) return null
    sa.face(o.f)
    val sb = Script()
    if (!goTo(minds[o.b], spot.surface, place.first, place.second, sb)) return null
    sb.face(o.f)
    st.parts[o.a] = part(SettleIn, sa.also { it.act(Rows.LOAF, LOAF_BEAT_S) })
    st.parts[o.b] = part(SettleIn, sb.also { it.act(Rows.LOAF, LOAF_BEAT_S) })
    return st
}

/**
 * A friend comes and curls up beside the one asleep ([o].a, who stays as it lies): behind it, or in
 * front when there is no room behind.
 */
internal fun PairPlays.curlJoin(o: Moment): Stage? {
    val s = minds[o.a]
    o.surface = s.exec.surface
    o.mx = s.exec.x
    o.mz = if (o.surface == SurfaceId.FLOOR) s.exec.z else World.surface(o.surface).z
    o.f = s.exec.facing
    // on the floor the joiner lies behind (further back), or in front when there is no room; on
    // furniture it lies where the room draws it: in front when it is the slot drawn in front
    val raised = o.surface != SurfaceId.FLOOR
    val inFront = raised && o.b == PairPose.DRAWN_FRONT_SLOT
    var place = if (inFront) null else spoon(o)
    if (place == null) {
        if (raised && !inFront) return null
        // in front of it instead: the joiner is the front cat, facing the same way
        val gap = PairPose.SNUGGLE.gap(stage(minds[o.b]), stage(s), o.mz)
        val x = fit(o.surface, o.mx - o.f * gap) ?: return null
        place = x to o.mz
    }
    val st = Stage(final = false)
    val sj = Script()
    if (!goTo(minds[o.b], o.surface, place.first, place.second, sj)) return null
    sj.face(o.f)
    st.parts[o.b] = part(SettleIn, sj.also { it.act(Rows.LOAF, LOAF_BEAT_S) })
    return st
}

/** They sleep side by side, half a breath apart: the second kneads first. */
internal fun PairPlays.curlSleep(o: Moment, napS: Double, firstLeftS: Double): Stage {
    val st = Stage(final = true)
    st.report = Social.NAP
    val most = CurlUp.maxMinutes * BrainTuning.Time.MINUTE - NAP_MARGIN_S
    val first = (if (firstLeftS.isNaN()) napS else firstLeftS).coerceAtMost(most)
    st.parts[o.a] = part(CurlUp, script { act(Rows.SLEEP, first) })
    val second =
        maxOf(
                t.NAP_SHORTEST_MIN * BrainTuning.Time.MINUTE,
                // the second wakes about when the first does: lying together adds no sleep
                minOf(napS, first) +
                    seconds(t.NAP_OFFSET_LO_MIN, t.NAP_OFFSET_HI_MIN) * BrainTuning.Time.MINUTE,
            )
            .coerceAtMost(most)
    st.parts[o.b] =
        part(
            CurlUp,
            script {
                act(Rows.KNEAD, seconds(t.KNEAD_LO_S, t.KNEAD_HI_S))
                act(Rows.SLEEP, second)
            },
        )
    return st
}

// ---------------------------------------------------------------- standoff

/**
 * The challenger (a) walks up to the holder (b): to the foot of the perch it holds ([o].perch), or
 * face to face on the floor. The holder turns to face it.
 */
internal fun PairPlays.standoffMeet(o: Moment): Stage? {
    val c = minds[o.a]
    val h = minds[o.b]
    val perch = o.perch
    if (perch == null) {
        val st = greetMeet(o) ?: return null
        for (slot in intArrayOf(o.a, o.b)) st.parts[slot] =
            Part(PairStandoff, st.parts[slot]!!.script)
        return st
    }
    val side = if (c.exec.x < perch.x) -1 else 1
    val x = inHouse(perch.x + side * t.PERCH_FOOT_PX)
    val z = meetZ(x, Z0)
    val st = Stage(final = false)
    val sc = Script()
    if (!toFloor(c, x, z, sc)) return null
    sc.face(-side)
    st.parts[o.a] = part(PairStandoff, sc)
    st.parts[o.b] = part(PairStandoff, script { face(side) })
    return st
}

/** The stare: the challenger low and growling, the holder upright, ears back. */
internal fun PairPlays.standoffStare(o: Moment): Stage {
    val st = Stage(final = false)
    st.parts[o.a] =
        part(
            PairStandoff,
            script {
                act(Rows.CROUCH, t.STANDOFF_CROUCH_S)
                act(Rows.MEOW, HISS_MEOW_S)
                act(Rows.CROUCH, t.STARE_S - t.STANDOFF_CROUCH_S)
            },
        )
    st.parts[o.b] = part(PairStandoff, script { act(Rows.SIT, t.STARE_S) })
    return st
}

/**
 * The end: the loser turns away and leaves; the winner holds its ground (a challenger that won a
 * perch climbs up to it and settles there).
 */
internal fun PairPlays.standoffEnd(o: Moment, winner: Int): Stage {
    val st = Stage(final = true)
    st.report = Social.STANDOFF
    st.winner = winner
    o.winner = winner
    val loser = if (winner == o.a) o.b else o.a
    val w = minds[winner]
    val l = minds[loser]
    val perch = o.perch
    val sw = script {
        act(Rows.MEOW, HISS_MEOW_S)
        act(Rows.SIT, SHORT_SIT_S)
        act(Rows.STRETCH, Rows.once(Rows.STRETCH))
    }
    val sl = Script()
    sl.act(Rows.CROUCH, t.STANDOFF_CROUCH_S)
    var vacated = l.exec.surface == SurfaceId.FLOOR
    if (vacated) sl.away(l, w.exec.x, t.RETREAT_LO, t.RETREAT_HI, run = true)
    else {
        // the holder lost its perch: down and away
        val dir = if (l.exec.x >= w.exec.x) 1f else -1f
        val x = inHouse(l.exec.x + dir * range(t.RETREAT_LO, t.RETREAT_HI))
        l.exec.route(SurfaceId.FLOOR, x)?.let {
            sl.route(it.steps)
            vacated = true
        }
    }
    sl.act(Rows.SIT, AFTER_SIT_S)
    sl.act(Rows.GROOM, seconds(GROOM_LO_S, GROOM_HI_S))
    o.prize = false
    if (perch != null && winner == o.a && vacated) {
        // the prize: the challenger goes up once the holder is off, and rests there
        sw.act(Rows.SIT, PRIZE_WAIT_S)
        val r = w.exec.route(perch.surface, perch.x)
        if (r != null) {
            o.prize = true
            sw.route(r.steps)
            sw.act(Rows.LOAF, seconds(LOAF_LO_S * 2, LOAF_HI_S * 2))
        } else sw.act(Rows.LOAF, seconds(LOAF_LO_S, LOAF_HI_S))
    } else sw.act(Rows.LOAF, seconds(LOAF_LO_S, LOAF_HI_S))
    st.parts[winner] = part(PairStandoff, sw)
    st.parts[loser] = part(PairStandoff, sl)
    return st
}

// ---------------------------------------------------------------- shadowing

/**
 * The kitten (a) trots after the grown cat (b) and does as it does a few steps behind: lies down by
 * a resting cat, sits by a busy one, washes when it washes.
 */
internal fun PairPlays.shadow(o: Moment, seconds: Double): Stage? {
    val k = minds[o.a]
    val d = minds[o.b]
    if (d.exec.surface != SurfaceId.FLOOR) return null
    val side = if (k.exec.x >= d.exec.x) 1 else -1
    var x = World.clampHouse(d.exec.x + side * t.SHADOW_BEHIND_PX)
    if (Math.abs(x - d.exec.x) < t.SHADOW_BEHIND_PX - 1f)
        x = World.clampHouse(d.exec.x - side * t.SHADOW_BEHIND_PX)
    val z = meetZ(x, d.exec.z)
    o.tx = d.exec.x
    val s = Script()
    if (!toFloor(k, x, z, s)) return null
    s.face(-side)
    val a = d.runner.activity
    val row =
        when {
            a.asleep || a === Loaf -> Rows.LOAF
            a === Groom || a === WakingRoutine -> Rows.GROOM
            else -> Rows.SIT
        }
    s.act(row, seconds)
    val st = Stage(final = true)
    st.report = Social.SHADOW
    st.parts[o.a] = part(Shadow, s)
    return st
}

/** A curled-up nap ends this long before its longest, the way there and the knead included. */
private const val NAP_MARGIN_S = 600.0
