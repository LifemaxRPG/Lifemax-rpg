package com.lifemax.rpg.feature.journal.presentation.body

import com.lifemax.rpg.feature.journal.domain.HeatCell
import com.lifemax.rpg.feature.journal.domain.JournalListItem
import com.lifemax.rpg.feature.journal.domain.JournalReflect
import com.lifemax.rpg.feature.journal.domain.WelcomeChip

/*
 * The few choices the Journal's body makes itself, kept out of the drawing so they can be tested.
 * Each follows the page's own (47-journal-coach.js), from the words the page projects.
 */

/**
 * The reflection box's counter line, as jcCounterText chooses it for stored text: claimed, then too
 * old to pay, then long enough, then the short line. The phase 1 box is not typed in, so the short
 * line carries no live count.
 */
fun counterLine(r: JournalReflect): String = r.counterFor(r.text.trim().length, typing = false)

/** Whether the counter shows green (.jc-count.on): claimed, or long enough to pay. */
fun counterOn(r: JournalReflect): Boolean = r.counterOn(r.text.trim().length)

/** What a welcome chip asks for (§3.4's Phase 1 actions). */
sealed interface ChipTap {
    /** `claim {id}`: Pet / Claim (+10 XP) or Start fresh (+25 XP). */
    data class Claim(val id: String) : ChipTap

    /** `catchUp`: the one-sentence catch-up. */
    data object CatchUp : ChipTap

    /** `{view}`: a hand-over to another view ("Open Life Director"). */
    data class Open(val view: String) : ChipTap
}

/**
 * The tap a chip makes, as the page's JC_CHIP_DO maps it, or null for a claimed chip (the page
 * disables it) or one it does not know.
 */
fun chipTap(chip: WelcomeChip): ChipTap? =
    if (chip.done) null
    else
        when (chip.id) {
            "pet",
            "fresh" -> ChipTap.Claim(chip.id)
            "line" -> ChipTap.CatchUp
            "director" -> ChipTap.Open("life-director")
            else -> null
        }

/**
 * The month's heat grid in weeks of seven: [lead] empty places before the 1st (the page's void
 * cells), then the days; the last week is left short, as a CSS grid leaves it.
 */
fun heatRows(lead: Int, cells: List<HeatCell>): List<List<HeatCell?>> =
    (List<HeatCell?>(lead.coerceIn(0, 6)) { null } + cells).chunked(7)

/** Where the strip's [selected] tile is, or -1, for scrolling it into view. */
fun selectedIndex(items: List<JournalListItem>, selected: String?): Int =
    items.indexOfFirst {
        when (it) {
            is JournalListItem.Day -> it.key == selected
            is JournalListItem.Review -> it.key == selected
            is JournalListItem.Quiet -> false
        }
    }

/** What TalkBack says for a label the page words with a symbol in front ("↻ Swap" is "Swap"). */
internal fun spokenLabel(text: String) = text.trimStart { !it.isLetterOrDigit() }
