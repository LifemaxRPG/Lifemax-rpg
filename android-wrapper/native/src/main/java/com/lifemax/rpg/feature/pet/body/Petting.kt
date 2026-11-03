package com.lifemax.rpg.feature.pet.body

/** The little pictures a touch makes rise from the cat (the room atlas has them all). */
enum class Burst {
    HEART,
    HEART_BIG,
    NOTE,
    EXCLAIM,
    SPARKLE,
}

/** The buzzes a touch has: a hand landing, a heart, a level gained. */
enum class Beat {
    DOWN,
    HEART,
    LEVEL_UP,
}

/**
 * What a touch asks of the screen that plays it: pictures to draw, buzzes, the purr (its sound is
 * the page's), and the page's side of a touch that ended.
 */
interface TouchSink {
    /** A picture rises from room position ([x], [y]). */
    fun burst(kind: Burst, x: Float, y: Float)

    /** The phone buzzes (when the owner's haptics are on). */
    fun buzz(beat: Beat)

    /** Cat [cat] purrs at [level] (0 to 1), or stops with 0: the page plays the sound. */
    fun purr(cat: Int, level: Float)

    /** A touch on cat [cat] ended: the page counts it and keeps the books. */
    fun stroke(cat: Int, outcome: TouchModel.Outcome)
}

/**
 * The reflexes of a cat under the owner's finger: the screen answers a touch at once, with the
 * cat's own body, and tells the page afterwards; the page keeps the books ([TouchSink.stroke]).
 *
 * A finger that lands on a cat (the front one where two overlap, see [TouchHit]) starts a
 * [TouchModel] for it, and the rules of the page play out as a reflex:
 * - a stranger (trust 0) startles and runs; a shy cat (1) sits and, after a while, gets up and
 *   leaves; a warmer cat blinks (2) or purrs (3 and up) and hearts rise;
 * - the ears go back when the petting is too much, and past that the cat gets up and goes;
 * - a sleeping cat that trusts you (3 and up) stays asleep and purrs softly; a lighter sleeper
 *   wakes with a stretch;
 * - on release the cat sits a moment, or blinks if it trusts you.
 *
 * A finger that lands on the empty room and drags onto a cat pets it from there ("drag-through").
 * Everything runs on the `dt` of the room's clock, so a pause pauses it too.
 *
 * @param house the cats; [HouseLife.direct] gives them their reflex plans.
 */
class Petting(private val house: HouseLife, private val sink: TouchSink, seed: Int = 1) {
    private val models = Array(house.count) { TouchModel() }
    private val rng = Rng.derive(seed, SALT)
    private val purring = BooleanArray(house.count)
    private val woke = BooleanArray(house.count)
    private val affectFor = FloatArray(house.count)
    private var active = NONE
    private var dragging = false
    private var fingerX = 0f

    /** The cat under the finger now, or -1. */
    val touching: Int
        get() = active

    /** The saturation the reflex holds for cat [cat] (for the page's projection and tests). */
    fun saturation(cat: Int): Float = models[cat].saturation

    /** The model of cat [cat]; tests read it. */
    internal fun model(cat: Int): TouchModel = models[cat]

    /**
     * How the page says cat [cat] takes touch: its trust [level], trait factor [satMul], trust
     * [trust] (0 to 1) and the saturation [sat] it holds.
     */
    fun profile(cat: Int, level: Int, satMul: Float, trust: Float, sat: Float) {
        if (cat !in models.indices) return
        models[cat].profile(level, house.bodies[cat].weak, satMul, trust)
        models[cat].seed(sat)
    }

    /**
     * A finger lands at room position ([x], [y]). Returns the cat it landed on, or [TouchHit.BOWL]
     * or [TouchHit.ROOM].
     */
    fun down(x: Float, y: Float): Int {
        fingerX = x
        val hit = TouchHit.at(house.bodies, x, y)
        dragging = hit == TouchHit.ROOM
        if (hit >= 0) begin(hit)
        return hit
    }

    /** The finger moved to ([x], [y]). */
    fun move(x: Float, y: Float) {
        fingerX = x
        if (active >= 0) {
            models[active].move()
            house.bodies[active].lookAt(x, house.bodies[active].pose.z)
        } else if (dragging) {
            val hit = TouchHit.at(house.bodies, x, y)
            if (hit >= 0) begin(hit)
        }
    }

    /** The finger lifted (or the system took the touch away). */
    fun up() {
        dragging = false
        if (active >= 0) finish()
    }

