package com.lifemax.rpg.feature.food.presentation.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.feature.food.domain.FoodNotice
import com.lifemax.rpg.feature.food.presentation.BlueAccent
import com.lifemax.rpg.feature.food.presentation.FoodContext
import kotlinx.coroutines.delay

/** How long the snackbar stays, a little longer than the page's 4.2 s so Undo is easy to reach. */
private const val NOTICE_MS = 4500L

/**
 * The page's message as a native snackbar ("Added … · 222 kcal", "Removed …", a refusal, a failed
 * save), with the page's action (Undo) as its button: a tap sends the page's own undo, once per
 * message, through the write channel (so a lost answer is not undone twice). TalkBack reads it as
 * it appears. Over the open sheet it shows at the top, where it hides no box or button; otherwise
 * above the bottom bar and the search dock, where the page shows its own.
 */
@Composable
internal fun FoodNoticeSnack(
    c: FoodContext,
    notice: FoodNotice,
    actions: FoodSheetController,
    overSheet: Boolean,
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
    val place =
        if (overSheet) Modifier.padding(top = c.topInsetDp.dp + 8.dp)
        else Modifier.padding(bottom = c.bottomInsetDp.dp + 84.dp)
    Row(
        modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = 16.dp)
            .then(place)
            .clip(shape)
            .background(c.selectedPanel)
            .semantics { liveRegion = LiveRegionMode.Polite }
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
                textAlign = TextAlign.Center,
                modifier =
                    Modifier.heightIn(min = 48.dp)
                        .clickable(role = Role.Button) {
                            actions.write(
                                "noticeAction",
                                notice.id.toString(),
                                mapOf("id" to notice.id),
                            )
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
    }
}
