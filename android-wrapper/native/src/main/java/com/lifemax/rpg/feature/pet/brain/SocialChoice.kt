package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.Rng
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.world.WorldContract

/**
 * How the [SocialDirector] chooses (plan 1.5 for two cats): who is free to be asked, how strong the
 * urge for a moment is now, which moments the friendship, the cats' states and the quiet times
 * allow (with their roles and appeal, the page's weights where the page has them), and who wins a
 * standoff (the page's contest, on native's estimate of each cat's confidence). Pure decisions: it
 * reads the minds and draws only from the [Rng] it is given.
 */
internal object SocialChoice {
    private val t = SocialTuning

    /** A moment that could start: its [kind], who begins it ([a]), its place, its appeal. */
    class Option(val kind: String, val a: Int, val spot: Spot?, val weight: Double)

    /**
     * Whether cat [m] may be asked for a moment now: not hungry, not eating, begging, at the
     * owner's game, making way or already together, not on its way somewhere or just settled, and
     * awake (or, when [wake], in a light sleep). A [firstMeeting] tolerates more hunger.
     */
    fun free(m: CatMind, wake: Boolean, firstMeeting: Boolean = false): Boolean {
        val p = m.persona
        val hungry =
            if (firstMeeting) BrainTuning.Persona.WEAK_STAGE else BrainTuning.Persona.BEG_STAGE
        if (!p.vacation && p.hungerStage >= hungry) return false
        val a = m.runner.activity
        if (a === Eat || a === Beg || a === OwnerPlay || a === MakeWay || a === WakingRoutine)
            return false
        if (Social.isPair(a) || m.toy != null || m.runner.waitingForWay || !m.exec.atPlace)
            return false
        if (m.runner.elapsedS < t.SETTLED_S) return false
        // only a light nap by day is broken (for the very first meeting); never a night's sleep
        if (a.asleep)
            return wake &&
                !m.schedule.inBed(m.hour) &&
                m.runner.atPlaceS < BrainTuning.Care.DEEP_SLEEP_S
        return true
    }

    /** Lying about: what a cat may be groomed during. */
    private fun resting(m: CatMind): Boolean {
        val a = m.runner.activity
        return a === Loaf || a === Groom || a === Knead
    }

    /**
     * Drowsy enough to curl up: tired enough that it would choose a nap on its own (the energy at
     * which [Sleep] becomes eligible), so a moment together never adds sleep to a day, not even at
     * night: a cat up in the night is not coaxed back to bed by the other.
     */
    fun sleepy(m: CatMind): Boolean {
        val p = BrainTuning.Persona
        val tired =
            if (m.persona.kitten) p.KITTEN_SLEEPY
            else t.SLEEPY_ENERGY + p.LAZY_SLEEPY * m.persona.lazy
        return m.drives[Drive.ENERGY] < tired
    }

    /**
     * Rested in a day nap: the other cat may wake it for a chase or a nose touch (a cat lying about
     * well rested gets up for company, as real cats do), never at night, never one hungry, tired or
     * at the owner's game.
     */
    fun rousable(m: CatMind): Boolean {
        if (m.runner.activity !== Sleep || m.schedule.inBed(m.hour)) return false
        if (!m.persona.vacation && m.persona.hungerStage >= BrainTuning.Persona.BEG_STAGE)
            return false
        if (m.toy != null || !m.exec.atPlace) return false
        return m.drives[Drive.ENERGY] >= t.ROUSE_ENERGY
    }

    /** How much of a kitten a cat still is: 1 a newborn .. 0 grown (the page's 1 − growth). */
    private fun kit(m: CatMind): Double = 1.0 - growth(m)

    private fun growth(m: CatMind): Double =
        if (m.persona.kitten) (m.persona.stage + 1.0) / (WorldContract.STAGE_MAX + 2.0) else 1.0

    /**
     * The urge for a moment together now (multiplies [SocialTuning.RATE_PER_H]): both cats' wish
     * for company, how friendly both are, the friendship; a glum cat keeps to itself.
     */
    fun urge(a: CatMind, b: CatMind, info: PairInfo): Double {
        val company = (a.drives[Drive.COMPANY] + b.drives[Drive.COMPANY]) / 2.0
        val friendly = (a.persona.friendly + b.persona.friendly) / 2.0
        var u =
            Curves.lerp(t.URGE_COMPANY_LO, t.URGE_COMPANY_HI, company) *
                Curves.lerp(t.URGE_FRIENDLY_LO, t.URGE_FRIENDLY_HI, friendly) *
                (t.URGE_REL_BASE + t.URGE_REL_GAIN * info.rel)
        if (minOf(a.valence, b.valence) < GLUM) u *= GLUM_DAMP
        return u
    }

    /** The page's hiss chance at a nose touch, for the cat [b] being greeted. */
    fun hissChance(rel: Double, b: Persona): Double {
        if (rel >= t.HISS_BELOW) return t.HISS_FRIENDS
        val shy = if (b.skittish >= t.SHY_SCORE) t.HISS_SHY else 0.0
        return (t.HISS_BASE - t.HISS_PER_REL * rel + shy).coerceIn(0.0, 1.0)
    }

