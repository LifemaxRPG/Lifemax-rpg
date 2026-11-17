package com.lifemax.rpg.feature.habits

import com.lifemax.rpg.feature.habits.domain.*
import kotlin.math.abs

object StreetGU4Scenarios {
    fun runAll(): Int {
        var checks = 0
        fun verify(name: String, run: () -> Unit) {
            run()
            checks++
            println("PASS $name")
        }
        val look = StreetCameos.GU4
        verify("stocky cameo stays outside ordinary traffic") {
            check(StreetLooks.ALL.none { it === look })
            check(look.build > StreetCameos.GU1.build && look.belly > 0f)
        }
        verify("sports panels and beard render without a towel") {
            val shapes = StreetRig.frame(look, StreetRig.STAND_AT).shapes
            check(shapes.none { it.rgb == 0xCBC1AA && it.mat == Mat.WOOL })
            check(shapes.any { it.rgb == look.sportPanel && it.mat == Mat.CLOTH })
            check(shapes.any { it.rgb == look.wear.coat && it.mat == Mat.CLOTH })
            check(look.face!!.beard && look.shorts)
        }
        verify("stocky walk settle and crouch remain finite") {
            for (f in 0 until StreetRig.TOTAL_FRAMES) {
                val fig = StreetRig.frame(look, f)
                check(fig.shapes.isNotEmpty() && fig.shapes.all { it.pts.all(Float::isFinite) })
                val bounds = FigBuilder.bounds(fig.shapes)
                check(bounds[2] - bounds[0] < 40f && bounds[3] < .5f)
            }
        }
        verify("stocky build preserves shared hands") {
            check(
                abs(look.build * look.armScale * look.handScale - StreetCameos.GROUP_HAND_BUILD) <
                    .00001f
            )
        }
        verify("receding crop renders in all head directions") {
            for (g in listOf(-1f, 0f, 1f)) {
                val shapes = StreetRig.head(look, g)
                check(shapes.isNotEmpty() && shapes.all { it.pts.all(Float::isFinite) })
                check(shapes.any { it.rgb == look.wear.hair && it.mat == Mat.WOOL })
            }
        }
        verify("heavier walk keeps a steady head") {
            val joints = (0 until StreetGait.FRAMES).map { StreetRig.frame(look, it).joints }
            check(joints.maxOf { it.top } - joints.minOf { it.top } < .50f)
            check(joints.maxOf { it.headA } - joints.minOf { it.headA } < .30f)
        }
        return checks
    }

    @JvmStatic
    fun main(args: Array<String>) {
        println("${runAll()} sports-shirt checks passed")
    }
}
