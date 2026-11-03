package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.lifemax.rpg.feature.pet.art.FrameRect
import com.lifemax.rpg.feature.pet.body.CatBody
import com.lifemax.rpg.feature.pet.body.ToyBody
import com.lifemax.rpg.feature.pet.body.ToyField
import com.lifemax.rpg.feature.pet.body.ToyKind
import com.lifemax.rpg.feature.pet.room.RoomImage
import com.lifemax.rpg.feature.pet.room.RoomLayout

/**
 * Draws the house's toys (a [ToyField]) in the live room the way [ToyBody] asks: a free toy sorted
 * by depth with the cats (before the first cat in front of it, the rest after the room's cats), a
 * toy at or in a mouth ([ToyBody.overCat]) just after the cat that holds it, each from the room
 * atlas moved, turned and scaled as the body says, nearest-neighbour. The yarn ball and the spring
 * have no atlas sprite yet: a disc of their colour; yarn also trails its thread.
 *
 * One frame: [begin], then [before] and [held] around each cat the room draws, then [rest]. Nothing
 * allocates while drawing.
 */
internal class ToyPainter(kit: RoomKit) {
    private val atlas = kit.bitmaps[RoomImage.ATLAS]
    private val rects: Array<FrameRect?> =
        Array(ToyKind.entries.size) { kit.art.atlas.rect(ToyKind.entries[it].key) }
    private var field: ToyField? = null
    private var drawn = BooleanArray(0)
    private lateinit var scope: DrawScope
    private var scale = 1
    private var originX = 0
    private var originY = 0

    /** A frame of [field] (null: no toys) onto [target] at [scale] with the house's origin. */
    fun begin(field: ToyField?, target: DrawScope, scale: Int, originX: Int, originY: Int) {
        this.field = field
        if (field == null) return
        if (drawn.size < field.count) drawn = BooleanArray(field.count)
        drawn.fill(false)
        scope = target
        this.scale = scale
        this.originX = originX
        this.originY = originY
    }

    /** The free toys behind depth [z] not drawn yet, moved [shift] pixels (a cat comes next). */
    fun before(z: Float, shift: Int) {
        val f = field ?: return
        for (i in 0 until f.count) {
            val t = f[i] ?: continue
            if (!drawn[i] && !t.overCat && t.z <= z) paint(i, t, shift)
        }
    }

    /** The toy [body] holds, when it is drawn over the cat (it was just drawn). */
    fun held(body: CatBody, shift: Int) {
        val f = field ?: return
        val t = body.heldToy ?: return
        if (t.overCat && t.id < f.count && !drawn[t.id]) paint(t.id, t, shift)
    }

    /** Every toy not drawn yet: in front of the cats. */
    fun rest() {
        val f = field ?: return
        for (i in 0 until f.count) {
            val t = f[i] ?: continue
            if (!drawn[i]) paint(i, t, 0)
        }
    }

    private fun paint(i: Int, t: ToyBody, shift: Int) {
        drawn[i] = true
        val s = scale.toFloat()
        val canvas = scope.drawContext.canvas
        if (t.kind.thread && t.threadCount > 1) thread(t, shift)
        canvas.save()
        val transform = scope.drawContext.transform
        transform.translate(
            originX + (t.drawX - RoomLayout.HOUSE_X0 + shift) * s,
            originY + t.drawY * s,
        )
        transform.rotate(Math.toDegrees(t.drawAngle.toDouble()).toFloat(), Offset.Zero)
        transform.scale(
            t.drawScaleX * t.depthScale * s,
            t.drawScaleY * t.depthScale * s,
            Offset.Zero,
        )
        transform.translate(-t.pivotU, -t.pivotV)
        val r = rects[t.kind.ordinal]
        if (r != null) {
            scope.drawImage(
                atlas,
                srcOffset = IntOffset(r.x, r.y),
                srcSize = IntSize(r.width, r.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(r.width, r.height),
                filterQuality = FilterQuality.None,
            )
        } else {
            val w = t.kind.width.toFloat()
            val h = t.kind.height.toFloat()
            scope.drawCircle(colourOf(t.kind), minOf(w, h) / 2f, Offset(w / 2f, h / 2f))
        }
        canvas.restore()
    }

    /** The yarn's thread: a line through its points, one device pixel per room pixel of width. */
    private fun thread(t: ToyBody, shift: Int) {
        val s = scale.toFloat()
        val p = t.thread
        for (k in 1 until t.threadCount) {
            val x0 = originX + (p[2 * k - 2] - RoomLayout.HOUSE_X0 + shift) * s
            val y0 = originY + p[2 * k - 1] * s
            val x1 = originX + (p[2 * k] - RoomLayout.HOUSE_X0 + shift) * s
            val y1 = originY + p[2 * k + 1] * s
            scope.drawLine(YARN, Offset(x0, y0), Offset(x1, y1), strokeWidth = s)
        }
    }

    private fun colourOf(kind: ToyKind): Color = if (kind == ToyKind.YARN) YARN else SPRING

    private companion object {
        /** The web room's yarn red and the spring's steel. */
        val YARN = Color(0xFFC8464B)
        val SPRING = Color(0xFF9AA4AE)
    }
}
