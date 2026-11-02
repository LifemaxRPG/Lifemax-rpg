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
import com.lifemax.rpg.feature.overview.presentation.LocalOverviewColors
import com.lifemax.rpg.feature.overview.presentation.OverviewTheme
import com.lifemax.rpg.feature.overview.presentation.cards.CatCardView
import com.lifemax.rpg.feature.overview.presentation.cards.CatLivePreview
import com.lifemax.rpg.feature.pet.body.Autopilot
import com.lifemax.rpg.feature.pet.body.CatLife
import com.lifemax.rpg.feature.pet.body.PlanStep
import com.lifemax.rpg.feature.pet.presentation.CatArt
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * The live cat on the Overview's cat card (Native cats, beta), as the phone draws it: the cat is
 * stepped to a fixed moment on a script (so every image is the same every time) and the clock is
 * not running. A sitting cat in dark and light, mid-walk, mid-groom, asleep, a kitten, a hungry
 * cat, and the sitting cat at twice the text size. The card's name and hearts are the page's.
 */
class CatLiveScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = phone(),
            renderingMode = SessionParams.RenderingMode.SHRINK,
            maxPercentDifference = 0.001,
        )

    private fun phone(fontScale: Float = 1f) =
        DeviceConfig.PIXEL_5.copy(density = Density(420), fontScale = fontScale)

    /** A cat that does exactly what [script] says, stepped [seconds] into it. */
    private fun life(
        weak: Boolean = false,
        seconds: Float,
        script: (CatLife) -> Unit = {},
    ): CatLife =
        CatLife(Autopilot.seedFor("Miso", "ginger"), weak, autopilot = false).also {
            script(it)
            it.advance(seconds)
        }

    private fun shoot(
        fixture: String,
        life: CatLife,
        light: Boolean = false,
        fontScale: Float = 1f,
        name: String? = null,
    ) {
        val j =
            JSONObject(javaClass.getResource("/fixtures/overview-cards-$fixture.json")!!.readText())
                .put("light", light)
                .put("tokens", ScreenshotPalette.tokens(light))
        val cards = WebOverviewRepository().apply { accept(j, true) }.state.value!!
        val live = cards.tiles.cat!!.live!!.first
        val sprites = CatArt.sprites(live.coat, live.stage)!!
        if (fontScale != 1f) paparazzi.unsafeUpdateConfig(phone(fontScale))
        paparazzi.snapshot(name) {
            OverviewTheme(cards.tokens, cards.light) {
                val c = LocalOverviewColors.current
                Box(Modifier.fillMaxWidth().background(c.bg).padding(12.dp)) {
                    CatCardView(
                        cards.tiles.cat!!,
                        cards.catPortrait,
                        {},
                        visible = false,
                        preview = CatLivePreview(sprites, life),
                    )
                }
            }
        }
    }

    @Test fun sittingDark() = shoot("pet-live", life(seconds = 1.2f))

    @Test fun sittingLight() = shoot("pet-live", life(seconds = 1.2f), light = true)

    @Test
    fun midWalk() =
        shoot(
            "pet-live",
            life(seconds = 0.9f) { it.body.replacePlan(1, listOf(PlanStep.Walk(130f))) },
        )

    @Test
    fun midGroom() =
        shoot(
            "pet-live",
            life(seconds = 1.7f) { it.body.replacePlan(1, listOf(PlanStep.Act("groom", 6f))) },
        )

    @Test
    fun asleep() =
        shoot(
            "pet-live",
            life(seconds = 5f) {
                it.body.replacePlan(
                    1,
                    listOf(PlanStep.Act("loaf", 1.5f), PlanStep.Act("sleep", 20f)),
                )
            },
        )

    @Test fun kitten() = shoot("kitten-live", life(seconds = 1.2f))

    @Test fun hungryCat() = shoot("pet-live", life(weak = true, seconds = 1.2f))

    @Test fun largerText() = shoot("pet-live", life(seconds = 1.2f), fontScale = 2f)
}
