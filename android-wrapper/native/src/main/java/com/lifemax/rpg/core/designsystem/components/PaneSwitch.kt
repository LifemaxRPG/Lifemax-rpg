package com.lifemax.rpg.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The switch between the panes of one bottom tab, as native Food draws its Meals and Recipes. Its
 * type is its own, whatever text style the screen around it sets, so both screens draw it alike and
 * it stays put when one hands over to the other. [onSelect] is called for a pane other than
 * [selected]. The colours are native Food's own, which does not take the page's tokens yet (backlog
 * DS-1), except that a selected pane's label in the light theme is the page's light accent-strong:
 * Food's blue on that pale pill is too faint to read.
 */
@Composable
fun PaneSwitch(
    panes: List<String>,
    selected: String,
    light: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .clip(shape)
            .background(if (light) TrackLight else TrackDark)
            .border(1.dp, if (light) BorderLight else BorderDark, shape)
            .padding(3.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        panes.forEach { pane ->
            val on = pane == selected
            Text(
                pane,
                Modifier.clip(RoundedCornerShape(10.dp))
                    .background(
                        if (!on) Color.Transparent else if (light) PaneOnLight else PaneOnDark
                    )
                    .selectable(on, role = Role.Tab) { if (!on) onSelect(pane) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                color =
                    if (on) (if (light) PaneOnTextLight else PaneOnTextDark)
                    else if (light) PaneTextLight else PaneTextDark,
                style = PaneText,
            )
        }
    }
}

/** Native Food's: its font's own line height, no letter spacing. */
private val PaneText = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

private val TrackDark = Color(0xFF141822)
private val TrackLight = Color(0xFFF7F9FC)
private val BorderDark = Color(0x14FFFFFF)
private val BorderLight = Color(0xFFD2D9E7)
private val PaneOnDark = Color(0xFF252B3B)
private val PaneOnLight = Color(0xFFE8EDF9)
private val PaneOnTextDark = Color(0xFF6E8EFB)
private val PaneOnTextLight = Color(0xFF2F4BB8)
private val PaneTextDark = Color(0xFF8B95A7)
private val PaneTextLight = Color(0xFF64748B)
