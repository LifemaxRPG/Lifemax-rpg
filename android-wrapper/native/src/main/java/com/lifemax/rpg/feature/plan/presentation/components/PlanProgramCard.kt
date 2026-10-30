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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.SurfaceCard
import com.lifemax.rpg.feature.plan.domain.PlanProjection

/** The ACTIVE PLAN card: the program, Leave plan, and the sessions done so far. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlanProgramCard(p: PlanProjection, onLeave: () -> Unit) {
    val c = LocalPlanColors.current
    SurfaceCard(padding = PaddingValues(15.dp)) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.align(Alignment.CenterVertically)) {
                Text(
                    "ACTIVE PLAN",
                    color = c.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.32.sp,
                    lineHeight = 16.5.sp,
                )
                Text(
                    p.program,
                    Modifier.padding(top = 4.dp),
                    color = c.textDim,
                    fontSize = 18.sp,
                    fontWeight = FontWeight(650),
                    letterSpacing = (-0.18).sp,
                    lineHeight = 27.sp,
                )
            }
            Text(
                "Leave plan",
                Modifier.align(Alignment.CenterVertically)
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.secondaryButton)
                    .border(1.dp, c.line, RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClick = onLeave)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                color = c.text,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 18.sp,
            )
        }
        val done = p.sessionsDone.toFloat() / p.sessionsTotal.coerceAtLeast(1)
        Box(
            Modifier.padding(top = 14.dp, bottom = 6.dp)
                .fillMaxWidth()
                .height(7.dp)
                .clip(CircleShape)
                .background(c.track)
                .border(1.dp, c.lineSoft, CircleShape)
                .padding(1.dp)
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(done, 0f..1f) }
        ) {
            Box(
                Modifier.fillMaxWidth(done).fillMaxHeight().clip(CircleShape).background(c.progress)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(p.status, color = c.muted2, fontSize = 11.sp, lineHeight = 16.5.sp)
            Text(
                p.summary,
                Modifier.weight(1f),
                color = c.muted2,
                fontSize = 11.sp,
                lineHeight = 16.5.sp,
                textAlign = TextAlign.End,
            )
        }
    }
}
