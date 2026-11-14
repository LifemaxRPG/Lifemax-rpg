package com.lifemax.rpg.feature.habits.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The scene's geometry, with no drawing in it: how a tower is stacked, where the camera stands, how the towers
 * follow a finger, how a city of them is laid out, what a tap hits and what the sky does at an hour. Lengths are
 * in dp at scale 1; a tower's x runs from the middle of its front face, its y up from the ground.
 */

/**
 * A block is drawn face on with its top and right side showing (a cabinet projection), so a habit's
 * name sits flat on the front face where it can be read. [W] is a block's front width, [DX] and
 * [DY] how far the back edge is drawn to the right and up.
 */
object TowerSpec {
    const val W = 172f
    const val DX = 22f
    const val DY = 15f

    /** A block's height at each size: two minutes, normal, stretch. */
    val HEIGHTS = floatArrayOf(46f, 66f, 90f)

    /** One floor of a habit being broken, and how many floors are drawn at most. */
    const val FLOOR = 26f
    const val MAX_FLOORS = 12

    /** The foundation: a slab wider than the blocks by [SLAB_OVER] on each side. */
    const val SLAB_H = 24f
    const val SLAB_OVER = 12f
    const val REWARD_H = 28f
    const val REWARD_W = 108f

    /** The "stack a habit here" outline above the roof, and the gap under it. */
    const val GHOST_H = 42f
    const val GHOST_GAP = 12f

    /** Air kept above a tower when the camera frames it. */
    const val HEADROOM = 46f

    /** A cleared habit's tree: how far from the tower's edge the first stands, and its size. */
    const val TREE_OUT = 34f
    const val TREE_H = 58f
}

enum class PieceKind {
    SLAB,
    BLOCK,
    FLOOR,
    REWARD,
    GHOST,
}

/**
 * One drawn piece of a tower: [bottom] and [height] above the ground, [width] of its front face.
 * [id] is the block's id (the stack's for the slab, the reward and the ghost). [plan] is a block
 * not laid yet today (a blueprint); [labelled] the piece that carries the habit's name (the top
 * floor of a habit being broken).
 */
data class Piece(
    val kind: PieceKind,
    val id: String,
    val bottom: Float,
    val height: Float,
    val width: Float,
    val plan: Boolean = false,
    val labelled: Boolean = true,
    val block: HabitBlock? = null,
) {
    val top: Float
        get() = bottom + height

    val middle: Float
        get() = bottom + height / 2f
}

/** A cleared habit's tree on the lot beside the tower: [x] from the tower's middle. */
data class TreeSpot(val id: String, val name: String, val x: Float)

/**
 * A tower from its foundation up. [height] reaches the top of its highest piece's top face; [roof]
 * is the top of the highest piece that really stands (not a blueprint, the reward or the ghost),
 * where the cat sits.
 */
data class TowerLayout(
    val stackId: String,
    val pieces: List<Piece>,
    val trees: List<TreeSpot>,
    val height: Float,
    val roof: Float,
    val roofWidth: Float,
) {
    /** The piece that carries block [id]'s name. */
    fun labelled(id: String): Piece? =
        pieces.firstOrNull { it.id == id && it.labelled && it.kind != PieceKind.SLAB }

    /** How high the middle of block [id] is, for the camera; a tree's is near the ground. */
    fun aim(id: String): Float? =
        pieces
            .filter { it.id == id && (it.kind == PieceKind.BLOCK || it.kind == PieceKind.FLOOR) }
            .let { mine ->
                if (mine.isEmpty()) trees.firstOrNull { it.id == id }?.let { TowerSpec.TREE_H / 2f }
                else (mine.first().bottom + mine.last().top) / 2f
            }
}

/**
 * Stacks [stack] up: the slab, then each habit in order (a habit being broken as its floors still
 * standing, a cleared one as a tree beside the tower), the rooftop reward when the stack has one,
 * and, with [ghost], the outline where the next habit would go. [live] is today: only today has
 * blueprints, the reward and the ghost.
 */
