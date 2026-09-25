package com.packetloss.samjho.extract

import com.packetloss.samjho.demo.DemoConsultations
import com.packetloss.samjho.model.FoodRelation
import com.packetloss.samjho.model.Language
import com.packetloss.samjho.model.Medicine
import com.packetloss.samjho.model.TimeOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishDemoTest {

    private val result = RuleExtractor.extract(DemoConsultations.ENGLISH.lines)

    private fun medicine(key: String): Medicine =
        result.medicines.firstOrNull { it.key == key }
            ?: error("no medicine '$key' in ${result.medicines.map { it.key }}")

    @Test
    fun detectsEnglish() {
        assertEquals(Language.ENGLISH, result.language)
    }

    @Test
    fun findsTheThreeMedicines() {
        assertEquals(
            listOf("paracetamol", "azithromycin", "cetirizine"),
            result.medicines.map { it.key },
        )
    }

    @Test
    fun threeTimesADayIsNotMistakenForADuration() {
        val m = medicine("paracetamol")
        assertEquals(3, m.timesPerDay)
        assertEquals(5, m.durationDays)
        assertEquals(FoodRelation.AFTER_FOOD, m.foodRelation)
    }

    @Test
    fun azithromycinMorningEmptyStomachThreeDays() {
        val m = medicine("azithromycin")
        assertEquals(listOf(TimeOfDay.MORNING), m.timesOfDay)
        assertEquals(FoodRelation.EMPTY_STOMACH, m.foodRelation)
        assertEquals(3, m.durationDays)
    }

    @Test
    fun cetirizineAtNightWithNothingInvented() {
        val m = medicine("cetirizine")
        assertEquals(listOf(TimeOfDay.NIGHT), m.timesOfDay)
        assertEquals(1, m.doseCount)
        assertNull(m.durationDays)
        assertNull(m.timesPerDay)
    }

    @Test
    fun readsTheAvoidList() {
        assertEquals(listOf("cold water", "fried food"), result.avoid.map { it.text })
    }

    @Test
    fun theWarningLineIsAWarningAndNotADiagnosis() {
        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings[0].text.contains("hospital"))
        // "you have trouble breathing" sits inside the warning; it must not become a diagnosis.
        assertTrue(result.diagnosis.none { it.text.contains("trouble") })
    }

    @Test
    fun findsTheDiagnosis() {
        assertEquals(listOf("a viral fever"), result.diagnosis.map { it.text })
    }

    @Test
    fun findsTheFollowUp() {
        val f = result.followUp
        assertNotNull(f)
        assertEquals(5, f!!.inDays)
    }
}
