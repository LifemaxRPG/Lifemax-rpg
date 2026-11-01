package com.lifemax.rpg.feature.overview.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.lifemax.rpg.feature.overview.domain.OverviewCards
import com.lifemax.rpg.feature.overview.domain.OverviewCommands
import com.lifemax.rpg.feature.overview.domain.OverviewNotice
import com.lifemax.rpg.feature.overview.domain.OverviewReply
import com.lifemax.rpg.feature.overview.domain.OverviewRepository
import java.util.UUID

/**
 * The cards' screen state: the page's projection, and what is the screen's own (the week day
 * picked, the message showing). Taps go through [taps]. The state is snapshot state so a tap
 * redraws at once. The page's celebrations are played by the screen (PageCelebrations), each once.
 * - The week day picked survives projections and goes back to the page's when the week's dates move
 *   (a new day).
 * - The page's message ([notice]) shows once per id; one cut short by the screen hiding is not
 *   shown again. The screen's own messages (a refused tip) show the same way.
 */
class OverviewViewModel(
    private val repository: OverviewRepository,
    commands: OverviewCommands,
    newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    var cards by mutableStateOf<OverviewCards?>(null)
        private set

    var notice by mutableStateOf<OverviewNotice?>(null)
        private set

    /** The week bar picked (0 to 6). */
    var weekDay by mutableIntStateOf(6)
        private set

    /** A page view the screen hands over to; set by the route (the plugin's "navigate"). */
    var onNavigate: (String) -> Unit = {}

    internal val taps = OverviewTaps(commands, { cards }, { onNavigate(it) }, ::say, newId)

    /** The page's arrival the cards play, once each, only while showing (phase 3). */
    val arrival = ArrivalController()

    private var showing = false
    private var weekDates: List<String>? = null
    private var noticed: Pair<String, Long>? = null
    private var said = 0L

    /** The screen opened (again). */
    fun opened() {
        showing = true
        notice = null
        taps.opened()
        projected()
    }

    /** The screen went behind the page: it draws no message. */
    fun hidden() {
        showing = false
        notice = null
        arrival.hidden()
    }

    /** The repository has taken a projection. */
    fun projected() {
        val p = repository.state.value ?: return
        cards = p
        taps.projected(p)
        arrival.projected(p.session, p.arrival, showing, p.reducedMotion)
        val dates = p.tiles.week.bars.map { it.date }
        if (dates != weekDates) {
            weekDates = dates
            weekDay = p.tiles.week.selected
        }
        p.notice?.let {
            val seen = p.session to it.id
            if (seen != noticed && showing) {
                noticed = seen
                notice = it
            }
        }
    }

    fun onReply(reply: OverviewReply) {
        taps.onReply(reply)
    }

    /** Pick a week bar; the page's own ovPick only redraws its line, so nothing is sent. */
    fun pickDay(index: Int) {
        if (index in 0..6) weekDay = index
    }

    /** The message has shown to the end. */
    fun noticeShown(shown: OverviewNotice) {
        if (notice == shown) notice = null
    }

    private fun say(text: String) {
        notice = OverviewNotice(-(++said), text)
    }
}
