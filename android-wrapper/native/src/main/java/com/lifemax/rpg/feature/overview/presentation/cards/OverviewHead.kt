package com.lifemax.rpg.feature.overview.presentation.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.PixelIcon
import com.lifemax.rpg.core.designsystem.components.pageShadow
import com.lifemax.rpg.core.designsystem.components.tapTarget
import com.lifemax.rpg.core.designsystem.mix
import com.lifemax.rpg.feature.character.presentation.components.CharacterClassCrest
import com.lifemax.rpg.feature.overview.domain.OverviewHead
import com.lifemax.rpg.feature.overview.domain.OverviewTip
import com.lifemax.rpg.feature.overview.presentation.LocalOverviewColors

/**
 * The head (.ov-head): the date in the accent, the greeting (the page's h1), and on the right the
 * Edit button above the class crest (none without a class). The button is lit while the editor is
 * open ([editing]), as the page's .ov-editbtn.on.
 */
@Composable
internal fun OverviewHeadRow(
    head: OverviewHead,
    onEdit: () -> Unit,
    editing: Boolean = false,
    focus: FocusRequester? = null,
) {
    val c = LocalOverviewColors.current
    Row(
        Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                head.date,
                color = c.accentStrong,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.2.sp,
                lineHeight = 16.5.sp,
            )
            Text(
                head.hello,
                Modifier.padding(top = 7.dp).semantics { heading() },
                color = c.text,
                fontSize = 29.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1.015).sp,
                lineHeight = 31.9.sp,
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EditButton(head, onEdit, editing, focus)
            if (head.classId.isNotEmpty())
                CharacterClassCrest(head.classId, Modifier.size(54.dp).clearAndSetSemantics {})
        }
    }
}

/** .ov-editbtn: the grid icon and "Edit" in a pill; TalkBack says what it edits. */
@Composable
private fun EditButton(
    head: OverviewHead,
    onEdit: () -> Unit,
    on: Boolean,
    focus: FocusRequester?,
) {
    val c = LocalOverviewColors.current
    val shape = RoundedCornerShape(99.dp)
    val ink = if (on) c.text else c.muted
    Row(
        Modifier.then(if (focus == null) Modifier else Modifier.focusRequester(focus))
            .tapTarget(Role.Button, onClick = onEdit)
            // the page's aria-expanded: the editor opens in place under this button, and the
            // screen reader says expanded or collapsed in its own words
            .semantics {
                if (on)
                    collapse {
                        onEdit()
                        true
                    }
                else
                    expand {
                        onEdit()
                        true
                    }
            }
            .clearAndSetSemantics { contentDescription = head.editA11y }
            .clip(shape)
            .heightIn(min = 32.dp)
            .background(if (on) c.accent.mix(0.12f, c.panel) else c.editFill)
            .border(1.dp, if (on) c.accent.copy(alpha = 0.55f) else c.line, shape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelIcon("grid", ink, Modifier.size(12.dp).alpha(0.85f))
        Text(
            head.editLabel,
            color = ink,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 18.75.sp,
        )
    }
}

/**
 * The page's feature tip (.feature-tip) under the head: its icon, title and words, and a ✕ that
 * dismisses it. The ✕ is 28 dp as on the page; its 48 dp target lies over the tip's corner.
 */
@Composable
internal fun OverviewTipCard(tip: OverviewTip, onDismiss: () -> Unit) {
    val c = LocalOverviewColors.current
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.padding(bottom = 14.dp)
            .fillMaxWidth()
            .pageShadow(shape, c.light)
            .clip(shape)
            .background(c.tipFill)
            .border(1.dp, c.tipBorder, shape)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // the page hides its icon from screen readers (aria-hidden)
            Box(
                Modifier.size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.accentDim)
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Text(tip.icon, color = c.accentStrong, fontSize = 18.sp, lineHeight = 27.sp)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    tip.title,
                    Modifier.padding(bottom = 3.dp),
                    color = c.text,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight(650),
                    lineHeight = 20.25.sp,
                )
                Text(tip.body, color = c.muted, fontSize = 12.5.sp, lineHeight = 18.125.sp)
            }
            Spacer(Modifier.size(28.dp))
        }
        Box(
            Modifier.align(Alignment.TopEnd)
                .padding(top = 4.dp, end = 6.dp)
                .clip(CircleShape)
                .tapTarget(Role.Button, onClick = onDismiss)
                .clearAndSetSemantics { contentDescription = tip.dismiss }
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(c.panel2)
                .border(1.dp, c.line, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("✕", color = c.muted, fontSize = 14.sp, lineHeight = 21.sp)
        }
    }
}
