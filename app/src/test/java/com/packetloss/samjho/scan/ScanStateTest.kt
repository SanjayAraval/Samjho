package com.packetloss.samjho.scan

import com.packetloss.samjho.model.Confirmation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanStateTest {

    private val read = OcrRead(latin = listOf("Tab Paracetarnol 500", "Cetirizlne"), devanagari = emptyList())

    private fun scanned(): ScanUi {
        val items = ScanMatcher.match(read.allLines())
        return ScanUi().reading().withRead(read, items, 120)
    }

    @Test
    fun aFreshScanShowsEverySuggestionAsUnconfirmed() {
        val ui = scanned()
        assertEquals(ScanStage.Results, ui.stage)
        assertEquals(setOf("paracetamol", "cetirizine"), ui.items.map { it.key }.toSet())
        assertTrue(ui.items.all { it.confirmation == Confirmation.UNCONFIRMED })
    }

    @Test
    fun theRawTextIsKeptSoTheScreenCanShowExactlyWhatWasRead() {
        assertEquals(listOf("Tab Paracetarnol 500", "Cetirizlne"), scanned().read!!.allLines())
    }

    @Test
    fun confirmRejectAndUndoActOnTheRightCardOnly() {
        val ui = scanned()
        val a = ui.items[0].id
        val b = ui.items[1].id
        val next = ui.confirm(a).reject(b)
        assertEquals(Confirmation.CONFIRMED, next.items.first { it.id == a }.confirmation)
        assertEquals(Confirmation.REJECTED, next.items.first { it.id == b }.confirmation)
        val undone = next.undo(b)
        assertEquals(Confirmation.UNCONFIRMED, undone.items.first { it.id == b }.confirmation)
        assertEquals(Confirmation.CONFIRMED, undone.items.first { it.id == a }.confirmation)
    }

    @Test
    fun chooseAnotherConfirmsThePatientsPickAndUndoRestoresTheGuess() {
        val ui = scanned()
        val card = ui.items.first { it.key == "paracetamol" }
        val chose = ui.choose(card.id, "ibuprofen")
        assertEquals("ibuprofen", chose.items.first { it.id == card.id }.key)
        assertEquals(Confirmation.CONFIRMED, chose.items.first { it.id == card.id }.confirmation)
        val back = chose.undo(card.id)
        assertEquals("paracetamol", back.items.first { it.id == card.id }.key)
    }

    // ---- the manual path must work with nothing read at all

    @Test
    fun theManualListWorksBeforeAnyPhotoIsTaken() {
        val ui = ScanUi().pick("metformin")
        assertEquals(ScanStage.Results, ui.stage)
        val item = ui.items.single()
        assertEquals("metformin", item.key)
        assertEquals(Confirmation.CONFIRMED, item.confirmation)
        assertEquals(null, item.readAs)
    }

    @Test
    fun theManualListWorksWhenTheCameraFailed() {
        val ui = ScanUi().failed("no camera").pick("iron")
        assertEquals(ScanStage.Results, ui.stage)
        assertEquals(listOf("iron"), ui.items.map { it.key })
    }

    @Test
    fun theManualListWorksWhenTheCameraReadNothingUseful() {
        val empty = ScanUi().reading().withRead(OcrRead(emptyList(), emptyList()), ScanMatcher.match(emptyList()), 50)
        assertEquals(emptyList<ScanItem>(), empty.items)
        val picked = empty.pick("azithromycin").pick("cetirizine")
        assertEquals(listOf("azithromycin", "cetirizine"), picked.items.map { it.key })
        assertEquals(setOf(0, 1), picked.items.map { it.id }.toSet())
    }

    @Test
    fun pickingSomethingTheCameraAlreadySuggestedConfirmsThatCardInsteadOfDuplicatingIt() {
        val ui = scanned()
        val picked = ui.pick("paracetamol")
        assertEquals(ui.items.size, picked.items.size)
        assertEquals(Confirmation.CONFIRMED, picked.items.first { it.key == "paracetamol" }.confirmation)
    }

    @Test
    fun pickingSomethingAlreadyRejectedBringsItBack() {
        val ui = scanned()
        val id = ui.items.first { it.key == "paracetamol" }.id
        val back = ui.reject(id).pick("paracetamol")
        assertEquals(Confirmation.CONFIRMED, back.items.first { it.id == id }.confirmation)
    }

    @Test
    fun undoOnAMedicineTheyPickedByHandRemovesIt() {
        val ui = ScanUi().pick("iron")
        assertEquals(emptyList<ScanItem>(), ui.undo(ui.items.single().id).items)
    }

    @Test
    fun scanningAgainKeepsWhatThePatientPickedAndNeverDuplicatesIt() {
        val first = scanned().pick("metformin").pick("paracetamol")
        val again = first.reading().withRead(read, ScanMatcher.match(read.allLines()), 90)
        assertEquals(1, again.items.count { it.key == "paracetamol" })
        assertEquals(Confirmation.CONFIRMED, again.items.first { it.key == "paracetamol" }.confirmation)
        assertTrue(again.items.any { it.key == "metformin" })
        assertEquals(again.items.size, again.items.map { it.id }.toSet().size)
    }

    @Test
    fun scanningAgainKeepsAConfirmedSuggestionConfirmedAndNeverSuggestsARejectedOneAgain() {
        val first = scanned()
        val para = first.items.first { it.key == "paracetamol" }.id
        val cet = first.items.first { it.key == "cetirizine" }.id
        val again = first.confirm(para).reject(cet).reading().withRead(read, ScanMatcher.match(read.allLines()), 5)
        assertEquals(Confirmation.CONFIRMED, again.items.single { it.key == "paracetamol" }.confirmation)
        assertEquals(Confirmation.REJECTED, again.items.single { it.key == "cetirizine" }.confirmation)
        assertEquals(2, again.items.size)
    }

    @Test
    fun scanningAgainReplacesSuggestionsThePatientNeverAnsweredWithTheNewReading() {
        val first = scanned()
        val second = OcrRead(latin = listOf("Tab Metforrnin 500"), devanagari = emptyList())
        val again = first.reading().withRead(second, ScanMatcher.match(second.allLines()), 5)
        assertEquals(listOf("metformin"), again.items.map { it.key })
        assertTrue(again.items.all { it.confirmation == Confirmation.UNCONFIRMED })
    }

    @Test
    fun cardIdsStayUniqueAcrossScansAndPicks() {
        var ui = scanned()
        ui = ui.pick("iron").pick("zincovit")
        ui = ui.reading().withRead(read, ScanMatcher.match(read.allLines()), 1)
        assertEquals(ui.items.size, ui.items.map { it.id }.toSet().size)
    }

    @Test
    fun ocrLinesAreDeduplicatedAcrossTheTwoReaders() {
        val r = OcrRead(latin = listOf("Paracetamol 500", " Dolo "), devanagari = listOf("Paracetamol 500", "", "पैरासिटामोल"))
        assertEquals(listOf("Paracetamol 500", "Dolo", "पैरासिटामोल"), r.allLines())
    }
}
