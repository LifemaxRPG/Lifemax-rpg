package com.lifemax.rpg.feature.library.data

import com.lifemax.rpg.feature.library.domain.AudioProgress
import com.lifemax.rpg.feature.library.domain.FeatureTip
import com.lifemax.rpg.feature.library.domain.FilterControl
import com.lifemax.rpg.feature.library.domain.FilterOption
import com.lifemax.rpg.feature.library.domain.LibraryHeader
import com.lifemax.rpg.feature.library.domain.LibraryPane
import com.lifemax.rpg.feature.library.domain.ListMessage
import com.lifemax.rpg.feature.library.domain.MediaAudio
import com.lifemax.rpg.feature.library.domain.MediaButtons
import com.lifemax.rpg.feature.library.domain.MediaFilter
import com.lifemax.rpg.feature.library.domain.MediaItem
import com.lifemax.rpg.feature.library.domain.MediaProjection
import com.lifemax.rpg.feature.library.domain.MediaRepository
import com.lifemax.rpg.feature.library.domain.MediaStat
import com.lifemax.rpg.feature.library.domain.PageNotice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Decodes the page's Media pane projection (50-native-library.js). Malformed data (a key missing or
 * of the wrong type included) is rejected and the last good state kept; an update older than the
 * one held is ignored; a projection from another page session is taken only on an explicit open.
 */
class WebMediaRepository : MediaRepository {
    private val mutable = MutableStateFlow<MediaProjection?>(null)
    override val state = mutable.asStateFlow()

    fun accept(j: JSONObject, establishSession: Boolean = false) {
        require(j.getInt("protocol") == 1) { "Unsupported library protocol" }
        val session = j.getString("webSessionId")
        val sequence = j.getLong("sequence")
        require(session.isNotBlank() && sequence > 0)
        val old = mutable.value
        if (old != null && old.session != session && !establishSession) return
        if (old?.session == session && sequence <= old.sequence) return

        val panes =
            j.getJSONArray("panes").objects().map {
                LibraryPane(it.getString("id"), it.getString("label"), it.getBoolean("current"))
            }
        require(panes.count { it.current } == 1) { "One pane is the current one" }
        val stats = j.getJSONArray("stats").objects().map(::stat)
        require(stats.size == 4) { "Expected the four stats" }
        val statusFilter = control(j.getJSONObject("statusFilter"))
        val typeFilter = control(j.getJSONObject("typeFilter"))
        val f = j.getJSONObject("filter")
        val filter = MediaFilter(f.getString("search"), f.getString("status"), f.getString("type"))
        require(filter.status in statusFilter && filter.type in typeFilter) {
            "The page's filter is not one of its options"
        }
        val items = j.getJSONArray("items").objects().map(::item)
        require(items.map { it.id }.toSet().size == items.size) { "An item appears twice" }
        val header = j.getJSONObject("header")
        val search = j.getJSONObject("search")
        val buttons = j.getJSONObject("buttons")
        val colors = j.getJSONObject("tokens")
        mutable.value =
            MediaProjection(
                session,
                sequence,
                panes,
                LibraryHeader(
                    header.getString("eyebrow"),
                    header.getString("title"),
                    header.getString("blurb"),
                    header.getString("add"),
                ),
                j.objectOrNull("tip") {
                    FeatureTip(
                        it.getString("icon"),
                        it.getString("title"),
                        it.getString("body"),
                        it.getString("dismiss"),
                    )
                },
                stats,
                search.getString("label"),
                search.getString("placeholder"),
                statusFilter,
                typeFilter,
                filter,
                f.getLong("revision"),
                items,
                message(j.getJSONObject("empty"), hasAdd = true),
                message(j.getJSONObject("noMatch"), hasAdd = false),
                MediaButtons(
                    buttons.getString("edit"),
                    buttons.getString("remove"),
                    buttons.getString("play"),
                    buttons.getString("replace"),
                    buttons.getString("removeAudio"),
                    buttons.getString("attach"),
                    buttons.getString("link"),
                ),
                j.objectOrNull("notice") { PageNotice(it.getLong("id"), it.getString("text")) },
                TOKENS.associateWith { colors.getLong(it) },
                j.getBoolean("light"),
            )
    }

    private fun stat(s: JSONObject): MediaStat {
        require(s.has("bar")) { "A stat without its bar" }
        val bar = if (s.isNull("bar")) null else s.getInt("bar")
        require(bar == null || bar in 0..100) { "A stat's bar is out of range" }
        return MediaStat(s.getString("label"), s.getString("value"), s.getString("foot"), bar)
    }

    private fun control(c: JSONObject): FilterControl {
        val options =
            c.getJSONArray("options").objects().map {
                FilterOption(it.getString("id"), it.getString("label"))
            }
        return FilterControl(c.getString("label"), options).also {
            require(MediaFilter.ALL in it) { "A filter without its \"all\"" }
        }
    }

    private fun item(x: JSONObject): MediaItem {
        val id = x.getString("id")
        val status = x.getString("status")
        val finished = x.getBoolean("finished")
        require(id.isNotEmpty()) { "An item without an id" }
        require(finished == (status == "finished")) { "Finished and the status disagree" }
        return MediaItem(
            id,
            x.getString("title"),
            x.getString("type"),
            x.getString("creator"),
            x.getString("subtitle"),
            status,
            x.getString("statusLabel"),
            finished,
            x.getString("notes"),
            x.getString("link"),
            x.objectOrNull("audio", ::audio),
        )
    }

    private fun audio(a: JSONObject): MediaAudio {
        val p = a.getJSONObject("progress")
        val progress = AudioProgress(p.getDouble("pct"), p.getString("label"), p.getString("time"))
        require(progress.pct in 0.0..100.0) { "Audio progress is out of range" }
        return MediaAudio(a.getString("file"), a.getString("meta"), progress)
    }

    /** What shows in place of rows; only the empty library's has an add button. */
    private fun message(m: JSONObject, hasAdd: Boolean) =
        ListMessage(
            m.getString("icon"),
            m.getString("title"),
            m.getString("body"),
            if (hasAdd) m.getString("add") else "",
        )
}

private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

/** The object at [key] decoded, or null where the page sends null; a missing key is malformed. */
private fun <T> JSONObject.objectOrNull(key: String, decode: (JSONObject) -> T): T? {
    require(has(key)) { "Missing $key" }
    return if (isNull(key)) null else decode(getJSONObject(key))
}

/** The page's palette tokens (NativeTabKit's TOKENS) the screen draws with. */
private val TOKENS =
    listOf(
        "bg",
        "panel",
        "panel2",
        "panel3",
        "text",
        "muted",
        "muted2",
        "accent",
        "accent-dim",
        "accent-strong",
        "line",
        "line-soft",
        "good",
    )
