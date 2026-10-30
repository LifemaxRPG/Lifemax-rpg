package com.lifemax.rpg

import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Toast

/**
 * Real tap-to-screen timing for the native tabs, measured on the phone: the moment a bar button is
 * touched, the moment the screen's open call arrives from the page, and the second frame drawn
 * after the new screen is attached (the first frame is when it is composed). Optional toasts show
 * the numbers (Settings, Appearance).
 */
internal object TabTiming {
    @Volatile private var tapAt = 0L
    @Volatile private var openAt = 0L
    @Volatile private var tapId = ""
    @Volatile var toasts = false
    @Volatile
    var lastSummary = ""
        private set

    fun tap(id: String) {
        tapAt = SystemClock.uptimeMillis()
        openAt = 0L
        tapId = id
    }

    /** the page's open call for a native screen reached the main thread */
    fun opened() {
        if (tapAt != 0L && openAt == 0L) openAt = SystemClock.uptimeMillis()
    }

    /** the new screen is attached; report when its second frame has been drawn */
    fun attached(view: View, label: String) {
        val t0 = tapAt
        if (t0 == 0L) return
        tapAt = 0L
        if (SystemClock.uptimeMillis() - t0 > 5000) return
        val tOpen = openAt
        val tAttach = SystemClock.uptimeMillis()
        var frames = 0
        view.postInvalidateOnAnimation()
        view.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (++frames < 2) {
                        view.postInvalidateOnAnimation()
                        return true
                    }
                    if (view.viewTreeObserver.isAlive)
                        view.viewTreeObserver.removeOnPreDrawListener(this)
                    val now = SystemClock.uptimeMillis()
                    val toOpen = if (tOpen > 0) tOpen - t0 else -1
                    lastSummary =
                        "$tapId → $label  tap→open ${toOpen}  attach ${tAttach - (if (tOpen > 0) tOpen else t0)}" +
                            "  drawn ${now - tAttach}  total ${now - t0} ms"
                    if (toasts)
                        Toast.makeText(
                                view.context,
                                "$label ${now - t0} ms (open ${toOpen})",
                                Toast.LENGTH_SHORT,
                            )
                            .show()
                    return true
                }
            }
        )
    }
}
