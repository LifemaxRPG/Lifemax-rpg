package com.lifemax.rpg.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.android.resources.Density
import com.lifemax.rpg.core.designsystem.form.FieldKind
import com.lifemax.rpg.core.designsystem.form.FieldValue
import com.lifemax.rpg.core.designsystem.form.FormField
import com.lifemax.rpg.core.designsystem.form.FormFold
import com.lifemax.rpg.core.designsystem.form.FormOption
import com.lifemax.rpg.core.designsystem.form.FormSchema
import com.lifemax.rpg.core.designsystem.form.SchemaForm
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette
import org.junit.Rule
import org.junit.Test

/**
 * The form kit (4B) on Life Director's sheet, every field kind drawn through SchemaForm as the
 * page's own forms have them (ruleSheet and goalForm in logic.js): empty, filled, refused with the
 * page's words, and disabled, then whole forms. Time, date and number fields are their own images
 * ([pickers]), re-recorded when the real pickers replace the stand-ins. Where the page has no words
 * for a refused field (a choice, a check, a typed number), the images use a stand-in sentence, only
 * to show where the words go.
 */
class DirectorFormKitScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(density = Density(420)),
            renderingMode = SessionParams.RenderingMode.SHRINK,
            useDeviceResolution = true,
            maxPercentDifference = 0.001,
        )

    private fun snap(name: String, content: @Composable () -> Unit) =
        paparazzi.snapshot(name) {
            Box(
                Modifier.width(411.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(DirectorPalette.sheetTop, DirectorPalette.sheetBottom)
                        )
                    )
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                content()
            }
        }

    /**
     * [field] four times: as the page leaves it ([empty]), filled ([filled]), refused, and
     * disabled.
     */
    private fun states(name: String, field: FormField, empty: FieldValue, filled: FieldValue) =
        snap(name) {
            Column(Modifier.fillMaxWidth()) {
                State("empty") { One(field, empty) }
                State("filled") { One(field, filled) }
                State("error") { One(field, empty, error = field.required) }
                State("disabled") { One(field, filled, enabled = false) }
            }
        }

    @Test
    fun text() =
        states(
            "text",
            FormField(
                "gTitle",
                FieldKind.TEXT,
                "Name",
                FieldValue.Text(""),
                maxLength = 50,
                placeholder = "e.g. Learn guitar",
                required = "Give the goal a name",
            ),
            FieldValue.Text(""),
            FieldValue.Text("Read More"),
        )

    @Test
    fun choice() =
        states(
            "choice",
            FormField(
                "gLevel",
                FieldKind.CHOICE,
                "Experience",
                FieldValue.Choice("NOVICE"),
                options =
                    listOf(
                        FormOption("NOVICE", "New to this"),
                        FormOption("INTERMEDIATE", "Some experience"),
                        FormOption("ADVANCED", "Experienced"),
                    ),
                required = "Pick one",
            ),
            FieldValue.Choice(""),
            FieldValue.Choice("INTERMEDIATE"),
        )

    @Test
    fun choiceMenu() =
        states(
            "choice-menu",
            FormField(
                "gTag",
                FieldKind.CHOICE,
                "Activity",
                FieldValue.Choice("fitness.strength"),
                options = TAGS,
                required = "Pick one",
            ),
            FieldValue.Choice(""),
            FieldValue.Choice("reading"),
        )

    @Test
    fun check() =
        states(
            "check",
            FormField(
                "rRot",
                FieldKind.CHECK,
                "Rotating shift pattern (e.g. 4 on / 4 off)",
                FieldValue.Check(false),
                required = "Tick to continue",
            ),
            FieldValue.Check(false),
            FieldValue.Check(true),
        )

    @Test
    fun days() =
        states(
            "days",
            DAYS_FIELD,
            FieldValue.Days(emptySet()),
            FieldValue.Days(setOf(1, 2, 3, 4, 5)),
        )

    /**
     * The stand-ins for the pickers: re-recorded when TimeField, DateField and StepperField arrive.
     */
    @Test
    fun pickers() {
        states(
            "time",
            FormField("rStart", FieldKind.TIME, "Starts", FieldValue.Time(540)),
            FieldValue.Time(0),
            FieldValue.Time(1350),
        )
        states(
            "date",
            FormField(
                "rAnchor",
                FieldKind.DATE,
                "First day on",
                FieldValue.Date(""),
                required = "Pick the first day on",
            ),
            FieldValue.Date(""),
            FieldValue.Date("2026-11-01"),
        )
        states(
            "number",
            FormField(
                "rCommute",
                FieldKind.NUMBER,
                "Travel each way (min)",
                FieldValue.Number(0),
                min = 0,
                max = 240,
                step = 5,
            ),
            FieldValue.Number(0),
            FieldValue.Number(25),
        )
        states(
            "number-typed",
            FormField(
                "gUnits",
                FieldKind.NUMBER,
                "Total",
                FieldValue.Number(null),
                placeholder = "3600",
                required = "Enter a total",
            ),
            FieldValue.Number(null),
            FieldValue.Number(3600),
        )
    }

    /** The rule sheet as the page draws it, a fixed shift; then rotating with its date refused. */
    @Test
    fun ruleForm() {
        snap("rule") { SchemaForm(RULE, RULE.initialValues(), { _, _ -> }) }
        snap("rule-rotating") {
            SchemaForm(
                RULE,
                RULE.initialValues() +
                    mapOf("rRot" to FieldValue.Check(true), "rAnchor" to FieldValue.Date("")),
                { _, _ -> },
                errors = mapOf("rAnchor" to "Pick the first day on"),
            )
        }
    }

    /** The goal form with its target folded away, then opened by a refused field inside it. */
    @Test
    fun goalForm() {
        snap("goal") { SchemaForm(GOAL, GOAL.initialValues(), { _, _ -> }) }
        snap("goal-target") {
            SchemaForm(
                GOAL,
                GOAL.initialValues(),
                { _, _ -> },
                errors = mapOf("gUnits" to "Enter a total"),
            )
        }
    }
}

