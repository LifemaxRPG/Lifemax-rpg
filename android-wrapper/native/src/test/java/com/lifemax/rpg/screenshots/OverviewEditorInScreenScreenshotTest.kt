package com.lifemax.rpg.screenshots

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.feature.overview.data.WebOverviewRepository
import com.lifemax.rpg.feature.overview.domain.editorRows
import com.lifemax.rpg.feature.overview.presentation.OverviewActions
import com.lifemax.rpg.feature.overview.presentation.OverviewEditing
import com.lifemax.rpg.feature.overview.presentation.OverviewScreen
import com.lifemax.rpg.feature.overview.presentation.editor.DragPreview
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * The native editor in place in the Overview (phase 2), as the page's #ovEdit stands where its
 * tiles were: the head with Edit lit, then the editor with every tile the page's projection names,
 * in its order and words. The established save in dark and light, the page's tip above it, a
 * reordered layout with hidden tiles, and a row lifted mid-drag; 411 dp wide like the owner's
 * phone.
 */
class OverviewEditorInScreenScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 2700, density = Density(420)),
            useDeviceResolution = true,
            maxPercentDifference = 0.001,
        )

    private fun fixture(name: String) =
        JSONObject(javaClass.getResource("/fixtures/overview-cards-$name.json")!!.readText())

    private fun screen(
        name: String = "established",
        light: Boolean = false,
        layout: Pair<List<String>, List<String>>? = null,
        lifted: Boolean = false,
    ) {
        val j = fixture(name).put("light", light).put("tokens", ScreenshotPalette.tokens(light))
        layout?.let { (order, hidden) ->
            j.put(
                "layout",
                JSONObject().put("order", JSONArray(order)).put("hidden", JSONArray(hidden)),
            )
        }
        val cards = WebOverviewRepository().apply { accept(j, true) }.state.value!!
        paparazzi.snapshot {
            // Next moves lifted and carried down past the two rows under it
            val preview =
                if (lifted) DragPreview("next", 3, with(LocalDensity.current) { 150.dp.toPx() })
                else null
            OverviewScreen(
                cards,
                OverviewActions(),
                editing =
                    OverviewEditing(
                        open = true,
                        rows = editorRows(cards.layout, cards.tileInfo),
                        preview = preview,
                        animateOpen = false,
                    ),
            )
        }
    }

    @Test fun editorDark() = screen()

    @Test fun editorLight() = screen(light = true)

    /** The page's feature tip stays above the editor, as on the page. */
    @Test fun editorUnderTheTip() = screen("tip")

    /** Luck first, Rewards last; the cat and the week hidden ("Hidden", the crossed eye). */
    @Test
    fun reorderedWithHiddenTiles() =
        screen(
            layout =
                listOf(
                    "luck",
                    "plan",
                    "next",
                    "hero",
                    "apps",
                    "health",
                    "attrs",
                    "week",
                    "cat",
                    "recent",
                    "rewards",
                ) to listOf("cat", "week")
        )

    @Test fun liftedRow() = screen(lifted = true)

    @Test fun liftedRowLight() = screen(light = true, lifted = true)
}
