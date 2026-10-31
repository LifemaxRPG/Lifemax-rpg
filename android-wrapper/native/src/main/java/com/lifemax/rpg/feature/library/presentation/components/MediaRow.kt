package com.lifemax.rpg.feature.library.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.ProgressBar
import com.lifemax.rpg.feature.library.domain.AudioProgress
import com.lifemax.rpg.feature.library.domain.MediaAction
import com.lifemax.rpg.feature.library.domain.MediaButtons
import com.lifemax.rpg.feature.library.domain.MediaItem

/**
 * A row of the list (.media-item at phone width): the finished box, then the title, the type and
 * creator, the audiobook's progress, the status and link pills, the notes and the audio buttons,
 * and Edit and Remove in a column. Every row but the [last] has a divider under it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MediaRow(
    item: MediaItem,
    buttons: MediaButtons,
    last: Boolean,
    onAction: (MediaAction) -> Unit,
) {
    val c = LocalLibraryColors.current
    Column(Modifier.cardPiece(c).padding(horizontal = 16.dp)) {
        Row(
            Modifier.padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            FinishedBox(item) { onAction(MediaAction.SetFinished(item.id, it)) }
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    color = c.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight(650),
                    lineHeight = 21.sp,
                )
                Text(
                    item.subtitle,
                    Modifier.padding(top = 3.dp),
                    color = c.muted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
                item.audio?.let { Listened(it.progress) }
                FlowRow {
                    Pill(item.statusLabel, finished = item.finished)
                    if (item.link.isNotEmpty())
                        Pill(buttons.link) { onAction(MediaAction.OpenLink(item.id)) }
                }
                if (item.notes.isNotEmpty())
                    Text(
                        item.notes,
                        Modifier.padding(top = 7.dp),
                        color = c.muted,
                        fontSize = 12.5.sp,
                        lineHeight = 18.125.sp,
                    )
                AudioButtons(item, buttons, onAction)
            }
            Column(
                Modifier.width(IntrinsicSize.Max),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PageButton(
                    buttons.edit,
                    { onAction(MediaAction.Edit(item.id)) },
                    Modifier.fillMaxWidth(),
                    size = ButtonSize.COMPACT,
                )
                PageButton(
                    buttons.remove,
                    { onAction(MediaAction.Remove(item.id)) },
                    Modifier.fillMaxWidth(),
                    size = ButtonSize.COMPACT,
                )
            }
        }
        if (!last) HorizontalDivider(thickness = 1.dp, color = c.lineSoft)
    }
}

/**
 * The page's 22 dp check box with its margins (4 dp left, 2 top, 3 right), labelled as the page
 * labels it. Compose widens its touch area to 48 dp without moving the row. The tick is part of the
 * box, so it keeps its size whatever the font size.
 */
@Composable
private fun FinishedBox(item: MediaItem, onChange: (Boolean) -> Unit) {
    val c = LocalLibraryColors.current
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier.padding(start = 4.dp, top = 2.dp, end = 3.dp)
            .size(22.dp)
            .toggleable(
                item.finished,
                interactionSource = null,
                indication = ripple(bounded = false, radius = 20.dp),
                role = Role.Checkbox,
                onValueChange = onChange,
            )
            .semantics { contentDescription = "Mark ${item.title} finished" }
            .clip(shape)
            .background(if (item.finished) c.good else c.bg)
            .border(1.5.dp, if (item.finished) c.good else BoxBorder, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (item.finished)
            with(LocalDensity.current) {
                Text(
                    "✓",
                    color = CheckInk,
                    fontSize = 14.dp.toSp(),
                    fontWeight = FontWeight.Black,
                    lineHeight = 20.dp.toSp(),
                )
            }
    }
}

/** How far into its audio an item is: the page's words and time over a 5 dp bar. */
@Composable
private fun Listened(progress: AudioProgress) {
    val c = LocalLibraryColors.current
    val figures = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
    Column(Modifier.padding(top = 8.dp, bottom = 2.dp).widthIn(max = 420.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                progress.label,
                Modifier.weight(1f),
                color = c.muted,
                fontSize = 10.5.sp,
                lineHeight = 15.75.sp,
                style = figures,
            )
            Text(
                progress.time,
                color = c.accent,
                fontSize = 10.5.sp,
                fontWeight = FontWeight(750),
                lineHeight = 15.75.sp,
                style = figures,
            )
        }
        ProgressBar(
            (progress.pct / 100).toFloat(),
            c.listened,
            height = 5.dp,
            track = c.line,
            outline = Color.Transparent,
        )
    }
}

/**
 * A small bordered pill (.media-pill) with its 5 dp margins above and after: the status, in green
 * when [finished], or, with [onOpen], the link, in the accent colour and underlined as the page's
 * anchor is.
 */
@Composable
private fun Pill(text: String, finished: Boolean = false, onOpen: (() -> Unit)? = null) {
    val c = LocalLibraryColors.current
    val shape = RoundedCornerShape(6.dp)
    Text(
        text,
        Modifier.padding(top = 5.dp, end = 5.dp)
            .clip(shape)
            .then(if (finished) Modifier.background(c.goodDim) else Modifier)
            .border(1.dp, if (finished) FinishedBorder else c.line, shape)
            .then(
                if (onOpen != null) Modifier.clickable(role = Role.Button, onClick = onOpen)
                else Modifier
            )
            .padding(horizontal = 9.dp, vertical = 4.dp),
        color =
            when {
                finished -> c.good
                onOpen != null -> c.accent
                else -> c.muted
            },
        fontSize = 10.5.sp,
        lineHeight = 15.75.sp,
        textDecoration = if (onOpen != null) TextDecoration.Underline else null,
    )
}

/** Play, Replace file and Remove audio and the file's line for an item with a file; else Attach. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AudioButtons(item: MediaItem, buttons: MediaButtons, onAction: (MediaAction) -> Unit) {
    val c = LocalLibraryColors.current
    val audio = item.audio?.takeIf { it.file.isNotEmpty() }
    FlowRow(
        Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val centred = Modifier.align(Alignment.CenterVertically)
        if (audio == null)
            PageButton(
                buttons.attach,
                { onAction(MediaAction.Attach(item.id)) },
                size = ButtonSize.SMALL,
            )
        else {
            PageButton(
                buttons.play,
                { onAction(MediaAction.Play(item.id)) },
                centred,
                primary = true,
                size = ButtonSize.SMALL,
            )
            PageButton(
                buttons.replace,
                { onAction(MediaAction.Replace(item.id)) },
                centred,
                size = ButtonSize.SMALL,
            )
            PageButton(
                buttons.removeAudio,
                { onAction(MediaAction.RemoveAudio(item.id)) },
                centred,
                size = ButtonSize.SMALL,
            )
        }
    }
    if (audio != null)
        Text(
            audio.meta,
            Modifier.padding(top = 4.dp),
            color = c.muted,
            fontSize = 11.5.sp,
            lineHeight = 17.25.sp,
        )
}
