package com.lifemax.rpg.feature.pet.domain

import com.lifemax.rpg.feature.pet.art.RigMorph

/*
 * The Cat tab's projection `pet.projection.v1` (50-native-pet.js): what the page's Cat card shows
 * for the cat whose card it is, with the cats native draws in the room. Every word is the page's
 * (petStatusData, petGrowData, petFavsData and the page's care text); native decides nothing here,
 * it draws these. Colours are ARGB.
 */

/**
 * One cat of the house as the room needs it: its [name] and [coat] seed its habits, [stage] is its
 * growth stage (-1 grown, 0 to 5 a kitten) and [weak] its hunger (the weak rows).
 */
data class PetCatLook(
    val name: String,
    val coat: String,
    val stage: Int,
    val weak: Boolean,
    /** The cat's proportions; the art as drawn when the page sends none (`pet.rig.v1`). */
    val rig: RigMorph = RigMorph.NEUTRAL,
)

/** The bond bar: how far to the next level in percent, and its words ("Fond · 40% to Devoted"). */
data class PetBond(val pct: Int, val text: String)

/** A kitten's growth line: the label, "DAY 3 OF 30", how far in percent, and the small print. */
data class PetGrowth(val label: String, val day: String, val pct: Int, val text: String)

/** The first stat: when the last meal was, and the caption under it ("last meal"). */
data class PetMeal(val value: String, val label: String)

/** The Favorite place / Favorite toy row: the words, "Still deciding" included. */
data class PetFavourites(val place: String, val toy: String)

/** The Feed button: its label, and whether it reads as waiting (not hungry, or on vacation). */
data class PetFeed(val label: String, val wait: Boolean)

/** The "Care and settings" card: the page's two paragraphs, the vacation switch and the name. */
data class PetCare(
    val how: String,
    val bond: String,
    val vacationHint: String,
    val vacation: Boolean,
    val name: String,
    val nameMax: Int,
)

/** The labels the page puts on the screen's fixed parts. */
data class PetWords(
    val room: String,
    val play: String,
    val blink: String,
    val blinkHint: String,
    val careTitle: String,
    val vacationTitle: String,
    val nameLabel: String,
    val rename: String,
    val moreTitle: String,
    val moreText: String,
)

/**
 * A meal the page served to cat [cat] (0 the first, 1 the second), numbered [id] by the page and
 * [ago] milliseconds ago when this projection was made. The room plays each id once.
 */
data class PetFedCue(val cat: Int, val id: Long, val ago: Long)

/**
 * The page's toast while it shows; [id] counts the page's toasts, so the same words twice are two.
 */
data class PetNotice(val id: Long, val text: String)

data class PetProjection(
    val session: String,
    val sequence: Long,
    /** The cats of the house native draws: one or two, the first being the page's first cat. */
    val cats: List<PetCatLook>,
    /** Which of [cats] the card is about. */
    val focus: Int,
    val name: String,
    val eyebrow: String,
    /** The trust level, 0 to 5: the hearts lit. */
    val hearts: Int,
    val mood: String,
    val bond: PetBond,
    val growth: PetGrowth?,
    val lastMeal: PetMeal,
    val mealsToday: String,
    val daysTogether: String,
    val favourites: PetFavourites,
    val hint: String,
    val feed: PetFeed,
    val care: PetCare,
    /** The line about a second cat ("Ted and Lily share the house..."), or empty with one. */
    val together: String,
    val words: PetWords,
    val fedCues: List<PetFedCue>,
    val notice: PetNotice?,
    val tokens: Map<String, Long>,
    val light: Boolean,
)
