package com.lifemax.rpg.app.shell

/**
 * Whether the shared bars may leave yet (tab swipe phase 2, plans/TAB_SWIPE.md §5). A screen that
 * goes to a web page lets the bars go 60 ms later, but during a swipe's reveal the veil lies over
 * the WebView, its own bars included, and under the shared bars: they must stay until the veil has
 * faded, or the veil would show where they were. The reveal holds them with its token and unholds
 * them after the fade; a release asked for meanwhile waits and then runs at once. A screen that
 * takes the bars over, and the Overview shell's or a removal's immediate release, end the wait.
 * Pure, for ChromeHost on the main thread.
 */
internal class ChromeHold {
    private var token: String? = null
    private var waiting = false

    val held: Boolean
        get() = token != null

    /** A reveal needs the bars until [unhold] with the same token. */
    fun hold(token: String) {
        this.token = token
    }

    /** The reveal is over. True when a release waited for it: run it now. */
    fun unhold(token: String): Boolean {
        if (this.token != token) return false
        this.token = null
        return waiting.also { waiting = false }
    }

    /** The owner left for a web page. True when the release may be posted now; else it waits. */
    fun leave(): Boolean {
        if (token == null) return true
        waiting = true
        return false
    }

    /** No release is waiting any more: a screen took the bars, or they were released at once. */
    fun cancel() {
        waiting = false
    }
}
