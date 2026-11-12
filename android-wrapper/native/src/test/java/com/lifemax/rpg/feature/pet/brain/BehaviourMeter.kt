package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.Clips
import com.lifemax.rpg.feature.pet.body.MouthAnchors
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.ToyField
import com.lifemax.rpg.feature.pet.body.ToyKind
import com.lifemax.rpg.feature.pet.body.World
import java.util.Locale

/**
 * Measures how much two cats of the house really move (the roaming work): a [CatWorld] on live
 * bodies run on the fake clock, sampled at every think quantum (a quarter second), with the house's
 * own toys lying on the rugs as the app lays them and the owner's view open. A [Trace] of the run
 * can be summed up over any window: per cat the share of the time by posture, the distinct places
 * stood at, the mean and longest time held in one posture, the distance walked and the time shared
 * with the other cat. Test scope only: [RoamingTest] asserts on it and prints the numbers the
 * tuning is judged by.
 */
object BehaviourMeter {
    /** How a clip counts: what the cat is doing with its body at that instant. */
    enum class Posture {
        SLEEP,
        LIE,
        SIT,

        /** Washing, eating, stretching, scratching, kneading, calling: busy, but in place. */
        BUSY,
        MOVE,
        PLAY,
        EXPLORE,
    }

    /** The posture a clip named [clip] is. */
    fun posture(clip: String): Posture =
        when (clip) {
            "sleep",
            "sleep_w" -> Posture.SLEEP
            "loaf",
            "loaf_w" -> Posture.LIE
            "idle_sit",
            "idle_sit_w",
            "slowblink" -> Posture.SIT
            "walk",
            "walk_w",
            "run",
            "walk_front",
            "walk_back",
            "carry",
            "leap",
            "jump_up",
            "jump_air",
            "jump_land",
            "turn" -> Posture.MOVE
            "play",
            "bat",
            "swat",
            "kick",
            "roll",
            "crouch",
            "pickup",
            "toss",
            "bonk" -> Posture.PLAY
            "sniff" -> Posture.EXPLORE
            else -> Posture.BUSY
        }

    const val SAMPLE_S = 0.25

    /** One cat's numbers over a window. */
    class CatStats(val name: String) {
        val samples = IntArray(Posture.entries.size)
        val places = LinkedHashSet<String>()
        val rooms = HashSet<Int>()
        var distance = 0.0
        var runs = 0
        var runSamples = 0
        var longestStill = 0
        var pairSamples = 0
        private var lastPosture: Posture? = null
        private var run = 0
        private var still = 0
        private var lastX = Float.NaN
        private var lastZ = 0f

        val total: Int
            get() = samples.sum()

        /** Awake samples (not asleep). */
        val awake: Int
            get() = total - samples[Posture.SLEEP.ordinal]

        /** Share of the awake time spent moving, playing or exploring (0..1). */
        val activeShare: Double
            get() =
                if (awake == 0) 0.0
                else
                    (samples[Posture.MOVE.ordinal] +
                        samples[Posture.PLAY.ordinal] +
                        samples[Posture.EXPLORE.ordinal]) / awake.toDouble()

        /** Share of the whole time spent lying, sitting or sleeping (0..1). */
        val lazyShare: Double
            get() =
                if (total == 0) 0.0
                else
                    (samples[Posture.SLEEP.ordinal] +
                        samples[Posture.LIE.ordinal] +
                        samples[Posture.SIT.ordinal]) / total.toDouble()

        /** The share of the whole time in [p] (0..1). */
        fun share(p: Posture): Double =
            if (total == 0) 0.0 else samples[p.ordinal] / total.toDouble()

        /** Mean seconds the cat stayed in one posture before it changed. */
        val meanPostureS: Double
            get() = if (runs == 0) 0.0 else runSamples * SAMPLE_S / runs

        /** The longest stretch awake in lying, sitting or a busy bit in place, seconds. */
        val longestStillS: Double
            get() = longestStill * SAMPLE_S

