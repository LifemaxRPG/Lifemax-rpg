package com.lifemax.rpg.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.core.designsystem.LocalPageColors
import com.lifemax.rpg.core.designsystem.PageColors
import com.lifemax.rpg.core.designsystem.PageTheme
import com.lifemax.rpg.feature.pet.data.WebPetRepository
import com.lifemax.rpg.feature.pet.domain.CatteryScreen
import com.lifemax.rpg.feature.pet.domain.PetNotice
import com.lifemax.rpg.feature.pet.domain.PetProjection
import com.lifemax.rpg.feature.pet.presentation.CatteryActions
import com.lifemax.rpg.feature.pet.presentation.CatteryEntryCard
import com.lifemax.rpg.feature.pet.presentation.CatteryHost
import com.lifemax.rpg.feature.pet.presentation.PetUi
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * The Cat tab's cattery screens (Native Cat, beta, `pet.cattery.v1`) as the phone draws them, from
 * the projections the page made (`tools/gen_pet_cattery_fixtures.js`): the entry card on the Cat
 * tab, the Cattery roster, the Breeding lab (with a litter, with no mate, with a full cattery),
 * Adoption of a second cat and In memory. Dark and light, and large text, where cards stack their
 * halves. A genetic coat's picture comes from the page on the phone; here the sheets are not
 * answered, so those frames are empty and only the layout, the words and the shapes show; the
 * memorial's ginger cat and any shipped coat draw. The dialogs (release, rename, the pickers) are
 * windows of their own and are not in these images.
 */
class PetCatteryScreenshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = phone(), maxPercentDifference = 0.001)

    private fun phone(fontScale: Float = 1f, tall: Int = SHORT) =
        DeviceConfig.PIXEL_5.copy(
            density = Density(420),
            screenHeight = tall,
            fontScale = fontScale,
        )

    private fun projection(name: String, light: Boolean): PetProjection =
        WebPetRepository()
            .apply {
                accept(
                    JSONObject(
                            javaClass.getResource("/fixtures/pet-cattery-$name.json")!!.readText()
                        )
                        .put("light", light)
                        .put("tokens", ScreenshotPalette.tokens(light)),
                    true,
                )
            }
            .state
            .value!!

    private fun screen(
        name: String,
        which: CatteryScreen,
        light: Boolean = false,
        fontScale: Float = 1f,
        tall: Int = SHORT,
        notice: PetNotice? = null,
    ) {
        val p = projection(name, light)
        paparazzi.unsafeUpdateConfig(phone(fontScale, tall))
        paparazzi.snapshot {
            CatteryHost(p, which, PetUi(notice = notice), CatteryActions(), 64f, 72f)
        }
    }

    private fun entry(light: Boolean, fontScale: Float = 1f) {
        val p = projection("entry", light)
        paparazzi.unsafeUpdateConfig(phone(fontScale, ENTRY))
        paparazzi.snapshot {
            PageTheme(remember(p.tokens, p.light) { PageColors(p.tokens, p.light) }) {
                Column(
                    Modifier.fillMaxWidth().background(LocalPageColors.current.bg).padding(12.dp)
                ) {
                    CatteryEntryCard(p.cattery!!, {})
                }
            }
        }
    }

    @Test fun entryCardDark() = entry(light = false)

    @Test fun entryCardLight() = entry(light = true)

    @Test fun entryCardLargeText() = entry(light = false, fontScale = 2f)

    @Test fun catteryRosterDark() = screen("cattery", CatteryScreen.CATTERY, tall = TALL)

    @Test
    fun catteryRosterLight() = screen("cattery", CatteryScreen.CATTERY, light = true, tall = TALL)

    @Test
    fun catteryRosterLargeText() =
        screen("cattery", CatteryScreen.CATTERY, fontScale = 2f, tall = HUGE)

    @Test fun breedingLabWithALitterDark() = screen("lab", CatteryScreen.LAB, tall = TALL)

    @Test
    fun breedingLabWithALitterLight() = screen("lab", CatteryScreen.LAB, light = true, tall = TALL)

    @Test fun breedingLabLargeText() = screen("lab", CatteryScreen.LAB, fontScale = 2f, tall = HUGE)

    @Test fun breedingLabWithNoMate() = screen("labNoMate", CatteryScreen.LAB, tall = SHORT)

    @Test
    fun breedingLabWithAFullCattery() =
        screen(
            "labFull",
            CatteryScreen.LAB,
            tall = TALL,
            notice = PetNotice(1, "Ted had 3 kittens"),
        )

    @Test fun adoptingASecondCatDark() = screen("adopt", CatteryScreen.ADOPT, tall = TALL)

    @Test
    fun adoptingASecondCatLight() = screen("adopt", CatteryScreen.ADOPT, light = true, tall = TALL)

    @Test
    fun adoptingASecondCatLargeText() =
        screen("adopt", CatteryScreen.ADOPT, fontScale = 2f, tall = HUGE)

    @Test fun inMemoryDark() = screen("memorial", CatteryScreen.MEMORIAL, tall = SHORT)

    @Test
    fun inMemoryLight() = screen("memorial", CatteryScreen.MEMORIAL, light = true, tall = SHORT)

    @Test
    fun inMemoryLargeText() =
        screen("memorial", CatteryScreen.MEMORIAL, fontScale = 2f, tall = TALL)

    private companion object {
        const val ENTRY = 600
        const val SHORT = 1400
        const val TALL = 2400
        const val HUGE = 5200
    }
}
