package com.lifemax.rpg.feature.character.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.tokenColor
import com.lifemax.rpg.core.designsystem.form.FormKitError
import com.lifemax.rpg.core.designsystem.form.FormKitLook
import com.lifemax.rpg.core.designsystem.form.FormTextField
import com.lifemax.rpg.core.designsystem.form.LocalFormKitLook
import com.lifemax.rpg.feature.character.domain.CharacterProjection
import com.lifemax.rpg.feature.character.presentation.CharacterSheet

/** What the sheets ask of the screen's actions. */
class CharacterSheetActions(
    val close: () -> Unit = {},
    val retryLuck: () -> Unit = {},
    val change: (name: String?, value: String?) -> Unit = { _, _ -> },
    val save: () -> Unit = {},
    val edit: (Int) -> Unit = {},
    val cancelEdit: () -> Unit = {},
    val remove: (Int) -> Unit = {},
)

/**
 * The sheet on show over Character, with the pane dimmed around it: rising from the bottom above
 * the shared bottom bar ([bottomInset]) and never over the header ([topInset]). A tap on the dimmed
 * pane or Back closes it, unless the page is answering.
 */
@Composable
fun BoxScope.CharacterSheets(
    p: CharacterProjection,
    sheet: CharacterSheet,
    visible: Boolean,
    topInset: Dp,
    bottomInset: Dp,
    actions: CharacterSheetActions,
) {
    val busy =
        when (sheet) {
            is CharacterSheet.Luck -> sheet.pending
            is CharacterSheet.Benchmarks -> sheet.pending
        }
    val heading =
        when (sheet) {
            is CharacterSheet.Luck -> p.words.luck.heading
            is CharacterSheet.Benchmarks -> p.words.benchmarks.heading
        }
    val close = { if (!busy) actions.close() }
    BackHandler(enabled = visible) { close() }
    Box(
        Modifier.matchParentSize()
            .background(Scrim)
            .clearAndSetSemantics {}
            .pointerInput(busy) { detectTapGestures { close() } }
    )
    val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    val edge = tokenColor(p.tokens, "line")
    Box(
        Modifier.align(Alignment.BottomCenter)
            .padding(top = topInset + 8.dp, bottom = bottomInset)
            .imePadding()
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(tokenColor(p.tokens, "panel"))
            .border(1.dp, edge, shape)
            .pointerInput(Unit) { detectTapGestures {} }
            .semantics { paneTitle = heading }
    ) {
        CompositionLocalProvider(LocalFormKitLook provides remember(p.tokens) { formLook(p) }) {
            Column(
                Modifier.verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SheetHead(heading, closeLabel(p, sheet), !busy, actions.close)
                when (sheet) {
                    is CharacterSheet.Luck -> LuckBody(p, sheet, actions)
                    is CharacterSheet.Benchmarks -> BenchmarkBody(p, sheet, actions)
                }
            }
        }
    }
}

private fun closeLabel(p: CharacterProjection, sheet: CharacterSheet) =
    when (sheet) {
        is CharacterSheet.Luck -> p.words.luck.close
        is CharacterSheet.Benchmarks -> p.words.benchmarks.close
    }

@Composable
private fun SheetHead(heading: String, close: String, enabled: Boolean, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            heading,
            Modifier.weight(1f).semantics { heading() },
            fontSize = 17.sp,
            fontWeight = FontWeight(650),
            lineHeight = 22.sp,
        )
        TextButton(onClick = onClose, enabled = enabled) { Text(close) }
    }
}

// ---- Luck ----

