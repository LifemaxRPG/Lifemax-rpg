package com.lifemax.rpg.feature.food.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import org.json.JSONArray
import org.json.JSONObject

/** Ratios of protein, carbs and fat eaten against their goals, each 0..1. */
internal fun macroRatios(data: JSONObject): Triple<Float, Float, Float> {
    val goals = data.optJSONObject("goals") ?: JSONObject()
    val totals = data.optJSONObject("totals") ?: JSONObject()

    fun ratio(key: String): Float {
        val goal = goals.optDouble(key, 0.0).toFloat().coerceAtLeast(0f)
        return if (goal > 0f) (totals.optDouble(key, 0.0).toFloat() / goal).coerceIn(0f, 1f) else 0f
    }
    return Triple(ratio("p"), ratio("c"), ratio("f"))
}

/**
 * Everything the Food panes share for one composition: the palette for the current theme, the
 * projection from the page split into its parts, the screen's own UI state and the ring animation
 * state. A pane is a function of this and reads it as if it were local.
 */
internal class FoodContext(
    val data: JSONObject,
    val topInsetDp: Float,
    val bottomInsetDp: Float,
    val dispatch: (command: String, payload: Map<String, Any>) -> Unit,
    val scope: CoroutineScope,
    paneState: MutableState<String>,
    showUpNextIngredientsState: MutableState<Boolean>,
    showNutritionDetailsState: MutableState<Boolean>,
    selectedRingMacroState: MutableState<String?>,
    flightActiveState: MutableState<Boolean>,
    prevRatiosState: MutableState<Triple<Float, Float, Float>>,
    targetRatiosState: MutableState<Triple<Float, Float, Float>>,
    val flightProgress: Animatable<Float, AnimationVector1D>,
) {
    var activePane by paneState
    var showUpNextIngredients by showUpNextIngredientsState
    var showNutritionDetails by showNutritionDetailsState
    var selectedRingMacro by selectedRingMacroState
    var flightActive by flightActiveState
    var prevRatios by prevRatiosState
    var targetRatios by targetRatiosState

    val light = data.optBoolean("light")
    val bg = if (light) Color(0xFFEEF3FA) else BgObsidian
    val fg = if (light) Color(0xFF0F172A) else TextPrimary
    val muted = if (light) Color(0xFF475569) else TextMuted
    val subtle = if (light) Color(0xFF64748B) else TextSubtle
    val cardTop = if (light) Color.White else CardTop
    val cardBottom = if (light) Color(0xFFF7F9FC) else CardBottom
    val sunken = if (light) Color(0xFFF3F6FA) else SunkenTile
    val selectedPanel = if (light) Color(0xFFE8EDF9) else PillSelected
    val panelBorder = if (light) Color(0xFFD2D9E7) else BorderSubtle
    val trackColor = if (light) Color(0xFFE4E9F2) else Color(0xFF171B26)

    val goals = data.optJSONObject("goals") ?: JSONObject()
    val totals = data.optJSONObject("totals") ?: JSONObject()
    val entries = data.optJSONArray("entries") ?: JSONArray()
    val week = data.optJSONArray("week") ?: JSONArray()
    val recents = data.optJSONArray("recents") ?: JSONArray()
    val copyYesterday = data.optJSONObject("copyYesterday") ?: JSONObject()
    val planObj = data.optJSONObject("plan")
    val shopObj = data.optJSONObject("shop")

    val calLogged = totals.optDouble("kcal", 0.0).roundToInt()
    val calGoal = goals.optDouble("cal", 0.0).roundToInt().coerceAtLeast(0)
    val proLogged = totals.optDouble("p", 0.0).toFloat()
    val proGoal = goals.optDouble("p", 0.0).toFloat().coerceAtLeast(0f)
    val carbLogged = totals.optDouble("c", 0.0).toFloat()
    val carbGoal = goals.optDouble("c", 0.0).toFloat().coerceAtLeast(0f)
    val fatLogged = totals.optDouble("f", 0.0).toFloat()
    val fatGoal = goals.optDouble("f", 0.0).toFloat().coerceAtLeast(0f)
}

@Composable
internal fun rememberFoodContext(
    data: JSONObject,
    topInsetDp: Float,
    bottomInsetDp: Float,
    dispatch: (command: String, payload: Map<String, Any>) -> Unit,
): FoodContext {
    val pane =
        remember(data.optString("pane")) {
            mutableStateOf(data.optString("pane", "plan").ifBlank { "plan" })
        }
    val showUpNextIngredients = remember { mutableStateOf(false) }
    val showNutritionDetails = remember { mutableStateOf(false) }
    val selectedRingMacro = remember { mutableStateOf<String?>(null) }
    val flightActive = remember { mutableStateOf(false) }
    val prevRatios = remember { mutableStateOf(macroRatios(data)) }
    val targetRatios = remember { mutableStateOf(macroRatios(data)) }
    val flightProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    return FoodContext(
        data,
        topInsetDp,
        bottomInsetDp,
        dispatch,
        scope,
        pane,
        showUpNextIngredients,
        showNutritionDetails,
        selectedRingMacro,
        flightActive,
        prevRatios,
        targetRatios,
        flightProgress,
    )
}
