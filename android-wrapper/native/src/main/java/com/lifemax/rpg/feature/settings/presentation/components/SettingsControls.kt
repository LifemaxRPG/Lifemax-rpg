package com.lifemax.rpg.feature.settings.presentation.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LocalPageColors
import com.lifemax.rpg.core.designsystem.components.PageCheck
import com.lifemax.rpg.core.designsystem.components.toggleTarget
import com.lifemax.rpg.feature.settings.domain.ButtonStyle
import com.lifemax.rpg.feature.settings.domain.ControlOp
import com.lifemax.rpg.feature.settings.domain.FieldInput
import com.lifemax.rpg.feature.settings.domain.SettingsControl
import com.lifemax.rpg.feature.settings.presentation.SettingsUi
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** What a tap on a control asks for; the screen's actions answer it. */
interface ControlTaps {
    fun toggle(control: SettingsControl.Switch)

    fun choose(control: SettingsControl, value: String)

    fun press(control: SettingsControl.Button)

    fun reveal(control: SettingsControl)

    /** Text typed in a field the screen edits (a draft until its form is saved). */
    fun type(control: SettingsControl.Field, text: String) {}

    /** A time picked for a field the screen edits, in minutes after midnight. */
    fun time(control: SettingsControl.Field, minutes: Int) {}
}

/** Whether the screen shows: a hidden screen closes any open list of choices. */
internal val LocalScreenShown = compositionLocalOf { true }

/** Where a control stands, which decides how the page draws a field or a select. */
internal enum class Place {
    /** At the right of a row (.settingsline): LMA_IN selects and times, the XP rules' boxes. */
    Row,

    /** Under a form field's label (.field): a full-width input. */
    Form,

    /** Anywhere else: buttons on their own or in a note. */
    Loose,
}

/** One control of the page as the page draws it where it stands. */
@Composable
internal fun ControlView(
    control: SettingsControl,
    ui: SettingsUi,
    taps: ControlTaps,
    place: Place,
    modifier: Modifier = Modifier,
) {
    when (control) {
        is SettingsControl.Switch -> SwitchBox(control, ui, taps, modifier)
        is SettingsControl.Select -> SelectBox(control, ui, taps, place, modifier)
        is SettingsControl.Choice -> ChoiceGroup(control, ui, taps, modifier)
        is SettingsControl.Button ->
            SettingsButton(
                control.label,
                control.description,
                control.style,
                control.small,
                control.enabled,
                { taps.press(control) },
                modifier,
                pending = ui.pending(control),
            )
        is SettingsControl.Field ->
            if (control.op == ControlOp.Edit) EditFieldBox(control, ui, taps, place, modifier)
            else FieldBox(control, taps, place, modifier)
        is SettingsControl.Avatar -> AvatarView(control, taps, modifier)
    }
}

/**
 * A tick box that is its own switch, where its row holds more than the box (a bottom-bar row): the
 * box keeps its 20 dp and the page's margins, and Compose widens its touch area. TalkBack calls it
 * by the page's label.
 */
@Composable
internal fun SwitchBox(
    control: SettingsControl.Switch,
    ui: SettingsUi,
    taps: ControlTaps,
    modifier: Modifier = Modifier,
) {
    val pending = ui.pending(control)
    PageCheck(
        ui.on(control),
        modifier
            .checkMargins()
            .toggleTarget(ui.on(control), enabled = control.enabled && !pending, reserve = false) {
                taps.toggle(control)
            }
            .semantics {
                contentDescription = control.label
                switchState(control, pending, ui)?.let { stateDescription = it }
            },
        enabled = control.enabled,
        pending = pending,
    )
}

/**
 * The space the page leaves round a tick box: .check's own margin-top:1px, and on the other sides
 * the browser's checkbox margins, which .check keeps (3 dp right and below, 4 dp left).
 */
internal fun Modifier.checkMargins(): Modifier =
    padding(start = 4.dp, end = 3.dp, top = 1.dp, bottom = 3.dp)

/**
 * What TalkBack adds to a switch's on or off: that the page is turning it, or that a tap hands it
 * to the page (Android asks first), or nothing.
 */
internal fun switchState(
    control: SettingsControl.Switch,
    pending: Boolean,
    ui: SettingsUi,
): String? =
    when {
        pending -> if (ui.on(control)) "Turning on" else "Turning off"
        control.op == ControlOp.Reveal -> if (control.on) "On" else "Off, opens on the page"
        else -> null
    }

/**
 * A select. At the right of a row it is drawn with LMA_IN (panel2, a line edge, 8 dp corners, 6 by
 * 8 padding, 33 dp tall), as wide as its widest option the way a browser sizes one; under a form
 * label it is the form's full-width input. Either way its text starts 4 dp further in than the
 * padding says, as the browser draws a select ([MenuListIndent]). One the page lets the screen
 * change opens its options here; one the page keeps (a form's) hands the tap to the page. While the
 * page has not answered, it shows the option asked for, faded.
 */
