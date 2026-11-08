package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.economy.EconomyContext
import com.lifemax.rpg.core.economy.EconomySave
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The comparison is not vacuous (ECONOMY_SHADOW_PLAN WP6b, mutation kill tests): native is made
 * wrong on purpose, one rule at a time, and the golden actions go through the transport again.
 * Every action the mutation changes must come out as an unexplained difference naming the field the
 * rule writes, and no allowlist entry may absorb it; actions it leaves alone stay matched.
 */
class ShadowMutationTest {
    /** A wrong rule: what it changes in native's result, and the path a difference must name. */
    private class Mutant(
        val name: String,
        val path: Regex,
        val change: (String, EconomySave, EntryRun, EconomyContext) -> EntryRun?,
    )

    private val mutants =
        listOf(
            Mutant("the cap table pays one more", Regex("activities\\[\\+1]\\.xp")) {
                _,
                before,
                run,
                _ ->
                val added = run.save.activities.drop(before.activities.size)
                if (added.isEmpty() || added[0].xp == 0.0) null
                else
                    run.withSave(
                        run.save.copy(
                            activities =
                                run.save.activities.dropLast(added.size) +
                                    added[0].copy(xp = added[0].xp + 1) +
                                    added.drop(1)
                        )
                    )
            },
            Mutant("the rested rate banks one more", Regex("restedXP")) { _, before, run, _ ->
                if (run.save.restedXp.isNaN() || run.save.restedXp == before.restedXp) null
                else run.withSave(run.save.copy(restedXp = run.save.restedXp + 1))
            },
            Mutant("D05 fixed: the pool spends from the capped amount", Regex("restedXP")) {
                _,
                before,
                run,
                _ ->
                if (!(run.save.restedXp < before.restedXp)) null
                else run.withSave(run.save.copy(restedXp = run.save.restedXp + 1))
            },
            Mutant("Sparks are not rounded", Regex("sparks")) { _, before, run, _ ->
                if (run.save.sparks.isNaN() || run.save.sparks == before.sparks) null
                else run.withSave(run.save.copy(sparks = run.save.sparks + 0.5))
            },
            Mutant("a toast says xp for XP", Regex("toasts\\[0]")) { _, _, run, _ ->
                val first = run.toasts.firstOrNull()
                if (first == null || "XP" !in first) null
                else
                    EntryRun(
                        run.save,
                        run.returns,
                        listOf(first.replace("XP", "xp")) + run.toasts.drop(1),
                    )
            },
            Mutant("the return value reports one more", Regex("returns\\.xp")) { _, _, run, _ ->
                val o = run.returns as? JsonObject
                val xp = o?.get("xp") as? JsonNumber
                if (o == null || xp == null) null
                else
                    EntryRun(
                        run.save,
                        JsonObject(o.members + ("xp" to JsonNumber(xp.value + 1))),
                        run.toasts,
                    )
            },
            Mutant("the attribute split credits Physique", Regex("attrXP\\.physique")) {
                _,
                before,
                run,
                _ ->
                if (run.save.attrXp == before.attrXp) null
                else
                    run.withSave(
                        run.save.copy(
                            attrXp =
                                run.save.attrXp + ("physique" to run.save.attrXp("physique") + 1)
                        )
                    )
            },
            Mutant("the skill cache gains one more", Regex("skillXP\\.\\*#[0-9a-f]{6}")) {
                _,
                before,
                run,
                _ ->
                val changed =
                    run.save.skillXp.keys.firstOrNull { run.save.skillXp[it] != before.skillXp[it] }
                if (changed == null) null
                else
                    run.withSave(
                        run.save.copy(
                            skillXp =
                                run.save.skillXp +
                                    (changed to run.save.skillXp.getValue(changed) + 1)
                        )
                    )
            },
            Mutant("one more write", Regex("saves\\.count")) { _, _, run, ctx ->
                ctx.attempts?.attempted(true)
                run
            },
            Mutant("a new record is dated a day later", Regex("activities\\[\\+1]\\.date")) {
                _,
                before,
                run,
                _ ->
                val added = run.save.activities.drop(before.activities.size).firstOrNull()
                val date = added?.date?.let { runCatching { Instant.parse(it) }.getOrNull() }
                if (added == null || date == null) null
                else
                    run.withSave(
                        run.save.copy(
                            activities =
                                run.save.activities.map {
                                    if (it === added)
                                        it.copy(date = date.plusSeconds(DAY_SECONDS).toString())
                                    else it
                                }
                        )
                    )
            },
            Mutant("a quest mark is lost", Regex("questDone\\.\\*#[0-9a-f]{6}")) { _, before, run, _
                ->
                val key = (run.save.questDone.keys - before.questDone.keys).firstOrNull()
                if (key == null) null
                else run.withSave(run.save.copy(questDone = run.save.questDone - key))
            },
            Mutant("a deposit is stored a cent more", Regex("money\\.tx\\[\\+1]\\.amount")) {
                _,
                before,
                run,
                _ ->
                val had = before.money?.transactions?.size ?: 0
                val money = run.save.money
                val added = money?.transactions?.drop(had)?.firstOrNull()
                if (money == null || added == null) null
                else
                    run.withSave(
                        run.save.copy(
                            money =
                                money.copy(
                                    transactions =
                                        money.transactions.map {
                                            if (it === added) it.copy(amount = it.amount + 0.01)
                                            else it
                                        }
                                )
                        )
                    )
            },
            Mutant("the shop forgets a perk", Regex("perksOwned(\\[.*)?")) { kind, before, run, _ ->
                val owned = run.save.perksOwned
                if (
                    kind != "spark.buy" ||
                        owned == null ||
                        owned == before.perksOwned ||
                        owned.isEmpty()
                )
                    null
                else run.withSave(run.save.copy(perksOwned = owned.drop(1)))
            },
        )

