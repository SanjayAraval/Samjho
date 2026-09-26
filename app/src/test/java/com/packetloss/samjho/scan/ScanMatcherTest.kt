package com.packetloss.samjho.scan

import com.packetloss.samjho.model.Confirmation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanMatcherTest {

    private fun keys(vararg lines: String) = ScanMatcher.match(lines.toList()).map { it.key }

    private fun only(vararg lines: String) = ScanMatcher.match(lines.toList()).single()

    // ---- clean printed text

    @Test
    fun aPrintedPrescriptionMatchesEachMedicineByItsExactName() {
        val items = ScanMatcher.match(
            listOf(
                "Dr. Anil Kumar MBBS",
                "Rx",
                "Tab. Paracetamol 500 mg 1-0-1 x 5 days",
                "Cap. Amoxicillin 500mg TDS",
                "Tab Cetirizine 10mg HS",
            ),
        )
        assertEquals(setOf("paracetamol", "amoxicillin", "cetirizine"), items.map { it.key }.toSet())
        assertTrue(items.all { it.basis == ScanBasis.EXACT })
    }

    @Test
    fun aBrandNameMatchesItsMedicineAndTheCardKeepsTheWordAsRead() {
        val item = only("Tab Dolo 650 twice daily")
        assertEquals("paracetamol", item.key)
        assertEquals("Dolo", item.readAs)
    }

    @Test
    fun theWordsAreShownExactlyAsTheCameraReadThemNotTidied() {
        assertEquals("PARACETAMOL", only("TAB PARACETAMOL 500").readAs)
    }

    @Test
    fun aMedicineRepeatedOnThePageIsOneCard() {
        assertEquals(listOf("paracetamol"), keys("Paracetamol 500 mg", "Tab Paracetamol after food", "Paracetamol"))
    }

    // ---- character-level OCR errors, the reason for the small edit distance

    @Test
    fun aLetterReadAsTwoLetters() {
        // "m" read as "rn" is the classic slip.
        val item = only("Tab Paracetarnol 500")
        assertEquals("paracetamol", item.key)
        assertEquals("Paracetarnol", item.readAs)
        assertEquals(Confirmation.UNCONFIRMED, item.confirmation)
    }

    @Test
    fun aDigitReadInsteadOfALetter() {
        assertEquals(listOf("amoxicillin"), keys("Cap Amox1cillin 500"))
        assertEquals(listOf("pantoprazole"), keys("Tab Pantoprazo1e 40"))
        assertEquals(listOf("cetirizine"), keys("Cetir1zine 10mg"))
    }

    @Test
    fun aLookAlikeLetterInTheMiddle() {
        assertEquals(listOf("cetirizine"), keys("Cetirizlne"))
        assertEquals(listOf("azithromycin"), keys("Azithromycln 500"))
        assertEquals(listOf("metformin"), keys("Metforrnin 500"))
    }

    @Test
    fun aNameSplitAcrossAGapIsJoined() {
        assertEquals(listOf("paracetamol"), keys("Tab Para cetamol 500"))
        assertEquals(listOf("amoxicillin"), keys("Cap Amoxi cillin"))
    }

    @Test
    fun aSlightlyDifferentSpellingStillPointsAtTheMedicine() {
        assertEquals(listOf("azithromycin"), keys("Azithromicin 500"))
        assertEquals(listOf("ciprofloxacin"), keys("Ciprofloxacine 500"))
    }

    @Test
    fun aSlipIsNotPassedOffAsAnExactMatch() {
        val item = only("Paracetarnol")
        assertTrue(item.basis == ScanBasis.SPELLING || item.basis == ScanBasis.SOUNDS_LIKE)
    }

    // ---- Devanagari

    @Test
    fun aDevanagariNameMatches() {
        assertEquals(listOf("paracetamol"), keys("पैरासिटामोल 500 मि.ग्रा."))
        assertEquals(listOf("cetirizine"), keys("सेटिरिजिन रात को"))
    }

    // ---- what must never happen

    @Test
    fun everydayPrescriptionWordsAreNeverMistakenForAMedicine() {
        val words = ScanFixtures.EVERYDAY_WORDS
        for (w in words) assertEquals("\"$w\" must not match a medicine", emptyList<String>(), keys(w))
    }

    @Test
    fun aWholeHeaderAndFooterOfOrdinaryTextMatchesNothing() {
        val lines = ScanFixtures.HEADER_AND_FOOTER
        assertEquals(emptyList<String>(), keys(*lines.toTypedArray()))
    }

    @Test
    fun unreadableHandwritingGarbageMatchesNothing() {
        assertEquals(emptyList<String>(), keys("Xlqpz mnbvw", "rtyu ~~ 1l|l", "Zzzxq wvvvb", "ooo  lll  iii"))
    }

    @Test
    fun nothingReadMeansNothingSuggested() {
        assertEquals(emptyList<ScanItem>(), ScanMatcher.match(emptyList()))
        assertEquals(emptyList<ScanItem>(), ScanMatcher.match(listOf("", "   ")))
    }

    @Test
    fun numbersAndDosesAloneNeverMatch() {
        assertEquals(emptyList<String>(), keys("500 mg", "1-0-1", "650mg", "10 ml", "x 5 days", "12/03/2026"))
    }

    @Test
    fun nothingTheCameraSuggestsIsEverConfirmed() {
        val items = ScanMatcher.match(listOf("Paracetamol", "Dolo 650", "Cetirizlne", "पैरासिटामोल", "Amox1cillin"))
        assertTrue(items.isNotEmpty())
        assertTrue(items.all { it.confirmation == Confirmation.UNCONFIRMED })
        assertFalse(items.any { it.basis == ScanBasis.PICKED })
    }

    // ---- choosing another

    @Test
    fun aSoundAlikeCardOffersItsCloseNeighboursFirstWhenChoosingAnother() {
        val item = only("Tab Cetirizlne")
        assertFalse(item.key in item.candidates)
        assertTrue(item.candidates.size <= 5)
    }

    // ---- the patient's own decisions

    @Test
    fun onlyThePatientsTapMovesACardOutOfUnconfirmed() {
        val item = only("Paracetarnol")
        assertEquals(Confirmation.CONFIRMED, item.confirmed().confirmation)
        assertEquals(Confirmation.REJECTED, item.rejected().confirmation)
    }

    @Test
    fun choosingAnotherConfirmsThePatientsPickAndUndoRestoresTheOriginalGuess() {
        val item = only("Paracetarnol")
        val chose = item.choosing("ibuprofen")
        assertEquals("ibuprofen", chose.key)
        assertEquals(Confirmation.CONFIRMED, chose.confirmation)
        val undone = chose.reopened()
        assertEquals("paracetamol", undone.key)
        assertEquals(Confirmation.UNCONFIRMED, undone.confirmation)
    }

    @Test
    fun aMedicinePickedFromTheListIsTheOnlyThingThatStartsConfirmed() {
        val p = ScanItem.picked(7, "metformin")
        assertEquals(Confirmation.CONFIRMED, p.confirmation)
        assertEquals(ScanBasis.PICKED, p.basis)
        assertEquals(null, p.readAs)
    }
}
