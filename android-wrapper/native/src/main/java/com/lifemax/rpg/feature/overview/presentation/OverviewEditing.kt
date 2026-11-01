package com.lifemax.rpg.feature.overview.presentation

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lifemax.rpg.core.designsystem.feedback.LocalPageBuzz
import com.lifemax.rpg.core.designsystem.feedback.play
import com.lifemax.rpg.feature.overview.domain.EditorBuzz
import com.lifemax.rpg.feature.overview.domain.EditorRow
import com.lifemax.rpg.feature.overview.domain.OverviewCards
import com.lifemax.rpg.feature.overview.presentation.editor.DragPreview
import com.lifemax.rpg.feature.overview.presentation.editor.OverviewEditor

/**
 * What the screen's editor shows and does (phase 2): open or not, its rows in the layout shown (the
 * page's, or a change on its way), and its taps. [preview] freezes a lifted row (screenshots).
 */
class OverviewEditing(
    val open: Boolean = false,
    val rows: List<EditorRow> = emptyList(),
    val reorder: (order: List<String>, then: EditorBuzz) -> Unit = { _, _ -> },
    val toggle: (String) -> Unit = {},
    val reset: () -> Unit = {},
    val done: () -> Unit = {},
    val preview: DragPreview? = null,
    /** Whether the editor's open animation plays (off for a still screenshot). */
    val animateOpen: Boolean = true,
)

/**
 * The editor in place of the tiles, inside the screen's list: a lifted row near the window's edge
 * scrolls [list], and the page's buzzes play at the owner's strength (none with haptics off).
 */
@Composable
internal fun OverviewEditorItem(
    cards: OverviewCards,
    editing: OverviewEditing,
    visible: Boolean,
    list: LazyListState,
) {
    val buzz = LocalPageBuzz.current
    OverviewEditor(
        rows = editing.rows,
        onReorder = editing.reorder,
        onReset = editing.reset,
        onDone = editing.done,
        onToggle = editing.toggle,
        buzz = { buzz?.play(cards.haptics, *it.timings) },
        words = cards.editor,
        reducedMotion = cards.reducedMotion,
        visible = visible,
        animateOpen = editing.animateOpen,
        autoScroll = { list.dispatchRawDelta(it) },
        preview = editing.preview,
    )
}

/**
 * The list follows the editor as the page's does: opening shows the editor where the tiles were
 * (scrolled to it when the list is below it, as scrollIntoView "nearest"), and Done goes back to
 * the top, smoothly unless motion is reduced. Leaving the screen ends edit mode quietly, with no
 * scroll (the page's ovEditClose(quiet)). [editorIndex] is the editor's item.
 */
@Composable
internal fun FollowEditor(
    open: Boolean,
    editorIndex: Int,
    list: LazyListState,
    reduced: Boolean,
    visible: Boolean,
) {
    var was by remember { mutableStateOf(open) }
    LaunchedEffect(open) {
        if (open == was) return@LaunchedEffect
        was = open
        when {
            open && list.firstVisibleItemIndex > editorIndex -> list.scrollToItem(editorIndex)
            !open && !visible -> Unit
            !open && reduced -> list.scrollToItem(0)
            !open -> list.animateScrollToItem(0)
        }
    }
}
