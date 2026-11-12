package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.ArgbImage
import com.lifemax.rpg.feature.pet.art.FrameRect
import com.lifemax.rpg.feature.pet.art.RoomAtlas

/** Every picture the room draws from; the painters name them, the screen holds the bitmaps. */
internal enum class RoomImage(val room: String? = null, val base: Boolean = false) {
    /** The item atlas: bed, bowls, sun, moon, clouds. */
    ATLAS,
    LIVING("living"),
    BEDROOM("bedroom"),
    KITCHEN("kitchen"),
    POUF,
    CUSHION,
    BOX_BACK,
    BOX_FRONT,

    /** The furniture cut out of the backdrops, packed into one picture (2.5D room). */
    LAYERS,

    /** The living room's backdrop with the furniture removed and the wall and floor rebuilt. */
    LIVING_BASE("living", true),
    BEDROOM_BASE("bedroom", true),
    KITCHEN_BASE("kitchen", true),

    /** The rooms that open as the cats grow close (`house.json`): their backdrops and bases. */
    SUN("sun"),
    STUDY("study"),
    BATH("bath"),
    SUN_BASE("sun", true),
    STUDY_BASE("study", true),
    BATH_BASE("bath", true);

    companion object {
        /** The backdrop picture of room [id]. */
        fun backdrop(id: String): RoomImage =
            entries.firstOrNull { it.room == id && !it.base } ?: error("no backdrop for room $id")

        /** The base picture (furniture removed) of room [id]. */
        fun baseOf(id: String): RoomImage =
            entries.firstOrNull { it.room == id && it.base } ?: error("no base for room $id")
    }
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
        for (room in HouseMap.current.rooms) {
            val img = image(RoomImage.backdrop(room.id))
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
            else ->
                if (image.base) layers?.baseOf(image.room!!) ?: NOTHING
                // a room that is not open has no backdrop here (the screens still make a bitmap of
                // every picture, so it is one clear pixel)
                else
                    images[image]
                        ?: if (image.room != null) NOTHING else error("room art is missing $image")
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
 * World x runs from [HOUSE_X0] at the house's left edge; a house pixel is `world - HOUSE_X0`. The
 * rooms and their windows come from the house map ([HouseMap]).
 */
internal object RoomLayout {
    const val HEIGHT = 144
    const val BACKDROP_WIDTH = 192

    /** World x of the open house's left edge, and its width ([HouseMap]: the open rooms). */
    val HOUSE_X0: Int
        get() = HouseMap.current.x0

    val HOUSE_WIDTH: Int
        get() = HouseMap.current.width

    /** Sky offsets (world x) of the open rooms' windows, left to right. */
    val windows: IntArray
        get() = HouseMap.current.windows

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
