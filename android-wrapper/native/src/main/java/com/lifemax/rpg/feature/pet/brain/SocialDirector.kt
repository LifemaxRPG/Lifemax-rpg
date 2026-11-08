package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.Rng
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.World
import com.lifemax.rpg.feature.pet.world.BlobPair
import com.lifemax.rpg.feature.pet.world.OpIds
import com.lifemax.rpg.feature.pet.world.SocialReport
import com.lifemax.rpg.feature.pet.world.WorldPair

/**
 * The page's friendship of the two cats as the brain sees it (the `world` block's `pair`): [rel]
 * 0..1, rank [lead] (-1 unsettled, else the slot) and [settled], whether the two have [met], and
 * whether the page [records] the moments (it said so with `met`, `pet.social.v1`).
 */
data class PairInfo(
    val rel: Double = SocialTuning.REL_START,
    val lead: Int = -1,
    val settled: Boolean = false,
    val met: Boolean = true,
    val records: Boolean = false,
) {
    companion object {
        /** The brain's view of the page's pair. */
        fun of(p: WorldPair): PairInfo =
            PairInfo(p.rel, p.lead, p.settled, p.met ?: true, p.met != null)
    }
}

/**
 * A moment the two cats shared: [kind] (one of [Social]) at world epoch [atMs], cats [a] and [b].
 */
data class PairEvent(val kind: String, val atMs: Long, val a: Int, val b: Int, val winner: Int)

/**
 * A moment together being played: its [kind], the cats' roles ([a] begins it: the greeter, the
 * chaser, the groomer, the front sleeper, the challenger; [b] the other), its [token] and the
 * stages still to come ([queue]); the rest is the choreography's working state.
 */
internal class Moment(val kind: String, var a: Int, var b: Int, val token: Int, val startMs: Long) {
    val queue = ArrayDeque<(Moment) -> Stage?>()
    var stageMs = 0L

    /** The step index at which each cat's part of this stage is done (-1: none to wait for). */
    val hold = IntArray(2) { -1 }

    /** Which cats have a part in the moment (a sleeper being joined has none until the end). */
    val inIt = BooleanArray(2)
    var mx = 0f
    var mz = 0f
    var f = 1
    var tx = 0f
    var rounds = 0
    var hiss = false
    var friends = false
    var back = false
    var prize = false
    var winner = -1
    var surface = SurfaceId.FLOOR
    var spot: Spot? = null
    var perch: Spot? = null
    var napS = 0.0
    var firstLeftS = Double.NaN
}

/**
 * What two cats do together (plan 1.1 `SocialDirector`, CB5): every few seconds it looks for a
 * moment (a nose touch, a chase, grooming, curling up, a standoff over a perch, a kitten shadowing
 * the grown cat), at a rate set by both cats' wish for company, friendliness, the friendship and
 * the hour, within the page's friendship gates and each kind's quiet time ([SocialTuning]).
 *
 * The sync protocol, bounded at every step:
 * - **Proposal and accept.** A moment starts only when both cats accept: each must be free (not
 *   eating, begging, at the owner's game, making way, hungry, on its way somewhere, just settled,
 *   or asleep unless the moment is to join it). A turned-down proposal waits
 *   [SocialTuning.DECLINED_S].
 * - **Stages.** Each stage hands both cats their part (the runner's [ActivityRunner.playPart]); a
 *   cat done with its part holds a pose; the next stage begins when both are done.
 * - **Timeouts.** A stage longer than [SocialTuning.STAGE_MAX_S] or a moment longer than its kind's
 *   [SocialTuning.MAX_MIN] is given up; the runner's watchdog bounds every part as well.
 * - **Abort.** Anything else either cat begins (a meal served, the owner's toy, the overlap guard,
 *   a restore) clears its token: the moment is given up and the other cat picks anew. Nobody waits.
 * - **Restarts.** A moment never survives a restore half-done (its activities do not resume),
 *   except two cats curled up asleep side by side, whose nap goes on ([restored]).
 *
 * A moment that completes is told to the page (`social`, only when [PairInfo.records]) and logged
 * in [events]; its kind then rests for its quiet time (the blob's `pair.cool`).
 */
