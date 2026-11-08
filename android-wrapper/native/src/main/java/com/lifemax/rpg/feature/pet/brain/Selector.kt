package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.Rng
import com.lifemax.rpg.feature.pet.body.Route
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.Tuning

/**
 * A candidate or the pick: [activity] at [spot] (null: in place) with its [score] and [reason];
 * [seconds] at the place when already decided (a resume, a scene), else NaN (the activity draws).
 */
class Choice(
    val activity: Activity,
    val spot: Spot?,
    val score: Double,
    val reason: String,
    val seconds: Double = Double.NaN,
) {
    /** `activity@place`, as the explain ring lists candidates. */
    val label: String
        get() = activity.id + "@" + (spot?.id ?: "here")
}

/**
 * How far a place is for one cat (plan 1.5 "travel cost"): the parkour planner's route from where
 * the cat stands, as seconds and the hardest jump's effort, cached by the standing spot (rounded to
 * [BrainTuning.Select.ROUTE_CACHE_PX]) and the cat's capability, so a cat that sits still costs no
 * planning. "Fridge only via the counter" is the planner's geometry, not a rule here.
 */
class Travel(
    private val exec: Executor,
    /** The measured ways, by place and start bucket; shared with the cat's catch-up copy. */
    private val cache: HashMap<Long, FloatArray> = HashMap(),
) {

    /** Seconds to [spot] (walking, run-ups, flights) and the hardest jump's effort, or null. */
    fun to(spot: Spot): FloatArray? {
        val from = exec.surface
        val bucket = Math.round(exec.x / BrainTuning.Select.ROUTE_CACHE_PX)
        val key =
            (from.ordinal.toLong() shl SURFACE_SHIFT) or
                ((bucket + BUCKET_BIAS).toLong() shl BUCKET_SHIFT) or
                (spot.index.toLong() shl SPOT_SHIFT) or
                (if (exec.weak) 1L else 0L)
        cache[key]?.let {
            return if (it[0] < 0f) null else it
        }
        if (cache.size >= BrainTuning.Select.ROUTE_CACHE_MAX) cache.clear()
        val info = measure(spot)
        cache[key] = info ?: UNREACHABLE
        return info
    }

    private fun measure(spot: Spot): FloatArray? {
        val walk = Tuning.Walk.WALK_SPEED
        if (exec.surface == SurfaceId.FLOOR && spot.onFloor) {
            val s =
                Math.abs(spot.x - exec.x) / walk + Math.abs(spot.z - exec.z) / Tuning.Walk.Z_SPEED
            return floatArrayOf(s, 0f, 0f)
        }
        val r: Route = exec.route(spot.surface, spot.x) ?: return null
        var effort = 0f
        for (i in r.leaps.indices) effort = maxOf(effort, r.leaps[i].arc.effort)
        return floatArrayOf(r.cost, effort, SpotBook.passesOf(r.leaps, spot.surface).toFloat())
    }

    private companion object {
        val UNREACHABLE = floatArrayOf(-1f, 0f, 0f)
        const val SURFACE_SHIFT = 40
        const val BUCKET_SHIFT = 16
        const val SPOT_SHIFT = 1
        const val BUCKET_BIAS = 4096
    }
}

/**
 * Chooses what a cat does next (plan 1.5): every eligible activity of the registry, at every place
 * that suits it, is scored `appeal × place × mood × novelty × noise / (1 + travel/30 + effort²)`;
 * the pick is a softmax over the best [BrainTuning.Select.TOP] with the persona's temperature, ties
 * to the earlier candidate. The house rules for a weak cat (low places near the bowl) are applied
 * here, for every activity alike.
 */
class Selector(private val registry: ActivityRegistry) {
    private val s = BrainTuning.Select

    /** The best candidates of the last pick, best first (the explain ring's top 3). */
    val lastTop = ArrayList<Choice>()

    /**
     * The pick for [m] with [rng], from activities not cooling down ([cooling]) and with [recent]
     * (activity indexes, newest first) damped by novelty; [travel] prices the places.
     */
    fun pick(
        m: MindView,
        rng: Rng,
        travel: Travel,
        cooling: (Int) -> Boolean,
        recent: IntArray,
    ): Choice {
        val cands = ArrayList<Choice>()
        val all = registry.all
        for (i in all.indices) {
            val a = all[i]
            if (a.reflexOnly || cooling(i) || !a.eligible(m)) continue
            val base = a.appeal(m) * novelty(i, recent) * moodFactor(m, a)
            if (base <= 0.0) continue
            if (a.placeFlags == 0) {
                cands += Choice(a, null, base * noise(rng), a.reason(m))
                continue
            }
            for (spot in Spots.all) {
                if (
                    (spot.flags and a.placeFlags) == 0 ||
                        spot.hidden ||
                        !allowed(m, spot) ||
                        !a.suits(m, spot)
                )
                    continue
                val info = travel.to(spot) ?: continue
                if (!m.book.free(m.slot, spot, spot.x, info[2].toInt())) continue
                val cost = 1.0 + info[0] / s.TRAVEL_S + info[1] * info[1]
                val moves = recent[0] == i && spot !== m.here
                val stay = if (moves) s.SAME_ELSEWHERE else 1.0
                val score = base * a.placeFactor(m, spot) * stay / cost * noise(rng)
                cands += Choice(a, spot, score, a.reason(m))
            }
        }
        cands.sortWith { x, y -> y.score.compareTo(x.score) }
        lastTop.clear()
        for (i in 0 until minOf(EXPLAIN_TOP, cands.size)) lastTop += cands[i]
        if (cands.isEmpty())
            return Choice(registry.find(Catalogue.LOAF)!!, null, 0.0, "nothing else")
        return softmax(cands, m.persona.temperature, rng)
    }

    private fun softmax(c: List<Choice>, temperature: Double, rng: Rng): Choice {
        val n = minOf(s.TOP, c.size)
        val best = c[0].score
        if (best <= 0.0) return c[0]
        val w = DoubleArray(n)
        var total = 0.0
        for (i in 0 until n) {
            w[i] = Math.exp((c[i].score / best - 1.0) / temperature)
            total += w[i]
        }
        var roll = rng.nextFloat() * total
        for (i in 0 until n) {
            roll -= w[i]
            if (roll < 0.0) return c[i]
        }
        return c[0]
    }

    private fun novelty(index: Int, recent: IntArray): Double {
        for (k in recent.indices) if (recent[k] == index) return s.NOVELTY[k]
        return 1.0
    }

    /** An excited cat leans to lively activities, a calm one to quiet ones. */
    private fun moodFactor(m: MindView, a: Activity): Double =
        1.0 + MOOD_LEAN * m.arousal * a.arousal

    private fun noise(rng: Rng): Double = 1.0 + s.NOISE * (2.0 * rng.nextFloat() - 1.0)

    companion object {
        private const val EXPLAIN_TOP = 3
        private const val MOOD_LEAN = 0.25

        /**
         * The house rules on places (plan 1.4): a weak cat keeps to low places near its bowl, and
         * at the last stages only to the floor by the bowl.
         */
        fun allowed(m: MindView, spot: Spot): Boolean {
            val p = m.persona
            if (!p.weak) return true
            val fromBowl = Math.abs(spot.x - Spots.bowl(m.slot).x)
            if (p.atBowlOnly) return spot.onFloor && fromBowl <= BrainTuning.Persona.BOWL_REST_REACH
            return spot.low && fromBowl <= BrainTuning.Persona.WEAK_BOWL_REACH
        }
    }
}
