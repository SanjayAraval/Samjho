package com.packetloss.samjho.extract

import com.packetloss.samjho.demo.DemoConsultations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Dosing heard, medicine name unclear" must only ever show a doctor's instruction. Found on the
 * phone: the English demo's opening line, the PATIENT saying "I have had fever ... for two days",
 * was listed there because a duration alone counted as dosing.
 */
class UnnamedDosingTest {

    private fun unnamed(vararg lines: String) = RuleExtractor.extract(lines.toList()).unnamedDosing.map { it.text }

    // ---- what must not be flagged

    @Test
    fun aPatientsSymptomDurationIsNotDosing() {
        assertFalse(RuleExtractor.hasDosing("Namaste doctor, I have had fever and a sore throat for two days."))
    }

    @Test
    fun aDurationOnItsOwnIsNotDosing() {
        assertFalse(RuleExtractor.hasDosing("for five days"))
        assertFalse(RuleExtractor.hasDosing("the fever has been there for 3 days"))
    }

    @Test
    fun aTimeOfDayOnItsOwnIsNotDosing() {
        assertFalse(RuleExtractor.hasDosing("the cough gets worse at night"))
        assertFalse(RuleExtractor.hasDosing("come in the morning"))
    }

    @Test
    fun firstPersonSpeechIsNeverDosing() {
        assertFalse(RuleExtractor.hasDosing("I take it after food three times a day"))
        assertFalse(RuleExtractor.hasDosing("my cough is worse at night for 3 days"))
        assertFalse(RuleExtractor.hasDosing("मुझे दो दिन से रात को बुखार है"))
    }

    @Test
    fun theGreetingLineOfBothBakedInDemosIsNotFlagged() {
        assertTrue(RuleExtractor.extract(DemoConsultations.ENGLISH.lines).unnamedDosing.isEmpty())
        assertTrue(RuleExtractor.extract(DemoConsultations.HINDI.lines).unnamedDosing.isEmpty())
    }

    @Test
    fun aFollowUpOrWarningLineIsNotFlagged() {
        assertEquals(emptyList<String>(), unnamed("Come back after 5 days", "If the fever goes above 102 go to the hospital immediately"))
    }

    // ---- what must still be flagged (the real line that used to vanish)

    @Test
    fun aFrequencyAloneIsDosing() {
        assertTrue(RuleExtractor.hasDosing("Is it thrombison once a day"))
        assertTrue(RuleExtractor.hasDosing("at three times a day after vote for five days"))
    }

    @Test
    fun twoKindsOfDetailAreDosing() {
        assertTrue(RuleExtractor.hasDosing("and the thromison in the morning on an empty stomach for 3 days"))
        assertTrue(RuleExtractor.hasDosing("egg 1 tablet of citrus in at night"))
        assertTrue(RuleExtractor.hasDosing("take it after food for 5 days"))
    }

    @Test
    fun theRealThrombisonLineIsStillTheOnlyUnnamedLineInTheAsHeardDemo() {
        val r = RuleExtractor.extract(DemoConsultations.ENGLISH_AS_HEARD.lines)
        assertEquals(
            listOf("Is it thrombison once a day in the morning on an empty stomach for 3 days"),
            r.unnamedDosing.map { it.text },
        )
    }

    @Test
    fun theLanguageModelIsNeverAskedAboutThePatientsSymptomLine() {
        val r = RuleExtractor.extract(DemoConsultations.ENGLISH.lines)
        assertTrue(NameRepair.targets(r).isEmpty())
    }
}
