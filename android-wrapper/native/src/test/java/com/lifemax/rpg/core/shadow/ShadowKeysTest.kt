package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.save.SaveFields
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The page's boot hook (`05-shadow-boot.js`) and native name the same shadow keys, record keys and
 * kinds, so a key one side adds is never refused or lost by the other (ECONOMY_SHADOW_PLAN 2.2).
 */
class ShadowKeysTest {
    private fun segment(name: String): String {
        val start = File(System.getProperty("user.dir")).absoluteFile
        return generateSequence(start) { it.parentFile }
            .map { File(it, "lifemax_modular_v73/src/runtime/segments/$name") }
            .first { it.isFile }
            .readText()
    }

    private fun list(text: String, name: String): List<String> {
        val body =
            Regex("const $name=\\[([^\\]]*)\\]").find(text)?.groupValues?.get(1) ?: error(name)
        return Regex("'([^']+)'").findAll(body).map { it.groupValues[1] }.toList()
    }

    @Test
    fun thePageAndNativeNameTheSameKeys() {
        val boot = segment("05-shadow-boot.js")
        assertEquals(SaveFields.TRACKED, list(boot, "TRACKED"))
        assertEquals(ShadowKeys.INPUTS, list(boot, "INPUTS"))
        val rec = Regex("REC=\\[([^\\]]*)\\]").find(boot)!!.groupValues[1]
        assertEquals(
            ShadowKeys.REC_KEYS,
            Regex("'([^']+)'").findAll(rec).map { it.groupValues[1] }.toSet(),
        )
    }

    @Test
    fun everyKindThePageSendsIsOneNativeKnows() {
        val points = segment("50-native-shadow-points.js")
        val kinds =
            Regex("\\['([a-z.]+)',(?:W|HOST),").findAll(points).map { it.groupValues[1] }.toSet()
        assertTrue(kinds.size > 20)
        assertEquals(emptySet<String>(), kinds - ShadowKeys.KINDS)
        assertEquals(setOf("boot.load", "boot.decay"), ShadowKeys.KINDS - kinds)
        assertTrue(kinds.all { ShadowKeys.KIND_SHAPE.matches(it) })
    }
}
