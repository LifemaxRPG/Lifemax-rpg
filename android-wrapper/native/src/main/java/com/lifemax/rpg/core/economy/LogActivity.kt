package com.lifemax.rpg.core.economy

import kotlin.math.max
import kotlin.math.min

/** A Log draft as the form holds it: text fields (LOG-1). */
data class LogDraft(
    val type: String = "",
    val customName: String = "",
    val duration: String = "",
    val quality: String = "",
    val skill: String = "",
    val primary: String = "",
    val secondary: String = "",
    val notes: String = "",
    val opId: String? = null,
)

/** PREV-1. */
data class LogPreview(
    val total: Double,
    val primary: String,
    val secondary: String,
    val skill: String,
    val durationFactor: Double,
)

/** LOGAWARD-1: what a Log would pay, split and starred, with its reasons. */
data class LogAward(val xp: Double, val split: List<XpShare>, val message: String)

/** LOG-2's answer. */
data class LogResult(
    val ok: Boolean,
    val message: String,
    val xp: Double? = null,
    val duplicate: Boolean = false,
)

/** Section 11.1: logging an activity by hand. */
object LogActivity {
    val QUALITIES = listOf("0.8", "1", "1.1", "1.2")

    /** PREV-1: under 20 minutes a quality above 1 is ignored; a quality below 1 always applies. */
    fun preview(rules: Rules, draft: LogDraft): LogPreview {
        val rule = rules.of(draft.type)
        val minutes = max(0.0, jsNumber(draft.duration).orZero)
        val quality = jsNumber(draft.quality).or(1.0)
        val factor = durationFactor(minutes)
        val q = if (minutes < 20) min(1.0, quality) else quality
        return LogPreview(
            total = max(0.0, jsRound((rule.base * factor) * q)),
            primary = draft.primary.ifEmpty { rule.primary },
            secondary = draft.secondary, // D23: no fallback to the rule's secondary
            skill = draft.skill.ifEmpty { rule.skill }.trim().ifEmpty { rule.skill },
            durationFactor = factor,
        )
    }

    /** LOGAWARD-1: the pipeline for today, as a manual log, then the split and the stars. */
    fun award(
        save: EconomySave,
        draft: LogDraft,
        ctx: EconomyContext,
        peek: Boolean = false,
    ): Outcome<LogAward> {
        val preview = preview(save.rules, draft)
        if (preview.total == 0.0)
            return save.result(LogAward(0.0, emptyList(), "Log at least 5 minutes to earn XP"))
        val awarded =
            save.award(
                draft.type,
                preview.total,
                jsNumber(draft.duration).orZero,
                ctx,
                manual = true,
                peek = peek,
            )
        val (xp, message) = awarded.value
        if (xp <= 0) return awarded.map { LogAward(0.0, emptyList(), message) }
        val reasons = if (message.isEmpty()) mutableListOf() else mutableListOf(message)
        val parts = awarded.save.starred(splitXp(xp, preview.primary, preview.secondary), reasons)
        return awarded.map { LogAward(parts.sumOf { it.xp }, parts, reasons.joinToString(" · ")) }
    }

    /**
     * LOG-2: records a Log. A replayed operation id answers with the first save's XP and changes
     * nothing; a save that fails is taken back whole.
     */
    fun record(save: EconomySave, draft: LogDraft, ctx: EconomyContext): Outcome<LogResult> {
        if (!draft.opId.isNullOrEmpty()) {
            save.activities
                .firstOrNull { it.opId == draft.opId }
                ?.let { first ->
                    return save.result(
                        LogResult(true, "Activity saved · +${first.xp.text()} XP", first.xp, true)
                    )
                }
        }
        val rule = save.rules.of(draft.type)
        val preview = preview(save.rules, draft)
        val awarded = award(save, draft, ctx)
        val award = awarded.value
        if (award.xp == 0.0) return save.result(LogResult(false, award.message))
        val record =
            Activity(
                id = ctx.ids.uuid(),
                opId = draft.opId,
                type = draft.type,
                name =
                    if (draft.type == "custom")
                        draft.customName.trim().ifEmpty { "Custom activity" }
                    else rule.name,
                icon = rule.icon,
                duration = jsNumber(draft.duration).orZero,
                quality = jsNumber(draft.quality), // D24: not the quality the XP used
                skill = preview.skill,
                primary = preview.primary,
                secondary = preview.secondary,
                xp = award.xp,
                date = ctx.nowIso,
                notes = draft.notes.trim(),
                split = award.split,
            )
        val saved =
            SavePipeline.save(awarded.save.copy(activities = awarded.save.activities + record), ctx)
        return if (saved.value)
            saved.map { LogResult(true, "Activity saved · +${award.xp.text()} XP", award.xp) }
        else
            saved.then {
                SavePipeline.takeBack(save, LogResult(false, SavePipeline.unsavedMessage(ctx)), ctx)
            }
    }

    /** LOG-1: the native Log screen's normalisation of a draft. */
    fun clean(rules: Rules, draft: Map<String, String?>): LogDraft {
        val presetType = draft["type"]?.takeIf { it in rules } ?: "reading"
        val preset = rules.of(presetType)
        fun field(name: String, fallback: String) = draft[name] ?: fallback
        val type = field("type", presetType).takeIf { it in rules } ?: "custom"
        val rule = rules.of(type)
        val primary = field("primary", preset.primary)
        val secondary = field("secondary", preset.secondary)
        return LogDraft(
            type = type,
            customName = field("customName", "").take(80),
            duration =
                jsRound(min(1440.0, max(0.0, jsRound(jsNumber(field("duration", "30")).orZero))))
                    .text(),
            quality = field("quality", "1").takeIf { it in QUALITIES } ?: "1",
            skill = field("skill", preset.skill).take(60),
            primary = primary.takeIf { Attribute.of(it) != null } ?: rule.primary,
            secondary = secondary.takeIf { it.isEmpty() || Attribute.of(it) != null } ?: "",
            notes = field("notes", "").take(2000),
        )
    }

    /** LOG-1, the native Save: clean the draft, record it once per operation id. */
    fun submit(
        save: EconomySave,
        draft: Map<String, String?>,
        operationId: String?,
        ctx: EconomyContext,
    ): Outcome<LogResult> {
        val opId = operationId.orEmpty().take(80)
        if (opId.isEmpty())
            return save.result(LogResult(false, "Could not save: missing operation id"))
        val result = record(save, clean(save.rules, draft).copy(opId = opId), ctx)
        return if (!result.value.ok) result.map { LogResult(false, it.message) }
        else result.toast(result.value.message).map { it.copy(duplicate = false) }
    }
}
