package com.lifemax.rpg.core.lifedirector.host

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * The fake host for tests (HC-4): it answers each call with the answer the page's recording host
 * gave at the same place of the same step ([recorded], in order), so a command runs natively
 * against exactly the world the page saw. A call of another name than the one recorded there, or
 * one past the end, is noted in [mismatches] and answered with an empty value. [retires] is whether
 * the page's host had `retiredUntimedQuest`; [ids] counts the ids minted (as the recorder's stub
 * does).
 */
class ReplayHost(
    private val recorded: List<JsonObject>,
    private val retires: Boolean,
    private val ids: IntArray,
) : HostEffects {
    private var next = 0
    val mismatches = ArrayList<String>()

    private fun take(fn: String): JsonValue? {
        val c = recorded.getOrNull(next++)
        if (c == null || (c["fn"] as JsonString).value != fn) {
            mismatches +=
                "call ${next - 1}: native asked $fn, the page ${(c?.get("fn") as? JsonString)?.value ?: "nothing"}"
            return null
        }
        return c["out"]
    }

    private fun bool(v: JsonValue?) = v == JsonBool.TRUE

    private fun str(v: JsonValue?) = (v as? JsonString)?.value

    private fun num(v: JsonValue?) = (v as? JsonNumber)?.value

    private fun field(v: JsonValue?, k: String) = (v as? JsonObject)?.get(k)

    private fun items(v: JsonValue?) = (v as? JsonArray)?.items ?: emptyList()

    override fun connected() = bool(take("connected"))

    override fun toast(message: String) = bool(take("toast"))

    override fun playerId() = str(take("playerId")) ?: "player"

    override fun readQuests(now: Double, zone: String, overlay: JsonValue, revoked: JsonValue?) =
        items(take("readQuests"))

    override fun readPlanSessions(now: Double, zone: String, days: Double) =
        items(take("readPlanSessions"))

    override fun awardActivity(event: JsonValue, title: String?): AwardReply {
        val o = take("awardActivity")
        return AwardReply(
            bool(field(o, "awarded")),
            str(field(o, "reason")),
            str(field(o, "activityId")),
            num(field(o, "xp")),
        )
    }

    override fun completeQuest(hostRef: JsonValue): QuestCompletion {
        val o = take("completeQuest")
        return QuestCompletion(
            bool(field(o, "completed")),
            str(field(o, "name")),
            num(field(o, "xp")),
            str(field(o, "reason")),
        )
    }

    override fun activitiesSince(cursor: String?) =
        items(take("activitiesSince")).map {
            HostActivity(
                str(field(it, "id")) ?: "",
                str(field(it, "type")) ?: "",
                str(field(it, "name")) ?: "",
                num(field(it, "minutes")) ?: 0.0,
                str(field(it, "iso")) ?: "",
                str(field(it, "tag")) ?: "",
                str(field(it, "planDate")),
            )
        }

    override fun activityExists(id: String) = bool(take("activityExists"))

    override fun milestones() = items(take("milestones"))

    override val resolvesRetiredQuests: Boolean
        get() = retires

    override fun retiredUntimedQuest(ref: JsonValue): JsonValue? =
        take("retiredUntimedQuest")?.takeIf { it != JsonNull }

    override fun newId(): String =
        "00000000-0000-4000-8000-" + (++ids[0]).toString().padStart(12, '0')

    override fun deviceZone() = "UTC"

    override fun loadState(playerId: String, zone: String, now: Double): LoadedState {
        val o = take("store.load")
        val r = field(o, "report")
        return LoadedState(
            field(o, "state") ?: JsonObject.EMPTY,
            str(field(r, "source")) ?: "NEW",
            bool(field(r, "recovered")),
            str(field(r, "quarantined")),
            num(field(r, "migratedFrom")),
        )
    }

    private fun saved(o: JsonValue?) = SaveOutcome(bool(field(o, "ok")), str(field(o, "error")))

    override fun saveState(state: JsonValue) = saved(take("store.save"))

    override fun clearState(playerId: String, zone: String, now: Double) =
        saved(take("store.clear"))

    override fun exportBackup(state: JsonValue) = str(take("store.exportJson")) ?: ""

    override fun importBackup(text: String): ImportedBackup {
        val o = take("store.importJson")
        return ImportedBackup(field(o, "state"), str(field(o, "error")))
    }

    /** Calls recorded that native never made. */
    fun unused(): Int = recorded.size - next
}
