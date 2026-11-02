package com.lifemax.rpg.screenshots

import androidx.compose.foundation.lazy.rememberLazyListState
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.feature.journal.data.WebJournalRepository
import com.lifemax.rpg.feature.journal.domain.JournalCommands
import com.lifemax.rpg.feature.journal.presentation.JournalActions
import com.lifemax.rpg.feature.journal.presentation.JournalScreen
import com.lifemax.rpg.feature.journal.presentation.JournalViewModel
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * Screen A of the native Journal on a 411 dp wide phone like the owner's: the switch, the tip and
 * the hero of the open Journal, the lock card in its three states, and the resting page, each in
 * both themes, and larger text. Square screenfuls at the device's own resolution. The frames are
 * the hand-made fixtures (fixtures/handmade) until gen_native_fixtures.js journal() lands; Screen
 * B's body (the strip, the day, the reviews) has its own screenshots.
 */
class JournalChromeScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(deviceConfig = phone(), useDeviceResolution = true, maxPercentDifference = 0.001)

    private fun phone(fontScale: Float = 1f, height: Int = 1080) =
        DeviceConfig.PIXEL_5.copy(
            screenHeight = height,
            density = Density(420),
            fontScale = fontScale,
        )

    private fun model(fixture: String, light: Boolean, rest: Boolean = false): JournalViewModel {
        val repo =
            WebJournalRepository().apply {
                accept(
                    JSONObject(
                            javaClass
                                .getResource("/fixtures/handmade/journal-a-$fixture.json")!!
                                .readText()
                        )
                        .put("light", light)
                        .put("tokens", ScreenshotPalette.tokens(light)),
                    true,
                )
                if (rest) rest()
            }
        return JournalViewModel(repo, JournalCommands { _, _ -> }).apply {
            opened()
            onProjection()
            // the toast rises in over time; Paparazzi's single frame would catch it half drawn
            ui.value.toast?.let { toastShown(it.id) }
        }
    }

    private fun snap(
        fixture: String,
        light: Boolean = false,
        rest: Boolean = false,
        unlocking: Boolean = false,
        item: Int = 0,
    ) {
        val vm = model(fixture, light, rest)
        if (unlocking) vm.unlock()
        paparazzi.snapshot {
            JournalScreen(vm.ui.value, JournalActions(), list = rememberLazyListState(item))
        }
    }

    /** The open Journal's top: the switch, the tip under it, the hero. */
    @Test fun heroDark() = snap("open")

    @Test fun heroLight() = snap("open", light = true)

    /** The whole hero from its top: the stats and the 28-day rhythm, today outlined. */
    @Test fun rhythmDark() = snap("open", item = HERO)

    @Test fun rhythmLight() = snap("open", light = true, item = HERO)

    /** The hero without the tip, as most visits show it. */
    @Test fun heroNoTipDark() = snap("review")

    @Test
    fun lockCardDark() {
        paparazzi.unsafeUpdateConfig(phone(height = CARD_HEIGHT))
        snap("locked")
    }

    @Test
    fun lockCardLight() {
        paparazzi.unsafeUpdateConfig(phone(height = CARD_HEIGHT))
        snap("locked", light = true)
    }

    /** The system prompt is up: Unlock waits, faded. */
    @Test
    fun lockCardPromptingDark() {
        paparazzi.unsafeUpdateConfig(phone(height = CARD_HEIGHT))
        snap("locked-prompting")
    }

    @Test
    fun lockCardPromptingLight() {
        paparazzi.unsafeUpdateConfig(phone(height = CARD_HEIGHT))
        snap("locked-prompting", light = true)
    }

    /** A failed unlock: the page's error line under Unlock. */
    @Test
    fun lockCardErrorDark() {
        paparazzi.unsafeUpdateConfig(phone(height = CARD_HEIGHT))
        snap("locked-error")
    }

    @Test
    fun lockCardErrorLight() {
        paparazzi.unsafeUpdateConfig(phone(height = CARD_HEIGHT))
        snap("locked-error", light = true)
    }

    /** Unlock sent, before the prompt rises: the button waits too. */
    @Test
    fun lockCardUnlockingDark() {
        paparazzi.unsafeUpdateConfig(phone(height = CARD_HEIGHT))
        snap("locked", unlocking = true)
    }

    /** A screen not on show: the switch on the page background. */
    @Test fun restingDark() = snap("open", rest = true)

    @Test fun restingLight() = snap("open", light = true, rest = true)

    /** Larger text (Settings, font size, at its largest). */
    @Test
    fun largeTextHero() {
        paparazzi.unsafeUpdateConfig(phone(fontScale = 2f))
        snap("open")
    }

    @Test
    fun largeTextLockCard() {
        paparazzi.unsafeUpdateConfig(phone(fontScale = 2f, height = 2000))
        snap("locked-error")
    }

    @Test
    fun largeTextRhythm() {
        paparazzi.unsafeUpdateConfig(phone(fontScale = 2f))
        snap("open", item = HERO)
    }

    private companion object {
        /** The hero's place in the open Journal's list: the switch, the tip, the hero. */
        const val HERO = 2

        /** Tall enough for the whole lock card with its error line, under the switch. */
        const val CARD_HEIGHT = 1500
    }
}
