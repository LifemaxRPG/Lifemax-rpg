package com.lifemax.rpg.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LocalPageColors
import com.lifemax.rpg.core.designsystem.boxShadow

/*
 * The page's shared classes (.card, .tag, .btn, .eyebrow) at phone width, in PageTheme's colours.
 */

/** The page's .card: its soft border, its drop shadow and [padding]. */
@Composable
fun PageCard(
    modifier: Modifier = Modifier,
    padding: Dp = 15.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalPageColors.current
    val shadow =
        if (c.light)
            Modifier.boxShadow(CardShadowLightFar, 10.dp, CardShape, 2.dp)
                .boxShadow(CardShadowLightNear, 2.dp, CardShape, 1.dp)
        else Modifier.boxShadow(CardShadowDark, 8.dp, CardShape, 2.dp)
    SurfaceCard(modifier.then(shadow), PaddingValues(padding), c.lineSoft, content)
}

/** The page's small bordered label (.tag). */
@Composable
fun PageTag(text: String, modifier: Modifier = Modifier, weight: FontWeight? = null) {
    val c = LocalPageColors.current
    Text(
        text,
        modifier
            .clip(TagShape)
            .background(c.tagFill)
            .border(1.dp, c.line, TagShape)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        color = c.tagText,
        fontSize = 11.5.sp,
        fontWeight = weight,
        lineHeight = 17.25.sp,
    )
}

/**
 * The page's primary button (.btn): accent, with a light top edge and, in the light theme, a glow.
 * It is at least 48 dp tall, where the page's is 41. [label] is what TalkBack says: [text] without
 * its symbols.
 */
@Composable
fun PageButton(text: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalPageColors.current
    val edge = if (c.light) ButtonEdgeLight else ButtonEdgeDark
    Box(
        modifier
            .then(
                if (c.light) Modifier.boxShadow(ButtonGlowLight, 8.dp, ButtonShape, 2.dp)
                else Modifier
            )
            .clip(ButtonShape)
            .background(c.accent)
            .drawBehind { drawRect(edge, size = Size(size.width, 1.dp.toPx())) }
            .semantics { contentDescription = label }
            .tapTarget(Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = c.onAccent,
            fontSize = 14.sp,
            fontWeight = FontWeight(650),
            lineHeight = 21.sp,
            softWrap = false,
        )
    }
}

/** The page's accent caption (.eyebrow), upper case. */
@Composable
fun PageEyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier,
        color = LocalPageColors.current.accent,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.32.sp,
        lineHeight = 16.5.sp,
    )
}

/** The corners of SurfaceCard, which the shadow follows. */
private val CardShape = RoundedCornerShape(14.dp)
private val TagShape = RoundedCornerShape(7.dp)
private val ButtonShape = RoundedCornerShape(8.dp)

/**
 * Colours the page's styles fix: the cards' drop shadows (one layer in the dark theme, two in the
 * light), the button's top edge and the light theme's glow under it.
 */
private val CardShadowDark = Color(0x40000000)
private val CardShadowLightNear = Color(0x081A2744)
private val CardShadowLightFar = Color(0x0C1A2744)
private val ButtonEdgeDark = Color(0x22FFFFFF)
private val ButtonEdgeLight = Color(0x44FFFFFF)
private val ButtonGlowLight = Color(0x333B5BDB)
