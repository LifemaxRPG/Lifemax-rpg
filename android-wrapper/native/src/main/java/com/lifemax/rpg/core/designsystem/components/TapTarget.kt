package com.lifemax.rpg.core.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/**
 * Makes an element tappable: at least 48 dp each way, announced as [role], with [label] saying what
 * a tap does when the element's own text does not. The click spans the whole 48 dp, so the ripple
 * and what TalkBack outlines cover it all; put it inside the element's clip, so the ripple takes
 * its shape.
 */
fun Modifier.tapTarget(role: Role, label: String? = null, onClick: () -> Unit): Modifier =
    clickable(onClickLabel = label, role = role, onClick = onClick)
        .minimumInteractiveComponentSize()

/**
 * Makes an element a switch: a tap asks for the other state through [onToggle], and TalkBack
 * announces it as a switch that is [on] or off, and dimmed when not [enabled]. Put it on the whole
 * row a switch names, so the row's words are what TalkBack reads. It is at least 48 dp each way
 * when [reserve] is true; a box in a row too tight to grow keeps its own size, and Compose still
 * widens its touch area to 48 dp around it.
 */
fun Modifier.toggleTarget(
    on: Boolean,
    enabled: Boolean = true,
    reserve: Boolean = true,
    onToggle: (Boolean) -> Unit,
): Modifier =
    toggleable(on, enabled = enabled, role = Role.Switch, onValueChange = onToggle)
        .then(if (reserve) Modifier.minimumInteractiveComponentSize() else Modifier)
