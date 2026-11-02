package com.lifemax.rpg.app.shell.audio

import android.app.Activity
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.lifemax.rpg.app.shell.ChromeHost
import com.lifemax.rpg.app.shell.ImeState

/**
 * The audiobook dock: ONE view owned by the shell, not by any tab, so it is the same dock through a
 * make-before-break hand-over or a swipe (never two). It sits just above the bottom bar of
 * whichever native screen shows (the shared bars, or the Overview's own), between the screens and
 * the bars in the stack, and it shows only while such bars are up: on a view the page draws, the
 * page's own player is the one in use. While it shows it adds its height to what every screen
 * clears ([AudioDockInsets]), and tells the page ([onDock]) so the live Overview's body clears it
 * too. Main thread only.
 */
internal object AudioDockHost {
    /** Whether the owner switched the dock on and the native side may draw it. */
    private var enabled = false
    private var activity: Activity? = null
    private var view: ComposeView? = null
    private var shownHeight = 0f

    /** The page's last push. A snapshot state, so the dock redraws when it changes. */
    private var state by mutableStateOf(AudioDockState())

    /** The page's accept list for the native file picker; null while the dock is off. */
    var accept: AudioAccept? = null

    /** Whether the owner switched the dock on: Play and the picker go to the dock's commands. */
    val isOn: Boolean
        get() = enabled

    /** What the dock asks of the page; set by the plugin. */
    var actions = AudioDockActions()

    /** Told when the dock shows or goes, or changes height: (shown, height in dp). */
    var onDock: (Boolean, Float) -> Unit = { _, _ -> }

    /** Told the same, for the screens' insets. */
    private val insetListeners = LinkedHashSet<() -> Unit>()

    private val keyboard: (Boolean) -> Unit = { refresh() }

    /** The height the dock takes from every screen's bottom: 0 while it is not showing. */
    val heightDp: Float
        get() = shownHeight

    fun listenInsets(listener: () -> Unit) {
        insetListeners += listener
    }

    fun forgetInsets(listener: () -> Unit) {
        insetListeners -= listener
    }

    /** The bars came, went or were redrawn (ChromeHost, the Overview's shell): the dock follows. */
    fun barsChanged(activity: Activity) {
        this.activity = activity
        refresh()
    }

    fun enable(activity: Activity, on: Boolean) {
        this.activity = activity
        enabled = on
        if (on) ImeState.listen(keyboard) else ImeState.forget(keyboard)
        if (!on) state = AudioDockState()
        refresh()
    }

    /** The page's push. */
    fun push(next: AudioDockState) {
        state = next
        refresh()
    }

    /**
     * Puts the stack in order: the screens, then the dock, then the [bars] on top. A screen opened
     * after the dock was added would cover it otherwise. Nothing moves when the order is right.
     */
    fun arrange(bars: View) {
        val parent = bars.parent as? ViewGroup ?: return
        val dock = view?.takeIf { it.parent === parent }
        val n = parent.childCount
        val inOrder =
            parent.indexOfChild(bars) == n - 1 &&
                (dock == null || parent.indexOfChild(dock) == n - 2)
        if (inOrder) return
        dock?.bringToFront()
        bars.bringToFront()
    }

    /** Shows, moves, resizes or removes the dock to match the state and where the bars are. */
    fun refresh() {
        val host = activity ?: return
        val bar = ChromeHost.bottomBarPx()
        val show = dockShows(enabled, state, bar, ImeState.up)
        val height = if (show) state.heightDp else 0f
        if (show) place(host, bar) else unplace()
        if (height != shownHeight) {
            shownHeight = height
            onDock(show, height)
            insetListeners.toList().forEach { it() }
        }
    }

    private fun place(host: Activity, barPx: Int) {
        val density = host.resources.displayMetrics.density
        val params =
            FrameLayout.LayoutParams(
                    -1,
                    (if (state.minimized) AudioDockMetrics.MINIMIZED_DP
                        else AudioDockMetrics.EXPANDED_DP)
                        .times(density)
                        .toInt(),
                    Gravity.BOTTOM,
                )
                .apply { bottomMargin = barPx + (AudioDockMetrics.GAP_DP * density).toInt() }
        val existing = view
        if (existing != null && existing.parent != null) {
            existing.layoutParams = params
            existing.visibility = View.VISIBLE
            return
        }
        val dock =
            ComposeView(host).apply {
                id = View.generateViewId()
                tag = "native-audio-dock"
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    AudioDockRoute(
                        state,
                        AudioDockActions(
                            { actions.toggle() },
                            { actions.skip(it) },
                            { actions.seek(it) },
                            { actions.rate(it) },
                            { actions.minimize() },
                            { actions.close() },
                        ),
                        ::now,
                    )
                }
            }
        host.findViewById<ViewGroup>(android.R.id.content).addView(dock, params)
        view = dock
    }

    private fun unplace() {
        val v = view ?: return
        (v.parent as? ViewGroup)?.removeView(v)
        view = null
    }

    private fun now() = SystemClock.elapsedRealtime()

    /** The clock the state's extrapolation counts from, for [AudioDockState.decode]. */
    fun clock() = now()
}