        fun add(p: Posture, x: Float, z: Float, place: String?, room: Int) {
            samples[p.ordinal]++
            if (place != null && p != Posture.MOVE) places += place
            rooms += room
            if (!lastX.isNaN()) distance += Math.abs(x - lastX) + Math.abs(z - lastZ)
            lastX = x
            lastZ = z
            if (p == lastPosture) {
                run++
            } else {
                if (lastPosture != null) {
                    runs++
                    runSamples += run
                }
                run = 1
                lastPosture = p
            }
            val idle = p == Posture.LIE || p == Posture.SIT || p == Posture.BUSY
            still = if (idle) still + 1 else 0
            longestStill = maxOf(longestStill, still)
        }

        /** One line of the report. */
        fun line(): String {
            fun pct(p: Posture) = String.format(Locale.ROOT, "%3.0f", share(p) * 100)
            return String.format(
                Locale.ROOT,
                "%-5s sleep %s lie %s sit %s busy %s | move %s play %s sniff %s | active(awake) %3.0f%% " +
                    "places %2d rooms %d meanPosture %5.1fs longestStill %5.0fs dist %5.0fpx pair %2.0f%%",
                name,
                pct(Posture.SLEEP),
                pct(Posture.LIE),
                pct(Posture.SIT),
                pct(Posture.BUSY),
                pct(Posture.MOVE),
                pct(Posture.PLAY),
                pct(Posture.EXPLORE),
                activeShare * 100,
                places.size,
                rooms.size,
                meanPostureS,
                longestStillS,
                distance,
                if (total == 0) 0.0 else pairSamples * 100.0 / total,
            )
        }
    }

    /** What the cats start with: the page's needs, as the brain sees them. */
    data class Needs(
        val energy: Double = 0.6,
        val hunger: Int = 0,
        val trust: Double = 0.6,
        val rel: Double = 0.5,
        val name: String = "typical",
    )

    /** Ted: a curious, playful black kitten; Lily: a calmer ginger and white grown cat. */
    val TED = mapOf("sk" to 0.2, "ou" to 0.85, "dm" to 0.5, "sp" to 0.85, "fr" to 0.7, "vo" to 0.6)
    val LILY =
        mapOf("sk" to 0.35, "ou" to 0.35, "dm" to 0.45, "sp" to 0.3, "fr" to 0.65, "vo" to 0.4)

    fun house(needs: Needs): List<CatProfile> =
        listOf(
            CatDays.cat("Ted", TED, id = "cmu9rkw00", stage = 2, hunger = needs.hunger)
                .copy(trust = needs.trust),
            CatDays.cat("Lily", LILY, id = "cmv2cdxc0", slot = 1, hunger = needs.hunger)
                .copy(trust = needs.trust),
        )

    /**
     * A recorded run: one sample per think quantum per cat, from [startHour] (local hour of day at
     * the first sample), so any window of it can be summed up with [stats].
     */
    class Trace(val names: List<String>, val startHour: Double, capacity: Int) {
        val spots = LinkedHashMap<String, Int>()
        val acts = LinkedHashMap<String, Int>()
        val posture = Array(names.size) { ByteArray(capacity) }
        val place = Array(names.size) { ShortArray(capacity) }
        val room = Array(names.size) { ByteArray(capacity) }
        val act = Array(names.size) { ShortArray(capacity) }
        val x = Array(names.size) { FloatArray(capacity) }
        val z = Array(names.size) { FloatArray(capacity) }
        val pair = Array(names.size) { BooleanArray(capacity) }
        var size = 0

        /** The distinct activity names of the run. */
        val activityNames: Set<String>
            get() = acts.keys

        /** Seconds cat [cat] spent in activity [name]. */
        fun seconds(cat: Int, name: String): Double {
            val id = acts[name] ?: return 0.0
            var n = 0
            for (i in 0 until size) if (act[cat][i].toInt() == id) n++
            return n * SAMPLE_S
        }

        /** Cat [cat]'s numbers for the hours [fromH] until [toH] of the day (local, from 0). */
        fun stats(cat: Int, fromH: Double, toH: Double): CatStats {
            val c = CatStats(names[cat])
            val spotNames = spots.entries.associate { it.value to it.key }
            val a = ((fromH - startHour) * 3600 / SAMPLE_S).toInt().coerceIn(0, size)
            val b = ((toH - startHour) * 3600 / SAMPLE_S).toInt().coerceIn(0, size)
            for (i in a until b) {
                val pl = place[cat][i].toInt()
                c.add(
                    Posture.entries[posture[cat][i].toInt()],
                    x[cat][i],
                    z[cat][i],
                    if (pl < 0) null else spotNames[pl],
                    room[cat][i].toInt(),
                )
                if (pair[cat][i]) c.pairSamples++
            }
            return c
        }
    }

