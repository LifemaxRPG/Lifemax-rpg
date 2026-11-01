package com.lifemax.rpg.app.shell

/**
 * Whether the keyboard is up (backlog NAV-5). MainActivity's insets listener, which already pads
 * the content above the keyboard, tells it; the shared bottom bar hides while it holds, so a field
 * near the bottom of a screen is never under the bar. Main thread only, as the insets listener and
 * the views are.
 */
object ImeState {
    var up = false
        private set

    private val listeners = LinkedHashSet<(Boolean) -> Unit>()

    @JvmStatic
    fun set(up: Boolean) {
        if (up == this.up) return
        this.up = up
        listeners.toList().forEach { it(up) }
    }

    fun listen(listener: (Boolean) -> Unit) {
        listeners += listener
    }

    fun forget(listener: (Boolean) -> Unit) {
        listeners -= listener
    }
}
