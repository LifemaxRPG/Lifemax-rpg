package com.lifemax.rpg.feature.log.data

import com.lifemax.rpg.feature.log.domain.LogAttribute
import com.lifemax.rpg.feature.log.domain.LogDraft
import com.lifemax.rpg.feature.log.domain.LogPreset
import com.lifemax.rpg.feature.log.domain.LogPreview
import com.lifemax.rpg.feature.log.domain.LogProjection
import com.lifemax.rpg.feature.log.domain.LogQuality
import com.lifemax.rpg.feature.log.domain.LogRepository
import com.lifemax.rpg.feature.log.domain.XpShare
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Decodes the page's Log projection (50-native-log.js). Malformed data is rejected and the last
 * good state kept; an update older than the one held is ignored; a projection from another page
 * session is taken only on an explicit open.
 */
class WebLogRepository : LogRepository {
    private val mutable = MutableStateFlow<LogProjection?>(null)
    override val state = mutable.asStateFlow()

    fun accept(j: JSONObject, establishSession: Boolean = false) {
        require(j.getInt("protocol") == 1) { "Unsupported Log protocol" }
        val session = j.getString("webSessionId")
        val sequence = j.getLong("sequence")
        require(session.isNotBlank() && sequence > 0)
        val old = mutable.value
        if (old != null && old.session != session && !establishSession) return
        if (old?.session == session && sequence <= old.sequence) return

        val presets =
            j.getJSONArray("presets").objects().map {
                LogPreset(
                    it.getString("id"),
                    it.getString("name"),
                    it.getString("icon"),
                    it.getInt("base"),
                    it.getString("skill"),
                    it.getString("primary"),
                    it.optString("secondary", ""),
                )
            }
        val attributes =
            j.getJSONArray("attributes").objects().map {
                LogAttribute(
                    it.getString("id"),
                    it.getString("name"),
                    it.getString("short"),
                    it.getString("icon"),
                    it.getLong("color"),
                )
            }
        val qualities =
            j.getJSONArray("qualities").objects().map {
                LogQuality(it.getString("value"), it.getString("label"), it.getString("hint"))
            }
        require(presets.isNotEmpty() && presets.any { it.id == "custom" }) { "Missing presets" }
        require(attributes.size == 8) { "Expected the eight attributes" }
        require(qualities.isNotEmpty()) { "Missing effort levels" }

        val d = j.getJSONObject("draft")
        val draft =
            LogDraft(
                d.getString("type"),
                d.optString("customName", ""),
                d.getString("duration").toIntOrNull() ?: 0,
                d.getString("quality"),
                d.optString("skill", ""),
                d.getString("primary"),
                d.optString("secondary", ""),
                d.optString("notes", ""),
            )
        require(presets.any { it.id == draft.type }) { "Draft names an unknown preset" }
        val p = j.getJSONObject("preview")
        val preview =
            LogPreview(
                p.getInt("total"),
                p.getString("skill"),
                p.getDouble("durFactor"),
                p.getJSONArray("split").objects().map {
                    XpShare(it.getString("attr"), it.getInt("xp"))
                },
                p.getBoolean("enough"),
            )
        require(preview.total >= 0 && preview.durationFactor.isFinite())
        val colors = j.getJSONObject("tokens")
        mutable.value =
            LogProjection(
                session,
                sequence,
                presets,
                attributes,
                qualities,
                j.getInt("maxMinutes"),
                draft,
                j.getLong("draftRevision"),
                preview,
                colors.keys().asSequence().associateWith { colors.getLong(it) },
                j.getBoolean("light"),
            )
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
}