    /**
     * Every moment the two cats could begin now, with roles and appeal. [lastTogether] is when they
     * were last together (a nose touch is for cats that have been apart), [cooling] whether a kind
     * still rests.
     */
    fun options(
        minds: List<CatMind>,
        info: PairInfo,
        hour: Double,
        now: Long,
        lastTogether: Long,
        cooling: (String, Long) -> Boolean,
        rng: Rng,
    ): List<Option> {
        val a = minds[0]
        val b = minds[1]
        val out = ArrayList<Option>()
        val awake = free(a, wake = false) && free(b, wake = false)
        val peak = (a.schedule.peak(hour) + b.schedule.peak(hour)) / 2.0
        if (awake) {
            val apartMin = (now - lastTogether) / MINUTE_MS
            if (!cooling(Social.GREET, now) && apartMin >= t.APART_MIN)
                out += Option(Social.GREET, coin(rng), null, t.W_GREET)
            standoff(a, b, info, rng)?.takeIf { !cooling(Social.STANDOFF, now) }?.let { out += it }
            chase(a, b, info, peak, rng)?.takeIf { !cooling(Social.CHASE, now) }?.let { out += it }
        } else {
            // one awake and the other rested in a day nap: the awake one wakes it to play or greet
            val waker =
                when {
                    free(a, wake = false) && rousable(b) -> a
                    free(b, wake = false) && rousable(a) -> b
                    else -> null
                }
            // friends wake each other; strangers let each other sleep
            if (waker != null && info.rel >= t.CHASE_REL) {
                val apartMin = (now - lastTogether) / MINUTE_MS
                if (!cooling(Social.GREET, now) && apartMin >= t.APART_MIN)
                    out += Option(Social.GREET, waker.slot, null, t.W_GREET * t.ROUSE_WEIGHT)
                chase(a, b, info, peak, rng)
                    ?.takeIf { !cooling(Social.CHASE, now) }
                    ?.let { out += Option(it.kind, waker.slot, null, it.weight * t.ROUSE_WEIGHT) }
            }
        }
        if (info.rel >= t.GROOM_REL && !cooling(Social.GROOM, now))
            groom(a, b, info, rng)?.let { out += it }
        if (info.rel >= t.NAP_REL && !cooling(Social.NAP, now))
            nap(minds, awake, rng)?.let {
                val window =
                    if (a.schedule.inNap(hour) || a.schedule.inBed(hour)) t.NAP_WINDOW_GAIN else 1.0
                out += Option(it.kind, it.a, it.spot, it.weight * window)
            }
        return out
    }

    private fun coin(rng: Rng): Int = if (rng.chance(HALF)) 0 else 1

    /** A standoff: over the perch one holds, or (rank unsettled) face to face on the floor. */
    private fun standoff(a: CatMind, b: CatMind, info: PairInfo, rng: Rng): Option? {
        val w = if (info.settled) t.W_STANDOFF_SETTLED else t.W_STANDOFF_UNSETTLED
        for (holder in arrayOf(a, b)) {
            val spot = holder.here ?: continue
            if (spot.onFloor || !spot.single) continue
            val challenger = if (holder === a) b else a
            return Option(Social.STANDOFF, challenger.slot, spot, w)
        }
        if (info.settled) return null
        val lead = info.lead
        val chal = if (lead < 0) coin(rng) else if (rng.chance(UNDERDOG)) 1 - lead else lead
        return Option(Social.STANDOFF, chal, null, w * FLOOR_STANDOFF)
    }

    /** A chase: the younger starts it, else (no age gap) the confident cat more often. */
    private fun chase(a: CatMind, b: CatMind, info: PairInfo, peak: Double, rng: Rng): Option? {
        val kitten = maxOf(kit(a), kit(b))
        if (info.rel < t.CHASE_REL && kitten <= KITTEN_CHASE) return null
        if (minOf(a.drives[Drive.ENERGY], b.drives[Drive.ENERGY]) < t.CHASE_ENERGY) return null
        val rest = resting(a) || resting(b)
        val e = (a.arousal + b.arousal) / 2.0
        val playful = maxOf(a.persona.spontaneous, b.persona.spontaneous) >= t.PLAYFUL_SCORE
        val w =
            (if (rest) t.W_CHASE_REST else 1.0) *
                (t.W_CHASE_BASE + maxOf(0.0, e)) *
                (1.0 + t.W_CHASE_KITTEN * kitten) *
                (if (playful) t.W_CHASE_PLAYFUL else 1.0) *
                (1.0 + t.PEAK_GAIN * peak)
        val gap = kit(b) - kit(a)
        val starter =
            when {
                gap > t.KITTEN_GAP -> b.slot
                -gap > t.KITTEN_GAP -> a.slot
                info.lead >= 0 && rng.chance(RANK_BIAS) -> info.lead
                else -> coin(rng)
            }
        return Option(Social.CHASE, starter, null, w)
    }

