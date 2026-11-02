package com.lifemax.rpg.feature.pet.body

/**
 * Everything a renderer needs to draw the cat for one instant. The body refills the same instance
 * in place on every [CatBody.step], so reading it costs nothing; copy it with [copyFrom] to keep a
 * snapshot.
 *
 * Units: room pixels for positions and offsets, radians for angles, plain multipliers for scales.
 */
class CatPose {
    /** Horizontal room position of the feet. */
    var x = 0f

    /** Screen row of the feet (smaller is higher up the screen). */
    var y = World.FLOOR_Y_ART

    /** Depth, 0 at the back wall to 100 at the glass. */
    var z = World.ART_Z

    /** Height of the feet above the floor line at this depth, in pixels. */
    var elevation = 0f

    /** Depth scale multiplier for the sprite (0.55 to 1.85). */
    var depthScale = 1f

    /** The surface the cat stands on, or is jumping to once it has landed. */
    var surface = SurfaceId.FLOOR

    /** Which way the cat faces: +1 right, -1 left. Changes at the middle of a turn. */
    var facing = 1

    /** True when the sprite must be mirrored (facing left). */
    var flip = false

    /** Index of the clip in [Clips.all]. */
    var clip = 0

    /** Frame of the clip to draw. */
    var frame = 0

    /** How far through the frame the clip is, 0..1, for renderers that blend frames. */
    var framePhase = 0f

    /** Whole-body horizontal scale: squash, stretch and the narrowing of a turn. */
    var scaleX = 1f

    /** Whole-body vertical scale. */
    var scaleY = 1f

    /** Body rotation about the feet, radians, clockwise positive. */
    var rotation = 0f

    /** Vertical body bob in pixels (positive is down), from footfalls and reactions. */
    var bob = 0f

    /** Ear angles in radians from upright: [0] the left ear, [1] the right ear. */
    val ear = FloatArray(2)

    /** How flat the ears lie, 0..1 (overshoot allowed). */
    var earFlat = 0f

    /** Tail bend per segment from root to tip, radians, positive upward. */
    val tail = FloatArray(Tuning.Tail.SEGMENTS)

    /** Whisker sweep, -1 (back) to 1 (forward). */
    var whisker = 0f

    /** Breathing scale of the torso, about 1. */
    var breath = 1f

    /** Where the cat is looking, along the way it faces (see [Attention]). */
    var lookX = 0f

    /** Where the cat is looking, up (-1) to down (+1). */
    var lookY = 0f

    /** Body lean toward the look target, radians. */
    var lean = 0f

    /** How shut the eyes are, 0..1, from reactions such as the slow blink. */
    var eyeClose = 0f

    /** Fast vertical vibration in pixels (purr). */
    var vibration = 0f

    /** Strength of a skid in progress, 0..1; renderers may add dust. */
    var skid = 0f

    /** Horizontal position of the ground shadow. */
    var shadowX = 0f

    /** Screen row of the ground the shadow lies on. */
    var shadowY = 0f

    /** Shadow size multiplier (shrinks as the cat rises). */
    var shadowScale = 1f

    /** Shadow opacity, 0..1. */
    var shadowAlpha = 0f

    /** Half width of the shadow in pixels before scaling. */
    var shadowHalfWidth = 0f

    /** Events raised by the latest step. */
    val events = CatEvents()

    /** The name of the clip, as the web room calls it. */
    val clipName: String
        get() = Clips.all[clip].name

    /** Copy every field from [o]. */
    fun copyFrom(o: CatPose) {
        x = o.x
        y = o.y
        z = o.z
        elevation = o.elevation
        depthScale = o.depthScale
        surface = o.surface
        facing = o.facing
        flip = o.flip
        clip = o.clip
        frame = o.frame
        framePhase = o.framePhase
        scaleX = o.scaleX
        scaleY = o.scaleY
        rotation = o.rotation
        bob = o.bob
        ear[0] = o.ear[0]
        ear[1] = o.ear[1]
        earFlat = o.earFlat
        for (i in tail.indices) tail[i] = o.tail[i]
        whisker = o.whisker
        breath = o.breath
        lookX = o.lookX
        lookY = o.lookY
        lean = o.lean
        eyeClose = o.eyeClose
        vibration = o.vibration
        skid = o.skid
        shadowX = o.shadowX
        shadowY = o.shadowY
        shadowScale = o.shadowScale
        shadowAlpha = o.shadowAlpha
        shadowHalfWidth = o.shadowHalfWidth
        events.copyFrom(o.events)
    }
}
