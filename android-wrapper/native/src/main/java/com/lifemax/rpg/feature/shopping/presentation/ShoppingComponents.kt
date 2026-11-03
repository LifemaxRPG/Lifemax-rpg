package com.lifemax.rpg.feature.shopping.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.tapTarget
import com.lifemax.rpg.feature.food.presentation.BlueAccent
import com.lifemax.rpg.feature.food.presentation.CardBottom
import com.lifemax.rpg.feature.food.presentation.CardTop
import com.lifemax.rpg.feature.food.presentation.LunchGreen
import com.lifemax.rpg.feature.food.presentation.SunkenTile
import com.lifemax.rpg.feature.food.presentation.TextMuted
import com.lifemax.rpg.feature.food.presentation.TextPrimary
import com.lifemax.rpg.feature.food.presentation.TextSubtle

/** The shopping pane's colours for the current theme, taken from the Food tab's palette. */
internal class ShopColors(val light: Boolean) {
    val fg = if (light) Color(0xFF0F172A) else TextPrimary
    val muted = if (light) Color(0xFF475569) else TextMuted
    val subtle = if (light) Color(0xFF5B6678) else TextSubtle
    val cardTop = if (light) Color.White else CardTop
    val cardBottom = if (light) Color(0xFFF7F9FC) else CardBottom
    val sunken = if (light) Color(0xFFF3F6FA) else SunkenTile
    val border = if (light) Color(0xFFD2D9E7) else Color(0x14FFFFFF)
    val accent = if (light) Color(0xFF3452C9) else BlueAccent
    val onAccent = if (light) Color.White else Color(0xFF0B1020)
    val good = if (light) Color(0xFF15803D) else LunchGreen
    val warn = if (light) Color(0xFF9A5B00) else Color(0xFFFFC15E)
    val warnFill = if (light) Color(0xFFFFF4DC) else Color(0x26FFC15E)
    val bad = if (light) Color(0xFFB42318) else Color(0xFFFF7A8A)
}

/** A rounded panel in the Food tab's card style; [tint] draws an attention border. */
@Composable
internal fun ShopCard(
    colors: ShopColors,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(
                    if (tint != null)
                        Brush.verticalGradient(listOf(colors.warnFill, colors.warnFill))
                    else Brush.verticalGradient(listOf(colors.cardTop, colors.cardBottom))
                )
                .border(if (tint != null) 2.dp else 1.dp, tint ?: colors.border, shape)
                .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** A button: [primary] is the filled call to action; a disabled one is dimmed and says so. */
@Composable
internal fun ShopButton(
    text: String,
    colors: ShopColors,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    description: String? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val label = description ?: text
    Box(
        modifier =
            modifier
                .heightIn(min = 48.dp)
                .alpha(if (enabled) 1f else 0.45f)
                .clip(shape)
                .background(if (primary) colors.accent else colors.sunken)
                .border(1.dp, if (primary) colors.accent else colors.border, shape)
                .then(if (enabled) Modifier.tapTarget(Role.Button, null, onClick) else Modifier)
                .semantics {
                    contentDescription = label
                    if (!enabled) disabled()
                }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (primary) colors.onAccent else colors.fg,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** A small label pill. */
@Composable
internal fun ShopPill(text: String, color: Color, fill: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier =
            modifier
                .clip(RoundedCornerShape(10.dp))
                .background(fill)
                .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

/** A number with minus and plus buttons; TalkBack hears the quantity and what each button does. */
@Composable
internal fun ShopStepper(
    value: Int,
    colors: ShopColors,
    subject: String,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ShopButton(
            "−",
            colors,
            Modifier.widthIn(min = 48.dp),
            enabled = value > min,
            description = "Fewer $subject",
        ) {
            onChange(value - 1)
        }
        Text(
            text = value.toString(),
            color = colors.fg,
            fontSize = 17.sp,
            fontWeight = FontWeight.Black,
            modifier =
                Modifier.widthIn(min = 32.dp).padding(horizontal = 4.dp).semantics {
                    contentDescription = "$subject: $value"
                },
        )
        ShopButton(
            "+",
            colors,
            Modifier.widthIn(min = 48.dp),
            enabled = value < max,
            description = "More $subject",
        ) {
            onChange(value + 1)
        }
    }
}

@Composable
internal fun ShopTitle(text: String, colors: ShopColors) {
    Text(text = text, color = colors.fg, fontSize = 17.sp, fontWeight = FontWeight.Black)
}

@Composable
internal fun ShopBody(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(text = text, color = color, fontSize = 13.sp, modifier = modifier.fillMaxWidth())
}
