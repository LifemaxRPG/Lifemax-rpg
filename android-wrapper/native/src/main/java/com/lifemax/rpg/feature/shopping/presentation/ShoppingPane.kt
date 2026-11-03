package com.lifemax.rpg.feature.shopping.presentation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.feature.shopping.data.AndroidUrlOpener
import com.lifemax.rpg.feature.shopping.data.WalmartCartLauncher
import org.json.JSONObject

/**
 * The state of the shopping pane for one Food screen: built from the first projection so the first
 * frame already shows the list, and fed every later one. Dispatches go to the page.
 */
@Composable
internal fun rememberShoppingState(
    shopping: JSONObject?,
    dispatch: (command: String, payload: Map<String, Any>) -> Unit,
): ShoppingState {
    val context = LocalContext.current
    val state = remember {
        ShoppingState(WalmartCartLauncher(AndroidUrlOpener(context)), dispatch).also {
            it.accept(shopping)
        }
    }
    LaunchedEffect(shopping) { state.accept(shopping) }
    return state
}

/**
 * The weekly grocery list with its Walmart cart: the cart card, the unmatched items first with
 * their attention border, then the matched ones, the ones already owned, lines to check by hand,
 * and the recipes that can be added. Drawn only when the page advertises and projects shopping;
 * otherwise the Food tab keeps the classic shopping pane.
 */
@Composable
internal fun ShoppingPane(state: ShoppingState, weekStart: String, light: Boolean) {
    val colors = ShopColors(light)
    val context = LocalContext.current
    val device = remember(context) { deviceActions(context) }
    val rows = state.rows
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShoppingCartCard(state, weekStart, colors, device)
        ShoppingRecipePicker(state, colors)
        val need = rows.filter { it.needsProduct }
        val ready = rows.filter { !it.owned && !it.needsProduct }
        val owned = rows.filter { it.owned }
        Section("Need a product", need, state, colors)
        Section("Matched to Walmart", ready, state, colors)
        Section("Already own", owned, state, colors)
        if (state.review.isNotEmpty()) {
            ShopCard(colors) {
                ShopTitle("Check these by hand", colors)
                ShopBody("These recipe lines have no amount LifeMax can read.", colors.muted)
                state.review.forEach { ShopBody("${it.text} (${it.origin})", colors.fg) }
            }
        }
        if (state.removedCount > 0) {
            Row(Modifier.fillMaxWidth()) {
                ShopButton(
                    "Bring back ${state.removedCount} removed",
                    colors,
                    Modifier.fillMaxWidth(),
                ) {
                    state.restoreRemoved()
                }
            }
        }
    }
}

@Composable
private fun Section(
    title: String,
    rows: List<ShoppingRow>,
    state: ShoppingState,
    colors: ShopColors,
) {
    if (rows.isEmpty()) return
    Text(
        "${title.uppercase()} · ${rows.size}",
        color = colors.accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.2.sp,
    )
    rows.forEach { ShoppingRowCard(it, state, colors) }
}

private fun deviceActions(context: Context) =
    ShoppingDeviceActions(
        copy = { text ->
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("Shopping list", text))
        },
        share = { text ->
            val send =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
            val chooser = Intent.createChooser(send, "Share shopping list")
            if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(chooser)
            } catch (e: Exception) {
                // no share target: nothing to do, the list can still be copied
            }
        },
    )
