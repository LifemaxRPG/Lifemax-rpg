package com.lifemax.rpg.feature.overview.data

import com.lifemax.rpg.core.data.pageTokens
import com.lifemax.rpg.feature.overview.domain.OverviewCards
import com.lifemax.rpg.feature.overview.domain.OverviewRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Decodes the page's Overview cards (`overview.cards.v1`, 50-native-overview-cards.js). Malformed
 * data (a key missing or of the wrong kind) is rejected and the last good state kept; an update
 * older than the one held is ignored; a projection from another page session is taken only on an
 * explicit open. Rejecting throws, which the kit reports as a failed open or update, and the page
 * then gives the view to the live Overview (plan §3.1).
 */
class WebOverviewRepository : OverviewRepository {
    private val mutable = MutableStateFlow<OverviewCards?>(null)
    override val state = mutable.asStateFlow()

    fun accept(j: JSONObject, establishSession: Boolean = false) {
        require(j.int("protocol") == 1) { "Unsupported overview protocol" }
        val session = j.str("webSessionId")
        val sequence = j.long("sequence")
        require(session.isNotBlank() && sequence > 0)
        val old = mutable.value
        if (old != null && old.session != session && !establishSession) return
        if (old?.session == session && sequence <= old.sequence) return
        mutable.value = decode(j, session, sequence)
    }

    private fun decode(j: JSONObject, session: String, sequence: Long): OverviewCards {
        val tileInfo = j.list("tileInfo").map(::tileInfo).also(::requireTileInfo)
        val tiles = tiles(j.getJSONObject("tiles"))
        val images = images(j.getJSONObject("images"))
        // every image a card names is in the table
        val named =
            listOfNotNull(tiles.hero.avatar) +
                tiles.apps?.items?.mapNotNull { it.image }.orEmpty() +
                listOfNotNull(tiles.cat?.portrait)
        require(images.keys.containsAll(named)) { "A card names an image the page did not send" }
        val celebrations = j.list("celebrations").map(::celebration)
        require(celebrations.map { it.id }.toSet().size == celebrations.size) {
            "A celebration appears twice"
        }
        return OverviewCards(
            session = session,
            sequence = sequence,
            head = head(j.getJSONObject("head")),
            tip = j.orNull("tip") { tip(this) },
            layout = layout(j.getJSONObject("layout")),
            tileInfo = tileInfo,
            editor = editorWords(j.getJSONObject("editor")),
            tiles = tiles,
            images = images,
            notice = j.orNull("notice") { notice(this) },
            celebrations = celebrations,
            celebrationTarget = point(j.getJSONObject("celebrationTarget")),
            reducedMotion = j.getJSONObject("motion").bool("reduced"),
            haptics = j.orNull("haptics") { haptics(this) },
            // phase 3's arrival counter: a page before it sends none
            arrival = if (j.has("arrival")) j.long("arrival") else 0L,
            tokens = j.getJSONObject("tokens").pageTokens(),
            light = j.bool("light"),
        )
    }
}
