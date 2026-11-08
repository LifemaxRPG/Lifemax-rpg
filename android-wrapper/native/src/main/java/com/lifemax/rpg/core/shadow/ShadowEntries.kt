package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.economy.Ascension
import com.lifemax.rpg.core.economy.Bonuses
import com.lifemax.rpg.core.economy.Coach
import com.lifemax.rpg.core.economy.Decay
import com.lifemax.rpg.core.economy.EconomyContext
import com.lifemax.rpg.core.economy.EconomySave
import com.lifemax.rpg.core.economy.FixedAwards
import com.lifemax.rpg.core.economy.LifeDirector
import com.lifemax.rpg.core.economy.LogActivity
import com.lifemax.rpg.core.economy.LogDraft
import com.lifemax.rpg.core.economy.Outcome
import com.lifemax.rpg.core.economy.Quests
import com.lifemax.rpg.core.economy.SavePipeline
import com.lifemax.rpg.core.economy.Sparks
import com.lifemax.rpg.core.economy.Voice
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/** What one entry did natively: the save it leaves, its return value as the page's JSON, toasts. */
class EntryRun(val save: EconomySave, val returns: JsonValue, val toasts: List<String>) {
    companion object {
        fun <T> of(outcome: Outcome<T>, returns: (T) -> JsonValue): EntryRun =
            EntryRun(outcome.save, returns(outcome.value), outcome.toasts)

        /** An outcome whose page entry returns nothing (`undefined`, sent as null). */
        fun nothing(outcome: Outcome<*>): EntryRun =
            EntryRun(outcome.save, JsonNull, outcome.toasts)
    }
}

/** What an entry needs beside its input, the save and the context. */
class EntryWorld(
    /** The page's state before the action (the shadow keys), as JSON. */
    val before: Map<String, JsonValue>,
    val content: ShadowContent,
)

/** Thrown when an action needs page content native does not hold ([ShadowContent]). */
class ContentMissing : Exception("content") {
    override fun fillInStackTrace(): Throwable = this
}

/**
 * One shadow point that WP6b and WP6c compare on the economy's model (ECONOMY_SHADOW_PLAN 1.2): the
 * env parts it may carry and the native call that stands for the page's entry, glue included (an
 * early return the page makes before asking, the answer it turns into its return value).
 */
class ShadowEntry(
    val kind: String,
    val shape: EnvShape,
    val run: (EntryInput, EconomySave, EconomyContext, EntryWorld) -> EntryRun,
)

/**
 * The economy kinds WP6b and WP6c compare (the Daily Hand's own kinds are [QuestEntries]), the
 * kinds WP6d compares ([LATER]: Health and decay) and the page entries that have no native
 * counterpart ([NOT_NATIVE]: the journal's own text saves around the coach's rewards, and
 * `removeAct`, a cache edit the save always follows). `boot.load` is the runner's own (its
 * before-state is the stored save).
 */
object ShadowEntries {
    /** Kinds compared from WP6d on. */
    val LATER: Set<String> = setOf("health.sync", "decay.apply", "boot.decay")

    /** Page entries native does not model as one action. */
    val NOT_NATIVE: Set<String> = setOf("log.remove", "coach.reflect", "coach.review")

    /** The boot's load (SAVE-5); see [ShadowRunner]. */
    const val BOOT_LOAD = "boot.load"