    /** The activities two cats play together (the director's). */
    private val pairIds: Set<String> by lazy { Social.ACTIVITIES.map { it.id }.toSet() }

    /**
     * Runs the house for [hours] from local [startHour] of day [day] (day 0 is the harness's 30
     * Oct), the owner's view open throughout, meals at [meals] (hours of day), the cats last fed
     * [lastFedH] hours before the start, and records every think quantum from [skipMin] minutes in.
     * With [preset] the cats start with the [needs]' energy, taken up at once (the first pick is
     * from that state), instead of the world's start drives.
     */
    fun record(
        needs: Needs = Needs(),
        day: Int = 0,
        startHour: Double = 0.0,
        hours: Double = 24.0,
        meals: List<Double> = listOf(8.0, 18.25),
        lastFedH: Double = 5.75,
        skipMin: Double = 0.0,
        preset: Boolean = false,
        cats: List<CatProfile> = house(needs),
    ): Trace {
        val skipQuanta = (skipMin * 60 / SAMPLE_S).toInt()
        val total = (hours * 3600 / SAMPLE_S).toInt()
        val trace = Trace(cats.map { it.name }, startHour + skipMin / 60, total - skipQuanta + 1)
        val owner =
            CatDays.owner(
                cats = 2,
                meals = meals.map { it - startHour }.filter { it > 0 },
                visits = listOf(0.0 to hours),
                lastFedH = lastFedH,
                hours = hours.toInt() + 1,
            )
        var quanta = 0
        val start = CatDays.MIDNIGHT + day * 24 * CatDays.HOUR + (startHour * CatDays.HOUR).toLong()
        CatDays.run(
            cats,
            owner,
            hours = hours,
            start = start,
            setup = { world ->
                world.pair(PairInfo(rel = needs.rel, met = true))
                val toys = ToyField()
                toys.add(ToyKind.BALL_A, 112f, SurfaceId.FLOOR, 58f)
                toys.add(ToyKind.BALL_A, -54f, SurfaceId.FLOOR, 62f)
                world.bindToys(toys)
                for (m in world.minds) {
                    m.body?.attachToys(toys, MouthAnchors.of(null, m.profile.stage))
                    m.trust = needs.trust
                    if (preset) {
                        m.drives[Drive.ENERGY] = needs.energy
                        m.resume(null)
                    }
                }
            },
        ) { world, _, think ->
            if (!think) return@run
            if (quanta++ < skipQuanta || trace.size >= trace.x[0].size) return@run
            val i = trace.size++
            for ((c, m) in world.minds.withIndex()) {
                val p = m.body!!.pose
                val spot = Spots.at(p.surface, p.x)
                val act = world.now(c).activity
                trace.posture[c][i] = posture(Clips.all[p.clip].name).ordinal.toByte()
                trace.place[c][i] =
                    if (spot == null) -1 else trace.spots.getOrPut(spot.id) { spot.index }.toShort()
                trace.room[c][i] = World.roomOf(p.x).toByte()
                trace.act[c][i] = trace.acts.getOrPut(act) { trace.acts.size }.toShort()
                trace.x[c][i] = p.x
                trace.z[c][i] = p.z
                trace.pair[c][i] = act in pairIds
            }
        }
        return trace
    }

    /** A [minutes] window after a [warmMin] warm-up from the preset [needs], from local [hour]. */
    fun window(
        hour: Double,
        needs: Needs,
        warmMin: Double = 6.0,
        minutes: Double = 20.0,
    ): List<CatStats> {
        val t =
            record(
                needs,
                startHour = hour,
                hours = (warmMin + minutes) / 60,
                skipMin = warmMin,
                preset = true,
                meals = emptyList(),
                lastFedH = if (needs.hunger >= 3) 30.0 else 5.75,
            )
        val from = hour + warmMin / 60
        return t.names.indices.map { t.stats(it, from, from + minutes / 60) }
    }
}