@Composable
private fun State(caption: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(bottom = 10.dp)) {
        Text(caption, color = DirectorPalette.ink3.copy(alpha = .6f), fontSize = 10.sp)
        content()
    }
}

@Composable
private fun One(
    field: FormField,
    value: FieldValue,
    error: String? = null,
    enabled: Boolean = true,
) {
    val schema = FormSchema(listOf(field))
    SchemaForm(
        schema,
        mapOf(field.key to value),
        { _, _ -> },
        errors = error?.let { mapOf(field.key to it) } ?: emptyMap(),
        enabled = enabled,
    )
}

private val TAGS =
    listOf(
            "fitness.strength" to "Strength training",
            "fitness.cardio" to "Cardio",
            "fitness.mobility" to "Mobility / yoga",
            "study.deep" to "Study",
            "reading" to "Reading",
            "language" to "Language",
        )
        .map { (v, l) -> FormOption(v, l) }

private val DAYS_FIELD =
    FormField(
        "rDays",
        FieldKind.DAYS,
        "Days",
        FieldValue.Days(setOf(1, 2, 3, 4, 5)),
        options =
            listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa").mapIndexed { k, l ->
                FormOption(k.toString(), l)
            },
        required = "Pick at least one day",
    )

private fun select(
    key: String,
    label: String,
    value: String,
    vararg options: Pair<String, String>,
) =
    FormField(
        key,
        FieldKind.CHOICE,
        label,
        FieldValue.Choice(value),
        options = options.map { (v, l) -> FormOption(v, l) },
    )

