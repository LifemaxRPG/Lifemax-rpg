package com.lifemax.rpg.feature.lifedirector.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.feature.lifedirector.domain.BlockKind
import com.lifemax.rpg.feature.lifedirector.domain.MixShare
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette as P

/** How the day divides up: one bar, doubling as the legend for the dial's colours (mixHtml). */
@Composable
fun DayMix(shares: List<MixShare>) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = .04f)),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            for (share in shares) {
                if (share.minutes == 0) continue
                val color =
                    if (share.kind == BlockKind.FREE) P.free.copy(alpha = .35f)
                    else P.of(share.kind)
                Box(Modifier.weight(share.minutes.toFloat()).fillMaxHeight().background(color))
            }
        }
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            for (pair in shares.chunked(2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    pair.forEach { Share(it, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Share(share: MixShare, modifier: Modifier) {
    Row(
        modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(P.of(share.kind), RoundedCornerShape(3.dp)))
        Text(
            share.label,
            Modifier.weight(1f),
            style = DirectorText,
            color = P.ink3,
            fontSize = 11.8.sp,
            maxLines = 1,
        )
        Text(
            share.text,
            style = DirectorText,
            color = P.ink2,
            fontSize = 11.8.sp,
            fontWeight = FontWeight(650),
        )
    }
}
