package com.lifemax.rpg.feature.nutrition.data

import com.lifemax.rpg.feature.nutrition.domain.logic.NutritionCalculations
import com.lifemax.rpg.feature.nutrition.domain.model.*

/**
 * Deterministic synthetic fixtures reproducing exact food catalog items, Smart Diet meals, shopping
 * aisle items, and recipes from:
 * - lifemax_modular_v73/src/runtime/segments/50-food.js (FV2_FOODS)
 * - lifemax_modular_v73/src/runtime/segments/50-smartdiet.js (SD_INGREDIENTS, SD_MEALS)
 * - lifemax_modular_v73/src/runtime/segments/30-food.js (RECIPE_DB, CLASS_DIET_PLANS)
 */
object DeterministicNutritionFixtures {

    const val DEFAULT_TODAY_KEY = "2025-05-14" // Wednesday

    val defaultCatalog: List<FoodCatalogItem> =
        listOf(
            FoodCatalogItem(
                "egg_whole",
                "Large Whole Egg",
                "Generic · 1 egg",
                50.0,
                "1 large (50g)",
                72,
                6.3,
                0.4,
                4.8,
                0.0,
                0.2,
                71,
            ),
            FoodCatalogItem(
                "oats_dry",
                "Rolled Oats",
                "Porridge oats · dry",
                50.0,
                "1/2 cup (50g)",
                190,
                6.5,
                34.0,
                3.3,
                5.0,
                0.5,
                2,
            ),
            FoodCatalogItem(
                "greek_yogurt_0",
                "Greek Yogurt 0%",
                "Fage Total 0%",
                170.0,
                "1 pot (170g)",
                92,
                17.5,
                5.1,
                0.0,
                0.0,
                5.1,
                65,
            ),
            FoodCatalogItem(
                "chicken_breast",
                "Chicken Breast (Cooked)",
                "Grilled skinless",
                150.0,
                "1 breast (150g)",
                248,
                46.5,
                0.0,
                5.4,
                0.0,
                0.0,
                111,
            ),
            FoodCatalogItem(
                "salmon_fillet",
                "Atlantic Salmon Fillet",
                "Baked / pan-seared",
                150.0,
                "1 fillet (150g)",
                312,
                30.6,
                0.0,
                20.1,
                0.0,
                0.0,
                89,
            ),
            FoodCatalogItem(
                "white_rice_cooked",
                "Jasmine Rice (Cooked)",
                "Steamed white rice",
                180.0,
                "1 cup (180g)",
                234,
                4.9,
                50.4,
                0.5,
                0.7,
                0.1,
                4,
            ),
            FoodCatalogItem(
                "avocado",
                "Hass Avocado",
                "Fresh sliced",
                80.0,
                "1/2 medium (80g)",
                128,
                1.6,
                6.8,
                11.8,
                5.4,
                0.5,
                6,
            ),
            FoodCatalogItem(
                "whey_scoop",
                "Whey Protein Isolate",
                "Optimum Gold Standard",
                31.0,
                "1 scoop (31g)",
                120,
                24.0,
                3.0,
                1.0,
                0.0,
                1.0,
                130,
            ),
            FoodCatalogItem(
                "banana",
                "Medium Banana",
                "Fresh fruit",
                118.0,
                "1 medium (118g)",
                105,
                1.3,
                27.0,
                0.4,
                3.1,
                14.4,
                1,
            ),
            FoodCatalogItem(
                "blueberries",
                "Fresh Blueberries",
                "Berries",
                80.0,
                "1 handful (80g)",
                46,
                0.6,
                11.6,
                0.3,
                1.9,
                8.0,
                1,
            ),
            FoodCatalogItem(
                "sourdough_slice",
                "Sourdough Bread",
                "Artisan loaf · toasted",
                55.0,
                "1 thick slice (55g)",
                148,
                5.8,
                28.6,
                1.1,
                1.5,
                0.8,
                290,
            ),
            FoodCatalogItem(
                "olive_oil",
                "Extra Virgin Olive Oil",
                "Cold pressed",
                14.0,
                "1 tbsp (14g)",
                119,
                0.0,
                0.0,
                13.5,
                0.0,
                0.0,
                0,
            ),
            FoodCatalogItem(
                "almonds",
                "Roasted Almonds",
                "Unsalted",
                30.0,
                "1 handful (30g)",
                174,
                6.3,
                6.5,
                15.0,
                3.8,
                1.3,
                1,
            ),
            FoodCatalogItem(
                "sweet_potato",
                "Baked Sweet Potato",
                "Flesh only",
                180.0,
                "1 medium (180g)",
                162,
                3.6,
                37.3,
                0.3,
                5.9,
                11.7,
                65,
            ),
            FoodCatalogItem(
                "broccoli_steamed",
                "Steamed Broccoli",
                "Florets",
                120.0,
                "1 cup (120g)",
                42,
                3.4,
                8.6,
                0.5,
                4.0,
                1.7,
                49,
            ),
        )

