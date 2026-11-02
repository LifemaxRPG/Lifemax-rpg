package com.lifemax.rpg.feature.library.presentation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.core.designsystem.components.PageHead
import com.lifemax.rpg.core.designsystem.components.PageSegment
import com.lifemax.rpg.core.designsystem.components.PageSegments
import com.lifemax.rpg.feature.library.domain.LibraryHeader
import com.lifemax.rpg.feature.library.domain.LibraryPane

/**
 * The Library's Media/Journal switch (.libseg): core's, which the native Journal draws too.
 * Choosing another pane goes to its page view.
 */
@Composable
internal fun LibrarySwitch(panes: List<LibraryPane>, onOpen: (String) -> Unit) =
    PageSegments(panes.map { PageSegment(it.id, it.label, it.current) }, onOpen)

/** The page head (.pagehead at phone width): eyebrow, title, blurb, and the add button below. */
@Composable
internal fun LibraryHead(header: LibraryHeader, onAdd: () -> Unit) =
    PageHead(header.eyebrow, header.title, header.blurb, Modifier.padding(bottom = 18.dp)) {
        LibraryButton(header.add, onAdd, it, primary = true)
    }
