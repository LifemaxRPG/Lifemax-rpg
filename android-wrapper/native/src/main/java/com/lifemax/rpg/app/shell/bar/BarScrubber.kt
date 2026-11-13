package com.lifemax.rpg.app.shell.bar

import kotlin.math.abs

/** The haptic moments of a scrub, as the page's lmaBuzz names them. */
internal enum class ScrubBuzz {
    /** the finger starts scrubbing */
    Start,
    /** a tab was passed */
    Tick,
    /** the finger lifted after a scrub */
    End,
}

/** What the scrubber needs of the bar it sits on. Everything is in dp, on the main thread. */
internal interface ScrubHost {
    /** the bar's visible width */
    val viewport: Float

    /** how far the strip is scrolled, and how far it can be */
    val scroll: Float
    val maxScroll: Float

    fun scrollToDp(dp: Float)

    /** The tab with this id is to open, as a tap on it would. */
    fun open(id: String)

    fun buzz(kind: ScrubBuzz)

    /**
     * The tab under the finger is shown as selected at once (null: back to what the page draws).
     */
    fun highlight(tab: BarTab?)

    /** Run [action] after [delayMs] as [slot]; a later call for the same slot replaces it. */
    fun after(slot: Int, delayMs: Long, action: () -> Unit)

    fun cancel(slot: Int)

    /**
     * A scrub needs the bar to stay put ([on]) until it has landed: the page may pass over a tab
     * that has no native screen, whose bars would otherwise be released under the finger.
     */
    fun hold(on: Boolean) {}
}

/**
 * Finger-scrubbing along the bar, with the page's own semantics (42-scrubnav.js): a slide of a few
 * dp along the bar starts a scrub; the finger then picks a tab ([ScrubMap]), which the bar shows as
 * selected at once and scrolls into view ([ScrubFollow]), with a tick for each tab passed and
 * firmer pulses at the start and the end. The page opens the tab once the finger slows (not while
 * it moves fast), and the last tab when the finger lifts, so a fast swipe stays light. A slide that
 * is more up than along is not a scrub, and a tap is left to the tab buttons.
 *
 * Pure: the clock and the bar come from [ScrubHost], so the gesture can be driven in a test.
 */
internal class BarScrubber(private val host: ScrubHost) {
    /** The page's latest word about the bar; null leaves the bar a plain scroller. */
    var scene: BarScrubScene? = null

    /** The scrub in progress, once the slide has started. */
    val scrubbing: Boolean
        get() = drag?.moved == true

    private class Drag(
        val tabs: List<BarTab>,
        val speed: ScrubSpeed,
        val haptics: Boolean,
        val downX: Float,
        val downY: Float,
        val start: Int,
    ) {
        var moved = false
        var selected = start
        var opened = start
        var lastX = downX
        var lastAt = -1L
        var pace = 0f

        /** The finger moved since the page last looked at its pace. */
        var fresh = false
    }

    private var drag: Drag? = null

    /** The host was asked to keep the bar for a scrub, and has not been told to let go. */
    private var holding = false

    /** The tab a finished scrub ended on, still shown as selected until the page's scene agrees. */
    private var landing: BarTab? = null

    /** A finger went down at ([x], [y]), in dp from the bar's top left. */
    fun down(x: Float, y: Float) {
        if (landing != null) clearLanding()
        val s = scene?.takeIf { it.scrubbable }
        drag = s?.let { Drag(it.tabs, it.speed, it.haptics, x, y, it.activeIndex.coerceAtLeast(0)) }
    }

    /**
     * The finger moved to ([x], [y]) at [now] (ms). True once this is a scrub, which then takes the
     * touch: the buttons under the finger are cancelled.
     */
    fun move(x: Float, y: Float, now: Long): Boolean {
        val d = drag ?: return false
        val dx = x - d.downX
        if (!d.moved) {
            if (abs(dx) < SLOP) return false
            if (abs(y - d.downY) > abs(dx) * 1.5f) {
                drag = null // more up than along: not for the bar
                return false
            }
            d.moved = true
            holding = true
            host.hold(true)
            buzz(d, ScrubBuzz.Start)
            host.highlight(d.tabs[d.selected])
        }
        if (d.lastAt >= 0) {
            val dt = (now - d.lastAt).coerceAtLeast(1)
            d.pace = d.pace * .5f + abs(x - d.lastX) / dt * .5f
            d.fresh = true
        }
        d.lastAt = now
        d.lastX = x
        val i = ScrubMap.indexAt(x, d.downX, host.viewport, d.start, d.tabs.size, d.speed.gain)
        if (i != d.selected) pass(d, i)
        return true
    }

    /** The finger lifted: the tab it is over opens now, if the page is not already there. */
    fun up() {
        val d = drag ?: return
        drag = null
        host.cancel(SETTLE)
        if (!d.moved) return
        buzz(d, ScrubBuzz.End)
        if (d.selected != d.opened) host.open(d.tabs[d.selected].id)
        // the pill stays on the tab until the page's scene shows it there (or a moment passes)
        landing = d.tabs[d.selected]
        host.after(RELEASE, RELEASE_MS) { clearLanding() }
    }

    /** The touch was taken away: the same as a lift, where the finger last was. */
    fun cancel() = up()

    /** A new bar scene came, with the page on [active]: a pill that waited for it is done with. */
    fun sceneArrived(active: String) {
        if (drag == null && landing?.key == active) clearLanding()
    }

    private fun clearLanding() {
        landing = null
        host.cancel(RELEASE)
        host.highlight(null)
        if (holding && drag?.moved != true) {
            holding = false
            host.hold(false)
        }
    }

    private fun pass(d: Drag, i: Int) {
        d.selected = i
        host.highlight(d.tabs[i])
        val tab = d.tabs[i]
        host.scrollToDp(
            ScrubFollow.revealWithNeighbours(
                host.scroll,
                host.viewport,
                tab.left,
                tab.right,
                d.tabs.getOrNull(i - 1)?.left,
                d.tabs.getOrNull(i + 1)?.right,
                host.maxScroll,
            )
        )
        buzz(d, ScrubBuzz.Tick)
        host.after(SETTLE, SETTLE_MS) { settle(d) }
    }

    /** The finger rests on a tab (or crawls): the page goes to it. A fast finger waits a moment. */
    private fun settle(d: Drag) {
        if (drag !== d || d.selected == d.opened) return
        if (d.pace > FAST && d.fresh) {
            // still moving: look again shortly; a finger that sent nothing since has stopped
            d.fresh = false
            host.after(SETTLE, RETRY_MS) { settle(d) }
            return
        }
        d.opened = d.selected
        host.open(d.tabs[d.selected].id)
    }

    private fun buzz(d: Drag, kind: ScrubBuzz) {
        if (d.haptics) host.buzz(kind)
    }

    companion object {
        /** the slide that starts a scrub (the page's `slop`) */
        const val SLOP = 5f

        /** how long the finger rests on a tab before the page goes there (`settle`) */
        const val SETTLE_MS = 100L

        /** the pace above which the page waits (dp per ms; `fast`), and how long it waits */
        const val FAST = .16f
        const val RETRY_MS = 60L

        /** the pill stays a moment after the lift for the page's scene to take over */
        const val RELEASE_MS = 600L
        const val SETTLE = 1
        const val RELEASE = 2
    }
}
