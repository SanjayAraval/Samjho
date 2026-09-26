package com.packetloss.samjho.reminders

import com.packetloss.samjho.demo.DemoConsultations
import com.packetloss.samjho.extract.RuleExtractor
import com.packetloss.samjho.model.Extraction
import com.packetloss.samjho.model.FoodRelation
import com.packetloss.samjho.model.Language
import com.packetloss.samjho.model.Medicine
import com.packetloss.samjho.model.TimeOfDay
import com.packetloss.samjho.ui.Strings
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPlanTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    private fun at(text: String): Instant = ZonedDateTime.parse("${text}[Asia/Kolkata]").toInstant()

    private fun demo(id: String): Extraction = RuleExtractor.extract(DemoConsultations.ALL.first { it.id == id }.lines)

    private fun plan(e: Extraction, now: Instant = at("2026-09-27T06:00:00+05:30")) = ReminderPlan.plan("c1", e, now, zone)

    private fun reminder(remaining: Int?, next: String, hour: Int = 8) = Reminder(
        id = "c1|x|MORNING", consultationId = "c1", name = "X", doseCount = null, food = null,
        slot = TimeOfDay.MORNING, hour = hour, remaining = remaining, nextAt = at(next).toEpochMilli(),
    )

    // ---- the hours

    @Test
    fun hoursAreEightTwoSixNine() {
        assertEquals(8, ReminderPlan.hour(TimeOfDay.MORNING))
        assertEquals(14, ReminderPlan.hour(TimeOfDay.AFTERNOON))
        assertEquals(18, ReminderPlan.hour(TimeOfDay.EVENING))
        assertEquals(21, ReminderPlan.hour(TimeOfDay.NIGHT))
    }

    // ---- which medicines get a reminder

    @Test
    fun onlyMedicinesWithATimeOfDayGetOne() {
        val p = plan(demo("en-viral-fever"))
        assertEquals(listOf("azithromycin", "cetirizine"), p.reminders.map { it.name })
        assertEquals(listOf(TimeOfDay.MORNING, TimeOfDay.NIGHT), p.reminders.map { it.slot })
        // Paracetamol was given "three times a day" but no time of day: no reminder, and it says so.
        assertEquals(listOf(Skipped("paracetamol", SkipReason.NO_TIME_OF_DAY)), p.skipped)
    }

    @Test
    fun aTimesPerDayWithoutATimeOfDayNeverBecomesATime() {
        val e = Extraction(Language.ENGLISH, emptyList(), listOf(Medicine("paracetamol", "paracetamol", timesPerDay = 3, durationDays = 5)))
        val p = plan(e)
        assertTrue(p.reminders.isEmpty())
        assertEquals(listOf(Skipped("paracetamol", SkipReason.NO_TIME_OF_DAY)), p.skipped)
    }

    @Test
    fun durationComesFromTheDoctorOrIsUntilCancelled() {
        val p = plan(demo("en-viral-fever"))
        assertEquals(3, p.reminders.first { it.name == "azithromycin" }.remaining)
        assertNull(p.reminders.first { it.name == "cetirizine" }.remaining)
    }

    @Test
    fun doseAndFoodAreOnlyWhatTheDoctorSaid() {
        val p = plan(demo("en-viral-fever"))
        val cetirizine = p.reminders.first { it.name == "cetirizine" }
        assertEquals(1, cetirizine.doseCount)
        assertNull(cetirizine.food)
        assertEquals(FoodRelation.EMPTY_STOMACH, p.reminders.first { it.name == "azithromycin" }.food)
    }

    @Test
    fun aMedicineWithTwoTimesOfDayGetsTwoReminders() {
        val e = Extraction(
            Language.ENGLISH, emptyList(),
            listOf(Medicine("dolo", "paracetamol", timesOfDay = listOf(TimeOfDay.MORNING, TimeOfDay.NIGHT), durationDays = 4)),
        )
        val p = plan(e)
        assertEquals(listOf(8, 21), p.reminders.map { it.hour })
        assertEquals(2, p.reminders.map { it.id }.toSet().size)
    }

    @Test
    fun anUnconfirmedGuessGetsNoReminderEvenWithATime() {
        val e = demo("en-as-heard")
        val guess = e.medicines.first { it.name == "citrus" }
        assertTrue(guess.isUnconfirmed)
        assertTrue(guess.timesOfDay.isNotEmpty())
        val p = plan(e)
        assertTrue(p.skipped.contains(Skipped("citrus", SkipReason.NOT_CONFIRMED)))
        assertTrue(p.reminders.none { it.name == "citrus" })
    }

    @Test
    fun onceThePatientConfirmsItGetsOneUnderTheConfirmedName() {
        val e = demo("en-as-heard")
        val index = e.medicines.indexOfFirst { it.name == "citrus" }
        val confirmed = e.updateMedicine(index) { it.confirmed() }
        val p = plan(confirmed)
        assertEquals(listOf("Cetirizine"), p.reminders.map { it.name })
    }

    @Test
    fun aDismissedMedicineIsLeftOutAltogether() {
        val e = demo("en-viral-fever")
        val index = e.medicines.indexOfFirst { it.name == "cetirizine" }
        val p = plan(e.updateMedicine(index) { it.rejected() })
        assertTrue(p.reminders.none { it.name == "cetirizine" })
        assertTrue(p.skipped.none { it.name == "cetirizine" })
    }

    // ---- when the first one is due

    @Test
    fun theFirstOccurrenceIsTodayIfTheHourIsAheadOtherwiseTomorrow() {
        val morning = ReminderPlan.firstOccurrence(at("2026-09-27T07:00:00+05:30"), 8, zone)
        assertEquals(at("2026-09-27T08:00:00+05:30"), morning.toInstant())
        val exactly = ReminderPlan.firstOccurrence(at("2026-09-27T08:00:00+05:30"), 8, zone)
        assertEquals(at("2026-09-28T08:00:00+05:30"), exactly.toInstant())
        val late = ReminderPlan.firstOccurrence(at("2026-09-27T21:30:00+05:30"), 21, zone)
        assertEquals(at("2026-09-28T21:00:00+05:30"), late.toInstant())
    }

    // ---- what happens after one fires

    @Test
    fun afterAFiringTheNextIsTomorrowAtTheSameHourAndOneFewerRemains() {
        val now = at("2026-09-27T08:00:05+05:30")
        val next = ReminderPlan.advance(reminder(3, "2026-09-27T08:00:00+05:30"), now, zone)
        assertNotNull(next)
        assertEquals(2, next!!.remaining)
        assertEquals(at("2026-09-28T08:00:00+05:30").toEpochMilli(), next.nextAt)
    }

    @Test
    fun theLastOccurrenceEndsTheReminder() {
        val now = at("2026-09-29T08:00:05+05:30")
        assertNull(ReminderPlan.advance(reminder(1, "2026-09-29T08:00:00+05:30"), now, zone))
    }

    @Test
    fun noDurationRepeatsUntilCancelled() {
        var r: Reminder? = reminder(null, "2026-09-27T08:00:00+05:30")
        var now = at("2026-09-27T08:00:05+05:30")
        repeat(40) {
            r = ReminderPlan.advance(r!!, now, zone)
            assertNotNull(r)
            assertNull(r!!.remaining)
            now = Instant.ofEpochMilli(r!!.nextAt).plusSeconds(5)
        }
    }

    @Test
    fun daysMissedWhilePhoneWasOffCountAsDoneAndAreNotFiredLate() {
        // Due 5 days ago with 5 to go: the phone was off, so all five have passed and nothing is left.
        val gone = ReminderPlan.onRebuild(reminder(5, "2026-09-22T08:00:00+05:30"), at("2026-09-27T09:00:00+05:30"), zone)
        assertNull(gone)
        // Due 2 days ago with 5 to go: two passed, three remain, the next is tomorrow morning.
        val kept = ReminderPlan.onRebuild(reminder(5, "2026-09-25T08:00:00+05:30"), at("2026-09-27T09:00:00+05:30"), zone)
        assertNotNull(kept)
        assertEquals(2, kept!!.remaining)
        assertEquals(at("2026-09-28T08:00:00+05:30").toEpochMilli(), kept.nextAt)
    }

    @Test
    fun aReminderStillInTheFutureIsLeftAloneOnRebuild() {
        val r = reminder(3, "2026-09-28T08:00:00+05:30")
        assertEquals(r, ReminderPlan.onRebuild(r, at("2026-09-27T09:00:00+05:30"), zone))
    }

    @Test
    fun theHourSurvivesADaylightSavingChange() {
        val ny = ZoneId.of("America/New_York")
        val night = ZonedDateTime.of(2026, 3, 7, 21, 0, 0, 0, ny)
        val r = reminder(null, "2026-09-27T08:00:00+05:30", hour = 21).copy(nextAt = night.toInstant().toEpochMilli())
        val next = ReminderPlan.advance(r, night.toInstant().plusSeconds(5), ny)!!
        val local = ZonedDateTime.ofInstant(Instant.ofEpochMilli(next.nextAt), ny)
        assertEquals(21, local.hour)
        assertEquals(8, local.dayOfMonth)
    }

    // ---- the wording

    @Test
    fun theNotificationLineIsTheNameAndWhatTheDoctorSaid() {
        val en = Strings(Language.ENGLISH)
        assertEquals("Paracetamol, 1 tablet, after food", en.reminderLine("Paracetamol", 1, FoodRelation.AFTER_FOOD))
        assertEquals("Paracetamol, 2 tablets", en.reminderLine("Paracetamol", 2, null))
        assertEquals("Paracetamol, after food", en.reminderLine("Paracetamol", null, FoodRelation.AFTER_FOOD))
        assertEquals("Paracetamol", en.reminderLine("Paracetamol", null, null))
        assertEquals("पैरासिटामोल, 1 गोली, खाने के बाद", Strings(Language.HINDI).reminderLine("पैरासिटामोल", 1, FoodRelation.AFTER_FOOD))
    }

    @Test
    fun theClockIsShownInTheLanguageOfTheToggle() {
        assertEquals("8:00 am · Morning", Strings(Language.ENGLISH).reminderTime(TimeOfDay.MORNING, 8))
        assertEquals("2:00 pm · Afternoon", Strings(Language.ENGLISH).reminderTime(TimeOfDay.AFTERNOON, 14))
        assertEquals("9:00 pm · Night", Strings(Language.ENGLISH).reminderTime(TimeOfDay.NIGHT, 21))
        assertEquals("सुबह 8:00", Strings(Language.HINDI).reminderTime(TimeOfDay.MORNING, 8))
    }
}
