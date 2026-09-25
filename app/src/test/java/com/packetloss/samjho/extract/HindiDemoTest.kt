package com.packetloss.samjho.extract

import com.packetloss.samjho.demo.DemoConsultations
import com.packetloss.samjho.model.FoodRelation
import com.packetloss.samjho.model.Language
import com.packetloss.samjho.model.Medicine
import com.packetloss.samjho.model.MissingField
import com.packetloss.samjho.model.TimeOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HindiDemoTest {

    private val result = RuleExtractor.extract(DemoConsultations.HINDI.lines)

    private fun medicine(key: String): Medicine =
        result.medicines.firstOrNull { it.key == key }
            ?: error("no medicine '$key' in ${result.medicines.map { it.key }}")

    @Test
    fun detectsHindi() {
        assertEquals(Language.HINDI, result.language)
    }

    @Test
    fun findsExactlyTheThreeMedicinesTheDoctorNamed() {
        assertEquals(
            listOf("paracetamol", "azithromycin", "cetirizine"),
            result.medicines.map { it.key },
        )
    }

    @Test
    fun showsTheDoctorsOwnWordForEachMedicine() {
        assertEquals("पैरासिटामोल", medicine("paracetamol").name)
        assertEquals("एजिथ्रोमाइसिन", medicine("azithromycin").name)
        assertEquals("सेटिरिजिन", medicine("cetirizine").name)
    }

    @Test
    fun paracetamolThreeTimesADayAfterFoodForFiveDays() {
        val m = medicine("paracetamol")
        assertEquals(3, m.timesPerDay)
        assertEquals(FoodRelation.AFTER_FOOD, m.foodRelation)
        assertEquals(5, m.durationDays)
    }

    @Test
    fun azithromycinMorningEmptyStomachForThreeDays() {
        val m = medicine("azithromycin")
        assertEquals(listOf(TimeOfDay.MORNING), m.timesOfDay)
        assertEquals(FoodRelation.EMPTY_STOMACH, m.foodRelation)
        assertEquals(3, m.durationDays)
    }

    @Test
    fun cetirizineOneTabletAtNight() {
        val m = medicine("cetirizine")
        assertEquals(listOf(TimeOfDay.NIGHT), m.timesOfDay)
        assertEquals(1, m.doseCount)
    }

    @Test
    fun whatTheDoctorDidNotSayAboutCetirizineIsFlaggedNotGuessed() {
        val m = medicine("cetirizine")
        assertNull(m.durationDays)
        assertNull(m.foodRelation)
        assertTrue(MissingField.DURATION in m.missing)
        assertTrue(MissingField.FOOD_RELATION in m.missing)
    }

    @Test
    fun readsTheAvoidList() {
        assertEquals(listOf("ठंडा पानी", "तला हुआ खाना"), result.avoid.map { it.text })
    }

    @Test
    fun keepsTheWarningLineWhole() {
        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings[0].text.contains("अस्पताल"))
    }

    @Test
    fun findsTheFollowUpInFiveDays() {
        val f = result.followUp
        assertNotNull(f)
        assertEquals(5, f!!.inDays)
    }

    @Test
    fun findsTheDiagnosis() {
        assertEquals(listOf("वायरल बुखार"), result.diagnosis.map { it.text })
    }

    @Test
    fun theWarningLineIsNotAlsoReadAsSomethingToAvoid() {
        assertTrue(result.avoid.none { it.text.contains("अस्पताल") })
    }

    @Test
    fun thePatientsOpeningLineProducesNoMedicine() {
        assertTrue(result.medicines.none { 0 in it.sourceLines })
    }
}
