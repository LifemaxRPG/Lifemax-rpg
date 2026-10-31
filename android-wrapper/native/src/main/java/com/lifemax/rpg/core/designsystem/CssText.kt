package com.lifemax.rpg.core.designsystem

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * The page's text as CSS sets it, for a screen that follows the page's styles: no letter spacing
 * but a style's own, and each line height spread evenly above and below the text.
 */
@Composable
fun CssText(content: @Composable () -> Unit) =
    CompositionLocalProvider(
        LocalTextStyle provides
            LocalTextStyle.current.copy(letterSpacing = 0.sp, lineHeightStyle = CssLines),
        content = content,
    )

private val CssLines = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
