package com.lifemax.rpg.feature.nutrition.domain.model

/**
 * Authoritative domain models for the LifeMax RPG Nutrition & Recipes feature. Mirrors the exact
 * state schema in:
 * - lifemax_modular_v73/src/runtime/segments/50-food.js
 * - lifemax_modular_v73/src/runtime/segments/50-smartdiet.js
 * - lifemax_modular_v73/src/runtime/segments/30-food.js
 */
enum class NutritionTopTab(val id: String, val label: String) {
    MEALS("meals", "Meals"),
    RECIPES("recipes", "Recipes"),
}

enum class MealsSubPane(val id: String, val label: String) {
    PLAN("plan", "Plan"),
    LOG("log", "Log"),
    SHOPPING("shop", "Shopping"),
}

enum class MealSlotType(
    val id: String,
    val label: String,
    val timeHint: String,
    val colorHex: String,
) {
    BREAKFAST("breakfast", "Breakfast", "Morning", "#F2B84A"),
    LUNCH("lunch", "Lunch", "Midday", "#5FD08A"),
    DINNER("dinner", "Dinner", "Evening", "#6AA4F0"),
    SNACKS("snacks", "Snacks", "Anytime", "#EF7A85");

    companion object {
        fun fromId(id: String): MealSlotType =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: SNACKS
    }
}

enum class NutritionThemeMode {
    DARK,
    LIGHT,
}

enum class SmartDietMode(val id: String, val label: String) {
    ASSISTED("assisted", "Assisted"),
    AUTO("auto", "Auto"),
}

enum class SmartDietComplexity(val id: String, val label: String, val subLabel: String) {
    SIMPLE("simple", "Standard Diet", "Everyday food"),
    NORMAL("normal", "Simple Recipes", "More meal variety"),
}

enum class SmartDietFuelLevel(
    val id: String,
    val label: String,
    val calDelta: Int,
    val carbShare: Double,
    val fatShare: Double,
) {
    AUTO("auto", "Auto", 0, 0.42, 0.28),
    REST("rest", "Rest", -200, 0.35, 0.32),
    LIGHT("light", "Light", 0, 0.42, 0.28),
    MODERATE("moderate", "Moderate", 220, 0.45, 0.27),
    HARD("hard", "Hard", 420, 0.50, 0.25);

    companion object {
        fun fromId(id: String): SmartDietFuelLevel =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: AUTO
    }
}

enum class WeekDayRingStatus {
    UNDER,
    OK, // 0.90 .. 1.08 of calGoal
    OVER, // > 1.08 of calGoal
}

data class NutritionGoals(
    val calGoal: Int = 2150,
    val proGoal: Int = 155,
    val carbGoal: Int = 225,
    val fatGoal: Int = 70,
    val waterGoalMl: Int = 2000,
)

data class FoodCatalogItem(
    val id: String,
    val name: String,
    val brand: String,
    val servingG: Double,
    val servingLabel: String,
    val cal: Int,
    val pro: Double,
    val carb: Double,
    val fat: Double,
    val fiber: Double = 0.0,
    val sugar: Double = 0.0,
    val sodiumMg: Int = 0,
    val isCustom: Boolean = false,
)

data class LoggedFoodEntry(
    val entryIndex: Int,
    val id: String,
    val foodId: String,
    val name: String,
    val brand: String,
    val mealSlot: MealSlotType,
    val grams: Double,
    val amountText: String,
    val cal: Int,
    val pro: Double,
    val carb: Double,
    val fat: Double,
    val fiber: Double = 0.0,
    val sugar: Double = 0.0,
    val sodiumMg: Int = 0,
    val loggedAtEpochMs: Long = 0L,
)

data class DayMacroTotals(
    val cal: Int = 0,
    val pro: Double = 0.0,
    val carb: Double = 0.0,
    val fat: Double = 0.0,
    val fiber: Double = 0.0,
    val sugar: Double = 0.0,
    val sodiumMg: Int = 0,
    val entryCount: Int = 0,
)

data class DayNutritionSummary(
    val dayKey: String,
    val entries: List<LoggedFoodEntry>,
    val totals: DayMacroTotals,
    val waterMl: Int,
    val xpAwardedToday: Boolean,
) {
    fun entriesForSlot(slot: MealSlotType): List<LoggedFoodEntry> =
        entries.filter { it.mealSlot == slot }

    fun slotCalories(slot: MealSlotType): Int = entriesForSlot(slot).sumOf { it.cal }
}

data class WeekDayStatus(
    val dayKey: String,
    val shortDayName: String, // M, T, W, T, F, S, S
    val dayOfMonth: Int,
    val totalCal: Int,
    val calGoal: Int,
    val isToday: Boolean,
    val isSelected: Boolean,
    val ringPct: Int,
    val ringStatus: WeekDayRingStatus,
)

data class ClassFuelSlotSuggestion(
    val slotTitle: String,
    val targetKcal: Int,
    val mealOptions: List<String>,
)

