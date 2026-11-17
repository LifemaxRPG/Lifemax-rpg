package com.lifemax.rpg.feature.habits

import com.lifemax.rpg.feature.habits.domain.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import kotlin.math.abs

/** Also runnable without Android/JUnit using the designer preview helper. */
object StreetGU1Scenarios {
    fun fingerprint(look: Int, explicitAppearance: Boolean = false): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            for (f in 0 until StreetRig.TOTAL_FRAMES) for (s in
                (if (explicitAppearance)
                        StreetRig.frame(StreetLooks.ALL[look], f, motionLook = look)
                    else StreetRig.frame(look, f))
                    .shapes) {
                out.writeInt(s.layer)
                out.writeInt(s.rgb)
                out.writeInt(s.mat)
                out.writeFloat(s.alpha)
                out.writeInt(s.pts.size)
                for (x in s.pts) out.writeFloat(x)
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()).joinToString("") {
            "%02x".format(it)
        }
    }

    fun runAll(): Int {
        var checks = 0
        fun verify(name: String, block: () -> Unit) {
            block()
            checks++
            println("PASS $name")
        }
        val look = StreetCameos.GU1
        verify("cameo is not registered into the ambient cache") {
            check(StreetLooks.ALL.none { it === look })
        }
        for (i in StreetLooks.ALL.indices) verify(
            "registered and explicit appearance paths agree for look $i"
        ) {
            check(fingerprint(i) == fingerprint(i, explicitAppearance = true))
        }
        verify("all walk stop and crouch poses have finite geometry and grounded feet") {
            for (f in 0 until StreetRig.TOTAL_FRAMES) {
                val fig = StreetRig.frame(look, f)
                check(fig.shapes.isNotEmpty())
                check(fig.shapes.all { s -> s.pts.size >= 6 && s.pts.all { it.isFinite() } })
                val bounds = FigBuilder.bounds(fig.shapes)
                check(bounds[2] - bounds[0] < 35f)
                check(bounds[3] < .5f)
            }
        }
        verify("distance-based gait still plants the foot through stance") {
            var reference = Float.NaN
            val stride = Gait.STRIDE * look.height * (look.walkStyle ?: StreetGait.style(3)).stride
            for (f in 6 * StreetGait.FRAMES / 32..11 * StreetGait.FRAMES / 32) {
                val j = StreetRig.frame(look, f).joints
                val x = j.footNX + stride * f / StreetGait.FRAMES
                if (reference.isNaN()) reference = x
                check(abs(x - reference) < .12f)
            }
        }
        verify("shirt carries its stripes and shorts leave exposed calves") {
            val shapes = StreetRig.frame(look, StreetRig.STAND_AT).shapes
            check(shapes.count { it.rgb == StreetCameos.STRIPE_DARK } >= 6)
            check(shapes.count { it.rgb == StreetCameos.STRIPE_LIGHT } >= 12)
            val bare = shapes.filter { it.rgb == look.wear.skin && it.mat == Mat.SKIN }
            check(bare.isNotEmpty() && bare.maxOf { FigBuilder.bounds(listOf(it))[3] } > -2f)
        }
        verify("shorts plain shoes and close crop are configured for this character") {
            check(look.shorts && look.tee == Tee.LOOSE_STRIPED && look.plainTrainers)
            check(look.shoes == Shoe.TRAINER && look.hair == StreetHair.CLOSE_CROP)
            check(look.face != null && !look.elder && !look.stick)
        }
        verify("all three face directions render the custom profile") {
            val views = listOf(-1f, 0f, 1f).map { StreetRig.head(look, it) }
            check(views.all { it.isNotEmpty() && it.all { s -> s.pts.all { x -> x.isFinite() } } })
            check(
                views[0].size != views[2].size ||
                    views[0].indices.any { !views[0][it].pts.contentEquals(views[2][it].pts) }
            )
            check(StreetRig.frame(look, StreetRig.STAND_AT, glance = .8f).shapes.isNotEmpty())
        }
        verify("walking keeps the head steady without changing planted-foot motion") {
            val joints = (0 until StreetGait.FRAMES).map { StreetRig.frame(look, it).joints }
            val vertical = joints.maxOf { it.top } - joints.minOf { it.top }
            val angle = joints.maxOf { it.headA } - joints.minOf { it.headA }
            println("Head motion: $vertical units vertically, $angle degrees")
            check(vertical < .50f)
            check(angle < .30f)
        }
        verify("cropped hair is one connected silhouette extending to the nape") {
            for (g in listOf(-1f, 0f, 1f)) {
                val hair = StreetRig.head(look, g).filter { it.mat == Mat.HAIR }
                check(hair.size == 1)
                check(FigBuilder.bounds(hair)[3] > -1.5f)
            }
        }
        verify("invalid frame indexes refuse") {
            for (f in listOf(-1, StreetRig.TOTAL_FRAMES)) {
                var failed = false
                try {
                    StreetRig.frame(look, f)
                } catch (_: IllegalArgumentException) {
                    failed = true
                }
                check(failed)
            }
        }
        return checks
    }

    @JvmStatic
    fun main(args: Array<String>) {
        println("${runAll()} cameo checks passed")
    }
}
