package com.lifemax.rpg.core.economy

import java.time.LocalDate

/**
 * Section 11.7.3: the journal coach's once-only awards (COACH-1). Passive for streak and decay. The
 * owner's words are stored first, with a plain write; the reward is then paid in a save of its own,
 * so a failed save takes back the reward and never the words.
 */
object Coach {
    /** COACH-1: records a coach award (passive, uncapped); the callers save. */
    fun award(
        save: EconomySave,
        xp: Double,
        name: String,
        note: String,
        ctx: EconomyContext,
    ): EconomySave =
        save.credited(
            Activity(
                id = ctx.ids.uuid(),
                type = "coach",
                name = name,
                icon = "book",
                duration = 0.0,
                quality = 1.0,
                skill = "Reflection",
                primary = "wellbeing",
                secondary = "",
                xp = xp,
                date = ctx.nowIso,
                notes = note,
                split = listOf(XpShare("wellbeing", xp)),
                source = "coach",
            )
        )

    private fun EconomySave.journal(change: JournalCoach.() -> JournalCoach) =
        copy(journal = journal.change())

    /**
     * The plain write that stores the owner's words before any reward (`jcKeepWords`): no rebuild,
     * Sparks or Daily Hand. Words that cannot be stored stay in memory and the page says nothing
     * was saved. The value is whether they were stored.
     */
    private fun keepWords(save: EconomySave, ctx: EconomyContext): Outcome<Boolean> {
        val kept = save.copy(customRules = save.rules.byType)
        ctx.attempts?.attempted(ctx.storage == Storage.Writable)
        return if (ctx.storage == Storage.Writable) kept.result(true)
        else kept.result(false, SavePipeline.unsavedMessage(ctx))
    }

    /**
     * A reward paid in its own save (`jcPay`). [was] is the save after the words were stored, so a
     * failed save takes back the reward alone and says the words were kept ([kept], as "Reflection
     * saved") or, for a reward with no words of its own, that nothing was saved. The reward's toast
     * comes only after a stored save. The value is whether it was paid.
     */
    private fun paid(
        was: EconomySave,
        awarded: EconomySave,
        ctx: EconomyContext,
        toast: String,
        kept: String? = null,
    ): Outcome<Boolean> {
        val saved = SavePipeline.save(awarded, ctx)
        if (saved.value) return saved.toast(toast)
        return saved.then {
            if (kept == null) SavePipeline.takeBack(was, false, ctx)
            else was.result(false, "$kept · XP not saved (storage full?)")
        }
    }

    /**
     * COACH-1, a reflection: 15 XP for a reflection of 40+ characters, once for today and once for
     * yesterday. The caller has stored the words; a refused reflection changes nothing. The value
     * is whether it was paid.
     */
    fun reflect(
        save: EconomySave,
        day: String,
        text: String,
        ctx: EconomyContext,
    ): Outcome<Boolean> {
        val eligible = text.trim().length >= 40 && (day == ctx.today || day == ctx.clock.daysAgo(1))
        if (!eligible || save.journal.days[day] == true) return save.result(false)
        val awarded =
            award(
                save.journal { copy(days = days + (day to true)) },
                15.0,
                "Journal reflection",
                "",
                ctx,
            )
        return paid(save, awarded, ctx, "+15 XP · +2 bond · Reflection saved", "Reflection saved")
    }

    /** What a welcome-back card can pay. */
    enum class Claim(val key: String, val xp: Double, val title: String) {
        Pet("pet", 10.0, "Welcome back"),
        Fresh("fresh", 25.0, "New chapter"),
    }

    /** The one chip each welcome tier offers: 1 pet, 2 the catch-up line, 3 start fresh. */
    private val TIER_CHIP = mapOf(1 to "pet", 2 to "line", 3 to "fresh")

    /**
     * The quiet days before [day] (`jcGap`): days in a row, going back, with no training record and
     * no written reflection; 0 unless some record came before them.
     */
    fun quietDays(save: EconomySave, day: String, clock: EconomyClock): Int {
        val first = save.activities.minOfOrNull { clock.dayOf(it.date) } ?: return 0
        val trained =
            save.activities.filterNot { it.isPassive }.map { clock.dayOf(it.date) }.toSet()
        val start = LocalDate.parse(day)
        val back = { n: Int -> start.minusDays(n.toLong()).toString() }
        var gap = 0
        while (gap < 399) {
            val k = back(gap + 1)
            if (k in trained || !save.dayReflections[k].isNullOrBlank()) break
            gap++
        }
        return if (back(gap + 1) >= first) gap else 0
    }

