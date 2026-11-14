package com.lifemax.rpg.feature.habits.presentation.scene

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.feature.habits.domain.Activity
import com.lifemax.rpg.feature.habits.domain.Ambient
import com.lifemax.rpg.feature.habits.domain.BuildingLayout
import com.lifemax.rpg.feature.habits.domain.BuildingSpec as B
import com.lifemax.rpg.feature.habits.domain.Dice
import com.lifemax.rpg.feature.habits.domain.HabitBlock
import com.lifemax.rpg.feature.habits.domain.HabitMaterial
import com.lifemax.rpg.feature.habits.domain.HabitStack
import com.lifemax.rpg.feature.habits.domain.Occupancy
import com.lifemax.rpg.feature.habits.domain.Piece
import com.lifemax.rpg.feature.habits.domain.PieceKind
import com.lifemax.rpg.feature.habits.domain.RuinWindow
import com.lifemax.rpg.feature.habits.domain.Silhouettes
import com.lifemax.rpg.feature.habits.domain.crackLine
import com.lifemax.rpg.feature.habits.domain.floorWindows
import com.lifemax.rpg.feature.habits.domain.lightOrder
import com.lifemax.rpg.feature.habits.domain.occupancy
import com.lifemax.rpg.feature.habits.domain.ruinWindow
import com.lifemax.rpg.feature.habits.presentation.HabitsCopy

/**
 * Words measured for the scene. A building is drawn in its own units (dp at scale 1) under one
 * canvas transform, so its words are measured once at that size (a density of 1: a 12 sp name is 12
 * units tall) and scale with the building, instead of being measured again at every frame of a
 * zoom. The owner's text size counts up to [MAX_FONT_SCALE]: a floor is only so big.
 */
internal class SceneText(private val measurer: TextMeasurer, fontScale: Float) {
    private val density = Density(1f, fontScale.coerceIn(1f, MAX_FONT_SCALE))

    fun layout(
        text: String,
        size: Float,
        weight: FontWeight,
        maxWidth: Float,
        maxLines: Int = 1,
        spacing: Float = 0f,
    ): TextLayoutResult =
        measurer.measure(
            text,
            TextStyle(
                fontSize = size.sp,
                fontWeight = weight,
                letterSpacing = spacing.sp,
                lineHeight = (size * 1.18f).sp,
            ),
            overflow = TextOverflow.Ellipsis,
            softWrap = maxLines > 1,
            maxLines = maxLines,
            constraints = Constraints(maxWidth = maxWidth.toInt().coerceAtLeast(1)),
            density = density,
        )

    private companion object {
        const val MAX_FONT_SCALE = 1.25f
    }
}

/**
 * One storey worked out once, when the habits change, so a frame only draws: its walls' brush and
 * paths, its windows, which of them light and who is in them, its plaque's words, its crack.
 */
internal class FloorArt(
    val piece: Piece,
    val block: HabitBlock,
    val facade: Facade,
    /** Its front as a rectangle, y down from its top, in building units (top is -piece.top). */
    val top: Float,
    val bottom: Float,
    val wall: Brush,
    /** The side face and the brick, stone, steel or grime drawn over the wall. */
    val side: Path,
    val texture: Path,
    val trim: Path,
    val reflect: Path,
    /** Its windows as x, y, w, h (y from the front's top), in the order they light up. */
    val windows: FloatArray,
    val occupancy: Occupancy,
    /** Who is in the first [Occupancy.people] lit windows, and their own seeds. */
    val activities: Array<Activity>,
    val seeds: IntArray,
    /** A run-down storey's windows, in window order. */
    val ruin: Array<RuinWindow>,
    val name: TextLayoutResult?,
    val count: TextLayoutResult?,
    val plaqueW: Float,
    val plaqueH: Float,
    val crack: Path?,
    /** The storey of a habit being broken (0 the lowest), and how many it has. */
    val storey: Int,
    val storeys: Int,
) {
    val windowCount: Int
        get() = windows.size / 4
}

