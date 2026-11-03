package com.lifemax.rpg.feature.pet.body

/** The scenarios of the golden traces and the contact sheets: a scene, its plan and its length. */
object ToyScenes {
    class Case(val name: String, val scene: ToyScene, val seconds: Float, val plan: List<PlanStep>)

    private fun case(name: String, seconds: Float, scene: ToyScene, vararg steps: PlanStep): Case =
        Case(name, scene, seconds, steps.toList())

    /** The cat walks up to a toy, notices it, reaches down and takes it. */
    fun pickup(kind: ToyKind = ToyKind.BALL_A, hz: Int = 60) =
        case("pickup", 4f, ToyScene(kind, toyX = 112f, catX = 50f, hz = hz), PlanStep.PickUp(0))

    /** Pick up, carry right, turn round and carry back: the toy lags the turn. */
    fun carryTurn(kind: ToyKind = ToyKind.MOUSE, hz: Int = 60) =
        case(
            "carry-turn",
            9f,
            ToyScene(kind, toyX = 100f, catX = 56f, hz = hz),
            PlanStep.PickUp(0),
            PlanStep.Carry(160f),
            PlanStep.Carry(70f),
            PlanStep.Act("idle_sit", 1f),
        )

    /** Pick up, walk, toss along the floor: the toy keeps the swing, tumbles and rests. */
    fun tossFloor(kind: ToyKind = ToyKind.BALL_A, hz: Int = 60) =
        case(
            "toss-floor",
            9f,
            ToyScene(kind, toyX = 62f, catX = 20f, hz = hz),
            PlanStep.PickUp(0),
            PlanStep.Carry(98f),
            PlanStep.Toss(-1, 0.7f),
            PlanStep.Act("idle_sit", 2f),
        )

    /** In the kitchen: pick up, step up to the counter and toss the toy onto its worktop. */
    fun tossCounter(kind: ToyKind = ToyKind.BALL_A, hz: Int = 60) =
        case(
            "toss-counter",
            8f,
            ToyScene(kind, toyX = 236f, catX = 200f, hz = hz),
            PlanStep.PickUp(0),
            PlanStep.Carry(256f),
            PlanStep.Toss(1, 0.8f, SurfaceId.COUNTER, 312f),
            PlanStep.Act("idle_sit", 2f),
        )

    /** Swat the toy, run after it, swat again. */
    fun batChase(kind: ToyKind = ToyKind.BALL_B, hz: Int = 60) =
        case(
            "bat-chase",
            12f,
            ToyScene(kind, toyX = 100f, catX = 40f, hz = hz),
            PlanStep.Bat(0, 0.8f),
            PlanStep.Chase(0, 5f),
            PlanStep.Bat(0, 0.3f),
            PlanStep.Chase(0, 4f),
        )

    /** Belly-up hug and kick. */
    fun kick(kind: ToyKind = ToyKind.BALL_A, hz: Int = 60) =
        case(
            "kick",
            8f,
            ToyScene(kind, toyX = 100f, catX = 50f, hz = hz),
            PlanStep.Kick(0, 0.6f, 1.5f),
            PlanStep.Chase(0, 4f),
        )

    /** Stalk, wiggle, pounce. */
    fun pounce(kind: ToyKind = ToyKind.MOUSE, hz: Int = 60) =
        case(
            "pounce",
            6f,
            ToyScene(kind, toyX = 130f, catX = 40f, hz = hz),
            PlanStep.Pounce(0),
            PlanStep.Chase(0, 3f),
        )

    /** Carry the toy to a perch and set it down. */
    fun carryToBed(kind: ToyKind = ToyKind.FEATHER, hz: Int = 60) =
        case(
            "carry-bed",
            9f,
            ToyScene(kind, toyX = 70f, catX = 20f, hz = hz),
            PlanStep.PickUp(0),
            PlanStep.Carry(36f),
            PlanStep.Hop(SurfaceId.BED, 36f),
            PlanStep.SetDown,
            PlanStep.Act("idle_sit", 1f),
        )

    /** Every golden case. */
    fun all(): List<Case> =
        listOf(
            pickup(),
            carryTurn(),
            tossFloor(),
            tossCounter(),
            batChase(),
            kick(),
            pounce(),
            carryToBed(),
        )
}
