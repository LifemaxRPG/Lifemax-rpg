package com.lifemax.rpg.core.directorshadow

import com.lifemax.rpg.core.lifedirector.host.AwardReply
import com.lifemax.rpg.core.lifedirector.host.HostActivity
import com.lifemax.rpg.core.lifedirector.host.HostEffects
import com.lifemax.rpg.core.lifedirector.host.ImportedBackup
import com.lifemax.rpg.core.lifedirector.host.LoadedState
import com.lifemax.rpg.core.lifedirector.host.QuestCompletion
import com.lifemax.rpg.core.lifedirector.host.SaveOutcome
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * The host a command runs against in the check (SH-37): every call is answered with what the page's
 * host and store answered at the same place of the same command ([calls], in order), as the host
 * golden's fake does, so native's commands meet exactly the world the page met. Nothing here
 * reaches the economy, the quests, the store or the screen: native never awards, never writes and
 * never shows (SH-34). A call of another name than the page's at that place, or one past the page's
 * last, is noted in [mismatches] and answered with an empty value; [ids] are the ids the page
 * minted, in order.
 */
class PageAnswers(
    private val calls: List<CallFact>,
    private val ids: List<String>,
    private val zone: String,
    private val retires: Boolean,
) : HostEffects {
    private var next = 0
    private var minted = 0
    val mismatches = ArrayList<Int>()

    /** How many of the page's calls native made. */
    val used: Int
        get() = next

    private fun take(fn: String): JsonValue? {
        val c = calls.getOrNull(next++)
        if (c == null || c.fn != fn) {
            mismatches += next - 1
            return null
        }
        return c.out
    }

    private fun bool(v: JsonValue?) = v == JsonBool.TRUE

    private fun str(v: JsonValue?) = (v as? JsonString)?.value

    private fun num(v: JsonValue?) = (v as? JsonNumber)?.value

    private fun field(v: JsonValue?, k: String) = (v as? JsonObject)?.members?.get(k)

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

    /** The page's ids in order; past them a fixed text, which the comparison then shows. */
    override fun newId(): String = ids.getOrNull(minted++) ?: "native-id-$minted"

    override fun deviceZone() = zone

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
}
