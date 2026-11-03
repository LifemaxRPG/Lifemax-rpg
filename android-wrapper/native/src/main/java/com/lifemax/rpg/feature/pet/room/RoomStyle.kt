package com.lifemax.rpg.feature.pet.room

/**
 * How the room is drawn: the classic flat room the web paints, or the 2.5D room (CT9) with its
 * furniture as small solids, a camera that sways a little, soft shadows and light.
 *
 * This is the one switch the room has. [CLASSIC] draws exactly what the room always drew (the
 * fidelity test and every existing screenshot are taken with it); [DEPTH25] turns everything on.
 * Each effect can be turned off on its own to judge it, and the style is a plain value: the live
 * room is handed one ([RoomScene]'s `style`; the stage and the host pass it down) and nothing else
 * decides.
 *
 * @property layers draw the furniture as pieces between the backdrop and the cats (the base of each
 *   room has the furniture removed). Everything below needs it.
 * @property parallax move everything with the camera sway by its depth: the wall a little, the
 *   floor more the nearer its row, each piece and cat by the depth it stands at.
 * @property volume render each standing piece as a small solid ([VolumeRaster]): the sway shows its
 *   sides and top, shaded from its own colours. Off, the pieces stay their flat painted sprites.
 * @property softShadows soft elliptical contact shadows under the cats and the furniture.
 * @property floorLight the windows' light lying on the floor, and the dark seam where wall meets
 *   floor.
 * @property rimLight a warm (day) or cool (night) edge on the cats' window side.
 * @property cameraFollow the room camera's pan feeds the sway: nearby furniture shifts a little
 *   against the far wall as the camera follows a cat, and each piece shows the side that faces the
 *   middle of the screen ([PanFollow]). Off, only the idle drift and the tilt move the camera.
 * @property idleDrift the camera's slow idle drift ([DepthSway]): the room swings a little on its
 *   own. The live rooms have it off (the "room shake" fix, PET_TAB_INVENTORY.md): whole-pixel steps
 *   of a drift read on a phone as the room shaking while the camera stands still, so there the room
 *   moves only when the camera pans (or the phone tilts).
 * @property level how strong the depth is ([DepthLevel]): every effect scales with it.
 * @property reducedMotion no sway at all: the room stays at rest (the owner's "reduce motion"
 *   setting), and only the rest picture of each piece is ever rendered.
 */
internal data class RoomStyle(
    val layers: Boolean = false,
    val parallax: Boolean = false,
    val volume: Boolean = false,
    val softShadows: Boolean = false,
    val floorLight: Boolean = false,
    val rimLight: Boolean = false,
    val reducedMotion: Boolean = false,
    val cameraFollow: Boolean = false,
    val idleDrift: Boolean = true,
    val level: DepthLevel = DepthLevel.SUBTLE,
) {
    /** True for any 2.5D effect: the room needs its layer art. */
    val depth: Boolean
        get() = layers

    companion object {
        /** The flat room the page paints; the default everywhere in this phase. */
        val CLASSIC = RoomStyle()

        /** The 2.5D room with every effect on. */
        val DEPTH25 =
            RoomStyle(
                layers = true,
                parallax = true,
                volume = true,
                softShadows = true,
                floorLight = true,
                rimLight = true,
            )

        /** [DEPTH25] without sway, for a phone that asks for reduced motion. */
        val DEPTH25_STILL = DEPTH25.copy(reducedMotion = true)

        /** [DEPTH25] at [level]. */
        fun depth25(level: DepthLevel) = DEPTH25.copy(level = level)

        /**
         * The style of the live rooms (the Overview card and the Cat tab): [DEPTH25] at [level]
         * with the camera's pan feeding the sway and no idle drift, or the rest picture only
         * ([DEPTH25_STILL]) with [reducedMotion]. A null [level] is the classic flat room (the
         * performance guard's last step).
         */
        fun live(level: DepthLevel?, reducedMotion: Boolean = false): RoomStyle =
            when {
                level == null -> CLASSIC
                reducedMotion -> depth25(level).copy(reducedMotion = true)
                else -> depth25(level).copy(cameraFollow = true, idleDrift = false)
            }
    }
}
