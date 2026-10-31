package com.lifemax.rpg.feature.library.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.lifemax.rpg.feature.library.domain.MediaFilter
import com.lifemax.rpg.feature.library.domain.MediaItem
import com.lifemax.rpg.feature.library.domain.MediaProjection
import com.lifemax.rpg.feature.library.domain.MediaRepository

/**
 * The Media pane's own search and filters, and the rows they leave. The page's filter is taken
 * whenever the page sets it (a new session, or a new filter revision: typed on the page, or a voice
 * search); otherwise the screen keeps its own across updates and while hidden. Every tap the page
 * answers closes the screen, so one is handed over per opening and a second, quick tap is dropped.
 *
 * The state is snapshot state rather than a flow so that the search box sees each key at once.
 */
class MediaViewModel(private val repository: MediaRepository) : ViewModel() {
    var ui by mutableStateOf<MediaUi?>(null)
        private set

    private var adopted: Pair<String, Long>? = null
    private var handedOver = false

    /** The screen opened (again): it takes taps. */
    fun opened() {
        handedOver = false
    }

    /** The repository has taken a projection. */
    fun onProjection() {
        val p = repository.state.value ?: return
        val key = p.session to p.filterRevision
        val filter = ui?.filter?.takeIf { key == adopted } ?: p.filter
        adopted = key
        show(p, filter)
    }

    fun search(text: String) = refilter { _, f -> f.copy(search = text) }

    /** Show the items of status [id] ("all" for every status), one of the page's options. */
    fun showStatus(id: String) = refilter { p, f ->
        if (id in p.statusFilter) f.copy(status = id) else f
    }

    /** Show the items of type [id] ("all" for every type), one of the page's options. */
    fun showType(id: String) = refilter { p, f -> if (id in p.typeFilter) f.copy(type = id) else f }

    /** Whether a tap may go to the page, which closes the screen to answer it. */
    fun handOver(): Boolean {
        if (handedOver) return false
        handedOver = true
        return true
    }

    private fun refilter(change: (MediaProjection, MediaFilter) -> MediaFilter) {
        val now = ui ?: return
        show(now.projection, change(now.projection, now.filter))
    }

    private fun show(p: MediaProjection, filter: MediaFilter) {
        ui = MediaUi(p, filter, filter.matching(p.items))
    }
}

/** What the screen draws: the page's projection, the screen's filter and the rows it shows. */
data class MediaUi(
    val projection: MediaProjection,
    val filter: MediaFilter,
    val shown: List<MediaItem>,
)
