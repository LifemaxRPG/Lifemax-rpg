package com.lifemax.rpg.feature.plan.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.PageButton
import com.lifemax.rpg.core.designsystem.components.PageCard
import com.lifemax.rpg.core.designsystem.components.PageEyebrow
import com.lifemax.rpg.core.designsystem.form.DateField
import com.lifemax.rpg.feature.plan.domain.PlanOffer

/**
 * The plan the page offers while there is none (the page's AVAILABLE PLAN card): the program, what
 * each day holds, the page's note, the start date and the sign-up button. Everything but the date
 * box is the page's words, from the same data its own card is drawn from (planOfferFor).
 */
@Composable
internal fun PlanOfferCard(
    offer: PlanOffer,
    start: String,
    busy: Boolean,
    onStart: (String) -> Unit,
    onSignUp: () -> Unit,
) {
    val c = LocalPlanColors.current
    PageCard {
        PageEyebrow(offer.eyebrow)
        Text(
            offer.title,
            Modifier.padding(top = 6.dp, bottom = 4.dp).semantics { heading() },
            color = c.text,
            fontSize = 22.sp,
            fontWeight = FontWeight(650),
            lineHeight = 28.sp,
        )
        Text(
            offer.blurb,
            Modifier.padding(bottom = 12.dp),
            color = c.muted,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
        offer.rows.forEachIndexed { i, row ->
            if (i > 0) HorizontalDivider(color = c.lineSoft)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(row.whenText, color = c.text, fontSize = 13.sp, lineHeight = 19.5.sp)
                Text(
                    row.what,
                    Modifier.weight(1f),
                    color = c.muted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
        }
        val shape = RoundedCornerShape(10.dp)
        Text(
            offer.note,
            Modifier.padding(vertical = 14.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(c.noteFill)
                .border(1.dp, c.noteBorder, shape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            color = c.noteText,
            fontSize = 12.5.sp,
            lineHeight = 18.75.sp,
        )
        PlanForms { DateField(offer.startLabel, start, onStart, Modifier.fillMaxWidth()) }
        PageButton(
            offer.button,
            offer.button,
            { if (!busy) onSignUp() },
            Modifier.padding(top = 12.dp),
        )
    }
}