    /**
     * Whether today's welcome card offers chip [key], not yet claimed (`jcOffered`): a welcome
     * record whose tier offers that chip, while 3 or more quiet days still lead to today. A wrong
     * or replayed request pays nothing.
     */
    fun offered(save: EconomySave, key: String, ctx: EconomyContext): Boolean {
        val welcome = save.journal.welcome[ctx.today] ?: return false
        return TIER_CHIP[welcome.tier] == key &&
            key !in welcome.claimed &&
            quietDays(save, ctx.today, ctx.clock) >= 3
    }

    /**
     * COACH-1, a welcome-back chip, once, and only the one today's card offers. The cat's own pat
     * is outside the economy. The value is whether it was paid.
     */
    fun claim(save: EconomySave, claim: Claim, ctx: EconomyContext): Outcome<Boolean> {
        if (!offered(save, claim.key, ctx)) return save.result(false)
        val welcome = save.journal.welcome.getValue(ctx.today)
        val claimed =
            save.journal {
                copy(
                    welcome =
                        this.welcome +
                            (ctx.today to welcome.copy(claimed = welcome.claimed + claim.key))
                )
            }
        val note = "Returned after ${welcome.gap.text()} days"
        return paid(
            save,
            award(claimed, claim.xp, claim.title, note, ctx),
            ctx,
            "+${claim.xp.text()} XP · ${claim.title}",
        )
    }

    /**
     * COACH-1, the catch-up note, asked only while today's card offers it (tier 2): a sentence of
     * 12+ characters joins today's reflection (not again when it already ends it, as after a failed
     * reward) and is stored first; then 20 XP is paid in its own save.
     */
    fun catchUp(save: EconomySave, sentence: String, ctx: EconomyContext): Outcome<Unit> {
        if (!offered(save, "line", ctx)) return save.unchanged()
        val line = sentence.trim()
        if (line.length < 12) return save.result(Unit, "Write a full sentence (12+ characters)")
        val today = ctx.today
        val welcome = save.journal.welcome.getValue(today)
        val had = save.dayReflections[today].orEmpty()
        val reflection =
            if (had == line || had.endsWith("\n$line")) had
            else ((if (had.isEmpty()) "" else "$had\n") + line).take(4000)
        val words =
            keepWords(save.copy(dayReflections = save.dayReflections + (today to reflection)), ctx)
        if (!words.value) return words.map {}
        val kept = words.save
        val awarded =
            award(
                kept.journal {
                    copy(
                        welcome =
                            this.welcome +
                                (today to welcome.copy(claimed = welcome.claimed + "line"))
                    )
                },
                20.0,
                "Catch-up note",
                "",
                ctx,
            )
        return paid(kept, awarded, ctx, "+20 XP · Catch-up saved", "Catch-up saved").map {}
    }

    /**
     * COACH-1, weekly and monthly reviews: the answer is stored on every call; 30 XP (weekly, 50+
     * characters) or 50 (monthly, 60+) once, in a save of its own.
     */
    fun saveReview(
        save: EconomySave,
        key: String,
        text: String,
        ctx: EconomyContext,
    ): Outcome<Unit> {
        val answer = text.trim().take(2000)
        val held = save.journal.reviews[key]
        val monthly = key.startsWith("M")
        val alreadyPaid = held?.paid == true
        val words =
            keepWords(
                save.journal {
                    copy(reviews = reviews + (key to JournalCoach.Review(answer, alreadyPaid)))
                },
                ctx,
            )
        if (!words.value || alreadyPaid || answer.length < if (monthly) 60 else 50)
            return words.map {}
        val kept = words.save
        val (xp, title) = if (monthly) 50.0 to "Monthly chronicle" else 30.0 to "Weekly review"
        val awarded =
            award(
                kept.journal {
                    copy(reviews = reviews + (key to JournalCoach.Review(answer, true)))
                },
                xp,
                title,
                "",
                ctx,
            )
        return paid(kept, awarded, ctx, "+${xp.text()} XP · Review saved", "Review saved").map {}
    }
}
