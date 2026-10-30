package com.lifemax.rpg

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.webkit.WebView
import org.json.JSONObject

/** The Overview's own bars (its shell). Only one set of bars may be visible, so the shared bars hide these. */
internal object OverviewBars {
    var shell: LiveOverviewShell? = null

    fun hide() {
        shell?.root?.visibility = View.INVISIBLE
    }
}

/**
 * One shared top and bottom bar for the native tab screens. Switching between Character and Food used to
 * tear the bars down and build them again (new views, new software layers, a fresh draw). Now the same bars
 * stay on screen and only get the new scene; they are dropped shortly after the last native screen leaves.
 *
 * Replies from the page can arrive out of order when you switch quickly, so every scene carries the page's
 * running sequence number and a scene older than the one already showing is ignored. Only the screen that
 * currently owns the bars may release them.
 */
internal object ChromeHost {
    private var shell: LiveOverviewShell? = null
    private var listener: ((String) -> Unit)? = null
    private var owner: Any? = null
    private var lastSequence = -1
    private var lastShownAt = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val release = Runnable {
        shell?.close()
        shell = null
        listener = null
        owner = null
    }

    fun show(
        owner: Any,
        activity: Activity,
        web: WebView,
        scene: JSONObject,
        onAction: (String) -> Unit,
    ): LiveOverviewShell {
        handler.removeCallbacks(release)
        val existing = shell
        val sequence = scene.optInt("sequence")
        val now = SystemClock.uptimeMillis()
        // an older scene that arrives late must not overwrite a newer one (the highlighted tab would go back)
        if (existing != null && sequence < lastSequence && now - lastShownAt < 3000) return existing
        this.owner = owner
        listener = onAction
        val current =
            existing
                ?: LiveOverviewShell(activity, web, chromeOnly = true) { id -> listener?.invoke(id) }
                    .also { shell = it }
        current.show(scene)
        OverviewBars.hide()
        lastSequence = sequence
        lastShownAt = now
        // a screen added after the bars must not cover them
        val parent = current.root.parent as? android.view.ViewGroup
        if (parent != null && parent.indexOfChild(current.root) != parent.childCount - 1)
            current.root.bringToFront()
        return current
    }

    /**
     * A native screen went away. When it is moving to another native tab, that tab opened first and now owns
     * the bars, so this does nothing. When it is moving to a web tab, the bars go almost at once.
     */
    fun leave(by: Any) {
        if (owner !== by) return
        handler.removeCallbacks(release)
        handler.postDelayed(release, 60)
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
