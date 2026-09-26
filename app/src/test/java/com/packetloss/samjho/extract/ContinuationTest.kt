package com.packetloss.samjho.extract

import com.packetloss.samjho.model.Continuation
import com.packetloss.samjho.model.Detail
import com.packetloss.samjho.model.FoodRelation
import com.packetloss.samjho.model.Medicine
import com.packetloss.samjho.model.TimeOfDay
import com.packetloss.samjho.share.SummaryBuilder
import com.packetloss.samjho.ui.Strings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Doctors name a medicine, pause, and give the food or timing as a sentence of its own. A bare dosing line attaches to
 * the medicine just named, under strict conditions, and always keeps its own line as its source.
 */
class ContinuationTest {

    private fun extract(vararg lines: String) = RuleExtractor.extract(lines.toList())

    private fun Medicine.only() = this

    private fun com.packetloss.samjho.model.Extraction.med(key: String) = medicines.first { it.key == key }

    // ---- the case that was found on the phone

    @Test
    fun theFoodInstructionOnItsOwnLineIsAttachedToParacetamol() {
        val e = extract("Take paracetamol for 3 days", "After food", "Come back after 5 days")
        val m = e.med("paracetamol")
        assertEquals(FoodRelation.AFTER_FOOD, m.foodRelation)
        assertEquals(3, m.durationDays) // the medicine's own line is untouched
        assertFalse(com.packetloss.samjho.model.MissingField.FOOD_RELATION in m.missing)
    }

    @Test
    fun theAttachedDetailKeepsItsOwnLineAndTheMedicinesOwnLineStaysItsOwn() {
        val m = extract("Take paracetamol for 3 days", "After food", "Come back after 5 days").med("paracetamol")
        assertEquals(listOf(0), m.sourceLines)
        assertEquals(listOf(Continuation(1, listOf(Detail.FOOD))), m.continuations)
        assertEquals(1, m.continuedFrom(Detail.FOOD))
        assertNull(m.continuedFrom(Detail.DURATION)) // "for 3 days" was said on the medicine's own line
        assertEquals(listOf(0, 1), m.allLines)
    }

    @Test
    fun theCardCanQuoteBothLines() {
        val e = extract("Take paracetamol for 3 days", "After food", "Come back after 5 days")
        assertEquals(listOf("Take paracetamol for 3 days", "After food"), e.quotes(e.med("paracetamol").allLines).map { it.text })
    }

    @Test
    fun theContinuationLineIsNoLongerLeftOverAsUnnamedDosing() {
        val e = extract("Take paracetamol", "twice a day after food")
        assertEquals(listOf(1), e.dosingLines.toList()) // it is a dosing line ...
        assertEquals(emptyList<Int>(), e.unnamedDosing.map { it.index }) // ... but it is now accounted for
        assertTrue(1 in e.coveredLines)
    }

    @Test
    fun theFollowUpLineIsStillTheFollowUpAndNotAContinuation() {
        val e = extract("Take paracetamol for 3 days", "After food", "Come back after 5 days")
        assertEquals(5, e.followUp?.inDays)
        assertEquals(listOf(2), e.followUp?.sourceLines)
        assertEquals(listOf(1), e.med("paracetamol").continuations.map { it.line })
    }

    // ---- each kind of detail

    @Test
    fun aDurationAFrequencyATimeAndADoseCanEachArriveOnALaterLine() {
        val m = extract("Take paracetamol", "three times a day", "for five days", "in the morning", "one tablet").med("paracetamol")
        assertEquals(3, m.timesPerDay)
        assertEquals(5, m.durationDays)
        assertEquals(listOf(TimeOfDay.MORNING), m.timesOfDay)
        assertEquals(1, m.doseCount)
        assertEquals(listOf(1, 2, 3, 4), m.continuations.map { it.line })
    }

