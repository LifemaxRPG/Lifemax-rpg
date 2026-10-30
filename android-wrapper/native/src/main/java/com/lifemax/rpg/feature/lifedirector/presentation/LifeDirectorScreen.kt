package com.lifemax.rpg.feature.lifedirector.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LifeMaxTheme
import com.lifemax.rpg.core.designsystem.components.tokenColor
import com.lifemax.rpg.feature.lifedirector.domain.DialArc
import com.lifemax.rpg.feature.lifedirector.domain.DirectorToday
import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorProjection
import com.lifemax.rpg.feature.lifedirector.domain.Tap
import com.lifemax.rpg.feature.lifedirector.domain.UpNextAction
import com.lifemax.rpg.feature.lifedirector.presentation.components.Banner
import com.lifemax.rpg.feature.lifedirector.presentation.components.DayMix
import com.lifemax.rpg.feature.lifedirector.presentation.components.DayStrip
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorFooter
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorText
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorToastView
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorTopLine
import com.lifemax.rpg.feature.lifedirector.presentation.components.EarlierFold
import com.lifemax.rpg.feature.lifedirector.presentation.components.OrderHeading
import com.lifemax.rpg.feature.lifedirector.presentation.components.OrderRowView
import com.lifemax.rpg.feature.lifedirector.presentation.components.RemindersCard
import com.lifemax.rpg.feature.lifedirector.presentation.components.SectionTabs
import com.lifemax.rpg.feature.lifedirector.presentation.components.Slice
import com.lifemax.rpg.feature.lifedirector.presentation.components.UpNextCard
import com.lifemax.rpg.feature.lifedirector.presentation.components.cardSlice
import com.lifemax.rpg.feature.lifedirector.presentation.components.earlierCard
import com.lifemax.rpg.feature.lifedirector.presentation.components.ellipseWash
import com.lifemax.rpg.feature.lifedirector.presentation.dial.ChronoDial
import kotlinx.coroutines.delay

/**
 * Life Director's Today, drawn natively from the page's projection. [visible] is false while the
 * screen is hidden behind the page: then nothing ticks or pulses (A24).
 */
@Composable
fun LifeDirectorRoute(
    vm: LifeDirectorViewModel,
    visible: Boolean,
    topInsetDp: Float,
    bottomInsetDp: Float,
    emit: (action: String, id: String) -> Unit,
) {
    val projection by vm.projection.collectAsState()
    val ui by vm.ui.collectAsState()
    val p = projection ?: return
    LaunchedEffect(p.session, p.notice?.id) { p.notice?.let { vm.notice(p.session, it) } }
    ui.toast?.let { toast ->
        LaunchedEffect(toast.serial) {
            delay(TOAST_MS)
            vm.dismiss(toast.serial)
        }
    }
    val actions = remember(vm, emit) { DirectorActions(vm, emit) }
    LifeDirectorScreen(p, ui, actions, visible, topInsetDp, bottomInsetDp)
}

/** Everything the screen can ask for, so the screen itself holds no state. */
class DirectorActions(
    val tap: (Tap) -> Unit = {},
    val day: (Int) -> Unit = {},
    val tip: (String) -> Unit = {},
    val earlier: () -> Unit = {},
) {
    constructor(
        vm: LifeDirectorViewModel,
        emit: (action: String, id: String) -> Unit,
    ) : this({ if (vm.handOver()) emit(it.act, it.id) }, vm::showDay, vm::tip, vm::toggleEarlier)

    /** An arc runs its tap on the page; fixed time only says what it is. */
    fun arc(arc: DialArc) {
        when {
            arc.tap != null -> tap(arc.tap)
            arc.tip.isNotEmpty() -> tip(arc.tip)
        }
    }

    /** An up-next button runs its tap on the page or shows another day of the strip. */
    fun upNext(action: UpNextAction) {
        when {
            action.tap != null -> tap(action.tap)
            action.day != null -> day(action.day)
        }
    }
}

/**
 * The web's renderToday, in its order, on Life Director's dark panel: header, sections, banner, the
 * day strip, the dial, the day's mix, up next, all-day quests and the running order. Lazy, so a
 * long day only composes what is on screen; each item draws its slice of the panel.
 */
