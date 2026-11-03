package com.lifemax.rpg.feature.pet.body

/**
 * The clip that plays the legs for [gait]: the carry row while a toy is in the mouth (walking or
 * running), the hungry walk when [weak], otherwise the gait's own stride.
 */
internal fun strideClipFor(gait: Gait, weak: Boolean, carrying: Boolean): Clip =
    when {
        carrying && (gait == Gait.WALK || gait == Gait.RUN) -> Clips.CARRY
        weak -> Clips.WALK_W
        gait == Gait.RUN -> Clips.RUN
        gait == Gait.FRONT -> Clips.WALK_FRONT
        gait == Gait.BACK -> Clips.WALK_BACK
        else -> Clips.WALK
    }