/** A building worked out once: its storeys and the words on its lobby and its roof. */
internal class BuildingArt(
    val layout: BuildingLayout,
    val stack: HabitStack,
    val floors: Array<FloorArt>,
    val lobby: Piece,
    val sign: Piece?,
    val ghost: Piece?,
    val lobbyName: TextLayoutResult,
    val treat: TextLayoutResult?,
    val ghostWords: TextLayoutResult?,
    val awning: Color,
    val lobbySide: Path,
    /** The material of its top storey, which sets its cornice. */
    val crown: HabitMaterial?,
    /**
     * Its storeys of a habit being broken that are the top of the building: the crane stands there.
     */
    val craneOn: FloorArt?,
)

internal fun buildingArt(layout: BuildingLayout, stack: HabitStack, text: SceneText): BuildingArt {
    val floors = ArrayList<FloorArt>()
    val storeyCount = HashMap<String, Int>()
    for (p in layout.pieces) if (p.kind == PieceKind.RUIN)
        storeyCount[p.id] = (storeyCount[p.id] ?: 0) + 1
    val seen = HashMap<String, Int>()
    for (p in layout.pieces) {
        val b = p.block ?: continue
        if (p.kind != PieceKind.FLOOR && p.kind != PieceKind.RUIN) continue
        val storey = seen[p.id] ?: 0
        seen[p.id] = storey + 1
        floors += floorArt(p, b, text, storey, storeyCount[p.id] ?: 1)
    }
    val lobby = layout.pieces.first { it.kind == PieceKind.LOBBY }
    val sign = layout.pieces.firstOrNull { it.kind == PieceKind.SIGN }
    val ghost = layout.pieces.firstOrNull { it.kind == PieceKind.GHOST }
    val top = floors.lastOrNull()
    return BuildingArt(
        layout,
        stack,
        floors.toTypedArray(),
        lobby,
        sign,
        ghost,
        text.layout(stack.cue.uppercase(), 10.5f, FontWeight.Bold, B.W - 30f, spacing = 1.1f),
        sign?.let {
            text.layout(
                if (stack.claimed) HabitsCopy.ENJOYED else stack.treat,
                10.5f,
                FontWeight.Bold,
                it.width - 16f,
            )
        },
        ghost?.let {
            text.layout(HabitsCopy.STACK_HERE, 11.5f, FontWeight.SemiBold, it.width - 16f)
        },
        AWNINGS[((Dice(stack.id).next() * AWNINGS.size).toInt()).coerceIn(0, AWNINGS.size - 1)],
        sidePath(-lobby.top, -lobby.bottom),
        top?.block?.material,
        top?.takeIf { it.piece.kind == PieceKind.RUIN },
    )
}

private fun sidePath(top: Float, bottom: Float): Path =
    Path().apply {
        moveTo(B.W / 2f, top)
        lineTo(B.W / 2f + B.DX, top - B.DY)
        lineTo(B.W / 2f + B.DX, bottom - B.DY)
        lineTo(B.W / 2f, bottom)
        close()
    }

