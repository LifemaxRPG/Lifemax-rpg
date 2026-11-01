package com.lifemax.rpg.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.core.designsystem.PageColors
import com.lifemax.rpg.core.designsystem.PageTheme
import com.lifemax.rpg.feature.overview.domain.EditorRow
import com.lifemax.rpg.feature.overview.pageEditorWords
import com.lifemax.rpg.feature.overview.presentation.editor.DragPreview
import com.lifemax.rpg.feature.overview.presentation.editor.OverviewEditor
import org.junit.Rule
import org.junit.Test

/**
 * The native Overview editor (phase 2) on its own, as the page's #ovEdit looks at 411 dp: all
 * eleven tiles in the default order, a hidden tile, and a row lifted mid-drag with the rows it has
 * passed standing aside. Dark and light.
 */
class OverviewEditorScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 2800, density = Density(420)),
            useDeviceResolution = true,
            maxPercentDifference = 0.001,
        )

    private fun frame(
        light: Boolean,
        hidden: Set<String> = emptySet(),
        preview: @Composable () -> DragPreview? = { null },
    ) =
        paparazzi.snapshot {
            val colors = PageColors(ScreenshotPalette.tokenMap(light), light)
            PageTheme(colors) {
                Box(
                    Modifier.fillMaxSize()
                        .background(colors.bg)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    OverviewEditor(
                        rows(light).map { it.copy(hidden = it.id in hidden) },
                        onReorder = { _, _ -> },
                        onReset = {},
                        onDone = {},
                        words = pageEditorWords(),
                        animateOpen = false,
                        preview = preview(),
                    )
                }
            }
        }

    @Test fun open() = frame(light = false)

    @Test fun openLight() = frame(light = true)

    @Test fun hiddenTile() = frame(light = false, hidden = setOf("cat", "apps"))

    @Test fun hiddenTileLight() = frame(light = true, hidden = setOf("cat", "apps"))

    // Next moves lifted from slot 1 and carried down past Level & today and Rewards
    private val lifted: @Composable () -> DragPreview = {
        DragPreview("next", 3, with(LocalDensity.current) { 150.dp.toPx() })
    }

    @Test fun midDrag() = frame(light = false, preview = lifted)

    @Test fun midDragLight() = frame(light = true, preview = lifted)

    private companion object {
        /**
         * OV_TILES after the three splices, with the page's colours resolved as it projects them.
         */
        fun rows(light: Boolean): List<EditorRow> {
            val accent = if (light) 0xFF3B5BDBL else 0xFF7C9CFFL
            val gold = if (light) 0xFFB8860BL else 0xFFE8B84AL
            val good = if (light) 0xFF0F9F6EL else 0xFF3DD68CL
            return listOf(
                EditorRow(
                    "plan",
                    "Today’s plan",
                    "Life Director’s live day dial",
                    "clock",
                    0xFF38BDF8,
                ),
                EditorRow("next", "Next moves", "The most useful things to do now", "bolt", accent),
                EditorRow(
                    "hero",
                    "Level & today",
                    "Today’s XP ring, level and streak",
                    "star",
                    gold,
                ),
                EditorRow("rewards", "Rewards", "Sparks, shields and the shop", "star", 0xFFFBBF24),
                EditorRow(
                    "apps",
                    "App shortcuts",
                    "Your phone apps in one row",
                    "grid",
                    0xFFA78BFA,
                ),
                EditorRow(
                    "health",
                    "Activity today",
                    "Steps, workouts, sleep and weight from Health Connect",
                    "bolt",
                    0xFF34D399,
                ),
                EditorRow(
                    "attrs",
                    "Attributes",
                    "All eight stats at a glance",
                    "target",
                    0xFFA78BFA,
                ),
                EditorRow("week", "This week", "XP day by day", "grid", good),
                EditorRow("cat", "Cat", "Miso’s room", "paw", 0xFFFF7A9A),
                EditorRow("luck", "Daily luck", "Today’s dice roll", "dice", 0xFFFFD35C),
                EditorRow("recent", "Recent activity", "Your last few logs", "list", 0xFF94A3B8),
            )
        }
    }
}
