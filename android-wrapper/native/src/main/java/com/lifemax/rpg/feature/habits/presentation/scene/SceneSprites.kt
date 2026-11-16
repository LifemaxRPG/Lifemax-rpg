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
import com.lifemax.rpg.feature.habits.domain.DogRig
import com.lifemax.rpg.feature.habits.domain.HabitMaterial
import com.lifemax.rpg.feature.habits.domain.Looks
import com.lifemax.rpg.feature.habits.domain.PeopleActivities
import com.lifemax.rpg.feature.habits.domain.RoomKind
import com.lifemax.rpg.feature.habits.domain.SeasonState
import com.lifemax.rpg.feature.habits.domain.StreetGait
import com.lifemax.rpg.feature.habits.domain.StreetLife
import com.lifemax.rpg.feature.habits.domain.StreetLooks
import com.lifemax.rpg.feature.habits.domain.StreetRig
import com.lifemax.rpg.feature.habits.domain.Trees
import com.lifemax.rpg.feature.habits.domain.WalkerJoints

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

/** The sun's layers as images, with the sprite that says their sizes. */
internal class SunImages(
    val sprite: SunSprite,
    val core: ImageBitmap,
    val bloom: ImageBitmap,
    val sky: ImageBitmap,
    val rays: ImageBitmap,
    val disc: ImageBitmap,
    val discLight: ImageBitmap,
)

/**
 * The moon's surface (to make the picture of a phase from, [SceneSprites.moonPicture]) and its halo
 * as an image.
 */
internal class MoonImages(val sprite: MoonSprite, val halo: ImageBitmap)

/** The moon at one [step] of its cycle, as an image. */
internal class MoonPictureImage(val step: Int, val layer: SkyLayer, val image: ImageBitmap)

/** A tree variant's images, one per [TreeLook]. */
internal class TreeImages(val sprite: TreeSprite, val looks: Array<ImageBitmap>)

/** A puddle's two layers as images: the pool itself and the bright rim of its far edge. */
internal class PuddleImages(val fill: ImageBitmap, val rim: ImageBitmap)

/**
 * The weather's pieces as images ([WeatherSprites]): the rain tile as a repeating brush (scrolled
 * by translating the canvas under it), the splash frames, the puddles, the mist band as a brush
 * that repeats sideways and holds its last row downward.
 */
internal class WeatherImages(
    val rain: Brush,
    val rainImage: ImageBitmap,
    val splashes: Array<ImageBitmap>,
    val puddles: Array<PuddleImages>,
    val fog: Brush,
)

/** The street's soft light as images ([LightSprites]). */
internal class LightImages(
    val air: ImageBitmap,
    val pool: ImageBitmap,
    val spill: ImageBitmap,
    val ramp: ImageBitmap,
    val point: ImageBitmap,
)

/** The lens flare's parts as images: the three kinds of ghost and the streak. */
internal class FlareImages(
    val sprite: FlareSprite,
    val disc: ImageBitmap,
    val ring: ImageBitmap,
    val poly: ImageBitmap,
    val streak: ImageBitmap,
) {
    fun ghost(kind: Int): ImageBitmap =
        when (kind) {
            0 -> disc
            1 -> ring
            else -> poly
        }
}

/** A baked far-city strip ([SkylineStrip]) as an image, with its measures. */
internal class SkylineImage(val strip: SkylineStrip, val image: ImageBitmap) {
    val h: Int
        get() = strip.h

    val ppd: Float
        get() = strip.ppd

    val topDp: Float
        get() = strip.topDp

    val footDp: Float
        get() = strip.footDp
}

/** A far-city row's images: its body, and its windows (none for the furthest row). */
internal class SkylineImages(val body: SkylineImage, val windows: SkylineImage?)

/** The near layer's things as images ([ForegroundSprites]). */
internal class ForegroundImages(
    val rail: ImageBitmap,
    val bollards: ImageBitmap,
    val post: ImageBitmap,
    val branch: ImageBitmap,
)

/**
 * A room's layers as images ([Room]): the unlit room, its lamp's light and its screen's (soft, at
 * [RoomSprites.LIGHT_PPU]) and what is in front of the person, each cut to what it uses and drawn
 * at its own offset from the window's top left, in its own pixels.
 */
internal class RoomImages(
    val base: ImageBitmap,
    val lamp: ImageBitmap,
    val lampDx: Float,
    val lampDy: Float,
    val screen: ImageBitmap?,
    val screenDx: Float,
    val screenDy: Float,
    val front: ImageBitmap,
    val frontDx: Float,
    val frontDy: Float,
)

