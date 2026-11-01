package com.lifemax.rpg.feature.lifedirector.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LifeMaxTheme
import com.lifemax.rpg.core.designsystem.components.tokenColor
import com.lifemax.rpg.core.designsystem.cssBlurRadius
import com.lifemax.rpg.feature.lifedirector.domain.DirectorToday
import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorProjection
import com.lifemax.rpg.feature.lifedirector.domain.Sections
import com.lifemax.rpg.feature.lifedirector.domain.SessionSheetModel
import com.lifemax.rpg.feature.lifedirector.presentation.components.Banner
import com.lifemax.rpg.feature.lifedirector.presentation.components.ConfirmChangeSheet
import com.lifemax.rpg.feature.lifedirector.presentation.components.DayMix
import com.lifemax.rpg.feature.lifedirector.presentation.components.DayStrip
import com.lifemax.rpg.feature.lifedirector.presentation.components.DialConfirmSheet
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorFooter
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorText
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorToastView
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorTopLine
import com.lifemax.rpg.feature.lifedirector.presentation.components.EarlierFold
import com.lifemax.rpg.feature.lifedirector.presentation.components.LocalDirectorBuzz
import com.lifemax.rpg.feature.lifedirector.presentation.components.OrderHeading
import com.lifemax.rpg.feature.lifedirector.presentation.components.OrderRowView
import com.lifemax.rpg.feature.lifedirector.presentation.components.RemindersCard
import com.lifemax.rpg.feature.lifedirector.presentation.components.RestoreSheet
import com.lifemax.rpg.feature.lifedirector.presentation.components.SectionTabs
import com.lifemax.rpg.feature.lifedirector.presentation.components.Slice
import com.lifemax.rpg.feature.lifedirector.presentation.components.UpNextCard
import com.lifemax.rpg.feature.lifedirector.presentation.components.VibratorBuzz
import com.lifemax.rpg.feature.lifedirector.presentation.components.animatorScale
import com.lifemax.rpg.feature.lifedirector.presentation.components.cardSlice
import com.lifemax.rpg.feature.lifedirector.presentation.components.earlierCard
import com.lifemax.rpg.feature.lifedirector.presentation.components.ellipseWash
import com.lifemax.rpg.feature.lifedirector.presentation.dial.BuildRun
import com.lifemax.rpg.feature.lifedirector.presentation.dial.ChronoDial
import com.lifemax.rpg.feature.lifedirector.presentation.dial.DialTourRun
import com.lifemax.rpg.feature.lifedirector.presentation.dial.dialTour
import com.lifemax.rpg.feature.lifedirector.presentation.dial.rememberBuildRun
import com.lifemax.rpg.feature.lifedirector.presentation.guide.CoachMarks
import com.lifemax.rpg.feature.lifedirector.presentation.guide.GuideAnchors
import com.lifemax.rpg.feature.lifedirector.presentation.guide.guideAnchor
import com.lifemax.rpg.feature.lifedirector.presentation.sheets.SessionSheet
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
    LaunchedEffect(visible) { if (!visible) vm.hidden() }
    ui.toast?.let { toast ->
        LaunchedEffect(toast.serial) {
            delay(TOAST_MS)
            vm.dismiss(toast.serial)
        }
    }
    BackHandler(enabled = visible && ui.ask?.confirm != null) { vm.keep() }
    BackHandler(enabled = visible && ui.confirm != null) { vm.cancelChange() }
    BackHandler(enabled = visible && ui.restoring) { vm.closeRestore() }
    BackHandler(enabled = visible && ui.sheet != null) { vm.closeSheet() }
    // the backup the page answered with goes on the phone's clipboard, as the page's Copy backup
    // puts it on the browser's
    val clipboard = LocalClipboardManager.current
    ui.copyText?.let { text ->
        LaunchedEffect(text) {
            vm.copied(runCatching { clipboard.setText(AnnotatedString(text)) }.isSuccess)
        }
    }
    val context = LocalContext.current
    val buzz = remember(context) { VibratorBuzz(context) }
    val actions = remember(vm, emit) { DirectorActions(vm, emit) }
    CompositionLocalProvider(LocalDirectorBuzz provides buzz) {
        val run =
            rememberBuildRun(
                // the page sends none with reduced motion; a projection that had one is not played
                p.today?.dial?.build?.takeUnless { p.reducedMotion },
                p.session,
                ready = visible && ui.hold == null && ui.ask == null,
                ledger = vm::takeBuild,
                touched = { vm.touched },
            )
        // a guide not seen yet starts by itself, once, when its section shows on a visible screen;
        // after a build still to play, once it is over (the page's waits for its build too)
        val build = p.today?.dial?.build?.takeUnless { p.reducedMotion }
        val playable = remember(build?.id) { animatorScale(context) > 0f }
        val buildDue = build != null && playable && vm.buildDue(p.session, build.id)
        LaunchedEffect(visible, p.tab, p.guide?.key, p.guide?.auto, run == null, buildDue) {
            if (visible && run == null && !buildDue) vm.startGuide(auto = true)
        }
        LifeDirectorScreen(p, ui, actions, visible, topInsetDp, bottomInsetDp, run)
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
    run: BuildRun? = null,
) {
    val confirm = ui.ask?.confirm
    DirectorTheme(p) {
        Box(
            Modifier.fillMaxSize().background(tokenColor(p.tokens, "bg")).pointerInput(actions) {
                observeTouches(actions.touch)
            }
        ) {
            val today = p.today?.takeIf { p.tab == Sections.TODAY }
            val week = p.week?.takeIf { p.tab == Sections.WEEK }
            val quests = p.quests?.takeIf { p.tab == Sections.QUESTS }
            val goals = p.goals?.takeIf { p.tab == Sections.GOALS }
            val settings = p.settings?.takeIf { p.tab == Sections.SETTINGS }
            val more =
                today != null || week != null || quests != null || goals != null || settings != null
            val list = rememberLazyListState()
            val anchors = remember { GuideAnchors() }
            val guideRun = ui.guide?.takeIf { g -> p.guide?.key == g.key }
            // the part a guide step points at is scrolled into view first (the page scrolls it to
            // the middle)
            LaunchedEffect(guideRun?.step, week?.days?.size) {
                val step =
                    guideRun?.let { r -> p.guide?.steps?.getOrNull(r.step) }
                        ?: return@LaunchedEffect
                val days = week?.days?.size ?: 0
                val index =
                    when (step.anchor) {
                        "week.stats" -> 1
                        "week.rows" -> 2
                        "week.yield" -> 4 + days
                        // Today's: the dial and its middle are in the first item, up next the
                        // second
                        "dial",
                        "dial.centre" -> 0
                        "upNext" -> 1
                        "footer" -> list.layoutInfo.totalItemsCount - 1
                        else -> -1
                    }
                if (index == 0) list.animateScrollToItem(0)
                else if (index > 0) list.animateScrollToItem(index, scrollOffset = -200)
            }
            // a section change slides the body in from below, the frame stays (the page's
            // .ld-v-in); none with reduced motion
            val bodyIn = remember { Animatable(1f) }
            LaunchedEffect(p.tab) {
                if (animate && !p.reducedMotion) {
                    bodyIn.snapTo(0f)
                    bodyIn.animateTo(1f, tween(260))
                }
            }
            val body =
                Modifier.graphicsLayer {
                    alpha = bodyIn.value
                    translationY = (1f - bodyIn.value) * 18.dp.toPx()
                }
            LazyColumn(
                Modifier.fillMaxSize()
                    .pointerInput(actions) { detectSectionFlick(actions.flick) }
                    .then(
                        // behind a confirmation the screen is blurred as .ld-sheet-bg blurs the
                        // page, on Android 12 and later
                        if (confirm != null)
                            Modifier.clearAndSetSemantics {}.blur(sheetBlur(LocalDensity.current))
                        else Modifier
                    ),
                state = list,
                contentPadding =
                    PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = topInsetDp.dp + 12.dp,
                        bottom = bottomInsetDp.dp + 16.dp,
                    ),
            ) {
                item("top") {
                    Panel(if (more) Slice.TOP else Slice.WHOLE, PANEL_WASHES) {
                        Column(Modifier.padding(top = 16.dp)) {
                            DirectorTopLine(p.header, p.clock, actions.tap)
                            Spacer(Modifier.height(12.dp))
                            SectionTabs(p.tabs, ui.pendingSection ?: p.tab, actions.tap)
                            Spacer(Modifier.height(15.dp))
                            p.banner?.let {
                                Banner(it, actions.tap)
                                Spacer(Modifier.height(12.dp))
                            }
                            if (today != null)
                                TodayTop(
                                    today,
                                    ui,
                                    actions,
                                    animate,
                                    run,
                                    anchors,
                                    dialTour(p, guideRun),
                                )
                        }
                    }
                }
                if (today != null) todayBelow(today, ui, actions, animate, anchors)
                if (week != null) weekBody(week, ui, actions, anchors, body)
                if (quests != null) questsBody(quests, actions, body)
                if (goals != null) goalsBody(goals, ui, actions, body)
                if (settings != null) settingsBody(settings, ui, actions, body)
            }
            if (confirm != null)
                DialConfirmSheet(confirm, bottomInsetDp.dp, actions.change, actions.keep)
            ui.confirm?.let { c ->
                ConfirmChangeSheet(c, bottomInsetDp.dp, actions.confirmChange, actions.cancelChange)
            }
            val data = p.settings?.data
            if (ui.restoring && data != null)
                RestoreSheet(
                    data.restoreTitle,
                    data.restoreText,
                    data.restoreButton,
                    ui.restoreDraft,
                    bottomInsetDp.dp,
                    actions.restoreDraft,
                    actions.submitRestore,
                    actions.closeRestore,
                )
            sessionSheet(p, ui)?.let { s ->
                val typed = ui.minutesDraft?.takeIf { it.id == s.id }?.text
                SessionSheet(
                    s,
                    typed ?: s.minutes?.value?.toString().orEmpty(),
                    ui.pendingToday,
                    bottomInsetDp.dp,
                    actions.session,
                )
            }
            val guide = p.guide
            if (guideRun != null && guide != null)
                CoachMarks(
                    guide,
                    guideRun.step,
                    anchors,
                    actions.guideNext,
                    actions.guideBack,
                    actions.guideSkip,
                )
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

/** The session sheet the screen asked for, once the page has sent its model. */
private fun sessionSheet(p: LifeDirectorProjection, ui: DirectorUi): SessionSheetModel? {
    val open = ui.sheet ?: return null
    return (p.sheet as? SessionSheetModel)?.takeIf { it.kind == open.kind && it.id == open.id }
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
    run: BuildRun?,
    anchors: GuideAnchors,
    tour: DialTourRun?,
) {
    DayStrip(today.strip, ui.pendingDay ?: today.dayIndex, actions.day)
    Spacer(Modifier.height(8.dp))
    ChronoDial(
        today.dial,
        today.centre,
        animate,
        actions::arc,
        actions.tap,
        Modifier.guideAnchor(anchors, "dial"),
        hold = ui.hold,
        asking = ui.ask != null,
        handles = actions.handles,
        run = run,
        tour = tour,
        centreAnchor = Modifier.guideAnchor(anchors, "dial.centre"),
    )
    DialHint(today.hint)
    DayMix(today.mix)
}

/** The words under the dial, with a handle's pip between them (.ld-dial-hint, .ld-hint-pip). */
@Composable
private fun DialHint(hint: Pair<String, String>) {
    if (hint.first.isEmpty() && hint.second.isEmpty()) return
    Text(
        buildAnnotatedString {
            append(hint.first)
            append(" ")
            appendInlineContent(PIP, " ")
            append(" ")
            append(hint.second)
        },
        Modifier.fillMaxWidth().padding(top = 2.dp),
        style = DirectorText,
        color = DirectorPalette.ink3,
        fontSize = 12.2.sp,
        textAlign = TextAlign.Center,
        inlineContent = HINT_PIP,
    )
}

private const val PIP = "pip"

/** The pip: a 10 dp handle ring in the work colour, a dp under the baseline, a dp either side. */
private val HINT_PIP =
    mapOf(
        PIP to
            InlineTextContent(Placeholder(12.sp, 10.sp, PlaceholderVerticalAlign.AboveBaseline)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.offset(y = 1.dp)
                            .size(10.dp)
                            .background(DirectorPalette.handleFill, CircleShape)
                            .border(2.dp, DirectorPalette.work, CircleShape)
                    )
                }
            }
    )

private fun LazyListScope.todayBelow(
    today: DirectorToday,
    ui: DirectorUi,
    actions: DirectorActions,
    animate: Boolean,
    anchors: GuideAnchors,
) {
    today.upNext?.let { up ->
        item("up-next") {
            Panel(Slice.MIDDLE) {
                Box(Modifier.guideAnchor(anchors, "upNext")) {
                    UpNextCard(up, animate, actions::upNext)
                }
            }
        }
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
    item("footer") {
        Panel(Slice.BOTTOM) {
            Box(Modifier.guideAnchor(anchors, "footer")) { DirectorFooter(actions.tap) }
        }
    }
}

/** One item's slice of Life Director's panel, with the panel's side padding. */
@Composable
internal fun Panel(
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

/**
 * The blur of .ld-sheet-bg's backdrop-filter: blur(2px), a deviation of 2 px: the deviation a CSS
 * shadow blur of 4 px has ([cssBlurRadius]; Android's radius r blurs with 0.57735 r + 0.5 pixels).
 */
private fun sheetBlur(density: Density) = with(density) { cssBlurRadius(4.dp.toPx()).toDp() }
