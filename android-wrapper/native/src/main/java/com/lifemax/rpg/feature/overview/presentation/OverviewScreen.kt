package com.lifemax.rpg.feature.overview.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.core.designsystem.components.PageToast
import com.lifemax.rpg.core.designsystem.components.playToast
import com.lifemax.rpg.core.designsystem.feedback.PageCelebrations
import com.lifemax.rpg.feature.lifedirector.presentation.dial.DialIntroLedger
import com.lifemax.rpg.feature.lifedirector.presentation.glance.DirectorGlance
import com.lifemax.rpg.feature.overview.data.OverviewViews
import com.lifemax.rpg.feature.overview.domain.MoveGo
import com.lifemax.rpg.feature.overview.domain.OverviewCards
import com.lifemax.rpg.feature.overview.domain.OverviewNotice
import com.lifemax.rpg.feature.overview.domain.OverviewTileIds
import com.lifemax.rpg.feature.overview.presentation.cards.AppsCardView
import com.lifemax.rpg.feature.overview.presentation.cards.AttributesCardView
import com.lifemax.rpg.feature.overview.presentation.cards.CatCardView
import com.lifemax.rpg.feature.overview.presentation.cards.HealthCardView
import com.lifemax.rpg.feature.overview.presentation.cards.HeroCardView
import com.lifemax.rpg.feature.overview.presentation.cards.LuckCardView
import com.lifemax.rpg.feature.overview.presentation.cards.NextMovesCard
import com.lifemax.rpg.feature.overview.presentation.cards.OverviewHeadRow
import com.lifemax.rpg.feature.overview.presentation.cards.OverviewTipCard
import com.lifemax.rpg.feature.overview.presentation.cards.RecentCardView
import com.lifemax.rpg.feature.overview.presentation.cards.RewardsCardView
import com.lifemax.rpg.feature.overview.presentation.cards.WeekCardView

/**
 * The cards' screen as the plugin shows it: [visible] is the plugin's, [emit] its action sender
 * ("navigate" with a page view). A hidden screen stays composed (A24) and draws no message and no
 * celebration.
 */
@Composable
fun OverviewRoute(
    vm: OverviewViewModel,
    visible: Boolean,
    topInsetDp: Float,
    bottomInsetDp: Float,
    emit: (action: String, id: String) -> Unit,
) {
    SideEffect { vm.onNavigate = { emit("navigate", it) } }
    LaunchedEffect(visible) { if (!visible) vm.hidden() }
    val cards = vm.cards ?: return
    val actions = remember(vm) { OverviewActions(vm) }
    OverviewScreen(cards, actions, visible, topInsetDp, bottomInsetDp, vm.weekDay, vm.notice)
}

/** Everything the cards can ask for, so the screen itself holds no state. */
class OverviewActions(
    val go: (String) -> Unit = {},
    val move: (MoveGo) -> Unit = {},
    val attr: (id: String, tile: String) -> Unit = { _, _ -> },
    val luck: () -> Unit = {},
    val shop: () -> Unit = {},
    val health: () -> Unit = {},
    val appLaunch: (String) -> Unit = {},
    val appAdd: () -> Unit = {},
    val appsEdit: () -> Unit = {},
    val plan: (act: String, id: String) -> Unit = { _, _ -> },
    val edit: () -> Unit = {},
    val dismissTip: () -> Unit = {},
    val pickDay: (Int) -> Unit = {},
    val noticeShown: (OverviewNotice) -> Unit = {},
) {
    constructor(
        vm: OverviewViewModel
    ) : this(
        go = vm.taps::go,
        move = vm.taps::move,
        attr = vm.taps::attr,
        luck = vm.taps::luck,
        shop = vm.taps::shop,
        health = vm.taps::health,
        appLaunch = vm.taps::appLaunch,
        appAdd = vm.taps::appAdd,
        appsEdit = vm.taps::appsEdit,
        plan = vm.taps::plan,
        edit = vm.taps::edit,
        dismissTip = vm.taps::dismissTip,
        pickDay = vm::pickDay,
        noticeShown = vm::noticeShown,
    )
}

/**
 * The Overview as the page lays it out on a phone: the head, the tip, then each tile in the page's
 * order (`layout`), one item per tile keyed by its id, under the shared bars ([topInsetDp],
 * [bottomInsetDp]); the page's message as its toast, and the celebrations the page handed over
 * (fxSink) drawn over everything, each once, from the page's last tap to the logo, only while
 * [visible]. [celebrationAt] freezes the first at a moment (screenshots).
 */