    @Test
    fun eachContinuationLineIsCitedSeparately() {
        val m = extract("Take paracetamol", "three times a day", "for five days", "after food").med("paracetamol")
        assertEquals(1, m.continuedFrom(Detail.TIMES_PER_DAY))
        assertEquals(2, m.continuedFrom(Detail.DURATION))
        assertEquals(3, m.continuedFrom(Detail.FOOD))
    }

    @Test
    fun oneLineCarryingSeveralDetailsCitesAllOfThem() {
        val m = extract("Take paracetamol", "twice a day after food for three days").med("paracetamol")
        assertEquals(listOf(Continuation(1, listOf(Detail.TIMES_PER_DAY, Detail.FOOD, Detail.DURATION))), m.continuations)
    }

    @Test
    fun aFillerWordOrTwoDoesNotStopALineFromAttaching() {
        val m = extract("Take paracetamol", "and you should take it after food").med("paracetamol")
        assertEquals(FoodRelation.AFTER_FOOD, m.foodRelation)
    }

    @Test
    fun hindiWorksTheSameWay() {
        val e = extract("पैरासिटामोल तीन दिन तक लें", "खाने के बाद")
        val m = e.med("paracetamol")
        assertEquals(FoodRelation.AFTER_FOOD, m.foodRelation)
        assertEquals(3, m.durationDays)
        assertEquals(listOf(Continuation(1, listOf(Detail.FOOD))), m.continuations)
    }

    // ---- condition 1: the line names no medicine of its own

    @Test
    fun aLineThatNamesAnotherMedicineIsNotAContinuation() {
        val e = extract("Take paracetamol", "Take azithromycin after food")
        assertNull(e.med("paracetamol").foodRelation)
        assertEquals(emptyList<Continuation>(), e.med("paracetamol").continuations)
        assertEquals(FoodRelation.AFTER_FOOD, e.med("azithromycin").foodRelation) // its own line, its own detail
        assertEquals(emptyList<Continuation>(), e.med("azithromycin").continuations)
    }

    @Test
    fun aLineNamingTwoMedicinesIsNotSomethingToAttachTo() {
        val e = extract("Take paracetamol and azithromycin", "after food")
        assertNull(e.med("paracetamol").foodRelation)
        assertNull(e.med("azithromycin").foodRelation)
    }

    // ---- condition 2: nothing but dosing details and connectors

    @Test
    fun aLineWithAnythingElseInItDoesNotAttach() {
        for (other in listOf(
            "The tablet is white, take it after food",
            "Namaste, after food",
            "I have had fever, after food",
            "Kenny Risen after food",
            "You have a viral infection after food",
        )) {
            val m = extract("Take paracetamol", other).med("paracetamol")
            assertNull("'$other' must not attach", m.foodRelation)
            assertEquals(emptyList<Continuation>(), m.continuations)
        }
    }

    @Test
    fun anUnnamedDosingLineForADifferentMedicineIsNotSwallowed() {
        // The real "thrombison" line: dosing words, but also a word that is not a dosing word.
        val e = extract("Take the paracetamol tablet three times a day", "Is it thrombison once a day in the morning on an empty stomach for 3 days")
        val m = e.med("paracetamol")
        assertEquals(emptyList<TimeOfDay>(), m.timesOfDay)
        assertNull(m.foodRelation)
        assertNull(m.durationDays)
        assertEquals(listOf(1), e.unnamedDosing.map { it.index }) // still shown as dosing whose medicine was lost
    }

    @Test
    fun aLineWithOtherContentEndsTheAssociationSoALaterBareLineDoesNotReachBack() {
        val m = extract("Take paracetamol", "It helps with the pain", "after food").med("paracetamol")
        assertNull(m.foodRelation)
    }

    @Test
    fun fillerOnlyLinesNeitherAttachNorEndTheAssociation() {
        val m = extract("Take paracetamol", "okay", "after food").med("paracetamol")
        assertEquals(FoodRelation.AFTER_FOOD, m.foodRelation)
        assertEquals(1 + 1, m.continuations.single().line)
    }

    // ---- condition 3: close enough

