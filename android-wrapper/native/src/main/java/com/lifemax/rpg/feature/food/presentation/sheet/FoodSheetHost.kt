package com.lifemax.rpg.feature.food.presentation.sheet

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.feature.food.data.decodeAnswer
import com.lifemax.rpg.feature.food.data.decodeNotice
import com.lifemax.rpg.feature.food.data.decodeSheet
import com.lifemax.rpg.feature.food.domain.FoodSheet
import com.lifemax.rpg.feature.food.domain.SheetView
import com.lifemax.rpg.feature.food.presentation.FoodContext

/**
 * Whether the page's projection carries the add-food sheet (an older page does not, and Add food
 * hands over).
 */
internal fun FoodContext.hasSheet(): Boolean = data.has("sheet")

/**
 * The add-food sheet and the page's message, drawn over Food. A sheet that does not decode is not
 * drawn, and the page is told to close it, so the page never holds a sheet the player cannot see
 * (Android Back would step through it invisibly).
 */
@Composable
internal fun FoodSheetHost(c: FoodContext) {
    val decoded = remember(c.data) { runCatching { decodeSheet(c.data) } }
    val sheet = decoded.getOrNull()
    val notice = remember(c.data) { runCatching { decodeNotice(c.data) }.getOrNull() }
    val answer = remember(c.data) { runCatching { decodeAnswer(c.data) }.getOrNull() }
    val actions = remember { FoodSheetController(c.dispatch) }
    actions.dispatch = c.dispatch
    LaunchedEffect(answer) { actions.answered(answer) }
    LaunchedEffect(decoded) {
        decoded.exceptionOrNull()?.let {
            Log.w("NativeNutrition", "Add-food sheet not drawn: ${it.message}")
            actions.send("sheetClose")
        }
    }
    Box(Modifier.fillMaxSize()) {
        if (sheet != null) SheetPanel(c, sheet, actions)
        if (notice != null) {
            val place = if (sheet != null) Alignment.TopCenter else Alignment.BottomCenter
            FoodNoticeSnack(c, notice, actions, sheet != null, Modifier.align(place))
        }
    }
}

@Composable
private fun SheetPanel(c: FoodContext, sheet: FoodSheet, actions: FoodSheetController) {
    // the dimmed Food around the sheet: a tap there steps back, as the page's own overlay does;
    // TalkBack skips it (the head's button does the same)
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clearAndSetSemantics {}
            .pointerInput(Unit) { detectTapGestures { actions.send("sheetBack") } }
    )
    // the shared bottom bar lies over this screen's bottom edge; while the keyboard is up the bar
    // hides and the content is already lifted above the keyboard (NAV-5)
    val bottom = if (rememberImeUp()) 16.dp else c.bottomInsetDp.dp + 16.dp
    Box(Modifier.fillMaxSize(), Alignment.BottomCenter) {
        val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        Column(
            Modifier.fillMaxWidth()
                // as tall as its view needs, at least the page's min-height, never over the header
                .padding(top = c.topInsetDp.dp)
                .heightIn(min = 560.dp)
                .clip(shape)
                .background(c.cardTop)
                // a tap on the sheet itself stays on it, and is no button for TalkBack
                .pointerInput(Unit) { detectTapGestures {} }
                .semantics { paneTitle = sheet.title }
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetHeader(c, sheet, actions)
            // each view scrolls its own body over its own foot (SheetScaffold)
            val room = Modifier.weight(1f, fill = false)
            // a view's own boxes start afresh each time it opens, as the page draws it anew
            key(sheet.view, sheet.portion?.edit?.id) {
                when (sheet.view) {
                    SheetView.Search -> SheetSearchView(c, sheet, focusOnOpen = true, actions, room)
                    SheetView.Portion,
                    SheetView.Edit ->
                        sheet.portion?.let { SheetPortionView(c, sheet, it, actions, room) }
                    SheetView.Quick ->
                        sheet.quick?.let { SheetQuickView(c, sheet, it, actions, room) }
                    SheetView.Create -> sheet.create?.let { SheetCreateView(c, it, actions, room) }
                    SheetView.Goal -> sheet.goal?.let { SheetGoalView(c, it, actions, room) }
                }
            }
        }
    }
}

/** The page's head: its title and one button, Back where the page steps back, else Close. */
@Composable
private fun SheetHeader(c: FoodContext, sheet: FoodSheet, actions: FoodSheetController) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (sheet.back) HeaderButton(c, "‹", sheet.words.back) { actions.send("sheetBack") }
        Text(
            sheet.title,
            color = c.fg,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).padding(start = 4.dp).semantics { heading() },
        )
        if (!sheet.back) HeaderButton(c, "✕", sheet.words.close) { actions.send("sheetClose") }
    }
}

@Composable
private fun HeaderButton(c: FoodContext, glyph: String, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp)
            .semantics {
                role = Role.Button
                contentDescription = label
            }
            .clickable(onClick = onClick),
        Alignment.Center,
    ) {
        Text(glyph, color = c.muted, fontSize = 22.sp, modifier = Modifier.clearAndSetSemantics {})
    }
}
