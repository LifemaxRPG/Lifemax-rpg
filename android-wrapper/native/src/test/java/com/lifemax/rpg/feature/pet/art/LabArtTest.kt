package com.lifemax.rpg.feature.pet.art

import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The lab rows (`tools/pet_art`, docs/specs/PET_ART_LAB.md) as the app ships them:
 * - every frame of the page's own rows is byte-identical to the fingerprint recorded before the
 *   first lab row existed (`pet-lab/base-frames.json`), for every kind;
 * - every lab frame recoloured by [Recolour] for the four coats equals the toolchain's own
 *   rendering (`pet-lab/lab-frames.json`, written by `tools/pet_art/build.js`);
 * - a genetic coat composed by the page gets the lab rows too, recoloured exactly as the room does;
 * - the metadata (`lab.json`) is complete and consistent with the sheet table.
 */
class LabArtTest {
    private val art = PetArtResources.load()
    private val lab = PetArtResources.loadLab()
    private val coats = PetArtFixtures.coats
    private val labNames = lab.rows.keys

    private fun resource(name: String): String =
        String(
            LabArtTest::class.java.getResourceAsStream("/pet-lab/$name")!!.use { it.readBytes() },
            Charsets.UTF_8,
        )

    private fun hex16(digest: MessageDigest): String =
        digest.digest().joinToString("") { "%02x".format(it) }.substring(0, HASH_DIGITS)

    private fun kindOf(name: String): SheetKind =
        when {
            name == "adult" -> SheetKind.Adult
            name == "depth" -> SheetKind.Depth
            name.startsWith("dst") -> SheetKind.DepthStage(name.removePrefix("dst").toInt())
            else -> SheetKind.Stage(name.removePrefix("st").toInt())
        }

    /** the page's base-frame fingerprint: SHA-256 over the four coats' indices, first 16 hex */
    private fun indexHash(kind: SheetKind, rect: FrameRect): String {
        val md = MessageDigest.getInstance("SHA-256")
        for (coat in coats) {
            val sheet = art.sheet(coat, kind)!!
            val bytes = ByteArray(rect.width * rect.height)
            for (y in 0 until rect.height) for (x in 0 until rect.width) {
                bytes[y * rect.width + x] =
                    sheet.indices[(rect.y + y) * sheet.width + rect.x + x].toByte()
            }
            md.update(bytes)
        }
        return hex16(md)
    }

    /** the toolchain's lab reference: SHA-256 over the four coats recoloured, R G B A per pixel */
    private fun rgbaHash(kind: SheetKind, rect: FrameRect): String {
        val md = MessageDigest.getInstance("SHA-256")
        for (coat in coats) {
            val img = Recolour.region(art.sheet(coat, kind)!!, art.palette(coat)!!, rect)
            val bytes = ByteArray(img.pixels.size * 4)
            for ((i, p) in img.pixels.withIndex()) {
                bytes[i * 4] = (p ushr 16).toByte()
                bytes[i * 4 + 1] = (p ushr 8).toByte()
                bytes[i * 4 + 2] = p.toByte()
                bytes[i * 4 + 3] = (p ushr 24).toByte()
            }
            md.update(bytes)
        }
        return hex16(md)
    }

    @Test
    fun everyFrameOfThePagesOwnRowsIsByteIdenticalToItsRecordedFingerprint() {
        val record = JSONObject(resource("base-frames.json"))
        var checked = 0
        for (kindName in record.keys()) {
            val kind = kindOf(kindName)
            val rows = record.getJSONObject(kindName)
            val depth = kind == SheetKind.Depth || kind is SheetKind.DepthStage
            val table = if (depth) art.anims.depth else art.anims.adult
            for (row in rows.keys()) {
                val anim = table.first { it.name == row }
                val want = rows.getJSONArray(row)
                for (f in 0 until want.length()) {
                    assertEquals(
                        "$kindName $row#$f",
                        want.getString(f),
                        indexHash(kind, art.anims.frameRect(anim, f)),
                    )
                    checked++
                }
            }
        }
        assertTrue("checked $checked frames", checked > 1000)
    }

