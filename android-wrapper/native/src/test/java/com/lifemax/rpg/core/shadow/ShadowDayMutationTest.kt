package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.economy.Decay
import com.lifemax.rpg.core.economy.EconomyClock
import com.lifemax.rpg.core.economy.EconomyContext
import com.lifemax.rpg.core.economy.EconomySave
import com.lifemax.rpg.core.economy.Outcome
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP6d's comparison is not vacuous: native's Health sync and decay pass are made wrong on purpose,
 * one rule at a time (the decay rate, the grace days, each rest-day rule, the shield tick and its
 * spending, D28, the steps cap, the sleep table, the weight rounding, the seen marks' age, the
 * sync's clock, the rested rounding, the day key's zone, the boot's write), and the WP6d golden
 * actions and the real-page day corpus go through the transport again. Every action a mutation
 * changes must be an unexplained difference at the field the rule writes, never absorbed by the
 * allowlist.
 */
class ShadowDayMutationTest {
    private class Mutant(
        val name: String,
        val path: Regex,
        val change: (String, EconomySave, EntryRun, EconomyContext) -> EntryRun?,
    )

    private val decayPaths = Regex("(attrXP|decayTotal|decayApplied)\\.\\w+|decayLog.*|restedXP")

    private val mutants =
        listOf(
            decay("the decay rate is a point higher", decayPaths) { s, ctx ->
                val e = Decay.settings(s)
                rerun(s, s.copy(decay = e.copy(rate = e.rate!! + 1)), ctx)
            },
            decay("one grace day fewer", decayPaths) { s, ctx ->
                val e = Decay.settings(s)
                rerun(s, s.copy(decay = e.copy(grace = e.grace!! - 1)), ctx)
            },
            decay("a planned rest day does not count", Regex("$decayPaths|shield\\w+(\\..*)?")) {
                s,
                ctx ->
                Decay.apply(s, ctx.copy(isPlanRestDay = { false }))
            },
            decay("a rest day taken does not count", Regex("$decayPaths|shield\\w+(\\..*)?")) {
                s,
                ctx ->
                rerun(s, s.copy(restDays = emptyMap()), ctx)
            },
            decay(
                "a recovery session no longer makes a rest day",
                Regex("$decayPaths|shield\\w+(\\..*)?"),
            ) { s, ctx ->
                val renamed =
                    s.activities.map {
                        if (it.type == "recovery") it.copy(type = "recovery-") else it
                    }
                rerun(s, s.copy(activities = renamed), ctx)
            },
            decay("the day key is read in UTC", Regex("$decayPaths|shield\\w+(\\..*)?")) { s, ctx ->
                Decay.apply(s, ctx.copy(clock = EconomyClock(ctx.now, ZoneOffset.UTC)))
            },
            result("the shield tick does not move its mark", Regex("shieldTick")) { k, b, r, _ ->
                if (
                    !decayKind(k) || r.save.shields.tick == b.shields.tick || b.shields.tick == null
                )
                    null
                else r.withSave(r.save.copy(shields = r.save.shields.copy(tick = b.shields.tick)))
            },
            result("a covered day spends no shield", Regex("streakShields")) { k, b, r, _ ->
                val days = r.save.shields.days.orEmpty().size
                if (!decayKind(k) || days <= b.shields.days.orEmpty().size) null
                else
                    r.withSave(
                        r.save.copy(shields = r.save.shields.copy(count = r.save.shields.count + 1))
                    )
            },
            result("D28 fixed: the milestone never goes down", Regex("shieldMilestone")) {
                k,
                b,
                r,
                _ ->
                val was = b.shields.milestone
                if (!decayKind(k) || !(r.save.shields.milestone < was)) null
                else r.withSave(r.save.copy(shields = r.save.shields.copy(milestone = was)))
            },
            result("the rested XP banked is not rounded", Regex("restedXP")) { k, b, r, _ ->
                if (
                    !decayKind(k) ||
                        !(r.save.restedXp > b.restedXp.let { if (it.isNaN()) 0.0 else it })
                )
                    null
                else r.withSave(r.save.copy(restedXp = r.save.restedXp + 0.5))
            },
            health("a day of steps pays up to 16", Regex("activities\\[\\+\\d+]\\.xp")) { b, r ->
                newRecords(b, r) { it.type == "steps" && it.xp == STEPS_CAP }
                    ?.let { set ->
                        r.save.copy(
                            activities =
                                r.save.activities.map {
                                    if (it in set) it.copy(xp = it.xp + 1) else it
                                }
                        )
                    }
            },
            health("sleep of 7.5 to 9.5 hours pays 14", Regex("activities\\[\\+\\d+]\\.xp")) { b, r
                ->
                newRecords(b, r) { it.type == "sleep" && it.xp == BEST_SLEEP }
                    ?.let { set ->
                        r.save.copy(
                            activities =
                                r.save.activities.map {
                                    if (it in set) it.copy(xp = it.xp - 1) else it
                                }
                        )
                    }
            },
            health("a weight is rounded to whole pounds", Regex("weight")) { b, r ->
                val w = r.save.weight
                if (w == null || w == b.weight || w == Math.rint(w)) null
                else r.save.copy(weight = Math.rint(w))
            },
            health("seen marks are kept for 30 days", Regex("health\\.seen\\..+")) { b, r ->
                val pruned = b.health.seen - r.save.health.seen.keys
                if (pruned.isEmpty()) null
                else r.save.copy(health = r.save.health.copy(seen = r.save.health.seen + pruned))
            },
            health("the sync leaves its time unset", Regex("health\\.last")) { b, r ->
                if (r.save.health.last.let { it.isNaN() || it == b.health.last }) null
                else r.save.copy(health = r.save.health.copy(last = b.health.last))
            },
            result("the boot writes when nothing changed", Regex("saves\\.count")) { k, _, r, ctx ->
                if (k != "boot.decay" || (ctx.attempts == null)) null
                else {
                    ctx.attempts?.attempted(true)
                    r
                }
            },
        )

    @Test
    fun everyMutationIsReportedAtItsField() {
        val built = GoldenActions.build().filter { it.action.kind in KINDS }
        assertTrue(built.size >= MIN_GOLDEN)
        val missed = ArrayList<String>()
        for (m in mutants) {
            var changed = 0
            val mutation = EntryMutation { kind, before, run, ctx ->
                if (kind !in KINDS) run
                else m.change(kind, before, run, ctx)?.also { changed++ } ?: run
            }
            val session = ShadowGoldenCompareTest.session(mutation)
            val page = FakeShadowPage()
            for (part in page.base(built.first().action.before)) session.base(part, 0)
            // a boot.decay envelope per golden decay pass: the boot's pass is the same, then a
            // write
            for (b in built + bootDecays(built)) {
                GoldenActions.CONTENT.setup = b.setup
                session.observe(page.send(b.action), FakeShadowPage.T0)
            }
            val ledgers =
                listOf(session.ledger) + ShadowDayCorpusTest.replayAll(mutation).map { it.ledger }
            val different = ledgers.sumOf { it.different }
            println("day mutation '${m.name}': $changed actions changed, $different unexplained")
            if (changed == 0) missed += "${m.name}: changed nothing"
            if (different != changed) missed += "${m.name}: changed $changed, $different reported"
            for (l in ledgers) for (e in l.mismatches().filter { it.unexplained }) if (
                e.paths.none(m.path::matches)
            )
                missed += "${m.name}: ${e.kind} ${e.paths}"
            for (l in ledgers) for (e in l.mismatches()) if (
                e.allow.isNotEmpty() && e.unexplained.not()
            )
                missed += "${m.name}: absorbed by ${e.allow}"
        }
        assertEquals(missed.joinToString("\n"), emptyList<String>(), missed)
        assertTrue(mutants.size >= MIN_MUTANTS)
    }

    private fun decay(
        name: String,
        path: Regex,
        pass: (EconomySave, EconomyContext) -> Outcome<Boolean>,
    ) =
        Mutant(name, path) { kind, before, run, ctx ->
            if (!decayKind(kind)) null
            else {
                val out = pass(before, ctx)
                val returns = if (kind == "decay.apply") JsonBool.of(out.value) else JsonNull
                val next = EntryRun(out.save, returns, out.toasts)
                if (
                    next.save == run.save &&
                        next.toasts == run.toasts &&
                        next.returns == run.returns
                )
                    null
                else next
            }
        }

    private fun result(
        name: String,
        path: Regex,
        change: (String, EconomySave, EntryRun, EconomyContext) -> EntryRun?,
    ) = Mutant(name, path, change)

    private fun health(name: String, path: Regex, change: (EconomySave, EntryRun) -> EconomySave?) =
        Mutant(name, path) { kind, before, run, _ ->
            if (kind != "health.sync") null else change(before, run)?.let { run.withSave(it) }
        }

    /** The pass on [changed], with what the change touched put back as [before] had it. */
    private fun rerun(
        before: EconomySave,
        changed: EconomySave,
        ctx: EconomyContext,
    ): Outcome<Boolean> {
        val out = Decay.apply(changed, ctx)
        val back =
            out.save.copy(
                decay = before.decay,
                restDays = before.restDays,
                activities = before.activities,
            )
        return Outcome(out.value, back, out.toasts)
    }

    private fun newRecords(
        before: EconomySave,
        run: EntryRun,
        which: (com.lifemax.rpg.core.economy.Activity) -> Boolean,
    ): Set<com.lifemax.rpg.core.economy.Activity>? {
        val had = before.activities.map { it.id }.toSet()
        return run.save.activities.filter { it.id !in had && which(it) }.toSet().ifEmpty { null }
    }

    /**
     * Each golden decay pass also as the boot's: returns nothing, one plain write when it lost XP.
     */
    private fun bootDecays(built: List<GoldenActions.Built>): List<GoldenActions.Built> =
        ShadowDayGoldenTest.bootDecays(built)

    private fun EntryRun.withSave(save: EconomySave) = EntryRun(save, returns, toasts)

    private companion object {
        val KINDS = setOf("health.sync", "decay.apply", "boot.decay")
        const val MIN_MUTANTS = 12
        const val MIN_GOLDEN = 100
        const val STEPS_CAP = 15.0
        const val BEST_SLEEP = 15.0

        fun decayKind(kind: String) = kind == "decay.apply" || kind == "boot.decay"
    }
}
