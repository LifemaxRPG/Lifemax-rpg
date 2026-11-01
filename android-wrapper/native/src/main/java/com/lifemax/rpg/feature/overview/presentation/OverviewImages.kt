package com.lifemax.rpg.feature.overview.presentation

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * The page's images (the avatar, app icons), decoded once per key: the page sends its whole table
 * with every projection (every 15 s), so each data URL is decoded the first time its key is seen
 * and kept while it is among the last few used. A URL that is not an image, or larger than the
 * page's 2,000,000-character cap (as Character's avatar), draws nothing. Plan §3.2's core
 * PageImages lift can take this over.
 */
internal class OverviewImages(max: Int = 32) {
    private val cache = LruCache<String, Decoded>(max)

    private class Decoded(val bitmap: ImageBitmap?)

    /** The image for [key] from [images], or null when there is none or it cannot be read. */
    fun get(images: Map<String, String>, key: String?): ImageBitmap? {
        if (key == null) return null
        cache.get(key)?.let {
            return it.bitmap
        }
        val url = images[key] ?: return null
        return Decoded(decode(url)).also { cache.put(key, it) }.bitmap
    }

    private fun decode(url: String): ImageBitmap? =
        runCatching {
                if (!url.startsWith("data:image/") || url.length >= MAX_CHARS) return null
                val bytes = Base64.decode(url.substringAfter(','), Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
            .getOrNull()

    private companion object {
        const val MAX_CHARS = 2_000_000
    }
}
