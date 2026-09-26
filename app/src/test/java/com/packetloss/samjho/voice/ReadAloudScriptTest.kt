package com.packetloss.samjho.voice

import com.packetloss.samjho.demo.DemoConsultations
import com.packetloss.samjho.extract.RuleExtractor
import com.packetloss.samjho.ui.Strings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Read-aloud may only restate what the result screen shows, and must never present a guess as fact. */
class ReadAloudScriptTest {

    private fun say(lines: List<String>, mutate: (com.packetloss.samjho.model.Extraction) -> com.packetloss.samjho.model.Extraction = { it }): String {
        val e = mutate(RuleExtractor.extract(lines))
        return ReadAloudScript.build(e, Strings(e.language)).joinToString(" ")
    }

    @Test
    fun theEnglishDemoIsReadWithEverySectionAndItsDetails() {
        val s = say(DemoConsultations.ENGLISH.lines)
        listOf(
            "paracetamol: 3 times a day, After food, For 5 days",
            "azithromycin: Morning, Empty stomach, For 3 days",
            "cetirizine: 1 tablet, Night",
            "cold water", "fried food", "go to the hospital immediately", "In 5 days",
            "Samjho only repeats what the doctor said",
        ).forEach { assertTrue("missing '$it' in: $s", s.contains(it)) }
    }

    @Test
    fun anUnconfirmedMedicineIsSpokenAsNotConfirmedNeverAsAFact() {
        val s = say(DemoConsultations.ENGLISH_AS_HEARD.lines)
        assertTrue(s.contains("Not confirmed: heard as parasite, possibly Paracetamol"))
        assertTrue(s.contains("Not confirmed: heard as citrus, possibly Cetirizine"))
        assertFalse(s.contains("Paracetamol:"))
        assertFalse(s.contains("Cetirizine:"))
    }

    @Test
    fun aConfirmedGuessIsSpokenUnderTheConfirmedName() {
        val s = say(DemoConsultations.ENGLISH_AS_HEARD.lines) { e ->
            e.updateMedicine(e.medicines.indexOfFirst { it.name == "parasite" }) { it.confirmed() }
        }
        assertTrue(s.contains("Paracetamol: 3 times a day"))
        assertFalse(s.contains("heard as parasite"))
    }

    @Test
    fun aDismissedMedicineIsNotSpokenAtAll() {
        val s = say(DemoConsultations.ENGLISH_AS_HEARD.lines) { e ->
            e.updateMedicine(e.medicines.indexOfFirst { it.name == "citrus" }) { it.rejected() }
        }
        assertFalse(s.contains("citrus"))
        assertFalse(s.contains("Cetirizine"))
    }

    @Test
    fun theUnnamedDosingLineIsReadAsTheLineItIsWithNoMedicineNamed() {
        val s = say(DemoConsultations.ENGLISH_AS_HEARD.lines)
        assertTrue(s.contains("Dosing heard, medicine name unclear"))
        assertTrue(s.contains("Is it thrombison once a day in the morning on an empty stomach for 3 days"))
        assertFalse(s.contains("Azithromycin"))
    }

    @Test
    fun aBrandWithNothingSaidAboutItIsReadAsNotMentionedNotWithASchedule() {
        val s = say(DemoConsultations.ENGLISH_AS_HEARD.lines)
        assertTrue(s.contains("Dolo. Not mentioned: how many times a day"))
    }

    @Test
    fun theHindiDemoIsReadInHindi() {
        val s = say(DemoConsultations.HINDI.lines)
        assertTrue(s.contains("पैरासिटामोल"))
        assertTrue(s.contains("दिन में 3 बार"))
        assertTrue(s.contains("इनसे बचें"))
        assertFalse(s.contains("Medicines"))
    }

    @Test
    fun aNothingFoundConsultationOnlyIntroducesAndSaysWhatWasNotMentioned() {
        val e = RuleExtractor.extract(listOf("आज मौसम अच्छा है।"))
        val out = ReadAloudScript.build(e, Strings(e.language))
        assertEquals(out.first(), Strings(e.language).readIntro)
        assertTrue(out.any { it.startsWith(Strings(e.language).notMentioned) })
        assertTrue(out.none { it.contains("दिन में") })
    }

    @Test
    fun everySentenceIsShortEnoughForTheSpeechEngine() {
        val e = RuleExtractor.extract(DemoConsultations.ENGLISH_AS_HEARD.lines)
        ReadAloudScript.build(e, Strings(e.language)).forEach { assertTrue(it.length < 3900) }
    }
}
