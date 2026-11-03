package com.lifemax.rpg.feature.pet.presentation

import com.lifemax.rpg.core.designsystem.feedback.HapticScale
import com.lifemax.rpg.core.designsystem.feedback.PageBuzz
import com.lifemax.rpg.core.designsystem.feedback.play
import com.lifemax.rpg.feature.pet.body.Beat
import com.lifemax.rpg.feature.pet.body.Burst
import com.lifemax.rpg.feature.pet.body.TouchModel
import com.lifemax.rpg.feature.pet.body.TouchSink
import com.lifemax.rpg.feature.pet.domain.PetStroke

/**
 * Where a touch on the room goes beyond the room: the phone's vibrator at the owner's strength
 * ([haptics], null with haptics off, and then nothing buzzes), and the view model, which tells the
 * page what the touch came to and when the purr's sound is to start and stop.
 */
internal class PetTouchSink(
    private val vm: PetViewModel,
    private val buzz: PageBuzz?,
    private val haptics: () -> HapticScale?,
) : TouchSink {
    override fun burst(kind: Burst, x: Float, y: Float) = Unit // the room draws these itself

    /**
     * The page's own buzzes for the moments: a hand landing 8 ms, a heart 6, a level [10, 40, 10].
     */
    override fun buzz(beat: Beat) {
        when (beat) {
            Beat.DOWN -> buzz?.play(haptics(), DOWN_MS)
            Beat.HEART -> buzz?.play(haptics(), HEART_MS)
            Beat.LEVEL_UP -> buzz?.play(haptics(), LEVEL_ON_MS, LEVEL_GAP_MS, LEVEL_ON_MS)
        }
    }

    override fun purr(cat: Int, level: Float) = vm.purr(cat, level)

    override fun stroke(cat: Int, outcome: TouchModel.Outcome) {
        val left =
            when {
                outcome.over -> LEFT_OVER
                outcome.leftShy -> LEFT_SHY
                else -> ""
            }
        vm.stroke(
            cat,
            PetStroke(cat, outcome.heldMs, left, outcome.ignored, outcome.spam, outcome.keepSleep),
        )
    }

    private companion object {
        const val DOWN_MS = 8L
        const val HEART_MS = 6L
        const val LEVEL_ON_MS = 10L
        const val LEVEL_GAP_MS = 40L
        const val LEFT_OVER = "over"
        const val LEFT_SHY = "shy"
    }
}
