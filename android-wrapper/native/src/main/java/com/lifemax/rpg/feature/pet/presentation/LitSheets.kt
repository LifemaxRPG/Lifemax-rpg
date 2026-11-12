package com.lifemax.rpg.feature.pet.presentation

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.lifemax.rpg.feature.pet.art.LitRows

/**
 * One cat's lit sheet rows ([LitRows]) as bitmaps, one per row slot, uploaded only when a row is
 * made. A light key changes rarely by design, so after a cat's first steps in a light nothing is
 * made, uploaded or allocated. The picture of a row replaces nothing mid-frame: a row is made and
 * uploaded before the frame that draws it. Main thread only.
 */
internal class LitSheets(val rows: LitRows) {
    private val bitmaps = arrayOfNulls<Bitmap>(LitRows.DEFAULT_CAPACITY)
    private val images = arrayOfNulls<ImageBitmap>(LitRows.DEFAULT_CAPACITY)

    /**
     * The row of frames whose top is sheet row [top] (of the depth sheet when [onDepth]) lit by
     * [key], or null when that sheet is not here: then the plain sheet is drawn.
     */
    fun image(onDepth: Boolean, top: Int, key: Int): ImageBitmap? {
        val slot = rows.slotFor(onDepth, top, key)
        if (slot < 0) return null
        if (rows.wasBaked) {
            bakes++
            lastBakeNanos = rows.lastBakeNanos
        }
        if (rows.wasBaked || images[slot] == null) upload(slot)
        return images[slot]
    }

    private fun upload(slot: Int) {
        val w = rows.width
        val h = rows.frameHeight
        val bitmap =
            bitmaps[slot]
                ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                    bitmaps[slot] = it
                    images[slot] = it.asImageBitmap()
                }
        bitmap.setPixels(rows.pixels(slot), 0, w, 0, 0, w, h)
    }

    companion object {
        /** Rows made by every cat since the start, and the time the latest took (the readout). */
        @Volatile
        var bakes = 0L
            private set

        @Volatile
        var lastBakeNanos = 0L
            private set
    }
}