private val RULE =
    FormSchema(
        listOf(
            FormField("rTitle", FieldKind.TEXT, "Name", FieldValue.Text("Work"), maxLength = 40),
            select(
                "rKind",
                "Type",
                "WORK",
                "WORK" to "Work shift",
                "CLASS" to "School / class",
                "APPOINTMENT" to "Regular commitment",
                "UNAVAILABLE" to "Unavailable (personal)",
            ),
            FormField(
                "rRot",
                FieldKind.CHECK,
                "Rotating shift pattern (e.g. 4 on / 4 off)",
                FieldValue.Check(false),
                reveals = listOf("rOn", "rOff", "rAnchor"),
                hides = listOf("rDays"),
            ),
            DAYS_FIELD,
            FormField(
                "rOn",
                FieldKind.NUMBER,
                "Days on",
                FieldValue.Number(4),
                min = 1,
                max = 14,
                group = "rot",
            ),
            FormField(
                "rOff",
                FieldKind.NUMBER,
                "Days off",
                FieldValue.Number(4),
                min = 1,
                max = 14,
                group = "rot",
            ),
            FormField(
                "rAnchor",
                FieldKind.DATE,
                "First day on",
                FieldValue.Date("2026-11-01"),
                group = "rot",
                required = "Pick the first day on",
            ),
            FormField("rStart", FieldKind.TIME, "Starts", FieldValue.Time(540), group = "times"),
            FormField("rEnd", FieldKind.TIME, "Ends", FieldValue.Time(1020), group = "times"),
            FormField(
                "rCommute",
                FieldKind.NUMBER,
                "Travel each way (min)",
                FieldValue.Number(0),
                min = 0,
                max = 240,
                step = 5,
                group = "travel",
            ),
            FormField(
                "rUnwind",
                FieldKind.NUMBER,
                "Unwind after (min)",
                FieldValue.Number(30),
                min = 0,
                max = 120,
                step = 5,
                group = "travel",
            ),
            select(
                    "rLoad",
                    "How tiring",
                    "MIXED_MODERATE",
                    "MIXED_MODERATE" to "Mixed",
                    "PHYSICAL_HEAVY" to "Physically hard",
                    "COGNITIVE_HEAVY" to "Mentally hard",
                    "LIGHT" to "Light",
                )
                .copy(
                    note =
                        "Overnight shifts are fine: an end time earlier than the start runs into the next morning. " +
                            "Tiring shifts keep heavy sessions away from the hours after them."
                ),
        )
    )

private val GOAL =
    FormSchema(
        listOf(
            FormField(
                "gTitle",
                FieldKind.TEXT,
                "Name",
                FieldValue.Text("Read more"),
                maxLength = 50,
                placeholder = "e.g. Learn guitar",
                required = "Give the goal a name",
            ),
            select(
                    "gPrio",
                    "Priority",
                    "3",
                    "5" to "Top",
                    "4" to "High",
                    "3" to "Medium",
                    "2" to "Light",
                    "1" to "Low",
                )
                .copy(group = "a"),
            select(
                    "gLevel",
                    "Experience",
                    "NOVICE",
                    "NOVICE" to "New to this",
                    "INTERMEDIATE" to "Some experience",
                    "ADVANCED" to "Experienced",
                )
                .copy(group = "a"),
        ),
        FormFold(
            "Add a measurable target (optional)",
            listOf(
                FormField(
                    "gUnits",
                    FieldKind.NUMBER,
                    "Total",
                    FieldValue.Number(null),
                    placeholder = "3600",
                    group = "t1",
                ),
                FormField(
                    "gUnit",
                    FieldKind.TEXT,
                    "Unit",
                    FieldValue.Text(""),
                    placeholder = "pages",
                    group = "t1",
                ),
                FormField(
                    "gRate",
                    FieldKind.NUMBER,
                    "Per hour",
                    FieldValue.Number(null),
                    placeholder = "30",
                    group = "t2",
                ),
                FormField(
                    "gDeadline",
                    FieldKind.DATE,
                    "By",
                    FieldValue.Date(""),
                    group = "t2",
                    note =
                        "Life Director paces sessions to hit the date, and tells you honestly if the date is not realistic.",
                ),
            ),
        ),
    )
