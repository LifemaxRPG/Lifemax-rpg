package com.lifemax.rpg.app.shell.swipe

import android.content.Context
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.PixelIcon
import com.lifemax.rpg.core.designsystem.components.pixelIconNames

/**
 * What slides in where no screen can (plan §3): the page's background with the tab's icon and
 * label, for a native tab not opened yet, or the bare background behind a wall's rubber band. It
 * sits at index 1 of android.R.id.content, above the WebView and below every screen and bar, so it
 * never covers the bars; GONE when unused. Decorative: not important for accessibility. A touch
 * that starts on it is swallowed ([onTouch] hears it), never reaching the WebView.
 */
internal class SwipeVeil(context: Context, private val onTouch: (MotionEvent) -> Unit) :
    AbstractComposeView(context) {
    private var tab by mutableStateOf<SwipeTab?>(null)
    private var colors by mutableStateOf(SwipeVeilDefaults.colors)

    init {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    /** The tab to show, or null for the bare background; [colors] when the page sent them. */
    fun show(tab: SwipeTab?, colors: SwipeColors?) {
        this.tab = tab
        if (colors != null) this.colors = colors
    }

    @Composable override fun Content() = SwipeVeilContent(tab, colors)

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        onTouch(event)
        return true
    }
}

/** The veil's drawing: [colors]' background, and [tab]'s icon and label in the muted colour. */
@Composable
fun SwipeVeilContent(tab: SwipeTab?, colors: SwipeColors) {
    Box(Modifier.fillMaxSize().background(Color(colors.bg)), contentAlignment = Alignment.Center) {
        if (tab != null) {
            val muted = Color(colors.muted)
            Column(
                Modifier.padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (tab.icon in pixelIconNames) PixelIcon(tab.icon, muted, Modifier.size(40.dp))
                Text(
                    tab.label,
                    color = muted,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

internal object SwipeVeilDefaults {
    /** The page's dark theme, until a scene brings the page's own. */
    val colors =
        SwipeColors(bg = 0xFF0C0E12.toInt(), text = 0xFFEEF1F6.toInt(), muted = 0xFF8B93A3.toInt())
}
