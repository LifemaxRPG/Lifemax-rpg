package com.lifemax.rpg.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.feature.nutrition.domain.model.MealSlotType
import com.lifemax.rpg.feature.nutrition.domain.model.NutritionThemeMode

/**
 * Pixel-accurate 1:1 visual tokens mapped directly from:
 * - lifemax_modular_v73/src/styles/00-00-foundation.css
 * - lifemax_modular_v73/src/styles/08-food.css
 * - lifemax_modular_v73/src/runtime/segments/50-food.js
 * - lifemax_modular_v73/src/runtime/segments/50-smartdiet.js
 */
@Immutable
data class LifeMaxNutritionColors(
    val isLight: Boolean,
    val bgPrimary: Color,
    val cardTop: Color,
    val cardBottom: Color,
    val surfaceSunken: Color,
    val surfaceElevated: Color,
    val borderSubtle: Color,
    val borderStrong: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val textSubtle: Color,
    val accent: Color,
    val accentBlue: Color,
    val accentGold: Color,
    val statusOk: Color,
    val statusWarn: Color,
    val statusBad: Color,
    // Macro dial colors (50-food.js lines 536-539)
    val macroProtein: Color = Color(0xFFFF7A8A),
    val macroCarbs: Color = Color(0xFFFFC15E),
    val macroFat: Color = Color(0xFF4FD6C0),
    val macroOverflow: Color = Color(0xEBFFFFFF),
    // Meal slot accent dots (50-food.js lines 18-23)
    val slotBreakfast: Color = Color(0xFFF2B84A),
    val slotLunch: Color = Color(0xFF5FD08A),
    val slotDinner: Color = Color(0xFF6AA4F0),
    val slotSnacks: Color = Color(0xFFEF7A85),
    // Water card gradient & fill (08-food.css lines 173-204)
    val waterAccent: Color = Color(0xFF4FB3FF),
    val waterCyan: Color = Color(0xFF00F5D4),
) {
    val cardGradient: Brush
        get() = Brush.verticalGradient(listOf(cardTop, cardBottom))

    val activePillGradient: Brush
        get() =
            Brush.linearGradient(
                listOf(
                    accent.copy(alpha = if (isLight) 0.24f else 0.19f),
                    accentBlue.copy(alpha = if (isLight) 0.18f else 0.14f),
                )
            )

    val primaryCtaGradient: Brush
        get() =
            Brush.linearGradient(listOf(accent.copy(alpha = 0.22f), accentBlue.copy(alpha = 0.18f)))

    fun slotColor(slot: MealSlotType): Color =
        when (slot) {
            MealSlotType.BREAKFAST -> slotBreakfast
            MealSlotType.LUNCH -> slotLunch
            MealSlotType.DINNER -> slotDinner
            MealSlotType.SNACKS -> slotSnacks
        }
}

@Immutable
data class LifeMaxNutritionDimens(
    val rootMaxWidth: Dp = 680.dp,
    val cardGap: Dp = 12.dp,
    val cardRadius: Dp = 20.dp,
    val heroRadius: Dp = 24.dp,
    val pillRadius: Dp = 14.dp,
    val tileRadius: Dp = 14.dp,
    val sheetRadius: Dp = 24.dp,
    val dialSize: Dp = 264.dp,
    val weekRingSize: Dp = 28.dp,
)

val LocalLifeMaxNutritionColors = staticCompositionLocalOf {
    buildLifeMaxNutritionColors(NutritionThemeMode.DARK, "#00F5D4")
}

val LocalLifeMaxNutritionDimens = staticCompositionLocalOf { LifeMaxNutritionDimens() }

fun parseHexColor(hex: String, fallback: Color = Color(0xFF00F5D4)): Color {
    val clean = hex.trim().removePrefix("#")
    return try {
        when (clean.length) {
            6 -> Color(clean.toLong(16) or 0xFF000000L)
            8 -> Color(clean.toLong(16))
            else -> fallback
        }
    } catch (_: Exception) {
        fallback
    }
}

fun buildLifeMaxNutritionColors(
    themeMode: NutritionThemeMode,
    accentHex: String,
): LifeMaxNutritionColors {
    val accentColor = parseHexColor(accentHex, Color(0xFF00F5D4))
    return if (themeMode == NutritionThemeMode.LIGHT) {
        LifeMaxNutritionColors(
            isLight = true,
            bgPrimary = Color(0xFFEEF3FA),
            cardTop = Color(0xFAFFFFFF),
            cardBottom = Color(0xF5F4F8FD),
            surfaceSunken = Color(0x0B0F172A),
            surfaceElevated = Color(0xFFFFFFFF),
            borderSubtle = Color(0x170F172A),
            borderStrong = Color(0x2E0F172A),
            textPrimary = Color(0xFF0F172A),
            textMuted = Color(0xAD0F172A),
            textSubtle = Color(0x750F172A),
            accent = accentColor,
            accentBlue = Color(0xFF2563EB),
            accentGold = Color(0xFFD97706),
            statusOk = Color(0xFF10B981),
            statusWarn = Color(0xFFF59E0B),
            statusBad = Color(0xFFEF4444),
        )
    } else {
        LifeMaxNutritionColors(
            isLight = false,
            bgPrimary = Color(0xFF080C14),
            cardTop = Color(0xF5121B29),
            cardBottom = Color(0xFA0C121D),
            surfaceSunken = Color(0x09FFFFFF),
            surfaceElevated = Color(0xFF151F30),
            borderSubtle = Color(0x14FFFFFF),
            borderStrong = Color(0x29FFFFFF),
            textPrimary = Color(0xFFF3F7FD),
            textMuted = Color(0xB8DDE7F6),
            textSubtle = Color(0x78DDE7F6),
            accent = accentColor,
            accentBlue = Color(0xFF3A86FF),
            accentGold = Color(0xFFFFBE0B),
            statusOk = Color(0xFF3BE798),
            statusWarn = Color(0xFFF7B955),
            statusBad = Color(0xFFFF6D7A),
        )
    }
}

@Composable
fun LifeMaxNutritionTheme(
    themeMode: NutritionThemeMode = NutritionThemeMode.DARK,
    accentHex: String = "#00F5D4",
    content: @Composable () -> Unit,
) {
    val colors = buildLifeMaxNutritionColors(themeMode, accentHex)
    CompositionLocalProvider(
        LocalLifeMaxNutritionColors provides colors,
        LocalLifeMaxNutritionDimens provides LifeMaxNutritionDimens(),
        content = content,
    )
}
