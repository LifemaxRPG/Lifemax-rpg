package com.lifemax.rpg.core.designsystem.form

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * The colours of the shared form fields and their pickers. A screen with a look of its own (Life
 * Director's dark panel) provides one through [LocalPickerFieldLook]; otherwise the fields take
 * Material's colours, which LifeMax's theme sets from the page's tokens.
 */
@Immutable
data class PickerFieldLook(
    /** a field's label above it (.ld-root label) */
    val label: Color,
    /** the value's text */
    val ink: Color,
    val fill: Color,
    val border: Color,
    /** focus, the picker's selection */
    val accent: Color,
    /** the ink on [accent] */
    val onAccent: Color,
    /** a stepper's − and + */
    val button: Color,
    /** the picker's card and the screen dimmed around it */
    val panel: Color,
    val scrim: Color,
    /** the picker's quieter text (weekdays, the unselected half of the day) */
    val muted: Color,
)

val LocalPickerFieldLook = staticCompositionLocalOf<PickerFieldLook?> { null }

/** The look in force: the screen's own, or one from Material's colours. */
@Composable
fun pickerFieldLook(): PickerFieldLook =
    LocalPickerFieldLook.current
        ?: MaterialTheme.colorScheme.let {
            PickerFieldLook(
                label = it.onSurfaceVariant,
                ink = it.onSurface,
                fill = it.surface,
                border = it.outlineVariant,
                accent = it.primary,
                onAccent = it.onPrimary,
                button = it.surfaceVariant,
                panel = it.surfaceContainerHigh,
                scrim = Color.Black.copy(alpha = .6f),
                muted = it.onSurfaceVariant,
            )
        }

/** A picker a field asked for: its [label] heads the card. */
sealed interface PickerRequest {
    val label: String
}

/** A time of day in minutes after midnight, on a 12- or 24-hour clock ([h12]). */
class TimePick(
    override val label: String,
    val minutes: Int,
    val h12: Boolean,
    val onPick: (Int) -> Unit,
) : PickerRequest

/** A date as "YYYY-MM-DD". */
class DatePick(override val label: String, val value: String, val onPick: (String) -> Unit) :
    PickerRequest

/** The picker open over a [PickerHost], if any. */
@Stable
class PickerState(initial: PickerRequest? = null) {
    var request by mutableStateOf(initial)
        private set

    fun show(r: PickerRequest) {
        request = r
    }

    fun dismiss() {
        request = null
    }
}

val LocalPickerState = compositionLocalOf<PickerState?> { null }

/**
 * Where the fields inside [content] open their pickers: a card over the whole host, the rest
 * dimmed, drawn in the screen's own layer (so it is styled with the screen, can be screenshotted,
 * and Back closes it first). A field outside any host opens its picker in a dialog window instead.
 */
@Composable
fun PickerHost(
    state: PickerState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    CompositionLocalProvider(LocalPickerState provides state) {
        Box(modifier) {
            content()
            state.request?.let { r ->
                PickerBack(state::dismiss)
                Box(
                    Modifier.matchParentSize()
                        .background(pickerFieldLook().scrim)
                        .clickable(
                            remember { MutableInteractionSource() },
                            indication = null,
                            onClick = state::dismiss,
                        )
                        .clearAndSetSemantics {}
                )
                PickerCard(r, state::dismiss, Modifier.align(Alignment.Center))
            }
        }
    }
}

/** The state a field opens its picker on: the host's, or its own shown in a dialog window. */
@Composable
internal fun rememberPickerState(): PickerState {
    LocalPickerState.current?.let {
        return it
    }
    val own = remember { PickerState() }
    own.request?.let { r ->
        Dialog(onDismissRequest = own::dismiss) { PickerCard(r, own::dismiss) }
    }
    return own
}

/** Back closes the picker, where the screen has a back dispatcher (not in a screenshot). */
@Composable
private fun PickerBack(onBack: () -> Unit) {
    if (LocalOnBackPressedDispatcherOwner.current != null) BackHandler(onBack = onBack)
}

/** The card: the field's label, its picker, then Cancel and OK. */
@Composable
private fun PickerCard(r: PickerRequest, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val look = pickerFieldLook()
    val shape = RoundedCornerShape(24.dp)
    val ok =
        remember(r) {
            when (r) {
                is TimePick -> PickedValue(r.minutes)
                is DatePick -> PickedValue(r.value)
            }
        }
    Column(
        modifier
            .padding(12.dp)
            .widthIn(max = 380.dp)
            .clip(shape)
            .background(look.panel, shape)
            .border(1.dp, look.border, shape)
            .pointerInput(Unit) { detectTapGestures {} }
            .semantics { paneTitle = r.label }
            .verticalScroll(rememberScrollState())
            .padding(top = 18.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            r.label,
            Modifier.fillMaxWidth().padding(horizontal = 22.dp).semantics { heading() },
            style = LocalTextStyle.current,
            color = look.label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Box(Modifier.padding(top = 12.dp)) {
            when (r) {
                is TimePick -> TimeWheel(r, look) { ok.value = it }
                is DatePick -> DateCalendar(r, look) { ok.value = it }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
        ) {
            CardButton(CANCEL, look, onDone)
            CardButton(OK, look) {
                when (r) {
                    is TimePick -> r.onPick(ok.value as Int)
                    is DatePick -> r.onPick(ok.value as String)
                }
                onDone()
            }
        }
    }
}

/** What the picker shows now, sent only on OK. */
private class PickedValue(initial: Any) {
    var value: Any = initial
}

@Composable
private fun CardButton(label: String, look: PickerFieldLook, onClick: () -> Unit) =
    Box(
        Modifier.heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = LocalTextStyle.current,
            color = look.accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }

private const val CANCEL = "Cancel"
private const val OK = "OK"
