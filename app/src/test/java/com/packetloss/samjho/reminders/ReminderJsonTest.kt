package com.packetloss.samjho.reminders

import com.packetloss.samjho.demo.DemoConsultations
import com.packetloss.samjho.extract.RuleExtractor
import com.packetloss.samjho.model.Extraction
import com.packetloss.samjho.model.FoodRelation
import com.packetloss.samjho.model.PaperNote
import com.packetloss.samjho.model.TimeOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A reminder must open the summary exactly as the patient left it, so the summary has to survive a round trip. */
class ReminderJsonTest {

    private fun roundTrip(e: Extraction) = ReminderJson.extractionFrom(ReminderJson.extraction(e))

    @Test
    fun everyDemoSummarySurvivesARoundTrip() {
        DemoConsultations.ALL.forEach { demo ->
            val e = RuleExtractor.extract(demo.lines)
            assertEquals(demo.id, e, roundTrip(e))
        }
    }

    @Test
    fun theirAnswersAndCandidatesSurvive() {
        val e = RuleExtractor.extract(DemoConsultations.ALL.first { it.id == "en-as-heard" }.lines)
        assertTrue(e.medicines.any { it.isUnconfirmed && it.candidates.isNotEmpty() })
        val answered = e.updateMedicine(0) { it.confirmed() }.updateMedicine(1) { it.rejected() }
        assertEquals(answered, roundTrip(answered))
    }

    @Test
    fun aPaperNoteAndContinuationsSurvive() {
        val e = RuleExtractor.extract(listOf("You have a viral fever.", "Take paracetamol for 3 days.", "After food."))
        val m = e.medicines.first()
        assertTrue("the second sentence is a continuation", m.continuations.isNotEmpty())
        val withPaper = e.updateMedicine(0) { it.copy(paper = PaperNote("paracetamoll")) }
        assertEquals(withPaper, roundTrip(withPaper))
        val fromList = e.updateMedicine(0) { it.copy(paper = PaperNote(null)) }
        assertEquals(fromList, roundTrip(fromList))
    }

    @Test
    fun aBrokenFileIsNoSummaryRatherThanACrash() {
        assertNull(ReminderJson.extractionFrom("{not json"))
        assertNull(ReminderJson.extractionFrom(""))
        assertNull(ReminderJson.extractionFrom(null))
    }

    @Test
    fun remindersSurviveARoundTrip() {
        val list = listOf(
            Reminder("c|a|MORNING", "c", "Azithromycin", null, FoodRelation.EMPTY_STOMACH, TimeOfDay.MORNING, 8, 3, 1_800_000_000_000),
            Reminder("c|b|NIGHT", "c", "पैरासिटामोल", 1, null, TimeOfDay.NIGHT, 21, null, 1_800_000_500_000),
        )
        assertEquals(list, ReminderJson.remindersFrom(ReminderJson.reminders(list)))
        assertEquals(emptyList<Reminder>(), ReminderJson.remindersFrom(null))
        assertEquals(emptyList<Reminder>(), ReminderJson.remindersFrom("garbage"))
    }
}
