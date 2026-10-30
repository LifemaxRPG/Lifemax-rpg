package com.lifemax.rpg.feature.log.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LifeMaxTheme
import com.lifemax.rpg.core.designsystem.components.tokenColor
import com.lifemax.rpg.feature.log.domain.LogDraft
import com.lifemax.rpg.feature.log.domain.LogProjection
import com.lifemax.rpg.feature.log.presentation.components.LogAttributeCard
import com.lifemax.rpg.feature.log.presentation.components.LogDetailsCard
import com.lifemax.rpg.feature.log.presentation.components.LogDurationCard
import com.lifemax.rpg.feature.log.presentation.components.LogEffortCard
import com.lifemax.rpg.feature.log.presentation.components.LogPresetGrid
import com.lifemax.rpg.feature.log.presentation.components.LogXpHero

@Composable
fun LogRoute(vm: LogViewModel, topInsetDp: Float, bottomInsetDp: Float) {
    val projection by vm.projection.collectAsState()
    val draft by vm.draft.collectAsState()
    val p = projection ?: return
    LaunchedEffect(p.session, p.draftRevision) { vm.adopt(p) }
    LogScreen(p, draft ?: p.draft, LogActions(vm), topInsetDp, bottomInsetDp)
}

/** Everything the screen can ask for, so the screen itself holds no state. */
class LogActions(
    val choose: (String) -> Unit = {},
    val duration: (Int) -> Unit = {},
    val quality: (String) -> Unit = {},
    val customName: (String) -> Unit = {},
    val skill: (String) -> Unit = {},
    val primary: (String) -> Unit = {},
    val secondary: (String) -> Unit = {},
    val notes: (String) -> Unit = {},
    val submit: () -> Unit = {},
) {
    constructor(
        vm: LogViewModel
    ) : this(
        vm::choose,
        vm::setDuration,
        vm::setQuality,
        vm::setCustomName,
        vm::setSkill,
        vm::setPrimary,
        vm::setSecondary,
        vm::setNotes,
        vm::submit,
    )
}

@Composable
fun LogScreen(
    p: LogProjection,
    draft: LogDraft,
    actions: LogActions,
    topInsetDp: Float = 64f,
    bottomInsetDp: Float = 72f,
) {
    LifeMaxTheme(p.tokens, p.light) {
        Box(Modifier.fillMaxSize().background(tokenColor(p.tokens, "bg")).imePadding()) {
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = topInsetDp.dp + 12.dp, bottom = bottomInsetDp.dp + 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column {
                    Text(
                        "TURN ACTION INTO PROGRESS",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp,
                    )
                    Text(
                        "Log an activity",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                LogXpHero(p, p.preview)
                LogPresetGrid(p, draft.type, actions.choose)
                LogDurationCard(draft.duration, actions.duration)
                LogEffortCard(p.qualities, draft.quality, actions.quality)
                LogAttributeCard(
                    p,
                    draft.primary,
                    draft.secondary,
                    actions.primary,
                    actions.secondary,
                )
                LogDetailsCard(draft, actions.customName, actions.skill, actions.notes)
                Spacer(Modifier.height(4.dp))
            }
            SaveButton(
                p,
                enabled = p.preview.enough,
                onClick = actions.submit,
                modifier =
                    Modifier.align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = bottomInsetDp.dp + 12.dp),
            )
        }
    }
}

@Composable
private fun SaveButton(
    p: LogProjection,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(
                if (enabled) Brush.horizontalGradient(listOf(accent.copy(alpha = .85f), accent))
                else
                    Brush.horizontalGradient(
                        listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface)
                    )
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (enabled) "✦  Award ${p.preview.total} XP" else "Log at least 5 minutes",
            color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
