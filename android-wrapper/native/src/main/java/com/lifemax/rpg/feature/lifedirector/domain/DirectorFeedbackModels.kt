package com.lifemax.rpg.feature.lifedirector.domain

/**
 * Where the XP celebration's orbs fly (the projection's `celebrationTarget`, NativeTabKit's): the
 * centre of the top bar's logo, in page px (dp here) from the top left of the page.
 */
data class DirectorPoint(val x: Float, val y: Float)

/**
 * A sheet the Overview's glance asked for (the projection's `intent`, phase 4A): the screen opens
 * the [sheet] of kind "session", "timer" or "meal" for [target] once, by its [id], which counts up
 * within a page session.
 */
data class GlanceIntent(val id: Long, val sheet: String, val target: String)
