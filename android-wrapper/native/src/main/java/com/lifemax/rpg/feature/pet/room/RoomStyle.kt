package com.lifemax.rpg.feature.pet.room

/**
 * How the room is drawn: the classic flat room the web paints, or the 2.5D room (CT9) with its
 * furniture as layers, a little parallax, thickness, soft shadows and light.
 *
 * This is the one switch the room has. [CLASSIC] draws exactly what the room always drew (the
 * fidelity test and every existing screenshot are taken with it); [DEPTH25] turns everything on.
 * Each effect can be turned off on its own to judge it, and the style is a plain value: the live
 * room is handed one ([RoomScene]'s `style`; the stage and the host pass it down) and nothing else
 * decides.
 *
 * @property layers draw the furniture as sprites between the backdrop and the cats (the base of
 *   each room has the furniture removed). Everything below needs it.
 * @property parallax shift each depth plane by the camera sway, 1 to 6 art pixels.
 * @property thickness give the pieces marked thick a darker side face that shifts with the sway.
 * @property softShadows soft elliptical contact shadows under the cats and the furniture.
 * @property floorLight the windows' light lying on the floor, and the dark seam where wall meets
 *   floor.
 * @property rimLight a warm (day) or cool (night) edge on the cats' window side.
 * @property level how strong the depth is ([DepthLevel]): every effect scales with it.
 * @property reducedMotion no sway at all: the layers keep their rest positions (the owner's "reduce
 *   motion" setting).
 */
internal data class RoomStyle(
    val layers: Boolean = false,
    val parallax: Boolean = false,
    val thickness: Boolean = false,
    val softShadows: Boolean = false,
    val floorLight: Boolean = false,
    val rimLight: Boolean = false,
    val reducedMotion: Boolean = false,
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
                thickness = true,
                softShadows = true,
                floorLight = true,
                rimLight = true,
            )

        /** [DEPTH25] without sway, for a phone that asks for reduced motion. */
        val DEPTH25_STILL = DEPTH25.copy(reducedMotion = true)

        /** [DEPTH25] at [level]. */
        fun depth25(level: DepthLevel) = DEPTH25.copy(level = level)
    }
}
