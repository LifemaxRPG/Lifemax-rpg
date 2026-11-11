package com.lifemax.rpg.core.lifedirector.hostadapters

import com.lifemax.rpg.core.economy.EconomyClock
import com.lifemax.rpg.core.economy.LifeDirector
import com.lifemax.rpg.core.economy.Storage
import com.lifemax.rpg.core.lifedirector.hostadapters.AdapterReplay.Mutation
import java.time.ZoneOffset
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mutation spot checks: each plausible porting mistake, put in front of the adapter, must make the
 * golden replay fail in the families it should, so the corpus is known to notice it. (Source
 * mutations of the adapter's own code were run as well: LIFE_DIRECTOR_ADAPTERS_SPEC section 9.)
 */
class AdapterMutationTest {
    private fun assertCaught(families: Set<String>, least: Int, mutation: Mutation) {
        val caught =
            AdapterGolden.cases
                .filter { it.family in families }
                .filter { AdapterReplay.difference(it, mutation) != null }
        assertTrue(
            "only ${caught.size} cases noticed the mutation, wanted $least",
            caught.size >= least,
        )
    }

    /** A port that answers like [inner] except where [change] says otherwise. */
    private open class Wrapped(val inner: HostPort) : HostPort by inner

    /** D1 answered the recommended way (the award dated in another zone than the device's). */
    @Test
    fun awardsDatedInUtcAreNoticed() =
        assertCaught(
            setOf("award", "xp", "bounty", "flow"),
            12,
            Mutation(context = { it.copy(clock = EconomyClock(it.clock.now, ZoneOffset.UTC)) }),
        )

    /** The week's bounds in the device's zone instead of the plan's (HA-9, HA-16). */
    @Test
    fun weekBoundsInTheDeviceZoneAreNoticed() =
        assertCaught(
            setOf("quests"),
            20,
            Mutation(
                port = { p ->
                    object : Wrapped(p) {
                        override fun readQuests(
                            nowEpoch: Double,
                            zone: String,
                            overlay: QuestOverlay,
                            revoked: Set<String>,
                        ) = inner.readQuests(nowEpoch, "UTC", overlay, revoked)
                    }
                }
            ),
        )

    /** A save that never fails (HA-14: the award is taken back whole when it does). */
    @Test
    fun savesThatNeverFailAreNoticed() =
        assertCaught(
            setOf("award", "bounty", "flow"),
            3,
            Mutation(context = { it.copy(storage = Storage.Writable) }),
        )

    /** Revoked events still counted toward a quest (HA-16). */
    @Test
    fun revokedEventsCountedAreNoticed() =
        assertCaught(
            setOf("quests", "fuzz"),
            1,
            Mutation(
                port = { p ->
                    object : Wrapped(p) {
                        override fun readQuests(
                            nowEpoch: Double,
                            zone: String,
                            overlay: QuestOverlay,
                            revoked: Set<String>,
                        ) = inner.readQuests(nowEpoch, zone, overlay, emptySet())
                    }
                }
            ),
        )

    /** A bounty paid for a period that has moved on (HA-15: `PERIOD_CHANGED`). */
    @Test
    fun bountiesForAnOldPeriodAreNoticed() =
        assertCaught(
            setOf("bounty", "flow"),
            4,
            Mutation(
                port = { p ->
                    object : Wrapped(p) {
                        override fun completeQuest(ref: LifeDirector.QuestRef?) =
                            inner.completeQuest(ref).let { r ->
                                if (r.reason == "PERIOD_CHANGED") r.copy(reason = null) else r
                            }
                    }
                }
            ),
        )

    /** Plan days read as never done (HA-17). */
    @Test
    fun planDaysNeverDoneAreNoticed() =
        assertCaught(
            setOf("plan"),
            8,
            Mutation(
                port = { p ->
                    object : Wrapped(p) {
                        override fun readPlanSessions(
                            nowEpoch: Double,
                            zone: String,
                            days: Double,
                        ) =
                            when (val r = inner.readPlanSessions(nowEpoch, zone, days)) {
                                is HostAnswer.Answer ->
                                    HostAnswer.Answer(r.value.map { it.copy(done = false) })
                                else -> r
                            }

                        override fun isPlanDayDone(key: String) = false
                    }
                }
            ),
        )

    /** Life Director's own activities handed back as logged elsewhere (HA-20). */
    @Test
    fun ownActivitiesHandedBackAreNoticed() =
        assertCaught(
            setOf("since", "flow"),
            3,
            Mutation(
                port = { p ->
                    object : Wrapped(p) {
                        override fun activitiesSince(sinceIso: String?) =
                            inner.activitiesSince(sinceIso).filter { it.type != "plan" }
                    }
                }
            ),
        )

    /** A meal block dated by the device's zone instead of the plan's (HA-23). */
    @Test
    fun mealBlocksDatedOnTheDeviceAreNoticed() =
        assertCaught(
            setOf("meals"),
            5,
            Mutation(
                port = { p ->
                    object : Wrapped(p) {
                        override fun mealOfBlock(block: MealBlock, zone: String) =
                            HostAnswer.Answer(
                                inner.scheduledMeal(
                                    MealRef(
                                        block.title,
                                        null,
                                        null,
                                        block.mealStartEpoch ?: block.startEpoch,
                                    )
                                )
                            )
                    }
                }
            ),
        )

    /** A preview dated by the device's today instead of the day asked for (HA-10, HA-11). */
    @Test
    fun previewsDatedTodayAreNoticed() =
        assertCaught(
            setOf("xp"),
            4,
            Mutation(
                port = { p ->
                    object : Wrapped(p) {
                        override fun xpFor(
                            type: String,
                            minutes: Double,
                            quality: Double,
                            dateKey: String?,
                            commit: Boolean,
                        ) = inner.xpFor(type, minutes, quality, null, commit)
                    }
                }
            ),
        )
}
