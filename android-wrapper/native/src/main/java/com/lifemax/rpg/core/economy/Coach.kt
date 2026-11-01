package com.lifemax.rpg.core.economy

/** Section 11.7.3: the journal coach's once-only awards (COACH-1). Passive for streak and decay. */
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

    private fun paid(save: EconomySave, ctx: EconomyContext, toast: String): Outcome<Unit> =
        SavePipeline.save(save, ctx).map {}.toast(toast)

    /**
     * COACH-1, a reflection: 15 XP for a reflection of 40+ characters, once for today and once for
     * yesterday.
     */
    fun reflect(save: EconomySave, day: String, text: String, ctx: EconomyContext): Outcome<Unit> {
        val marked = save.journal { copy(days = days + (day to (days[day] ?: false))) }
        val eligible = text.trim().length >= 40 && (day == ctx.today || day == ctx.clock.daysAgo(1))
        if (!eligible || marked.journal.days[day] == true) return marked.unchanged()
        val awarded =
            award(
                marked.journal { copy(days = days + (day to true)) },
                15.0,
                "Journal reflection",
                "",
                ctx,
            )
        return paid(awarded, ctx, "+15 XP · +2 bond · Reflection saved")
    }

    /** What a welcome-back card can pay. */
    enum class Claim(val key: String, val xp: Double, val title: String) {
        Pet("pet", 10.0, "Welcome back"),
        Fresh("fresh", 25.0, "New chapter"),
    }

    /**
     * COACH-1, a welcome-back chip, once each per welcome. The cat's own pat is outside the
     * economy.
     */
    fun claim(save: EconomySave, claim: Claim, ctx: EconomyContext): Outcome<Unit> {
        val welcome = save.journal.welcome[ctx.today]
        if (welcome == null || claim.key in welcome.claimed) return save.unchanged()
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
            award(claimed, claim.xp, claim.title, note, ctx),
            ctx,
            "+${claim.xp.text()} XP · ${claim.title}",
        )
    }

    /**
     * COACH-1, the catch-up note: 20 XP for a sentence of 12+ characters on a welcome-back day,
     * kept in the day's reflection.
     */
    fun catchUp(save: EconomySave, sentence: String, ctx: EconomyContext): Outcome<Unit> {
        val line = sentence.trim()
        if (line.length < 12) return save.result(Unit, "Write a full sentence (12+ characters)")
        val today = ctx.today
        val welcome = save.journal.welcome[today]
        if (welcome == null || "line" in welcome.claimed) return save.unchanged()
        val reflection = save.dayReflections[today]?.let { "$it\n$line" } ?: line
        val noted =
            save
                .journal {
                    copy(
                        welcome =
                            this.welcome +
                                (today to welcome.copy(claimed = welcome.claimed + "line"))
                    )
                }
                .copy(dayReflections = save.dayReflections + (today to reflection.take(4000)))
        return paid(award(noted, 20.0, "Catch-up note", "", ctx), ctx, "+20 XP · Catch-up saved")
    }

    /**
     * COACH-1, weekly and monthly reviews: the answer is kept on every call; 30 XP (weekly, 50+
     * characters) or 50 (monthly, 60+) once.
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
        val pays = held?.paid != true && answer.length >= if (monthly) 60 else 50
        val kept =
            save.journal {
                copy(
                    reviews =
                        reviews + (key to JournalCoach.Review(answer, pays || held?.paid == true))
                )
            }
        if (!pays) return SavePipeline.save(kept, ctx).map {}
        val (xp, title) = if (monthly) 50.0 to "Monthly chronicle" else 30.0 to "Weekly review"
        return paid(award(kept, xp, title, "", ctx), ctx, "+${xp.text()} XP · Review saved")
    }
}
