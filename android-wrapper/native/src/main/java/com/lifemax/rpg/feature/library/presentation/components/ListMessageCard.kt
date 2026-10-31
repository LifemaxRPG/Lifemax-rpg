package com.lifemax.rpg.feature.library.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.feature.library.domain.ListMessage

/**
 * What the list's card shows in place of rows (.empty): an icon, a title, words, maybe a button.
 */
@Composable
internal fun ListMessageCard(message: ListMessage, onAdd: () -> Unit) {
    val c = LocalLibraryColors.current
    Column(
        Modifier.cardPiece(c).fillMaxWidth().padding(horizontal = 32.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            message.icon,
            Modifier.padding(bottom = 8.dp).alpha(0.7f),
            color = c.muted,
            fontSize = 28.sp,
            lineHeight = 42.sp,
        )
        Text(
            message.title,
            color = c.muted,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            message.body,
            color = c.muted,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
        )
        if (message.add.isNotEmpty())
            PageButton(
                message.add,
                onAdd,
                Modifier.padding(top = 12.dp),
                primary = true,
                size = ButtonSize.SMALL,
            )
    }
}
