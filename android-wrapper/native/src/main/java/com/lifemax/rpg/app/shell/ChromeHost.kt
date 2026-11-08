package com.lifemax.rpg.app.shell

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.webkit.WebView
import com.lifemax.rpg.app.shell.audio.AudioDockHost
import com.lifemax.rpg.app.shell.audio.insetsWithDock
import com.lifemax.rpg.app.shell.swipe.TabSwipe
import org.json.JSONObject

/**
 * The Overview's own bars (its shell). Only one set of bars may be visible, so the shared bars hide
 * these.
 */
internal object OverviewBars {
    var shell: LiveOverviewShell? = null

    fun hide() {
        shell?.root?.visibility = View.INVISIBLE
    }

    /** The Overview's bars came or went: the audio dock follows them. */
    fun changed(activity: Activity) = AudioDockHost.barsChanged(activity)
}

/**
 * One shared top and bottom bar for the native tab screens. Switching between Character and Food
 * used to tear the bars down and build them again (new views, new software layers, a fresh draw).
 * Now the same bars stay on screen and only get the new scene; they are dropped shortly after the
 * last native screen leaves.
 *
 * Replies from the page can arrive out of order when you switch quickly, so every scene carries the
 * page's running sequence number and a scene older than the one already showing is ignored. Only
 * the screen that currently owns the bars may release them. A scene that is drawn also brings the
 * tab swipe its state (`swipe`), so the swipe follows the same sequence and stale rule.
 */
internal object ChromeHost {
    private var shell: LiveOverviewShell? = null
    private var listener: ((String) -> Unit)? = null
    private var owner: Any? = null
    private var lastSequence = -1
    private var lastShownAt = 0L
    private var ownerPane = ""
    /**
     * how far each native screen is scrolled from its top (px), so the header is right when a
     * screen comes back
     */
    private val scrolled = HashMap<String, Float>()

    /**
     * Native screen [pane] is scrolled [px] from its top; the header follows it while that screen
     * owns the bars.
     */
    fun scrolled(pane: String, px: Float) {
        scrolled[pane] = px
        if (pane == ownerPane) shell?.followScroll(px)
    }

    private val handler = Handler(Looper.getMainLooper())

    /**
     * A finger is scrubbing along the bar (or its pill has not landed yet): the bars stay up even
     * if the page passes over a web tab, which has no native screen to own them. A release asked
     * for meanwhile waits in [releaseWaiting] and runs once the scrub has landed.
     */
    private var scrubHeld = false
    private var releaseWaiting = false
    private val holds = ChromeHold()
    private val release = Runnable {
        holds.cancel()
        scrubHeld = false
        releaseWaiting = false
        shell?.close()
        shell = null
        listener = null
        owner = null
        AudioDockHost.refresh() // no native bars: no dock either
    }

    /**
     * Shows [scene] in the shared bars for [owner]. A screen [opening] becomes the owner even when
     * its scene is older than the last one shown (scenes arrive out of order when you switch
     * quickly), so the screen it replaces, leaving, does not take the bars away under it; only the
     * stale scene itself is not drawn. A late scene from an update changes nothing.
     */
    fun show(
        owner: Any,
        activity: Activity,
        web: WebView,
        scene: JSONObject,
        opening: Boolean = false,
        pane: String = "",
        onAction: (String) -> Unit,
    ): LiveOverviewShell {
        handler.removeCallbacks(release)
        holds.cancel()
        releaseWaiting = false
        val existing = shell
        val sequence = scene.optInt("sequence")
        val now = SystemClock.uptimeMillis()
        val stale = existing != null && sequence < lastSequence && now - lastShownAt < 3000
        if (stale && !opening) return existing!!
        this.owner = owner
        listener = onAction
        val paneChanged = pane != ownerPane
        ownerPane = pane
        val current =
            existing
                ?: LiveOverviewShell(activity, web, chromeOnly = true) { id ->
                        listener?.invoke(id)
                    }
                    .also { shell = it }
        // an older scene that arrives late must not overwrite a newer one (the highlighted tab
        // would go back), nor reach the tab swipe: only a scene the bars draw does (A34)
        if (!stale) {
            current.show(scene)
            TabSwipe.scene(scene.optJSONObject("swipe"), sequence)
            lastSequence = sequence
            lastShownAt = now
        }
        if (paneChanged) current.followScroll(scrolled[pane] ?: 0f)
        OverviewBars.hide()
        // the audio dock follows the bars (it sits above them), and a screen added after the bars
        // or the dock must cover neither
        AudioDockHost.barsChanged(activity)
        AudioDockHost.arrange(current.root)
        return current
    }

    /**
     * The shared bars' bottom bar in px, or the Overview's own while it shows; -1 with no native
     * bars up. The audio dock sits on top of it.
     */
    fun bottomBarPx(): Int =
        shell?.navPx
            ?: OverviewBars.shell?.takeIf { it.root.visibility == View.VISIBLE }?.navPx
            ?: -1

    /**
     * What a native screen under the shared bars clears at its top and bottom (dp): the bars' own
     * and, at the bottom, the audio dock while it shows. Null while no shared bars are up.
     */
    fun insetsDp(): Pair<Float, Float>? =
        shell?.contentInsetsDp()?.let { (top, bottom) ->
            insetsWithDock(top, bottom, AudioDockHost.heightDp)
        }

    /**
     * A native screen went away. When it is moving to another native tab, that tab opened first and
     * now owns the bars, so this does nothing. When it is moving to a web tab, the bars go almost
     * at once.
     */
    fun leave(by: Any) {
        if (owner !== by) return
        handler.removeCallbacks(release)
        if (scrubHeld) {
            releaseWaiting = true
            return
        }
        if (holds.leave()) handler.postDelayed(release, 60)
    }

    /**
     * A tab swipe's reveal (phase 2) needs the bars until it [unhold]s [token]: a [leave] meanwhile
     * waits for it. [closeAll] and [closeNow] still release at once.
     */
    fun hold(token: String) = holds.hold(token)

    /** The reveal is over: a release that waited for it runs now. */
    fun unhold(token: String) {
        if (holds.unhold(token)) {
            handler.removeCallbacks(release)
            release.run()
        }
    }

    /**
     * A scrub along the shared bottom bar needs the bars to stay ([on]) until it has landed. When
     * it lets go and a screen left for a web tab meanwhile, the bars go as they would have.
     */
    fun scrubHold(on: Boolean) {
        scrubHeld = on
        if (!on && releaseWaiting) {
            releaseWaiting = false
            if (holds.leave()) handler.postDelayed(release, 60)
        }
    }

    /** The Overview shell is taking over: no shared bars may stay on screen. */
    fun closeAll() {
        handler.removeCallbacks(release)
        release.run()
    }

    fun closeNow(by: Any) {
        if (owner != null && owner !== by) return
        handler.removeCallbacks(release)
        release.run()
    }
}
