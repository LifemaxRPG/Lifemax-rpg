package com.lifemax.rpg.core.designsystem.form

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.core.designsystem.components.toggleTarget

/**
 * A tick box with its words (.ld-check): a 20 dp box, ticked in the page's green (its accent-color,
 * --task), then the words in the second ink, 10 dp apart. The whole row is the switch, so TalkBack
 * reads the words with its state. [error] is the page's words when it refused the field (a check it
 * requires).
 */
@Composable
fun CheckField(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    error: String? = null,
) {
    val look = LocalFormKitLook.current
    Column(modifier.alpha(if (enabled) 1f else look.disabledAlpha)) {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleTarget(checked, enabled, onToggle = onChange)
                .semantics { if (error != null) error(error) },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TickBox(checked, error != null)
            Text(label, style = look.text, color = look.ink2, fontWeight = FontWeight(550))
        }
    }
}

@Composable
private fun TickBox(checked: Boolean, refused: Boolean) {
    val look = LocalFormKitLook.current
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier.size(20.dp)
            .background(if (checked) look.on else look.well, shape)
            .then(
                if (checked && !refused) Modifier
                else Modifier.border(1.5.dp, if (refused) look.error else look.ink3, shape)
            )
    ) {
        if (checked)
            Canvas(Modifier.size(20.dp)) {
                val tick =
                    Path().apply {
                        moveTo(size.width * .24f, size.height * .52f)
                        lineTo(size.width * .42f, size.height * .70f)
                        lineTo(size.width * .76f, size.height * .32f)
                    }
                drawPath(
                    tick,
                    look.onInk,
                    style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
    }
}
