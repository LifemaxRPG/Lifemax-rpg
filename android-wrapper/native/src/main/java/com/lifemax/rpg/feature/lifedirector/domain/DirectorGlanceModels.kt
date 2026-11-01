package com.lifemax.rpg.feature.lifedirector.domain

/**
 * Life Director's glance (Today's plan on the Overview) as the page decides it
 * (LifeDirector.native.glance() in logic.js): [state], the head's [title] and [link], and either an
 * empty state's words ([intro], with [button] before set-up) or today's [dial] with its middle
 * ([centre]) and [foot]. The dial is Today's without handles or a build; the middle's title is
 * cleaned, [fullTitle] is the session's whole name, read by TalkBack.
 */
data class DirectorGlance(
    val state: GlanceState,
    val title: String,
    val link: String,
    val intro: String?,
    val button: String?,
    val dial: DirectorDial?,
    val centre: DialCentre?,
    val fullTitle: String?,
    val foot: GlanceFoot?,
)

/** Before set-up; no day to show; today's dial. */
enum class GlanceState {
    SETUP,
    NONE,
    DAY,
}

/** The line under the dial: sessions left and their time, and "N done" ('' for none) in green. */
data class GlanceFoot(val text: String, val done: String)
