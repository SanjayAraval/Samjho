package com.packetloss.samjho.extract

import com.packetloss.samjho.llm.LlmEngine
import com.packetloss.samjho.model.Basis
import com.packetloss.samjho.model.Confirmation
import com.packetloss.samjho.model.Hypothesis
import com.packetloss.samjho.model.Utterance
import com.packetloss.samjho.speech.Hypotheses
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A REAL capture: the English consult read aloud on the iQOO I2501 with airplane mode on, Wi-Fi and
 * mobile data off (ping unreachable), Android system recogniser, offline en-US, EXTRA_MAX_RESULTS = 5.
 * These are the recogniser's raw hypothesis lists, unedited, including its all-zero confidences.
 *
 * What this capture showed: the N-best lists are the same sentence again (capitalisation, "5" vs
 * "five", a typo), never a different medicine, so layer 1 finds nothing here; and it exposed a
 * brand/generic mix-up and a dosing line that was silently dropped, both now fixed and tested below.
 */
class RealNBestTest {

    private class Raw(val hyps: List<String>, val conf: FloatArray)

    private fun raw(vararg h: String) = Raw(h.toList(), FloatArray(h.size)) // every confidence was 0.00

    private val capture = listOf(
        raw("You have a viral fever nothing to worry about", "You have a viral fever Nothing to worry about"),
        raw("parasite mall three times a day after food for 5 days", "parasite mall three times a day after food for five days"),
        raw(
            "Is it thrombison once a day in the morning on an empty stomach for 3 days",
            "Is it thrombison once a day in the morning on an empty stomach for three days",
            "Is it thrombison once a day in the morning on an empty stomach for free days",
        ),
        raw("and one citrus in tablet at night before sleeping", "and one centers in tablet at night before sleeping"),
        raw("Also Dolo 650 if the fever comes back", "Also DOLO 650 if the fever comes back"),
        raw("avoid cold water and fried food", "award cold water and fried food"),
        raw("If the fever goes above 102 go to the hospital immediately", "If the fever goes above 102 go to the hospital immediatly"),
        raw(
            "Come back after 5 days for a checkup", "come back after 5 days for a checkup",
            "Come back after 5 days for a second", "Come back after 5 days for a check up",
        ),
    )

    private val cleaned: List<Utterance> = capture.map { r ->
        val h = Hypotheses.build(r.hyps, r.conf)
        Utterance(h.first().text, h)
    }

    private val result = RuleExtractor.extractUtterances(cleaned)

    // ---- the N-best list itself

    @Test
    fun allZeroConfidencesAreDroppedNotShownAsRealScores() {
        assertTrue(cleaned.all { u -> u.hypotheses.all { it.confidence == null } })
    }

    @Test
    fun aHypothesisThatDiffersOnlyInCapitalsIsDropped() {
        assertEquals(1, cleaned[0].hypotheses.size)
        assertEquals(1, cleaned[4].hypotheses.size)
        assertEquals(3, cleaned[7].hypotheses.size) // the lower-case "come back" repeat is gone
    }

    @Test
    fun realConfidencesThatDifferAreKeptAndAMinusOneIsNotAScore() {
        val h = Hypotheses.build(listOf("a b", "a c", "a d"), floatArrayOf(0.9f, 0.4f, -1f))
        assertEquals(listOf(0.9f, 0.4f, null), h.map { it.confidence })
    }

    @Test
    fun missingScoresAreFine() {
        assertEquals(2, Hypotheses.build(listOf("one", "two"), null).size)
        assertTrue(Hypotheses.build(listOf("one", "  ", ""), null).size == 1)
    }

    @Test
    fun onThisRealCaptureTheNBestListAddedNoMedicineAtAll() {
        val topOnly = RuleExtractor.extract(cleaned.map { it.text })
        assertEquals(topOnly.medicines, result.medicines)
        assertTrue(result.medicines.none { it.basis == Basis.ALTERNATE_HEARING })
    }

    // ---- what the rules make of it

    @Test
    fun paracetamolHeardAsParasiteKeepsItsOwnScheduleAndNeedsConfirming() {
        val m = result.medicines.first { it.name == "parasite" }
        assertEquals("paracetamol", m.key)
        assertEquals(Basis.SOUNDS_LIKE, m.basis)
        assertEquals(Confirmation.UNCONFIRMED, m.confirmation)
        assertEquals(3, m.timesPerDay)
        assertEquals(5, m.durationDays)
        assertEquals(listOf(1), m.sourceLines)
    }

