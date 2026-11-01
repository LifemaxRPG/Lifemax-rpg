package com.lifemax.rpg.screenshots

import androidx.compose.foundation.lazy.rememberLazyListState
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.feature.settings.data.WebSettingsRepository
import com.lifemax.rpg.feature.settings.domain.SettingsRow
import com.lifemax.rpg.feature.settings.presentation.SettingsActions
import com.lifemax.rpg.feature.settings.presentation.SettingsScreen
import com.lifemax.rpg.feature.settings.presentation.SettingsViewModel
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * The "Native Overview cards (beta)" switch on native Settings, Look & feel, Appearance (plan
 * OVERVIEW_BODY.md §3.9): the row right after "Native Overview", off by default, as the page words
 * it. The existing Settings references end above it, so it has its own images here rather than
 * changing those (tools/gen_native_fixtures.js settings).
 */
class SettingsOverviewCardsScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 1620, density = Density(420))
        )

    /** Appearance scrolled so the "Native Overview" row is at the top, the cards' row under it. */
    private fun snap(fixture: String, light: Boolean) {
        val json =
            JSONObject(javaClass.getResource("/fixtures/settings-$fixture.json")!!.readText())
                .put("light", light)
                .put("tokens", ScreenshotPalette.tokens(light))
        val repository = WebSettingsRepository().apply { accept(json, true) }
        val vm = SettingsViewModel(repository, { _, _ -> }, nowMs = { 0L }).apply { onOpen() }
        val p = repository.state.value!!
        val appearance = p.cardsIn("look").first()
        val row =
            appearance.rows.indexOfFirst { (it as? SettingsRow.Line)?.title == "Native Overview" }
        require(row >= 0) { "no Native Overview row on Appearance" }
        // the list: the head, the chips and the classic row, then each card's title and its rows
        val item = 3 + 1 + row
        paparazzi.snapshot {
            SettingsScreen(p, vm.ui.value, SettingsActions(), list = rememberLazyListState(item, 0))
        }
    }

    @Test fun overviewCardsRowDark() = snap("default", light = false)

    @Test fun overviewCardsRowLight() = snap("light", light = true)
}