@Composable
fun LifeDirectorScreen(
    p: LifeDirectorProjection,
    ui: DirectorUi,
    actions: DirectorActions,
    animate: Boolean = true,
    topInsetDp: Float = 64f,
    bottomInsetDp: Float = 72f,
) {
    DirectorTheme(p) {
        Box(Modifier.fillMaxSize().background(tokenColor(p.tokens, "bg"))) {
            val today = p.today
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = topInsetDp.dp + 12.dp,
                        bottom = bottomInsetDp.dp + 16.dp,
                    ),
            ) {
                item("top") {
                    Panel(if (today == null) Slice.WHOLE else Slice.TOP, PANEL_WASHES) {
                        Column(Modifier.padding(top = 16.dp)) {
                            DirectorTopLine(p.header, p.clock, actions.tap)
                            Spacer(Modifier.height(12.dp))
                            SectionTabs(p.tabs, p.tab, actions.tap)
                            Spacer(Modifier.height(15.dp))
                            p.banner?.let {
                                Banner(it, actions.tap)
                                Spacer(Modifier.height(12.dp))
                            }
                            if (today != null) TodayTop(today, ui, actions, animate)
                        }
                    }
                }
                if (today != null) todayBelow(today, ui, actions, animate)
            }
            ui.toast?.let {
                DirectorToastView(
                    it.text,
                    Modifier.align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = bottomInsetDp.dp + 16.dp),
                )
            }
        }
    }
}

/**
 * LifeMax's theme from the page, with Life Director's light ink as the content colour: the panel is
 * dark in both themes, and a pressed button's ripple takes that colour.
 */
@Composable
private fun DirectorTheme(p: LifeDirectorProjection, content: @Composable () -> Unit) =
    LifeMaxTheme(p.tokens, p.light) {
        CompositionLocalProvider(LocalContentColor provides DirectorPalette.ink, content = content)
    }

@Composable
private fun TodayTop(
    today: DirectorToday,
    ui: DirectorUi,
    actions: DirectorActions,
    animate: Boolean,
) {
    DayStrip(today.strip, ui.pendingDay ?: today.dayIndex, actions.day)
    Spacer(Modifier.height(8.dp))
    ChronoDial(today.dial, today.centre, animate, actions::arc, actions.tap)
    Text(
        "Tap a session for details.",
        Modifier.fillMaxWidth().padding(top = 2.dp),
        style = DirectorText,
        color = DirectorPalette.ink3,
        fontSize = 12.2.sp,
        textAlign = TextAlign.Center,
    )
    DayMix(today.mix)
}

private fun LazyListScope.todayBelow(
    today: DirectorToday,
    ui: DirectorUi,
    actions: DirectorActions,
    animate: Boolean,
) {
    today.upNext?.let { up ->
        item("up-next") { Panel(Slice.MIDDLE) { UpNextCard(up, animate, actions::upNext) } }
    }
    if (today.reminders.isNotEmpty())
        item("reminders") { Panel(Slice.MIDDLE) { RemindersCard(today.reminders) } }
    item("heading") { Panel(Slice.MIDDLE) { OrderHeading(today.isToday, today.sessions) } }
    today.earlier?.let { earlier ->
        val open = ui.earlierOpen && earlier.rows.isNotEmpty()
        item("earlier") {
            Panel(Slice.MIDDLE) {
                Box(
                    Modifier.padding(top = 4.dp, bottom = if (open) 0.dp else 6.dp)
                        .earlierCard(if (open) Slice.TOP else Slice.WHOLE)
                ) {
                    EarlierFold(earlier.summary, ui.earlierOpen, actions.earlier)
                }
            }
        }
        if (open)
            itemsIndexed(earlier.rows, key = { _, row -> row.key }) { i, row ->
                val last = i == earlier.rows.lastIndex
                Panel(Slice.MIDDLE) {
                    Box(
                        Modifier.padding(bottom = if (last) 6.dp else 0.dp)
                            .earlierCard(if (last) Slice.BOTTOM else Slice.MIDDLE)
                            .padding(end = 10.dp, bottom = if (last) 8.dp else 0.dp)
                    ) {
                        OrderRowView(row, actions.tap)
                    }
                }
            }
    }
    items(today.ahead, key = { it.key }) { row ->
        Panel(Slice.MIDDLE) { OrderRowView(row, actions.tap) }
    }
    item("footer") { Panel(Slice.BOTTOM) { DirectorFooter(actions.tap) } }
}

/** One item's slice of Life Director's panel, with the panel's side padding. */
@Composable
private fun Panel(
    slice: Slice,
    washes: List<Brush> = emptyList(),
    content: @Composable () -> Unit,
) {
    Box(
        Modifier.fillMaxWidth()
            .cardSlice(slice, 24.dp, PANEL_FILL, DirectorPalette.panelEdge, washes)
            .padding(horizontal = 14.dp)
            .padding(bottom = if (slice == Slice.BOTTOM || slice == Slice.WHOLE) 28.dp else 0.dp)
    ) {
        content()
    }
}

private val PANEL_FILL = SolidColor(DirectorPalette.bg0)

/**
 * The panel's two soft lights (.ld-root's background): blue from the top right, violet from the
 * left. The web sizes them by the whole panel; here by its width, for a day of typical length.
 */
private val PANEL_WASHES =
    listOf(
        ellipseWash(.85f, -.32f, .9f, 2.13f, DirectorPalette.free.copy(alpha = .11f)),
        ellipseWash(0f, .96f, .7f, 1.6f, DirectorPalette.sleep.copy(alpha = .06f)),
    )

private const val TOAST_MS = 4000L
