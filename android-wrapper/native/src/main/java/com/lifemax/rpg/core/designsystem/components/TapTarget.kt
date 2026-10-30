package com.lifemax.rpg.core.designsystem.components

import androidx.compose.foundation.clickable
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
