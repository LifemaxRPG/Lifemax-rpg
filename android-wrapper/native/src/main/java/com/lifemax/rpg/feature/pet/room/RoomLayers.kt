package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.ArgbImage
import org.json.JSONObject

/**
 * How deep a standing piece is (`tools/pet_room_volume.js` writes it from measured profiles): the
 * piece as a small solid, and where that solid stands in the room.
 *
 * Depth is in floor rows ([Parallax]). The piece's front plane stands on [sortFront], the row the
 * cats sort against: a cat whose feet stand on a nearer row is in front of the plane. Its parallax
 * is that of [parallaxFront]: the same row, except for a piece against the wall, whose back moves
 * with the wall (the bookcase stands on the skirting, so it sorts behind every cat, but its front
 * is still [depth] rows out from the wall when the camera sways).
 *
 * @property model the solid.
 * @property depth how many rows deep the piece is.
 * @property wall whether its back stands against the wall.
 * @property surfaces how far behind the front plane a cat standing on each of the piece's surfaces
 *   is, by surface key.
 * @property covered by surface key, the piece wholly in front of a cat standing there (the cat
 *   tree's top platform over a cat on the middle one).
 */
internal class PieceVolume(
    val model: VolumeModel,
    val sortFront: Float,
    val depth: Float,
    val wall: Boolean,
    val surfaces: Map<String, Float>,
    val covered: Map<String, String> = emptyMap(),
) {
    /** The row whose parallax the front plane has. */
    val parallaxFront: Float
        get() = if (wall) Parallax.WALL_ROW + depth else sortFront
}

/**
 * One piece of furniture cut out of a painted backdrop (`tools/pet_room_cut.js` writes them with
 * `layers.json`): a sprite with its place in the room, how far back it stands, and what a cat can
 * do with it.
 *
 * @property name the piece's name, also its file (`sofa`, `cat_tree_slab_mid`).
 * @property room 0 living room, 1 bedroom, 2 kitchen.
 * @property x left edge in the room's own pixels (0 at the room's left edge).
 * @property y top edge in room pixels (0 at the top of the walls).
 * @property baseY the row of its lowest pixel as the page keeps it (`PET_Z_BLOCK`), or null for
 *   something lying flat on the floor (a rug), which no cat is ever behind.
 * @property z the depth that row is at ([DepthMath.zFromRow]); -1 for a flat piece: it is under
 *   every cat.
 * @property occludes whether a cat can walk behind it and be hidden.
 * @property surfaces the cat's surfaces that lie on it
 *   ([com.lifemax.rpg.feature.pet.body.SurfaceId] keys: `couch`, `mid`, `top`, `counter`).
 * @property volume the piece as a solid; null for a flat piece.
 * @property atlasX where its pixels sit in [RoomLayerArt.atlas].
 */
internal class LayerPiece(
    val name: String,
    val room: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val baseY: Int?,
    val z: Float,
    val occludes: Boolean,
    val surfaces: List<String>,
    val volume: PieceVolume?,
    val atlasX: Int,
    val atlasY: Int,
) {
    /** Left edge in house pixels (0 at the living room's left edge). */
    val houseX: Int
        get() = room * RoomLayout.BACKDROP_WIDTH + x

    /** True for something lying flat on the floor. */
    val flat: Boolean
        get() = baseY == null

    /**
     * True for a piece that stands on the floor (its bottom row is at its base, or just above): the
     * sofa, the lamp, the counter, the bottom platform of the tree; not the tree's trunk or the
     * platforms above it, and not the bookcase, whose base is the skirting above the floor. A piece
     * like this gets a contact shadow.
     */
    val onFloor: Boolean
        get() =
            baseY != null &&
                baseY >= LightTuning.SEAM_ROW &&
                y + height - 1 >= baseY - DepthTuning.FLOOR_REACH
}

/**
 * The shipped layers: every [LayerPiece] in draw order, the three [bases] (a room's backdrop with
 * the furniture removed and the wall and floor rebuilt behind it), and the pieces' pixels packed
 * into one [atlas]. Drawing the pieces in order over their base gives the original backdrop back
 * exactly ([RoomLayersTest]).
 */