fun towerLayout(stack: HabitStack, live: Boolean = true, ghost: Boolean = false): TowerLayout {
    val pieces = ArrayList<Piece>()
    var y = 0f
    pieces +=
        Piece(PieceKind.SLAB, stack.id, y, TowerSpec.SLAB_H, TowerSpec.W + 2 * TowerSpec.SLAB_OVER)
    y += TowerSpec.SLAB_H
    var roof = y
    var roofWidth = TowerSpec.W + 2 * TowerSpec.SLAB_OVER
    for (b in stack.blocks) {
        if (b.cleared) continue
        if (b.fade) {
            val floors = min(b.left, TowerSpec.MAX_FLOORS)
            for (f in 0 until floors) {
                pieces +=
                    Piece(
                        PieceKind.FLOOR,
                        b.id,
                        y,
                        TowerSpec.FLOOR,
                        TowerSpec.W,
                        labelled = f == floors - 1,
                        block = b,
                    )
                y += TowerSpec.FLOOR
            }
            roof = y
            roofWidth = TowerSpec.W
        } else {
            val h = TowerSpec.HEIGHTS[b.size.coerceIn(0, 2)]
            val plan = live && !b.done
            pieces += Piece(PieceKind.BLOCK, b.id, y, h, TowerSpec.W, plan = plan, block = b)
            y += h
            if (!plan) {
                roof = y
                roofWidth = TowerSpec.W
            }
        }
    }
    if (live && stack.treat.isNotEmpty() && stack.blocks.isNotEmpty()) {
        pieces += Piece(PieceKind.REWARD, stack.id, y, TowerSpec.REWARD_H, TowerSpec.REWARD_W)
        y += TowerSpec.REWARD_H
    }
    if (live && ghost) {
        y += TowerSpec.GHOST_GAP
        pieces += Piece(PieceKind.GHOST, stack.id, y, TowerSpec.GHOST_H, TowerSpec.W * .8f)
        y += TowerSpec.GHOST_H
    }
    val trees =
        stack.blocks
            .filter { it.cleared }
            .take(4)
            .mapIndexed { i, b ->
                val out =
                    TowerSpec.W / 2f + TowerSpec.SLAB_OVER + TowerSpec.TREE_OUT + (i / 2) * 30f
                TreeSpot(b.id, b.title, if (i % 2 == 0) -out else out + TowerSpec.DX)
            }
    return TowerLayout(stack.id, pieces, trees, y + TowerSpec.DY, roof, roofWidth)
}

enum class HitKind {
    SLAB,
    BLOCK,
    REWARD,
    GHOST,
    TREE,
}

/** What a tap landed on: a block or tree by its habit's id, the others by the stack's. */
data class Hit(val kind: HitKind, val id: String)

/**
 * What stands at ([x], [y]) of this tower: the piece whose front, top or side face holds the point,
 * the highest one first (a piece hides the top face of the one under it), else a tree.
 */
fun TowerLayout.hit(x: Float, y: Float): Hit? {
    for (p in pieces.asReversed()) {
        val half = p.width / 2f
        val front = abs(x) <= half && y >= p.bottom && y <= p.top
        // the top face leans right as it goes up; the side face rises as it goes right
        val up = y - p.top
        val onTop =
            up > 0f &&
                up <= TowerSpec.DY &&
                (x - up * TowerSpec.DX / TowerSpec.DY).let { it >= -half && it <= half }
        val right = x - half
        val onSide =
            right > 0f &&
                right <= TowerSpec.DX &&
                (y - right * TowerSpec.DY / TowerSpec.DX).let { it >= p.bottom && it <= p.top }
        if (front || onTop || onSide)
            return Hit(
                when (p.kind) {
                    PieceKind.SLAB -> HitKind.SLAB
                    PieceKind.BLOCK,
                    PieceKind.FLOOR -> HitKind.BLOCK
                    PieceKind.REWARD -> HitKind.REWARD
                    PieceKind.GHOST -> HitKind.GHOST
                },
                p.id,
            )
    }
    return trees
        .firstOrNull { abs(x - it.x) <= 24f && y >= -6f && y <= TowerSpec.TREE_H }
        ?.let { Hit(HitKind.TREE, it.id) }
}

/**
 * Where the camera stands on the tower in front: [scale] of the tower and [pan], how far it is slid
 * down so its upper part shows, between 0 (the ground at the bottom of the view) and [maxPan] (the
 * top of the tower at the top of the view). A point [y] above the ground is drawn `scale * y - pan`
 * above the bottom of the view.
 */
