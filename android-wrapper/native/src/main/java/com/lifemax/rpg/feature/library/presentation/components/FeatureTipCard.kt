package com.lifemax.rpg.feature.library.presentation.components

import androidx.compose.runtime.Composable
import com.lifemax.rpg.core.designsystem.components.PageTipCard
import com.lifemax.rpg.feature.library.domain.FeatureTip

/** The page's feature tip (.feature-tip), core's, which the native Journal draws too. */
@Composable
internal fun FeatureTipCard(tip: FeatureTip, onDismiss: () -> Unit) =
    PageTipCard(tip.icon, tip.title, tip.body, tip.dismiss, onDismiss)
