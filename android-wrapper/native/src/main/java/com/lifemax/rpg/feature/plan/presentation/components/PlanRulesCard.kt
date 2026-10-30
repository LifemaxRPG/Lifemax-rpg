package com.lifemax.rpg.feature.plan.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.SurfaceCard
import com.lifemax.rpg.feature.plan.domain.PlanRule

/** The program's General rules, folded like the page's details element; [initiallyOpen] or not. */
@Composable
internal fun PlanRulesCard(
    rules: List<PlanRule>,
    progression: List<PlanRule>,
    initiallyOpen: Boolean,
) {
    val c = LocalPlanColors.current
    var open by rememberSaveable { mutableStateOf(initiallyOpen) }
    SurfaceCard(padding = PaddingValues(15.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .clickable(role = Role.Button) { open = !open }
                .semantics {
                    heading()
                    stateDescription = if (open) "Expanded" else "Collapsed"
                },
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(8.dp).rotate(if (open) 90f else 0f).drawWithCache {
                    val marker =
                        Path().apply {
                            lineTo(size.width, size.height / 2)
                            lineTo(0f, size.height)
                            close()
                        }
                    onDrawBehind { drawPath(marker, c.text) }
                }
            )
            Text(
                "General rules",
                color = c.text,
                fontSize = 14.sp,
                fontWeight = FontWeight(650),
                lineHeight = 21.sp,
            )
        }
        if (open) {
            Column(Modifier.padding(vertical = 13.dp)) {
                rules.forEach { rule ->
                    Row {
                        Text(
                            "•",
                            Modifier.width(40.dp).padding(end = 8.dp),
                            color = c.muted,
                            fontSize = 13.sp,
                            lineHeight = 22.1.sp,
                            textAlign = TextAlign.End,
                        )
                        Text(
                            rule.annotated(),
                            color = c.muted,
                            fontSize = 13.sp,
                            lineHeight = 22.1.sp,
                        )
                    }
                }
            }
            if (progression.isNotEmpty())
                Column(
                    Modifier.padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Weekly progression:",
                        color = c.muted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                    progression.forEach {
                        Text(it.annotated(), color = c.muted, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
        }
    }
}

/** A rule with its lead in bold, as the page writes it. */
private fun PlanRule.annotated() = buildAnnotatedString {
    if (lead.isNotEmpty()) {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(lead) }
        append(" ")
    }
    append(text)
}
