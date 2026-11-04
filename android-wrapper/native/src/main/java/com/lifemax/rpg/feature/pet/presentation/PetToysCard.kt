package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LocalPageColors
import com.lifemax.rpg.core.designsystem.components.tapTarget
import com.lifemax.rpg.feature.pet.domain.PetPanelWords
import com.lifemax.rpg.feature.pet.domain.PetToy
import com.lifemax.rpg.feature.pet.domain.PetToys

/*
 * The toy list: every toy with the page's word for it ("Favorite", "Loves it", or what it waits
 * for: "Unlocks at Friend"), and for a toy still locked the Rewards shop's early unlock for
 * Sparks. The unlock is a command; the price, the balance and the save are the page's. Playing
 * with a toy is the tray under Play (PetToyTray, `pet.play.v1`).
 */

/** `.pet-toy.locked`: a toy the cat cannot play with yet is shown fainter. */
private const val LOCKED_ALPHA = .6f
private const val POOR_ALPHA = .55f
private val FavouritePink = androidx.compose.ui.graphics.Color(0xFFF0718F)
private val RowShape = RoundedCornerShape(12.dp)

/** The toy card: the Sparks the owner has, then a row per toy. */
@Composable
internal fun PetToysCard(
    toys: PetToys,
    words: PetPanelWords,
    waiting: Boolean,
    onBuy: (String) -> Unit,
    modifier: Modifier = Modifier,
    startOpen: Boolean = false,
) {
    val c = LocalPageColors.current
    PetCollapsibleCard(words.toys, modifier, startOpen) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(words.toysSub, color = c.muted, fontSize = 12.sp)
            Text(
                "${words.sparks} ✦ ${toys.sparks}",
                color = c.gold,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            toys.items.forEach { ToyRow(it, words, waiting, onBuy) }
        }
    }
}

@Composable
private fun ToyRow(toy: PetToy, words: PetPanelWords, waiting: Boolean, onBuy: (String) -> Unit) {
    val c = LocalPageColors.current
    val favourite = toy.unlocked && toy.word == FAVOURITE
    val offer = !toy.unlocked && !toy.bought && toy.price > 0
    val stack = panelLargeText()
    val label: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            Text(toy.name, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(
                if (toy.bought && !toy.unlocked) words.bought else toy.word,
                color = if (favourite) FavouritePink else c.muted,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
            )
        }
    }
    val action: @Composable () -> Unit = {
        when {
            offer -> BuyButton(toy, words.buy, waiting, onBuy)
            toy.bought -> Text("✓", color = c.muted, fontSize = 15.sp)
        }
    }
    Box(
        Modifier.fillMaxWidth()
            .alpha(if (toy.unlocked) 1f else LOCKED_ALPHA)
            .clip(RowShape)
            .background(c.panel2)
            .border(1.dp, if (favourite) FavouritePink else c.lineSoft, RowShape)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics(mergeDescendants = !offer) { if (!toy.unlocked) stateDescription = "Locked" }
    ) {
        if (stack)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                label(Modifier)
                action()
            }
        else
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                label(Modifier.weight(1f))
                action()
            }
    }
}

private const val FAVOURITE = "Favorite"

/**
 * The early unlock: "✦ 150", fainter when the Sparks do not cover it (a tap still asks the page).
 */
@Composable
private fun BuyButton(toy: PetToy, verb: String, waiting: Boolean, onBuy: (String) -> Unit) {
    val c = LocalPageColors.current
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.alpha(if (toy.affordable && !waiting) 1f else POOR_ALPHA)
            .clip(shape)
            .background(c.accent)
            .semantics { contentDescription = "$verb ${toy.name}, ${toy.price} Sparks" }
            .tapTarget(Role.Button) { if (!waiting) onBuy(toy.id) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "✦ ${toy.price}",
            color = c.onAccent,
            fontSize = 13.sp,
            fontWeight = FontWeight(650),
            softWrap = false,
        )
    }
}
