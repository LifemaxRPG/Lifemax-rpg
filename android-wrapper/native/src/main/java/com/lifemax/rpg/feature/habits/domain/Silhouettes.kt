package com.lifemax.rpg.feature.habits.domain

import kotlin.math.abs

/*
 * The people in the lit windows: what they are doing (by what the habit is called), the few poses of each
 * doing it as small lists of points, and which pose shows at a moment. Pure data and arithmetic: the painter
 * turns each pose into a path once and only picks one per window per frame.
 *
 * A pose is drawn from the middle of its window's sill, in building units (dp at scale 1): x to the right,
 * y up as negative numbers. A standing person is about 18 tall; the smallest window is 24.
 */

/** What someone in a window is doing. */
enum class Activity {
    TYPING,
    STRETCHING,
    READING,
    LIFTING,
    WALKING,
}

/**
 * One pose: the head's middle ([headX], [headY]), solid [shapes] (each a polygon as x, y, x, y...:
 * the body, the legs, a desk, a book), and [limbs] (lines as x0, y0, x1, y1... drawn with a stroke:
 * arms).
 */
class Pose(
    val headX: Float,
    val headY: Float,
    val shapes: Array<FloatArray>,
    val limbs: FloatArray,
)

object Silhouettes {
    const val HEAD_R = 2.4f
    const val LIMB = 1.5f

    /** What the habit [name] has its people doing, by the words in it; null when nothing fits. */
    fun activityOf(name: String): Activity? {
        val n = name.lowercase()
        return KEYWORDS.firstOrNull { (_, words) -> words.any { n.contains(it) } }?.first
    }

    private val KEYWORDS: List<Pair<Activity, List<String>>> =
        listOf(
            Activity.LIFTING to
                listOf(
                    "push-up",
                    "pushup",
                    "push up",
                    "pull-up",
                    "pullup",
                    "squat",
                    "lift",
                    "gym",
                    "weight",
                    "plank",
                    "workout",
                    "work out",
                    "burpee",
                    "sit-up",
                    "situp",
                    "exercise",
                    "dumbbell",
                    "kettlebell",
                    "strength",
                ),
            Activity.STRETCHING to
                listOf(
                    "stretch",
                    "yoga",
                    "medita",
                    "breath",
                    "mobility",
                    "pilates",
                    "posture",
                    "toes",
                    "cold shower",
                    "shower",
                    "tai chi",
                    "relax",
                ),
            Activity.READING to
                listOf(
                    "read",
                    "book",
                    "page",
                    "study",
                    "learn",
                    "language",
                    "vocab",
                    "flashcard",
                    "pray",
                ),
            Activity.TYPING to
                listOf(
                    "write",
                    "journal",
                    "plan",
                    "email",
                    "inbox",
                    "code",
                    "type",
                    "note",
                    "budget",
                    "work",
                    "focus",
                    "diary",
                    "gratitude",
                    "review",
                    "list",
                    "draft",
                ),
            Activity.WALKING to
                listOf(
                    "walk",
                    "run",
                    "jog",
                    "step",
                    "tidy",
                    "clean",
                    "dish",
                    "laundry",
                    "bed",
                    "water",
                    "cook",
                    "commute",
                    "dog",
                    "errand",
                    "clothes",
                ),
        )

    /**
     * What the person in the [nth] occupied window of a floor called [name] does: the first ones do
     * the habit itself; now and then someone walks past instead. A name nothing fits fills its
     * windows with everyday office life by turns.
     */
    fun activityFor(name: String, nth: Int): Activity {
        val own = activityOf(name) ?: return GENERIC[nth % GENERIC.size]
        return if (nth % 3 == 2 && own != Activity.WALKING) Activity.WALKING else own
    }

    private val GENERIC = arrayOf(Activity.TYPING, Activity.WALKING, Activity.READING)

    /**
     * Which pose of [activity] shows at [step] ([Ambient.step]) for a person of their own [seed].
     */
    fun frame(activity: Activity, step: Int, seed: Int): Int {
        val seq = SEQUENCES[activity.ordinal]
        val i = (step + abs(seed) % seq.size) % seq.size
        return seq[if (i < 0) i + seq.size else i]
    }

    /** The poses of [activity]. */
    fun poses(activity: Activity): Array<Pose> = POSES[activity.ordinal]

    /**
     * Where someone walking past stands across a window [width] wide at [seconds], from its left
     * edge, or NaN while they are out of it: they cross in about four seconds, then the window is
     * empty for a while. Standing still (0 seconds), they stand in the middle.
     */
    fun walkX(seconds: Float, seed: Int, width: Float): Float {
        if (seconds <= 0f) return width / 2f
        val cycle = 6.5f
        val t = ((seconds / cycle + Ambient.hash(seed, 0, 90)) % 1f + 1f) % 1f
        if (t >= .7f) return Float.NaN
        val u = t / .7f
        val x = -6f + (width + 12f) * u
        return if (Ambient.hash(seed, 0, 91) < .5f) x else width - x
    }

