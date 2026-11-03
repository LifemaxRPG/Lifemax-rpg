package com.lifemax.rpg.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.core.designsystem.LocalPageColors
import com.lifemax.rpg.core.designsystem.PageColors
import com.lifemax.rpg.core.designsystem.PageTheme
import com.lifemax.rpg.core.designsystem.components.PageCard
import com.lifemax.rpg.feature.pet.data.WebPetRepository
import com.lifemax.rpg.feature.pet.domain.PetPanels
import com.lifemax.rpg.feature.pet.domain.PetProjection
import com.lifemax.rpg.feature.pet.presentation.PetDnaCard
import com.lifemax.rpg.feature.pet.presentation.PetInnerCard
import com.lifemax.rpg.feature.pet.presentation.PetLikesCard
import com.lifemax.rpg.feature.pet.presentation.PetMindRow
import com.lifemax.rpg.feature.pet.presentation.PetTogetherCard
import com.lifemax.rpg.feature.pet.presentation.PetToysCard
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * The Cat tab's panels (Native Cat, beta, `pet.panels.v1`) as the phone draws them, one image per
 * card and state, from the projections the page made (`tools/gen_pet_fixtures.js`): the mind row in
 * the cat's card, the Together card with its switcher, what the cat likes, its inner life, its
 * genetics (the genome table open too) and the toy list with a locked toy and its early unlock.
 * Each card is drawn open on the page's background; the last image has all of them closed, as they
 * come. Dark and light, and large text, where cards stack their halves.
 */