    val defaultRecipes: List<RecipeItem> =
        listOf(
            RecipeItem(
                id = "rec_1",
                name = "Anabolic French Toast",
                tags = listOf("High Protein", "Quick", "Budget"),
                cal = 460,
                pro = 44,
                carb = 52,
                fat = 7,
                ingredients =
                    listOf(
                        "4 slices thick bread",
                        "200g liquid egg whites",
                        "100g 0% Greek yogurt",
                        "Cinnamon & zero-cal syrup",
                    ),
                instructions =
                    "Whisk egg whites and cinnamon. Soak bread 30s per side, pan-sear on medium heat until golden, top with Greek yogurt.",
                isCustom = false,
            ),
            RecipeItem(
                id = "rec_2",
                name = "Honey Garlic Chicken Rice Bowl",
                tags = listOf("High Protein", "Quick"),
                cal = 590,
                pro = 48,
                carb = 68,
                fat = 12,
                ingredients =
                    listOf(
                        "160g chicken breast",
                        "180g cooked jasmine rice",
                        "120g broccoli",
                        "1 tbsp honey + soy + garlic glaze",
                    ),
                instructions =
                    "Dice and sear chicken breast until crisp. Toss in honey-soy-garlic glaze for 90s and serve over steamed jasmine rice and broccoli.",
                isCustom = false,
            ),
            RecipeItem(
                id = "rec_3",
                name = "Salmon & Avocado Power Salad",
                tags = listOf("High Protein", "Low Carb"),
                cal = 540,
                pro = 36,
                carb = 16,
                fat = 36,
                ingredients =
                    listOf(
                        "150g salmon fillet",
                        "80g avocado",
                        "Mixed baby spinach & cucumber",
                        "Lemon & olive oil dressing",
                    ),
                instructions =
                    "Pan-sear salmon skin-side down for 4 mins, flip for 2 mins. Flake over spinach, avocado, and cucumber with lemon oil.",
                isCustom = false,
            ),
            RecipeItem(
                id = "rec_4",
                name = "Crispy Tofu Peanut Stir-Fry",
                tags = listOf("Vegetarian", "High Protein", "Quick"),
                cal = 510,
                pro = 29,
                carb = 54,
                fat = 19,
                ingredients =
                    listOf(
                        "200g firm tofu",
                        "150g rice noodles",
                        "Mixed bell peppers & pak choi",
                        "20g peanut butter + soy + lime",
                    ),
                instructions =
                    "Pan-fry cubed firm tofu until golden. Toss with wok-charred peppers, rice noodles, and warm peanut-lime sauce.",
                isCustom = false,
            ),
        )

    val defaultClassFuelPlan =
        ClassFuelPlanProjection(
            classKey = "vanguard",
            className = "Vanguard",
            planTitle = "Vanguard — Hybrid Strength & Conditioning Fuel",
            macroSplitText = "30% Protein · 42% Carbs · 28% Fat",
            targetKcal = 2150,
            targetProG = 155,
            targetCarbG = 225,
            targetFatG = 70,
            coachingTip =
                "Anchor every meal with 35–45g leucin-rich protein and time complex carbs around your primary training block.",
            slotSuggestions =
                listOf(
                    ClassFuelSlotSuggestion(
                        "Breakfast",
                        540,
                        listOf("Greek Yogurt Power Bowl + Oats", "3 Eggs + 2 Sourdough Toast"),
                    ),
                    ClassFuelSlotSuggestion(
                        "Lunch",
                        645,
                        listOf(
                            "Chicken Breast + Jasmine Rice + Broccoli",
                            "Tuna & Sweet Potato Mash",
                        ),
                    ),
                    ClassFuelSlotSuggestion(
                        "Dinner",
                        645,
                        listOf("Baked Salmon + Roasted Potatoes + Greens", "Lean Beef Chili + Rice"),
                    ),
                    ClassFuelSlotSuggestion(
                        "Snacks",
                        320,
                        listOf("Whey Isolate Shake + Banana", "Cottage Cheese + Blueberries"),
                    ),
                ),
        )

