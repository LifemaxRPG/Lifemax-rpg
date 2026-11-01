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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.core.designsystem.components.PageToast
import com.lifemax.rpg.core.designsystem.components.playToast
import com.lifemax.rpg.core.designsystem.feedback.XpAward
import com.lifemax.rpg.core.designsystem.feedback.XpCelebration
import com.lifemax.rpg.feature.overview.data.OverviewViews
import com.lifemax.rpg.feature.overview.domain.CardCelebration
import com.lifemax.rpg.feature.overview.domain.MoveGo
import com.lifemax.rpg.feature.overview.domain.OverviewCards
import com.lifemax.rpg.feature.overview.domain.OverviewNotice
import com.lifemax.rpg.feature.overview.domain.OverviewTileIds
import com.lifemax.rpg.feature.overview.presentation.cards.HeroCardView
import com.lifemax.rpg.feature.overview.presentation.cards.LuckCardView
import com.lifemax.rpg.feature.overview.presentation.cards.NextMovesCard
import com.lifemax.rpg.feature.overview.presentation.cards.OverviewHeadRow
import com.lifemax.rpg.feature.overview.presentation.cards.OverviewTipCard
import com.lifemax.rpg.feature.overview.presentation.cards.PendingTile
import com.lifemax.rpg.feature.overview.presentation.cards.RecentCardView
import com.lifemax.rpg.feature.overview.presentation.cards.RewardsCardView

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
    OverviewScreen(
        cards,
        actions,
        visible,
        topInsetDp,
        bottomInsetDp,
        vm.weekDay,
        vm.notice,
        vm.celebration,
    )
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
    val celebrationShown: (CardCelebration) -> Unit = {},
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
        celebrationShown = vm::celebrationShown,
    )
}

/**
 * The Overview as the page lays it out on a phone: the head, the tip, then each tile in the page's
 * order (`layout`), one item per tile keyed by its id, under the shared bars ([topInsetDp],
 * [bottomInsetDp]); the page's message as its toast, and a celebration the page handed over drawn
 * over everything.
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
    celebration: CardCelebration? = null,
    list: LazyListState = rememberLazyListState(),
) {
    val images = remember { OverviewImages() }
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
                    Tile(id, cards, actions, weekDay, images)
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
            if (visible && celebration != null)
                CelebrationLayer(celebration, cards, actions.celebrationShown)
        }
    }
}

/** One tile by its id: Cards A here, the others where their own files draw them. */
@Composable
private fun Tile(
    id: String,
    cards: OverviewCards,
    actions: OverviewActions,
    weekDay: Int,
    images: OverviewImages,
) {
    val t = cards.tiles
    when (id) {
        OverviewTileIds.NEXT -> NextMovesCard(t.next) { actions.move(it.go) }
        OverviewTileIds.HERO ->
            HeroCardView(t.hero, images.get(cards.images, t.hero.avatar)) {
                actions.go(OverviewViews.CHARACTER)
            }
        OverviewTileIds.REWARDS -> RewardsCardView(t.rewards, actions.shop)
        OverviewTileIds.LUCK -> LuckCardView(t.luck, actions.luck)
        OverviewTileIds.RECENT -> RecentCardView(t.recent) { actions.go(OverviewViews.HISTORY) }
        else -> PendingTile(id, cards, actions, weekDay)
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

/**
 * An XP celebration the page handed to the cards (fxSink): orbs from the page's last tap to the
 * logo in the shared bar, as Workout plan draws its own; the page plays the sound and buzz.
 */
@Composable
private fun CelebrationLayer(
    celebration: CardCelebration,
    cards: OverviewCards,
    onShown: (CardCelebration) -> Unit,
) {
    val density = LocalDensity.current.density
    XpCelebration(
        id = celebration.id.hashCode().toLong(),
        awards = listOf(XpAward(celebration.xp, celebration.gains)),
        origin = Offset(celebration.origin.x * density, celebration.origin.y * density),
        target = DpOffset(cards.celebrationTarget.x.dp, cards.celebrationTarget.y.dp),
        reduced = cards.reducedMotion,
        onFinished = { onShown(celebration) },
    )
}
