package com.lifemax.rpg.core.economy

import java.text.NumberFormat
import java.util.Currency
import kotlin.math.max
import kotlin.math.min

/**
 * Section 11.6: fixed bonuses that go through the cap. Unlike the scored paths they take rested XP
 * before the cap, so the pool is spent even when the cap pays nothing [D03].
 */
object Bonuses {
    private fun EconomySave.takeThenCap(
        xp: Double,
        ctx: EconomyContext,
    ): Pair<Double, EconomySave> {
        val (rested, after) = restedTake(xp)
        return cap(rested, activities.dayXp(ctx.today, ctx.clock)) to after
    }

    private fun EconomySave.record(activity: Activity) = copy(activities = activities + activity)

    /**
     * NUTRI-1: 8 XP once a day when today's food log lands within 15% of the calorie goal. The day
     * is marked before the cap, and a bonus the cap refuses is not saved [D04].
     */
    fun nutrition(save: EconomySave, ctx: EconomyContext): Outcome<Unit> {
        val goal = max(0.0, save.calorieGoal.orZero)
        if (goal == 0.0) return save.unchanged()
        val day = ctx.today
        val marks = save.nutriDone ?: emptyMap()
        val opened = save.copy(nutriDone = marks)
        if ((marks[day] ?: 0.0).let { it != 0.0 && !it.isNaN() }) return opened.unchanged()
        val kcal = save.foodLog.filter { it.date == day }.sumOf { it.kcal.orZero }
        if (kcal < goal * 0.85 || kcal > goal * 1.15) return opened.unchanged()
        val (xp, paid) = opened.copy(nutriDone = marks + (day to 1.0)).takeThenCap(8.0, ctx)
        if (xp <= 0) return paid.unchanged()
        val activity =
            Activity(
                id = "nut_${ctx.now}",
                type = "nutrition",
                name = "Nutrition goal hit",
                icon = "heart",
                duration = 0.0,
                skill = "Nutrition",
                primary = "wellbeing",
                secondary = "physique",
                xp = xp,
                date = ctx.nowIso,
                notes = "${kcal.text()} kcal against a goal of ${goal.text()}",
                split = splitXp(xp, "wellbeing", "physique"),
            )
        return SavePipeline.save(paid.record(activity), ctx)
            .map {}
            .toast("Nutrition goal hit · +${xp.text()} XP")
    }

    /** The three cat interactions that make a day of care (CARE-1). */
    enum class Care(val key: String) {
        Feed("feed"),
        Play("play"),
        Pet("pet"),
    }

    /** CARE-1: 10 XP once a day when the cat was fed, played with and petted. */
    fun catCare(save: EconomySave, kind: Care, ctx: EconomyContext): Outcome<Unit> {
        val today = ctx.today
        val held = save.careDay?.takeIf { it.day == today } ?: CareDay(today)
        val day =
            when (kind) {
                Care.Feed -> held.copy(feed = true)
                Care.Play -> held.copy(play = true)
                Care.Pet -> held.copy(pet = true)
            }
        val marked = save.copy(careDay = day)
        if (!(day.feed && day.play && day.pet) || day.paid) return marked.unchanged()
        val (xp, paid) = marked.copy(careDay = day.copy(paid = true)).takeThenCap(10.0, ctx)
        if (xp <= 0) return paid.unchanged()
        val activity =
            Activity(
                id = "care_${ctx.now}",
                type = "catcare",
                name = "Cat care: fed, played, petted",
                icon = "paw",
                duration = 0.0,
                skill = "Cat Care",
                primary = "wellbeing",
                secondary = "social",
                xp = xp,
                date = ctx.nowIso,
                notes = "",
                split = splitXp(xp, "wellbeing", "social"),
            )
        return SavePipeline.save(paid.record(activity), ctx)
            .map {}
            .toast("Cat care complete · +${xp.text()} XP")
    }

    /** MONEY-1's answer. */
    data class MoneyResult(val ok: Boolean, val message: String)