    fun createPopulatedProjection(
        themeMode: NutritionThemeMode = NutritionThemeMode.DARK,
        accentHex: String = "#00F5D4",
        activeTopTab: NutritionTopTab = NutritionTopTab.MEALS,
        activeMealsPane: MealsSubPane = MealsSubPane.LOG,
    ): NutritionProjection {
        val goals =
            NutritionGoals(
                calGoal = 2150,
                proGoal = 155,
                carbGoal = 225,
                fatGoal = 70,
                waterGoalMl = 2000,
            )
        val entries =
            listOf(
                NutritionCalculations.scaleFoodForGrams(
                    defaultCatalog[1],
                    60.0,
                    MealSlotType.BREAKFAST,
                    0,
                    1747209600000L,
                ),
                NutritionCalculations.scaleFoodForGrams(
                    defaultCatalog[2],
                    170.0,
                    MealSlotType.BREAKFAST,
                    1,
                    1747209660000L,
                ),
                NutritionCalculations.scaleFoodForGrams(
                    defaultCatalog[9],
                    80.0,
                    MealSlotType.BREAKFAST,
                    2,
                    1747209720000L,
                ),
                NutritionCalculations.scaleFoodForGrams(
                    defaultCatalog[3],
                    165.0,
                    MealSlotType.LUNCH,
                    3,
                    1747224000000L,
                ),
                NutritionCalculations.scaleFoodForGrams(
                    defaultCatalog[5],
                    200.0,
                    MealSlotType.LUNCH,
                    4,
                    1747224060000L,
                ),
                NutritionCalculations.scaleFoodForGrams(
                    defaultCatalog[6],
                    70.0,
                    MealSlotType.LUNCH,
                    5,
                    1747224120000L,
                ),
                NutritionCalculations.scaleFoodForGrams(
                    defaultCatalog[7],
                    31.0,
                    MealSlotType.SNACKS,
                    6,
                    1747234800000L,
                ),
                NutritionCalculations.scaleFoodForGrams(
                    defaultCatalog[8],
                    118.0,
                    MealSlotType.SNACKS,
                    7,
                    1747234860000L,
                ),
            )
        val totals = NutritionCalculations.computeDayTotals(entries)
        val daySummary =
            DayNutritionSummary(
                dayKey = DEFAULT_TODAY_KEY,
                entries = entries,
                totals = totals,
                waterMl = 1500,
                xpAwardedToday =
                    NutritionCalculations.isWithinDailyXpWindow(totals.cal, goals.calGoal),
            )

        val weekDays =
            listOf(
                buildWeekDay(
                    "2025-05-12",
                    "M",
                    12,
                    2090,
                    goals.calGoal,
                    isToday = false,
                    isSelected = false,
                ),
                buildWeekDay(
                    "2025-05-13",
                    "T",
                    13,
                    2140,
                    goals.calGoal,
                    isToday = false,
                    isSelected = false,
                ),
                buildWeekDay(
                    "2025-05-14",
                    "W",
                    14,
                    totals.cal,
                    goals.calGoal,
                    isToday = true,
                    isSelected = true,
                ),
                buildWeekDay(
                    "2025-05-15",
                    "T",
                    15,
                    0,
                    goals.calGoal,
                    isToday = false,
                    isSelected = false,
                ),
                buildWeekDay(
                    "2025-05-16",
                    "F",
                    16,
                    0,
                    goals.calGoal,
                    isToday = false,
                    isSelected = false,
                ),
                buildWeekDay(
                    "2025-05-17",
                    "S",
                    17,
                    0,
                    goals.calGoal,
                    isToday = false,
                    isSelected = false,
                ),
                buildWeekDay(
                    "2025-05-18",
                    "S",
                    18,
                    0,
                    goals.calGoal,
                    isToday = false,
                    isSelected = false,
                ),
            )

        return NutritionProjection(
            webSessionId = "session-fixture-v198",
            projectionSequence = 1L,
            todayDayKey = DEFAULT_TODAY_KEY,
            selectedDayKey = DEFAULT_TODAY_KEY,
            activeTopTab = activeTopTab,
            activeMealsPane = activeMealsPane,
            themeMode = themeMode,
            accentHex = accentHex,
            goals = goals,
            daySummary = daySummary,
            weekDays = weekDays,
            recentFoods = defaultCatalog.take(7),
            foodCatalog = defaultCatalog,
            classFuelPlan = defaultClassFuelPlan,
            smartDietPlan = buildSmartDietPlan(DEFAULT_TODAY_KEY, goals),
            smartDietShop = buildSmartDietShopping(DEFAULT_TODAY_KEY),
            recipesState = RecipesProjection(recipes = defaultRecipes),
            capabilities = NutritionCapabilities(),
        )
    }

