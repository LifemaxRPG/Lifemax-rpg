package com.lifemax.rpg.feature.library.presentation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.core.designsystem.PageTheme
import com.lifemax.rpg.core.designsystem.components.PageToast
import com.lifemax.rpg.core.designsystem.components.cardPiece
import com.lifemax.rpg.core.designsystem.components.playToast
import com.lifemax.rpg.feature.library.domain.MediaAction
import com.lifemax.rpg.feature.library.domain.MediaForm
import com.lifemax.rpg.feature.library.domain.MediaProjection
import com.lifemax.rpg.feature.library.domain.PageNotice
import com.lifemax.rpg.feature.library.presentation.components.FeatureTipCard
import com.lifemax.rpg.feature.library.presentation.components.LibraryColors
import com.lifemax.rpg.feature.library.presentation.components.LibraryHead
import com.lifemax.rpg.feature.library.presentation.components.LibrarySwitch
import com.lifemax.rpg.feature.library.presentation.components.ListMessageCard
import com.lifemax.rpg.feature.library.presentation.components.LocalLibraryColors
import com.lifemax.rpg.feature.library.presentation.components.MediaConfirm
import com.lifemax.rpg.feature.library.presentation.components.MediaFilters
import com.lifemax.rpg.feature.library.presentation.components.MediaRow
import com.lifemax.rpg.feature.library.presentation.components.MediaSheet
import com.lifemax.rpg.feature.library.presentation.components.MediaSheetActions
import com.lifemax.rpg.feature.library.presentation.components.MediaStats

@Composable
fun MediaRoute(
    vm: MediaViewModel,
    visible: Boolean,
    topInsetDp: Float,
    bottomInsetDp: Float,
    onPage: (MediaAction) -> Unit,
) {
    val ui = vm.ui ?: return
    val context = LocalContext.current
    val actions = remember(vm, onPage, context) { MediaActions(vm, onPage, context::openLink) }
    val s = vm.sheets
    val edits = MediaEdits(ui.projection.form, s.sheet, s.ask, s.ticks)
    MediaScreen(ui, actions, visible, topInsetDp, bottomInsetDp, vm.notice, edits = edits)
}

/** Hands a row's link to the browser; the projection carries only http and https links. */
private fun Context.openLink(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        // nothing on the phone opens web links: the page's own link would open nothing either
    }
}

/**
 * What the Media sheets put over the pane, while the page says the screen draws them ([form] not
 * null): the add/edit sheet, a confirm, and the ticks waiting for the page's answer.
 */
data class MediaEdits(
    val form: MediaForm? = null,
    val sheet: MediaSheetUi? = null,
    val ask: MediaAskUi? = null,
    val ticks: Map<String, Boolean> = emptyMap(),
)

/** Everything the screen can ask for, so the screen itself holds no state. */
class MediaActions(
    val search: (String) -> Unit = {},
    val showStatus: (String) -> Unit = {},
    val showType: (String) -> Unit = {},
    val page: (MediaAction) -> Unit = {},
    val noticeShown: (PageNotice) -> Unit = {},
    val sheet: MediaSheetActions = MediaSheetActions(),
    val confirm: () -> Unit = {},
    val dismiss: () -> Unit = {},
) {
    /**
     * A tap the Media sheets answer stays on the screen; a link opens in the browser ([openLink]);
     * any other tap goes to the page only while the view model hands one over (one per opening).
     */
    constructor(
        vm: MediaViewModel,
        page: (MediaAction) -> Unit,
        openLink: (String) -> Unit,
    ) : this(
        vm::search,
        vm::showStatus,
        vm::showType,
        { action ->
            when {
                vm.sheets.handle(action) -> Unit
                action is MediaAction.OpenLink ->
                    vm.ui
                        ?.projection
                        ?.items
                        ?.firstOrNull { it.id == action.id }
                        ?.link
                        ?.takeIf { it.isNotEmpty() }
                        ?.let(openLink)
                vm.handOver() -> page(action)
            }
        },
        vm::noticeShown,
        MediaSheetActions(vm.sheets::change, vm.sheets::save, vm.sheets::cancel),
        vm.sheets::confirmAsk,
        vm.sheets::dismissAsk,
    )

    /** As above, with links opening nowhere (tests). */
    constructor(vm: MediaViewModel, page: (MediaAction) -> Unit) : this(vm, page, {})
}

/**
 * The Media pane as the page lays it out on a phone: the Media/Journal switch, the page head, the
 * feature tip, the stats, then one card holding the filters and the rows (or what shows in their
 * place), and the page's [notice] as its toast. The rows are items of the list, keyed by the page's
 * item ids, so the card is drawn in a piece per item. A hidden screen ([visible] false) gives up
 * the keyboard and any open menu, and draws no toast. The Media sheets' [edits] (the add/edit
 * sheet, a confirm) are drawn over the pane, the page's toast above them.
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
    edits: MediaEdits = MediaEdits(),
    sheetScroll: ScrollState = rememberScrollState(),
) {
    val p = ui.projection
    val focus = LocalFocusManager.current
    LaunchedEffect(visible) { if (!visible) focus.clearFocus() }
    LibraryTheme(p.tokens, p.light) {
        val c = LocalLibraryColors.current
        Box(Modifier.fillMaxSize().background(c.bg)) {
            MediaList(ui, actions, visible, topInsetDp, bottomInsetDp, list, edits.ticks)
            val form = edits.form
            if (form != null && edits.sheet != null)
                MediaSheet(
                    form,
                    edits.sheet,
                    visible,
                    topInsetDp.dp,
                    bottomInsetDp.dp,
                    actions.sheet,
                    sheetScroll,
                )
            if (form != null && edits.ask != null)
                MediaConfirm(edits.ask, visible, actions.confirm, actions.dismiss)
            // over a sheet the toast drops from the top, as Food's does, clear of the sheet's
            // buttons
            val overSheet = form != null && edits.sheet != null
            if (visible && notice != null)
                NoticeToast(
                    notice,
                    p,
                    if (overSheet)
                        Modifier.align(Alignment.TopCenter)
                            .padding(start = 12.dp, end = 12.dp, top = topInsetDp.dp + 12.dp)
                    else
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
    ticks: Map<String, Boolean>,
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
            MediaRow(
                item,
                p.buttons,
                last = i == ui.shown.lastIndex,
                onAction = actions.page,
                finished = ticks[item.id] ?: item.finished,
                pending = item.id in ticks,
            )
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

/** The page's palette and type for the Library, with the Library's own colours. */
@Composable
internal fun LibraryTheme(
    tokens: Map<String, Long>,
    light: Boolean,
    content: @Composable () -> Unit,
) {
    val colors = remember(tokens, light) { LibraryColors(tokens, light) }
    PageTheme(colors) {
        CompositionLocalProvider(LocalLibraryColors provides colors, content = content)
    }
}
