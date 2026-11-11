package com.lifemax.rpg.core.lifedirector.readmodels

import com.lifemax.rpg.core.lifedirector.readmodels.ReadModelsReplay.Mutation
import com.lifemax.rpg.core.save.JsonString
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mutation spot checks for the read models: each plausible porting mistake, put in place of one
 * read model or one exported word, must make the golden replay fail in the families it should.
 * (Source mutations of the native code itself were also run by hand for WP-LD8a: spec section 9.)
 */
class ReadModelsMutationTest {
    private val cases = ReadModelsReplay.golden.cases
    private val ALL = ReadModelsGoldenTest.FAMILY_COUNTS.keys

    private fun assertCaught(families: Set<String>, least: Int, mutation: Mutation) {
        val found =
            cases
                .filter { it.family in families }
                .count { ReadModelsReplay.difference(it, mutation) != null }
        assertTrue("only $found cases noticed the mutation, wanted $least", found >= least)
    }

    private val words = ReadModelWords.packaged

    private fun words(
        late: Double = words.lateSeconds,
        ending: Double = words.endingSeconds,
        soon: Double = words.soonSeconds,
        phrases: Map<String, String> = words.phrases,
        kindClass: Map<String, String> = words.kindClass,
        dayNames: List<String> = words.dayNames,
    ) =
        ReadModelWords(
            dayNames,
            kindClass,
            words.weekLegend,
            words.weekTicks,
            words.yieldNote,
            words.allFit,
            soon,
            ending,
            late,
            phrases,
            words.attributeColours,
            words.fallbackColour,
        )

    /** "Late" from the eleventh minute instead of the tenth (`>` for `>=`). */
    @Test
    fun lateOneSecondLaterIsNoticed() =
        assertCaught(setOf("now"), 10, Mutation(words = words(late = words.lateSeconds + 1)))

    @Test
    fun wrappingUpOneSecondEarlierIsNoticed() =
        assertCaught(setOf("now"), 5, Mutation(words = words(ending = words.endingSeconds - 1)))

    @Test
    fun startingSoonOneSecondLaterIsNoticed() =
        assertCaught(setOf("now"), 10, Mutation(words = words(soon = words.soonSeconds + 1)))

    /** A retyped phrase ("Free" for the page's "Free time"). */
    @Test
    fun aRetypedPhraseIsNoticed() =
        assertCaught(
            setOf("scenario"),
            300,
            Mutation(words = words(phrases = words.phrases + ("freeTime" to "Free"))),
        )

    /** Decompression counted as downtime, not as travel. */
    @Test
    fun aKindInTheWrongClassIsNoticed() =
        assertCaught(
            setOf("scenario"),
            50,
            Mutation(words = words(kindClass = words.kindClass + ("DECOMPRESS" to "rest"))),
        )

    /** Day names starting on Monday. */
    @Test
    fun dayNamesFromMondayAreNoticed() =
        assertCaught(
            setOf("scenario"),
            250,
            Mutation(words = words(dayNames = words.dayNames.drop(1) + words.dayNames.first())),
        )

    /** Free time only from half an hour (RM-9 says a quarter hour). */
    @Test
    fun freeTimeFromHalfAnHourIsNoticed() =
        assertCaught(
            setOf("scenario", "life"),
            20,
            Mutation(
                blocks = { m, d ->
                    m.dayBlocks(d).filter {
                        !(it.session == null &&
                            it.code == JsonString("FREE") &&
                            it.endEpoch - it.startEpoch < 1800)
                    }
                }
            ),
        )

    /** A meal's own start lost when it is clipped at midnight. */
    @Test
    fun aClippedMealsStartIsNoticed() =
        assertCaught(
            setOf("midnight"),
            10,
            Mutation(
                blocks = { m, d ->
                    m.dayBlocks(d).map {
                        if (it.mealStartEpoch == null) it
                        else
                            DayBlock(
                                it.startEpoch,
                                it.endEpoch,
                                it.code,
                                it.kind,
                                it.title,
                                it.refId,
                                it.startEpoch,
                                it.clipped,
                                it.session,
                            )
                    }
                }
            ),
        )

    /** The timer counted as running while it is paused. */
    @Test
    fun aPausedTimerCountedAsRunningIsNoticed() =
        assertCaught(
            setOf("focus"),
            10,
            Mutation(
                now = { m ->
                    val n = m.nowState()
                    val f = m.focus()
                    n.copy(
                        running =
                            n.running ||
                                (f != null &&
                                    n.current?.session != null &&
                                    com.lifemax.rpg.core.lifedirector.PageValues.same(
                                        com.lifemax.rpg.core.lifedirector.PageValues.member(
                                            f,
                                            "id",
                                        ),
                                        n.current.session.instanceId,
                                    ))
                    )
                }
            ),
        )

    /** The next block allowed to be a session already done. */
    @Test
    fun aDoneSessionAsNextIsNoticed() =
        assertCaught(
            ALL,
            3,
            Mutation(
                now = { m ->
                    val n = m.nowState()
                    val tl = m.liveTimeline()
                    val cur =
                        n.current?.let { c ->
                            tl.firstOrNull {
                                it.startEpoch == c.startEpoch && it.endEpoch == c.endEpoch
                            }
                        }
                    val after = cur?.endEpoch ?: m.nowEpoch
                    n.copy(
                        next =
                            tl.firstOrNull {
                                it !== cur &&
                                    it.startEpoch >= after - 60 &&
                                    it.blockClass(m.words) != "free" &&
                                    it.isLive
                            }
                    )
                }
            ),
        )

    /** The week's shares rounded down instead of to the nearest hundredth. */
    @Test
    fun sharesRoundedDownAreNoticed() =
        assertCaught(
            setOf("seed", "life", "fuzz"),
            30,
            Mutation(
                week = { m ->
                    m.weekProjection()?.let { p ->
                        p.copy(
                            yield =
                                p.yield?.let { y ->
                                    y.copy(
                                        rows =
                                            y.rows.map {
                                                it.copy(done = Math.floor(it.done * 10) / 10)
                                            }
                                    )
                                }
                        )
                    }
                }
            ),
        )
}
