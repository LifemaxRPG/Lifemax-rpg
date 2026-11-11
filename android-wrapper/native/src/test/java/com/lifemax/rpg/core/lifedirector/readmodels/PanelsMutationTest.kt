package com.lifemax.rpg.core.lifedirector.readmodels

import com.lifemax.rpg.core.lifedirector.readmodels.PanelsReplay.Mutation
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mutation spot checks for the read models of part b: each plausible porting mistake, put in place
 * of one exported word or one read model, must make the golden replay fail in the families it
 * should. (Source mutations of the native code itself were also run: spec section 15.)
 */
class PanelsMutationTest {
    private val cases = PanelsReplay.cases

    private fun assertCaught(families: Set<String>, least: Int, mutation: Mutation) {
        val found =
            cases
                .filter { it.family in families }
                .count { PanelsReplay.difference(it, mutation) != null }
        assertTrue("only $found cases noticed the mutation, wanted $least", found >= least)
    }

    private val text =
        PanelWords::class.java.classLoader!!.getResourceAsStream(PanelWords.RESOURCE)!!.use {
            it.readBytes().toString(Charsets.UTF_8)
        }

    /** The packaged words with [from] replaced by [to] once (the text must change). */
    private fun words(from: String, to: String): PanelWords {
        val changed = text.replaceFirst(from, to)
        assertTrue("$from is not in the words", changed != text)
        return PanelWords.read(changed)
    }

    /** A retyped phrase: "x" for the page's "×" after a template's count. */
    @Test
    fun aRetypedGoalPhraseIsNoticed() =
        assertCaught(
            setOf("goals"),
            150,
            Mutation(words = words("\"times\": \"×\"", "\"times\": \"x\"")),
        )

    @Test
    fun aRetypedQuestStatusIsNoticed() =
        assertCaught(
            setOf("quests"),
            40,
            Mutation(
                words =
                    words(
                        "\"couldNotFit\": \"Could not fit\"",
                        "\"couldNotFit\": \"Could not fit.\"",
                    )
            ),
        )

    /** A 24-hour clock where the page was recorded with a 12-hour one (hour words only). */
    @Test
    fun aTwentyFourHourClockIsNoticed() =
        assertCaught(setOf("time"), 140, Mutation(words = words("\"h12\": true", "\"h12\": false")))

    @Test
    fun aRetypedFormLabelIsNoticed() =
        assertCaught(
            setOf("forms"),
            120,
            Mutation(
                words = words("\"unwind\": \"Unwind after (min)\"", "\"unwind\": \"Unwind (min)\"")
            ),
        )

    /** Appointments not draggable on the dial. */
    @Test
    fun aKindMissingFromTheDragKindsIsNoticed() =
        assertCaught(
            setOf("handles", "dst"),
            5,
            Mutation(words = words("\"APPOINTMENT\": 1", "\"APPOINTMENTS\": 1")),
        )

    /** Study before reading in the icon table: a reading goal shows the study icon. */
    @Test
    fun theIconTableInAnotherOrderIsNoticed() =
        assertCaught(
            setOf("forms"),
            100,
            Mutation(words = words("\"fitness\",\n    \"dumbbell\"", "\"fitness\",\n    \"star\"")),
        )

    @Test
    fun aRetypedSettingsTextIsNoticed() =
        assertCaught(
            setOf("settings"),
            100,
            Mutation(words = words("\"restTail\": \" of 3", "\"restTail\": \" out of 3")),
        )

    /** Move offers one time fewer (the first 47). */
    @Test
    fun oneMoveTimeFewerIsNoticed() =
        assertCaught(
            setOf("move"),
            20,
            Mutation(
                move = { p, id ->
                    when (val a = p.move(id)) {
                        is ReadAnswer.Value ->
                            ReadAnswer.Value(
                                (a.value as? MoveSheet.Move)?.let {
                                    it.copy(times = it.times.take(47))
                                } ?: a.value
                            )
                        else -> a
                    }
                }
            ),
        )

    /** Shifts sorted by start instead of by end. */
    @Test
    fun shiftsByStartAreNoticed() =
        assertCaught(
            setOf("shifts"),
            5,
            Mutation(
                shifts = { p, a, b ->
                    when (val r = p.shifts(a, b)) {
                        is ReadAnswer.Value ->
                            ReadAnswer.Value(
                                r.value.sortedBy {
                                    com.lifemax.rpg.core.lifedirector.PageValues.number(
                                        it.startEpoch
                                    )
                                }
                            )
                        else -> r
                    }
                }
            ),
        )

    /** Handles' lengths kept for spans under a quarter hour (no "too short"). */
    @Test
    fun tooShortSpansShownAreNoticed() =
        assertCaught(
            setOf("handles"),
            50,
            Mutation(words = words("\"tooShort\": \"Too short\"", "\"tooShort\": \"Too short!\"")),
        )
}
