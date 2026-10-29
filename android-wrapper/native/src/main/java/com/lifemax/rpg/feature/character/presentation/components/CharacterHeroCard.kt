package com.lifemax.rpg.feature.character.presentation.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.feature.character.domain.*

@Composable
internal fun CharacterAvatar(p: CharacterProjection, modifier: Modifier, initialsSize: Int = 38) {
    val avatar =
        remember(p.avatar) {
            runCatching {
                    if (p.avatar.startsWith("data:image/") && p.avatar.length < 2_000_000) {
                        val bytes = Base64.decode(p.avatar.substringAfter(','), Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    } else null
                }
                .getOrNull()
        }
    Box(modifier.background(p.tokenColor("panel2")), contentAlignment = Alignment.Center) {
        if (avatar != null)
            Image(
                avatar,
                "Character profile picture",
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        else
            Text(
                p.name.take(1).uppercase(),
                fontSize = initialsSize.sp,
                color = p.tokenColor("muted"),
                fontWeight = FontWeight.Bold,
            )
    }
}

@Composable
internal fun CharacterHeroCard(p: CharacterProjection, onAction: (String, String) -> Unit) {
    val panel = p.tokenColor("panel")
    val accent = p.tokenColor("accent")
    CharacterCard(padding = PaddingValues(0.dp)) {
        Column(
            Modifier.fillMaxWidth()
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            listOf(accent.copy(alpha = .24f), Color.Transparent),
                            Offset(size.width / 2, -size.height * .08f),
                            size.width * .82f,
                        )
                    )
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, panel.copy(alpha = .8f)),
                            startY = size.height * .60f,
                        )
                    )
                }
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(152.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val inset = size.width * .05f
                    drawCircle(
                        p.tokenColor("line"),
                        radius = size.width * .45f,
                        style = Stroke(5.7.dp.toPx()),
                    )
                    drawArc(
                        Brush.linearGradient(listOf(Color(0xff7ab0ff), Color(0xffc58bff))),
                        -90f,
                        360f * (p.pct / 100).coerceIn(0f, 1f),
                        false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2),
                        style = Stroke(5.7.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
                CharacterAvatar(
                    p,
                    Modifier.size(118.dp).clip(CircleShape).border(3.dp, panel, CircleShape),
                )
                Text(
                    "LV ${p.level}",
                    Modifier.align(Alignment.BottomCenter)
                        .offset(y = 7.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(Color(0xff8fb0ff), Color(0xffc58bff)))
                        )
                        .padding(horizontal = 13.dp, vertical = 3.dp),
                    fontSize = 12.sp,
                    letterSpacing = .96.sp,
                    color = Color(0xff0b0e16),
                    fontWeight = FontWeight.Black,
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                p.name,
                fontSize = 24.sp,
                letterSpacing = (-.72).sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                p.title,
                fontSize = 11.sp,
                letterSpacing = 2.42.sp,
                color = accent,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Column(Modifier.widthIn(max = 330.dp).fillMaxWidth().padding(top = 14.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        p.xp,
                        Modifier.weight(1f),
                        fontSize = 11.5.sp,
                        color = p.tokenColor("text").copy(alpha = .8f),
                    )
                    Text(
                        p.nextLevel,
                        fontSize = 11.5.sp,
                        color = p.tokenColor("text").copy(alpha = .8f),
                    )
                }
                CharacterProgress(p.pct, accent)
                Text(
                    p.nextTitle,
                    fontSize = 11.5.sp,
                    color = p.tokenColor("muted"),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for ((label, value) in
                    listOf(
                        "STREAK" to p.streak,
                        "TOTAL XP" to p.totalXP,
                        "THIS WEEK" to
                            p.week.removePrefix("+").let { if (it == "0") it else p.week },
                        "LUCK" to p.luck,
                    )) {
                    Column(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(13.dp))
                            .background(p.tokenColor("panel2").copy(alpha = .86f))
                            .border(
                                1.dp,
                                p.tokenColor("line").copy(alpha = .5f),
                                RoundedCornerShape(13.dp),
                            )
                            .clickable(enabled = label == "LUCK") { onAction("luck", "") }
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            label,
                            fontSize = 8.5.sp,
                            letterSpacing = 1.19.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = p.tokenColor("muted"),
                            maxLines = 1,
                        )
                        Text(
                            value,
                            Modifier.padding(top = 4.dp),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            color =
                                if (label == "THIS WEEK" && p.week != "+0") p.tokenColor("good")
                                else if (label == "LUCK") p.tokenColor("muted")
                                else p.tokenColor("text"),
                        )
                    }
                }
            }
            HorizontalDivider(
                Modifier.padding(top = 14.dp, bottom = 13.dp),
                color = p.tokenColor("line").copy(alpha = .5f),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("WEIGHT", fontSize = 9.sp, letterSpacing = 1.sp, color = p.tokenColor("muted"))
                Text(p.weight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Box(Modifier.width(1.dp).height(16.dp).background(p.tokenColor("line")))
                Text("BMI", fontSize = 9.sp, letterSpacing = 1.sp, color = p.tokenColor("muted"))
                Text(p.bmi, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
