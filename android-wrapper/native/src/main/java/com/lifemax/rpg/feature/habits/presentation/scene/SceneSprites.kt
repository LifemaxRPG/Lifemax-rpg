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

/** A tree variant's images, one per [TreeLook]. */
internal class TreeImages(val sprite: TreeSprite, val looks: Array<ImageBitmap>)

/** A room's two overlays as images: behind the person, and in front of them. */
internal class RoomImages(val back: ImageBitmap, val front: ImageBitmap)

/**
 * The scene's pre-rendered art, made once for the whole process the first time it is needed (or, in
 * the app, ahead of that on a background thread: [warm]) and shared by every scene after it. The
 * generators are deterministic and know nothing of Compose; this only turns their pixels into
 * images. The pool is about 4 MB: [bytes].
 */
internal object SceneSprites {
    val clouds: Array<CloudImages> by lazy {
        Array(CloudSprites.SPECS.size) {
            val s = CloudSprites.generate(it)
            CloudImages(s, image(s.shade, s.w, s.h), image(s.mid, s.w, s.h), image(s.lit, s.w, s.h))
        }
    }

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
        arrayOfNulls<ImageBitmap>(
            Looks.COUNT * Activity.entries.size * PeopleActivities.ALL.maxOf { it.frames }
        )
    private val mostFrames = PeopleActivities.ALL.maxOf { it.frames }
    private var personBytes = 0

    @Synchronized
    fun person(look: Int, a: Activity, frame: Int): ImageBitmap {
        val i = (look * Activity.entries.size + a.ordinal) * mostFrames + frame
        frames[i]?.let {
            return it
        }
        val sp = PeopleSprites.render(look, a, frame)
        personBytes += sp.w * sp.h * 4
        return image(sp.pixels, sp.w, sp.h).also { frames[i] = it }
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
        grain
        for (h in BuildingSpec.HEIGHTS) {
            val rows = if (h >= BuildingSpec.TWO_ROWS) 2 else 1
            val wh = (h - BuildingSpec.BAND_TOP - BuildingSpec.PLAQUE_BAND - 6f * (rows - 1)) / rows
            for (k in RoomKind.entries) room(k, wh)
        }
    }

    /** What the pools hold, in bytes. */
    fun bytes(): Int =
        CloudSprites.bytes() + TreeSprites.bytes() + Grain.SIZE * Grain.SIZE * 4 + peopleBytes()

    private fun image(pixels: IntArray, w: Int, h: Int): ImageBitmap =
        Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
}
