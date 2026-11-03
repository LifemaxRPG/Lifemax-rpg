package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.ArgbImage
import com.lifemax.rpg.feature.pet.art.FrameRect
import com.lifemax.rpg.feature.pet.art.RoomAtlas

/** Every picture the room draws from; the painters name them, the screen holds the bitmaps. */
internal enum class RoomImage {
    /** The item atlas: bed, bowls, sun, moon, clouds. */
    ATLAS,
    LIVING,
    BEDROOM,
    KITCHEN,
    POUF,
    CUSHION,
    BOX_BACK,
    BOX_FRONT,

    /** The furniture cut out of the backdrops, packed into one picture (2.5D room). */
    LAYERS,

    /** The living room's backdrop with the furniture removed and the wall and floor rebuilt. */
    LIVING_BASE,
    BEDROOM_BASE,
    KITCHEN_BASE,
}

/**
 * The room's shipped art, decoded: the item [atlas], the three painted backdrops (each 192 x 144)
 * and the free-standing props. Written by `tools/gen_pet_native_assets.js` from the page's own
 * data.
 */
internal class RoomArt(
    val atlas: RoomAtlas,
    private val images: Map<RoomImage, ArgbImage>,
    /** The furniture layers of the 2.5D room; null in a test that does not need them. */
    val layers: RoomLayerArt? = null,
) {
    init {
        for (backdrop in listOf(RoomImage.LIVING, RoomImage.BEDROOM, RoomImage.KITCHEN)) {
            val img = image(backdrop)
            require(img.width == RoomLayout.BACKDROP_WIDTH && img.height == RoomLayout.HEIGHT) {
                "a backdrop is ${RoomLayout.BACKDROP_WIDTH}x${RoomLayout.HEIGHT}"
            }
        }
    }

    /** The pixels of [image]. */
    fun image(image: RoomImage): ArgbImage =
        when (image) {
            RoomImage.ATLAS -> atlas.image
            RoomImage.LAYERS -> layers?.atlas ?: NOTHING
            RoomImage.LIVING_BASE -> layers?.base(0) ?: NOTHING
            RoomImage.BEDROOM_BASE -> layers?.base(1) ?: NOTHING
            RoomImage.KITCHEN_BASE -> layers?.base(2) ?: NOTHING
            else -> images[image] ?: error("room art is missing $image")
        }

    /** Where the atlas sprite [name] is; the room's own names (`bed_back`, `sun`, ...). */
    fun item(name: String): FrameRect = atlas.rect(name) ?: error("the room atlas has no $name")

    private companion object {
        /** What a picture stands for when the art has no layers: one clear pixel. */
        val NOTHING = ArgbImage(1, 1, IntArray(1))
    }
}

/**
 * Where things stand in the house, in the web room's numbers (15-runtime.js and 24-pet-depth.js).
 * World x runs from [HOUSE_X0] at the house's left edge; a house pixel is `world - HOUSE_X0`.
 */
internal object RoomLayout {
    const val HOUSE_X0 = -192
    const val HOUSE_WIDTH = 576
    const val HEIGHT = 144
    const val BACKDROP_WIDTH = 192

    /** Sky offsets of the three windows relative to the bedroom's: living, bedroom, kitchen. */
    val windows = intArrayOf(-152, 0, 280)

    /** The glass of a window, relative to its offset. */
    const val GLASS_X = 22
    const val GLASS_Y = 14
    const val GLASS_W = 64
    const val GLASS_H = 56

    /** Food and water bowls: world x of the centre and the row of the rim. */
    const val BOWL_FOOD_X = 104
    const val BOWL_WATER_X = 124
    const val BOWL_TOP = 126
    const val BOWL_HALF_WIDTH = 9

    /** The cat bed's two halves: world x and row of their top left corners. */
    const val BED_X = 10
    const val BED_BACK_Y = 118
    const val BED_FRONT_Y = 126

    /** Depths the web sorts the room's own furniture at. */
    const val Z_BED_BACK = 30f
    const val Z_BOWLS = 34f
    const val Z_BED_FRONT = 40f

    /** The living room lamp: world x and row of its glow's centre, and the glow's radii. */
    const val LAMP_X = -172
    const val LAMP_Y = 34
    const val LAMP_INNER = 2
    const val LAMP_REACH = 46
}
