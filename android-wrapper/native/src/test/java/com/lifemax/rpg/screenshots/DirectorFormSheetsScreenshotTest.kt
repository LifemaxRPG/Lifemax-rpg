package com.lifemax.rpg.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.core.designsystem.form.FieldValue
import com.lifemax.rpg.feature.lifedirector.data.WebLifeDirectorRepository
import com.lifemax.rpg.feature.lifedirector.domain.FormSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorProjection
import com.lifemax.rpg.feature.lifedirector.domain.OpenSheet
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorUi
import com.lifemax.rpg.feature.lifedirector.presentation.FormUi
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette
import com.lifemax.rpg.feature.lifedirector.presentation.sheets.FormSheetActions
import com.lifemax.rpg.feature.lifedirector.presentation.sheets.FormSheets
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * My time's sheets (phase 4B) from the page's own projections (director-form-*.json): the new
 * commitment, an edit of a rotating shift refused for its empty first day, "Add a goal", and a goal
 * form with its target folded and open. On a 411 dp wide phone tall enough that each sheet shows
 * whole; the morph between grid and form has no image (Paparazzi draws one frame, settled).
 */
class DirectorFormSheetsScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 2500, density = Density(420)),
            useDeviceResolution = true,
            maxPercentDifference = 0.001,
        )

    private fun projection(fixture: String): LifeDirectorProjection =
        WebLifeDirectorRepository()
            .apply {
                accept(
                    JSONObject(
                            javaClass.getResource("/fixtures/director-$fixture.json")!!.readText()
                        )
                        .put("light", false)
                        .put("tokens", ScreenshotPalette.tokens(false)),
                    true,
                )
            }
            .state
            .value!!

    private fun snap(
        name: String,
        p: LifeDirectorProjection,
        forms: FormUi = FormUi(),
        foldOpen: Boolean = false,
    ) =
        paparazzi.snapshot(name) {
            Sheet(
                p,
                DirectorUi(sheet = OpenSheet(p.sheet!!.kind, p.sheet!!.id), forms = forms),
                foldOpen,
            )
        }

    @Composable
    private fun Sheet(p: LifeDirectorProjection, ui: DirectorUi, foldOpen: Boolean) =
        Box(Modifier.fillMaxSize().background(DirectorPalette.bg0)) {
            FormSheets(p, ui, 72.dp, FormSheetActions(), animate = false, foldOpen = foldOpen)
        }

    /** Add a commitment, as the page opens it: a weekday work shift with its day chips. */
    @Test fun ruleNew() = snap("rule-new", projection("form-rule"))

    /**
     * Edit Nurse shift: rotating, so the days on and off and the first day show and the chips do
     * not; the first day emptied and Save tapped, so the page's words show under it; and Remove.
     */
    @Test
    fun ruleRotatingRefused() {
        val p = projection("form-rule-rotating")
        val m = p.sheet as FormSheetModel
        val open = OpenSheet(m.kind, m.id)
        val draft = m.schema.initialValues() + ("anchor" to FieldValue.Date(""))
        snap(
            "rule-rotating-error",
            p,
            FormUi(
                drafts = mapOf(open to draft),
                errors = mapOf("anchor" to "Pick the first day on"),
            ),
        )
    }

    /** Add a goal: the eight kinds and Something else, two to a row. */
    @Test fun goalType() = snap("goal-type", projection("form-goal-type"))

    /** Read More's form with its measurable target folded. */
    @Test fun goalFolded() = snap("goal-folded", projection("form-goal"))

    /** The same with the target open: total and unit, per hour and by, and the page's note. */
    @Test fun goalOpen() = snap("goal-open", projection("form-goal"), foldOpen = true)

    /** Something else: the activity, its minutes and times a week, and the two efforts. */
    @Test fun goalCustom() = snap("goal-custom", projection("form-goal-custom"))
}