    @Test
    fun aContinuationTwoLinesAfterTheMedicineStillAttaches() {
        assertEquals(FoodRelation.AFTER_FOOD, extract("Take paracetamol", "okay", "after food").med("paracetamol").foodRelation)
    }

    @Test
    fun aContinuationTooFarFromAnyMedicineDoesNotAttach() {
        val e = extract("Take paracetamol", "okay", "okay", "okay", "after food")
        assertNull(e.med("paracetamol").foodRelation)
        assertEquals(emptyList<Continuation>(), e.med("paracetamol").continuations)
    }

    @Test
    fun aContinuationWithNoMedicineAtAllBeforeItAttachesToNothing() {
        val e = extract("Namaste doctor", "after food")
        assertEquals(emptyList<Medicine>(), e.medicines)
    }

    @Test
    fun theWindowMovesOnWithEachContinuationSoAShortChainStillWorks() {
        val m = extract("Take paracetamol", "three times a day", "for five days", "after food").med("paracetamol")
        assertEquals(listOf(1, 2, 3), m.continuations.map { it.line })
    }

    // ---- condition 4: another medicine in between

    @Test
    fun aContinuationNeverReachesBackPastAnotherMedicine() {
        val e = extract("Take paracetamol three times a day", "And azithromycin in the morning", "for five days")
        val para = e.med("paracetamol")
        assertNull(para.durationDays) // the earlier medicine gets nothing
        assertEquals(emptyList<Continuation>(), para.continuations)
        // the most recent medicine is the one it belongs to
        assertEquals(5, e.med("azithromycin").durationDays)
        assertEquals(2, e.med("azithromycin").continuedFrom(Detail.DURATION))
    }

    @Test
    fun withAnotherMedicineInBetweenAndItsOwnValueAlreadySetNothingAttachesAnywhere() {
        val e = extract("Take paracetamol three times a day", "And azithromycin in the morning for three days", "for five days")
        assertNull(e.med("paracetamol").durationDays)
        assertEquals(3, e.med("azithromycin").durationDays)
        assertEquals(emptyList<Continuation>(), e.med("paracetamol").continuations + e.med("azithromycin").continuations)
    }

    // ---- condition 4: never overwrite

    @Test
    fun aContinuationNeverOverwritesAFoodInstructionTheDoctorAlreadyGave() {
        val m = extract("Take paracetamol after food", "before food").med("paracetamol")
        assertEquals(FoodRelation.AFTER_FOOD, m.foodRelation)
        assertEquals(emptyList<Continuation>(), m.continuations)
    }

    @Test
    fun aContinuationNeverOverwritesADuration() {
        val m = extract("Take paracetamol for 3 days", "for 5 days").med("paracetamol")
        assertEquals(3, m.durationDays)
        assertEquals(emptyList<Continuation>(), m.continuations)
    }

    @Test
    fun aLineThatContradictsTheMedicineIsLeftAloneNotHalfApplied() {
        // "twice a day" clashes with the three times already said, so the food in the same line is not taken either.
        val m = extract("Take paracetamol three times a day", "twice a day after food").med("paracetamol")
        assertEquals(3, m.timesPerDay)
        assertNull(m.foodRelation)
        assertEquals(emptyList<Continuation>(), m.continuations)
    }

    @Test
    fun aDetailThatIsStillMissingCanBeFilledByALaterLineWhileTheOthersStayAsSaid() {
        val m = extract("Take paracetamol three times a day for 3 days", "after food").med("paracetamol")
        assertEquals(3, m.timesPerDay)
        assertEquals(3, m.durationDays)
        assertEquals(FoodRelation.AFTER_FOOD, m.foodRelation)
    }

    // ---- condition 5: the other categories win

    @Test
    fun anAvoidLineIsNotSwallowedAsAContinuation() {
        val e = extract("Take paracetamol", "Avoid cold water")
        assertEquals(listOf("cold water"), e.avoid.map { it.text })
        assertEquals(emptyList<Continuation>(), e.med("paracetamol").continuations)
    }

