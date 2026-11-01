package com.lifemax.rpg.feature.overview.domain

import kotlinx.coroutines.flow.StateFlow

/** The page's Overview cards as last accepted (null before the first). */
interface OverviewRepository {
    val state: StateFlow<OverviewCards?>
}

/**
 * The commands the cards send the page (plan §3.4); each returns its request id. A command that
 * leaves the view or opens a page sheet over it (plan, attr, luck, shop, health, appAdd, appsEdit,
 * edit) is never answered, as the screen steps aside first; appLaunch and tip keep the screen and
 * are answered. The writes (luck's roll, tip's dismissal) carry an operation id [op] per (command,
 * target), so a repeat the page sees is recognised ("Already recorded.") and not done twice.
 */
interface OverviewCommands {
    /** A glance tap: Life Director's Today and, for [act] on [id], what that tap does there. */
    fun plan(act: String, id: String): String

    /** An attribute's sheet; the page first scrolls [tile] into view behind it. */
    fun attr(id: String, tile: String): String

    /** Daily Luck: rolls today's dice when not rolled yet (a write). */
    fun luck(op: String): String

    fun shop(): String

    fun health(): String

    fun appLaunch(pkg: String): String

    fun appAdd(): String

    fun appsEdit(): String

    /** Dismiss the feature tip (a write: tipsSeen). */
    fun tip(op: String): String

    /** Phase 1: the page's own editor. */
    fun edit(): String
}

/** The page's answer to a command: ok only once stored; [message] is the page's words. */
data class OverviewReply(
    val requestId: String,
    val command: String,
    val ok: Boolean,
    val changed: Boolean,
    val message: String,
)