    private val all: List<ShadowEntry> =
        listOf(
            ShadowEntry("log.record", EnvShape.SAVES) { input, s, ctx, _ ->
                input.count(1)
                EntryRun.of(LogActivity.record(s, draft(input.obj(0)), ctx)) {
                    obj(
                        "ok" to JsonBool.of(it.ok),
                        "xp" to it.xp?.let(::num),
                        "message" to JsonString(it.message),
                        "duplicate" to if (it.duplicate) JsonBool.TRUE else null,
                    )
                }
            },
            ShadowEntry("log.delete", EnvShape(confirms = 1)) { input, s, ctx, _ ->
                input.count(1)
                val id = input.text(0)
                // the page returns before asking when the record is not there
                if (s.activities.none { it.id == id }) EntryRun(s, JsonNull, emptyList())
                else EntryRun.nothing(SavePipeline.deleteActivity(s, id, ctx, input.confirmed()))
            },
            ShadowEntry("fixed.undo", EnvShape.SAVES) { input, s, ctx, _ ->
                input.count(1)
                EntryRun.nothing(FixedAwards.undo(s, input.text(0), ctx))
            },
            ShadowEntry("voice.log", EnvShape.SAVES) { input, s, ctx, _ ->
                input.count(1)
                val o = input.obj(0)
                val said =
                    Voice.Said(
                        type = EntryInput.member(o, "type", "args[0]"),
                        name = EntryInput.member(o, "name", "args[0]"),
                        minutes = EntryInput.numberOf(o["duration"], "duration" in o, "args[0]"),
                        day =
                            o["day"]
                                ?.takeIf { it != JsonNull }
                                ?.let { EntryInput.textOf(it, "args[0].day") },
                        at = EntryInput.numberOf(o["atMs"], "atMs" in o, "args[0]").toLong(),
                    )
                EntryRun.of(Voice.logNow(s, said, ctx)) {
                    if (it.ok)
                        obj(
                            "ok" to JsonBool.TRUE,
                            "xp" to it.xp?.let(::num),
                            "id" to it.id?.let(::JsonString),
                        )
                    else obj("ok" to JsonBool.FALSE, "msg" to it.message?.let(::JsonString))
                }
            },
            ShadowEntry("director.award", EnvShape.SAVES) { input, s, ctx, world ->
                input.count(1, 2)
                val e = input.obj(0)
                fun t(key: String) = EntryInput.member(e, key, "args[0]")
                fun n(key: String) = EntryInput.numberOf(e[key], key in e, "args[0].$key")
                val session =
                    LifeDirector.Session(
                        eventId = t("eventId"),
                        tag = t("activityTypeTag"),
                        startEpochSeconds = n("actualStartEpoch"),
                        minutes = n("actualDurationMinutes"),
                        title = t("title"),
                        partial = (e["lifecycleState"] as? JsonString)?.value == PARTIAL,
                    )
                val title = (input.arg(1) as? JsonObject)?.get("title")?.takeIf { it != JsonNull }
                val award =
                    LifeDirector.awardActivity(
                        s,
                        session,
                        title?.let { EntryInput.textOf(it, "args[1].title") },
                        ctx,
                        connected(world),
                    )
                EntryRun.of(award) {
                    obj(
                        "awarded" to JsonBool.of(it.awarded),
                        "reason" to it.reason?.let(::JsonString),
                        "activityId" to it.activityId?.let(::JsonString),
                        "xp" to it.xp?.let(::num),
                    )
                }
            },
            ShadowEntry("director.quest", EnvShape.SAVES) { input, s, ctx, world ->
                input.count(0, 1)
                val ref =
                    (input.arg(0) as? JsonObject)?.let {
                        LifeDirector.QuestRef(
                            it["questId"]
                                ?.takeIf { v -> v != JsonNull }
                                ?.let { v -> EntryInput.textOf(v, "args[0].questId") },
                            it["key"]
                                ?.takeIf { v -> v != JsonNull }
                                ?.let { v -> EntryInput.textOf(v, "args[0].key") },
                            EntryInput.truthy(it["future"]),
                        )
                    }
                EntryRun.of(LifeDirector.completeQuest(s, ref, ctx, connected(world))) {
                    obj(
                        "completed" to JsonBool.of(it.completed),
                        "reason" to it.reason?.let(::JsonString),
                        "xp" to it.xp?.let(::num),
                        "name" to it.name?.let(::JsonString),
                    )
                }
            },
            ShadowEntry("bonus.care", EnvShape.SAVES) { input, s, ctx, _ ->
                input.count(1)
                val care = Bonuses.Care.entries.firstOrNull { it.key == input.text(0) }
                // ecoCare(kind) of a kind it does not know changes nothing
                if (care == null) EntryRun(s, JsonNull, emptyList())
                else EntryRun.nothing(Bonuses.catCare(s, care, ctx))
            },
            ShadowEntry("bonus.nutrition", EnvShape.SAVES) { input, s, ctx, _ ->
                input.count(0, MAX_IGNORED_ARGS)
                EntryRun.nothing(Bonuses.nutrition(s, ctx))
            },
            ShadowEntry("money.record", EnvShape.SAVES) { input, s, ctx, _ ->
                input.count(0, 3)
                val result =
                    Bonuses.recordMoney(s, input.text(0), input.number(1), input.text(2), ctx)
                EntryRun.of(result) {
                    obj("ok" to JsonBool.of(it.ok), "message" to JsonString(it.message))
                }
            },
            ShadowEntry("money.remove", EnvShape.SAVES) { input, s, ctx, _ ->
                input.count(1)
                EntryRun.of(Bonuses.removeMoney(s, input.text(0), ctx)) { JsonBool.of(it) }
            },
            ShadowEntry("fixed.plan", EnvShape(plan = true)) { input, s, ctx, world ->
                input.count(4)
                val date = input.text(0)
                val day = world.content.planDay(date) ?: throw ContentMissing()
                val links = world.content.planLinks(day.kind) ?: throw ContentMissing()
                val tick =
                    FixedAwards.tickPlan(
                        s,
                        date,
                        day,
                        input.text(1),
                        input.number(2).toInt(),
                        input.truthy(3),
                        links,
                        ctx,
                    )
                EntryRun.of(tick) {
                    obj("saved" to JsonBool.of(it.saved), "message" to JsonString(it.message))
                }
            },
            ShadowEntry("fixed.catalog", EnvShape(dom = setOf("actDur", "actRes", "actNote"))) {
                input,
                s,
                ctx,
                world ->
                input.count(1)
                val entry = world.content.catalog(input.text(0)) ?: throw ContentMissing()
                val form =
                    FixedAwards.CatalogInput(
                        input.domText("actDur"),
                        input.domText("actRes"),
                        input.domText("actNote"),
                    )
                EntryRun.nothing(FixedAwards.confirm(s, entry, form, ctx))
            },
            ShadowEntry("coach.claim", EnvShape.SAVES) { input, s, ctx, _ ->
                input.count(1)
                val claim = Coach.Claim.entries.firstOrNull { it.key == input.text(0) }
                // jcClaim of a gift it does not know answers false and changes nothing
                if (claim == null) EntryRun(s, JsonBool.FALSE, emptyList())
                else EntryRun.of(Coach.claim(s, claim, ctx)) { JsonBool.of(it) }
            },
            ShadowEntry("coach.catchup", EnvShape(prompts = 1)) { input, s, ctx, _ ->
                input.count(0)
                // not asked (the card did not offer it): native is run with nothing typed, and must
                // agree, or its "full sentence" toast shows as a difference; a cancel changes
                // nothing
                val typed = input.prompts.firstOrNull()
                if (input.prompts.isNotEmpty() && typed == null) EntryRun(s, JsonNull, emptyList())
                else EntryRun.nothing(Coach.catchUp(s, typed.orEmpty(), ctx))
            },
            ShadowEntry("spark.buy", EnvShape.SAVES) { input, s, ctx, _ ->
                input.count(1, 2)
                EntryRun.nothing(Sparks.buy(s, shopItem(input.text(0)), input.text(1), ctx))
            },
            ShadowEntry("ascend", EnvShape(confirms = 1)) { input, s, ctx, _ ->
                input.count(1)
                EntryRun.nothing(Ascension.ascend(s, input.text(0), ctx, input.confirmed()))
            },
            ShadowEntry("rest.take", EnvShape(plan = true)) { input, s, ctx, _ ->
                input.count(0)
                EntryRun.nothing(Decay.takeRest(s, ctx))
            },
            ShadowEntry("rules.save", EnvShape(domPrefix = RULE_PREFIX)) { input, s, ctx, _ ->
                input.count(0)
                val bases =
                    input.dom.keys.associate { it.removePrefix(RULE_PREFIX) to input.domText(it) }
                EntryRun.nothing(SavePipeline.saveRuleBases(s, bases, ctx))
            },
            ShadowEntry("rules.reset", EnvShape.SAVES) { input, s, ctx, _ ->
                input.count(0)
                EntryRun.nothing(SavePipeline.resetRules(s, ctx))
            },
            ShadowEntry("rules.base", EnvShape(dom = setOf("actBase"))) { input, s, ctx, _ ->
                input.count(1, 2)
                val base = if (input.truthy(1)) null else input.domText("actBase")
                EntryRun.nothing(FixedAwards.setBase(s, input.text(0), base, ctx))
            },
            ShadowEntry(
                "rules.decay",
                EnvShape(dom = setOf("decayOn", "decayGrace", "decayRate")),
            ) { input, s, ctx, _ ->
                input.count(0)
                val saved =
                    Decay.saveSettings(
                        s,
                        input.domChecked("decayOn"),
                        input.domText("decayGrace"),
                        input.domText("decayRate"),
                        ctx,
                    )
                EntryRun.nothing(saved)
            },
            ShadowEntry("quest.toggle", EnvShape(domPrefix = NOTE_PREFIX)) { input, s, ctx, _ ->
                input.count(1, 2)
                // `state.quests.find(q => q.id === id)`: an id that is not text finds nothing
                val id = (input.arg(0) as? JsonString)?.value
                if (id == null) EntryRun(s, JsonNull, emptyList())
                else {
                    val note =
                        input.dom["$NOTE_PREFIX$id"]?.let { input.domText("$NOTE_PREFIX$id") }
                    EntryRun.nothing(Quests.toggle(s, id, input.truthy(1), ctx, note.orEmpty()))
                }
            },
            ShadowEntry("save.other", EnvShape.SAVES) { input, s, ctx, _ ->
                input.count(0, MAX_IGNORED_ARGS)
                EntryRun.of(SavePipeline.save(s, ctx)) { JsonBool.of(it) }
            },
        )