    @Test
    fun anAvoidLineThatMentionsFoodDoesNotGiveTheMedicineAFoodInstruction() {
        val e = extract("Take paracetamol", "Avoid milk after food")
        assertTrue(e.avoid.isNotEmpty())
        assertNull(e.med("paracetamol").foodRelation)
    }

    @Test
    fun aWarningLineIsNotSwallowedAsAContinuation() {
        val e = extract("Take paracetamol", "If the fever comes back after food go to the hospital immediately")
        assertEquals(1, e.warnings.size)
        assertNull(e.med("paracetamol").foodRelation)
        assertEquals(emptyList<Continuation>(), e.med("paracetamol").continuations)
    }

    @Test
    fun aDiagnosisLineIsNotSwallowedAsAContinuation() {
        val e = extract("Take paracetamol", "You have a viral fever")
        assertEquals(1, e.diagnosis.size)
        assertEquals(emptyList<Continuation>(), e.med("paracetamol").continuations)
    }

    @Test
    fun aFollowUpLineIsNotSwallowedAsAContinuation() {
        val e = extract("Take paracetamol", "Come back after five days")
        assertEquals(5, e.followUp?.inDays)
        assertNull(e.med("paracetamol").durationDays)
        assertEquals(emptyList<Continuation>(), e.med("paracetamol").continuations)
    }

    @Test
    fun aCategoryLineBetweenTheMedicineAndABareLineMeansTheDoctorMovedOn() {
        val m = extract("Take paracetamol", "Avoid cold water", "after food").med("paracetamol")
        assertNull(m.foodRelation)
    }

    // ---- shown honestly

    @Test
    fun theSharedSummaryMarksADetailSaidInASeparateSentenceAndKeepsTheRest() {
        val e = extract("Take paracetamol for 3 days", "After food", "Come back after 5 days")
        val entry = SummaryBuilder.build(e, Strings(e.language), "27 Sep 2026").sections.first { it.title == "Medicines" }.entries.single()
        assertTrue("After food" in entry.details && "For 3 days" in entry.details)
        assertTrue(entry.note!!.contains("Said in a separate sentence: before or after food"))
        assertFalse(entry.note!!.contains("Not mentioned: before or after food")) // no longer claimed as missing
    }

    @Test
    fun aMedicineWithNoContinuationsIsSharedExactlyAsBefore() {
        val e = extract("Take paracetamol three times a day after food for five days")
        val entry = SummaryBuilder.build(e, Strings(e.language), "d").sections.first { it.title == "Medicines" }.entries.single()
        assertFalse(entry.note.orEmpty().contains("separate sentence"))
    }

    // ---- what it must not disturb

    @Test
    fun aMedicineMentionedAgainLaterStillMergesTheWayItAlwaysDid() {
        val m = extract("Take paracetamol", "Avoid cold water", "Paracetamol after food for five days").med("paracetamol")
        assertEquals(FoodRelation.AFTER_FOOD, m.foodRelation)
        assertEquals(5, m.durationDays)
        assertEquals(listOf(0, 2), m.sourceLines)
        assertEquals(emptyList<Continuation>(), m.continuations) // named again: that is its own line, not a continuation
    }

    @Test
    fun aSingleLineConsultationIsUntouched() {
        val m = extract("Take paracetamol three times a day after food for five days").med("paracetamol")
        assertEquals(emptyList<Continuation>(), m.continuations)
        assertEquals(listOf(0), m.allLines)
    }

    @Test
    fun ifThePatientDismissesTheMedicineItsContinuationLineIsAccountedForAsDosingAgain() {
        // A dismissed medicine stops speaking for its lines, exactly as before: dosing nobody owns is shown as unnamed.
        val e = extract("Take paracetamol", "twice a day after food")
        assertEquals(emptyList<Int>(), e.unnamedDosing.map { it.index })
        val dismissed = e.updateMedicine(0) { it.rejected() }
        assertEquals(listOf(1), dismissed.unnamedDosing.map { it.index })
    }
}
