package com.lifemax.rpg.core.designsystem.feedback

/**
 * One attribute's share of an award, as the page sends it in a reply's `celebrations[].gains[]`:
 * the attribute's key ("strength"), its short name ("STR"), the XP it gained and its colour as
 * 0xAARRGGBB.
 */
data class XpGain(val attribute: String, val short: String, val xp: Int, val color: Long)

/**
 * An award the page held back for a native screen to draw (celebrateXP, 07-runtime.js), as a reply
 * carries it in `celebrations[]`: the total [xp] the label shows and the [gains] that colour it. An
 * award without gains is not drawn.
 */
data class XpAward(val xp: Int, val gains: List<XpGain>)