class PetPanelsScreenshotTest {
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
                            javaClass.getResource("/fixtures/pet-panels-$name.json")!!.readText()
                        )
                        .put("light", light)
                        .put("tokens", ScreenshotPalette.tokens(light)),
                    true,
                )
            }
            .state
            .value!!

    /** The cards [content] draws for [name]'s panels, on the page's background. */
    private fun shoot(
        name: String,
        light: Boolean = false,
        fontScale: Float = 1f,
        tall: Int = SHORT,
        label: String? = null,
        content: @Composable (PetPanels) -> Unit,
    ) {
        val p = projection(name, light)
        paparazzi.unsafeUpdateConfig(phone(fontScale, tall))
        paparazzi.snapshot(label) {
            PageTheme(remember(p.tokens, p.light) { PageColors(p.tokens, p.light) }) {
                Column(
                    Modifier.fillMaxWidth().background(LocalPageColors.current.bg).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    content(p.panels!!)
                }
            }
        }
    }

    @Composable
    private fun mindIn(panels: PetPanels) {
        PageCard(Modifier.fillMaxWidth(), padding = 14.dp) { PetMindRow(panels.mind!!) }
    }

    @Test
    fun mindRowOfABondedCatWithAwayNoteDark() =
        shoot("bonded", tall = SMALL, content = { mindIn(it) })

    @Test
    fun mindRowOfABondedCatLight() =
        shoot("bonded", light = true, tall = SMALL, content = { mindIn(it) })

    @Test fun mindRowOfAStrayAndHungryCat() = shoot("stray", tall = SMALL, content = { mindIn(it) })

    @Test
    fun mindRowLargeText() =
        shoot("geneticFemale", fontScale = 2f, tall = SMALL_LARGE, content = { mindIn(it) })

    @Test
    fun togetherCardTwoCatsDark() =
        shoot("twoCats") { PetTogetherCard(it.pair!!, it.words, false, {}, Modifier) }

    @Test
    fun togetherCardTwoCatsLight() =
        shoot("twoCats", light = true) { PetTogetherCard(it.pair!!, it.words, false, {}) }

    @Test
    fun togetherCardSecondCatInFocus() =
        shoot("twoCatsSecond") { PetTogetherCard(it.pair!!, it.words, false, {}) }

    @Test
    fun togetherCardWaitingForThePage() =
        shoot("twoCats") { PetTogetherCard(it.pair!!, it.words, true, {}) }

    @Test
    fun togetherCardLargeText() =
        shoot("twoCats", fontScale = 2f, tall = TALLER) {
            PetTogetherCard(it.pair!!, it.words, false, {})
        }

    @Test
    fun likesOfABondedCatDark() =
        shoot("bonded", tall = SHORT) { PetLikesCard(it.likes!!, it.words, startOpen = true) }

    @Test
    fun likesOfABondedCatLight() =
        shoot("bonded", light = true) { PetLikesCard(it.likes!!, it.words, startOpen = true) }

    @Test
    fun likesOfAStrayHaveNoPlacesYet() =
        shoot("stray") { PetLikesCard(it.likes!!, it.words, startOpen = true) }

    @Test
    fun likesLargeText() =
        shoot("bonded", fontScale = 2f, tall = TALL) {
            PetLikesCard(it.likes!!, it.words, startOpen = true)
        }

    @Test
    fun innerLifeOfABondedCatDark() =
        shoot("bonded", tall = TALL) { PetInnerCard(it.inner!!, it.words, startOpen = true) }

    @Test
    fun innerLifeOfABondedCatLight() =
        shoot("bonded", light = true, tall = TALL) {
            PetInnerCard(it.inner!!, it.words, startOpen = true)
        }

    @Test
    fun innerLifeOfAStray() =
        shoot("stray", tall = TALL) { PetInnerCard(it.inner!!, it.words, startOpen = true) }

    @Test
    fun innerLifeLargeText() =
        shoot("bonded", fontScale = 2f, tall = HUGE) {
            PetInnerCard(it.inner!!, it.words, startOpen = true)
        }

    @Test
    fun geneticsOfAFemaleDark() =
        shoot("geneticFemale", tall = DNA) { PetDnaCard(it.dna!!, it.words, {}, startOpen = true) }

    @Test
    fun geneticsOfAFemaleLight() =
        shoot("geneticFemale", light = true, tall = DNA) {
            PetDnaCard(it.dna!!, it.words, {}, startOpen = true)
        }

    @Test
    fun geneticsOfASterileMale() =
        shoot("geneticSterile", tall = DNA) { PetDnaCard(it.dna!!, it.words, {}, startOpen = true) }

    @Test
    fun geneticsOfAKitten() =
        shoot("kitten", tall = DNA) { PetDnaCard(it.dna!!, it.words, {}, startOpen = true) }

    @Test
    fun geneticsWithTheGenomeOpen() =
        shoot("geneticFemaleGenome", tall = GENOME) {
            PetDnaCard(it.dna!!, it.words, {}, startOpen = true)
        }

    @Test
    fun geneticsLargeText() =
        shoot("geneticMale", fontScale = 2f, tall = DNA_LARGE) {
            PetDnaCard(it.dna!!, it.words, {}, startOpen = true)
        }

    @Test
    fun toysOfAStrayWithLockedToysDark() =
        shoot("stray", tall = TOYS) {
            PetToysCard(it.toys!!, it.words, false, {}, startOpen = true)
        }

    @Test
    fun toysOfAStrayLight() =
        shoot("stray", light = true, tall = TOYS) {
            PetToysCard(it.toys!!, it.words, false, {}, startOpen = true)
        }

    @Test
    fun toysWithOneBoughtEarly() =
        shoot("strayBought", tall = TOYS) {
            PetToysCard(it.toys!!, it.words, false, {}, startOpen = true)
        }

    @Test
    fun toysOfABondedCatAllOpen() =
        shoot("bonded", tall = TOYS) {
            PetToysCard(it.toys!!, it.words, false, {}, startOpen = true)
        }

    @Test
    fun toysLargeText() =
        shoot("stray", fontScale = 2f, tall = HUGE) {
            PetToysCard(it.toys!!, it.words, false, {}, startOpen = true)
        }

    @Test
    fun allTheCardsClosedAsTheyCome() =
        shoot("twoCats", tall = TALL) {
            PetTogetherCard(it.pair!!, it.words, false, {})
            PetLikesCard(it.likes!!, it.words)
            PetInnerCard(it.inner!!, it.words)
            PetDnaCard(it.dna!!, it.words, {})
            PetToysCard(it.toys!!, it.words, false, {})
        }

    private companion object {
        const val SMALL = 700
        const val SMALL_LARGE = 1500
        const val SHORT = 1400
        const val TOYS = 1800
        const val TALLER = 3600
        const val DNA = 5600
        const val DNA_LARGE = 11500
        const val TALL = 2400
        const val HUGE = 5200
        const val GENOME = 9800
    }
}
