package com.lifemax.rpg.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.core.designsystem.form.DatePick
import com.lifemax.rpg.core.designsystem.form.PickerState
import com.lifemax.rpg.core.designsystem.form.TimePick
import com.lifemax.rpg.feature.lifedirector.data.WebLifeDirectorRepository
import com.lifemax.rpg.feature.lifedirector.domain.DisruptSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorProjection
import com.lifemax.rpg.feature.lifedirector.domain.OpenSheet
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorActions
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorUi
import com.lifemax.rpg.feature.lifedirector.presentation.LifeDirectorScreen
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette
import com.lifemax.rpg.feature.lifedirector.presentation.components.LocalWallClock
import com.lifemax.rpg.feature.lifedirector.presentation.sheets.DisruptSheet
import com.lifemax.rpg.feature.lifedirector.presentation.sheets.MoveDisruptActions
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * Today's Move and "Something came up" sheets (phase 4A) from the page's own projections
 * (director-sheet-move.json, director-sheet-disrupt.json), over the native Today on a 411 dp wide
 * phone tall enough that each sheet shows whole; and the pickers of "Called into work" open over
 * the sheet.
 */
class DirectorMoveDisruptScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 1900, density = Density(420)),
            useDeviceResolution = true,
            maxPercentDifference = 0.001,
        )

    private fun projection(fixture: String, edit: JSONObject.() -> Unit = {}) =
        WebLifeDirectorRepository()
            .apply {
                accept(
                    JSONObject(
                            javaClass.getResource("/fixtures/director-$fixture.json")!!.readText()
                        )
                        .put("light", false)
                        .put("tokens", ScreenshotPalette.tokens(false))
                        .apply(edit),
                    true,
                )
            }
            .state
            .value!!

    private fun snap(name: String, p: LifeDirectorProjection) =
        paparazzi.snapshot(name) {
            CompositionLocalProvider(LocalWallClock provides { (p.today?.now ?: 0) * 1000 }) {
                LifeDirectorScreen(
                    p,
                    DirectorUi(sheet = OpenSheet(p.sheet!!.kind, p.sheet!!.id)),
                    DirectorActions(),
                    animate = false,
                )
            }
        }

    /** Move: the session's open times, the page's 48 at most. */
    @Test fun move() = snap("move", projection("sheet-move"))

    /** Move with no open time: the page's words instead. */
    @Test
    fun moveNone() =
        snap("none", projection("sheet-move") { getJSONObject("sheet").put("times", JSONArray()) })

    /** Something came up as the page opens it: "Called into work" open with its form. */
    @Test fun disruptWork() = snap("work", projection("sheet-disrupt"))

    /** Every fold closed. */
    @Test
    fun disruptFolded() =
        snap(
            "folded",
            projection("sheet-disrupt") {
                getJSONObject("sheet").getJSONArray("sections").getJSONObject(0).put("open", false)
            },
        )

    /** Every fold open: the choices, the lone button and the notes. */
    @Test
    fun disruptOpen() {
        paparazzi.unsafeUpdateConfig(
            DeviceConfig.PIXEL_5.copy(screenHeight = 2500, density = Density(420))
        )
        val d = projection("sheet-disrupt").sheet as DisruptSheetModel
        paparazzi.snapshot("open") { sheet(d, PickerState(), allOpen = true) }
    }

    /** "From" tapped: the clock in the page's 12-hour style, over the sheet. */
    @Test
    fun timePicker() {
        val d = projection("sheet-disrupt").sheet as DisruptSheetModel
        paparazzi.snapshot("time") { sheet(d, PickerState(TimePick("From", 1020, true) {})) }
    }

    /** The same on a 24-hour clock. */
    @Test
    fun timePicker24() {
        val d = projection("sheet-disrupt").sheet as DisruptSheetModel
        paparazzi.snapshot("time24") { sheet(d, PickerState(TimePick("Until", 1260, false) {})) }
    }

    /** "Date" tapped: the calendar on the page's day. */
    @Test
    fun datePicker() {
        val d = projection("sheet-disrupt").sheet as DisruptSheetModel
        paparazzi.snapshot("date") { sheet(d, PickerState(DatePick("Date", "2026-10-28") {})) }
    }

    @androidx.compose.runtime.Composable
    private fun sheet(d: DisruptSheetModel, pickers: PickerState, allOpen: Boolean = false) =
        Box(Modifier.fillMaxSize().background(DirectorPalette.bg0)) {
            DisruptSheet(d, null, 72.dp, MoveDisruptActions(), pickers, allOpen)
        }
}
