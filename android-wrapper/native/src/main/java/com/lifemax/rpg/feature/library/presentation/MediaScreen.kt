package com.lifemax.rpg.feature.library.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.core.designsystem.CssText
import com.lifemax.rpg.core.designsystem.LifeMaxTheme
import com.lifemax.rpg.core.designsystem.components.PageToast
import com.lifemax.rpg.core.designsystem.components.playToast
import com.lifemax.rpg.feature.library.domain.MediaAction
import com.lifemax.rpg.feature.library.domain.MediaProjection
import com.lifemax.rpg.feature.library.domain.PageNotice
import com.lifemax.rpg.feature.library.presentation.components.FeatureTipCard
import com.lifemax.rpg.feature.library.presentation.components.LibraryColors
import com.lifemax.rpg.feature.library.presentation.components.LibraryHead
import com.lifemax.rpg.feature.library.presentation.components.LibrarySwitch
import com.lifemax.rpg.feature.library.presentation.components.ListMessageCard
import com.lifemax.rpg.feature.library.presentation.components.LocalLibraryColors
import com.lifemax.rpg.feature.library.presentation.components.MediaFilters
import com.lifemax.rpg.feature.library.presentation.components.MediaRow
import com.lifemax.rpg.feature.library.presentation.components.MediaStats
import com.lifemax.rpg.feature.library.presentation.components.cardPiece

@Composable
fun MediaRoute(
    vm: MediaViewModel,
    visible: Boolean,
    topInsetDp: Float,
    bottomInsetDp: Float,
    onPage: (MediaAction) -> Unit,
) {
    val ui = vm.ui ?: return
    val actions = remember(vm, onPage) { MediaActions(vm, onPage) }
    MediaScreen(ui, actions, visible, topInsetDp, bottomInsetDp, vm.notice)
}

/** Everything the screen can ask for, so the screen itself holds no state. */
class MediaActions(
    val search: (String) -> Unit = {},
    val showStatus: (String) -> Unit = {},
    val showType: (String) -> Unit = {},
    val page: (MediaAction) -> Unit = {},
    val noticeShown: (PageNotice) -> Unit = {},
) {
    /** A tap for the page goes only while the view model hands one over (one per opening). */
    constructor(
        vm: MediaViewModel,
        page: (MediaAction) -> Unit,
    ) : this(
        vm::search,
        vm::showStatus,
        vm::showType,
        { if (vm.handOver()) page(it) },
        vm::noticeShown,
    )
}

/**
 * The Media pane as the page lays it out on a phone: the Media/Journal switch, the page head, the
 * feature tip, the stats, then one card holding the filters and the rows (or what shows in their
 * place), and the page's [notice] as its toast. The rows are items of the list, keyed by the page's
 * item ids, so the card is drawn in a piece per item. A hidden screen ([visible] false) gives up
 * the keyboard and any open menu, and draws no toast.
 */
@Composable
fun MediaScreen(
    ui: MediaUi,
    actions: MediaActions,
    visible: Boolean = true,
    topInsetDp: Float = 64f,
    bottomInsetDp: Float = 72f,
    notice: PageNotice? = null,
    list: LazyListState = rememberLazyListState(),
) {
    val p = ui.projection
    val focus = LocalFocusManager.current
    LaunchedEffect(visible) { if (!visible) focus.clearFocus() }
    LibraryTheme(p.tokens, p.light) {
        val c = LocalLibraryColors.current
        Box(Modifier.fillMaxSize().background(c.bg)) {
            MediaList(ui, actions, visible, topInsetDp, bottomInsetDp, list)
            if (visible && notice != null)
                NoticeToast(
                    notice,
                    p,
                    Modifier.align(Alignment.BottomCenter)
                        .padding(start = 12.dp, end = 12.dp, bottom = bottomInsetDp.dp + 12.dp),
                    actions.noticeShown,
                )
        }
    }
}

/** The switch, the head, the tip and the stats, then the card of filters and rows, in one list. */
@Composable
private fun MediaList(
    ui: MediaUi,
    actions: MediaActions,
    visible: Boolean,
    topInsetDp: Float,
    bottomInsetDp: Float,
    list: LazyListState,
) {
    val p = ui.projection
    val c = LocalLibraryColors.current
    LazyColumn(
        Modifier.fillMaxSize().imePadding(),
        state = list,
        contentPadding =
            PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = topInsetDp.dp + 16.dp,
                bottom = bottomInsetDp.dp + 24.dp,
            ),
    ) {
        item("panes") { LibrarySwitch(p.panes) { actions.page(MediaAction.OpenPane(it)) } }
        item("header") { LibraryHead(p.header) { actions.page(MediaAction.Add) } }
        p.tip?.let { tip ->
            item("tip") { FeatureTipCard(tip) { actions.page(MediaAction.DismissTip) } }
        }
        item("stats") { MediaStats(p.stats) }
        item("filters") {
            MediaFilters(
                ui.filter,
                p.searchLabel,
                p.searchPlaceholder,
                p.statusFilter,
                p.typeFilter,
                visible,
                onSearch = actions.search,
                onStatus = actions.showStatus,
                onType = actions.showType,
            )
        }
        if (ui.shown.isEmpty()) {
            val message = if (p.items.isEmpty()) p.empty else p.noMatch
            item("message") { ListMessageCard(message) { actions.page(MediaAction.Add) } }
        }
        itemsIndexed(
            ui.shown,
            key = { _, item -> "row/${item.id}" },
            contentType = { _, _ -> "row" },
        ) { i, item ->
            MediaRow(item, p.buttons, last = i == ui.shown.lastIndex, onAction = actions.page)
        }
        item("end") { Spacer(Modifier.fillMaxWidth().height(16.dp).cardPiece(c, last = true)) }
    }
}

/**
 * The page's message as its toast shows it: it rises in, stays as long as the page's, fades away,
 * and is then done.
 */
@Composable
private fun NoticeToast(
    notice: PageNotice,
    p: MediaProjection,
    modifier: Modifier,
    onShown: (PageNotice) -> Unit,
) {
    val shown = remember(notice) { Animatable(0f) }
    LaunchedEffect(notice) {
        shown.playToast()
        onShown(notice)
    }
    PageToast(notice.text, p.tokens, p.light, modifier, shown = { shown.value })
}

/** The page's palette and type for the Library: its theme tokens, its colours, CSS line spacing. */
@Composable
internal fun LibraryTheme(
    tokens: Map<String, Long>,
    light: Boolean,
    content: @Composable () -> Unit,
) {
    val colors = remember(tokens, light) { LibraryColors(tokens, light) }
    LifeMaxTheme(tokens, light) {
        CompositionLocalProvider(LocalLibraryColors provides colors) { CssText(content) }
    }
}