    @Test
    fun everyLabFrameRecolouredForTheFourCoatsMatchesTheToolchain() {
        val ref = JSONObject(resource("lab-frames.json"))
        for (kindName in ref.keys()) {
            val kind = kindOf(kindName)
            val rows = ref.getJSONObject(kindName)
            assertEquals(labNames, rows.keys().asSequence().toSet())
            for (row in rows.keys()) {
                val anim = art.anims[row]!!
                val want = rows.getJSONArray(row)
                assertEquals("$kindName $row frames", anim.frames, want.length())
                for (f in 0 until want.length()) assertEquals(
                    "$kindName $row#$f",
                    want.getString(f),
                    rgbaHash(kind, art.anims.frameRect(anim, f)),
                )
            }
        }
    }

    @Test
    fun aGeneticCoatComposedByThePageHasTheLabRowsRecolouredAsTheRoomDoes() {
        for (label in listOf("calico", "longwhite")) {
            val bridge = FakeCoatBridge()
            val source = PageCoatSource().also { it.bind(bridge.requester) }
            val coat = CoatFixtures.coats.getValue(label)
            for ((name, kind) in CoatFixtures.kinds) {
                source.sheet(coat, kind)
                assertTrue(source.onReply(CoatFixtures.reply(label, name, bridge.last().id)))
            }
            val dna = PetArtResources.load(source)
            for ((stage, sheetName) in listOf(Growth.ADULT to "adult", 1 to "st1")) {
                val room = CoatFixtures.room(label, sheetName)
                for (name in labNames) {
                    val anim = dna.growth.resolve(stage, name, dna.anims)!!
                    for (f in 0 until anim.frames) {
                        val got = dna.frame(coat, stage, name, f)!!
                        val r = dna.anims.frameRect(anim, f)
                        val want =
                            IntArray(r.width * r.height) {
                                room[r.x + it % r.width, r.y + it / r.width]
                            }
                        val tag = "$label $sheetName $name#$f"
                        val diff = firstDifference(tag, ArgbImage(r.width, r.height, want), got)
                        assertTrue(diff ?: "", diff == null)
                        assertTrue("$tag is drawn", got.pixels.any { it != CatPalette.CLEAR })
                    }
                }
            }
        }
    }

    @Test
    fun theLabRowsFollowThePagesRowsOnTheSheetWithTheirMetadata() {
        val adult = art.anims.adult
        assertEquals(PAGE_ROWS + labNames.size, adult.size)
        for ((i, anim) in adult.drop(PAGE_ROWS).withIndex()) {
            assertEquals(anim.name, PAGE_ROWS + i, anim.row)
            val row = assertNotNullRow(anim.name)
            assertEquals(anim.name, anim.fps, row.fps, 0.0)
            assertEquals(anim.name, anim.frames, row.frames)
            assertTrue(anim.name, anim.frames in 1..MAX_FRAMES)
            assertTrue(anim.name, row.planted.all { it in 0 until anim.frames })
            for (stage in listOf(Growth.ADULT, 0, 1, 2, 3, 4, 5)) {
                assertNotNull(
                    "${anim.name} at $stage",
                    art.growth.resolve(stage, anim.name, art.anims),
                )
                for (f in 0 until anim.frames) {
                    val m = row.mouth(stage, f) ?: continue
                    assertTrue(
                        "${anim.name}#$f mouth inside the frame",
                        m.x in 0f..72f && m.y in 0f..48f,
                    )
                }
            }
        }
        assertTrue(lab["turn"]!!.flipAtEnd)
        assertEquals(4, lab["pickup"]!!.bite)
        assertEquals(1, lab["toss"]!!.release)
        for (f in 0 until lab["carry"]!!.frames) assertNotNull(
            lab["carry"]!!.mouth(Growth.ADULT, f)
        )
    }

    @Test
    fun everyPairNamesRowsTheSheetHasAndHasAnOffsetPerStage() {
        assertEquals(setOf("snuggle", "groom", "greet"), lab.pairs.keys)
        for (p in lab.pairs.values) {
            assertNotNull(p.name, art.anims[p.a])
            assertNotNull(p.name, art.anims[p.b])
            for (stage in listOf(Growth.ADULT, 0, 5)) assertTrue(p.name, p.offset(stage).x != 0f)
        }
    }

    private fun assertNotNullRow(name: String): LabRow =
        lab[name] ?: error("$name has no lab metadata")

    private companion object {
        const val HASH_DIGITS = 16
        const val PAGE_ROWS = 28
        const val MAX_FRAMES = 8
    }
}
