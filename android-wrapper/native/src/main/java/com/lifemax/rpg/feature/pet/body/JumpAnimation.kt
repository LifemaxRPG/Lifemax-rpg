package com.lifemax.rpg.feature.pet.body

/**
 * The clips of a floor hop as [Jump] moves through its phases: the crouch of the anticipation, the
 * leap row through the flight (its first frame rising, its falling frame from the apex on) and the
 * crouch again on landing with the squash, reporting the landing through [landed]. Split from
 * [CatBody] so the body stays readable; it keeps no state of its own and allocates nothing.
 *
 * @param play starts a clip the way the body does (a posture change squashes the springs).
 * @param landed reports [CatEventType.Arrived] for the step being played.
 */
internal class JumpAnimation(
    private val jump: Jump,
    private val player: ClipPlayer,
    private val springs: BodySprings,
    private val play: (Clip) -> Unit,
    private val landed: () -> Unit,
) {
    /** One sub-step of [dt] seconds of the hop's animation. */
    fun update(dt: Float) {
        when (jump.phase) {
            JumpPhase.ANTICIPATE -> {
                if (player.clip !== Clips.CROUCH) play(Clips.CROUCH)
                player.advance(dt, 1f / jump.anticipationSeconds)
            }
            JumpPhase.AIR -> {
                if (jump.launchedNow) {
                    play(Clips.LEAP)
                    springs.stretch(Tuning.Jump.LAUNCH_STRETCH)
                }
                player.seek(
                    if (jump.flightProgress < Tuning.Jump.APEX_FRAME_SWITCH) 0f else FALLING_PHASE
                )
            }
            JumpPhase.LAND -> {
                if (jump.landedNow) {
                    play(Clips.CROUCH)
                    player.seek(1f)
                    springs.squash(jump.impact)
                    landed()
                }
            }
            else -> Unit
        }
    }

    private companion object {
        /** Where in the leap row the falling frame starts. */
        const val FALLING_PHASE = 0.75f
    }
}
