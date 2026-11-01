package com.lifemax.rpg.feature.overview.presentation.cards

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.lifemax.rpg.feature.overview.domain.CatPortrait
import com.lifemax.rpg.feature.overview.domain.PortraitBounds
import com.lifemax.rpg.feature.overview.domain.portraitBounds
import com.lifemax.rpg.feature.overview.domain.portraitPngBytes
import com.lifemax.rpg.feature.overview.domain.portraitScale
import kotlin.math.roundToInt

/**
 * The cat card's portrait: the still frame the page drew of the cat sitting (petPortrait). The
 * clear margin the page leaves around the cat is cropped, and the cat is shown at the largest
 * whole-number pixel scale that fits, centred, with nearest-neighbour sampling so the pixel art
 * stays crisp. The image is decoded (and its margin measured) once per portrait key, not on every
 * projection (one arrives every 15 s with the same key while the cat looks the same). With no
 * portrait (the page is still loading the cat's art, or it could not be decoded) a quiet paw stands
 * in, in [placeholder]. [description] is for TalkBack; leave it null where the card already reads
 * the cat's name.
 */
@Composable
fun PetPortrait(
    portrait: CatPortrait?,
    modifier: Modifier = Modifier,
    placeholder: Color = Color(0xFF8B93A3),
    description: String? = null,
) {
    val decoded = remember(portrait?.key) { portrait?.let { decodePortrait(it.dataUrl) } }
    val described =
        if (description == null) modifier
        else
            modifier.semantics {
                contentDescription = description
                role = Role.Image
            }
    Box(described, contentAlignment = Alignment.Center) {
        if (decoded != null) PortraitImage(decoded) else PawPlaceholder(placeholder)
    }
}

/** A decoded portrait and the part of it the cat covers. */
internal class DecodedPortrait(val image: ImageBitmap, val cat: PortraitBounds)

/** The PNG of a portrait data URL as a bitmap with its cat's bounds, or null when it is not one. */
internal fun decodePortrait(dataUrl: String): DecodedPortrait? {
    val bytes = portraitPngBytes(dataUrl) ?: return null
    return runCatching {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val cat = portraitBounds(pixels, bitmap.width, bitmap.height) ?: return null
            DecodedPortrait(bitmap.asImageBitmap(), cat)
        }
        .getOrNull()
}

@Composable
private fun PortraitImage(p: DecodedPortrait) {
    Canvas(Modifier.fillMaxSize()) {
        val c = p.cat
        val k = portraitScale(size.width, size.height, c.width, c.height)
        if (k <= 0f) return@Canvas
        val w = (c.width * k).roundToInt()
        val h = (c.height * k).roundToInt()
        drawImage(
            p.image,
            srcOffset = IntOffset(c.left, c.top),
            srcSize = IntSize(c.width, c.height),
            dstOffset =
                IntOffset(
                    ((size.width - w) / 2).roundToInt(),
                    ((size.height - h) / 2).roundToInt(),
                ),
            dstSize = IntSize(w, h),
            filterQuality = FilterQuality.None,
        )
    }
}

/**
 * A paw (four toes over a pad) on an 11-cell grid, two fifths of the box's shorter side, centred.
 */
@Composable
private fun PawPlaceholder(color: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val cell = (minOf(size.width, size.height) * 0.4f / PAW.size).coerceAtLeast(1f)
        val o = Offset(center.x - PAW.size * cell / 2, center.y - PAW.size * cell / 2)
        PAW.forEachIndexed { y, row ->
            row.forEachIndexed { x, c ->
                if (c == '#')
                    drawRect(color, Offset(o.x + x * cell, o.y + y * cell), Size(cell, cell))
            }
        }
    }
}

/* the paw, 11 x 11 cells */
private val PAW =
    listOf(
        "...........",
        "..##...##..",
        "..##...##..",
        "##.......##",
        "##.......##",
        "....###....",
        "..#######..",
        ".#########.",
        ".#########.",
        "..##...##..",
        "...........",
    )
