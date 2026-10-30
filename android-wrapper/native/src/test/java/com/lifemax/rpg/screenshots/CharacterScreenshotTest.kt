package com.lifemax.rpg.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.lifemax.rpg.core.designsystem.LifeMaxTheme
import com.lifemax.rpg.feature.character.data.WebCharacterRepository
import com.lifemax.rpg.feature.character.domain.CharacterProjection
import com.lifemax.rpg.feature.character.presentation.CharacterScreen
import com.lifemax.rpg.feature.character.presentation.CharacterSelection
import com.lifemax.rpg.feature.character.presentation.components.CharacterAttributesCard
import com.lifemax.rpg.feature.character.presentation.components.CharacterBenchmarksCard
import com.lifemax.rpg.feature.character.presentation.components.CharacterBuildCard
import com.lifemax.rpg.feature.character.presentation.components.CharacterSkillsCard
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/** Character as the phone draws it, from the same fixture the projection tests use. */
class CharacterScreenshotTest {
    // Renders are exact, and the default tolerance (0.1 %) would pass a changed bar end.
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 3200),
            maxPercentDifference = 0.001,
        )

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

    /** The cards below the hero, which the screen image cuts off, each as tall as it draws. */
    private fun snapCards(light: Boolean) {
        paparazzi.unsafeUpdateConfig(renderingMode = SessionParams.RenderingMode.SHRINK)
        val p = projection(light)
        val ui = CharacterSelection()
        val physique = p.attributes.first { it.id == "physique" }
        card("build", p) { CharacterBuildCard(p, ui, physique, {}, {}) { _, _ -> } }
        card("attributes", p) { CharacterAttributesCard(p, ui, {}) { _, _ -> } }
        card("skills", p) { CharacterSkillsCard(p, ui) {} }
        card("benchmarks", p) { CharacterBenchmarksCard(p) { _, _ -> } }
    }

    private fun card(name: String, p: CharacterProjection, content: @Composable () -> Unit) =
        paparazzi.snapshot(name) {
            LifeMaxTheme(p.tokens, p.light) {
                Box(Modifier.background(MaterialTheme.colorScheme.background).padding(12.dp)) {
                    content()
                }
            }
        }

    @Test fun characterDark() = snap(false)

    @Test fun characterLight() = snap(true)

    @Test fun characterCardsDark() = snapCards(false)

    @Test fun characterCardsLight() = snapCards(true)
}
