package com.lifemax.rpg.feature.pet.body

/** What the cat is doing with its body in a clip; decides what may follow it and how it settles. */
enum class Posture {
    /** Walking, trotting or running: the stride clips, which share a phase. */
    LOCOMOTION,

    /** Sitting or standing at rest: a place to hold when the queue is empty. */
    SIT,

    /** Loaf: a place to hold. */
    LOAF,

    /** Lying or asleep: a place to hold. */
    LIE,

    /** A one-off activity (groom, eat, play, scratch): returns to sitting afterwards. */
    ACTION,

    /** In the air. */
    AIR,
}

/**
 * One animation row of the sprite sheet, as the body sees it.
 *
 * @property id index into [Clips.all]; this is the `clip` the pose reports.
 * @property name the web room's row name.
 * @property row row on its sheet (main sheet, or the depth sheet when [depthSheet]).
 * @property frames number of frames.
 * @property fps the web's base frame rate.
 * @property bottom pixel row of the feet inside a 72x48 frame.
 * @property loop false for rows that play once and hold their last frame.
 * @property contacts frames on which a foot lands (locomotion only; inferred, see [Clips]).
 * @property posture see [Posture].
 * @property height relative body height of the pose, used to size the squash when the cat changes
 *   posture (sitting down is a smaller height than standing).
 * @property stridePx ground covered by one full cycle at the web's speed for the gait; zero for
 *   clips that are not locomotion. Playing at `speed / stridePx` cycles per second means no slip.
 * @property shadowHalfWidth half width of the ground shadow in art pixels.
 * @property cheapBeat true when letting the clip finish is a short, natural beat.
 * @property openFrames for clips that blink: the frames with open eyes, played slowly.
 * @property blinkFrames for clips that blink: the frames of one blink.
 */
class Clip(
    val id: Int,
    val name: String,
    val row: Int,
    val frames: Int,
    val fps: Float,
    val bottom: Int,
    val loop: Boolean,
    val contacts: IntArray,
    val posture: Posture,
    val height: Float,
    val stridePx: Float,
    val shadowHalfWidth: Float,
    val cheapBeat: Boolean,
    val openFrames: IntArray,
    val blinkFrames: IntArray,
    val depthSheet: Boolean,
) {
    val isLocomotion: Boolean
        get() = posture == Posture.LOCOMOTION

    /** True for clips that hold a pose when the queue runs out. */
    val isHoldable: Boolean
        get() = posture == Posture.SIT || posture == Posture.LOAF || posture == Posture.LIE

    val blinks: Boolean
        get() = blinkFrames.isNotEmpty()

    /** Seconds for one pass at the base frame rate. */
    val seconds: Float
        get() = frames / fps

    /** Phase (0..1 of the cycle) at which contact number [i] lands. */
    fun contactPhase(i: Int): Float = contacts[i].toFloat() / frames
}

/**
 * The clip table, built from `petAssets.anims` (names, row order, frame counts, fps, bottom foot
 * row) plus the four `depth` rows. Loop flags, postures, heights and foot-contact frames are not in
 * the web data: foot contacts are inferred as the two half-cycle points of the stride (frame 0 and
 * the middle frame), the natural place for a symmetric walk or gallop cycle.
 */
object Clips {
    private const val WALK_SPEED = 32f
    private const val RUN_SPEED = 96f
    private const val WEAK_SPEED = 17f
    private const val FRONT_STRIDE = 24f
    private const val NARROW_SHADOW = 11f
    private const val NORMAL_SHADOW = 15f
    private const val WIDE_SHADOW = 22f
    private val NONE = IntArray(0)
    private val SIT_OPEN = intArrayOf(0, 1, 2, 6, 7)
    private val SIT_BLINK = intArrayOf(3, 4, 5)
    private val SIT_W_OPEN = intArrayOf(0, 1, 6, 7)
    private val SIT_W_BLINK = intArrayOf(2, 3, 4, 5)

    private val list = ArrayList<Clip>()
    private val byName = HashMap<String, Clip>()

    private fun add(
        name: String,
        row: Int,
        frames: Int,
        fps: Float,
        bottom: Int,
        posture: Posture,
        height: Float,
        loop: Boolean = true,
        speed: Float = 0f,
        contacts: IntArray = NONE,
        shadow: Float = NORMAL_SHADOW,
        cheapBeat: Boolean = false,
        open: IntArray = NONE,
        blink: IntArray = NONE,
        depth: Boolean = false,
    ): Clip {
        val stride = if (speed > 0f) speed * frames / fps else 0f
        val clip =
            Clip(
                list.size,
                name,
                row,
                frames,
                fps,
                bottom,
                loop,
                contacts,
                posture,
                height,
                stride,
                shadow,
                cheapBeat,
                open,
                blink,
                depth,
            )
        list.add(clip)
        byName[name] = clip
        return clip
    }

    private fun halfCycle(frames: Int) = intArrayOf(0, frames / 2)

    private fun action(name: String, row: Int, n: Int, fps: Float, bottom: Int, h: Float) =
        add(name, row, n, fps, bottom, Posture.ACTION, h)

