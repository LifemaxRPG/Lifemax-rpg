package com.lifemax.rpg.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.android.resources.Density
import com.lifemax.rpg.feature.overview.data.WebOverviewRepository
import com.lifemax.rpg.feature.overview.domain.LiveCat
import com.lifemax.rpg.feature.overview.presentation.LocalOverviewColors
import com.lifemax.rpg.feature.overview.presentation.OverviewTheme
import com.lifemax.rpg.feature.overview.presentation.cards.CatCardView
import com.lifemax.rpg.feature.overview.presentation.cards.CatHousePreview
import com.lifemax.rpg.feature.overview.presentation.cards.CatLivePreview
import com.lifemax.rpg.feature.pet.art.CoatFixtures
import com.lifemax.rpg.feature.pet.art.FakeCoatBridge
import com.lifemax.rpg.feature.pet.art.RigMorph
import com.lifemax.rpg.feature.pet.body.Autopilot
import com.lifemax.rpg.feature.pet.body.CatBody
import com.lifemax.rpg.feature.pet.body.CatLife
import com.lifemax.rpg.feature.pet.body.EarState
import com.lifemax.rpg.feature.pet.body.HouseCat
import com.lifemax.rpg.feature.pet.body.HouseLife
import com.lifemax.rpg.feature.pet.body.PlanStep
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.TailState
import com.lifemax.rpg.feature.pet.body.World
import com.lifemax.rpg.feature.pet.presentation.CatArt
import com.lifemax.rpg.feature.pet.presentation.CatSprites
import com.lifemax.rpg.feature.pet.presentation.HouseStage
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * The cat's rig on the live cat, as the phone draws it: the ears, tail and whiskers where the
 * body's springs have them (the brain's states are only the springs' targets, so each image is a
 * cat some moments after its state changed), and the proportions of its genes. The card's strip
 * (the cat stepped to a fixed moment on a script) and the Cat tab's room (the same room stage) show
 * the same renderer. Ears forward, flat and swivelling; four tail states on a walking cat; long and
 * short proportions, a bobtail; a kitten, a genetic coat, light and dark; reduced motion.
 */
class CatRigScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(density = Density(420)),
            renderingMode = SessionParams.RenderingMode.SHRINK,
            maxPercentDifference = 0.001,
        )

    private val longEars = RigMorph(ear = 2.0, earWidth = 1.4)
    private val extremeLong = RigMorph(2.2, 1.5, 1.8, 1.4, 1.5, 1.8)
    private val extremeShort = RigMorph(0.5, 0.7, 0.45, 0.7, 0.7, 0.5)
    private val bobtail = RigMorph(1.1, 1.0, 1.3, 1.0, 1.0, 1.0, bob = true)

    /** A cat that does what [script] says, stepped [seconds] into it. */
    private fun life(
        seconds: Float,
        seed: String = "ginger",
        script: (CatBody) -> Unit = {},
    ): CatLife =
        CatLife(Autopilot.seedFor("Miso", seed), false, autopilot = false).also {
            script(it.body)
            it.advance(seconds)
        }

    private fun walk(ear: EarState, tail: TailState): (CatBody) -> Unit = {
        it.setAffect(ear, tail)
        it.replacePlan(1, listOf(PlanStep.Walk(240f)))
    }

    private fun sitting(ear: EarState, tail: TailState): (CatBody) -> Unit = {
        it.setAffect(ear, tail)
    }

    /** The sprites of [live] with [morph]; a genetic coat is asked of a fake page. */
    private fun sprites(live: LiveCat, morph: RigMorph): CatSprites {
        val label = CoatFixtures.coats.entries.firstOrNull { it.value == live.coat }?.key
        if (label == null) return CatArt.sprites(live.coat, live.stage, morph)!!
        val bridge = FakeCoatBridge()
        CatArt.coats.bind(bridge.requester)
        var answered = 0
        var found: CatSprites? = null
        repeat(4) {
            found = CatArt.sprites(live.coat, live.stage, morph)
            while (answered < bridge.sent.size) {
                val s = bridge.sent[answered++]
                if (s.kind.key in CoatFixtures.kinds) {
                    CatArt.coats.onReply(CoatFixtures.reply(label, s.kind.key, s.id))
                }
            }
        }
        return found!!
    }

    private fun cards(fixture: String, light: Boolean) =
        WebOverviewRepository()
            .apply {
                accept(
                    JSONObject(
                            javaClass
                                .getResource("/fixtures/overview-cards-$fixture.json")!!
                                .readText()
                        )
                        .put("light", light)
                        .put("tokens", ScreenshotPalette.tokens(light)),
                    true,
                )
            }
            .state
            .value!!

    private fun strip(
        morph: RigMorph,
        life: CatLife,
        fixture: String = "pet-live",
        light: Boolean = false,
        reducedMotion: Boolean = false,
    ) {
        val cards = cards(fixture, light)
        val sprites = sprites(cards.tiles.cat!!.live!!.first, morph)
        paparazzi.snapshot {
            OverviewTheme(cards.tokens, cards.light) {
                val c = LocalOverviewColors.current
                Box(Modifier.fillMaxWidth().background(c.bg).padding(12.dp)) {
                    CatCardView(
                        cards.tiles.cat!!,
                        cards.catPortrait,
                        {},
                        visible = false,
                        reducedMotion = reducedMotion,
                        preview = CatLivePreview(sprites, life),
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------------ ears

    @Test
    fun earsForward() =
        strip(longEars, life(1.5f, script = sitting(EarState.FORWARD, TailState.NEUTRAL)))

    @Test
    fun earsFlat() =
        strip(longEars, life(1.5f, script = sitting(EarState.AIRPLANE_FLAT, TailState.NEUTRAL)))

    @Test
    fun earsFlatPlainCat() =
        strip(
            RigMorph.NEUTRAL,
            life(1.5f, script = sitting(EarState.AIRPLANE_FLAT, TailState.NEUTRAL)),
            light = true,
        )

    @Test
    fun earsSwivel() =
        strip(longEars, life(1.7f, script = sitting(EarState.RADAR_SWIVEL, TailState.NEUTRAL)))

    @Test
    fun earsFlatWhileReducedMotionHoldsThem() =
        strip(
            longEars,
            life(1.5f, script = sitting(EarState.AIRPLANE_FLAT, TailState.NEUTRAL)),
            reducedMotion = true,
        )

    // ------------------------------------------------------------------ tail

    @Test
    fun tailHighFlagWalking() =
        strip(RigMorph.NEUTRAL, life(1.0f, script = walk(EarState.FORWARD, TailState.HIGH_FLAG)))

    @Test
    fun tailTuckedWalking() =
        strip(
            RigMorph.NEUTRAL,
            life(1.0f, script = walk(EarState.RELAXED_SIDE, TailState.TUCKED_CURL)),
        )

    @Test
    fun tailLowSwishWalking() =
        strip(RigMorph.NEUTRAL, life(1.0f, script = walk(EarState.FORWARD, TailState.LOW_SWISH)))

    @Test
    fun tailTwitchWalking() =
        strip(RigMorph.NEUTRAL, life(1.0f, script = walk(EarState.FORWARD, TailState.TIP_TWITCH)))

    // ------------------------------------------------------------------ proportions

    @Test
    fun extremeLongProportionsMidWalk() =
        strip(extremeLong, life(0.9f, script = walk(EarState.FORWARD, TailState.NEUTRAL)))

    @Test
    fun extremeShortProportionsMidWalk() =
        strip(extremeShort, life(0.9f, script = walk(EarState.FORWARD, TailState.NEUTRAL)))

    @Test
    fun bobtailWalking() =
        strip(bobtail, life(0.9f, script = walk(EarState.FORWARD, TailState.NEUTRAL)), light = true)

    @Test
    fun kittenWithLongEarsKeepsItsEars() =
        strip(
            longEars,
            life(1.5f, script = sitting(EarState.AIRPLANE_FLAT, TailState.NEUTRAL)),
            fixture = "kitten-live",
        )

    /**
     * A stage-2 kitten sitting still on its eye-open frame 2, whose tail the art sinks a pixel
     * through the floor: drawn mended (RestingFloor), the tail lies on the floor as in the other
     * frames. Once as the plain sheet, once through the rig.
     */
    @Test
    fun kittenSittingStillKeepsItsTailOnTheFloor() =
        strip(
            RigMorph.NEUTRAL,
            life(SIT_FRAME_2_S, script = sitting(EarState.FORWARD, TailState.NEUTRAL)),
            fixture = "kitten-live",
        )

    @Test
    fun kittenSittingStillKeepsItsTailOnTheFloorRigged() =
        strip(
            RigMorph(tail = 1.3, whisker = 1.2),
            life(SIT_FRAME_2_S, script = sitting(EarState.FORWARD, TailState.NEUTRAL)),
            fixture = "kitten-live",
        )

    @Test
    fun geneticCoatWithLongEarsFlat() =
        strip(
            extremeLong,
            life(1.5f, seed = "dna", script = sitting(EarState.AIRPLANE_FLAT, TailState.NEUTRAL)),
            fixture = "pet-live-dna",
        )

    // ------------------------------------------------------------------ the room (Cat tab and
    // Overview)

    @Test
    fun theRoomsTwoCatsWithTheirOwnProportions() {
        val cards = cards("two-cats-live", light = false)
        val cats = cards.tiles.cat!!.live!!.house
        val house =
            HouseLife(
                cats.map { HouseCat(Autopilot.seedFor(it.name ?: "cat", it.coat), it.weak) },
                autopilot = false,
            )
        house.bodies[0].placeAt(-118f, World.ART_Z, SurfaceId.FLOOR, 1)
        house.bodies[1].placeAt(-70f, World.ART_Z, SurfaceId.FLOOR, -1)
        house.bodies[0].setAffect(EarState.AIRPLANE_FLAT, TailState.NEUTRAL)
        house.bodies[1].setAffect(EarState.RADAR_SWIVEL, TailState.NEUTRAL)
        val stage = HouseStage(CatArt.room()!!, house) { 12.0 }
        stage.setSprites(0, sprites(cats[0], extremeLong))
        stage.setSprites(1, sprites(cats[1], extremeShort))
        stage.advance(2f)
        paparazzi.snapshot {
            OverviewTheme(cards.tokens, cards.light) {
                val c = LocalOverviewColors.current
                Box(Modifier.fillMaxWidth().background(c.bg).padding(12.dp)) {
                    CatCardView(
                        cards.tiles.cat!!,
                        cards.catPortrait,
                        {},
                        visible = false,
                        house = CatHousePreview(stage),
                    )
                }
            }
        }
    }

    private companion object {
        /**
         * Seconds into a fresh sitting clip at which the eye-open cycle (frames 0, 1, 2, 6, 7 at
         * half of 7 fps) shows frame 2, before the first blink (at 4 s at the earliest).
         */
        const val SIT_FRAME_2_S = 0.7f
    }
}
