package com.packetloss.samjho.extract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneticTest {

    private fun keys(vararg lines: String) = RuleExtractor.extract(lines.toList()).medicines.map { it.key }

    // ---- the skeleton itself

    @Test
    fun dropsVowelsAndFoldsConfusableSounds() {
        assertEquals("prstml", Phonetic.skeleton("paracetamol"))
        assertEquals("prst", Phonetic.skeleton("pracite"))
        assertEquals("dl", Phonetic.skeleton("dolo"))
        assertEquals("dlr", Phonetic.skeleton("dollar"))
        assertEquals(Phonetic.skeleton("cetirizine"), Phonetic.skeleton("setirizin"))
    }

    @Test
    fun foldsPhAndThAndKAndQ() {
        assertEquals(Phonetic.skeleton("fone"), Phonetic.skeleton("phone"))
        assertEquals(Phonetic.skeleton("tin"), Phonetic.skeleton("thin"))
        assertEquals(Phonetic.skeleton("kat"), Phonetic.skeleton("qat"))
    }

    @Test
    fun devanagariAndLatinLandOnTheSameSkeleton() {
        assertEquals(
            Phonetic.skeleton(Normalize.text("paracetamol")),
            Phonetic.skeleton(Normalize.text("पैरासिटामोल")),
        )
    }

    // ---- the mishearings we actually saw

    @Test
    fun praciteIsParacetamolNextToADosageForm() {
        assertEquals(listOf("paracetamol"), keys("take the pracite tablet twice a day after food"))
    }

    @Test
    fun dollarIsDoloNextToADosageForm() {
        assertEquals(listOf("paracetamol"), keys("dollar tablet three times a day"))
    }

    @Test
    fun aMishearingIsAcceptedNextToDosingWordsEvenWithoutAFormWord() {
        assertEquals(listOf("paracetamol"), keys("pracite three times a day for five days"))
    }

    @Test
    fun theDoctorsOwnWordIsKeptNotReplacedWithTheLexiconName() {
        val m = RuleExtractor.extract(listOf("pracite tablet twice a day")).medicines.single()
        assertEquals("pracite", m.name)
        assertEquals(2, m.timesPerDay)
    }

    @Test
    fun aMangledHindiBrandNameStillMatches() {
        assertEquals(listOf("paracetamol"), keys("प्रेसिटामोल की गोली दिन में तीन बार"))
    }

    // ---- the words that must never become a medicine

    private val neverAMedicine = listOf("doctor", "please", "practice", "hospital", "patient", "pharmacy")

    @Test
    fun ordinaryWordsNeverMatchOnSoundAlone() {
        neverAMedicine.forEach { w -> assertNull("'$w' matched", Lexicon.matchPhonetic(Normalize.text(w))) }
    }

    @Test
    fun ordinaryWordsNeverBecomeAMedicineEvenInsideDosingSpeech() {
        neverAMedicine.forEach { w ->
            assertEquals(
                "'$w' became a medicine",
                emptyList<String>(),
                keys("$w, take one tablet twice a day after food"),
            )
        }
    }

    @Test
    fun doctorAndPleaseAndPracticeInARealisticConsultationFindNothing() {
        assertEquals(
            emptyList<String>(),
            keys(
                "Doctor, please take rest and practice good hygiene.",
                "Please come back after five days.",
            ),
        )
    }

    @Test
    fun longEverydayMedicalWordsNeverMatchEvenWithTheFirstLetterRelaxed() {
        // The relaxed tier ignores the first consonant on 5+ consonant skeletons, so it is the one
        // most exposed to coincidence: prove ordinary consultation vocabulary stays clear of it.
        listOf(
            "temperature", "medication", "prescription", "thermometer", "antibiotic", "stomach",
            "breathing", "immediately", "appointment", "symptoms", "diagnosis", "injection",
            "tomorrow", "afternoon", "vomiting", "dehydration", "pharmacist", "vaccination",
            "morning", "evening", "hospital", "emergency", "medicine",
        ).forEach { w -> assertNull("'$w' matched", Lexicon.matchPhonetic(Normalize.text(w))) }
    }

    @Test
    fun aPluralOfARealMedicineStillMatches() {
        assertEquals("paracetamol", Lexicon.matchPhonetic(Normalize.text("paracetamols")))
    }

    @Test
    fun thePlainSpokenConsultationWordsInTheDemosFindNothingExtra() {
        assertEquals(
            emptyList<String>(),
            keys("Please drink more water, take rest, and avoid the stomach infection risk in the evening."),
        )
    }

    @Test
    fun friedFoodIsNotIron() {
        // "fried" is one letter from ferrous (frs) and "food" reads as a dosing word.
        assertEquals(emptyList<String>(), keys("Avoid cold water and fried food."))
        assertNull(Lexicon.matchPhonetic(Normalize.text("fried")))
        assertNull(Lexicon.matchPhonetic(Normalize.text("pharmacy")))
        assertNull(Lexicon.matchPhonetic(Normalize.text("pain")))
    }

    @Test
    fun aTwoConsonantBrandDoesNotMatchShortEverydayWords() {
        assertNull(Lexicon.matchPhonetic(Normalize.text("dal")))
        assertNull(Lexicon.matchPhonetic(Normalize.text("do")))
        assertNull(Lexicon.matchPhonetic(Normalize.text("day")))
    }

    @Test
    fun aMishearingWithNoDosingContextIsIgnored() {
        assertEquals(emptyList<String>(), keys("my friend pracite told me about the weather"))
    }

    @Test
    fun theEnglishAndHindiDemosAreUnchanged() {
        assertTrue(keys("पैरासिटामोल की गोली दिन में तीन बार").contains("paracetamol"))
    }
}
