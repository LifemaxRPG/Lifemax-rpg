package com.lifemax.rpg.feature.habits.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The people who walk the pavement, built for a close-up: the same skeleton as the window figures ([FigBuilder]
 * and [Gait] agree on the legs, so the feet land where the gait says), dressed so that the silhouette reads at a
 * hand's width on the phone: a coat with a hem, trousers or a skirt, shoes with a heel and a toe, hair, a bag, a
 * neck, hands that are hands. Limbs bow a little at the thigh and the calf; the far arm and leg are darker than
 * the near ones, so the figure has depth. What hangs loose (the hem, a ponytail, a bag) trails the motion by a
 * moment ([StreetGait.trail]). Pure data and arithmetic; the sprite bakery turns the shapes into pixels once.
 *
 * Space is the street's: units, x forward (the figure faces +x), y down, the feet on y = 0.
 */

/** A tee's cut and fabric pattern; the original pedestrian keeps its plain tucked-in tee. */
enum class Tee {
    PLAIN,
    LOOSE_STRIPED,
    PLAID_SHIRT,
    WORK_SHIRT,
    SPORT_SHIRT,
    STRIPED_HOODIE,
}

/** Optional face proportions, independent of hairstyle. Defaults preserve the original six. */
data class StreetFaceProfile(
    val nose: Float,
    val tip: Float,
    val lips: Float,
    val chin: Float,
    val brow: Float,
    val old: Boolean = false,
    val woman: Boolean = false,
    val skullWidth: Float = 1f,
    val skullHeight: Float = 1f,
    val cheek: Float = 0f,
    val jaw: Float = 0f,
    val moustache: Boolean = false,
    val goatee: Boolean = false,
    val beard: Boolean = false,
)

/** What a street walker wears on top. */
enum class Coat {
    /** A tee or a jumper: the torso's outline only. */
    NONE,
    /** A short jacket, its hem over the hips. */
    JACKET,
    /** A long coat, its hem at the knees, swinging. */
    LONG,
}

/** What a street walker carries. */
enum class Bag {
    NONE,
    SHOULDER,
    BACKPACK,
    HAND,
    CANVAS,
}

/** What a street walker has on their feet. */
enum class Shoe {
    /** Leather lace-ups: a toe cap, a welt. */
    LEATHER,
    /** Trainers: laces, a stripe down the side, a bumper round the toe. */
    TRAINER,
    /** Ankle boots: the shaft up over the ankle. */
    BOOT,
    /** Flat slip-ons: a strap over the instep. */
    FLAT,
    SANDAL,
}

/** A street walker's hair (or hat). */
enum class StreetHair {
    CROP,
    PONY,
    BUN,
    LONG,
    BEANIE,
    CAP,
    /** A tweed flat cap, grey hair under it. */
    FLAT,
    /** Short hair above the ears, a softly irregular cropped crown. */
    CLOSE_CROP,
    /** Long hair under an unbranded forward-facing baseball cap. */
    LONG_CAP,
    /** Close, finely textured crop with no hat or detached crown shapes. */
    TIGHT_CROP,
    /** Short hair with a higher front hairline and fuller hair at the sides. */
    SHORT_RECEDING,
    /** Tall swept-up crown with short sides and a continuous nape. */
    HIGH_QUIFF,
}

/**
 * What a street walker's things are coloured (RGB): their [skin], [hair] (and [hat]), the [coat]
 * (or jacket), the [top] under it (or the tee), the [legs] (trousers, or tights under a skirt), the
 * [skirt], the [shoe]'s upper and its [sole], and the [bag].
 */
class Wear(
    val skin: Int,
    val hair: Int,
    val hat: Int,
    val coat: Int,
    val top: Int,
    val legs: Int,
    val skirt: Int,
    val shoe: Int,
    val sole: Int,
    val bag: Int,
)

/**
 * One street walker's look: build, clothes, hair and bag, and their colours; [elder] for an older
 * person (who walks as one: [StreetGait.style]), [stick] for one who walks with a stick in the near
 * hand.
 */
class StreetLook(
    val height: Float,
    val build: Float,
    val head: Float,
    val coat: Coat,
    val hair: StreetHair,
    val bag: Bag,
    val skirt: Boolean,
    val wear: Wear,
    val elder: Boolean = false,
    val stick: Boolean = false,
    val shoes: Shoe = Shoe.LEATHER,
    val shorts: Boolean = false,
    val tee: Tee = Tee.PLAIN,
    val face: StreetFaceProfile? = null,
    val plainTrainers: Boolean = false,
    val armScale: Float = 1f,
    val chestDepth: Float = 1f,
    val backDepth: Float = 1f,
    val neckScale: Float = 1f,
    val neckForward: Float = 0f,
    val headLag: Float = 1f,
    val walkStyle: Gait.Style? = null,
    val cargoShorts: Boolean = false,
    val shortsLength: Float = .96f,
    val sockHeight: Float = .38f,
    /** Palm/finger proportions are independent of arm thickness. */
    val handScale: Float = 1f,
    val underShirt: Int? = null,
    val belly: Float = 0f,
    val towel: Int? = null,
    val sportPanel: Int? = null,
)

object StreetLooks {
    val ALL =
        arrayOf(
            // a camel long coat over a pale shirt, charcoal trousers, brown brogues
            StreetLook(
                1.04f,
                .92f,
                .98f,
                Coat.LONG,
                StreetHair.CROP,
                Bag.SHOULDER,
                false,
                Wear(
                    skin = 0xD7A27C,
                    hair = 0x3A2619,
                    hat = 0x3A2619,
                    coat = 0xA87B4C,
                    top = 0xE4DED2,
                    legs = 0x3B3E46,
                    skirt = 0,
                    shoe = 0x5C3520,
                    sole = 0x2A1A12,
                    bag = 0x2C2624,
                ),
            ),
            // a navy jacket, a red beanie, jeans, white trainers, a mustard backpack
            StreetLook(
                .97f,
                1.12f,
                1.02f,
                Coat.JACKET,
                StreetHair.BEANIE,
                Bag.BACKPACK,
                false,
                Wear(
                    skin = 0x7C4E33,
                    hair = 0x1C1512,
                    hat = 0xB2362C,
                    coat = 0x2D3D62,
                    top = 0x9EA4AC,
                    legs = 0x45618A,
                    skirt = 0,
                    shoe = 0xE2E2DC,
                    sole = 0xB9B9B2,
                    bag = 0xC48F2E,
                ),
                shoes = Shoe.TRAINER,
            ),
            // a green jacket, a burgundy skirt over dark tights, auburn ponytail, a tan handbag
            StreetLook(
                .95f,
                .9f,
                1.04f,
                Coat.JACKET,
                StreetHair.PONY,
                Bag.HAND,
                true,
                Wear(
                    skin = 0xF0C9A8,
                    hair = 0x8E4426,
                    hat = 0x8E4426,
                    coat = 0x3E6B58,
                    top = 0xEDE6D6,
                    legs = 0x2E2A33,
                    skirt = 0x7C2E3C,
                    shoe = 0x24201F,
                    sole = 0x141212,
                    bag = 0xA86C3C,
                ),
                shoes = Shoe.BOOT,
            ),
            // a coral tee, a navy cap, stone chinos, grey trainers (the cyclist too)
            StreetLook(
                1.02f,
                .96f,
                1f,
                Coat.NONE,
                StreetHair.CAP,
                Bag.NONE,
                false,
                Wear(
                    skin = 0xB57E58,
                    hair = 0x1E1916,
                    hat = 0x24324F,
                    coat = 0xC8553F,
                    top = 0xC8553F,
                    legs = 0x8E8266,
                    skirt = 0,
                    shoe = 0x2E3A55,
                    sole = 0xE4E4DE,
                    bag = 0x2C2624,
                ),
                shoes = Shoe.TRAINER,
            ),
            // an older woman: silver hair in a bun, a plum long coat, slate trousers, flat brown
            // shoes, a small shoulder bag
            StreetLook(
                .95f,
                .98f,
                1f,
                Coat.LONG,
                StreetHair.BUN,
                Bag.SHOULDER,
                false,
                Wear(
                    skin = 0xE6BFA0,
                    hair = 0xC9C6C0,
                    hat = 0xC9C6C0,
                    coat = 0x6A4A5C,
                    top = 0xDCD6CC,
                    legs = 0x4A4F58,
                    skirt = 0,
                    shoe = 0x4A3226,
                    sole = 0x241A14,
                    bag = 0x3A2C26,
                ),
                elder = true,
                shoes = Shoe.FLAT,
            ),
            // an older man: a tweed flat cap over grey hair, a charcoal-olive overcoat, brown
            // trousers and shoes, a walking stick
            StreetLook(
                .97f,
                1.02f,
                1f,
                Coat.LONG,
                StreetHair.FLAT,
                Bag.NONE,
                false,
                Wear(
                    skin = 0xC99878,
                    hair = 0xA9A6A2,
                    hat = 0x6E6450,
                    coat = 0x474B42,
                    top = 0xCFC8BA,
                    legs = 0x5B4E42,
                    skirt = 0,
                    shoe = 0x3E2A1E,
                    sole = 0x1E1612,
                    bag = 0x2C2624,
                ),
                elder = true,
                stick = true,
            ),
        )

    val COUNT: Int
        get() = ALL.size
}

/**
 * Where a baked walker's parts are, for what the painter hangs on them: the feet (x along the walk,
 * [footNH] and [footFH] their height over the pavement), the near hand (for a dog's lead) and the
 * head's top.
 */
class WalkerJoints {
    var footNX = 0f
    var footNH = 0f
    var footFX = 0f
    var footFH = 0f
    var handNX = 0f
    var handNY = 0f
    var top = 0f

    /**
     * What the layers are hung on ([StreetLayers]): the near and the far shoulder, the neck's pivot
     * (the head layer turns about it, the bob's lag in it), the head's angle from upright, the
     * arms' angles as the walk has them (as [K.AN], [K.EN], [K.AF], [K.EF]) and the ponytail's
     * swing (-1 to 1).
     */
    var shNX = 0f
    var shNY = 0f
    var shFX = 0f
    var shFY = 0f
    var neckX = 0f
    var neckY = 0f
    var headA = 0f
    var an = 0f
    var en = 0f
    var af = 0f
    var ef = 0f
    var tail = 0f
}

/** The shapes of one street walker in one pose, with where its joints are. */
class StreetFigure(val shapes: List<Shape>, val joints: WalkerJoints)

object StreetRig {
    /** The far arm and leg are this much of the near ones' colour, a touch cooler. */
    const val FAR = .64f

    /** [rgb] darkened to [k] of itself (and cooled a little with it). */
    fun dim(rgb: Int, k: Float): Int {
        if (k >= 1f) return rgb
        val c = 1f - k
        val r = ((rgb shr 16 and 255) * k * (1f - .05f * c)).toInt().coerceIn(0, 255)
        val g = ((rgb shr 8 and 255) * k).toInt().coerceIn(0, 255)
        val b = ((rgb and 255) * k * (1f + .1f * c)).toInt().coerceIn(0, 255)
        return (r shl 16) or (g shl 8) or b
    }

