package com.packetloss.samjho.scan

import com.packetloss.samjho.demo.DemoConsultations
import com.packetloss.samjho.extract.RuleExtractor
import com.packetloss.samjho.model.Confirmation
import com.packetloss.samjho.model.Extraction
import com.packetloss.samjho.share.SummaryBuilder
import com.packetloss.samjho.ui.Strings
import com.packetloss.samjho.voice.ReadAloudScript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The summary, the read-aloud and the shared PDF all draw from the same merged result, so they must agree. */
class PaperRenderTest {

    private fun paper(key: String, readAs: String? = null) =
        ScanItem(0, readAs, key, key, emptyList(), ScanBasis.EXACT, Confirmation.CONFIRMED)

    private val asHeard = RuleExtractor.extract(DemoConsultations.ENGLISH_AS_HEARD.lines)
    private val clean = RuleExtractor.extract(DemoConsultations.ENGLISH.lines)

    private fun merged(base: Extraction, vararg keys: String) = PaperMerge.merge(base, keys.map { paper(it) }).extraction

    private fun shared(e: Extraction) = SummaryBuilder.build(e, Strings(e.language), "27 Sep 2026, 10:00")

    private fun read(e: Extraction) = ReadAloudScript.build(e, Strings(e.language))

    private fun medicinesOf(e: Extraction) = shared(e).sections.first { it.title == "Medicines" }.entries

    // ---- the shared summary (image and PDF)

    @Test
    fun theSharedSummaryListsTheConfirmedNameWithTheDoctorsDosingAndWhereTheNameCameFrom() {
        val e = merged(asHeard, "azithromycin")
        val entry = medicinesOf(e).single { it.headline == "Azithromycin" }
        assertEquals(listOf("Once a day", "Morning", "Empty stomach", "For 3 days"), entry.details)
        assertTrue(entry.note!!.contains("Confirmed from prescription"))
        assertTrue(entry.note!!.contains("Heard as “thrombison”"))
    }

    @Test
    fun theWordThatWasHeardIsNoLongerListedAsAnUnnamedDosingLineOnceThePaperNamedIt() {
        val before = shared(asHeard).sections.first { it.title.startsWith("Not confirmed") }.entries.map { it.headline }
        assertTrue(before.any { it.contains("thrombison") })
        val afterSections = shared(merged(asHeard, "azithromycin")).sections
        val notConfirmed = afterSections.firstOrNull { it.title.startsWith("Not confirmed") }?.entries.orEmpty()
        assertFalse(notConfirmed.any { it.headline.contains("thrombison") })
    }

    @Test
    fun aMedicineOnlyOnThePaperIsSharedWithNoTimingsAndMarkedNotSpoken() {
        val entry = medicinesOf(merged(clean, "metformin")).single { it.headline == "Metformin" }
        assertEquals(emptyList<String>(), entry.details)
        assertTrue(entry.note!!.startsWith("From prescription, not spoken"))
        assertTrue(entry.note!!.contains("Not mentioned: how many times a day"))
    }

    @Test
    fun aSpokenMedicineThePaperLacksIsSharedExactlyAsBefore() {
        val before = medicinesOf(clean).single { it.headline == "paracetamol" }
        val after = medicinesOf(merged(clean, "metformin")).single { it.headline == "paracetamol" }
        assertEquals(before, after)
    }

    @Test
    fun aNameConfirmedFromPaperIsListedAsAMedicineNotUnderNotConfirmed() {
        val e = merged(asHeard, "cetirizine")
        assertTrue(medicinesOf(e).any { it.headline == "Cetirizine" })
        val notConfirmed = shared(e).sections.first { it.title.startsWith("Not confirmed") }.entries
        assertFalse(notConfirmed.any { it.headline.contains("citrus") })
    }

    @Test
    fun theFooterAdmitsThePrescriptionOnlyWhenANameCameFromIt() {
        val plain = shared(clean).footer
        assertTrue(plain.contains("doctor's own words"))
        assertFalse(plain.contains("prescription"))
        val withPaper = shared(merged(clean, "metformin")).footer
        assertTrue(withPaper.contains("doctor's own words and the medicine names on the prescription"))
        assertTrue(withPaper.contains("gives no medical advice"))
    }

    @Test
    fun aDismissedPaperCardDoesNotChangeTheFooter() {
        val e = merged(clean, "metformin")
        val dismissed = e.updateMedicine(e.medicines.lastIndex) { it.rejected() }
        assertFalse(shared(dismissed).footer.contains("prescription"))
    }

    // ---- read-aloud

    @Test
    fun readAloudSaysTheConfirmedNameAndThatItCameFromThePrescription() {
        val spoken = read(merged(asHeard, "azithromycin"))
        val line = spoken.single { it.startsWith("Azithromycin") }
        assertTrue(line.startsWith("Azithromycin, confirmed from the prescription, heard as thrombison"))
        assertTrue(line.contains("once a day", ignoreCase = true))
    }

    @Test
    fun readAloudSaysAPaperOnlyMedicineWasNotMentionedByTheDoctorAndListsWhatIsMissing() {
        val line = read(merged(clean, "metformin")).single { it.startsWith("Metformin") }
        assertTrue(line.contains("from the prescription, not mentioned by the doctor"))
        assertTrue(line.contains("Not mentioned: how many times a day"))
    }

    @Test
    fun readAloudNoLongerCallsANamePaperConfirmedUnconfirmed() {
        val before = read(asHeard).joinToString(" ")
        assertTrue(before.contains("Not confirmed: heard as citrus"))
        val after = read(merged(asHeard, "cetirizine")).joinToString(" ")
        assertFalse(after.contains("Not confirmed: heard as citrus"))
        assertTrue(after.contains("Cetirizine, confirmed from the prescription, heard as citrus"))
    }

    @Test
    fun aPaperMergeNeverMakesReadAloudSpeakDosingThatTheDoctorDidNotGive() {
        val before = read(clean).filter { !it.startsWith("Metformin") }
        val after = read(merged(clean, "metformin")).filter { !it.startsWith("Metformin") }
        assertEquals(before, after)
    }
}
