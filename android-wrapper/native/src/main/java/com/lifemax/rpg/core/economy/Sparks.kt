package com.lifemax.rpg.core.economy

import kotlin.math.max

/** Section 12: Sparks, what they buy, and the shop's prices. */
object Sparks {
    const val THEME_PRICE = 300.0
    const val PERK_PRICE = 600.0
    const val REST_TOKEN_PRICE = 250.0
    const val SHIELD_PRICE = 250.0
    const val REROLL_PRICE = 50.0
    val TOY_PRICES =
        mapOf(
            "ball_b" to 150.0,
            "yarn" to 150.0,
            "feather" to 200.0,
            "spring" to 300.0,
            "wand" to 450.0,
        )

    private const val WEEK_MS = 7 * EconomyClock.DAY_MILLIS
    private const val MONTH_MS = 30 * EconomyClock.DAY_MILLIS

    /**
     * SPARK-1: one Spark per XP of every record not yet credited, marked so it is never credited
     * twice. Removing the record later takes nothing back [D14].
     */
    fun synced(save: EconomySave): EconomySave {
        var added = 0.0
        val marked =
            save.activities.map {
                if (it.sparked) it
                else {
                    added += max(0.0, it.xp.orZero)
                    it.copy(sparked = true)
                }
            }
        return save.copy(
            activities = marked,
            sparks = if (added != 0.0) save.sparks.orZero + added else save.sparks,
        )
    }

    /** SPARK-2. */
    fun balance(save: EconomySave): Double = max(0.0, jsRound(save.sparks.orZero))

    /** SPARK-3: spends [amount], or refuses with a toast; the value says whether it was spent. */
    fun spend(save: EconomySave, amount: Double): Outcome<Boolean> =
        if (balance(save) < amount) save.result(false, "Not enough Sparks")
        else save.copy(sparks = save.sparks.orZero - amount).result(true)

    /** What the shop sells (SPARK-4). */
    enum class Item {
        Theme,
        Perk,
        Toy,
        RestToken,
        ShieldRefill,
        Unknown,
    }

    /** SPARK-4. Owned themes and perks toggle on and off without spending. */
    fun buy(save: EconomySave, item: Item, id: String, ctx: EconomyContext): Outcome<Unit> {
        val shop =
            save.copy(
                perksOwned = save.perksOwned ?: emptyList(),
                themesOwned = save.themesOwned ?: emptyList(),
                toysBought = save.toysBought ?: emptyMap(),
            )
        val bought: Outcome<Boolean> =
            when (item) {
                Item.Theme -> {
                    val owned = id in shop.themesOwned.orEmpty()
                    (if (owned) shop.result(true) else spend(shop, THEME_PRICE)).mapSave { s, paid
                        ->
                        if (!paid) s
                        else
                            s.copy(
                                themesOwned =
                                    if (owned) s.themesOwned else s.themesOwned.orEmpty() + id,
                                theme = if (s.theme == id) null else id,
                            )
                    }
                }
                Item.Perk -> {
                    val owned = id in shop.perksOwned.orEmpty()
                    (if (owned) shop.result(true) else spend(shop, PERK_PRICE)).mapSave { s, paid ->
                        if (!paid) s
                        else
                            s.copy(
                                perksOwned =
                                    if (owned) s.perksOwned else s.perksOwned.orEmpty() + id,
                                perk = if (s.perk == id) null else id,
                            )
                    }
                }
                Item.Toy ->
                    if (shop.toysBought.orEmpty().containsKey(id)) return shop.unchanged()
                    else
                        spend(shop, TOY_PRICES[id] ?: Double.NaN)
                            .mapSave { s, paid ->
                                if (paid) s.copy(toysBought = s.toysBought.orEmpty() + (id to 1.0))
                                else s
                            }
                            .let { if (it.value) it.toast("Toy unlocked for your cat") else it }
                Item.RestToken ->
                    if (shop.restBoughtAt > ctx.now - WEEK_MS)
                        shop.result(false, "One rest token a week")
                    else
                        spend(shop, REST_TOKEN_PRICE).mapSave { s, paid ->
                            if (paid)
                                s.copy(
                                    restTokens = s.restTokens.orZero + 1,
                                    restBoughtAt = ctx.now.toDouble(),
                                )
                            else s
                        }
                Item.ShieldRefill ->
                    when {
                        shop.shields.boughtAt > ctx.now - MONTH_MS ->
                            shop.result(false, "One shield refill a month")
                        shop.shields.count >= 2 -> shop.result(false, "Shields are already full")
                        else ->
                            spend(shop, SHIELD_PRICE).mapSave { s, paid ->
                                if (paid)
                                    s.copy(
                                        shields =
                                            s.shields.copy(
                                                count = 2.0,
                                                boughtAt = ctx.now.toDouble(),
                                            )
                                    )
                                else s
                            }
                    }
                Item.Unknown -> shop.result(true)
            }
        val refusedEarly = !bought.value && item != Item.Unknown
        return if (refusedEarly) bought.map {}
        else bought.then { SavePipeline.save(it, ctx) }.map {}
    }
}

internal inline fun <T> Outcome<T>.mapSave(transform: (EconomySave, T) -> EconomySave): Outcome<T> =
    copy(save = transform(save, value))
