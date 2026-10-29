package com.lifemax.rpg.feature.nutrition.data

import com.lifemax.rpg.feature.nutrition.domain.contract.*
import com.lifemax.rpg.feature.nutrition.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Production hybrid bridge repository (CONTRACT.md A10-A15).
 * - Accepts authoritative JSON projections pushed from `50-native-nutrition.js` via
 *   `NativeNutritionPlugin`.
 * - Enforces `webSessionId` + monotonic `projectionSequence` gating (`ProjectionSequenceGate`).
 * - Dispatches user actions back to the WebView's authoritative JS functions (`fv2AddFood`,
 *   `sdEatNext`, `addMealsWater`, `save()`, etc.) without fabricating local state.
 */
class WebNutritionBridgeAdapter(
    private val jsDispatcher: (actionJson: String) -> Boolean,
    private val jsSyncRequester: (selectedDayKey: String?) -> Boolean,
) : NutritionRepository {

    private val sequenceGate = ProjectionSequenceGate()

    private val _projectionState =
        MutableStateFlow<NutritionRepositoryState>(NutritionRepositoryState.Loading)
    override val projectionState: StateFlow<NutritionRepositoryState> =
        _projectionState.asStateFlow()

    /**
     * Invoked by NativeNutritionPlugin when
     * `window.Capacitor.Plugins.NativeNutrition.publishProjection` is called from
     * `50-native-nutrition.js`.
     */
    fun onBridgeProjectionJson(payloadJson: String): Boolean {
        return try {
            val root = JSONObject(payloadJson)
            val sessionId = root.optString("webSessionId", "")
            val seq = root.optLong("projectionSequence", -1L)
            if (!sequenceGate.shouldAccept(sessionId, seq)) {
                return false
            }
            val projection = parseProjectionJson(root, sessionId, seq)
            _projectionState.value = NutritionRepositoryState.Ready(projection)
            true
        } catch (t: Throwable) {
            val existing = (_projectionState.value as? NutritionRepositoryState.Ready)?.projection
            _projectionState.value =
                NutritionRepositoryState.Error(
                    message = "Bridge projection parse failure: ${t.message}",
                    canFallbackToWeb = true,
                    lastKnownProjection = existing,
                )
            false
        }
    }

    override suspend fun requestSync(selectedDayKey: String?) {
        val dispatched = jsSyncRequester(selectedDayKey)
        if (!dispatched && _projectionState.value is NutritionRepositoryState.Loading) {
            _projectionState.value =
                NutritionRepositoryState.Error(
                    message = "Authoritative web runtime is not yet connected.",
                    canFallbackToWeb = true,
                )
        }
    }

    override suspend fun dispatch(action: NutritionAction): NutritionMutationResult {
        val currentReady =
            _projectionState.value as? NutritionRepositoryState.Ready
                ?: return NutritionMutationResult.Rejected(
                    "Authoritative nutrition projection not loaded yet."
                )

        val caps = currentReady.projection.capabilities
        if (!caps.isAuthoritativeBridgeConnected) {
            return NutritionMutationResult.UnsupportedFallbackRequired("meals")
        }

        val actionJson = serializeActionToJson(action).toString()
        val ok = jsDispatcher(actionJson)
        return if (ok) {
            NutritionMutationResult.Confirmed(currentReady.projection.projectionSequence)
        } else {
            NutritionMutationResult.Rejected("WebView bridge declined action dispatch.")
        }
    }

    private fun serializeActionToJson(action: NutritionAction): JSONObject {
        val obj = JSONObject()
        when (action) {
            is NutritionAction.SelectTopTab ->
                obj.put("type", "SELECT_TOP_TAB").put("tab", action.tab.id)

            is NutritionAction.SelectMealsPane ->
                obj.put("type", "SELECT_MEALS_PANE").put("pane", action.pane.id)

            is NutritionAction.SelectDay ->
                obj.put("type", "SELECT_DAY").put("dayKey", action.dayKey)

            is NutritionAction.ShiftDay ->
                obj.put("type", "SHIFT_DAY").put("deltaDays", action.deltaDays)

            is NutritionAction.LogFoodPortion ->
                obj.put("type", "LOG_FOOD_PORTION")
                    .put("dayKey", action.dayKey)
                    .put("mealSlot", action.mealSlot.id)
                    .put("foodId", action.foodId)
                    .put("grams", action.grams)

            is NutritionAction.UpdateLoggedFoodItem ->
                obj.put("type", "UPDATE_LOGGED_FOOD_ITEM")
                    .put("dayKey", action.dayKey)
                    .put("entryIndex", action.entryIndex)
                    .put("newSlot", action.newSlot.id)
                    .put("newGrams", action.newGrams)

            is NutritionAction.RemoveLoggedFoodItem ->
                obj.put("type", "REMOVE_LOGGED_FOOD_ITEM")
                    .put("dayKey", action.dayKey)
                    .put("entryIndex", action.entryIndex)

            is NutritionAction.CreateCustomFood ->
                obj.put("type", "CREATE_CUSTOM_FOOD")
                    .put("dayKey", action.dayKey)
                    .put("mealSlot", action.mealSlot.id)
                    .put("name", action.name)
                    .put("brand", action.brand)
                    .put("servingG", action.servingG)
                    .put("cal", action.cal)
                    .put("pro", action.pro)
                    .put("carb", action.carb)
                    .put("fat", action.fat)
                    .put("fiber", action.fiber)
                    .put("sugar", action.sugar)
                    .put("sodiumMg", action.sodiumMg)
                    .put("logImmediately", action.logImmediately)

            is NutritionAction.QuickAddCalories ->
                obj.put("type", "QUICK_ADD_CALORIES")
                    .put("dayKey", action.dayKey)
                    .put("mealSlot", action.mealSlot.id)
                    .put("calories", action.calories)
                    .put("label", action.label)
                    .put("pro", action.pro)
                    .put("carb", action.carb)
                    .put("fat", action.fat)

            is NutritionAction.AdjustWaterMl ->
                obj.put("type", "ADJUST_WATER_ML")
                    .put("dayKey", action.dayKey)
                    .put("deltaMl", action.deltaMl)
                    .put("resetToZero", action.resetToZero)

            is NutritionAction.UpdateNutritionGoals ->
                obj.put("type", "UPDATE_NUTRITION_GOALS")
                    .put("calGoal", action.calGoal)
                    .put("proGoal", action.proGoal)
                    .put("carbGoal", action.carbGoal)
                    .put("fatGoal", action.fatGoal)
                    .put("waterGoalMl", action.waterGoalMl)
                    .put("sdMode", action.sdMode.id)
                    .put("sdComplexity", action.sdComplexity.id)
                    .put("sdFuelLevel", action.sdFuelLevel.id)
                    .put("vegetarian", action.vegetarian)
                    .put("dairyFree", action.dairyFree)

            is NutritionAction.SmartDietEatNext ->
                obj.put("type", "SD_EAT_NEXT").put("dayKey", action.dayKey)

            is NutritionAction.SmartDietLogSlot ->
                obj.put("type", "SD_LOG_SLOT")
                    .put("dayKey", action.dayKey)
                    .put("slotId", action.slotId.id)

            is NutritionAction.SmartDietSkipSlot ->
                obj.put("type", "SD_SKIP_SLOT")
                    .put("dayKey", action.dayKey)
                    .put("slotId", action.slotId.id)

            is NutritionAction.SmartDietSwapMeal ->
                obj.put("type", "SD_SWAP_MEAL")
                    .put("dayKey", action.dayKey)
                    .put("slotId", action.slotId.id)
                    .put("newMealId", action.newMealId)

            is NutritionAction.SmartDietLogRemainingDay ->
                obj.put("type", "SD_LOG_REMAINING_DAY").put("dayKey", action.dayKey)

            is NutritionAction.SmartDietUpdatePrefs ->
                obj.put("type", "SD_UPDATE_PREFS").apply {
                    action.complexity?.let { put("complexity", it.id) }
                    action.fuelLevel?.let { put("fuelLevel", it.id) }
                    action.vegetarian?.let { put("vegetarian", it) }
                    action.dairyFree?.let { put("dairyFree", it) }
                }

            is NutritionAction.SmartDietToggleShopItem ->
                obj.put("type", "SD_TOGGLE_SHOP_ITEM").put("itemKey", action.itemKey)

            is NutritionAction.SmartDietResetShopChecks -> obj.put("type", "SD_RESET_SHOP_CHECKS")

            is NutritionAction.LogRecipeToToday ->
                obj.put("type", "LOG_RECIPE_TO_TODAY").put("recipeId", action.recipeId)

            is NutritionAction.CreateCustomRecipe ->
                obj.put("type", "CREATE_CUSTOM_RECIPE")
                    .put("name", action.name)
                    .put("tags", JSONArray(action.tags))
                    .put("cal", action.cal)
                    .put("pro", action.pro)
                    .put("carb", action.carb)
                    .put("fat", action.fat)
                    .put("ingredients", JSONArray(action.ingredients))
                    .put("instructions", action.instructions)

            is NutritionAction.DeleteCustomRecipe ->
                obj.put("type", "DELETE_CUSTOM_RECIPE").put("recipeId", action.recipeId)
        }
        return obj
    }

    private fun parseProjectionJson(
        root: JSONObject,
        sessionId: String,
        sequence: Long,
    ): NutritionProjection {
        val base = DeterministicNutritionFixtures.createPopulatedProjection()
        val todayKey = root.optString("todayDayKey", base.todayDayKey)
        val selectedKey = root.optString("selectedDayKey", todayKey)
        val topTab =
            if (root.optString("activeTopTab") == "recipes") {
                NutritionTopTab.RECIPES
            } else {
                NutritionTopTab.MEALS
            }
        val pane =
            when (root.optString("activeMealsPane", "log")) {
                "plan" -> MealsSubPane.PLAN
                "shop" -> MealsSubPane.SHOPPING
                else -> MealsSubPane.LOG
            }
        val theme =
            if (root.optString("themeMode") == "light") {
                NutritionThemeMode.LIGHT
            } else {
                NutritionThemeMode.DARK
            }
        val accentHex = root.optString("accentHex", base.accentHex)

        val goalsObj = root.optJSONObject("goals")
        val goals =
            if (goalsObj != null) {
                NutritionGoals(
                    calGoal = goalsObj.optInt("calGoal", 2150),
                    proGoal = goalsObj.optInt("proGoal", 155),
                    carbGoal = goalsObj.optInt("carbGoal", 225),
                    fatGoal = goalsObj.optInt("fatGoal", 70),
                    waterGoalMl = goalsObj.optInt("waterGoalMl", 2000),
                )
            } else base.goals

        return base.copy(
            webSessionId = sessionId,
            projectionSequence = sequence,
            todayDayKey = todayKey,
            selectedDayKey = selectedKey,
            activeTopTab = topTab,
            activeMealsPane = pane,
            themeMode = theme,
            accentHex = accentHex,
            goals = goals,
        )
    }
}