    @Test
    fun everyMutationIsReportedAtItsField() {
        val built = GoldenActions.build()
        val missed = ArrayList<String>()
        for (m in mutants) {
            var changed = 0
            val mutation = EntryMutation { kind, before, run, ctx ->
                m.change(kind, before, run, ctx)?.also { changed++ } ?: run
            }
            val session = ShadowGoldenCompareTest.session(mutation)
            val page = FakeShadowPage()
            for (part in page.base(built.first().action.before)) assertTrue(
                session.base(part, 0).ok
            )
            for (b in built) session.observe(page.send(b.action), FakeShadowPage.T0)
            val l = session.ledger
            val unexplained = l.mismatches().filter { it.unexplained }
            if (changed == 0) missed += "${m.name}: changed nothing"
            if (l.different != changed)
                missed += "${m.name}: changed $changed actions, ${l.different} reported"
            for (e in unexplained) if (e.paths.none(m.path::matches))
                missed += "${m.name}: ${e.kind} ${e.paths}"
            assertEquals(m.name, built.size, l.compared)
        }
        assertEquals(missed.joinToString("\n"), emptyList<String>(), missed)
    }

    @Test
    fun aMutationThatChangesNothingLeavesEverythingMatched() {
        val built = GoldenActions.build()
        val session =
            ShadowGoldenCompareTest.session(
                EntryMutation { _, _, run, _ -> EntryRun(run.save, run.returns, run.toasts) }
            )
        val page = FakeShadowPage()
        for (part in page.base(built.first().action.before)) session.base(part, 0)
        for (b in built) session.observe(page.send(b.action), FakeShadowPage.T0)
        assertEquals(0, session.ledger.different)
        assertEquals(built.size, session.ledger.compared)
    }

    private fun EntryRun.withSave(save: EconomySave) = EntryRun(save, returns, toasts)

    private companion object {
        const val DAY_SECONDS = 86_400L
    }
}
