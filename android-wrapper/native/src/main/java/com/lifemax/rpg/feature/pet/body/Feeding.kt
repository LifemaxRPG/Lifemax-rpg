package com.lifemax.rpg.feature.pet.body

/**
 * The plans for a cat at its bowl when the owner feeds it, as the page makes them (petFeedClick):
 * from wherever the cat is, over the room's surfaces to the floor in front of its bowl, turn to
 * face it, then eat and wash. The first cat eats at the left bowl facing right, the second at the
 * right bowl facing left (the page's EAT2), as in [HousePlans.eat].
 */
internal object Feeding {
    /** How long the meal and the wash after it last (the page's eat 7.5 s, groom 4.5 s). */
    const val EAT_SECONDS = 7.5f
    const val GROOM_SECONDS = 4.5f

    /**
     * What a cat that is not hungry does at the bowl: sit and wash (the page's idle_sit, groom).
     */
    private const val SNIFF_SECONDS = 1.6f
    private const val WASH_SECONDS = 3f
    private const val MAX_STEPS = 10

    /** Where cat [slot] stands to eat. */
    fun bowlX(slot: Int): Float = HousePlans.eatX(slot)

    /** Which way cat [slot] faces at its bowl. */
    fun facing(slot: Int): Int = if (slot == 0) 1 else -1

    /** The way to the bowl and a meal. */
    fun meal(body: CatBody, slot: Int): List<PlanStep> =
        toBowl(body, slot).also {
            it.add(PlanStep.Act("eat", EAT_SECONDS))
            it.add(PlanStep.Act("groom", GROOM_SECONDS))
        }

    /** The way to the bowl and a wash, for a cat that is full. */
    fun visit(body: CatBody, slot: Int): List<PlanStep> =
        toBowl(body, slot).also {
            it.add(PlanStep.Act("idle_sit", SNIFF_SECONDS))
            it.add(PlanStep.Act("groom", WASH_SECONDS))
        }

    private fun toBowl(body: CatBody, slot: Int): MutableList<PlanStep> {
        val steps = ArrayList<PlanStep>(MAX_STEPS)
        steps.addAll(body.routeTo(SurfaceId.FLOOR, bowlX(slot)))
        steps.add(PlanStep.Face(facing(slot)))
        return steps
    }
}
