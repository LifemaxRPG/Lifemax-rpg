package com.lifemax.rpg.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Whether the keyboard is up (NAV-5's [ImeState], which MainActivity's insets listener sets). The
 * activity already pads the content above the keyboard and the shared bottom bar hides while it is
 * up, so a screen with a field drops the room it keeps for the bar and brings the field's cursor
 * into view again when this changes.
 */
@Composable
fun rememberImeUp(): Boolean {
    var up by remember { mutableStateOf(ImeState.up) }
    DisposableEffect(Unit) {
        val listener: (Boolean) -> Unit = { up = it }
        ImeState.listen(listener)
        up = ImeState.up
        onDispose { ImeState.forget(listener) }
    }
    return up
}
