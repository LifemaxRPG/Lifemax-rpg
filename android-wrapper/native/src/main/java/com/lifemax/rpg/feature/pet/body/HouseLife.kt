package com.lifemax.rpg.feature.pet.body

/**
 * What a house needs to know about one of its cats: its seed (see [Autopilot.seedFor]) and its
 * hunger.
 */
class HouseCat(
    val seed: Int,
    val weak: Boolean,
    /** Body scale for jumping: 1 grown, a kitten's `Growth.scale` (see [JumpCapability.of]). */
    val growthScale: Float = 1f,
    /** The rig's `bl` and `lg` morph multipliers; 1 when unknown. */
    val bodyLength: Float = 1f,
    val legLength: Float = 1f,
)

/**
 * One or two cats living in the whole house: each a [CatBody] kept busy by its own [Autopilot] in
 * house mode, which walks between the rooms, eats at the bowls, sleeps on the bed, sits on the
 * sofa, climbs the cat tree and watches from the window sill. The pilots know each other's plans
 * and keep clear of one another (see [HousePlans.GAP]), so the cats never stand on each other.
 *
 * Everything is stepped with explicit `dt`, never reads a clock and has no Android types, so a day
 * of two cats can be run to any moment in a test, and the same seeds and the same frame times
 * replay the same day.
 *
 * @param cats the cats of the house: one or two. The first is the one the page called first.
 * @param autopilot false hands the bodies to the caller (tests and previews script their plans).
 */
class HouseLife(cats: List<HouseCat>, private val autopilot: Boolean = true) {
    init {
        require(cats.size in 1..MAX_CATS) { "a house has one or two cats" }
    }

    private val pilots: Array<Autopilot>

    /** The cats' bodies, in the order given; each [CatBody.pose] is what to draw. */
    val bodies: Array<CatBody>

    init {
        val links = Array(cats.size) { HouseLink(it) }
        pilots =
            Array(cats.size) {
                Autopilot(
                    cats[it].seed,
                    World.HOUSE_MIN_X,
                    World.HOUSE_MAX_X,
                    cats[it].weak,
                    links[it],
                )
            }
        if (cats.size == MAX_CATS) {
            links[0].mate = pilots[1]
            links[1].mate = pilots[0]
        }
        bodies =
            Array(cats.size) {
                val pilot = pilots[it]
                CatBody(cats[it].seed, pilot.startX).also { body ->
                    body.weak = cats[it].weak
                    body.growthScale = cats[it].growthScale
                    body.bodyLength = cats[it].bodyLength
                    body.legLength = cats[it].legLength
                    body.placeAt(pilot.startX, World.ART_Z, SurfaceId.FLOOR, pilot.startFacing)
                }
            }
    }

    /** How many cats live here. */
    val count: Int
        get() = bodies.size

    /** The move cat [i] most recently began (for tests and for variety). */
    fun lastMove(i: Int): Autopilot.Move = pilots[i].last

    /** How many plans cat [i] has been given so far. */
    fun plans(i: Int): Int = pilots[i].plans

    /** Advance every cat by [dt] seconds of host time. Allocation free except when a cat plans. */
    fun step(dt: Float) {
        for (i in bodies.indices) {
            bodies[i].step(dt)
            if (autopilot) pilots[i].update(bodies[i], dt)
        }
    }

    /**
     * The owner fed cat [i]: it leaves what it was doing, walks to its bowl, eats and washes (the
     * page's feed, petFeedClick). A cat that does not exist is ignored.
     */
    fun serveMeal(i: Int) {
        if (i !in bodies.indices) return
        pilots[i].direct(bodies[i], Feeding.meal(bodies[i], i), Feeding.bowlX(i))
    }

    /**
     * The owner tried to feed cat [i] and it was not hungry: it goes to its bowl, sits and washes,
     * as the page's does.
     */
    fun visitBowl(i: Int) {
        if (i !in bodies.indices) return
        pilots[i].direct(bodies[i], Feeding.visit(bodies[i], i), Feeding.bowlX(i))
    }

    /**
     * The owner slow blinks at cat [i]: the cat blinks back and, if it was doing nothing, looks up
     * at the owner for a moment. A sleeping cat is left alone (the page says it is asleep). True if
     * the cat blinked.
     */
    fun slowBlink(i: Int): Boolean {
        if (i !in bodies.indices) return false
        val body = bodies[i]
        if (Clips.all[body.pose.clip].name.startsWith(ASLEEP)) return false
        body.react(ReactionKind.SLOW_BLINK)
        if (body.isIdle)
            pilots[i].direct(body, listOf(PlanStep.Act("lookup", LOOK_UP_S)), body.pose.x)
        return true
    }

    /** Step [seconds] at [hz] frames per second, as a host would; for tests and previews. */
    fun advance(seconds: Float, hz: Int = 60) {
        val dt = 1f / hz
        repeat(Math.round(seconds * hz)) { step(dt) }
    }

    companion object {
        const val MAX_CATS = 2
        private const val ASLEEP = "sleep"
        private const val LOOK_UP_S = 2.2f
    }
}
