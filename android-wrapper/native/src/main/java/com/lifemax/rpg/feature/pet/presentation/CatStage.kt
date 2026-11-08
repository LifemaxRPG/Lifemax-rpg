package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
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
 * feet, bob and breath, and the ground shadow. A cat with a rig ([CatSprites.rig]) also has its
 * ears, tail and whiskers drawn where the springs have them, and its proportions (see
 * [RigPainter]); the look offsets are not drawn yet.
 *
 * Nothing here allocates: offsets and sizes are value classes, the sheet is looked up by index and
 * the transform is applied in place.
 */
internal fun DrawScope.drawCatStage(
    pose: CatPose,
    sprites: CatSprites,
    colors: CatStageColors,
    motion: Boolean = true,
) {
    val s = pixelScale(size.width)
    val left = ((size.width - CatLife.WINDOW_WIDTH * s) / 2f).roundToInt()
    val bottom = size.height
    drawRect(colors.wall, Offset.Zero, size)
    val horizon = bottom - s * (FRAME_BELOW_FLOOR + World.FLOOR_Y_ART - World.HORIZON_Y)
    drawRect(colors.floor, Offset(0f, horizon), Size(size.width, bottom - horizon))
    drawRect(colors.baseboard, Offset(0f, horizon), Size(size.width, s.toFloat().coerceAtLeast(1f)))
    drawShadow(pose, s, left, bottom)
    drawSprite(pose, sprites, s, left, bottom, motion)
}

/** Device pixels per room pixel: a whole number, at least 1, so the pixel art stays crisp. */
internal fun pixelScale(width: Float): Int =
    floor(width / CatLife.WINDOW_WIDTH).toInt().coerceAtLeast(1)

private fun DrawScope.drawShadow(pose: CatPose, s: Int, left: Int, bottom: Float) {
    drawShadowAt(pose, s, left + pose.shadowX * s, bottom - s * (ROOM_HEIGHT - pose.shadowY))
}

/**
 * The room's two-row ground shadow of [pose] with its centre at device pixel ([x], [y]), [s] device
 * pixels to a room pixel; its alpha, width and shrinking come from the pose.
 */
internal fun DrawScope.drawShadowAt(pose: CatPose, s: Int, x: Float, y: Float) {
    if (pose.shadowAlpha <= 0f) return
    val wide = pose.shadowHalfWidth * pose.shadowScale
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
    motion: Boolean,
) {
    val footX = (left + pose.x * s).roundToInt().toFloat()
    val footY = (bottom - s * (ROOM_HEIGHT - pose.y) + pose.bob * s).roundToInt().toFloat()
    drawSpriteAt(pose, sprites, s, footX, footY, motion = motion)
}

/**
 * The cat of [pose] with its feet at device pixel ([footX], [footY]): the sheet frame of its clip,
 * mirrored when it faces left, squashed, stretched and turned about the feet, and breathing, as the
 * cat's steady drawing ([SpritePlan], [SpriteSteady]) says. With a [tint] every opaque pixel is
 * that colour instead (the rim light is the cat's silhouette so). With a rig the frame is the
 * composed one (ears, tail, whiskers, proportions) unless the cat is the art as drawn; [motion]
 * false keeps the parts as drawn (reduced motion).
 */
internal fun DrawScope.drawSpriteAt(
    pose: CatPose,
    sprites: CatSprites,
    s: Int,
    footX: Float,
    footY: Float,
    tint: Color? = null,
    motion: Boolean = true,
) {
    val sheet = sprites.sheet(pose.clip) ?: return
    val frameW = sprites.frameWidth
    val frameH = sprites.frameHeight
    val plan = drawPlan
    plan.plan(
        pose,
        sprites.rig,
        sprites.feet(pose.clip),
        frameW,
        frameH,
        SpriteSteady.of(pose),
        RigPainter.frames,
        s,
        motion,
    )
    val image = if (plan.slot >= 0) RigPainter.image(plan.slot, plan.fresh) else sheet
    val srcX = if (plan.slot >= 0) 0 else sprites.frame(pose.clip, pose.frame) * frameW
    val srcY = if (plan.slot >= 0) 0 else sprites.top(pose.clip)
    val filter = if (tint == null) null else ColorFilter.tint(tint, BlendMode.SrcIn)
    val count = plan.bands(s, bands)
    withTransform({
        translate(footX, footY)
        rotate(plan.turnDeg, Offset.Zero)
        scale(plan.scaleX, plan.scaleY, Offset.Zero)
    }) {
        for (b in 0 until count) {
            val i = b * SpritePlan.BAND
            drawImage(
                image,
                srcOffset = IntOffset(srcX, srcY + bands[i]),
                srcSize = IntSize(plan.width, bands[i + 1] - bands[i]),
                dstOffset = IntOffset(plan.left * s, bands[i + 2]),
                dstSize = IntSize(plan.width * s, bands[i + 3]),
                colorFilter = filter,
                filterQuality = FilterQuality.None,
            )
        }
    }
}

/** The plan and bands of the cat being drawn (main thread only; nothing allocated per frame). */
private val drawPlan = SpritePlan()
private val bands = IntArray(SpritePlan.MAX_BANDS * SpritePlan.BAND)

private fun snap(x: Float, y: Float) = Offset(x.roundToInt().toFloat(), y.roundToInt().toFloat())

private fun snapLength(v: Float) = v.roundToInt().toFloat()

/** Room rows under the feet in the card's window, as in the still portrait (a shadow fits). */
private const val FRAME_BELOW_FLOOR = 14
private const val ROOM_HEIGHT = World.FLOOR_Y_ART + FRAME_BELOW_FLOOR
private const val SHADOW_INSET = 3f

/** The room's ground shadow colour, rgb(30, 16, 40); its alpha comes from the pose. */
private val SHADOW = Color(0xFF1E1028)
