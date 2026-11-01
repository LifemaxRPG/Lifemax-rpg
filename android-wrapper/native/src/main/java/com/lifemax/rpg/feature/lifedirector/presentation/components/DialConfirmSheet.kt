package com.lifemax.rpg.feature.lifedirector.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.feature.lifedirector.domain.DialConfirm
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette as P

/**
 * The page's confirmation of a dial change (confirmDial in logic.js, the .ld-sheet panel): the
 * question, the time struck through and the new one, then either the problem and OK, or what the
 * change does with Change it, Keep and the small print. The dimmed screen around it, the close
 * button and Back all keep the time. It sits above the shared bottom bar ([bottomInset]), which a
 * native screen does not cover. A tap on the panel stays there without making the panel a button:
 * TalkBack reads its heading, words and buttons one by one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoxScope.DialConfirmSheet(
    confirm: DialConfirm,
    bottomInset: Dp,
    onApply: () -> Unit,
    onKeep: () -> Unit,
) =
    DirectorSheet(confirm.title, bottomInset, onKeep) {
        SheetTitle(confirm.title)
        Change(confirm.from, confirm.to)
        if (confirm.problem.isNotEmpty()) {
            Text(
                confirm.problem,
                Modifier.padding(vertical = 10.dp)
                    .fillMaxWidth()
                    .background(P.warnNote, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                style = DirectorText,
                color = P.amber,
                fontSize = 14.08.sp,
            )
            DirectorButton(confirm.keep, ButtonLook.PRIMARY, onKeep)
        } else {
            SheetMuted(confirm.body, 13.76f)
            FlowRow(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DirectorButton(confirm.apply, ButtonLook.PRIMARY, onApply)
                DirectorButton(confirm.keep, ButtonLook.GHOST, onKeep)
            }
            SheetMuted(confirm.note, 12.48f)
        }
    }

/**
 * The old time struck through, an arrow, the new time in bold (.ld-change). The strike and the
 * arrow are silent, so TalkBack hears "From 11:00 PM to 10:30 PM".
 */
@Composable
private fun Change(from: String, to: String) {
    Row(
        Modifier.padding(top = 14.dp, bottom = 6.dp).clearAndSetSemantics {
            contentDescription = "From $from to $to"
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val mono = DirectorText.copy(fontFamily = FontFamily.Monospace, letterSpacing = (-.03).em)
        Text(
            from,
            style = mono,
            color = P.ink3,
            fontSize = 17.6.sp,
            fontWeight = FontWeight.Medium,
            textDecoration = TextDecoration.LineThrough,
        )
        Text("→", style = mono, color = P.ink3, fontSize = 17.6.sp, fontWeight = FontWeight.Bold)
        Text(to, style = mono, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}
