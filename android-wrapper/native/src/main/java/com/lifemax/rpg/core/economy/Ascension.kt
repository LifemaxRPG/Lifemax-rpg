package com.lifemax.rpg.core.economy

/** Section 14: an attribute at level 20 banks its XP in the character and earns stars. */
object Ascension {
    /**
     * ASCEND-1: at level 20 an attribute banks its XP in the character, restarts at level 1 and
     * earns 5% more for good, up to three stars. [confirmed] is the owner's answer to the
     * confirmation.
     */
    fun ascend(
        save: EconomySave,
        attr: String,
        ctx: EconomyContext,
        confirmed: Boolean = true,
    ): Outcome<Unit> {
        val attribute = Attribute.of(attr) ?: return save.unchanged()
        if (save.attrLevel(attr).level < 20)
            return save.result(Unit, "Reach level 20 in ${attribute.title} first")
        if (save.starCount(attr) >= 3)
            return save.result(Unit, "${attribute.title} already has three stars")
        if (!confirmed) return save.unchanged()
        val stars = save.starCount(attr) + 1
        val ascended =
            save.copy(
                ascended =
                    save.ascended + (attr to save.ascended[attr].orNaN.orZero + save.attrXp(attr)),
                attrStars = save.attrStars + (attr to stars),
            )
        return SavePipeline.save(ascended, ctx)
            .map {}
            .toast("${attribute.title} ascended · ${"★".repeat(stars.toInt())}")
    }
}
