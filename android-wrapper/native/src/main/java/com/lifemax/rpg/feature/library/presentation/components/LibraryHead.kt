package com.lifemax.rpg.feature.library.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
internal fun LibraryHead(header: LibraryHeader, onAdd: () -> Unit) {
    val c = LocalLibraryColors.current
    Column(Modifier.padding(bottom = 18.dp)) {
        Text(
            header.eyebrow.uppercase(),
            color = c.accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.32.sp,
            lineHeight = 16.5.sp,
        )
        Text(
            header.title,
            Modifier.padding(top = 6.dp).semantics { heading() },
            color = c.text,
            fontSize = 21.sp,
            fontWeight = FontWeight(650),
            letterSpacing = (-0.63).sp,
            lineHeight = 24.15.sp,
        )
        Text(
            header.blurb,
            Modifier.padding(top = 6.dp),
            color = c.muted,
            fontSize = 13.5.sp,
            lineHeight = 20.25.sp,
        )
        PageButton(header.add, onAdd, Modifier.padding(top = 14.dp), primary = true)
    }
}
