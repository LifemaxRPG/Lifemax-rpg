package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.lifemax.rpg.feature.pet.body.CatLife
import com.lifemax.rpg.feature.pet.body.CatPose
import com.lifemax.rpg.feature.pet.body.World
import kotlin.math.floor
import kotlin.math.roundToInt

/** The colours of the little room behind the cat, taken from the card's theme by the caller. */
internal class CatStageColors(val wall: Color, val floor: Color, val baseboard: Color)

/**
 * Draws the room strip and the cat for one instant, from a [pose]. The room is a window 192 room
 * pixels wide (see [CatLife.WINDOW_WIDTH]) drawn at a whole number of device pixels per room pixel,
 * centred, with the feet 14 room pixels above the bottom edge; the wall and the floor fill the rest
 * of the stage.
 *
 * What of the pose is used: position, flip, clip and frame, squash and stretch, rotation about the
 * feet, bob and breath, and the ground shadow. The ears, tail, whiskers and look offsets in the
 * pose are not drawn yet: they need the rig, which is not ported (the plain sheet frames already
 * carry the art's own ear and tail poses).
 *
 * Nothing here allocates: offsets and sizes are value classes, the sheet is looked up by index and
 * the transform is applied in place.
 */
internal fun DrawScope.drawCatStage(pose: CatPose, sprites: CatSprites, colors: CatStageColors) {
    val s = pixelScale(size.width)
    val left = ((size.width - CatLife.WINDOW_WIDTH * s) / 2f).roundToInt()
    val bottom = size.height
    drawRect(colors.wall, Offset.Zero, size)
    val horizon = bottom - s * (FRAME_BELOW_FLOOR + World.FLOOR_Y_ART - World.HORIZON_Y)
    drawRect(colors.floor, Offset(0f, horizon), Size(size.width, bottom - horizon))
    drawRect(colors.baseboard, Offset(0f, horizon), Size(size.width, s.toFloat().coerceAtLeast(1f)))
    drawShadow(pose, s, left, bottom)
    drawSprite(pose, sprites, s, left, bottom)
}

/** Device pixels per room pixel: a whole number, at least 1, so the pixel art stays crisp. */
internal fun pixelScale(width: Float): Int =
    floor(width / CatLife.WINDOW_WIDTH).toInt().coerceAtLeast(1)

private fun DrawScope.drawShadow(pose: CatPose, s: Int, left: Int, bottom: Float) {
    if (pose.shadowAlpha <= 0f) return
    val wide = pose.shadowHalfWidth * pose.shadowScale
    val x = left + pose.shadowX * s
    val y = bottom - s * (ROOM_HEIGHT - pose.shadowY)
    val color = SHADOW.copy(alpha = pose.shadowAlpha)
    // the room's own two-row ground shadow: a wide thin row and a shorter one under it
    drawRect(color, snap(x - wide * s, y - s), Size(snapLength(wide * 2f * s), s * 2f))
    drawRect(
        color,
        snap(x - (wide - SHADOW_INSET) * s, y + s),
        Size(snapLength((wide * 2f - SHADOW_INSET * 2f) * s), s.toFloat()),
    )
}

private fun DrawScope.drawSprite(
    pose: CatPose,
    sprites: CatSprites,
    s: Int,
    left: Int,
    bottom: Float,
) {
    val sheet = sprites.sheet(pose.clip) ?: return
    val footX = (left + pose.x * s).roundToInt().toFloat()
    val footY = (bottom - s * (ROOM_HEIGHT - pose.y) + pose.bob * s).roundToInt().toFloat()
    val flip = if (pose.flip) -1f else 1f
    val frameW = sprites.frameWidth
    val frameH = sprites.frameHeight
    val feet = sprites.feet(pose.clip)
    withTransform({
        translate(footX, footY)
        rotate(Math.toDegrees(pose.rotation.toDouble()).toFloat(), Offset.Zero)
        scale(pose.scaleX * flip, pose.scaleY * pose.breath, Offset.Zero)
    }) {
        drawImage(
            sheet,
            srcOffset =
                IntOffset(sprites.frame(pose.clip, pose.frame) * frameW, sprites.top(pose.clip)),
            srcSize = IntSize(frameW, frameH),
            dstOffset = IntOffset(-(frameW / 2) * s, (1 - feet) * s),
            dstSize = IntSize(frameW * s, frameH * s),
            filterQuality = FilterQuality.None,
        )
    }
}

private fun snap(x: Float, y: Float) = Offset(x.roundToInt().toFloat(), y.roundToInt().toFloat())

private fun snapLength(v: Float) = v.roundToInt().toFloat()

/** Room rows under the feet in the card's window, as in the still portrait (a shadow fits). */
private const val FRAME_BELOW_FLOOR = 14
private const val ROOM_HEIGHT = World.FLOOR_Y_ART + FRAME_BELOW_FLOOR
private const val SHADOW_INSET = 3f

/** The room's ground shadow colour, rgb(30, 16, 40); its alpha comes from the pose. */
private val SHADOW = Color(0xFF1E1028)
