package com.lifemax.rpg.feature.journal

import com.lifemax.rpg.feature.journal.data.WebJournalRepository
import com.lifemax.rpg.feature.journal.domain.DayEntry
import com.lifemax.rpg.feature.journal.domain.JournalChrome
import com.lifemax.rpg.feature.journal.domain.JournalFrame
import com.lifemax.rpg.feature.journal.domain.OpenJournal
import com.lifemax.rpg.feature.journal.domain.ReviewEntry
import org.json.JSONObject

/**
 * The page's own Journal frames, as tools/gen_journal_fixtures.js records them from the real page
 * (fixtures/journal-NAME.json), decoded by the real decoder. Tests and screenshots draw from these
 * so the native side is checked against what the page sends, not against hand-made copies.
 */
internal object GeneratedJournal {
    /** Every frame the generator writes: the open ones, then the withheld ones. */
    val openNames =
        listOf(
            "today",
            "pastday",
            "notes",
            "blank",
            "care",
            "welcome1",
            "welcome1-claimed",
            "welcome2",
            "welcome3",
            "week",
            "month",
            "empty",
            "tip",
            "longstrip",
        )
    val withheldNames = listOf("locked", "locked-prompting", "locked-error", "notice")

    private fun text(file: String) =
        GeneratedJournal::class.java.getResource("/fixtures/$file")!!.readText()

    /** A fresh copy of journal-[name].json, for a test to bend. */
    fun json(name: String) = JSONObject(text("journal-$name.json"))

    /** The decoded frame; it throws if the decoder will not take what the page sent. */
    fun frame(name: String): JournalFrame =
        WebJournalRepository().apply { accept(json(name), true) }.state.value!!

    fun open(name: String): OpenJournal = (frame(name) as JournalFrame.Open).journal

    fun chrome(name: String): JournalChrome = frame(name).chrome

    fun day(name: String): DayEntry = open(name).entry as DayEntry

    fun review(name: String): ReviewEntry = open(name).entry as ReviewEntry

    /** journal-replies.json: each command's payload and the page's reply, by case name. */
    fun replies() = JSONObject(text("journal-replies.json"))

    /** journal-counter.json: the page's choice among the counter's lines for each case. */
    fun counter() = JSONObject(text("journal-counter.json"))
}
