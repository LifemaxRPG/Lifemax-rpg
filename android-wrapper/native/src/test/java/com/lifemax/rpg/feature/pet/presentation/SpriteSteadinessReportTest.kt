package com.lifemax.rpg.feature.pet.presentation

import com.lifemax.rpg.feature.pet.body.Clips
import com.lifemax.rpg.feature.pet.presentation.SteadinessProbe.Cause
import com.lifemax.rpg.feature.pet.presentation.SteadinessProbe.Renderer
import com.lifemax.rpg.feature.pet.presentation.SteadinessProbe.Run
import com.lifemax.rpg.feature.pet.presentation.SteadinessProbe.Summary
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Writes the sprite steadiness audit's tables (docs/specs/PET_SPRITE_STEADINESS.md): every clip of
 * the shipped art acted in place, the resting rows at both phone scales, the moving rows and two
 * cats' day in the house, each drawn before and after the steady drawing. It takes a few minutes,
 * so it only runs when `LMA_STEADINESS_REPORT` is set; the report lands in
 * `build/steadiness-report.md` of the app module. [SteadyCatTest] holds the rules that always run.
 */
class SpriteSteadinessReportTest {
    @Test
    fun writeTheReport() {
        assumeTrue(System.getenv("LMA_STEADINESS_REPORT") != null)
        val out = StringBuilder()
        val looks = SteadinessScenes.looks
        out.append("## Resting rows, ${REST_S.toInt()} s at 120 Hz\n\n")
        header(out)
        for (s in listOf(SteadinessScenes.CARD_SCALE, SteadinessScenes.ROOM_SCALE)) for (look in
            looks) for (row in SteadinessScenes.restRows) {
            val pair = Renderer.entries.map { SteadinessScenes.act(look, row, it, s, REST_S) }
            line(out, "$row | ${look.label} | $s", pair)
        }
        out.append("\n## Every clip acted in place, ${CLIP_S.toInt()} s, card scale\n\n")
        header(out)
        for (look in looks) for (clip in Clips.all) {
            val pair =
                Renderer.entries.map {
                    SteadinessScenes.act(look, clip.name, it, SteadinessScenes.CARD_SCALE, CLIP_S)
                }
            if (pair[0].changed.isEmpty()) {
                out.append("| ${clip.name} | ${look.label} | 5 | no art for this coat |\n")
                continue
            }
            line(out, "${clip.name} | ${look.label} | 5", pair)
        }
        out.append("\n## Moving rows, ${MOVE_S.toInt()} s, card scale\n\n")
        header(out)
        for (look in looks) for ((name, plan) in SteadinessScenes.moveRows) {
            val pair =
                Renderer.entries.map {
                    SteadinessScenes.move(look, plan, it, SteadinessScenes.CARD_SCALE, MOVE_S)
                }
            line(out, "$name | ${look.label} | 5", pair)
        }
        out.append("\n## Two cats in the house, ${HOUSE_S.toInt()} s, card scale\n\n")
        header(out)
        val house =
            Renderer.entries.map {
                SteadinessScenes.house(
                    listOf(SteadinessScenes.calico, SteadinessScenes.ginger),
                    it,
                    SteadinessScenes.CARD_SCALE,
                    HOUSE_S,
                )
            }
        for (i in house[0].indices) line(
            out,
            "house cat $i | ${house[0][i].look.label} | 5",
            house.map { it[i] },
        )
        REPORT.writeText(out.toString())
        println(out)
    }

    private fun header(out: StringBuilder) {
        out.append(
            "| scene | look | s | frames | before: changed steps | before: px/step min/med/max | " +
                "before: coat px off-art | before: unexplained steps (px) | before: a-b-a px | " +
                "after: changed steps | after: px/step min/med/max | after: coat px off-art | " +
                "after: unexplained steps (px) | after: a-b-a px | after: art/rig/breath/body steps |\n"
        )
        out.append("|" + "---|".repeat(COLUMNS) + "\n")
    }

    private fun line(out: StringBuilder, label: String, runs: List<Run>) {
        val b = Summary(runs[0])
        val a = Summary(runs[1])
        val r = runs[1]
        out.append(
            "| $label | ${b.steps} | ${b.changedSteps} | ${b.min}/${b.median}/${b.max} | " +
                "${b.coatOffArt} | ${b.unexplained} (${b.unexplainedPixels}) | ${b.flicker} | " +
                "${a.changedSteps} | ${a.min}/${a.median}/${a.max} | ${a.coatOffArt} | " +
                "${a.unexplained} (${a.unexplainedPixels}) | ${a.flicker} | " +
                "${a.stepsOf(Cause.ART, r)}/${a.stepsOf(Cause.RIG, r)}/" +
                "${a.stepsOf(Cause.BREATH, r)}/${a.stepsOf(Cause.BODY, r)} |\n"
        )
        REPORT.writeText(out.toString())
    }

    private companion object {
        const val REST_S = 8f
        const val CLIP_S = 3f
        const val MOVE_S = 6f
        const val HOUSE_S = 40f
        val REPORT = File("build/steadiness-report.md")
        const val COLUMNS = 15
    }
}
