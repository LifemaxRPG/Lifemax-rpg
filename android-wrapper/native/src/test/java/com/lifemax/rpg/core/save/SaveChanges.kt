package com.lifemax.rpg.core.save

import com.lifemax.rpg.core.economy.Activity
import com.lifemax.rpg.core.economy.CareDay
import com.lifemax.rpg.core.economy.DecaySettings
import com.lifemax.rpg.core.economy.EconomySave
import com.lifemax.rpg.core.economy.Money
import com.lifemax.rpg.core.economy.MoneyTransaction
import com.lifemax.rpg.core.economy.PlanChecks
import com.lifemax.rpg.core.economy.Quest
import com.lifemax.rpg.core.economy.Rules
import com.lifemax.rpg.core.economy.XpShare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** A change to every kind of tracked field: added, edited, removed and deleted values. */
internal object SaveChanges {
    private fun Double.plus1() = (if (isNaN()) 0.0 else this) + 1

    private val NEW_ACTIVITY =
        Activity(
            id = "act_codec_new",
            type = "reading",
            name = "Reading",
            icon = "📚",
            duration = 30.0,
            quality = 1.0,
            skill = "Reading",
            primary = "intelligence",
            secondary = "",
            xp = 24.0,
            date = "2026-10-28T12:00:00.000Z",
            notes = "q\"\\\n \ud800",
            split = listOf(XpShare("intelligence", 17.0), XpShare("discipline", 7.0)),
            sparked = true,
        )

    fun everything(s: EconomySave): EconomySave =
        s.copy(
            activities =
                s.activities.drop(if (s.activities.size >= 2) 1 else 0).map { a ->
                    a.copy(
                        sparked = !a.sparked,
                        xp = a.xp.plus1(),
                        split =
                            a.split.mapIndexed { i, share ->
                                if (i == 0) share.copy(xp = share.xp.plus1()) else share
                            },
                    )
                } + NEW_ACTIVITY,
            attrXp = s.attrXp.mapValues { it.value.plus1() },
            sparks = s.sparks.plus1(),
            restedXp = s.restedXp.plus1(),
            perk = if (s.perk == null) "scholar" else null,
            decay = if (s.decay == null) DecaySettings(on = false) else null,
            quests =
                s.quests.map { it.copy(xp = it.xp.plus1()) } +
                    Quest("quest_codec", "Codec quest", "daily", 15.0),
            questDone = s.questDone + ("quest_codec_2026-10-28" to "act_codec_new"),
            restDays = s.restDays + ("2026-12-01" to true),
            shields = s.shields.copy(tick = (s.shields.tick ?: 0.0) + 1),
            calorieGoal = 2000.0,
            careDay = if (s.careDay == null) CareDay("2026-10-28", feed = true) else null,
            money =
                (s.money ?: Money(start = 0.0)).let {
                    it.copy(
                        transactions =
                            it.transactions +
                                MoneyTransaction("tx_codec", "deposit", 125.0, "", "2026-10-28"),
                        peakBalance = 5.0,
                    )
                },
            planChecks =
                s.planChecks + ("2026-10-29" to PlanChecks(mapOf("w" to listOf(true, false)))),
            health = s.health.copy(seen = s.health.seen + ("hc_codec" to 1.0), message = "codec"),
            weight = if (s.weight == null) 150.5 else null,
            rerollsUsed = s.rerollsUsed + 1,
            journal = s.journal.copy(days = s.journal.days + ("2026-10-28" to true)),
            dayReflections = s.dayReflections + ("2026-10-28" to "words"),
            customRules =
                (s.customRules ?: emptyMap()) +
                    ("codec" to Rules.defaults.byType.getValue("reading").copy(base = 21.0)),
        )
}

/**
 * Checks that encoding kept everything the model does not write: walking the stored save beside
 * what the model wrote for it, every key the model does not write must come out identical, and
 * records with an `id` are followed by it.
 */
internal object UntouchedValues {
    fun check(stored: JsonObject, before: JsonObject, after: JsonObject, out: JsonObject) {
        walk(stored, before, after, out, "")
    }

    /** A key is the model's when it wrote it before or writes it now; every other key is kept. */
    private fun walk(
        stored: JsonValue,
        before: JsonValue?,
        after: JsonValue?,
        out: JsonValue?,
        path: String,
    ) {
        when {
            stored is JsonObject && before is JsonObject && out is JsonObject ->
                for ((key, value) in stored.members) {
                    val now = (after as? JsonObject)?.get(key)
                    if (key !in before.members && now == null) {
                        assertEquals("$path.$key kept", value, out[key])
                        assertTrue("$path.$key kept as text", sameText(value, out[key]))
                    } else if (key in out.members)
                        walk(value, before[key], now, out[key], "$path.$key")
                }
            stored is JsonArray && before is JsonArray && out is JsonArray -> {
                val afterItems = (after as? JsonArray)?.items.orEmpty()
                stored.items.forEachIndexed { i, item ->
                    val id = idOf(item) ?: return@forEachIndexed
                    val nth = stored.items.take(i).count { idOf(it) == id }
                    val now = out.items.filter { idOf(it) == id }.getOrNull(nth)
                    val next = afterItems.filter { idOf(it) == id }.getOrNull(nth)
                    if (now != null) walk(item, before.items.getOrNull(i), next, now, "$path[$id]")
                }
            }
        }
    }

    private fun idOf(v: JsonValue): String? = ((v as? JsonObject)?.get("id") as? JsonString)?.value

    /** Kept values keep their text too (a number read as `1.0` is written `1.0`). */
    private fun sameText(a: JsonValue?, b: JsonValue?): Boolean =
        a != null && b != null && JsonWriter.write(a) == JsonWriter.write(b)
}
