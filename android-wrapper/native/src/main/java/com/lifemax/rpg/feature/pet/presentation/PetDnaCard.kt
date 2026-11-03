package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LocalPageColors
import com.lifemax.rpg.core.designsystem.components.tapTarget
import com.lifemax.rpg.feature.pet.domain.PetBar
import com.lifemax.rpg.feature.pet.domain.PetDna
import com.lifemax.rpg.feature.pet.domain.PetGeneRow
import com.lifemax.rpg.feature.pet.domain.PetLine
import com.lifemax.rpg.feature.pet.domain.PetLocus
import com.lifemax.rpg.feature.pet.domain.PetMorph
import com.lifemax.rpg.feature.pet.domain.PetPanelWords

/*
 * The genetics panel (`#petDna`), read only: sex, karyotype and coat, one box per gene, what the
 * cat carries and what is hidden, the body shape and temperament bars, and the full genome table,
 * which opens on a tap and is in the projection only while open (the page sends its rows then).
 */

private val MaleBlue = Color(0xFF4F8FD6)
private val FemalePink = Color(0xFFE0679A)
private val BoxShape = RoundedCornerShape(10.dp)

/** The genetics card; [onGenome] tells the page the genome table opened (true) or closed. */
@Composable
internal fun PetDnaCard(
    dna: PetDna,
    words: PetPanelWords,
    onGenome: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    startOpen: Boolean = false,
) {
    val c = LocalPageColors.current
    PetCollapsibleCard(words.dna, modifier, startOpen) {
        DnaTop(dna)
        if (dna.sterile.isNotEmpty())
            Text(
                dna.sterile,
                Modifier.padding(top = 6.dp)
                    .fillMaxWidth()
                    .clip(BoxShape)
                    .background(c.panel2)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                color = c.text,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
            )
        PetFinePrint(PetLine("", dna.sub), Modifier.padding(top = 4.dp))
        Column(
            Modifier.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            dna.rows.forEach { GeneBox(it) }
        }
        PetFinePrint(dna.carries)
        PetFinePrint(dna.hidden)
        dna.morph?.let { MorphSection(it) }
        PetSectionHeading(dna.temperamentTitle)
        Temperament(dna)
        dna.persona.forEach { PetBarRow(it) }
        if (dna.mutations.isNotEmpty())
            PetFinePrint(PetLine("", dna.mutationsLead + dna.mutations + "."))
        GenomeTable(dna, words, onGenome)
    }
}

/** The page's sentence about the temperament, with the trait it reads as in bold. */
@Composable
private fun Temperament(dna: PetDna) {
    Text(
        buildAnnotatedString {
            append(dna.temperamentIntro)
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(dna.temperamentReads) }
            append(".")
        },
        Modifier.padding(vertical = 3.dp),
        color = LocalPageColors.current.muted,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    )
}

/** `.pet-dna-top`: the sex chip, the karyotype in monospace and the coat's name. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DnaTop(dna: PetDna) {
    val c = LocalPageColors.current
    FlowRow(
        Modifier.padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PetChip(dna.sex, if (dna.female) FemalePink else MaleBlue)
        Text(
            dna.karyotype,
            Modifier.padding(top = 4.dp),
            color = c.muted,
            fontSize = 11.5.sp,
            fontWeight = FontWeight(600),
            fontFamily = FontFamily.Monospace,
        )
        Text(
            dna.coat,
            Modifier.padding(top = 2.dp),
            color = c.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * `.pet-gene`: the gene's label and genotype on one line (stacked with large text), the text under.
 */
@Composable
private fun GeneBox(row: PetGeneRow) {
    val c = LocalPageColors.current
    Column(
        Modifier.fillMaxWidth()
            .clip(BoxShape)
            .background(c.panel2)
            .padding(horizontal = 9.dp, vertical = 7.dp)
            .semantics(mergeDescendants = true) {}
    ) {
        if (panelLargeText()) {
            Text(row.label, color = c.text, fontSize = 12.sp, fontWeight = FontWeight(650))
            Genotype(row.genotype)
        } else
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    row.label,
                    Modifier.weight(1f),
                    color = c.text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight(650),
                )
                Genotype(row.genotype)
            }
        Text(
            row.text,
            Modifier.padding(top = 1.dp),
            color = c.textDim,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
    }
}

@Composable
private fun Genotype(text: String) =
    Text(
        text,
        color = LocalPageColors.current.accent,
        fontSize = 12.sp,
        fontWeight = FontWeight(600),
        fontFamily = FontFamily.Monospace,
    )

/** The "Body shape" section: its words, the proportion bars and the tail gene. */
@Composable
private fun MorphSection(m: PetMorph) {
    PetSectionHeading(m.title)
    PetFinePrint(PetLine("", m.intro))
    m.rows.forEach { PetBarRow(PetBar(it.label, it.pct, it.note)) }
    Column(Modifier.padding(top = 6.dp)) { GeneBox(m.tail) }
}

/**
 * `details.pet-genome`: a heading row that opens the table of every locus (mother's and father's
 * allele); the rows are the page's, sent only while it is open.
 */
@Composable
private fun GenomeTable(dna: PetDna, words: PetPanelWords, onGenome: (Boolean) -> Unit) {
    val c = LocalPageColors.current
    val g = dna.genome
    Row(
        Modifier.fillMaxWidth()
            .padding(top = 10.dp)
            .semantics { stateDescription = if (g.open) "Open" else "Closed" }
            .tapTarget(Role.Button) { onGenome(!g.open) },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            g.title,
            Modifier.weight(1f),
            color = c.textDim,
            fontSize = 12.sp,
            fontWeight = FontWeight(650),
        )
        Text(if (g.open) words.genomeHide else words.genomeShow, color = c.accent, fontSize = 12.sp)
    }
    if (!g.open) return
    Column(
        Modifier.padding(top = 6.dp).fillMaxWidth().clip(BoxShape).border(1.dp, c.line, BoxShape)
    ) {
        LocusRow(g.head[0], g.head[1], g.head[2], g.head[3], header = true)
        g.loci.forEach { LocusRow(it.locusLabel(), it.chromosomeLabel(), it.mother, it.father) }
    }
    PetFinePrint(PetLine("", g.note), Modifier.padding(top = 6.dp))
}

private fun PetLocus.locusLabel() = if (gene.isEmpty()) locus else "$locus $gene"

private fun PetLocus.chromosomeLabel() =
    if (position > 0) "$chromosome ~$position Mb" else chromosome

/** One line of the genome table in monospace: locus, chromosome, mother's and father's allele. */
@Composable
private fun LocusRow(
    locus: String,
    chr: String,
    mom: String,
    dad: String,
    header: Boolean = false,
) {
    val c = LocalPageColors.current
    val weight = if (header) FontWeight.Bold else FontWeight(500)
    Row(
        Modifier.fillMaxWidth()
            .then(if (header) Modifier.background(c.panel2) else Modifier)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Cell(locus, 1.3f, weight)
        Cell(chr, 1.1f, weight)
        Cell(mom, .8f, weight)
        Cell(dad, .8f, weight)
    }
}

@Composable
private fun RowScope.Cell(text: String, share: Float, weight: FontWeight) =
    Text(
        text,
        Modifier.weight(share),
        color = LocalPageColors.current.text,
        fontSize = 11.5.sp,
        fontWeight = weight,
        fontFamily = FontFamily.Monospace,
        lineHeight = 15.sp,
    )
