package com.lifemax.rpg.feature.overview.presentation.cards

import androidx.compose.runtime.Composable
import com.lifemax.rpg.feature.overview.domain.OverviewCards
import com.lifemax.rpg.feature.overview.presentation.OverviewActions

/**
 * The tiles drawn outside Cards A: Today's plan (Life Director's DirectorGlance), App shortcuts,
 * Activity today, Attributes, This week and the cat. // align at merge: ov-native-b's cards and
 * ov-glance's DirectorGlance are called here, each from its model in [cards].tiles; until then
 * these tiles draw nothing.
 */
@Composable
internal fun PendingTile(id: String, cards: OverviewCards, actions: OverviewActions, weekDay: Int) {
    // nothing yet: see above
}
