package com.lifemax.rpg.feature.lifedirector.presentation.time

import com.lifemax.rpg.feature.lifedirector.domain.GuideTag
import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorProjection
import com.lifemax.rpg.feature.lifedirector.presentation.GuideRun

/** the page's key for set-up's guide (TOURS.setup), played over My time */
const val SETUP_GUIDE = "setup"

/**
 * The name tags set-up's guide pins on the mini sleep dial for the step on show ([run]): the page's
 * Bedtime and Wake up on the step whose mode is "mini" (tourTags in logic.js, placed by the page in
 * the dial's 360-unit square); none on any other step, guide or none. My time passes them to
 * [MiniSleepDial]'s tags.
 */
fun miniGuideTags(p: LifeDirectorProjection, run: GuideRun?): List<GuideTag> {
    val g = p.guide ?: return emptyList()
    if (run == null || g.key != SETUP_GUIDE || run.key != g.key) return emptyList()
    val step = g.steps.getOrNull(run.step) ?: return emptyList()
    return if (step.mode == MINI) step.tags else emptyList()
}

private const val MINI = "mini"
