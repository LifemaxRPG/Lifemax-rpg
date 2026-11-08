package com.lifemax.rpg.feature.pet.body

/**
 * Room over a surface for a cat to sit up in. On the cat tree's middle platform the top platform is
 * 24 rows over the cat's feet, and a cat sitting up there (44 rows tall grown, 34 a kitten) had its
 * head inside the top platform: the room draws the top platform over a cat on the middle one, so
 * the cat showed without its head. There a cat rests low instead: a pose that holds it upright (a
 * sit, a wash) is played as a loaf. Elsewhere nothing is overhead and every pose is played as
 * asked.
 */
internal object Headroom {
    /** The rows over the feet before something overhead on [surface] (no limit: MAX_VALUE). */
    fun of(surface: SurfaceId): Float = if (surface == SurfaceId.MID) MID_ROOM else Float.MAX_VALUE

    /**
     * [clip], or a loaf (the weak one when [weak]) when a cat of growth [scale] on [surface] has no
     * room for it upright.
     */
    fun fit(clip: Clip, surface: SurfaceId, scale: Float, weak: Boolean): Clip {
        if (clip.posture != Posture.SIT && clip.posture != Posture.ACTION) return clip
        if (Occluders.CAT_UPRIGHT_H * scale <= of(surface)) return clip
        return if (weak) Clips.LOAF_W else Clips.LOAF
    }

    /** The middle platform's row (100) less the top platform's lowest row (76). */
    private const val MID_ROOM = 24f
}
