package com.packetloss.samjho.extract

import com.packetloss.samjho.model.Basis
import com.packetloss.samjho.model.Confirmation
import com.packetloss.samjho.model.FoodRelation
import com.packetloss.samjho.model.TimeOfDay
import com.packetloss.samjho.model.Utterance
import com.packetloss.samjho.speech.Hypotheses
import com.packetloss.samjho.ui.Strings
import com.packetloss.samjho.voice.ReadAloudScript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A REAL live reading of the English consult on the iQOO I2501, release-candidate build 29d497a,
 * Android system recogniser, en-US, EXTRA_MAX_RESULTS = 5. The raw hypothesis lists are copied from
 * the SamjhoSpeech log unedited. (The network state during this run was not recorded, so it makes
 * no claim about offline behaviour.)
 *
 * It exposed four rule bugs, each covered below: paracetamol split into "Paris at Mall"; a diagnosis
 * sharing a sentence with the dosing and hiding it; "throw medicine" shown as a medicine; and
 * "tablet at night" with a lost drug name being invisible.
 */
class RealLiveRunTest {

    private fun utterance(vararg raw: String): Utterance {
        val h = Hypotheses.build(raw.toList(), FloatArray(raw.size))
        return Utterance(h.first().text, h)
    }

    private val run = listOf(
        utterance(
            "You have a viral fever nothing to worry about take Paris at Mall three times a day after food for 5 days",
            "You have a viral fever nothing to worry about take Paris at Mall three times a day after food for five days",
        ),
        utterance(
            "Is it throw medicine once a day in the morning on an empty stomach for 3 days",
            "Is it throw medicine once a day in the morning on an empty stomach for three days",
            "Is it throw medicine on Saturday in the morning on an empty stomach for 3 days",
        ),
        utterance(
            "Once it is in tablet at night before sleeping",
            "And once it is in tablet at night before sleeping",
            "Once it is in table at night before sleeping",
        ),
        utterance("also dollar 650 if the fever comes back"),
        utterance("Avoid cold water and fried food", "a white cold water and fried food"),
        utterance("If the fever goes above 102 go to the hospital immediately"),
        utterance("Come back after 5 days for a checkup", "Come back after 5 days for the checkup"),
    )

    private val result = RuleExtractor.extractUtterances(run)

    // ---- bug 1: a drug name split into words

    @Test
    fun paracetamolSplitIntoParisAtMallIsRecoveredAndNeedsConfirming() {
        val m = result.medicines.first { it.key == "paracetamol" }
        assertEquals("Paris at Mall", m.name)
        assertEquals(Basis.SOUNDS_LIKE, m.basis)
        assertEquals(Confirmation.UNCONFIRMED, m.confirmation)
        assertEquals(3, m.timesPerDay)
        assertEquals(FoodRelation.AFTER_FOOD, m.foodRelation)
        assertEquals(5, m.durationDays)
        assertEquals(listOf(0), m.sourceLines)
    }

    // ---- bug 2: the diagnosis hid the dosing

    @Test
    fun theDiagnosisIsStillReadFromTheSameSentence() {
        assertEquals(listOf("a viral fever"), result.diagnosis.map { it.text })
        assertEquals(listOf(0), result.diagnosis.flatMap { it.sourceLines })
    }

    @Test
    fun aDosingInstructionBeginningItIsIsNotAdoptedAsADiagnosis() {
        // Line 2 ("Once it is in tablet at night before sleeping") once produced the diagnosis
        // "in tablet at night before sleeping".
        assertTrue(result.diagnosis.none { 2 in it.sourceLines })
        assertTrue(RuleExtractor.extract(listOf("Once it is in tablet at night before sleeping")).diagnosis.isEmpty())
    }

    @Test
    fun realDiagnosesAreStillRead() {
        assertEquals(listOf("a viral infection"), RuleExtractor.extract(listOf("It is a viral infection.")).diagnosis.map { it.text })
        assertEquals(listOf("a viral fever"), RuleExtractor.extract(listOf("You have a viral fever, nothing to worry about.")).diagnosis.map { it.text })
        assertEquals(listOf("वायरल बुखार"), RuleExtractor.extract(listOf("आपको वायरल बुखार है, घबराने की बात नहीं है।")).diagnosis.map { it.text })
    }