data class Camera(val scale: Float, val pan: Float, val maxPan: Float)

const val CAMERA_MIN = .9f
const val CAMERA_MAX = 1.25f

/**
 * Frames a tower [height] tall in a view [avail] tall. A short tower is enlarged to fill it, up to
 * [CAMERA_MAX]; a tall one keeps a readable size, never under [CAMERA_MIN], and is panned instead:
 * to [pan] when the owner dragged it, else so that [aim] (the block to lay next) sits in the middle
 * of the view, else to its top.
 */
fun focusCamera(height: Float, avail: Float, aim: Float? = null, pan: Float? = null): Camera {
    val full = height + TowerSpec.HEADROOM
    val scale = (avail / full).coerceIn(CAMERA_MIN, CAMERA_MAX)
    val maxPan = max(0f, scale * full - avail)
    val wanted = pan ?: aim?.let { scale * it - avail / 2f } ?: maxPan
    return Camera(scale, wanted.coerceIn(0f, maxPan), maxPan)
}

/**
 * Where a tower is drawn: [x] of its middle from the middle of the view, its [scale], its [alpha].
 */
data class Pose(val x: Float, val scale: Float, val alpha: Float)

fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

fun Pose.towards(other: Pose, t: Float): Pose =
    Pose(lerp(x, other.x, t), lerp(scale, other.scale, t), lerp(alpha, other.alpha, t))

/**
 * How far apart neighbouring towers stand while one is in front, as a share of the view's width.
 */
const val PAGE_SHARE = .74f

/**
 * Tower [index] while the towers are at [position] (2.4 is four tenths of the way from the third
 * tower to the fourth): the one in front full size and solid, its neighbours smaller, fainter and a
 * page to the side, so they peek in at the edges; those further off are not drawn (alpha 0).
 */
fun pagerPose(index: Int, position: Float, width: Float): Pose {
    val d = index - position
    val away = min(1f, abs(d))
    return Pose(
        d * width * PAGE_SHARE,
        1f - .34f * away,
        if (abs(d) > 1.6f) 0f else 1f - .5f * away,
    )
}

/** [position] with the pull past either end of [count] towers held back to a third. */
fun rubberBand(position: Float, count: Int): Float {
    val last = max(0, count - 1).toFloat()
    return when {
        position < 0f -> position * RUBBER
        position > last -> last + (position - last) * RUBBER
        else -> position
    }
}

private const val RUBBER = .32f

/**
 * The tower a release settles on: where the towers would coast to at [velocity] (towers a second,
 * positive towards later ones), but never more than one from [start], the tower in front when the
 * drag began.
 */
fun settlePage(position: Float, velocity: Float, start: Int, count: Int): Int {
    if (count <= 0) return 0
    val coast = (position + velocity * COAST_SECONDS).roundToInt()
    return coast.coerceIn(start - 1, start + 1).coerceIn(0, count - 1)
}

private const val COAST_SECONDS = .22f

/** A city of towers: each one's middle from the middle of the view, all at one [scale]. */
data class CityLayout(val scale: Float, val xs: List<Float>, val slot: Float)

const val CITY_MAX_SCALE = .7f
const val CITY_MIN_SCALE = .16f

/** Room kept under the city's towers for their names. */
const val CITY_LABEL = 34f

/**
 * Every tower side by side in a view [width] wide and [avail] tall: as large as lets them all fit
 * with a margin (the tallest sets the scale too), between [CITY_MIN_SCALE] and [CITY_MAX_SCALE].
 */
fun cityLayout(heights: List<Float>, width: Float, avail: Float): CityLayout {
    val n = heights.size
    if (n == 0) return CityLayout(CITY_MAX_SCALE, emptyList(), 0f)
    val slot = TowerSpec.W + 2 * TowerSpec.SLAB_OVER + TowerSpec.DX + 22f
    val tallest = (heights.maxOrNull() ?: 0f) + TowerSpec.HEADROOM / 2f
    val scale =
        min(CITY_MAX_SCALE, min((width - 24f) / (n * slot), (avail - CITY_LABEL) / tallest))
            .coerceAtLeast(CITY_MIN_SCALE)
    return CityLayout(
        scale,
        List(n) { (it - (n - 1) / 2f) * slot * scale - TowerSpec.DX * scale / 2f },
        slot * scale,
    )
}

