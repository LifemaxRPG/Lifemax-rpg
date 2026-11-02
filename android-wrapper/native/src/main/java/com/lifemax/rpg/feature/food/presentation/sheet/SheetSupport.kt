package com.lifemax.rpg.feature.food.presentation.sheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.lifemax.rpg.app.shell.ImeState
import com.lifemax.rpg.core.designsystem.form.FormKitLook
import com.lifemax.rpg.feature.food.presentation.BlueAccent
import com.lifemax.rpg.feature.food.presentation.FoodContext
import kotlinx.coroutines.delay

/**
 * A box the player types in whose value the page holds (the search, the grams, a target): the text
 * is the box's own while typing and goes to [send] after [waitMs] without a key. A value from the
 * page replaces it only when it means something else than what was last sent ([same] compares), so
 * the page's echo of "1" never eats the point of "1." being typed, while a tab that clears the
 * search or a preset that fills a target does come back into the box.
 */
@Composable
internal fun rememberSynced(
    page: String,
    waitMs: Long,
    same: (String, String) -> Boolean = { a, b -> a == b },
    send: (String) -> Unit,
): MutableState<String> {
    val text = remember { mutableStateOf(page) }
    var sent by remember { mutableStateOf(page) }
    val latest by rememberUpdatedState(send)
    LaunchedEffect(page) {
        if (!same(page, sent)) {
            text.value = page
            sent = page
        }
    }
    LaunchedEffect(text.value) {
        val now = text.value
        if (now != sent) {
            delay(waitMs)
            sent = now
            latest(now)
        }
    }
    return text
}

/**
 * Two amounts that are the same number ("1" and "1."), or the same text when they are not numbers.
 */
internal fun sameNumber(a: String, b: String): Boolean {
    val x = a.toDoubleOrNull()
    val y = b.toDoubleOrNull()
    return if (x != null && y != null) x == y else a == b
}

/**
 * Whether the keyboard is up (NAV-5's ImeState, which MainActivity's insets listener sets). The
 * shared bottom bar hides while it is, and the content is already lifted above the keyboard, so the
 * sheet drops the room it keeps for the bar.
 */
@Composable
internal fun rememberImeUp(): Boolean {
    var up by remember { mutableStateOf(ImeState.up) }
    DisposableEffect(Unit) {
        val listener: (Boolean) -> Unit = { up = it }
        ImeState.listen(listener)
        up = ImeState.up
        onDispose { ImeState.forget(listener) }
    }
    return up
}

/**
 * The form kit (core/designsystem/form) in Food's palette, dark or light, as the page's .fv2-form
 * inputs look: the sunken well, the panel's edge, the blue focus.
 */
internal fun foodFormLook(c: FoodContext): FormKitLook =
    FormKitLook(
        ink = c.fg,
        ink2 = c.muted,
        ink3 = c.subtle,
        well = c.sunken,
        edge = c.panelBorder,
        segEdge = c.panelBorder,
        focus = BlueAccent,
        focusRing = BlueAccent.copy(alpha = .2f),
        segOn = BlueAccent.copy(alpha = .18f),
        segOnEdge = BlueAccent.copy(alpha = .4f),
        chipFill = c.sunken,
        error = if (c.light) Color(0xFFC62828) else Color(0xFFFF9B9B),
        cursor = BlueAccent,
    )

/** The page's --warn, for the targets' line when the macros overshoot. */
internal fun warnColor(c: FoodContext): Color =
    if (c.light) Color(0xFFC2410C) else Color(0xFFF0A35A)
