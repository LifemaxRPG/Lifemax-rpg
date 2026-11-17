package com.lifemax.rpg.feature.habits

import com.lifemax.rpg.feature.habits.domain.*
import kotlin.math.abs

object StreetGU3Scenarios {
    fun runAll(): Int {
        var checks = 0
        fun verify(name: String, run: () -> Unit) {
            run()
            checks++
            println("PASS $name")
        }
        val look = StreetCameos.GU3
        verify("work-shirt character stays outside ordinary traffic") {
            check(StreetLooks.ALL.none { it === look })
            check(look.build < StreetCameos.GU1.build && look.height > StreetCameos.GU2.height)
        }
        verify("work shirt red layer full trousers and belt render together") {
            val shapes = StreetRig.frame(look, StreetRig.STAND_AT).shapes
            check(look.tee == Tee.WORK_SHIRT && !look.shorts)
            check(shapes.any { it.rgb == look.wear.top && it.mat == Mat.CLOTH })
            check(shapes.any { it.rgb == look.underShirt && it.mat == Mat.CLOTH })
            check(shapes.any { it.rgb == look.wear.legs && it.mat == Mat.CLOTH })
            check(shapes.any { it.rgb == 0x2B2320 && it.mat == Mat.LEATHER })
        }
        verify("work-shirt walk settle and crouch poses remain finite") {
            for (f in 0 until StreetRig.TOTAL_FRAMES) {
                val fig = StreetRig.frame(look, f)
                check(fig.shapes.isNotEmpty() && fig.shapes.all { it.pts.all(Float::isFinite) })
                val bounds = FigBuilder.bounds(fig.shapes)
                check(bounds[2] - bounds[0] < 40f && bounds[3] < .5f)
            }
        }
        verify("shared hand dimensions match the other two characters") {
            val dimensions =
                listOf(look, StreetCameos.GU1, StreetCameos.GU2).map {
                    it.build * it.armScale * it.handScale
                }
            check(dimensions.all { abs(it - StreetCameos.GROUP_HAND_BUILD) < .00001f })
        }
        verify("close crop and facial features work in all head directions") {
            for (g in listOf(-1f, 0f, 1f)) {
                val head = StreetRig.head(look, g)
                check(head.isNotEmpty() && head.all { it.pts.all(Float::isFinite) })
                check(head.count { it.rgb == look.wear.hair && it.mat == Mat.WOOL } == 1)
                check(StreetRig.frame(look, StreetRig.STAND_AT, glance = g).shapes.isNotEmpty())
            }
            check(look.hair == StreetHair.TIGHT_CROP && look.face!!.moustache)
        }
        verify("steady head and planted foot survive the taller lean proportions") {
            val joints = (0 until StreetGait.FRAMES).map { StreetRig.frame(look, it).joints }
            check(joints.maxOf { it.top } - joints.minOf { it.top } < .50f)
            check(joints.maxOf { it.headA } - joints.minOf { it.headA } < .30f)
            val stride = Gait.STRIDE * look.height * look.walkStyle!!.stride
            var reference = Float.NaN
            for (f in 6 * StreetGait.FRAMES / 32..11 * StreetGait.FRAMES / 32) {
                val x = joints[f].footNX + stride * f / StreetGait.FRAMES
                if (reference.isNaN()) reference = x
                check(abs(x - reference) < .12f)
            }
        }
        verify("pocket stays on chest and stray red embroidery is absent") {
            val shapes = StreetRig.frame(look, StreetRig.STAND_AT).shapes
            val pocket =
                shapes.filter {
                    it.rgb == StreetRig.dim(look.wear.top, .88f) && it.mat == Mat.CLOTH
                }
            check(pocket.size == 1)
            check(pocket.single().pts.filterIndexed { i, _ -> i % 2 == 0 }.min() > 0f)
            check(shapes.none { it.rgb == 0xA3443B })
        }
        return checks
    }

    @JvmStatic
    fun main(args: Array<String>) {
        println("${runAll()} work-shirt checks passed")
    }
}
