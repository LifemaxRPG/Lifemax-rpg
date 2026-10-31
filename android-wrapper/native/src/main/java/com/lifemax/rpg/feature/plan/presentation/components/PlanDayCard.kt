package com.lifemax.rpg.feature.plan.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.PageCard
import com.lifemax.rpg.core.designsystem.components.PageTag
import com.lifemax.rpg.core.designsystem.components.PixelIcon
import com.lifemax.rpg.feature.plan.domain.PlanDay
import com.lifemax.rpg.feature.plan.domain.PlanItem
import com.lifemax.rpg.feature.plan.domain.PlanPageAction
import com.lifemax.rpg.feature.plan.domain.SetRef

/**
 * The selected day: its title, progress tag and note, and each exercise with a box per set, the
 * weight or minutes, and the session log. The boxes are ticked here (the page saves them); the
 * fields are edited on the page. [pending] holds the state asked for boxes not yet answered.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlanDayCard(
    day: PlanDay,
    pending: Map<SetRef, Boolean>,
    onToggle: (SetRef, Boolean, Offset) -> Unit,
    onPage: (PlanPageAction) -> Unit,
) {
    val c = LocalPlanColors.current
    PageCard {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                buildAnnotatedString {
                    appendInlineContent(ICON)
                    append(" ${day.title}")
                },
                Modifier.alignByBaseline().semantics { heading() },
                color = c.textDim,
                fontSize = 13.5.sp,
                fontWeight = FontWeight(650),
                letterSpacing = (-0.135).sp,
                lineHeight = 20.25.sp,
                inlineContent =
                    mapOf(
                        ICON to
                            InlineTextContent(
                                Placeholder(18.sp, 18.sp, PlaceholderVerticalAlign.TextCenter)
                            ) {
                                PixelIcon(day.icon, c.textDim, Modifier.fillMaxSize())
                            }
                    ),
            )
            PageTag(day.tag, Modifier.alignByBaseline())
        }
        Text(
            day.note,
            Modifier.padding(vertical = 12.dp),
            color = c.muted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
        if (day.locked && !day.rest) LockedNote()
        day.items.forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider(color = c.lineSoft)
            PlanItemRow(day, item, pending, onToggle, onPage)
        }
    }
}

@Composable
private fun LockedNote() {
    val c = LocalPlanColors.current
    val shape = RoundedCornerShape(10.dp)
    Text(
        "Locked until the day arrives.",
        Modifier.padding(bottom = 8.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(c.noteFill)
            .border(1.dp, c.noteBorder, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        color = c.noteText,
        fontSize = 12.5.sp,
        lineHeight = 18.75.sp,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanItemRow(
    day: PlanDay,
    item: PlanItem,
    pending: Map<SetRef, Boolean>,
    onToggle: (SetRef, Boolean, Offset) -> Unit,
    onPage: (PlanPageAction) -> Unit,
) {
    val c = LocalPlanColors.current
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                item.title,
                Modifier.alignByBaseline(),
                color = c.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 21.sp,
            )
            if (item.optional) PageTag("optional", Modifier.alignByBaseline(), FontWeight.Bold)
        }
        Text(item.detail, color = c.muted, fontSize = 12.sp, lineHeight = 18.sp)
        FlowRow(
            Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item.sets.forEachIndexed { i, saved ->
                val set = SetRef(day.date, item.key, i)
                val asked = pending[set]
                SetBox(
                    checked = asked ?: saved,
                    pending = asked != null,
                    enabled = !day.locked,
                    label = "${item.title} set ${i + 1}",
                ) { checked, at ->
                    onToggle(set, checked, at)
                }
            }
        }
        item.value?.let { value ->
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Field(
                    value.text,
                    value.placeholder,
                    "${value.placeholder}, ${item.title}",
                    enabled = !day.locked,
                    Modifier.width(170.dp),
                    PaddingValues(horizontal = 11.dp, vertical = 8.dp),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                ) {
                    onPage(PlanPageAction.EditValue(day.date, item.key))
                }
                if (value.last.isNotEmpty())
                    Text(
                        value.last,
                        Modifier.padding(start = 4.dp),
                        color = c.muted,
                        fontSize = 11.5.sp,
                    )
            }
        }
        Text(
            "Session log",
            Modifier.padding(top = 8.dp, bottom = 4.dp),
            color = c.muted,
            fontSize = 11.sp,
            fontWeight = FontWeight(550),
            lineHeight = 16.5.sp,
        )
        // the page's text area sits on a line of text, which leaves 5 dp under it
        Field(
            item.log.text,
            "e.g. 3×10 @ 120lb, felt strong",
            "Session log, ${item.title}",
            enabled = !day.locked,
            Modifier.padding(bottom = 5.dp)
                .fillMaxWidth()
                .heightIn(min = 53.dp)
                .alpha(if (day.locked) 0.55f else 1f),
            PaddingValues(horizontal = 11.dp, vertical = 9.dp),
            fontSize = 12.5.sp,
            lineHeight = 17.5.sp,
        ) {
            onPage(PlanPageAction.EditLog(day.date, item.key))
        }
        if (item.log.previous.isNotEmpty())
            Text(
                item.log.previous,
                Modifier.padding(top = 4.dp),
                color = c.muted,
                fontSize = 11.sp,
                lineHeight = 14.85.sp,
            )
    }
}

/**
 * A set's box: the page's 20 dp check box with its margins (4 dp left, 1 top, 3 right, 3 bottom),
 * so the boxes are 35 dp apart as on the page. Compose widens a small target's touch area to 48 dp,
 * and a tap between two boxes goes to the nearer one. A box waiting for the page's answer shows the
 * state asked for, faded.
 */
