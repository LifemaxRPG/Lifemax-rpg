package com.lifemax.rpg.feature.pet.presentation

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.lifemax.rpg.feature.pet.art.ArgbImage
import com.lifemax.rpg.feature.pet.room.RoomArt
import com.lifemax.rpg.feature.pet.room.RoomCanvas
import com.lifemax.rpg.feature.pet.room.RoomImage

/** The room's pictures as bitmaps, made once from the shipped art. */
internal class RoomBitmaps(art: RoomArt) {
    private val bitmaps = Array(RoomImage.entries.size) { bitmap(art.image(RoomImage.entries[it])) }

    operator fun get(image: RoomImage): ImageBitmap = bitmaps[image.ordinal]
}

internal fun bitmap(image: ArgbImage): ImageBitmap =
    Bitmap.createBitmap(image.pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
        .asImageBitmap()

/**
 * A [RoomCanvas] over a Compose [DrawScope]: every house pixel is [scale] device pixels, the
 * house's top left is at ([originX], [originY]) in the scope, and pictures are sampled without
 * filtering, so the art stays crisp at any whole-number scale. Set the three and the scope with
 * [begin] for each frame; nothing here allocates while drawing (offsets and sizes are value
 * classes, the quad's path is reused, and the lamp's picture is made again only when its strength
 * changes).
 */
internal class ComposeRoomCanvas(private val bitmaps: RoomBitmaps) : RoomCanvas {
    private lateinit var scope: DrawScope
    private var scale = 1
    private var originX = 0
    private var originY = 0
    private val path = Path()
    private var overlay: ImageBitmap? = null
    private var overlayVersion = Int.MIN_VALUE

    /** Draw the next frame into [scope] with the house at ([originX], [originY]), [scale] times. */
    fun begin(scope: DrawScope, scale: Int, originX: Int, originY: Int) {
        this.scope = scope
        this.scale = scale
        this.originX = originX
        this.originY = originY
    }

    private fun px(x: Int) = originX + x * scale

    private fun py(y: Int) = originY + y * scale

    override fun clip(x: Int, y: Int, w: Int, h: Int) {
        val canvas = scope.drawContext.canvas
        canvas.save()
        canvas.clipRect(px(x).toFloat(), py(y).toFloat(), px(x + w).toFloat(), py(y + h).toFloat())
    }

    override fun unclip() = scope.drawContext.canvas.restore()

    override fun fill(x: Int, y: Int, w: Int, h: Int, argb: Int) {
        scope.drawRect(
            Color(argb),
            Offset(px(x).toFloat(), py(y).toFloat()),
            Size((w * scale).toFloat(), (h * scale).toFloat()),
        )
    }

    override fun image(
        image: RoomImage,
        sx: Int,
        sy: Int,
        sw: Int,
        sh: Int,
        dx: Int,
        dy: Int,
        dw: Int,
        dh: Int,
    ) {
        scope.drawImage(
            bitmaps[image],
            srcOffset = IntOffset(sx, sy),
            srcSize = IntSize(sw, sh),
            dstOffset = IntOffset(px(dx), py(dy)),
            dstSize = IntSize(dw * scale, dh * scale),
            filterQuality = FilterQuality.None,
        )
    }

    override fun quad(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        x3: Float,
        y3: Float,
        argb: Int,
    ) {
        path.reset()
        path.moveTo(originX + x0 * scale, originY + y0 * scale)
        path.lineTo(originX + x1 * scale, originY + y1 * scale)
        path.lineTo(originX + x2 * scale, originY + y2 * scale)
        path.lineTo(originX + x3 * scale, originY + y3 * scale)
        path.close()
        scope.drawPath(path, Color(argb))
    }

    override fun multiply(rgb: Int, x: Int, y: Int, w: Int, h: Int) {
        scope.drawRect(
            Color(OPAQUE or rgb),
            Offset(px(x).toFloat(), py(y).toFloat()),
            Size((w * scale).toFloat(), (h * scale).toFloat()),
            blendMode = BlendMode.Multiply,
        )
    }

    override fun overlay(mask: ArgbImage, version: Int, x: Int, y: Int) {
        if (overlay == null || overlayVersion != version) {
            overlay = bitmap(mask)
            overlayVersion = version
        }
        scope.drawImage(
            overlay!!,
            dstOffset = IntOffset(px(x), py(y)),
            dstSize = IntSize(mask.width * scale, mask.height * scale),
            filterQuality = FilterQuality.None,
        )
    }

    private companion object {
        const val OPAQUE = 0xFF000000.toInt()
    }
}
