package com.packetloss.samjho.scan

import com.packetloss.samjho.extract.Lexicon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanSearchTest {

    private fun keys(query: String, first: List<String> = emptyList()) = ScanSearch.filter(query, first).map { it.key }

    @Test
    fun withNothingTypedEveryMedicineInTheLexiconIsListed() {
        val all = keys("")
        assertEquals(Lexicon.keys().toSet(), all.toSet())
        assertEquals(Lexicon.keys().size, all.size)
    }

    @Test
    fun theFullListIsAlphabetical() {
        val labels = ScanSearch.filter("").map { it.label.lowercase() }
        assertEquals(labels.sorted(), labels)
    }

    @Test
    fun aCardsCloseMatchesComeFirstWhenChoosingAnother() {
        val list = keys("", first = listOf("metformin", "ibuprofen"))
        assertEquals(listOf("metformin", "ibuprofen"), list.take(2))
        assertEquals(Lexicon.keys().size, list.size)
    }

    @Test
    fun aFewLettersFindTheNameAtTheTop() {
        assertEquals("paracetamol", keys("para").first())
        assertEquals("metformin", keys("met").first())
        assertEquals("azithromycin", keys("AZI").first())
    }

    @Test
    fun aBrandNameLeadsToItsMedicine() {
        assertEquals("paracetamol", keys("dolo").first())
        assertEquals("paracetamol", keys("crocin").first())
    }

    @Test
    fun aMisspeltNameStillFindsIt() {
        assertTrue("paracetamol" in keys("parasetmol"))
        assertTrue("azithromycin" in keys("azithromicin"))
    }

    @Test
    fun aDevanagariNameFindsIt() {
        assertTrue("paracetamol" in keys("पैरासिटामोल"))
    }

    @Test
    fun anUnrelatedQueryFindsNothingRatherThanEverything() {
        assertEquals(emptyList<String>(), keys("xyzzy"))
    }

    @Test
    fun spacesAndCaseDoNotMatter() {
        assertEquals(keys("para cetamol"), keys("PARACETAMOL"))
    }
}
