package com.lifemax.rpg.feature.overview.domain

import java.util.Base64
import org.json.JSONObject

/*
 * The cat card's portrait (plan OVERVIEW_BODY §3.3 tiles.cat, phase 3), the minimal model the
 * PetPortrait composable needs.
 *
 * align at merge: ov-native-a's OverviewCards.kt owns the cat tile and the projection's image table
 * (`images`, decoded once per key by PageImages). There `portrait` becomes a field of its cat tile
 * and `catPortrait` reads its images map; these two types and the decoder fold into that file.
 *
 * The page draws the portrait (petPortrait in 50-pet-portrait.js, with the pet room's own drawing
 * code) and sends it as a PNG data URL in `images`, named from `tiles.cat.portrait`. It is 192 x 128
 * px: 96 x 64 room pixels at two image pixels each, the cat sitting on a transparent background.
 * Native only shows it; it never draws the cat.
 */

/** The portrait as the projection carries it: the image key and the page's data URL for it. */
data class CatPortrait(val key: String, val dataUrl: String)

/** The portrait's size in image pixels, as petPortrait draws it (PET_PORTRAIT W x H at S = 2). */
const val CAT_PORTRAIT_WIDTH = 192
const val CAT_PORTRAIT_HEIGHT = 128

/**
 * The cat tile's portrait from the tile object and the projection's images: `portrait` is an image
 * key or null (no portrait yet: the room's art is still loading on the page, or a cat in memory).
 * Strict, like every decoder here: a missing `portrait` field, a key that is not a string, or a key
 * the images table does not carry is malformed (throws).
 */
fun JSONObject.catPortrait(images: JSONObject): CatPortrait? {
    require(has("portrait")) { "tiles.cat.portrait is missing" }
    if (isNull("portrait")) return null
    val key = get("portrait")
    require(key is String && key.isNotEmpty()) { "tiles.cat.portrait is not an image key" }
    require(images.has(key)) { "tiles.cat.portrait names an image the projection does not carry" }
    return CatPortrait(key, images.getString(key))
}

/** The largest data URL decoded, the cap Character's avatar uses (CharacterHeroCard.kt). */
const val PORTRAIT_MAX_URL = 2_000_000

private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

/**
 * The PNG bytes of a portrait data URL, or null when it is not a base64 PNG data URL, is larger
 * than the cap, or does not decode to a PNG. Only these bytes reach the bitmap decoder.
 */
fun portraitPngBytes(dataUrl: String): ByteArray? {
    val prefix = "data:image/png;base64,"
    if (!dataUrl.startsWith(prefix) || dataUrl.length > PORTRAIT_MAX_URL) return null
    val bytes =
        runCatching { Base64.getDecoder().decode(dataUrl.substring(prefix.length)) }.getOrNull()
            ?: return null
    if (bytes.size < PNG_SIGNATURE.size) return null
    for (i in PNG_SIGNATURE.indices) if (bytes[i] != PNG_SIGNATURE[i]) return null
    return bytes
}

/**
 * How many device pixels each portrait pixel takes in a box: a whole number when the portrait fits
 * at least once, so the pixel art stays crisp (every image pixel the same size), and the plain fit
 * when the box is smaller than the image.
 */
fun portraitScale(boxWidth: Float, boxHeight: Float, imageWidth: Int, imageHeight: Int): Float {
    if (imageWidth <= 0 || imageHeight <= 0 || boxWidth <= 0f || boxHeight <= 0f) return 0f
    val fit = minOf(boxWidth / imageWidth, boxHeight / imageHeight)
    return if (fit >= 1f) kotlin.math.floor(fit) else fit
}

/** A rectangle of image pixels: left and top, width and height. */
data class PortraitBounds(val left: Int, val top: Int, val width: Int, val height: Int)

/**
 * The part of the portrait the cat covers (its pixels and its shadow), from ARGB pixels row by row:
 * the page leaves room around the cat for the longest ears and tails a body-shape gene can give, so
 * a card that shows the cat as large as it can crops that clear margin first. Null when every pixel
 * is clear.
 */
fun portraitBounds(pixels: IntArray, width: Int, height: Int): PortraitBounds? {
    var x0 = width
    var y0 = height
    var x1 = -1
    var y1 = -1
    for (y in 0 until height) for (x in 0 until width) {
        if (pixels[y * width + x] ushr 24 == 0) continue
        if (x < x0) x0 = x
        if (x > x1) x1 = x
        if (y < y0) y0 = y
        if (y > y1) y1 = y
    }
    return if (x1 < 0) null else PortraitBounds(x0, y0, x1 - x0 + 1, y1 - y0 + 1)
}
