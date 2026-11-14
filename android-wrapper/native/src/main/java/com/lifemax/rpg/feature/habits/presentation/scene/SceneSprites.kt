package com.lifemax.rpg.feature.habits.presentation.scene

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** A cloud sprite's three layers, as images. */
internal class CloudImages(
    val sprite: CloudSprite,
    val shade: ImageBitmap,
    val mid: ImageBitmap,
    val lit: ImageBitmap,
)

/** A tree variant's images, one per [TreeLook]. */
internal class TreeImages(val sprite: TreeSprite, val looks: Array<ImageBitmap>)

/**
 * The scene's pre-rendered art, made once for the whole process the first time it is needed (or, in
 * the app, ahead of that on a background thread: [warm]) and shared by every scene after it. The
 * generators are deterministic and know nothing of Compose; this only turns their pixels into
 * images. The pool is about 4 MB: [bytes].
 */
internal object SceneSprites {
    val clouds: Array<CloudImages> by lazy {
        Array(CloudSprites.SPECS.size) {
            val s = CloudSprites.generate(it)
            CloudImages(s, image(s.shade, s.w, s.h), image(s.mid, s.w, s.h), image(s.lit, s.w, s.h))
        }
    }

    val trees: Array<TreeImages> by lazy {
        Array(com.lifemax.rpg.feature.habits.domain.Trees.VARIANTS) {
            val s = TreeSprites.generate(it)
            TreeImages(s, Array(s.pixels.size) { look -> image(s.pixels[look], s.w, s.h) })
        }
    }

    /** Makes both pools now (call off the main thread). */
    fun warm() {
        clouds
        trees
    }

    /** What the pools hold, in bytes. */
    fun bytes(): Int = CloudSprites.bytes() + TreeSprites.bytes()

    private fun image(pixels: IntArray, w: Int, h: Int): ImageBitmap =
        Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
}