@Composable
private fun LuckBody(
    p: CharacterProjection,
    sheet: CharacterSheet.Luck,
    actions: CharacterSheetActions,
) {
    val words = p.words.luck
    val roll = sheet.roll
    if (roll == null) {
        if (sheet.error.isNotEmpty()) {
            FormKitError(sheet.error)
            Button(onClick = actions.retryLuck, modifier = Modifier.fillMaxWidth()) {
                Text(words.retry)
            }
        } else {
            Text(
                words.rolling,
                Modifier.fillMaxWidth().padding(vertical = 28.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val tier = Color(roll.color.toInt())
    // the page's table is black whatever the theme: white dice and a tier colour read on it only
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Table)
            .padding(horizontal = 12.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { roll.dice.forEach { Die(it) } }
        Text(
            roll.score,
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            color = tier,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            roll.tier.uppercase(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp,
            color = tier,
        )
        if (roll.message.isNotEmpty())
            Text(roll.message, textAlign = TextAlign.Center, color = Color.White)
    }
    if (sheet.notice.isNotEmpty()) Notice(sheet.notice)
}

/** An ordinary white die with black pips, as the page's table has them. */
@Composable
private fun Die(face: Int) {
    Canvas(Modifier.size(52.dp).semantics { contentDescription = "Die showing $face" }) {
        drawRoundRect(Color.White, cornerRadius = CornerRadius(size.width * .16f))
        val r = size.width * .075f
        for ((cx, cy) in pips(face)) {
            drawCircle(Color(0xFF111111), r, Offset(size.width * cx, size.height * cy))
        }
    }
}

/** Pip centres on a die, as fractions of its side. */
internal fun pips(face: Int): List<Pair<Float, Float>> {
    val a = .27f
    val m = .5f
    val z = .73f
    return when (face) {
        1 -> listOf(m to m)
        2 -> listOf(a to a, z to z)
        3 -> listOf(a to a, m to m, z to z)
        4 -> listOf(a to a, z to a, a to z, z to z)
        5 -> listOf(a to a, z to a, m to m, a to z, z to z)
        else -> listOf(a to a, z to a, a to m, z to m, a to z, z to z)
    }
}

// ---- benchmarks ----

@Composable
private fun BenchmarkBody(
    p: CharacterProjection,
    sheet: CharacterSheet.Benchmarks,
    actions: CharacterSheetActions,
) {
    val words = p.words.benchmarks
    val enabled = !sheet.pending
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        p.benchmarks.forEachIndexed { i, b ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(b.name, fontWeight = FontWeight.SemiBold)
                    Text(b.value, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { actions.edit(i) }, enabled = enabled) { Text(words.edit) }
                TextButton(onClick = { actions.remove(i) }, enabled = enabled) {
                    Text(words.remove)
                }
            }
        }
        if (p.benchmarks.isEmpty())
            Text("No benchmarks yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    FormTextField(
        words.nameLabel,
        sheet.name,
        { actions.change(it, null) },
        enabled = enabled && sheet.editing == null,
        placeholder = words.namePlaceholder.ifEmpty { null },
        maxLength = p.limits.name,
    )
    FormTextField(
        words.valueLabel,
        sheet.value,
        { actions.change(null, it) },
        enabled = enabled,
        placeholder = words.valuePlaceholder.ifEmpty { null },
        maxLength = p.limits.value,
    )
    if (sheet.error.isNotEmpty()) FormKitError(sheet.error)
    if (sheet.notice.isNotEmpty()) Notice(sheet.notice)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = actions.save,
            enabled = enabled,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(),
            modifier = Modifier.alpha(if (enabled) 1f else .6f),
        ) {
            Text(if (sheet.editing == null) words.add else words.save)
        }
        if (sheet.editing != null)
            OutlinedButton(
                onClick = actions.cancelEdit,
                enabled = enabled,
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(words.cancel)
            }
    }
}

@Composable
private fun Notice(text: String) =
    Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

/**
 * The page's .field inputs in Character's colours: its background as the well, its line as the
 * edge.
 */
private fun formLook(p: CharacterProjection) =
    FormKitLook(
        ink = tokenColor(p.tokens, "text"),
        ink2 = tokenColor(p.tokens, "muted"),
        ink3 = tokenColor(p.tokens, "muted2"),
        well = tokenColor(p.tokens, "bg"),
        edge = tokenColor(p.tokens, "line"),
        segEdge = tokenColor(p.tokens, "line"),
        focus = tokenColor(p.tokens, "accent"),
        focusRing = tokenColor(p.tokens, "accent-dim"),
        error = tokenColor(p.tokens, "bad"),
        cursor = tokenColor(p.tokens, "accent"),
    )

/** The black table Daily Luck is rolled on. */
private val Table = Color(0xFF05070C)

/** The pane dimmed behind a sheet. */
private val Scrim = Color.Black.copy(alpha = .55f)
