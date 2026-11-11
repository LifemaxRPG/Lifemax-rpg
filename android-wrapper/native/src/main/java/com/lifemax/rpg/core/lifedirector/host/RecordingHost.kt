package com.lifemax.rpg.core.lifedirector.host

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * One call a command made to the host (HC-4): its name as the page's host and store have it
 * (`awardActivity`, `store.save`), its arguments and its answer, as JSON (a saved state is not
 * repeated in `store.save`'s arguments: the outcome's state is the state).
 */
data class HostCall(val fn: String, val args: List<JsonValue>, val out: JsonValue)

/** [inner] with every call noted in [calls], in order (HC-4). The commands' third result. */
class RecordingHost(private val inner: HostEffects) : HostEffects {
    val calls = ArrayList<HostCall>()

    private fun <T> note(fn: String, args: List<JsonValue>, out: T, json: (T) -> JsonValue): T {
        calls += HostCall(fn, args, json(out))
        return out
    }

    private fun str(s: String?): JsonValue = s?.let(::JsonString) ?: JsonNull

    private fun num(n: Double?): JsonValue = n?.let(::JsonNumber) ?: JsonNull

    private fun bool(b: Boolean): JsonValue = JsonBool.of(b)

    private fun o(vararg pairs: Pair<String, JsonValue?>): JsonObject =
        JsonObject(
            LinkedHashMap<String, JsonValue>().also { m ->
                for ((k, v) in pairs) if (v != null) m[k] = v
            }
        )

    override fun connected(): Boolean = note("connected", emptyList(), inner.connected(), ::bool)

    override fun toast(message: String): Boolean =
        note("toast", listOf(JsonString(message)), inner.toast(message), ::bool)

    override fun playerId(): String = note("playerId", emptyList(), inner.playerId(), ::JsonString)

    override fun readQuests(
        now: Double,
        zone: String,
        overlay: JsonValue,
        revoked: JsonValue?,
    ): List<JsonValue> =
        note(
            "readQuests",
            listOf(JsonNumber(now), JsonString(zone), overlay, revoked ?: JsonNull),
            inner.readQuests(now, zone, overlay, revoked),
            ::JsonArray,
        )

    override fun readPlanSessions(now: Double, zone: String, days: Double): List<JsonValue> =
        note(
            "readPlanSessions",
            listOf(JsonNumber(now), JsonString(zone), JsonNumber(days)),
            inner.readPlanSessions(now, zone, days),
            ::JsonArray,
        )

    override fun awardActivity(event: JsonValue, title: String?): AwardReply =
        note(
            "awardActivity",
            listOf(event, o("title" to title?.let(::JsonString))),
            inner.awardActivity(event, title),
        ) {
            o(
                "awarded" to bool(it.awarded),
                "reason" to it.reason?.let(::JsonString),
                "activityId" to it.activityId?.let(::JsonString),
                "xp" to it.xp?.let(::JsonNumber),
            )
        }

    override fun completeQuest(hostRef: JsonValue): QuestCompletion =
        note("completeQuest", listOf(hostRef), inner.completeQuest(hostRef)) {
            o(
                "completed" to bool(it.completed),
                "xp" to it.xp?.let(::JsonNumber),
                "name" to it.name?.let(::JsonString),
                "reason" to it.reason?.let(::JsonString),
            )
        }

    override fun activitiesSince(cursor: String?): List<HostActivity> =
        note("activitiesSince", listOf(str(cursor)), inner.activitiesSince(cursor)) { list ->
            JsonArray(
                list.map {
                    o(
                        "id" to JsonString(it.id),
                        "type" to JsonString(it.type),
                        "name" to JsonString(it.name),
                        "minutes" to JsonNumber(it.minutes),
                        "iso" to JsonString(it.iso),
                        "tag" to JsonString(it.tag),
                        "planDate" to str(it.planDate),
                    )
                }
            )
        }

    override fun activityExists(id: String): Boolean =
        note("activityExists", listOf(JsonString(id)), inner.activityExists(id), ::bool)

    override fun milestones(): List<JsonValue> =
        note("milestones", emptyList(), inner.milestones(), ::JsonArray)

    override val resolvesRetiredQuests: Boolean
        get() = inner.resolvesRetiredQuests

    override fun retiredUntimedQuest(ref: JsonValue): JsonValue? =
        note("retiredUntimedQuest", listOf(ref), inner.retiredUntimedQuest(ref)) { it ?: JsonNull }

    override fun newId(): String = inner.newId()

    override fun deviceZone(): String = inner.deviceZone()

    private fun options(playerId: String, zone: String, now: Double) =
        o(
            "playerId" to JsonString(playerId),
            "timezone" to JsonString(zone),
            "nowEpoch" to JsonNumber(now),
        )

    override fun loadState(playerId: String, zone: String, now: Double): LoadedState =
        note(
            "store.load",
            listOf(options(playerId, zone, now)),
            inner.loadState(playerId, zone, now),
        ) {
            o(
                "state" to it.state,
                "report" to
                    o(
                        "source" to JsonString(it.source),
                        "recovered" to bool(it.recovered),
                        "quarantined" to str(it.quarantined),
                        "migratedFrom" to num(it.migratedFrom),
                    ),
            )
        }

    private fun saved(r: SaveOutcome): JsonValue =
        o("ok" to bool(r.ok), "error" to r.error?.let(::JsonString))

    override fun saveState(state: JsonValue): SaveOutcome =
        note("store.save", emptyList(), inner.saveState(state), ::saved)

    override fun clearState(playerId: String, zone: String, now: Double): SaveOutcome =
        note(
            "store.clear",
            listOf(options(playerId, zone, now)),
            inner.clearState(playerId, zone, now),
            ::saved,
        )

    override fun exportBackup(state: JsonValue): String =
        note("store.exportJson", emptyList(), inner.exportBackup(state), ::JsonString)

    override fun importBackup(text: String): ImportedBackup =
        note("store.importJson", listOf(JsonString(text)), inner.importBackup(text)) {
            if (it.state != null) o("state" to it.state) else o("error" to str(it.error))
        }
}
