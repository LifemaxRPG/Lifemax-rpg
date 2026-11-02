package com.lifemax.rpg.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.lifemax.rpg.app.shell.swipe.TabScreenView
import com.lifemax.rpg.core.designsystem.LifeMaxTheme
import com.lifemax.rpg.feature.character.data.WebCharacterRepository
import com.lifemax.rpg.feature.character.domain.CharacterProjection
import com.lifemax.rpg.feature.character.presentation.CharacterScreen
import com.lifemax.rpg.feature.character.presentation.CharacterSelection
import com.lifemax.rpg.feature.character.presentation.CharacterSheet
import com.lifemax.rpg.feature.character.presentation.components.CharacterBenchmarksCard
import com.lifemax.rpg.feature.character.presentation.components.CharacterSheetActions
import com.lifemax.rpg.feature.character.presentation.components.CharacterSheets
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * Character's sheets (Daily Luck, the benchmarks) over the screen, from the page's own actions
 * projection (tools/gen_character_fixtures.js), and the benchmark card once the page speaks the
 * actions.
 */
class CharacterSheetsScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, maxPercentDifference = 0.001)

    private fun projection(light: Boolean, rolled: Boolean = false): CharacterProjection =
        WebCharacterRepository()
            .apply {
                val name = if (rolled) "character-actions-rolled" else "character-actions"
                accept(
                    JSONObject(javaClass.getResource("/fixtures/$name.json")!!.readText())
                        .put("light", light)
                        .put("tokens", ScreenshotPalette.tokens(light)),
                    true,
                )
            }
            .state
            .value!!

    private fun snap(p: CharacterProjection, sheet: CharacterSheet) =
        paparazzi.snapshot(
            TabScreenView(paparazzi.context, "character").apply {
                setContent {
                    CharacterScreen(
                        p = p,
                        ui = CharacterSelection(),
                        onSelect = {},
                        onCompare = {},
                        onSort = {},
                        onSkills = {},
                        onAction = { _, _ -> },
                    ) {
                        CharacterSheets(p, sheet, true, 64.dp, 72.dp, CharacterSheetActions())
                    }
                }
            }
        )

    private val rolled = { light: Boolean -> projection(light, rolled = true) }

    @Test
    fun luckRolledDark() {
        val p = rolled(false)
        snap(p, CharacterSheet.Luck(p.luckRoll, false))
    }

    @Test
    fun luckRolledLight() {
        val p = rolled(true)
        snap(p, CharacterSheet.Luck(p.luckRoll, false))
    }

    @Test
    fun luckRepeatNoticeDark() {
        val p = rolled(false)
        snap(p, CharacterSheet.Luck(p.luckRoll, false, notice = "Already rolled today."))
    }

    @Test fun luckRollingDark() = snap(projection(false), CharacterSheet.Luck(null, true))

    @Test
    fun luckRefusedDark() =
        snap(
            projection(false),
            CharacterSheet.Luck(
                null,
                false,
                error = "Could not save to this browser (storage full?)",
            ),
        )

    @Test fun benchmarksDark() = snap(projection(false), CharacterSheet.Benchmarks())

    @Test fun benchmarksLight() = snap(projection(true), CharacterSheet.Benchmarks())

    @Test
    fun benchmarksAddingDark() =
        snap(
            projection(false),
            CharacterSheet.Benchmarks(
                name = "Squat",
                value = "300 lb",
                error = "Enter a name and result",
            ),
        )

    @Test
    fun benchmarksEditingDark() =
        snap(
            projection(false),
            CharacterSheet.Benchmarks(editing = 1, name = "Bench press", value = "205 lb"),
        )

    @Test
    fun benchmarksPendingDark() =
        snap(
            projection(false),
            CharacterSheet.Benchmarks(name = "Squat", value = "300 lb", pending = true),
        )

    /** The card says "Add or edit ›" instead of "in Classic" when the sheet is the screen's. */
    @Test
    fun benchmarksCardWithActionsDark() {
        val p = projection(false)
        paparazzi.snapshot {
            LifeMaxTheme(p.tokens, p.light) {
                Box(Modifier.background(MaterialTheme.colorScheme.background).padding(12.dp)) {
                    CharacterBenchmarksCard(p) { _, _ -> }
                }
            }
        }
    }
}
