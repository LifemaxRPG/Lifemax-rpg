package com.lifemax.rpg.screenshots

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.feature.library.data.WebMediaRepository
import com.lifemax.rpg.feature.library.presentation.MediaActions
import com.lifemax.rpg.feature.library.presentation.MediaScreen
import com.lifemax.rpg.feature.library.presentation.MediaViewModel
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * The Library's Media pane as the phone draws it, from projections the web app produced
 * (tools/gen_native_fixtures.js), on a 411 dp wide phone like the owner's. Each image is a square
 * screenful at full resolution, so a change of a dp or a colour shows: the list scrolled to a given
 * item, and so far into it.
 */
class MediaScreenshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = phone())

    private fun phone(fontScale: Float = 1f) =
        DeviceConfig.PIXEL_5.copy(
            screenHeight = 1080,
            density = Density(420),
            fontScale = fontScale,
        )

    private fun model(fixture: String, light: Boolean) =
        MediaViewModel(
                WebMediaRepository().apply {
                    accept(
                        JSONObject(
                                javaClass.getResource("/fixtures/media-$fixture.json")!!.readText()
                            )
                            .put("light", light)
                            .put("tokens", ScreenshotPalette.tokens(light)),
                        true,
                    )
                }
            )
            .apply { onProjection() }

    /** The pane from list item [item] down, [scrolledDp] into it; [search] typed on the screen. */
    private fun snap(
        fixture: String,
        light: Boolean = false,
        item: Int = 0,
        scrolledDp: Int = 0,
        search: String? = null,
    ) {
        val vm = model(fixture, light)
        search?.let(vm::search)
        paparazzi.snapshot {
            MediaScreen(
                vm.ui!!,
                MediaActions(),
                list =
                    rememberLazyListState(
                        item,
                        with(LocalDensity.current) { scrolledDp.dp.roundToPx() },
                    ),
            )
        }
    }

    /** The top: the Media/Journal switch, the page head and the first stats. */
    @Test fun fullDark() = snap("full")

    @Test fun fullLight() = snap("full", light = true)

    @Test fun statsDark() = snap("full", item = STATS)

    @Test fun statsLight() = snap("full", light = true, item = STATS)

    /** The card's filters, then an audiobook with its progress, its file and its buttons. */
    @Test fun audiobookDark() = snap("full", item = FILTERS)

    @Test fun audiobookLight() = snap("full", light = true, item = FILTERS)

    /** A finished item with its link, items in progress with notes and a link. */
    @Test fun rowsDark() = snap("full", item = FIRST_ROW + 1)

    @Test fun rowsLight() = snap("full", light = true, item = FIRST_ROW + 1)

    /** An audiobook not played yet, a finished documentary, an old item without type or status. */
    @Test fun moreRowsDark() = snap("full", item = FIRST_ROW + 4)

    /** The end of the list and of its card. */
    @Test fun endDark() = snap("full", item = FIRST_ROW + 22)

    /** The list after "Search my library for wealth": the page's filter, taken by the screen. */
    @Test fun searchDark() = snap("search", item = FILTERS)

    @Test fun noMatchDark() = snap("full", item = FILTERS, search = "nothing like this")

    @Test fun emptyDark() = snap("empty", item = FILTERS)

    @Test fun emptyLight() = snap("empty", light = true, item = FILTERS)

    /** A new player's library: the feature tip under the page head. */
    @Test fun tipDark() = snap("tip", item = HEADER, scrolledDp = 120)

    @Test fun tipLight() = snap("tip", light = true, item = HEADER, scrolledDp = 120)

    /** Larger text (Settings, font size, at its largest). */
    @Test
    fun largeTextTop() {
        paparazzi.unsafeUpdateConfig(phone(fontScale = 2f))
        snap("full")
    }

    @Test
    fun largeTextFilters() {
        paparazzi.unsafeUpdateConfig(phone(fontScale = 2f))
        snap("full", item = FILTERS)
    }

    @Test
    fun largeTextRow() {
        paparazzi.unsafeUpdateConfig(phone(fontScale = 2f))
        snap("full", item = FIRST_ROW + 1)
    }

    private companion object {
        // the list's items when there is no tip: switch, head, stats, filters, then the rows
        const val HEADER = 1
        const val STATS = 2
        const val FILTERS = 3
        const val FIRST_ROW = 4
    }
}
