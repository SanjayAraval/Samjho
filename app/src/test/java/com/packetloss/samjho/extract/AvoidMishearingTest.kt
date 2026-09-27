package com.packetloss.samjho.extract

import com.packetloss.samjho.model.Basis
import com.packetloss.samjho.model.Confirmation
import com.packetloss.samjho.model.FoodRelation
import com.packetloss.samjho.model.Hypothesis
import com.packetloss.samjho.model.Provenance
import com.packetloss.samjho.model.Utterance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Avoid" is easy for a recogniser to mishear ("Award cold water and fried food" was a real run, and the whole avoid
 * section was lost). The mishearings are ordinary words, so they must never swallow a sentence that uses them in
 * their own sense.
 */
class AvoidMishearingTest {

    private fun avoid(vararg lines: String) = RuleExtractor.extract(lines.toList()).avoid.map { it.text }

    // ---- the mishearings that are now read as "avoid"

    @Test
    fun awardIsAvoid() = assertEquals(listOf("cold water", "fried food"), avoid("Award cold water and fried food"))

    @Test
    fun aWordIsAvoid() = assertEquals(listOf("cold water", "fried food"), avoid("A word cold water and fried food"))

    @Test
    fun aVoidIsAvoid() = assertEquals(listOf("cold water"), avoid("A void cold water"))

    @Test
    fun avoidsIsAvoid() = assertEquals(listOf("fried food"), avoid("Avoids fried food"))

    @Test
    fun aFillerBeforeItIsFine() {
        assertEquals(listOf("cold water"), avoid("Also award cold water"))
        assertEquals(listOf("sugar", "sweets"), avoid("And avoids sugar, sweets"))
    }

    @Test
    fun theItemsCiteTheirLineAndAreRulesItems() {
        val e = RuleExtractor.extract(listOf("You have a viral fever", "Award cold water and fried food"))
        assertEquals(listOf(listOf(1), listOf(1)), e.avoid.map { it.sourceLines })
        assertTrue(e.avoid.all { it.provenance == Provenance.RULES })
    }

    @Test
    fun theOriginalWordsStillWork() {
        assertEquals(listOf("cold water"), avoid("Avoid cold water"))
        assertEquals(listOf("fried food"), avoid("Don't eat fried food"))
    }

    // ---- the same words in another sense are left alone

    @Test
    fun anAwardInTheOrdinarySenseIsNotAvoid() {
        assertEquals(emptyList<String>(), avoid("Award winning hospital"))
        assertEquals(emptyList<String>(), avoid("He won an award for the best doctor"))
        assertEquals(emptyList<String>(), avoid("Awards for water conservation were given"))
        assertEquals(emptyList<String>(), avoid("Award the fried food a miss"))
        assertEquals(emptyList<String>(), avoid("Award me a certificate"))
    }

    @Test
    fun aWordOfSomethingIsNotAvoid() {
        assertEquals(emptyList<String>(), avoid("A word of caution about the fever"))
        assertEquals(emptyList<String>(), avoid("A word about fried food"))
        assertEquals(emptyList<String>(), avoid("A word on cold water"))
        assertEquals(emptyList<String>(), avoid("A void in the schedule this week"))
    }

    @Test
    fun onlyAtTheStartOfTheSentence() {
        assertEquals(emptyList<String>(), avoid("The hospital got an award for cold water research"))
        assertEquals(emptyList<String>(), avoid("Take paracetamol award cold water"))
    }

    @Test
    fun thingsAfterItMustNameSomethingToAvoid() {
        assertEquals(emptyList<String>(), avoid("Award ceremony tomorrow"))
        assertEquals(emptyList<String>(), avoid("Avoids the doctor"))
    }

    @Test
    fun aWarningIsStillAWarning() {
        val e = RuleExtractor.extract(listOf("A word of caution if the fever goes above 102 go to the hospital immediately"))
        assertEquals(emptyList<String>(), e.avoid.map { it.text })
        assertEquals(1, e.warnings.size)
    }

