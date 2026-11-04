package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LocalPageColors
import com.lifemax.rpg.core.designsystem.components.tapTarget
import com.lifemax.rpg.feature.pet.domain.PetPlay

/*
 * The toy tray under Play (`#petToys`, `pet.play.v1`): "Let the cat pick" and every toy the cat may
 * play with, in the page's words. A tap tosses the toy into the shared room, where a cat that wants
 * to plays with it (the native brain); the page counts the game when it ends.
 */

private val TileShape = RoundedCornerShape(12.dp)
private val FavouritePink = Color(0xFFF0718F)
private const val PER_ROW = 2
private const val FAVOURITE = "Favorite"

/**
 * The tray: its title, then the tiles two to a row. [onPick] gets a toy id, or null to let pick.
 */
@Composable
internal fun PetToyTray(tray: PetPlay, onPick: (String?) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalPageColors.current
    val tiles: List<Triple<String?, String, String>> =
        listOf(Triple<String?, String, String>(null, tray.pick, "")) +
            tray.toys.map { Triple(it.id, it.name, it.word) }
    Column(
        modifier.fillMaxWidth().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(tray.title, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        tiles.chunked(PER_ROW).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (id, name, word) ->
                    ToyTile(name, word, Modifier.weight(1f)) { onPick(id) }
                }
                repeat(PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ToyTile(name: String, word: String, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalPageColors.current
    val favourite = word == FAVOURITE
    Column(
        modifier
            .clip(TileShape)
            .background(c.panel2)
            .border(1.dp, if (favourite) FavouritePink else c.lineSoft, TileShape)
            .semantics(mergeDescendants = true) { contentDescription = "$name, $word" }
            .tapTarget(Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(name, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(
            word,
            color = if (favourite) FavouritePink else c.muted,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            maxLines = 1,
        )
    }
}
