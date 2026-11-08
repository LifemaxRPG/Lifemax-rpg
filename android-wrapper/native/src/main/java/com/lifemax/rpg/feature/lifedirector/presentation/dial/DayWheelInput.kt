package com.lifemax.rpg.feature.lifedirector.presentation.dial

/** The watch bezel streams raw rotation separately from date detents. */
internal class DayWheelInput(
    val count: Int,
    val selected: Int,
    val begin: () -> Unit,
    val preview: (Int, Float) -> Unit,
    val end: (Int?) -> Unit,
    val hold: ((Float) -> Unit)? = null,
)
