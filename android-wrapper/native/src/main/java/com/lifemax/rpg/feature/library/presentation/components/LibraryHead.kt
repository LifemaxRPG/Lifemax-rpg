package com.lifemax.rpg.feature.library.presentation.components

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
import com.lifemax.rpg.core.designsystem.components.PageHead
import com.lifemax.rpg.feature.library.domain.LibraryHeader
import com.lifemax.rpg.feature.library.domain.LibraryPane

/** The Library's Media/Journal switch (.libseg). Choosing another pane goes to its page view. */
@Composable
internal fun LibrarySwitch(panes: List<LibraryPane>, onOpen: (String) -> Unit) {
    val c = LocalLibraryColors.current
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
        panes.forEach { pane ->
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

/** The page head (.pagehead at phone width): eyebrow, title, blurb, and the add button below. */
@Composable
internal fun LibraryHead(header: LibraryHeader, onAdd: () -> Unit) =
    PageHead(header.eyebrow, header.title, header.blurb, Modifier.padding(bottom = 18.dp)) {
        LibraryButton(header.add, onAdd, it, primary = true)
    }
