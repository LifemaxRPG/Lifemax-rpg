package com.lifemax.rpg.feature.pet.presentation

import com.lifemax.rpg.feature.pet.domain.PetProjection
import com.lifemax.rpg.feature.pet.room.CameraDirector

/**
 * Which cat is selected, in one place: the page's focus (`PetProjection.focus`, what the stats card
 * shows and the Together card's pills switch). The camera frames that cat, and a finger on a cat in
 * the room asks the page for the same focus command the pills send, so the card, the camera and a
 * tap can never disagree: the page decides, the projection says.
 */
internal object CatSelection {
    /** The house cat the camera frames for [p], or [CameraDirector.NONE] with a single cat. */
    fun cameraCat(p: PetProjection): Int =
        if (p.cats.size >= 2 && p.focus in p.cats.indices) p.focus else CameraDirector.NONE

    /**
     * The focus to ask the page for after a finger landed on house cat [cat]: null when there is
     * nothing to switch (one cat, no switcher on the page, or that cat is already the focus).
     */
    fun tapped(p: PetProjection, cat: Int): Int? =
        if (p.cats.size == 2 && p.panels?.pair != null && cat in p.cats.indices && cat != p.focus)
            cat
        else null
}
