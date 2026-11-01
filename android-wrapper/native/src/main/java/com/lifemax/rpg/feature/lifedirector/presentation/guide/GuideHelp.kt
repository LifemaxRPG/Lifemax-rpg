package com.lifemax.rpg.feature.lifedirector.presentation.guide

import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorProjection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The ? button (phase 4B, the plan's guide.help). On a section with its own guide drawn here it
 * plays that guide. From Goals, Settings or Quests, which have none, it plays the guide the page
 * names in `help` (Today's, or set-up's until the week is built): the screen switches to that
 * guide's section first and plays it once the section has arrived with it. When the page names no
 * guide the screen draws, or the section arrives without it, the ? is handed over and the page
 * plays its own ([handOverDue]).
 *
 * [draws] says whether the screen draws a section, [showSection] asks the page for one,
 * [pendingSection] is the section asked for and not answered yet, and [startGuide] plays the guide
 * of the section on show.
 */
class GuideHelp(
    private val projection: StateFlow<LifeDirectorProjection?>,
    private val draws: (String) -> Boolean,
    private val showSection: (String) -> Unit,
    private val pendingSection: () -> String?,
    private val startGuide: () -> Unit,
) {
    /** the section whose guide the ? asked for, while the screen goes there */
    private var wanted: String? = null
    private val due = MutableStateFlow(false)

    /** true once the ? must go to the page after all: the screen hands it over */
    val handOverDue: StateFlow<Boolean> = due.asStateFlow()

    /**
     * The ? was tapped: true when the screen answers it itself (the guide plays, or its section is
     * on the way); false hands the tap over to the page.
     */
    fun ask(): Boolean {
        val p = projection.value ?: return false
        val own = p.guide
        if (own != null && own.key == p.tab) {
            startGuide()
            return true
        }
        val key = p.help ?: return false
        if (key == p.tab || !draws(key)) return false
        showSection(key)
        if (pendingSection() != key) return false // the screen is busy: the page answers it
        wanted = key
        return true
    }

    /**
     * A new projection: once the section asked for is on show, its guide plays, or the ? is handed
     * over when the page sent none. A section request refused (another section, nothing pending)
     * forgets the ?.
     */
    fun projected() {
        val key = wanted ?: return
        val p = projection.value ?: return
        if (p.tab != key) {
            if (pendingSection() == null) wanted = null
            return
        }
        wanted = null
        if (p.guide?.key == key) startGuide() else due.value = true
    }

    /** Takes the hand-over [handOverDue] asked for: true once, then false. */
    fun takeHandOver(): Boolean {
        if (!due.value) return false
        due.value = false
        return true
    }

    /** The screen opened or went behind the page: a ? on its way is forgotten. */
    fun reset() {
        wanted = null
        due.value = false
    }
}