internal class SocialDirector(
    private val minds: List<CatMind>,
    private val house: House,
    private val epochMs: () -> Long,
) {
    private val t = SocialTuning

    /** The page's friendship, from its newest block (see [pair]). */
    var info = PairInfo()
        private set

    /** The two met here: a block written before the page stored that meeting does not undo it. */
    private var metHere = false

    /** The two met in [other] (the catch-up's story): this director knows it too. */
    fun adoptMet(other: SocialDirector) {
        if (!other.metHere || metHere) return
        metHere = true
        info = info.copy(met = true)
    }

    /** The page's newest friendship. */
    fun pair(p: PairInfo) {
        info = if (metHere) p.copy(met = true) else p
    }

    private var rng = Rng(0)
    private val plays = PairPlays(minds) { rng }
    private var moment: Moment? = null
    private var nextToken = 1
    private var quanta = 0L
    private var quietUntil = Long.MIN_VALUE
    private var declinedUntil = Long.MIN_VALUE
    private val cool = HashMap<String, Long>()
    private var lastKind: String? = null
    private var lastAt = 0L
    private var lastTogether = Long.MIN_VALUE
    private var reforming = false
    private val log = ArrayList<PairEvent>()

    /** The moments shared so far (the last [EVENTS_KEPT]), oldest first. */
    val events: List<PairEvent>
        get() = log

    /** Times a moment was given up (a stage timed out, a cat was called away). */
    var aborted = 0
        private set

    /** The kind of moment being played, or null. */
    val playing: String?
        get() = moment?.kind

    private val two: Boolean
        get() = minds.size == 2

    /** A new local day: the director's own random stream, seeded by the lower cat id. */
    fun newDay(dayKey: String) {
        if (!two) return
        val id = minOf(minds[0].profile.id, minds[1].profile.id)
        rng = Rng(Rng.hash("$id|$dayKey|social"))
    }

    /**
     * True while both cats play one moment together that lets them be close (and while two cats
     * restored curled up are taken back): the only time they may share a spot.
     */
    fun sharing(): Boolean {
        if (!two) return false
        if (reforming) return true
        val ra = minds[0].runner
        val rb = minds[1].runner
        return ra.pairToken != 0 &&
            ra.pairToken == rb.pairToken &&
            Social.shares(ra.activity, rb.activity)
    }

    /** True while two cats being restored lie curled up together. */
    fun pairedOnRestore(): Boolean = reforming

    /**
     * Before the cats take up what they do on other bodies ([CatWorld.bind]): two curled up
     * together keep lying together, as after a restore. Call [restored] after.
     */
    fun rebinding() {
        if (!two) return
        reforming = sharing() && minds.all { it.runner.activity === CurlUp }
    }

    // ---------------------------------------------------------------- each quantum

    /** One think quantum, after both cats thought; [hour] is the local hour. */
    fun think(hour: Double) {
        if (!two) return
        val now = epochMs()
        val o = moment
        if (o != null) {
            tick(o, now)
            return
        }
        if (++quanta % CHECK_QUANTA != 0L) return
        if (now < declinedUntil) return
        maybeStart(now, hour)
    }

    private fun tick(o: Moment, now: Long) {
        for (s in 0..1) if (o.inIt[s] && minds[s].runner.pairToken != o.token) return abort(o)
        val stageS = (now - o.stageMs) / MS
        val totalS = (now - o.startMs) / MS
        val most = t.MAX_MIN.getValue(o.kind) * BrainTuning.Time.MINUTE
        if (stageS > t.STAGE_MAX_S || totalS > most) return abort(o)
        for (s in 0..1) if (o.hold[s] >= 0 && minds[s].exec.stepIndex < o.hold[s]) return
        next(o, now)
    }

    /** Plays the next stage of [o], or ends it when there is none or it cannot be played. */
    private fun next(o: Moment, now: Long) {
        val build = o.queue.removeFirstOrNull() ?: return finish(o)
        val st = build(o) ?: return abort(o)
        o.stageMs = now
        for (s in 0..1) {
            o.hold[s] = -1
            val p = st.parts[s] ?: continue
            if (!st.final) {
                o.hold[s] = p.script.size
                p.script.act(if (p.activity.asleep) Rows.LOAF else Rows.SIT, t.HOLD_S)
            }
            o.inIt[s] = true
            minds[s].playPart(p.activity, p.script, REASON, o.token)
        }
        st.report?.let { record(o, it, st.winner, now) }
        if (st.final) finish(o)
    }

    /** The moment played its last stage: it is over (each cat finishes its part on its own). */
    private fun finish(o: Moment) {
        if (moment === o) moment = null
        val now = epochMs()
        // a kitten following the grown cat is not a moment the two shared: no quiet after it
        if (o.kind != Social.SHADOW)
            quietUntil =
                now +
                    (rng.range(t.QUIET_LO_MIN.toFloat(), t.QUIET_HI_MIN.toFloat()) * MINUTE_MS)
                        .toLong()
        // a challenger that won a perch: the holder, who left it, holds it no more
        if (o.perch != null && o.winner == o.a) minds[o.b].book.release(o.b)
        if (o.prize) o.perch?.let { minds[o.a].book.claim(o.a, it, it.x, 0) }
    }

    private fun abort(o: Moment) {
        if (moment === o) moment = null
        aborted++
        for (s in 0..1) if (o.inIt[s] && minds[s].runner.pairToken == o.token) minds[s].leavePair()
        quietUntil = epochMs() + (t.QUIET_LO_MIN * MINUTE_MS).toLong()
    }

    /** A completed moment: the page is told (when it records them), the mood answers, it rests. */
    private fun record(o: Moment, kind: String, winner: Int, now: Long) {
        val a = minds[o.a]
        val b = minds[o.b]
        log += PairEvent(kind, now, o.a, o.b, winner)
        if (log.size > EVENTS_KEPT) log.removeAt(0)
        if (kind == Social.SHADOW) return // a kitten's following: the page does not count it
        lastKind = kind
        lastAt = now
        lastTogether = now
        cool[kind] = now + (t.COOL_MIN.getValue(kind) * MINUTE_MS).toLong()
        // a hiss or a nose touch is the meeting: neither is offered again before its quiet ends
        if (kind == Social.HISS) cool[Social.GREET] = maxOf(cool[Social.GREET] ?: 0L, cool[kind]!!)
        if (kind == Social.NAP) cool[Social.GROOM] = maxOf(cool[Social.GROOM] ?: 0L, now)
        if (kind == Social.GREET || kind == Social.HISS) {
            metHere = true
            info = info.copy(met = true)
        }
        feel(kind, a, b, winner)
        if (!info.records) return
        val w = if (winner >= 0) minds[winner].profile.id else null
        house.report(
            SocialReport(
                OpIds.of(a.profile.id, COMMAND, now),
                kind,
                listOf(a.profile.id, b.profile.id),
                w,
                now,
            )
        )
    }

    private fun feel(kind: String, a: CatMind, b: CatMind, winner: Int) {
        when (kind) {
            Social.GREET -> {
                a.mood.bump(t.FEEL_GREET_V, 0.0)
                b.mood.bump(t.FEEL_GREET_V, 0.0)
            }
            Social.HISS -> {
                b.mood.bump(t.FEEL_HISSER_V, t.FEEL_HISSER_A)
                a.mood.bump(t.FEEL_HISSED_V, t.FEEL_HISSED_A)
            }
            Social.CHASE -> {
                a.mood.bump(t.FEEL_CHASE_V, t.FEEL_CHASE_A)
                b.mood.bump(t.FEEL_CHASE_V, t.FEEL_CHASE_A)
            }
            Social.GROOM -> {
                a.mood.bump(t.FEEL_GROOMER_V, t.FEEL_GROOMER_A)
                b.mood.bump(t.FEEL_GROOMED_V, t.FEEL_GROOMED_A)
            }
            Social.NAP -> {
                a.mood.bump(t.FEEL_NAP_V, t.FEEL_NAP_A)
                b.mood.bump(t.FEEL_NAP_V, t.FEEL_NAP_A)
            }
            Social.STANDOFF -> {
                val w = minds[winner]
                val l = if (w === a) b else a
                w.mood.bump(t.FEEL_WIN_V, t.FEEL_WIN_A)
                l.mood.bump(t.FEEL_LOSE_V, t.FEEL_LOSE_A)
            }
        }
    }

    // ---------------------------------------------------------------- starting

    private fun cooling(kind: String, now: Long): Boolean = now < (cool[kind] ?: Long.MIN_VALUE)

    private fun maybeStart(now: Long, hour: Double) {
        val a = minds[0]
        val b = minds[1]
        // a moment given up keeps its quiet, the first meeting's too (no nap broken every 26 s)
        if (now < quietUntil) return
        if (!info.met) {
            // the very first meeting: as soon as both can, a light nap broken for it
            if (
                SocialChoice.free(a, wake = true, firstMeeting = true) &&
                    SocialChoice.free(b, wake = true, firstMeeting = true)
            )
                start(greet(rng.chance(HALF).let { if (it) 0 else 1 }), now)
            return
        }
        val urge = SocialChoice.urge(a, b, info)
        val p = 1.0 - Math.exp(-t.RATE_PER_H * urge * t.CHECK_S / BrainTuning.Time.HOUR)
        if (rng.nextFloat() >= p) {
            maybeShadow(now)
            return
        }
        val options = SocialChoice.options(minds, info, hour, now, lastTogether, ::cooling, rng)
        if (options.isEmpty()) return
        val pick = SocialChoice.weighted(options, rng)
        val o = build(pick, now)
        if (o == null) {
            declinedUntil = now + (t.DECLINED_S * MS).toLong()
            return
        }
        start(o, now)
    }

    /**
     * Starts the moment [kind] now, begun by cat [a] (at [spot] for a curl-up or a standoff), with
     * none of the chance or the gates: scenes in tests and a dev overlay. False when it cannot be
     * played from where the cats are.
     */
    internal fun force(kind: String, a: Int, spot: Spot? = null, seconds: Double = 60.0): Boolean {
        if (!two) return false
        moment?.let(::abort)
        val now = epochMs()
        val o =
            if (kind == Social.SHADOW)
                Moment(Social.SHADOW, a, 1 - a, nextToken++, now).also { m ->
                    m.queue += { plays.shadow(it, seconds) }
                }
            else build(SocialChoice.Option(kind, a, spot, 1.0), now) ?: return false
        start(o, now)
        return moment === o || o.inIt.any { it }
    }

    /** The end of the quiet after the last moment, epoch ms (tests). */
    internal val quietEndsAt: Long
        get() = quietUntil

    /** A kitten trots after the grown cat now and then (not a moment the page counts). */
    private fun maybeShadow(now: Long) {
        if (cooling(Social.SHADOW, now)) return
        val k = minds.firstOrNull { it.persona.kitten } ?: return
        val d = minds.first { it !== k }
        if (d.persona.kitten || d.exec.surface != SurfaceId.FLOOR) return
        if (!SocialChoice.free(k, wake = false) || k.runner.activity.asleep) return
        if (Math.abs(k.exec.x - d.exec.x) < t.SHADOW_FOLLOW_PX) return
        val p = 1.0 - Math.exp(-t.SHADOW_RATE_PER_H * t.CHECK_S / BrainTuning.Time.HOUR)
        if (rng.nextFloat() >= p) return
        val o = Moment(Social.SHADOW, k.slot, d.slot, nextToken++, now)
        val secs = rng.range(t.SHADOW_LO_MIN.toFloat(), t.SHADOW_HI_MIN.toFloat()) * MINUTE_S
        o.queue += { m -> plays.shadow(m, secs.toDouble()) }
        cool[Social.SHADOW] = now + (t.COOL_MIN.getValue(Social.SHADOW) * MINUTE_MS).toLong()
        start(o, now)
    }

    private fun start(o: Moment, now: Long) {
        moment = o
        o.stageMs = now
        o.inIt[o.a] = false
        o.inIt[o.b] = false
        // the cats that leave their places for the moment give them up; one that stays where it is
        // keeps its own, so the other never picks it: a sleeper being joined, a cat being groomed
        // where it lies, the holder of a perch being challenged, the grown cat a kitten follows
        val joined = o.kind == Social.NAP && o.spot == null
        if (!joined) minds[o.a].book.release(o.a)
        val stays =
            o.kind == Social.SHADOW ||
                o.kind == Social.GROOM ||
                (o.kind == Social.STANDOFF && o.perch != null)
        if (!stays) minds[o.b].book.release(o.b)
        next(o, now)
    }

    private fun greet(a: Int): Moment {
        val o = Moment(Social.GREET, a, 1 - a, nextToken++, epochMs())
        o.hiss = rng.nextFloat() < SocialChoice.hissChance(info.rel, minds[o.b].persona)
        o.friends = info.rel >= FRIENDS_REL
        o.queue += plays::greetMeet
        o.queue += plays::greetTouch
        o.queue += plays::greetEnd
        return o
    }

    /** The moment [pick] names, with its roles decided, or null when it cannot be played now. */
    private fun build(pick: SocialChoice.Option, now: Long): Moment? {
        val a = pick.a
        return when (pick.kind) {
            Social.GREET -> greet(a)
            Social.CHASE ->
                Moment(Social.CHASE, a, 1 - a, nextToken++, now).also { o ->
                    o.queue += plays::chaseLine
                    o.queue += plays::chaseStalk
                    o.queue += plays::chaseGo
                    o.queue += plays::chaseTussle
                    o.queue += plays::chaseMore
                }
            Social.GROOM ->
                Moment(Social.GROOM, a, 1 - a, nextToken++, now).also { o ->
                    o.queue += plays::groomLine
                    o.queue += plays::groomDo
                    o.queue += { m -> plays.groomBack(m, info.rel) ?: Stage(final = false) }
                    o.queue += { m -> afterGroom(m) }
                }
            Social.NAP ->
                Moment(Social.NAP, a, 1 - a, nextToken++, now).also { o ->
                    o.spot = pick.spot
                    o.napS = minds[a].let { CurlUp.duration(it, rng) }
                    if (pick.spot == null) {
                        // joining the one asleep (a): its nap goes on as long as it had left
                        val r = minds[a].runner
                        o.firstLeftS = (r.seconds - r.atPlaceS).coerceAtLeast(t.JOIN_LEFT_S)
                        o.queue += plays::curlJoin
                    } else o.queue += plays::curlCome
                    o.queue += { m ->
                        // the one being joined may have woken meanwhile: then there is no nap to
                        // join
                        val joined = !m.firstLeftS.isNaN()
                        if (joined && !minds[m.a].runner.activity.asleep) null
                        else plays.curlSleep(m, m.napS, m.firstLeftS)
                    }
                }
            Social.STANDOFF ->
                Moment(Social.STANDOFF, a, 1 - a, nextToken++, now).also { o ->
                    o.perch = pick.spot
                    o.queue += plays::standoffMeet
                    o.queue += plays::standoffStare
                    o.queue += { m ->
                        plays.standoffEnd(m, SocialChoice.contest(minds, m.a, info, rng))
                    }
                }
            else -> null
        }
    }

    /** After grooming, close friends that are drowsy (or now and then) curl up there and then. */
    private fun afterGroom(o: Moment): Stage? {
        val a = minds[o.a]
        val b = minds[o.b]
        val sleepy = SocialChoice.sleepy(a) && SocialChoice.sleepy(b)
        val soft = o.surface == SurfaceId.COUCH || o.surface == SurfaceId.FLOOR
        val now = epochMs()
        val nap = info.rel >= t.NAP_REL && soft && !cooling(Social.NAP, now) && sleepy
        if (!nap) return plays.groomEnd(o)
        // they settle into the spoon pose where they are, if it fits; the grooming is told once,
        // here or by the ordinary end
        o.firstLeftS = Double.NaN
        val join = joinAfterGroom(o) ?: return plays.groomEnd(o)
        record(o, Social.GROOM, -1, now)
        o.napS = CurlUp.duration(a, rng)
        o.queue.addFirst { m -> plays.curlSleep(m, m.napS, m.firstLeftS) }
        return join
    }

    /** How two cats settle together after grooming (a seam for tests). */
    internal var joinAfterGroom: (Moment) -> Stage? = { plays.curlJoin(it) }

    // ---------------------------------------------------------------- the blob

    /** The pair's state for the blob: the last moment and each kind's quiet until (ms). */
    fun export(kept: BlobPair?): BlobPair? {
        if (!two) return kept
        val c = cool.filterKeys { it in SocialReport.KINDS }.toSortedMap()
        if (lastKind == null && c.isEmpty()) return kept
        return BlobPair(lastKind, lastAt, c)
    }

    /** Takes back the pair's last moment and quiet times. */
    fun restore(p: BlobPair?) {
        moment = null
        cool.clear()
        if (p == null) return
        lastKind = p.lastKind
        lastAt = p.lastAt
        lastTogether = maxOf(lastTogether, p.lastAt)
        cool.putAll(p.cool.filterKeys { it in SocialReport.KINDS })
        if (p.lastKind != null) quietUntil = p.lastAt + (t.QUIET_LO_MIN * MINUTE_MS).toLong()
    }

    /**
     * Before the cats of a restore take up their activities: two saved curled up together side by
     * side ([acts] by slot, the blob's words) keep lying together. Call [restored] after.
     */
    fun restoring(acts: List<String?>) {
        if (!two) return
        val a = minds[0].exec
        val b = minds[1].exec
        val curled = acts.all { it?.let(MindState::activityOf) == CurlUp.id }
        val reach =
            (CURL_REACH + Math.abs(com.lifemax.rpg.feature.pet.body.PairPose.SNUGGLE.dx(-1))) *
                World.depthScale(maxOf(a.z, b.z))
        reforming =
            curled &&
                a.surface == b.surface &&
                Math.abs(a.x - b.x) <= reach &&
                Math.abs(a.z - b.z) <= BrainTuning.Apart.OVERLAP_Z
    }

    /** The cats took up their activities: two still curled up form one nap together again. */
    fun restored() {
        if (!two) return
        val was = reforming
        reforming = false
        if (!was) return
        val ra = minds[0].runner
        val rb = minds[1].runner
        if (ra.activity !== CurlUp || rb.activity !== CurlUp) return
        val token = nextToken++
        ra.pairToken = token
        rb.pairToken = token
    }

    private companion object {
        const val MS = 1000.0
        const val MINUTE_MS = 60_000.0
        const val MINUTE_S = 60f
        const val HALF = 0.5f
        const val FRIENDS_REL = 0.5
        const val EVENTS_KEPT = 512
        const val COMMAND = "social"
        const val REASON = "together"
        const val CURL_REACH = 4f
        val CHECK_QUANTA = Math.round(SocialTuning.CHECK_S / BrainTuning.Time.QUANTUM_S)
    }
}
