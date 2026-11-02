package com.lifemax.rpg.feature.overview.presentation.cards

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LocalPageColors
import com.lifemax.rpg.core.designsystem.components.tapTarget
import com.lifemax.rpg.core.designsystem.gesture.noTabSwipe
import com.lifemax.rpg.feature.overview.domain.AppItem
import com.lifemax.rpg.feature.overview.domain.AppsCard

/**
 * The App shortcuts card (ovAppsHtml): a row of the owner's apps, each its own icon over its name,
 * and "Add app" at the end. [image] turns an item's image key into its decoded icon (the
 * projection's `images`, decoded once); an icon not yet sent is an empty square, as the page's
 * blank gif is. [onLaunch] is an app's tap (`appLaunch {pkg}`, which leaves the cards showing),
 * [onAdd] "Add app" (`appAdd`), [onEdit] "Edit ›" (`appsEdit`).
 *
 * While the row is wider than the card it scrolls sideways, and a gesture that starts on it is
 * never a tab swipe ([noTabSwipe]), as the web's swipe refuses to start on an overflowing strip.
 */
@Composable
internal fun AppsCardView(
    tile: AppsCard,
    image: (String) -> ImageBitmap?,
    onLaunch: (String) -> Unit,
    onAdd: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    OverviewCard(modifier) {
        CardHead(tile.title) { CardLink(tile.link, onEdit) }
        val scroll = rememberScrollState()
        Row(
            Modifier.then(if (scroll.maxValue > 0) Modifier.noTabSwipe() else Modifier)
                .horizontalScroll(scroll)
                .padding(top = 2.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tile.items.forEach { a -> App(a, a.image?.let(image)) { onLaunch(a.pkg) } }
            Shortcut(tile.add, tile.add, onAdd) { AddTile() }
        }
        if (tile.hint.isNotEmpty())
            Text(
                tile.hint,
                Modifier.padding(top = 6.dp),
                color = c.muted,
                fontSize = 12.5.sp,
                lineHeight = 18.75.sp,
            )
    }
}

@Composable
private fun App(a: AppItem, icon: ImageBitmap?, onClick: () -> Unit) =
    Shortcut(a.label, a.a11y, onClick) {
        if (icon != null)
            Image(
                icon,
                contentDescription = null,
                Modifier.size(IconSize).clip(IconShape),
                contentScale = ContentScale.Crop,
            )
        else Box(Modifier.size(IconSize))
    }

/**
 * `.ova-app`: a 64 px button, its icon over one line of its name, read by TalkBack as [a11y] (the
 * page's aria-label).
 */
@Composable
private fun Shortcut(
    label: String,
    a11y: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val c = LocalPageColors.current
    Column(
        Modifier.width(64.dp)
            .clip(AppShape)
            .tapTarget(Role.Button, onClick = onClick)
            .semantics { contentDescription = a11y }
            .padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        icon()
        Text(
            label,
            Modifier.widthIn(max = 62.dp).clearAndSetSemantics {},
            color = c.text,
            fontSize = 11.sp,
            lineHeight = 12.65.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** `.ova-add i`: a dashed square with a plus. */
@Composable
private fun AddTile() {
    val c = LocalPageColors.current
    Box(
        Modifier.size(IconSize).drawBehind {
            val w = 2.dp.toPx()
            drawRoundRect(
                c.line,
                topLeft = Offset(w / 2, w / 2),
                size = size.copy(width = size.width - w, height = size.height - w),
                cornerRadius = CornerRadius(12.dp.toPx() - w / 2),
                style =
                    Stroke(
                        w,
                        pathEffect =
                            PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.5.dp.toPx())),
                    ),
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        // the button's name is the page's aria-label; the plus only draws the dashed square
        Text(
            "+",
            Modifier.clearAndSetSemantics {},
            color = c.muted,
            fontSize = 24.sp,
            lineHeight = 36.sp,
        )
    }
}

private val IconSize = 48.dp
private val IconShape = RoundedCornerShape(12.dp)
private val AppShape = RoundedCornerShape(14.dp)
