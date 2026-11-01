package com.lifemax.rpg.feature.settings.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LocalPageColors
import com.lifemax.rpg.core.designsystem.components.PageEyebrow
import com.lifemax.rpg.feature.settings.domain.SettingsCategory
import com.lifemax.rpg.feature.settings.domain.SettingsHead
import kotlin.math.roundToInt

/**
 * The page head (.pagehead at phone width, 01-base.css:75-87): the eyebrow, the title (21/650, a
 * heading for TalkBack) and the blurb (13.5/20.25 muted), 18 dp above what follows.
 */
@Composable
internal fun SettingsHeadView(head: SettingsHead) {
    val c = LocalPageColors.current
    Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        PageEyebrow(head.eyebrow)
        Text(
            head.title,
            Modifier.padding(top = 6.dp).semantics { heading() },
            color = c.text,
            fontSize = 21.sp,
            fontWeight = FontWeight(650),
            letterSpacing = (-0.63).sp,
            lineHeight = 24.15.sp,
        )
        if (head.blurb.isNotEmpty())
            Text(
                head.blurb,
                Modifier.padding(top = 6.dp),
                color = c.muted,
                fontSize = 13.5.sp,
                lineHeight = 20.25.sp,
            )
    }
}

/**
 * The category chips (.set-cats, 07-finishing.css:310-313): a strip that scrolls sideways, 8 dp
 * apart, 2 dp above and 14 below; each a pill at least 38 dp tall with a line edge and muted 13/600
 * text, the selected one filled with the accent and dark text in both themes. The strip scrolls the
 * selected chip into view (the page's last chips start off the screen). TalkBack reads them as
 * tabs. Compose widens each chip's touch area to 48 dp.
 */
@Composable
internal fun CategoryChips(
    categories: List<SettingsCategory>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val c = LocalPageColors.current
    val shape = RoundedCornerShape(999.dp)
    val scroll = rememberScrollState()
    val spans = remember { mutableStateMapOf<String, IntRange>() }
    var viewport by remember { mutableIntStateOf(0) }
    val span = spans[selected]
    LaunchedEffect(selected, span, viewport) {
        if (span == null || viewport == 0) return@LaunchedEffect
        val target =
            when {
                span.first < scroll.value -> span.first
                span.last > scroll.value + viewport -> span.last - viewport
                else -> return@LaunchedEffect
            }
        scroll.scrollTo(target.coerceIn(0, scroll.maxValue))
    }
    Row(
        Modifier.fillMaxWidth()
            .onSizeChanged { viewport = it.width }
            .horizontalScroll(scroll)
            .padding(top = 2.dp, bottom = 14.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.forEach { category ->
            val on = category.id == selected
            Box(
                Modifier.onPlaced {
                        val x = it.positionInParent().x.roundToInt()
                        spans[category.id] = x..x + it.size.width
                    }
                    .defaultMinSize(minHeight = 38.dp)
                    .clip(shape)
                    .background(if (on) c.accent else Color.Transparent)
                    .border(1.dp, if (on) Color.Transparent else c.line, shape)
                    .selectable(on, role = Role.Tab) { onSelect(category.id) }
                    .padding(horizontal = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    category.name,
                    color = if (on) ChipOnText else c.muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 19.5.sp,
                    softWrap = false,
                )
            }
        }
    }
}
