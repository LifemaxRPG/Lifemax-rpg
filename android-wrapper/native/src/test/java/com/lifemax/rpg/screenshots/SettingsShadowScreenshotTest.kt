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
 * The "Native economy check (beta)" card on native Settings, Data & advanced (ECONOMY_SHADOW_PLAN
 * 3.3, WP6e), in each of its states, as the page words and projects it
 * (tools/gen_shadow_card_fixtures.js): off, checking, a difference found (dark, light and twice the
 * text size), Clear's own question, Save case's warning, the report copied, paused for Battery
 * Saver, and stopped after errors. The card is an ordinary page card, so these are drawn by the
 * unchanged Settings screen; the existing Settings references stay as they are.
 */
class SettingsShadowScreenshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = phone())

    private fun phone(fontScale: Float = 1f) =
        DeviceConfig.PIXEL_5.copy(
            screenHeight = 1620,
            density = Density(420),
            fontScale = fontScale,
        )

    /**
     * [fixture] on Data & advanced, scrolled so the card's title is at the top, or the card's row
     * titled [row] when given.
     */
    private fun snap(fixture: String, light: Boolean = false, row: String? = null) {
        val json =
            JSONObject(
                    javaClass.getResource("/fixtures/settings-shadow-$fixture.json")!!.readText()
                )
                .put("light", light)
                .put("tokens", ScreenshotPalette.tokens(light))
        val repository = WebSettingsRepository().apply { accept(json, true) }
        val vm = SettingsViewModel(repository, { _, _ -> }, nowMs = { 0L }).apply { onOpen() }
        val p = repository.state.value!!
        val cards = p.cardsIn(vm.ui.value.category(p))
        val at = cards.indexOfFirst { it.id == CARD }
        require(at >= 0) { "no economy check card on ${vm.ui.value.category(p)}" }
        // the list: the head, the chips and the classic row, then each card's title and its rows
        val first = 3 + cards.take(at).sumOf { 1 + it.rows.size }
        val item =
            if (row == null) first
            else
                first +
                    1 +
                    cards[at]
                        .rows
                        .indexOfFirst { (it as? SettingsRow.Line)?.title == row }
                        .also { require(it >= 0) { "no row $row" } }
        paparazzi.snapshot {
            SettingsScreen(p, vm.ui.value, SettingsActions(), list = rememberLazyListState(item, 0))
        }
    }

    @Test fun shadowOffDark() = snap("off")

    @Test fun shadowCheckingDark() = snap("checking")

    @Test fun shadowDifferentDark() = snap("different")

    @Test fun shadowDifferentLight() = snap("light", light = true)

    @Test
    fun shadowDifferentLargeText() {
        paparazzi.unsafeUpdateConfig(phone(fontScale = 2f))
        snap("different")
    }

    @Test fun shadowClearAskDark() = snap("clear")

    /** From the Save case row down: the difference chosen and the warning note under it. */
    @Test fun shadowCaseWarningDark() = snap("case", row = SAVE_CASE)

    @Test fun shadowCaseWarningLight() = snap("case", light = true, row = SAVE_CASE)

    @Test fun shadowCopiedDark() = snap("copied")

    @Test fun shadowPausedDark() = snap("paused")

    @Test fun shadowStoppedDark() = snap("stopped")

    private companion object {
        const val CARD = "nativeShadowCard"
        const val SAVE_CASE = "Save case\u2026"
    }
}
