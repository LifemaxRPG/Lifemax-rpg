package com.lifemax.rpg.core.directorshadow

import com.lifemax.rpg.core.shadow.ShadowDecode
import com.lifemax.rpg.core.shadow.ShadowRefusal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** SH-2, SH-3, SH-6: the strict readers; a refusal names a key, never a value. */
class DirectorShadowJsonTest {
    private val save =
        """{"v":1,"scope":"director","sid":"d-abc","seq":3,"base":2,"kind":"director.save","t0":10,"t1":12,"zone":"UTC","ops":[["t",["ui"],"{}"]],"bytes":40,"cost":{"ms":1.5},"flags":[]}"""
    private val plan =
        """{"v":1,"scope":"director","sid":"d-abc","seq":4,"base":3,"kind":"director.plan","t0":10,"t1":12,"zone":"UTC","ops":[],"engine":{"planZone":"UTC","planOffset":0,"input":{},"output":{}},"cost":{"ms":1},"flags":[]}"""

    private fun refused(text: String): ShadowRefusal =
        (DirectorShadowJson.envelope(text) as ShadowDecode.Refused).reason

    @Test
    fun theV1ShapesRead() {
        val e = (DirectorShadowJson.envelope(save) as ShadowDecode.Ok).value
        assertEquals(DirectorShadowJson.SAVE, e.kind)
        assertEquals(listOf(TextOp('t', listOf("ui"), text = "{}")), e.ops)
        assertEquals(40, e.bytes)
        val p = (DirectorShadowJson.envelope(plan) as ShadowDecode.Ok).value
        assertEquals(0, p.engine!!.planOffset)
        val c =
            DirectorShadowJson.configure(
                """{"v":1,"scope":"director","enabled":true,"sid":"d-1","page":{"build":"b","fingerprint":"1.1.0/1","faults":0}}"""
            )
        assertTrue(c is ShadowDecode.Ok)
    }

    @Test
    fun everyWrongShapeIsRefused() {
        val cases =
            mapOf(
                save.replace("\"v\":1", "\"v\":2") to ShadowRefusal.UNSUPPORTED,
                save.replace("\"scope\":\"director\"", "\"scope\":\"economy\"") to
                    ShadowRefusal.OUT_OF_RANGE,
                save.replace("\"flags\":[]", "\"flags\":[],\"extra\":1") to
                    ShadowRefusal.UNKNOWN_KEY,
                save.replace(",\"bytes\":40", "") to ShadowRefusal.MISSING_KEY,
                save.replace("\"base\":2", "\"base\":3") to ShadowRefusal.OUT_OF_RANGE,
                save.replace("\"t1\":12", "\"t1\":9") to ShadowRefusal.BAD_TIME,
                save.replace("\"zone\":\"UTC\"", "\"zone\":\"Mars/Base\"") to
                    ShadowRefusal.BAD_ZONE,
                save.replace("\"kind\":\"director.save\"", "\"kind\":\"quest.toggle\"") to
                    ShadowRefusal.BAD_KIND,
                save.replace("[\"t\",[\"ui\"],\"{}\"]", "[\"t\",[\"ui\"],{}]") to
                    ShadowRefusal.BAD_DELTA,
                save.replace("[\"t\",[\"ui\"],\"{}\"]", "[\"x\",[\"ui\"],\"{}\"]") to
                    ShadowRefusal.BAD_DELTA,
                save.replace(
                    "[\"t\",[\"ui\"],\"{}\"]",
                    "[\"t\",[\"a\",\"b\",\"c\",\"d\",\"e\"],\"{}\"]",
                ) to ShadowRefusal.BAD_DELTA,
                save.replace("[\"t\",[\"ui\"],\"{}\"]", "[\"a\",[\"ui\"],-1]") to
                    ShadowRefusal.BAD_DELTA,
                save.replace("\"sid\":\"d-abc\"", "\"sid\":\"a b\"") to ShadowRefusal.OUT_OF_RANGE,
                save.replace("\"flags\":[]", "\"flags\":[\"Not a flag\"]") to
                    ShadowRefusal.OUT_OF_RANGE,
                plan.replace("\"ops\":[]", "\"ops\":[[\"t\",[\"ui\"],\"{}\"]]") to
                    ShadowRefusal.BAD_DELTA,
                plan.replace("\"planOffset\":0", "\"planOffset\":99999") to
                    ShadowRefusal.OUT_OF_RANGE,
                plan.replace(",\"output\":{}", "") to ShadowRefusal.MISSING_KEY,
                "[]" to ShadowRefusal.NOT_AN_OBJECT,
                "{" to ShadowRefusal.NOT_JSON,
            )
        for ((text, want) in cases) assertEquals(text, want, refused(text))
    }

    @Test
    fun aKindNativeDoesNotKnowIsReadForItsChanges() {
        val e =
            DirectorShadowJson.envelope(
                save.replace("director.save", "director.later").replace(",\"bytes\":40", "")
            )
        assertTrue(e is ShadowDecode.Ok)
    }

    @Test
    fun aBaseIsReadInParts() {
        val p =
            DirectorShadowJson.basePart(
                """{"v":1,"scope":"director","sid":"d-1","seq":1,"part":0,"parts":2,"text":"{\"ops\""}"""
            )
        assertTrue(p is ShadowDecode.Ok)
        assertEquals(
            ShadowRefusal.OUT_OF_RANGE,
            (DirectorShadowJson.basePart(
                    """{"v":1,"scope":"director","sid":"d-1","seq":1,"part":2,"parts":2,"text":""}"""
                ) as ShadowDecode.Refused)
                .reason,
        )
        assertTrue(
            DirectorShadowJson.baseOps("""{"ops":[["o",[],["a"]],["t",["a"],"1"]]}""")
                is ShadowDecode.Ok
        )
    }
}
