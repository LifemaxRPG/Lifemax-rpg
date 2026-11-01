package com.lifemax.rpg.app.shell

import org.junit.Assert.assertEquals
import org.junit.Test

class ImeStateTest {
    @Test
    fun listenersHearOnlyChangesAndStopHearingWhenForgotten() {
        val heard = mutableListOf<Boolean>()
        val listener: (Boolean) -> Unit = { heard += it }
        ImeState.set(false)
        ImeState.listen(listener)
        ImeState.set(true)
        ImeState.set(true) // the insets listener runs on every insets pass: no repeat
        ImeState.set(false)
        ImeState.forget(listener)
        ImeState.set(true)
        assertEquals(listOf(true, false), heard)
        ImeState.set(false)
    }
}