    /** Which way someone walking past faces: 1 right, -1 left. */
    fun walkDir(seed: Int): Float = if (Ambient.hash(seed, 0, 91) < .5f) 1f else -1f

    // ------------------------------------------------------------------ the poses

    private fun poly(vararg p: Float) = p

    private val LEGS_STANDING =
        arrayOf(
            poly(-3f, -8f, -1f, -8f, -1.2f, 0f, -3.4f, 0f),
            poly(1f, -8f, 3f, -8f, 3.4f, 0f, 1.2f, 0f),
            poly(-3.2f, -14.5f, 3.2f, -14.5f, 2.8f, -7.6f, -2.8f, -7.6f),
        )

    private fun standing(headX: Float, limbs: FloatArray, vararg extra: FloatArray) =
        Pose(headX, -16.9f, arrayOf(*LEGS_STANDING, *extra), limbs)

    private val STRETCHING =
        arrayOf(
            standing(0f, poly(-2.6f, -14f, -3.2f, -21f, 2.6f, -14f, 3.2f, -21f)),
            standing(0f, poly(-2.8f, -14f, -7.5f, -19.5f, 2.8f, -14f, 7.5f, -19.5f)),
            standing(-1.4f, poly(-2.6f, -14f, -6.2f, -19f, 2.6f, -14f, -1.8f, -21f)),
            standing(1.4f, poly(-2.6f, -14f, 1.8f, -21f, 2.6f, -14f, 6.2f, -19f)),
        )

    private val BAR_LOW =
        arrayOf(
            poly(-8f, -15f, 8f, -15f, 8f, -13.8f, -8f, -13.8f),
            poly(-8.8f, -16.6f, -7.2f, -16.6f, -7.2f, -12.2f, -8.8f, -12.2f),
            poly(7.2f, -16.6f, 8.8f, -16.6f, 8.8f, -12.2f, 7.2f, -12.2f),
        )
    private val BAR_HIGH =
        arrayOf(
            poly(-8f, -22.2f, 8f, -22.2f, 8f, -21f, -8f, -21f),
            poly(-8.8f, -23.8f, -7.2f, -23.8f, -7.2f, -19.4f, -8.8f, -19.4f),
            poly(7.2f, -23.8f, 8.8f, -23.8f, 8.8f, -19.4f, 7.2f, -19.4f),
        )

    private val LIFTING =
        arrayOf(
            standing(
                0f,
                poly(
                    -3f,
                    -14f,
                    -4.6f,
                    -11.4f,
                    -4.6f,
                    -11.4f,
                    -4f,
                    -14.4f,
                    3f,
                    -14f,
                    4.6f,
                    -11.4f,
                    4.6f,
                    -11.4f,
                    4f,
                    -14.4f,
                ),
                *BAR_LOW,
            ),
            standing(0f, poly(-3f, -14f, -4.4f, -21.6f, 3f, -14f, 4.4f, -21.6f), *BAR_HIGH),
        )

    private val DESK =
        arrayOf(
            poly(1.5f, -9.2f, 10.5f, -9.2f, 10.5f, -8.1f, 1.5f, -8.1f),
            poly(9.4f, -8.1f, 10.3f, -8.1f, 10.3f, 0f, 9.4f, 0f),
            poly(5f, -9.2f, 6.2f, -9.2f, 8.4f, -14f, 7.2f, -14f),
            poly(-4.6f, -6.6f, 1.4f, -6.6f, 1.4f, -5.6f, -4.6f, -5.6f),
            poly(-5.2f, -13f, -4.2f, -13f, -4.2f, -5.6f, -5.2f, -5.6f),
            poly(-2.2f, -5.6f, -1.2f, -5.6f, -1.2f, 0f, -2.2f, 0f),
        )
    private val SITTING =
        arrayOf(
            poly(-3.8f, -7f, -.2f, -7f, .6f, -14f, -3f, -14.3f),
            poly(-3.6f, -7.6f, 3f, -7.6f, 3f, -5.8f, -3.6f, -5.8f),
            poly(1.6f, -6.2f, 3f, -6.2f, 3.2f, 0f, 1.8f, 0f),
        )

