package com.packetloss.samjho.extract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NormalizeAndLexiconTest {

    @Test
    fun foldsChandrabinduOntoAnusvara() {
        assertEquals(Normalize.text("पाँच"), Normalize.text("पांच"))
    }

    @Test
    fun convertsDevanagariDigits() {
        assertEquals("5 din", Normalize.text("५ din"))
    }

    @Test
    fun keepsCommasBecauseTheySeparateAvoidLists() {
        assertEquals("a, b", Normalize.text("A, B."))
    }

    @Test
    fun readsNumbersInBothLanguages() {
        assertEquals(5, Numbers.parse("पाँच"))
        assertEquals(5, Numbers.parse("five"))
        assertEquals(5, Numbers.parse("5"))
        assertEquals(2, Numbers.parse("twice"))
        assertNull(Numbers.parse("बुखार"))
    }

    @Test
    fun matchesAMedicineDespiteASmallSpeechRecognitionSlip() {
        assertEquals("paracetamol", Lexicon.match(Normalize.text("पेरासिटामोल")))
        assertEquals("cetirizine", Lexicon.match(Normalize.text("cetrizine")))
    }

    @Test
    fun doesNotInventAMedicineFromAnUnrelatedWord() {
        assertNull(Lexicon.match(Normalize.text("नमस्ते")))
        assertNull(Lexicon.match(Normalize.text("hospital")))
        assertNull(Lexicon.match(Normalize.text("गले")))
    }

    @Test
    fun anUnknownBrandIsStillCaughtByThePhraseRule() {
        val result = RuleExtractor.extract(listOf("जिफी की गोली दिन में दो बार खाने के बाद, चार दिन तक।"))
        assertEquals(listOf("जिफी"), result.medicines.map { it.name })
        assertEquals(2, result.medicines.single().timesPerDay)
        assertEquals(4, result.medicines.single().durationDays)
    }

    @Test
    fun aSymptomBeforeTheWordMedicineIsNotTreatedAsADrug() {
        val result = RuleExtractor.extract(listOf("बुखार की दवा लेनी है।"))
        assertEquals(emptyList<String>(), result.medicines.map { it.name })
    }
}
