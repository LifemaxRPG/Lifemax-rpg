package com.lifemax.rpg.feature.pet.domain

import com.lifemax.rpg.core.designsystem.feedback.HapticScale

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
data class PetCatLook(val name: String, val coat: String, val stage: Int, val weak: Boolean)

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
    /**
     * The panels (`pet.panels.v1`): present only when the page sends them to a screen that asked.
     */
    val panels: PetPanels? = null,
    /** How the cats take touch, from a page that said `pet.touch.v1`; null from an older page. */
    val touch: PetTouch? = null,
)

/**
 * How a cat takes touch, in the page's numbers: [level] is the trust level (0 to 5), [satMul] its
 * trait's share of petting saturation, [trust] (0 to 1) how much it trusts the owner's hand, and
 * [sat] the saturation it holds now (0 to 1.2: ears go back at 0.65, the cat leaves past 0.85).
 */
data class PetTouchProfile(val level: Int, val satMul: Double, val trust: Double, val sat: Double)

/** The day's petting and playing counts (the page counts three pets and two plays a day). */
data class PetCounts(val pets: Int, val petsMax: Int, val plays: Int, val playsMax: Int)

/**
 * What a screen with `pet.touch.v1` is told to let the cats answer a finger: how each cat of
 * [PetProjection.cats] takes touch, the day's counts, and the owner's haptic strength (null with
 * haptics off: the buzzes play nothing).
 */
data class PetTouch(
    val profiles: List<PetTouchProfile>,
    val counts: PetCounts,
    val haptics: HapticScale?,
    val words: PetTouchWords,
)

/**
 * The page's words for the touch parts of the screen: the full-screen room's [closeRoom] chip, and
 * [countsLine] under the card ("Petted 2 of 3 · Played 1 of 2 today"; empty before the day's
 * first).
 */
data class PetTouchWords(val closeRoom: String, val countsLine: String)

/**
 * What a finished touch tells the page (the `touch` command's `stroke`): cat [cat] was held for
 * [heldMs]; [left] says whether it got up and went on its own (`over`: had enough, `shy`: a wary
 * cat) or stayed (empty); [ignored]: the owner kept on well after the ears went back; [spam]: this
 * was the fourth touch in three seconds; [keepSleep]: the cat slept through it.
 */
data class PetStroke(
    val cat: Int,
    val heldMs: Long,
    val left: String,
    val ignored: Boolean,
    val spam: Boolean,
    val keepSleep: Boolean,
)
