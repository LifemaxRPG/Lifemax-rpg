package com.lifemax.rpg.feature.food.presentation.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.feature.food.data.decodeNotice
import com.lifemax.rpg.feature.food.data.decodeSheet
import com.lifemax.rpg.feature.food.domain.FoodNotice
import com.lifemax.rpg.feature.food.domain.FoodSheet
import com.lifemax.rpg.feature.food.domain.SheetView
import com.lifemax.rpg.feature.food.presentation.BlueAccent
import com.lifemax.rpg.feature.food.presentation.FoodContext
import kotlinx.coroutines.delay

private const val NOTICE_MS = 4500L

/**
 * Whether the page's projection carries the add-food sheet (an older page does not, and Add food
 * hands over).
 */
internal fun FoodContext.hasSheet(): Boolean = data.has("sheet")

/**
 * The add-food sheet and the page's message, drawn over Food. A projection that does not decode is
 * not drawn (the page still owns the state and the next update replaces it).
 */
@Composable
internal fun FoodSheetHost(c: FoodContext) {
    val sheet = remember(c.data) { runCatching { decodeSheet(c.data) }.getOrNull() }
    val notice = remember(c.data) { runCatching { decodeNotice(c.data) }.getOrNull() }
    val send: (String, Map<String, Any>) -> Unit = { command, payload ->
        c.dispatch(command, payload)
    }
    Box(Modifier.fillMaxSize()) {
        if (sheet != null) SheetPanel(c, sheet, send)
        if (notice != null) NoticeSnack(c, notice, send, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun SheetPanel(c: FoodContext, sheet: FoodSheet, send: (String, Map<String, Any>) -> Unit) {
    val none = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable(none, null) {
            send("sheetClose", emptyMap())
        }
    )
    Box(Modifier.fillMaxSize().imePadding(), Alignment.BottomCenter) {
        val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        Column(
            Modifier.fillMaxWidth()
                .heightIn(max = 640.dp)
                .clip(shape)
                .background(c.cardTop)
                .clickable(none, null) {}
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetHeader(c, sheet, send)
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when (sheet.view) {
                    SheetView.Search -> SheetSearchView(c, sheet, focusOnOpen = true, send)
                    SheetView.Portion -> sheet.portion?.let { SheetPortionView(c, sheet, it, send) }
                    SheetView.Quick -> SheetQuickView(c, sheet, send)
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(
    c: FoodContext,
    sheet: FoodSheet,
    send: (String, Map<String, Any>) -> Unit,
) {
    val title =
        when (sheet.view) {
            SheetView.Search -> "Add food"
            SheetView.Portion -> "Portion"
            SheetView.Quick -> "Quick calories"
        }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (sheet.view != SheetView.Search) {
            HeaderButton(c, "‹", "Back") { send("sheetBack", emptyMap()) }
        }
        Text(
            title,
            color = c.fg,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        HeaderButton(c, "✕", "Close") { send("sheetClose", emptyMap()) }
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
        Text(glyph, color = c.muted, fontSize = 22.sp)
    }
}

/**
 * The page's message ("Added … · 222 kcal", a failed save) with its Undo, gone after a few seconds.
 */
@Composable
private fun NoticeSnack(
    c: FoodContext,
    notice: FoodNotice,
    send: (String, Map<String, Any>) -> Unit,
    modifier: Modifier,
) {
    var shownId by remember { mutableIntStateOf(0) }
    LaunchedEffect(notice.id) {
        shownId = notice.id
        delay(NOTICE_MS)
        shownId = 0
    }
    if (shownId != notice.id) return
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = c.bottomInsetDp.dp + 84.dp)
            .clip(shape)
            .background(c.selectedPanel)
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            notice.text,
            color = c.fg,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
        )
        if (notice.action.isNotEmpty()) {
            Text(
                notice.action,
                color = BlueAccent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier.heightIn(min = 48.dp)
                        .clickable { send("noticeAction", mapOf("id" to notice.id)) }
                        .padding(horizontal = 16.dp),
            )
        }
    }
}
