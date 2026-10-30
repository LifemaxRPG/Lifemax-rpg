package com.lifemax.rpg.app.shell

import android.webkit.WebView
import com.getcapacitor.WebViewListener

/**
 * Every native screen is drawn over the page that opened it, and only that page knows it is there.
 * Whenever the WebView starts loading a page, each host takes its screen away at once, the shared
 * bars with it, so the new page starts from a clean slate. This covers the reloads the page does
 * not start itself (LiveWeb switching builds, an error page, any other navigation); the page's own
 * reloads close every screen first (lmaLeaveNative). A tab switch loads no page.
 *
 * Hosts register here, not with the bridge: Capacitor replaces the bridge's listener list after it
 * has loaded the plugins, so a listener added in a plugin's load() is lost. MainActivity hands this
 * one to the bridge builder instead. Main thread only. Contract: A23.
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
        hosts.values.forEach { it() }
    }
}