/**
 * One baked figure: the picture (only the part with anything in it) and where its top-left corner
 * is from the figure's feet, in the picture's own pixels.
 */
internal class PersonImage(val image: ImageBitmap, val dx: Float, val dy: Float)

/**
 * One baked street walker frame: the picture, its top-left from the feet in its pixels, its joints.
 */
internal class WalkerImage(
    val image: ImageBitmap,
    val dx: Float,
    val dy: Float,
    val joints: WalkerJoints,
)

/**
 * The near car's pictures: by day, by night, and the lights to add at night; with the sprite's
 * measures.
 */
internal class CarImages(
    val sprite: CarSprite,
    val day: ImageBitmap,
    val night: ImageBitmap,
    val lights: ImageBitmap,
)

/**
 * The scene's pre-rendered art, made once for the whole process the first time it is needed (or, in
 * the app, ahead of that on a background thread: [warm]) and shared by every scene after it. The
 * generators are deterministic and know nothing of Compose; this only turns their pixels into
 * images. The pool is about 6 MB: [bytes].
 */
/** The small life's images: the steam's [puffs] and the [pigeon]'s frames. */
internal class DuskImages(val puffs: Array<ImageBitmap>, val pigeon: Array<ImageBitmap>)

/**
 * A material's wall as a repeating brush ([FacadeSprites]): drawn under a transform that scales its
 * pixels to units, so a floor's front tiles it from its own top-left corner.
 */
internal class FacadeImages(val tile: WallTile, val brush: Brush)

