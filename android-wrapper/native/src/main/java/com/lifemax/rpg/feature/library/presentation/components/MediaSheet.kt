package com.lifemax.rpg.feature.library.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.form.ChoiceField
import com.lifemax.rpg.core.designsystem.form.FormKitError
import com.lifemax.rpg.core.designsystem.form.FormKitLook
import com.lifemax.rpg.core.designsystem.form.FormOption
import com.lifemax.rpg.core.designsystem.form.FormTextField
import com.lifemax.rpg.core.designsystem.form.LocalFormKitLook
import com.lifemax.rpg.feature.library.domain.FilterOption
import com.lifemax.rpg.feature.library.domain.MediaField
import com.lifemax.rpg.feature.library.domain.MediaForm
import com.lifemax.rpg.feature.library.domain.MediaTextField
import com.lifemax.rpg.feature.library.presentation.MediaSheetUi

/** What the add/edit sheet can ask for. */
class MediaSheetActions(
    val change: (MediaField, String) -> Unit = { _, _ -> },
    val save: () -> Unit = {},
    val cancel: () -> Unit = {},
)

/**
 * The page's add/edit form (#mediaFormCard) as a sheet over the pane: the pane dimmed around it,
 * the card rising from the bottom above the shared bottom bar ([bottomInset]) and never over the
 * header ([topInset]). Its heading and Cancel, then the page's six fields one under another as the
 * page lays them out on a phone, each with the page's label, placeholder and length, and the page's
 * words under a field it refused; then Save and Cancel. While the page is answering ([pending])
 * nothing can be changed and Save is faded. A tap on the dimmed pane or Back is Cancel, as the
 * page's Cancel; a tap on the card stays there. [scroll] is how far down the card is scrolled.
 */
@Composable
internal fun BoxScope.MediaSheet(
    form: MediaForm,
    sheet: MediaSheetUi,
    visible: Boolean,
    topInset: Dp,
    bottomInset: Dp,
    actions: MediaSheetActions,
    scroll: ScrollState = rememberScrollState(),
) {
    val c = LocalLibraryColors.current
    BackHandler(enabled = visible) { actions.cancel() }
    Box(
        Modifier.matchParentSize()
            .background(Scrim)
            .clearAndSetSemantics {}
            .pointerInput(Unit) { detectTapGestures { actions.cancel() } }
    )
    val heading = if (sheet.id == null) form.addHeading else form.editHeading
    val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    Box(
        Modifier.align(Alignment.BottomCenter)
            .padding(top = topInset + 8.dp, bottom = bottomInset)
            .imePadding()
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(c.panel)
            .border(1.dp, c.line, shape)
            .pointerInput(Unit) { detectTapGestures {} }
            .semantics { paneTitle = heading }
    ) {
        CompositionLocalProvider(LocalFormKitLook provides remember(c) { formLook(c) }) {
            Column(
                Modifier.verticalScroll(scroll)
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SheetHead(heading, form.cancel, actions.cancel)
                SheetFields(form, sheet, actions.change)
                if (sheet.error.isNotEmpty()) FormKitError(sheet.error)
                // the accent button has no border, so it is 2 dp shorter: the two are centred
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LibraryButton(
                        form.save,
                        actions.save,
                        Modifier.alpha(if (sheet.pending) PENDING_ALPHA else 1f),
                        primary = true,
                    )
                    LibraryButton(form.cancel, actions.cancel)
                }
            }
        }
    }
}

/** The heading (the page's h3) and the small Cancel beside it. */
@Composable
private fun SheetHead(heading: String, cancel: String, onCancel: () -> Unit) {
    val c = LocalLibraryColors.current
    Row(
        Modifier.fillMaxWidth().padding(bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            heading,
            Modifier.weight(1f).semantics { heading() },
            color = c.text,
            fontSize = 17.sp,
            fontWeight = FontWeight(650),
            lineHeight = 22.sp,
        )
        LibraryButton(cancel, onCancel, size = ButtonSize.SMALL)
    }
}

@Composable
private fun SheetFields(
    form: MediaForm,
    sheet: MediaSheetUi,
    change: (MediaField, String) -> Unit,
) {
    val enabled = !sheet.pending
    @Composable
    fun text(field: MediaField, box: MediaTextField, lines: Int = 1) = Column {
        FormTextField(
            box.label,
            sheet.draft[field],
            { change(field, it) },
            enabled = enabled,
            placeholder = box.placeholder.ifEmpty { null },
            maxLength = box.maxLength,
            error = sheet.errors[field],
            lines = lines,
        )
        // the page's words for a box it refused, under it
        sheet.errors[field]?.let { FormKitError(it) }
    }
    @Composable
    fun choice(field: MediaField, label: String, options: List<FilterOption>) =
        ChoiceField(
            label,
            options.map { FormOption(it.id, it.label) },
            sheet.draft[field],
            { change(field, it) },
            enabled = enabled,
            error = sheet.errors[field],
            // the page's select, whatever the number of options
            menu = true,
        )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        text(MediaField.TITLE, form.title)
        choice(MediaField.TYPE, form.type.label, sheet.typeOptions)
        text(MediaField.CREATOR, form.creator)
        choice(MediaField.STATUS, form.status.label, form.status.options)
        text(MediaField.LINK, form.link)
        text(MediaField.NOTES, form.notes, lines = NOTES_LINES)
    }
}

/**
 * The page's .field inputs in the Library's colours: the page's background as the well, its line as
 * the edge, the accent edge and its dim ring on focus, and the page's red for a refusal.
 */
private fun formLook(c: LibraryColors) =
    FormKitLook(
        ink = c.text,
        ink2 = c.muted,
        ink3 = c.muted2,
        well = c.field,
        edge = c.line,
        segEdge = c.line,
        focus = c.accent,
        focusRing = c.accentDim,
        error = c.bad,
        cursor = c.accent,
    )

/** The page's notes box: two rows of text and its 80 px minimum, about three lines here. */
private const val NOTES_LINES = 3

/** The Save of a sheet the page is answering. */
internal const val PENDING_ALPHA = .6f

/** The pane dimmed behind a sheet or a confirm. */
internal val Scrim = Color.Black.copy(alpha = .55f)