    @Test
    fun aDiagnosisCitingALineDoesNotHideItsDosingWhenNoMedicineIsTiedToIt() {
        // Rejecting the paracetamol guess leaves line 0 with a diagnosis and dosing but no medicine.
        val i = result.medicines.indexOfFirst { it.key == "paracetamol" }
        val after = result.updateMedicine(i) { it.rejected() }
        assertTrue(0 in after.unnamedDosing.map { it.index })
    }

    // ---- bug 3: "throw medicine"

    @Test
    fun anUnknownWordBesideMedicineIsUnconfirmedNotPresentedAsAFact() {
        val m = result.medicines.first { it.name.equals("throw", ignoreCase = true) }
        assertFalse(Lexicon.isKey(m.key))
        assertEquals(Confirmation.UNCONFIRMED, m.confirmation)
        assertTrue(m.candidates.isNotEmpty()) // so "Choose another" has something to offer
        assertEquals(1, m.timesPerDay)
        assertEquals(listOf(TimeOfDay.MORNING), m.timesOfDay)
        assertEquals(3, m.durationDays)
        assertEquals(listOf(1), m.sourceLines)
    }

    @Test
    fun nothingInTheRunIsEverCreatedAsConfirmed() {
        assertTrue(result.medicines.none { it.confirmation == Confirmation.CONFIRMED || it.isRejected })
        assertTrue(result.medicines.all { it.isUnconfirmed })
    }

    // ---- bug 4: "tablet at night" with the name lost

    @Test
    fun aTabletAtNightWithTheDrugNameLostIsShownAsUnnamedDosing() {
        assertEquals(listOf(2), result.unnamedDosing.map { it.index })
        assertEquals("Once it is in tablet at night before sleeping", result.unnamedDosing.single().text)
    }

    // ---- the rest of the run is read correctly

    @Test
    fun avoidWarningAndFollowUpAreRead() {
        assertEquals(listOf("cold water", "fried food"), result.avoid.map { it.text })
        assertEquals(listOf(5), result.warnings.flatMap { it.sourceLines })
        assertEquals(5, result.followUp?.inDays)
        assertEquals(listOf(6), result.followUp?.sourceLines)
    }

    @Test
    fun theBrandLineWithNoDosingWordsInventsNothing() {
        assertTrue(result.medicines.none { 3 in it.sourceLines })
    }

    @Test
    fun theNBestListsAddedNothingOnThisRunEither() {
        val topOnly = RuleExtractor.extract(run.map { it.text })
        assertEquals(topOnly.medicines, result.medicines)
    }

    @Test
    fun theReadAloudScriptSpeaksTheGuessesAsNotConfirmed() {
        val s = ReadAloudScript.build(result, Strings(result.language)).joinToString(" ")
        assertTrue(s.contains("Not confirmed: heard as Paris at Mall, possibly Paracetamol"))
        assertTrue(s.contains("Not confirmed: heard as throw. It is not known whether this is a medicine name."))
        assertTrue(s.contains("Dosing heard, medicine name unclear"))
        assertFalse(s.contains("Paracetamol:"))
    }

    @Test
    fun everyItemStillCitesARealLine() {
        assertNotNull(result.lines)
        result.medicines.forEach { m -> assertTrue(m.sourceLines.all { it in result.lines.indices }) }
    }

    // ---- the joined-words rule must not turn ordinary speech into a medicine

    @Test
    fun ordinaryPhrasesNearDosingWordsNeverBecomeAJoinedMedicine() {
        listOf(
            "take rest and drink water three times a day",
            "come back after five days for a checkup",
            "cold water and fried food after food",
            "take it in the morning on an empty stomach for 3 days",
            "one tablet at night before sleeping",
            "you have a viral fever take rest for 5 days",
            "eat plenty of fruit and rest in bed twice a day",
        ).forEach { line ->
            val found = RuleExtractor.extract(listOf(line)).medicines.filter { Lexicon.isKey(it.key) }
            assertTrue("'$line' produced ${found.map { it.name }}", found.isEmpty())
        }
    }

    @Test
    fun joiningNeedsDosingWordsBesideIt() {
        assertTrue(RuleExtractor.extract(listOf("my friend Paris at Mall told me about it")).medicines.isEmpty())
    }
}
