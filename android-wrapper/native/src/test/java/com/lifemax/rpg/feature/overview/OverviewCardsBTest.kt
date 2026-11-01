package com.lifemax.rpg.feature.overview

import com.lifemax.rpg.core.designsystem.feedback.HapticScale
import com.lifemax.rpg.feature.overview.data.WebOverviewRepository
import com.lifemax.rpg.feature.overview.presentation.cards.CardsBGeometry as G
import com.lifemax.rpg.feature.overview.presentation.cards.WEEK_PICK_MS
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where Cards B put the page's numbers: the week chart's columns, segments and pace line, and the
 * health mini-bars, against what the page lays out at 412 CSS px (measured in the built page on the
 * established save, 2026-10-14 21:30); and the projection's haptics, which reach the week card as
 * the page's own level (overview-hapscale.json, from the page; PageBuzzTest plays every level).
 */
class OverviewCardsBTest {
    private val eps = 0.05f

    @Test
    fun aDaysColumnIsTheChartLessItsPaddingGapAndLetter() {
        // the page: .ov-bs is 105 px in a 132 px chart
        assertEquals(105f, G.column(), eps)
        // at twice the text size the letter's line is 30 dp and the column gives way
        assertEquals(90f, G.column(labelLine = 30f), eps)
        assertEquals(0f, G.column(labelLine = 500f), eps)
    }

    @Test
    fun segmentsStackTheirFractionsOfTheColumnAsThePageWritesThem() {
        // Friday: Physique 0.5694, Discipline 0.25 → 56.9 % and 25.0 % of 105 px (59.73 and 26.25
        // in the page)
        val h = G.segments(listOf(0.5694444f, 0.25f), G.column())
        assertEquals(59.745f, h[0], eps)
        assertEquals(26.25f, h[1], eps)
    }

    @Test
    fun segmentsNeverOverflowTheColumn() {
        val h = G.segments(listOf(0.7f, 0.6f, 0.2f), 100f)
        assertEquals(listOf(70f, 30f, 0f), h)
    }

    @Test
    fun thePaceLineSitsAtItsFractionOfTheChartPlusTwentyTwo() {
        // pace 60 of a 72 XP top (0.8333): the page draws it at the chart's top
        assertEquals(132f, G.paceBottom(0.8333333f), eps)
        // pace 100 of 120 at frac 0.5: 66 + 22
        assertEquals(88f, G.paceBottom(0.5f), eps)
        assertEquals(22f, G.paceBottom(0f), eps)
        // a larger letter lifts it by as much as the letter grew, and never past the chart
        assertEquals(103f, G.paceBottom(0.5f, labelLine = 30f), eps)
        assertEquals(132f, G.paceBottom(0.8333333f, labelLine = 30f), eps)
    }

    @Test
    fun anEmptyDayIsThePagesTallSlab() {
        assertEquals(73f, G.EMPTY_DAY, 0f)
    }

    @Test
    fun healthMiniBarsAreRoundedPixelsOfThirtyEightWithAFloorOfFour() {
        // the page's 3,100 / 12,050 / 0 / 15,000 steps against a 15,000 top
        assertEquals(8f, G.healthBar(0.20666667f), 0f)
        assertEquals(31f, G.healthBar(0.80333334f), 0f)
        assertEquals(4f, G.healthBar(0f), 0f)
        assertEquals(38f, G.healthBar(1f), 0f)
        // half a pixel rounds up, as Math.round does
        assertEquals(19f, G.healthBar(0.5f), 0f)
    }

    @Test
    fun aFractionIsWrittenToATenthOfAPercent() {
        assertEquals(0.569f, G.tenths(0.5694444f), 1e-6f)
        assertEquals(0.028f, G.tenths(0.0277777f), 1e-6f)
    }

    @Test
    fun theWeekPickIsTheSixMillisecondBuzz() {
        assertEquals(6L, WEEK_PICK_MS)
    }

    @Test
    fun theProjectionsHapticsAreThePagesLevel() {
        fun res(name: String) = javaClass.getResource("/fixtures/$name")!!.readText()
        val cards =
            WebOverviewRepository()
                .apply { accept(JSONObject(res("overview-cards-established.json")), true) }
                .state
                .value!!
        val levels = JSONArray(res("overview-hapscale.json"))
        val normal =
            (0 until levels.length()).map(levels::getJSONObject).first {
                it.getString("level") == "normal"
            }
        assertEquals(
            HapticScale(
                normal.getDouble("dm"),
                normal.getDouble("add"),
                normal.getInt("amp"),
                normal.getLong("min"),
                normal.getLong("max"),
            ),
            cards.haptics,
        )
    }
}
