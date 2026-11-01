package com.lifemax.rpg.feature.plan.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.core.designsystem.components.PageToast
import com.lifemax.rpg.core.designsystem.components.playToast
import com.lifemax.rpg.core.designsystem.feedback.XpCelebration
import com.lifemax.rpg.core.designsystem.feedback.rememberXpCelebration
import com.lifemax.rpg.feature.plan.domain.PagePoint
import com.lifemax.rpg.feature.plan.presentation.PlanFeedback
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * The page's answer to a tick, shown where the page would show it: the message as its toast above
 * the bottom bar, and its awards as the shared [XpCelebration] from the tapped box ([origin]) to
 * the top bar's logo ([target]), with fewer particles under [reduced] motion. [onShown] is called
 * once both are over. [startAt] is how many seconds of the celebration have already played (0 on
 * the phone; screenshot tests freeze a moment with it).
 */
@Composable
internal fun PlanFeedbackLayer(
    feedback: PlanFeedback,
    origin: Offset,
    target: PagePoint,
    bottomInsetDp: Float,
    reduced: Boolean,
    onShown: (Long) -> Unit,
    startAt: Float = 0f,
) {
    val celebration = rememberXpCelebration(feedback.id, feedback.awards, reduced, startAt)
    val toast = remember(feedback.id) { Animatable(0f) }
    LaunchedEffect(feedback.id) {
        coroutineScope {
            if (celebration != null) launch { celebration.play() }
            if (feedback.message.isNotEmpty()) launch { toast.playToast() }
        }
        onShown(feedback.id)
    }
    Box(Modifier.fillMaxSize()) {
        if (celebration != null)
            XpCelebration(celebration, origin, DpOffset(target.x.dp, target.y.dp))
        if (feedback.message.isNotEmpty()) {
            val c = LocalPlanColors.current
            PageToast(
                feedback.message,
                c.tokens,
                c.light,
                Modifier.align(Alignment.BottomCenter)
                    .padding(start = 12.dp, end = 12.dp, bottom = bottomInsetDp.dp + 12.dp),
                shown = { toast.value },
            )
        }
    }
}
