package com.lifemax.rpg.feature.food.presentation.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.feature.food.domain.FoodSheet
import com.lifemax.rpg.feature.food.domain.SheetRow
import com.lifemax.rpg.feature.food.presentation.BlueAccent
import com.lifemax.rpg.feature.food.presentation.FoodContext
import com.lifemax.rpg.feature.food.presentation.fmtNum
import kotlinx.coroutines.delay

/**
 * How long the sheet waits after the last key before it asks the page to search (the page's own
 * wait).
 */
private const val SEARCH_WAIT_MS = 110L

/**
 * The search view: a search box, the page's tabs, the foods the page found (or its words when there
 * are none) and the two side actions. The text typed is the box's own; the page is asked after a
 * short pause, and a change the page makes (a tab clears the search) is taken back into the box.
 */
@Composable
internal fun SheetSearchView(
    c: FoodContext,
    sheet: FoodSheet,
    focusOnOpen: Boolean,
    actions: FoodSheetController,
    modifier: Modifier,
) {
    var text by
        rememberSynced(sheet.query, SEARCH_WAIT_MS) { actions.send("sheetQuery", mapOf("q" to it)) }
    val focus = remember { FocusRequester() }
    if (focusOnOpen)
        LaunchedEffect(Unit) {
            delay(320)
            runCatching { focus.requestFocus() }
        }
    // the box and the tabs stay put, as the page's .fv2-sb does; the list scrolls under them
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SheetTextField(
            c,
            text,
            { text = it },
            sheet.words.search,
            Modifier.fillMaxWidth(),
            focus = focus,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sheet.tabs.forEach { tab ->
                SheetChip(
                    c,
                    tab.label,
                    tab.id == sheet.tab,
                    { actions.send("sheetTab", mapOf("tab" to tab.id)) },
                )
            }
        }
        SheetScaffold(
            Modifier.weight(1f, fill = false),
            foot = {
                GhostRow(
                    c,
                    sheet.words.quick to { actions.send("sheetQuick") },
                    // the page decides: its create form in this sheet, or the page's own sheet
                    sheet.words.create to { actions.send("sheetCreate") },
                )
            },
        ) {
            // A plain column: the page caps the list
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                sheet.rows.forEach { row -> FoodRow(c, row, sheet.words.kcal, actions) }
                if (sheet.searching) Note(c, sheet.words.searching, "")
                sheet.empty?.let { e -> Note(c, e.title, e.text) }
            }
        }
    }
}

/**
 * A food: a tap opens its portion view, the + logs it at its own amount at once. TalkBack reads the
 * food's words and kcal as one button, and the + by the page's label for it ("Add …").
 */
@Composable
private fun FoodRow(c: FoodContext, row: SheetRow, kcal: String, actions: FoodSheetController) {
    val ref = mapOf<String, Any>("index" to row.index, "id" to row.id)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.weight(1f)
                .clickable(role = Role.Button) { actions.send("sheetPick", ref) }
                .semantics(mergeDescendants = true) {}
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    row.name,
                    color = c.fg,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${row.detail} · P ${row.protein} C ${row.carbs} F ${row.fat}",
                    color = c.muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(fmtNum(row.kcal), color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(kcal, color = c.subtle, fontSize = 10.sp)
            }
        }
        val shape = RoundedCornerShape(14.dp)
        Box(
            Modifier.size(44.dp)
                .clip(shape)
                .background(c.sunken)
                .border(1.dp, c.panelBorder, shape)
                .semantics {
                    role = Role.Button
                    contentDescription = "Add ${row.name}"
                }
                .clickable { actions.write("sheetRowAdd", row.id, ref) },
            Alignment.Center,
        ) {
            Text(
                "+",
                color = BlueAccent,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

/**
 * What an empty list says (a title and a line), or the quiet "Searching…"; read out as it shows.
 */
@Composable
private fun Note(c: FoodContext, title: String, text: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 18.dp).semantics {
            liveRegion = LiveRegionMode.Polite
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (text.isNotEmpty()) Text(text, color = c.muted, fontSize = 13.sp)
    }
}
