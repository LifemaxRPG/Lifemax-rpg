package com.lifemax.rpg.feature.food.presentation.sheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.feature.food.domain.FoodSwap
import com.lifemax.rpg.feature.food.domain.SwapRow
import com.lifemax.rpg.feature.food.presentation.FoodContext

/**
 * The Smart Diet swap sheet: the other meals the page offers for the plan's meal, worded as the
 * page words them. A tap on one sends the swap as a write (an operation id per meal, kept until the
 * page answers); the page saves it, closes the sheet and shows its snackbar with Undo. The button
 * back to the plan is the page's, only while the meal is swapped.
 */
@Composable
internal fun SwapPanel(c: FoodContext, swap: FoodSwap, actions: FoodSheetController) {
    SheetFrame(c, swap.title, onScrim = { actions.send("swapClose") }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                swap.title,
                color = c.fg,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(start = 4.dp).semantics { heading() },
            )
            HeaderButton(c, "✕", swap.close) { actions.send("swapClose") }
        }
        SheetScaffold(
            Modifier.weight(1f, fill = false),
            foot = {
                swap.reset?.let { label ->
                    RowScopeGhost(
                        c,
                        label,
                        {
                            actions.write(
                                "swapReset",
                                "${swap.index}:reset",
                                mapOf("index" to swap.index),
                            )
                        },
                        Modifier.fillMaxWidth(),
                    )
                }
            },
        ) {
            Text(note(swap, c), color = c.muted, fontSize = 13.sp, lineHeight = 19.sp)
            swap.rows.forEach { row -> SwapRowView(c, swap.index, row, actions) }
            swap.none?.let { Empty(c, it) }
        }
    }
}

/** "Instead of **Meal**. The rest of the day rescales to keep your target." */
private fun note(swap: FoodSwap, c: FoodContext): AnnotatedString = buildAnnotatedString {
    append(swap.lead)
    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = c.fg)) { append(swap.name) }
    append(swap.tail)
}

/** A meal on offer, one button for TalkBack: its name, its words and its kcal. */
@Composable
private fun SwapRowView(c: FoodContext, index: Int, row: SwapRow, actions: FoodSheetController) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(role = Role.Button) {
                actions.write(
                    "swapPick",
                    "$index:${row.id}",
                    mapOf("index" to index, "id" to row.id),
                )
            }
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                row.name,
                color = c.fg,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                row.detail,
                color = c.muted,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(row.kcal, color = c.fg, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text(row.unit.uppercase(), color = c.subtle, fontSize = 10.sp)
        }
    }
}

/** The page's words for a plan with nothing else to offer. */
@Composable
private fun Empty(c: FoodContext, words: String) {
    Text(
        words,
        color = c.fg,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        modifier =
            Modifier.fillMaxWidth().padding(vertical = 18.dp).semantics {
                liveRegion = LiveRegionMode.Polite
            },
    )
}