@Composable
private fun SelectBox(
    control: SettingsControl.Select,
    ui: SettingsUi,
    taps: ControlTaps,
    place: Place,
    modifier: Modifier,
) {
    if (control.id == "haptics.nativeScrubStrength") {
        NativeScrubSlider(control, ui, taps, modifier)
        return
    }
    val c = LocalPageColors.current
    val ink = SettingsInk(c)
    val pending = ui.pending(control)
    val value = ui.value(control)
    val shown = control.options.firstOrNull { it.value == value }?.label ?: value
    var open by remember(control.id) { mutableStateOf(false) }
    val screenShown = LocalScreenShown.current
    val usable = control.enabled && !pending
    LaunchedEffect(screenShown, usable) { if (!screenShown || !usable) open = false }
    val form = place == Place.Form
    Box(
        modifier
            .alpha(
                when {
                    !control.enabled -> .45f
                    pending -> .6f
                    else -> 1f
                }
            )
            .inputSurface(c, if (form) ink.formField else ink.inField, 8.dp)
            .clickable(usable, role = Role.DropdownList) {
                if (control.op == ControlOp.Choose) open = true else taps.reveal(control)
            }
            .semantics {
                contentDescription = control.label
                stateDescription = shown
            }
            .padding(
                start = (if (form) 12.dp else 8.dp) + MenuListIndent + 1.dp,
                end = (if (form) 10.dp else 6.dp) + 1.dp,
                top = (if (form) 10.dp else 6.dp) + 1.dp,
                bottom = (if (form) 10.dp else 6.dp) + 1.dp,
            )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // the widest option sets the width, as a browser sizes a select; only the chosen shows
            Box(if (form) Modifier.weight(1f) else Modifier) {
                if (!form)
                    control.options.forEach {
                        InputText(it.label, c.text, Modifier.alpha(0f).clearAndSetSemantics {})
                    }
                InputText(shown, c.text, lineHeight = if (form) 21.sp else 19.sp)
            }
            Chevron(c.text, Modifier.padding(start = 6.dp))
        }
        if (open)
            DropdownMenu(open, { open = false }) {
                control.options.forEach { option ->
                    DropdownMenuItem(
                        {
                            Text(
                                option.label,
                                fontSize = 14.sp,
                                fontWeight =
                                    if (option.value == value) FontWeight.Bold
                                    else FontWeight.Normal,
                            )
                        },
                        {
                            open = false
                            taps.choose(control, option.value)
                        },
                        Modifier.semantics {
                            if (option.value == value) stateDescription = "Selected"
                        },
                    )
                }
            }
    }
}

/**
 * The space Chromium adds before a select's text, inside the page's own padding (its menu list's
 * internal start padding, which the page's selects keep: none of them sets appearance).
 */
private val MenuListIndent = 4.dp

