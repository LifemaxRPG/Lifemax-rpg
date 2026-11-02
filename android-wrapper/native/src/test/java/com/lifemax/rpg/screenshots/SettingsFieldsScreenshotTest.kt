package com.lifemax.rpg.screenshots

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.core.designsystem.form.PickerState
import com.lifemax.rpg.core.designsystem.form.TimePick
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
 * The text, number and time fields the screen edits itself (settings.fields.v1), from projections
 * the web app produced (tools/gen_native_fixtures.js settings: fields-phone, fields, fields-light).
 * They look like the fields handed to the page, which the existing Settings images still show; what
 * is new here is a draft being typed, a form waiting for the page, and a time picker open.
 */
class SettingsFieldsScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 1620, density = Density(420))
        )

    private fun snap(
        fixture: String,
        card: String,
        light: Boolean = false,
        scrolledDp: Int = 0,
        pickers: PickerState? = null,
        use: SettingsViewModel.(SettingsProjection) -> Unit = {},
    ) {
        val json =
            JSONObject(javaClass.getResource("/fixtures/settings-$fixture.json")!!.readText())
                .put("light", light)
                .put("tokens", ScreenshotPalette.tokens(light))
        val repository = WebSettingsRepository().apply { accept(json, true) }
        val vm =
            SettingsViewModel(repository, { _, _ -> }, nowMs = { 0L }).apply {
                onOpen()
                use(repository.state.value!!)
            }
        val p = repository.state.value!!
        val cards = p.cardsIn(p.category)
        val at = cards.indexOfFirst { it.id == card }
        require(at >= 0) { "$card is not on ${p.category}" }
        // the list: the head, the chips and the classic row, then each card's top and its rows
        val item = 3 + cards.take(at).sumOf { 1 + it.rows.size }
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
                pickers = pickers ?: remember { PickerState() },
            )
        }
    }

    private fun SettingsProjection.field(id: String): SettingsControl.Field =
        cards
            .flatMap { it.rows }
            .flatMap {
                when (it) {
                    is SettingsRow.Line -> it.controls
                    is SettingsRow.Field -> it.controls
                    else -> emptyList()
                }
            }
            .first { it.id == id } as SettingsControl.Field

    /** The profile as the page holds it: the name, an empty weight and height (placeholders). */
    @Test fun profileDark() = snap("fields", "character-profile")

    @Test fun profileLight() = snap("fields-light", "character-profile", light = true)

    /** Typed and not yet saved: the owner's text in the page's boxes. */
    @Test
    fun profileTyped() =
        snap("fields", "character-profile") { p ->
            type(p.field("#characterName"), "Ada Lovelace")
            type(p.field("#characterWeight"), "175.5")
            type(p.field("#characterHeightFt"), "5")
            type(p.field("#characterHeightIn"), "11")
        }

    /** Save sent: the button waits for the page, the typed text stays. */
    @Test
    fun profileSaving() =
        snap("fields", "character-profile") { p ->
            type(p.field("#characterName"), "Ada Lovelace")
            type(p.field("#characterWeight"), "175.5")
            press(
                p.cards
                    .first { it.id == "character-profile" }
                    .rows
                    .flatMap { (it as? SettingsRow.Controls)?.controls ?: emptyList() }
                    .filterIsInstance<SettingsControl.Button>()
                    .first { it.label == "Save character" }
            )
        }

    @Test fun decayDark() = snap("fields", "decay-system")

    @Test
    fun decayTyped() =
        snap("fields-light", "decay-system", light = true) { p ->
            type(p.field("#decayGrace"), "10")
            type(p.field("#decayRate"), "5")
        }

    /** The nine XP rules' boxes, the first two typed over. */
    @Test
    fun rulesTyped() =
        snap("fields", "xp-rules") { p ->
            type(p.field("#rule-reading"), "30")
            type(p.field("#rule-business"), "")
        }

    @Test fun rulesLight() = snap("fields-light", "xp-rules", light = true)

    /** Phone & sync, Notifications: the four times, the check-in's enabled. */
    @Test fun notificationsDark() = snap("fields-phone", "lmaNotifCard", scrolledDp = 330)

    /** A time picked and waiting for the page: it shows as picked. */
    @Test
    fun timePicked() =
        snap("fields-phone", "lmaNotifCard", scrolledDp = 330) { p ->
            time(p.field("reminders.workoutTime"), 18 * 60 + 45)
        }

    /** The clock picker over the screen, opened on the workout time. */
    @Test
    fun timePicker() =
        snap(
            "fields-phone",
            "lmaNotifCard",
            scrolledDp = 330,
            pickers = PickerState(TimePick("Workout time", 17 * 60, h12 = true) {}),
        )
}
