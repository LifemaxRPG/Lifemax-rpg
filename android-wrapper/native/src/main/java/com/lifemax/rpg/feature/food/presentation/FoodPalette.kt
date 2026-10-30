package com.lifemax.rpg.feature.food.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import java.text.NumberFormat
import java.util.Locale

internal val BgObsidian = Color(0xFF090B10)
internal val CardTop = Color(0xFF141822)
internal val CardBottom = Color(0xFF10131B)
internal val SunkenTile = Color(0xFF0C0F17)
internal val BorderSubtle = Color(0x14FFFFFF)
internal val TextPrimary = Color(0xFFF3F7FD)
internal val TextMuted = Color(0xFF8B95A7)
internal val TextSubtle = Color(0xFF7C8799)
internal val PillSelected = Color(0xFF252B3B)
internal val BlueAccent = Color(0xFF6E8EFB)
internal val ProteinRose = Color(0xFFFF7A8A)
internal val CarbsAmber = Color(0xFFFFC15E)
internal val FatTeal = Color(0xFF4FD6C0)
internal val BreakfastAmber = Color(0xFFF5B851)
internal val LunchGreen = Color(0xFF4ADE80)
internal val DinnerIndigo = Color(0xFF818CF8)
internal val SnacksCoral = Color(0xFFFF7A8A)

internal fun fmtNum(n: Int): String = NumberFormat.getNumberInstance(Locale.US).format(n)