@Composable
private fun SetBox(
    checked: Boolean,
    pending: Boolean,
    enabled: Boolean,
    label: String,
    onChange: (Boolean, Offset) -> Unit,
) {
    val c = LocalPlanColors.current
    val anchor = remember { Anchor() }
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier.padding(start = 4.dp, top = 1.dp, end = 3.dp, bottom = 3.dp)
            .size(20.dp)
            .onPlaced { anchor.coordinates = it }
            .toggleable(
                checked,
                interactionSource = null,
                indication = ripple(bounded = false, radius = 20.dp),
                enabled = enabled,
                role = Role.Checkbox,
            ) {
                onChange(it, anchor.center)
            }
            .clearAndSetSemantics {
                contentDescription = label
                if (pending) stateDescription = if (checked) "Ticking" else "Clearing"
            }
            .alpha(if (pending) 0.6f else 1f)
            .clip(shape)
            .background(if (checked) c.tickedFill else c.field)
            .border(1.5.dp, if (checked) c.tickedBorder else c.boxBorder, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked)
            Text(
                "✓",
                color = CheckInk,
                fontSize = 12.sp,
                fontWeight = FontWeight(800),
                lineHeight = 12.sp,
            )
    }
}

/**
 * A value or session-log field. The page edits it: tapping hands the field over to the page. The
 * [padding] includes the page field's 1 dp border.
 */
@Composable
private fun Field(
    text: String,
    placeholder: String,
    label: String,
    enabled: Boolean,
    modifier: Modifier,
    padding: PaddingValues,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    onEdit: () -> Unit,
) {
    val c = LocalPlanColors.current
    val shape = RoundedCornerShape(8.dp)
    Text(
        text.ifEmpty { placeholder },
        modifier
            .clip(shape)
            .background(c.field)
            .border(1.dp, c.line, shape)
            .clickable(enabled, "Edit on the page", Role.Button, onEdit)
            .semantics { contentDescription = "$label: ${text.ifEmpty { "empty" }}" }
            .padding(padding),
        color = if (text.isEmpty()) c.muted else c.text,
        fontSize = fontSize,
        lineHeight = lineHeight,
        maxLines = 6,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Where a box sits on screen: the celebration of a completed day starts from it. */
private class Anchor {
    var coordinates: LayoutCoordinates? = null
    val center: Offset
        get() = coordinates?.takeIf { it.isAttached }?.boundsInRoot()?.center ?: Offset.Unspecified
}

private const val ICON = "icon"
