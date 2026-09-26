package com.packetloss.samjho.share

import com.packetloss.samjho.demo.DemoConsultations
import com.packetloss.samjho.extract.RuleExtractor
import com.packetloss.samjho.model.Extraction
import com.packetloss.samjho.ui.Strings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared summary goes to someone who does not have the app and cannot tap Yes or No, so it is
 * stricter than the result screen: a guess is never listed as a medicine.
 */
class SummaryBuilderTest {

    private fun doc(e: Extraction) = SummaryBuilder.build(e, Strings(e.language), "26 Sep 2026, 18:00")

    private fun doc(lines: List<String>, change: (Extraction) -> Extraction = { it }) = doc(change(RuleExtractor.extract(lines)))

    private fun SummaryDocument.section(title: String) = sections.firstOrNull { it.title == title }

    private fun SummaryDocument.everything(): String = buildString {
        append(title).append(' ').append(dateLine).append(' ').append(footer)
        sections.forEach { s ->
            append(' ').append(s.title)
            s.entries.forEach { e -> append(' ').append(e.headline).append(' ').append(e.details.joinToString(" ")).append(' ').append(e.note.orEmpty()) }
        }
    }

    // ---- the clean case

    @Test
    fun theEnglishDemoHasEverySectionTheUserAskedFor() {
        val d = doc(DemoConsultations.ENGLISH.lines)
        assertEquals(
            listOf("What the doctor said it is", "Medicines", "Things to avoid", "Warning signs", "Next visit"),
            d.sections.map { it.title },
        )
        assertEquals("Consultation summary", d.title)
    }

    @Test
    fun medicinesCarryTheirTimings() {
        val meds = doc(DemoConsultations.ENGLISH.lines).section("Medicines")!!.entries
        assertEquals(listOf("paracetamol", "azithromycin", "cetirizine"), meds.map { it.headline })
        assertEquals(listOf("3 times a day", "After food", "For 5 days"), meds[0].details)
        assertEquals(listOf("Morning", "Empty stomach", "For 3 days"), meds[1].details)
        assertEquals(listOf("1 tablet", "Night"), meds[2].details)
    }

    @Test
    fun whatTheDoctorDidNotSayIsStillMarkedNotMentioned() {
        val meds = doc(DemoConsultations.ENGLISH.lines).section("Medicines")!!.entries
        assertEquals("Not mentioned: what time of day", meds[0].note)
        assertTrue(meds[2].note!!.startsWith("Not mentioned: how many times a day"))
    }

    @Test
    fun avoidWarningAndNextVisitAreRestatedInTheirOwnTones() {
        val d = doc(DemoConsultations.ENGLISH.lines)
        assertEquals(listOf("cold water", "fried food"), d.section("Things to avoid")!!.entries.map { it.headline })
        assertEquals(Tone.AVOID, d.section("Things to avoid")!!.tone)
        assertEquals(Tone.WARNING, d.section("Warning signs")!!.tone)
        assertTrue(d.section("Warning signs")!!.entries.single().headline.contains("go to the hospital immediately"))
        assertEquals("In 5 days", d.section("Next visit")!!.entries.single().headline)
    }

    @Test
    fun theFooterSaysItWasGeneratedFromTheDoctorsOwnWords() {
        val d = doc(DemoConsultations.ENGLISH.lines)
        assertTrue(d.footer.contains("doctor's own words"))
        assertTrue(d.footer.contains("gives no medical advice"))
    }

    // ---- the rule that matters most: a guess is never a medicine

    @Test
    fun anUnconfirmedGuessIsNeverListedAsAMedicine() {
        val d = doc(DemoConsultations.ENGLISH_AS_HEARD.lines)
        val listed = d.section("Medicines")!!.entries.map { it.headline }
        assertEquals(listOf("Dolo"), listed) // only the name that was heard outright
        assertFalse(listed.any { it.contains("Paracetamol", ignoreCase = true) || it.contains("Cetirizine", ignoreCase = true) })
    }