    /** The page says petting won cat [cat] a level of trust: a burst of big hearts and a buzz. */
    fun levelUp(cat: Int) {
        if (cat !in models.indices) return
        val pose = house.bodies[cat].pose
        val k = pose.depthScale
        repeat(LEVEL_HEARTS) {
            sink.burst(
                Burst.HEART_BIG,
                pose.x + rng.range(-LEVEL_SPREAD_X, LEVEL_SPREAD_X),
                pose.y - LEVEL_ABOVE * k + rng.range(-LEVEL_SPREAD_Y, LEVEL_SPREAD_Y),
            )
        }
        sink.buzz(Beat.LEVEL_UP)
    }

    /** Advance by [dt] seconds. Allocation free except when a reflex makes a plan. */
    fun step(dt: Float) {
        for (i in models.indices) {
            val flags = models[i].step(dt)
            if (flags != 0) react(i, flags)
            if (affectFor[i] > 0f) {
                affectFor[i] -= dt
                if (affectFor[i] <= 0f)
                    house.bodies[i].setAffect(EarState.FORWARD, TailState.NEUTRAL)
            }
        }
    }

    // ------------------------------------------------------------------ a finger lands

    private fun begin(cat: Int) {
        active = cat
        dragging = false
        val body = house.bodies[cat]
        val model = models[cat]
        val asleep = house.isAsleep(cat)
        val flags = model.begin(asleep)
        woke[cat] = flags and TouchModel.WOKE != 0
        body.lookAt(fingerX, body.pose.z)
        if (flags and TouchModel.SPAM != 0) affect(cat, EarState.AIRPLANE_FLAT, TailState.LOW_SWISH)
        if (flags and TouchModel.STARTLE != 0) return startle(cat)
        sink.buzz(Beat.DOWN)
        if (woke[cat]) {
            body.react(ReactionKind.STARTLE)
            burst(Burst.EXCLAIM, cat, EXCLAIM_ABOVE)
        }
        house.direct(cat, listOf(PlanStep.Act(holdClip(cat), HOLD_MAX_S)), body.pose.x)
        mood(cat)
    }

    /** A stranger: a hiss (the page's), a start, and away from the finger. */
    private fun startle(cat: Int) {
        val body = house.bodies[cat]
        body.react(ReactionKind.STARTLE)
        burst(Burst.EXCLAIM, cat, EXCLAIM_ABOVE)
        runAway(cat, FLEE_FAR, LOAF_S)
    }

    private fun holdClip(cat: Int): String =
        when {
            models[cat].keepSleep -> SLEEP
            house.bodies[cat].weak || models[cat].level <= 1 -> IDLE_SIT
            models[cat].level == 2 -> SLOW_BLINK
            else -> PURR
        }

    /** Ears and tail by how much the cat trusts the hand. */
    private fun mood(cat: Int) {
        when (models[cat].level) {
            1 -> affect(cat, EarState.FORWARD, TailState.LOW_SWISH)
            2,
            3 -> affect(cat, EarState.RELAXED_SIDE, TailState.QUESTION_MARK)
            else -> affect(cat, EarState.RELAXED_SIDE, TailState.HIGH_FLAG)
        }
        affectFor[cat] = 0f // the mood lasts as long as the finger does
    }

    private fun affect(cat: Int, ear: EarState, tail: TailState) {
        house.bodies[cat].setAffect(ear, tail)
        affectFor[cat] = AFFECT_S
    }

    // ------------------------------------------------------------------ a finger rests

    private fun react(cat: Int, flags: Int) {
        val body = house.bodies[cat]
        val model = models[cat]
        if (flags and TouchModel.WARNED != 0)
            affect(cat, EarState.AIRPLANE_FLAT, TailState.LOW_SWISH)
        if (flags and TouchModel.BEAT != 0) hearts(cat, model.level)
        if (flags and TouchModel.PURR_CHANGED != 0) purr(cat, model.purr)
        if (flags and (TouchModel.LEFT_OVER or TouchModel.LEFT_SHY) != 0) {
            burst(Burst.EXCLAIM, cat, EXCLAIM_ABOVE)
            if (flags and TouchModel.LEFT_OVER != 0) {
                affect(cat, EarState.AIRPLANE_FLAT, TailState.LOW_SWISH)
                runAway(cat, rng.range(OVER_NEAR, OVER_FAR), OVER_GROOM_S, groom = true)
            } else {
                runAway(cat, SHY_FAR, SHY_SIT_S, groom = false)
            }
            body.stopLooking()
        }
    }

    private fun hearts(cat: Int, level: Int) {
        val pose = house.bodies[cat].pose
        val big = level >= TouchTuning.DEVOTED_LEVEL
        val k = pose.depthScale
        val x = pose.x + pose.facing * HEART_SIDE * k + rng.range(-HEART_SPREAD, HEART_SPREAD)
        sink.burst(if (big) Burst.HEART_BIG else Burst.HEART, x, pose.y - HEART_ABOVE * k)
        sink.buzz(Beat.HEART)
        if (big && rng.chance(NOTE_CHANCE))
            sink.burst(
                Burst.NOTE,
                pose.x + rng.range(-NOTE_SPREAD, NOTE_SPREAD),
                pose.y - NOTE_ABOVE * k,
            )
    }