    @Test
    fun aMedicineLineIsNotTurnedIntoAvoid() {
        val e = RuleExtractor.extract(listOf("Take paracetamol three times a day after food for 5 days"))
        assertEquals(emptyList<String>(), e.avoid.map { it.text })
        assertEquals(FoodRelation.AFTER_FOOD, e.medicines.single().foodRelation)
    }

    // ---- Hindi: the forms a recogniser gives when it drops the anusvara

    @Test
    fun hindiDroppedAnusvaraAndInformalForms() {
        assertEquals(listOf("ठंडा पानी"), avoid("ठंडा पानी मत खाए"))
        assertEquals(listOf("तला हुआ खाना"), avoid("तला हुआ खाना मत खाओ"))
        assertEquals(listOf("ठंडा पानी"), avoid("ठंडा पानी मत पिए"))
        assertTrue(avoid("तली चीज़ों से बचे").isNotEmpty())
    }

    @Test
    fun hindiLeftOverIsNotAvoid() {
        assertEquals(emptyList<String>(), avoid("दवा से बचे हुए पैसे वापस मिलेंगे"))
    }

    @Test
    fun theOriginalHindiFormsStillWork() {
        assertEquals(listOf("ठंडा पानी", "तला हुआ खाना"), avoid("ठंडा पानी और तला हुआ खाना मत खाना"))
    }
}

/**
 * The live run that lost its avoid section, as the SamjhoSpeech log recorded it. The recogniser heard "Avoid" as
 * "Award"; a cetirizine line came out as "Once it resin at night before sleeping".
 */
class RealAwardRunTest {

    private fun u(vararg raw: String) = Utterance(raw[0], raw.map { Hypothesis(it) })

    private val run = listOf(
        u("You have a viral fever nothing to worry about"),
        u(
            "Take paracetamol 1 tablet three times a day after food for 5 days",
            "Take paracetamol one tablet three times a day after food for 5 days",
        ),
        u("One tablet in the morning on an empty stomach for 3 days"),
        u("Take Panther Brazil before breakfast", "Take pantoprazole before breakfast"),
        u("Once it resin at night before sleeping", "Once it resin at night before sleeping/"),
        u("Award cold water and fried food"),
        u(
            "If the fever goes above 102 or you have difficulty breathing go to the hospital immediately",
            "If the fever goes above 102 or you have difficulty breathing go to the hospital immediately/",
        ),
        u("Come back after 5 days"),
    )

    private val e = RuleExtractor.extractUtterances(run)

    @Test
    fun theAvoidSectionIsNoLongerLost() {
        assertEquals(listOf("cold water", "fried food"), e.avoid.map { it.text })
        assertEquals(listOf(listOf(5), listOf(5)), e.avoid.map { it.sourceLines })
        assertTrue(com.packetloss.samjho.model.MissingSection.AVOID !in e.missingSections)
    }

    @Test
    fun theRestOfTheRunIsUnchanged() {
        val paracetamol = e.medicines.first { it.key == "paracetamol" }
        assertEquals(Basis.HEARD, paracetamol.basis)
        assertEquals(3, paracetamol.timesPerDay)
        assertEquals(5, paracetamol.durationDays)
        assertEquals(1, e.warnings.size)
        assertEquals(5, e.followUp?.inDays)
        // The morning dosing whose medicine name was never heard is shown as heard, not given to another medicine.
        assertEquals(listOf(2), e.unnamedDosing.map { it.index })
    }

    @Test
    fun theResinCardIsUnconfirmedAndCetirizineIsOnItsChooseAnotherList() {
        val resin = e.medicines.first { it.name == "resin" }
        assertEquals(Confirmation.UNCONFIRMED, resin.confirmation)
        assertTrue("cetirizine" in resin.candidates)
    }
}
