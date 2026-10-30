package com.lifemax.rpg.screenshots

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.lifemax.rpg.feature.character.data.WebCharacterRepository
import com.lifemax.rpg.feature.character.presentation.CharacterScreen
import com.lifemax.rpg.feature.character.presentation.CharacterSelection
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/** Character as the phone draws it, from the same fixture the projection tests use. */
class CharacterScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 3200))

    private fun projection(light: Boolean) =
        WebCharacterRepository()
            .apply {
                accept(
                    JSONObject(javaClass.getResource("/character-projection.json")!!.readText())
                        .put("light", light)
                        .put("tokens", ScreenshotPalette.tokens(light)),
                    true,
                )
            }
            .state
            .value!!

    private fun snap(light: Boolean) =
        paparazzi.snapshot {
            CharacterScreen(
                p = projection(light),
                ui = CharacterSelection(),
                onSelect = {},
                onCompare = {},
                onSort = {},
                onSkills = {},
                onAction = { _, _ -> },
            )
        }

    @Test fun characterDark() = snap(false)

    @Test fun characterLight() = snap(true)
}
