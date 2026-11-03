package com.lifemax.rpg.feature.pet.world

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fails the build (testDebugUnitTest) when the cat brain (feature/pet/brain) or the world contract
 * (feature/pet/world), or any package of the app they reach, names an Android or AndroidX type; and
 * proves the check bites on seeded violations, direct and transitive, while comments and strings
 * that merely mention Android pass.
 */
class HeadlessCheckTest {
    private val app = "com.lifemax.rpg"

    @Test
    fun theBrainAndTheWorldContractAreHeadless() {
        val v =
            HeadlessScanner.scan(
                HeadlessScanner.mainSources(),
                listOf("$app.feature.pet.brain", "$app.feature.pet.world", "$app.feature.pet.host"),
                app,
            )
        assertEquals("Android or AndroidX in the headless cat brain: $v", emptyList<String>(), v)
    }

    @Test
    fun theCheckFailsOnASeededViolation() {
        val root = Files.createTempDirectory("headless").toFile()
        try {
            put(
                root,
                "$app.feature.pet.brain",
                "Mind.kt",
                "package x\nimport $app.feature.pet.body.Legs\nimport kotlin.math.abs\nclass Mind",
            )
            put(
                root,
                "$app.feature.pet.body",
                "Legs.kt",
                "package x\n// android.graphics is fine in a comment\nval s = \"androidx.compose\"\nclass Legs",
            )
            put(
                root,
                "$app.feature.pet.world",
                "Clock.kt",
                "package x\nimport android.os.SystemClock\nclass Clock",
            )
            val direct =
                HeadlessScanner.scan(
                    root,
                    listOf("$app.feature.pet.brain", "$app.feature.pet.world"),
                    app,
                )
            assertEquals(listOf("Clock.kt: android.o..."), direct)
            // a violation two imports away is found too
            put(root, "$app.feature.pet.world", "Clock.kt", "package x\nclass Clock")
            put(
                root,
                "$app.feature.pet.body",
                "Legs.kt",
                "package x\nimport $app.feature.pet.art.Fur\nclass Legs",
            )
            put(
                root,
                "$app.feature.pet.art",
                "Fur.kt",
                "package x\nfun draw(c: androidx.compose.ui.graphics.Canvas) {}",
            )
            val transitive =
                HeadlessScanner.scan(
                    root,
                    listOf("$app.feature.pet.brain", "$app.feature.pet.world"),
                    app,
                )
            assertEquals(listOf("Fur.kt: androidx.c..."), transitive)
            // a root with no sources cannot pass by accident
            assertTrue(
                HeadlessScanner.scan(root, listOf("$app.feature.pet.mind"), app)
                    .single()
                    .contains("no sources")
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private fun put(root: File, pkg: String, name: String, text: String) {
        val dir = File(root, pkg.replace('.', '/')).also { it.mkdirs() }
        File(dir, name).writeText(text)
    }
}
