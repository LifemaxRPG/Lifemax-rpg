package com.lifemax.rpg.feature.overview.presentation

import com.lifemax.rpg.feature.overview.data.OverviewCommandSender
import com.lifemax.rpg.feature.overview.domain.MoveGo
import com.lifemax.rpg.feature.overview.domain.OverviewCards
import com.lifemax.rpg.feature.overview.domain.OverviewCommands
import com.lifemax.rpg.feature.overview.domain.OverviewReply
import com.lifemax.rpg.feature.overview.domain.OverviewTileIds
import java.util.UUID

/**
 * Every tap of the cards, and what it asks of the page (plan §3.4). A tap that leaves the view or
 * opens a page sheet over it makes the screen step aside, so one is handed over per opening and a
 * second quick tap is dropped (as Library's). App shortcuts and the tip's ✕ keep the screen.
 *
 * The two writes carry an operation id per (command, target), kept until the page has done it: the
 * tip's until a reply says it was stored (a refused one keeps it, so the same dismissal lands on
 * the next tap), Luck's until the projection shows today's roll (its reply never comes, as the
 * screen has stepped aside). The page recognises a repeat and does not roll twice.
 */
internal class OverviewTaps(
    private val commands: OverviewCommands,
    private val cards: () -> OverviewCards?,
    private val navigate: (String) -> Unit,
    private val say: (String) -> Unit,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private var handedOver = false
    private var tipRequest: String? = null
    private val ops = HashMap<Pair<String, String>, String>()

    /** The operation id a write to [target] would carry now (for tests and diagnostics). */
    fun operation(command: String, target: String): String? = ops[command to target]

    /** Whether the tip's dismissal is waiting for the page's answer. */
    val tipWaiting: Boolean
        get() = tipRequest != null

    /** The screen opened (again): it takes a stepping-aside tap, and waits for no answer. */
    fun opened() {
        handedOver = false
        tipRequest = null
    }

    /** A new projection: a write the page shows as done needs its id no more. */
    fun projected(p: OverviewCards) {
        if (p.tiles.luck.rolled) ops.keys.removeAll { it.first == OverviewCommandSender.LUCK }
        if (p.tip == null) ops.keys.removeAll { it.first == OverviewCommandSender.TIP }
    }

    /** A page view ("character", "history", "settings", "pet", or a move's). */
    fun go(view: String) = step { navigate(view) }

    /** A Next move: its view, the attribute's sheet over the Next moves tile, or Daily Luck. */
    fun move(go: MoveGo) =
        when (go) {
            is MoveGo.View -> go(go.view)
            is MoveGo.Attr -> attr(go.id, OverviewTileIds.NEXT)
            MoveGo.Luck -> luck()
        }

    /** An attribute's sheet, with [tile] (the tapped one) shown behind it. */
    fun attr(id: String, tile: String = OverviewTileIds.ATTRS) = step { commands.attr(id, tile) }

    fun luck() = step {
        val key = OverviewCommandSender.LUCK to (cards()?.head?.date ?: "")
        commands.luck(ops.getOrPut(key, newId))
    }

    fun shop() = step { commands.shop() }

    fun health() = step { commands.health() }

    fun appAdd() = step { commands.appAdd() }

    fun appsEdit() = step { commands.appsEdit() }

    fun edit() = step { commands.edit() }

    /** A glance tap: [act] on [id] ("" or "glanceOpen" only opens Life Director). */
    fun plan(act: String, id: String) = step { commands.plan(act, id) }

    /** An app shortcut: launched without leaving the cards; only one the page lists. */
    fun appLaunch(pkg: String) {
        val apps = cards()?.tiles?.apps ?: return
        if (apps.items.any { it.pkg == pkg }) commands.appLaunch(pkg)
    }

    /** The tip's ✕: once, until the page answers. */
    fun dismissTip() {
        val tip = cards()?.tip ?: return
        if (tipRequest != null) return
        val op = ops.getOrPut(OverviewCommandSender.TIP to tip.key, newId)
        tipRequest = commands.tip(op)
    }

    /** Whether [reply] answered one of these taps. */
    fun onReply(reply: OverviewReply): Boolean {
        if (reply.command != OverviewCommandSender.TIP || reply.requestId != tipRequest)
            return false
        tipRequest = null
        if (reply.ok) ops.keys.removeAll { it.first == OverviewCommandSender.TIP }
        else say(reply.message.ifBlank { NOT_SAVED })
        return true
    }

    private fun step(tap: () -> Unit) {
        if (handedOver || cards() == null) return
        handedOver = true
        tap()
    }

    private companion object {
        const val NOT_SAVED = "Nothing was changed."
    }
}
