package com.lifemax.rpg.feature.habits

import com.lifemax.rpg.feature.habits.domain.*
import kotlin.math.abs

object StreetGU2Scenarios {
    fun runAll(): Int {
        var checks = 0
        fun verify(name: String, run: () -> Unit) {
            run()
            checks++
            println("PASS $name")
        }
        val look = StreetCameos.GU2
        verify("plaid friend remains separate from ordinary street appearances") {
            check(StreetLooks.ALL.none { it === look })
            check(look !== StreetCameos.GU1 && look.build < StreetCameos.GU1.build)
        }
        verify("all plaid walk settle and crouch poses stay finite and grounded") {
            for (f in 0 until StreetRig.TOTAL_FRAMES) {
                val fig = StreetRig.frame(look, f)
                check(fig.shapes.isNotEmpty())
                check(fig.shapes.all { it.pts.all(Float::isFinite) })
                val bounds = FigBuilder.bounds(fig.shapes)
                check(bounds[2] - bounds[0] < 40f && bounds[3] < .5f)
            }
        }
        verify("plaid weave and cargo pocket geometry are present") {
            val shapes = StreetRig.frame(look, StreetRig.STAND_AT).shapes
            check(look.tee == Tee.PLAID_SHIRT && look.cargoShorts)
            check(shapes.count { it.rgb == StreetCameos.PLAID_LIGHT } > 15)
            check(shapes.any { it.rgb == StreetCameos.PLAID_DARK })
            check(
                shapes.any { it.rgb == StreetRig.dim(look.wear.legs, .91f) && it.mat == Mat.CLOTH }
            )
        }
        verify(
            "forward cap has a bill ahead of the face and long blond hair reaches the shoulder"
        ) {
            val head = StreetRig.head(look, 0f)
            val cap =
                head.filter {
                    it.rgb == look.wear.hat || it.rgb == StreetRig.dim(look.wear.hat, .77f)
                }
            check(cap.isNotEmpty())
            val capBounds = FigBuilder.bounds(cap)
            check(capBounds[2] > 2f && capBounds[2] > -capBounds[0])
            val hair = head.filter { it.rgb == look.wear.hair && it.mat == Mat.HAIR }
            check(hair.size == 1 && FigBuilder.bounds(hair)[3] > 0f)
            check(look.face!!.moustache && look.face.goatee)
        }
        verify("calmer gait stays level and plants its foot") {
            val joints = (0 until StreetGait.FRAMES).map { StreetRig.frame(look, it).joints }
            check(joints.maxOf { it.top } - joints.minOf { it.top } < .50f)
            check(joints.maxOf { it.headA } - joints.minOf { it.headA } < .30f)
            var reference = Float.NaN
            val stride = Gait.STRIDE * look.height * look.walkStyle!!.stride
            for (f in 6 * StreetGait.FRAMES / 32..11 * StreetGait.FRAMES / 32) {
                val x = joints[f].footNX + stride * f / StreetGait.FRAMES
                if (reference.isNaN()) reference = x
                check(abs(x - reference) < .12f)
            }
        }
        verify("collar face and hat work in both head-turn directions") {
            for (g in listOf(-1f, 0f, 1f)) {
                val fig = StreetRig.frame(look, StreetRig.STAND_AT, glance = g)
                check(fig.shapes.all { it.pts.all(Float::isFinite) })
                check(StreetRig.head(look, g).isNotEmpty())
            }
        }
        return checks
    }

    @JvmStatic
    fun main(args: Array<String>) {
        println("${runAll()} plaid checks passed")
    }
}
