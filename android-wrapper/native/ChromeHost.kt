package com.lifemax.rpg

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import org.json.JSONObject

/**
 * One shared top and bottom bar for the native tab screens. Switching between Character and Food used to
 * tear the bars down and build them again (new views, new software layers, a fresh draw). Now the same bars
 * stay on screen and only get the new scene; they are dropped shortly after the last native screen leaves.
 */
internal object ChromeHost {
    private var shell: LiveOverviewShell? = null
    private var listener: ((String) -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private val release = Runnable {
        shell?.close()
        shell = null
        listener = null
    }

    fun show(
        activity: Activity,
        web: WebView,
        scene: JSONObject,
        onAction: (String) -> Unit,
    ): LiveOverviewShell {
        handler.removeCallbacks(release)
        listener = onAction
        val current =
            shell
                ?: LiveOverviewShell(activity, web, chromeOnly = true) { id -> listener?.invoke(id) }
                    .also { shell = it }
        current.show(scene)
        // a screen added after the bars must not cover them
        val parent = current.root.parent as? android.view.ViewGroup
        if (parent != null && parent.indexOfChild(current.root) != parent.childCount - 1)
            current.root.bringToFront()
        return current
    }

    /** A native screen went away. The bars stay unless nothing else picks them up soon. */
    fun leave() {
        handler.removeCallbacks(release)
        handler.postDelayed(release, 450)
    }

    fun closeNow() {
        handler.removeCallbacks(release)
        release.run()
    }
}
