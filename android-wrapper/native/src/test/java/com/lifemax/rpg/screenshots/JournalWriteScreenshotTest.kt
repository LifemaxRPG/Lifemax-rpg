package com.lifemax.rpg.screenshots

import com.lifemax.rpg.feature.journal.GeneratedJournal
import com.lifemax.rpg.feature.journal.domain.CatchUpUi
import com.lifemax.rpg.feature.journal.domain.DayEntry
import com.lifemax.rpg.feature.journal.domain.JournalDraft
import com.lifemax.rpg.feature.journal.presentation.body.EntryEditor
import com.lifemax.rpg.feature.journal.presentation.body.JournalBodyCallbacks
import com.lifemax.rpg.feature.journal.presentation.body.JournalEntryPage
import com.lifemax.rpg.screenshots.JournalBodyShots.largeText
import com.lifemax.rpg.screenshots.JournalBodyShots.shot
import org.junit.Rule
import org.junit.Test

/**
 * Phase 2, writing in the native Journal (library.journal.write.v1): the notebook field of a day's
 * reflection and of a review's answer as they are typed in (empty, short with the live count, long
 * enough to pay, claimed), the catch-up's field under the welcome card, the whole screen with words
 * in the draft. The whole screen and the XP flying to the top bar are
 * JournalWriteScreenScreenshotTest; the static fields of phase 1 (JournalDayPageScreenshotTest) are
 * unchanged.
 */
class JournalWriteScreenshotTest {
    @get:Rule val paparazzi = JournalBodyShots.paparazzi()

    private val words = GeneratedJournal.open("native-today").write!!.catchUp
    private val today = GeneratedJournal.day("native-today")

    private fun editor(draft: JournalDraft? = null, catchUp: CatchUpUi = CatchUpUi()) =
        EntryEditor(draft, words, catchUp, false, { _, _ -> }, {}, {}, {})

    private fun draftOf(text: String, key: String = today.key) =
        JournalDraft(key, text, today.reflect.text, typed = true)

    private fun box(
        day: DayEntry,
        draft: JournalDraft?,
        light: Boolean = false,
        scrolledDp: Int = 340,
        catchUp: CatchUpUi = CatchUpUi(),
    ) =
        paparazzi.shot(light, scrolledDp) {
            JournalEntryPage(day, null, JournalBodyCallbacks(editor = editor(draft, catchUp)))
        }

    private fun fresh(text: String = "", awarded: Boolean = false) =
        today.copy(
            reflect =
                today.reflect.copy(
                    text = text,
                    awarded = awarded,
                    meter = if (awarded) 100f else 0f,
                )
        )

    /** Nothing written: the page's placeholder on the lines, an empty meter, the short counter. */
    @Test
    fun emptyDark() {
        val day = fresh()
        box(day, null)
    }

    /** Typing: 33 characters, the counter counting them, the meter most of the way. */
    @Test
    fun typingShortDark() {
        val day = fresh()
        box(day, draftOf("Slept badly, but the run helped."))
    }

    @Test
    fun typingShortLight() {
        val day = fresh()
        box(day, draftOf("Slept badly, but the run helped."), light = true)
    }

    /** Long enough to pay: the green counter "Saves for +15 XP" and the full meter. */
    @Test fun longEnoughDark() = box(fresh(), draftOf(LONG))

    /** Several lines: the field grows and the lines follow the text. */
    @Test fun severalLinesDark() = box(fresh(), draftOf("$LONG\n\nA second paragraph.\n$LONG"))

    /** Paid: the claimed counter in green, the full meter, the words kept on their lines. */
    @Test fun claimedDark() = box(fresh(LONG, awarded = true), null)

    /** An older day cannot pay. */
    @Test
    fun oldDayDark() {
        val day = fresh().let { it.copy(reflect = it.reflect.copy(canPay = false)) }
        box(day, draftOf("An older day, written about later."))
    }

    @Test
    fun largeTextTyping() {
        paparazzi.largeText()
        box(fresh(), draftOf(LONG), scrolledDp = 700)
    }

    /** A review's answer box, typed in. */
    @Test
    fun reviewAnswerDark() {
        val week = GeneratedJournal.review("native-week")
        paparazzi.shot(false, 560) {
            JournalEntryPage(
                week,
                null,
                JournalBodyCallbacks(
                    editor =
                        editor(JournalDraft(week.key, "Lights out by eleven.", week.answer, true))
                ),
            )
        }
    }

    /** The one-sentence catch-up opened under the welcome card's chips. */
    @Test
    fun catchUpDark() {
        val day = GeneratedJournal.day("native-welcome2")
        paparazzi.shot(false, 330) {
            JournalEntryPage(
                day,
                null,
                JournalBodyCallbacks(editor = editor(null, CatchUpUi(open = true))),
            )
        }
    }

    @Test
    fun catchUpLight() {
        val day = GeneratedJournal.day("native-welcome2")
        paparazzi.shot(true, 330) {
            JournalEntryPage(
                day,
                null,
                JournalBodyCallbacks(editor = editor(null, CatchUpUi(open = true, sending = true))),
            )
        }
    }

    private companion object {
        const val LONG =
            "Reflecting on the whole day: the morning study block went well and I kept my phone away."
    }
}
