package com.packetloss.samjho.scan

import com.packetloss.samjho.demo.DemoConsultations
import com.packetloss.samjho.extract.RuleExtractor
import com.packetloss.samjho.model.Basis
import com.packetloss.samjho.model.Confirmation
import com.packetloss.samjho.model.Extraction
import com.packetloss.samjho.model.FoodRelation
import com.packetloss.samjho.model.Language
import com.packetloss.samjho.model.MissingField
import com.packetloss.samjho.model.Medicine
import com.packetloss.samjho.model.Provenance
import com.packetloss.samjho.model.TimeOfDay
import com.packetloss.samjho.model.TranscriptLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperMergeTest {

    private fun paper(key: String, readAs: String? = null, state: Confirmation = Confirmation.CONFIRMED) =
        ScanItem(0, readAs, key, key, emptyList(), ScanBasis.EXACT, state)

    private val clean = RuleExtractor.extract(DemoConsultations.ENGLISH.lines)
    private val asHeard = RuleExtractor.extract(DemoConsultations.ENGLISH_AS_HEARD.lines)

    private fun Extraction.card(key: String) = medicines.filter { it.key == key }

    /** A spoken card that the rules guessed wrongly, with dosing the doctor really gave. */
    private fun wrongGuess(confirmation: Confirmation = Confirmation.UNCONFIRMED) = Extraction(
        language = Language.ENGLISH,
        lines = listOf(TranscriptLine(0, "Take cefixim twice a day after food for five days")),
        medicines = listOf(
            Medicine(
                name = "cefixim",
                key = "ciprofloxacin",
                timesPerDay = 2,
                foodRelation = FoodRelation.AFTER_FOOD,
                durationDays = 5,
                sourceLines = listOf(0),
                basis = Basis.SOUNDS_LIKE,
                confirmation = confirmation,
            ),
        ),
        dosingLines = setOf(0),
    )

    // ---- 1. the paper wins on a mismatch

    @Test
    fun paperWinsWhenSpeechGuessedTheWrongMedicine() {
        val result = PaperMerge.merge(wrongGuess(), listOf(paper("cefixime", readAs = "Cefixime")))
        val card = result.extraction.medicines.single()
        assertEquals("cefixime", card.key)
        assertEquals(Confirmation.CONFIRMED, card.confirmation)
        assertEquals("cefixim", card.name) // what was heard is kept, not renamed
        assertEquals("Cefixime", card.paper!!.readAs)
        assertEquals(PaperMerge.Kind.PAPER_WINS, result.changes.single().kind)
        assertEquals("cefixim", PaperMerge.heardAs(card))
    }

    @Test
    fun whenThePaperWinsTheDoctorsTimingsAreExactlyWhatTheyWere() {
        val card = PaperMerge.merge(wrongGuess(), listOf(paper("cefixime"))).extraction.medicines.single()
        assertEquals(2, card.timesPerDay)
        assertEquals(FoodRelation.AFTER_FOOD, card.foodRelation)
        assertEquals(5, card.durationDays)
        assertEquals(listOf(0), card.sourceLines)
    }

    @Test
    fun undoingAPaperWinGoesBackToWhatSpeechSaid() {
        val card = PaperMerge.merge(wrongGuess(), listOf(paper("cefixime"))).extraction.medicines.single().reopened()
        assertEquals("ciprofloxacin", card.key)
        assertEquals(Confirmation.UNCONFIRMED, card.confirmation)
        assertNull(card.paper)
    }

    @Test
    fun aSpokenGuessOfTheSameMedicineIsConfirmedByThePaper() {
        val result = PaperMerge.merge(asHeard, listOf(paper("cetirizine")))
        val citrus = result.extraction.medicines.single { it.name == "citrus" }
        assertEquals(Confirmation.CONFIRMED, citrus.confirmation)
        assertNotNull(citrus.paper)
        assertEquals("citrus", PaperMerge.heardAs(citrus))
    }

    // ---- 2. the paper only

    @Test
    fun aMedicineOnlyOnThePaperIsAddedWithNoDosingAtAll() {
        val result = PaperMerge.merge(clean, listOf(paper("metformin", readAs = "Metforrnin")))
        val added = result.extraction.medicines.last()
        assertEquals("metformin", added.key)
        assertEquals(Basis.FROM_PRESCRIPTION, added.basis)
        assertEquals(Provenance.PRESCRIPTION, added.provenance)
        assertEquals(Confirmation.CONFIRMED, added.confirmation)
        assertEquals("Metforrnin", added.paper!!.readAs)
        assertNull(added.timesPerDay)
        assertNull(added.doseCount)
        assertNull(added.foodRelation)
        assertNull(added.durationDays)
        assertEquals(emptyList<TimeOfDay>(), added.timesOfDay)
        assertEquals(emptyList<Int>(), added.sourceLines)
        assertEquals(PaperMerge.Kind.PAPER_ONLY, result.changes.single().kind)
    }

    @Test
    fun theAddedMedicineIsFlaggedAsMissingEveryDetailTheSameWayAsAnyOther() {
        val added = PaperMerge.merge(clean, listOf(paper("metformin"))).extraction.medicines.last()
        assertEquals(
            listOf(MissingField.TIMES_PER_DAY, MissingField.TIME_OF_DAY, MissingField.FOOD_RELATION, MissingField.DURATION),
            added.missing,
        )
    }

    @Test
    fun aPaperOnlyMedicineDoesNotBorrowTimingsFromAnotherMedicine() {
        val added = PaperMerge.merge(clean, listOf(paper("ibuprofen"))).extraction.medicines.last()
        assertNull(added.timesPerDay)
        assertNull(added.durationDays)
        assertEquals(emptyList<TimeOfDay>(), added.timesOfDay)
    }

    // ---- 3. the spoken medicines the paper does not have

    @Test
    fun aSpokenMedicineTheDoctorNamedButThePaperLacksIsLeftExactlyAsItWas() {
        val before = clean.card("paracetamol").single()
        val result = PaperMerge.merge(clean, listOf(paper("cetirizine")))
        assertEquals(before, result.extraction.card("paracetamol").single())
        assertEquals(clean.card("azithromycin").single(), result.extraction.card("azithromycin").single())
        assertNull(result.extraction.card("paracetamol").single().paper)
    }

    @Test
    fun nothingIsEverRemoved() {
        val result = PaperMerge.merge(asHeard, listOf(paper("metformin"), paper("cetirizine")))
        assertTrue(result.extraction.medicines.size >= asHeard.medicines.size)
        asHeard.medicines.forEach { before -> assertTrue(result.extraction.medicines.any { it.name == before.name }) }
    }

    @Test
    fun aWholeConsultationWithNoConfirmedPaperIsUntouched() {
        assertEquals(asHeard, PaperMerge.merge(asHeard, emptyList()).extraction)
    }

    // ---- the real "thrombison" case: the doctor's dosing was spoken, the name was lost

    @Test
    fun theThrombisonLineBecomesAzithromycinFromThePaperWithTheDoctorsOwnDosing() {
        assertTrue(asHeard.unnamedDosing.any { it.text.contains("thrombison") })
        val result = PaperMerge.merge(asHeard, listOf(paper("azithromycin", readAs = "Azithromycin")))
        val card = result.extraction.card("azithromycin").single()
        assertEquals("thrombison", card.name)
        assertEquals(Confirmation.CONFIRMED, card.confirmation)
        assertEquals("thrombison", PaperMerge.heardAs(card))
        // Everything below was said by the doctor on that line.
        assertEquals(1, card.timesPerDay)
        assertEquals(listOf(TimeOfDay.MORNING), card.timesOfDay)
        assertEquals(FoodRelation.EMPTY_STOMACH, card.foodRelation)
        assertEquals(3, card.durationDays)
        assertEquals(PaperMerge.Kind.ATTACHED_TO_DOSING_LINE, result.changes.single().kind)
        assertFalse(result.extraction.unnamedDosing.any { it.text.contains("thrombison") })
    }

    @Test
    fun aSpokenGuessThatAlreadyFitsWhatWasHeardIsNotTakenOverByAnotherMedicine() {
        // "citrus" sounds like its guess, cetirizine, and only vaguely like azithromycin.
        val before = asHeard.medicines.single { it.name == "citrus" }
        val after = PaperMerge.merge(asHeard, listOf(paper("azithromycin"))).extraction.medicines.single { it.name == "citrus" }
        assertEquals(before, after)
    }

    @Test
    fun theWordOnTheDosingLineMustSoundLikeThePaperNameOrNothingIsAttached() {
        val result = PaperMerge.merge(asHeard, listOf(paper("metformin")))
        assertEquals(PaperMerge.Kind.PAPER_ONLY, result.changes.single().kind)
        assertTrue(result.extraction.unnamedDosing.any { it.text.contains("thrombison") }) // still shown as unnamed
        assertNull(result.extraction.card("metformin").single().timesPerDay)
    }

    @Test
    fun theWholeAsHeardConsultationMergesWithThePaperOfItsThreeMedicines() {
        val result = PaperMerge.merge(asHeard, listOf(paper("paracetamol"), paper("azithromycin"), paper("cetirizine")))
        assertEquals(0, result.count(PaperMerge.Kind.PAPER_ONLY))
        assertEquals(1, result.count(PaperMerge.Kind.ATTACHED_TO_DOSING_LINE))
        val meds = result.extraction.medicines
        assertTrue(meds.filter { it.key == "paracetamol" }.all { it.confirmation == Confirmation.CONFIRMED && it.paper != null })
        assertEquals(setOf("paracetamol", "azithromycin", "cetirizine"), meds.map { it.key }.toSet())
        assertEquals(emptyList<TranscriptLine>(), result.extraction.unnamedDosing)
    }

    // ---- what the paper may never override

    @Test
    fun aCardThePatientAlreadyAnsweredIsNeverOverridden() {
        val answered = wrongGuess(Confirmation.CONFIRMED)
        val result = PaperMerge.merge(answered, listOf(paper("cefixime")))
        assertEquals(answered.medicines.single(), result.extraction.medicines.first())
        assertEquals(PaperMerge.Kind.PAPER_ONLY, result.changes.single().kind)
    }

    @Test
    fun aNameTheDoctorSaidOutrightIsNeverOverriddenByADifferentPaperName() {
        val said = Extraction(
            language = Language.ENGLISH,
            lines = listOf(TranscriptLine(0, "Take cetirizine at night")),
            medicines = listOf(Medicine(name = "cetirizine", key = "cetirizine", timesOfDay = listOf(TimeOfDay.NIGHT), sourceLines = listOf(0))),
        )
        val result = PaperMerge.merge(said, listOf(paper("levocetirizine")))
        assertEquals(said.medicines.single(), result.extraction.medicines.first())
        assertEquals("levocetirizine", result.extraction.medicines.last().key)
        assertEquals(Basis.FROM_PRESCRIPTION, result.extraction.medicines.last().basis)
    }

    @Test
    fun aPaperSuggestionNobodyConfirmedIsIgnored() {
        val ignored = listOf(
            paper("metformin", state = Confirmation.UNCONFIRMED),
            paper("iron", state = Confirmation.REJECTED),
            paper("calcium", state = Confirmation.NOT_NEEDED),
        )
        assertEquals(clean, PaperMerge.merge(clean, ignored).extraction)
    }

    @Test
    fun aCardThePatientDismissedComesBackWhenTheyConfirmThatNameFromThePaper() {
        val dismissed = clean.updateMedicine(0) { it.rejected() }
        val key = dismissed.medicines[0].key
        val card = PaperMerge.merge(dismissed, listOf(paper(key))).extraction.medicines[0]
        assertEquals(Confirmation.CONFIRMED, card.confirmation)
    }

    // ---- housekeeping

    @Test
    fun mergingTheSamePaperTwiceChangesNothingFurther() {
        val once = PaperMerge.merge(asHeard, listOf(paper("azithromycin"), paper("metformin"), paper("cetirizine"))).extraction
        val twice = PaperMerge.merge(once, listOf(paper("azithromycin"), paper("metformin"), paper("cetirizine"))).extraction
        assertEquals(once, twice)
    }

    @Test
    fun aLaterAiMatchForANameThePaperAlreadySettledIsDropped() {
        val merged = PaperMerge.merge(asHeard, listOf(paper("azithromycin"))).extraction
        val ai = Medicine(name = "thrombison", key = "azithromycin", basis = Basis.AI_MATCHED, provenance = Provenance.AI, confirmation = Confirmation.UNCONFIRMED)
        assertEquals(merged, merged.withAdded(listOf(ai)))
    }

    @Test
    fun theHeardWordIsOnlyShownWhenItDiffersFromTheConfirmedName() {
        val same = PaperMerge.merge(clean, listOf(paper("azithromycin"))).extraction.card("azithromycin").single()
        assertNull(PaperMerge.heardAs(same))
        val paperOnly = PaperMerge.merge(clean, listOf(paper("metformin"))).extraction.medicines.last()
        assertNull(PaperMerge.heardAs(paperOnly))
        assertNull(PaperMerge.heardAs(clean.medicines.first()))
    }

    @Test
    fun aPaperMedicineOnAnOtherwiseEmptyConsultationStillAppearsAsMedicineWithoutAnyDosing() {
        val empty = RuleExtractor.extract(listOf("आज मौसम अच्छा है।"))
        val merged = PaperMerge.merge(empty, listOf(paper("iron"))).extraction
        assertEquals(1, merged.medicines.size)
        assertEquals(emptyList<Int>(), merged.coveredLines.toList())
    }
}
