package com.lifemax.rpg.screenshots

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.feature.settings.data.WebSettingsRepository
import com.lifemax.rpg.feature.settings.domain.SettingsControl
import com.lifemax.rpg.feature.settings.domain.SettingsProjection
import com.lifemax.rpg.feature.settings.domain.SettingsRow
import com.lifemax.rpg.feature.settings.presentation.SettingsActions
import com.lifemax.rpg.feature.settings.presentation.SettingsScreen
import com.lifemax.rpg.feature.settings.presentation.SettingsViewModel
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * Settings from projections the web app produced (tools/gen_native_fixtures.js settings), on a 411
 * dp wide phone like the owner's, each image a part of the screen near full resolution. The
 * screen's own state (a switch waiting for the page, a chip chosen) comes from its view model.
 */
class SettingsScreenshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = phone())

    private fun phone(fontScale: Float = 1f) =
        DeviceConfig.PIXEL_5.copy(
            screenHeight = 1620,
            density = Density(420),
            fontScale = fontScale,
        )

    /**
     * [fixture] on [category] (the page's own when null), after [use], scrolled to [card]'s top (or
     * the head) and [scrolledDp] further.
     */
    private fun snap(
        fixture: String,
        light: Boolean = false,
        category: String? = null,
        card: String? = null,
        scrolledDp: Int = 0,
        use: SettingsViewModel.() -> Unit = {},
    ) {
        val json =
            JSONObject(javaClass.getResource("/fixtures/settings-$fixture.json")!!.readText())
                .put("light", light)
                .put("tokens", ScreenshotPalette.tokens(light))
        if (category != null) json.put("category", category)
        val repository = WebSettingsRepository().apply { accept(json, true) }
        val vm =
            SettingsViewModel(repository, { _, _ -> }, nowMs = { 0L }).apply {
                onOpen()
                use()
            }
        val p = repository.state.value!!
        val item = card?.let { index(p, vm.ui.value.category(p), it) } ?: 0
        paparazzi.snapshot {
            SettingsScreen(
                p,
                vm.ui.value,
                SettingsActions(),
                list =
                    rememberLazyListState(
                        item,
                        with(LocalDensity.current) { scrolledDp.dp.roundToPx() },
                    ),
            )
        }
    }

    /** The list index of [card]'s top: the head, the chips and the classic row, then the cards. */
    private fun index(p: SettingsProjection, category: String, card: String): Int {
        val cards = p.cardsIn(category)
        val at = cards.indexOfFirst { it.id == card }
        require(at >= 0) { "$card is not on $category" }
        return 3 + cards.take(at).sumOf { 1 + it.rows.size }
    }

    private fun SettingsProjection.switch(id: String): SettingsControl.Switch =
        cards
            .flatMap { it.rows }
            .filterIsInstance<SettingsRow.Line>()
            .flatMap { it.controls }
            .first { it.id == id } as SettingsControl.Switch

    /** A new player on Look & feel: the head, the chips, the way back, Appearance. */
    @Test fun defaultDark() = snap("default")

    @Test fun defaultLight() = snap("light", light = true)

    @Test
    fun largeText() {
        paparazzi.unsafeUpdateConfig(phone(fontScale = 2f))
        snap("default")
    }

    /** Twice the size: the bar's rows, the tick boxes and arrows keep their size. */
    @Test
    fun largeTextBar() {
        paparazzi.unsafeUpdateConfig(phone(fontScale = 2f))
        snap("bar", card = "tabsCard", scrolledDp = 40)
    }

    /** Twice the size: the quiet hours' two times go under their title. */
    @Test
    fun largeTextNotifications() {
        paparazzi.unsafeUpdateConfig(phone(fontScale = 2f))
        snap("many", card = "lmaNotifCard", scrolledDp = 1500)
    }

    /** Quests moved to second, Cat and Money hidden, Settings added to the bar. */
    @Test fun barDark() = snap("bar", card = "tabsCard")

    @Test fun barLight() = snap("bar", light = true, card = "tabsCard")

    /** Navigation's and Quest effects' selects, Guided workout, the haptic strength. */
    @Test fun selectsDark() = snap("default", card = "lmaNavCard")

    @Test fun hapticsDark() = snap("many", category = "look", card = "hapCard")

    /** Phone & sync for a new player: updates, the widget's note, switches Android asks about. */
    @Test fun phoneDark() = snap("default", category = "phone")

    /** The backup warning, controls the page has switched off until a folder is chosen. */
    @Test fun backupWarningDark() = snap("default", category = "phone", card = "lmaBakCard")

    @Test
    fun backupWarningLight() = snap("light", light = true, category = "phone", card = "lmaBakCard")

    /** Most switches on: health sync and its types, the backup folder chosen. */
    @Test fun healthDark() = snap("many", card = "lmaHealthCard")

    /** Reminders with their times, quiet hours. */
    @Test fun notificationsDark() = snap("many", card = "lmaNotifCard", scrolledDp = 300)

    @Test
    fun notificationsLight() = snap("many", light = true, card = "lmaNotifCard", scrolledDp = 300)

    /** The profile picture, the form's fields and select, the rewards. */
    @Test fun profileDark() = snap("default", category = "game", card = "character-profile")

    @Test
    fun profileLight() = snap("light", light = true, category = "game", card = "character-profile")

    /** The XP rules' number boxes and their buttons. */
    @Test fun rulesDark() = snap("many", category = "game", card = "xp-rules")

    /** Data management's danger row and the privacy note; developer mode's tools shown. */
    @Test fun dataDark() = snap("many", category = "data", card = "devModeCard")

    @Test fun dataManagementDark() = snap("default", category = "data", card = "data-management")

    /** Voice & journal: disabled buttons, the journal lock that Android asks about. */
    @Test fun voiceDark() = snap("default", category = "voice")

    /**
     * The screen waiting for the page: light mode asked on and Native Recipes asked on (both drawn
     * faded at the state asked for), and the Phone & sync chip chosen before the page answers.
     */
    @Test
    fun pendingDark() =
        snap("default") {
            val p = projection.value!!
            toggle(p.switch("lightMode"))
            toggle(p.switch("nativeRecipes"))
        }

    /**
     * Phone & sync chosen on the screen before the page answers: its cards show at once. (A chip
     * off the strip's edge is scrolled into view by an effect, which a single-frame render does not
     * run.)
     */
    @Test fun chipChosenDark() = snap("default") { selectCategory("phone") }
}
