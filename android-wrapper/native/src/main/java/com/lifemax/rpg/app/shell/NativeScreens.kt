package com.lifemax.rpg.app.shell

import android.webkit.WebView
import com.getcapacitor.WebViewListener
import com.lifemax.rpg.app.shell.swipe.TabSwipe

/**
 * Every native screen is drawn over the page that opened it, and only that page knows it is there.
 * Whenever the WebView starts loading a page, each host takes its screen away at once, the shared
 * bars with it, so the new page starts from a clean slate. This is the net under any navigation the
 * page did not close its screens for, such as a same-origin link it follows. The page's own reloads
 * close every tab's screen first (lmaLeaveNative), but the page cannot close Cash: an import that
 * reloads the page while Cash is open (Cash opened during the import's folder backup) is caught
 * only here. LiveWeb's switch at start comes before anything opens, and its switch at run time is
 * the page's own restart. A tab switch loads no page.
 *
 * Hosts register here, not with the bridge: Capacitor replaces the bridge's listener list after it
 * has loaded the plugins, so a listener added in a plugin's load() is lost. MainActivity hands this
 * one to the bridge builder instead. The tab swipe is not a host: it forgets the page first, so the
 * removals that follow are plain teardown rather than a swipe cut short. A host's drop may add or
 * remove hosts (a first touch of some object), so the drops run over a copy. Main thread only.
 * Contract: A23.
 */
internal object NativeScreens : WebViewListener() {
    private val hosts = LinkedHashMap<Any, () -> Unit>()

    /** [host] draws a screen over the page; [drop] takes it away at once. */
    fun add(host: Any, drop: () -> Unit) {
        hosts[host] = drop
    }

    fun remove(host: Any) {
        hosts.remove(host)
    }

    override fun onPageStarted(webView: WebView?) {
        OverviewLaunch.cancel()
        TabSwipe.pageStarted()
        hosts.values.toList().forEach { it() }
    }
}
