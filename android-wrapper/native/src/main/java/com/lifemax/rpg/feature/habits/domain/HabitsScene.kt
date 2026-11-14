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

    /**
     * Air kept above a tower when the camera frames it: enough, at the camera's smallest scale, to
     * keep a tower that fits clear of [TOP_FADE].
     */
    const val HEADROOM = 30f

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
 * which the camera and the city measure a tower by.
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

    /** How high the middle of block [id] is; a tree's is near the ground. */
    fun aim(id: String): Float? = span(id)?.let { (it.first + it.second) / 2f }

    /**
     * From where to where block [id] stands above the ground (all its floors, for a habit being
     * broken), for the camera; a tree's is from the ground up.
     */
    fun span(id: String): Pair<Float, Float>? =
        pieces
            .filter { it.id == id && (it.kind == PieceKind.BLOCK || it.kind == PieceKind.FLOOR) }
            .let { mine ->
                if (mine.isEmpty())
                    trees.firstOrNull { it.id == id }?.let { 0f to TowerSpec.TREE_H }
                else mine.first().bottom to mine.last().top
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
 * What stands at ([x], [at]) of this tower: the piece whose front, top or side face holds the
 * point, the highest one first (a piece hides the top face of the one under it), else a tree.
 * [sink] is how far the camera has slid everything above the slab down behind it ([Camera.sink]):
 * what has gone under the slab's top is not there to be tapped.
 */
fun TowerLayout.hit(x: Float, at: Float, sink: Float = 0f): Hit? {
    for (p in pieces.asReversed()) {
        val slab = p.kind == PieceKind.SLAB
        if (!slab && sink > 0f && at < TowerSpec.SLAB_H) continue
        val y = if (slab) at else at + sink
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
        .firstOrNull { abs(x - it.x) <= 24f && at >= -6f && at <= TowerSpec.TREE_H }
        ?.let { Hit(HitKind.TREE, it.id) }
}

/**
 * The part of the view the towers may use, in dp from the top of a view [height] tall: under the
 * [top] band (the heading and its buttons) and above the [bottom] band (the rough-day switch, the
 * big button and the dots, or the time-lapse). The ground line is [GROUND_MARGIN] above the bottom
 * band, so the lot's front edge clears it; the towers' window runs from [windowTop] to [ground].
 */
data class SceneWindow(val height: Float, val top: Float, val bottom: Float) {
    val ground: Float
        get() = height - bottom - GROUND_MARGIN

    /** The room for a tower, never under [MIN_AVAIL] (a view too small to hold both bands). */
    val avail: Float
        get() = max(MIN_AVAIL, ground - top)

    val windowTop: Float
        get() = ground - avail

    /** Where a point [above] the ground line (in dp) is drawn, from the top of the view. */
    fun y(above: Float): Float = ground - above

    companion object {
        const val GROUND_MARGIN = 30f
        const val MIN_AVAIL = 120f
    }
}

/**
 * Where the camera stands on the tower in front. The foundation never moves: its slab stays on the
 * ground line with its name in view. [scale] is the tower's; [pan] how far everything above the
 * slab is slid down behind it so the upper part shows, in dp, between 0 (the first block on the
 * slab) and [maxPan] (the top of the tower at the top of the window).
 */
data class Camera(val scale: Float, val pan: Float, val maxPan: Float) {
    /** How far above the ground line the top of the slab is drawn, in dp. */
    val slabTop: Float
        get() = scale * TowerSpec.SLAB_H

    /** [pan] in the tower's own units. */
    val sink: Float
        get() = pan / scale

    /**
     * How far above the ground line a point [y] of the tower above its slab is drawn, in dp. Under
     * [slabTop] it is hidden behind the slab.
     */
    fun above(y: Float): Float = scale * y - pan
}

const val CAMERA_MIN = .7f
const val CAMERA_MAX = 1.25f

/** Air the camera keeps above the block it aims at, so what comes after it shows too. */
const val AIM_AIR = 30f

/**
 * How far under the top of the window a tower too tall for it fades out, in dp, so it never runs
 * under the heading.
 */
const val TOP_FADE = 20f

/**
 * Frames a tower [height] tall in a window [avail] tall. A short tower is enlarged to fill it, up
 * to [CAMERA_MAX]; a tall one is made smaller until it fits, but never under [CAMERA_MIN], where
 * its names would stop being readable: then it is slid down behind its slab instead. It is slid to
 * [pan] when the owner dragged it; else no further than it takes for [aim] (the block to lay next,
 * from its bottom to its top) to show whole with [AIM_AIR] over it, so the tower stays on its
 * foundation as long as it can; else, with nothing to aim at, to its top.
 */
fun focusCamera(
    height: Float,
    avail: Float,
    aim: Pair<Float, Float>? = null,
    pan: Float? = null,
): Camera {
    val full = height + TowerSpec.HEADROOM
    val scale = (avail / full).coerceIn(CAMERA_MIN, CAMERA_MAX)
    val maxPan = max(0f, scale * full - avail)
    val wanted =
        pan
            ?: aim?.let { (bottom, top) ->
                // its top in the window, unless that hides its foot behind the slab
                min(scale * (top + AIM_AIR) - avail, scale * (bottom - TowerSpec.SLAB_H))
            }
            ?: maxPan
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

/** Room kept clear over the city's towers: the sun or the moon stands there. */
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

/**
 * Where the sun or the moon is drawn, in dp from the left and the top of a view [width] wide: in
 * the lane beside the tower in front (left before the middle of its arc, right after), never behind
 * the tower's column ([column] is half its width) and never in the heading: between [top] (the top
 * of the towers' window) and [horizon]. In the [city] (0 to 1) it stands in the strip kept clear
 * over the towers.
 */
fun orbSpot(
    hour: Float,
    width: Float,
    top: Float,
    horizon: Float,
    column: Float,
    city: Float = 0f,
): Pair<Float, Float> {
    val (across, up) = orbAt(hour)
    val lane = max(0f, width / 2f - column)
    val fromEdge = max(ORB_EDGE, lane / 2f)
    val x = if (across < .5f) fromEdge else width - fromEdge
    val high = top + ORB_TOP
    val low = max(high, horizon - ORB_LOW)
    return x to lerp(lerp(low, high, up), high, city.coerceIn(0f, 1f))
}

/**
 * The orb's middle is kept this far from the view's edge, under the window's top and over the
 * horizon.
 */
const val ORB_EDGE = 34f
const val ORB_TOP = 18f
const val ORB_LOW = 44f

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
 * A crack (or the seam left by coming back) on a block's front face, its own shape for each [key]:
 * points as (how far across the face, 0 to 1; how far down it, 0 to 1). It starts on the top edge
 * somewhere in the right-hand half (the name is on the left), zigzags down and stops inside the
 * face, so it never meets the crack of the block under it; a [tall] block's has more turns.
 */
fun crackLine(key: String, tall: Boolean): List<Pair<Float, Float>> {
    val dice = Dice(key + "c")
    val n = (if (tall) 4 else 3) + if (dice.next() < .5f) 0 else 1
    val start = CRACK_LEFT + .09f + dice.next() * (CRACK_RIGHT - CRACK_LEFT - .18f)
    val depth = .5f + dice.next() * (CRACK_DEPTH - .5f)
    var side = if (dice.next() < .5f) -1f else 1f
    return List(n + 1) { i ->
        if (i == 0) start to 0f
        else {
            val u = start + side * (.03f + dice.next() * .06f)
            side = -side
            // the turns are not evenly spaced either
            u to depth * (i - .4f + dice.next() * .4f) / n
        }
    }
}

/** A crack keeps between these across its face, and never goes deeper than [CRACK_DEPTH] of it. */
const val CRACK_LEFT = .52f
const val CRACK_RIGHT = .94f
const val CRACK_DEPTH = .84f

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
