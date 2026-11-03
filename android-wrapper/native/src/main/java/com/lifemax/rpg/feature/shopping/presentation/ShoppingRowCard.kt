package com.lifemax.rpg.feature.shopping.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.feature.shopping.domain.ProductMatch

/**
 * One ingredient of the list: name and amount, the quantity to buy (editable), the matched Walmart
 * product, and the ways to change all that. An ingredient with no product is drawn with an
 * attention border and an "Add Walmart link" action; nothing is ever matched by guessing.
 */
@Composable
internal fun ShoppingRowCard(row: ShoppingRow, state: ShoppingState, colors: ShopColors) {
    var editing by remember(row.key) { mutableStateOf(false) }
    val matched = row.match as? ProductMatch.Matched
    val attention = row.needsProduct
    ShopCard(colors, tint = if (attention) colors.warn else null) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    row.item.name.replaceFirstChar { it.uppercase() },
                    color = colors.fg,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(amountLine(row), color = colors.muted, fontSize = 13.sp)
            }
            if (attention) ShopPill("Needs a product", colors.warn, colors.warnFill)
            else if (row.owned) ShopPill("Already own", colors.good, colors.sunken)
        }
        if (!row.owned) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text("Quantity", color = colors.muted, fontSize = 13.sp)
                ShopStepper(
                    row.quantity,
                    colors,
                    "${row.item.name} quantity",
                    1,
                    ShoppingState.MAX_QUANTITY_EDIT,
                ) {
                    state.setQuantity(row.key, it)
                }
            }
            if (matched != null)
                MatchedProduct(row, matched, state, colors, editing) { editing = it }
            else UnmatchedActions(row, state, colors, editing) { editing = it }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ShopButton(
                if (row.owned) "I need it" else "Already own",
                colors,
                Modifier.weight(1f),
                description =
                    if (row.owned) "I need ${row.item.name}" else "I already own ${row.item.name}",
            ) {
                state.setOwned(row.key, !row.owned)
            }
            ShopButton(
                "Remove",
                colors,
                Modifier.weight(1f),
                description = "Remove ${row.item.name} from the list",
            ) {
                state.remove(row.key)
            }
        }
    }
}

@Composable
private fun MatchedProduct(
    row: ShoppingRow,
    matched: ProductMatch.Matched,
    state: ShoppingState,
    colors: ShopColors,
    editing: Boolean,
    setEditing: (Boolean) -> Unit,
) {
    val p = matched.product
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            buildString {
                append("Walmart item ").append(p.productId)
                if (p.label.isNotBlank()) append(" · ").append(p.label)
                if (p.note.isNotBlank()) append(" · ").append(p.note)
            },
            color = colors.good,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        if (matched.source == com.lifemax.rpg.feature.shopping.domain.MatchSource.SESSION) {
            Text("Saving this choice…", color = colors.subtle, fontSize = 12.sp)
        }
        if (editing) {
            ShopProductForm(
                row.item.name,
                p,
                colors,
                { pasted, label, note ->
                    state.setProduct(row.key, pasted, label, note).also {
                        if (it == null) setEditing(false)
                    }
                },
            ) {
                setEditing(false)
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ShopButton(
                    "Change product",
                    colors,
                    Modifier.weight(1f),
                    description = "Change the Walmart product for ${row.item.name}",
                ) {
                    setEditing(true)
                }
                ShopButton(
                    "Forget",
                    colors,
                    Modifier.weight(1f),
                    description = "Forget the Walmart product for ${row.item.name}",
                ) {
                    state.clearProduct(row.key)
                }
            }
        }
    }
}

@Composable
private fun UnmatchedActions(
    row: ShoppingRow,
    state: ShoppingState,
    colors: ShopColors,
    editing: Boolean,
    setEditing: (Boolean) -> Unit,
) {
    if (editing) {
        ShopProductForm(
            row.item.name,
            null,
            colors,
            { pasted, label, note ->
                state.setProduct(row.key, pasted, label, note).also {
                    if (it == null) setEditing(false)
                }
            },
        ) {
            setEditing(false)
        }
        return
    }
    Text(
        "Pick the Walmart product yourself. LifeMax never guesses a product from a name.",
        color = colors.muted,
        fontSize = 12.sp,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ShopButton(
            "Add Walmart link",
            colors,
            Modifier.weight(1f),
            primary = true,
            description = "Add a Walmart link for ${row.item.name}",
        ) {
            setEditing(true)
        }
        ShopButton(
            "Search on Walmart",
            colors,
            Modifier.weight(1f),
            description = "Search Walmart for ${row.item.name}",
        ) {
            state.search(row.item.name)
        }
    }
}

/** `454 g · 1 pack (500 g)`: what is needed and how it turns into packages. */
internal fun amountLine(row: ShoppingRow): String {
    val item = row.item
    val needed = item.amount.text() + " needed"
    val pack = if (item.packLabel.isNotBlank()) " · sold as ${item.packLabel}" else ""
    val source = if (item.origins.isNotEmpty()) " · " + item.origins.joinToString(", ") else ""
    return needed + pack + source
}