    private fun buildWeekDay(
        dayKey: String,
        shortName: String,
        dayOfMonth: Int,
        totalCal: Int,
        calGoal: Int,
        isToday: Boolean,
        isSelected: Boolean,
    ): WeekDayStatus {
        val (pct, status) = NutritionCalculations.classifyWeekDayRing(totalCal, calGoal)
        return WeekDayStatus(
            dayKey = dayKey,
            shortDayName = shortName,
            dayOfMonth = dayOfMonth,
            totalCal = totalCal,
            calGoal = calGoal,
            isToday = isToday,
            isSelected = isSelected,
            ringPct = pct,
            ringStatus = status,
        )
    }

    fun buildSmartDietPlan(dayKey: String, goals: NutritionGoals): SmartDietDayProjection {
        val plannedMeals =
            listOf(
                SmartDietPlannedMeal(
                    slot = MealSlotType.BREAKFAST,
                    mealId = "sd_b_greek_oats",
                    title = "Greek Yogurt, Berry & Honey Oats Bowl",
                    prepMinutes = 5,
                    prepNote = null,
                    scaleFactor = 1.0,
                    cal = 510,
                    pro = 34,
                    carb = 64,
                    fat = 11,
                    fiber = 8,
                    ingredients =
                        listOf(
                            SmartDietIngredientLine("greek_yogurt", "0% Greek yogurt", 200, "200g"),
                            SmartDietIngredientLine("oats", "Rolled porridge oats", 60, "60g"),
                            SmartDietIngredientLine("berries", "Mixed berries", 80, "80g"),
                            SmartDietIngredientLine(
                                "honey",
                                "Runny honey",
                                15,
                                "1 tbsp (15g)",
                                isPantry = true,
                            ),
                        ),
                    steps =
                        listOf(
                            "Layer 0% Greek yogurt and rolled oats in a bowl.",
                            "Top with mixed berries and a drizzle of honey.",
                        ),
                    isLogged = true,
                    isSkipped = false,
                    loggedActualCal = 510,
                ),
                SmartDietPlannedMeal(
                    slot = MealSlotType.LUNCH,
                    mealId = "sd_l_chicken_rice",
                    title = "Chargrilled Chicken, Jasmine Rice & Broccoli",
                    prepMinutes = 12,
                    prepNote = "Cook double chicken for tomorrow's lunch wrap",
                    scaleFactor = 1.05,
                    cal = 640,
                    pro = 52,
                    carb = 72,
                    fat = 14,
                    fiber = 6,
                    ingredients =
                        listOf(
                            SmartDietIngredientLine(
                                "chicken_breast",
                                "Chicken breast fillets",
                                180,
                                "180g",
                            ),
                            SmartDietIngredientLine(
                                "jasmine_rice",
                                "Pouch microwave jasmine rice",
                                200,
                                "200g",
                            ),
                            SmartDietIngredientLine("broccoli", "Tenderstem broccoli", 120, "120g"),
                            SmartDietIngredientLine(
                                "olive_oil",
                                "Olive oil & soy seasoning",
                                10,
                                "2 tsp (10g)",
                                isPantry = true,
                            ),
                        ),
                    steps =
                        listOf(
                            "Season chicken breast with salt, pepper, and garlic; sear 5 mins per side.",
                            "Steam broccoli for 4 mins and heat jasmine rice pouch.",
                            "Slice chicken over rice and drizzle pan juices.",
                        ),
                    isLogged = false,
                    isSkipped = false,
                ),
                SmartDietPlannedMeal(
                    slot = MealSlotType.DINNER,
                    mealId = "sd_d_salmon_potato",
                    title = "Baked Honey-Mustard Salmon & Baby Potatoes",
                    prepMinutes = 18,
                    prepNote = null,
                    scaleFactor = 1.0,
                    cal = 680,
                    pro = 42,
                    carb = 58,
                    fat = 28,
                    fiber = 7,
                    ingredients =
                        listOf(
                            SmartDietIngredientLine("salmon_fillet", "Salmon fillets", 160, "160g"),
                            SmartDietIngredientLine(
                                "baby_potatoes",
                                "Baby new potatoes",
                                240,
                                "240g",
                            ),
                            SmartDietIngredientLine("spinach", "Baby spinach", 90, "90g"),
                            SmartDietIngredientLine(
                                "wholegrain_mustard",
                                "Wholegrain mustard & honey",
                                15,
                                "1 tbsp",
                                isPantry = true,
                            ),
                        ),
                    steps =
                        listOf(
                            "Halve baby potatoes and roast or air-fry at 200°C for 15 mins.",
                            "Brush salmon with mustard-honey glaze and bake alongside for the final 10 mins.",
                            "Wilt spinach in residual heat and plate.",
                        ),
                    isLogged = false,
                    isSkipped = false,
                ),
                SmartDietPlannedMeal(
                    slot = MealSlotType.SNACKS,
                    mealId = "sd_s_whey_banana",
                    title = "Whey Protein Shake & Medium Banana",
                    prepMinutes = 2,
                    prepNote = null,
                    scaleFactor = 1.0,
                    cal = 320,
                    pro = 27,
                    carb = 31,
                    fat = 10,
                    fiber = 4,
                    ingredients =
                        listOf(
                            SmartDietIngredientLine(
                                "whey_protein",
                                "Whey protein isolate",
                                32,
                                "1 scoop (32g)",
                            ),
                            SmartDietIngredientLine(
                                "banana",
                                "Medium banana",
                                118,
                                "1 banana (118g)",
                            ),
                            SmartDietIngredientLine(
                                "peanut_butter",
                                "Smooth peanut butter",
                                15,
                                "1 tbsp (15g)",
                                isPantry = true,
                            ),
                        ),
                    steps =
                        listOf(
                            "Shake whey isolate with 250ml cold water or milk.",
                            "Pair with banana and peanut butter.",
                        ),
                    isLogged = false,
                    isSkipped = false,
                ),
            )

        val swapCandidates =
            mapOf(
                MealSlotType.BREAKFAST to
                    listOf(
                        SmartDietSwapAlternative(
                            "sd_b_eggs_toast",
                            MealSlotType.BREAKFAST,
                            "3 Scrambled Eggs on Sourdough Toast",
                            6,
                            495,
                            31,
                            44,
                            21,
                            -15,
                        ),
                        SmartDietSwapAlternative(
                            "sd_b_protein_pancakes",
                            MealSlotType.BREAKFAST,
                            "High-Protein Banana Oat Pancakes",
                            10,
                            525,
                            36,
                            68,
                            10,
                            15,
                        ),
                    ),
                MealSlotType.LUNCH to
                    listOf(
                        SmartDietSwapAlternative(
                            "sd_l_turkey_wrap",
                            MealSlotType.LUNCH,
                            "Smoky Turkey, Avocado & Black Bean Wrap",
                            7,
                            625,
                            48,
                            66,
                            17,
                            -15,
                        ),
                        SmartDietSwapAlternative(
                            "sd_l_tuna_jacket",
                            MealSlotType.LUNCH,
                            "Crispy Jacket Potato with Tuna & Greek Yogurt",
                            10,
                            650,
                            50,
                            78,
                            12,
                            10,
                        ),
                    ),
                MealSlotType.DINNER to
                    listOf(
                        SmartDietSwapAlternative(
                            "sd_d_steak_sweet_potato",
                            MealSlotType.DINNER,
                            "Sirloin Steak Medallions & Sweet Potato Wedges",
                            16,
                            690,
                            49,
                            60,
                            26,
                            10,
                        ),
                        SmartDietSwapAlternative(
                            "sd_d_prawn_stirfry",
                            MealSlotType.DINNER,
                            "King Prawn & Ginger Rice Noodle Stir-Fry",
                            12,
                            660,
                            44,
                            76,
                            18,
                            -20,
                        ),
                    ),
                MealSlotType.SNACKS to
                    listOf(
                        SmartDietSwapAlternative(
                            "sd_s_cottage_berries",
                            MealSlotType.SNACKS,
                            "Cottage Cheese, Blueberries & Almonds",
                            2,
                            310,
                            26,
                            22,
                            13,
                            -10,
                        ),
                        SmartDietSwapAlternative(
                            "sd_s_protein_bar_apple",
                            MealSlotType.SNACKS,
                            "Grenade Protein Bar & Crisp Apple",
                            1,
                            330,
                            22,
                            38,
                            9,
                            10,
                        ),
                    ),
            )

        return SmartDietDayProjection(
            dayKey = dayKey,
            mode = SmartDietMode.ASSISTED,
            complexity = SmartDietComplexity.SIMPLE,
            fuelLevel = SmartDietFuelLevel.AUTO,
            resolvedFuelReason = "Moderate training day (+220 kcal carb support)",
            vegetarian = false,
            dairyFree = false,
            targetCal = goals.calGoal,
            targetPro = goals.proGoal,
            targetCarb = goals.carbGoal,
            targetFat = goals.fatGoal,
            plannedMeals = plannedMeals,
            upNextSlot = MealSlotType.LUNCH,
            coachHeadline = "Lunch is ready to log — 640 kcal & 52g protein",
            coachDetail =
                "Breakfast is locked in (510 kcal). Hitting Lunch keeps you right on pace for your 155g protein target.",
            tomorrowPrepBanner =
                "Tomorrow's breakfast uses overnight oats — soak 60g oats + yogurt tonight (2 mins).",
            swapCandidatesBySlot = swapCandidates,
        )
    }