@Composable
fun OverviewScreen(
    cards: OverviewCards,
    actions: OverviewActions,
    visible: Boolean = true,
    topInsetDp: Float = 64f,
    bottomInsetDp: Float = 72f,
    weekDay: Int = cards.tiles.week.selected,
    notice: OverviewNotice? = null,
    list: LazyListState = rememberLazyListState(),
    celebrationAt: Float = 0f,
) {
    val images = remember { OverviewImages() }
    // the glance's arrival plays once per arrival for the whole screen, not once per glance redraw
    val introLedger = remember { DialIntroLedger() }
    OverviewTheme(cards.tokens, cards.light) {
        val c = LocalOverviewColors.current
        Box(Modifier.fillMaxSize().background(c.bg)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = list,
                contentPadding =
                    PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = topInsetDp.dp + 14.dp,
                        bottom = bottomInsetDp.dp + 16.dp,
                    ),
            ) {
                item("head", "head") { OverviewHeadRow(cards.head, actions.edit) }
                cards.tip?.let { tip ->
                    item("tip", "tip") { OverviewTipCard(tip, actions.dismissTip) }
                }
                items(cards.shown, key = { "tile/$it" }, contentType = { it }) { id ->
                    Tile(id, cards, actions, visible, weekDay, images, introLedger)
                }
            }
            if (visible && notice != null)
                NoticeToast(
                    notice,
                    cards,
                    Modifier.align(Alignment.BottomCenter)
                        .padding(start = 12.dp, end = 12.dp, bottom = bottomInsetDp.dp + 12.dp),
                    actions.noticeShown,
                )
            PageCelebrations(
                cards.celebrations,
                cards.celebrationTarget,
                cards.reducedMotion,
                visible,
                startAt = celebrationAt,
            )
        }
    }
}

/** One tile by its id, each drawn by its own card from its model in the projection. */
@Composable
private fun Tile(
    id: String,
    cards: OverviewCards,
    actions: OverviewActions,
    visible: Boolean,
    weekDay: Int,
    images: OverviewImages,
    introLedger: DialIntroLedger,
) {
    val t = cards.tiles
    when (id) {
        OverviewTileIds.PLAN ->
            t.plan?.let {
                DirectorGlance(
                    it,
                    visible,
                    onTap = { tap -> actions.plan(tap.act, tap.id) },
                    // the 14 dp every tile keeps under it (OverviewCard's own for the others)
                    modifier = Modifier.padding(bottom = 14.dp),
                    // the page sends no arrival yet (phase 3): the dial's arrival never plays
                    arrival = cards.arrival,
                    reducedMotion = cards.reducedMotion,
                    introLedger = introLedger,
                )
            }
        OverviewTileIds.NEXT -> NextMovesCard(t.next) { actions.move(it.go) }
        OverviewTileIds.HERO ->
            HeroCardView(t.hero, images.get(cards.images, t.hero.avatar)) {
                actions.go(OverviewViews.CHARACTER)
            }
        OverviewTileIds.REWARDS -> RewardsCardView(t.rewards, actions.shop)
        OverviewTileIds.LUCK -> LuckCardView(t.luck, actions.luck)
        OverviewTileIds.RECENT -> RecentCardView(t.recent) { actions.go(OverviewViews.HISTORY) }
        OverviewTileIds.APPS ->
            t.apps?.let {
                AppsCardView(
                    it,
                    { key -> images.get(cards.images, key) },
                    actions.appLaunch,
                    actions.appAdd,
                    actions.appsEdit,
                )
            }
        OverviewTileIds.HEALTH ->
            t.health?.let {
                HealthCardView(it, actions.health, onSetup = { actions.go(OverviewViews.SETTINGS) })
            }
        OverviewTileIds.ATTRS ->
            AttributesCardView(
                t.attrs,
                { actions.go(OverviewViews.CHARACTER) },
                { gem -> actions.attr(gem, OverviewTileIds.ATTRS) },
            )
        OverviewTileIds.WEEK -> WeekCardView(t.week, weekDay, cards.haptics, actions.pickDay)
        OverviewTileIds.CAT ->
            t.cat?.let {
                CatCardView(it, cards.catPortrait, onOpen = { actions.go(OverviewViews.PET) })
            }
    }
}

/** The page's message as its toast shows it: it rises in, stays, fades away, and is done. */
@Composable
private fun NoticeToast(
    notice: OverviewNotice,
    cards: OverviewCards,
    modifier: Modifier,
    onShown: (OverviewNotice) -> Unit,
) {
    val shown = remember(notice) { Animatable(0f) }
    LaunchedEffect(notice) {
        shown.playToast()
        onShown(notice)
    }
    PageToast(notice.text, cards.tokens, cards.light, modifier, shown = { shown.value })
}
