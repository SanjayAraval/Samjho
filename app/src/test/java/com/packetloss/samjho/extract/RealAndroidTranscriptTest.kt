package com.packetloss.samjho.extract

import com.packetloss.samjho.model.FoodRelation
import com.packetloss.samjho.model.Language
import com.packetloss.samjho.model.TimeOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the Android system recogniser (en-US, offline, airplane mode on) returned on the iQOO I2501
 * for the English demo consult read with the real drug names, unedited. It is close to right and
 * wrong in exactly the ways real speech recognition is: "Take" heard as "egg", cetirizine as
 * "citrus in", azithromycin as "the thromison", and the leading "If" dropped from the warning.
 */
class RealAndroidTranscriptTest {

    private val heard = listOf(
        "Take the paracetamol tablet three times a day after food for 5 days",
        "and the thromison in the morning on an empty stomach for 3 days",
        "egg 1 tablet of citrus in at night",
        "avoid cold water and fried food",
        "the fever goes above 102 go to the hospital immediately",
        "Come back after 5 days",
        "Also dollar 650",
        "and crossing if the fever comes back",
        "Bluetooth",
    )

    private val result = RuleExtractor.extract(heard)

    private fun medicine(key: String) = result.medicines.firstOrNull { it.key == key }
        ?: error("no '$key' in ${result.medicines.map { it.key to it.name }}")

    @Test
    fun findsTheThreeMedicinesAndNothingElse() {
        assertEquals(
            listOf("paracetamol", "azithromycin", "cetirizine"),
            result.medicines.map { it.key },
        )
    }

    @Test
    fun aMishearingOfTakeIsNotReportedAsAMedicine() {
        assertTrue(result.medicines.none { it.name.equals("egg", ignoreCase = true) })
    }

    @Test
    fun paracetamolIsExtractedInFull() {
        val m = medicine("paracetamol")
        assertEquals(3, m.timesPerDay)
        assertEquals(FoodRelation.AFTER_FOOD, m.foodRelation)
        assertEquals(5, m.durationDays)
    }

    @Test
    fun azithromycinIsRecoveredFromTheMangledWord() {
        val m = medicine("azithromycin")
        assertEquals("thromison", m.name) // the doctor's word as heard, not the lexicon name
        assertEquals(listOf(TimeOfDay.MORNING), m.timesOfDay)
        assertEquals(FoodRelation.EMPTY_STOMACH, m.foodRelation)
        assertEquals(3, m.durationDays)
        assertEquals(listOf(1), m.sourceLines)
    }

    @Test
    fun cetirizineIsRecoveredAndTakenFromAfterTheOf() {
        val m = medicine("cetirizine")
        assertEquals("citrus", m.name)
        assertEquals(1, m.doseCount)
        assertEquals(listOf(TimeOfDay.NIGHT), m.timesOfDay)
        assertEquals(listOf(2), m.sourceLines)
    }

    @Test
    fun aWarningWhoseLeadingIfWasDroppedIsStillAWarning() {
        assertEquals(listOf(4), result.warnings.flatMap { it.sourceLines })
    }

    @Test
    fun readsAvoidAndFollowUp() {
        assertEquals(listOf("cold water", "fried food"), result.avoid.map { it.text })
        val f = result.followUp
        assertNotNull(f)
        assertEquals(5, f!!.inDays)
    }

    @Test
    fun brandNamesWithNoDosingContextAreNotGuessed() {
        // "dollar 650" and "crossing" are dolo and crocin, but nothing says how they are taken.
        assertTrue(result.medicines.none { 6 in it.sourceLines || 7 in it.sourceLines })
        assertEquals(Language.ENGLISH, result.language)
    }

    @Test
    fun everyItemStillCitesARealLine() {
        val valid = result.lines.indices
        result.medicines.forEach { m -> assertTrue(m.sourceLines.isNotEmpty() && m.sourceLines.all { it in valid }) }
    }

    @Test
    fun takeThisImmediatelyIsNotAWarning() {
        val r = RuleExtractor.extract(listOf("Take this tablet immediately after food"))
        assertTrue(r.warnings.isEmpty())
    }
}
