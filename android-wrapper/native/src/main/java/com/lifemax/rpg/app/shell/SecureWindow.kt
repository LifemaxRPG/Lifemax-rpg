package com.lifemax.rpg.app.shell

import android.app.Activity
import android.view.WindowManager

/**
 * The window's FLAG_SECURE (blank in the app switcher and in screenshots), held by whoever needs
 * it: the page's BiometricLock while the page shows its Journal, and the native Journal while it
 * draws a frame with the lock on (CONTRACT A35 item 4). The flag is set while any holder holds it
 * and cleared when the last lets go, so the page clearing its own on leaving the Journal no longer
 * uncovers a native Journal still on screen (make-before-break, a swipe's slide). On the main
 * thread.
 */
object SecureWindow {
    private val holds = SecureHolds()

    /** [owner] wants the window secure ([on]) or no longer; the flag follows every holder. */
    @JvmStatic
    fun hold(activity: Activity?, owner: Any, on: Boolean) {
        val change = holds.hold(owner, on) ?: return
        val window = activity?.window ?: return
        if (change) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

/**
 * Who holds the window secure. [hold] answers the flag's new value when it changes, else null: a
 * second holder, or a release by one that was not holding (a stale release), changes nothing.
 */
class SecureHolds {
    private val holders = LinkedHashSet<Any>()

    val secure: Boolean
        get() = holders.isNotEmpty()

    fun hold(owner: Any, on: Boolean): Boolean? {
        val was = secure
        if (on) holders += owner else holders -= owner
        return secure.takeIf { it != was }
    }
}