    /** Grooming: an awake cat licks one lying about on the floor or the sofa. */
    private fun groom(a: CatMind, b: CatMind, info: PairInfo, rng: Rng): Option? {
        val can =
            arrayOf(a, b).filter { r ->
                val g = if (r === a) b else a
                resting(r) &&
                    free(r, wake = false) &&
                    free(g, wake = false) &&
                    (r.exec.surface == SurfaceId.FLOOR || r.exec.surface == SurfaceId.COUCH)
            }
        if (can.isEmpty()) return null
        val both = resting(a) && resting(b)
        val receiver =
            if (can.size == 1) can[0]
            else {
                val gap = kit(b) - kit(a)
                val groomer =
                    when {
                        gap > t.KITTEN_GAP -> a.slot // the older grooms
                        -gap > t.KITTEN_GAP -> b.slot
                        info.lead >= 0 && rng.chance(RANK_BIAS) -> 1 - info.lead
                        else -> coin(rng)
                    }
                if (groomer == a.slot) b else a
            }
        val groomer = if (receiver === a) b else a
        val w =
            (if (both) t.W_GROOM_RESTING else t.W_GROOM_AWAKE) *
                (t.W_GROOM_CALM - t.W_GROOM_AROUSED * maxOf(0.0, groomer.arousal))
        return Option(Social.GROOM, groomer.slot, null, w)
    }

    /**
     * Curling up: a sleepy cat joins the one napping on the floor or the sofa, or two sleepy cats
     * go to one soft spot together (the sofa, a rug).
     */
    private fun nap(minds: List<CatMind>, awake: Boolean, rng: Rng): Option? {
        for (s in minds) {
            val j = minds.first { it !== s }
            if (s.runner.activity !== Sleep) continue
            val surface = s.exec.surface
            if (surface != SurfaceId.FLOOR && surface != SurfaceId.COUCH) continue
            if (surface == SurfaceId.FLOOR && s.here?.has(Spots.HIDDEN) == true) continue
            if (s.runner.seconds - s.runner.atPlaceS < t.JOIN_LEFT_S) continue
            if (!free(j, wake = false) || !sleepy(j)) continue
            return Option(Social.NAP, s.slot, null, t.W_NAP)
        }
        if (!awake || !minds.all(::sleepy)) return null
        var best: Spot? = null
        var bestScore = 0.0
        for (spot in CURL_SPOTS) {
            val like = minds.sumOf { it.book.like(it.slot, spot) }
            val soft = if (spot.has(Spots.SOFT)) BrainTuning.Places.SOFT_GAIN else 1.0
            val score = (BrainTuning.Places.LIKE_FLOOR + like) * soft
            if (score > bestScore) {
                bestScore = score
                best = spot
            }
        }
        return best?.let { Option(Social.NAP, coin(rng), it, t.W_NAP) }
    }

    /** The places two cats curl up on: wide enough for the spoon pose. */
    private val CURL_SPOTS = listOf(Spots.COUCH_L, Spots.LIVING_RUG, Spots.RUG)

    /** The appeal-weighted pick. */
    fun weighted(options: List<Option>, rng: Rng): Option {
        val total = options.sumOf { it.weight }
        var roll = rng.nextFloat() * total
        for (o in options) {
            roll -= o.weight
            if (roll < 0.0) return o
        }
        return options.last()
    }

    /**
     * The standoff's winner (a slot): the page's weighted coin on contest power (DNA dominance,
     * growth, confidence), challenger [chal] with its small edge. The page keeps the confidence;
     * native estimates it from the DNA as the page starts it, plus a little for the page's leader.
     */
    fun contest(minds: List<CatMind>, chal: Int, info: PairInfo, rng: Rng): Int {
        val other = 1 - chal
        val gap = power(minds[chal], info) + t.EDGE - power(minds[other], info)
        val p = 1.0 / (1.0 + Math.exp(-t.RANK_K * gap))
        return if (rng.nextFloat() < p) chal else other
    }

    private fun power(m: CatMind, info: PairInfo): Double {
        val p = m.persona
        val base =
            (CF_MID + CF_DM * (p.dominant - CF_MID) - CF_SK * (p.skittish - CF_MID) +
                    CF_OU * (p.outgoing - CF_MID))
                .coerceIn(CF_LO, CF_HI)
        val cf = base + if (info.lead == m.slot) t.LEAD_CF else 0.0
        return t.W_DM * p.dominant + t.W_AGE * growth(m) + t.W_CF * cf
    }

    private const val MINUTE_MS = 60_000.0
    private const val HALF = 0.5f
    private const val GLUM = -0.3
    private const val GLUM_DAMP = 0.5
    private const val UNDERDOG = 0.7f
    private const val RANK_BIAS = 0.65f
    private const val FLOOR_STANDOFF = 0.5
    private const val KITTEN_CHASE = 0.3

    /** The page's petRankBase: confidence from the DNA. */
    private const val CF_MID = 0.5
    private const val CF_DM = 0.5
    private const val CF_SK = 0.3
    private const val CF_OU = 0.15
    private const val CF_LO = 0.15
    private const val CF_HI = 0.85
}