    /**
     * MONEY-1: a deposit above the account's peak earns XP (5 to 25 a day, 50 a rolling week),
     * every sum a binary double in the spec's order (section 19).
     */
    fun recordMoney(
        save: EconomySave,
        type: String,
        amount: Double,
        note: String,
        ctx: EconomyContext,
    ): Outcome<MoneyResult> {
        val amt = jsRound(amount * 100) / 100
        if (!amt.isFinite() || amt <= 0 || amt > 1e12)
            return save.result(MoneyResult(false, "Enter a valid amount"))
        if (type != "deposit" && type != "withdraw")
            return save.result(MoneyResult(false, "Choose a transaction type"))
        val money = save.money ?: Money()
        val balance = money.balance
        if (type == "withdraw" && amt > balance)
            return save.result(MoneyResult(false, "Not enough in the account"))
        val deposit = type == "deposit"
        var account = money
        var after = save
        var xp = 0.0
        val cleanNote = note.trim().take(60)
        val txId = "tx_${ctx.now}"
        var actId: String? = null
        if (deposit) {
            val peak = max(money.peakBalance.orZero, balance)
            val net = max(0.0, balance + amt - peak)
            account = account.copy(peakBalance = max(peak, balance + amt))
            val weekStart = ctx.clock.daysAgo(6)
            val moneyRecords = save.activities.filter { it.type == "money" }
            val weekXp =
                moneyRecords.filter { ctx.clock.dayOf(it.date) >= weekStart }.sumOf { it.xp.orZero }
            val today = save.activities.onDay(ctx.today, ctx.clock)
            val doneToday =
                today.any {
                    it.type == "money" || it.link == "business.transfer-money-into-savings"
                }
            if (!doneToday && net >= Eco.DEPOSIT_MIN) {
                xp =
                    min(
                        min(Eco.DEPOSIT_CAP_DAY, max(5.0, jsRound(net / 10))),
                        max(0.0, Eco.DEPOSIT_CAP_WEEK - weekXp),
                    )
                if (xp > 0)
                    after.takeThenCap(xp, ctx).let { (capped, spent) ->
                        xp = capped
                        after = spent
                    }
                else xp = 0.0
            }
            if (xp > 0) {
                actId = "act_${ctx.now}"
                after =
                    after.record(
                        Activity(
                            id = actId,
                            type = "money",
                            name = "Deposit " + currency(amt, ctx),
                            icon = "coin",
                            duration = 0.0,
                            skill = "Saving",
                            primary = "business",
                            secondary = "discipline",
                            xp = xp,
                            date = ctx.nowIso,
                            notes = cleanNote,
                            split = splitXp(xp, "business", "discipline"),
                        )
                    )
            }
        }
        val transaction = MoneyTransaction(txId, type, amt, cleanNote, ctx.nowIso, actId)
        val recorded =
            after.copy(money = account.copy(transactions = account.transactions + transaction))
        val message =
            if (deposit) "Deposit recorded" + if (xp > 0) " · +${xp.text()} XP" else ""
            else "Withdrawal recorded"
        return SavePipeline.save(recorded, ctx).map { MoneyResult(true, message) }
    }

    /** UNDO-1, removing a money transaction: the transaction and its record go; the peak stays. */
    fun removeMoney(save: EconomySave, id: String, ctx: EconomyContext): Outcome<Boolean> {
        val money = save.money ?: return save.result(false)
        val transaction =
            money.transactions.firstOrNull { it.id == id } ?: return save.result(false)
        val removed =
            save.copy(
                money = money.copy(transactions = money.transactions - transaction),
                activities =
                    save.activities.filterNot {
                        transaction.actId != null && it.id == transaction.actId
                    },
            )
        return SavePipeline.save(removed, ctx).map { true }
    }

    /** US dollars in the device locale. */
    fun currency(amount: Double, ctx: EconomyContext): String =
        NumberFormat.getCurrencyInstance(ctx.locale)
            .apply { currency = Currency.getInstance("USD") }
            .format(amount)
}
