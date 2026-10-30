package com.lifemax.rpg.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.lifemax.rpg.core.designsystem.LifeMaxTheme
import com.lifemax.rpg.core.designsystem.components.ChoicePill
import com.lifemax.rpg.core.designsystem.components.PixelIcon
import com.lifemax.rpg.core.designsystem.components.ProgressBar
import com.lifemax.rpg.core.designsystem.components.RoundButton
import com.lifemax.rpg.core.designsystem.components.SectionLabel
import com.lifemax.rpg.core.designsystem.components.SegmentedChoice
import com.lifemax.rpg.core.designsystem.components.SurfaceCard
import com.lifemax.rpg.core.designsystem.components.pixelIconNames
import org.junit.Rule
import org.junit.Test

/**
 * Every core component in its states, in both palettes and at the largest font size. Each section
 * is its own image, only as tall as it needs, so it is stored close to full size and a wrong icon
 * pixel or a lost sliver of bar fails verify.
 */
class GalleryScreenshotTest {
    // Renders are exact, and the default tolerance (0.1 %) would pass either regression.
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5,
            renderingMode = SessionParams.RenderingMode.SHRINK,
            maxPercentDifference = 0.001,
        )

    private fun snap(light: Boolean) {
        section("cards", light) { Cards() }
        section("pills", light) { Pills() }
        section("segments", light) { Segments() }
        section("buttons", light) { Buttons() }
        section("bars", light) { Bars() }
        section("icons", light) { Icons() }
    }

    private fun section(name: String, light: Boolean, content: @Composable () -> Unit) =
        paparazzi.snapshot(name) {
            LifeMaxTheme(ScreenshotPalette.tokenMap(light), light) {
                Box(Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp)) {
                    content()
                }
            }
        }

    @Test fun galleryDark() = snap(false)

    @Test fun galleryLight() = snap(true)

    @Test
    fun galleryLargeText() {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_5.copy(fontScale = 2f))
        snap(false)
    }
}

// Three attributes, in the web's colours.
private val physique = Color(0xFFFF6B57)
private val intelligence = Color(0xFF5B8CFF)
private val discipline = Color(0xFFA78BFA)

@Composable
private fun Cards() = SurfaceCard {
    SectionLabel("Surface card")
    Text(
        "A card holds one section, under a section label.",
        Modifier.padding(top = 6.dp),
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Pills() = SurfaceCard {
    SectionLabel("Choice pill")
    FlowRow(
        Modifier.padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChoicePill("None", true, {})
        ChoicePill("Physique", false, {}, tint = physique) {
            PixelIcon("dumbbell", physique, Modifier.size(14.dp))
        }
        ChoicePill("Discipline", true, {}, tint = discipline) {
            PixelIcon("target", discipline, Modifier.size(14.dp))
        }
    }
    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(15, 30, 45, 60, 90).forEach { m ->
            ChoicePill("$m", m == 45, {}, Modifier.weight(1f), centered = true)
        }
    }
}

@Composable
private fun Segments() = SurfaceCard {
    SectionLabel("Segmented choice")
    SegmentedChoice(
        listOf(
            Triple("0.8", "Light", "0.8×"),
            Triple("1", "Normal", "1.0×"),
            Triple("1.1", "Challenging", "1.1×"),
            Triple("1.2", "High quality", "1.2×"),
        ),
        "1.1",
        {},
        Modifier.padding(top = 10.dp),
    )
    SegmentedChoice(
        listOf(Triple("day", "Day", ""), Triple("week", "Week", ""), Triple("month", "Month", "")),
        "week",
        {},
        Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun Buttons() = SurfaceCard {
    SectionLabel("Round button")
    Row(
        Modifier.padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundButton("−", "Five minutes less", {})
        Text("45 min", color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp)
        RoundButton("+", "Five minutes more", {})
    }
}

@Composable
private fun Bars() = SurfaceCard {
    SectionLabel("Progress bar")
    listOf(0f, .04f, .5f, 1f).forEach {
        ProgressBar(it, MaterialTheme.colorScheme.primary, Modifier.padding(top = 10.dp))
    }
    ProgressBar(
        .62f,
        Brush.horizontalGradient(listOf(intelligence.copy(alpha = .55f), intelligence)),
        Modifier.padding(top = 10.dp),
        height = 10.dp,
    )
    ProgressBar(
        .35f,
        SolidColor(physique),
        Modifier.padding(top = 10.dp),
        height = 4.dp,
        outline = Color.Transparent,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Icons() = SurfaceCard {
    SectionLabel("Pixel icons")
    FlowRow(
        Modifier.padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        pixelIconNames.forEach { name ->
            Column(Modifier.width(50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PixelIcon(name, MaterialTheme.colorScheme.primary, Modifier.size(24.dp))
                Text(
                    name,
                    Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