    private val TYPING =
        arrayOf(
            Pose(
                -.6f,
                -16.7f,
                arrayOf(*DESK, *SITTING),
                poly(-.8f, -13.4f, 1.2f, -10f, 1.2f, -10f, 4.2f, -9.8f),
            ),
            Pose(
                -.6f,
                -16.7f,
                arrayOf(*DESK, *SITTING),
                poly(-.8f, -13.4f, 1.4f, -10.3f, 1.4f, -10.3f, 4.8f, -10.4f),
            ),
            Pose(
                -.9f,
                -16.9f,
                arrayOf(*DESK, *SITTING),
                poly(-.8f, -13.4f, 2f, -10.4f, 2f, -10.4f, .6f, -15.2f),
            ),
        )

    private val CHAIR =
        arrayOf(
            poly(-5f, -6.4f, 4f, -6.4f, 4f, -4.8f, -5f, -4.8f),
            poly(-6.2f, -14.4f, -4.6f, -14.4f, -4.6f, -4.8f, -6.2f, -4.8f),
            poly(-5f, -4.8f, -4f, -4.8f, -4f, 0f, -5f, 0f),
        )
    private val LOUNGING =
        arrayOf(
            poly(-4.4f, -6.5f, -1f, -6.5f, -.4f, -14f, -3.4f, -14.2f),
            poly(-4f, -7f, 2.8f, -7.6f, 2.8f, -5.8f, -4f, -5.4f),
            poly(1.6f, -6.4f, 3f, -6.4f, 3.6f, 0f, 2.2f, 0f),
        )
    private val BOOK = poly(2f, -14f, 4.2f, -12.4f, 4.2f, -9.6f, 2f, -11.2f)
    private val PAGE = poly(3.6f, -13f, 5.8f, -14.4f, 5.8f, -11.4f, 3.6f, -10.2f)

    private val READING =
        arrayOf(
            Pose(
                -.8f,
                -16.6f,
                arrayOf(*CHAIR, *LOUNGING, BOOK),
                poly(-1f, -13f, 1.2f, -10f, 1.2f, -10f, 2.4f, -11.6f),
            ),
            Pose(
                -.8f,
                -16.6f,
                arrayOf(*CHAIR, *LOUNGING, BOOK, PAGE),
                poly(-1f, -13f, 1.6f, -10.6f, 1.6f, -10.6f, 5f, -12.8f),
            ),
            Pose(
                -.4f,
                -16.1f,
                arrayOf(*CHAIR, *LOUNGING, BOOK),
                poly(-1f, -13f, 1.2f, -10f, 1.2f, -10f, 2.4f, -11.6f),
            ),
        )

    private val WALKER = poly(-1.6f, -14.5f, 1.6f, -14.5f, 1.4f, -7.8f, -1.4f, -7.8f)
    private val WALKING =
        arrayOf(
            Pose(
                .4f,
                -16.9f,
                arrayOf(
                    WALKER,
                    poly(-1f, -8f, 1f, -8f, 3.2f, 0f, 1.4f, 0f),
                    poly(-1f, -8f, 1f, -8f, -1.6f, 0f, -3.4f, 0f),
                ),
                poly(0f, -14f, 2.2f, -9.5f, 0f, -14f, -2.2f, -9.5f),
            ),
            Pose(
                .4f,
                -17.1f,
                arrayOf(WALKER, poly(-1.2f, -8f, 1.2f, -8f, 1f, 0f, -1f, 0f)),
                poly(0f, -14f, .5f, -9f),
            ),
        )

    private val POSES: Array<Array<Pose>> = arrayOf(TYPING, STRETCHING, READING, LIFTING, WALKING)

    private fun hold(vararg runs: Int): IntArray {
        // pairs of (pose, steps)
        val out = ArrayList<Int>()
        var i = 0
        while (i < runs.size) {
            repeat(runs[i + 1]) { out += runs[i] }
            i += 2
        }
        return out.toIntArray()
    }

    /** Each activity's poses in turn, a step at a time ([Ambient.PEOPLE_FPS] a second). */
    private val SEQUENCES: Array<IntArray> =
        arrayOf(
            // typing in bursts, then a pause to think
            hold(0, 2, 1, 2, 0, 2, 1, 2, 0, 2, 1, 2, 0, 2, 1, 2, 2, 14, 0, 2, 1, 2, 0, 2, 1, 2),
            // slow stretches, held
            hold(0, 14, 1, 12, 0, 8, 2, 14, 0, 6, 3, 14),
            // reading, a page now and then, a nod
            hold(0, 30, 1, 4, 0, 26, 2, 8),
            // reps, then a breather
            hold(0, 5, 1, 5, 0, 5, 1, 5, 0, 5, 1, 5, 0, 16),
            // a stride
            hold(0, 3, 1, 3),
        )

    /** How many steps a full turn of [activity]'s poses takes. */
    fun cycle(activity: Activity): Int = SEQUENCES[activity.ordinal].size
}
