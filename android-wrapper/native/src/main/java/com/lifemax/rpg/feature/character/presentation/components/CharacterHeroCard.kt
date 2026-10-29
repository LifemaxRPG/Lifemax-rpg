package com.lifemax.rpg.feature.character.presentation.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.feature.character.domain.*
import kotlin.math.*

@Composable
internal fun CharacterHeroCard(p: CharacterProjection, onAction: (String, String) -> Unit) {
    CharacterCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(152.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(p.tokenColor("line"), style = Stroke(5.dp.toPx()))
                    drawArc(
                        Brush.linearGradient(listOf(Color(0xff7ab0ff), Color(0xffc58bff))),
                        -90f,
                        360f * (p.pct / 100).coerceIn(0f, 1f),
                        false,
                        style = Stroke(5.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
                val avatar =
                    remember(p.avatar) {
                        runCatching {
                                if (
                                    p.avatar.startsWith("data:image/") &&
                                        p.avatar.length < 2_000_000
                                ) {
                                    val bytes =
                                        Base64.decode(p.avatar.substringAfter(','), Base64.DEFAULT)
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        ?.asImageBitmap()
                                } else null
                            }
                            .getOrNull()
                    }
                if (avatar != null)
                    Image(
                        avatar,
                        "Character profile picture",
                        Modifier.size(118.dp).clip(CircleShape),
                    )
                else
                    Text(p.name.take(1).uppercase(), fontSize = 38.sp, fontWeight = FontWeight.Bold)
                Text(
                    "LV ${p.level}",
                    Modifier.align(Alignment.BottomCenter)
                        .clip(CircleShape)
                        .background(p.tokenColor("accent"))
                        .padding(horizontal = 13.dp, vertical = 3.dp),
                    color = Color(0xff0b0e16),
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(p.name, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(p.title, fontSize = 11.sp, color = p.tokenColor("muted"))
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(p.xp, Modifier.weight(1f), fontSize = 12.sp)
                Text(p.nextLevel, fontSize = 12.sp, color = p.tokenColor("muted"))
            }
            CharacterProgress(p.pct, p.tokenColor("accent"))
            Text(
                p.nextTitle,
                fontSize = 11.sp,
                color = p.tokenColor("muted"),
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                for ((label, value) in
                    listOf(
                        "STREAK" to p.streak,
                        "TOTAL XP" to p.totalXP,
                        "THIS WEEK" to p.week,
                        "LUCK" to p.luck,
                    )) {
                    Column(
                        Modifier.clickable(enabled = label == "LUCK") { onAction("luck", "") },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(label, fontSize = 9.sp, color = p.tokenColor("muted"))
                        Text(value, fontWeight = FontWeight.Bold)
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = p.tokenColor("line"))
            Text(
                "WEIGHT  ${p.weight}     ·     BMI  ${p.bmi}",
                fontSize = 12.sp,
                color = p.tokenColor("muted"),
            )
        }
    }
}