private fun floorArt(
    p: Piece,
    b: HabitBlock,
    text: SceneText,
    storey: Int,
    storeys: Int,
): FloorArt {
    val ruin = p.kind == PieceKind.RUIN
    val f = facade(if (ruin) HabitMaterial.FADE else b.material)
    val top = -p.top
    val bottom = -p.bottom
    val half = B.W / 2f
    // the plaque, in the band under the windows: the name, and the count beside it where it fits
    val labelled = p.labelled
    val room = B.W - 2 * B.PLAQUE_X - 10f
    val name =
        if (labelled) text.layout(b.name, if (ruin) 10.5f else 11.5f, FontWeight.Bold, room * .78f)
        else null
    val count =
        if (labelled && !ruin)
            text.layout(HabitsCopy.count(b), 8.5f, FontWeight.Medium, room).takeIf {
                name != null && name.size.width + 7f + it.size.width <= room
            }
        else null
    val plaqueW =
        if (name == null) 0f else name.size.width + (count?.let { it.size.width + 7f } ?: 0f) + 10f
    val plaqueH = if (name == null) 0f else maxOf(B.PLAQUE_H, name.size.height + 2f)
    val spots = floorWindows(p.height, ruin)
    val order = lightOrder(b.id + "/" + storey, spots.size)
    val windows = FloatArray(spots.size * 4)
    for ((k, i) in order.withIndex()) {
        val s = spots[i]
        windows[k * 4] = s.x
        windows[k * 4 + 1] = s.y
        windows[k * 4 + 2] = s.w
        windows[k * 4 + 3] = s.h
    }
    val occ = occupancy(b.reps, spots.size)
    val activities = Array(occ.people) { Silhouettes.activityFor(b.title, it) }
    val seeds = IntArray(occ.people) { (Ambient.hash(b.id.hashCode(), it, 5) * 100000).toInt() }
    val ruins = Array(spots.size) { ruinWindow(b.id, storey, storeys, it) }
    val texture = Path()
    val trim = Path()
    val reflect = Path()
    for (k in 0 until spots.size) {
        val x = windows[k * 4]
        val y = top + windows[k * 4 + 1]
        val w = windows[k * 4 + 2]
        val h = windows[k * 4 + 3]
        // a sill under every window, and a lintel over it from brick on
        trim.addRect(Rect(x - 1.5f, y + h, x + w + 1.5f, y + h + 2f))
        if (b.material != HabitMaterial.GLASS || ruin)
            trim.addRect(Rect(x - 1f, y - 2f, x + w + 1f, y - .6f))
        // a streak of sky on dark glass
        reflect.moveTo(x + w * .2f, y + h)
        reflect.lineTo(x + w * .75f, y)
        reflect.lineTo(x + w, y)
        reflect.lineTo(x + w, y + h * .2f)
        reflect.lineTo(x + w * .45f, y + h)
        reflect.close()
    }
    when {
        ruin -> {
            // grime running down from the sills
            val dice = Dice(b.id + "g" + storey)
            for (i in 0 until 6) {
                val x = -half + 8f + dice.next() * (B.W - 16f)
                texture.moveTo(x, top + 2f)
                texture.lineTo(
                    x + (dice.next() - .5f) * 3f,
                    top + 4f + dice.next() * (p.height - 6f),
                )
            }
        }
        b.material == HabitMaterial.GLASS -> {
            // the steel frame: a column between every window, a beam across the middle
            for (c in 0..B.COLS) {
                val x = -half + B.MARGIN + c * B.PITCH
                texture.moveTo(x, top)
                texture.lineTo(x, bottom)
            }
            texture.moveTo(-half, top + 2f)
            texture.lineTo(half, top + 2f)
        }
        b.material == HabitMaterial.CLAY -> {
            var y = top + 7f
            var row = 0
            while (y < bottom - 1f) {
                texture.moveTo(-half, y)
                texture.lineTo(half, y)
                var x = -half + if (row % 2 == 0) 14f else 7f
                while (x < half) {
                    texture.moveTo(x, y - 7f)
                    texture.lineTo(x, y)
                    x += 14f
                }
                y += 7f
                row++
            }
        }
        else -> {
            // cut stone: long courses, the joints of one over the middle of the stones below
            var y = top + 14f
            var row = 0
            while (y < bottom - 1f) {
                texture.moveTo(-half, y)
                texture.lineTo(half, y)
                var x = -half + if (row % 2 == 0) 30f else 15f
                while (x < half) {
                    texture.moveTo(x, y - 14f)
                    texture.lineTo(x, y)
                    x += 30f
                }
                y += 14f
                row++
            }
        }
    }
    val crack =
        if (!ruin && labelled && (b.miss >= 1 || b.scar))
            Path().apply {
                val pts = crackLine(b.id, p.height > 60f)
                pts.forEachIndexed { i, (u, v) ->
                    val x = -half + u * B.W
                    val y = top + v * p.height
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                val turn = pts.size / 2
                val (u, v) = pts[turn]
                val away = if (pts[turn + 1].first > u) -1f else 1f
                moveTo(-half + u * B.W, top + v * p.height)
                lineTo(-half + u * B.W + away * 9f, top + v * p.height + 6f)
            }
        else null
    return FloorArt(
        p,
        b,
        f,
        top,
        bottom,
        Brush.verticalGradient(listOf(f.wallTop, f.wallBottom), top, bottom),
        sidePath(top, bottom),
        texture,
        trim,
        reflect,
        windows,
        occ,
        activities,
        seeds,
        ruins,
        name,
        count,
        plaqueW,
        plaqueH,
        crack,
        storey,
        storeys,
    )
}
