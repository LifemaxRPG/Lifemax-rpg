package com.lifemax.rpg.feature.lifedirector.data

import com.lifemax.rpg.feature.lifedirector.domain.DirectorClock
import com.lifemax.rpg.feature.lifedirector.domain.DirectorHeader
import com.lifemax.rpg.feature.lifedirector.domain.DirectorTab
import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorProjection
import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorRepository
import com.lifemax.rpg.feature.lifedirector.domain.StatusDot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Decodes the page's Life Director projection (50-native-director.js). Malformed data is rejected
 * and the last good state kept; an update older than the one held is ignored; a projection from
 * another page session is taken only on an explicit open. A projection without a plan keeps the day
 * last shown.
 */
class WebLifeDirectorRepository : LifeDirectorRepository {
    private val mutable = MutableStateFlow<LifeDirectorProjection?>(null)
    override val state = mutable.asStateFlow()

    fun accept(j: JSONObject, establishSession: Boolean = false) {
        require(j.getInt("protocol") == 1) { "Unsupported Life Director protocol" }
        val session = j.getString("webSessionId")
        val sequence = j.getLong("sequence")
        require(session.isNotBlank() && sequence > 0)
        val old = mutable.value
        if (old != null && old.session != session && !establishSession) return
        if (old?.session == session && sequence <= old.sequence) return

        val header = j.getJSONObject("header")
        val clock = j.getJSONObject("clock")
        val tabs =
            j.getJSONArray("tabs").objects().map {
                DirectorTab(it.getString("id"), it.getString("label"), it.getString("icon"))
            }
        require(tabs.isNotEmpty()) { "Missing section tabs" }
        val colors = j.getJSONObject("tokens")
        mutable.value =
            LifeDirectorProjection(
                session = session,
                sequence = sequence,
                header =
                    DirectorHeader(
                        StatusDot.valueOf(header.getString("dot").uppercase()),
                        header.getString("line"),
                    ),
                clock = DirectorClock(clock.getString("time"), clock.getString("day")),
                tabs = tabs,
                tab = j.getString("tab"),
                banner = j.banner(),
                notice = j.notice(),
                today = j.today() ?: old?.takeIf { it.session == session }?.today,
                tokens = colors.keys().asSequence().associateWith { colors.getLong(it) },
                light = j.getBoolean("light"),
            )
    }
}
