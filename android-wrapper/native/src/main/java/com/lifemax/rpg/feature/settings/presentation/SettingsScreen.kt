package com.lifemax.rpg.feature.settings.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.core.designsystem.LocalPageColors
import com.lifemax.rpg.core.designsystem.PageColors
import com.lifemax.rpg.core.designsystem.PageTheme
import com.lifemax.rpg.core.designsystem.components.PageToast
import com.lifemax.rpg.core.designsystem.components.playToast
import com.lifemax.rpg.core.designsystem.form.PickerHost
import com.lifemax.rpg.core.designsystem.form.PickerState
import com.lifemax.rpg.feature.settings.domain.SettingsCard
import com.lifemax.rpg.feature.settings.domain.SettingsControl
import com.lifemax.rpg.feature.settings.domain.SettingsPageAction
import com.lifemax.rpg.feature.settings.domain.SettingsProjection
import com.lifemax.rpg.feature.settings.presentation.components.CardTop
import com.lifemax.rpg.feature.settings.presentation.components.CategoryChips
import com.lifemax.rpg.feature.settings.presentation.components.ClassicCard
import com.lifemax.rpg.feature.settings.presentation.components.ControlTaps
import com.lifemax.rpg.feature.settings.presentation.components.LocalScreenShown
import com.lifemax.rpg.feature.settings.presentation.components.RowView
import com.lifemax.rpg.feature.settings.presentation.components.SettingsHeadView
import com.lifemax.rpg.feature.settings.presentation.components.cardPiece
import com.lifemax.rpg.feature.settings.presentation.components.key
import com.lifemax.rpg.feature.settings.presentation.components.rowBoxes

/**
 * The screen of the page's Settings, fed by [vm]. What closes the screen (a reveal, the classic
 * page) is handed to [onPage]; commands go to the page through the view model.
 */
@Composable
fun SettingsRoute(
    vm: SettingsViewModel,
    visible: Boolean,
    topInsetDp: Float,
    bottomInsetDp: Float,
    onPage: (SettingsPageAction) -> Unit,
) {
    val projection by vm.projection.collectAsState()
    val ui by vm.ui.collectAsState()
    val page by rememberUpdatedState(onPage)
    LaunchedEffect(vm) { for (action in vm.actions) page(action) }
    val p = projection ?: return
    val actions = remember(vm) { SettingsActions(vm) }
    SettingsScreen(p, ui, actions, topInsetDp, bottomInsetDp, visible = visible)
}

/** Everything the screen can ask for, so the screen itself holds no state. */
class SettingsActions(
    val category: (String) -> Unit = {},
    val classic: () -> Unit = {},
    val toastShown: (Long) -> Unit = {},
    private val onToggle: (SettingsControl.Switch) -> Unit = {},
    private val onChoose: (SettingsControl, String) -> Unit = { _, _ -> },
    private val onPress: (SettingsControl.Button) -> Unit = {},
    private val onReveal: (SettingsControl) -> Unit = {},
    private val onType: (SettingsControl.Field, String) -> Unit = { _, _ -> },
    private val onTime: (SettingsControl.Field, Int) -> Unit = { _, _ -> },
) : ControlTaps {
    constructor(
        vm: SettingsViewModel
    ) : this(
        vm::selectCategory,
        vm::classic,
        vm::toastShown,
        vm::toggle,
        vm::choose,
        vm::press,
        vm::reveal,
        vm::type,
        vm::time,
    )

    override fun toggle(control: SettingsControl.Switch) = onToggle(control)

    override fun choose(control: SettingsControl, value: String) = onChoose(control, value)

    override fun press(control: SettingsControl.Button) = onPress(control)

    override fun reveal(control: SettingsControl) = onReveal(control)

    override fun type(control: SettingsControl.Field, text: String) = onType(control, text)

    override fun time(control: SettingsControl.Field, minutes: Int) = onTime(control, minutes)
}

/**
 * The page's Settings as it lays them out on a phone: the head, the category chips, the screen's
 * own way back to the classic page, then each card of the selected category (and those of none) in
 * the page's order. Every row is an item of the list, keyed by the page's control ids, so each card
 * is drawn in a piece per row. The page's toast shows over the bottom bar; a hidden screen
 * ([visible] false) shows none and closes any open list of choices.
 */
@Composable
fun SettingsScreen(
    p: SettingsProjection,
    ui: SettingsUi,
    actions: SettingsActions,
    topInsetDp: Float = 64f,
    bottomInsetDp: Float = 72f,
    list: LazyListState = rememberLazyListState(),
    visible: Boolean = true,
    pickers: PickerState = remember { PickerState() },
) {
    val colors = remember(p.tokens, p.light) { PageColors(p.tokens, p.light) }
    PageTheme(colors) {
        CompositionLocalProvider(LocalScreenShown provides visible) {
            PickerHost(pickers, Modifier.fillMaxSize().background(colors.bg)) {
                val category = ui.category(p)
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = list,
                    contentPadding =
                        PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = topInsetDp.dp + 16.dp,
                            bottom = bottomInsetDp.dp + 12.dp,
                        ),
                ) {
                    item("head", "head") { SettingsHeadView(p.head) }
                    item("chips", "chips") {
                        CategoryChips(p.categories, category, actions.category)
                    }
                    item("classic", "classic") {
                        ClassicCard(p.classic.label, p.classic.hint, actions.classic)
                    }
                    p.cardsIn(category).forEach { card(it, ui, actions) }
                }
                val toast = ui.toast
                if (visible && toast != null)
                    Toast(
                        toast,
                        p,
                        Modifier.align(Alignment.BottomCenter)
                            .padding(start = 12.dp, end = 12.dp, bottom = bottomInsetDp.dp + 12.dp),
                        actions.toastShown,
                    )
            }
        }
    }
}

/**
 * A card as list items: its top, then a piece per row, the last with the card's margin under it.
 */
private fun LazyListScope.card(card: SettingsCard, ui: SettingsUi, actions: SettingsActions) {
    val boxes = rowBoxes(card)
    item("card/${card.id}", "card") {
        val c = LocalPageColors.current
        val whole = card.rows.isEmpty()
        Box(
            Modifier.fillMaxWidth()
                .padding(bottom = if (whole) 14.dp else 0.dp)
                .cardPiece(c, first = true, last = whole)
        ) {
            CardTop(card)
        }
    }
    card.rows.forEachIndexed { i, row ->
        val last = i == card.rows.lastIndex
        item(row.key(card, i), row::class.simpleName) {
            val c = LocalPageColors.current
            Box(
                Modifier.fillMaxWidth()
                    .padding(bottom = if (last) 14.dp else 0.dp)
                    .cardPiece(c, first = false, last = last)
            ) {
                RowView(row, boxes[i], ui, actions)
            }
        }
    }
}

/** The page's toast: it rises in, stays as long as the page's, fades away, and is then done. */
@Composable
private fun Toast(
    toast: SettingsToast,
    p: SettingsProjection,
    modifier: Modifier,
    onShown: (Long) -> Unit,
) {
    val shown = remember(toast.id) { Animatable(0f) }
    LaunchedEffect(toast.id) {
        shown.playToast()
        onShown(toast.id)
    }
    PageToast(toast.text, p.tokens, p.light, modifier, shown = { shown.value })
}
