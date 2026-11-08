package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.quests.HandOdds
import com.lifemax.rpg.core.quests.QuestContent
import com.lifemax.rpg.core.save.JsonBool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The quest and Daily Hand comparison is not vacuous (ECONOMY_SHADOW_PLAN WP6c): each mutation
 * makes native's Daily Hand, class packs or custom quests wrong in one way (a number of the draw's
 * odds, a price, a template, a pack, the board's XP bounds, a step of behaviour) and replays every
 * golden envelope of the Daily Hand and the board ([HandGoldenActions]) with it. Each must leave
 * actions unexplained, and every unexplained difference must sit at the field the mutation is
 * about; none may be absorbed by the allowlist.
 */
class ShadowHandMutationTest {
    private val built = HandGoldenActions.build()
    private val content = QuestNative.PACKAGED.content!!

    private class Mutant(
        val name: String,
        val path: Regex,
        val quests: QuestNative,
        val mutation: EntryMutation = EntryMutation.NONE,
    )

    private fun with(change: (QuestContent) -> QuestContent) = QuestNative(change(content))

    private fun odds(change: HandOdds.() -> HandOdds) = QuestNative(content, HandOdds.PAGE.change())

    private val drawn = Regex("(dailyDraw|quests)(\\..*|\\[.*)?|dhMig|returns(\\..*)?|toasts.*")
    private val sparks = Regex("sparks|toasts.*|returns|dailyDraw(\\..*)?|quests(\\..*|\\[.*)?")

    private val mutants: List<Mutant> =
        listOf(
            Mutant("tier 1 weighs more", drawn, odds { copy(tier1 = 1.3) }),
            Mutant("tier 3 gains less from luck", drawn, odds { copy(tier3Luck = 0.9) }),
            Mutant("no luck means another score", drawn, odds { copy(luckFallback = 5600.0) }),
            Mutant("maintenance damps tier 3 less", drawn, odds { copy(maintenanceTier3 = 0.4) }),
            Mutant(
                "a reroll costs 40",
                sparks,
                with { it.copy(hand = it.hand.copy(rerollCost = 40)) },
            ),
            Mutant(
                "two free rerolls",
                sparks,
                with { it.copy(hand = it.hand.copy(freeRerolls = 2)) },
            ),
            Mutant(
                "a template pays one more",
                Regex("quests\\[.*\\]\\.xp"),
                with { c ->
                    c.copy(
                        hand =
                            c.hand.copy(
                                templates = c.hand.templates.map { it.copy(xp = it.xp + 1) }
                            )
                    )
                },
            ),
            Mutant(
                "a template checks another way",
                Regex("quests\\[.*\\]\\.verify"),
                with { c ->
                    c.copy(
                        hand =
                            c.hand.copy(
                                templates =
                                    c.hand.templates.map { it.copy(verify = it.verify + ":x") }
                            )
                    )
                },
            ),
            Mutant(
                "a class pack loses its last quest",
                Regex("quests(\\[.*|\\..*)?|classContent"),
                with { c ->
                    c.copy(classQuests = c.classQuests.mapValues { (_, q) -> q.dropLast(1) })
                },
            ),
            Mutant(
                "the starter quests lose one",
                Regex("quests(\\[.*|\\..*)?"),
                with { c -> c.copy(defaultQuests = c.defaultQuests.dropLast(1)) },
            ),
            Mutant(
                "a custom quest pays at most 499",
                Regex("quests\\[\\+?1?]\\.xp"),
                QuestNative(content, tuning = QuestNative.PACKAGED.tuning.copy(maxXp = 499.0)),
            ),
            Mutant(
                "an empty XP field means 26",
                Regex("quests\\[\\+?1?]\\.xp"),
                QuestNative(content, tuning = QuestNative.PACKAGED.tuning.copy(defaultXp = 26.0)),
            ),
            Mutant(
                "a reroll answers the opposite",
                Regex("returns"),
                QuestNative.PACKAGED,
                EntryMutation { kind, _, run, _ ->
                    if (kind != "hand.reroll" || run.returns !is JsonBool) run
                    else
                        EntryRun(
                            run.save,
                            JsonBool.of(!(run.returns as JsonBool).value),
                            run.toasts,
                        )
                },
            ),
            Mutant(
                "the deal shows a message",
                Regex("toasts.*"),
                QuestNative.PACKAGED,
                EntryMutation { kind, _, run, _ ->
                    if (kind != "hand.ensure") run
                    else EntryRun(run.save, run.returns, run.toasts + "Dealt")
                },
            ),
        )

    @Test
    fun everyMutationIsReportedAtItsField() {
        val missed = ArrayList<String>()
        for (m in mutants) {
            val l = ShadowQuestsGoldenTest.replay(built, m.mutation, m.quests) {}
            val unexplained = l.mismatches().filter { it.unexplained }
            if (l.different == 0) missed += "${m.name}: nothing reported"
            for (e in unexplained) if (
                e.paths.none(m.path::matches) && e.paths.none { it.startsWith("+") }
            )
                missed += "${m.name}: ${e.kind} ${e.paths}"
            assertTrue("${m.name}: no allowlist entry absorbs it", l.allowed == 0)
            assertEquals(m.name, built.size, l.compared)
        }
        assertTrue("at least a dozen mutations", mutants.size >= MIN_MUTANTS)
        assertEquals(missed.joinToString("\n"), emptyList<String>(), missed)
    }

    @Test
    fun theUnmutatedNativeAgreesEverywhere() {
        val l = ShadowQuestsGoldenTest.replay(built, EntryMutation.NONE, QuestNative.PACKAGED) {}
        assertEquals(0, l.different)
        assertEquals(built.size, l.same)
    }

    private companion object {
        const val MIN_MUTANTS = 12
    }
}