/** A select's drop-down arrow, as the browser draws it beside the chosen option. */
@Composable
private fun Chevron(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 10.dp, height = 6.dp)) {
        val w = 1.5.dp.toPx()
        val path =
            Path().apply {
                moveTo(w / 2, w / 2)
                lineTo(size.width / 2, size.height - w / 2)
                lineTo(size.width - w / 2, w / 2)
            }
        drawPath(path, color, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun InputText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    lineHeight: TextUnit = 19.sp,
) {
    Text(
        text,
        modifier,
        color = color,
        fontSize = 14.sp,
        lineHeight = lineHeight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
    )
}

/**
 * The haptic strength: a small button per option, the chosen one the accent button and the others
 * secondary, 6 dp apart and wrapping (50-haptics.js), each as tall as its line (the page's flex row
 * stretches the borderless accent button to the secondary ones' height). TalkBack reads them as one
 * group of choices.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceGroup(
    control: SettingsControl.Choice,
    ui: SettingsUi,
    taps: ControlTaps,
    modifier: Modifier,
) {
    val pending = ui.pending(control)
    val value = ui.value(control)
    FlowRow(
        modifier.selectableGroup().semantics { contentDescription = control.label },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        control.options.forEach { option ->
            val on = option.value == value
            SettingsButton(
                option.label,
                "",
                if (on) ButtonStyle.Primary else ButtonStyle.Secondary,
                small = true,
                enabled = control.enabled,
                onClick = { taps.choose(control, option.value) },
                Modifier.fillMaxRowHeight(),
                pending = pending,
                selected = on,
            )
        }
    }
}

/**
 * Native Compose slider; commits once on release rather than flooding the WebView with commands.
 */
@Composable
private fun NativeScrubSlider(
    control: SettingsControl.Select,
    ui: SettingsUi,
    taps: ControlTaps,
    modifier: Modifier,
) {
    val c = LocalPageColors.current
    val saved = (ui.value(control).toFloatOrNull() ?: 50f).coerceIn(0f, 100f)
    var dragging by remember(control.id) { mutableStateOf(false) }
    var draft by remember(control.id) { mutableStateOf(saved) }
    LaunchedEffect(saved, dragging) { if (!dragging) draft = saved }
    val pending = ui.pending(control)
    val enabled = control.enabled && !pending && control.op == ControlOp.Choose
    Row(
        modifier.width(280.dp).alpha(if (enabled) 1f else .45f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Slider(
            value = if (dragging) draft else saved,
            onValueChange = {
                draft = it
                dragging = true
            },
            onValueChangeFinished = {
                dragging = false
                val chosen = draft.toInt().coerceIn(0, 100).toString()
                if (chosen != saved.toInt().toString()) taps.choose(control, chosen)
            },
            valueRange = 0f..100f,
            steps = 99,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${(if (dragging) draft else saved).toInt()}%",
            modifier = Modifier.padding(start = 10.dp),
            color = c.text,
            fontSize = 14.sp,
        )
    }
}

/**
 * A text, number or time field. The page edits it: a tap hands the field to the page. It shows what
 * it holds (a time as the phone's clock writes it) or its placeholder in the browser's grey. At the
 * right of a row a time is drawn with LMA_IN (37 dp tall) and a number as the XP rules' box (78 dp
 * wide, 9 dp corners); under a form label it is the form's full-width input.
 */
@Composable
private fun FieldBox(
    control: SettingsControl.Field,
    taps: ControlTaps,
    place: Place,
    modifier: Modifier,
) {
    val c = LocalPageColors.current
    val look = fieldLook(control.input, place, c)
    val shown = if (control.input == FieldInput.Time) clock(control.value) else control.value
    Box(
        modifier
            .then(if (look.rule) Modifier.width(78.dp) else Modifier)
            .alpha(if (control.enabled) 1f else .45f)
            .inputSurface(c, look.fill, look.radius)
            .clickable(control.enabled, "Edit on the page", Role.Button) { taps.reveal(control) }
            .semantics { contentDescription = "${control.label}: ${shown.ifEmpty { "empty" }}" }
            .padding(horizontal = look.horizontal + 1.dp, vertical = look.vertical + 1.dp)
    ) {
        InputText(
            shown.ifEmpty { control.placeholder },
            if (shown.isEmpty()) Placeholder else c.text,
            lineHeight = if (control.input == FieldInput.Time && !look.form) 23.sp else 21.sp,
        )
    }
}

/**
 * A time input's "HH:mm" as the phone's browser writes it: with AM or PM where the phone's locale
 * uses them, 24-hour otherwise, two digits each.
 */
internal fun clock(value: String, locale: Locale = Locale.getDefault()): String {
    val time = runCatching { LocalTime.parse(value) }.getOrNull() ?: return value
    val pattern =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(time)
    val twelve = Regex("[AaPp]\\.?\\s?[Mm]\\.?|[ap]\\.m\\.").containsMatchIn(pattern)
    return time.format(DateTimeFormatter.ofPattern(if (twelve) "hh:mm a" else "HH:mm", locale))
}

/**
 * The profile picture (.avatar, #charAvatar): a 64 dp circle with a 2 dp line edge on panel2, the
 * picture filling it or the name's initial in muted 24/700. A tap hands it to the page's picker.
 */
@Composable
private fun AvatarView(control: SettingsControl.Avatar, taps: ControlTaps, modifier: Modifier) {
    val c = LocalPageColors.current
    val picture = remember(control.image) { pictureOf(control.image) }
    Box(
        modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(c.panel2)
            .clickable(control.enabled, role = Role.Button) { taps.reveal(control) }
            .semantics { contentDescription = control.label },
        contentAlignment = Alignment.Center,
    ) {
        if (picture != null)
            Image(picture, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else
            Text(
                control.initial,
                color = c.muted,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 36.sp,
            )
        Box(Modifier.fillMaxSize().border(2.dp, c.line, CircleShape))
    }
}

/** A data URL's picture, or null for none or one the phone cannot read. */
private fun pictureOf(url: String): ImageBitmap? {
    if (!url.startsWith("data:image/")) return null
    return runCatching {
            val bytes = Base64.decode(url.substringAfter(','), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
        .getOrNull()
}

/**
 * A bottom-bar row's ◀ or ▶ (.tabrow .tb): 40 dp square, a line edge and 10 dp corners on nothing,
 * a 12 dp glyph, at .3 opacity when the tab cannot move that way. Compose widens its touch area;
 * TalkBack reads the page's "Move Quests earlier".
 */
@Composable
internal fun ArrowButton(
    control: SettingsControl.Button,
    ui: SettingsUi,
    taps: ControlTaps,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    val shape = RoundedCornerShape(10.dp)
    val pending = ui.pending(control)
    Box(
        modifier
            .size(40.dp)
            .alpha(
                when {
                    !control.enabled -> .3f
                    pending -> .6f
                    else -> 1f
                }
            )
            .clip(shape)
            .border(1.dp, c.line, shape)
            .clickable(control.enabled && !pending, role = Role.Button) { taps.press(control) }
            .semantics { contentDescription = control.description.ifEmpty { control.label } },
        contentAlignment = Alignment.Center,
    ) {
        Text(control.label, color = c.text, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

/** The gap between a form field's two inputs (Height: feet and inches). */
internal val FormGap: Dp = 8.dp