data class ClassFuelPlanProjection(
    val classKey: String,
    val className: String,
    val planTitle: String,
    val macroSplitText: String,
    val targetKcal: Int,
    val targetProG: Int,
    val targetCarbG: Int,
    val targetFatG: Int,
    val coachingTip: String,
    val slotSuggestions: List<ClassFuelSlotSuggestion>,
)

data class SmartDietIngredientLine(
    val ingredientKey: String,
    val name: String,
    val grams: Int,
    val displayAmount: String,
    val isPantry: Boolean = false,
)

data class SmartDietPlannedMeal(
    val slot: MealSlotType,
    val mealId: String,
    val title: String,
    val prepMinutes: Int,
    val prepNote: String?,
    val scaleFactor: Double,
    val cal: Int,
    val pro: Int,
    val carb: Int,
    val fat: Int,
    val fiber: Int,
    val ingredients: List<SmartDietIngredientLine>,
    val steps: List<String>,
    val isLogged: Boolean,
    val isSkipped: Boolean,
    val loggedActualCal: Int = 0,
)

data class SmartDietSwapAlternative(
    val mealId: String,
    val slot: MealSlotType,
    val title: String,
    val prepMinutes: Int,
    val cal: Int,
    val pro: Int,
    val carb: Int,
    val fat: Int,
    val calDeltaFromTarget: Int,
)

data class SmartDietDayProjection(
    val dayKey: String,
    val mode: SmartDietMode,
    val complexity: SmartDietComplexity,
    val fuelLevel: SmartDietFuelLevel,
    val resolvedFuelReason: String,
    val vegetarian: Boolean,
    val dairyFree: Boolean,
    val targetCal: Int,
    val targetPro: Int,
    val targetCarb: Int,
    val targetFat: Int,
    val plannedMeals: List<SmartDietPlannedMeal>,
    val upNextSlot: MealSlotType?,
    val coachHeadline: String,
    val coachDetail: String,
    val tomorrowPrepBanner: String?,
    val swapCandidatesBySlot: Map<MealSlotType, List<SmartDietSwapAlternative>>,
)

data class ShoppingListItem(
    val itemKey: String,
    val name: String,
    val categoryAisle: String,
    val totalGrams: Int,
    val packCount: Int,
    val unitPackLabel: String, // e.g. "2 × 500g pack" or "650g"
    val usedInMealCount: Int,
    val buyTwicePerishableWarning: Boolean, // "Buy twice, goes off"
    val isPantryHomeItem: Boolean,
    val isChecked: Boolean,
)

data class ShoppingAisleGroup(val aisleName: String, val items: List<ShoppingListItem>)

data class SmartDietShoppingProjection(
    val startDayKey: String,
    val horizonDays: Int = 7,
    val aisleGroups: List<ShoppingAisleGroup>,
    val pantryHomeItems: List<ShoppingListItem>,
    val totalActiveItems: Int,
    val checkedItemCount: Int,
)

data class RecipeItem(
    val id: String,
    val name: String,
    val tags: List<String>,
    val cal: Int,
    val pro: Int,
    val carb: Int,
    val fat: Int,
    val ingredients: List<String>,
    val instructions: String,
    val isCustom: Boolean,
)

data class RecipesProjection(
    val recipes: List<RecipeItem>,
    val availableTags: List<String> =
        listOf("All", "High Protein", "Quick", "Low Carb", "Vegetarian", "Budget"),
)

data class NutritionCapabilities(
    val canLogFood: Boolean = true,
    val canEditLoggedFood: Boolean = true,
    val canDeleteLoggedFood: Boolean = true,
    val canCreateCustomFood: Boolean = true,
    val canQuickAddCalories: Boolean = true,
    val canUpdateGoals: Boolean = true,
    val canLogWater: Boolean = true,
    val canUseSmartDietPlan: Boolean = true,
    val canSwapSmartDietMeal: Boolean = true,
    val canCheckShoppingItems: Boolean = true,
    val canLogRecipes: Boolean = true,
    val canCreateCustomRecipe: Boolean = true,
    val isAuthoritativeBridgeConnected: Boolean = true,
)

data class NutritionProjection(
    val webSessionId: String,
    val projectionSequence: Long,
    val todayDayKey: String,
    val selectedDayKey: String,
    val activeTopTab: NutritionTopTab,
    val activeMealsPane: MealsSubPane,
    val themeMode: NutritionThemeMode,
    val accentHex: String,
    val goals: NutritionGoals,
    val daySummary: DayNutritionSummary,
    val weekDays: List<WeekDayStatus>,
    val recentFoods: List<FoodCatalogItem>,
    val foodCatalog: List<FoodCatalogItem>,
    val classFuelPlan: ClassFuelPlanProjection,
    val smartDietPlan: SmartDietDayProjection,
    val smartDietShop: SmartDietShoppingProjection,
    val recipesState: RecipesProjection,
    val capabilities: NutritionCapabilities = NutritionCapabilities(),
)
