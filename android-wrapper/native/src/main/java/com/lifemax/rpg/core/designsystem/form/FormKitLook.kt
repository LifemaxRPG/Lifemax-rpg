package com.lifemax.rpg.core.designsystem.form

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The form kit's colours and type. The defaults are Life Director's form controls (style.css:
 * .ld-root label, its inputs and selects, .ld-seg, .ld-dchip, .ld-check), the only forms drawn with
 * the kit so far; another tab provides its own through [LocalFormKitLook], as SheetLook does for
 * sheets.
 */
data class FormKitLook(
    /** .ld-root's body text: 15 px, line height 1.42, tabular figures. */
    val text: TextStyle =
        TextStyle(
            fontSize = 15.sp,
            lineHeight = 1.42.em,
            letterSpacing = (-.005).em,
            fontFeatureSettings = "tnum",
            lineHeightStyle =
                LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        ),
    val ink: Color = Color(0xFFEFF4FF),
    val ink2: Color = Color(0xFFB6C2D9),
    val ink3: Color = Color(0xFF8592AA),
    /** The input's well (#0b1019) and its edge (--line2); the segmented control's edge (--line). */
    val well: Color = Color(0xFF0B1019),
    val edge: Color = Color.White.copy(alpha = .13f),
    val segEdge: Color = Color.White.copy(alpha = .07f),
    /** A focused input's edge (--accent) and the 3 px ring around it. */
    val focus: Color = Color(0xFF5B9BFF),
    val focusRing: Color = Color(0xFF5B9BFF).copy(alpha = .2f),
    /** The picked segment (.ld-seg button.on): a blue wash with a blue inner edge. */
    val segOn: Color = Color(0xFF3B82F6).copy(alpha = .18f),
    val segOnEdge: Color = Color(0xFF3B82F6).copy(alpha = .4f),
    /** A day chip off (a white wash) and on (--task, with dark words). */
    val chipFill: Color = Color.White.copy(alpha = .04f),
    val on: Color = Color(0xFF10B981),
    val onInk: Color = Color(0xFF04150F),
    /** A field the page refused: its edge and its words (.ld-danger's red). */
    val error: Color = Color(0xFFFF9B9B),
    val cursor: Color = Color(0xFF86B4FF),
    /** A control that cannot be used, as the page dims a button it disables. */
    val disabledAlpha: Float = .45f,
)

/** The page's input box: 44 px tall, 11 px round (html body .ld-root.ld-root input). */
object FormKitSize {
    val fieldHeight = 44.dp
    val fieldRadius = 11.dp
}

val LocalFormKitLook = staticCompositionLocalOf { FormKitLook() }

/** A field's label (.ld-root label, .ld-lbl): 13 px, semibold, in the second ink. */
@Composable
fun FormKitLabel(text: String, modifier: Modifier = Modifier) {
    val look = LocalFormKitLook.current
    Text(
        text,
        modifier,
        style = look.text,
        color = look.ink2,
        fontSize = 13.1.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

/** The page's words for a field it refused, under the field; read out as soon as it shows. */
@Composable
fun FormKitError(text: String, modifier: Modifier = Modifier) {
    val look = LocalFormKitLook.current
    Text(
        text,
        modifier.padding(top = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
        style = look.text,
        color = look.error,
        fontSize = 13.1.sp,
        fontWeight = FontWeight.Medium,
    )
}

/** A muted line under a field (.ld-muted): 13.8 px in the third ink. */
@Composable
fun FormKitNote(text: String, modifier: Modifier = Modifier) {
    val look = LocalFormKitLook.current
    Text(
        text,
        modifier.padding(top = 6.dp),
        style = look.text,
        color = look.ink3,
        fontSize = 13.8.sp,
        lineHeight = 1.5.em,
    )
}

/**
 * The same look for the picker fields (TimeField, DateField, StepperField), which take theirs from
 * [LocalPickerFieldLook]: a form drawn by [SchemaForm] gives its pickers the kit's dark wells, so
 * they match the text fields beside them instead of falling back to Material's light colours.
 */
fun FormKitLook.pickers(): PickerFieldLook =
    PickerFieldLook(
        label = ink2,
        ink = ink,
        fill = well,
        border = edge,
        accent = focus,
        onAccent = Color.White,
        button = Color.White.copy(alpha = .08f),
        panel = Color(0xFF131A26),
        scrim = Color.Black.copy(alpha = .55f),
        muted = ink3,
    )