    fun buildSmartDietShopping(startDayKey: String): SmartDietShoppingProjection {
        val aisles =
            listOf(
                ShoppingAisleGroup(
                    aisleName = "Meat, fish & eggs",
                    items =
                        listOf(
                            ShoppingListItem(
                                "chicken_breast",
                                "Chicken breast fillets",
                                "Meat, fish & eggs",
                                900,
                                2,
                                "2 × 500g pack",
                                5,
                                buyTwicePerishableWarning = true,
                                isPantryHomeItem = false,
                                isChecked = false,
                            ),
                            ShoppingListItem(
                                "salmon_fillet",
                                "Salmon fillets (2 pack)",
                                "Meat, fish & eggs",
                                480,
                                2,
                                "2 × 240g pack",
                                3,
                                buyTwicePerishableWarning = true,
                                isPantryHomeItem = false,
                                isChecked = false,
                            ),
                            ShoppingListItem(
                                "eggs_free_range",
                                "Free-range large eggs",
                                "Meat, fish & eggs",
                                600,
                                1,
                                "1 × 12-egg box",
                                4,
                                buyTwicePerishableWarning = false,
                                isPantryHomeItem = false,
                                isChecked = true,
                            ),
                        ),
                ),
                ShoppingAisleGroup(
                    aisleName = "Dairy & chiller",
                    items =
                        listOf(
                            ShoppingListItem(
                                "greek_yogurt",
                                "0% Fat Greek Yogurt",
                                "Dairy & chiller",
                                1000,
                                2,
                                "2 × 500g tub",
                                5,
                                buyTwicePerishableWarning = false,
                                isPantryHomeItem = false,
                                isChecked = false,
                            ),
                            ShoppingListItem(
                                "cottage_cheese",
                                "Longley Farm Cottage Cheese",
                                "Dairy & chiller",
                                300,
                                1,
                                "1 × 300g tub",
                                2,
                                buyTwicePerishableWarning = false,
                                isPantryHomeItem = false,
                                isChecked = false,
                            ),
                        ),
                ),
                ShoppingAisleGroup(
                    aisleName = "Fruit & veg",
                    items =
                        listOf(
                            ShoppingListItem(
                                "broccoli",
                                "Tenderstem broccoli",
                                "Fruit & veg",
                                600,
                                2,
                                "2 × 300g bag",
                                5,
                                buyTwicePerishableWarning = false,
                                isPantryHomeItem = false,
                                isChecked = false,
                            ),
                            ShoppingListItem(
                                "spinach",
                                "Baby spinach leaves",
                                "Fruit & veg",
                                270,
                                2,
                                "2 × 150g bag",
                                3,
                                buyTwicePerishableWarning = true,
                                isPantryHomeItem = false,
                                isChecked = false,
                            ),
                            ShoppingListItem(
                                "bananas",
                                "Medium bananas",
                                "Fruit & veg",
                                700,
                                1,
                                "1 × bunch of 6",
                                6,
                                buyTwicePerishableWarning = false,
                                isPantryHomeItem = false,
                                isChecked = true,
                            ),
                            ShoppingListItem(
                                "berries",
                                "Mixed blueberries & raspberries",
                                "Fruit & veg",
                                400,
                                2,
                                "2 × 200g punnet",
                                5,
                                buyTwicePerishableWarning = true,
                                isPantryHomeItem = false,
                                isChecked = false,
                            ),
                            ShoppingListItem(
                                "baby_potatoes",
                                "Baby new potatoes",
                                "Fruit & veg",
                                950,
                                1,
                                "1 × 1kg bag",
                                4,
                                buyTwicePerishableWarning = false,
                                isPantryHomeItem = false,
                                isChecked = false,
                            ),
                        ),
                ),
                ShoppingAisleGroup(
                    aisleName = "Tins, jars & packets",
                    items =
                        listOf(
                            ShoppingListItem(
                                "jasmine_rice",
                                "Microwave basmati/jasmine rice pouches",
                                "Tins, jars & packets",
                                1000,
                                4,
                                "4 × 250g pouch",
                                5,
                                buyTwicePerishableWarning = false,
                                isPantryHomeItem = false,
                                isChecked = false,
                            ),
                            ShoppingListItem(
                                "oats",
                                "Scottish rolled porridge oats",
                                "Tins, jars & packets",
                                420,
                                1,
                                "1 × 1kg bag",
                                7,
                                buyTwicePerishableWarning = false,
                                isPantryHomeItem = false,
                                isChecked = true,
                            ),
                        ),
                ),
                ShoppingAisleGroup(
                    aisleName = "Bread & bakery",
                    items =
                        listOf(
                            ShoppingListItem(
                                "sourdough",
                                "Sliced sourdough loaf",
                                "Bread & bakery",
                                440,
                                1,
                                "1 × 600g loaf",
                                4,
                                buyTwicePerishableWarning = false,
                                isPantryHomeItem = false,
                                isChecked = false,
                            )
                        ),
                ),
            )

        val pantry =
            listOf(
                ShoppingListItem(
                    "olive_oil",
                    "Extra virgin olive oil",
                    "Cupboard",
                    70,
                    1,
                    "70ml used",
                    6,
                    buyTwicePerishableWarning = false,
                    isPantryHomeItem = true,
                    isChecked = false,
                ),
                ShoppingListItem(
                    "honey",
                    "Runny honey",
                    "Cupboard",
                    75,
                    1,
                    "75g used",
                    5,
                    buyTwicePerishableWarning = false,
                    isPantryHomeItem = true,
                    isChecked = false,
                ),
                ShoppingListItem(
                    "wholegrain_mustard",
                    "Wholegrain mustard",
                    "Cupboard",
                    45,
                    1,
                    "45g used",
                    3,
                    buyTwicePerishableWarning = false,
                    isPantryHomeItem = true,
                    isChecked = false,
                ),
                ShoppingListItem(
                    "peanut_butter",
                    "Smooth peanut butter",
                    "Cupboard",
                    90,
                    1,
                    "90g used",
                    6,
                    buyTwicePerishableWarning = false,
                    isPantryHomeItem = true,
                    isChecked = false,
                ),
            )

        val allItems = aisles.flatMap { it.items }
        return SmartDietShoppingProjection(
            startDayKey = startDayKey,
            horizonDays = 7,
            aisleGroups = aisles,
            pantryHomeItems = pantry,
            totalActiveItems = allItems.size,
            checkedItemCount = allItems.count { it.isChecked },
        )
    }
}
