package com.lifemax.rpg.feature.shopping.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.gesture.noTabSwipe
import com.lifemax.rpg.feature.shopping.domain.PreferredProduct
import com.lifemax.rpg.feature.shopping.domain.WalmartProductMatcher

/** A text box in the Food tab's look; [name] is what TalkBack calls it. */
@Composable
internal fun ShopField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    name: String,
    colors: ShopColors,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    val shape = RoundedCornerShape(14.dp)
    val style = TextStyle(color = colors.fg, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier =
            Modifier.fillMaxWidth()
                .noTabSwipe()
                .clip(shape)
                .background(colors.sunken)
                .border(1.dp, colors.border, shape)
                .semantics { contentDescription = name },
        decorationBox = { inner ->
            Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 14.dp), Alignment.CenterStart) {
                if (value.isEmpty()) Text(hint, color = colors.subtle, style = style)
                inner()
            }
        },
    )
}

/**
 * Paste a Walmart product link (or item number) for one ingredient, with an optional brand or size
 * note. A link that is not a Walmart product is refused with the reason and nothing is saved.
 * [current] fills the fields when changing a product that is already chosen.
 */
@Composable
internal fun ShopProductForm(
    ingredient: String,
    current: PreferredProduct?,
    colors: ShopColors,
    onSave: (pasted: String, label: String, note: String) -> String?,
    onCancel: () -> Unit,
) {
    var pasted by remember { mutableStateOf(current?.productId.orEmpty()) }
    var label by remember { mutableStateOf(current?.label.orEmpty()) }
    var note by remember { mutableStateOf(current?.note.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ShopField(
            pasted,
            {
                pasted = it
                error = null
            },
            "Paste a walmart.com/ip/… link or item number",
            "Walmart product link for $ingredient",
            colors,
            KeyboardType.Uri,
        )
        ShopField(
            label,
            { label = it.take(WalmartProductMatcher.MAX_LABEL) },
            "Brand or product name (optional)",
            "Preferred brand for $ingredient",
            colors,
        )
        ShopField(
            note,
            { note = it.take(WalmartProductMatcher.MAX_NOTE) },
            "Size note, e.g. 32 oz (optional)",
            "Preferred size for $ingredient",
            colors,
        )
        error?.let { Text(it, color = colors.bad, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ShopButton("Save product", colors, Modifier.weight(1f), primary = true) {
                error = onSave(pasted, label, note)
            }
            ShopButton("Cancel", colors, Modifier.weight(1f), onClick = onCancel)
        }
    }
}