/** The awning's cloth as two repeating brushes: its shade and its light. */
internal class ClothImages(val tile: ClothTile, val shade: Brush, val light: Brush)

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

    /** The far city's three rows, furthest first ([SkylineSprites]). */
    val skyline: Array<SkylineImages> by lazy {
        Array(SkylineSprites.ROWS.size) {
            val r = SkylineSprites.generate(it)
            SkylineImages(
                SkylineImage(r.body, image(r.body.pixels, r.body.w, r.body.h)),
                r.windows?.let { w -> SkylineImage(w, image(w.pixels, w.w, w.h)) },
            )
        }
    }

    /** The near layer's things ([ForegroundSprites]). */
    val foreground: ForegroundImages by lazy {
        fun img(s: ForegroundSprite) = image(s.pixels, s.w, s.h)
        ForegroundImages(
            img(ForegroundSprites.rail()),
            img(ForegroundSprites.bollards()),
            img(ForegroundSprites.post()),
            img(ForegroundSprites.branch()),
        )
    }

    /** The street's soft light ([LightSprites]): a lamp's air, its pool, a window's spill. */
    val light: LightImages by lazy {
        LightImages(
            image(LightSprites.lampAir(), LightSprites.AIR_W, LightSprites.AIR_H),
            image(LightSprites.pool(), LightSprites.POOL_W, LightSprites.POOL_H),
            image(LightSprites.spill(), LightSprites.SPILL_W, LightSprites.SPILL_H),
            image(LightSprites.ramp(), LightSprites.RAMP, 1),
            image(LightSprites.point(), LightSprites.POINT, LightSprites.POINT),
        )
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

    val sun: SunImages by lazy {
        val s = SunMoonSprites.sun()
        SunImages(
            s,
            image(s.core),
            image(s.bloom),
            image(s.sky),
            image(s.rays),
            image(s.disc),
            image(s.discLight),
        )
    }

    val moon: MoonImages by lazy {
        val m = SunMoonSprites.moon()
        MoonImages(m, image(m.halo))
    }

    private var picture: MoonPictureImage? = null

    /**
     * The moon at [step] of [com.lifemax.rpg.feature.habits.domain.MoonPhase.STEPS]: only the
     * current one is kept, made from the surface the first time a night of that phase is drawn.
     */
    @Synchronized
    fun moonPicture(step: Int): MoonPictureImage {
        picture?.let { if (it.step == step) return it }
        val l = SunMoonSprites.moonPicture(moon.sprite, step)
        return MoonPictureImage(step, l, image(l)).also { picture = it }
    }

    val trees: Array<TreeImages> by lazy {
        Array(com.lifemax.rpg.feature.habits.domain.Trees.VARIANTS) {
            val s = TreeSprites.generate(it)
            TreeImages(s, Array(s.pixels.size) { look -> image(s.pixels[look], s.w, s.h) })
        }
    }

    // ---- the seasons: the other looks of the trees, made as the year first needs them

    private val seasonTrees = HashMap<Int, TreeImages>()
    private val snowTrees = arrayOfNulls<ImageBitmap>(Trees.VARIANTS)
    private var seasonTreeBytes = 0

    /**
     * The tree [variant] in [season]: the summer pool for summer and for the evergreens, otherwise
     * a look baked the first time that season is drawn and kept (a season's looks are about a
     * megabyte; two or three are ever in use).
     */
    @Synchronized
    fun tree(variant: Int, season: TreeSeason): TreeImages {
        if (season == TreeSeason.SUMMER || Trees.evergreen(variant)) return trees[variant]
        val key = season.ordinal * 64 + variant
        seasonTrees[key]?.let {
            return it
        }
        val s = TreeSprites.generate(variant, season)
        seasonTreeBytes += s.w * s.h * 4 * s.pixels.size
        return TreeImages(s, Array(s.pixels.size) { look -> image(s.pixels[look], s.w, s.h) })
            .also { seasonTrees[key] = it }
    }

    /** The snow that lies on the tree [variant], made the first winter day it is drawn. */
    @Synchronized
    fun treeSnow(variant: Int): ImageBitmap {
        snowTrees[variant]?.let {
            return it
        }
        val sp = trees[variant].sprite
        seasonTreeBytes += sp.w * sp.h * 4
        return image(TreeSprites.snow(variant), sp.w, sp.h).also { snowTrees[variant] = it }
    }

    /** What the seasons' tree looks and snow made so far take, in bytes. */
    @Synchronized fun seasonBytes(): Int = seasonTreeBytes

    /**
     * The falling leaves (shape times palette, [SeasonSprites.leaves]), petals and the snowflake.
     */
    val leaves: Array<ImageBitmap> by lazy {
        SeasonSprites.leaves()
            .map { image(it, SeasonSprites.LEAF, SeasonSprites.LEAF) }
            .toTypedArray()
    }
    val petals: Array<ImageBitmap> by lazy {
        SeasonSprites.petals()
            .map { image(it, SeasonSprites.PETAL, SeasonSprites.PETAL) }
            .toTypedArray()
    }
    val flake: ImageBitmap by lazy {
        image(SeasonSprites.flake(), SeasonSprites.FLAKE, SeasonSprites.FLAKE)
    }

    val flare: FlareImages by lazy {
        val f = FlareSprites.flare()
        FlareImages(
            f,
            image(f.disc),
            image(f.ring),
            image(f.poly),
            image(f.streak, f.streakW, f.streakH),
        )
    }

    /**
     * Makes now (off the main thread) what the trees of [season] are drawn with, so the first frame
     * of a new season does not bake them.
     */
    fun warm(season: SeasonState) {
        val plan = FloatArray(TreeSeason.entries.size)
        for (v in 0 until Trees.VARIANTS) {
            TreeDress.plan(season, v, plan)
            for (s in TreeSeason.entries) if (plan[s.ordinal] > .01f) tree(v, s)
            if (season.snow > .01f) treeSnow(v)
        }
        if (season.autumn > 0f) leaves
        if (season.blossom > 0f) petals
        if (season.snowfall > 0f) flake
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

    // ---- the street's people and the near car, each made the first time it is drawn

    private val walkers = arrayOfNulls<WalkerImage>(StreetLooks.COUNT * StreetRig.TOTAL_FRAMES)
    private val dogs = arrayOfNulls<PersonImage>(DogRig.TOTAL_FRAMES)
    private val cycles = arrayOfNulls<PersonImage>(StreetGait.CRANK_FRAMES)
    private val cars = arrayOfNulls<CarImages>(StreetLife.CAR_BODIES * StreetLife.CAR_PAINTS)
    private var streetBytes = 0

    /** Street walker [look]'s frame [frame] ([StreetRig.TOTAL_FRAMES]). */
    @Synchronized
    fun walker(look: Int, frame: Int): WalkerImage {
        val i = look * StreetRig.TOTAL_FRAMES + frame
        walkers[i]?.let {
            return it
        }
        val w = StreetSprites.walker(look, frame)
        val sp = w.sprite
        streetBytes += sp.w * sp.h * 4
        return WalkerImage(image(sp.pixels, sp.w, sp.h), -sp.ox, -sp.oy, w.joints).also {
            walkers[i] = it
        }
    }

    /** The dog's frame [frame] ([DogRig.TOTAL_FRAMES]). */
    @Synchronized
    fun dog(frame: Int): PersonImage {
        dogs[frame]?.let {
            return it
        }
        val sp = StreetSprites.dog(frame)
        streetBytes += sp.w * sp.h * 4
        return PersonImage(image(sp.pixels, sp.w, sp.h), -sp.ox, -sp.oy).also { dogs[frame] = it }
    }

    /** The bicycle and its rider at crank frame [frame]. */
    @Synchronized
    fun cycle(frame: Int): PersonImage {
        cycles[frame]?.let {
            return it
        }
        val sp = StreetSprites.cycle(frame)
        streetBytes += sp.w * sp.h * 4
        return PersonImage(image(sp.pixels, sp.w, sp.h), -sp.ox, -sp.oy).also { cycles[frame] = it }
    }

    /** The bicycle's wheel, centred, at the walkers' resolution and size. */
    val cycleWheel: Sprite by lazy {
        StreetSprites.wheel(
            StreetGait.WHEEL_R,
            StreetSprites.PPU * StreetLife.SIZE,
            10,
            .55f,
            .6f,
            false,
        )
    }
    val cycleWheelImage: ImageBitmap by lazy {
        image(cycleWheel.pixels, cycleWheel.w, cycleWheel.h)
    }

    /** The near car's wheel, centred, at the car's resolution. */
    val carWheel: Sprite by lazy {
        StreetSprites.wheel(StreetLife.CAR_WHEEL, StreetSprites.CAR_PPU, 0, .9f, 2.2f, true)
    }
    val carWheelImage: ImageBitmap by lazy { image(carWheel.pixels, carWheel.w, carWheel.h) }

    /** The near car [body] in [paint]. */
    @Synchronized
    fun car(body: Int, paint: Int): CarImages {
        val i = body * StreetLife.CAR_PAINTS + paint
        cars[i]?.let {
            return it
        }
        val sp = StreetSprites.car(body, paint)
        streetBytes += sp.w * sp.h * 4 * 3
        return CarImages(
                sp,
                image(sp.day, sp.w, sp.h),
                image(sp.night, sp.w, sp.h),
                image(sp.lights, sp.w, sp.h),
            )
            .also { cars[i] = it }
    }

    /** What the street's people and cars made so far take, in bytes. */
    @Synchronized fun streetBytes(): Int = streetBytes

    private val rooms = HashMap<Int, RoomImages>()
    private var roomBytes = 0

    /**
     * The room [kind] in its [variant] as seen through a window [h] tall (heights are told apart to
     * half a unit).
     */
    @Synchronized
    fun room(kind: RoomKind, variant: Int, h: Float): RoomImages {
        val v = ((variant % RoomSprites.VARIANTS) + RoomSprites.VARIANTS) % RoomSprites.VARIANTS
        val key = (kind.ordinal * RoomSprites.VARIANTS + v) * 4096 + (h * 2f).toInt()
        rooms[key]?.let {
            return it
        }
        val r = RoomSprites.render(kind, v, (h * 2f).toInt() / 2f)
        roomBytes +=
            (r.base.w * r.base.h +
                r.front.w * r.front.h +
                r.lamp.w * r.lamp.h +
                (r.screen?.let { it.w * it.h } ?: 0)) * 4
        return RoomImages(
                image(r.base.pixels, r.base.w, r.base.h),
                image(r.lamp.pixels, r.lamp.w, r.lamp.h),
                -r.lamp.ox,
                -r.lamp.oy,
                r.screen?.let { image(it.pixels, it.w, it.h) },
                -(r.screen?.ox ?: 0f),
                -(r.screen?.oy ?: 0f),
                image(r.front.pixels, r.front.w, r.front.h),
                -r.front.ox,
                -r.front.oy,
            )
            .also { rooms[key] = it }
    }

    /** What the rooms made so far take, in bytes. */
    @Synchronized fun roomBytes(): Int = roomBytes

    /**
     * What the people and their rooms made so far take, in bytes (they are made as they are first
     * drawn).
     */
    @Synchronized fun peopleBytes(): Int = personBytes + roomBytes

    val weather: WeatherImages by lazy {
        val rain = WeatherSprites.streaks()
        val rainImage = image(rain.pixels, rain.w, rain.h)
        val fog = WeatherSprites.fog()
        WeatherImages(
            ShaderBrush(ImageShader(rainImage, TileMode.Repeated, TileMode.Repeated)),
            rainImage,
            Array(WeatherSprites.SPLASH_FRAMES) {
                val f = WeatherSprites.splash(it)
                image(f.pixels, f.w, f.h)
            },
            Array(WeatherSprites.PUDDLES) {
                val (fill, rim) = WeatherSprites.puddle(it)
                PuddleImages(image(fill.pixels, fill.w, fill.h), image(rim.pixels, rim.w, rim.h))
            },
            ShaderBrush(
                ImageShader(image(fog.pixels, fog.w, fog.h), TileMode.Repeated, TileMode.Clamp)
            ),
        )
    }

    /** The street's small life: the steam's puffs and the pigeon's frames ([DuskSprites]). */
    val dusk: DuskImages by lazy {
        DuskImages(
            Array(DuskSprites.PUFFS) {
                val p = DuskSprites.puff(it)
                image(p.pixels, p.w, p.h)
            },
            Array(DuskSprites.PIGEON_FRAMES) {
                val p = DuskSprites.pigeon(it)
                image(p.pixels, p.w, p.h)
            },
        )
    }

    private val facades = arrayOfNulls<FacadeImages>(HabitMaterial.entries.size)
    private var facadeBytes = 0

    /** The wall of a floor of [m], made the first time a floor of it is drawn. */
    @Synchronized
    fun facade(m: HabitMaterial): FacadeImages {
        facades[m.ordinal]?.let {
            return it
        }
        val t = FacadeSprites.tile(m)
        facadeBytes += t.w * t.h * 4
        return FacadeImages(
                t,
                ShaderBrush(
                    ImageShader(image(t.pixels, t.w, t.h), TileMode.Repeated, TileMode.Repeated)
                ),
            )
            .also { facades[m.ordinal] = it }
    }

    /** The awnings' cloth. */
    val cloth: ClothImages by lazy {
        val t = FacadeSprites.cloth()
        facadeBytes += t.w * t.h * 4 * 2
        fun brush(px: IntArray) =
            ShaderBrush(ImageShader(image(px, t.w, t.h), TileMode.Repeated, TileMode.Clamp))
        ClothImages(t, brush(t.shade), brush(t.light))
    }

    /** The lobby's granite wall, a repeating brush like a floor's. */
    val lobbyWall: FacadeImages by lazy {
        val t = FacadeSprites.lobbyTile()
        synchronized(this) { facadeBytes += t.w * t.h * 4 }
        FacadeImages(
            t,
            ShaderBrush(
                ImageShader(image(t.pixels, t.w, t.h), TileMode.Repeated, TileMode.Repeated)
            ),
        )
    }

    /** The far city the glass mirrors by day: a strip repeated across, clamped down. */
    val mirror: Brush by lazy {
        val t = FacadeSprites.mirror()
        synchronized(this) { facadeBytes += t.w * t.h * 4 }
        ShaderBrush(ImageShader(image(t.light, t.w, t.h), TileMode.Repeated, TileMode.Clamp))
    }

    /** What the facades' walls made so far take, in bytes. */
    @Synchronized fun facadeBytes(): Int = facadeBytes

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
        sun
        moon
        trees
        vehicles
        glow
        beam
        streak
        grain
        plane
        contrail
        blimp
        weather
        dusk

        skyline
        foreground
        flare
        light
        for (h in BuildingSpec.HEIGHTS) {
            val rows = if (h >= BuildingSpec.TWO_ROWS) 2 else 1
            val wh = (h - BuildingSpec.BAND_TOP - BuildingSpec.PLAQUE_BAND - 6f * (rows - 1)) / rows
            // the first variant of every kind; the others are made as they are first seen
            for (k in RoomKind.entries) room(k, 0, wh)
        }
    }

    /** What the pools hold, in bytes. */
    fun bytes(): Int =
        CloudSprites.bytes() +
            SunMoonSprites.bytes() +
            TreeSprites.bytes() +
            RoadSkySprites.bytes() +
            AirSprites.bytes() +
            WeatherSprites.bytes() +
            DuskSprites.bytes() +
            SkylineSprites.bytes() +
            ForegroundSprites.bytes() +
            LightSprites.bytes() +
            Grain.SIZE * Grain.SIZE * 4 +
            peopleBytes() +
            seasonBytes() +
            streetBytes() +
            facadeBytes()

    private fun image(l: SkyLayer): ImageBitmap = image(l.pixels, l.size, l.size)

    private fun image(pixels: IntArray, w: Int, h: Int): ImageBitmap =
        Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
}
