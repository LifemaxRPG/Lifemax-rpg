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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LocalPageColors

/** One side of a page's switch: the page view it opens, its label, and whether it is this one. */
data class PageSegment(val id: String, val label: String, val current: Boolean)

/**
 * The page's switch between the panes of one tab (.libseg), in PageTheme's colours: the Library's
 * Media/Journal. Each pane's screen draws this one at the same place, so the pills never move when
 * one screen hands over to the other. Choosing another pane goes to its page view.
 */
@Composable
fun PageSegments(segments: List<PageSegment>, onOpen: (String) -> Unit) {
    val c = LocalPageColors.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.padding(bottom = 14.dp)
            .clip(shape)
            .background(c.panel2)
            .border(1.dp, c.line, shape)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        segments.forEach { pane ->
            Text(
                pane.label,
                Modifier.clip(RoundedCornerShape(9.dp))
                    .then(if (pane.current) Modifier.background(c.accentDim) else Modifier)
                    .selectable(pane.current, role = Role.Tab) {
                        if (!pane.current) onOpen(pane.id)
                    }
                    .padding(horizontal = 18.dp, vertical = 7.dp),
                color = if (pane.current) c.accentStrong else c.muted,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 20.25.sp,
            )
        }
    }
}