    /** From [a] towards [b] by [t]. */
    fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) =
            ((a shr s and 255) + ((b shr s and 255) - (a shr s and 255)) * t)
                .toInt()
                .coerceIn(0, 255)
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** The legs, as [Gait] has them (units at height 1). */
    private const val THIGH = 5.9f
    private const val SHIN = 5.7f

    /** The figure of [look] at [phi] of its walk. */
    fun figure(look: Int, phi: Float): StreetFigure =
        build(StreetLooks.ALL[look], StreetGait.pose(phi, look), phi)

    /**
     * Preview/group appearance using an existing gait; does not enlarge the ambient sprite cache.
     */
    fun figure(
        look: StreetLook,
        phi: Float,
        motionLook: Int = 3,
        glance: Float = 0f,
    ): StreetFigure = build(look, appearanceWalk(look, phi, motionLook), phi, glance = glance)

    /** Full animation of a new appearance, including the existing stop and crouch poses. */
    fun frame(look: StreetLook, frame: Int, motionLook: Int = 3, glance: Float = 0f): StreetFigure {
        require(frame in 0 until TOTAL_FRAMES)
        return build(
            look,
            appearancePose(look, frame, motionLook),
            if (frame < StreetGait.FRAMES) frame / StreetGait.FRAMES.toFloat() else Gait.PASSING,
            glance = glance,
        )
    }

    /**
     * Character-specific motion stays in the real rig; the preview never moves the head separately.
     */
    private fun appearanceWalk(look: StreetLook, phi: Float, reference: Int): FloatArray =
        look.walkStyle?.let { Gait.walkPose(phi, it) } ?: StreetGait.pose(phi, reference)

    private fun appearancePose(look: StreetLook, frame: Int, reference: Int): FloatArray {
        if (look.walkStyle == null) return poseOf(reference, frame)
        return when {
            frame < SETTLE_AT ->
                appearanceWalk(look, frame / StreetGait.FRAMES.toFloat(), reference)
            frame < CROUCH_AT ->
                Gait.between(appearanceWalk(look, Gait.PASSING, reference), STAND, SETTLE_FRAMES)[
                        frame - SETTLE_AT]
            else -> CROUCHING[frame - CROUCH_AT]
        }
    }

    /** The figure of [look] in the pose [p], with what hangs loose as at [phi] of a walk. */
    fun figure(look: Int, p: FloatArray, phi: Float): StreetFigure =
        build(StreetLooks.ALL[look], p, phi)

    /** Frames baked after the walk's: the settle from the passing pose to standing still. */
    const val SETTLE_FRAMES = 4

    /** Standing still, side on, the arms hanging, the weight on both feet. */
    val STAND: FloatArray =
        pose(
            turn = .94f,
            lean = 1.2f,
            head = -1f,
            an = 3f,
            en = 9f,
            af = -3f,
            ef = 8f,
            ln = 1.5f,
            kn = 3f,
            lf = -1.5f,
            kf = 3f,
        )

    /** The settle's poses: from the passing pose, eased, to [STAND] (the last one). */
    val SETTLE: List<FloatArray> by lazy { settle(-1) }

    /** [SETTLE] from look [look]'s own passing pose. */
    fun settle(look: Int): List<FloatArray> =
        Gait.between(StreetGait.pose(Gait.PASSING, look), STAND, SETTLE_FRAMES)

    private val SETTLES: Array<List<FloatArray>?> = arrayOfNulls(StreetLooks.COUNT)

    private fun settleOf(look: Int): List<FloatArray> =
        SETTLES[look] ?: settle(look).also { SETTLES[look] = it }

    /**
     * Frames baked after the settle's: from standing down to a crouch over the near foot (to tie a
     * shoe), the far knee on the pavement.
     */
    const val CROUCH_FRAMES = 5

    /** The first settle frame, the standing one, and the first crouch frame. */
    const val SETTLE_AT = StreetGait.FRAMES
    const val STAND_AT = SETTLE_AT + SETTLE_FRAMES - 1
    const val CROUCH_AT = SETTLE_AT + SETTLE_FRAMES

    /**
     * Down on the far knee over the near foot: the hips gone back and down (the near foot stays
     * where it stood, the far one steps back to kneel), the back bent over the shoe.
     */
    val CROUCH: FloatArray =
        pose(
            x = -4.6f,
            turn = .94f,
            lean = 32f,
            head = -6f,
            an = 30f,
            en = 40f,
            af = 25f,
            ef = 40f,
            ln = 92f,
            kn = 98f,
            lf = -17f,
            kf = 74f,
            ann = 4f,
            anf = -48f,
        )

    /** The crouch's poses: from [STAND], eased, to [CROUCH] (the last one). */
    val CROUCHING: List<FloatArray> by lazy { Gait.between(STAND, CROUCH, CROUCH_FRAMES) }

    /** The pose of [look]'s frame [frame]: the walk's, the settle's or the crouch's. */
    private fun poseOf(look: Int, frame: Int): FloatArray =
        when {
            frame < SETTLE_AT -> StreetGait.pose(frame / StreetGait.FRAMES.toFloat(), look)
            frame < CROUCH_AT -> settleOf(look)[frame - SETTLE_AT]
            else -> CROUCHING[frame - CROUCH_AT]
        }

    /** Frame [frame] of [look]: the walk's [StreetGait.FRAMES], then the settle's, the crouch's. */
    fun frame(look: Int, frame: Int): StreetFigure =
        if (frame < StreetGait.FRAMES) figure(look, frame / StreetGait.FRAMES.toFloat())
        else figure(look, poseOf(look, frame), Gait.PASSING)

    /** Every frame a look has. */
    const val TOTAL_FRAMES = StreetGait.FRAMES + SETTLE_FRAMES + CROUCH_FRAMES

    /**
     * Frame [frame] of [look] as the street bakes it, in layers: the body alone (the legs, the
     * torso, the bag, the neck's root), the arms and the head left off and their anchors and angles
     * in the joints, so that the arms and the head can do something other than walk
     * ([StreetLayers]) while the legs keep the walk's every frame.
     */
    fun body(look: Int, frame: Int): StreetFigure {
        val p = poseOf(look, frame)
        val phi =
            if (frame < StreetGait.FRAMES) frame / StreetGait.FRAMES.toFloat() else Gait.PASSING
        return build(StreetLooks.ALL[look], p, phi, false)
    }

    private val JOINTS: Array<Array<WalkerJoints>?> = arrayOfNulls(StreetLooks.COUNT)

    /** The joints of [look]'s every frame (as [body] has them), worked out once. */
    fun joints(look: Int): Array<WalkerJoints> =
        JOINTS[look] ?: Array(TOTAL_FRAMES) { body(look, it).joints }.also { JOINTS[look] = it }

    // ---- the layers hung on the body: each in its own frame, turned about its joint when drawn

    /**
     * Which hand a forearm layer ends in: open and relaxed, closed round something, in a pocket.
     */
    const val OPEN = 0
    const val GRIP = 1
    const val POCKET = 2

    /**
     * How far above the elbow (and below the neck's pivot) a layer's limb runs on for its shading.
     */
    private const val STUB = 1.6f

    /**
     * The upper arm of [look] ([near] or far), hanging from the shoulder at 0, 0 down +y: turned
     * about the shoulder by its angle when drawn.
     */
    fun upperArm(look: Int, near: Boolean): List<Shape> {
        val l = StreetLooks.ALL[look]
        val o = Out()
        armShapes(o, WalkerJoints(), l, 0f, 0f, 0f, 0f, near, ARM_UPPER or ARM_STUB, OPEN)
        return o.shapes
    }

    /**
     * The radius of [look]'s shoulder: what an [upperArm] layer's stub above the shoulder is cut
     * back to, in units.
     */
    fun shoulderR(look: Int): Float {
        val l = StreetLooks.ALL[look]
        return if (l.coat == Coat.NONE) 1.22f * l.build else 1.1f * l.build * thick(l)
    }

    /**
     * The forearm and the [hand] of [look] ([near] or far), from the elbow at 0, 0 down +y. The
     * limb is shaded as if it ran on [STUB] above the elbow, so the joint shows no edge; [cut]
     * takes that part off again (everything above the elbow outside its round).
     */
    fun forearm(look: Int, near: Boolean, hand: Int): List<Shape> {
        val l = StreetLooks.ALL[look]
        val o = Out()
        // the shoulder above the elbow, so the elbow is at 0, 0
        val sy = -upperLength(look)
        armShapes(o, WalkerJoints(), l, 0f, sy, 0f, 0f, near, ARM_FORE or ARM_STUB, hand)
        return o.shapes
    }

    /** The radius of [look]'s elbow (what [forearm]'s stub is cut to), in units. */
    fun elbowR(look: Int): Float {
        val l = StreetLooks.ALL[look]
        return .82f * l.build * thick(l)
    }

    /**
     * How far above the elbow a [forearm] layer's stub runs (and above the shoulder an [upperArm]
     * layer's), in units.
     */
    const val FORE_STUB = STUB

    private fun thick(l: StreetLook) = if (l.coat != Coat.NONE) 1.12f else 1f

    /**
     * The head of [look] upright, the neck's pivot at 0, 0 (the neck itself stays on the body): the
     * face in profile, or [glance]d round towards us. A ponytail is left off ([tail]): it swings on
     * its own.
     */
    fun head(look: Int, glance: Boolean): List<Shape> = head(look, if (glance) 1f else 0f)

    /**
     * The head of [look] as [head] has it, turned [g]: 0 in profile, 1 glanced round towards us, -1
     * turned away from us (to a shop window): the ear comes forward and the face goes round the far
     * side, only the eye's corner showing.
     */
    fun head(look: Int, g: Float): List<Shape> = head(StreetLooks.ALL[look], g)

    /** Same face layer as the street, for an appearance that is not in random traffic yet. */
    fun head(l: StreetLook, g: Float): List<Shape> {
        val o = Out()
        val hgt = l.height
        val rx = 1.35f * hgt * l.head * (l.face?.skullWidth ?: 1f)
        val ry = 1.65f * hgt * l.head * (l.face?.skullHeight ?: 1f)
        val neck = 1.0f * hgt
        val hy = -(neck * .6f + ry * .92f)
        head(o, l.wear, l.hair, 0f, hy, rx, ry, 0f, .94f, g, l.face)
        hair(o, l.hair, l.wear, 0f, hy, rx, ry, 0f, hgt, 0f, false)
        return o.shapes
    }

    /** Where [look]'s ponytail hangs from in [head]'s frame, x then y. */
    fun tailRootX(look: Int): Float {
        val l = StreetLooks.ALL[look]
        return -1.35f * l.height * l.head * .95f
    }

    fun tailRootY(look: Int): Float {
        val l = StreetLooks.ALL[look]
        val ry = 1.65f * l.height * l.head
        return -(l.height * .6f + ry * .92f) - ry * .3f
    }

    /** [look]'s ponytail hanging from its root at 0, 0: turned by its swing when drawn. */
    fun tail(look: Int): List<Shape> {
        val l = StreetLooks.ALL[look]
        val o = Out()
        if (l.hair == StreetHair.PONY) ponytail(o, l.wear.hair, 0f, 0f, 0f, l.height, 0f)
        return o.shapes
    }

    /** How far a ponytail's swing turns the whole tail, in degrees, for a swing of 1. */
    const val TAIL_SWING = 20f

    /** A phone held in the hand at 0, 0, its top up -y, the screen on its back (-x) side. */
    fun phone(): List<Shape> {
        val o = Out()
        o.add(box(0f, -.55f, .3f, 1.05f, 0f), 0x24262C, Mat.LEATHER)
        o.add(FigBuilder.capsule(-.2f, -1.45f, .09f, -.2f, .35f, .09f, 3), 0xA8C4DC, Mat.FLAT, .85f)
        // the screen's lit edge, brightest at the top, and the light it spills past the case
        o.add(FigBuilder.capsule(-.3f, -1.5f, .07f, -.3f, -.3f, .06f, 3), 0xE6F2FF, Mat.FLAT, .9f)
        o.add(FigBuilder.capsule(-.36f, -1.4f, .16f, -.36f, .1f, .12f, 3), 0xBFD8F0, Mat.FLAT, .3f)
        // the case's edge catching the light on the other side
        o.add(FigBuilder.capsule(.24f, -1.5f, .05f, .24f, .3f, .05f, 3), 0x6A6E78, Mat.FLAT, .6f)
        return o.shapes
    }

    /** A takeaway coffee held in the hand at 0, 0, upright: the paper cup, its sleeve, the lid. */
    fun cup(): List<Shape> {
        val o = Out()
        val top = -1.15f
        val bottom = .75f
        o.add(
            FigBuilder.chaikin(floatArrayOf(-.62f, top, .62f, top, .48f, bottom, -.48f, bottom), 1),
            0xEEE9E0,
        )
        o.add(
            FigBuilder.chaikin(floatArrayOf(-.6f, -.55f, .6f, -.55f, .54f, .3f, -.54f, .3f), 1),
            0xA0703F,
        )
        // the sleeve's printed band and its edges
        o.add(FigBuilder.capsule(-.56f, -.55f, .06f, .56f, -.55f, .06f, 3), 0x6E4A28, Mat.FLAT, .6f)
        o.add(FigBuilder.capsule(-.52f, .3f, .06f, .52f, .3f, .06f, 3), 0x6E4A28, Mat.FLAT, .6f)
        o.add(FigBuilder.ellipse(.05f, -.12f, .2f, .2f, 0f, 6), 0xEEE9E0, Mat.FLAT, .7f)
        // the light down the cup's lit side
        o.add(
            FigBuilder.capsule(-.38f, top + .25f, .09f, -.32f, bottom - .15f, .07f, 3),
            0xFFFFFF,
            Mat.FLAT,
            .35f,
        )
        o.add(FigBuilder.capsule(-.66f, top - .08f, .17f, .66f, top - .08f, .17f, 3), 0x2B2A2A)
        o.add(FigBuilder.capsule(-.36f, top - .32f, .14f, .36f, top - .32f, .14f, 3), 0x2B2A2A)
        // the lid's rim and its sip hole catching the light
        o.add(
            FigBuilder.capsule(-.6f, top - .16f, .06f, .6f, top - .16f, .06f, 3),
            0x8A8886,
            Mat.FLAT,
            .6f,
        )
        o.add(
            FigBuilder.capsule(.12f, top - .44f, .05f, .3f, top - .44f, .05f, 3),
            0x8A8886,
            Mat.FLAT,
            .5f,
        )
        return o.shapes
    }

    /** [look]'s handbag hanging from the hand at 0, 0 down +y (it swings as it is drawn). */
    fun handbag(look: Int): List<Shape> {
        val l = StreetLooks.ALL[look]
        val o = Out()
        val j = WalkerJoints()
        handbag(o, l.wear, j, l.height, l.build, 0f)
        return o.shapes
    }

    /**
     * [look]'s walking stick hanging from its handle at 0, 0 down +y ([stickLength] long): the
     * crook over the hand, the shaft, the rubber ferrule (none for a look without a stick).
     */
    fun stick(look: Int): List<Shape> {
        val l = StreetLooks.ALL[look]
        val o = Out()
        if (!l.stick) return o.shapes
        val len = stickLength(look)
        val wood = 0x4A3222
        o.add(FigBuilder.capsule(0f, -.2f, .3f, 0f, len - .5f, .26f, 6), wood, Mat.LEATHER)
        // the crook: over the top and a little way down the front
        o.add(
            FigBuilder.capsule(-.4f, -.55f, .3f, 1.0f, -.5f, .28f, 4),
            dim(wood, .9f),
            Mat.LEATHER,
        )
        o.add(
            FigBuilder.capsule(1.05f, -.5f, .27f, 1.25f, .15f, .25f, 3),
            dim(wood, .9f),
            Mat.LEATHER,
        )
        o.add(
            FigBuilder.capsule(0f, len - .7f, .34f, 0f, len - .15f, .34f, 3),
            0x1A1714,
            Mat.RUBBER,
        )
        // the handle's polish along the crook's top, the light down the shaft, a brass collar
        // above the ferrule
        o.add(
            FigBuilder.capsule(-.3f, -.68f, .1f, .95f, -.64f, .09f, 3),
            mix(wood, 0xFFFFFF, .45f),
            Mat.FLAT,
            .6f,
        )
        o.add(
            FigBuilder.capsule(-.12f, .3f, .07f, -.1f, len - 1.2f, .06f, 3),
            mix(wood, 0xFFFFFF, .3f),
            Mat.FLAT,
            .35f,
        )
        o.add(FigBuilder.capsule(0f, len - .82f, .3f, 0f, len - .72f, .3f, 3), 0xA8884A, Mat.METAL)
        return o.shapes
    }

    /**
     * How long [look]'s stick is, handle to tip, units: as high as their hand hangs with the elbow
     * a little bent, from their own standing frame.
     */
    fun stickLength(look: Int): Float {
        val l = StreetLooks.ALL[look]
        return 9.6f * l.height
    }

    /** How far a handbag's swing turns it, degrees, for a trail of 1. */
    const val BAG_SWING = 9f

    /**
     * How tall [look] stands, in units, at the rig's size: from the pavement to the top of the
     * head.
     */
    fun height(look: Int): Float = -figure(look, Gait.PASSING).joints.top

    private fun rad(d: Float) = d * (PI.toFloat() / 180f)

    private class Out {
        val shapes = ArrayList<Shape>()

        fun add(pts: FloatArray, rgb: Int, mat: Int = Mat.CLOTH, alpha: Float = 1f) {
            shapes += Shape(Shape.BODY, pts, 1f, rgb, mat, alpha)
        }
    }

    /**
     * The figure of [look] in the pose [p] at [phi]: [whole], or the body alone with the arms' and
     * the head's anchors in the joints ([body]).
     */
    private fun build(
        look: StreetLook,
        p: FloatArray,
        phi: Float,
        whole: Boolean = true,
        glance: Float = 0f,
    ): StreetFigure {
        val o = Out()
        val j = WalkerJoints()
        val f = phi - kotlin.math.floor(phi)
        val hgt = look.height
        val b = look.build
        val hs = look.head
        val torsoL = 7.4f * hgt
        val neck = 1.0f * hgt
        val headRy = 1.65f * hgt * hs * (look.face?.skullHeight ?: 1f)
        val headRx = 1.35f * hgt * hs * (look.face?.skullWidth ?: 1f)
        val th = THIGH * hgt
        val sh = SHIN * hgt
        val turn = p[K.TURN]
        val lean = p[K.LEAN]
        val coat = look.coat
        val thick = if (coat != Coat.NONE) 1.12f else 1f
        val trail = StreetGait.trail(f)
        val trail2 = StreetGait.trail2(f)

        val hw = (FigBuilder.lerp(1.5f, .4f, turn) + p[K.TWIST]) * b
        val px = p[K.X]
        val py = 0f
        val ux = sin(rad(lean))
        val uy = -cos(rad(lean))
        val nx = cos(rad(lean))
        val ny = sin(rad(lean))
        val sx = px + ux * torsoL
        val sy = py + uy * torsoL
        val sw = (FigBuilder.lerp(2.9f, .5f, turn) + p[K.SHX]) * b

        // ---- the far arm, the far leg, then the near leg (a coat is drawn over the thighs after)
        val farTone = FAR
        val arms = if (whole) ARM_UPPER or ARM_FORE else 0
        armShapes(o, j, look, sx - nx * sw, sy - ny * sw, p[K.AF], p[K.EF], false, arms, OPEN)
        leg(o, j, px - hw, py, th, sh, b, hgt, p[K.LF], p[K.KF], p[K.ANF], farTone, false, look)
        if (look.bag == Bag.BACKPACK)
            backpack(o, look.wear, px, py, sx, sy, nx, ny, ux, uy, b, hgt, trail2)
        leg(o, j, px + hw, py, th, sh, b, hgt, p[K.LN], p[K.KN], p[K.ANN], 1f, true, look)

        // ---- the skirt over the thighs
        if (look.skirt) skirt(o, look.wear, px, py, th, b, p, trail)

        // ---- the torso: the body's own side profile (the chest forward, the shoulder blades and
        // the seat back, the small of the back and the waist in), and the clothes over it, each
        // with its own fall: a tee close over the body, a jacket hanging straight from the chest
        // and the shoulder blades to its hem, a long coat from the shoulder blades to below the
        // knee, its skirts swinging with the walk
        val w = look.wear
        val torsoRgb = if (coat == Coat.NONE) w.top else w.coat
        val cloth = clothOf(look)
        val woman = look.skirt || look.hair == StreetHair.BUN || look.hair == StreetHair.PONY
        val shoulderW = FigBuilder.lerp(3.3f, 1.9f, turn) * b * thick
        val chestW = FigBuilder.lerp(3.0f, 2.1f, turn) * b * thick
        val hipW = FigBuilder.lerp(2.8f, 2.1f, turn) * b
        // a point on the torso: [up] of the way from the hips to the shoulders, [out] towards the
        // front (negative: the back)
        fun tx(up: Float, out: Float) = px + ux * torsoL * up + nx * out
        fun ty(up: Float, out: Float) = py + uy * torsoL * up + ny * out
        val st = BODY_T
        val ns = st.size
        val bodyF = FloatArray(ns)
        val bodyB = FloatArray(ns)
        for (i in 0 until ns) {
            var f = BODY_F[i]
            var bk = BODY_B[i]
            if (woman) {
                f += BODY_WOMAN_F[i]
                bk += BODY_WOMAN_B[i]
            }
            bodyF[i] = FigBuilder.lerp(BODY_W[i], f * look.chestDepth, turn) * b
            bodyB[i] = FigBuilder.lerp(BODY_W[i], bk * look.backDepth, turn) * b
            if (look.belly != 0f) {
                val u = ((st[i] - .30f) / .50f).coerceIn(-1f, 1f)
                bodyF[i] += look.belly * b * (1f - u * u)
            }
        }
        // the station nearest [t]'s front and back, eased between
        fun at(arr: FloatArray, t: Float): Float {
            if (t <= st[0]) return arr[0]
            for (i in 1 until ns) if (t <= st[i]) {
                val k = (t - st[i - 1]) / (st[i] - st[i - 1])
                return arr[i - 1] + (arr[i] - arr[i - 1]) * k
            }
            return arr[ns - 1]
        }
        val topT = st[ns - 1]
        val looseTee =
            look.tee == Tee.LOOSE_STRIPED ||
                look.tee == Tee.PLAID_SHIRT ||
                look.tee == Tee.WORK_SHIRT ||
                look.tee == Tee.SPORT_SHIRT ||
                look.tee == Tee.STRIPED_HOODIE
        val teeHem = if (look.tee == Tee.WORK_SHIRT) .12f else if (looseTee) -.12f else .1f
        val teeEase = if (looseTee) .29f * b else .12f * b
        // Blend a hanging shirt with the underlying ribcage/waist instead of clamping the
        // entire torso to its widest station. A little low-front fullness and trailing hem
        // keep it relaxed without a rigid rectangular outline or an athletic pinched waist.
        fun teeFront(t: Float): Float {
            if (!looseTee) return at(bodyF, t) + teeEase
            val u = t.coerceIn(0f, 1f)
            val hang = at(bodyF, .18f) + (at(bodyF, .78f) - at(bodyF, .18f)) * u
            val fall = (1f - u) * (1f - u)
            return .60f * at(bodyF, t) + .40f * hang + teeEase + .16f * b * (1f - u) -
                trail * .055f * fall
        }
        fun teeBack(t: Float): Float {
            if (!looseTee) return at(bodyB, t) + teeEase
            val u = t.coerceIn(0f, 1f)
            val hang = at(bodyB, .12f) + (at(bodyB, .82f) - at(bodyB, .12f)) * u
            val fall = (1f - u) * (1f - u)
            return .72f * at(bodyB, t) + .28f * hang + teeEase + trail * .055f * fall
        }
        val pts = ArrayList<Float>()
        fun put(t: Float, out: Float) {
            pts += tx(t, out)
            pts += ty(t, out)
        }
        fun take(rgb: Int, mat: Int, smooth: Int = 2) {
            o.add(FigBuilder.chaikin(pts.toFloatArray(), smooth), rgb, mat)
            pts.clear()
        }
        // the seat and the hips in the trousers (under a tee or a jacket)
        if (!look.skirt && coat != Coat.LONG) {
            for (t in floatArrayOf(.24f, .1f, 0f, -.08f)) put(t, at(bodyF, t) + .02f)
            put(-.13f, bodyF[0] * .4f)
            put(-.14f, -bodyB[0] * .3f)
            for (t in floatArrayOf(-.08f, 0f, .1f, .24f)) put(t, -at(bodyB, t) - .02f)
            take(
                w.legs,
                if (look.tee == Tee.WORK_SHIRT) Mat.FLAT
                else if (look.bag == Bag.BACKPACK) Mat.DENIM else Mat.CLOTH,
            )
        }
        var hem: FloatArray? = null
        when (coat) {
            Coat.NONE -> {
                // A loose tee hangs over the waistband; a normal tee keeps its existing cut.
                for (i in 0 until ns) if (st[i] >= teeHem)
                    put(st[i], if (looseTee) teeFront(st[i]) else bodyF[i] + teeEase)
                for (i in ns - 1 downTo 0) if (st[i] >= teeHem)
                    put(st[i], if (looseTee) -teeBack(st[i]) else -bodyB[i] - teeEase)
                put(teeHem, -teeBack(teeHem))
                put(teeHem, teeFront(teeHem))
                take(w.top, Mat.CLOTH)
                if (look.tee == Tee.LOOSE_STRIPED || look.tee == Tee.PLAID_SHIRT) {
                    // Cloth bands follow the body's own contour, never screen-aligned rectangles.
                    fun stripe(lo: Float, hi: Float, rgb: Int) {
                        put(lo, teeFront(lo) - .025f)
                        for (i in 0 until ns) if (st[i] > lo && st[i] < hi)
                            put(st[i], teeFront(st[i]) - .025f)
                        put(hi, teeFront(hi) - .025f)
                        put(hi, -teeBack(hi) + .025f)
                        for (i in ns - 1 downTo 0) if (st[i] > lo && st[i] < hi)
                            put(st[i], -teeBack(st[i]) + .025f)
                        put(lo, -teeBack(lo) + .025f)
                        take(rgb, Mat.FLAT, 0)
                    }
                    if (look.tee == Tee.LOOSE_STRIPED) {
                        for (u in floatArrayOf(-.035f, .145f, .325f, .505f, .685f, .865f)) {
                            stripe(u, u + .062f, StreetCameos.STRIPE_DARK)
                            stripe(u + .013f, u + .021f, StreetCameos.STRIPE_LIGHT)
                            stripe(u + .044f, u + .052f, StreetCameos.STRIPE_LIGHT)
                        }
                    } else {
                        // Woven checks, in shirt coordinates, clipped to its draping silhouette.
                        for (u in floatArrayOf(-.04f, .14f, .32f, .50f, .68f, .86f)) {
                            stripe(u, u + .040f, StreetCameos.PLAID_LIGHT)
                            stripe(u + .052f, u + .064f, StreetCameos.PLAID_DARK)
                        }
                        fun vertical(lo: Float, hi: Float, rgb: Int) {
                            fun clip(x: Float, t: Float) =
                                x.coerceIn(-teeBack(t) + .02f, teeFront(t) - .02f)
                            put(teeHem, clip(hi, teeHem))
                            for (i in 0 until ns) if (st[i] >= teeHem) put(st[i], clip(hi, st[i]))
                            for (i in ns - 1 downTo 0) if (st[i] >= teeHem)
                                put(st[i], clip(lo, st[i]))
                            put(teeHem, clip(lo, teeHem))
                            take(rgb, Mat.FLAT, 0)
                        }
                        for (x in floatArrayOf(-1.72f, -.67f, .38f, 1.43f)) {
                            vertical(x * b, (x + .12f) * b, StreetCameos.PLAID_LIGHT)
                            vertical((x + .19f) * b, (x + .23f) * b, StreetCameos.PLAID_DARK)
                        }
                        // The placket and small buttons sit on top of the woven pattern.
                        val opening = .76f * b
                        o.add(
                            FigBuilder.capsule(
                                tx(.02f, opening),
                                ty(.02f, opening),
                                .12f * b,
                                tx(.92f, opening),
                                ty(.92f, opening),
                                .12f * b,
                                3,
                            ),
                            dim(w.top, .85f),
                            Mat.CLOTH,
                        )
                        for (t in floatArrayOf(.12f, .29f, .46f, .63f, .80f)) o.add(
                            FigBuilder.ellipse(
                                tx(t, opening),
                                ty(t, opening),
                                .065f,
                                .065f,
                                lean,
                                8,
                            ),
                            0xD4D8D2,
                            Mat.FLAT,
                        )
                        // A single chest pocket with a stitched flap.
                        for ((t, out) in
                            arrayOf(
                                .49f to -.95f * b,
                                .49f to -.10f * b,
                                .70f to -.10f * b,
                                .70f to -.95f * b,
                            )) put(t, out)
                        take(dim(w.top, .87f), Mat.CLOTH, 1)
                        o.add(
                            FigBuilder.capsule(
                                tx(.67f, -.93f * b),
                                ty(.67f, -.93f * b),
                                .055f,
                                tx(.67f, -.12f * b),
                                ty(.67f, -.12f * b),
                                .055f,
                                3,
                            ),
                            StreetCameos.PLAID_LIGHT,
                            Mat.FLAT,
                            .65f,
                        )
                    }
                }
                if (look.tee == Tee.WORK_SHIRT) {
                    // Only the near chest pocket is visible in this side view.
                    for (center in floatArrayOf(teeFront(.62f) - .40f * b)) {
                        val width = .34f * b
                        for ((t, out) in
                            arrayOf(
                                .51f to center - width,
                                .51f to center + width,
                                .73f to center + width,
                                .73f to center - width,
                            )) put(t, out)
                        take(dim(w.top, .88f), Mat.CLOTH, 1)
                        o.add(
                            FigBuilder.capsule(
                                tx(.70f, center - width),
                                ty(.70f, center - width),
                                .075f,
                                tx(.70f, center + width),
                                ty(.70f, center + width),
                                .075f,
                                3,
                            ),
                            mix(w.top, 0xFFFFFF, .13f),
                            Mat.CLOTH,
                        )
                        o.add(
                            FigBuilder.ellipse(
                                tx(.67f, center),
                                ty(.67f, center),
                                .06f,
                                .06f,
                                lean,
                                8,
                            ),
                            dim(w.top, .45f),
                            Mat.FLAT,
                        )
                    }
                    val opening = .72f * b
                    o.add(
                        FigBuilder.capsule(
                            tx(.14f, opening),
                            ty(.14f, opening),
                            .10f,
                            tx(.88f, opening),
                            ty(.88f, opening),
                            .10f,
                            3,
                        ),
                        dim(w.top, .80f),
                        Mat.CLOTH,
                    )
                    for (t in floatArrayOf(.24f, .40f, .56f, .72f)) o.add(
                        FigBuilder.ellipse(tx(t, opening), ty(t, opening), .06f, .06f, lean, 8),
                        0xB5B7A3,
                        Mat.FLAT,
                    )
                    // Tucked hem over the trouser waist; belt remains below it.
                    o.add(
                        FigBuilder.capsule(
                            tx(.077f, -at(bodyB, .08f)),
                            ty(.077f, -at(bodyB, .08f)),
                            .16f,
                            tx(.077f, at(bodyF, .08f)),
                            ty(.077f, at(bodyF, .08f)),
                            .16f,
                            3,
                        ),
                        BELT,
                        Mat.LEATHER,
                    )
                    o.add(
                        FigBuilder.ellipse(
                            tx(.077f, .68f * b),
                            ty(.077f, .68f * b),
                            .18f,
                            .13f,
                            lean,
                            8,
                        ),
                        0xB4A48C,
                        Mat.METAL,
                    )
                }
                if (look.tee == Tee.STRIPED_HOODIE) {
                    // Woven vertical stripes are clipped by sampling the shirt silhouette.
                    for (stripe in -6..6) {
                        val out = stripe * .44f * b
                        for (i in 0 until ns) {
                            val t = st[i].coerceAtLeast(teeHem)
                            put(t, out.coerceIn(-teeBack(t), teeFront(t)))
                        }
                        for (i in ns - 1 downTo 0) {
                            val t = st[i].coerceAtLeast(teeHem)
                            put(t, (out + .13f * b).coerceIn(-teeBack(t), teeFront(t)))
                        }
                        take(if (stripe % 2 == 0) 0x5A7048 else 0x1A6956, Mat.CLOTH, 1)
                    }
                    // Zip follows the forward chest, down from the open V at the collar.
                    o.add(
                        FigBuilder.capsule(
                            tx(.10f, teeFront(.10f) - .15f * b),
                            ty(.10f, teeFront(.10f) - .15f * b),
                            .045f,
                            tx(.80f, teeFront(.80f) - .20f * b),
                            ty(.80f, teeFront(.80f) - .20f * b),
                            .045f,
                            3,
                        ),
                        0x234334,
                        Mat.FLAT,
                    )
                    // The hood lies behind the neck, rather than making a collar on the face.
                    for ((t, x) in
                        arrayOf(
                            .78f to -1.3f * b,
                            .87f to -2.0f * b,
                            1.10f to -1.50f * b,
                            1.16f to -.42f * b,
                            .96f to -.50f * b,
                        )) put(t, x)
                    take(dim(w.top, .86f), Mat.CLOTH, 2)
                    for ((t, x) in
                        arrayOf(1.04f to .05f * b, .84f to .70f * b, 1.02f to 1.22f * b)) put(t, x)
                    take(w.skin, Mat.SKIN, 1)
                    // Dark beaded cord around the open neckline.
                    for (i in 0..16) {
                        val t = 1.05f - .32f * i / 16f
                        val out = .10f * b + (.70f * b) * i / 16f
                        o.add(
                            FigBuilder.ellipse(tx(t, out), ty(t, out), .055f, .065f, lean, 7),
                            0x3E3224,
                            Mat.FLAT,
                        )
                    }
                }
                if (look.tee == Tee.SPORT_SHIRT) {
                    // Blue shoulder yoke over the dark body, following the full chest contour.
                    put(.78f, teeFront(.78f))
                    for (i in 0 until ns) if (st[i] > .78f) put(st[i], teeFront(st[i]))
                    for (i in ns - 1 downTo 0) if (st[i] > .78f) put(st[i], -teeBack(st[i]))
                    put(.78f, -teeBack(.78f))
                    take(w.coat, Mat.CLOTH, 2)
                }
            }
            Coat.JACKET,
            Coat.LONG -> {
                val long = coat == Coat.LONG
                val ease = (if (cloth == Mat.NYLON) .5f else if (long) .42f else .22f) * b
                val drop =
                    if (long) th * .92f else if (cloth == Mat.NYLON) 2.4f * hgt else 1.3f * hgt
                val flare = (if (long) .55f else .2f) * b
                val legFwd = sin(rad(max(p[K.LN], p[K.LF]))) * th * (if (long) .32f else .1f)
                val back = -trail * (if (long) 1.3f else .4f)
                // the hem's front and back corners, below the hips along the body
                val fX = px + nx * (bodyF[1] + ease + flare) + legFwd - ux * drop
                val fY = py + ny * (bodyF[1] + ease + flare) - uy * drop + .2f
                val bX = px - nx * (bodyB[1] + ease + flare) + back - ux * drop
                val bY = py - ny * (bodyB[1] + ease + flare) - uy * drop + .35f
                // the front: over the chest, then hanging (a fitted wool jacket follows the
                // waist in a little)
                val chestF = bodyF[7] + ease
                val fit = if (cloth == Mat.WOOL && !long) 1f else 0f
                for (i in ns - 1 downTo 0) {
                    val t = st[i]
                    if (t < 0f) continue
                    val hang = chestF + (bodyF[1] + ease + flare * .3f - chestF) * ((.8f - t) / .8f)
                    val off =
                        if (t >= .8f) bodyF[i] + ease
                        else max(bodyF[i] + ease, hang - fit * (bodyF[7] - bodyF[i]))
                    put(t, off)
                }
                // down the skirts to the hem (a jacket's short)
                pts += (fX + px + nx * (bodyF[1] + ease)) / 2f - ux * drop * .45f
                pts += (fY + py + ny * (bodyF[1] + ease)) / 2f - uy * drop * .45f
                pts += fX
                pts += fY
                for (q in 1..3) {
                    val t = q / 4f
                    pts += fX + (bX - fX) * t + back * .4f * (1f - abs(2f * t - 1f))
                    pts += fY + (bY - fY) * t + .12f * (1f - abs(2f * t - 1f))
                }
                pts += bX
                pts += bY
                pts += (bX + px - nx * (bodyB[1] + ease)) / 2f + back * .3f - ux * drop * .45f
                pts += (bY + py - ny * (bodyB[1] + ease)) / 2f - uy * drop * .45f
                // the back: straight up from the hem to the shoulder blades, then over them
                val bladeB = bodyB[8] + ease
                for (i in 0 until ns) {
                    val t = st[i]
                    if (t < 0f) continue
                    val hang =
                        bodyB[1] +
                            ease +
                            flare * .3f +
                            (bladeB - bodyB[1] - ease - flare * .3f) * (t / .9f)
                    val off =
                        if (t >= .9f) bodyB[i] + ease
                        else max(bodyB[i] + ease, hang - fit * .5f * (bodyB[8] - bodyB[i]))
                    put(t, -off)
                }
                take(w.coat, cloth)
                hem = floatArrayOf(0f, 0f, 0f, 0f, fX, fY, bX, bY, 0f, 0f, 0f, 0f)
                val seam = dim(w.coat, .55f)
                val h = hem!!
                // the front's opening, from the lapel's foot down to the hem
                o.add(
                    FigBuilder.capsule(
                        tx(.62f, chestF - .35f),
                        ty(.62f, chestF - .35f),
                        .07f,
                        h[4] - nx * .35f - ux * .3f,
                        h[5] - ny * .35f - uy * .3f,
                        .07f,
                        3,
                    ),
                    seam,
                    Mat.FLAT,
                    .55f,
                )
                if (long) {
                    // the hem's turned edge
                    o.add(band(h, ux, uy, nx, ny, .45f), seam, Mat.FLAT, .3f)
                    // two folds falling from the belt down the skirts, pushed by the stride
                    for (k in 0 until 2) {
                        val f0 = (if (k == 0) .6f else -.4f) * bodyF[3]
                        o.add(
                            FigBuilder.capsule(
                                tx(.3f, f0),
                                ty(.3f, f0),
                                .14f,
                                h[4] * (.55f - k * .3f) + h[6] * (.45f + k * .3f) + ux * .6f,
                                h[5] * (.55f - k * .3f) + h[7] * (.45f + k * .3f) + uy * .6f,
                                .3f,
                                3,
                            ),
                            seam,
                            Mat.FLAT,
                            .12f,
                        )
                    }
                    // the belt at the waist, its buckle, and the buttons above it
                    val wf = at(bodyF, .38f) + ease
                    val wb = at(bodyB, .38f) + ease
                    o.add(
                        FigBuilder.capsule(
                            tx(.38f, -wb),
                            ty(.38f, -wb),
                            .4f,
                            tx(.38f, wf),
                            ty(.38f, wf),
                            .4f,
                            3,
                        ),
                        dim(w.coat, .82f),
                        cloth,
                    )
                    o.add(
                        FigBuilder.ellipse(
                            tx(.38f, wf - .45f),
                            ty(.38f, wf - .45f),
                            .38f,
                            .34f,
                            lean,
                            8,
                        ),
                        dim(w.coat, .5f),
                        Mat.LEATHER,
                    )
                    for (u in floatArrayOf(.5f, .64f)) {
                        val bx = tx(u, chestF - .75f)
                        val by = ty(u, chestF - .75f)
                        o.add(
                            FigBuilder.ellipse(bx, by, .24f, .24f, 0f, 8),
                            dim(w.coat, .42f),
                            Mat.LEATHER,
                        )
                    }
                    // a pocket's flap at the hip
                    o.add(
                        FigBuilder.capsule(
                            tx(-.1f, -.5f),
                            ty(-.1f, -.5f),
                            .09f,
                            tx(-.14f, bodyF[1] * .75f),
                            ty(-.14f, bodyF[1] * .75f),
                            .09f,
                            3,
                        ),
                        seam,
                        Mat.FLAT,
                        .5f,
                    )
                } else {
                    // the jacket's waistband (ribbed on the nylon one), a welt pocket
                    o.add(band(h, ux, uy, nx, ny, .85f), dim(w.coat, .78f), cloth)
                    o.add(
                        FigBuilder.capsule(
                            tx(.14f, -.2f),
                            ty(.14f, -.2f),
                            .09f,
                            tx(.26f, bodyF[1] * .7f),
                            ty(.26f, bodyF[1] * .7f),
                            .09f,
                            3,
                        ),
                        seam,
                        Mat.FLAT,
                        .55f,
                    )
                    if (cloth == Mat.NYLON) {
                        // the quilting's stitched lines round the body
                        for (u in floatArrayOf(.2f, .44f, .68f)) o.add(
                            FigBuilder.capsule(
                                tx(u + .02f, -at(bodyB, u) - ease + .25f),
                                ty(u + .02f, -at(bodyB, u) - ease + .25f),
                                .06f,
                                tx(u - .02f, at(bodyF, u) + ease - .3f),
                                ty(u - .02f, at(bodyF, u) + ease - .3f),
                                .06f,
                                3,
                            ),
                            seam,
                            Mat.FLAT,
                            .4f,
                        )
                        // the zip up the front: its tape, its teeth, the pull at the throat
                        val z0 = .02f
                        val z1 = .95f
                        var u = z0
                        while (u < z1) {
                            val off = max(at(bodyF, u) + ease, chestF - (.8f - u) * .2f) - .22f
                            o.add(
                                FigBuilder.ellipse(tx(u, off), ty(u, off), .07f, .05f, lean, 4),
                                0xB8BEC8,
                                Mat.FLAT,
                                .55f,
                            )
                            u += .035f
                        }
                        o.add(
                            FigBuilder.capsule(
                                tx(.93f, chestF - .3f),
                                ty(.93f, chestF - .3f),
                                .1f,
                                tx(.86f, chestF - .3f),
                                ty(.86f, chestF - .3f),
                                .09f,
                                3,
                            ),
                            0xC8CCD2,
                            Mat.METAL,
                        )
                    } else {
                        // three buttons down the front
                        for (u in floatArrayOf(.3f, .5f, .7f)) {
                            val off = max(at(bodyF, u) + ease - fit * .2f, 0f) - .45f
                            o.add(
                                FigBuilder.ellipse(tx(u, off), ty(u, off), .2f, .2f, 0f, 8),
                                dim(w.coat, .45f),
                                Mat.LEATHER,
                            )
                        }
                        // the shaped seam over the chest
                        o.add(
                            FigBuilder.capsule(
                                tx(.85f, .2f),
                                ty(.85f, .2f),
                                .05f,
                                tx(.15f, bodyF[3] * .4f),
                                ty(.15f, bodyF[3] * .4f),
                                .05f,
                                3,
                            ),
                            seam,
                            Mat.FLAT,
                            .3f,
                        )
                    }
                }
                // the lapels (and the shirt in the V between them): a raised form on the chest
                val lf = chestF
                val shirt =
                    floatArrayOf(
                        tx(topT - .02f, at(bodyF, topT) + .1f),
                        ty(topT - .02f, at(bodyF, topT) + .1f),
                        tx(.68f, lf - .5f),
                        ty(.68f, lf - .5f),
                        tx(topT - .04f, at(bodyF, topT) - .7f),
                        ty(topT - .04f, at(bodyF, topT) - .7f),
                    )
                o.add(FigBuilder.chaikin(shirt, 1), w.top, Mat.CLOTH)
                if (cloth != Mat.NYLON) {
                    val lapel =
                        floatArrayOf(
                            tx(topT + .01f, at(bodyF, topT) - .2f),
                            ty(topT + .01f, at(bodyF, topT) - .2f),
                            tx(.86f, lf + .08f),
                            ty(.86f, lf + .08f),
                            tx(.8f, lf - .1f),
                            ty(.8f, lf - .1f),
                            tx(.83f, lf + .02f),
                            ty(.83f, lf + .02f),
                            tx(.6f, lf - .25f),
                            ty(.6f, lf - .25f),
                            tx(.74f, lf - .75f),
                            ty(.74f, lf - .75f),
                            tx(.93f, at(bodyF, topT) - .75f),
                            ty(.93f, at(bodyF, topT) - .75f),
                        )
                    o.add(FigBuilder.chaikin(lapel, 1), mix(w.coat, 0xFFFFFF, .05f), cloth)
                }
            }
        }
        if (
            coat == Coat.NONE &&
                !look.skirt &&
                look.tee != Tee.SPORT_SHIRT &&
                look.tee != Tee.STRIPED_HOODIE
        ) {
            // the belt over the trousers' top, its buckle at the front, a loop behind
            o.add(
                FigBuilder.capsule(
                    tx(.11f, -at(bodyB, .11f) + .1f),
                    ty(.11f, -at(bodyB, .11f) + .1f),
                    .24f * hgt,
                    tx(.11f, at(bodyF, .11f) - .1f),
                    ty(.11f, at(bodyF, .11f) - .1f),
                    .24f * hgt,
                    3,
                ),
                BELT,
                Mat.LEATHER,
            )
            o.add(
                FigBuilder.ellipse(
                    tx(.11f, at(bodyF, .11f) - .2f),
                    ty(.11f, at(bodyF, .11f) - .2f),
                    .3f,
                    .26f,
                    lean,
                    8,
                ),
                0xC9C2B0,
                Mat.METAL,
            )
            // the tee's folds where it is tucked
            for (k in 0 until 3) {
                val o0 = -hipW * .6f + k * hipW * .55f
                o.add(
                    FigBuilder.capsule(
                        tx(.17f, o0),
                        ty(.17f, o0),
                        .08f,
                        tx(.27f, o0 + .3f),
                        ty(.27f, o0 + .3f),
                        .06f,
                        3,
                    ),
                    dim(w.top, .62f),
                    Mat.FLAT,
                    .3f,
                )
            }
        }

        // ---- the neck and the head, nodding a little behind the body
        val headA = lean + p[K.HEAD]
        val hux = sin(rad(headA))
        val huy = -cos(rad(headA))
        val hfx = cos(rad(headA))
        val hfy = sin(rad(headA))
        val nkx =
            sx + sin(rad(lean + p[K.HEAD] * .5f)) * (neck * .55f) + nx * look.neckForward * hgt
        val nky =
            sy - cos(rad(lean + p[K.HEAD] * .5f)) * (neck * .55f) + ny * look.neckForward * hgt
        val drop = StreetGait.headDrop(f) * 24f * hgt * look.headLag
        val hx = nkx + hux * (neck * .6f + headRy * .92f)
        val hy = nky + huy * (neck * .6f + headRy * .92f) + drop
        if (whole)
            o.add(
                FigBuilder.capsule(
                    sx + nx * .2f + ux * .3f,
                    sy + ny * .2f + uy * .3f,
                    .9f * b * look.neckScale,
                    hx,
                    hy + .3f,
                    .8f * b * look.neckScale,
                    6,
                ),
                w.skin,
                Mat.SKIN,
            )
        else {
            // the neck as far as the jaw: the head layer turns over its top (its nape shows
            // behind a head bowed over a phone)
            val l = 1.1f
            o.add(
                FigBuilder.capsule(
                    sx + nx * .2f + ux * .3f,
                    sy + ny * .2f + uy * .3f,
                    .9f * b * look.neckScale,
                    nkx + hux * l,
                    nky + huy * l,
                    .8f * b * look.neckScale,
                    6,
                ),
                w.skin,
                Mat.SKIN,
            )
            // the jaw's shadow over the neck's top
            o.add(
                FigBuilder.ellipse(
                    nkx + hux * .75f + hfx * .25f,
                    nky + huy * .75f + hfy * .25f,
                    .95f * b * look.neckScale,
                    .55f,
                    headA,
                    8,
                ),
                dim(w.skin, .66f),
                Mat.FLAT,
                .45f,
            )
        }
        j.neckX = nkx
        j.neckY = nky + drop
        j.headA = headA
        j.tail = trail2
        j.shNX = sx + nx * sw
        j.shNY = sy + ny * sw
        j.shFX = sx - nx * sw
        j.shFY = sy - ny * sw
        j.an = p[K.AN]
        j.en = p[K.EN]
        j.af = p[K.AF]
        j.ef = p[K.EF]
        // the soft shade the collar (or the neckline) throws on the chest under it
        o.add(
            FigBuilder.ellipse(
                sx - ux * .25f + nx * .2f,
                sy - uy * .25f + ny * .2f,
                (if (coat != Coat.NONE) 1.8f else 1.4f) * b,
                .5f * b,
                lean,
                8,
            ),
            dim(torsoRgb, .55f),
            Mat.FLAT,
            .25f,
        )
        if (coat != Coat.NONE) {
            // a collar over the neck's root
            o.add(
                FigBuilder.ellipse(sx + ux * .4f, sy + uy * .4f, 1.9f * b, .9f * b, lean, 8),
                dim(w.coat, .86f),
            )
        } else if (look.tee == Tee.PLAID_SHIRT || look.tee == Tee.WORK_SHIRT) {
            // Open collar: the skin V and two cloth collar points, distinct from a tee neckline.
            o.add(
                FigBuilder.chaikin(
                    floatArrayOf(
                        tx(1.10f, -.30f * b),
                        ty(1.10f, -.30f * b),
                        tx(if (look.tee == Tee.WORK_SHIRT) .68f else .79f, .76f * b),
                        ty(if (look.tee == Tee.WORK_SHIRT) .68f else .79f, .76f * b),
                        tx(1.10f, 1.08f * b),
                        ty(1.10f, 1.08f * b),
                    ),
                    1,
                ),
                look.underShirt ?: w.skin,
                if (look.underShirt == null) Mat.SKIN else Mat.CLOTH,
            )
            o.add(
                FigBuilder.chaikin(
                    floatArrayOf(
                        tx(1.10f, -.58f * b),
                        ty(1.10f, -.58f * b),
                        tx(.87f, -.05f * b),
                        ty(.87f, -.05f * b),
                        tx(1.00f, .57f * b),
                        ty(1.00f, .57f * b),
                    ),
                    1,
                ),
                mix(w.top, 0xFFFFFF, .24f),
                Mat.CLOTH,
            )
            o.add(
                FigBuilder.chaikin(
                    floatArrayOf(
                        tx(1.10f, 1.22f * b),
                        ty(1.10f, 1.22f * b),
                        tx(.86f, 1.16f * b),
                        ty(.86f, 1.16f * b),
                        tx(1.00f, .64f * b),
                        ty(1.00f, .64f * b),
                    ),
                    1,
                ),
                dim(w.top, .86f),
                Mat.CLOTH,
            )
            look.underShirt?.let { red ->
                // The undershirt has a rounded crew neck above the work-shirt collar.
                o.add(
                    FigBuilder.ellipse(
                        sx + nx * .30f + ux * .18f,
                        sy + ny * .30f + uy * .18f,
                        .83f * b,
                        .22f * hgt,
                        lean,
                        12,
                    ),
                    red,
                    Mat.CLOTH,
                )
            }
        } else {
            // the tee's neckline
            o.add(
                FigBuilder.ellipse(sx + ux * .1f, sy + uy * .1f, 1.5f * b, .5f * b, lean, 8),
                dim(w.top, .85f),
            )
        }
        look.towel?.let { cloth ->
            fun towelTail(top: Float, bottom: Float, offset: Float, width: Float) {
                fun center(t: Float) = teeFront(t) - offset + trail * .06f * hgt * (1f - t)
                for (i in 0..10) {
                    val t = top + (bottom - top) * i / 10f
                    put(t, center(t) + width / 2f)
                }
                for (i in 10 downTo 0) {
                    val t = top + (bottom - top) * i / 10f
                    put(t, center(t) - width / 2f)
                }
                take(cloth, Mat.WOOL, 2)
                for (edge in floatArrayOf(-.36f, .36f)) {
                    for (i in 0 until 10) {
                        val t0 = top + (bottom - top) * i / 10f
                        val t1 = top + (bottom - top) * (i + 1) / 10f
                        o.add(
                            FigBuilder.capsule(
                                tx(t0, center(t0) + width * edge),
                                ty(t0, center(t0) + width * edge),
                                .025f,
                                tx(t1, center(t1) + width * edge),
                                ty(t1, center(t1) + width * edge),
                                .025f,
                                3,
                            ),
                            dim(cloth, .76f),
                            Mat.FLAT,
                            .42f,
                        )
                    }
                }
                // A sewn border and a few small terry-loop marks near each end.
                o.add(
                    FigBuilder.capsule(
                        tx(bottom + .035f, center(bottom) - width * .37f),
                        ty(bottom + .035f, center(bottom) - width * .37f),
                        .055f,
                        tx(bottom + .035f, center(bottom) + width * .37f),
                        ty(bottom + .035f, center(bottom) + width * .37f),
                        .055f,
                        3,
                    ),
                    dim(cloth, .78f),
                    Mat.FLAT,
                    .55f,
                )
                for (i in 0..5) {
                    val t = bottom + .10f + (i % 3) * .11f
                    val x = center(t) + (if (i < 3) -.17f else .13f) * width
                    o.add(
                        FigBuilder.ellipse(tx(t, x), ty(t, x), .025f, .035f, lean, 6),
                        mix(cloth, 0xFFFFFF, .20f),
                        Mat.FLAT,
                        .45f,
                    )
                }
            }
            towelTail(1.10f, .43f, 1.45f * b, .88f * b)
            towelTail(1.08f, .16f, .46f * b, 1.00f * b)
            o.add(
                FigBuilder.capsule(
                    tx(1.08f, -.35f * b),
                    ty(1.08f, -.35f * b),
                    .34f * b,
                    tx(1.08f, teeFront(1.08f) - .20f * b),
                    ty(1.08f, teeFront(1.08f) - .20f * b),
                    .36f * b,
                    6,
                ),
                cloth,
                Mat.WOOL,
            )
        }
        if (whole) {
            head(o, w, look.hair, hx, hy, headRx, headRy, headA, turn, glance, look.face)
            hair(o, look.hair, w, hx, hy, headRx, headRy, headA, hgt, trail2, true)
        }
        j.top = hy - headRy * 1.08f

        // ---- the shade the near arm keeps on the body round the shoulder, then the bag, then the
        // near arm over everything
        run {
            // (close round the joint, so it stays under the arm whatever the arm is doing)
            val ax = j.shNX - ux * .5f
            val ay = j.shNY - uy * .5f
            o.add(
                FigBuilder.ellipse(ax, ay, 1.25f * b * thick, 1.15f, lean, 8),
                dim(torsoRgb, .5f),
                Mat.FLAT,
                .25f,
            )
        }
        when (look.bag) {
            Bag.CANVAS -> {
                // Broad canvas sling and soft rectangular satchel, tucked behind the near arm.
                val bx = tx(-.11f, -2.30f * b) - trail * .20f
                val by = ty(-.11f, -2.30f * b) + 1.7f * hgt
                o.add(
                    FigBuilder.capsule(
                        tx(1.01f, .65f * b),
                        ty(1.01f, .65f * b),
                        .32f * b,
                        bx,
                        by - 1.5f * hgt,
                        .31f * b,
                        5,
                    ),
                    0xA7997C,
                    Mat.CLOTH,
                )
                o.add(box(bx, by, 1.40f * hgt, 2.05f * hgt, -trail * 3f), w.bag, Mat.CLOTH)
                o.add(
                    box(bx, by - 1.35f * hgt, 1.45f * hgt, .65f * hgt, -trail * 3f),
                    dim(w.bag, .85f),
                    Mat.CLOTH,
                )
                o.add(
                    FigBuilder.capsule(
                        bx - 1.12f * hgt,
                        by + .95f * hgt,
                        .05f,
                        bx + 1.12f * hgt,
                        by + .95f * hgt,
                        .05f,
                        3,
                    ),
                    mix(w.bag, 0xFFFFFF, .20f),
                    Mat.FLAT,
                )
            }
            Bag.SHOULDER -> shoulderBag(o, w, px, py, sx, sy, nx, ny, ux, uy, b, hgt, hipW, trail)
            Bag.BACKPACK -> straps(o, w, sx, sy, nx, ny, ux, uy, b, hgt, shoulderW)
            else -> {}
        }
        armShapes(o, j, look, sx + nx * sw, sy + ny * sw, p[K.AN], p[K.EN], true, arms, OPEN)
        if (whole && look.bag == Bag.HAND) handbag(o, w, j, hgt, b, trail)

        ground(o, j, p[K.LIFT])
        return StreetFigure(o.shapes, j)
    }

    /**
     * A band along a coat's [hem] (its front and back corners at 4, 5 and 6, 7), [depth] units up
     * from the edge along ([ux], [uy]), held inside the coat's rounded corners.
     */
    private fun band(hem: FloatArray, ux: Float, uy: Float, nx: Float, ny: Float, depth: Float) =
        FigBuilder.chaikin(
            floatArrayOf(
                hem[4] - nx * .45f + ux * .12f,
                hem[5] - ny * .45f + uy * .12f,
                hem[6] + nx * .45f + ux * .2f,
                hem[7] + ny * .45f + uy * .2f,
                hem[6] + nx * .3f + ux * (depth + .2f),
                hem[7] + ny * .3f + uy * (depth + .2f),
                hem[4] - nx * .3f + ux * (depth + .12f),
                hem[5] - ny * .3f + uy * (depth + .12f),
            ),
            1,
        )

    // The body's side profile: at these stations up the torso (0 the hips, 1 the shoulders),
    // how far it stands out in front and behind, in the build's units: the seat, the small of the
    // back, the chest, the shoulder blades, the root of the neck; a woman's waist narrower, her
    // bust and hips fuller; and the half widths seen from the front, for a figure turned to us
    private val BODY_T = floatArrayOf(-.1f, 0f, .15f, .3f, .42f, .55f, .68f, .8f, .9f, .97f, 1.1f)
    private val BODY_F =
        floatArrayOf(1.38f, 1.52f, 1.56f, 1.36f, 1.3f, 1.46f, 1.72f, 1.8f, 1.6f, 1.2f, .72f)
    private val BODY_B =
        floatArrayOf(1.5f, 1.62f, 1.52f, 1.3f, 1.2f, 1.32f, 1.55f, 1.72f, 1.7f, 1.42f, .95f)
    private val BODY_W =
        floatArrayOf(2.7f, 2.8f, 2.6f, 2.35f, 2.3f, 2.45f, 2.75f, 2.95f, 3.1f, 2.9f, 1.0f)
    private val BODY_WOMAN_F = floatArrayOf(0f, 0f, 0f, -.12f, -.14f, .05f, .3f, .22f, 0f, 0f, 0f)
    private val BODY_WOMAN_B = floatArrayOf(.1f, .18f, .08f, -.1f, -.12f, -.05f, 0f, 0f, 0f, 0f, 0f)

    /** A belt's leather. */
    private const val BELT = 0x2B2320

    /** Puts the figure on the pavement: its lowest point goes to y = -lift. */
    private fun ground(o: Out, j: WalkerJoints, lift: Float) {
        var low = -1e9f
        for (s in o.shapes) {
            var i = 1
            while (i < s.pts.size) {
                low = max(low, s.pts[i])
                i += 2
            }
        }
        val dy = -low - lift
        for (s in o.shapes) {
            var i = 1
            while (i < s.pts.size) {
                s.pts[i] += dy
                i += 2
            }
        }
        j.footNH = -(j.footNH + dy)
        j.footFH = -(j.footFH + dy)
        j.handNY += dy
        j.top += dy
        j.shNY += dy
        j.shFY += dy
        j.neckY += dy
    }

    /**
     * A limb from ([x0], [y0]) to ([x1], [y1]), [r0] to [r1] thick, its middle bowed [bow] units to
     * the side (positive: towards +x, the figure's front).
     */
    fun bowed(
        x0: Float,
        y0: Float,
        r0: Float,
        x1: Float,
        y1: Float,
        r1: Float,
        bow: Float,
    ): FloatArray {
        val dx = x1 - x0
        val dy = y1 - y0
        val len = max(1e-3f, sqrt(dx * dx + dy * dy))
        // the side the round top ends on (so the outline never crosses itself), the bow towards +x
        val nx = -dy / len
        val ny = dx / len
        val bw = if (nx < 0f) -bow else bow
        val n = 6
        val out = FloatArray((n + 1) * 4 + 8)
        var o = 0
        // the round top, then down one side
        val t0 = atan2(dy, dx)
        for (i in 0..3) {
            val a = t0 + PI.toFloat() / 2f + PI.toFloat() * i / 3f
            out[o++] = x0 + cos(a) * r0
            out[o++] = y0 + sin(a) * r0
        }
        for (i in 1 until n) {
            val t = i / n.toFloat()
            val cx = x0 + dx * t + nx * bw * 4f * t * (1f - t)
            val cy = y0 + dy * t + ny * bw * 4f * t * (1f - t)
            val r = r0 + (r1 - r0) * t
            out[o++] = cx - nx * r
            out[o++] = cy - ny * r
        }
        for (i in 0..3) {
            val a = t0 - PI.toFloat() / 2f + PI.toFloat() * i / 3f
            out[o++] = x1 + cos(a) * r1
            out[o++] = y1 + sin(a) * r1
        }
        for (i in n - 1 downTo 1) {
            val t = i / n.toFloat()
            val cx = x0 + dx * t + nx * bw * 4f * t * (1f - t)
            val cy = y0 + dy * t + ny * bw * 4f * t * (1f - t)
            val r = r0 + (r1 - r0) * t
            out[o++] = cx + nx * r
            out[o++] = cy + ny * r
        }
        return out.copyOf(o)
    }

    /**
     * A limb from ([x0], [y0]) to ([x1], [y1]) with its own shape: at evenly spaced stations along
     * it, [front] and [back] say how far its outline stands out on its front side (to the left of
     * the way it runs, which for a leg or an arm hanging down is the way the figure faces) and on
     * its back, times [k]; round at both ends. So a thigh is fuller at the front, a calf at the
     * back, a trouser leg falls straight from the knee.
     */
    fun limb(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        front: FloatArray,
        back: FloatArray,
        k: Float,
        cut: Boolean = false,
    ): FloatArray {
        val n = front.size
        val ddx = x1 - x0
        val ddy = y1 - y0
        val len = max(1e-3f, sqrt(ddx * ddx + ddy * ddy))
        val dx = ddx / len
        val dy = ddy / len
        val fx = dy
        val fy = -dx
        val arc = 5
        val out = FloatArray((2 * n + 2 * (arc - 1)) * 2)
        var o = 0
        for (i in 0 until n) {
            val t = i / (n - 1f) * len
            out[o++] = x0 + dx * t + fx * front[i] * k
            out[o++] = y0 + dy * t + fy * front[i] * k
        }
        val tf = atan2(fy, fx)
        fun cap(cx: Float, cy: Float, r: Float, from: Float) {
            for (s in 1 until arc) {
                val a = from + PI.toFloat() * s / arc
                out[o++] = cx + cos(a) * r
                out[o++] = cy + sin(a) * r
            }
        }
        val rb = (front[n - 1] + back[n - 1]) / 2f * k
        val sb = (front[n - 1] - back[n - 1]) / 2f * k
        if (!cut) cap(x1 + fx * sb, y1 + fy * sb, rb, tf)
        for (i in n - 1 downTo 0) {
            val t = i / (n - 1f) * len
            out[o++] = x0 + dx * t - fx * back[i] * k
            out[o++] = y0 + dy * t - fy * back[i] * k
        }
        val ra = (front[0] + back[0]) / 2f * k
        val sa = (front[0] - back[0]) / 2f * k
        cap(x0 + fx * sa, y0 + fy * sa, ra, tf + PI.toFloat())
        return FigBuilder.chaikin(out, 1)
    }

    // The legs' shapes, hip to knee and knee to hem (or ankle), front and back, in the build's
    // units: a trouser leg full over the thigh and falling straight from the knee, a little wider
    // at the hem; a leg in tights with its knee, its calf and a slim ankle
    val THIGH_F = floatArrayOf(1.1f, 1.34f, 1.4f, 1.3f, 1.15f, 1.02f)
    val THIGH_B = floatArrayOf(1.3f, 1.5f, 1.42f, 1.24f, 1.1f, 1.02f)
    val SHIN_F = floatArrayOf(1.02f, .94f, .9f, .88f, .9f, .94f)
    val SHIN_B = floatArrayOf(1.02f, 1.06f, 1.02f, .96f, .95f, .98f)
    private val LEG_THIGH_F = floatArrayOf(1.42f, 1.46f, 1.36f, 1.2f, 1.0f, .82f)
    private val LEG_THIGH_B = floatArrayOf(1.5f, 1.42f, 1.3f, 1.12f, .96f, .82f)
    private val LEG_SHIN_F = floatArrayOf(.82f, .8f, .72f, .62f, .5f, .42f)
    private val LEG_SHIN_B = floatArrayOf(.82f, 1.06f, 1.0f, .78f, .54f, .45f)

    private fun leg(
        o: Out,
        j: WalkerJoints,
        hx: Float,
        hy: Float,
        th: Float,
        sh: Float,
        b: Float,
        hgt: Float,
        l: Float,
        kn: Float,
        ankle: Float,
        shade: Float,
        near: Boolean,
        look: StreetLook,
    ) {
        val tx = hx + sin(rad(l)) * th
        val ty = hy + cos(rad(l)) * th
        val sa = l - kn
        val ax = tx + sin(rad(sa)) * sh
        val ay = ty + cos(rad(sa)) * sh
        val w = look.wear
        val trousers = !look.skirt
        val legRgb = dim(w.legs, shade)
        val legMat =
            if (look.shoes == Shoe.TRAINER && look.coat == Coat.JACKET) Mat.DENIM
            else if (look.coat == Coat.LONG) Mat.WOOL else Mat.CLOTH
        // the shoe ([shoe]) in the foot's own frame (forward along the sole, down through the
        // sole), turned by the ankle
        val fx = sin(rad(sa + 90f + ankle))
        val fy = cos(rad(sa + 90f + ankle))
        val dx = sin(rad(sa + ankle))
        val dy = cos(rad(sa + ankle))
        val L = hgt
        // the shin's way down and its front
        val sdx = sin(rad(sa))
        val sdy = cos(rad(sa))
        val sfx = sdy
        val sfy = -sdx
        val tdx = sin(rad(l))
        val tdy = cos(rad(l))
        if (look.shorts) {
            val skin = dim(w.skin, shade)
            o.add(limb(hx, hy, tx, ty, LEG_THIGH_F, LEG_THIGH_B, b), skin, Mat.SKIN)
            o.add(limb(tx, ty, ax, ay, LEG_SHIN_F, LEG_SHIN_B, b), skin, Mat.SKIN)
            val cuffX = hx + (tx - hx) * look.shortsLength
            val cuffY = hy + (ty - hy) * look.shortsLength
            o.add(limb(hx, hy, cuffX, cuffY, THIGH_F, THIGH_B, b * 1.24f), legRgb, Mat.CLOTH)
            if (near && look.sportPanel != null) {
                val local =
                    floatArrayOf(
                        .12f,
                        1.10f,
                        .12f,
                        .65f,
                        .69f,
                        .62f,
                        .85f,
                        .30f,
                        .95f,
                        -.20f,
                        .95f,
                        .60f,
                        .75f,
                        1.12f,
                    )
                val panel = FloatArray(local.size)
                for (i in local.indices step 2) {
                    panel[i] = hx + tdx * th * local[i] + tdy * b * local[i + 1]
                    panel[i + 1] = hy + tdy * th * local[i] - tdx * b * local[i + 1]
                }
                o.add(FigBuilder.chaikin(panel, 2), look.sportPanel, Mat.CLOTH)
            }
            if (look.cargoShorts) {
                fun pocket(along: Float, across: Float): Pair<Float, Float> =
                    hx + tdx * along + tdy * across to hy + tdy * along - tdx * across
                val corners =
                    arrayOf(
                        pocket(th * .42f, -.63f * b),
                        pocket(th * .42f, .74f * b),
                        pocket(th * .78f, .74f * b),
                        pocket(th * .78f, -.63f * b),
                    )
                o.add(
                    FigBuilder.chaikin(
                        corners.flatMap { listOf(it.first, it.second) }.toFloatArray(),
                        1,
                    ),
                    dim(legRgb, .91f),
                    Mat.CLOTH,
                )
                val a = pocket(th * .46f, -.60f * b)
                val z = pocket(th * .46f, .70f * b)
                o.add(
                    FigBuilder.capsule(
                        a.first,
                        a.second,
                        .16f * hgt,
                        z.first,
                        z.second,
                        .16f * hgt,
                        3,
                    ),
                    mix(legRgb, 0xFFFFFF, .13f),
                    Mat.CLOTH,
                )
                val button = pocket(th * .48f, .1f * b)
                o.add(
                    FigBuilder.ellipse(button.first, button.second, .065f, .065f, l, 8),
                    dim(legRgb, .45f),
                    Mat.FLAT,
                )
            }
            // Two sewn lines on a broad, soft basketball-short hem, with a slight fold above it.
            for (up in floatArrayOf(.12f, .27f)) o.add(
                FigBuilder.capsule(
                    cuffX - tdx * up - tdy * 1.12f * b,
                    cuffY - tdy * up + tdx * 1.12f * b,
                    .055f,
                    cuffX - tdx * up + tdy * 1.12f * b,
                    cuffY - tdy * up - tdx * 1.12f * b,
                    .055f,
                    3,
                ),
                mix(legRgb, 0xFFFFFF, .2f),
                Mat.FLAT,
                .5f,
            )
            if (look.shoes != Shoe.SANDAL)
                o.add(
                    bowed(
                        ax - sdx * look.sockHeight * hgt,
                        ay - sdy * look.sockHeight * hgt,
                        .55f * b,
                        ax,
                        ay,
                        .54f * b,
                        0f,
                    ),
                    dim(0xE7E4DC, shade),
                    Mat.CLOTH,
                )
            shoe(o.shapes, look, ax, ay, fx, fy, dx, dy, shade)
        } else if (trousers) {
            // the shoe under the trouser's hem, the hem a little past the ankle over its top
            shoe(o.shapes, look, ax, ay, fx, fy, dx, dy, shade)
            val hemX = ax + sdx * .12f
            val hemY = ay + sdy * .12f
            o.add(limb(hx, hy, tx, ty, THIGH_F, THIGH_B, b), legRgb, legMat)
            o.add(limb(tx, ty, hemX, hemY, SHIN_F, SHIN_B, b, true), legRgb, legMat)
            val dark = dim(legRgb, .6f)
            // the side seam down the leg's middle, and for jeans the gold stitching beside it
            o.add(
                FigBuilder.capsule(
                    hx + tdx * 1.4f,
                    hy + tdy * 1.4f,
                    .05f,
                    tx - sfx * .05f,
                    ty - sfy * .05f,
                    .05f,
                    3,
                ),
                dark,
                Mat.FLAT,
                .35f,
            )
            o.add(
                FigBuilder.capsule(
                    tx - sfx * .05f,
                    ty - sfy * .05f,
                    .05f,
                    hemX - sfx * .1f - sdx * .3f,
                    hemY - sfy * .1f - sdy * .3f,
                    .05f,
                    3,
                ),
                dark,
                Mat.FLAT,
                .35f,
            )
            if (legMat == Mat.DENIM) {
                o.add(
                    FigBuilder.capsule(
                        tx + sfx * .2f,
                        ty + sfy * .2f,
                        .035f,
                        hemX + sfx * .12f - sdx * .3f,
                        hemY + sfy * .12f - sdy * .3f,
                        .035f,
                        3,
                    ),
                    0xC89A4A,
                    Mat.FLAT,
                    .35f * shade,
                )
                // the hem's turned-up edge
                o.add(
                    FigBuilder.capsule(
                        hemX - sdx * .3f + sfx * 1.05f * b,
                        hemY - sdy * .3f + sfy * 1.05f * b,
                        .06f,
                        hemX - sdx * .3f - sfx * 1.1f * b,
                        hemY - sdy * .3f - sfy * 1.1f * b,
                        .06f,
                        3,
                    ),
                    mix(legRgb, 0xFFFFFF, .25f),
                    Mat.FLAT,
                    .4f,
                )
            } else {
                // the pressed crease down the front of the thigh and the shin
                val crease = mix(legRgb, 0xFFFFFF, .18f)
                o.add(
                    FigBuilder.capsule(
                        hx + tdx * 1.6f + tdy * 1.3f * b,
                        hy + tdy * 1.6f - tdx * 1.3f * b,
                        .07f,
                        tx + tdy * 1.0f * b - tdx * .3f,
                        ty - tdx * 1.0f * b - tdy * .3f,
                        .07f,
                        3,
                    ),
                    crease,
                    Mat.FLAT,
                    .3f,
                )
                o.add(
                    FigBuilder.capsule(
                        tx + sfx * .95f * b + sdx * .5f,
                        ty + sfy * .95f * b + sdy * .5f,
                        .07f,
                        hemX + sfx * .95f * b - sdx * .6f,
                        hemY + sfy * .95f * b - sdy * .6f,
                        .07f,
                        3,
                    ),
                    crease,
                    Mat.FLAT,
                    .3f,
                )
            }
            // the cloth bunching where the hem breaks over the shoe: two soft folds
            for (k in 0 until 2) {
                val up = .9f + k * .75f
                o.add(
                    FigBuilder.capsule(
                        hemX - sdx * up + sfx * 1.0f * b,
                        hemY - sdy * up + sfy * 1.0f * b,
                        .07f,
                        hemX - sdx * (up + .35f) - sfx * .2f * b,
                        hemY - sdy * (up + .35f) - sfy * .2f * b,
                        .05f,
                        3,
                    ),
                    dark,
                    Mat.FLAT,
                    .3f,
                )
            }
        } else {
            // a leg in tights: the thigh, the knee, the calf, the ankle; the shoe (a boot's
            // shaft over the ankle) after it
            o.add(limb(hx, hy, tx, ty, LEG_THIGH_F, LEG_THIGH_B, b), legRgb, Mat.SKIN)
            o.add(limb(tx, ty, ax, ay, LEG_SHIN_F, LEG_SHIN_B, b), legRgb, Mat.SKIN)
            shoe(o.shapes, look, ax, ay, fx, fy, dx, dy, shade)
        }
        // the folds behind a bent knee
        val bend = (kn / 30f).coerceIn(0f, 1f)
        if (bend > .05f)
            for (k in 0 until 2) o.add(
                FigBuilder.capsule(
                    tx - cos(rad(sa)) * (.5f + .3f * k) * b - sin(rad(sa)) * (.1f + .35f * k),
                    ty + sin(rad(sa)) * (.5f + .3f * k) * b - cos(rad(sa)) * (.1f + .35f * k),
                    .08f,
                    tx - cos(rad(sa)) * (1.1f + .1f * k) * b + sin(rad(sa)) * (.3f - .2f * k),
                    ty + sin(rad(sa)) * (1.1f + .1f * k) * b + cos(rad(sa)) * (.3f - .2f * k),
                    .06f,
                    3,
                ),
                dim(legRgb, .6f),
                Mat.FLAT,
                .35f * bend,
            )
        if (near) {
            j.footNX = ax + fx * .8f * L
            j.footNH = ay + dy * .95f * .85f * b
        } else {
            j.footFX = ax + fx * .8f * L
            j.footFH = ay + dy * .95f * .85f * b
        }
    }

    /** The shoe in its own frame (forward, down): heel, sole, ball, toe, instep, ankle. */
    private val SHOE =
        floatArrayOf(
            -.55f,
            -.55f, // the ankle's back
            -.95f,
            .05f, // the heel's back, top
            -1.0f,
            .95f, // the heel, on the ground
            .6f,
            1.0f, // the sole
            2.1f,
            .98f, // the ball
            2.55f,
            .72f, // the toe's tip
            2.35f,
            .25f, // the toe's top
            1.2f,
            -.05f, // the instep
            .45f,
            -.5f, // the ankle's front
        )

    /** The shoe's upper: the same, stopping short of the ground by the sole's depth. */
    private val UPPER =
        floatArrayOf(
            -.55f,
            -.55f,
            -.95f,
            .05f,
            -.97f,
            .62f,
            .6f,
            .66f,
            2.1f,
            .64f,
            2.46f,
            .52f,
            2.35f,
            .25f,
            1.2f,
            -.05f,
            .45f,
            -.5f,
        )

    /** Which parts of an arm [armShapes] draws: the upper arm, the forearm and hand, its stub. */
    private const val ARM_UPPER = 1
    private const val ARM_FORE = 2
    private const val ARM_STUB = 4

    /** The upper arm's and the forearm's lengths for [look], in units. */
    fun upperLength(look: Int): Float = 4.1f * StreetLooks.ALL[look].height

    fun foreLength(look: Int): Float = 3.7f * StreetLooks.ALL[look].height

    // The arms' shapes, shoulder to elbow and elbow to wrist, front and back: a sleeve full at
    // the shoulder and easing down to the cuff; a bare arm with its deltoid, its triceps, the
    // forearm's swell below the elbow and a narrow wrist
    private val UP_F = floatArrayOf(1.1f, 1.08f, 1.0f, .93f, .87f, .82f)
    private val UP_B = floatArrayOf(1.1f, 1.06f, .98f, .9f, .85f, .82f)
    private val FORE_F = floatArrayOf(.82f, .82f, .77f, .71f, .66f, .63f)
    private val FORE_B = floatArrayOf(.82f, .8f, .75f, .7f, .65f, .63f)
    val BARE_UP_F = floatArrayOf(1.1f, 1.04f, .92f, .84f, .8f, .8f)
    val BARE_UP_B = floatArrayOf(1.1f, 1.0f, .96f, .88f, .82f, .8f)
    val BARE_FORE_F = floatArrayOf(.8f, .84f, .78f, .66f, .54f, .48f)
    val BARE_FORE_B = floatArrayOf(.8f, .82f, .74f, .62f, .52f, .47f)

    /** What [look]'s coat (or jacket) is made of. */
    private fun clothOf(look: StreetLook): Int =
        when {
            look.coat == Coat.LONG -> Mat.WOOL
            look.coat == Coat.JACKET && look.bag == Bag.BACKPACK -> Mat.NYLON
            look.coat == Coat.JACKET -> Mat.WOOL
            else -> Mat.CLOTH
        }

    /**
     * An arm of [look] from the shoulder at ([sx], [sy]), [a] degrees from hanging (forward
     * positive) with the elbow bent [e] more: the [parts] asked for, ending in [hand]; the near
     * hand's joint either way.
     */
    private fun armShapes(
        o: Out,
        j: WalkerJoints,
        look: StreetLook,
        sx: Float,
        sy: Float,
        a: Float,
        e: Float,
        near: Boolean,
        parts: Int,
        hand: Int,
    ) {
        val ua = 4.1f * look.height
        val fa = 3.7f * look.height
        val b = look.build * thick(look) * look.armScale
        val shade = if (near) 1f else FAR
        val ex = sx + sin(rad(a)) * ua
        val ey = sy + cos(rad(a)) * ua
        val fa0 = a + e
        val wx = ex + sin(rad(fa0)) * fa
        val wy = ey + cos(rad(fa0)) * fa
        val w = look.wear
        val skin = dim(w.skin, shade)
        val upper = parts and ARM_UPPER != 0
        val fore = parts and ARM_FORE != 0
        // the stub: the limb run on above the elbow, so the forearm's shading carries on through
        // the joint (it is cut off again once baked)
        val stx = ex - sin(rad(fa0)) * STUB
        val sty = ey - cos(rad(fa0)) * STUB
        val stub = parts and ARM_STUB != 0
        if (look.tee == Tee.STRIPED_HOODIE && look.coat == Coat.NONE) {
            fun sleeve(x0: Float, y0: Float, x1: Float, y1: Float, r0: Float, r1: Float) {
                o.add(bowed(x0, y0, r0, x1, y1, r1, 0f), dim(w.top, shade), Mat.CLOTH)
                val dx = x1 - x0
                val dy = y1 - y0
                val len = max(.001f, sqrt(dx * dx + dy * dy))
                val nx = dy / len
                val ny = -dx / len
                for (k in -1..1) {
                    val off = k * r0 * .50f
                    o.add(
                        FigBuilder.capsule(
                            x0 + nx * off,
                            y0 + ny * off,
                            .055f,
                            x1 + nx * off * .8f,
                            y1 + ny * off * .8f,
                            .05f,
                            3,
                        ),
                        dim(0x5A7048, shade),
                        Mat.CLOTH,
                    )
                }
            }
            if (upper) sleeve(sx, sy, ex, ey, 1.13f * b, .91f * b)
            if (fore) {
                if (stub)
                    o.add(
                        bowed(stx, sty, .90f * b, ex, ey, .90f * b, 0f),
                        dim(w.top, shade),
                        Mat.CLOTH,
                    )
                sleeve(ex, ey, wx, wy, .91f * b, .64f * b)
                o.add(
                    FigBuilder.capsule(
                        wx - cos(rad(fa0)) * .62f * b,
                        wy + sin(rad(fa0)) * .62f * b,
                        .13f,
                        wx + cos(rad(fa0)) * .62f * b,
                        wy - sin(rad(fa0)) * .62f * b,
                        .13f,
                        3,
                    ),
                    dim(w.top, shade * .75f),
                    Mat.CLOTH,
                )
            }
        } else if (look.tee == Tee.PLAID_SHIRT && look.coat == Coat.NONE) {
            val cloth = dim(w.top, shade)
            fun sleeve(x0: Float, y0: Float, x1: Float, y1: Float, r0: Float, r1: Float) {
                o.add(bowed(x0, y0, r0, x1, y1, r1, 0f), cloth, Mat.CLOTH)
                val dx = x1 - x0
                val dy = y1 - y0
                val length = max(.001f, sqrt(dx * dx + dy * dy))
                val nx = dy / length
                val ny = -dx / length
                for (t in floatArrayOf(.18f, .52f, .86f)) {
                    val x = x0 + dx * t
                    val y = y0 + dy * t
                    val radius = (r0 + (r1 - r0) * t) * .92f
                    o.add(
                        FigBuilder.capsule(
                            x - nx * radius,
                            y - ny * radius,
                            .085f,
                            x + nx * radius,
                            y + ny * radius,
                            .085f,
                            3,
                        ),
                        dim(StreetCameos.PLAID_LIGHT, shade),
                        Mat.FLAT,
                    )
                }
                for (off in floatArrayOf(-.45f, .40f)) o.add(
                    FigBuilder.capsule(
                        x0 + nx * r0 * off,
                        y0 + ny * r0 * off,
                        .045f,
                        x1 + nx * r1 * off,
                        y1 + ny * r1 * off,
                        .045f,
                        3,
                    ),
                    dim(StreetCameos.PLAID_LIGHT, shade),
                    Mat.FLAT,
                )
            }
            if (upper) sleeve(sx, sy, ex, ey, 1.08f * b, .84f * b)
            if (fore) {
                if (stub) o.add(bowed(stx, sty, .82f * b, ex, ey, .82f * b, 0f), cloth, Mat.CLOTH)
                o.add(limb(ex, ey, wx, wy, BARE_FORE_F, BARE_FORE_B, b), skin, Mat.SKIN)
                val cx = ex + (wx - ex) * .43f
                val cy = ey + (wy - ey) * .43f
                sleeve(ex, ey, cx, cy, .84f * b, .75f * b)
                val fdx = sin(rad(fa0))
                val fdy = cos(rad(fa0))
                o.add(
                    FigBuilder.capsule(
                        cx - fdx * .12f - fdy * .78f * b,
                        cy - fdy * .12f + fdx * .78f * b,
                        .16f,
                        cx - fdx * .12f + fdy * .78f * b,
                        cy - fdy * .12f - fdx * .78f * b,
                        .16f,
                        3,
                    ),
                    mix(cloth, 0xFFFFFF, .20f),
                    Mat.CLOTH,
                )
            }
        } else if (look.coat == Coat.NONE) {
            // a bare arm, the tee's short sleeve over its top half
            if (upper) o.add(limb(sx, sy, ex, ey, BARE_UP_F, BARE_UP_B, b), skin, Mat.SKIN)
            // the stub above the shoulder (an upper arm layer's, cut off again once baked)
            val top = if (upper && stub) STUB else 0f
            if (fore) {
                if (stub) o.add(bowed(stx, sty, .82f * b, ex, ey, .82f * b, 0f), skin, Mat.SKIN)
                o.add(limb(ex, ey, wx, wy, BARE_FORE_F, BARE_FORE_B, b), skin, Mat.SKIN)
            }
            if (upper) {
                val sleeve =
                    if (look.tee == Tee.LOOSE_STRIPED) .72f
                    else if (look.tee == Tee.WORK_SHIRT) .67f
                    else if (look.tee == Tee.SPORT_SHIRT) .75f else .55f
                val mx = sx + (ex - sx) * sleeve
                val my = sy + (ey - sy) * sleeve
                o.add(
                    FigBuilder.hull(bowed(sx, sy - top, 1.22f * b, mx, my, 1.02f * b, 0f)),
                    dim(if (look.tee == Tee.SPORT_SHIRT) w.coat else w.top, shade),
                )
                if (look.tee == Tee.LOOSE_STRIPED) {
                    val alongX = sin(rad(a))
                    val alongY = cos(rad(a))
                    val acrossX = alongY
                    val acrossY = -alongX
                    fun band(t: Float, depth: Float, rgb: Int) {
                        val r = (1.22f + (1.02f - 1.22f) * t) * b - .05f
                        val x = sx + (mx - sx) * t
                        val y = sy + (my - sy) * t
                        o.add(
                            floatArrayOf(
                                x - acrossX * r,
                                y - acrossY * r,
                                x + acrossX * r,
                                y + acrossY * r,
                                x + acrossX * r + alongX * depth,
                                y + acrossY * r + alongY * depth,
                                x - acrossX * r + alongX * depth,
                                y - acrossY * r + alongY * depth,
                            ),
                            dim(rgb, shade),
                            Mat.FLAT,
                        )
                    }
                    for (t in floatArrayOf(.24f, .61f)) {
                        band(t, .37f, StreetCameos.STRIPE_DARK)
                        band(t + .025f, .055f, StreetCameos.STRIPE_LIGHT)
                        band(t + .105f, .055f, StreetCameos.STRIPE_LIGHT)
                    }
                }
                // the sleeve's hemmed edge, and the shade it throws on the arm
                val ax = (ex - sx)
                val ay = (ey - sy)
                val al = max(1e-3f, sqrt(ax * ax + ay * ay))
                val px = -ay / al
                val py = ax / al
                o.add(
                    FigBuilder.capsule(
                        mx - px * .98f * b + ax / al * .02f,
                        my - py * .98f * b + ay / al * .02f,
                        .14f,
                        mx + px * .98f * b,
                        my + py * .98f * b,
                        .14f,
                        3,
                    ),
                    dim(if (look.tee == Tee.SPORT_SHIRT) w.coat else w.top, shade * .6f),
                    Mat.FLAT,
                    .45f,
                )
            }
        } else {
            // the sleeve: full at the shoulder, easing to the elbow and the cuff
            val sleeve = dim(w.coat, shade)
            val cloth = clothOf(look)
            if (upper) {
                if (stub) o.add(bowed(sx, sy - STUB, 1.1f * b, sx, sy, 1.1f * b, 0f), sleeve, cloth)
                o.add(limb(sx, sy, ex, ey, UP_F, UP_B, b), sleeve, cloth)
            }
            if (fore) {
                if (stub) o.add(bowed(stx, sty, .82f * b, ex, ey, .82f * b, 0f), sleeve, cloth)
                o.add(limb(ex, ey, wx, wy, FORE_F, FORE_B, b), sleeve, cloth)
                val cx = ex + (wx - ex) * .84f
                val cy = ey + (wy - ey) * .84f
                o.add(
                    FigBuilder.capsule(cx, cy, .68f * b, wx, wy, .66f * b, 4),
                    dim(sleeve, .8f),
                    cloth,
                )
                // the sleeve's folds below the elbow, on its inside
                val fdx = sin(rad(fa0))
                val fdy = cos(rad(fa0))
                for (k in 0 until 2) {
                    val t = .7f + k * .7f
                    o.add(
                        FigBuilder.capsule(
                            ex + fdx * t + fdy * .1f,
                            ey + fdy * t - fdx * .1f,
                            .1f,
                            ex + fdx * (t + .35f) + fdy * .62f * b,
                            ey + fdy * (t + .35f) - fdx * .62f * b,
                            .08f,
                            3,
                        ),
                        dim(sleeve, .62f),
                        Mat.FLAT,
                        .3f,
                    )
                }
            }
        }
        // the hand: a relaxed hand hangs curled a little forward of the wrist, the thumb a small
        // lobe on its front; a hand holding something closes round it, the thumb over the top
        val ha = fa0 + if (hand == GRIP) 6f else 16f
        val reach = if (hand == GRIP) .62f else .75f
        val hx = wx + sin(rad(ha)) * reach
        val hy = wy + cos(rad(ha)) * reach
        if (fore && hand != POCKET) {
            val handBuild = b * look.handScale
            // the hand in the hand's own frame: [along] down it from the wrist, [fwd] towards its
            // front (the thumb's side)
            val ca = cos(rad(ha))
            val sa = sin(rad(ha))
            fun px(along: Float, fwd: Float) = wx + sa * along * handBuild + ca * fwd * handBuild
            fun py(along: Float, fwd: Float) = wy + ca * along * handBuild - sa * fwd * handBuild
            fun seg(a0: Float, f0: Float, r0: Float, a1: Float, f1: Float, r1: Float) =
                FigBuilder.capsule(
                    px(a0, f0),
                    py(a0, f0),
                    r0 * handBuild,
                    px(a1, f1),
                    py(a1, f1),
                    r1 * handBuild,
                    4,
                )
            val dark = dim(skin, .62f)
            if (hand == GRIP) {
                // a fist round what it holds: the back of the hand, the fingers rolled under with
                // their knuckles, the thumb over the top
                o.add(
                    FigBuilder.ellipse(
                        px(.62f, -.04f),
                        py(.62f, -.04f),
                        .5f * handBuild,
                        .56f * handBuild,
                        ha,
                        10,
                    ),
                    skin,
                    Mat.SKIN,
                )
                o.add(seg(.95f, -.1f, .3f, 1.05f, .3f, .27f), dim(skin, .94f), Mat.SKIN)
                for (k in 0 until 3) o.add(
                    seg(.7f + k * .13f, .12f, .02f, .74f + k * .13f, .42f, .02f),
                    dark,
                    Mat.FLAT,
                    .5f,
                )
                o.add(seg(.42f, .32f, .17f, .72f, .52f, .14f), dim(skin, .9f), Mat.SKIN)
                o.add(
                    seg(.66f, .54f, .05f, .74f, .56f, .04f),
                    mix(skin, 0xFFE8E0, .4f),
                    Mat.FLAT,
                    .5f,
                )
            } else {
                // a relaxed hand: the palm, the fingers hanging and curling a little at their
                // ends (the near one in front, the others just behind it), the thumb along the
                // front
                o.add(
                    FigBuilder.ellipse(
                        px(.5f, .0f),
                        py(.5f, .0f),
                        .4f * handBuild,
                        .5f * handBuild,
                        ha,
                        10,
                    ),
                    skin,
                    Mat.SKIN,
                )
                o.add(seg(.8f, -.05f, .17f, 1.22f, .02f, .14f), skin, Mat.SKIN)
                o.add(seg(1.22f, .02f, .14f, 1.42f, .16f, .12f), skin, Mat.SKIN)
                o.add(seg(.82f, -.3f, .15f, 1.18f, -.24f, .13f), dim(skin, .9f), Mat.SKIN)
                o.add(seg(1.18f, -.24f, .13f, 1.34f, -.12f, .11f), dim(skin, .9f), Mat.SKIN)
                // the gaps between the fingers, the knuckle's crease
                o.add(seg(.86f, -.16f, .018f, 1.3f, -.08f, .015f), dark, Mat.FLAT, .55f)
                o.add(seg(1.18f, -.12f, .02f, 1.24f, .14f, .02f), dark, Mat.FLAT, .35f)
                o.add(seg(.28f, .3f, .16f, .68f, .4f, .12f), dim(skin, .93f), Mat.SKIN)
                o.add(seg(.68f, .4f, .12f, .86f, .36f, .1f), dim(skin, .93f), Mat.SKIN)
                // a nail's light at the finger's end
                o.add(
                    FigBuilder.ellipse(
                        px(1.4f, .2f),
                        py(1.4f, .2f),
                        .06f * handBuild,
                        .05f * handBuild,
                        ha,
                        6,
                    ),
                    mix(skin, 0xFFF0EA, .5f),
                    Mat.FLAT,
                    .6f,
                )
            }
            // the light on the back of the hand
            o.add(
                FigBuilder.ellipse(
                    px(.55f, -.22f),
                    py(.55f, -.22f),
                    .1f * handBuild,
                    .26f * handBuild,
                    ha,
                    6,
                ),
                mix(skin, 0xFFF4E8, .3f),
                Mat.FLAT,
                .3f,
            )
        }
        if (near) {
            val contactX = hx + sin(rad(ha)) * .9f * b
            val contactY = hy + cos(rad(ha)) * .9f * b
            j.handNX = if (look.handScale == 1f) contactX else wx + (contactX - wx) * look.handScale
            j.handNY = if (look.handScale == 1f) contactY else wy + (contactY - wy) * look.handScale
        }
    }

    private fun skirt(
        o: Out,
        w: Wear,
        px: Float,
        py: Float,
        th: Float,
        b: Float,
        p: FloatArray,
        trail: Float,
    ) {
        val ln = p[K.LN]
        val lf = p[K.LF]
        val hem = th * .8f
        val ax = px + sin(rad(ln)) * hem
        val ay = py + cos(rad(ln)) * hem
        val bx = px + sin(rad(lf)) * hem
        val by = py + cos(rad(lf)) * hem
        val hipW = FigBuilder.lerp(2.8f, 2.1f, p[K.TURN]) * b
        val wd = hipW * 1.22f
        val back = -trail * .7f
        o.add(
            FigBuilder.hull(
                floatArrayOf(
                    px - hipW,
                    py - .6f,
                    px + hipW,
                    py - .6f,
                    ax + wd,
                    ay + .2f,
                    ax - wd * .5f + back,
                    ay + .5f,
                    bx + wd * .5f,
                    by + .2f,
                    bx - wd + back,
                    by + .5f,
                )
            ),
            w.skirt,
        )
        // a pleat's fold down the middle, where the legs part, and one towards the front
        o.add(
            FigBuilder.capsule(px + .3f, py + .6f, .16f, (ax + bx) / 2f, (ay + by) / 2f, .2f, 3),
            dim(w.skirt, .55f),
            Mat.FLAT,
            .5f,
        )
        o.add(
            FigBuilder.capsule(px + hipW * .5f, py + .9f, .12f, ax + wd * .55f, ay - .1f, .16f, 3),
            dim(w.skirt, .62f),
            Mat.FLAT,
            .35f,
        )
        // the hem's light edge
        o.add(
            FigBuilder.capsule(bx - wd + back, by + .2f, .12f, ax + wd * .95f, ay - .05f, .12f, 3),
            mix(w.skirt, 0xFFFFFF, .2f),
            Mat.FLAT,
            .3f,
        )
    }

    /**
     * How a face is cut, by who it is (their hair tells the look): how far the nose comes forward
     * and how its tip turns, how full the lips are, how the chin and the brow stand, their age.
     */
    private fun face(h: StreetHair): StreetFaceProfile =
        when (h) {
            StreetHair.CROP,
            StreetHair.CLOSE_CROP,
            StreetHair.LONG_CAP,
            StreetHair.TIGHT_CROP,
            StreetHair.HIGH_QUIFF,
            StreetHair.SHORT_RECEDING ->
                StreetFaceProfile(.17f, .02f, .02f, .03f, .03f, false, false)
            StreetHair.BEANIE -> StreetFaceProfile(.15f, .05f, .03f, .0f, .04f, false, false)
            StreetHair.PONY -> StreetFaceProfile(.12f, -.05f, .07f, -.03f, -.02f, false, true)
            StreetHair.LONG -> StreetFaceProfile(.12f, -.04f, .06f, -.03f, -.02f, false, true)
            StreetHair.CAP -> StreetFaceProfile(.14f, -.02f, .03f, .02f, .02f, false, false)
            StreetHair.BUN -> StreetFaceProfile(.15f, .04f, .0f, -.04f, .0f, true, true)
            StreetHair.FLAT -> StreetFaceProfile(.22f, .07f, -.01f, -.02f, .06f, true, false)
        }

    /**
     * The head's outline in profile, facing +x: the cranium, the forehead, the brow over the eye's
     * hollow, the nose, the lips, the chin, the jaw back to its angle under the ear. Points [lx]
     * forward and [ly] down, in the head's radii.
     */
    private fun profile(f: StreetFaceProfile): FloatArray =
        floatArrayOf(
            .12f,
            -1.0f,
            .6f,
            -.89f,
            .85f,
            -.66f,
            .93f,
            -.42f,
            .95f + f.brow,
            -.2f,
            .89f,
            -.1f,
            .93f,
            -.02f,
            1.0f + f.nose * .5f,
            .12f,
            .98f + f.nose,
            .27f - f.tip,
            1.0f + f.nose * .7f,
            .33f,
            .95f,
            .37f,
            .97f + f.lips,
            .45f,
            .93f,
            .51f,
            .96f + f.lips * .8f,
            .57f,
            .87f + f.cheek * .35f,
            .66f,
            .92f + f.chin,
            .76f,
            .85f + f.chin,
            .87f,
            .55f + f.jaw * .3f,
            .92f,
            .15f - f.jaw,
            .8f + f.jaw * .3f,
            -.1f - f.cheek * .7f,
            .58f + f.jaw * .5f,
            -.45f - f.cheek * .5f,
            .6f,
            -.8f,
            .38f,
            -.98f,
            -.04f,
            -.95f,
            -.42f,
            -.74f,
            -.78f,
            -.34f,
            -.98f,
        )

    /**
     * The head and its face, in profile: the skull and the face's profile, an ear, the eye in its
     * hollow under the brow (its white, the iris, the lid and a catchlight), the brow's hair, the
     * nostril, the lips and the mouth's line, and what makes each face its own: a beard, a pair of
     * glasses, the lines of age. Turned towards us ([g] > 0) the features come round and the far
     * eye shows; turned away ([g] < 0) only the eye's corner and the brow's end show past the
     * cheek.
     */
    private fun head(
        o: Out,
        w: Wear,
        hair: StreetHair,
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        ang: Float,
        @Suppress("UNUSED_PARAMETER") turn: Float,
        g: Float,
        features: StreetFaceProfile? = null,
    ) {
        val f = features ?: face(hair)
        val ca = cos(rad(ang))
        val sa = sin(rad(ang))
        // a point of the head: [lx] forward, [ly] down, in its radii
        fun hx(lx: Float, ly: Float) = cx + lx * rx * ca - ly * ry * sa
        fun hy(lx: Float, ly: Float) = cy + lx * rx * sa + ly * ry * ca
        fun local(pts: FloatArray, smooth: Int): FloatArray {
            val out = FloatArray(pts.size)
            var i = 0
            while (i < pts.size) {
                out[i] = hx(pts[i], pts[i + 1])
                out[i + 1] = hy(pts[i], pts[i + 1])
                i += 2
            }
            return if (smooth > 0) FigBuilder.chaikin(out, smooth) else out
        }
        fun stroke(x0: Float, y0: Float, x1: Float, y1: Float, r0: Float, r1: Float = r0) =
            FigBuilder.capsule(hx(x0, y0), hy(x0, y0), r0, hx(x1, y1), hy(x1, y1), r1, 3)
        fun dot(x: Float, y: Float, a: Float, b: Float) =
            FigBuilder.ellipse(hx(x, y), hy(x, y), a, b, ang, 8)
        val skin = w.skin
        val glance = g > 0f
        val away = g < 0f
        val gl = max(g, 0f)
        // the features come round towards us as the head turns: back along the face
        val sh = -.2f * gl
        o.add(local(profile(f), 1), skin, Mat.SKIN)
        val shade = dim(skin, .62f)
        val warm = mix(skin, 0xB0443C, .3f)
        // the ear: its shell standing off the head (a form of its own, shadowing the skull behind
        // it), the helix's rim, the hollow inside
        if (hair != StreetHair.LONG) {
            val ex = if (away) .3f else -.1f - .3f * gl
            val ew = (if (f.old) .23f else .2f) * (1f - .35f * gl)
            val lobe = if (f.old) .05f else 0f
            o.add(
                local(
                    floatArrayOf(
                        ex + ew * .2f,
                        -.14f,
                        ex + ew,
                        -.1f,
                        ex + ew * 1.05f,
                        .1f,
                        ex + ew * .7f,
                        .3f,
                        ex + ew * .1f,
                        .36f + lobe,
                        ex - ew * .7f,
                        .22f,
                        ex - ew * .9f,
                        -.02f,
                        ex - ew * .5f,
                        -.16f,
                    ),
                    2,
                ),
                mix(dim(skin, .93f), 0xC05048, .08f),
                Mat.SKIN,
            )
            o.add(
                stroke(ex - ew * .45f, -.06f, ex + ew * .1f, .26f, .07f * rx, .05f * rx),
                dim(skin, .55f),
                Mat.FLAT,
                .55f,
            )
            o.add(
                stroke(ex - ew * .7f, .0f, ex - ew * .5f, -.12f, .05f * rx),
                mix(skin, 0xFFF0E0, .3f),
                Mat.FLAT,
                .4f,
            )
            o.add(
                dot(ex + ew * .25f, .08f, .07f * rx, .1f * ry),
                mix(shade, 0x7A2E28, .25f),
                Mat.FLAT,
                .6f,
            )
        }
        // the jaw's shade under the face, where the head turns under to the neck
        if (!away) o.add(stroke(.1f, .8f, .7f, .9f, .16f * ry, .1f * ry), shade, Mat.FLAT, .35f)
        if (away) {
            // only the far brow's end and the lashes' corner show past the cheek
            o.add(stroke(.74f, -.2f, .88f, -.19f, .06f * ry), dim(w.hair, .9f), Mat.FLAT, .7f)
            o.add(stroke(.8f, -.04f, .9f, -.03f, .035f * ry), EYE, Mat.FLAT, .6f)
            return
        }
        // the eye's hollow under the brow
        o.add(dot(.76f + sh, -.06f, .2f * rx, .1f * ry), mix(shade, 0x6A3A40, .15f), Mat.FLAT, .35f)
        // a warm tone on the cheek and the nose's end
        o.add(dot(.55f + sh, .22f, .26f * rx, .14f * ry), warm, Mat.FLAT, .18f)
        o.add(dot(1.0f + f.nose * .8f, .26f - f.tip, .08f * rx, .06f * ry), warm, Mat.FLAT, .2f)
        // the eye: its white towards the front, the iris, the lid's dark line and the lashes over
        // it, the crease above, a catchlight from the sky
        val ex = .8f + sh
        o.add(dot(ex + .03f, -.03f, .13f * rx, .05f * ry), 0xEDE6DE, Mat.FLAT, .85f)
        o.add(dot(ex + .04f, -.03f, .065f * rx, .05f * ry), mix(EYE, 0x4A3020, .4f), Mat.FLAT, 1f)
        o.add(
            stroke(ex - .09f, -.065f, ex + .14f, -.07f, .034f * ry, .024f * ry),
            EYE,
            Mat.FLAT,
            .9f,
        )
        if (f.woman)
            o.add(stroke(ex + .08f, -.07f, ex + .16f, -.1f, .016f * ry), EYE, Mat.FLAT, .8f)
        o.add(stroke(ex - .05f, -.12f, ex + .1f, -.125f, .014f * ry), shade, Mat.FLAT, .45f)
        o.add(dot(ex + .055f, -.045f, .018f * rx, .015f * ry), 0xFFFFFF, Mat.FLAT, .9f)
        if (glance) {
            // the far eye, past the nose's bridge
            o.add(dot(.92f, -.03f, .05f * rx, .035f * ry), 0xEDE6DE, Mat.FLAT, .7f * gl)
            o.add(dot(.92f, -.03f, .035f * rx, .035f * ry), EYE, Mat.FLAT, .9f * gl)
        }
        // the brow's hair over the eye, a little arched; heavier and greyer with age
        val browC = if (f.old) mix(w.hair, 0x8A8680, .2f) else dim(w.hair, .9f)
        val bw = (if (f.old && !f.woman) .05f else if (f.woman) .028f else .04f) * ry
        o.add(stroke(.62f + sh, -.18f, .78f + sh, -.22f, bw, bw * 1.1f), browC, Mat.FLAT, .85f)
        o.add(
            stroke(.78f + sh, -.22f, .93f + f.brow, -.2f, bw * 1.1f, bw * .7f),
            browC,
            Mat.FLAT,
            .85f,
        )
        // the nostril's wing and its dark, the line from the nose to the mouth's corner
        o.add(
            stroke(.9f + f.nose * .6f, .25f - f.tip * .5f, .95f + f.nose * .6f, .33f, .03f * ry),
            shade,
            Mat.FLAT,
            .5f,
        )
        o.add(
            dot(.97f + f.nose * .6f, .325f, .045f * rx, .025f * ry),
            mix(shade, 0x502824, .4f),
            Mat.FLAT,
            .7f,
        )
        o.add(
            stroke(.86f + f.nose * .3f, .34f, .8f, .52f, .022f * ry),
            shade,
            Mat.FLAT,
            if (f.old) .55f else .22f,
        )
        if (f.beard) {
            o.add(
                local(
                    floatArrayOf(
                        -.09f,
                        .23f,
                        .05f,
                        .37f,
                        .38f,
                        .44f,
                        .66f,
                        .42f,
                        .91f,
                        .36f,
                        1.00f,
                        .46f,
                        .96f,
                        .62f,
                        1.0f,
                        .80f,
                        .89f,
                        .96f,
                        .58f,
                        1.06f,
                        .18f,
                        .94f,
                        -.04f,
                        .74f,
                        -.20f,
                        .47f,
                    ),
                    2,
                ),
                w.hair,
                Mat.WOOL,
            )
            for (i in 0..4) o.add(
                stroke(.18f + i * .14f, .78f, .22f + i * .14f, .87f, .02f * ry),
                mix(w.hair, skin, .25f),
                Mat.FLAT,
                .45f,
            )
        }
        // the lips: their rose, the line between them, the light on the lower lip
        val rose =
            mix(dim(skin, .88f), if (f.woman) 0xB04858 else 0xA05050, if (f.woman) .5f else .3f)
        o.add(stroke(.86f + sh * .5f, .47f, .96f + f.lips, .45f, .05f * ry), rose, Mat.FLAT, .8f)
        o.add(
            stroke(.86f + sh * .5f, .55f, .94f + f.lips * .8f, .56f, (.045f + f.lips * .2f) * ry),
            rose,
            Mat.FLAT,
            .75f,
        )
        o.add(
            stroke(.84f + sh * .5f, .51f, .95f, .51f, .02f * ry),
            mix(EYE, rose, .3f),
            Mat.FLAT,
            .75f,
        )
        o.add(dot(.92f, .565f, .03f * rx, .015f * ry), 0xFFE6DA, Mat.FLAT, .35f)
        if (f.old) {
            // the lines of age: at the eye's corner, across the brow, along the jowl
            for (k in 0 until 2) o.add(
                stroke(.6f + sh, -.06f + k * .07f, .54f + sh, -.09f + k * .1f, .014f * ry),
                shade,
                Mat.FLAT,
                .5f,
            )
            o.add(stroke(.6f, -.4f, .85f, -.42f, .015f * ry), shade, Mat.FLAT, .35f)
            o.add(stroke(.6f, .62f, .78f, .74f, .02f * ry), shade, Mat.FLAT, .35f)
        }
        if (f.moustache) {
            val moustache = mix(dim(w.hair, .68f), dim(skin, .48f), .35f)
            o.add(
                stroke(.83f + sh, .39f, 1.03f + sh, .40f, .048f * ry, .032f * ry),
                moustache,
                Mat.FLAT,
                .85f,
            )
            o.add(
                stroke(.82f + sh, .39f, .70f + sh, .42f, .045f * ry, .024f * ry),
                moustache,
                Mat.FLAT,
                .75f,
            )
        }
        if (f.goatee)
            o.add(
                local(floatArrayOf(.66f, .61f, .90f, .61f, .86f, .84f, .66f, .86f, .54f, .74f), 2),
                mix(dim(w.hair, .68f), skin, .15f),
                Mat.FLAT,
                .78f,
            )
        when (hair) {
            StreetHair.BEANIE -> {
                // a short full beard along the jaw and round the chin, the moustache over the lip
                o.add(
                    local(
                        floatArrayOf(
                            -.08f,
                            .1f,
                            .1f,
                            .22f,
                            .45f,
                            .32f,
                            .8f,
                            .4f,
                            .99f,
                            .41f,
                            1.02f,
                            .47f,
                            .9f,
                            .5f,
                            .9f,
                            .58f,
                            .95f,
                            .64f,
                            .98f,
                            .8f,
                            .9f,
                            .95f,
                            .55f,
                            1.02f,
                            .1f,
                            .88f,
                            -.12f,
                            .6f,
                        ),
                        2,
                    ),
                    w.hair,
                    Mat.HAIR,
                    .95f,
                )
            }
            StreetHair.BUN -> {
                // reading glasses: the rim seen edge on in front of the eye, its glint, the arm
                // back to the ear, a faint sheen on the lens
                val frame = 0x6A5A50
                o.add(stroke(1.0f, -.17f, 1.02f, .06f, .03f * ry), frame, Mat.FLAT, .9f)
                o.add(stroke(.985f, -.13f, .99f, -.03f, .015f * ry), 0xFFFFFF, Mat.FLAT, .55f)
                o.add(stroke(.05f, -.06f, 1.0f, -.12f, .022f * ry), frame, Mat.FLAT, .85f)
                o.add(stroke(.6f, -.18f, .98f, -.16f, .06f * ry), 0xD8E4EC, Mat.FLAT, .12f)
            }
            StreetHair.CROP,
            StreetHair.CAP -> {
                // the shadow of a shave along the jaw
                o.add(
                    local(
                        floatArrayOf(
                            .05f,
                            .3f,
                            .6f,
                            .5f,
                            .9f,
                            .62f,
                            .9f,
                            .9f,
                            .5f,
                            .95f,
                            .05f,
                            .7f,
                        ),
                        2,
                    ),
                    mix(dim(skin, .8f), 0x40404A, .25f),
                    Mat.FLAT,
                    .14f,
                )
            }
            else -> {}
        }
    }

    /**
     * A rider's head for [look] at ([hx], [hy]), turned [ang] degrees: the face, the hair at the
     * nape and a helmet in the look's hat colour, its vents and its strap.
     */
    fun riderHead(shapes: ArrayList<Shape>, look: StreetLook, hx: Float, hy: Float, ang: Float) {
        val o = Out()
        val w = look.wear
        val hgt = look.height
        val rx = 1.35f * hgt * look.head * (look.face?.skullWidth ?: 1f)
        val ry = 1.65f * hgt * look.head * (look.face?.skullHeight ?: 1f)
        head(o, w, look.hair, hx, hy, rx, ry, ang, 0f, 0f)
        o.add(cap(hx, hy, rx, ry, ang, -.1f, -.6f, 1.06f, 0f, -.08f), w.hair, Mat.HAIR)
        val ca = cos(rad(ang))
        val sa = sin(rad(ang))
        fun px(lx: Float, ly: Float) = hx + lx * rx * ca - ly * ry * sa
        fun py(lx: Float, ly: Float) = hy + lx * rx * sa + ly * ry * ca
        // the strap from the shell down under the jaw
        o.add(
            FigBuilder.capsule(
                px(-.08f, -.2f),
                py(-.08f, -.2f),
                .12f,
                px(.4f, .78f),
                py(.4f, .78f),
                .12f,
                3,
            ),
            0x1C1C20,
            Mat.FLAT,
            .75f,
        )
        o.add(cap(hx, hy, rx, ry, ang, .5f, -.05f, 1.24f, .14f, -.1f), w.hat, Mat.LEATHER)
        // the vents: three dark slots along the shell
        for (k in 0 until 3) {
            val t = -.55f + k * .5f
            o.add(
                FigBuilder.capsule(
                    px(t, -1.08f + abs(t) * .28f),
                    py(t, -1.08f + abs(t) * .28f),
                    .14f,
                    px(t + .26f, -1.1f + abs(t + .26f) * .28f),
                    py(t + .26f, -1.1f + abs(t + .26f) * .28f),
                    .14f,
                    3,
                ),
                dim(w.hat, .4f),
                Mat.FLAT,
                .8f,
            )
        }
        // the light along the shell's crown
        o.add(
            FigBuilder.capsule(
                px(-.65f, -1.08f),
                py(-.65f, -1.08f),
                .2f,
                px(.35f, -1.18f),
                py(.35f, -1.18f),
                .14f,
                4,
            ),
            mix(w.hat, 0xFFFFFF, .45f),
            Mat.FLAT,
            .45f,
        )
        // the shell's rim
        o.add(
            FigBuilder.capsule(
                px(.95f, -.48f),
                py(.95f, -.48f),
                .2f,
                px(-1.1f, .12f),
                py(-1.1f, .12f),
                .2f,
                3,
            ),
            dim(w.hat, .62f),
        )
        shapes += o.shapes
    }

    /**
     * A shoe for [look] with its ankle at ([ax], [ay]), forward along ([fx], [fy]) and down along
     * ([dx], [dy]), its colours at [shade]: the sole, then the upper.
     */
    fun shoe(
        shapes: ArrayList<Shape>,
        look: StreetLook,
        ax: Float,
        ay: Float,
        fx: Float,
        fy: Float,
        dx: Float,
        dy: Float,
        shade: Float,
    ) {
        val L = look.height
        val b = look.build
        fun foot(shape: FloatArray): FloatArray {
            val pts = FloatArray(shape.size)
            var i = 0
            while (i < shape.size) {
                val sx = shape[i] * L
                val sy = shape[i + 1] * (.85f * b)
                pts[i] = ax + fx * sx + dx * sy
                pts[i + 1] = ay + fy * sx + dy * sy
                i += 2
            }
            return FigBuilder.chaikin(pts, 1)
        }
        val w = look.wear
        val sole = dim(w.sole, shade)
        val upper = dim(w.shoe, shade)
        fun add(pts: FloatArray, rgb: Int, mat: Int = Mat.FLAT, alpha: Float = 1f) {
            shapes += Shape(Shape.BODY, pts, 1f, rgb, mat, alpha)
        }
        // a line in the foot's frame, [r] units thick
        fun line(x0: Float, y0: Float, x1: Float, y1: Float, r: Float): FloatArray {
            val a = foot(floatArrayOf(x0, y0, x1, y1))
            return FigBuilder.capsule(a[0], a[1], r, a[a.size - 2], a[a.size - 1], r, 3)
        }
        if (look.shoes == Shoe.BOOT) {
            // the shaft up over the ankle, a little wider than it
            val r = .86f * b
            add(
                FigBuilder.capsule(
                    ax - dx * .1f,
                    ay - dy * .1f,
                    r,
                    ax - dx * 1.9f,
                    ay - dy * 1.9f,
                    r * .92f,
                    6,
                ),
                upper,
                Mat.LEATHER,
            )
        }
        if (look.shoes == Shoe.SANDAL) {
            add(foot(SHOE), sole, Mat.RUBBER)
            add(foot(UPPER), dim(w.skin, shade), Mat.SKIN)
            for (x in floatArrayOf(.10f, 1.37f)) {
                add(
                    foot(
                        floatArrayOf(
                            x - .23f,
                            -.28f,
                            x + .23f,
                            -.22f,
                            x + .38f,
                            .61f,
                            x - .14f,
                            .66f,
                        )
                    ),
                    upper,
                    Mat.LEATHER,
                )
                add(line(x, .05f, x + .10f, .40f, .07f), mix(upper, 0xFFFFFF, .20f), Mat.FLAT)
            }
            add(line(-.78f, -.55f, -.75f, .40f, .20f), upper, Mat.LEATHER)
            for (i in 0..2) add(
                line(2.05f + i * .11f, .21f, 2.09f + i * .11f, .46f, .027f),
                dim(w.skin, shade * .65f),
                Mat.FLAT,
                .7f,
            )
            return
        }
        add(foot(SHOE), sole, Mat.RUBBER)
        add(foot(UPPER), upper, Mat.LEATHER)
        // the light along the sole's top edge, where it meets the upper
        add(line(-.85f, .7f, 2.25f, .66f, .12f), mix(sole, 0xFFFFFF, .4f), Mat.FLAT, .6f)
        when (look.shoes) {
            Shoe.TRAINER -> {
                // the rubber bumper round the toe, a stripe down the side, the laces
                add(line(1.95f, .5f, 2.4f, .45f, .16f), mix(sole, upper, .25f), Mat.FLAT, .7f)
                val light = (w.shoe shr 16 and 255) > 150
                if (!look.plainTrainers)
                    add(
                        line(-.5f, .38f, 1.35f, .12f, .16f),
                        if (light) dim(0x2E3A55, shade) else mix(upper, 0xFFFFFF, .75f),
                        Mat.FLAT,
                        .6f,
                    )
                for (k in 0 until 3) {
                    val t = .55f + k * .35f
                    add(
                        line(t, -.32f + k * .1f, t + .18f, -.22f + k * .1f, .07f),
                        if (light) dim(0xB8B8B0, shade) else mix(upper, 0xFFFFFF, .6f),
                        Mat.FLAT,
                        .6f,
                    )
                }
            }
            Shoe.LEATHER -> {
                // the toe cap's seam and the heel counter, the shine on the toe
                add(line(1.55f, -.02f, 1.75f, .55f, .07f), dim(upper, .55f), Mat.FLAT, .5f)
                add(line(-.2f, -.35f, -.55f, .55f, .07f), dim(upper, .6f), Mat.FLAT, .4f)
                add(line(1.8f, .1f, 2.2f, .25f, .16f), mix(upper, 0xFFFFFF, .45f), Mat.FLAT, .5f)
            }
            Shoe.BOOT -> {
                // the shaft's top edge, a side zip, the shine on the toe
                add(
                    FigBuilder.capsule(
                        ax - dx * 1.9f - fx * .8f * b,
                        ay - dy * 1.9f - fy * .8f * b,
                        .1f,
                        ax - dx * 1.9f + fx * .8f * b,
                        ay - dy * 1.9f + fy * .8f * b,
                        .1f,
                        3,
                    ),
                    mix(upper, 0xFFFFFF, .25f),
                    Mat.FLAT,
                    .5f,
                )
                add(
                    FigBuilder.capsule(
                        ax - dx * 1.7f,
                        ay - dy * 1.7f,
                        .07f,
                        ax + fx * -.2f,
                        ay + fy * -.2f,
                        .07f,
                        3,
                    ),
                    mix(upper, 0xFFFFFF, .3f),
                    Mat.FLAT,
                    .35f,
                )
                add(line(1.85f, .05f, 2.2f, .2f, .1f), mix(upper, 0xFFFFFF, .35f), Mat.FLAT, .4f)
            }
            Shoe.SANDAL -> {} // rendered above with exposed feet
            Shoe.FLAT -> {
                // the strap over the instep, its little buckle
                add(line(.7f, -.15f, .95f, .55f, .12f), dim(upper, .6f), Mat.FLAT, .55f)
                add(line(.85f, .3f, .9f, .38f, .08f), 0xB8A98A, Mat.FLAT, .6f)
            }
        }
    }

    /** The eye, at this distance a dark dot. */
    private const val EYE = 0x231A16

    /**
     * A skull-cap of hair (or a hat's crown) round the head: the head's oval [grow] times its size,
     * cut along a line from [front] (in head radii, up from the middle, at the face) to [back] (at
     * the nape).
     */
    private fun cap(
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        ang: Float,
        front: Float,
        back: Float,
        grow: Float,
        lift: Float = 0f,
        shift: Float = 0f,
        temple: Boolean = false,
    ): FloatArray {
        val n = if (temple) 48 else 22
        val out = FloatArray(n * 2)
        val ca = cos(rad(ang))
        val sa = sin(rad(ang))
        for (i in 0 until n) {
            val t = i / n.toFloat() * 2f * PI.toFloat()
            val lx = cos(t) * grow + shift
            // up, in head radii
            var up = -sin(t) * grow + lift
            val line =
                if (temple) hairline(lx / grow, front, back)
                else back + (front - back) * ((lx / grow + 1f) / 2f).coerceIn(0f, 1f)
            if (up < line) up = line
            val x = lx * rx
            val y = -up * ry
            out[i * 2] = cx + x * ca - y * sa
            out[i * 2 + 1] = cy + x * sa + y * ca
        }
        return out
    }

    /**
     * A hairline round the side of the head, [n] from -1 (the nape) to 1 (the face): the forehead
     * at [front], back at the temple, down in front of the ear as a sideburn, up over the ear and
     * down behind it to the nape at [back].
     */
    private fun hairline(n: Float, front: Float, back: Float): Float {
        val xs = HAIRLINE_X
        val ys = floatArrayOf(back, back * .4f, .26f, .2f, -.2f, front * .55f, front, front)
        if (n <= xs[0]) return ys[0]
        for (i in 1 until xs.size) if (n <= xs[i]) {
            val t = (n - xs[i - 1]) / (xs[i] - xs[i - 1])
            return ys[i - 1] + (ys[i] - ys[i - 1]) * t
        }
        return front
    }

    private val HAIRLINE_X = floatArrayOf(-1f, -.5f, -.25f, .02f, .14f, .32f, .55f, 1f)

    private fun hair(
        o: Out,
        h: StreetHair,
        w: Wear,
        hx: Float,
        hy: Float,
        rx: Float,
        ry: Float,
        ang: Float,
        hgt: Float,
        trail2: Float,
        tail: Boolean,
    ) {
        val ux = sin(rad(ang))
        val uy = -cos(rad(ang))
        val fx = cos(rad(ang))
        val fy = sin(rad(ang))
        val hair = w.hair
        // a point on the head: [lx] head radii forward, [up] up
        fun hp(lx: Float, up: Float) = hx + fx * lx * rx + ux * up * ry
        fun vp(lx: Float, up: Float) = hy + fy * lx * rx + uy * up * ry
        when (h) {
            StreetHair.HIGH_QUIFF -> {
                // A single tall swept crest connected to the sideburn and nape.
                val outline =
                    floatArrayOf(
                        .72f,
                        .65f,
                        .96f,
                        1.05f,
                        .96f,
                        1.43f,
                        .77f,
                        1.62f,
                        .39f,
                        1.68f,
                        -.04f,
                        1.65f,
                        -.47f,
                        1.56f,
                        -.79f,
                        1.28f,
                        -.98f,
                        .83f,
                        -1.03f,
                        .24f,
                        -.95f,
                        -.39f,
                        -.73f,
                        -.78f,
                        -.48f,
                        -.81f,
                        -.36f,
                        -.42f,
                        -.34f,
                        .03f,
                        -.10f,
                        .23f,
                        .12f,
                        .23f,
                        .24f,
                        .02f,
                        .34f,
                        -.33f,
                        .48f,
                        -.37f,
                        .54f,
                        .18f,
                        .53f,
                        .68f,
                    )
                val points = FloatArray(outline.size)
                for (i in outline.indices step 2) {
                    points[i] = hp(outline[i], outline[i + 1])
                    points[i + 1] = vp(outline[i], outline[i + 1])
                }
                o.add(FigBuilder.chaikin(points, 2), hair, Mat.WOOL)
                // Fine upward comb lines stay inside the mass; no floating hair blobs.
                for (k in 0..8) {
                    val x = -.75f + k * .16f
                    val up = 1.20f + .27f * (1f - kotlin.math.abs(x - .10f))
                    o.add(
                        FigBuilder.capsule(
                            hp(x, .72f),
                            vp(x, .72f),
                            .025f,
                            hp(x - .08f, up),
                            vp(x - .08f, up),
                            .018f,
                            3,
                        ),
                        mix(hair, 0xA28E71, .17f),
                        Mat.FLAT,
                        .65f,
                    )
                }
            }
            StreetHair.SHORT_RECEDING -> {
                val local =
                    floatArrayOf(
                        .58f,
                        -.88f,
                        .17f,
                        -1.02f,
                        -.35f,
                        -.98f,
                        -.79f,
                        -.73f,
                        -.98f,
                        -.32f,
                        -.88f,
                        .10f,
                        -.65f,
                        .40f,
                        -.40f,
                        .51f,
                        -.29f,
                        .27f,
                        -.31f,
                        -.06f,
                        -.14f,
                        -.20f,
                        .0f,
                        -.18f,
                        .10f,
                        -.05f,
                        .19f,
                        -.20f,
                        .17f,
                        -.50f,
                        .28f,
                        -.71f,
                    )
                val points = FloatArray(local.size)
                for (i in local.indices step 2) {
                    points[i] = hp(local[i], -local[i + 1])
                    points[i + 1] = vp(local[i], -local[i + 1])
                }
                o.add(FigBuilder.chaikin(points, 2), hair, Mat.WOOL)
            }
            StreetHair.TIGHT_CROP -> {
                val local =
                    floatArrayOf(
                        .85f,
                        -.53f,
                        .78f,
                        -.75f,
                        .45f,
                        -.99f,
                        .02f,
                        -1.06f,
                        -.45f,
                        -.98f,
                        -.81f,
                        -.76f,
                        -.99f,
                        -.36f,
                        -.91f,
                        .06f,
                        -.71f,
                        .34f,
                        -.45f,
                        .48f,
                        -.37f,
                        .30f,
                        -.37f,
                        -.08f,
                        -.15f,
                        -.22f,
                        .02f,
                        -.15f,
                        .14f,
                        .0f,
                        .22f,
                        -.12f,
                        .25f,
                        -.39f,
                        .50f,
                        -.49f,
                        .72f,
                        -.53f,
                    )
                val points = FloatArray(local.size)
                for (i in local.indices step 2) {
                    points[i] = hp(local[i], -local[i + 1])
                    points[i + 1] = vp(local[i], -local[i + 1])
                }
                o.add(FigBuilder.chaikin(points, 2), hair, Mat.WOOL)
                // Fine texture sits inside the crop and never changes the crown silhouette.
                for (row in 0..1) for (col in 0..4) {
                    val x = -.58f + col * .23f
                    val up = .62f + row * .19f
                    o.add(
                        FigBuilder.capsule(
                            hp(x, up),
                            vp(x, up),
                            .025f,
                            hp(x + .035f, up + .025f),
                            vp(x + .035f, up + .025f),
                            .02f,
                            3,
                        ),
                        mix(hair, 0x8B7867, .25f),
                        Mat.FLAT,
                        .32f,
                    )
                }
            }
            StreetHair.LONG_CAP -> {
                // Shoulder-length hair stays one connected shape; the hat sits over the roots.
                val local =
                    floatArrayOf(
                        .65f,
                        -.72f,
                        .27f,
                        -.98f,
                        -.28f,
                        -1.0f,
                        -.73f,
                        -.83f,
                        -1.04f,
                        -.33f,
                        -1.07f,
                        .28f,
                        -1.22f,
                        .85f,
                        -1.08f,
                        1.35f,
                        -.90f,
                        1.55f,
                        -.81f,
                        1.37f,
                        -.68f,
                        1.56f,
                        -.59f,
                        1.37f,
                        -.45f,
                        1.46f,
                        -.34f,
                        1.15f,
                        -.40f,
                        .70f,
                        -.45f,
                        .28f,
                        -.39f,
                        -.08f,
                        -.19f,
                        -.20f,
                        .02f,
                        -.14f,
                        .09f,
                        .28f,
                        .22f,
                        .62f,
                        .37f,
                        .49f,
                        .33f,
                        -.15f,
                        .64f,
                        -.46f,
                    )
                val points = FloatArray(local.size)
                for (i in local.indices step 2) {
                    val drift =
                        if (local[i + 1] > .65f) trail2 * .075f * (local[i + 1] - .65f) else 0f
                    points[i] = hp(local[i] + drift, -local[i + 1])
                    points[i + 1] = vp(local[i] + drift, -local[i + 1])
                }
                o.add(FigBuilder.chaikin(points, 2), hair, Mat.HAIR)
                for (i in 0..4) {
                    val x = -.85f + i * .105f
                    o.add(
                        FigBuilder.capsule(
                            hp(x, .03f),
                            vp(x, .03f),
                            .035f,
                            hp(x - .08f, -1.18f),
                            vp(x - .08f, -1.18f),
                            .025f,
                            3,
                        ),
                        mix(hair, 0xFFF0BA, .36f),
                        Mat.FLAT,
                        .55f,
                    )
                }
                // Forward cap: the bill extends ahead of the face, with a red stitched edge.
                o.add(cap(hx, hy, rx, ry, ang, .58f, .10f, 1.16f, .16f, -.06f), w.hat, Mat.CLOTH)
                val bill = floatArrayOf(.85f, -.43f, 1.92f, -.40f, 2.05f, -.51f, 1.00f, -.70f)
                val brim = FloatArray(bill.size)
                for (i in bill.indices step 2) {
                    brim[i] = hp(bill[i], -bill[i + 1])
                    brim[i + 1] = vp(bill[i], -bill[i + 1])
                }
                o.add(FigBuilder.chaikin(brim, 1), dim(w.hat, .77f), Mat.CLOTH)
                o.add(
                    FigBuilder.capsule(
                        hp(1.00f, .47f),
                        vp(1.00f, .47f),
                        .045f,
                        hp(1.95f, .43f),
                        vp(1.95f, .43f),
                        .035f,
                        3,
                    ),
                    0xA44543,
                    Mat.FLAT,
                    .8f,
                )
                // Rear adjustment strap, without a team name or logo.
                o.add(
                    FigBuilder.capsule(
                        hp(-.60f, .58f),
                        vp(-.60f, .58f),
                        .10f,
                        hp(-1.02f, .57f),
                        vp(-1.02f, .57f),
                        .10f,
                        3,
                    ),
                    dim(w.hat, .6f),
                    Mat.CLOTH,
                )
            }
            StreetHair.CLOSE_CROP -> {
                // One close-cut silhouette: forehead, crown, back of skull, tapered nape,
                // then around the ear to the sideburn. No detached bumps at the crown.
                val local =
                    floatArrayOf(
                        .83f,
                        -.39f,
                        .71f,
                        -.77f,
                        .31f,
                        -1.01f,
                        -.20f,
                        -1.03f,
                        -.69f,
                        -.86f,
                        -.96f,
                        -.46f,
                        -1.00f,
                        -.04f,
                        -.83f,
                        .37f,
                        -.57f,
                        .65f,
                        -.38f,
                        .76f,
                        -.33f,
                        .55f,
                        -.45f,
                        .27f,
                        -.43f,
                        -.03f,
                        -.28f,
                        -.20f,
                        -.10f,
                        -.21f,
                        .035f,
                        -.12f,
                        .07f,
                        .13f,
                        .18f,
                        .12f,
                        .17f,
                        -.23f,
                        .34f,
                        -.38f,
                        .58f,
                        -.40f,
                        .74f,
                        -.36f,
                    )
                val points = FloatArray(local.size)
                for (i in local.indices step 2) {
                    points[i] = hp(local[i], -local[i + 1])
                    points[i + 1] = vp(local[i], -local[i + 1])
                }
                o.add(FigBuilder.chaikin(points, 2), hair, Mat.HAIR)
                // Fine surface strands stay inside the silhouette rather than changing its outline.
                for (i in 0..4) {
                    val x = -.57f + i * .23f
                    o.add(
                        FigBuilder.capsule(
                            hp(x, .72f),
                            vp(x, .72f),
                            .025f,
                            hp(x - .10f, .83f),
                            vp(x - .10f, .83f),
                            .02f,
                            3,
                        ),
                        mix(hair, 0x8A7869, .22f),
                        Mat.FLAT,
                        .30f,
                    )
                }
            }
            StreetHair.CROP ->
                o.add(
                    cap(hx, hy, rx, ry, ang, .6f, -.45f, 1.08f, .04f, -.06f, true),
                    hair,
                    Mat.HAIR,
                )
            StreetHair.BUN -> {
                o.add(
                    cap(hx, hy, rx, ry, ang, .58f, -.25f, 1.07f, .03f, -.05f, true),
                    hair,
                    Mat.HAIR,
                )
                o.add(
                    FigBuilder.ellipse(
                        hx + ux * ry * .95f - fx * rx * .75f,
                        hy + uy * ry * .95f - fy * rx * .75f,
                        1.0f * hgt,
                        .95f * hgt,
                        ang,
                        10,
                    ),
                    hair,
                    Mat.HAIR,
                )
            }
            StreetHair.PONY -> {
                // the tail swings behind the bob
                val x0 = hx - fx * rx * .95f + ux * ry * .3f
                val y0 = hy - fy * rx * .95f + uy * ry * .3f
                if (tail) ponytail(o, hair, x0, y0, ang, hgt, trail2)
                o.add(
                    cap(hx, hy, rx, ry, ang, .58f, -.3f, 1.08f, .03f, -.05f, true),
                    hair,
                    Mat.HAIR,
                )
                // the band round the tail
                o.add(
                    FigBuilder.ellipse(x0 - fx * .35f, y0 - fy * .35f, .4f, .62f, ang, 6),
                    dim(w.skirt, .9f),
                )
            }
            StreetHair.LONG -> {
                // the hair down the back, swinging a little behind the walk
                val x0 = hx - fx * rx * .6f
                val y0 = hy - fy * rx * .6f
                val sw = .9f * trail2
                val x1 = x0 - fx * .5f - ux * (ry * 2.2f) + sw
                val y1 = y0 - fy * .5f - uy * (ry * 2.2f)
                o.add(FigBuilder.capsule(x0, y0, 1.3f, x1, y1, 1.1f, 7), hair, Mat.HAIR)
                o.add(cap(hx, hy, rx, ry, ang, .56f, -.9f, 1.1f, .02f, -.06f, true), hair, Mat.HAIR)
            }
            StreetHair.BEANIE -> {
                // the hair showing under it at the nape, the knitted crown, its turned-up brim
                // and the bobble
                o.add(cap(hx, hy, rx, ry, ang, .5f, -.55f, 1.05f, 0f, -.1f, true), hair, Mat.HAIR)
                o.add(
                    FigBuilder.ellipse(
                        hx + ux * ry * 1.25f - fx * rx * .1f,
                        hy + uy * ry * 1.25f - fy * rx * .1f,
                        .6f,
                        .6f,
                        ang,
                        8,
                    ),
                    w.hat,
                    Mat.KNIT,
                )
                o.add(
                    cap(hx, hy, rx, ry, ang, .45f, -.2f, 1.2f, .02f, -.04f),
                    dim(w.hat, .8f),
                    Mat.KNIT,
                )
                o.add(cap(hx, hy, rx, ry, ang, .75f, .1f, 1.15f, .1f, -.04f), w.hat, Mat.KNIT)
            }
            StreetHair.FLAT -> {
                // grey hair at the nape and over the ear, then the cap: a low crown pulled
                // forward and down to a short peak
                o.add(cap(hx, hy, rx, ry, ang, .5f, -.6f, 1.07f, 0f, -.1f, true), hair, Mat.HAIR)
                o.add(
                    cap(hx, hy, rx, ry, ang, .5f, .2f, 1.14f, -.04f, .06f),
                    dim(w.hat, .82f),
                    Mat.TWEED,
                )
                o.add(cap(hx, hy, rx, ry, ang, .62f, .34f, 1.12f, -.02f, .1f), w.hat, Mat.TWEED)
                val bx = hx + ux * ry * .5f + fx * rx * .9f
                val by = hy + uy * ry * .5f + fy * rx * .9f
                o.add(
                    FigBuilder.capsule(
                        bx,
                        by,
                        .24f,
                        bx + fx * .95f,
                        by + fy * .95f + .28f,
                        .13f,
                        5,
                    ),
                    dim(w.hat, .62f),
                )
            }
            StreetHair.CAP -> {
                o.add(cap(hx, hy, rx, ry, ang, .5f, -.5f, 1.06f, 0f, -.08f, true), hair, Mat.HAIR)
                o.add(cap(hx, hy, rx, ry, ang, .44f, .08f, 1.13f, .06f, -.04f), w.hat)
                val bx = hx + ux * ry * .4f + fx * rx * .82f
                val by = hy + uy * ry * .4f + fy * rx * .82f
                o.add(
                    FigBuilder.capsule(
                        bx,
                        by,
                        .24f,
                        bx + fx * 1.1f,
                        by + fy * 1.1f + .22f,
                        .13f,
                        5,
                    ),
                    dim(w.hat, .78f),
                )
                // the button on top
                o.add(
                    FigBuilder.ellipse(
                        hx + ux * ry * 1.17f,
                        hy + uy * ry * 1.17f,
                        .3f,
                        .2f,
                        ang,
                        6,
                    ),
                    dim(w.hat, .7f),
                )
                // the peak's stitched edge catching the light, and a seam over the crown
                o.add(
                    FigBuilder.capsule(
                        bx + fx * .2f,
                        by + fy * .2f - .2f,
                        .1f,
                        bx + fx * 1.0f,
                        by + fy * 1.0f,
                        .08f,
                        3,
                    ),
                    mix(w.hat, 0xFFFFFF, .3f),
                    Mat.FLAT,
                    .45f,
                )
                o.add(
                    FigBuilder.capsule(
                        hp(0f, 1.15f),
                        vp(0f, 1.15f),
                        .07f,
                        hp(.5f, .55f),
                        vp(.5f, .55f),
                        .07f,
                        3,
                    ),
                    dim(w.hat, .6f),
                    Mat.FLAT,
                    .45f,
                )
            }
        }
        when (h) {
            StreetHair.CROP,
            StreetHair.BUN,
            StreetHair.PONY,
            StreetHair.LONG -> {
                // strands combed back from the hairline
                val dark = dim(hair, .62f)
                for (k in 0 until 3) {
                    val f0 = .3f - k * .32f
                    o.add(
                        FigBuilder.capsule(
                            hp(f0, 1.02f - k * .05f),
                            vp(f0, 1.02f - k * .05f),
                            .07f,
                            hp(f0 - .55f, .62f - k * .1f),
                            vp(f0 - .55f, .62f - k * .1f),
                            .06f,
                            3,
                        ),
                        dark,
                        Mat.FLAT,
                        .4f,
                    )
                }
            }
            StreetHair.BEANIE -> {
                // the knit's ribs on the turned-up brim
                for (k in 0 until 4) {
                    val f0 = .6f - k * .45f
                    o.add(
                        FigBuilder.capsule(
                            hp(f0, .32f + .1f * k),
                            vp(f0, .32f + .1f * k),
                            .07f,
                            hp(f0 - .05f, .62f + .1f * k),
                            vp(f0 - .05f, .62f + .1f * k),
                            .07f,
                            3,
                        ),
                        dim(w.hat, .62f),
                        Mat.FLAT,
                        .4f,
                    )
                }
            }
            else -> {}
        }
    }

    /**
     * A ponytail from its root ([x0], [y0]), the head at [ang], swinging [trail2] behind the bob.
     */
    private fun ponytail(
        o: Out,
        hair: Int,
        x0: Float,
        y0: Float,
        ang: Float,
        hgt: Float,
        trail2: Float,
    ) {
        val swing = 14f * trail2
        val a1 = ang + 196f + swing
        val a2 = a1 + 18f + swing * .8f
        val x1 = x0 + sin(rad(a1)) * 2.0f * hgt
        val y1 = y0 - cos(rad(a1)) * 2.0f * hgt
        val x2 = x1 + sin(rad(a2)) * 2.6f * hgt
        val y2 = y1 - cos(rad(a2)) * 2.6f * hgt
        o.add(FigBuilder.capsule(x0, y0, .9f, x1, y1, .85f, 6), hair, Mat.HAIR)
        o.add(FigBuilder.capsule(x1, y1, .85f, x2, y2, .3f, 6), hair, Mat.HAIR)
    }

    private fun shoulderBag(
        o: Out,
        w: Wear,
        px: Float,
        py: Float,
        sx: Float,
        sy: Float,
        nx: Float,
        ny: Float,
        ux: Float,
        uy: Float,
        b: Float,
        hgt: Float,
        hipW: Float,
        trail: Float,
    ) {
        // the strap over the near shoulder down to the far hip, the bag hanging there and swinging,
        // its flap and buckle
        val x0 = sx + nx * .9f * b + ux * .3f
        val y0 = sy + ny * .9f * b + uy * .3f
        val bx = px - nx * (hipW + .9f * b) - trail * .5f
        val by = py - ny * (hipW + .9f * b) + 1.2f * hgt
        val ang = -trail * 7f
        o.add(
            FigBuilder.capsule(x0, y0, .24f * b, bx, by - 1.2f * hgt, .22f * b, 5),
            dim(w.bag, .8f),
            Mat.LEATHER,
        )
        o.add(box(bx, by, 1.5f * hgt, 1.1f * hgt, ang), w.bag, Mat.LEATHER)
        o.add(
            box(bx + sin(rad(ang)) * .4f * hgt, by - .42f * hgt, 1.56f * hgt, .7f * hgt, ang),
            dim(w.bag, .86f),
            Mat.LEATHER,
        )
        // the flap's brass buckle and its strap
        o.add(
            FigBuilder.capsule(
                bx + .35f * hgt,
                by - .2f * hgt,
                .12f,
                bx + .35f * hgt,
                by + .45f * hgt,
                .12f,
                3,
            ),
            dim(w.bag, .7f),
            Mat.LEATHER,
        )
        o.add(
            FigBuilder.ellipse(bx + .35f * hgt, by + .22f * hgt, .2f, .16f, 0f, 8),
            0xB8984A,
            Mat.METAL,
        )
        // the stitching round the flap's edge, and the light along the strap's top
        val cs = cos(rad(ang))
        val sn = sin(rad(ang))
        val fy = by - .5f * hgt + .62f * hgt
        o.add(
            FigBuilder.capsule(
                bx - 1.3f * hgt * cs + sin(rad(ang)) * .4f * hgt,
                fy - .2f * hgt - 1.3f * hgt * sn,
                .04f,
                bx + 1.3f * hgt * cs + sin(rad(ang)) * .4f * hgt,
                fy - .2f * hgt + 1.3f * hgt * sn,
                .04f,
                3,
            ),
            mix(w.bag, 0xFFFFFF, .4f),
            Mat.FLAT,
            .4f,
        )
    }

    private fun backpack(
        o: Out,
        w: Wear,
        px: Float,
        py: Float,
        sx: Float,
        sy: Float,
        nx: Float,
        ny: Float,
        ux: Float,
        uy: Float,
        b: Float,
        hgt: Float,
        trail2: Float,
    ) {
        // the pack on the back, from the shoulders down to the waist, a little behind the torso,
        // and its front pocket
        val top = .88f
        val bottom = .34f
        val out = 1.5f * b + .12f * trail2
        val tx = px + ux * (7.4f * hgt * top)
        val ty = py + uy * (7.4f * hgt * top)
        val bx = px + ux * (7.4f * hgt * bottom)
        val by = py + uy * (7.4f * hgt * bottom)
        val w0 = 2.4f * b
        val w1 = 2.1f * b
        o.add(
            FigBuilder.chaikin(
                floatArrayOf(
                    tx - nx * w0,
                    ty - ny * w0,
                    tx - nx * (w0 + out * .8f),
                    ty - ny * (w0 + out * .8f) + .9f,
                    bx - nx * (w1 + out),
                    by - ny * (w1 + out) - .4f,
                    bx - nx * (w1 + out * .5f),
                    by - ny * (w1 + out * .5f) + .4f,
                    bx - nx * w1,
                    by - ny * w1,
                ),
                2,
            ),
            w.bag,
            Mat.NYLON,
        )
        // the carry loop over the top
        o.add(
            FigBuilder.capsule(
                tx - nx * (w0 + .2f),
                ty - ny * (w0 + .2f) - .15f,
                .14f,
                tx - nx * (w0 + out * .6f),
                ty - ny * (w0 + out * .6f) + .2f,
                .14f,
                3,
            ),
            dim(w.bag, .6f),
            Mat.NYLON,
        )
        val cx = bx + (tx - bx) * .3f - nx * (w1 + out * .85f)
        val cy = by + (ty - by) * .3f - ny * (w1 + out * .85f)
        val lean = atan2(ux, -uy) * 180f / PI.toFloat()
        o.add(
            FigBuilder.ellipse(cx, cy, .75f * b, 1.6f * hgt, lean, 10),
            dim(w.bag, .84f),
            Mat.NYLON,
        )
        // the pocket's zip pull
        o.add(
            FigBuilder.ellipse(cx - nx * .5f, cy - ny * .5f - 1.2f * hgt, .12f, .3f, lean, 6),
            0x2A2A2E,
            Mat.LEATHER,
        )
        // the zip's line round the top
        o.add(
            FigBuilder.capsule(
                tx - nx * (w0 + out * .4f),
                ty - ny * (w0 + out * .4f) + .5f,
                .13f,
                bx - nx * (w1 + out * .95f) + (tx - bx) * .55f,
                by - ny * (w1 + out * .95f) + (ty - by) * .55f,
                .13f,
                3,
            ),
            dim(w.bag, .5f),
            Mat.FLAT,
            .5f,
        )
    }

    private fun straps(
        o: Out,
        w: Wear,
        sx: Float,
        sy: Float,
        nx: Float,
        ny: Float,
        ux: Float,
        uy: Float,
        b: Float,
        hgt: Float,
        shoulderW: Float,
    ) {
        val x0 = sx + nx * shoulderW * .55f + ux * .2f
        val y0 = sy + ny * shoulderW * .55f + uy * .2f
        val x1 = x0 + nx * .4f * b - ux * 4.2f * hgt
        val y1 = y0 + ny * .4f * b - uy * 4.2f * hgt
        o.add(FigBuilder.capsule(x0, y0, .36f * b, x1, y1, .3f * b, 5), dim(w.bag, .72f), Mat.NYLON)
        // the strap's stitched edge
        o.add(
            FigBuilder.capsule(
                x0 + nx * .2f,
                y0 + ny * .2f,
                .04f,
                x1 + nx * .16f,
                y1 + ny * .16f,
                .04f,
                3,
            ),
            dim(w.bag, .45f),
            Mat.FLAT,
            .5f,
        )
    }

    private fun handbag(o: Out, w: Wear, j: WalkerJoints, hgt: Float, b: Float, trail: Float) {
        // hanging from the near hand's fingers, swinging behind the arm, its clasp catching the
        // light
        val ang = -trail * 9f
        val x0 = j.handNX
        val y0 = j.handNY
        val x1 = x0 + sin(rad(ang)) * 1.6f * hgt
        val y1 = y0 + cos(rad(ang)) * 1.6f * hgt
        o.add(FigBuilder.capsule(x0, y0, .22f, x1, y1 - .2f, .22f, 4), dim(w.bag, .7f), Mat.LEATHER)
        o.add(box(x1, y1 + 1.0f * hgt, 1.5f * hgt, 1.0f * hgt, ang), w.bag, Mat.LEATHER)
        // the stitching under its top seam, and along its base
        val cs = cos(rad(ang))
        val sn = sin(rad(ang))
        for (yy in floatArrayOf(.32f, 1.68f)) {
            val cx = x1 - sn * yy * hgt
            val cy = y1 + cs * yy * hgt
            o.add(
                FigBuilder.capsule(
                    cx - cs * 1.25f * hgt,
                    cy - sn * 1.25f * hgt,
                    .06f,
                    cx + cs * 1.25f * hgt,
                    cy + sn * 1.25f * hgt,
                    .06f,
                    3,
                ),
                mix(w.bag, 0xFFFFFF, .4f),
                Mat.FLAT,
                .4f,
            )
        }
        o.add(FigBuilder.ellipse(x1, y1 + .55f * hgt, .3f, .22f, ang, 6), 0xD9C9A0, Mat.FLAT, .85f)
    }

    /** A rounded box [hw] by [hh] about ([cx], [cy]), turned [ang] degrees. */
    private fun box(cx: Float, cy: Float, hw: Float, hh: Float, ang: Float): FloatArray {
        val c = cos(rad(ang))
        val s = sin(rad(ang))
        return FigBuilder.chaikin(
            floatArrayOf(
                cx + (-hw * c - -hh * s),
                cy + (-hw * s + -hh * c),
                cx + (hw * c - -hh * s),
                cy + (hw * s + -hh * c),
                cx + (hw * c - hh * s),
                cy + (hw * s + hh * c),
                cx + (-hw * c - hh * s),
                cy + (-hw * s + hh * c),
            ),
            2,
        )
    }
}
