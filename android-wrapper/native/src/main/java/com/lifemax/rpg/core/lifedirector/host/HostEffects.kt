package com.lifemax.rpg.core.lifedirector.host

import com.lifemax.rpg.core.save.JsonValue

/**
 * Everything Life Director's host commands ask of the world outside the plan (HC-4,
 * LIFE_DIRECTOR_HOST_SPEC.md section 2): the LifeMax economy and quests (the page's `LifeMaxHost`,
 * `engine.js:2355-2620`), the toast, and the Life Director store (`LifeDirectorStore`,
 * `engine.js:2242-2340`, whose codec is WP-LD7's). Every answer is a value; an implementation must
 * not throw (a failure is an answer: `awarded = false` with a reason, a refused save).
 *
 * The commands call these in the page's order, with the page's arguments, so a recording
 * implementation ([RecordingHost]) reproduces the page's host calls exactly. The real adapters
 * (WP-LD11, `hostadapters`) implement this interface over the native economy, quests and store.
 */
interface HostEffects {
    /** `Host.connected()`: whether LifeMax (its state, save and quest functions) is there. */
    fun connected(): Boolean

    /**
     * `Host.toast(message)`: shows it; false when there is no toast (the page then draws its own).
     */
    fun toast(message: String): Boolean

    /** `Host.playerId()`: the LifeMax player's id, `player` when not connected. */
    fun playerId(): String

    /**
     * `Host.readQuests(now, zone, overlay, revoked)`: LifeMax's quests as the engine's quest
     * records (`[]` when not connected).
     */
    fun readQuests(
        now: Double,
        zone: String,
        overlay: JsonValue,
        revoked: JsonValue?,
    ): List<JsonValue>

    /** `Host.readPlanSessions(now, zone, days)`: the class plan's days as date-bound quests. */
    fun readPlanSessions(now: Double, zone: String, days: Double): List<JsonValue>

    /** `Host.awardActivity(event, {title})`: one LifeMax activity for the completion event. */
    fun awardActivity(event: JsonValue, title: String?): AwardReply

    /** `Host.completeQuest(hostRef)`: the quest's own guarded check-off with its bounty. */
    fun completeQuest(hostRef: JsonValue): QuestCompletion

    /**
     * `Host.activitiesSince(cursor)`: activities logged in LifeMax after [cursor] (all when null).
     */
    fun activitiesSince(cursor: String?): List<HostActivity>

    /** `Host.activityExists(id)`: whether the LifeMax log still holds that activity. */
    fun activityExists(id: String): Boolean

    /** `Host.milestones()`: the earned milestones; only their ids are read. */
    fun milestones(): List<JsonValue>

    /**
     * Whether `Host.retiredUntimedQuest` exists (the page tests the function before calling it,
     * `logic.js:209`).
     */
    val resolvesRetiredQuests: Boolean

    /** `Host.retiredUntimedQuest(ref)`: a retired daily card as an untimed quest, or null. */
    fun retiredUntimedQuest(ref: JsonValue): JsonValue?

    /** `crypto.randomUUID()`: a new unique id (the page's own forms mint one per request). */
    fun newId(): String

    /** The device's time zone (`Intl...resolvedOptions().timeZone || 'UTC'`, `logic.js:13`). */
    fun deviceZone(): String

    /** `store.load(opts)`: the saved state (migrated, recovered or new) and the report. */
    fun loadState(playerId: String, zone: String, now: Double): LoadedState

    /** `store.save(state)`: write the state; refused writes answer why. */
    fun saveState(state: JsonValue): SaveOutcome

    /** `store.clear(opts)`: save a fresh default state in its place. */
    fun clearState(playerId: String, zone: String, now: Double): SaveOutcome

    /** `store.exportJson(state)`: the backup text. */
    fun exportBackup(state: JsonValue): String

    /** `store.importJson(text)`: the state a backup text holds, or why it cannot be used. */
    fun importBackup(text: String): ImportedBackup
}

/**
 * `Host.awardActivity`'s answer: awarded with the activity's id and XP, or not with a reason
 * (`HOST_NOT_CONNECTED`, `ALREADY_IN_LIFEMAX` with the existing activity, `HOST_READ_ONLY`,
 * `HOST_SAVE_FAILED`).
 */
data class AwardReply(
    val awarded: Boolean,
    val reason: String? = null,
    val activityId: String? = null,
    val xp: Double? = null,
)

/** `Host.completeQuest`'s answer: completed with the quest's name and bounty, or not (and why). */
data class QuestCompletion(
    val completed: Boolean,
    val name: String? = null,
    val xp: Double? = null,
    val reason: String? = null,
)

/**
 * One LifeMax activity as `Host.activitiesSince` gives it: its id, type, name, minutes, ISO date,
 * Life Director tag and the plan day it belongs to.
 */
data class HostActivity(
    val id: String,
    val type: String,
    val name: String,
    val minutes: Double,
    val iso: String,
    val tag: String,
    val planDate: String? = null,
)

/** A save's outcome: ok, or the store's words for why not. */
data class SaveOutcome(val ok: Boolean, val error: String? = null) {
    companion object {
        val OK = SaveOutcome(true)
    }
}

/**
 * `store.load`'s answer: the state, where it came from ([source]: the key, `NEW` or `MEMORY_ONLY`),
 * whether a damaged copy was replaced by the backup ([recovered]), the name the damaged text was
 * kept under, and the schema version it was migrated from.
 */
data class LoadedState(
    val state: JsonValue,
    val source: String,
    val recovered: Boolean = false,
    val quarantined: String? = null,
    val migratedFrom: Double? = null,
)

/** `store.importJson`'s answer: the state, or the error message the page shows. */
data class ImportedBackup(val state: JsonValue?, val error: String? = null)