    @Test
    fun doloIsItsOwnCardAndNeverInheritsParacetamolsSchedule() {
        val dolo = result.medicines.first { it.name == "Dolo" }
        assertEquals("paracetamol", dolo.key)
        assertEquals(Basis.HEARD, dolo.basis)
        assertEquals(listOf(4), dolo.sourceLines)
        // The doctor gave a schedule for paracetamol and only "if the fever comes back" for Dolo.
        assertNull(dolo.timesPerDay)
        assertNull(dolo.foodRelation)
        assertNull(dolo.durationDays)
        assertEquals(2, result.medicines.count { it.key == "paracetamol" })
    }

    @Test
    fun theOtherItemsAreReadCorrectly() {
        assertEquals(listOf("a viral fever"), result.diagnosis.map { it.text })
        assertEquals(listOf("cold water", "fried food"), result.avoid.map { it.text })
        assertEquals(listOf(6), result.warnings.flatMap { it.sourceLines })
        assertEquals(5, result.followUp?.inDays)
        assertEquals("cetirizine", result.medicines.first { it.name == "citrus" }.key)
    }

    // ---- the dosing line that used to vanish

    @Test
    fun theThrombisonDosingLineIsShownAsHeardButUnnamedNotDropped() {
        assertTrue(2 in result.dosingLines)
        assertEquals(listOf(2), result.unnamedDosing.map { it.index })
        assertTrue(result.unnamedDosing.single().text.contains("once a day in the morning"))
        assertTrue(result.medicines.none { 2 in it.sourceLines }) // and no medicine is guessed for it
    }

    @Test
    fun aCitedDosingLineIsNotAlsoListedAsUnnamed() {
        assertTrue(1 !in result.unnamedDosing.map { it.index })
        assertTrue(3 !in result.unnamedDosing.map { it.index })
    }

    @Test
    fun dismissingAGuessBringsItsDosingLineBackAsUnnamed() {
        val i = result.medicines.indexOfFirst { it.name == "parasite" }
        val after = result.updateMedicine(i) { it.rejected() }
        assertEquals(listOf(1, 2), after.unnamedDosing.map { it.index })
        val undone = after.updateMedicine(i) { it.reopened() }
        assertEquals(listOf(2), undone.unnamedDosing.map { it.index })
    }

    @Test
    fun theLanguageModelCanReachThrombisonAndThenTheLineIsNoLongerUnnamed() {
        val cands = NameRepair.candidatesFor(result.lines[2].text, result.medicines.map { it.key }.toSet() - "azithromycin")
        assertEquals("azithromycin", cands.first().key)
        assertEquals("thrombison", cands.first().heard)

        val llm = object : LlmEngine {
            override fun unavailableReason(): String? = null
            override fun complete(prompt: String): String? = if (prompt.contains("thrombison")) "azithromycin" else "UNKNOWN"
        }
        val merged = result.withAdded(NameRepair.repair(result, llm))
        val ai = merged.medicines.single { it.key == "azithromycin" }
        assertEquals(Basis.AI_MATCHED, ai.basis)
        assertEquals(Confirmation.UNCONFIRMED, ai.confirmation)
        assertEquals(listOf(2), ai.sourceLines)
        assertEquals(1, ai.timesPerDay) // "once a day", read by the rules from the line
        assertEquals(3, ai.durationDays)
        assertTrue(merged.unnamedDosing.isEmpty())
    }

    @Test
    fun withNoLanguageModelTheThrombisonLineStaysUnnamedAndNothingIsInvented() {
        assertTrue(result.medicines.none { it.key == "azithromycin" })
        assertEquals(listOf(2), result.unnamedDosing.map { it.index })
    }

    // ---- brand versus generic, in general

    @Test
    fun aBrandAndItsGenericAreSeparateCardsAcrossLanguages() {
        val r = RuleExtractor.extract(
            listOf(
                "पैरासिटामोल की गोली सुबह लेनी है",
                "Take paracetamol after food for 5 days",
                "Crocin if the fever comes back",
            ),
        )
        assertEquals(2, r.medicines.size) // Hindi and English generic share one card; Crocin is its own
        val generic = r.medicines.first { it.name != "Crocin" }
        assertEquals(listOf(0, 1), generic.sourceLines)
        assertEquals(5, generic.durationDays)
    }

    @Test
    fun theSameBrandMentionedTwiceStillMergesIntoOneCard() {
        val r = RuleExtractor.extract(listOf("Take Dolo twice a day", "Dolo after food for 3 days"))
        val dolo = r.medicines.single()
        assertEquals(2, dolo.timesPerDay)
        assertEquals(3, dolo.durationDays)
    }
}
