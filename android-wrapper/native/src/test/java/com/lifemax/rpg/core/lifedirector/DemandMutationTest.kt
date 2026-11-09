package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.DemandReplay.Mutation
import com.lifemax.rpg.core.lifedirector.DemandReplay.cases
import com.lifemax.rpg.core.lifedirector.KernelGolden.array
import com.lifemax.rpg.core.lifedirector.KernelGolden.bool
import com.lifemax.rpg.core.lifedirector.KernelGolden.number
import com.lifemax.rpg.core.lifedirector.KernelGolden.string
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mutation spot checks for the demand layer: each plausible porting mistake, put in place of one
 * adapter, must make the golden replay fail in the families it should. (Source mutations of the
 * native code itself were also run by hand for WP-LD3: spec section 8.3.)
 */
class DemandMutationTest {
    private fun assertCaught(
        families: Set<String>,
        fn: String,
        least: Int,
        wrong: (List<JsonValue>) -> JsonValue,
    ) {
        val mutation = Mutation(mapOf(fn to wrong))
        val found =
            cases
                .filter { it.family in families && it.fn == fn }
                .filter { DemandReplay.difference(it, mutation) != null }
        assertTrue(
            "only ${found.size} cases noticed the mutation of $fn, wanted $least",
            found.size >= least,
        )
    }

    private fun num(v: JsonValue): Double = (v as JsonNumber).value

    /**
     * fdlibm's exponential (`StrictMath`, what older JavaScript engines give) instead of the
     * page's.
     */
    @Test
    fun fdlibmsExponentialIsNoticed() =
        assertCaught(setOf("float"), "exp", 100) { a -> number(StrictMath.exp(num(a[0]))) }

    /**
     * fdlibm's power (`StrictMath`): off in the last bit, and Java's special values, where `1` to
     * an infinite power is 1 and JavaScript says NaN.
     */
    @Test
    fun fdlibmsPowerIsNoticed() =
        assertCaught(setOf("float"), "pow", 100) { a ->
            number(StrictMath.pow(num(a[0]), num(a[1])))
        }

    /** Tags compared without case (the page compares UTF-16 text exactly, LDD-1). */
    @Test
    fun tagsComparedWithoutCaseAreNoticed() =
        assertCaught(setOf("tag"), "tagMatches", 10) { a ->
            fun low(v: JsonValue?) =
                (v as? JsonString)?.let { JsonString(it.value.lowercase()) } ?: v
            bool(ActivityTags.matches(low(a.getOrNull(0)), low(a.getOrNull(1))))
        }

    /**
     * Fresh ids that ignore the used ones (LDC-9): a re-plan would reuse a finished session's id.
     */
    @Test
    fun freshIdsThatIgnoreUsedIdsAreNoticed() =
        assertCaught(setOf("ids"), "freshIds", 8) { a ->
            array(CandidateIds.fresh(null, (a[1] as JsonString).value, num(a[2])).map(::string))
        }

    /** The cycle members sorted (the page lists them in the order Tarjan's components close). */
    @Test
    fun cycleMembersSortedAreNoticed() =
        assertCaught(setOf("cycle"), "findQuestCycles", 5) { a ->
            val quests = (a[0] as JsonArray).items.map(PlayerQuests::of)
            array(PlayerQuests.cycles(quests).sorted().map(::string))
        }
}
