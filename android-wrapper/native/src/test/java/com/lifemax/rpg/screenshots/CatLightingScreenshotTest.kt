package com.lifemax.rpg.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.android.resources.Density
import com.lifemax.rpg.feature.overview.data.WebOverviewRepository
import com.lifemax.rpg.feature.overview.presentation.LocalOverviewColors
import com.lifemax.rpg.feature.overview.presentation.OverviewTheme
import com.lifemax.rpg.feature.overview.presentation.cards.CatCardView
import com.lifemax.rpg.feature.overview.presentation.cards.CatHousePreview
import com.lifemax.rpg.feature.pet.art.CoatFixtures
import com.lifemax.rpg.feature.pet.body.PlanStep
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.light.Weather
import com.lifemax.rpg.feature.pet.presentation.LiveLight
import com.lifemax.rpg.feature.pet.room.DepthLevel
import com.lifemax.rpg.feature.pet.room.RoomStyle
import org.json.JSONObject
import org.junit.After
import org.junit.Rule
import org.junit.Test

/**
 * Cat lighting (docs/specs/CAT_LIGHTING_PLAN.md WP-L3) on the Overview card's live room, with the
 * developer switch on: the cats take the room's light at the fixed hours of the plan (12, 18.75,
 * 23) in the window's sun patch, out of it, in a box, on a sunny sill, in the moon patch and beside
 *     the lamp, for the four shipped coats and a genetic one, plus an overcast noon. One image has
 *     the switch off: it must be the very picture the room drew before lighting existed.
 *
 * New in this class: CI records it with `[refresh-cat-snapshots]` (the cat references job).
 */
class CatLightingScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(density = Density(420)),
            renderingMode = SessionParams.RenderingMode.SHRINK,
            maxPercentDifference = 0.001,
        )

    /**
     * The switch back off for the classes that run after this one in the same JVM. Written inside a
     * mutable snapshot, never straight into the global one: a global write after the last frame
     * makes Compose's GlobalSnapshotManager post its apply notification to this test's render
     * session, Paparazzi's teardown throws the session's queue away with it, and that manager then
     * never sends another one (its `sent` latch stays set and AndroidUiDispatcher.Main stays
     * "scheduled"). Every later screenshot class in the JVM would lose the state it writes outside
     * composition: the Life Director guide's spotlight target, the food sheets' snackbar (CI build
     * 509).
     */
    @After
    fun lightsOff() {
        lightSwitch(false)
    }

    /** [LiveLight]'s switches set in a mutable snapshot (see [lightsOff]). */
    private fun lightSwitch(on: Boolean) {
        Snapshot.withMutableSnapshot {
            LiveLight.enabled = on
            LiveLight.previewHour = null
        }
    }

    private fun card(
        coats: List<String>,
        spots: List<LiveSpot>,
        hour: Double,
        lit: Boolean = true,
        weather: Weather = Weather.CLEAR,
        style: RoomStyle = RoomStyle.live(DepthLevel.RICH),
    ) {
        lightSwitch(lit)
        val j =
            JSONObject(
                    javaClass
                        .getResource("/fixtures/overview-cards-two-cats-live.json")!!
                        .readText()
                )
                .put("light", false)
                .put("tokens", ScreenshotPalette.tokens(false))
        val cards = WebOverviewRepository().apply { accept(j, true) }.state.value!!
        val looks = coats.mapIndexed { i, coat -> LiveLook("cat ${i + 1}", coat, -1, false) }
        val stage =
            LiveDepthShots.stage(
                looks,
                spots,
                hour,
                4f,
                hold = true,
                style = style,
                weather = { weather },
            )
        paparazzi.snapshot() {
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

    /** Sitting on the floor at [x]. */
    private fun sit(x: Float, facing: Int = 1) = LiveSpot(x, facing)

    /** In the box (the living room's cardboard box), as a loaf. */
    private fun inBox(x: Float) =
        LiveSpot(x, 1, plan = listOf(PlanStep.Hop(SurfaceId.BOX, x), PlanStep.Act("loaf", 60f)))

    /** On the living room's window sill. */
    private fun onSill(x: Float) =
        LiveSpot(
            x,
            -1,
            plan = listOf(PlanStep.Hop(SurfaceId.LSILL, x), PlanStep.Act("idle_sit", 60f)),
        )

    @Test
    fun gingerInTheNoonPatchAndGrayOutOfIt() =
        card(listOf("ginger", "gray"), listOf(sit(-80f), sit(-150f, -1)), 12.0)

    @Test
    fun theSameMomentWithLightingOffIsTodaysRoom() =
        card(listOf("ginger", "gray"), listOf(sit(-80f), sit(-150f, -1)), 12.0, lit = false)

    @Test
    fun aBlackCatInThePatchTakesASheen() =
        card(listOf("black", "tuxedo"), listOf(sit(-80f), sit(-150f, -1)), 12.0)

    @Test
    fun aMirroredCatLitFromItsUndrawnSideIsFlattened() =
        card(listOf("ginger", "ginger"), listOf(sit(-80f, -1), sit(-60f, 1)), 12.0)

    @Test
    fun theMoonPatchAndTheLampAtNight() =
        card(listOf("gray", "black"), listOf(sit(-50f), sit(-168f, -1)), 23.0)

    @Test
    fun aDuskSillAndACatInABox() =
        card(listOf("tuxedo", "ginger"), listOf(onSill(-100f), inBox(-20f)), 18.75)

    @Test
    fun aGeneticCoatInTheBedroomPatch() =
        card(
            listOf(CoatFixtures.coats.getValue("calico"), "gray"),
            listOf(sit(60f), sit(150f, -1)),
            12.0,
        )

    @Test
    fun anOvercastNoonGivesAGreyHalfTone() =
        card(
            listOf("ginger", "black"),
            listOf(sit(-80f), sit(-70f, -1)),
            12.0,
            weather = Weather.OVERCAST,
        )
}