    private val byKind: Map<String, ShadowEntry> = all.associateBy { it.kind }

    /** Every kind compared, the Daily Hand's own and `boot.load` included. */
    val COMPARED: Set<String> = byKind.keys + QuestEntries.KINDS + BOOT_LOAD

    /** The entry of [kind], or null when it is not compared here. */
    fun of(kind: String): ShadowEntry? = byKind[kind]

    /** The Log form's draft (LOG-1): text fields only; any other member refuses the action. */
    fun draft(d: JsonObject): LogDraft {
        for (key in d.members.keys) if (key !in DRAFT_KEYS) EntryInput.refuse("args[0]")
        fun t(key: String) = EntryInput.member(d, key, "args[0]")
        return LogDraft(
            type = t("type"),
            customName = t("customName"),
            duration = t("duration"),
            quality = t("quality"),
            skill = t("skill"),
            primary = t("primary"),
            secondary = t("secondary"),
            notes = t("notes"),
            opId =
                d["opId"]?.takeIf(EntryInput::truthy)?.let { EntryInput.textOf(it, "args[0].opId") },
        )
    }

    /** Life Director pays only while the page's host is ready: `quests` is a list (LD-2). */
    private fun connected(world: EntryWorld) = world.before["quests"] is JsonArray

    private fun shopItem(kind: String) =
        when (kind) {
            "theme" -> Sparks.Item.Theme
            "perk" -> Sparks.Item.Perk
            "toy" -> Sparks.Item.Toy
            "rest" -> Sparks.Item.RestToken
            "shield" -> Sparks.Item.ShieldRefill
            else -> Sparks.Item.Unknown
        }

    /** An object with the members that are not null, in order. */
    fun obj(vararg members: Pair<String, JsonValue?>): JsonObject =
        JsonObject(
            LinkedHashMap<String, JsonValue>().apply {
                for ((k, v) in members) if (v != null) put(k, v)
            }
        )

    private fun num(value: Double) = JsonNumber(value)

    private val DRAFT_KEYS =
        setOf(
            "type",
            "customName",
            "duration",
            "quality",
            "skill",
            "primary",
            "secondary",
            "notes",
            "opId",
        )
    private const val RULE_PREFIX = "rule-"

    /** The quest card's note field, `qnote-<id>`, which `toggleQuest` reads. */
    private const val NOTE_PREFIX = "qnote-"
    private const val PARTIAL = "PARTIALLY_COMPLETED"

    /** Entries the page calls from a timer or a listener may be given an event; it is ignored. */
    private const val MAX_IGNORED_ARGS = 1
}
