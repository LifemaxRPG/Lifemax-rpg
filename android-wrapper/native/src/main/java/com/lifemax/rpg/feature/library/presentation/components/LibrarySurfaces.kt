package com.lifemax.rpg.feature.library.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.pageButtonShadow

/** The page's .btn sizes: padding inside the border, font size, weight and line height. */
internal enum class ButtonSize(
    val horizontal: Dp,
    val vertical: Dp,
    val fontSize: TextUnit,
    val weight: FontWeight,
    val lineHeight: TextUnit,
) {
    REGULAR(14.dp, 10.dp, 14.sp, FontWeight(650), 21.sp),
    SMALL(10.dp, 7.dp, 12.sp, FontWeight.SemiBold, 18.sp),
    /** .btn.small in a row's Edit and Remove on phones. */
    COMPACT(8.dp, 6.dp, 12.sp, FontWeight.SemiBold, 18.sp),
}

/**
 * The page's .btn at the page's own sizes: the accent button when [primary], otherwise .secondary,
 * with its border (which the page draws outside the padding) and, in the light theme, its shadow.
 * Unlike core's PageButton it keeps the page's drawn height (41, 34 or 32 dp), which Compose widens
 * to 48 dp for touch without moving anything (backlog DS-3), and draws no top edge. TalkBack hears
 * [text] without the symbols in front of it ("＋ Add media" is "Add media").
 */
@Composable
internal fun LibraryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    size: ButtonSize = ButtonSize.REGULAR,
) {
    val c = LocalLibraryColors.current
    val shape = RoundedCornerShape(8.dp)
    val border = if (primary) 0.dp else 1.dp
    Box(
        modifier
            .pageButtonShadow(shape, c.light, primary)
            .clip(shape)
            .background(if (primary) c.accent else c.secondaryButton)
            .then(if (primary) Modifier else Modifier.border(border, c.line, shape))
            .semantics { contentDescription = spoken(text) }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = size.horizontal + border, vertical = size.vertical + border),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (primary) c.onAccent else c.text,
            fontSize = size.fontSize,
            fontWeight = size.weight,
            lineHeight = size.lineHeight,
            textAlign = TextAlign.Center,
        )
    }
}

/** What TalkBack says for a label the page words with a symbol in front ("▶ Play" is "Play"). */
internal fun spoken(text: String) = text.trimStart { !it.isLetterOrDigit() }
