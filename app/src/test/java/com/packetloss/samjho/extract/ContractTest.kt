package com.packetloss.samjho.extract

import com.packetloss.samjho.demo.DemoConsultations
import com.packetloss.samjho.model.MissingField
import com.packetloss.samjho.model.MissingSection
import com.packetloss.samjho.model.Provenance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules the product promises, tested directly rather than left to review. */
class ContractTest {

    private val hindi = RuleExtractor.extract(DemoConsultations.HINDI.lines)
    private val english = RuleExtractor.extract(DemoConsultations.ENGLISH.lines)

    @Test
    fun everyItemCitesARealTranscriptLine() {
        for (result in listOf(hindi, english)) {
            val valid = result.lines.indices
            val citations = buildList {
                result.medicines.forEach { add(it.name to it.sourceLines) }
                result.diagnosis.forEach { add(it.text to it.sourceLines) }
                result.avoid.forEach { add(it.text to it.sourceLines) }
                result.warnings.forEach { add(it.text to it.sourceLines) }
                result.followUp?.let { add(it.text to it.sourceLines) }
            }
            assertTrue("nothing was extracted", citations.isNotEmpty())
            citations.forEach { (label, lines) ->
                assertTrue("$label cites no line", lines.isNotEmpty())
                lines.forEach { i ->
                    assertTrue("$label cites line $i, outside $valid", i in valid)
                }
            }
        }
    }

    @Test
    fun everyRuleItemIsTaggedAsComingFromRules() {
        val provenances = buildList {
            hindi.medicines.forEach { add(it.provenance) }
            hindi.diagnosis.forEach { add(it.provenance) }
            hindi.avoid.forEach { add(it.provenance) }
            hindi.warnings.forEach { add(it.provenance) }
            hindi.followUp?.let { add(it.provenance) }
        }
        assertTrue(provenances.isNotEmpty())
        assertTrue(provenances.all { it == Provenance.RULES })
    }

    @Test
    fun aMedicineWithNoDetailsReportsAllFourAsNotMentioned() {
        val result = RuleExtractor.extract(listOf("पैरासिटामोल की गोली लेनी है।"))
        assertEquals(1, result.medicines.size)
        val m = result.medicines.single()
        assertEquals(
            setOf(
                MissingField.TIMES_PER_DAY,
                MissingField.TIME_OF_DAY,
                MissingField.FOOD_RELATION,
                MissingField.DURATION,
            ),
            m.missing.toSet(),
        )
    }

    @Test
    fun aConsultationWithNothingInItInventsNothing() {
        val result = RuleExtractor.extract(listOf("आज मौसम अच्छा है।", "ठीक है, चलता हूँ।"))
        assertTrue(result.medicines.isEmpty())
        assertTrue(result.avoid.isEmpty())
        assertTrue(result.warnings.isEmpty())
        assertNull(result.followUp)
        assertTrue(MissingSection.MEDICINES in result.missingSections)
        assertTrue(MissingSection.FOLLOW_UP in result.missingSections)
    }

    @Test
    fun anAbsentFollowUpStaysAbsentRatherThanBeingInvented() {
        val lines = DemoConsultations.HINDI.lines.dropLast(1)
        val result = RuleExtractor.extract(lines)
        assertNull(result.followUp)
        assertTrue(MissingSection.FOLLOW_UP in result.missingSections)
    }

    /**
     * The lines the rules understood nothing on. This is the only material the on-device LLM is
     * later allowed to look at, which is what keeps it additive.
     */
    @Test
    fun theGreetingLineIsLeftUncoveredForTheLlmToLookAtLater() {
        assertTrue(0 !in hindi.coveredLines)
        assertTrue(1 in hindi.coveredLines)
        assertTrue(2 in hindi.coveredLines)
    }

    @Test
    fun rulesFinishWellInsideTheTwoSecondBudget() {
        val long = List(40) { DemoConsultations.HINDI.lines }.flatten()
        RuleExtractor.extract(long) // warm up the regex cache
        val start = System.nanoTime()
        val result = RuleExtractor.extract(long)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertTrue("rules took ${elapsedMs}ms for ${long.size} lines", elapsedMs < 2_000)
        assertEquals(3, result.medicines.size)
    }
}
