package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * The developer readout under the Cat tab's buttons (only with the page's developer mode, the same
 * gate as "Demo parkour"): the depth level in use, why the performance guard chose it, its load and
 * the skew in pixels, so the owner can say what the phone is doing. Nothing is saved or logged.
 */
@Composable
internal fun DepthReadout(note: () -> String, modifier: Modifier = Modifier) {
    val text by
        produceState(note()) {
            while (true) {
                delay(READOUT_MS)
                value = note()
            }
        }
    PetNote(text, modifier, size = 11.sp)
}

private const val READOUT_MS = 500L
