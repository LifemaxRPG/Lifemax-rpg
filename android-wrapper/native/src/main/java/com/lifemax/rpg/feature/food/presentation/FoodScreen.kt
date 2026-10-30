package com.lifemax.rpg.feature.food.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/**
 * Native Food (Plan, Log, Shopping). Every displayed fact comes from the page's projection and
 * every change goes back to the page's own handlers through [dispatch]; this file only lays the
 * panes out. The panes live in the Food*Pane files and share [FoodContext].
 */
@Composable
internal fun NativeNutritionScreen(
    data: JSONObject,
    topInsetDp: Float,
    bottomInsetDp: Float,
    active: Boolean,
    dispatch: (command: String, payload: Map<String, Any>) -> Unit,
) {
    // a hidden screen stays composed, so it must not take the Back button from the tab that is
    // showing
    BackHandler(enabled = active) { dispatch("close", emptyMap()) }

    val c = rememberFoodContext(data, topInsetDp, bottomInsetDp, dispatch)
    FoodRingTorch(c)

    with(c) {
        Box(modifier = Modifier.fillMaxSize().background(bg)) {
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .padding(bottom = bottomInsetDp.dp + 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                FoodDateHeader(c)
                FoodWeekStrip(c)
                FoodPaneSwitcher(c)
                when (activePane) {
                    "plan" -> FoodPlanPane(c)
                    "log" -> FoodLogPane(c)
                    "shop" -> FoodShopPane(c)
                }
            }
            FoodSearchBar(c)
        }
    }
}

/** Switches to Log and flies the macro rings' torch whenever the calories eaten go up. */
@Composable
internal fun FoodRingTorch(c: FoodContext) {
    var lastCalLogged by remember { mutableIntStateOf(c.calLogged) }
    LaunchedEffect(c.calLogged, c.proLogged, c.carbLogged, c.fatLogged) {
        with(c) {
            val nextTriple = macroRatios(data)
            if (calLogged > lastCalLogged) {
                activePane = "log"
                targetRatios = nextTriple
                flightActive = true
                flightProgress.snapTo(0f)
                flightProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 1620, easing = LinearEasing),
                )
                flightActive = false
                prevRatios = nextTriple
            } else {
                prevRatios = nextTriple
                targetRatios = nextTriple
            }
            lastCalLogged = calLogged
        }
    }
}