    private fun purr(cat: Int, level: Float) {
        val body = house.bodies[cat]
        if (level > 0f) body.react(ReactionKind.PURR, PURR_S)
        else body.endReaction(ReactionKind.PURR)
        purring[cat] = level > 0f
        sink.purr(cat, level)
    }

    // ------------------------------------------------------------------ a finger lifts

    private fun finish() {
        val cat = active
        active = NONE
        val body = house.bodies[cat]
        val model = models[cat]
        val gone = model.phase == TouchModel.Phase.LEFT
        val out = model.end()
        body.stopLooking()
        if (purring[cat]) purr(cat, 0f)
        if (!gone) settle(cat, out)
        sink.stroke(cat, out)
    }

    /** The hand lifts from a cat that stayed: back to what it was, or a moment of sitting. */
    private fun settle(cat: Int, out: TouchModel.Outcome) {
        val body = house.bodies[cat]
        val steps =
            when {
                out.keepSleep -> listOf(PlanStep.Act(SLEEP, RESUME_SLEEP_S))
                woke[cat] -> WAKE_CASCADE
                else ->
                    listOf(
                        PlanStep.Act(
                            if (out.level >= 3 && !body.weak) SLOW_BLINK else IDLE_SIT,
                            SETTLE_S,
                        )
                    )
            }
        house.direct(cat, steps, body.pose.x)
        affectFor[cat] = SETTLE_S
        if (out.level >= 2 && !body.weak && out.saturation < TouchTuning.SAT_WARN)
            body.react(ReactionKind.SLOW_BLINK)
    }

    /** Walk (run) [far] pixels away from the finger, then [then] for a while. */
    private fun runAway(cat: Int, far: Float, rest: Float, groom: Boolean = false) {
        val body = house.bodies[cat]
        val away = if (body.pose.x >= fingerX) 1f else -1f
        val x = World.clampHouse(body.pose.x + away * far)
        val steps = ArrayList<PlanStep>(RETREAT_STEPS)
        steps.addAll(
            body.routeTo(SurfaceId.FLOOR, x).map {
                if (it is PlanStep.Walk) it.copy(run = true) else it
            }
        )
        steps.add(PlanStep.Face(if (away > 0f) -1 else 1))
        if (groom) steps.add(PlanStep.Act(GROOM, GROOM_S))
        steps.add(PlanStep.Act(if (groom) IDLE_SIT else LOAF, rest))
        house.direct(cat, steps, x)
    }

    private fun burst(kind: Burst, cat: Int, above: Float) {
        val pose = house.bodies[cat].pose
        val k = pose.depthScale
        sink.burst(kind, pose.x + pose.facing * EXCLAIM_SIDE * k, pose.y - above * k)
    }

    private companion object {
        const val NONE = -1
        const val SALT = 0x70e7
        const val SLEEP = "sleep"
        const val IDLE_SIT = "idle_sit"
        const val SLOW_BLINK = "slowblink"
        const val PURR = "purr"
        const val GROOM = "groom"
        const val LOAF = "loaf"

        /** A hold lasts until the finger lifts; this is only the longest the plan can run. */
        const val HOLD_MAX_S = 600f
        const val PURR_S = 600f
        const val AFFECT_S = 6f
        const val SETTLE_S = 2.4f
        const val RESUME_SLEEP_S = 20f
        const val FLEE_FAR = 40f
        const val LOAF_S = 10f
        const val OVER_NEAR = 30f
        const val OVER_FAR = 50f
        const val OVER_GROOM_S = 6f
        const val GROOM_S = 3f
        const val SHY_FAR = 40f
        const val SHY_SIT_S = 3f
        const val RETREAT_STEPS = 6
        const val EXCLAIM_ABOVE = 42f
        const val EXCLAIM_SIDE = 10f
        const val HEART_SIDE = 14f
        const val HEART_SPREAD = 6f
        const val HEART_ABOVE = 40f
        const val NOTE_CHANCE = 0.5f
        const val NOTE_SPREAD = 12f
        const val NOTE_ABOVE = 44f
        const val LEVEL_HEARTS = 5
        const val LEVEL_SPREAD_X = 14f
        const val LEVEL_SPREAD_Y = 6f
        const val LEVEL_ABOVE = 30f

        /** The wake-up of a cat roused from sleep: a stretch, a quiet mew, a slow blink, a sit. */
        val WAKE_CASCADE =
            listOf(
                PlanStep.Act("stretch", 2.0f),
                PlanStep.Act("meow", 0.6f),
                PlanStep.Act(SLOW_BLINK, 1.8f),
                PlanStep.Act(IDLE_SIT, 2.4f),
            )
    }
}