/** The tower of the city under [x] (from the middle of the view), if any. */
fun CityLayout.towerAt(x: Float): Int? = xs.indices.firstOrNull { abs(x - xs[it]) <= slot / 2f }

/** The weeks ago a time-lapse slider shows at [fraction] of its track: the right end is today. */
fun lapseWeeks(fraction: Float, max: Int): Int =
    ((1f - fraction.coerceIn(0f, 1f)) * max).roundToInt()

/** And back: where the slider's thumb is for [weeks] ago. */
fun lapseFraction(weeks: Int, max: Int): Float =
    if (max <= 0) 1f else 1f - weeks.coerceIn(0, max) / max.toFloat()

enum class SkyPhase {
    NIGHT,
    DUSK,
    DAY,
}

/** Night until 5:30 and from 20:30; the hours around sunrise and sunset are dusk. */
fun skyPhase(hour: Float): SkyPhase =
    when {
        hour < 5.5f || hour >= 20.5f -> SkyPhase.NIGHT
        hour < 7.5f || hour >= 18f -> SkyPhase.DUSK
        else -> SkyPhase.DAY
    }

/**
 * Where the sun (by day) or the moon (by night) stands at [hour]: how far across the sky, 0 to 1,
 * and how high, 0 at the horizon ends of its arc to 1 at the top of it.
 */
fun orbAt(hour: Float): Pair<Float, Float> {
    val night = skyPhase(hour) == SkyPhase.NIGHT
    val t = (if (night) ((hour + 3.5f) % 24f) / 9f else (hour - 5.5f) / 15f).coerceIn(0f, 1f)
    return t to sin(t * Math.PI).toFloat()
}

/** The same few random numbers for the same key, every time: what makes a block's marks its own. */
class Dice(key: String) {
    private var h: Int = FNV

    init {
        for (c in key) h = (h xor c.code) * 16777619
    }

    /** The next number, from 0 up to but not 1. */
    fun next(): Float {
        h = h * 1664525 + 1013904223
        return (h.toLong() and 0xFFFFFFFFL).toFloat() / 4294967296f
    }

    private companion object {
        const val FNV = -2128831035 // 2166136261 as a 32-bit integer
    }
}

/**
 * A crack (or the seam left by coming back) down a block's front face, its own shape for each
 * [key]: points as (how far across the face, 0 to 1; how far down it, 0 to 1), from the top edge to
 * the bottom, a zigzag through the right-hand third.
 */
fun crackLine(key: String, tall: Boolean): List<Pair<Float, Float>> {
    val dice = Dice(key + "c")
    val n = if (tall) 6 else 4
    return List(n + 1) { i ->
        (.74f + (if (i % 2 == 1) .09f else 0f) + dice.next() * .05f) to i / n.toFloat()
    }
}

/** One building of a skyline: from [x], [w] wide and [h] tall, in the units given to [skyline]. */
data class Building(val x: Float, val w: Float, val h: Float)

/**
 * A row of buildings from [from] to [to], the same for the same [seed]: each 12 to 31 wide and
 * between [lo] and [hi] tall.
 */
fun skyline(seed: Int, lo: Int, hi: Int, from: Float, to: Float): List<Building> {
    var r = seed
    var x = from
    val out = ArrayList<Building>()
    while (x < to) {
        r = (r * 73 + 19) % 997
        val w = 12 + r % 20
        r = (r * 73 + 19) % 997
        out += Building(x, w.toFloat(), (lo + r % (hi - lo)).toFloat())
        x += w
    }
    return out
}

/**
 * A star: across and down the sky (0 to 1), its radius in dp, its brightness, whether it twinkles.
 */
data class Star(val x: Float, val y: Float, val r: Float, val alpha: Float, val twinkles: Boolean)

/** [count] stars, the same every time. */
fun stars(count: Int, seed: Int = 7): List<Star> {
    var r = seed
    return List(count) { i ->
        r = (r * 73 + 19) % 997
        val x = r / 997f
        r = (r * 73 + 19) % 997
        val big = i % 7 == 0
        Star(x, r / 997f, if (big) 1.3f else .8f, .3f + (i % 5) * .13f, big)
    }
}
