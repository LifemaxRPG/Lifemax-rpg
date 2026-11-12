package com.lifemax.rpg.feature.pet.art

/**
 * The index report of WP-L2: which palette indices occur in which sheets, and whether any value
 * outside the documented scheme (10 outline, 20-50 coat, 60-80 stripes, 90-110 white, 120-140 pink,
 * 150 pupil, 160-170 iris, 180-210 details) turns up, which would mean the split table is wrong for
 * that sheet.
 */
object ShadeSplitReport {
    /** Every index the palette header documents. */
    val documented: Set<Int> = setOf(0, 10) + (20..210 step 10)

    class Report(val text: String, val surprises: Set<Int>)

    fun build(cases: List<SheetCase>): Report {
        val surprises = sortedSetOf<Int>()
        val lines = ArrayList<String>()
        for ((kind, group) in cases.groupBy { it.kind.key }) {
            val seen = sortedMapOf<Int, Long>()
            for (c in group) for (v in c.sheet.indices) seen.merge(v, 1L, Long::plus)
            surprises += seen.keys.filter { it !in documented }
            lines +=
                "REPORT $kind (${group.size} sheets): " +
                    seen.entries.joinToString(" ") { "${it.key}:${it.value}" }
        }
        lines += "REPORT undocumented indices: $surprises"
        for (label in cases.map { it.label.substringBefore(' ') }.distinct()) {
            val families = sortedSetOf<String>()
            for (c in cases.filter { it.label.substringBefore(' ') == label }) {
                for (v in c.sheet.indices.toSet()) {
                    val f = ShadeSplit.family(v)
                    if (f != ShadeFamily.FIXED) families += f.name
                }
            }
            lines += "REPORT $label uses ramps: $families"
        }
        return Report(lines.joinToString("\n"), surprises)
    }
}
