package com.lifemax.rpg.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.lifemax.rpg.feature.overview.domain.CatPortrait
import com.lifemax.rpg.feature.overview.presentation.cards.PetPortrait
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * The cat card's portrait as the page drew it (tests/pet-portrait.test.js, RECORD=1, where it is
 * proven pixel for pixel against the pet room), on the card's panel: cropped to the cat and drawn
 * at the whole-pixel scale a 120 x 88 dp box gives on a Pixel 5 (two device pixels per image pixel)
 * and a larger box gives (four), and the paw that stands in while the page has no portrait yet.
 */
class OverviewPetPortraitScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, maxPercentDifference = 0.001)

    @Test
    fun portraitAndPlaceholder() {
        val fixture =
            JSONObject(javaClass.getResource("/fixtures/overview-cat-portrait.json")!!.readText())
        val key = fixture.getJSONObject("cat").getString("portrait")
        val portrait = CatPortrait(key, fixture.getJSONObject("images").getString(key))
        paparazzi.snapshot {
            Box(Modifier.fillMaxSize().background(Backdrop).padding(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PetPortrait(portrait, cell(120, 88), Muted)
                        PetPortrait(null, cell(120, 88), Muted)
                    }
                    PetPortrait(portrait, cell(252, 168), Muted)
                }
            }
        }
    }

    private fun cell(w: Int, h: Int) =
        Modifier.size(w.dp, h.dp).clip(RoundedCornerShape(12.dp)).background(Panel)

    private companion object {
        val Backdrop = Color(0xFF0C0E12)
        val Panel = Color(0xFF1C212B)
        val Muted = Color(0xFF8B93A3)
    }
}
