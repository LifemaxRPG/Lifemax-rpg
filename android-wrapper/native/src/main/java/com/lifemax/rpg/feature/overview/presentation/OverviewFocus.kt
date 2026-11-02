package com.lifemax.rpg.feature.overview.presentation

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext

/**
 * Whether a touch-exploring screen reader (TalkBack) is on; it follows the owner turning it on or
 * off.
 */
@Composable
internal fun rememberTouchExploration(): Boolean {
    val manager =
        LocalContext.current.getSystemService(Context.ACCESSIBILITY_SERVICE)
            as? AccessibilityManager
    var on by remember { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { on = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return on
}

/**
 * Where a screen reader's focus goes when the editor closes. Its Done button disappears with the
 * editor, and a focused node that is removed leaves TalkBack with nothing, so it would start again
 * from the top of the list. Edit is the control the owner pressed to get here and the next stop
 * after the head, so the focus returns to it. Only with TalkBack on (a sighted owner sees no focus
 * ring appear), and only while the screen shows: a screen that hides ends the editor quietly and
 * must not take the focus from the screen that replaces it. The returned requester goes on the Edit
 * button.
 */
@Composable
internal fun rememberEditFocus(editing: Boolean, visible: Boolean): FocusRequester {
    val requester = remember { FocusRequester() }
    val exploring = rememberTouchExploration()
    val showing by rememberUpdatedState(visible)
    var was by remember { mutableStateOf(editing) }
    LaunchedEffect(editing) {
        if (was && !editing && showing && exploring) {
            // the button is composed (the head is always there); a refused request leaves focus as
            // it was
            runCatching { requester.requestFocus() }
        }
        was = editing
    }
    return requester
}