    @Test
    fun anUnconfirmedGuessAppearsUnderNotConfirmedWithTheWordsThatWereHeard() {
        val d = doc(DemoConsultations.ENGLISH_AS_HEARD.lines)
        val box = d.section("Not confirmed: please check")!!
        assertEquals(Tone.NOTICE, box.tone)
        val heard = box.entries.first { it.headline.contains("parasite") }
        assertTrue(heard.headline.startsWith("Heard as “parasite”"))
        assertTrue(heard.headline.contains("Possibly: Paracetamol"))
        assertEquals("Ask your doctor or pharmacist before treating this as the right medicine.", heard.note)
        assertEquals(listOf("3 times a day", "After food", "For 5 days"), heard.details)
    }

    @Test
    fun aWordThatIsNotEvenAKnownMedicineIsNotOfferedAsOne() {
        val d = doc(DemoConsultations.ENGLISH_LIVE_READ.lines)
        val throwEntry = d.section("Not confirmed: please check")!!.entries.first { it.headline.contains("throw") }
        assertTrue(throwEntry.headline.contains("Is this a medicine name?"))
        assertFalse(throwEntry.headline.contains("Possibly"))
        assertFalse(d.section("Medicines")?.entries.orEmpty().any { it.headline.contains("throw") })
    }

    @Test
    fun theUnnamedDosingLineIsSharedAsTheLineAsHeardWithNoMedicineNamed() {
        val d = doc(DemoConsultations.ENGLISH_AS_HEARD.lines)
        val line = d.section("Not confirmed: please check")!!.entries.first { it.headline.startsWith("Is it thrombison") }
        assertEquals("Is it thrombison once a day in the morning on an empty stomach for 3 days", line.headline)
        assertFalse(d.everything().contains("Azithromycin"))
    }

    @Test
    fun aDismissedMedicineIsLeftOutEntirely() {
        val d = doc(DemoConsultations.ENGLISH_AS_HEARD.lines) { e ->
            e.updateMedicine(e.medicines.indexOfFirst { it.name == "parasite" }) { it.rejected() }
        }
        assertFalse(d.everything().contains("Heard as “parasite”"))
        assertFalse(d.everything().contains("Possibly: Paracetamol"))
    }

    @Test
    fun aConfirmedGuessIsListedUnderItsConfirmedNameWithItsTimings() {
        val d = doc(DemoConsultations.ENGLISH_AS_HEARD.lines) { e ->
            e.updateMedicine(e.medicines.indexOfFirst { it.name == "parasite" }) { it.confirmed() }
        }
        val listed = d.section("Medicines")!!.entries
        assertTrue(listed.any { it.headline == "Paracetamol" && it.details.contains("3 times a day") })
        assertTrue(d.section("Not confirmed: please check")!!.entries.none { it.headline.contains("parasite") })
    }

    // ---- nothing is invented, and the language follows the consult

    @Test
    fun nothingAppearsThatTheResultScreenDoesNotShow() {
        val e = RuleExtractor.extract(DemoConsultations.ENGLISH.lines)
        val text = doc(e).everything()
        // No medicine outside the three the doctor named, and no "possibly" guesses on a clean consult.
        assertFalse(text.contains("Possibly"))
        listOf("ibuprofen", "amoxicillin", "metformin", "Dolo").forEach { assertFalse(it, text.contains(it, ignoreCase = true)) }
    }

    @Test
    fun aConsultationWithNothingInItSharesOnlyWhatWasNotMentioned() {
        val d = doc(listOf("आज मौसम अच्छा है।"))
        assertEquals(1, d.sections.size)
        assertEquals("नहीं बताया गया", d.sections.single().title)
        assertNotNull(d.footer)
    }

    @Test
    fun theHindiDemoIsSharedInHindi() {
        val d = doc(DemoConsultations.HINDI.lines)
        assertEquals("परामर्श का सार", d.title)
        assertTrue(d.footer.contains("डॉक्टर के अपने शब्दों"))
        assertTrue(d.section("दवाइयाँ")!!.entries.any { it.headline == "पैरासिटामोल" })
        assertTrue(d.section("इनसे बचें") != null)
    }

    @Test
    fun theDateLineIsCarriedThroughUnchanged() {
        assertEquals("26 Sep 2026, 18:00", doc(DemoConsultations.ENGLISH.lines).dateLine)
    }
}