    private fun once(name: String, row: Int, n: Int, fps: Float, bottom: Int, h: Float) =
        add(name, row, n, fps, bottom, Posture.ACTION, h, loop = false)

    val IDLE_SIT =
        add("idle_sit", 0, 8, 7f, 47, Posture.SIT, 1f, open = SIT_OPEN, blink = SIT_BLINK)
    val WALK =
        add(
            "walk",
            1,
            8,
            12f,
            46,
            Posture.LOCOMOTION,
            1f,
            speed = WALK_SPEED,
            contacts = halfCycle(8),
        )
    val RUN =
        add(
            "run",
            2,
            6,
            16f,
            46,
            Posture.LOCOMOTION,
            0.95f,
            speed = RUN_SPEED,
            contacts = halfCycle(6),
        )
    val SLEEP = add("sleep", 3, 4, 2f, 47, Posture.LIE, 0.6f, shadow = WIDE_SHADOW)
    val LOAF = add("loaf", 4, 4, 2f, 47, Posture.LOAF, 0.72f, shadow = WIDE_SHADOW)
    val GROOM = action("groom", 5, 8, 5f, 47, 0.95f)
    val EAT = action("eat", 6, 6, 5f, 46, 0.9f)
    val PLAY = action("play", 7, 6, 9f, 47, 0.95f)
    val STRETCH = once("stretch", 8, 7, 4f, 47, 1f)
    val LOOKUP = action("lookup", 9, 4, 3f, 47, 1.05f)
    val SLOWBLINK = add("slowblink", 10, 6, 4f, 47, Posture.SIT, 1f, loop = false, cheapBeat = true)
    val PURR = action("purr", 11, 4, 4f, 47, 0.8f)
    val RUB = action("rub", 12, 4, 5f, 46, 0.95f)
    val KNEAD = add("knead", 13, 4, 4f, 47, Posture.ACTION, 0.75f, shadow = WIDE_SHADOW)
    val MEOW = action("meow", 14, 4, 6f, 47, 1f)
    val LEAP = add("leap", 15, 2, 4f, 46, Posture.AIR, 1.1f, loop = false)
    val IDLE_SIT_W =
        add("idle_sit_w", 16, 8, 5f, 47, Posture.SIT, 0.95f, open = SIT_W_OPEN, blink = SIT_W_BLINK)
    val WALK_W =
        add(
            "walk_w",
            17,
            8,
            8f,
            46,
            Posture.LOCOMOTION,
            0.95f,
            speed = WEAK_SPEED,
            contacts = halfCycle(8),
        )
    val LOAF_W = add("loaf_w", 18, 4, 1.5f, 47, Posture.LOAF, 0.7f, shadow = WIDE_SHADOW)
    val SLEEP_W = add("sleep_w", 19, 4, 1.5f, 47, Posture.LIE, 0.6f, shadow = WIDE_SHADOW)
    val CROUCH = add("crouch", 20, 3, 7f, 47, Posture.ACTION, 0.8f, loop = false)
    val SCRATCH = action("scratch", 21, 8, 7f, 46, 1.05f)
    val SCRATCH_IN = once("scratch_in", 22, 3, 7f, 47, 1f)
    val SCRATCH_OUT = once("scratch_out", 23, 3, 7f, 47, 1f)
    val ROLL = action("roll", 24, 8, 8f, 47, 0.6f)
    val KICK = action("kick", 25, 6, 12f, 47, 0.7f)
    val BAT = action("bat", 26, 8, 9f, 46, 0.95f)
    val SWAT = action("swat", 27, 6, 9f, 47, 0.95f)
    val WALK_FRONT = depthWalk("walk_front", 0)
    val WALK_BACK = depthWalk("walk_back", 1)
    val SNIFF =
        add("sniff", 2, 4, 10f, 46, Posture.ACTION, 0.95f, shadow = NARROW_SHADOW, depth = true)
    val BONK =
        add(
            "bonk",
            3,
            2,
            6f,
            46,
            Posture.ACTION,
            1f,
            loop = false,
            shadow = NARROW_SHADOW,
            depth = true,
        )

    private fun depthWalk(name: String, row: Int) =
        add(
            name,
            row,
            6,
            10f,
            46,
            Posture.LOCOMOTION,
            1f,
            speed = FRONT_STRIDE * 10f / 6f,
            contacts = halfCycle(6),
            shadow = NARROW_SHADOW,
            depth = true,
        )

    /** All clips in id order. */
    val all: List<Clip> = list

    /** The clip for a web row name, or null if there is no such row. */
    fun find(name: String): Clip? = byName[name]

    /** The weak (hungry) look of [clip], as the web's PET_WEAK map: run becomes walk_w. */
    fun weakOf(clip: Clip): Clip =
        when (clip) {
            WALK,
            RUN,
            WALK_FRONT,
            WALK_BACK -> WALK_W
            IDLE_SIT -> IDLE_SIT_W
            LOAF -> LOAF_W
            SLEEP -> SLEEP_W
            else -> clip
        }
}
