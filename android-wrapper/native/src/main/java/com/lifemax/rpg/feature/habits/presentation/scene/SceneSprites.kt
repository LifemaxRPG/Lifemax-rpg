package com.lifemax.rpg.feature.habits.presentation.scene

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import com.lifemax.rpg.feature.habits.domain.Activity
import com.lifemax.rpg.feature.habits.domain.BuildingSpec
import com.lifemax.rpg.feature.habits.domain.Looks
import com.lifemax.rpg.feature.habits.domain.PeopleActivities
import com.lifemax.rpg.feature.habits.domain.RoomKind

/** A cloud sprite's three layers, as images. */
internal class CloudImages(
    val sprite: CloudSprite,
    val shade: ImageBitmap,
    val mid: ImageBitmap,
    val lit: ImageBitmap,
)

/** A far-road vehicle's two pictures as images, by day and by night. */
internal class VehicleImages(
    val sprite: VehicleSprite,
    val day: ImageBitmap,
    val night: ImageBitmap,
)

/** The meteor's streak as images: its hot core and its wider halo. */
internal class StreakImages(val sprite: StreakSprite, val core: ImageBitmap, val halo: ImageBitmap)

/** A thing of the air (the plane, its contrail, the blimp): its tintable layers as images. */
internal class AirImages(val sprite: AirSprite, val layers: Array<ImageBitmap>)

/** A tree variant's images, one per [TreeLook]. */
internal class TreeImages(val sprite: TreeSprite, val looks: Array<ImageBitmap>)

/** A room's two overlays as images: behind the person, and in front of them. */
internal class RoomImages(val back: ImageBitmap, val front: ImageBitmap)

/**
 * One baked figure: the picture (only the part with anything in it) and where its top-left corner
 * is from the figure's feet, in the picture's own pixels.
 */
internal class PersonImage(val image: ImageBitmap, val dx: Float, val dy: Float)

/**
 * The scene's pre-rendered art, made once for the whole process the first time it is needed (or, in
 * the app, ahead of that on a background thread: [warm]) and shared by every scene after it. The
 * generators are deterministic and know nothing of Compose; this only turns their pixels into
 * images. The pool is about 5 MB: [bytes].
 */
internal object SceneSprites {
    val clouds: Array<CloudImages> by lazy {
        Array(CloudSprites.SPECS.size) {
            val s = CloudSprites.generate(it)
            CloudImages(s, image(s.shade, s.w, s.h), image(s.mid, s.w, s.h), image(s.lit, s.w, s.h))
        }
    }

    /**
     * The far road's vehicles, the lights they throw and the meteor's streak ([RoadSkySprites]).
     */
    val vehicles: Array<VehicleImages> by lazy {
        Array(RoadSkySprites.VEHICLES.size) {
            val s = RoadSkySprites.generate(it)
            VehicleImages(s, image(s.day, s.w, s.h), image(s.night, s.w, s.h))
        }
    }

    val glow: ImageBitmap by lazy {
        image(RoadSkySprites.glow(), RoadSkySprites.GLOW, RoadSkySprites.GLOW)
    }

    val beam: ImageBitmap by lazy {
        image(RoadSkySprites.beam(), RoadSkySprites.BEAM_W, RoadSkySprites.BEAM_H)
    }

    val streak: StreakImages by lazy {
        val s = RoadSkySprites.streak()
        StreakImages(s, image(s.core, s.w, s.h), image(s.halo, s.w, s.h))
    }

    val plane: AirImages by lazy { air(AirSprites.plane()) }
    val contrail: AirImages by lazy { air(AirSprites.contrail()) }
    val blimp: AirImages by lazy { air(AirSprites.blimp()) }

    private fun air(s: AirSprite) =
        AirImages(s, Array(s.layers.size) { image(s.layers[it], s.w, s.h) })

    val trees: Array<TreeImages> by lazy {
        Array(com.lifemax.rpg.feature.habits.domain.Trees.VARIANTS) {
            val s = TreeSprites.generate(it)
            TreeImages(s, Array(s.pixels.size) { look -> image(s.pixels[look], s.w, s.h) })
        }
    }

    /**
     * The frames of the people, each made the first time it is drawn: [look] doing [a], frame
     * [frame].
     */
    private val frames =
        arrayOfNulls<PersonImage>(
            Looks.COUNT * Activity.entries.size * PeopleActivities.ALL.maxOf { it.totalFrames }
        )
    private val mostFrames = PeopleActivities.ALL.maxOf { it.totalFrames }
    private var personBytes = 0

    @Synchronized
    fun person(look: Int, a: Activity, frame: Int): PersonImage {
        val i = (look * Activity.entries.size + a.ordinal) * mostFrames + frame
        frames[i]?.let {
            return it
        }
        // only the part of the box a frame uses is kept: the pose reaches less than the box
        val sp = PeopleSprites.bake(look, a, frame)
        personBytes += sp.w * sp.h * 4
        return PersonImage(image(sp.pixels, sp.w, sp.h), -sp.ox, -sp.oy).also { frames[i] = it }
    }

    private val rooms = HashMap<Int, RoomImages>()
    private var roomBytes = 0

    /**
     * The room [kind] as seen through a window [h] tall (heights are told apart to half a unit).
     */
    @Synchronized
    fun room(kind: RoomKind, h: Float): RoomImages {
        val key = kind.ordinal * 4096 + (h * 2f).toInt()
        rooms[key]?.let {
            return it
        }
        val r = RoomSprites.render(kind, (h * 2f).toInt() / 2f)
        roomBytes += (r.back.w * r.back.h + r.front.w * r.front.h) * 4
        return RoomImages(
                image(r.back.pixels, r.back.w, r.back.h),
                image(r.front.pixels, r.front.w, r.front.h),
            )
            .also { rooms[key] = it }
    }

    /**
     * What the people and their rooms made so far take, in bytes (they are made as they are first
     * drawn).
     */
    @Synchronized fun peopleBytes(): Int = personBytes + roomBytes

    /** The film grain, a tile repeated over the picture. */
    val grain: Brush by lazy {
        ShaderBrush(
            ImageShader(
                image(Grain.pixels(), Grain.SIZE, Grain.SIZE),
                TileMode.Repeated,
                TileMode.Repeated,
            )
        )
    }

    /** Makes the pools now (call off the main thread): the rooms of every window the street has. */
    fun warm() {
        clouds
        trees
        vehicles
        glow
        beam
        streak
        grain
        plane
        contrail
        blimp
        for (h in BuildingSpec.HEIGHTS) {
            val rows = if (h >= BuildingSpec.TWO_ROWS) 2 else 1
            val wh = (h - BuildingSpec.BAND_TOP - BuildingSpec.PLAQUE_BAND - 6f * (rows - 1)) / rows
            for (k in RoomKind.entries) room(k, wh)
        }
    }

    /** What the pools hold, in bytes. */
    fun bytes(): Int =
        CloudSprites.bytes() +
            TreeSprites.bytes() +
            RoadSkySprites.bytes() +
            AirSprites.bytes() +
            Grain.SIZE * Grain.SIZE * 4 +
            peopleBytes()

    private fun image(pixels: IntArray, w: Int, h: Int): ImageBitmap =
        Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
}
