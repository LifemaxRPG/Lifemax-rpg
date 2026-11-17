package com.lifemax.rpg.feature.habits

import com.lifemax.rpg.feature.habits.domain.*
import kotlin.math.abs

object StreetGU5Scenarios {
    fun runAll(): Int {
        var checks = 0
        fun verify(name: String, run: () -> Unit) {
            run()
            checks++
            println("PASS $name")
        }
        val look = StreetCameos.GU5
        verify("high-haired cameo is isolated and uses shared hands") {
            check(StreetLooks.ALL.none { it === look })
            check(
                abs(look.build * look.armScale * look.handScale - StreetCameos.GROUP_HAND_BUILD) <
                    .00001f
            )
        }
        verify("hoodie satchel cargo shorts and sandals render together") {
            val shapes = StreetRig.frame(look, StreetRig.STAND_AT).shapes
            check(shapes.any { it.rgb == look.wear.bag && it.mat == Mat.CLOTH })
            check(shapes.any { it.rgb == 0xA7997C && it.mat == Mat.CLOTH })
            check(shapes.any { it.rgb == 0x5A7048 && it.mat == Mat.CLOTH })
            check(shapes.none { it.rgb == 0xE7E4DC && it.mat == Mat.CLOTH })
            check(look.cargoShorts && look.shorts && look.shoes == Shoe.SANDAL)
        }
        verify("tall hair is continuous and extends above the skull in each head direction") {
            for (g in listOf(-1f, 0f, 1f)) {
                val head = StreetRig.head(look, g)
                val hair = head.filter { it.rgb == look.wear.hair && it.mat == Mat.WOOL }
                check(hair.size == 1)
                val hb = FigBuilder.bounds(hair)
                val skin = FigBuilder.bounds(head.filter { it.mat == Mat.SKIN })
                check(hb[1] < skin[1] - .7f)
                check(hb[3] - hb[1] > 3.8f)
            }
        }
        verify("all high-hair poses stay finite and grounded") {
            for (f in 0 until StreetRig.TOTAL_FRAMES) {
                val fig = StreetRig.frame(look, f)
                check(fig.shapes.isNotEmpty() && fig.shapes.all { it.pts.all(Float::isFinite) })
                val bounds = FigBuilder.bounds(fig.shapes)
                check(bounds[2] - bounds[0] < 40f && bounds[3] < .5f)
            }
        }
        verify("sandals expose skin and have leather straps instead of closed uppers") {
            val shapes = arrayListOf<Shape>()
            StreetRig.shoe(shapes, look, 0f, 0f, 1f, 0f, 0f, 1f, 1f)
            check(shapes.count { it.mat == Mat.SKIN } == 1)
            check(shapes.count { it.mat == Mat.LEATHER } == 3)
        }
        verify("tall hair walk stays steady and preserves planted feet") {
            val joints = (0 until StreetGait.FRAMES).map { StreetRig.frame(look, it).joints }
            check(joints.maxOf { it.top } - joints.minOf { it.top } < .50f)
            check(joints.maxOf { it.headA } - joints.minOf { it.headA } < .30f)
            val stride = Gait.STRIDE * look.height * look.walkStyle!!.stride
            var ref = Float.NaN
            for (f in 6 * StreetGait.FRAMES / 32..11 * StreetGait.FRAMES / 32) {
                val x = joints[f].footNX + stride * f / StreetGait.FRAMES
                if (ref.isNaN()) ref = x
                check(abs(x - ref) < .12f)
            }
        }
        return checks
    }

    @JvmStatic
    fun main(args: Array<String>) {
        println("${runAll()} high-hair checks passed")
    }
}