internal class RoomLayerArt(
    val pieces: List<LayerPiece>,
    val bases: List<ArgbImage>,
    val atlas: ArgbImage,
) {
    /** The base of [room]. */
    fun base(room: Int): ArgbImage = bases[room]

    /** The piece called [name]; the manifest has it or the art is broken. */
    fun piece(name: String): LayerPiece =
        pieces.firstOrNull { it.name == name } ?: error("the layers have no piece $name")

    /** The pieces whose cats' surfaces include [surface], in draw order. */
    fun providing(surface: String): List<LayerPiece> = pieces.filter { surface in it.surfaces }

    companion object {
        /** The atlas is this wide; pieces are packed left to right in rows, in manifest order. */
        const val ATLAS_WIDTH = 256
        private const val GAP = 1

        /**
         * Builds the layers from the text of `layers.json` and a way to read one of the shipped
         * pictures ([read] gets a path relative to `room/`, such as `layers/living_base.png` or
         * `volume/sofa.png`).
         */
        fun parse(manifest: String, read: (String) -> ArgbImage): RoomLayerArt {
            val json = JSONObject(manifest)
            require(json.getInt("roomWidth") == RoomLayout.BACKDROP_WIDTH) { "layers: room width" }
            require(json.getInt("roomHeight") == RoomLayout.HEIGHT) { "layers: room height" }
            val rooms = json.getJSONObject("rooms")
            val names = arrayOf("living", "bedroom", "kitchen")
            val bases = names.map { read(LAYERS + rooms.getJSONObject(it).getString("base")) }
            val list = json.getJSONArray("pieces")
            val sprites = ArrayList<ArgbImage>(list.length())
            val placed = ArrayList<IntArray>(list.length())
            var cx = 0
            var cy = 0
            var rowH = 0
            for (i in 0 until list.length()) {
                val img = read(LAYERS + list.getJSONObject(i).getString("file"))
                if (cx + img.width > ATLAS_WIDTH) {
                    cx = 0
                    cy += rowH + GAP
                    rowH = 0
                }
                placed.add(intArrayOf(cx, cy))
                sprites.add(img)
                cx += img.width + GAP
                rowH = maxOf(rowH, img.height)
            }
            val atlas = IntArray(ATLAS_WIDTH * (cy + rowH))
            val pieces = ArrayList<LayerPiece>(list.length())
            for (i in 0 until list.length()) {
                val o = list.getJSONObject(i)
                val img = sprites[i]
                require(o.getInt("w") == img.width && o.getInt("h") == img.height) {
                    "layers: ${o.getString("name")} is not the size the manifest says"
                }
                for (y in 0 until img.height) System.arraycopy(
                    img.pixels,
                    y * img.width,
                    atlas,
                    (placed[i][1] + y) * ATLAS_WIDTH + placed[i][0],
                    img.width,
                )
                val baseY = if (o.isNull("baseY")) null else o.getInt("baseY")
                pieces.add(
                    LayerPiece(
                        name = o.getString("name"),
                        room = names.indexOf(o.getString("room")),
                        x = o.getInt("x"),
                        y = o.getInt("y"),
                        width = img.width,
                        height = img.height,
                        baseY = baseY,
                        z = if (baseY == null) FLAT_Z else DepthMath.zFromRow(baseY.toFloat()),
                        occludes = o.getBoolean("occludes"),
                        surfaces =
                            o.getJSONArray("surfaces").let { a ->
                                List(a.length()) { a.getString(it) }
                            },
                        volume = volume(o, img, baseY, read),
                        atlasX = placed[i][0],
                        atlasY = placed[i][1],
                    )
                )
            }
            val height = atlas.size / ATLAS_WIDTH
            return RoomLayerArt(pieces, bases, ArgbImage(ATLAS_WIDTH, height, atlas))
        }

        private fun volume(
            o: JSONObject,
            sprite: ArgbImage,
            baseY: Int?,
            read: (String) -> ArgbImage,
        ): PieceVolume? {
            if (o.isNull("volume")) return null
            val v = o.getJSONObject("volume")
            require(baseY != null) { "layers: a flat piece has no volume" }
            val surfaces = v.getJSONObject("surfaces")
            val covered = v.getJSONObject("covered")
            return PieceVolume(
                covered = covered.keys().asSequence().associateWith { covered.getString(it) },
                model = VolumeModel.decode(sprite, read(v.getString("file"))),
                sortFront = baseY - v.getDouble("front").toFloat(),
                depth = v.getDouble("depth").toFloat(),
                wall = v.getBoolean("wall"),
                surfaces =
                    surfaces.keys().asSequence().associateWith { surfaces.getDouble(it).toFloat() },
            )
        }

        private const val LAYERS = "layers/"

        /** The depth of a flat piece: under every cat. */
        const val FLAT_Z = -1f
    }
}
