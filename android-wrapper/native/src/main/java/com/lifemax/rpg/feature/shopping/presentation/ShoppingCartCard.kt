package com.lifemax.rpg.feature.shopping.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** What the screen can do with the phone: copying and sharing the list text. */
internal class ShoppingDeviceActions(val copy: (String) -> Unit, val share: (String) -> Unit)

/**
 * The top card: the list's summary, the primary "Add All to Walmart Cart" button, what happened
 * after it was pressed, and Copy / Share. It never claims the cart was updated: it can only say the
 * link was handed to the phone and tell the owner to check the cart.
 */
@Composable
internal fun ShoppingCartCard(
    state: ShoppingState,
    weekStart: String,
    colors: ShopColors,
    device: ShoppingDeviceActions,
) {
    val plan = state.plan
    val summary = state.summary
    val first = plan.links.getOrNull(state.cartIndex)
    ShopCard(colors) {
        ShopTitle("Weekly grocery list", colors)
        Text("Week of $weekStart", color = colors.muted, fontSize = 13.sp)
        Text(
            summary.text,
            color = colors.fg,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        ShopButton(
            text = "Add All to Walmart Cart",
            colors = colors,
            modifier = Modifier.fillMaxWidth(),
            primary = true,
            enabled = state.canAddAll,
            description =
                if (state.canAddAll) "Add all matched items to the Walmart cart"
                else "Add all to Walmart cart, unavailable: no item has a Walmart product yet",
        ) {
            state.openCart()
        }
        if (!state.canAddAll) {
            ShopBody("Add a Walmart link to at least one item to turn this on.", colors.muted)
        }
        if (plan.links.size > 1 && first != null) {
            ShopBody(
                "This list is split into ${plan.links.size} carts. Next: Cart ${first.index} of ${first.count}.",
                colors.accent,
            )
        }
        plan.issues.forEach { ShopBody(it.message, colors.warn) }
        NoticeBlock(state, colors)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            val text = { ShoppingListText.build(weekStart, state.rows) }
            ShopButton("Copy list", colors, Modifier.weight(1f)) {
                device.copy(text())
                state.announce("Shopping list copied.")
            }
            ShopButton("Share list", colors, Modifier.weight(1f)) { device.share(text()) }
        }
        state.message?.let {
            ShopBody(it, colors.accent, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        ShopBody(
            "This opens a Walmart link, not an official connection. Check your Walmart cart before and after.",
            colors.subtle,
        )
    }
}

@Composable
private fun NoticeBlock(state: ShoppingState, colors: ShopColors) {
    when (val n = state.notice) {
        null -> Unit
        is CartNotice.Handed ->
            ShopBody(
                "Opened Walmart (cart ${n.link.index} of ${n.link.count}). Check your Walmart cart.",
                colors.fg,
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        is CartNotice.Failed -> ShopBody(n.reason, colors.bad)
        is CartNotice.ConfirmRepeat -> {
            ShopBody(
                "You opened this cart a moment ago. Opening it again may add everything a second time.",
                colors.warn,
                Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ShopButton("Open again anyway", colors, Modifier.weight(1f), primary = true) {
                    state.openCart(n.link.index - 1, openAgainAnyway = true)
                }
                ShopButton("Cancel", colors, Modifier.weight(1f)) { state.dismissNotice() }
            }
        }
    }
}
